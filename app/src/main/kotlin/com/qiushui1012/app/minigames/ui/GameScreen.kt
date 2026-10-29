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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.difficultyFromStorageName
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.Record
import com.qiushui1012.app.minigames.data.SavedGame
import com.qiushui1012.app.minigames.data.Settings
import kotlinx.coroutines.launch

/**
 * A game being played.
 *
 * The screen owns the clock and the save: a game is written whenever the application stops
 * being visible, and the clock stops then too. Both live here rather than in the composition
 * of a board, because a rotation must not lose either.
 */
@Composable
fun GameScreen(
    container: AppContainer,
    settings: Settings,
    game: GameInProgress,
    onExit: () -> Unit,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var elapsed by remember { mutableLongStateOf(game.elapsedSeconds) }

    // A finished game must not be written back as an unfinished one. The effect at the end of
    // this function saves on every way out of the screen, including the one taken here, so the
    // flag is what tells it that this exit is a finish.
    var finished by remember { mutableStateOf(false) }

    // The clock is driven by the recomposition of the header rather than by a coroutine that
    // outlives the screen: a game that is not on screen is not being timed, which is what the
    // saved seconds mean.
    androidx.compose.runtime.LaunchedEffect(game) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsed += 1
        }
    }

    fun save() {
        if (finished) {
            return
        }
        val save =
            when (game) {
                is GameInProgress.Pipes ->
                    SavedGame(GameKind.Pipes, game.board.toSnapshot(), game.playerName, elapsed, System.currentTimeMillis())

                is GameInProgress.Sudoku ->
                    SavedGame(GameKind.Sudoku, game.board.toSnapshot(), game.playerName, elapsed, System.currentTimeMillis())
            }
        // A save the player made by leaving must outlive the screen, for the same reason the
        // record does.
        container.scope.launch { container.records.putSave(save) }
    }


    fun finish() {
        if (finished) {
            return
        }
        finished = true
        val record =
            Record(
                key = game.key,
                playerName = game.playerName,
                seconds = elapsed,
                finishedAt = System.currentTimeMillis(),
            )
        // The record is written in the container's scope, not this screen's: leaving the
        // screen cancels a scope the screen owns, and the write would be cancelled with it,
        // which is a game that finishes and leaves no record behind.
        container.scope.launch {
            container.records.addRecord(record)
            container.records.clearSave(game.kind)
        }
        onFinished()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        GameHeader(
            game = game,
            elapsed = elapsed,
            onUndo = {
                when (game) {
                    is GameInProgress.Pipes -> game.board.undo()
                    is GameInProgress.Sudoku -> game.board.undo()
                }
            },
            onBack = {
                save()
                onExit()
            },
        )
        when (game) {
            is GameInProgress.Pipes ->
                PipesPlay(
                    board = game.board,
                    settings = settings,
                    onSolved = ::finish,
                )

            is GameInProgress.Sudoku ->
                SudokuPlay(
                    board = game.board,
                    settings = settings,
                    onSolved = ::finish,
                )
        }
    }

    // Leaving the application, for any reason, writes the game.
    //
    // Two effects, because one event is not enough. `ON_STOP` is the one that fires when the
    // application goes to the background, which covers a Home press, a switch to another
    // application and a system kill that starts from the background; `onDispose` covers the
    // screen being replaced while the application is in front. A save written twice is
    // written twice: it is the same bytes.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle, game) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    save()
                }
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            save()
        }
    }
}

/** The bar above a board: the game's parameters, the player, the clock and undo. */
@Composable
private fun GameHeader(
    game: GameInProgress,
    elapsed: Long,
    onUndo: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.play_back)) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(keyLabelOf(game), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.play_player, game.playerName.ifBlank { stringResource(R.string.records_unknown_player) }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatDuration(elapsed), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onUndo) { Text(stringResource(R.string.play_undo)) }
    }
}

/** Returns the label of the parameters a game is played with. */
@Composable
private fun keyLabelOf(game: GameInProgress): String =
    when (val key = game.key) {
        is com.qiushui1012.app.minigames.data.RecordKey.Pipes ->
            stringResource(
                R.string.home_pipes_summary,
                key.size,
                stringResource(if (key.wrapped) R.string.pipes_layout_wrapped else R.string.pipes_layout_flat),
            )

        is com.qiushui1012.app.minigames.data.RecordKey.Sudoku ->
            stringResource(
                R.string.home_sudoku_summary,
                difficultyLabel(difficultyFromStorageName(key.difficulty)),
            )
    }
