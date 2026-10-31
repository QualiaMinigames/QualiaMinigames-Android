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

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The nonogram and hashi forms, as a player meets them.
 *
 * The assertions are about what the forms accept and what they say: a form that started a board
 * outside its range would fail in the board rather than here, and a helper line that read `20%`
 * as a count would say the wrong number about a board it had understood.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NewGameFormsTest {
    @get:Rule
    val compose = createComposeRule()

    /** Returns the container a dialog needs, which resolves its own stores from the context. */
    private fun container(): AppContainer =
        (ApplicationProvider.getApplicationContext() as com.qiushui1012.app.minigames.MinigamesApplication).container

    /** Returns the context the labels are read from. */
    private fun context(): android.content.Context = ApplicationProvider.getApplicationContext()

    /** Returns the start button of a form, which is what a range is judged by. */
    private fun start() = context().getString(R.string.dialog_start)

    @Test
    fun the_nonogram_form_opens_on_the_default_board_and_can_start() {
        compose.setContent {
            NonogramNewGameDialog(container = container(), settings = Settings(), onDismiss = {}, onStart = {})
        }
        compose.onNodeWithText(context().getString(R.string.nonogram_width_range)).assertExists()
        compose.onNodeWithText(context().getString(R.string.nonogram_height_range)).assertExists()
        compose.onNodeWithText(context().getString(R.string.nonogram_slider_ticks)).assertExists()
        compose.onNodeWithText(start()).assertIsEnabled()
    }

    @Test
    fun the_largest_nonogram_field_is_accepted_and_starts() {
        // The field goes to 64 while the slider stops at 35: a value past the slider's end is a
        // size the board accepts, and the form has to let it through.
        compose.setContent {
            NonogramNewGameDialog(container = container(), settings = Settings(), onDismiss = {}, onStart = {})
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextClearance()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("64")
        compose.onNodeWithText(start()).assertIsEnabled()
    }

    @Test
    fun a_nonogram_field_beyond_the_range_is_refused() {
        compose.setContent {
            NonogramNewGameDialog(container = container(), settings = Settings(), onDismiss = {}, onStart = {})
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextClearance()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("65")
        compose.onNodeWithText(start()).assertIsNotEnabled()
    }

    @Test
    fun the_hashi_form_opens_on_the_default_board_and_can_start() {
        compose.setContent {
            HashiNewGameDialog(container = container(), settings = Settings(), onDismiss = {}, onStart = {})
        }
        compose.onNodeWithText(context().getString(R.string.hashi_width_range)).assertExists()
        compose.onNodeWithText(context().getString(R.string.hashi_height_range)).assertExists()
        compose.onNodeWithText(context().getString(R.string.hashi_islands)).assertExists()
        compose.onNodeWithText(start()).assertIsEnabled()
        // The default share of the default board is fifteen percent of a hundred points, which
        // is fifteen islands: the helper line has to say so.
        assertEquals(15, com.qiushui1012.app.minigames.rules.Rules.hashiIslandsForPerMille(100, 150))
        compose.onNodeWithText(context().getString(R.string.hashi_islands_helper_count, 15)).assertExists()
    }

    @Test
    fun a_hashi_share_is_read_as_a_share_and_its_helper_says_the_count() {
        compose.setContent {
            HashiNewGameDialog(container = container(), settings = Settings(), onDismiss = {}, onStart = {})
        }
        // The island field is the third text field: the two sizes come first.
        val islands = compose.onAllNodes(hasSetTextAction())[2]
        islands.performTextClearance()
        islands.performTextInput("20%")
        // Twenty per cent of a hundred points is twenty islands, not twenty per cent of a count:
        // a field that read the text as a number would say something else here.
        compose.onNodeWithText(context().getString(R.string.hashi_islands_helper_count, 20)).assertExists()
        compose.onNodeWithText(start()).assertIsEnabled()
    }

    @Test
    fun a_hashi_share_below_or_above_its_range_is_refused() {
        compose.setContent {
            HashiNewGameDialog(container = container(), settings = Settings(), onDismiss = {}, onStart = {})
        }
        val islands = compose.onAllNodes(hasSetTextAction())[2]
        islands.performTextClearance()
        islands.performTextInput("5%")
        compose.onNodeWithText(start()).assertIsNotEnabled()

        islands.performTextClearance()
        islands.performTextInput("40%")
        compose.onNodeWithText(start()).assertIsNotEnabled()
    }
}
