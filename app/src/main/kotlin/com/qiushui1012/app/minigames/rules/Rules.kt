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

package com.qiushui1012.app.minigames.rules

import com.qiushui1012.app.minigames.ffi.BridgeInfo as FfiBridgeInfo
import com.qiushui1012.app.minigames.ffi.ClueLine as FfiClueLine
import com.qiushui1012.app.minigames.ffi.HashiGame as FfiHashiGame
import com.qiushui1012.app.minigames.ffi.IslandInfo as FfiIslandInfo
import com.qiushui1012.app.minigames.ffi.MinesweeperEvent as FfiMinesweeperEvent
import com.qiushui1012.app.minigames.ffi.MinesweeperGame as FfiMinesweeperGame
import com.qiushui1012.app.minigames.ffi.MinesweeperMode as FfiMinesweeperMode
import com.qiushui1012.app.minigames.ffi.MinesweeperPhase as FfiMinesweeperPhase
import com.qiushui1012.app.minigames.ffi.NonogramGame as FfiNonogramGame
import com.qiushui1012.app.minigames.ffi.PipesGame as FfiPipesGame
import com.qiushui1012.app.minigames.ffi.PipesLayout as FfiPipesLayout
import com.qiushui1012.app.minigames.ffi.SudokuDifficulty as FfiSudokuDifficulty
import com.qiushui1012.app.minigames.ffi.SudokuGame as FfiSudokuGame
import com.qiushui1012.app.minigames.ffi.dailySeed as ffiDailySeed
import com.qiushui1012.app.minigames.ffi.coreVersion as ffiCoreVersion
import com.qiushui1012.app.minigames.ffi.hashiIslandPerMilleMax as ffiHashiIslandPerMilleMax
import com.qiushui1012.app.minigames.ffi.hashiIslandPerMilleMin as ffiHashiIslandPerMilleMin
import com.qiushui1012.app.minigames.ffi.hashiIslandSnapPerMille as ffiHashiIslandSnapPerMille
import com.qiushui1012.app.minigames.ffi.hashiIslandsForPerMille as ffiHashiIslandsForPerMille
import com.qiushui1012.app.minigames.ffi.hashiMaxPoints as ffiHashiMaxPoints
import com.qiushui1012.app.minigames.ffi.hashiMinPoints as ffiHashiMinPoints
import com.qiushui1012.app.minigames.ffi.minesweeperInputPerMilleMax as ffiMinesweeperInputPerMilleMax
import com.qiushui1012.app.minigames.ffi.minesweeperInputPerMilleMin as ffiMinesweeperInputPerMilleMin
import com.qiushui1012.app.minigames.ffi.minesweeperMaxSize as ffiMinesweeperMaxSize
import com.qiushui1012.app.minigames.ffi.minesweeperMinSize as ffiMinesweeperMinSize
import com.qiushui1012.app.minigames.ffi.minesweeperMinesForPerMille as ffiMinesweeperMinesForPerMille
import com.qiushui1012.app.minigames.ffi.minesweeperNoguessInputPerMilleMax as ffiMinesweeperNoguessInputPerMilleMax
import com.qiushui1012.app.minigames.ffi.minesweeperNoguessSliderPerMilleMax as ffiMinesweeperNoguessSliderPerMilleMax
import com.qiushui1012.app.minigames.ffi.minesweeperNoguessWarnPerMille as ffiMinesweeperNoguessWarnPerMille
import com.qiushui1012.app.minigames.ffi.minesweeperSliderPerMilleMax as ffiMinesweeperSliderPerMilleMax
import com.qiushui1012.app.minigames.ffi.minesweeperSliderPerMilleMin as ffiMinesweeperSliderPerMilleMin
import com.qiushui1012.app.minigames.ffi.minesweeperSnapPerMille as ffiMinesweeperSnapPerMille
import com.qiushui1012.app.minigames.ffi.nonogramMaxSize as ffiNonogramMaxSize
import com.qiushui1012.app.minigames.ffi.nonogramMinSize as ffiNonogramMinSize
import com.qiushui1012.app.minigames.ffi.pipesMaxSize as ffiPipesMaxSize
import com.qiushui1012.app.minigames.ffi.pipesMinSize as ffiPipesMinSize
import com.qiushui1012.app.minigames.ffi.seedFromText as ffiSeedFromText
import com.qiushui1012.app.minigames.ffi.sideBottom as ffiSideBottom
import com.qiushui1012.app.minigames.ffi.sideLeft as ffiSideLeft
import com.qiushui1012.app.minigames.ffi.sideRight as ffiSideRight
import com.qiushui1012.app.minigames.ffi.sideTop as ffiSideTop
import com.qiushui1012.app.minigames.ffi.sudokuTryGenerate as ffiSudokuTryGenerate
import com.qiushui1012.app.minigames.ffi.uniffiEnsureInitialized

