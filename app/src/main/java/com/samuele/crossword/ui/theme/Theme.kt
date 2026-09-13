package com.samuele.crossword.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Ink300,
    onPrimary = Ink900,
    primaryContainer = Ink600,
    onPrimaryContainer = Ink50,
    secondary = Amber400,
    onSecondary = Ink900,
    secondaryContainer = Amber600,
    onSecondaryContainer = Ink50,
    tertiary = Mint300,
    background = Ink900,
    onBackground = Ink50,
    surface = Ink800,
    onSurface = Ink50,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink200,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    outline = Ink400
)

private val LightColorScheme = lightColorScheme(
    primary = Ink500,
    onPrimary = Color.White,
    primaryContainer = Ink100,
    onPrimaryContainer = Ink700,
    secondary = Amber500,
    onSecondary = Ink900,
    secondaryContainer = Amber400,
    onSecondaryContainer = Ink900,
    tertiary = Mint300,
    background = Color(0xFFFAF8FC),
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    surfaceVariant = Ink50,
    onSurfaceVariant = Ink700,
    surfaceContainer = Color(0xFFF3EEF8),
    surfaceContainerHigh = Color(0xFFEBE3F2),
    outline = Ink300
)

@Composable
fun CrosswordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}
