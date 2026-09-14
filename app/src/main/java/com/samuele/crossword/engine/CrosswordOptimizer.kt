package com.samuele.crossword.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Punteggio estetico e geometrico del cruciverba.
 * Separa rigorosamente la fase di risoluzione (SOLVER) dalla fase di
 * valutazione qualitativa ed estetica (OPTIMIZER).
 */
data class AestheticScore(
    val crossings: Int,
    val density: Float,
    val compactness: Float,
    val symmetry: Float,
    val internalHoles: Int,
    val isolatedWords: Int,
    val area: Int,
    val aspectRatio: Float,
    val totalScore: Float
)

object CrosswordOptimizer {

    /**
     * Frequenza relativa delle lettere nella lingua italiana (in percentuale normalizzata).
     * Le vocali e le consonanti liquide/dentali creano i migliori punti di ancoraggio.
     */
    private val ITALIAN_LETTER_WEIGHTS: Map<Char, Float> = mapOf(
        'E' to 11.79f, 'A' to 11.74f, 'I' to 11.28f, 'O' to 9.83f,
        'N' to 6.88f,  'R' to 6.37f,  'T' to 5.62f,  'S' to 4.98f,
        'L' to 6.51f,  'C' to 4.50f,  'D' to 3.73f,  'U' to 3.11f,
        'M' to 2.51f,  'P' to 3.05f,  'V' to 2.10f,  'G' to 1.64f,
        'B' to 0.92f,  'F' to 1.15f,  'Z' to 1.18f,  'H' to 1.43f,
        'Q' to 0.51f,
        'K' to 0.50f,  'W' to 0.50f,  'X' to 0.50f,  'Y' to 0.50f, 'J' to 0.50f
    )

    /**
     * Calcola il potenziale di incrocio (Crossing Potential) di una parola
     * rispetto all'insieme di parole inserite dall'utente.
     * Considera sia la frequenza teorica delle lettere in italiano, sia l'effettiva
     * presenza delle lettere nelle altre parole del cruciverba.
     */
    fun calculateCrossingPotential(word: String, allWords: List<String>): Float {
        val upper = WordClue.normalize(word)
        val otherWords = allWords.map { WordClue.normalize(it) }.filter { it != upper }
        if (otherWords.isEmpty()) return 0f

        var matchOpportunities = 0f
        for (ch in upper) {
            val italianFreq = ITALIAN_LETTER_WEIGHTS[ch] ?: 1.0f
            var appearsInOthers = 0
            for (other in otherWords) {
                if (ch in other) appearsInOthers++
            }
            // Più la lettera compare nelle altre parole e più è frequente, più alto è il potenziale
            matchOpportunities += (appearsInOthers * 10f) + (italianFreq * 1.5f)
        }

        // Bonus lunghezza media equilibrata (le parole tra 4 e 8 lettere formano i perni migliori)
        val lengthBonus = when (upper.length) {
            in 5..7 -> 25f
            in 4..8 -> 15f
            else -> 0f
        }

        return matchOpportunities + lengthBonus
    }

    /**
     * Valuta geometricamente ed esteticamente uno schema di cruciverba completo.
     * Formula:
     * score = crossings * 100
     *       + density * 80
     *       + compactness * 100
     *       + symmetry * 35
     *       - holes * 40
     *       - isolated * 90
     *       - area * 0.5
     *       - aspectRatioPenalty
     */
    fun evaluateAesthetic(built: BuiltCrossword): AestheticScore {
        val rows = built.rows
        val cols = built.cols
        val area = rows * cols

        val occupiedCells = built.whiteCells.sumOf { r -> r.count { it } }
        val density = if (area > 0) occupiedCells.toFloat() / area.toFloat() else 0f

        // 1. Numero incroci effettivi: ogni incrocio rende la griglia un vero cruciverba
        val crossings = built.totalIntersections
        val minTreeCrossings = max(1, built.placed.size - 1)
        val extraCrossings = max(0, crossings - minTreeCrossings)
        val extraCrossingsBonus = (extraCrossings * extraCrossings) * 150f

        // 2. Compattezza geometrica: rapporto tra celle occupate e l'area convessa approssimata
        val compactness = calculateCompactness(built.whiteCells, rows, cols)

        // 3. Simmetria (rotazionale a 180 gradi, standard enigmistico)
        val symmetry = calculateRotationalSymmetry(built.whiteCells, rows, cols)

        // 4. Buchi interni (celle nere isolate 1x1, peso minimo per non penalizzare anelli a 4 parole)
        val internalHoles = countInternalHoles(built.whiteCells, rows, cols)

        // 5. Parole isolate (parole che hanno solo 1 incrocio al bordo o che creano tentacoli esterni)
        val isolatedWords = countIsolatedWords(built)

        // 6. Rapporto d'aspetto (penalità per griglie a corridoio sbilanciate)
        val longer = max(rows, cols).toFloat()
        val shorter = max(1, min(rows, cols)).toFloat()
        val ratio = longer / shorter
        val aspectRatioPenalty = if (ratio > 1.30f) (ratio - 1.0f) * 250f else 0f

        val score = (crossings * 250f) +
                extraCrossingsBonus +
                (density * 220f) +
                (compactness * 120f) +
                (symmetry * 25f) -
                (isolatedWords * 160f) -
                (internalHoles * 5f) -
                (area * 0.4f) -
                aspectRatioPenalty

        return AestheticScore(
            crossings = crossings,
            density = density,
            compactness = compactness,
            symmetry = symmetry,
            internalHoles = internalHoles,
            isolatedWords = isolatedWords,
            area = area,
            aspectRatio = ratio,
            totalScore = score
        )
    }