/**
 * The one place the generated bindings are touched.
 *
 * Everything above this file sees ordinary Kotlin values: a board is a handle with methods,
 * a saved game is a byte array. The generated types stay here so that a change in the
 * generator — a renamed method, a shifted package — is a change in one file rather than in
 * every screen. The imports above are aliased for that reason: a wrapper function that
 * called its own name would resolve to itself, compile, and overflow the stack at runtime.
 *
 * The bindings are generated at build time from the Rust library by `uniffi-bindgen`; see
 * `app/build.gradle.kts`.
 */

/** Which of the two layouts a pipes board uses. */
enum class PipesLayout {
    /** A flat board: a cell on an edge has no neighbour beyond it. */
    Flat,

    /** A wrapped board: the opposite edges are joined. */
    Wrapped,
}

/** How hard a sudoku puzzle is. */
enum class SudokuDifficulty {
    /** Solvable with singles alone. */
    Easy,

    /** Needs a locked candidate or a naked pair. */
    Medium,

    /** Needs a hidden pair. */
    Hard,
}

/** Which rules a minesweeper board is generated under. */
enum class MinesweeperMode {
    /** A board a player may have to guess on. */
    Normal,

    /** A board two deductions can finish, when one is found within the attempt budget. */
    NoGuess,
}

/** Where a minesweeper game stands. */
enum class MinesweeperPhase {
    /** The board has not been generated yet, because no cell has been opened. */
    Pending,

    /** The board is generated and the game is being played. */
    Playing,

    /** Every cell that holds no mine is open. */
    Won,

    /** A mine was opened. */
    Lost,
}

/** What opening a minesweeper cell did. */
enum class MinesweeperEvent {
    /** The cell and possibly a flood of its neighbours opened. */
    Revealed,

    /** The cell held a mine, which ended the game. */
    HitMine,

    /** The last cell that could be opened was opened. */
    Won,

    /** The cell could not be opened. */
    Ignored,
}

/** The sides a pipe piece can join, as bits of a mask. */
object PipesSide {
    /** The top side. */
    val TOP: Int = ffiSideTop().toInt()

    /** The right side. */
    val RIGHT: Int = ffiSideRight().toInt()

    /** The bottom side. */
    val BOTTOM: Int = ffiSideBottom().toInt()

    /** The left side. */
    val LEFT: Int = ffiSideLeft().toInt()
}

/** One game of pipes. */
interface PipesBoard {
    /** The number of cells on a side. */
    val size: Int

    /** The layout the board uses. */
    val layout: PipesLayout

    /** The seed the puzzle was generated from. */
    val seed: Long

    /** Returns the side mask a cell shows now. */
    fun piece(index: Int): Int

    /**
     * Returns every cell's side mask at once, in row-major order.
     *
     * A board that is drawn asks for all of them in one frame, and one call per cell is one
     * crossing of the language boundary per cell: 1225 of them for the largest board, which
     * costs more than the drawing they feed.
     */
    fun pieces(): List<Int>

    /** Turns a cell a quarter turn clockwise. */
    fun turnClockwise(index: Int)

    /** Turns a cell a quarter turn anti-clockwise. */
    fun turnAntiClockwise(index: Int)

    /** Takes back the last turn, reporting whether there was one. */
    fun undo(): Boolean

    /** Returns whether the board is one network without a loop. */
    fun isSolved(): Boolean

    /** Returns the cells on a loop, in ascending order. */
    fun loopCells(): List<Int>

    /** Returns the cells water reaches, in ascending order. */
    fun waterCells(): List<Int>

    /** Returns the cell the pump stands on, which is where water enters the board. */
    fun waterStart(): Int

    /** Writes the game, so it can be saved. */
    fun toSnapshot(): ByteArray
}

/** One game of sudoku. */
interface SudokuBoard {
    /** The difficulty the puzzle was generated for. */
    val difficulty: SudokuDifficulty

    /** The seed the puzzle was generated from. */
    val seed: Long

