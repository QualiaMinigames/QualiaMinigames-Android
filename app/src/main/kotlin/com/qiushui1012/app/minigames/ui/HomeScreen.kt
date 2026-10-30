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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qiushui1012.app.minigames.AlertDark
import com.qiushui1012.app.minigames.AlertLight
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.difficultyFromStorageName
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.RecordKey
import com.qiushui1012.app.minigames.data.SavedGame
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.minesweeperModeFromStorageName
import com.qiushui1012.app.minigames.rules.MinesweeperMode
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.Rules
import com.qiushui1012.app.minigames.rules.SudokuDifficulty

/** The density a card shows when the settings hold a value the field would have refused. */
private const val DEFAULT_MINESWEEPER_PERCENT = 123/**
 * The narrowest a game card may be drawn before the grid uses fewer columns.
 *
 * A phone held upright is about 400 dp across, and its two cards side by side are about 185 dp
 * each — narrow, but a card is a mark, a name and two buttons, and all three fit. The threshold
 * is therefore below that rather than at a tablet's card width: a grid of one card per row on a
 * phone leaves most of the screen empty, which is what it used to do.
 */
private val GameCardMinWidth = 168.dp

/**
 * The size of a game's mark on its card.
 *
 * The mark is the largest thing on the card, and deliberately so: it is what a player
 * recognises the game by, and the name is read once. It is a size rather than a fraction of the
 * card because the cards are laid out in columns of different widths, and a mark that grew with
 * the column would make two cards of one grid disagree.
 */
private val GameIconSize = 140.dp

/**
 * The games and the entries that start them.
 *
 * One card per game: its icon, its name, the parameters the next game would start with, and the
 * two ways forward. The parameters are the ones the last game was started with.
 *
 * # Why the card has the buttons
 *
 * Continuing and starting are two different intentions, and a card with a single button made
 * the player answer a dialog to find out which one they were about to do. Continuing uses the
 * save directly; starting opens the parameters, and when a save exists it asks first, because
 * the save is a game in progress and discarding it is not something to do by mis-tap.
 *
 * The buttons are only shown when they mean something: no save, no continue.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    settings: Settings,
    onContinue: (GameInProgress) -> Unit,
    onNewGame: (GameKind) -> Unit,
    onDiscard: (GameKind) -> Unit,
) {
    val pipesSave by container.records.save(GameKind.Pipes).collectAsStateWithLifecycle(initialValue = null)
    val sudokuSave by container.records.save(GameKind.Sudoku).collectAsStateWithLifecycle(initialValue = null)
    val minesweeperSave by container.records.save(GameKind.Minesweeper).collectAsStateWithLifecycle(initialValue = null)

    // The minesweeper card's summary is read back from the parameters the settings kept, so
    // that the card and the dialog that produced it cannot describe the same game in two
    // ways. A value the field would have refused is read as the default, because a corrupt
    // setting must not draw a card that says nothing.
    val minesweeperPercent = settings.lastMinesweeperPercent.toIntOrNull() ?: DEFAULT_MINESWEEPER_PERCENT
    val minesweeperCells = settings.lastMinesweeperWidth * settings.lastMinesweeperHeight
    val minesweeperMines =
        if (minesweeperCells > 0) {
            Rules.minesweeperMinesForPerMille(minesweeperCells, minesweeperPercent)
        } else {
            0
        }

    val entries =
        listOf(
            GameEntry(
                kind = GameKind.Pipes,
                name = stringResource(R.string.home_pipes_name),
                summary = keyLabel(RecordKey.Pipes(settings.lastPipesSize, settings.lastPipesWrapped)),
                save = pipesSave,
            ),
            GameEntry(
                kind = GameKind.Sudoku,
                name = stringResource(R.string.home_sudoku_name),
                summary = keyLabel(RecordKey.Sudoku(settings.lastSudokuDifficulty)),
                save = sudokuSave,
            ),
            GameEntry(
                kind = GameKind.Minesweeper,
                name = stringResource(R.string.home_minesweeper_name),
                summary =
                    keyLabel(
                        RecordKey.Minesweeper(
                            width = settings.lastMinesweeperWidth,
                            height = settings.lastMinesweeperHeight,
                            mines = minesweeperMines,
                            noGuess = minesweeperModeFromStorageName(settings.lastMinesweeperMode) == MinesweeperMode.NoGuess,
                        ),
                    ),
                save = minesweeperSave,
            ),
        )

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // A card keeps at least [GameCardMinWidth] and the grid fills the window with them:
            // two side by side on a phone held upright and three on a window wide enough for
            // it. The last row is padded with spacers of the same weight, so the cards of a
            // row are the same width whether the row is full or not.
            val columns = (maxWidth / GameCardMinWidth).toInt().coerceIn(2.coerceAtMost(entries.size), entries.size)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.l, vertical = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                ScreenHeader(stringResource(R.string.home_title))
                for (row in entries.chunked(columns)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        for (entry in row) {
                            Box(Modifier.weight(1f)) {
                                GameEntryCard(
                                    entry = entry,
                                    onContinue = onContinue,
                                    onNewGame = onNewGame,
                                    onDiscard = onDiscard,
                                )
                            }
                        }
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                // The navigation floats over the narrow layout and this page has no inset to
                // leave for it: two cards are short enough that the island covers nothing.
                Box(modifier = Modifier.padding(bottom = Space.xl))
            }
        }
    }
}

/** One game's entry, before its buttons decide anything. */
private data class GameEntry(
    /** Which game it is. */
    val kind: GameKind,
    /** Its name in the interface's language. */
    val name: String,
    /** The parameters the next game would start with. */
    val summary: String,
    /** The unfinished game of this kind, if there is one. */
    val save: SavedGame?,
)

