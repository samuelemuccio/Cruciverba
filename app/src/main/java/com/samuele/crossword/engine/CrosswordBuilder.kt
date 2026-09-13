package com.samuele.crossword.engine

import kotlinx.coroutines.ensureActive
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
    fun toResult(): CrosswordResult {
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

        return CrosswordResult(
            rows = rows,
            cols = cols,
            whiteCells = whiteCells,
            letters = letters,
            slots = numbered,
            assignments = assignments,
            unsolvedSlotIds = numbered.filter { it.id !in assignments }.map { it.id }.toSet(),
            complete = numbered.all { it.id in assignments }
        )
    }
}

data class BuildReport(
    val crossword: BuiltCrossword?,
    val result: CrosswordResult? = crossword?.toResult(),
    val placedWords: List<String>,
    val unplacedWords: List<WordClue>,
    val complete: Boolean,
    val aestheticScore: AestheticScore? = crossword?.aestheticScore
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

    /**
     * Esegue la pipeline completa di generazione e ottimizzazione.
     */
    suspend fun buildVariationsStrict(
        words: List<WordClue>,
        maxSolutions: Int = 20,
        requireAllWords: Boolean = true
    ): List<BuildReport> {
        val unique = words
            .map { it.copy(word = it.word.trim().uppercase()) }
            .distinctBy { it.word }
            .filter { it.word.length >= 2 && it.word.all { c -> c.isLetter() } }

        if (unique.size < 2) return emptyList()

        val allWordStrings = unique.map { it.word }
        val totalWords = unique.size

        // 1. PREPROCESSING: Calcolo del Crossing Potential per ciascuna parola
        val potentialMap = unique.associateWith { wc ->
            CrosswordOptimizer.calculateCrossingPotential(wc.word, allWordStrings)
        }

        // Ordina le parole in base al loro potenziale di incrocio
        val wordsByPotential = unique.sortedByDescending { potentialMap[it] ?: 0f }

        // Limite dimensionale per lato (garantisce schemi compatti evitando forme disperse)
        val maxWordLen = unique.maxOf { it.word.length }
        val totalChars = unique.sumOf { it.word.length }
        val maxSideLimit = max(13, max(maxWordLen + 4, (sqrt(totalChars.toDouble()) * 1.95).toInt()))

        val completedCrosswords = mutableListOf<BuiltCrossword>()
        val signatures = HashSet<String>()

        // 2. BEAM SEARCH CON SELEZIONE MULTIPLA DI ANCHOR WORDS
        // Selezioniamo le migliori anchor words (parole con alto potenziale d'incrocio)
        val candidateAnchors = wordsByPotential.take(min(6, wordsByPotential.size))

        for (anchor in candidateAnchors) {
            coroutineContext.ensureActive()
            // Testiamo l'ancora sia orizzontale che verticale
            for (dir in listOf(Direction.ACROSS, Direction.DOWN)) {
                val solutionsFromBeam = runBeamSearch(
                    anchor = anchor,
                    anchorDir = dir,
                    allWords = unique,
                    potentialMap = potentialMap,
                    maxSideLimit = maxSideLimit,
                    beamWidth = 45
                )

                for (sol in solutionsFromBeam) {
                    if (sol.placed.size == totalWords && sol.aspectRatioFactor <= 1.80f) {
                        val sig = signature(sol)
                        if (sig !in signatures) {
                            signatures += sig
                            completedCrosswords += sol
                        }
                    }
                }

                if (completedCrosswords.size >= maxSolutions * 2) break
            }
            if (completedCrosswords.size >= maxSolutions * 2) break
        }

        // 3. FALLBACK CSP BACKTRACKING (Se il Beam Search necessita di ulteriori varianti)
        if (completedCrosswords.size < maxSolutions) {
            var restart = 0
            val maxRestarts = 120
            while (completedCrosswords.size < maxSolutions && restart < maxRestarts) {
                coroutineContext.ensureActive()
                restart++

                val anchor = wordsByPotential.take(min(5, wordsByPotential.size)).random(random)
                val anchorDir = if (restart % 2 == 0) Direction.ACROSS else Direction.DOWN
                val remaining = unique.filter { it.word != anchor.word }.shuffled(random)

                val sol = solveBacktracking(
                    firstWord = anchor,
                    firstDirection = anchorDir,
                    remaining = remaining,
                    maxSideLimit = maxSideLimit + 1
                )

                if (sol != null && sol.placed.size == totalWords && sol.aspectRatioFactor <= 1.85f) {
                    val sig = signature(sol)
                    if (sig !in signatures) {
                        signatures += sig
                        completedCrosswords += sol
                    }
                }
            }
        }

        // 4. DECOUPLED GRID OPTIMIZER:
        // Valutazione estetica geometrica rigorosa su tutte le soluzioni trovate
        val scoredReports = completedCrosswords.map { cw ->
            val aesthetic = cw.aestheticScore
            BuildReport(
                crossword = cw,
                result = cw.toResult(),
                placedWords = cw.placed.map { it.word },
                unplacedWords = emptyList(),
                complete = true,
                aestheticScore = aesthetic
            )
        }

        // Ordina rigorosamente per il punteggio estetico matematico dell'Optimizer
        return scoredReports
            .sortedByDescending { it.aestheticScore?.totalScore ?: Float.NEGATIVE_INFINITY }
            .take(maxSolutions)
    }

    suspend fun buildVariations(
        words: List<WordClue>,
        maxSolutions: Int = 20,
        minWordsUsed: Int = 2
    ): List<BuiltCrossword> {
        val reports = buildVariationsStrict(words, maxSolutions, requireAllWords = true)
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
        beamWidth: Int
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
                var chosenWord: WordClue? = null
                var chosenPlacements: List<CandidatePlacement> = emptyList()
                var minDomainSize = Int.MAX_VALUE
                var bestDegree = -1f

                for (candidate in state.unplaced) {
                    val placements = findLegalPlacements(
                        cellMap = state.cellMap,
                        placed = state.placed,
                        bbox = state.bbox,
                        word = candidate.word,
                        maxSideLimit = maxSideLimit
                    )

                    if (placements.isEmpty()) {
                        // Se una parola non ha domini validi, questo ramo è senza uscita
                        chosenWord = null
                        break
                    }

                    val degree = potentialMap[candidate] ?: 0f

                    if (placements.size < minDomainSize ||
                        (placements.size == minDomainSize && degree > bestDegree)
                    ) {
                        minDomainSize = placements.size
                        chosenPlacements = placements
                        chosenWord = candidate
                        bestDegree = degree
                    }
                }

                if (chosenWord == null) {
                    // Ramo non completabile, proseguiamo con gli altri stati del beam
                    continue
                }

                val nextUnplaced = state.unplaced.filter { it.word != chosenWord.word }

                // Espansione dei candidati per questo stato
                for (p in chosenPlacements) {
                    val newMap = HashMap(state.cellMap)
                    chosenWord.word.forEachIndexed { i, ch ->
                        val r = if (p.direction == Direction.ACROSS) p.row else p.row + i
                        val c = if (p.direction == Direction.ACROSS) p.col + i else p.col
                        newMap[key(r, c)] = ch
                    }

                    val newPlaced = ArrayList(state.placed)
                    newPlaced.add(PlacedWord(chosenWord.word, chosenWord.clue, p.row, p.col, p.direction))

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
            // Introduciamo una minima componente stocastica per diversità tra rami
            beam = nextCandidates
                .sortedByDescending { it.score + random.nextFloat() * 15f }
                .take(beamWidth)
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
        maxSideLimit: Int
    ): List<CandidatePlacement> {
        val out = mutableListOf<CandidatePlacement>()
        val visited = HashSet<Long>()

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

                    val stateKey = (startRow.toLong() shl 36) or
                            ((startCol.toLong() and 0xFFFFFFL) shl 1) or
                            (if (newDir == Direction.ACROSS) 1L else 0L)

                    if (!visited.add(stateKey)) continue

                    val candidate = evaluatePlacement(
                        cellMap = cellMap,
                        bbox = bbox,
                        word = word,
                        row = startRow,
                        col = startCol,
                        dir = newDir,
                        maxSideLimit = maxSideLimit
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
        maxSideLimit: Int
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

        // Scarta forme sproporzionate
        if (ratio > 1.80f) return null

        val currentArea = bbox.area
        val newArea = newRows * newCols
        val deltaArea = newArea - currentArea
        val deltaPerimeter = (newRows + newCols) - (bbox.rows + bbox.cols)

        // Punteggio Euristico CSP:
        // + molti incroci
        // + bonus spazio interno (zero espansione griglia)
        // - penalità espansione area e perimetro
        // - penalità distanza dal baricentro
        // - penalità allungamento forma
        val intersectionPoints = when (intersections) {
            1 -> 120f
            2 -> 600f
            3 -> 1800f
            else -> 3000f
        }

        val interiorFitBonus = if (deltaArea == 0) 450f else 0f
        val expansionPenalty = (deltaArea * 6f) + (deltaPerimeter * 16f)
        val aspectPenalty = ((ratio - 1.0f) * 350f) + (abs(newRows - newCols) * 10f)

        // Calcolo vicinanza al baricentro attuale
        val centerR = (bbox.minR + bbox.maxR) / 2f
        val centerC = (bbox.minC + bbox.maxC) / 2f
        val wordMidR = (wordMinR + wordMaxR) / 2f
        val wordMidC = (wordMinC + wordMaxC) / 2f
        val distToCenter = sqrt((wordMidR - centerR) * (wordMidR - centerR) + (wordMidC - centerC) * (wordMidC - centerC))
        val centerPenalty = distToCenter * 12f

        val stepScore = intersectionPoints + interiorFitBonus - expansionPenalty - aspectPenalty - centerPenalty

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

        var bestWord: WordClue? = null
        var bestCandidates: List<CandidatePlacement> = emptyList()
        var minCandidates = Int.MAX_VALUE

        for (candidate in unplaced) {
            val placements = findLegalPlacements(
                cellMap = cellMap,
                placed = placed,
                bbox = bbox,
                word = candidate.word,
                maxSideLimit = maxSideLimit
            )
            if (placements.isEmpty()) return false
            if (placements.size < minCandidates) {
                minCandidates = placements.size
                bestCandidates = placements
                bestWord = candidate
                if (minCandidates == 1) break
            }
        }

        val chosenWord = bestWord ?: return false
        val sortedCandidates = bestCandidates.sortedByDescending { it.stepScore + random.nextInt(-20, 21) }
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