    /** Returns the digit a cell was given as, or zero for an empty cell. */
    fun given(index: Int): Int

    /** Returns the digit a cell holds now, or zero for an empty cell. */
    fun digit(index: Int): Int

    /** Returns the candidates noted in a cell, as a mask of the digits `1` to `9`. */
    fun notes(index: Int): Int

    /** Returns whether a cell was given. */
    fun isGiven(index: Int): Boolean

    /** Enters a digit into a cell. */
    fun enter(index: Int, digit: Int)

    /** Clears a cell the player filled. */
    fun clear(index: Int)

    /** Adds or removes a candidate in an empty cell. */
    fun toggleNote(index: Int, digit: Int)

    /** Takes back the last move, reporting whether there was one. */
    fun undo(): Boolean

    /** Returns the cells that break a rule, in ascending order. */
    fun conflictCells(): List<Int>

    /** Returns whether the board is filled and every rule holds. */
    fun isSolved(): Boolean

    /** Writes the game, so it can be saved. */
    fun toSnapshot(): ByteArray
}

/** One game of minesweeper. */
interface MinesweeperBoard {
    /** The number of cells across. */
    val width: Int

    /** The number of cells down. */
    val height: Int

    /** The number of cells on the board. */
    val cellCount: Int

    /** How many mines the board holds. */
    val mineCount: Int

    /** How many cells are flagged. */
    val flagCount: Int

    /** How many mines the flags leave unaccounted for, which may be negative. */
    val remainingMines: Int

    /** Where the game stands. */
    val phase: MinesweeperPhase

    /** The rules the board was asked for. */
    val mode: MinesweeperMode

    /** Whether a no-guess board could not be found and the game fell back to normal. */
    val fellBack: Boolean

    /** The seed the board was generated from, or zero while none has been. */
    val seed: Long

    /**
     * Returns what a cell shows now.
     *
     * The value is the neighbouring mine count for an open cell that holds none, and
     * [MinesweeperCell.COVERED], [MinesweeperCell.FLAGGED] or [MinesweeperCell.MINE] for the
     * other states.
     */
    fun cell(index: Int): Int

    /**
     * Returns every cell's state at once, in row-major order.
     *
     * A board of 128 by 128 cells is 16384 crossings of the language boundary if it is read
     * one cell at a time, which costs more than the drawing it feeds.
     */
    fun cellsState(): List<Int>

    /** Opens a cell and reports what it did. */
    fun reveal(index: Int): MinesweeperEvent

    /** Flags a covered cell, or takes its flag off, reporting whether it changed. */
    fun toggleFlag(index: Int): Boolean

    /** Writes the game, so it can be saved. */
    fun toSnapshot(): ByteArray
}

/** The values a minesweeper cell takes, as the rules report them. */
object MinesweeperCell {
    /** The largest neighbouring mine count an open cell shows. */
    const val MAX_ADJACENT: Int = 8

    /** A covered cell. */
    const val COVERED: Int = 9

    /** A flagged cell. */
    const val FLAGGED: Int = 10

    /** An open cell that holds a mine. */
    const val MINE: Int = 11
}

/** The values a nonogram cell takes, as the rules report them. */
object NonogramCell {
    /** A cell the player has left alone. */
    const val EMPTY: Int = 0

    /** A cell the player filled. */
    const val FILLED: Int = 1

    /** A cell the player marked as staying empty. */
    const val MARKED: Int = 2
}

/** One line's clue, as a nonogram reads it. */
data class ClueLine(
    /** The run lengths of the filled cells, in the order they appear along the line. */
    val values: List<Int>,
)

/** One island of a hashi board. */
data class HashiIsland(
    /** The row the island stands in. */
    val row: Int,

    /** The column the island stands in. */
    val column: Int,

    /** The number of bridges the island has to end up with. */
    val clue: Int,

    /** The number of bridges the island has now. */
    val degree: Int,

    /** Whether the player marked the island as solved. */
    val marked: Boolean,
)

/** One pair of joined islands of a hashi board. */
data class HashiBridge(
    /** The smaller island index. */
    val a: Int,

    /** The larger island index. */
    val b: Int,

    /** How many bridges join them, one or two. */
    val count: Int,
)

/** One game of nonogram. */
interface NonogramBoard {
    /** The number of cells across. */
    val width: Int

    /** The number of cells down. */
    val height: Int

    /** The number of cells on the board. */
    val cellCount: Int

    /** The seed the board was drawn from. */
    val seed: Long

