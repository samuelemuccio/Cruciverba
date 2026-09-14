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

    @Test
    fun `parse strips diacritics from words`() {
        val input = "POKÉMON : Creature tascabili\nCITTÀ : Centro urbano"
        val result = WordListParser.parse(input)
        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.words.size)
        assertEquals("POKEMON", result.words[0].word)
        assertEquals("CITTA", result.words[1].word)
    }

    @Test
    fun `parse handles diverse delimiters and numbered lists from AI`() {
        val input = """
            1. MILANO - Centro economico lombardo
            2) FIRENZE = Culla del Rinascimento
            3. VENEZIA | Città sull'acqua e gondole
            * NAPOLI: Patria della pizza margherita
            - TORINO: Prima capitale d'Italia
        """.trimIndent()
        val result = WordListParser.parse(input)
        assertTrue("Expected no errors but got ${result.errors}", result.errors.isEmpty())
        assertEquals(5, result.words.size)
        assertEquals("MILANO", result.words[0].word)
        assertEquals("Centro economico lombardo", result.words[0].clue)
        assertEquals("FIRENZE", result.words[1].word)
        assertEquals("VENEZIA", result.words[2].word)
        assertEquals("NAPOLI", result.words[3].word)
        assertEquals("TORINO", result.words[4].word)
    }

    @Test
    fun `parse handles inverted question format`() {
        val input = "Qual è la capitale d'Italia? ROMA"
        val result = WordListParser.parse(input)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.words.size)
        assertEquals("ROMA", result.words[0].word)
        assertEquals("Qual è la capitale d'Italia?", result.words[0].clue)
    }
}
