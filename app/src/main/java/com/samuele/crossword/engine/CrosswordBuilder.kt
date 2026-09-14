package com.samuele.crossword.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class PlacedWord(
    val word: String,
    val clue: String,
    val row: Int,
    val col: Int,
    val direction: Direction
) {
    fun cells(): List<Pair<Int, Int>> = (0 until word.length).map { i ->
        if (direction == Direction.ACROSS) row to (col + i)
        else (row + i) to col
    }
}

data class BuiltCrossword(
    val rows: Int,
    val cols: Int,
    val whiteCells: Array<BooleanArray>,
    val letters: Array<CharArray>,
    val placed: List<PlacedWord>
) {
    /** Calcola il numero totale di intersezioni (lettere condivise da 2 parole incrociate). */
    val totalIntersections: Int by lazy {
        val totalWordLetters = placed.sumOf { it.word.length }
        val uniqueWhiteCells = whiteCells.sumOf { row -> row.count { it } }
        totalWordLetters - uniqueWhiteCells
    }

    /** Rapporto di forma tra lato lungo e lato corto (1.0 = perfettamente quadrato). */
    val aspectRatioFactor: Float by lazy {
        val longer = max(rows, cols).toFloat()
        val shorter = max(1, min(rows, cols)).toFloat()
        longer / shorter
    }

    /** Densità della griglia: percentuale di celle con lettere sull'area del rettangolo. */
    val density: Float by lazy {
        val uniqueWhiteCells = whiteCells.sumOf { row -> row.count { it } }
        val area = rows * cols
        if (area > 0) uniqueWhiteCells.toFloat() / area.toFloat() else 0f
    }

    /** Punteggio estetico e geometrico calcolato dall'Optimizer. */
    val aestheticScore: AestheticScore by lazy {
        CrosswordOptimizer.evaluateAesthetic(this)
    }

    /** Converte in CrosswordResult per la UI e l'esportazione. */
    fun toResult(mysteryWord: WordClue? = null): CrosswordResult {
        val grid = CrosswordGrid(rows, cols, whiteCells)
        val rawSlots = grid.extractSlots()
        val numbered = Numbering.assignNumbers(grid, rawSlots)

        val assignments = HashMap<Int, WordClue>()
        for (slot in numbered) {
            val match = placed.firstOrNull { pw ->
                pw.direction == slot.direction &&
                pw.row == slot.row &&
                pw.col == slot.col &&
                pw.word.length == slot.length
            }
            if (match != null) {
                assignments[slot.id] = WordClue(match.word, match.clue)
            }
        }

        val mysteryInfo = if (mysteryWord != null) {
            val norm = WordClue.normalize(mysteryWord.word)
            val mPlaced = placed.firstOrNull { it.word == norm }
            if (mPlaced != null) {
                val mCells = mPlaced.cells()
                val intersected = mutableListOf<Int>()
                for (i in 0 until mPlaced.word.length) {
                    val coord = mCells[i]
                    val hasCross = placed.any { pw -> pw != mPlaced && pw.cells().contains(coord) }
                    if (hasCross) intersected += i
                }
                val mSlot = numbered.firstOrNull { s ->
                    s.direction == mPlaced.direction && s.row == mPlaced.row && s.col == mPlaced.col && s.length == mPlaced.word.length
                }
                MysteryWordInfo(
                    word = norm,
                    clue = mysteryWord.clue,
                    slotId = mSlot?.id,
                    slotNumber = mSlot?.number,
                    direction = mPlaced.direction,
                    cells = mCells,
                    intersectedIndices = intersected,
                    rawDisplayWord = mysteryWord.word
                )
            } else null
        } else null

        return CrosswordResult(
            rows = rows,
            cols = cols,
            whiteCells = whiteCells,
            letters = letters,
            slots = numbered,
            assignments = assignments,
            unsolvedSlotIds = numbered.filter { it.id !in assignments }.map { it.id }.toSet(),
            complete = numbered.all { it.id in assignments },
            mysteryInfo = mysteryInfo
        )
    }
}

data class BuildReport(
    val crossword: BuiltCrossword?,
    val result: CrosswordResult? = crossword?.toResult(),
    val placedWords: List<String>,
    val unplacedWords: List<WordClue>,
    val complete: Boolean,
    val aestheticScore: AestheticScore? = crossword?.aestheticScore,
    val mysteryInfo: MysteryWordInfo? = result?.mysteryInfo
)