    /** Whether no unique board was found and an ambiguous one is being played. */
    val fellBack: Boolean

    /** Returns the clue of each row, top to bottom. */
    fun rowClues(): List<ClueLine>

    /** Returns the clue of each column, left to right. */
    fun columnClues(): List<ClueLine>

    /**
     * Returns every cell's state at once, in row-major order.
     *
     * The values are [NonogramCell.EMPTY], [NonogramCell.FILLED] and [NonogramCell.MARKED].
     * A board of 64 by 64 cells is 4096 crossings of the language boundary if it is read one
     * cell at a time, which costs more than the drawing it feeds.
     */
    fun cellsState(): List<Int>

    /** Returns the rows whose clues the player's cells can no longer match. */
    fun brokenRows(): List<Int>

    /** Returns the columns whose clues the player's cells can no longer match. */
    fun brokenColumns(): List<Int>

    /** Turns a cell filled, or back to empty, reporting whether it changed. */
    fun toggleFilled(index: Int): Boolean

    /** Turns a cell marked, or back to empty, reporting whether it changed. */
    fun toggleMarked(index: Int): Boolean

    /** Returns whether every row and column runs exactly as its clue says. */
    fun isSolved(): Boolean

    /** Writes the game, so it can be saved. */
    fun toSnapshot(): ByteArray
}

/** One game of hashi. */
interface HashiBoard {
    /** The number of points across. */
    val width: Int

    /** The number of points down. */
    val height: Int

    /** How many islands the board holds. */
    val islandCount: Int

    /** The seed the board was generated from. */
    val seed: Long

    /** Returns every island, in ascending point order. */
    fun islands(): List<HashiIsland>

    /**
     * Returns every pair of islands that has a bridge, in ascending pair order.
     *
     * The order is fixed, so a screen can redraw from one call without comparing it against
     * the previous frame.
     */
    fun bridges(): List<HashiBridge>

    /** Cycles the bridges between two islands, reporting whether the board changed. */
    fun connect(first: Int, second: Int): Boolean

    /** Turns the player's solved mark on an island off or on, reporting whether it changed. */
    fun toggleMark(index: Int): Boolean

    /**
     * Returns the islands that can no longer reach their clue.
     *
     * The screen paints these red. An island is stranded when it carries more bridges than its
     * clue, or when the bridges it still needs cannot all be laid in the directions it has left.
     */
    fun strandedIslands(): List<Int>

    /** Returns whether every island carries its clue and the bridges join them all. */
    fun isSolved(): Boolean

    /** Writes the game, so it can be saved. */
    fun toSnapshot(): ByteArray
}

/**
 * The rules the application plays by.
 *
 * Every member forwards to the Rust library. The object exists so that loading the library
 * happens once, at first use, and so that the failure of that load has one place to be
 * reported from.
 */
object Rules {
    /** The number of cells on a side of a sudoku board. */
    const val SUDOKU_SIZE: Int = 9

    /** The number of cells on a sudoku board. */
    const val SUDOKU_CELLS: Int = 81

    init {
        // The generated bindings register their converters and load the library here. Doing
        // it in an initialiser means the first board a screen asks for pays the cost, and a
        // library that cannot be loaded fails at that point rather than at an arbitrary
        // later one.
        uniffiEnsureInitialized()
    }

    /** Returns the version of the rules the application was built against. */
    fun version(): String = ffiCoreVersion()

    /** Returns the smallest pipes board, in cells on a side. */
    fun pipesMinSize(): Int = ffiPipesMinSize().toInt()

    /** Returns the largest pipes board, in cells on a side. */
    fun pipesMaxSize(): Int = ffiPipesMaxSize().toInt()

    /**
     * Generates a pipes puzzle.
     *
     * Returns null when the parameters are outside their range, which a screen validates
     * before it calls this: a size of zero is a programming error rather than a state a
     * player reaches.
     */
    fun newPipes(size: Int, layout: PipesLayout, seed: Long): PipesBoard? =
        try {
            FfiPipesGame(size.toUInt(), layout.toFfi(), seed.toULong()).let(::Pipes)
        } catch (_: Exception) {
            null
        }

    /** Reads a saved pipes game, or returns null when the bytes are not one. */
    fun pipesFromSnapshot(bytes: ByteArray): PipesBoard? =
        try {
            FfiPipesGame.fromSnapshot(bytes).let(::Pipes)
        } catch (_: Exception) {
            null
        }

