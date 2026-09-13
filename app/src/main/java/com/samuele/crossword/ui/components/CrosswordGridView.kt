package com.samuele.crossword.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.samuele.crossword.engine.CrosswordResult

@Composable
fun CrosswordGridView(
    result: CrosswordResult,
    modifier: Modifier = Modifier,
    showSolution: Boolean = true,
    onCellTap: ((Int, Int) -> Unit)? = null
) {
    Canvas(
        modifier = modifier
            .aspectRatio(result.cols.toFloat() / result.rows)
            .pointerInput(Unit) {
                if (onCellTap != null) detectTapGestures { offset ->
                    val cell = size.width / result.cols
                    val c = (offset.x / cell).toInt()
                    val r = (offset.y / cell).toInt()
                    if (r in 0 until result.rows && c in 0 until result.cols) onCellTap(r, c)
                }
            }
    ) {
        drawCrossword(result, showSolution, this)
    }
}

private fun drawCrossword(
    result: CrosswordResult,
    showSolution: Boolean,
    scope: DrawScope
) {
    val cell = scope.size.width / result.cols
    val stroke = 1.5f

    for (r in 0 until result.rows) for (c in 0 until result.cols) {
        val topLeft = Offset(c * cell, r * cell)
        val sz = Size(cell, cell)
        if (!result.whiteCells[r][c]) {
            scope.drawRect(Color.Black, topLeft, sz)
        } else {
            scope.drawRect(Color.White, topLeft, sz)
            scope.drawRect(Color.Black, topLeft, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
    }
    val numberPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = cell * 0.22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val letterPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = cell * 0.55f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    scope.drawContext.canvas.nativeCanvas.apply {
        result.slots.filter { it.number > 0 }.forEach { slot ->
            val r = slot.cells.first() / result.cols
            val c = slot.cells.first() % result.cols
            drawText(slot.number.toString(), c * cell + cell * 0.06f, r * cell + cell * 0.24f, numberPaint)
        }
        if (showSolution) {
            for (r in 0 until result.rows) for (c in 0 until result.cols) {
                val ch = result.letters[r][c]
                if (ch == ' ') continue
                drawText(ch.toString(), c * cell + cell / 2f, r * cell + cell * 0.72f, letterPaint)
            }
        }
    }
}
