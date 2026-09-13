package com.samuele.crossword.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.CrosswordTheme

@Composable
fun FreeSpaceCrosswordViewer(
    result: CrosswordResult,
    theme: CrosswordTheme,
    showSolution: Boolean,
    onToggleSolution: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember(result) { mutableFloatStateOf(1f) }
    var offset by remember(result) { mutableStateOf(Offset.Zero) }

    val aspectRatio = result.cols.toFloat() / result.rows.toFloat()
    val whiteCellsCount = remember(result) { result.whiteCells.sumOf { r -> r.count { it } } }
    val densityPct = remember(result) {
        val area = result.rows * result.cols
        if (area > 0) (whiteCellsCount * 100) / area else 0
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 350.dp, max = 480.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .pointerInput(result) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.6f, 3.5f)
                    offset = Offset(
                        x = offset.x + pan.x,
                        y = offset.y + pan.y
                    )
                }
            }
            .pointerInput(result) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1.1f) 1f else 1.8f
                        offset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Calcolo esatto per adattare il cruciverba al 100% nello spazio disponibile senza tagliarlo
        val safePadding = 56.dp
        val availW = (maxWidth - safePadding).coerceAtLeast(120.dp)
        val availH = (maxHeight - safePadding).coerceAtLeast(120.dp)

        val gridW: androidx.compose.ui.unit.Dp
        val gridH: androidx.compose.ui.unit.Dp
        if (availW.value / availH.value > aspectRatio) {
            gridH = availH
            gridW = availH * aspectRatio
        } else {
            gridW = availW
            gridH = availW / aspectRatio
        }

        // Foglio del cruciverba rettangolare che si muove liberamente nello spazio
        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(8.dp))
                .background(Color(theme.background), RoundedCornerShape(8.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            CrosswordGridView(
                result = result,
                theme = theme,
                showSolution = showSolution,
                modifier = Modifier.size(width = gridW, height = gridH)
            )
        }

        // Badge informativo in alto a sinistra (proporzioni rettangolari reali e parole totali)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${result.cols}×${result.rows}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "• ${result.assignments.size} parole (${densityPct}% densità)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Toolbar galleggiante dei controlli rapidi (Zoom, Centra, Soluzione)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(4.dp)
            ) {
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.size(36.dp).testTag("fit_crossword_button")
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = "Adatta allo schermo",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { scale = (scale + 0.3f).coerceAtMost(3.5f) },
                    modifier = Modifier.size(36.dp).testTag("zoom_in_button")
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = "Ingrandisci",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { scale = (scale - 0.3f).coerceAtLeast(0.6f) },
                    modifier = Modifier.size(36.dp).testTag("zoom_out_button")
                ) {
                    Icon(
                        Icons.Default.ZoomOut,
                        contentDescription = "Rimpicciolisci",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleSolution,
                    modifier = Modifier.size(36.dp).testTag("toggle_solution_canvas_button")
                ) {
                    Icon(
                        if (showSolution) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showSolution) "Nascondi lettere" else "Mostra lettere",
                        modifier = Modifier.size(20.dp),
                        tint = if (showSolution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
