package com.samuele.crossword.export

import android.content.Context
import android.net.Uri
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
        includeSolutions: Boolean = true,
        transparentCells: Boolean = true
    ): File {
        val folder = exportFolder(context)
        val safe = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return when (format) {
            ExportFormat.PNG -> {
                val puzzle = File(folder, "${safe}_puzzle.png")
                PngExporter.export(
                    context = context,
                    result = result,
                    scale = pngScale,
                    showSolution = false,
                    outputFile = puzzle,
                    transparentCells = transparentCells
                )
                if (includeSolutions) {
                    val solution = File(folder, "${safe}_soluzione.png")
                    PngExporter.export(
                        context = context,
                        result = result,
                        scale = pngScale,
                        showSolution = true,
                        outputFile = solution,
                        transparentCells = transparentCells
                    )
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

    /**
     * Salva direttamente nella Galleria del dispositivo (Album "Cruciverba").
     * Ritorna la lista degli URI salvati (schema + eventuale soluzione).
     */
    fun saveToGallery(
        context: Context,
        result: CrosswordResult,
        name: String,
        pngScale: Int = 4,
        includeSolutions: Boolean = false,
        transparentCells: Boolean = true
    ): List<Uri> {
        val safe = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val savedUris = mutableListOf<Uri>()

        val puzzleUri = PngExporter.exportToGallery(
            context = context,
            result = result,
            scale = pngScale,
            showSolution = false,
            title = safe,
            transparentCells = transparentCells
        )
        if (puzzleUri != null) {
            savedUris += puzzleUri
        }

        if (includeSolutions) {
            val solutionUri = PngExporter.exportToGallery(
                context = context,
                result = result,
                scale = pngScale,
                showSolution = true,
                title = safe,
                transparentCells = transparentCells
            )
            if (solutionUri != null) {
                savedUris += solutionUri
            }
        }

        return savedUris
    }
}
