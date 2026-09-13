package com.samuele.crossword.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CrosswordSolverTest {

    @Test
    fun `simple 3x3 solves`() = runBlocking {
        val words = listOf(
            WordClue("ABC", "first across"),
            WordClue("DEF", "second across"),
            WordClue("GHI", "third across"),
            WordClue("ADG", "first down"),
            WordClue("BEH", "second down"),
            WordClue("CFI", "third down")
        )
        val grid = CrosswordGrid(3, 3)
        val solver = CrosswordSolver(grid, words)
        val outcome = solver.solve()
        assertTrue(outcome is SolveOutcome.Success)
    }

    @Test
    fun `no words returns invalid input`() = runBlocking {
        val outcome = CrosswordSolver(CrosswordGrid(3, 3), emptyList()).solve()
        assertTrue(outcome is SolveOutcome.InvalidInput)
    }

    @Test
    fun `solveAll finds multiple valid solutions`() = runBlocking {
        val words = listOf(
            WordClue("ABC", "first across"),
            WordClue("DEF", "second across"),
            WordClue("GHI", "third across"),
            WordClue("ADG", "first down"),
            WordClue("BEH", "second down"),
            WordClue("CFI", "third down")
        )
        val grid = CrosswordGrid(3, 3)
        val solver = CrosswordSolver(grid, words)
        val solutions = solver.solveAll(limit = 5)
        assertTrue(solutions.isNotEmpty())
        assertTrue(solutions.first().complete)
    }
}
