package com.samuele.crossword.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import com.samuele.crossword.engine.CrosswordResult
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class PngExportOptions(
    val scale: Int = 4,
    val showSolution: Boolean = false,
    val transparentCells: Boolean = true,
    val baseCellSize: Float = 80f,
    val strokeWidthScale: Float = 1.0f
)

object PngExporter {

    /**
     * Esegue il rendering della griglia del cruciverba ad altissima risoluzione,
     * generando un'immagine PNG trasparente contenente ESCLUSIVAMENTE i quadrati del
     * cruciverba con i loro bordi e i rispettivi numeretti (senza alcuno sfondo, testo esterno
     * né definizioni), ideale per essere importata e ricalcata su Canva o altri editor.
     */
    fun render(
        result: CrosswordResult,
        options: PngExportOptions = PngExportOptions()
    ): Bitmap {
        val cell = options.baseCellSize * options.scale
        val padding = 12f * options.scale
        val totalW = (padding * 2 + result.cols * cell).toInt()
        val totalH = (padding * 2 + result.rows * cell).toInt()

        // Bitmap ARGB_8888 trasparente (nessun colore di sfondo impostato)
        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val fillWhite = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(2.5f, 2.8f * options.scale * options.strokeWidthScale)
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.MITER
        }

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = cell * 0.23f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = cell * 0.55f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Mappa delle celle numerate (elimina duplicati tra orizzontali e verticali)
        val numberedCells = HashMap<Pair<Int, Int>, Int>()
        for (slot in result.slots) {
            if (slot.number > 0 && slot.cells.isNotEmpty()) {
                val r = slot.cells.first() / result.cols
                val c = slot.cells.first() % result.cols
                if ((r to c) !in numberedCells) {
                    numberedCells[r to c] = slot.number
                }
            }
        }

        // Coordinate delle celle appartenenti alla parola cardine misteriosa
        val mysteryCells = HashSet<Pair<Int, Int>>()
        result.mysteryInfo?.let { minfo ->
            mysteryCells.addAll(minfo.cells)
        }

        val fillMystery = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(255, 251, 235) // Crema dorato
            style = Paint.Style.FILL
        }

        val mysteryDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(245, 158, 11) // Ambra
            style = Paint.Style.FILL
        }

        // Disegna SOLO i quadrati che appartengono al cruciverba (whiteCells == true)
        for (r in 0 until result.rows) {
            for (c in 0 until result.cols) {
                if (result.whiteCells[r][c]) {
                    val left = padding + c * cell
                    val top = padding + r * cell
                    val right = left + cell
                    val bottom = top + cell
                    val isMystery = (r to c) in mysteryCells

                    // Se richiesto lo sfondo delle celle
                    if (!options.transparentCells) {
                        if (isMystery) {
                            canvas.drawRect(left, top, right, bottom, fillMystery)
                        } else {
                            canvas.drawRect(left, top, right, bottom, fillWhite)
                        }
                    }

                    // Bordo netto del quadrato
                    canvas.drawRect(left, top, right, bottom, borderPaint)

                    // Se parola misteriosa, disegna un dischetto dorato discreto in alto a destra
                    if (isMystery) {
                        val dotR = (cell * 0.08f).coerceIn(2f, 8f)
                        canvas.drawCircle(right - cell * 0.16f, top + cell * 0.16f, dotR, mysteryDotPaint)
                    }

                    // Numeretto nell'angolo in alto a sinistra
                    val number = numberedCells[r to c]
                    if (number != null) {
                        val numX = left + cell * 0.08f
                        val numY = top + cell * 0.27f
                        canvas.drawText(number.toString(), numX, numY, numberPaint)
                    }

                    // Lettera della soluzione (se richiesta esplicitamente)
                    if (options.showSolution) {
                        val ch = result.letters[r][c]
                        if (ch != ' ') {
                            val cx = left + cell / 2f
                            val cy = top + cell * 0.70f
                            canvas.drawText(ch.toString(), cx, cy, letterPaint)
                        }
                    }
                }
            }
        }

        return bmp
    }

    /**
     * Esporta su file locale.
     */
    fun export(
        context: Context,
        result: CrosswordResult,
        scale: Int = 4,
        showSolution: Boolean = false,
        outputFile: File,
        transparentCells: Boolean = true
    ) {
        val options = PngExportOptions(
            scale = scale,
            showSolution = showSolution,
            transparentCells = transparentCells
        )
        val bmp = render(result, options)
        FileOutputStream(outputFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
    }

    /**
     * Esporta e salva direttamente nella Galleria del dispositivo (album Pictures/Cruciverba).
     */
    fun exportToGallery(
        context: Context,
        result: CrosswordResult,
        scale: Int = 4,
        showSolution: Boolean = false,
        title: String = "cruciverba",
        transparentCells: Boolean = true
    ): Uri? {
        val options = PngExportOptions(
            scale = scale,
            showSolution = showSolution,
            transparentCells = transparentCells
        )
        val bmp = render(result, options)
        val uri = GalleryExporter.saveToGallery(
            context = context,
            bitmap = bmp,
            title = if (showSolution) "${title}_soluzione" else "${title}_schema",
            subfolder = "Cruciverba"
        )
        bmp.recycle()
        return uri
    }
}