/**
 * Generatore professionale di cruciverba custom basato sull'architettura:
 *
 * WORD BANK
 *    ↓
 * Preprocessing (Crossing potential & Italian letter weights)
 *    ↓
 * Anchor selection (Scelta dei migliori perni di incrocio)
 *    ↓
 * CSP + MRV + Degree Heuristic (Constraint Satisfaction Problem)
 *    ↓
 * Beam Search (Mantiene i migliori K stati parziali per livello)
 *    ↓
 * Grid Optimizer (Valutazione estetica, geometrica, simmetria e densità)
 *    ↓
 * Trim + Normalize + Validate (100% parole garantite)
 */
class CrosswordBuilder(private val random: Random = Random.Default) {

    private data class BoundingBox(
        val minR: Int,
        val maxR: Int,
        val minC: Int,
        val maxC: Int
    ) {
        val rows: Int get() = maxR - minR + 1
        val cols: Int get() = maxC - minC + 1
        val area: Int get() = rows * cols
        val longer: Float get() = max(rows, cols).toFloat()
        val shorter: Float get() = max(1, min(rows, cols)).toFloat()
        val aspectRatio: Float get() = longer / shorter
    }

    private data class PartialState(
        val placed: List<PlacedWord>,
        val cellMap: Map<Long, Char>,
        val unplaced: List<WordClue>,
        val bbox: BoundingBox,
        val score: Float,
        val intersections: Int
    )

    private data class CandidatePlacement(
        val row: Int,
        val col: Int,
        val direction: Direction,
        val intersections: Int,
        val newBbox: BoundingBox,
        val stepScore: Float
    )

    private data class GridPosKey(
        val row: Int,
        val col: Int,
        val direction: Direction
    )

