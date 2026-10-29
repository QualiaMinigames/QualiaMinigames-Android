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

import com.qiushui1012.app.minigames.ffi.PipesGame as FfiPipesGame
import com.qiushui1012.app.minigames.ffi.PipesLayout as FfiPipesLayout
import com.qiushui1012.app.minigames.ffi.SudokuDifficulty as FfiSudokuDifficulty
import com.qiushui1012.app.minigames.ffi.SudokuGame as FfiSudokuGame
import com.qiushui1012.app.minigames.ffi.dailySeed as ffiDailySeed
import com.qiushui1012.app.minigames.ffi.coreVersion as ffiCoreVersion
import com.qiushui1012.app.minigames.ffi.pipesMaxSize as ffiPipesMaxSize
import com.qiushui1012.app.minigames.ffi.pipesMinSize as ffiPipesMinSize
import com.qiushui1012.app.minigames.ffi.seedFromText as ffiSeedFromText
import com.qiushui1012.app.minigames.ffi.sideBottom as ffiSideBottom
import com.qiushui1012.app.minigames.ffi.sideLeft as ffiSideLeft
import com.qiushui1012.app.minigames.ffi.sideRight as ffiSideRight
import com.qiushui1012.app.minigames.ffi.sideTop as ffiSideTop
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

    /** Reads a saved sudoku game, or returns null when the bytes are not one. */
    fun sudokuFromSnapshot(bytes: ByteArray): SudokuBoard? =
        try {
            Sudoku(FfiSudokuGame.fromSnapshot(bytes))
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
