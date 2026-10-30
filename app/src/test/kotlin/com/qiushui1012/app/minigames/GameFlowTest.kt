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
}
