package com.samuele.crossword.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RoyalIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E2548),
    onPrimaryContainer = Color(0xFFD6DEFF),
    secondary = RoyalIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1B2038),
    onSecondaryContainer = Color(0xFFC7D2FE),
    tertiary = IosTeal,
    background = IosDarkBackground,
    onBackground = Color(0xFFFFFFFF),
    surface = IosDarkSurface,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = IosDarkSurfaceElevated,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainer = IosDarkSurfaceContainer,
    surfaceContainerHigh = IosDarkSurfaceContainerHigh,
    outline = Color(0x24FFFFFF)
)

private val LightColorScheme = lightColorScheme(
    primary = IosIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECEBFF),
    onPrimaryContainer = Color(0xFF2B2875),
    secondary = IosOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECCC),
    onSecondaryContainer = Color(0xFF5C3300),
    tertiary = IosTeal,
    background = IosLightBackground,
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    surfaceContainer = Color(0xFFEBEBF0),
    surfaceContainerHigh = Color(0xFFE3E3E8),
    outline = Color(0x1F000000)
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