    /**
     * Calcola la simmetria rotazionale enigmistica (180°).
     * Più celle bianche hanno il corrispondente ruotato bianco, più lo schema è armonico.
     */
    private fun calculateRotationalSymmetry(grid: Array<BooleanArray>, rows: Int, cols: Int): Float {
        if (rows == 0 || cols == 0) return 0f
        var totalMatches = 0
        var totalWhites = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c]) {
                    totalWhites++
                    val symR = rows - 1 - r
                    val symC = cols - 1 - c
                    if (grid[symR][symC]) {
                        totalMatches++
                    }
                }
            }
        }
        return if (totalWhites > 0) totalMatches.toFloat() / totalWhites.toFloat() else 0f
    }

    /**
     * Calcola la compattezza misurando quante celle bianche sono vicine al baricentro
     * e la concentrazione delle celle rispetto al bounding box.
     */
    private fun calculateCompactness(grid: Array<BooleanArray>, rows: Int, cols: Int): Float {
        if (rows == 0 || cols == 0) return 0f
        val centerR = rows / 2f
        val centerC = cols / 2f
        val maxDist = kotlin.math.sqrt(centerR * centerR + centerC * centerC).coerceAtLeast(1f)

        var distSum = 0f
        var count = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c]) {
                    val dr = r - centerR
                    val dc = c - centerC
                    val dist = kotlin.math.sqrt(dr * dr + dc * dc)
                    distSum += dist
                    count++
                }
            }
        }
        if (count == 0) return 0f
        val avgDist = distSum / count
        // 1.0 = tutte al centro, decresce se sparse verso l'esterno
        return (1.0f - (avgDist / maxDist)).coerceIn(0f, 1f)
    }

    /**
     * Conta le celle nere "intrappolate" (buchi interni inutili),
     * ovvero celle vuote che hanno celle bianche sia a sinistra che a destra,
     * e sia sopra che sotto (creando un antiestetico formaggio coi buchi).
     */
    private fun countInternalHoles(grid: Array<BooleanArray>, rows: Int, cols: Int): Int {
        var holes = 0
        for (r in 1 until rows - 1) {
            for (c in 1 until cols - 1) {
                if (!grid[r][c]) {
                    // Controlla se è circondata orizzontalmente e verticalmente da celle bianche
                    val hasLeft = (0 until c).any { grid[r][it] }
                    val hasRight = (c + 1 until cols).any { grid[r][it] }
                    val hasTop = (0 until r).any { grid[it][c] }
                    val hasBottom = (r + 1 until rows).any { grid[it][c] }

                    if (hasLeft && hasRight && hasTop && hasBottom) {
                        holes++
                    }
                }
            }
        }
        return holes
    }

    /**
     * Conta le parole "isolate" o con un solo incrocio debole.
     */
    private fun countIsolatedWords(built: BuiltCrossword): Int {
        var isolated = 0
        for (pw in built.placed) {
            val pwCells = pw.cells().toSet()
            // Quante altre parole intersecano questa?
            val crossingsWithOthers = built.placed.count { other ->
                other !== pw && other.cells().any { it in pwCells }
            }
            if (crossingsWithOthers <= 1) {
                isolated++
            }
        }
        return isolated
    }
}
