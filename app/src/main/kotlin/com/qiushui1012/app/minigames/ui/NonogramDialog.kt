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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.resolveSeed
import com.qiushui1012.app.minigames.rules.NonogramBoard
import com.qiushui1012.app.minigames.rules.Rules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The most digits a nonogram size field accepts, which is what the largest board needs. */
private const val MAX_SIZE_DIGITS = 2

/**
 * The dialog that starts a nonogram game.
 *
 * # The order of the controls
 *
 * The width and the height, each a field above a slider, then the player's name and the seed.
 * The sliders cover 4 to 35 and the fields accept up to 64, which is the division the
 * minesweeper dialog makes over its own wider range: a value past the slider's end starts the
 * game at the value the field holds.
 *
 * # Why the form stays open while the board is drawn
 *
 * A 64 by 64 board is drawn and checked for a unique answer eight times over, and that is not
 * an instant. The button is disabled and the form says what it is waiting for rather than
 * closing on a board that does not exist yet; a generation that fails leaves the form open with
 * the reason, so the player can change the seed instead of losing the form.
 */
@Composable
fun NonogramNewGameDialog(
    container: AppContainer,
    settings: Settings,
    onDismiss: () -> Unit,
    onStart: (GameInProgress) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val minimum = Rules.nonogramMinSize()
    val maximum = Rules.nonogramMaxSize()
    // The seed a random game starts from is taken when the dialog opens, so that the moment the
    // button is pressed cannot change the board the player is looking at.
    val fallbackSeed = remember { System.currentTimeMillis() }

    var width by remember { mutableStateOf(settings.lastNonogramWidth.coerceIn(minimum, maximum)) }
    var widthText by remember { mutableStateOf(width.toString()) }
    var height by remember { mutableStateOf(settings.lastNonogramHeight.coerceIn(minimum, maximum)) }
    var heightText by remember { mutableStateOf(height.toString()) }
    var playerName by remember { mutableStateOf(settings.playerName) }
    var seedText by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val widthValid = widthText.toIntOrNull()?.let { it in minimum..maximum } == true
    val heightValid = heightText.toIntOrNull()?.let { it in minimum..maximum } == true
    val valid = widthValid && heightValid

    ModalForm(
        title = stringResource(R.string.dialog_new_game_title),
        onDismiss = onDismiss,
        confirm = {
            ModalConfirm(
                label = stringResource(R.string.dialog_start),
                enabled = valid && !generating,
                onClick = {
                    failed = false
                    generating = true
                    startNonogram(
                        scope = scope,
                        container = container,
                        width = width,
                        height = height,
                        seedText = seedText,
                        fallbackSeed = fallbackSeed,
                        playerName = playerName,
                        onStarted = onStart,
                        onFailed = {
                            // The form is left open with the reason: a player who typed a seed
                            // that draws no board changes the seed, and a dialog that vanished
                            // would take their typing with it.
                            generating = false
                            failed = true
                        },
                    )
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            SizeControl(
                label = stringResource(R.string.nonogram_width_range),
                value = width,
                text = widthText,
                minimum = minimum,
                maximum = maximum,
                note = stringResource(R.string.nonogram_slider_ticks),
                onText = { typed ->
                    widthText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                },
                onSlider = {
                    width = it
                    widthText = it.toString()
                },
            )
            SizeControl(
                label = stringResource(R.string.nonogram_height_range),
                value = height,
                text = heightText,
                minimum = minimum,
                maximum = maximum,
                note = stringResource(R.string.nonogram_slider_ticks),
                onText = { typed ->
                    heightText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                },
                onSlider = {
                    height = it
                    heightText = it.toString()
                },
            )
            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text(stringResource(R.string.dialog_player_name)) },
                placeholder = { Text(stringResource(R.string.dialog_player_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SeedField(value = seedText, onValueChange = { seedText = it })
            if (generating) {
                Text(
                    stringResource(R.string.nonogram_generating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failed) {
                Text(
                    stringResource(R.string.nonogram_generate_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Draws a new nonogram in the background and enters it.
 *
 * The board is drawn away from the drawing thread: eight attempts on a 64 by 64 board is work
 * measured in tens of milliseconds to a second, and running it on the thread that draws the
 * dialog would freeze the frame the button press is shown in.
 */
@Suppress("LongParameterList")
private fun startNonogram(
    scope: CoroutineScope,
    container: AppContainer,
    width: Int,
    height: Int,
    seedText: String,
    fallbackSeed: Long,
    playerName: String,
    onStarted: (GameInProgress) -> Unit,
    onFailed: () -> Unit,
) {
    val typed = seedText.trim().isNotEmpty()
    val resolved = resolveSeed(seedText, fallbackSeed)
    scope.launch {
        val board: NonogramBoard? = withContext(Dispatchers.Default) { Rules.newNonogram(width, height, resolved.seed) }
        if (board == null) {
            onFailed()
            return@launch
        }
        container.settings.setLastNonogram(width, height)
        container.records.clearSave(GameKind.Nonogram)
        onStarted(GameInProgress.Nonogram(board, playerName.trim(), 0, typed))
    }
}
