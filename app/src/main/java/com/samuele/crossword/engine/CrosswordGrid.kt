package com.samuele.crossword.engine

class CrosswordGrid(
    val rows: Int,
    val cols: Int,
    mask: Array<BooleanArray>? = null
) {
    val whiteCells: Array<BooleanArray> =
        mask ?: Array(rows) { BooleanArray(cols) { true } }

    init {
        require(whiteCells.size == rows && whiteCells.all { it.size == cols }) {
            "Mask size must be $rows x $cols"
        }
    }

    fun isWhite(r: Int, c: Int): Boolean =
        r in 0 until rows && c in 0 until cols && whiteCells[r][c]

    /** Estrae slot con celle precalcolate (flat index r*cols + c). */
    fun extractSlots(): List<Slot> {
        val out = ArrayList<Slot>()
        var id = 0
        // ACROSS
        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                if (isWhite(r, c) && (c == 0 || !isWhite(r, c - 1))) {
                    var len = 0
                    while (c + len < cols && isWhite(r, c + len)) len++
                    if (len >= 2) {
                        val cells = IntArray(len) { i -> r * cols + (c + i) }
                        out += Slot(id++, r, c, len, Direction.ACROSS, cells)
                    }
                    c += maxOf(len, 1)
                } else c++
            }
        }
        // DOWN
        for (c in 0 until cols) {
            var r = 0
            while (r < rows) {
                if (isWhite(r, c) && (r == 0 || !isWhite(r - 1, c))) {
                    var len = 0
                    while (r + len < rows && isWhite(r + len, c)) len++
                    if (len >= 2) {
                        val cells = IntArray(len) { i -> (r + i) * cols + c }
                        out += Slot(id++, r, c, len, Direction.DOWN, cells)
                    }
                    r += maxOf(len, 1)
                } else r++
            }
        }
        return out
    }
}