    /** Generates a sudoku puzzle. */
    fun newSudoku(difficulty: SudokuDifficulty, seed: Long): SudokuBoard =
        Sudoku(FfiSudokuGame.generate(difficulty.toFfi(), seed.toULong()))

    /**
     * Generates a sudoku puzzle, or returns null when the seed cannot produce the difficulty.
     *
     * A seed a player typed is not a seed the generator chose, so the fallible entry point is
     * what a screen that offers a seed uses: the infallible one panics, and a panic across the
     * language boundary ends the process.
     */
    fun trySudoku(difficulty: SudokuDifficulty, seed: Long): SudokuBoard? =
        try {
            ffiSudokuTryGenerate(difficulty.toFfi(), seed.toULong())?.let(::Sudoku)
        } catch (_: Exception) {
            null
        }

    /** Reads a saved sudoku game, or returns null when the bytes are not one. */
    fun sudokuFromSnapshot(bytes: ByteArray): SudokuBoard? =
        try {
            Sudoku(FfiSudokuGame.fromSnapshot(bytes))
        } catch (_: Exception) {
            null
        }

    /** Returns the smallest minesweeper board, in cells on a side. */
    fun minesweeperMinSize(): Int = ffiMinesweeperMinSize().toInt()

    /** Returns the largest minesweeper board, in cells on a side. */
    fun minesweeperMaxSize(): Int = ffiMinesweeperMaxSize().toInt()

    /** Returns the lowest share of a board a mine field accepts, in thousandths of a percent. */
    fun minesweeperInputPerMilleMin(): Int = ffiMinesweeperInputPerMilleMin().toInt()

    /** Returns the highest share of a board a mine field accepts, in thousandths of a percent. */
    fun minesweeperInputPerMilleMax(): Int = ffiMinesweeperInputPerMilleMax().toInt()

    /** Returns the lowest share the mine slider covers, in thousandths of a percent. */
    fun minesweeperSliderPerMilleMin(): Int = ffiMinesweeperSliderPerMilleMin().toInt()

    /** Returns the highest share the mine slider covers, in thousandths of a percent. */
    fun minesweeperSliderPerMilleMax(): Int = ffiMinesweeperSliderPerMilleMax().toInt()

    /** Returns the highest share the mine slider covers in no-guess mode. */
    fun minesweeperNoGuessSliderPerMilleMax(): Int = ffiMinesweeperNoguessSliderPerMilleMax().toInt()

    /** Returns the highest share the mine field accepts in no-guess mode. */
    fun minesweeperNoGuessInputPerMilleMax(): Int = ffiMinesweeperNoguessInputPerMilleMax().toInt()

    /** Returns the share from which a no-guess board is likely to fall back. */
    fun minesweeperNoGuessWarnPerMille(): Int = ffiMinesweeperNoguessWarnPerMille().toInt()

    /** Returns the shares a mine slider snaps to, in thousandths of a percent, ascending. */
    fun minesweeperSnapPerMille(): List<Int> = ffiMinesweeperSnapPerMille().map { it.toInt() }

    /** Returns how many mines a share of a board converts to. */
    fun minesweeperMinesForPerMille(cells: Int, perMille: Int): Int =
        ffiMinesweeperMinesForPerMille(cells.toUInt(), perMille.toUInt()).toInt()

    /**
     * Starts a minesweeper game whose board is generated when the first cell is opened.
     *
     * Returns null when the parameters are outside their range, which the dialog validates
     * before it calls this: a board that broke its own parameters is a programming error
     * rather than a state a player reaches.
     */
    fun newMinesweeper(
        width: Int,
        height: Int,
        mines: Int,
        mode: MinesweeperMode,
        baseSeed: Long,
    ): MinesweeperBoard? =
        try {
            FfiMinesweeperGame.random(
                width.toUInt(),
                height.toUInt(),
                mines.toUInt(),
                mode.toFfi(),
                baseSeed.toULong(),
            ).let(::Minesweeper)
        } catch (_: Exception) {
            null
        }

    /**
     * Starts a minesweeper game on a board generated from an explicit seed.
     *
     * The board exists at once and its first click is not protected, which is what a seed a
     * player typed means.
     */
    fun minesweeperFromSeed(
        width: Int,
        height: Int,
        mines: Int,
        mode: MinesweeperMode,
        seed: Long,
    ): MinesweeperBoard? =
        try {
            FfiMinesweeperGame.fromSeed(
                width.toUInt(),
                height.toUInt(),
                mines.toUInt(),
                mode.toFfi(),
                seed.toULong(),
            ).let(::Minesweeper)
        } catch (_: Exception) {
            null
        }

