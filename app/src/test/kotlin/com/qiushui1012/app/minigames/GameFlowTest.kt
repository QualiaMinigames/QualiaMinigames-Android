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
import com.qiushui1012.app.minigames.data.ArrowCubeRecord
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.GameRecord
import com.qiushui1012.app.minigames.data.HashiRecord
import com.qiushui1012.app.minigames.data.MinesweeperRecord
import com.qiushui1012.app.minigames.data.NonogramRecord
import com.qiushui1012.app.minigames.data.PipesRecord
import com.qiushui1012.app.minigames.data.Play
import com.qiushui1012.app.minigames.data.RecordsFile
import com.qiushui1012.app.minigames.data.RecordsStore
import com.qiushui1012.app.minigames.data.SavedGame
import com.qiushui1012.app.minigames.data.SudokuRecord
import com.qiushui1012.app.minigames.data.append
import com.qiushui1012.app.minigames.export.RecordImage
import com.qiushui1012.app.minigames.rules.ArrowCubeBoard
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
 * own rules allow — by entering digits until the board reports itself solved — and an arrow
 * cube is taken apart by tapping blocks until the board is empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameFlowTest {
    /** Returns a store over an empty in-memory DataStore. */
    private fun newStore(): RecordsStore = RecordsStore(MemoryStore())

    /** Returns a group with one play in it, the way a finished screen builds one. */
    private fun <T : GameRecord> finished(
        key: T,
        seconds: Long,
        player: String = "QiuShui",
        seed: Long? = null,
        failed: Boolean? = null,
        typedSeed: Boolean = false,
        mistakes: Int? = null,
        finishedAt: Long = 2_000,
    ): T {
        val play =
            Play(
                playerName = player,
                seconds = seconds,
                finishedAt = finishedAt,
                seed = seed,
                failed = failed,
                typedSeed = typedSeed,
                mistakes = mistakes,
            )
        @Suppress("UNCHECKED_CAST")
        return key.append(play) as T
    }

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

    /**
     * Takes an arrow cube apart by tapping blocks, the way a player does.
     *
     * The scan tries every block in turn and counts the refusals as mistakes, which is also
     * what the core counts. A board that offers nothing at all is a failure rather than an
     * empty loop, because the generator promises a solvable board.
     */
    private fun solveArrowCube(board: ArrowCubeBoard): Int {
        var refused = 0
        while (!board.isSolved()) {
            var taken = false
            for (index in 0 until board.blockCount) {
                val result = board.take(index)
                if (result.removed) {
                    taken = true
                    break
                }
                if (result.blocker != null) {
                    refused += 1
                }
            }
            assertTrue("a generated arrow cube board always offers a block", taken)
        }
        assertEquals(refused, board.mistakes)
        return refused
    }

    @Test
    fun a_sudoku_can_be_played_to_a_finish_through_its_own_moves() {
        val board = Rules.newSudoku(SudokuDifficulty.Easy, 4_242)
        assertTrue("a generated sudoku is solvable", solveSudoku(board))
        assertTrue(board.isSolved())
    }

    @Test
    fun finishing_a_pipes_game_writes_a_group_and_clears_its_save() = runBlocking {
        val store = newStore()
        val board = requireNotNull(Rules.newPipes(4, PipesLayout.Flat, 12_345))
        store.putSave(SavedGame(GameKind.Pipes, board.toSnapshot(), "QiuShui", 42, 1_000))
        assertNotNull(store.save(GameKind.Pipes).first())

        val group = finished(PipesRecord(4, wrapped = false), 42)
        store.addPlay(group)

        assertNull("the save of a finished game is gone", store.save(GameKind.Pipes).first())
        val groups = store.records.first()
        assertEquals(1, groups.size)
        assertEquals("QiuShui", groups.single().history.single().playerName)
        assertEquals(42L, groups.single().bestSeconds)
    }

    @Test
    fun finishing_a_sudoku_game_leaves_the_other_game_alone() = runBlocking {
        val store = newStore()
        store.putSave(SavedGame(GameKind.Pipes, byteArrayOf(1), "QiuShui", 5, 1_000))
        val board = Rules.newSudoku(SudokuDifficulty.Easy, 4_242)
        store.putSave(SavedGame(GameKind.Sudoku, board.toSnapshot(), "QiuShui", 33, 1_000))

        assertTrue(solveSudoku(board))
        store.addPlay(finished(SudokuRecord("easy"), 33))

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
        assertEquals(PipesRecord(5, wrapped = true), resumed.key)
    }

    @Test
    fun a_saved_game_of_another_format_is_not_restored() {
        val save = SavedGame(GameKind.Pipes, byteArrayOf(1, 2, 3), "QiuShui", 5, 1_000)
        assertNull("bytes that are not a snapshot are not a game", restoreGame(save))
    }

    @Test
    fun the_seed_of_a_game_survives_its_save_and_reaches_its_play() {
        // A play keeps the seed so that a finished puzzle can be identified afterwards. The
        // seed travels through the snapshot, which is what a saved game is, and the play is
        // built from the game the player was in.
        val board = requireNotNull(Rules.newPipes(6, PipesLayout.Flat, 0x5EED))
        val save = SavedGame(GameKind.Pipes, board.toSnapshot(), "QiuShui", 9, 1_000)
        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.Pipes
        assertEquals(board.seed, resumed.seed)

        val group = finished(resumed.key as PipesRecord, 9, seed = resumed.seed)
        assertEquals(0x5EEDL, group.history.single().seed)
    }

    @Test
    fun a_saved_sudoku_is_restored_with_its_difficulty_and_its_notes() = runBlocking {
        val board = Rules.newSudoku(SudokuDifficulty.Hard, 31)
        val empty = (0 until Rules.SUDOKU_CELLS).first { !board.isGiven(it) }
        board.toggleNote(empty, 7)
        val save = SavedGame(GameKind.Sudoku, board.toSnapshot(), "QiuShui", 8, 1_000)

        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.Sudoku
        assertEquals(SudokuDifficulty.Hard, resumed.board.difficulty)
        assertEquals(SudokuRecord("hard"), resumed.key)
        assertEquals(board.notes(empty), resumed.board.notes(empty))
    }

    @Test
    fun the_play_of_a_finished_game_exports_to_an_image() {
        val group = finished(PipesRecord(9, wrapped = false), 125, seed = 7)
        val play = group.history.single()
        val name: (GameRecord) -> String = { "Pipes" }
        val params: (GameRecord) -> String = { key -> key.toString() }
        val single = RecordImage.single(group, play, name, params, "Unnamed", "exported", Typeface.MONOSPACE)
        assertEquals(RecordImage.width(), single.width)
        assertTrue(single.height > 0)

        val several =
            RecordImage.several(
                listOf(group to play, group to play, group to play),
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
    fun a_lost_minesweeper_game_writes_a_failed_play_and_clears_its_save() = runBlocking {
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

        val group =
            finished(
                MinesweeperRecord(9, 9, 10, noGuess = false),
                12,
                seed = board.seed,
                failed = true,
            )
        store.addPlay(group)

        assertNull("a lost game leaves no save", store.save(GameKind.Minesweeper).first())
        val stored = store.records.first().single()
        assertEquals(true, stored.history.single().failed)
        assertNull("a loss is not a best time", stored.bestSeconds)
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
        assertEquals(MinesweeperRecord(16, 16, 40, noGuess = false), resumed.key)
    }

    @Test
    fun a_minesweeper_game_that_fell_back_is_filed_as_a_normal_board() {
        // A 4 by 4 board at 12 mines can never be deduced, so every no-guess attempt falls back.
        // The record has to say normal, because that is the board the player actually played.
        val board = requireNotNull(Rules.newMinesweeper(4, 4, 12, MinesweeperMode.NoGuess, 1))
        board.reveal(5)
        assertEquals(true, board.fellBack)
        val game = GameInProgress.Minesweeper(board, "QiuShui", 3)
        assertEquals(MinesweeperRecord(4, 4, 12, noGuess = false), game.key)
    }

    @Test
    fun a_typed_seed_reaches_the_play() {
        // The flag is what tells a history that the seed was chosen rather than produced, and
        // it travels with the game rather than with the record's key: two games on one board
        // are the same group whether or not either seed was typed.
        val board = requireNotNull(Rules.minesweeperFromSeed(9, 9, 10, MinesweeperMode.Normal, 4_242))
        val game = GameInProgress.Minesweeper(board, "QiuShui", 8, typedSeed = true)
        val group = finished(game.key as MinesweeperRecord, 8, seed = game.seed, typedSeed = game.typedSeed)
        assertEquals(true, group.history.single().typedSeed)
        assertEquals(4_242L, group.history.single().seed)
    }

    @Test
    fun a_saved_nonogram_game_is_restored_with_its_cells_and_its_clues() {
        val board = requireNotNull(Rules.newNonogram(9, 9, 13))
        board.toggleFilled(5)
        board.toggleMarked(6)
        board.toggleMarked(20)
        val save = SavedGame(GameKind.Nonogram, board.toSnapshot(), "QiuShui", 41, 1_000)

        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.Nonogram
        assertEquals("QiuShui", resumed.playerName)
        assertEquals(41, resumed.elapsedSeconds)
        assertEquals(board.cellsState(), resumed.board.cellsState())
        assertEquals(board.rowClues(), resumed.board.rowClues())
        assertEquals(NonogramRecord(9, 9), resumed.key)
        assertEquals(board.seed, resumed.seed)
    }

    @Test
    fun a_saved_hashi_game_is_restored_with_its_bridges_and_its_marks() {
        val board = requireNotNull(Rules.newHashi(9, 9, 12, 17))
        val joinable = (1 until board.islandCount).firstOrNull { board.connect(0, it) }
        requireNotNull(joinable) { "the first island reaches another one" }
        board.toggleMark(2)
        val save = SavedGame(GameKind.Hashi, board.toSnapshot(), "QiuShui", 27, 1_000)

        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.Hashi
        assertEquals("QiuShui", resumed.playerName)
        assertEquals(27, resumed.elapsedSeconds)
        assertEquals(board.islands(), resumed.board.islands())
        assertEquals(board.bridges(), resumed.board.bridges())
        assertEquals(HashiRecord(9, 9, 12), resumed.key)
    }

    @Test
    fun a_nonogram_that_finishes_writes_a_group_and_clears_its_save() = runBlocking {
        val store = newStore()
        val board = requireNotNull(Rules.newNonogram(4, 4, 7))
        store.putSave(SavedGame(GameKind.Nonogram, board.toSnapshot(), "QiuShui", 3, 1_000))
        val game = GameInProgress.Nonogram(board, "QiuShui", 3)
        store.addPlay(finished(game.key as NonogramRecord, 3, seed = game.seed, typedSeed = game.typedSeed))

        assertNull("a finished game leaves no save", store.save(GameKind.Nonogram).first())
        val group = store.records.first().single()
        assertEquals(NonogramRecord(4, 4), group.keyParameters())
        assertEquals(3L, group.bestSeconds)
    }

    @Test
    fun a_typed_seed_on_a_new_game_reaches_its_play() {
        // The two new games carry the flag the same way the three others do: it belongs to the
        // game, and a play copies it while the game is still being played. The seed a play
        // keeps is the one the board was drawn from, which is a derived seed rather than the
        // number the player typed: typing the same text again has to replay the same board,
        // and the derivation is what turns one into the other.
        val b = requireNotNull(Rules.newNonogram(6, 6, 4_242))
        val nonogram = GameInProgress.Nonogram(b, "QiuShui", 9, typedSeed = true)
        val again = requireNotNull(Rules.newNonogram(6, 6, 4_242))
        assertEquals("the same seed replays the same board", again.seed, nonogram.seed)
        assertEquals(again.rowClues(), nonogram.board.rowClues())
        assertEquals(true, nonogram.typedSeed)
        assertEquals(NonogramRecord(6, 6), nonogram.key)

        val h = requireNotNull(Rules.newHashi(8, 8, 10, 4_242))
        val hashi = GameInProgress.Hashi(h, "QiuShui", 9, typedSeed = true)
        val hashiAgain = requireNotNull(Rules.newHashi(8, 8, 10, 4_242))
        assertEquals("the same seed replays the same board", hashiAgain.seed, hashi.seed)
        assertEquals(hashiAgain.islands(), hashi.board.islands())
        assertEquals(true, hashi.typedSeed)
        assertEquals(HashiRecord(8, 8, 10), hashi.key)
    }

    @Test
    fun solving_an_arrow_cube_writes_a_group_with_its_time_and_its_mistakes() = runBlocking {
        val store = newStore()
        val board = requireNotNull(Rules.newArrowCube(5, 5, 5, 21))
        store.putSave(SavedGame(GameKind.ArrowCube, board.toSnapshot(), "QiuShui", 12, 1_000))
        val refused = solveArrowCube(board)
        assertTrue("the solved board has no blocks left", board.isSolved())

        val game = GameInProgress.ArrowCube(board, "QiuShui", 42)
        store.addPlay(
            finished(
                game.key as ArrowCubeRecord,
                42,
                seed = game.seed,
                mistakes = board.mistakes,
            ),
        )

        assertNull("a finished game leaves no save", store.save(GameKind.ArrowCube).first())
        val group = store.records.first().single()
        assertEquals(ArrowCubeRecord(5, 5, 5), group.keyParameters())
        assertEquals(42L, group.bestSeconds)
        assertEquals(refused, (group as ArrowCubeRecord).bestMistakes)
        assertEquals(42L, group.history.single().seconds)
        assertEquals(refused, group.history.single().mistakes)
    }

    @Test
    fun a_saved_arrow_cube_is_restored_with_its_taken_blocks_and_its_clock() {
        val board = requireNotNull(Rules.newArrowCube(5, 5, 5, 23))
        var taken = false
        for (index in 0 until board.blockCount) {
            if (board.take(index).removed) {
                taken = true
                break
            }
        }
        assertTrue("a generated board offers a block", taken)
        val save = SavedGame(GameKind.ArrowCube, board.toSnapshot(), "QiuShui", 17, 1_000)

        val resumed = requireNotNull(restoreGame(save)) as GameInProgress.ArrowCube
        assertEquals("QiuShui", resumed.playerName)
        assertEquals(17, resumed.elapsedSeconds)
        assertEquals(ArrowCubeRecord(5, 5, 5), resumed.key)
        assertEquals(board.mistakes, resumed.board.mistakes)
        assertEquals(board.remainingCount, resumed.board.remainingCount)
        assertEquals(board.blocks(), resumed.board.blocks())
    }

    /** Returns a group's parameters without its plays, for comparing it with a key. */
    private fun GameRecord.keyParameters(): GameRecord =
        when (this) {
            is PipesRecord -> PipesRecord(size, wrapped)
            is SudokuRecord -> SudokuRecord(difficulty)
            is MinesweeperRecord -> MinesweeperRecord(width, height, mines, noGuess)
            is NonogramRecord -> NonogramRecord(width, height)
            is HashiRecord -> HashiRecord(width, height, islands)
            is ArrowCubeRecord -> ArrowCubeRecord(width, depth, height)
        }
}
