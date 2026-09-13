package com.samuele.crossword.export

import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.Direction
import java.io.File

object SvgExporter {
    fun export(result: CrosswordResult, outputFile: File, showSolution: Boolean, cellSize: Int = 60) {
        val cols = result.cols; val rows = result.rows
        val pad = 40
        val gridW = cols * cellSize
        val gridH = rows * cellSize
        val cluesH = if (showSolution) 0 else 200 + 20 * result.slots.size
        val width = gridW + pad * 2
        val height = gridH + pad * 2 + cluesH

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">
<rect width="$width" height="$height" fill="white"/>
<style>
.cellRect { fill:none; stroke:black; stroke-width:1.5; }
.cellBlack { fill:black; }
.num { font-family: sans-serif; font-size:${cellSize * 0.22}px; font-weight:bold; fill:black; }
.letter { font-family: sans-serif; font-size:${cellSize * 0.55}px; font-weight:bold; fill:black; text-anchor:middle; dominant-baseline:middle; }
.title { font-family: sans-serif; font-size:22px; font-weight:bold; fill:black; }
.clue { font-family: sans-serif; font-size:15px; fill:#333; }
</style>
""")

        for (r in 0 until rows) for (c in 0 until cols) {
            val x = pad + c * cellSize
            val y = pad + r * cellSize
            if (!result.whiteCells[r][c]) {
                sb.append("""<rect class="cellBlack" x="$x" y="$y" width="$cellSize" height="$cellSize"/>""")
            } else {
                sb.append("""<rect class="cellRect" x="$x" y="$y" width="$cellSize" height="$cellSize"/>""")
            }
        }
        result.slots.filter { it.number > 0 }.forEach { s ->
            val r = s.cells.first() / cols
            val c = s.cells.first() % cols
            val x = pad + c * cellSize + cellSize * 0.06
            val y = pad + r * cellSize + cellSize * 0.24
            sb.append("""<text class="num" x="$x" y="$y">${s.number}</text>""")
        }
        if (showSolution) {
            for (r in 0 until rows) for (c in 0 until cols) {
                val ch = result.letters[r][c]
                if (ch == ' ') continue
                val x = pad + c * cellSize + cellSize / 2.0
                val y = pad + r * cellSize + cellSize / 2.0
                sb.append("""<text class="letter" x="$x" y="$y">$ch</text>""")
            }
        } else {
            var y = pad + gridH + 30
            val across = result.slots.filter { it.direction == Direction.ACROSS && it.number > 0 }.sortedBy { it.number }
            val down = result.slots.filter { it.direction == Direction.DOWN && it.number > 0 }.sortedBy { it.number }
            sb.append("""<text class="title" x="$pad" y="$y">ORIZZONTALI</text>"""); y += 22
            across.forEach { s ->
                val clue = escapeXml(result.assignments[s.id]?.clue ?: "—")
                sb.append("""<text class="clue" x="$pad" y="$y">${s.number}. $clue (${s.length})</text>""")
                y += 18
            }
            y += 12
            sb.append("""<text class="title" x="$pad" y="$y">VERTICALI</text>"""); y += 22
            down.forEach { s ->
                val clue = escapeXml(result.assignments[s.id]?.clue ?: "—")
                sb.append("""<text class="clue" x="$pad" y="$y">${s.number}. $clue (${s.length})</text>""")
                y += 18
            }
        }

        sb.append("</svg>")
        outputFile.writeText(sb.toString())
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
