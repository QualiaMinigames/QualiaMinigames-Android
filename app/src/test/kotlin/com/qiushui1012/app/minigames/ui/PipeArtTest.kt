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
 * These run against the assets in `assets/`, which is what the device loads. A file that the
 * parser cannot make sense of, or a shape whose turns do not point the way its mask says, is
 * the kind of fault that shows up as a board that looks wrong rather than as an error.
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
            assertTrue("${shape.file} holds no paths", drawing.faces.isNotEmpty())
            assertTrue("${shape.file} has no width", drawing.pipeWidth > 0f)
            assertTrue("${shape.file} has no wall width", drawing.wallWidth > 0f)
        }
    }

    @Test
    fun a_pipe_is_wider_inside_than_its_two_walls() {
        val drawing = PipeArt.of(context, PipeArt.Shape.Straight)
        assertTrue(
            "the lumen is not the space between the walls",
            drawing.lumenWidth > drawing.wallWidth,
        )
        assertTrue(
            "the pipe is not wider than its lumen by one wall at each edge",
            drawing.pipeWidth > drawing.lumenWidth,
        )
    }

    @Test
    fun the_pump_reads_as_filled_faces() {
        val drawing = PipeArt.pump(context)
        val filled = drawing.faces.filter { it.isFilled }
        assertTrue("the pump has no filled face", filled.size >= 2)
    }

    @Test
    fun every_piece_of_the_rules_has_a_shape_and_a_turn() {
        for (mask in 1..15) {
            val placement = pipeShapeOf(mask)
            assertNotNull("mask $mask has no shape", placement)
        }
    }

    @Test
    fun the_shape_of_a_mask_joins_the_sides_the_mask_names() {
        // A straight has no bend in it: whatever turns the mask needs, the drawing's own two
        // walls must lie along one axis. A bend is the shape whose drawing turns; a tee lacks
        // exactly one side; a cross has four.
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
