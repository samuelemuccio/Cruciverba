package com.samuele.crossword.engine

data class CrosswordTheme(
    val id: String,
    val name: String,
    val background: Long,
    val cellWhite: Long,
    val cellBlack: Long,
    val gridLine: Long,
    val numberColor: Long,
    val letterColor: Long,
    val clueTextColor: Long,
    val headerColor: Long,
    val fontFamily: String = "sans-serif"
) {
    companion object {
        val Paper = CrosswordTheme(
            id = "paper",
            name = "Classico",
            background = 0xFFFFFFFF,
            cellWhite = 0xFFFFFFFF,
            cellBlack = 0xFF000000,
            gridLine = 0xFF000000,
            numberColor = 0xFF000000,
            letterColor = 0xFF000000,
            clueTextColor = 0xFF333333,
            headerColor = 0xFF000000,
            fontFamily = "sans-serif"
        )
        val Dark = CrosswordTheme(
            id = "dark",
            name = "Notte",
            background = 0xFF121212,
            cellWhite = 0xFF1E1E1E,
            cellBlack = 0xFF000000,
            gridLine = 0xFF888888,
            numberColor = 0xFFFFD54F,
            letterColor = 0xFFFFFFFF,
            clueTextColor = 0xFFBBBBBB,
            headerColor = 0xFFFFD54F,
            fontFamily = "sans-serif"
        )
        val Pastel = CrosswordTheme(
            id = "pastel",
            name = "Pastello",
            background = 0xFFFDF6E3,
            cellWhite = 0xFFFFF8E1,
            cellBlack = 0xFF5D4037,
            gridLine = 0xFF8D6E63,
            numberColor = 0xFF5D4037,
            letterColor = 0xFF3E2723,
            clueTextColor = 0xFF5D4037,
            headerColor = 0xFF8D6E63,
            fontFamily = "sans-serif"
        )
        val Neon = CrosswordTheme(
            id = "neon",
            name = "Neon",
            background = 0xFF0A0A0A,
            cellWhite = 0xFF0A0A0A,
            cellBlack = 0xFF00FF88,
            gridLine = 0xFF00FF88,
            numberColor = 0xFFFF00FF,
            letterColor = 0xFF00FF88,
            clueTextColor = 0xFF00FF88,
            headerColor = 0xFFFF00FF,
            fontFamily = "sans-serif"
        )
        val Blue = CrosswordTheme(
            id = "blue",
            name = "Blu",
            background = 0xFFE3F2FD,
            cellWhite = 0xFFFFFFFF,
            cellBlack = 0xFF0D47A1,
            gridLine = 0xFF1976D2,
            numberColor = 0xFF0D47A1,
            letterColor = 0xFF0D47A1,
            clueTextColor = 0xFF1565C0,
            headerColor = 0xFF0D47A1,
            fontFamily = "sans-serif"
        )
        val all = listOf(Paper, Dark, Pastel, Neon, Blue)
    }
}

data class FontOption(val value: String, val label: String)

val FontFamilies = listOf(
    FontOption("sans-serif", "Standard"),
    FontOption("serif", "Serif"),
    FontOption("monospace", "Monospace"),
    FontOption("sans-serif-condensed", "Condensed"),
    FontOption("casual", "Casual"),
    FontOption("cursive", "Cursive")
)
