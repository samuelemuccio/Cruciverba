package com.samuele.crossword.engine

/**
 * Numerazione standard cruciverba:
 * scan top-left → bottom-right. Incrementa il contatore quando una cella
 * è inizio di almeno uno slot (across o down) di lunghezza >= 2.
 */
object Numbering {
    fun assignNumbers(grid: CrosswordGrid, slots: List<Slot>): List<Slot> {
        // Mappa cella flat -> true se è inizio di uno slot
        val starts = HashSet<Int>()
        for (s in slots) starts += s.cells.first()

        val numberByStart = HashMap<Int, Int>()
        var counter = 1
        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                if (!grid.isWhite(r, c)) continue
                val flat = r * grid.cols + c
                if (flat in starts) numberByStart[flat] = counter++
            }
        }
        return slots.map { s ->
            s.copy(number = numberByStart[s.cells.first()] ?: 0)
        }
    }
}
