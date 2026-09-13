package com.samuele.crossword.export

import android.content.Context
import android.os.Environment
import com.samuele.crossword.engine.CrosswordResult
import java.io.File

enum class ExportFormat { PNG, PDF, SVG }

object ExportManager {
    fun exportFolder(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "crosswords")
            .apply { if (!exists()) mkdirs() }

    fun export(
        context: Context,
        result: CrosswordResult,
        format: ExportFormat,
        name: String,
        pngScale: Int = 4,
        includeSolutions: Boolean = true
    ): File {
        val folder = exportFolder(context)
        val safe = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return when (format) {
            ExportFormat.PNG -> {
                val puzzle = File(folder, "${safe}_puzzle.png")
                PngExporter.export(context, result, pngScale, false, puzzle)
                if (includeSolutions) {
                    PngExporter.export(context, result, pngScale, true,
                        File(folder, "${safe}_soluzione.png"))
                }
                puzzle
            }
            ExportFormat.PDF -> {
                val f = File(folder, "$safe.pdf")
                PdfExporter.export(context, result, f, includeSolutions)
                f
            }
            ExportFormat.SVG -> {
                val puzzle = File(folder, "${safe}_puzzle.svg")
                SvgExporter.export(result, puzzle, false)
                if (includeSolutions) {
                    val solution = File(folder, "${safe}_soluzione.svg")
                    SvgExporter.export(result, solution, true)
                }
                puzzle
            }
        }
    }
}
