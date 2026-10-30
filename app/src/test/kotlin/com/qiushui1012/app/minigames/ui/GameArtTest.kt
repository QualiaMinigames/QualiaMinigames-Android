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
import com.qiushui1012.app.minigames.data.GameKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The marks of the two games, read from their files exactly as a card reads them.
 *
 * A file the parser cannot make sense of would draw an empty square on the home screen, which
 * is a card without a game's mark rather than a crash; that is the failure these check for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameArtTest {
    /** The context the assets are read through. */
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun every_game_reads_a_mark_from_its_file() {
        for (kind in GameKind.entries) {
            val bands = GameArt.of(context, kind)
            assertTrue("${GameArt.file(kind)} holds no paths", bands.isNotEmpty())
        }
    }

    @Test
    fun a_mark_is_drawn_inside_the_square_its_file_declares() {
        // A path outside the file's own `viewBox` would be scaled past the icon's box and drawn
        // over the card's text.
        for (kind in GameKind.entries) {
            for (band in GameArt.of(context, kind)) {
                val bounds = band.path.getBounds()
                assertTrue("${GameArt.file(kind)} starts before its square", bounds.left >= -0.5f)
                assertTrue("${GameArt.file(kind)} starts above its square", bounds.top >= -0.5f)
                assertTrue("${GameArt.file(kind)} ends past its square", bounds.right <= GameArt.SIZE + 0.5f)
                assertTrue("${GameArt.file(kind)} ends below its square", bounds.bottom <= GameArt.SIZE + 0.5f)
            }
        }
    }

    @Test
    fun a_mark_paints_both_of_its_tones() {
        // The files draw a mark in black on white, and the card paints those two tones with the
        // theme's ink and surface: a mark that lost one of them is a silhouette or a blank.
        val pipes = GameArt.of(context, GameKind.Pipes)
        assertTrue("the pipes mark has no dark part", pipes.any { it.fill == true || it.stroke == true })
        assertTrue("the pipes mark has no light part", pipes.any { it.fill == false || it.stroke == false })

        // The sudoku mark's cells are white inside a black outline, so its outline is the dark
        // tone and its fill is not.
        val sudoku = GameArt.of(context, GameKind.Sudoku)
        assertTrue("the sudoku mark has no outlined cell", sudoku.any { it.stroke == true && it.strokeWidth > 0f })
        assertTrue("the sudoku mark has no dark digit", sudoku.any { it.fill == true })
    }
}
