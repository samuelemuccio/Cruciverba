package com.samuele.crossword.export

import android.graphics.Color
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.Direction
import com.samuele.crossword.engine.Slot
import com.samuele.crossword.engine.WordClue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PngExporterTest {

    private fun createSimpleCrossword(): CrosswordResult {
        // 3x3 grid with an L-shape: (0,0)-(0,1)-(0,2) and (0,0)-(1,0)-(2,0)
        // (1,1), (1,2), (2,1), (2,2) are not part of the crossword
        val rows = 3
        val cols = 3
        val whiteCells = Array(rows) { r ->
            BooleanArray(cols) { c ->
                (r == 0) || (c == 0)
            }
        }
        val letters = Array(rows) { CharArray(cols) { ' ' } }
        letters[0][0] = 'C'; letters[0][1] = 'A'; letters[0][2] = 'T'
        letters[1][0] = 'A'
        letters[2][0] = 'R'

        val slots = listOf(
            Slot(id = 1, row = 0, col = 0, length = 3, direction = Direction.ACROSS, cells = intArrayOf(0, 1, 2), number = 1),
            Slot(id = 2, row = 0, col = 0, length = 3, direction = Direction.DOWN, cells = intArrayOf(0, 3, 6), number = 1)
        )

        val assignments = mapOf(
            1 to WordClue("CAT", "Feline"),
            2 to WordClue("CAR", "Vehicle")
        )

        return CrosswordResult(
            rows = rows,
            cols = cols,
            whiteCells = whiteCells,
            letters = letters,
            slots = slots,
            assignments = assignments,
            unsolvedSlotIds = emptySet(),
            complete = true
        )
    }

    @Test
    fun `render produces high resolution transparent bitmap`() {
        val result = createSimpleCrossword()
        val options = PngExportOptions(scale = 2, transparentCells = true, baseCellSize = 80f)

        val bitmap = PngExporter.render(result, options)
        assertNotNull(bitmap)

        // Expected cell: 160px, padding: 24px -> total: 24*2 + 3*160 = 528px
        assertEquals(528, bitmap.width)
        assertEquals(528, bitmap.height)

        // The bottom-right area (2,2) is outside the crossword: should be 100% transparent (alpha == 0)
        val unusedX = 24 + 2 * 160 + 80
        val unusedY = 24 + 2 * 160 + 80
        val unusedPixel = bitmap.getPixel(unusedX, unusedY)
        assertEquals("Unused cell should be completely transparent (alpha 0)", 0, Color.alpha(unusedPixel))

        // Center of cell (0,1) with transparentCells=true should also be transparent
        val cellCenterX = 24 + 1 * 160 + 80
        val cellCenterY = 24 + 0 * 160 + 80
        val cellCenterPixel = bitmap.getPixel(cellCenterX, cellCenterY)
        assertEquals("Cell interior should be transparent when transparentCells is true", 0, Color.alpha(cellCenterPixel))

        bitmap.recycle()
    }

    @Test
    fun `render with opaque white cells produces white interior but transparent exterior`() {
        val result = createSimpleCrossword()
        val options = PngExportOptions(scale = 2, transparentCells = false, baseCellSize = 80f)

        val bitmap = PngExporter.render(result, options)
        assertNotNull(bitmap)

        // Unused cell outside crossword must still be 100% transparent
        val unusedX = 24 + 2 * 160 + 80
        val unusedY = 24 + 2 * 160 + 80
        val unusedPixel = bitmap.getPixel(unusedX, unusedY)
        assertEquals("Unused cell must remain transparent", 0, Color.alpha(unusedPixel))

        // Cell interior (0,1) should be white (alpha 255, RGB 255,255,255)
        val cellCenterX = 24 + 1 * 160 + 80
        val cellCenterY = 24 + 0 * 160 + 80
        val cellCenterPixel = bitmap.getPixel(cellCenterX, cellCenterY)
        assertEquals("Cell interior should be opaque white", 255, Color.alpha(cellCenterPixel))
        assertEquals("Cell interior color should be white", Color.WHITE, cellCenterPixel)

        bitmap.recycle()
    }
}