    /**
     * Esegue la pipeline completa di generazione e ottimizzazione.
     * Esegue ricerche iterative ad alta densità per massimizzare il numero di incroci,
     * la compattezza e la qualità geometrica, esplorando più configurazioni e ripetizioni.
     */
     suspend fun buildVariationsStrict(
         words: List<WordClue>,
         mysteryWord: WordClue? = null,
         maxSolutions: Int = 30,
         requireAllWords: Boolean = true
     ): List<BuildReport> = withContext(Dispatchers.Default) {
         val normMystery = mysteryWord?.let {
             val clean = WordClue.normalize(it.word)
             if (clean.length >= 2) WordClue(clean, it.clue.ifBlank { "Parola misteriosa da svelare" }) else null
         }

         val combinedWords = if (normMystery != null) {
             val filtered = words.filterNot { WordClue.normalize(it.word) == normMystery.word }
             listOf(normMystery) + filtered
         } else {
             words
         }

         val unique = combinedWords
             .map { it.copy(word = WordClue.normalize(it.word)) }
             .distinctBy { it.word }
             .filter { it.word.length >= 2 && it.word.all { c -> c in 'A'..'Z' } }

         if (unique.size < 2) return@withContext emptyList()

         val allWordStrings = unique.map { it.word }
         val totalWords = unique.size

         // 1. PREPROCESSING: Calcolo del Crossing Potential per ciascuna parola
         val potentialMap = unique.associateWith { wc ->
             CrosswordOptimizer.calculateCrossingPotential(wc.word, allWordStrings)
         }

         // Ordina le parole in base al loro potenziale di incrocio
         val wordsByPotential = unique.sortedByDescending { potentialMap[it] ?: 0f }

         val maxWordLen = unique.maxOf { it.word.length }
         val totalChars = unique.sumOf { it.word.length }
         val tightLimit = max(11, max(maxWordLen + 3, (sqrt(totalChars.toDouble()) * 1.55).toInt()))
         val standardLimit = max(13, max(maxWordLen + 6, (sqrt(totalChars.toDouble()) * 2.00).toInt()))

         val completedCrosswords = mutableListOf<BuiltCrossword>()
         val signatures = HashSet<String>()

         fun addSolution(sol: BuiltCrossword, minSize: Int = if (requireAllWords) totalWords else 3) {
             val maxAspect = if (normMystery != null) 3.85f else 2.15f
             if (sol.placed.size >= minSize && sol.aspectRatioFactor <= maxAspect) {
                 if (normMystery == null || sol.placed.any { it.word == normMystery.word }) {
                     val sig = signature(sol)
                     if (sig !in signatures) {
                         signatures += sig
                         completedCrosswords += sol
                     }
                 }
             }
         }

         // 2. FASE 1: BEAM SEARCH AD ALTA DENSITÀ CON SELEZIONE ANCORAGGIO
         // Esplora sia l'ancoraggio diretto che ancoraggi su parole ad alto potenziale
         val candidateAnchors = if (normMystery != null) {
             listOf(normMystery) + wordsByPotential.filter { it.word != normMystery.word }.take(min(3, wordsByPotential.size))
         } else {
             wordsByPotential.take(min(8, wordsByPotential.size))
         }

         for (limit in listOf(tightLimit, standardLimit)) {
             for (anchor in candidateAnchors) {
                 coroutineContext.ensureActive()
                 for (dir in listOf(Direction.ACROSS, Direction.DOWN)) {
                     val solutionsFromBeam = runBeamSearch(
                         anchor = anchor,
                         anchorDir = dir,
                         allWords = unique,
                         potentialMap = potentialMap,
                         maxSideLimit = limit,
                         beamWidth = 140,
                         anchorBonusWord = normMystery?.word,
                         allowPartial = false
                     )

                     for (sol in solutionsFromBeam) {
                         addSolution(sol)
                     }

                     if (completedCrosswords.size >= maxSolutions * 2) break
                 }
                 if (completedCrosswords.size >= maxSolutions * 2) break
             }
             if (completedCrosswords.size >= maxSolutions * 2) break
         }

         // 3. FASE 2: RIPETIZIONI ITERATIVE MULTI-START
         var restarts = 0
         val maxRestarts = if (normMystery != null) 90 else 160
         val minTargetCrossings = totalWords

         while (restarts < maxRestarts) {
             coroutineContext.ensureActive()
             restarts++

             val anchor = if (normMystery != null && random.nextFloat() < 0.60f) {
                 normMystery
             } else {
                 wordsByPotential.take(min(7, wordsByPotential.size)).random(random)
             }
             val anchorDir = if (random.nextBoolean()) Direction.ACROSS else Direction.DOWN
             val remaining = unique.filter { it.word != anchor.word }.shuffled(random)

             val currentLimit = if (restarts % 2 == 0) tightLimit else standardLimit

             val sol = solveBacktracking(
                 firstWord = anchor,
                 firstDirection = anchorDir,
                 remaining = remaining,
                 maxSideLimit = currentLimit
             )

             if (sol != null) {
                 addSolution(sol)
             }

             val bestIntersections = completedCrosswords.maxOfOrNull { it.totalIntersections } ?: 0
             if (completedCrosswords.size >= maxSolutions * 3 && bestIntersections > minTargetCrossings && restarts >= 50) {
                 break
             }
         }

         // Fallback se nessuna soluzione completa è stata trovata con la parola misteriosa:
         // accetta soluzioni con il maggior numero possibile di parole incrociate
         if (completedCrosswords.isEmpty() && normMystery != null) {
             for (dir in listOf(Direction.ACROSS, Direction.DOWN)) {
                 coroutineContext.ensureActive()
                 val partials = runBeamSearch(
                     anchor = normMystery,
                     anchorDir = dir,
                     allWords = unique,
                     potentialMap = potentialMap,
                     maxSideLimit = standardLimit + 2,
                     beamWidth = 160,
                     anchorBonusWord = normMystery.word,
                     allowPartial = true
                 )
                 for (p in partials) {
                     addSolution(p, minSize = 3)
                 }
             }
         }

         // 4. VALUTAZIONE ESTETICA GEOMETRICA E ORDINAMENTO RIGOROSO
         val scoredReports = completedCrosswords.map { cw ->
             val aesthetic = cw.aestheticScore
             val result = cw.toResult(normMystery)
             val placedSet = cw.placed.map { it.word }.toSet()
             val unplaced = unique.filter { it.word !in placedSet }
             BuildReport(
                 crossword = cw,
                 result = result,
                 placedWords = cw.placed.map { it.word },
                 unplacedWords = unplaced,
                 complete = unplaced.isEmpty(),
                 aestheticScore = aesthetic,
                 mysteryInfo = result.mysteryInfo
             )
         }

         // Ordina con preferenza per numero di parole posizionate, bilanciamento incroci ed estetica a griglia reale
         if (normMystery != null) {
             scoredReports
                 .sortedWith(
                     compareByDescending<BuildReport> { it.placedWords.size }
                         .thenByDescending { report ->
                             val info = report.mysteryInfo
                             val total = max(1, info?.totalLetters ?: 1)
                             val rev = info?.revealedCount ?: 0
                             val idealMin = max(2, (total * 0.20f).toInt())
                             val idealMax = max(3, (total * 0.50f).toInt())
                             // Bilanciamento ottimale: parte delle lettere incrociate (es. 3-6 lettere su 14)
                             // e le altre dedotte, premiando una griglia a cruciverba reale intrecciata anziché a pettine
                             val balanceBonus = when {
                                 rev in idealMin..idealMax -> 500f
                                 rev > 0 && rev <= idealMax + 2 -> 250f
                                 rev > (total * 0.70f) -> -350f // Troppe parole sulla parola cardine (effetto pettine sgradito)
                                 else -> -100f
                             }
                             (report.aestheticScore?.totalScore ?: 0f) + balanceBonus
                         }
                 )
                 .take(maxSolutions)
         } else {
             scoredReports
                 .sortedByDescending { it.aestheticScore?.totalScore ?: Float.NEGATIVE_INFINITY }
                 .take(maxSolutions)
         }
     }

