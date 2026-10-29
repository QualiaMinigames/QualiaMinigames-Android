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

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The artwork files, read as the board reads them.
 *
 * These run against the assets in `assets/`, which is what the device loads. A file the parser
 * cannot make sense of, or a shape whose turns do not point the way its mask says, is the kind of
 * fault that shows up as a board that looks wrong rather than as an error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PipeArtTest {
    /** The context the assets are read through. */
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun every_shape_reads_from_its_file() {
        for (shape in PipeArt.Shape.entries) {
            val drawing = PipeArt.of(context, shape)
            assertTrue("${shape.file} holds no paths", drawing.bands.isNotEmpty())
        }
    }

    @Test
    fun a_straight_is_two_walls_and_nothing_else() {
        val drawing = PipeArt.of(context, PipeArt.Shape.Straight)
        assertEquals("a straight is drawn as two bands", 2, drawing.bands.size)
    }

    @Test
    fun a_cross_is_four_walls() {
        val drawing = PipeArt.of(context, PipeArt.Shape.Cross)
        assertEquals("a cross is drawn as four bands", 4, drawing.bands.size)
    }

    @Test
    fun the_lumen_is_narrower_than_the_cell_so_it_stays_inside() {
        assertTrue(
            "a lumen as wide as the cell would cover the walls",
            PipeArt.LUMEN_WIDTH < PipeArt.SIZE,
        )
        assertTrue("the lumen has no width", PipeArt.LUMEN_WIDTH > 0f)
    }

    @Test
    fun a_wall_band_lies_between_the_centre_and_the_edge() {
        // The straight's first band runs from x = 7.65 to 9.65, so its bounds must sit on the
        // left of the square and stop short of the middle.
        val drawing = PipeArt.of(context, PipeArt.Shape.Straight)
        val bounds = drawing.bands.first().path.getBounds()
        assertTrue("the first band is not on the left", bounds.left < PipeArt.CENTRE)
        assertTrue("the first band crosses the middle", bounds.right <= PipeArt.CENTRE)
        assertEquals("the band is not two units wide", 2f, bounds.width, 0.01f)
    }

    @Test
    fun the_pump_reads_as_two_faces_in_two_colours() {
        val drawing = PipeArt.pump(context)
        assertEquals("the pump is a body and a drop", 2, drawing.bands.size)
        assertTrue(
            "the body and the drop are the same colour in the file",
            drawing.bands[0].color != drawing.bands[1].color,
        )
    }

    @Test
    fun every_piece_of_the_rules_has_a_shape_and_a_turn() {
        for (mask in 1..15) {
            assertNotNull("mask $mask has no shape", pipeShapeOf(mask))
        }
    }

    @Test
    fun the_shape_of_a_mask_joins_the_sides_the_mask_names() {
        assertEquals(PipeArt.Shape.Straight, pipeShapeOf(PipeSides.TOP or PipeSides.BOTTOM)?.first)
        assertEquals(PipeArt.Shape.Bend, pipeShapeOf(PipeSides.TOP or PipeSides.LEFT)?.first)
        assertEquals(PipeArt.Shape.Cross, pipeShapeOf(15)?.first)
        assertEquals(
            PipeArt.Shape.Tee,
            pipeShapeOf(PipeSides.TOP or PipeSides.LEFT or PipeSides.BOTTOM)?.first,
        )
        assertEquals(PipeArt.Shape.End, pipeShapeOf(PipeSides.RIGHT)?.first)
    }

    @Test
    fun a_mask_that_is_not_a_piece_has_no_shape() {
        assertNull("an empty mask is not a piece", pipeShapeOf(0))
    }
}
