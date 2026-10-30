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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.MinesweeperPreset
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.data.Settings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The minesweeper form, as a player meets it.
 *
 * The assertions are about the words the form shows, because those are what say the numbers
 * were understood: a helper line that said "0 mines" for a 12.3% board would be a form that
 * had read the field as a count, and a slider with no label would be a range with no name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MinesweeperDialogTest {
    @get:Rule
    val compose = createComposeRule()

    /** Returns the container a dialog needs, which resolves its own stores from the context. */
    private fun container(): AppContainer =
        (ApplicationProvider.getApplicationContext() as com.qiushui1012.app.minigames.MinigamesApplication).container

    @Test
    fun the_form_opens_on_the_classic_beginner_board() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            MinesweeperNewGameDialog(
                container = container(),
                settings = Settings(),
                onDismiss = {},
                onStart = {},
            )
        }
        compose.onNodeWithText(context.getString(R.string.minesweeper_mines)).assertExists()
        // The helper line says what the default share comes to on the default board: a form
        // that had read 12.3 as a count would say "0 mines" here.
        compose.onNodeWithText(context.getString(R.string.minesweeper_mines_helper_count, 10)).assertExists()
        compose.onNodeWithText(context.getString(MinesweeperPreset.Beginner.label)).assertExists()
    }

    @Test
    fun every_preset_is_offered_and_names_its_board() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            MinesweeperNewGameDialog(
                container = container(),
                settings = Settings(),
                onDismiss = {},
                onStart = {},
            )
        }
        for (preset in MinesweeperPreset.entries) {
            compose.onNodeWithText(context.getString(preset.label)).assertExists()
        }
        // The three classic shapes are the ones the presets fill in.
        assertEquals(9, MinesweeperPreset.Beginner.width)
        assertEquals(9, MinesweeperPreset.Beginner.height)
        assertEquals(10, com.qiushui1012.app.minigames.minesForPerMille(81, MinesweeperPreset.Beginner.perMille))
        assertEquals(40, com.qiushui1012.app.minigames.minesForPerMille(256, MinesweeperPreset.Intermediate.perMille))
        assertEquals(99, com.qiushui1012.app.minigames.minesForPerMille(480, MinesweeperPreset.Expert.perMille))
    }

    @Test
    fun the_slider_names_the_density_it_is_nearest_to() {
        // The eight snap points are never laid out side by side: the form shows the one the
        // value is nearest to, which is the one a player would call the board by.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            MinesweeperNewGameDialog(
                container = container(),
                settings = Settings(),
                onDismiss = {},
                onStart = {},
            )
        }
        compose.onNodeWithText(context.getString(R.string.minesweeper_snap_beginner)).assertExists()
    }

    private fun assertEquals(
        expected: Int,
        actual: Int,
    ) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
