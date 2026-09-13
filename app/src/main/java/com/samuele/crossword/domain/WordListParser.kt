package com.samuele.crossword.domain

import com.samuele.crossword.engine.WordClue

object WordListParser {
    /**
     * Formato: "parola : definizione" una per riga.
     * Ignora righe vuote e commenti (#).
     */
    fun parse(text: String): ParseResult {
        val words = ArrayList<WordClue>()
        val errors = ArrayList<String>()
        text.lineSequence().forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val sep = line.indexOf(':')
            if (sep <= 0) {
                errors += "Riga ${idx + 1}: separatore ':' mancante"
                return@forEachIndexed
            }
            val word = line.substring(0, sep).trim()
            val clue = line.substring(sep + 1).trim()
            if (word.isEmpty() || clue.isEmpty()) {
                errors += "Riga ${idx + 1}: parola o definizione vuota"
                return@forEachIndexed
            }
            words += WordClue(word.uppercase(), clue)
        }
        return ParseResult(words, errors)
    }
    data class ParseResult(val words: List<WordClue>, val errors: List<String>)
}
