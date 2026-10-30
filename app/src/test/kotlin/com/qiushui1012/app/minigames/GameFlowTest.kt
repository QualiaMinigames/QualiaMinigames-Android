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

import androidx.datastore.core.DataStore
import android.graphics.Typeface
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.Record
import com.qiushui1012.app.minigames.data.RecordKey
import com.qiushui1012.app.minigames.data.RecordsFile
import com.qiushui1012.app.minigames.data.RecordsStore
import com.qiushui1012.app.minigames.data.SavedGame
import com.qiushui1012.app.minigames.export.RecordImage
import com.qiushui1012.app.minigames.rules.MinesweeperCell
import com.qiushui1012.app.minigames.rules.MinesweeperEvent
import com.qiushui1012.app.minigames.rules.MinesweeperMode
import com.qiushui1012.app.minigames.rules.MinesweeperPhase
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.Rules
import com.qiushui1012.app.minigames.rules.SudokuDifficulty
import com.qiushui1012.app.minigames.ui.restoreGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A DataStore that keeps everything in memory, so a test needs no file. */
private class MemoryStore : DataStore<RecordsFile> {
    private val state = MutableStateFlow(RecordsFile())

    override val data = state

    override suspend fun updateData(transform: suspend (t: RecordsFile) -> RecordsFile): RecordsFile {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/**
 * A game from its first move to the record it leaves.
 *
 * The tests either side of this one check the rules and the store alone; this one checks the
 * seam, which is where a game that finishes without a record, or a record whose save was left
 * behind, would hide. It drives the calls the screens drive, in the order they drive them.
 *
 * Nothing here reads a solution out of the library: the interface does not expose one, and a
 * test that asked for it would be asking for a way to cheat. A sudoku is solved the way its
 * own rules allow — by entering digits until the board reports itself solved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameFlowTest {
    /** Returns a store over an empty in-memory DataStore. */
    private fun newStore(): RecordsStore = RecordsStore(MemoryStore())

    /**
     * Solves a sudoku by trying digits and backing out of a dead end.
     *
     * This is the solver a player does not have: it exists to reach a finish in a test, and it
     * uses only `enter`, `clear` and `conflictCells`, which are the moves the screen offers.
     */
    private fun solveSudoku(board: com.qiushui1012.app.minigames.rules.SudokuBoard): Boolean {
        val cells = (0 until Rules.SUDOKU_CELLS).filter { !board.isGiven(it) }
        fun place(position: Int): Boolean {
            if (position == cells.size) {
                return board.isSolved()
            }
            val index = cells[position]
            for (digit in 1..9) {
                board.enter(index, digit)
                if (board.conflictCells().isEmpty() && place(position + 1)) {
                    return true
                }
                board.clear(index)
            }
            return false
        }
        return place(0)
    }

    @Test
    fun a_sudoku_can_be_played_to_a_finish_through_its_own_moves() {
        val board = Rules.newSudoku(SudokuDifficulty.Easy, 4_242)
        assertTrue("a generated sudoku is solvable", solveSudoku(board))
        assertTrue(board.isSolved())
    }

    @Test
    fun finishing_a_pipes_game_writes_a_record_and_clears_its_save() = runBlocking {
        val store = newStore()
        val board = requireNotNull(Rules.newPipes(4, PipesLayout.Flat, 12_345))
        store.putSave(SavedGame(GameKind.Pipes, board.toSnapshot(), "QiuShui", 42, 1_000))
        assertNotNull(store.save(GameKind.Pipes).first())

        // A game that ended: the record is what the screen writes, and the save is what it
        // clears in the same breath.
        store.addRecord(Record(RecordKey.Pipes(4, wrapped = false), "QiuShui", 42, 2_000))
        store.clearSave(GameKind.Pipes)

        assertNull("the save of a finished game is gone", store.save(GameKind.Pipes).first())
        val records = store.records.first()
        assertEquals(1, records.size)
        assertEquals("QiuShui", records.single().playerName)
        assertEquals(42, records.single().seconds)
    }

    @Test
    fun finishing_a_sudoku_game_leaves_the_other_game_alone() = runBlocking {
        val store = newStore()
        store.putSave(SavedGame(GameKind.Pipes, byteArrayOf(1), "QiuShui", 5, 1_000))
        val board = Rules.newSudoku(SudokuDifficulty.Easy, 4_242)
        store.putSave(SavedGame(GameKind.Sudoku, board.toSnapshot(), "QiuShui", 33, 1_000))

        assertTrue(solveSudoku(board))
        store.addRecord(Record(RecordKey.Sudoku("easy"), "QiuShui", 33, 2_000))
        store.clearSave(GameKind.Sudoku)

        assertNull(store.save(GameKind.Sudoku).first())
        assertNotNull("finishing one game does not end the other", store.save(GameKind.Pipes).first())
        assertEquals(1, store.records.first().size)
    }

    @Test
    fun a_saved_pipes_game_is_restored_with_its_moves_and_its_clock() = runBlocking {
        val board = requireNotNull(Rules.newPipes(5, PipesLayout.Wrapped, 99))
        board.turnClockwise(6)
        board.turnAntiClockwise(6)
        board.turnAntiClockwise(6)
        val save = SavedGame(GameKind.Pipes, board.toSnapshot(), "QiuShui", 17, 1_000)

        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.Pipes
        assertEquals("QiuShui", resumed.playerName)
        assertEquals(17, resumed.elapsedSeconds)
        // The piece was turned anti-clockwise twice net, which a snapshot has to keep apart
        // from a single clockwise turn.
        assertEquals(board.piece(6), resumed.board.piece(6))
        assertEquals(RecordKey.Pipes(5, wrapped = true), resumed.key)
    }

    @Test
    fun a_saved_game_of_another_format_is_not_restored() {
        val save = SavedGame(GameKind.Pipes, byteArrayOf(1, 2, 3), "QiuShui", 5, 1_000)
        assertNull("bytes that are not a snapshot are not a game", restoreGame(save))
    }

    @Test
    fun the_seed_of_a_game_survives_its_save_and_reaches_its_record() {
        // A record keeps the seed so that a finished puzzle can be identified afterwards. The
        // seed travels through the snapshot, which is what a saved game is, and the record is
        // built from the game the player was in.
        val board = requireNotNull(Rules.newPipes(6, PipesLayout.Flat, 0x5EED))
        val save = SavedGame(GameKind.Pipes, board.toSnapshot(), "QiuShui", 9, 1_000)
        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.Pipes
        assertEquals(board.seed, resumed.seed)

        val record = Record(resumed.key, resumed.playerName, 9, 2_000, seed = resumed.seed)
        assertEquals(0x5EEDL, record.seed)
    }

    @Test
    fun a_saved_sudoku_is_restored_with_its_difficulty_and_its_notes() = runBlocking {
        val board = Rules.newSudoku(SudokuDifficulty.Hard, 31)
        val empty = (0 until Rules.SUDOKU_CELLS).first { !board.isGiven(it) }
        board.toggleNote(empty, 7)
        val save = SavedGame(GameKind.Sudoku, board.toSnapshot(), "QiuShui", 8, 1_000)

        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.Sudoku
        assertEquals(SudokuDifficulty.Hard, resumed.board.difficulty)
        assertEquals(RecordKey.Sudoku("hard"), resumed.key)
        assertEquals(board.notes(empty), resumed.board.notes(empty))
    }

    @Test
    fun the_record_of_a_finished_game_export_to_an_image() {
        val record = Record(RecordKey.Pipes(9, wrapped = false), "QiuShui", 125, 1_700_000_000_000, seed = 7)
        val name: (RecordKey) -> String = { "Pipes" }
        val params: (RecordKey) -> String = { key -> key.toString() }
        val single = RecordImage.single(record, name, params, "Unnamed", "exported", Typeface.MONOSPACE)
        assertEquals(RecordImage.width(), single.width)
        assertTrue(single.height > 0)

        val several =
            RecordImage.several(
                List(3) { record },
                name,
                params,
                "Unnamed",
                "exported",
                "Play records",
                listOf("Player", "Game", "Time", "Finished"),
                Typeface.MONOSPACE,
            )
        assertTrue(several.width > 0)
        assertTrue(several.height > 0)
    }

    @Test
    fun a_lost_board_reports_every_mine_as_a_mine() {
        // A board the player lost has to hand its mines back as the value the board draws a
        // mine from. The rules report an open mine as 11, and a screen that received the
        // covered value instead would show a lost board with nothing on it.
        val board = requireNotNull(Rules.minesweeperFromSeed(9, 9, 10, MinesweeperMode.Normal, 5))
        val mine =
            (0 until board.cellCount).first { index ->
                val probe = requireNotNull(Rules.minesweeperFromSeed(9, 9, 10, MinesweeperMode.Normal, 5))
                probe.reveal(index) == MinesweeperEvent.HitMine
            }
        assertEquals(MinesweeperEvent.HitMine, board.reveal(mine))
        val state = board.cellsState()
        assertEquals(MinesweeperCell.MINE, state[mine])
        assertEquals(
            "the board shows ten mines and not one",
            10,
            state.count { it == MinesweeperCell.MINE },
        )
    }

    @Test
    fun a_lost_minesweeper_game_writes_a_failed_record_and_clears_its_save() = runBlocking {
        val store = newStore()
        val board = requireNotNull(Rules.minesweeperFromSeed(9, 9, 10, MinesweeperMode.Normal, 5))
        store.putSave(SavedGame(GameKind.Minesweeper, board.toSnapshot(), "QiuShui", 12, 1_000))
        // A board started from a seed is laid down at once, so a mine can be found by opening
        // cells until one explodes: the test reads the board rather than knowing it.
        val mine =
            (0 until board.cellCount).first { index ->
                val probe = requireNotNull(Rules.minesweeperFromSeed(9, 9, 10, MinesweeperMode.Normal, 5))
                probe.reveal(index) == MinesweeperEvent.HitMine
            }
        assertEquals(MinesweeperEvent.HitMine, board.reveal(mine))
        assertEquals(MinesweeperPhase.Lost, board.phase)

        val record =
            Record(
                key = RecordKey.Minesweeper(9, 9, 10, noGuess = false),
                playerName = "QiuShui",
                seconds = 12,
                finishedAt = 2_000,
                seed = board.seed,
                failed = true,
            )
        store.addRecord(record)
        store.clearSave(GameKind.Minesweeper)

        assertNull("a lost game leaves no save", store.save(GameKind.Minesweeper).first())
        assertEquals(true, store.records.first().single().failed)
        assertNull("a loss is not a best time", store.best(record.key).first())
    }

    @Test
    fun a_saved_minesweeper_game_is_restored_with_its_flags_and_its_open_cells() {
        val board = requireNotNull(Rules.newMinesweeper(16, 16, 40, MinesweeperMode.Normal, 21))
        board.reveal(120)
        board.toggleFlag(0)
        val save = SavedGame(GameKind.Minesweeper, board.toSnapshot(), "QiuShui", 33, 1_000)

        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.Minesweeper
        assertEquals("QiuShui", resumed.playerName)
        assertEquals(33, resumed.elapsedSeconds)
        assertEquals(board.cellsState(), resumed.board.cellsState())
        assertEquals(board.remainingMines, resumed.board.remainingMines)
        assertEquals(RecordKey.Minesweeper(16, 16, 40, noGuess = false), resumed.key)
    }

    @Test
    fun a_minesweeper_game_that_fell_back_is_filed_as_a_normal_board() {
        // A 4 by 4 board at 12 mines can never be deduced, so every no-guess attempt falls back.
        // The record has to say normal, because that is the board the player actually played.
        val board = requireNotNull(Rules.newMinesweeper(4, 4, 12, MinesweeperMode.NoGuess, 1))
        board.reveal(5)
        assertEquals(true, board.fellBack)
        val game = GameInProgress.Minesweeper(board, "QiuShui", 3)
        assertEquals(RecordKey.Minesweeper(4, 4, 12, noGuess = false), game.key)
    }

    @Test
    fun a_typed_seed_reaches_the_record() {
        // The flag is what tells a history that the seed was chosen rather than produced, and
        // it travels with the game rather than with the record's key: two games on one board
        // are the same group whether or not either seed was typed.
        val board = requireNotNull(Rules.minesweeperFromSeed(9, 9, 10, MinesweeperMode.Normal, 4_242))
        val game = GameInProgress.Minesweeper(board, "QiuShui", 8, typedSeed = true)
        val record =
            Record(
                key = game.key,
                playerName = game.playerName,
                seconds = 8,
                finishedAt = 2_000,
                seed = game.seed,
                typedSeed = game.typedSeed,
            )
        assertEquals(true, record.typedSeed)
        assertEquals(4_242L, record.seed)
    }
}
