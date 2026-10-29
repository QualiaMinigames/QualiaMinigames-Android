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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.difficultyFromStorageName
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.SavedGame
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.Rules
import com.qiushui1012.app.minigames.rules.SudokuDifficulty
import com.qiushui1012.app.minigames.toStorageName

/**
 * The games and the entries that start them.
 *
 * One card per game. A card shows the parameters the next game would start with, which are
 * the ones the last game was started with, and says whether an unfinished one is waiting.
 * Tapping it opens the dialog that chooses the parameters and either starts a new game or
 * continues the saved one.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    settings: Settings,
    onStartGame: (GameInProgress) -> Unit,
) {
    val pipesSave by container.records.save(GameKind.Pipes).collectAsStateWithLifecycle(initialValue = null)
    val sudokuSave by container.records.save(GameKind.Sudoku).collectAsStateWithLifecycle(initialValue = null)
    var dialog by remember { mutableStateOf<GameKind?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        ScreenHeader(stringResource(R.string.home_title))

        GameEntryCard(
            name = stringResource(R.string.home_pipes_name),
            summary =
                stringResource(
                    R.string.home_pipes_summary,
                    settings.lastPipesSize,
                    stringResource(
                        if (settings.lastPipesWrapped) R.string.pipes_layout_wrapped else R.string.pipes_layout_flat,
                    ),
                ),
            hasSave = pipesSave != null,
            onClick = { dialog = GameKind.Pipes },
        )
        GameEntryCard(
            name = stringResource(R.string.home_sudoku_name),
            summary =
                stringResource(
                    R.string.home_sudoku_summary,
                    difficultyLabel(difficultyFromStorageName(settings.lastSudokuDifficulty)),
                ),
            hasSave = sudokuSave != null,
            onClick = { dialog = GameKind.Sudoku },
        )
    }

    when (dialog) {
        GameKind.Pipes ->
            PipesNewGameDialog(
                container = container,
                settings = settings,
                save = pipesSave,
                onDismiss = { dialog = null },
                onStart = {
                    dialog = null
                    onStartGame(it)
                },
            )

        GameKind.Sudoku ->
            SudokuNewGameDialog(
                container = container,
                settings = settings,
                save = sudokuSave,
                onDismiss = { dialog = null },
                onStart = {
                    dialog = null
                    onStartGame(it)
                },
            )

        null -> Unit
    }
    }
}

/** One game's entry: its name, its parameters, and whether a game is waiting. */
@Composable
private fun GameEntryCard(
    name: String,
    summary: String,
    hasSave: Boolean,
    onClick: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasSave) {
                    Text(
                        stringResource(R.string.home_can_continue),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            androidx.compose.material3.Button(onClick = onClick) {
                Text(stringResource(R.string.pipes_new_game))
            }
        }
    }
}

/** Returns the label of a difficulty. */
@Composable
fun difficultyLabel(difficulty: SudokuDifficulty): String =
    stringResource(
        when (difficulty) {
            SudokuDifficulty.Easy -> R.string.sudoku_difficulty_easy
            SudokuDifficulty.Medium -> R.string.sudoku_difficulty_medium
            SudokuDifficulty.Hard -> R.string.sudoku_difficulty_hard
        },
    )

/** Returns the label of a layout. */
@Composable
fun layoutLabel(layout: PipesLayout): String =
    stringResource(if (layout == PipesLayout.Wrapped) R.string.pipes_layout_wrapped else R.string.pipes_layout_flat)

/**
 * Rebuilds a saved game.
 *
 * Returns null when the bytes cannot be read, which is what a save written by a build whose
 * format differs looks like. The caller reports that rather than starting a game the player
 * did not ask for.
 */
fun restoreGame(save: SavedGame): GameInProgress? =
    when (save.kind) {
        GameKind.Pipes ->
            Rules.pipesFromSnapshot(save.snapshot)?.let { board ->
                GameInProgress.Pipes(board, save.playerName, save.seconds)
            }

        GameKind.Sudoku ->
            Rules.sudokuFromSnapshot(save.snapshot)?.let { board ->
                GameInProgress.Sudoku(board, save.playerName, save.seconds)
            }
    }

/** Returns the rules' version, or an empty string when the library cannot be loaded. */
fun rulesVersionOrEmpty(): String =
    try {
        Rules.version()
    } catch (_: Throwable) {
        ""
    }