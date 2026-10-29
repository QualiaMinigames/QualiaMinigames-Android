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
 * The artwork files and the turns that point them.
 *
 * The files are read here exactly as the board reads them, so a file the parser cannot make sense
 * of fails here rather than as a board that draws nothing.
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
    fun every_pipe_file_draws_its_lumen_as_well_as_its_walls() {
        // The lumen is a path in the file, drawn white, so a shape that reached the board without
        // one would be a hollow outline with the board showing through it.
        for (shape in PipeArt.Shape.entries) {
            val drawing = PipeArt.of(context, shape)
            assertTrue("${shape.file} draws no lumen", drawing.bands.any { it.isLumen })
            assertTrue("${shape.file} draws no wall", drawing.bands.any { !it.isLumen })
        }
    }

    @Test
    fun a_straight_is_two_walls_and_a_lumen() {
        val drawing = PipeArt.of(context, PipeArt.Shape.Straight)
        assertEquals("two walls", 2, drawing.bands.count { !it.isLumen })
        assertTrue("at least one lumen path", drawing.bands.count { it.isLumen } >= 1)
    }

    @Test
    fun a_wall_band_lies_between_the_edge_and_the_middle() {
        // The straight's first wall runs from x = 7.65 to 9.65, so it must sit left of the middle
        // and be two units wide.
        val drawing = PipeArt.of(context, PipeArt.Shape.Straight)
        val bounds = drawing.bands.first { !it.isLumen }.path.getBounds()
        assertTrue("the wall is not on the left", bounds.left < PipeArt.CENTRE)
        assertTrue("the wall crosses the middle", bounds.right <= PipeArt.CENTRE)
        assertEquals("the wall is not two units wide", 2f, bounds.width, 0.01f)
    }

    @Test
    fun the_pump_reads_as_two_faces_in_two_colours() {
        val drawing = PipeArt.pump(context)
        assertEquals("the pump is a body and a mark", 2, drawing.bands.size)
        assertTrue(
            "the body and the mark share a colour in the file",
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
    fun the_turn_of_a_shape_lands_on_the_sides_the_mask_names() {
        // The property that matters: whatever pair is returned, turning the drawing's own sides by
        // that many quarter turns has to give back the mask the piece joins. A tee that turned the
        // wrong way would satisfy every other check here and draw the wrong piece.
        for (mask in 1..15) {
            val (shape, turns) = requireNotNull(pipeShapeOf(mask))
            assertEquals(
                "mask $mask is drawn as ${shape.file} turned $turns",
                mask,
                rotateSides(shape.sides, turns),
            )
        }
    }

    @Test
    fun a_tee_turns_a_quarter_turn_at_a_time() {
        // The tee's file joins up, down and left — a `┣`. One quarter turn clockwise carries each
        // arm one side round, which is the case that used to be drawn as a `┫` and is the reason
        // this is checked by name rather than by a number.
        val tee = PipeArt.Shape.Tee
        assertEquals(
            "the tee's own sides are not up, down and left",
            setOf(PipeSides.TOP, PipeSides.BOTTOM, PipeSides.LEFT),
            sidesOf(tee.sides),
        )
        assertEquals(
            "one turn is not a ┳",
            setOf(PipeSides.TOP, PipeSides.RIGHT, PipeSides.LEFT),
            sidesOf(rotateSides(tee.sides, 1)),
        )
        // Four turns come back to the drawing, and each turn moves every arm: that is what makes
        // the other two directions right without listing them, which is where the mistakes were.
        assertEquals("four turns are not the drawing", tee.sides, rotateSides(tee.sides, 4))
        assertEquals(
            "two turns are not made of the same arms as one, moved once more",
            sidesOf(rotateSides(tee.sides, 1)).map { turn(it) }.toSet(),
            sidesOf(rotateSides(tee.sides, 2)),
        )
    }

    /** Returns the side one quarter turn clockwise from [side]. */
    private fun turn(side: Int): Int =
        when (side) {
            PipeSides.TOP -> PipeSides.RIGHT
            PipeSides.RIGHT -> PipeSides.BOTTOM
            PipeSides.BOTTOM -> PipeSides.LEFT
            else -> PipeSides.TOP
        }

    /** Returns the sides a mask names, so a test can compare them by name. */
    private fun sidesOf(mask: Int): Set<Int> =
        listOf(PipeSides.TOP, PipeSides.RIGHT, PipeSides.BOTTOM, PipeSides.LEFT)
            .filter { mask and it != 0 }
            .toSet()

    @Test
    fun a_bend_turns_a_quarter_turn_at_a_time() {
        val bend = PipeArt.Shape.Bend
        assertEquals("the bend's own sides", PipeSides.TOP or PipeSides.LEFT, bend.sides)
        assertEquals(PipeSides.TOP or PipeSides.RIGHT, rotateSides(bend.sides, 1))
        assertEquals(PipeSides.BOTTOM or PipeSides.RIGHT, rotateSides(bend.sides, 2))
        assertEquals(PipeSides.BOTTOM or PipeSides.LEFT, rotateSides(bend.sides, 3))
    }

    @Test
    fun a_mask_that_is_not_a_piece_has_no_shape() {
        assertNull("an empty mask is not a piece", pipeShapeOf(0))
    }
}
