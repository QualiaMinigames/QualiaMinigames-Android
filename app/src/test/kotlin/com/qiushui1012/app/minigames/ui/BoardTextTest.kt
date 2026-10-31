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

import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The face and the placement the four boards share.
 *
 * Robolectric's graphics stack measures text with a stub face, so the assertions are about the
 * relationship the placement promises rather than about the pixels of a particular glyph: the
 * ink's box has to end up on the cell's centre, and the digit has to stay inside one cell. Both
 * hold whatever face is loaded, which is what makes them worth checking here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BoardTextTest {
    /** The size a board's digits are drawn at, in board units. */
    private val digitSize = 0.62f

    /** Returns a paint set up the way a board sets one up. */
    private fun paint(size: Float): Paint =
        boardNumberPaint(
            BoardText.typeface(ApplicationProvider.getApplicationContext()),
            size,
        )

    @Test
    fun the_face_loads_and_is_never_null() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNotNull(BoardText.typeface(context))
    }

    @Test
    fun the_face_is_loaded_once_per_context() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            "the face is loaded again on every request",
            BoardText.typeface(context) === BoardText.typeface(context),
        )
    }

    @Test
    fun the_centre_offset_puts_a_digit_on_the_middle_of_its_cell() {
        val paint = paint(digitSize)
        val measuring = Paint(paint).apply { textSize = BoardText.MEASURING_TEXT_SIZE }
        val box = android.graphics.Rect()
        measuring.getTextBounds("8", 0, 1, box)
        val offset = BoardText.centre("8", paint)
        val scale = digitSize / BoardText.MEASURING_TEXT_SIZE
        // The ink's box, moved by the offset, has to be centred on the cell: its left and right
        // edges sit symmetrically about the centre, and so do its top and bottom. The tolerance
        // is a fiftieth of a cell, which is below what a screen can show.
        val left = offset.x + box.left * scale
        val right = offset.x + box.right * scale
        val top = offset.y + box.top * scale
        val bottom = offset.y + box.bottom * scale
        assertTrue("the digit is left of the centre: $left and $right", kotlin.math.abs(left + right) < 0.02f)
        assertTrue("the digit is above the centre: $top and $bottom", kotlin.math.abs(top + bottom) < 0.02f)
    }

    @Test
    fun a_digit_is_no_taller_than_the_cell_it_sits_in() {
        val paint = paint(digitSize)
        val offset = BoardText.centre("8", paint)
        val measuring = Paint(paint).apply { textSize = BoardText.MEASURING_TEXT_SIZE }
        val box = android.graphics.Rect()
        measuring.getTextBounds("8", 0, 1, box)
        val height = box.height() * digitSize / BoardText.MEASURING_TEXT_SIZE
        // The whole ink, from its top to its bottom and moved by the offset, has to stay on the
        // cell: a digit that overflowed would be cut by the cell beside it.
        assertTrue("the digit is taller than its cell: $height", height < 1f)
        assertTrue("the digit runs off the top of its cell", offset.y + box.top * digitSize / BoardText.MEASURING_TEXT_SIZE > -0.5f)
        assertTrue("the digit runs off the bottom of its cell", offset.y + box.bottom * digitSize / BoardText.MEASURING_TEXT_SIZE < 0.5f)
    }

    @Test
    fun a_two_digit_clue_stays_inside_one_cell() {
        // A nonogram's clue of twelve is the widest text a cell holds, and a grid of them is
        // what the clue margin is sized for.
        val paint = paint(digitSize)
        val measuring = Paint(paint).apply { textSize = BoardText.MEASURING_TEXT_SIZE }
        val box = android.graphics.Rect()
        measuring.getTextBounds("12", 0, 2, box)
        val width = box.width() * digitSize / BoardText.MEASURING_TEXT_SIZE
        assertTrue("a two-digit clue is wider than its cell: $width", width < 1f)
    }

    @Test
    fun a_board_paint_does_not_ask_the_pen_to_centre_its_advance() {
        // The placement adds its own offset, which only works when the pen is not centring the
        // advance as well: the two would push the glyph in opposite directions.
        val paint = paint(digitSize)
        assertEquals(Paint.Align.LEFT, paint.textAlign)
        assertTrue(paint.isAntiAlias)
    }
}
