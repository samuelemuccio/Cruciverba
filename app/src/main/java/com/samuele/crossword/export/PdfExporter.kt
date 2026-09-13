package com.samuele.crossword.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.Direction
import java.io.File
import java.io.FileOutputStream

object PdfExporter {
    private const val A4_W = 595
    private const val A4_H = 842

    fun export(
        context: Context,
        result: CrosswordResult,
        outputFile: File,
        includeSolutions: Boolean
    ) {
        val doc = PdfDocument()
        var pageNumber = 1
        // Pagina 1: puzzle
        drawPuzzlePage(doc, pageNumber++, result, showLetters = false)
        // Pagina 2: soluzioni
        if (includeSolutions) {
            drawPuzzlePage(doc, pageNumber++, result, showLetters = true)
        }
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()
    }

    private fun drawPuzzlePage(
        doc: PdfDocument,
        pageNumber: Int,
        result: CrosswordResult,
        showLetters: Boolean
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(A4_W, A4_H, pageNumber).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val margin = 40f
        val availW = A4_W - 2 * margin
        val availH = A4_H * 0.55f
        val cell = minOf(availW / result.cols, availH / result.rows)
        val gridW = cell * result.cols
        val gridH = cell * result.rows
        val offsetX = (A4_W - gridW) / 2f
        val offsetY = margin + 20f

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.BLACK
        }
        val fillBlack = Paint().apply { color = Color.BLACK }
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = cell * 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = cell * 0.55f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val cluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = 10f
        }

        canvas.drawText(
            if (showLetters) "SOLUZIONI" else "CRUCIVERBA",
            margin, margin, titlePaint
        )

        // Griglia
        for (r in 0 until result.rows) for (c in 0 until result.cols) {
            val l = offsetX + c * cell
            val t = offsetY + r * cell
            if (!result.whiteCells[r][c]) {
                canvas.drawRect(l, t, l + cell, t + cell, fillBlack)
            } else {
                canvas.drawRect(l, t, l + cell, t + cell, stroke)
            }
        }
        // Numeri
        result.slots.filter { it.number > 0 }.forEach { slot ->
            val r = slot.cells.first() / result.cols
            val c = slot.cells.first() % result.cols
            val l = offsetX + c * cell
            val t = offsetY + r * cell
            canvas.drawText(slot.number.toString(), l + cell * 0.06f, t + cell * 0.24f, numberPaint)
        }
        // Lettere
        if (showLetters) {
            for (r in 0 until result.rows) for (c in 0 until result.cols) {
                val ch = result.letters[r][c]
                if (ch == ' ') continue
                val cx = offsetX + c * cell + cell / 2
                val cy = offsetY + r * cell + cell * 0.72f
                canvas.drawText(ch.toString(), cx, cy, letterPaint)
            }
        }

        // Definizioni
        if (!showLetters) {
            var y = offsetY + gridH + 24f
            val across = result.slots.filter { it.direction == Direction.ACROSS && it.number > 0 }
                .sortedBy { it.number }
            val down = result.slots.filter { it.direction == Direction.DOWN && it.number > 0 }
                .sortedBy { it.number }
            canvas.drawText("ORIZZONTALI", margin, y, titlePaint); y += 14f
            across.forEach { s ->
                val clue = result.assignments[s.id]?.clue ?: "—"
                canvas.drawText("${s.number}. $clue (${s.length})", margin, y, cluePaint)
                y += 12f
            }
            y += 8f
            canvas.drawText("VERTICALI", margin, y, titlePaint); y += 14f
            down.forEach { s ->
                val clue = result.assignments[s.id]?.clue ?: "—"
                canvas.drawText("${s.number}. $clue (${s.length})", margin, y, cluePaint)
                y += 12f
            }
        }

        doc.finishPage(page)
    }
}
