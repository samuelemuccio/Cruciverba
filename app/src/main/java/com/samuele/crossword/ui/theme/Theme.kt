package com.samuele.crossword.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E63D8),
    secondary = Color(0xFF4A4A66),
    tertiary = Color(0xFFD84A1E)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AC0FF),
    secondary = Color(0xFFBFC2DD),
    tertiary = Color(0xFFFFB59A)
)

@Composable
fun CrosswordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
