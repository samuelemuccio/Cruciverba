package com.samuele.crossword.domain

import com.samuele.crossword.engine.WordClue

object WordListParser {

    const val AI_PROMPT_TEMPLATE = """Formatta il seguente elenco di mie domande e risposte per il mio generatore di cruciverba.
Converti ogni coppia ESATTAMENTE in questo formato (una riga per parola, PAROLA in maiuscolo seguita da ':' e definizione):

PAROLA: Breve definizione

Regole fondamentali:
1. La PAROLA deve essere in MAIUSCOLO, una sola parola senza spazi o trattini, composta solo da lettere.
2. Se la risposta conteneva più parole, uniscile (es. "NEW YORK" diventa "NEWYORK").
3. La definizione deve essere concisa e descrittiva.
4. Non aggiungere introduzioni, numeri di elenco o conclusioni, genera SOLO le righe nel formato richiesto.

Ecco le mie domande e risposte da formattare:
[INCOLLA QUI LE TUE DOMANDE E RISPOSTE]"""

    data class ParseResult(val words: List<WordClue>, val errors: List<String>)

    /**
     * Esegue il parsing di testo incollato o generato da AI.
     * Supporta:
     * - "PAROLA: Definizione"
     * - "PAROLA - Definizione" o "PAROLA – Definizione"
     * - "PAROLA = Definizione"
     * - "PAROLA | Definizione"
     * - Liste numerate: "1. PAROLA: Definizione", "1) PAROLA - Definizione"
     * - Liste puntate: "- PAROLA: Definizione", "* PAROLA: Definizione"
     * - Inversioni con punto interrogativo: "Qual è la capitale? ROMA"
     * - Formati tabellari o CSV (tab, punto e virgola)
     */
    fun parse(text: String): ParseResult {
        val words = ArrayList<WordClue>()
        val errors = ArrayList<String>()

        text.lineSequence().forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            // Rimuove numerazione o elenchi puntati all'inizio: "1. ", "1) ", "- ", "* ", "• "
            val cleanedLine = line
                .replaceFirst(Regex("""^(\d+[\.\)]|\-\s*|\*\s*|•\s*|\[\d+\]\s*)\s*"""), "")
                .trim()

            if (cleanedLine.isEmpty()) return@forEachIndexed

            val parsedPair = extractPair(cleanedLine)
            if (parsedPair == null) {
                errors += "Riga ${idx + 1}: separatore mancante o formato non riconosciuto"
                return@forEachIndexed
            }

            val (rawWord, rawClue) = parsedPair
            val cleanWord = WordClue.normalize(rawWord)
            val cleanClue = rawClue.trim().trim('"', '\'')

            if (cleanWord.length < 2 || cleanClue.isBlank()) {
                errors += "Riga ${idx + 1}: parola ('$rawWord') o definizione non valida (minimo 2 lettere)"
                return@forEachIndexed
            }

            words += WordClue(cleanWord, cleanClue)
        }

        return ParseResult(words, errors)
    }

    private fun extractPair(line: String): Pair<String, String>? {
        // Verifica ':'
        val colonIdx = line.indexOf(':')
        if (colonIdx > 0) {
            val p1 = line.substring(0, colonIdx).trim().trim('*', '"', '\'')
            val p2 = line.substring(colonIdx + 1).trim().trim('"', '\'')
            return resolveWordAndClue(p1, p2)
        }

        // Verifica ' - ' o ' – ' o ' — '
        val dashMatch = Regex("""\s+[-–—]\s+""").find(line)
        if (dashMatch != null) {
            val p1 = line.substring(0, dashMatch.range.first).trim().trim('*', '"', '\'')
            val p2 = line.substring(dashMatch.range.last + 1).trim().trim('"', '\'')
            return resolveWordAndClue(p1, p2)
        }

        // Verifica '='
        val eqIdx = line.indexOf('=')
        if (eqIdx > 0) {
            val p1 = line.substring(0, eqIdx).trim().trim('*', '"', '\'')
            val p2 = line.substring(eqIdx + 1).trim().trim('"', '\'')
            return resolveWordAndClue(p1, p2)
        }

        // Verifica '|'
        val pipeIdx = line.indexOf('|')
        if (pipeIdx > 0) {
            val p1 = line.substring(0, pipeIdx).trim().trim('*', '"', '\'')
            val p2 = line.substring(pipeIdx + 1).trim().trim('"', '\'')
            return resolveWordAndClue(p1, p2)
        }

        // Verifica tab '\t'
        val tabIdx = line.indexOf('\t')
        if (tabIdx > 0) {
            val p1 = line.substring(0, tabIdx).trim()
            val p2 = line.substring(tabIdx + 1).trim()
            return resolveWordAndClue(p1, p2)
        }

        // Verifica domanda '?'
        val qIdx = line.indexOf('?')
        if (qIdx > 0 && qIdx < line.length - 1) {
            val question = line.substring(0, qIdx + 1).trim()
            val answer = line.substring(qIdx + 1).trim()
            return resolveWordAndClue(answer, question)
        }

        // Verifica punto e virgola ';'
        val semiIdx = line.indexOf(';')
        if (semiIdx > 0) {
            val p1 = line.substring(0, semiIdx).trim()
            val p2 = line.substring(semiIdx + 1).trim()
            return resolveWordAndClue(p1, p2)
        }

        return null
    }

    private fun resolveWordAndClue(p1: String, p2: String): Pair<String, String> {
        val norm2 = WordClue.normalize(p2)
        val p1Words = p1.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        val p2Words = p2.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }

        // Se p1 ha più parole ed è una domanda/frase e p2 è una sola parola compatta valida, inverti!
        return if (p1Words.size > 1 && p2Words.size == 1 && norm2.length >= 2) {
            p2 to p1
        } else {
            p1 to p2
        }
    }
}

