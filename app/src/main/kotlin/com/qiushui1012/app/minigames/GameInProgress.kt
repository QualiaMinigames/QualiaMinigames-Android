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

package com.qiushui1012.app.minigames

import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.RecordKey
import com.qiushui1012.app.minigames.rules.HashiBoard
import com.qiushui1012.app.minigames.rules.MinesweeperBoard
import com.qiushui1012.app.minigames.rules.MinesweeperMode
import com.qiushui1012.app.minigames.rules.MinesweeperPhase
import com.qiushui1012.app.minigames.rules.NonogramBoard
import com.qiushui1012.app.minigames.rules.PipesBoard
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.SudokuBoard
import com.qiushui1012.app.minigames.rules.SudokuDifficulty

/**
 * A game the player is in the middle of.
 *
 * The board is the Rust object, and the rest is what the interface needs to draw it: the
 * game's parameters, the name it is played under, and how long it has taken. The timer is
 * held as a pair of accumulated seconds and a mark rather than as a running value, so that
 * pausing when the application goes to the background costs nothing and a resumed game
 * continues from where it stopped.
 */
sealed interface GameInProgress {
    /** The parameters the game is played with, which is also what its record is filed under. */
    val key: RecordKey

    /** The name the player is playing under, or empty when none was given. */
    val playerName: String

    /** How long the game had taken when it was last paused, in whole seconds. */
    val elapsedSeconds: Long

    /** The board itself. */
    val kind: GameKind

    /** The seed the puzzle was generated from, which a finished game's record keeps. */
    val seed: Long

    /** Whether the seed was typed by the player rather than generated. */
    val typedSeed: Boolean

    /** Whether the clock waits for the first move, which only minesweeper does. */
    val waitsForFirstMove: Boolean get() = false

    /** A pipes game. */
    data class Pipes(
        /** The board. */
        val board: PipesBoard,
        override val playerName: String,
        override val elapsedSeconds: Long,
        override val typedSeed: Boolean = false,
    ) : GameInProgress {
        override val kind: GameKind = GameKind.Pipes

        override val key: RecordKey = RecordKey.Pipes(board.size, board.layout == PipesLayout.Wrapped)

        override val seed: Long get() = board.seed
    }

    /** A sudoku game. */
    data class Sudoku(
        /** The board. */
        val board: SudokuBoard,
        override val playerName: String,
        override val elapsedSeconds: Long,
        override val typedSeed: Boolean = false,
    ) : GameInProgress {
        override val kind: GameKind = GameKind.Sudoku

        override val key: RecordKey = RecordKey.Sudoku(board.difficulty.toStorageName())

        override val seed: Long get() = board.seed
    }

    /**
     * A minesweeper game.
     *
     * The clock waits for the first cell to be opened, which is why the screen reads
     * [waitsForFirstMove]: a board that does not exist yet is not a board a player is playing.
     */
    data class Minesweeper(
        /** The board. */
        val board: MinesweeperBoard,
        override val playerName: String,
        override val elapsedSeconds: Long,
        override val typedSeed: Boolean = false,
    ) : GameInProgress {
        override val kind: GameKind = GameKind.Minesweeper

        // The key is read from the board rather than captured: whether a game fell back is only
        // known after its first click, and a key built before that would file every game as
        // the rules the player asked for rather than the board they played.
        override val key: RecordKey
            get() =
                RecordKey.Minesweeper(
                    width = board.width,
                    height = board.height,
                    mines = board.mineCount,
                    // A game that fell back is filed as the normal board it turned out to be: a
                    // record that claimed the no-guess rules for a board that needed a guess
                    // would be a record of a game nobody played.
                    noGuess = board.mode == MinesweeperMode.NoGuess && !board.fellBack,
                )

        override val seed: Long get() = board.seed

        // A minesweeper board is not laid down until the first cell is opened, so the clock
        // starts with that opening rather than with the screen: a player who is still reading
        // the board is not being timed.
        override val waitsForFirstMove: Boolean get() = board.phase == MinesweeperPhase.Pending
    }

    /** A nonogram game. */
    data class Nonogram(
        /** The board. */
        val board: NonogramBoard,
        override val playerName: String,
        override val elapsedSeconds: Long,
        override val typedSeed: Boolean = false,
    ) : GameInProgress {
        override val kind: GameKind = GameKind.Nonogram

        override val key: RecordKey = RecordKey.Nonogram(board.width, board.height)

        override val seed: Long get() = board.seed
    }

    /** A hashi game. */
    data class Hashi(
        /** The board. */
        val board: HashiBoard,
        override val playerName: String,
        override val elapsedSeconds: Long,
        override val typedSeed: Boolean = false,
    ) : GameInProgress {
        override val kind: GameKind = GameKind.Hashi

        override val key: RecordKey =
            RecordKey.Hashi(board.width, board.height, board.islandCount)

        override val seed: Long get() = board.seed
    }
}

/** Returns the name the difficulty is stored under, which is the rules' own name. */
fun SudokuDifficulty.toStorageName(): String =
    when (this) {
        SudokuDifficulty.Easy -> "easy"
        SudokuDifficulty.Medium -> "medium"
        SudokuDifficulty.Hard -> "hard"
    }

/** Returns the name the mode is stored under, which is the settings' own name. */
fun MinesweeperMode.toStorageName(): String =
    when (this) {
        MinesweeperMode.Normal -> "normal"
        MinesweeperMode.NoGuess -> "no_guess"
    }

/** Returns the mode a stored name names, or normal when it names none. */
fun minesweeperModeFromStorageName(name: String): MinesweeperMode =
    when (name) {
        "no_guess" -> MinesweeperMode.NoGuess
        else -> MinesweeperMode.Normal
    }

/** Returns the difficulty a stored name names, or easy when it names none. */
fun difficultyFromStorageName(name: String): SudokuDifficulty =
    when (name) {
        "medium" -> SudokuDifficulty.Medium
        "hard" -> SudokuDifficulty.Hard
        else -> SudokuDifficulty.Easy
    }
