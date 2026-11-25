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
import com.qiushui1012.app.minigames.DurationFont
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.Play
import com.qiushui1012.app.minigames.data.SavedGame
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.data.append
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

    // A minesweeper board does not exist until its first cell is opened, so its clock waits
    // for that opening; the other two games time the whole visit. The flag is read from the
    // board rather than from a setting, because what says a board has started is the board.
    var started by remember { mutableStateOf(!game.waitsForFirstMove) }

    // The mines a game's flags leave unaccounted for, which the header draws beside the clock
    // for the one game that counts them. It is held here because the header sits above the
    // board's composition and has to follow a flag the board has just placed; the board
    // reports every change through the callback below.
    val minesweeper = game as? GameInProgress.Minesweeper
    var remainingMines by remember(game) { mutableStateOf(minesweeper?.board?.remainingMines ?: 0) }
    var mineCount by remember(game) { mutableStateOf(minesweeper?.board?.mineCount ?: 0) }

    // The mistakes an arrow cube board has counted, held here for the same reason: the header
    // is above the board and has to follow a take the board has just made.
    val arrowCube = game as? GameInProgress.ArrowCube
    var mistakes by remember(game) { mutableStateOf(arrowCube?.board?.mistakes ?: 0) }

    // The clock is driven by the recomposition of the header rather than by a coroutine that
    // outlives the screen: a game that is not on screen is not being timed, which is what the
    // saved seconds mean. A finished game stops it too, which matters for the board that stays
    // on screen after a mine: a clock still running over a game that is already recorded would
    // put one time in the header and another in the record.
    androidx.compose.runtime.LaunchedEffect(game, started, finished) {
        while (started && !finished) {
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

                is GameInProgress.Minesweeper ->
                    SavedGame(GameKind.Minesweeper, game.board.toSnapshot(), game.playerName, elapsed, System.currentTimeMillis())

                is GameInProgress.Nonogram ->
                    SavedGame(GameKind.Nonogram, game.board.toSnapshot(), game.playerName, elapsed, System.currentTimeMillis())

                is GameInProgress.Hashi ->
                    SavedGame(GameKind.Hashi, game.board.toSnapshot(), game.playerName, elapsed, System.currentTimeMillis())

                is GameInProgress.ArrowCube ->
                    SavedGame(GameKind.ArrowCube, game.board.toSnapshot(), game.playerName, elapsed, System.currentTimeMillis())
            }
        // A save the player made by leaving must outlive the screen, for the same reason the
        // record does.
        container.scope.launch { container.records.putSave(save) }
    }

    /**
     * Writes the record and clears the save.
     *
     * [navigate] says whether the screen then leaves for the records page. A cleared board
     * does, because there is nothing left to look at; a board lost on a mine does not, because
     * the player asked to see where the mines were and a screen that jumped away would take
     * that answer back before it was read. The board itself refuses every later tap, and the
     * flag below keeps the save from being written when the player does leave.
     */
    fun finish(failed: Boolean = false, navigate: Boolean = true) {
        if (finished) {
            return
        }
        finished = true
        // The play carries the facts an older record kept as fields of its own: who played,
        // how long it took, when it finished, which puzzle it was and how it started. The
        // game's parameters, which decide the group, come from the board instead.
        val play =
            Play(
                playerName = game.playerName,
                seconds = elapsed,
                finishedAt = System.currentTimeMillis(),
                seed = game.seed,
                failed = failed,
                typedSeed = game.typedSeed,
                mistakes = (game as? GameInProgress.ArrowCube)?.board?.mistakes,
            )
        val group = game.key.append(play)
        // The record is written in the container's scope, not this screen's: leaving the
        // screen cancels a scope the screen owns, and the write would be cancelled with it,
        // which is a game that finishes and leaves no record behind. Adding the play also
        // clears the game's save, so the two states cannot disagree after a crash.
        container.scope.launch { container.records.addPlay(group) }
        if (navigate) {
            onFinished()
        }
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
            mines = if (minesweeper != null) remainingMines else null,
            mineTotal = mineCount,
            mistakes = if (arrowCube != null) mistakes else null,
            onUndo = {
                when (game) {
                    is GameInProgress.Pipes -> game.board.undo()
                    is GameInProgress.Sudoku -> game.board.undo()
                    // Minesweeper has no undo: a flag is cheap to take back and an opening is
                    // a decision, so the header draws no undo button for it at all.
                    is GameInProgress.Minesweeper -> Unit
                    // A nonogram cell toggles back where it was and a hashi bridge cycles
                    // through zero, so neither needs an undo step.
                    is GameInProgress.Nonogram -> Unit
                    is GameInProgress.Hashi -> Unit
                    // An arrow cube take is final: the header draws no undo button for it.
                    is GameInProgress.ArrowCube -> Unit
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

            is GameInProgress.Minesweeper ->
                MinesweeperPlay(
                    board = game.board,
                    onStarted = { started = true },
                    onCountersChanged = { left, total ->
                        remainingMines = left
                        mineCount = total
                    },
                    onWon = { finish() },
                    // The board stays on screen with its mines shown: the player leaves by
                    // pressing back, which is the one way out of a board that has ended.
                    onLost = { finish(failed = true, navigate = false) },
                )

            is GameInProgress.Nonogram ->
                NonogramPlay(
                    board = game.board,
                    settings = settings,
                    onSolved = ::finish,
                )

            is GameInProgress.Hashi ->
                HashiPlay(
                    board = game.board,
                    settings = settings,
                    onSolved = ::finish,
                )

            is GameInProgress.ArrowCube ->
                ArrowCubePlay(
                    board = game.board,
                    onMistakes = { mistakes = it },
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

/**
 * The bar above a board: the game's parameters, the player, the clock, the mines left and undo.
 *
 * [mines] and [mineTotal] are the two numbers the header shows for minesweeper and are null
 * for the games that do not count them, which is why the counter is a slot rather than a
 * second header: the bar is one row and a game that has nothing to put in it leaves it empty.
 */
@Composable
private fun GameHeader(
    game: GameInProgress,
    elapsed: Long,
    mines: Int?,
    mineTotal: Int,
    mistakes: Int?,
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
        if (mines != null) {
            // The counter sits to the left of the clock, which is the order a minesweeper
            // player reads them in: how much is left, then how long it has taken.
            Text(
                stringResource(R.string.minesweeper_mines_counter, mines, mineTotal),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = DurationFont,
            )
        }
        if (mistakes != null) {
            // The same place the minesweeper counter takes, for the same reason: it is a
            // count about the game, read before the clock that says how long it has taken.
            Text(
                stringResource(R.string.arrow_cube_mistakes, mistakes),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = DurationFont,
            )
        }
        Text(formatDuration(elapsed), style = MaterialTheme.typography.titleMedium, fontFamily = DurationFont)
        when (game) {
            // Minesweeper has no undo, so the header offers none: a button that did nothing
            // would be a control the player would press and then wonder about. Nonogram,
            // hashi and arrow cube are the same: a tap is final, or undoes itself where it
            // stands.
            is GameInProgress.Minesweeper -> Unit
            is GameInProgress.Nonogram -> Unit
            is GameInProgress.Hashi -> Unit
            is GameInProgress.ArrowCube -> Unit
            else -> TextButton(onClick = onUndo) { Text(stringResource(R.string.play_undo)) }
        }
    }
}

/** Returns the label of the parameters a game is played with. */
@Composable
private fun keyLabelOf(game: GameInProgress): String = game.key.keyLabel()
