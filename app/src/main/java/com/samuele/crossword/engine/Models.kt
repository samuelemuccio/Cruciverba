package com.samuele.crossword.engine

import java.text.Normalizer

data class WordClue(val word: String, val clue: String) {
    init {
        require(word.isNotBlank()) { "word must not be blank" }
    }
    val normalized: String get() = normalize(word)

    companion object {
        fun normalize(raw: String): String {
            val nfd = Normalizer.normalize(raw.trim().uppercase(), Normalizer.Form.NFD)
            return nfd.replace("\\p{M}+".toRegex(), "").filter { it in 'A'..'Z' }
        }
    }
}

enum class Direction { ACROSS, DOWN }

data class Cell(val row: Int, val col: Int)

data class Slot(
    val id: Int,
    val row: Int,
    val col: Int,
    val length: Int,
    val direction: Direction,
    val cells: IntArray,        // flat indices r*cols + c, precalcolati
    val number: Int = 0         // popolato dopo numbering
) {
    fun cellAt(idx: Int, cols: Int): Cell = Cell(cells[idx] / cols, cells[idx] % cols)
}

data class MysteryWordInfo(
    val word: String,
    val clue: String,
    val slotId: Int? = null,
    val slotNumber: Int? = null,
    val direction: Direction = Direction.ACROSS,
    val cells: List<Pair<Int, Int>> = emptyList(),
    val intersectedIndices: List<Int> = emptyList() // indici delle lettere (0..length-1) incrociate con altre parole
) {
    val totalLetters: Int get() = word.length
    val revealedCount: Int get() = intersectedIndices.size
    val deductionCount: Int get() = (totalLetters - revealedCount).coerceAtLeast(0)
}

/** Risultato completo: griglia + slots numerati + definizioni. */
data class CrosswordResult(
    val rows: Int,
    val cols: Int,
    val whiteCells: Array<BooleanArray>,
    val letters: Array<CharArray>,           // ' ' per celle vuote
    val slots: List<Slot>,                   // con number popolato
    val assignments: Map<Int, WordClue>,     // slotId -> WordClue
    val unsolvedSlotIds: Set<Int>,           // slot che non hanno trovato parola
    val complete: Boolean,
    val mysteryInfo: MysteryWordInfo? = null
)

sealed class SolveOutcome {
    data class Success(val result: CrosswordResult) : SolveOutcome()
    data class Partial(val result: CrosswordResult) : SolveOutcome()
    data object NoSolution : SolveOutcome()
    data object Timeout : SolveOutcome()
    data class InvalidInput(val reason: String) : SolveOutcome()
}