    /** Reads a saved minesweeper game, or returns null when the bytes are not one. */
    fun minesweeperFromSnapshot(bytes: ByteArray): MinesweeperBoard? =
        try {
            FfiMinesweeperGame.fromSnapshot(bytes).let(::Minesweeper)
        } catch (_: Exception) {
            null
        }

    /** Returns the smallest nonogram board, in cells on a side. */
    fun nonogramMinSize(): Int = ffiNonogramMinSize().toInt()

    /** Returns the largest nonogram board, in cells on a side. */
    fun nonogramMaxSize(): Int = ffiNonogramMaxSize().toInt()

    /**
     * Draws a nonogram board and checks that exactly one answer matches its clues.
     *
     * Returns null when the parameters are outside their range, which the dialog validates
     * before it calls this, or when the board could not be drawn at all.
     */
    fun newNonogram(width: Int, height: Int, seed: Long): NonogramBoard? =
        try {
            FfiNonogramGame(width.toUInt(), height.toUInt(), seed.toULong()).let(::Nonogram)
        } catch (_: Exception) {
            null
        }

    /** Reads a saved nonogram game, or returns null when the bytes are not one. */
    fun nonogramFromSnapshot(bytes: ByteArray): NonogramBoard? =
        try {
            FfiNonogramGame.fromSnapshot(bytes).let(::Nonogram)
        } catch (_: Exception) {
            null
        }

    /** Returns the smallest hashi board, in points on a side. */
    fun hashiMinPoints(): Int = ffiHashiMinPoints().toInt()

    /** Returns the largest hashi board, in points on a side. */
    fun hashiMaxPoints(): Int = ffiHashiMaxPoints().toInt()

    /** Returns the lowest share of a hashi board that may hold islands, in thousandths. */
    fun hashiIslandPerMilleMin(): Int = ffiHashiIslandPerMilleMin().toInt()

    /** Returns the highest share of a hashi board that may hold islands, in thousandths. */
    fun hashiIslandPerMilleMax(): Int = ffiHashiIslandPerMilleMax().toInt()

    /** Returns the shares a hashi island slider snaps to, in thousandths, ascending. */
    fun hashiIslandSnapPerMille(): List<Int> = ffiHashiIslandSnapPerMille().map { it.toInt() }

    /** Returns how many islands a share of a board's points converts to. */
    fun hashiIslandsForPerMille(points: Int, perMille: Int): Int =
        ffiHashiIslandsForPerMille(points.toUInt(), perMille.toUInt()).toInt()

    /**
     * Generates a hashi board, or returns null when the parameters leave none.
     *
     * A board whose island count leaves no room for a spanning tree cannot be played at all,
     * so the dialog keeps its form open rather than entering a game that cannot be finished.
     */
    fun newHashi(width: Int, height: Int, islands: Int, seed: Long): HashiBoard? =
        try {
            FfiHashiGame(width.toUInt(), height.toUInt(), islands.toUInt(), seed.toULong())
                .let(::Hashi)
        } catch (_: Exception) {
            null
        }

    /** Reads a saved hashi game, or returns null when the bytes are not one. */
    fun hashiFromSnapshot(bytes: ByteArray): HashiBoard? =
        try {
            FfiHashiGame.fromSnapshot(bytes).let(::Hashi)
        } catch (_: Exception) {
            null
        }

    /** Returns the seed a piece of text produces. */
    fun seedFromText(text: String): Long = ffiSeedFromText(text).toLong()

    /** Returns the seed a date produces. */
    fun dailySeed(date: String): Long = ffiDailySeed(date).toLong()
}

/** Returns the layout the bindings name. */
private fun PipesLayout.toFfi(): FfiPipesLayout =
    when (this) {
        PipesLayout.Flat -> FfiPipesLayout.FLAT
        PipesLayout.Wrapped -> FfiPipesLayout.WRAPPED
    }

/** Returns the layout the rules name. */
private fun FfiPipesLayout.toModel(): PipesLayout =
    when (this) {
        FfiPipesLayout.FLAT -> PipesLayout.Flat
        FfiPipesLayout.WRAPPED -> PipesLayout.Wrapped
    }

