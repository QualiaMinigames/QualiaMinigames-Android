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

import com.qiushui1012.app.minigames.data.Record
import com.qiushui1012.app.minigames.data.RecordKey
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.Rules
import com.qiushui1012.app.minigames.rules.SudokuDifficulty
import com.qiushui1012.app.minigames.ui.formatDuration
import com.qiushui1012.app.minigames.ui.groupRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rules as the application reaches them.
 *
 * These run on the JVM against the library built for the host, which is the same Rust code
 * the device runs: a binding that compiles but answers wrongly is exactly what this catches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RulesTest {
    @Test
    fun the_rules_report_a_version() {
        assertTrue(Rules.version().isNotEmpty())
    }

    @Test
    fun the_size_range_is_the_one_the_dialog_offers() {
        assertEquals(4, Rules.pipesMinSize())
        assertEquals(35, Rules.pipesMaxSize())
    }

    @Test
    fun a_pipes_game_starts_unsolved_and_answers_its_shape() {
        val board = Rules.newPipes(9, PipesLayout.Wrapped, 7)
        assertNotNull(board)
        requireNotNull(board)
        assertEquals(9, board.size)
        assertEquals(PipesLayout.Wrapped, board.layout)
        assertTrue(!board.isSolved())
    }

    @Test
    fun a_pipes_size_outside_the_range_is_refused() {
        assertEquals(null, Rules.newPipes(3, PipesLayout.Flat, 0))
        assertEquals(null, Rules.newPipes(36, PipesLayout.Flat, 0))
    }

    @Test
    fun turning_and_undoing_a_pipes_cell_restores_it() {
        val board = requireNotNull(Rules.newPipes(8, PipesLayout.Flat, 3))
        val before = board.piece(17)
        board.turnClockwise(17)
        assertTrue(board.piece(17) != before || board.undo())
        assertTrue(board.undo())
        assertEquals(before, board.piece(17))
    }

    @Test
    fun a_pipes_snapshot_crosses_the_boundary() {
        val board = requireNotNull(Rules.newPipes(12, PipesLayout.Flat, 5))
        board.turnClockwise(4)
        board.turnAntiClockwise(4)
        board.turnAntiClockwise(4)
        val restored = requireNotNull(Rules.pipesFromSnapshot(board.toSnapshot()))
        assertEquals(board.piece(4), restored.piece(4))
    }

    @Test
    fun broken_bytes_are_not_a_game() {
        assertEquals(null, Rules.pipesFromSnapshot(byteArrayOf(0, 1, 2)))
        assertEquals(null, Rules.sudokuFromSnapshot(byteArrayOf(0)))
    }

    @Test
    fun a_sudoku_game_keeps_its_givens_and_its_entries() {
        val board = Rules.newSudoku(SudokuDifficulty.Easy, 11)
        assertEquals(SudokuDifficulty.Easy, board.difficulty)
        val empty = (0 until Rules.SUDOKU_CELLS).first { !board.isGiven(it) }
        board.enter(empty, 4)
        assertEquals(4, board.digit(empty))
        assertTrue(board.undo())
        assertEquals(0, board.digit(empty))
    }

    @Test
    fun a_sudoku_digit_that_repeats_in_a_unit_is_a_conflict() {
        val board = Rules.newSudoku(SudokuDifficulty.Medium, 13)
        val first = (0 until Rules.SUDOKU_CELLS).first { !board.isGiven(it) }
        val row = first / 9
        val second = (0 until Rules.SUDOKU_CELLS).first { it != first && it / 9 == row && !board.isGiven(it) }
        board.enter(first, 4)
        board.enter(second, 4)
        val conflicts = board.conflictCells()
        assertTrue(conflicts.contains(first))
        assertTrue(conflicts.contains(second))
    }

    @Test
    fun the_same_seed_generates_the_same_sudoku() {
        val first = Rules.newSudoku(SudokuDifficulty.Hard, 21)
        val second = Rules.newSudoku(SudokuDifficulty.Hard, 21)
        assertEquals(first.toSnapshot().toList(), second.toSnapshot().toList())
    }

    @Test
    fun a_seed_can_be_typed_as_text() {
        assertEquals(Rules.seedFromText("example"), Rules.seedFromText("example"))
        assertTrue(Rules.seedFromText("example") != Rules.seedFromText("another"))
    }
}

/** The records screen's grouping and formatting, which are pure functions over its data. */
class RecordsGroupingTest {
    /** Returns a finished pipes game. */
    private fun pipes(size: Int, wrapped: Boolean, seconds: Long) =
        Record(RecordKey.Pipes(size, wrapped), "Tester", seconds, 1_000)

    /** Returns a finished sudoku game. */
    private fun sudoku(difficulty: String, seconds: Long) =
        Record(RecordKey.Sudoku(difficulty), "Tester", seconds, 1_000)

    @Test
    fun pipes_groups_come_before_sudoku_and_are_ordered_by_size() {
        val groups =
            groupRecords(
                listOf(
                    sudoku("hard", 10),
                    pipes(15, false, 10),
                    pipes(9, true, 10),
                    pipes(9, false, 10),
                ),
            )
        assertEquals(RecordKey.Pipes(9, false), groups[0].first)
        assertEquals(RecordKey.Pipes(9, true), groups[1].first)
        assertEquals(RecordKey.Pipes(15, false), groups[2].first)
        assertEquals(RecordKey.Sudoku("hard"), groups[3].first)
    }

    @Test
    fun every_record_belongs_to_exactly_one_group() {
        val records = listOf(pipes(9, false, 10), pipes(9, false, 20), sudoku("easy", 30))
        val groups = groupRecords(records)
        assertEquals(records.size, groups.sumOf { it.second.size })
    }

    @Test
    fun a_duration_past_an_hour_shows_hours() {
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("2:03:04", formatDuration(2 * 3600 + 3 * 60 + 4))
    }
}