    suspend fun buildVariations(
        words: List<WordClue>,
        mysteryWord: WordClue? = null,
        maxSolutions: Int = 20,
        minWordsUsed: Int = 2
    ): List<BuiltCrossword> {
        val reports = buildVariationsStrict(words, mysteryWord, maxSolutions, requireAllWords = true)
        return reports.mapNotNull { it.crossword }
    }

    /**
     * BEAM SEARCH CSP SOLVER:
     * Ad ogni livello seleziona la parola più vincolata (MRV + Degree)
     * ed espande lo spazio di ricerca mantenendo i migliori [beamWidth] stati parziali.
     */
    private suspend fun runBeamSearch(
        anchor: WordClue,
        anchorDir: Direction,
        allWords: List<WordClue>,
        potentialMap: Map<WordClue, Float>,
        maxSideLimit: Int,
        beamWidth: Int,
        anchorBonusWord: String? = null,
        allowPartial: Boolean = false
    ): List<BuiltCrossword> {
        val initialMap = HashMap<Long, Char>()
        anchor.word.forEachIndexed { i, ch ->
            val r = if (anchorDir == Direction.ACROSS) 0 else i
            val c = if (anchorDir == Direction.ACROSS) i else 0
            initialMap[key(r, c)] = ch
        }
        val initialPlaced = listOf(PlacedWord(anchor.word, anchor.clue, 0, 0, anchorDir))
        val initialBbox = BoundingBox(
            minR = 0,
            maxR = if (anchorDir == Direction.ACROSS) 0 else anchor.word.length - 1,
            minC = 0,
            maxC = if (anchorDir == Direction.ACROSS) anchor.word.length - 1 else 0
        )

        var beam = listOf(
            PartialState(
                placed = initialPlaced,
                cellMap = initialMap,
                unplaced = allWords.filter { it.word != anchor.word },
                bbox = initialBbox,
                score = 0f,
                intersections = 0
            )
        )

        val totalWords = allWords.size
        val results = mutableListOf<BuiltCrossword>()

        while (beam.isNotEmpty()) {
            coroutineContext.ensureActive()

            // Se gli stati hanno completato tutte le parole, raccoglili
            val finished = beam.filter { it.placed.size == totalWords }
            for (f in finished) {
                results += trim(f.placed, f.cellMap)
            }

            val ongoing = beam.filter { it.placed.size < totalWords }
            if (ongoing.isEmpty()) break

            val nextCandidates = mutableListOf<PartialState>()

            for (state in ongoing) {
                // 2. SCELTA DELLA PROSSIMA PAROLA: MRV (Minimum Remaining Values) + DEGREE
                val candidatesWithPlacements = mutableListOf<Pair<WordClue, List<CandidatePlacement>>>()

                for (candidate in state.unplaced) {
                    val placements = findLegalPlacements(
                        cellMap = state.cellMap,
                        placed = state.placed,
                        bbox = state.bbox,
                        word = candidate.word,
                        maxSideLimit = maxSideLimit,
                        anchorBonusWord = anchorBonusWord
                    )

                    if (placements.isNotEmpty()) {
                        candidatesWithPlacements += candidate to placements
                    }
                }

                if (candidatesWithPlacements.isEmpty()) {
                    // Solo qui il ramo è bloccato perché nessuna parola rimasta può agganciarsi
                    continue
                }

                // Cerca posizionamenti con 2 o più incroci tra TUTTE le parole candidate
                val multiIntersectionMoves = mutableListOf<Pair<WordClue, CandidatePlacement>>()
                for ((candWord, placements) in candidatesWithPlacements) {
                    for (p in placements) {
                        if (p.intersections >= 2) {
                            multiIntersectionMoves += candWord to p
                        }
                    }
                }

                // Parola MRV (quella con minor numero di posizionamenti ammissibili per completezza CSP)
                val mrvPair = candidatesWithPlacements.minWith(
                    compareBy<Pair<WordClue, List<CandidatePlacement>>> { it.second.size }
                        .thenByDescending { potentialMap[it.first] ?: 0f }
                        .thenByDescending { it.first.word.length }
                )

                // Raccoglie le migliori mosse:
                // 1) Priorità assoluta alle mosse che creano 2+ incroci chiudendo cicli e densità
                // 2) Mantiene anche i migliori posizionamenti della parola MRV per evitare blocchi
                val movesToExpand = mutableListOf<Pair<WordClue, CandidatePlacement>>()
                if (multiIntersectionMoves.isNotEmpty()) {
                    movesToExpand += multiIntersectionMoves
                        .sortedByDescending { it.second.stepScore }
                        .take(12)
                }
                movesToExpand += mrvPair.second
                    .sortedByDescending { it.stepScore }
                    .take(4)
                    .map { mrvPair.first to it }

                val distinctMoves = movesToExpand.distinctBy { (w, p) ->
                    "${w.word}:${p.row}:${p.col}:${p.direction}"
                }

                // Espansione dei candidati per questo stato
                for ((chosenWord, p) in distinctMoves) {
                    val newMap = HashMap(state.cellMap)
                    chosenWord.word.forEachIndexed { i, ch ->
                        val r = if (p.direction == Direction.ACROSS) p.row else p.row + i
                        val c = if (p.direction == Direction.ACROSS) p.col + i else p.col
                        newMap[key(r, c)] = ch
                    }

                    val newPlaced = ArrayList(state.placed)
                    newPlaced.add(PlacedWord(chosenWord.word, chosenWord.clue, p.row, p.col, p.direction))

                    val nextUnplaced = state.unplaced.filter { it.word != chosenWord.word }
                    val accumulatedScore = state.score + p.stepScore
                    val totalIntersections = state.intersections + p.intersections

                    nextCandidates += PartialState(
                        placed = newPlaced,
                        cellMap = newMap,
                        unplaced = nextUnplaced,
                        bbox = p.newBbox,
                        score = accumulatedScore,
                        intersections = totalIntersections
                    )
                }
            }

            if (nextCandidates.isEmpty()) break

            // BEAM PRUNING: Conserva solo i migliori K stati ordinati per punteggio parziale
            // Introduciamo una minima componente stocastica per diversità tra rami valutata una volta per stato
            beam = nextCandidates
                .map { it to (it.score + random.nextFloat() * 20f) }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(beamWidth)
        }

        if (allowPartial && results.isEmpty()) {
            val partials = beam.filter { it.placed.size >= 3 }.sortedByDescending { it.placed.size }
            for (p in partials.take(15)) {
                results += trim(p.placed, p.cellMap)
            }
        }

        return results
    }