/** Returns the difficulty the bindings name. */
private fun SudokuDifficulty.toFfi(): FfiSudokuDifficulty =
    when (this) {
        SudokuDifficulty.Easy -> FfiSudokuDifficulty.EASY
        SudokuDifficulty.Medium -> FfiSudokuDifficulty.MEDIUM
        SudokuDifficulty.Hard -> FfiSudokuDifficulty.HARD
    }

/** Returns the difficulty the rules name. */
private fun FfiSudokuDifficulty.toModel(): SudokuDifficulty =
    when (this) {
        FfiSudokuDifficulty.EASY -> SudokuDifficulty.Easy
        FfiSudokuDifficulty.MEDIUM -> SudokuDifficulty.Medium
        FfiSudokuDifficulty.HARD -> SudokuDifficulty.Hard
    }

/** Wraps the generated pipes object. */
private class Pipes(private val game: FfiPipesGame) : PipesBoard {
    override val size: Int = game.size().toInt()

    override val layout: PipesLayout = game.layout().toModel()

    override val seed: Long = game.seed().toLong()

    override fun piece(index: Int): Int = game.piece(index.toUInt()).toInt()

    override fun pieces(): List<Int> = game.pieces().map { it.toInt() }

    override fun turnClockwise(index: Int) {
        game.turnClockwise(index.toUInt())
    }

    override fun turnAntiClockwise(index: Int) {
        game.turnAntiClockwise(index.toUInt())
    }

    override fun undo(): Boolean = game.undo()

    override fun isSolved(): Boolean = game.isSolved()

    override fun loopCells(): List<Int> = game.loopCells().map { it.toInt() }

    override fun waterCells(): List<Int> = game.waterCells().map { it.toInt() }

    override fun waterStart(): Int = game.waterStart().toInt()

    override fun toSnapshot(): ByteArray = game.toSnapshot()
}

/** Wraps the generated sudoku object. */
private class Sudoku(private val game: FfiSudokuGame) : SudokuBoard {
    override val difficulty: SudokuDifficulty = game.difficulty().toModel()

    override val seed: Long = game.seed().toLong()

    override fun given(index: Int): Int = game.given(index.toUInt()).toInt()

    override fun digit(index: Int): Int = game.digit(index.toUInt()).toInt()

    override fun notes(index: Int): Int = game.notes(index.toUInt()).toInt()

    override fun isGiven(index: Int): Boolean = game.isGiven(index.toUInt())

    override fun enter(index: Int, digit: Int) {
        game.enter(index.toUInt(), digit.toUInt())
    }

    override fun clear(index: Int) {
        game.clear(index.toUInt())
    }

    override fun toggleNote(index: Int, digit: Int) {
        game.toggleNote(index.toUInt(), digit.toUInt())
    }

    override fun undo(): Boolean = game.undo()

    override fun conflictCells(): List<Int> = game.conflictCells().map { it.toInt() }

    override fun isSolved(): Boolean = game.isSolved()

    override fun toSnapshot(): ByteArray = game.toSnapshot()
}

/** Returns the mode the bindings name. */
private fun MinesweeperMode.toFfi(): FfiMinesweeperMode =
    when (this) {
        MinesweeperMode.Normal -> FfiMinesweeperMode.NORMAL
        MinesweeperMode.NoGuess -> FfiMinesweeperMode.NO_GUESS
    }

/** Returns the mode the rules name. */
private fun FfiMinesweeperMode.toModel(): MinesweeperMode =
    when (this) {
        FfiMinesweeperMode.NORMAL -> MinesweeperMode.Normal
        FfiMinesweeperMode.NO_GUESS -> MinesweeperMode.NoGuess
    }

/** Returns the phase the rules name. */
private fun FfiMinesweeperPhase.toModel(): MinesweeperPhase =
    when (this) {
        FfiMinesweeperPhase.PENDING -> MinesweeperPhase.Pending
        FfiMinesweeperPhase.PLAYING -> MinesweeperPhase.Playing
        FfiMinesweeperPhase.WON -> MinesweeperPhase.Won
        FfiMinesweeperPhase.LOST -> MinesweeperPhase.Lost
    }

/** Returns the event the rules name. */
private fun FfiMinesweeperEvent.toModel(): MinesweeperEvent =
    when (this) {
        FfiMinesweeperEvent.REVEALED -> MinesweeperEvent.Revealed
        FfiMinesweeperEvent.HIT_MINE -> MinesweeperEvent.HitMine
        FfiMinesweeperEvent.WON -> MinesweeperEvent.Won
        FfiMinesweeperEvent.IGNORED -> MinesweeperEvent.Ignored
    }

