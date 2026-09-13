package com.samuele.crossword.ui.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samuele.crossword.engine.CrosswordTheme
import com.samuele.crossword.engine.FontFamilies

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSheet(
    currentTheme: CrosswordTheme,
    showSolution: Boolean,
    cellScale: Float,
    onThemeChange: (CrosswordTheme) -> Unit,
    onFontChange: (String) -> Unit,
    onCellScaleChange: (Float) -> Unit,
    onToggleSolution: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.35f), // Permette di vedere la griglia aggiornarsi dietro
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 44.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Personalizza Stile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 1. Temi Colori con Mini Preview 3x3 Canvas
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Tema colori",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(CrosswordTheme.all) { theme ->
                        val isSelected = currentTheme.id == theme.id
                        ThemeCard(
                            theme = theme,
                            selected = isSelected,
                            onClick = { onThemeChange(theme) }
                        )
                    }
                }
            }

            // 2. Font per Lettere e Numeri
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Carattere",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FontFamilies) { font ->
                        val isSelected = currentTheme.fontFamily == font.value
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFontChange(font.value) },
                            shape = RoundedCornerShape(12.dp),
                            label = {
                                Text(
                                    text = font.label,
                                    fontFamily = getFontFamily(font.value),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("style_font_${font.value}")
                        )
                    }
                }
            }

            // 3. Dimensione Griglia (Slider)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dimensione griglia",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(cellScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = cellScale,
                    onValueChange = onCellScaleChange,
                    valueRange = 0.6f..1.2f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Toggle Mostra Soluzione
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mostra soluzione",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Visualizza le lettere all'interno delle caselle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showSolution,
                        onCheckedChange = { onToggleSolution() },
                        modifier = Modifier.testTag("sheet_toggle_solution")
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ThemeCard(theme: CrosswordTheme, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val borderWidth = if (selected) 2.dp else 1.dp

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(theme.background),
        modifier = Modifier
            .size(width = 96.dp, height = 90.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
            .testTag("style_theme_${theme.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mini preview 3x3 con i colori del tema
            Box(Modifier.size(38.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val cs = size.width / 3f
                    for (r in 0 until 3) {
                        for (c in 0 until 3) {
                            val isBlack = (r == 0 && c == 2) || (r == 2 && c == 0)
                            drawRect(
                                color = Color(if (isBlack) theme.cellBlack else theme.cellWhite),
                                topLeft = Offset(c * cs, r * cs),
                                size = Size(cs, cs)
                            )
                            if (!isBlack) {
                                drawRect(
                                    color = Color(theme.gridLine),
                                    topLeft = Offset(c * cs, r * cs),
                                    size = Size(cs, cs),
                                    style = Stroke(width = 1f)
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = theme.name,
                color = if (theme.id == "pastel") Color(0xFF3E2723) else Color(theme.letterColor),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

private fun getFontFamily(name: String): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "sans-serif-condensed" -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    "casual" -> FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL))
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}
