package com.samuele.crossword.domain

import com.samuele.crossword.engine.WordClue

data class AnalysisResult(
    val totalWords: Int,
    val orphanWords: List<WordClue>, // parole senza lettere in comune con altre
    val possible: Boolean
)

object WordListAnalyzer {

    /**
     * Controlla se le parole fornite condividono lettere tra loro per permettere
     * incroci validi all'interno della griglia.
     */
    fun analyzeConnectability(words: List<WordClue>): AnalysisResult {
        val validWords = words.map { it.copy(word = WordClue.normalize(it.word)) }
            .filter { it.word.length >= 2 }

        if (validWords.isEmpty()) {
            return AnalysisResult(totalWords = 0, orphanWords = emptyList(), possible = false)
        }

        val orphans = validWords.filter { w ->
            validWords.none { other ->
                other !== w && other.word.any { c -> c in w.word }
            }
        }

        return AnalysisResult(
            totalWords = validWords.size,
            orphanWords = orphans,
            possible = orphans.isEmpty() || (orphans.size < validWords.size && validWords.size - orphans.size >= 2)
        )
    }
}