    /**
     * Ricerca di tutti i domini validi per una parola nel CSP.
     * Rispetta:
     * - Coerenza degli incroci (stessa lettera sulla cella condivisa)
     * - Nessuna collisione illegale
     * - Nessuna parola spuria adiacente (separazione ortogonale)
     * - Rispetto dei limiti massimi della griglia e rapporto d'aspetto
     */
    private fun findLegalPlacements(
        cellMap: Map<Long, Char>,
        placed: List<PlacedWord>,
        bbox: BoundingBox,
        word: String,
        maxSideLimit: Int,
        anchorBonusWord: String? = null
    ): List<CandidatePlacement> {
        val out = mutableListOf<CandidatePlacement>()
        val visited = HashSet<GridPosKey>()

        for (existing in placed) {
            val existCells = existing.cells()
            for ((idxE, cell) in existCells.withIndex()) {
                val letterE = existing.word[idxE]
                for (idxN in word.indices) {
                    val letterN = word[idxN]
                    if (letterN != letterE) continue

                    val newDir = if (existing.direction == Direction.ACROSS) Direction.DOWN else Direction.ACROSS
                    val startRow = if (newDir == Direction.ACROSS) cell.first else cell.first - idxN
                    val startCol = if (newDir == Direction.ACROSS) cell.second - idxN else cell.second

                    val stateKey = GridPosKey(startRow, startCol, newDir)

                    if (!visited.add(stateKey)) continue

                    val candidate = evaluatePlacement(
                        cellMap = cellMap,
                        bbox = bbox,
                        word = word,
                        row = startRow,
                        col = startCol,
                        dir = newDir,
                        maxSideLimit = maxSideLimit,
                        placedCount = placed.size,
                        placed = placed,
                        anchorBonusWord = anchorBonusWord
                    )
                    if (candidate != null) {
                        out += candidate
                    }
                }
            }
        }
        return out
    }