/**
 * One game's card: the icon at the top, its name with its parameters beside it, then the two
 * buttons.
 *
 * The icon sits alone above the text rather than beside it, so that the two cards line up
 * whatever their names and parameters are and so that a card in a grid column is read from its
 * picture down to its buttons. The parameters stand beside the name rather than under it: the
 * name is the title of the card and the parameters qualify it, which is one line's worth of
 * reading.
 */
@Composable
private fun GameEntryCard(
    entry: GameEntry,
    onContinue: (GameInProgress) -> Unit,
    onNewGame: (GameKind) -> Unit,
    onDiscard: (GameKind) -> Unit,
) {
    // A save that cannot be restored is not one this build can continue. It is reported rather
    // than removed: the bytes are another version's game, and a build that cannot read them is
    // in no position to decide they are worthless.
    val resumed = remember(entry.save) { entry.save?.let(::restoreGame) }
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            GameIcon(entry.kind)
            // The title and its parameters wrap as a unit: in a narrow column they sit on two
            // lines rather than being squeezed side by side, and each stays whole. Where they do
            // share a line they share a baseline, so the smaller text does not float against the
            // larger.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Text(entry.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.save != null && resumed == null) {
                Text(
                    stringResource(R.string.dialog_restore_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // The buttons wrap rather than squeeze: in a narrow column two of them side by side
            // would each be narrower than their own label, and a button with a wrapped label is
            // taller than the row holding it.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // Only a game that can be resumed offers the button: a "continue" over a save
                // that will not open is a control that does nothing.
                if (resumed != null) {
                    Button(onClick = { onContinue(resumed) }) {
                        Text(stringResource(R.string.pipes_continue))
                    }
                }
                TextButton(
                    onClick = {
                        if (entry.save != null) {
                            onDiscard(entry.kind)
                        } else {
                            onNewGame(entry.kind)
                        }
                    },
                ) {
                    Text(stringResource(R.string.pipes_new_game))
                }
            }
        }
    }
}

/**
 * The mark of a game, drawn above its name.
 *
 * The drawing is the game's own file in `assets/`: `pipes.svg`, `sudoku.svg` and
 * `minesweeper.svg`. A mark is not the same thing as a board cell, so it is read here rather
 * than from [PipeArt], whose files are the pieces a board is made of; and it is read rather
 * than copied into code, so an edited drawing changes the card without a second record of its
 * geometry.
 *
 * Each band is drawn in the tone its file gives it, resolved against the theme: the ink is the
 * theme's foreground, the surface is the card's own colour, the alert is the red a board uses
 * for a loop, and the neutral is the secondary surface. That is what makes one drawing legible
 * in both schemes and what lets the minesweeper mark keep its red and its grey.
 */
@Composable
private fun GameIcon(kind: GameKind) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tones =
        mapOf(
            GameArt.Tone.Ink to MaterialTheme.colorScheme.onSurface,
            GameArt.Tone.Surface to MaterialTheme.colorScheme.surface,
            GameArt.Tone.Alert to if (dark) AlertDark else AlertLight,
            GameArt.Tone.Neutral to MaterialTheme.colorScheme.surfaceVariant,
        )
    val bands = remember(context, kind) { GameArt.of(context, kind) }
    Canvas(modifier = Modifier.size(GameIconSize)) {
        val scale = size.minDimension / GameArt.SIZE
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            for (band in bands) {
                band.fill?.let { tone ->
                    drawPath(band.path, color = tones.getValue(tone), style = Fill)
                }
                band.stroke?.let { tone ->
                    drawPath(
                        band.path,
                        color = tones.getValue(tone),
                        style = Stroke(width = band.strokeWidth, join = StrokeJoin.Miter),
                    )
                }
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

        GameKind.Minesweeper ->
            Rules.minesweeperFromSnapshot(save.snapshot)?.let { board ->
                GameInProgress.Minesweeper(board, save.playerName, save.seconds)
            }
    }

/** Returns the rules' version, or an empty string when the library cannot be loaded. */
fun rulesVersionOrEmpty(): String =
    try {
        Rules.version()
    } catch (_: Throwable) {
        ""
    }
