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
import androidx.compose.runtime.mutableIntStateOf
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
import com.qiushui1012.app.minigames.rules.ArrowCubeBoard
import com.qiushui1012.app.minigames.rules.Rules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The most digits an arrow cube size field accepts, which is what the largest box needs. */
private const val MAX_SIZE_DIGITS = 2

/** The size a new box starts at, which is not remembered from the last game. */
private const val DEFAULT_SIZE = 10

/**
 * The dialog that starts an arrow cube game.
 *
 * # The order of the controls
 *
 * The width, the depth, the height, then the player's name and the seed, which is the order
 * the parameters are read in: the box is an extent in each direction, and a player describing
 * it says them in that order. The three sizes share one line under the last slider because
 * they share one range; three copies of "5 to 30" would be three times the same sentence.
 *
 * # Why the sizes are not remembered
 *
 * The other games keep their last parameters in the settings. A box is started at its default
 * instead, every time: its three sides are the one parameter set a player is most likely to
 * want to change, and a dialog that opened at the last game's 30 by 30 by 30 would make the
 * default unreachable without typing it back.
 *
 * # Why the form stays open while the box is filled
 *
 * A 30 by 30 by 30 box is tiled and peeled apart, which is measured in tens of milliseconds
 * rather than being an instant. The button is disabled and the form says what it is waiting
 * for rather than closing on a board that does not exist yet; a generation that fails leaves
 * the form open with the reason, so the player can change one field instead of retyping them.
 */
@Composable
fun ArrowCubeNewGameDialog(
    container: AppContainer,
    settings: Settings,
    onDismiss: () -> Unit,
    onStart: (GameInProgress) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val minimum = Rules.arrowCubeMinSize()
    val maximum = Rules.arrowCubeMaxSize()
    // The seed a random game starts from is taken when the dialog opens, so that the moment the
    // button is pressed cannot change the board the player is looking at.
    val fallbackSeed = remember { System.currentTimeMillis() }

    var width by remember { mutableIntStateOf(DEFAULT_SIZE.coerceIn(minimum, maximum)) }
    var widthText by remember { mutableStateOf(width.toString()) }
    var depth by remember { mutableIntStateOf(DEFAULT_SIZE.coerceIn(minimum, maximum)) }
    var depthText by remember { mutableStateOf(depth.toString()) }
    var height by remember { mutableIntStateOf(DEFAULT_SIZE.coerceIn(minimum, maximum)) }
    var heightText by remember { mutableStateOf(height.toString()) }
    var playerName by remember { mutableStateOf(settings.playerName) }
    var seedText by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val widthValid = widthText.toIntOrNull()?.let { it in minimum..maximum } == true
    val depthValid = depthText.toIntOrNull()?.let { it in minimum..maximum } == true
    val heightValid = heightText.toIntOrNull()?.let { it in minimum..maximum } == true
    val valid = widthValid && depthValid && heightValid

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
                    startArrowCube(
                        scope = scope,
                        container = container,
                        width = width,
                        depth = depth,
                        height = height,
                        seedText = seedText,
                        fallbackSeed = fallbackSeed,
                        playerName = playerName,
                        onStarted = onStart,
                        onFailed = {
                            // The form keeps every value it had, so a player whose board could
                            // not be generated changes one character rather than retyping.
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
                label = stringResource(R.string.arrow_cube_width_range),
                value = width,
                text = widthText,
                minimum = minimum,
                maximum = maximum,
                sliderMaximum = maximum,
                enabled = !generating,
                onText = { typed ->
                    widthText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                    widthText.toIntOrNull()?.let { typedWidth ->
                        if (typedWidth in minimum..maximum) {
                            width = typedWidth
                        }
                    }
                },
                onSlider = {
                    width = it
                    widthText = it.toString()
                },
            )
            SizeControl(
                label = stringResource(R.string.arrow_cube_depth_range),
                value = depth,
                text = depthText,
                minimum = minimum,
                maximum = maximum,
                sliderMaximum = maximum,
                enabled = !generating,
                onText = { typed ->
                    depthText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                    depthText.toIntOrNull()?.let { typedDepth ->
                        if (typedDepth in minimum..maximum) {
                            depth = typedDepth
                        }
                    }
                },
                onSlider = {
                    depth = it
                    depthText = it.toString()
                },
            )
            SizeControl(
                label = stringResource(R.string.arrow_cube_height_range),
                value = height,
                text = heightText,
                minimum = minimum,
                maximum = maximum,
                sliderMaximum = maximum,
                note = stringResource(R.string.arrow_cube_slider_ticks),
                enabled = !generating,
                onText = { typed ->
                    heightText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                    heightText.toIntOrNull()?.let { typedHeight ->
                        if (typedHeight in minimum..maximum) {
                            height = typedHeight
                        }
                    }
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
                enabled = !generating,
                modifier = Modifier.fillMaxWidth(),
            )
            SeedField(value = seedText, onValueChange = { seedText = it }, enabled = !generating)
            if (generating) {
                Text(
                    stringResource(R.string.arrow_cube_generating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failed) {
                Text(
                    stringResource(R.string.arrow_cube_generate_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Fills a new box in the background and enters it.
 *
 * The fill is done away from the drawing thread: tiling and peeling a 30 by 30 by 30 box is
 * work measured in tens of milliseconds, and running it on the thread that draws the dialog
 * would freeze the frame the button press is shown in.
 */
@Suppress("LongParameterList")
private fun startArrowCube(
    scope: CoroutineScope,
    container: AppContainer,
    width: Int,
    depth: Int,
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
        val board: ArrowCubeBoard? = withContext(Dispatchers.Default) { Rules.newArrowCube(width, depth, height, resolved.seed) }
        if (board == null) {
            onFailed()
            return@launch
        }
        container.records.clearSave(GameKind.ArrowCube)
        onStarted(GameInProgress.ArrowCube(board, playerName.trim(), 0, typed))
    }
}