    /**
     * Valuta la validità CSP e il punteggio euristico locale di una collocazione.
     */
    private fun evaluatePlacement(
        cellMap: Map<Long, Char>,
        bbox: BoundingBox,
        word: String,
        row: Int,
        col: Int,
        dir: Direction,
        maxSideLimit: Int,
        placedCount: Int,
        placed: List<PlacedWord> = emptyList(),
        anchorBonusWord: String? = null
    ): CandidatePlacement? {
        // Cella subito prima e subito dopo devono essere vuote
        val beforeR = if (dir == Direction.ACROSS) row else row - 1
        val beforeC = if (dir == Direction.ACROSS) col - 1 else col
        if (cellMap.containsKey(key(beforeR, beforeC))) return null

        val afterR = if (dir == Direction.ACROSS) row else row + word.length
        val afterC = if (dir == Direction.ACROSS) col + word.length else col
        if (cellMap.containsKey(key(afterR, afterC))) return null

        var intersections = 0
        for (i in word.indices) {
            val r = if (dir == Direction.ACROSS) row else row + i
            val c = if (dir == Direction.ACROSS) col + i else col
            val existing = cellMap[key(r, c)]
            if (existing != null) {
                if (existing != word[i]) return null
                intersections++
            } else {
                // Prevenzione di parole spurie: se la cella è nuova, le celle adiacenti parallele
                // non devono contenere lettere che si incollerebbero lateralmente creando parole non volute
                val side1 = if (dir == Direction.ACROSS) key(r - 1, c) else key(r, c - 1)
                val side2 = if (dir == Direction.ACROSS) key(r + 1, c) else key(r, c + 1)
                if (cellMap.containsKey(side1) || cellMap.containsKey(side2)) return null
            }
        }
        if (intersections == 0) return null

        val wordMinR = row
        val wordMaxR = if (dir == Direction.ACROSS) row else row + word.length - 1
        val wordMinC = col
        val wordMaxC = if (dir == Direction.ACROSS) col + word.length - 1 else col

        val newMinR = min(bbox.minR, wordMinR)
        val newMaxR = max(bbox.maxR, wordMaxR)
        val newMinC = min(bbox.minC, wordMinC)
        val newMaxC = max(bbox.maxC, wordMaxC)

        val newRows = newMaxR - newMinR + 1
        val newCols = newMaxC - newMinC + 1

        if (newRows > maxSideLimit || newCols > maxSideLimit) return null

        val longer = max(newRows, newCols).toFloat()
        val shorter = max(1, min(newRows, newCols)).toFloat()
        val ratio = longer / shorter

        // Scarta forme sproporzionate (più tollerante all'inizio per consentire incroci su parole lunghe)
        val maxAllowedRatio = if (placedCount <= 4) 2.6f else 2.1f
        if (ratio > maxAllowedRatio) return null

        val currentArea = bbox.area
        val newArea = newRows * newCols
        val deltaArea = newArea - currentArea
        val deltaPerimeter = (newRows + newCols) - (bbox.rows + bbox.cols)

        // Punteggio Euristico CSP:
        // + bonus massiccio per incroci multipli (favorisce chiusura di cicli, anelli e densità)
        // + bonus spazio interno (zero espansione griglia)
        // - penalità espansione area e perimetro
        // - penalità distanza dal baricentro
        // - penalità allungamento forma
        val intersectionPoints = when {
            intersections >= 4 -> 5500f + (intersections - 4) * 1500f
            intersections == 3 -> 3600f
            intersections == 2 -> 1700f
            intersections == 1 -> if (placedCount <= 2) 150f else 0f
            else -> -1000f
        }

        val interiorFitBonus = if (deltaArea == 0) 750f else 0f
        val expansionPenalty = (deltaArea * 6f) + (deltaPerimeter * 15f)
        val aspectPenalty = ((ratio - 1.0f) * 250f) + (abs(newRows - newCols) * 10f)

        // Calcolo vicinanza al baricentro attuale
        val centerR = (bbox.minR + bbox.maxR) / 2f
        val centerC = (bbox.minC + bbox.maxC) / 2f
        val wordMidR = (wordMinR + wordMaxR) / 2f
        val wordMidC = (wordMinC + wordMaxC) / 2f
        val distToCenter = sqrt((wordMidR - centerR) * (wordMidR - centerR) + (wordMidC - centerC) * (wordMidC - centerC))
        val centerPenalty = distToCenter * 12f

        // Bonus per chi incrocia la parola cardine misteriosa (con tetto ottimale per evitare l'effetto pettine)
        var anchorCrosses = 0
        var anchorPlacedRef: PlacedWord? = null
        if (anchorBonusWord != null && placed.isNotEmpty()) {
            val anchorPlaced = placed.firstOrNull { it.word == anchorBonusWord }
            if (anchorPlaced != null) {
                anchorPlacedRef = anchorPlaced
                val aCells = anchorPlaced.cells()
                for (i in word.indices) {
                    val r = if (dir == Direction.ACROSS) row else row + i
                    val c = if (dir == Direction.ACROSS) col + i else col
                    if (aCells.contains(r to c)) {
                        anchorCrosses++
                    }
                }
            }
        }

        val anchorBonus = if (anchorPlacedRef != null && anchorCrosses > 0) {
            val existingAnchorCrosses = placed.count { pw ->
                pw != anchorPlacedRef && pw.cells().any { anchorPlacedRef.cells().contains(it) }
            }
            // Target ideale: circa il 35-45% delle lettere della parola cardine incrociate direttamente
            val targetWords = max(2, min(5, (anchorPlacedRef.word.length * 0.38f).toInt()))
            if (existingAnchorCrosses < targetWords) {
                anchorCrosses * 350f
            } else {
                // Raggiunta la soglia ideale, riduci il bonus così le altre parole si incrociano tra loro
                anchorCrosses * 30f
            }
        } else 0f

        // Bonus griglia 2D: premia fortemente le parole che corrono parallele alla parola cardine
        // e incrociano le parole perpendicolari, trasformando il pettine in un vero cruciverba
        val gridWeaveBonus = if (anchorPlacedRef != null && dir == anchorPlacedRef.direction && intersections >= 1) {
            450f
        } else 0f

        val stepScore = intersectionPoints + interiorFitBonus + anchorBonus + gridWeaveBonus - expansionPenalty - aspectPenalty - centerPenalty

        return CandidatePlacement(
            row = row,
            col = col,
            direction = dir,
            intersections = intersections,
            newBbox = BoundingBox(newMinR, newMaxR, newMinC, newMaxC),
            stepScore = stepScore
        )
    }

