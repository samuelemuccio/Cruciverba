package com.samuele.crossword.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Superficie in vetro satinato (Frosted Glass) ispirata a iOS Human Interface Guidelines.
 * Riempimento scuro traslucido stratificato con stroke sottile a gradiente (135°) simulante il riflesso della luce.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color(0xFF131622).copy(alpha = 0.78f),
    elevation: Dp = 4.dp,
    content: @Composable () -> Unit
) {
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.14f),
            Color.White.copy(alpha = 0.02f)
        )
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.6f)
            )
            .border(
                width = 1.dp,
                brush = borderGradient,
                shape = shape
            ),
        shape = shape,
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        content()
    }
}

/**
 * Modificatore per applicare lo sfondo Dark Midnight / Obsidian stratificato
 */
fun Modifier.obsidianBackground(): Modifier = this.then(
    Modifier.background(
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0B0C10),
                Color(0xFF10121A),
                Color(0xFF0D0F16)
            )
        )
    )
)
