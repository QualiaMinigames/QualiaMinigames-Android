// Copyright (C) 2026 QualiaMinigames contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.qiushui1012.app.minigames.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a tap on a board reports, as a player meets it.
 *
 * A cell is drawn in board units and a tap arrives in pixels, so the two agree only if the hit
 * test reads the geometry the drawing was made with. Every test taps a point the centre of a
 * cell is drawn at: a cell answered for that is not the one under the finger, or none answered
 * for at all, is the defect this class exists to catch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1080dp-h1920dp")
class ZoomableBoardTest {
    @get:Rule
    val compose = createComposeRule()

    /** Converts a length in dp to the pixels a touch position is given in. */
    private fun px(value: Float): Float = with(compose.density) { value.dp.toPx() }

    /** Taps the point [x], [y] dp in from the top-left corner of the board, and settles. */
    private fun tap(x: Float, y: Float) {
        compose.onNodeWithTag(TAG).performTouchInput { click(Offset(px(x), px(y))) }
        compose.waitForIdle()
    }

    @Test
    fun a_tap_names_the_cell_it_lands_in_on_a_board_that_fills_the_view() {
        var tapped: Pair<Int, Int>? = null
        compose.setContent {
            Box(Modifier.size(300.dp).testTag(TAG)) {
                ZoomableBoard(
                    boardWidth = 3f,
                    boardHeight = 3f,
                    minCellDp = 8f,
                    columns = 3,
                    rows = 3,
                    onTap = { row, column -> tapped = row to column },
                    content = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // A 300 dp view holds three cells of 100 dp, so the centre of row 1, column 2 is at
        // 2.5 cells across and 1.5 cells down.
        tap(x = 250f, y = 150f)
        assertEquals(1 to 2, tapped)
    }

    @Test
    fun a_tap_names_the_cell_it_lands_in_on_a_board_inset_by_a_clue_margin() {
        var tapped: Pair<Int, Int>? = null
        compose.setContent {
            Box(Modifier.size(width = 500.dp, height = 400.dp).testTag(TAG)) {
                ZoomableBoard(
                    boardWidth = 5f,
                    boardHeight = 4f,
                    minCellDp = 8f,
                    columns = 3,
                    rows = 3,
                    cellWidth = 1f,
                    cellHeight = 1f,
                    // Two cells of clue margin down the left and one across the top, which is
                    // what a nonogram's clue columns are.
                    cellOrigin = Offset(2.5f, 1.5f),
                    onTap = { row, column -> tapped = row to column },
                    content = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // The whole board is 100 dp a unit, so the centre of the first cell is at (250, 150).
        tap(x = 250f, y = 150f)
        assertEquals(0 to 0, tapped)

        tapped = null
        // A point inside the margin column, which is not a cell.
        tap(x = 100f, y = 50f)
        assertNull("a tap in the clue margin names no cell", tapped)
    }

    @Test
    fun a_tap_names_the_cell_it_lands_in_on_a_board_centred_in_a_wider_view() {
        var tapped: Pair<Int, Int>? = null
        compose.setContent {
            Box(Modifier.size(width = 360.dp, height = 240.dp).testTag(TAG)) {
                ZoomableBoard(
                    boardWidth = 3f,
                    boardHeight = 3f,
                    minCellDp = 8f,
                    columns = 3,
                    rows = 3,
                    onTap = { row, column -> tapped = row to column },
                    content = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // The shorter side sets the scale, so a cell is 80 dp and the drawn board is 240 dp wide
        // and 60 dp in from the left: row 2, column 0 has its centre at (100, 200).
        tap(x = 100f, y = 200f)
        assertEquals(2 to 0, tapped)
    }

    /** The tag the board is found by, which is the only node the tests tap. */
    private companion object {
        const val TAG = "board"
    }
}