    /**
     * Fallback Backtracking MRV per set di parole particolarmente restrittivi.
     */
    private suspend fun solveBacktracking(
        firstWord: WordClue,
        firstDirection: Direction,
        remaining: List<WordClue>,
        maxSideLimit: Int
    ): BuiltCrossword? {
        val placed = ArrayList<PlacedWord>()
        val cellMap = HashMap<Long, Char>()

        firstWord.word.forEachIndexed { i, ch ->
            val r = if (firstDirection == Direction.ACROSS) 0 else i
            val c = if (firstDirection == Direction.ACROSS) i else 0
            cellMap[key(r, c)] = ch
        }
        placed += PlacedWord(firstWord.word, firstWord.clue, 0, 0, firstDirection)

        val initialBbox = BoundingBox(
            minR = 0,
            maxR = if (firstDirection == Direction.ACROSS) 0 else firstWord.word.length - 1,
            minC = 0,
            maxC = if (firstDirection == Direction.ACROSS) firstWord.word.length - 1 else 0
        )

        val stepCounter = intArrayOf(0)
        val maxSteps = 2500

        val success = backtrackStep(
            placed = placed,
            cellMap = cellMap,
            bbox = initialBbox,
            unplaced = remaining,
            maxSideLimit = maxSideLimit,
            stepCounter = stepCounter,
            maxSteps = maxSteps
        )

        if (!success || placed.size != remaining.size + 1) return null

        return trim(placed, cellMap)
    }

