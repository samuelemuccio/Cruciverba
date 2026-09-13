package com.samuele.crossword.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun MaskEditor(
    rows: Int,
    cols: Int,
    mask: Array<BooleanArray>,
    onMaskChange: (Array<BooleanArray>) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentValue by remember { mutableStateOf(true) }
    var lastToggled by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Canvas(modifier = modifier
        .fillMaxWidth()
        .aspectRatio(cols.toFloat() / rows)
        .pointerInput(rows, cols, mask) {
            detectTapGestures { offset ->
                val cell = size.width / cols
                val c = (offset.x / cell).toInt()
                val r = (offset.y / cell).toInt()
                if (r in 0 until rows && c in 0 until cols) {
                    val copy = Array(rows) { mask[it].clone() }
                    copy[r][c] = !copy[r][c]
                    onMaskChange(copy)
                }
            }
        }
        .pointerInput(rows, cols, mask) {
            detectDragGestures(
                onDragStart = { offset ->
                    val cell = size.width / cols
                    val c = (offset.x / cell).toInt()
                    val r = (offset.y / cell).toInt()
                    if (r in 0 until rows && c in 0 until cols) {
                        currentValue = !mask[r][c]
                        val copy = Array(rows) { mask[it].clone() }
                        copy[r][c] = currentValue
                        onMaskChange(copy)
                        lastToggled = r to c
                    }
                },
                onDrag = { change, _ ->
                    val cell = size.width / cols
                    val c = (change.position.x / cell).toInt()
                    val r = (change.position.y / cell).toInt()
                    if (r in 0 until rows && c in 0 until cols && (r to c) != lastToggled) {
                        val copy = Array(rows) { mask[it].clone() }
                        copy[r][c] = currentValue
                        onMaskChange(copy)
                        lastToggled = r to c
                    }
                }
            )
        }
    ) {
        val cell = size.width / cols
        for (r in 0 until rows) for (c in 0 until cols) {
            val topLeft = Offset(c * cell, r * cell)
            val sz = Size(cell, cell)
            drawRect(
                if (mask[r][c]) Color.White else Color.Black,
                topLeft, sz
            )
            drawRect(
                Color.Black, topLeft, sz,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)
            )
        }
    }
}