/**
 * Wraps the generated minesweeper object.
 *
 * The values that change while a game is played — the flag count, the mines left, the phase,
 * the seed and the fallback flag — are read from the rules on every access rather than kept
 * as properties: a value captured when the wrapper was built would be the board's state
 * before the first click, which is exactly the state a screen has to stop showing.
 */
private class Minesweeper(private val game: FfiMinesweeperGame) : MinesweeperBoard {
    override val width: Int = game.width().toInt()

    override val height: Int = game.height().toInt()

    override val cellCount: Int = game.cellCount().toInt()

    override val mineCount: Int = game.mineCount().toInt()

    override val flagCount: Int
        get() = game.flagCount().toInt()

    override val remainingMines: Int
        get() = game.remainingMines()

    override val mode: MinesweeperMode
        get() = game.mode().toModel()

    override val fellBack: Boolean
        get() = game.fellBack()

    override val seed: Long
        get() = game.seed().toLong()

    override val phase: MinesweeperPhase
        get() = game.phase().toModel()

    override fun cell(index: Int): Int = game.cell(index.toUInt()).toInt()

    override fun cellsState(): List<Int> = game.cellsState().map { it.toInt() }

    override fun reveal(index: Int): MinesweeperEvent = game.reveal(index.toUInt()).toModel()

    override fun toggleFlag(index: Int): Boolean = game.toggleFlag(index.toUInt())

    override fun toSnapshot(): ByteArray = game.toSnapshot()
}

/**
 * Wraps the generated nonogram object.
 *
 * The values that change while a game is played are read from the rules on every access
 * rather than kept as properties, for the reason given on [Minesweeper]: a captured value
 * would be the board as it stood before the player's first tap.
 */
private class Nonogram(private val game: FfiNonogramGame) : NonogramBoard {
    override val width: Int = game.width().toInt()

    override val height: Int = game.height().toInt()

    override val cellCount: Int = game.cellCount().toInt()

    override val seed: Long
        get() = game.seed().toLong()

    override val fellBack: Boolean
        get() = game.fellBack()

    override fun rowClues(): List<ClueLine> =
        game.rowClues().map { line -> ClueLine(line.values.map { it.toInt() }) }

    override fun columnClues(): List<ClueLine> =
        game.columnClues().map { line -> ClueLine(line.values.map { it.toInt() }) }

    override fun cellsState(): List<Int> = game.cellsState().map { it.toInt() }

    override fun brokenRows(): List<Int> = game.brokenRows().map { it.toInt() }

    override fun brokenColumns(): List<Int> = game.brokenColumns().map { it.toInt() }

    override fun toggleFilled(index: Int): Boolean = game.toggleFilled(index.toUInt())

    override fun toggleMarked(index: Int): Boolean = game.toggleMarked(index.toUInt())

    override fun isSolved(): Boolean = game.isSolved()

    override fun toSnapshot(): ByteArray = game.toSnapshot()
}

/**
 * Wraps the generated hashi object.
 *
 * The island and bridge lists are converted on every call rather than cached: a connection
 * changes both of them, and a list captured when the wrapper was built would draw the board
 * the player connected before.
 */
private class Hashi(private val game: FfiHashiGame) : HashiBoard {
    override val width: Int = game.width().toInt()

    override val height: Int = game.height().toInt()

    override val islandCount: Int = game.islandCount().toInt()

    override val seed: Long = game.seed().toLong()

    override fun islands(): List<HashiIsland> =
        game.islands().map { island ->
            HashiIsland(
                row = island.row.toInt(),
                column = island.column.toInt(),
                clue = island.clue.toInt(),
                degree = island.degree.toInt(),
                marked = island.marked,
            )
        }

    override fun bridges(): List<HashiBridge> =
        game.bridges().map { bridge ->
            HashiBridge(a = bridge.a.toInt(), b = bridge.b.toInt(), count = bridge.count.toInt())
        }

    override fun connect(first: Int, second: Int): Boolean =
        game.connect(first.toUInt(), second.toUInt())

    override fun toggleMark(index: Int): Boolean = game.toggleMark(index.toUInt())

    override fun strandedIslands(): List<Int> = game.strandedIslands().map { it.toInt() }

    override fun isSolved(): Boolean = game.isSolved()

    override fun toSnapshot(): ByteArray = game.toSnapshot()
}