    private suspend fun backtrackStep(
        placed: ArrayList<PlacedWord>,
        cellMap: HashMap<Long, Char>,
        bbox: BoundingBox,
        unplaced: List<WordClue>,
        maxSideLimit: Int,
        stepCounter: IntArray,
        maxSteps: Int
    ): Boolean {
        if (unplaced.isEmpty()) return true
        if (stepCounter[0] > maxSteps) return false
        stepCounter[0]++

        if (stepCounter[0] % 120 == 0) {
            coroutineContext.ensureActive()
        }

        val candidatesWithPlacements = mutableListOf<Pair<WordClue, List<CandidatePlacement>>>()

        for (candidate in unplaced) {
            val placements = findLegalPlacements(
                cellMap = cellMap,
                placed = placed,
                bbox = bbox,
                word = candidate.word,
                maxSideLimit = maxSideLimit
            )
            if (placements.isNotEmpty()) {
                candidatesWithPlacements += candidate to placements
            }
        }

        if (candidatesWithPlacements.isEmpty()) return false

        // Verifica se ci sono parole in grado di realizzare 2 o più incroci contemporanei
        val maxIntersections = candidatesWithPlacements.maxOf { pair ->
            pair.second.maxOf { it.intersections }
        }

        val (chosenWord, chosenPlacements) = if (maxIntersections >= 2) {
            // Priorità a parole che chiudono maglie dense, tie-break con MRV
            candidatesWithPlacements
                .filter { pair -> pair.second.any { it.intersections == maxIntersections } }
                .minByOrNull { it.second.size }!!
        } else {
            // Normale MRV per evitare di rimanere bloccati
            candidatesWithPlacements.minByOrNull { it.second.size }!!
        }

        val sortedCandidates = chosenPlacements
            .map { it to (it.stepScore + random.nextInt(-25, 26)) }
            .sortedByDescending { it.second }
            .map { it.first }
        val nextUnplaced = unplaced.filter { it.word != chosenWord.word }

        for (p in sortedCandidates) {
            val newKeys = ArrayList<Long>()
            chosenWord.word.forEachIndexed { i, ch ->
                val r = if (p.direction == Direction.ACROSS) p.row else p.row + i
                val c = if (p.direction == Direction.ACROSS) p.col + i else p.col
                val k = key(r, c)
                if (!cellMap.containsKey(k)) {
                    cellMap[k] = ch
                    newKeys += k
                }
            }
            placed += PlacedWord(chosenWord.word, chosenWord.clue, p.row, p.col, p.direction)

            if (backtrackStep(placed, cellMap, p.newBbox, nextUnplaced, maxSideLimit, stepCounter, maxSteps)) {
                return true
            }

            // Rollback
            placed.removeAt(placed.lastIndex)
            for (k in newKeys) {
                cellMap.remove(k)
            }
        }

        return false
    }

    /**
     * Normalizzazione e Bounding Box trimming:
     * Trasla la griglia cosicché minRow = 0 e minCol = 0,
     * rimuovendo qualsiasi margine esterno non necessario.
     */
    private fun trim(placed: List<PlacedWord>, cellMap: Map<Long, Char>): BuiltCrossword {
        val minR = cellMap.keys.minOf { (it shr 32).toInt() }
        val maxR = cellMap.keys.maxOf { (it shr 32).toInt() }
        val minC = cellMap.keys.minOf { it.toInt() }
        val maxC = cellMap.keys.maxOf { it.toInt() }
        val rows = maxR - minR + 1
        val cols = maxC - minC + 1

        val white = Array(rows) { BooleanArray(cols) }
        val letters = Array(rows) { CharArray(cols) { ' ' } }
        cellMap.forEach { (k, ch) ->
            val r = (k shr 32).toInt() - minR
            val c = k.toInt() - minC
            white[r][c] = true
            letters[r][c] = ch
        }
        val shifted = placed.map {
            it.copy(row = it.row - minR, col = it.col - minC)
        }
        return BuiltCrossword(rows, cols, white, letters, shifted)
    }

    private fun signature(b: BuiltCrossword): String {
        val sb = StringBuilder()
        for (r in 0 until b.rows) {
            for (c in 0 until b.cols) {
                sb.append(if (b.whiteCells[r][c]) b.letters[r][c] else '#')
            }
        }
        return sb.toString()
    }

    private fun key(r: Int, c: Int): Long =
        (r.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)
}
