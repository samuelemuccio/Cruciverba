package com.samuele.crossword.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListParserTest {

    @Test
    fun `parse single line valid format`() {
        val input = "ROMA : Capitale d'Italia"
        val result = WordListParser.parse(input)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.words.size)
        assertEquals("ROMA", result.words[0].word)
        assertEquals("Capitale d'Italia", result.words[0].clue)
    }

    @Test
    fun `parse ignores comments and blank lines`() {
        val input = """
            # Questo è un commento
            
            ROMA : Capitale d'Italia
            MARE : Distesa d'acqua salata
        """.trimIndent()
        val result = WordListParser.parse(input)
        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.words.size)
    }

    @Test
    fun `parse captures errors for missing separator`() {
        val input = "ROMA Capitale"
        val result = WordListParser.parse(input)
        assertEquals(1, result.errors.size)
        assertTrue(result.words.isEmpty())
    }
}
