package com.samuele.crossword.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
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
    // Mappa numeretti delle caselle (elimina duplicati tra orizzontali e verticali)
    val numberedCells = remember(result) {
        val map = HashMap<Pair<Int, Int>, Int>()
        for (slot in result.slots) {
            if (slot.number > 0 && slot.cells.isNotEmpty()) {
                val r = slot.cells.first() / result.cols
                val c = slot.cells.first() % result.cols
                if ((r to c) !in map) {
                    map[r to c] = slot.number
                }
            }
        }
        map
    }

    val mysteryCells = remember(result.mysteryInfo) {
        result.mysteryInfo?.cells?.toSet() ?: emptySet()
    }

    // Transizione pulita e discreta di opacità al cambio di variante (niente tessere che scivolano o sformano la griglia)
    val gridAlpha = remember { Animatable(1f) }
    LaunchedEffect(result) {
        gridAlpha.snapTo(0.5f)
        gridAlpha.animateTo(1f, animationSpec = tween(durationMillis = 180))
    }

    val fontTypeface = remember(theme) {
        try {
            Typeface.create(theme.fontFamily, Typeface.BOLD)
        } catch (_: Throwable) {
            Typeface.DEFAULT_BOLD
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(result.cols.toFloat() / result.rows.toFloat())
            .pointerInput(result) {
                if (onCellTap != null) {
                    detectTapGestures { offset ->
                        val cell = minOf(size.width / result.cols, size.height / result.rows)
                        val gridW = cell * result.cols
                        val gridH = cell * result.rows
                        val startX = (size.width - gridW) / 2f
                        val startY = (size.height - gridH) / 2f

                        val relX = offset.x - startX
                        val relY = offset.y - startY
                        if (relX >= 0 && relY >= 0) {
                            val c = (relX / cell).toInt()
                            val r = (relY / cell).toInt()
                            if (r in 0 until result.rows && c in 0 until result.cols) {
                                onCellTap(r, c)
                            }
                        }
                    }
                }
            }
    ) {
        val currentAlpha = gridAlpha.value

        // Calcolo perfetto della dimensione cella quadrata e centratura nel riquadro
        val cell = minOf(size.width / result.cols, size.height / result.rows)
        val gridW = cell * result.cols
        val gridH = cell * result.rows
        val startX = (size.width - gridW) / 2f
        val startY = (size.height - gridH) / 2f

        // 1. Sfondo del riquadro: rigorosamente scuro/obsidian (nessun bianco spurio sopra o sotto!)
        drawRect(
            color = Color(0xFF0B0C10),
            size = size
        )

        // Stili di disegno: RIGOROSAMENTE QUADRATI CON ANGOLI RETTI (90° netti, non smussati né stondati)
        val strokeW = (cell * 0.045f).coerceIn(1.5f, 3.5f)

        val numberPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = cell * 0.25f
            isAntiAlias = true
            typeface = fontTypeface
            alpha = (255 * currentAlpha).toInt()
        }

        val letterPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = cell * 0.55f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = fontTypeface
            alpha = (255 * currentAlpha).toInt()
        }

        // 2. Disegno delle celle
        for (r in 0 until result.rows) {
            for (c in 0 until result.cols) {
                val left = startX + c * cell
                val top = startY + r * cell
                val cellSize = Size(cell, cell)
                val topLeft = Offset(left, top)

                val isWhite = result.whiteCells[r][c]
                val isMystery = (r to c) in mysteryCells

                if (isWhite) {
                    if (isMystery) {
                        // Cella della Parola Cardine Misteriosa: fondo crema/dorato con bordo ambra netto
                        drawRect(
                            color = Color(0xFFFFFBEB).copy(alpha = currentAlpha),
                            topLeft = topLeft,
                            size = cellSize
                        )
                        drawRect(
                            color = Color(0xFFD97706).copy(alpha = currentAlpha),
                            topLeft = topLeft,
                            size = cellSize,
                            style = Stroke(width = strokeW * 1.25f)
                        )
                        // Indicatore dorato nell'angolino in alto a destra
                        val dotRadius = (cell * 0.08f).coerceIn(2.5f, 6f)
                        drawCircle(
                            color = Color(0xFFF59E0B).copy(alpha = currentAlpha),
                            radius = dotRadius,
                            center = Offset(left + cell - cell * 0.16f, top + cell * 0.16f)
                        )
                    } else {
                        // Cella Bianca del Cruciverba: quadrato netto con riempimento bianco e bordo nero preciso
                        drawRect(
                            color = Color.White.copy(alpha = currentAlpha),
                            topLeft = topLeft,
                            size = cellSize
                        )
                        drawRect(
                            color = Color(0xFF1E2230).copy(alpha = currentAlpha),
                            topLeft = topLeft,
                            size = cellSize,
                            style = Stroke(width = strokeW)
                        )
                    }

                    // Numeretto in alto a sinistra (se presente)
                    val number = numberedCells[r to c]
                    if (number != null && currentAlpha > 0.3f) {
                        drawContext.canvas.nativeCanvas.drawText(
                            number.toString(),
                            left + cell * 0.09f,
                            top + cell * 0.28f,
                            numberPaint
                        )
                    }

                    // Lettera della soluzione al centro (se abilitata)
                    if (showSolution && currentAlpha > 0.3f) {
                        val ch = result.letters[r][c]
                        if (ch != ' ') {
                            drawContext.canvas.nativeCanvas.drawText(
                                ch.toString(),
                                left + cell / 2f,
                                top + cell * 0.70f,
                                letterPaint
                            )
                        }
                    }
                } else {
                    // Cella Nera: quadrato scuro pieno, tipico dei cruciverba classici
                    drawRect(
                        color = Color(0xFF131520),
                        topLeft = topLeft,
                        size = cellSize
                    )
                    drawRect(
                        color = Color(0xFF1E2230).copy(alpha = 0.5f),
                        topLeft = topLeft,
                        size = cellSize,
                        style = Stroke(width = 0.8f)
                    )
                }
            }
        }

        // 3. Cornice perimetrale netta e quadrata attorno all'intera griglia
        drawRect(
            color = Color(0xFF1E2230),
            topLeft = Offset(startX, startY),
            size = Size(gridW, gridH),
            style = Stroke(width = strokeW * 1.4f)
        )
    }
}
