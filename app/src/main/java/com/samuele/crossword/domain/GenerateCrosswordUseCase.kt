package com.samuele.crossword.domain

import com.samuele.crossword.engine.*
import kotlin.time.Duration

class GenerateCrosswordUseCase {
    suspend operator fun invoke(
        rows: Int,
        cols: Int,
        mask: Array<BooleanArray>?,
        words: List<WordClue>,
        timeout: Duration
    ): SolveOutcome {
        if (words.isEmpty()) return SolveOutcome.InvalidInput("Lista parole vuota")
        val grid = CrosswordGrid(rows, cols, mask)
        val solver = CrosswordSolver(grid, words, timeout)
        return solver.solve()
    }
}
