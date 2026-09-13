package com.samuele.crossword.export

import android.content.Context
import android.graphics.*
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.Direction
import java.io.File
import java.io.FileOutputStream

object PngExporter {

    /**
     * @param scale moltiplicatore risoluzione (es. 4 → 4x).
     * @param showSolution se true disegna le lettere, altrimenti solo i numeri.
     */
    fun export(
        context: Context,
        result: CrosswordResult,
        scale: Int,
        showSolution: Boolean,
        outputFile: File
    ) {
        val baseCell = 60f
        val cell = baseCell * scale
        val padding = 40f * scale
        val gridW = padding * 2 + result.cols * cell
        val gridH = padding * 2 + result.rows * cell
        val cluesH = if (!showSolution) 300f * scale else 0f
        val totalH = gridH + cluesH
        val bmp = Bitmap.createBitmap(
            gridW.toInt(), totalH.toInt(), Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f * scale
        }
        val fillBlack = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = cell * 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = cell * 0.55f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val clueTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 20f * scale
            typeface = Typeface.DEFAULT_BOLD
        }
        val cluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = 16f * scale
        }

        // Griglia
        for (r in 0 until result.rows) {
            for (c in 0 until result.cols) {
                val left = padding + c * cell
                val top = padding + r * cell
                val right = left + cell
                val bottom = top + cell
                if (!result.whiteCells[r][c]) {
                    canvas.drawRect(left, top, right, bottom, fillBlack)
                } else {
                    canvas.drawRect(left, top, right, bottom, linePaint)
                }
            }
        }
        // Numeri
        result.slots.filter { it.number > 0 }.forEach { slot ->
            val r = slot.cells.first() / result.cols
            val c = slot.cells.first() % result.cols
            val left = padding + c * cell
            val top = padding + r * cell
            canvas.drawText(slot.number.toString(), left + cell * 0.06f, top + cell * 0.24f, numberPaint)
        }
        // Lettere (solo soluzione)
        if (showSolution) {
            for (r in 0 until result.rows) for (c in 0 until result.cols) {
                val ch = result.letters[r][c]
                if (ch == ' ') continue
                val cx = padding + c * cell + cell / 2
                val cy = padding + r * cell + cell * 0.72f
                canvas.drawText(ch.toString(), cx, cy, letterPaint)
            }
        } else {
            // Definizioni sotto
            var y = gridH + 30f * scale
            val across = result.slots.filter { it.direction == Direction.ACROSS && it.number > 0 }
                .sortedBy { it.number }
            val down = result.slots.filter { it.direction == Direction.DOWN && it.number > 0 }
                .sortedBy { it.number }

            canvas.drawText("ORIZZONTALI", padding, y, clueTitlePaint); y += 22f * scale
            across.forEach { s ->
                val clue = result.assignments[s.id]?.clue ?: "—"
                canvas.drawText("${s.number}. $clue (${s.length})", padding, y, cluePaint)
                y += 20f * scale
            }
            y += 12f * scale
            canvas.drawText("VERTICALI", padding, y, clueTitlePaint); y += 22f * scale
            down.forEach { s ->
                val clue = result.assignments[s.id]?.clue ?: "—"
                canvas.drawText("${s.number}. $clue (${s.length})", padding, y, cluePaint)
                y += 20f * scale
            }
        }

        FileOutputStream(outputFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }
}
