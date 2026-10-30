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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.difficultyFromStorageName
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.Rules
import com.qiushui1012.app.minigames.rules.SudokuDifficulty
import com.qiushui1012.app.minigames.toStorageName
import kotlinx.coroutines.launch

/**
 * The dialog that starts a pipes game.
 *
 * # Why the size is both a field and a slider
 *
 * The board goes from 4 to 35 cells on a side, which is too wide a range for a slider to land
 * on one value by touch, and too tedious to reach by pressing a field's arrow thirty times.
 * The field is above and the slider below it: the field is where a number is typed and read,
 * the slider is where the range is felt, and reading the form goes from the exact value to the
 * coarser control that sets it.
 *
 * The player name defaults to the one in the settings, and is remembered by the parameter it
 * was typed under only for this game: a name typed once is not a reason to rename the player
 * for every later game.
 *
 * # Why there is no "new game" button
 *
 * A new game is what this dialog is for, so the card on the home screen starts it and the
 * dialog only chooses the parameters. The button that used to sit here asked the player to
 * confirm the thing they had just asked for.
 */
@Composable
fun PipesNewGameDialog(
    container: AppContainer,
    settings: Settings,
    onDismiss: () -> Unit,
    onStart: (GameInProgress) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val minimum = Rules.pipesMinSize()
    val maximum = Rules.pipesMaxSize()
    var size by remember { mutableStateOf(settings.lastPipesSize.coerceIn(minimum, maximum)) }
    var sizeText by remember { mutableStateOf(size.toString()) }
    var wrapped by remember { mutableStateOf(settings.lastPipesWrapped) }
    var playerName by remember { mutableStateOf(settings.playerName) }

    ModalForm(
        title = stringResource(R.string.dialog_new_game_title),
        onDismiss = onDismiss,
        confirm = {
            ModalConfirm(
                label = stringResource(R.string.dialog_start),
                onClick = { startNew(scope, container, size, wrapped, playerName, onStart) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            OutlinedTextField(
                value = sizeText,
                onValueChange = { typed ->
                    sizeText = typed.filter { it.isDigit() }.take(2)
                    sizeText.toIntOrNull()?.let { typedSize ->
                        if (typedSize in minimum..maximum) {
                            size = typedSize
                        }
                    }
                },
                label = { Text(stringResource(R.string.pipes_size_range, minimum, maximum)) },
                singleLine = true,
                isError = sizeText.toIntOrNull()?.let { it !in minimum..maximum } ?: true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = size.toFloat(),
                onValueChange = {
                    size = it.toInt().coerceIn(minimum, maximum)
                    sizeText = size.toString()
                },
                valueRange = minimum.toFloat()..maximum.toFloat(),
                steps = maximum - minimum - 1,
            )
            Text(stringResource(R.string.pipes_layout), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ChoiceButton(stringResource(R.string.pipes_layout_flat), !wrapped) { wrapped = false }
                ChoiceButton(stringResource(R.string.pipes_layout_wrapped), wrapped) { wrapped = true }
            }
            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text(stringResource(R.string.dialog_player_name)) },
                placeholder = { Text(stringResource(R.string.dialog_player_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Starts a new pipes game and remembers the parameters it was started with. */
private fun startNew(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    size: Int,
    wrapped: Boolean,
    playerName: String,
    onStart: (GameInProgress) -> Unit,
) {
    val board = Rules.newPipes(size, if (wrapped) PipesLayout.Wrapped else PipesLayout.Flat, System.currentTimeMillis()) ?: return
    scope.launch {
        container.settings.setLastPipes(size, wrapped)
        container.records.clearSave(GameKind.Pipes)
    }
    onStart(GameInProgress.Pipes(board, playerName.trim(), 0))
}

/**
 * The dialog that starts a sudoku game.
 *
 * The difficulty is the only parameter, so it is a row of three and no field; the player name
 * works exactly as it does for pipes.
 */
@Composable
fun SudokuNewGameDialog(
    container: AppContainer,
    settings: Settings,
    onDismiss: () -> Unit,
    onStart: (GameInProgress) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var difficulty by remember { mutableStateOf(difficultyFromStorageName(settings.lastSudokuDifficulty)) }
    var playerName by remember { mutableStateOf(settings.playerName) }

    ModalForm(
        title = stringResource(R.string.dialog_new_game_title),
        onDismiss = onDismiss,
        confirm = {
            ModalConfirm(
                label = stringResource(R.string.dialog_start),
                onClick = { startSudoku(scope, container, difficulty, playerName, onStart) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(stringResource(R.string.sudoku_difficulty), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for (option in SudokuDifficulty.entries) {
                    ChoiceButton(difficultyLabel(option), option == difficulty) { difficulty = option }
                }
            }
            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text(stringResource(R.string.dialog_player_name)) },
                placeholder = { Text(stringResource(R.string.dialog_player_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Starts a new sudoku game and remembers the difficulty it was started with. */
private fun startSudoku(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    difficulty: SudokuDifficulty,
    playerName: String,
    onStart: (GameInProgress) -> Unit,
) {
    val board = Rules.newSudoku(difficulty, System.currentTimeMillis())
    scope.launch {
        container.settings.setLastSudokuDifficulty(difficulty.toStorageName())
        container.records.clearSave(GameKind.Sudoku)
    }
    onStart(GameInProgress.Sudoku(board, playerName.trim(), 0))
}
