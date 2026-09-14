package com.samuele.crossword.engine

import com.samuele.crossword.domain.WordListAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrosswordBuilderTest {

    private val sampleWords = listOf(
        WordClue("ROMA", "Capitale d'Italia"),
        WordClue("MARE", "Distesa d'acqua"),
        WordClue("SOLE", "Stella"),
        WordClue("LUNA", "Satellite"),
        WordClue("ARTE", "Creatività"),
        WordClue("PIZZA", "Piatto"),
        WordClue("MUSICA", "Suoni"),
        WordClue("GATTO", "Animale"),
        WordClue("CASA", "Abitazione"),
        WordClue("LIBRO", "Pagine")
    )

    @Test
    fun `buildVariationsStrict includes ALL 10 words in every generated variant`() = runBlocking {
        val builder = CrosswordBuilder()
        val reports = builder.buildVariationsStrict(sampleWords, maxSolutions = 10, requireAllWords = true)

        assertFalse("Should produce crossword variations", reports.isEmpty())
        for ((index, report) in reports.withIndex()) {
            assertTrue("Variant #$index must be marked complete", report.complete)
            assertEquals("Variant #$index must contain exactly 10 words", 10, report.placedWords.size)
            assertTrue("Variant #$index must have empty unplaced words", report.unplacedWords.isEmpty())

            val res = report.result
            assertTrue("Result must not be null", res != null)
            assertEquals("Crossword assignments must contain all 10 words", 10, res!!.assignments.size)
            assertTrue("Rows and cols must be valid and positive", res.rows >= 3 && res.cols >= 3)
            // Compattezza: verifica che la griglia non sia dispersa
            val area = res.rows * res.cols
            assertTrue("Grid area ($area for ${res.cols}x${res.rows}) must be reasonably compact", area <= 250)
            val cw = report.crossword!!
            assertTrue("Should have multiple intersections", cw.totalIntersections >= 5)
        }
    }

    @Test
    fun `WordListAnalyzer detects orphan words without common letters`() {
        val words = listOf(
            WordClue("CASA", "Abitazione"),
            WordClue("SOLE", "Stella"),
            WordClue("XYZ", "Nessuna lettera in comune")
        )

        val analysis = WordListAnalyzer.analyzeConnectability(words)
        assertEquals(3, analysis.totalWords)
        assertEquals(1, analysis.orphanWords.size)
        assertEquals("XYZ", analysis.orphanWords.first().word)
    }

    @Test
    fun `GridCleaner clean removes isolated cells`() {
        // Griglia 3x3 con una sola cella bianca isolata a (1,1)
        val white = Array(3) { BooleanArray(3) }
        white[1][1] = true
        val letters = Array(3) { CharArray(3) { ' ' } }
        letters[1][1] = 'A'

        val built = BuiltCrossword(3, 3, white, letters, emptyList())
        val cleaned = GridCleaner.clean(built)

        assertFalse("Isolated cell should be cleaned to black", cleaned.whiteCells[1][1])
    }

    @Test
    fun `buildVariationsStrict handles accented words like POKEMON and generates dense grid`() = runBlocking {
        val challengingWords = listOf(
            WordClue("POKÉMON", "Creature tascabili"),
            WordClue("SENPAIHOUSE", "Casa dei senpai"),
            WordClue("GIANNANNINI", "Cantante senese"),
            WordClue("LALALAND", "Musical con Gosling"),
            WordClue("GINOCCHIO", "Articolazione"),
            WordClue("DISNEY", "Mondo animato"),
            WordClue("SUSHI", "Piatto giapponese")
        )

        val builder = CrosswordBuilder()
        val reports = builder.buildVariationsStrict(challengingWords, maxSolutions = 5, requireAllWords = true)

        assertFalse("Should produce crossword variations even with long words and accents", reports.isEmpty())
        val best = reports.first()
        assertTrue("All 7 words must be placed", best.complete)
        assertEquals(7, best.placedWords.size)
        // Check that POKEMON normalized is placed
        assertTrue(best.placedWords.any { it == "POKEMON" })
        assertTrue("Should have multiple intersections", best.crossword!!.totalIntersections >= 6)
    }

    @Test
    fun `buildVariationsStrict produces dense schema with cyclic intersections`() = runBlocking {
        // CASA, SOLE, LATO, SALE can form a closed 4-word cycle (4 intersections instead of tree's 3)
        val cycleWords = listOf(
            WordClue("CASA", "Abitazione"),
            WordClue("SOLE", "Stella"),
            WordClue("LATO", "Fianco"),
            WordClue("SALE", "Cloruro di sodio")
        )

        val builder = CrosswordBuilder()
        val reports = builder.buildVariationsStrict(cycleWords, maxSolutions = 5, requireAllWords = true)

        assertFalse("Should find solutions", reports.isEmpty())
        val top = reports.first()
        assertTrue("All 4 words placed", top.complete)
        val crossings = top.crossword!!.totalIntersections
        assertTrue("Should achieve cycle with 4 intersections, got $crossings", crossings >= 4)
    }
}
