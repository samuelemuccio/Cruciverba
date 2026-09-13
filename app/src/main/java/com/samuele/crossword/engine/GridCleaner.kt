package com.samuele.crossword.engine

object GridCleaner {

    /**
     * Trasforma in nere le celle bianche "orfane":
     * quelle che non fanno parte di una parola orizzontale o verticale
     * di lunghezza >= 2.
     */
    fun clean(grid: BuiltCrossword): BuiltCrossword {
        val white = Array(grid.rows) { grid.whiteCells[it].clone() }
        var changed = true
        while (changed) {
            changed = false
            for (r in 0 until grid.rows) {
                for (c in 0 until grid.cols) {
                    if (!white[r][c]) continue
                    val inAcross = isInSlot(white, r, c, Direction.ACROSS)
                    val inDown = isInSlot(white, r, c, Direction.DOWN)
                    if (!inAcross && !inDown) {
                        white[r][c] = false
                        changed = true
                    }
                }
            }
        }
        return grid.copy(whiteCells = white)
    }

    private fun isInSlot(white: Array<BooleanArray>, r: Int, c: Int, dir: Direction): Boolean {
        val rows = white.size
        val cols = white[0].size
        var len = 1
        if (dir == Direction.ACROSS) {
            var cc = c - 1
            while (cc >= 0 && white[r][cc]) { len++; cc-- }
            cc = c + 1
            while (cc < cols && white[r][cc]) { len++; cc++ }
        } else {
            var rr = r - 1
            while (rr >= 0 && white[rr][c]) { len++; rr-- }
            rr = r + 1
            while (rr < rows && white[rr][c]) { len++; rr++ }
        }
        return len >= 2
    }

    /**
     * Dopo la pulizia, alcune parole piazzate potrebbero essere incomplete o rotte → le rimuove.
     */
    fun removeBrokenWords(grid: BuiltCrossword): BuiltCrossword {
        val stillValid = grid.placed.filter { pw ->
            val cells = pw.cells()
            cells.indices.all { idx ->
                val (r, c) = cells[idx]
                r in 0 until grid.rows && c in 0 until grid.cols &&
                        grid.whiteCells[r][c] &&
                        grid.letters[r][c] == pw.word[idx]
            }
        }
        return grid.copy(placed = stillValid)
    }
}
