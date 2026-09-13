package com.samuele.crossword.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.CrosswordTheme

@Composable
fun CrosswordGridView(
    result: CrosswordResult,
    theme: CrosswordTheme = CrosswordTheme.Paper,
    showSolution: Boolean = true,
    modifier: Modifier = Modifier,
    onCellTap: ((Int, Int) -> Unit)? = null
) {
    Canvas(
        modifier = modifier
            .aspectRatio(result.cols.toFloat() / result.rows)
            .pointerInput(result) {
                if (onCellTap != null) {
                    detectTapGestures { offset ->
                        val cell = size.width / result.cols
                        val c = (offset.x / cell).toInt()
                        val r = (offset.y / cell).toInt()
                        if (r in 0 until result.rows && c in 0 until result.cols) {
                            onCellTap(r, c)
                        }
                    }
                }
            }
    ) {
        val cell = size.width / result.cols
        val innerStroke = 1.2f
        val borderStroke = 2.5f

        // 1. Sfondo del cruciverba
        drawRect(Color(theme.background), size = size)

        // 2. Celle bianche e nere
        for (r in 0 until result.rows) {
            for (c in 0 until result.cols) {
                val topLeft = Offset(c * cell, r * cell)
                val cellSize = Size(cell, cell)
                val isWhite = result.whiteCells[r][c]
                drawRect(
                    if (isWhite) Color(theme.cellWhite) else Color(theme.cellBlack),
                    topLeft,
                    cellSize
                )
                if (isWhite) {
                    drawRect(
                        Color(theme.gridLine),
                        topLeft,
                        cellSize,
                        style = Stroke(innerStroke)
                    )
                }
            }
        }

        // Bordo esterno rifinito tipografico
        drawRect(
            Color(theme.gridLine),
            Offset.Zero,
            size = Size(result.cols * cell, result.rows * cell),
            style = Stroke(borderStroke)
        )

        val fontTypeface = try {
            Typeface.create(theme.fontFamily, Typeface.BOLD)
        } catch (_: Throwable) {
            Typeface.DEFAULT_BOLD
        }

        // 3. Numeri dei quesiti negli angoli superiori delle celle
        val numberPaint = Paint().apply {
            color = theme.numberColor.toInt()
            textSize = cell * 0.26f
            isAntiAlias = true
            typeface = fontTypeface
        }

        drawContext.canvas.nativeCanvas.apply {
            result.slots.filter { it.number > 0 }.forEach { slot ->
                val r = slot.cells.first() / result.cols
                val c = slot.cells.first() % result.cols
                drawText(
                    slot.number.toString(),
                    c * cell + cell * 0.08f,
                    r * cell + cell * 0.27f,
                    numberPaint
                )
            }

            // 4. Lettere della soluzione al centro
            if (showSolution) {
                val letterPaint = Paint().apply {
                    color = theme.letterColor.toInt()
                    textSize = cell * 0.54f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = fontTypeface
                }
                for (r in 0 until result.rows) {
                    for (c in 0 until result.cols) {
                        val ch = result.letters[r][c]
                        if (ch == ' ') continue
                        drawText(
                            ch.toString(),
                            c * cell + cell / 2f,
                            r * cell + cell * 0.70f,
                            letterPaint
                        )
                    }
                }
            }
        }
    }
}
