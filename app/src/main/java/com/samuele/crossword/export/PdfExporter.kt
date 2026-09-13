package com.samuele.crossword.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.samuele.crossword.engine.*
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    private const val A4_W = 595
    private const val A4_H = 842

    data class Options(
        val theme: CrosswordTheme = CrosswordTheme.Paper,
        val includeSolution: Boolean = true,
        val dpi: Int = 300,
        val title: String = "CRUCIVERBA",
        val authorLine: String? = null,
        val separateCluePages: Boolean = true
    )

    fun export(
        ctx: Context,
        result: CrosswordResult,
        outputFile: File,
        options: Options = Options()
    ) {
        val doc = PdfDocument()
        var pageNum = 1

        if (options.separateCluePages) {
            // Pagina 1: solo griglia + titolo
            drawPuzzlePage(doc, pageNum++, result, options, showLetters = false)
            // Pagina 2: solo definizioni
            drawCluesPage(doc, pageNum++, result, options)
            // Pagina 3: soluzione (opzionale)
            if (options.includeSolution) {
                drawPuzzlePage(doc, pageNum++, result, options, showLetters = true)
            }
        } else {
            // Layout compatto: griglia + definizioni sotto
            drawCompactPage(doc, pageNum++, result, options, showLetters = false)
            if (options.includeSolution) {
                drawCompactPage(doc, pageNum++, result, options, showLetters = true)
            }
        }

        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()
    }

    /** Overload di retrocompatibilità */
    fun export(
        ctx: Context,
        result: CrosswordResult,
        outputFile: File,
        includeSolutions: Boolean
    ) {
        export(ctx, result, outputFile, Options(includeSolution = includeSolutions))
    }

    // ---------- PAGINA 1: SOLO GRIGLIA ----------
    private fun drawPuzzlePage(
        doc: PdfDocument, pageNum: Int,
        result: CrosswordResult, opt: Options, showLetters: Boolean
    ) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, pageNum).create())
        val canvas = page.canvas
        val theme = opt.theme

        canvas.drawColor(theme.background.toInt())

        // Titolo grande centrato
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.headerColor.toInt()
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(theme.fontFamily, Typeface.BOLD)
        }
        val displayTitle = if (showLetters) "${opt.title} — SOLUZIONE" else opt.title
        canvas.drawText(displayTitle, A4_W / 2f, 70f, titlePaint)

        // Sottotitolo opzionale
        opt.authorLine?.let {
            val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.clueTextColor.toInt()
                textSize = 13f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(theme.fontFamily, Typeface.NORMAL)
            }
            canvas.drawText(it, A4_W / 2f, 92f, sub)
        }

        // Calcola dimensione griglia per riempire A4 con margini generosi
        val margin = 50f
        val availW = A4_W - 2 * margin
        val availH = A4_H - margin - 150f
        val cell = minOf(availW / result.cols, availH / result.rows).coerceAtMost(50f)
        val gridW = cell * result.cols
        val gridH = cell * result.rows
        val ox = (A4_W - gridW) / 2f
        val oy = 120f + (availH - gridH) / 2f

        drawGrid(canvas, result, theme, ox, oy, cell, showLetters)

        // Footer con numero pagina
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.clueTextColor.toInt()
            textSize = 10f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(theme.fontFamily, Typeface.NORMAL)
        }
        canvas.drawText("Pagina $pageNum", A4_W - margin, A4_H - 30f, foot)

        doc.finishPage(page)
    }

    // ---------- PAGINA 2: SOLO DEFINIZIONI ----------
    private fun drawCluesPage(
        doc: PdfDocument, pageNum: Int,
        result: CrosswordResult, opt: Options
    ) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, pageNum).create())
        val canvas = page.canvas
        val theme = opt.theme
        canvas.drawColor(theme.background.toInt())

        val margin = 45f
        val gap = 30f
        val colW = (A4_W - 2 * margin - gap) / 2f

        // Titolo pagina definizioni
        val pageTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.headerColor.toInt()
            textSize = 24f
            typeface = Typeface.create(theme.fontFamily, Typeface.BOLD)
        }
        canvas.drawText("DEFINIZIONI", margin, 60f, pageTitlePaint)

        val across = result.slots.filter { it.direction == Direction.ACROSS && it.number > 0 }
            .sortedBy { it.number }
        val down = result.slots.filter { it.direction == Direction.DOWN && it.number > 0 }
            .sortedBy { it.number }

        // Colonna sinistra: ORIZZONTALI
        val startY = 95f
        drawClueSection(canvas, "ORIZZONTALI", across, result, theme, margin, startY, colW)

        // Colonna destra: VERTICALI
        drawClueSection(canvas, "VERTICALI", down, result, theme, margin + colW + gap, startY, colW)

        // Footer
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.clueTextColor.toInt()
            textSize = 10f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(theme.fontFamily, Typeface.NORMAL)
        }
        canvas.drawText("Pagina $pageNum", A4_W - margin, A4_H - 30f, foot)

        doc.finishPage(page)
    }

    private fun drawClueSection(
        canvas: Canvas, title: String,
        slots: List<Slot>, result: CrosswordResult, theme: CrosswordTheme,
        x: Float, startY: Float, maxW: Float
    ): Float {
        var y = startY

        // Titolo sezione
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.headerColor.toInt()
            textSize = 16f
            typeface = Typeface.create(theme.fontFamily, Typeface.BOLD)
        }
        canvas.drawText(title, x, y, titlePaint)
        y += 8f

        // Linea decorativa
        val linePaint = Paint().apply {
            color = theme.gridLine.toInt()
            strokeWidth = 1.2f
        }
        canvas.drawLine(x, y, x + maxW, y, linePaint)
        y += 20f

        // Voci
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.numberColor.toInt()
            textSize = 11f
            typeface = Typeface.create(theme.fontFamily, Typeface.BOLD)
        }
        val cluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.clueTextColor.toInt()
            textSize = 10.5f
            typeface = Typeface.create(theme.fontFamily, Typeface.NORMAL)
        }
        val lengthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.clueTextColor.toInt()
            textSize = 9.5f
            typeface = Typeface.create(theme.fontFamily, Typeface.ITALIC)
        }

        slots.forEach { slot ->
            val clue = result.assignments[slot.id]?.clue ?: return@forEach
            val numText = "${slot.number}."
            canvas.drawText(numText, x, y, numPaint)
            val numW = numPaint.measureText(numText)
            val clueStartX = x + numW + 5f

            // Calcolo larghezza disponibile per testo definizione
            val lenText = "(${slot.length})"
            val lenW = lengthPaint.measureText(lenText)
            val availTextW = maxW - numW - 5f - lenW - 8f

            val lines = wrapText(clue, cluePaint, availTextW)
            lines.forEachIndexed { i, line ->
                val currX = if (i == 0) clueStartX else clueStartX + 4f
                canvas.drawText(line, currX, y, cluePaint)
                if (i < lines.lastIndex) y += 14f
            }

            // Lunghezza a destra della prima riga
            canvas.drawText(lenText, x + maxW - lenW, y, lengthPaint)

            y += 18f
        }
        return y
    }

    private fun wrapText(text: String, paint: Paint, maxW: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (w in words) {
            val test = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(test) <= maxW) {
                current.clear()
                current.append(test)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current.clear()
                current.append(w)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return if (lines.isEmpty()) listOf(text) else lines
    }

    // ---------- GRIGLIA ----------
    private fun drawGrid(
        canvas: Canvas, result: CrosswordResult, theme: CrosswordTheme,
        ox: Float, oy: Float, cell: Float, showLetters: Boolean
    ) {
        val whitePaint = Paint().apply { color = theme.cellWhite.toInt() }
        val blackPaint = Paint().apply { color = theme.cellBlack.toInt() }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.gridLine.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val outerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.gridLine.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Celle
        for (r in 0 until result.rows) {
            for (c in 0 until result.cols) {
                val l = ox + c * cell
                val t = oy + r * cell
                if (result.whiteCells[r][c]) {
                    canvas.drawRect(l, t, l + cell, t + cell, whitePaint)
                    canvas.drawRect(l, t, l + cell, t + cell, linePaint)
                } else {
                    canvas.drawRect(l, t, l + cell, t + cell, blackPaint)
                }
            }
        }

        // Bordo esterno
        canvas.drawRect(ox, oy, ox + result.cols * cell, oy + result.rows * cell, outerLinePaint)

        // Numeri
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.numberColor.toInt()
            textSize = cell * 0.24f
            typeface = Typeface.create(theme.fontFamily, Typeface.BOLD)
        }
        result.slots.filter { it.number > 0 }.forEach { slot ->
            val r = slot.cells.first() / result.cols
            val c = slot.cells.first() % result.cols
            val x = ox + c * cell + cell * 0.08f
            val y = oy + r * cell + cell * 0.26f
            canvas.drawText(slot.number.toString(), x, y, numPaint)
        }

        // Lettere (se soluzione attiva)
        if (showLetters) {
            val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.letterColor.toInt()
                textSize = cell * 0.55f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(theme.fontFamily, Typeface.BOLD)
            }
            for (r in 0 until result.rows) {
                for (c in 0 until result.cols) {
                    val ch = result.letters[r][c]
                    if (ch == ' ') continue
                    val x = ox + c * cell + cell / 2f
                    val y = oy + r * cell + cell * 0.72f
                    canvas.drawText(ch.toString(), x, y, letterPaint)
                }
            }
        }
    }

    // ---------- LAYOUT COMPATTO ----------
    private fun drawCompactPage(
        doc: PdfDocument, pageNum: Int,
        result: CrosswordResult, opt: Options, showLetters: Boolean
    ) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, pageNum).create())
        val canvas = page.canvas
        val theme = opt.theme
        canvas.drawColor(theme.background.toInt())

        val margin = 35f
        val availW = A4_W - 2 * margin
        val availH = A4_H * 0.50f
        val cell = minOf(availW / result.cols, availH / result.rows).coerceAtMost(36f)
        val gridW = cell * result.cols
        val gridH = cell * result.rows
        val ox = (A4_W - gridW) / 2f
        val oy = 40f

        drawGrid(canvas, result, theme, ox, oy, cell, showLetters)

        val cluesY = oy + gridH + 30f
        val colW = (A4_W - 2 * margin - 20f) / 2f
        val across = result.slots.filter { it.direction == Direction.ACROSS && it.number > 0 }.sortedBy { it.number }
        val down = result.slots.filter { it.direction == Direction.DOWN && it.number > 0 }.sortedBy { it.number }

        drawClueSection(canvas, "ORIZZONTALI", across, result, theme, margin, cluesY, colW)
        drawClueSection(canvas, "VERTICALI", down, result, theme, margin + colW + 20f, cluesY, colW)

        doc.finishPage(page)
    }
}
