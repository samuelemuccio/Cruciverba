package com.samuele.crossword.export

import com.samuele.crossword.engine.CrosswordResult

object CrosswordRenderer {
    data class Theme(
        val cellSize: Float,
        val lineWidth: Float,
        val numberTextSize: Float,
        val letterTextSize: Float,
        val clueTextSize: Float,
        val padding: Float,
        val blackColor: Int,
        val whiteColor: Int,
        val gridColor: Int,
        val numberColor: Int,
        val letterColor: Int,
        val showSolution: Boolean,
        val showCluesBelow: Boolean
    )

    fun estimateSize(result: CrosswordResult, theme: Theme): Pair<Float, Float> {
        val gridW = theme.padding * 2 + result.cols * theme.cellSize
        val gridH = theme.padding * 2 + result.rows * theme.cellSize
        return gridW to gridH
    }
}
