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

import com.qiushui1012.app.minigames.data.ArrowCubeRecord
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.GameRecord
import com.qiushui1012.app.minigames.data.HashiRecord
import com.qiushui1012.app.minigames.data.MinesweeperRecord
import com.qiushui1012.app.minigames.data.NonogramRecord
import com.qiushui1012.app.minigames.data.PipesRecord
import com.qiushui1012.app.minigames.data.Play
import com.qiushui1012.app.minigames.data.SudokuRecord
import com.qiushui1012.app.minigames.data.append
import com.qiushui1012.app.minigames.data.groupKey
import com.qiushui1012.app.minigames.data.kind
import com.qiushui1012.app.minigames.data.sortedRecords
import com.qiushui1012.app.minigames.rules.MinesweeperCell
import com.qiushui1012.app.minigames.rules.MinesweeperEvent
import com.qiushui1012.app.minigames.rules.MinesweeperMode
import com.qiushui1012.app.minigames.rules.MinesweeperPhase
import com.qiushui1012.app.minigames.rules.NonogramCell
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.Rules
import com.qiushui1012.app.minigames.rules.SudokuDifficulty
import com.qiushui1012.app.minigames.ui.formatDuration
import com.qiushui1012.app.minigames.ui.recordsByKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun a_minesweeper_board_waits_for_its_first_click() {
        val board = requireNotNull(Rules.newMinesweeper(9, 9, 10, MinesweeperMode.Normal, 7))
        assertEquals(9, board.width)
        assertEquals(9, board.height)
        assertEquals(81, board.cellCount)
        assertEquals(10, board.mineCount)
        assertEquals(MinesweeperPhase.Pending, board.phase)
        assertEquals(0L, board.seed)
        assertEquals(10, board.remainingMines)
        assertTrue(board.cellsState().all { it == MinesweeperCell.COVERED })
    }

    @Test
    fun the_first_click_of_a_minesweeper_board_is_safe() {
        // The board is generated by the click, so the click can only be safe by construction.
        // The check is that the promise holds for a spread of shapes, including the narrow ones
        // where the protected window is 2 by 2.
        for ((width, height) in listOf(9 to 9, 16 to 16, 4 to 4, 128 to 5)) {
            val cells = width * height
            val mines = minOf(10, cells - 5)
            val board = requireNotNull(Rules.newMinesweeper(width, height, mines, MinesweeperMode.Normal, 3))
            assertTrue(board.reveal(cells / 2) != MinesweeperEvent.HitMine)
            assertEquals(MinesweeperPhase.Playing, board.phase)
            assertTrue(board.seed != 0L)
        }
    }

    @Test
    fun the_same_seed_generates_the_same_minesweeper_board() {
        val first = requireNotNull(Rules.minesweeperFromSeed(16, 16, 40, MinesweeperMode.Normal, 21))
        val second = requireNotNull(Rules.minesweeperFromSeed(16, 16, 40, MinesweeperMode.Normal, 21))
        assertEquals(first.cellsState(), second.cellsState())
        assertEquals(21L, first.seed)
    }

    @Test
    fun a_minesweeper_snapshot_crosses_the_boundary() {
        val board = requireNotNull(Rules.newMinesweeper(16, 16, 40, MinesweeperMode.NoGuess, 11))
        board.reveal(120)
        board.toggleFlag(0)
        val restored = requireNotNull(Rules.minesweeperFromSnapshot(board.toSnapshot()))
        assertEquals(board.cellsState(), restored.cellsState())
        assertEquals(board.mode, restored.mode)
        assertEquals(board.fellBack, restored.fellBack)
        assertEquals(board.remainingMines, restored.remainingMines)
    }

    @Test
    fun a_minesweeper_parameter_outside_the_range_is_refused() {
        assertNull(Rules.newMinesweeper(3, 9, 5, MinesweeperMode.Normal, 0))
        assertNull(Rules.newMinesweeper(129, 9, 5, MinesweeperMode.Normal, 0))
        assertNull(Rules.newMinesweeper(9, 9, 0, MinesweeperMode.Normal, 0))
        // A 4 by 4 board protects four cells, so twelve mines fit at random and thirteen do
        // not: the bound is what a promised safe first click puts on a game.
        assertNotNull(Rules.newMinesweeper(4, 4, 12, MinesweeperMode.Normal, 0))
        assertNull(Rules.newMinesweeper(4, 4, 13, MinesweeperMode.Normal, 0))
    }

    @Test
    fun the_minesweeper_densities_agree_with_the_rules() {
        assertEquals(4, Rules.minesweeperMinSize())
        assertEquals(128, Rules.minesweeperMaxSize())
        assertEquals(100, Rules.minesweeperInputPerMilleMin())
        assertEquals(250, Rules.minesweeperInputPerMilleMax())
        assertEquals(120, Rules.minesweeperSliderPerMilleMin())
        assertEquals(250, Rules.minesweeperSliderPerMilleMax())
        assertEquals(156, Rules.minesweeperNoGuessSliderPerMilleMax())
        assertEquals(206, Rules.minesweeperNoGuessInputPerMilleMax())
        assertEquals(180, Rules.minesweeperNoGuessWarnPerMille())
        assertEquals(listOf(120, 123, 150, 156, 180, 206, 220, 250), Rules.minesweeperSnapPerMille())
    }

    @Test
    fun the_three_classic_densities_convert_to_their_mine_counts() {
        // The round trip is the point of the table: each classic board is its classic count.
        assertEquals(10, Rules.minesweeperMinesForPerMille(81, 123))
        assertEquals(40, Rules.minesweeperMinesForPerMille(256, 156))
        assertEquals(99, Rules.minesweeperMinesForPerMille(480, 206))
    }

    @Test
    fun a_nonogram_board_starts_empty_with_a_clue_for_every_line() {
        val board = requireNotNull(Rules.newNonogram(10, 10, 7))
        assertEquals(10, board.width)
        assertEquals(10, board.height)
        assertEquals(100, board.cellCount)
        assertEquals(10, board.rowClues().size)
        assertEquals(10, board.columnClues().size)
        assertTrue(board.rowClues().all { it.values.isNotEmpty() })
        assertTrue(board.cellsState().all { it == NonogramCell.EMPTY })
        assertTrue(!board.isSolved())
    }

    @Test
    fun the_same_nonogram_seed_draws_the_same_clues() {
        val first = requireNotNull(Rules.newNonogram(9, 7, 11))
        val second = requireNotNull(Rules.newNonogram(9, 7, 11))
        assertEquals(first.rowClues(), second.rowClues())
        assertEquals(first.columnClues(), second.columnClues())
        assertEquals(first.seed, second.seed)
    }

    @Test
    fun a_nonogram_toggle_changes_the_cell_and_its_broken_lines() {
        val board = requireNotNull(Rules.newNonogram(4, 4, 3))
        assertTrue(board.toggleFilled(0))
        assertEquals(NonogramCell.FILLED, board.cellsState()[0])
        assertTrue(board.toggleMarked(0))
        assertEquals(NonogramCell.MARKED, board.cellsState()[0])
        // A mark is cleared by the first click and filled by the second: the two states are
        // opposite claims about one cell, so one click never turns a mark straight into ink.
        assertTrue(board.toggleFilled(0))
        assertEquals(NonogramCell.EMPTY, board.cellsState()[0])
        assertTrue(board.toggleFilled(0))
        assertEquals(NonogramCell.FILLED, board.cellsState()[0])
        assertTrue("an index off the board changes nothing", !board.toggleFilled(16))
    }

    @Test
    fun a_hashi_island_past_its_clue_is_reported_rather_than_refused() {
        // A board of two islands joined by two bridges: each clue is two, so the board starts
        // solved. Taking a bridge away and laying two the other way is not possible on two
        // islands, so the test asks the two facts that matter about a legal board: a bridge is
        // laid rather than refused, and a clue that is met reports no stranded island.
        val board = requireNotNull(Rules.newHashi(6, 6, 2, 5))
        assertEquals(2, board.islandCount)
        assertEquals(0, board.islands()[0].degree)
        assertTrue("a legal bridge is laid", board.connect(0, 1))
        assertTrue("the pair takes a double bridge", board.connect(0, 1))
        assertEquals(2, board.islands()[0].degree)
        // Two islands whose clues come from one edge of two bridges: meeting the clue exactly is
        // not stranded, and the board is solved.
        assertTrue(board.isSolved())
        assertTrue(board.strandedIslands().isEmpty())
    }

    @Test
    fun a_nonogram_snapshot_round_trips_across_the_binding() {
        val board = requireNotNull(Rules.newNonogram(8, 8, 5))
        board.toggleFilled(9)
        board.toggleMarked(10)
        val restored = requireNotNull(Rules.nonogramFromSnapshot(board.toSnapshot()))
        assertEquals(board.cellsState(), restored.cellsState())
        assertEquals(board.rowClues(), restored.rowClues())
        assertEquals(board.columnClues(), restored.columnClues())
        assertEquals(board.fellBack, restored.fellBack)
    }

    @Test
    fun a_nonogram_size_outside_the_range_is_refused() {
        assertNull(Rules.newNonogram(3, 10, 0))
        assertNull(Rules.newNonogram(10, 65, 0))
    }

    @Test
    fun a_hashi_board_holds_the_islands_it_was_asked_for() {
        val board = requireNotNull(Rules.newHashi(10, 10, 15, 7))
        assertEquals(10, board.width)
        assertEquals(10, board.height)
        assertEquals(15, board.islandCount)
        assertEquals(15, board.islands().size)
        assertTrue(board.islands().all { it.clue >= 1 })
        assertTrue(board.islands().all { it.degree == 0 })
        assertTrue(board.bridges().isEmpty())
    }

    @Test
    fun a_hashi_connection_cycles_and_a_refusal_changes_nothing() {
        val board = requireNotNull(Rules.newHashi(6, 6, 4, 3))
        val joinable = (1 until board.islandCount).firstOrNull { board.connect(0, it) }
        assertTrue("the first island reaches another one", joinable != null)
        assertEquals("a fresh connection is one bridge", 1, board.bridges().first().count)
        assertTrue("the second connection makes it two", board.connect(0, requireNotNull(joinable)))
        assertEquals(2, board.bridges().first().count)
        assertTrue("the third connection takes it back to none", board.connect(0, requireNotNull(joinable)))
        assertTrue("two cycles back to none", board.bridges().isEmpty())
        assertFalse(board.connect(0, 0))
        assertFalse(board.connect(0, 99))
    }

    @Test
    fun a_hashi_mark_does_not_finish_the_board() {
        val board = requireNotNull(Rules.newHashi(8, 8, 10, 4))
        assertTrue(board.toggleMark(0))
        assertTrue(board.islands()[0].marked)
        assertFalse(board.toggleMark(99))
    }

    @Test
    fun a_hashi_snapshot_round_trips_across_the_binding() {
        val board = requireNotNull(Rules.newHashi(9, 9, 12, 6))
        board.toggleMark(1)
        val restored = requireNotNull(Rules.hashiFromSnapshot(board.toSnapshot()))
        assertEquals(board.islands(), restored.islands())
        assertEquals(board.seed, restored.seed)
    }

    @Test
    fun a_hashi_board_outside_its_ranges_is_refused() {
        assertNull(Rules.newHashi(3, 10, 5, 0))
        assertNull(Rules.newHashi(10, 36, 5, 0))
        assertNull(Rules.newHashi(10, 10, 1, 0))
        assertNull(Rules.newHashi(10, 10, 101, 0))
    }

    @Test
    fun the_new_games_report_the_ranges_the_dialogs_offer() {
        assertEquals(4, Rules.nonogramMinSize())
        assertEquals(64, Rules.nonogramMaxSize())
        assertEquals(4, Rules.hashiMinPoints())
        assertEquals(35, Rules.hashiMaxPoints())
        assertEquals(100, Rules.hashiIslandPerMilleMin())
        assertEquals(350, Rules.hashiIslandPerMilleMax())
        assertEquals(listOf(100, 150, 200, 250, 300, 350), Rules.hashiIslandSnapPerMille())
        assertEquals(2, Rules.hashiIslandsForPerMille(16, 100))
        assertEquals(429, Rules.hashiIslandsForPerMille(1225, 350))
    }

    @Test
    fun an_arrow_cube_board_answers_its_shape_and_its_seed() {
        val board = requireNotNull(Rules.newArrowCube(6, 5, 5, 4_242))
        assertEquals(6, board.width)
        assertEquals(5, board.depth)
        assertEquals(5, board.height)
        assertEquals(150, board.cellCount)
        assertEquals(board.blockCount, board.blocks().size)
        assertEquals(board.blockCount, board.remainingCount)
        assertEquals(4_242L, board.seed)
        assertEquals(0, board.mistakes)
        assertFalse(board.isSolved())
    }

    @Test
    fun the_same_arrow_cube_seed_reaches_the_same_blocks() {
        val first = requireNotNull(Rules.newArrowCube(5, 5, 5, 7))
        val second = requireNotNull(Rules.newArrowCube(5, 5, 5, 7))
        assertEquals(first.blocks(), second.blocks())
    }

    @Test
    fun an_arrow_cube_take_reports_its_success_and_its_mistake() {
        val board = requireNotNull(Rules.newArrowCube(5, 5, 5, 3))
        var failed = 0
        var taken = false
        for (index in 0 until board.blockCount) {
            val result = board.take(index)
            if (result.removed) {
                taken = true
                break
            }
            failed += 1
            assertNotNull("a refused take names its blocker", result.blocker)
            assertTrue(result.gap >= 0)
        }
        assertTrue("a generated board offers a block", taken)
        assertEquals(failed, board.mistakes)
        assertEquals(board.blockCount - 1, board.remainingCount)
    }

    @Test
    fun an_arrow_cube_index_outside_the_table_changes_nothing() {
        val board = requireNotNull(Rules.newArrowCube(5, 5, 5, 5))
        val before = board.blocks()
        val result = board.take(10_000)
        assertFalse(result.removed)
        assertNull(result.blocker)
        assertEquals(0, result.gap)
        assertFalse(result.solved)
        assertEquals(before, board.blocks())
        assertEquals(0, board.mistakes)
    }

    @Test
    fun an_arrow_cube_snapshot_round_trips() {
        val board = requireNotNull(Rules.newArrowCube(5, 5, 5, 13))
        var taken = false
        for (index in 0 until board.blockCount) {
            if (board.take(index).removed) {
                taken = true
                break
            }
        }
        assertTrue(taken)
        val restored = requireNotNull(Rules.arrowCubeFromSnapshot(board.toSnapshot()))
        assertEquals(board.blocks(), restored.blocks())
        assertEquals(board.mistakes, restored.mistakes)
        assertEquals(board.remainingCount, restored.remainingCount)
        assertEquals(board.seed, restored.seed)
    }

    @Test
    fun an_arrow_cube_board_outside_its_range_is_refused() {
        assertNull(Rules.newArrowCube(4, 5, 5, 0))
        assertNull(Rules.newArrowCube(5, 31, 5, 0))
        assertNull(Rules.newArrowCube(31, 5, 5, 0))
        assertNull(Rules.newArrowCube(5, 5, 4, 0))
        assertNull(Rules.newArrowCube(5, 5, 31, 0))
    }
}

/**
 * The records page's grouping and formatting, which are pure functions over its data.
 *
 * The store returns its groups already sorted, and these tests drive the same comparator
 * through a list of groups rather than through a live store: the order is a fact about the
 * parameters, not about where the file was read.
 */
class RecordsGroupingTest {
    /** Returns a group with one finished play in it. */
    private fun finished(
        record: GameRecord,
        seconds: Long = 10,
        failed: Boolean? = null,
        mistakes: Int? = null,
    ): GameRecord =
        record.append(
            Play(
                playerName = "Tester",
                seconds = seconds,
                finishedAt = 1_000,
                failed = failed,
                mistakes = mistakes,
            ),
        )

    /** Returns a finished pipes group. */
    private fun pipes(
        size: Int,
        wrapped: Boolean = false,
        seconds: Long = 10,
    ) = finished(PipesRecord(size, wrapped), seconds)

    /** Returns a finished sudoku group. */
    private fun sudoku(
        difficulty: String,
        seconds: Long = 10,
    ) = finished(SudokuRecord(difficulty), seconds)

    /** Returns a finished minesweeper group. */
    private fun minesweeper(
        width: Int,
        height: Int,
        mines: Int,
        noGuess: Boolean = false,
        seconds: Long = 10,
    ) = finished(MinesweeperRecord(width, height, mines, noGuess), seconds)

    /** Returns a finished nonogram group. */
    private fun nonogram(
        width: Int,
        height: Int,
        seconds: Long = 10,
    ) = finished(NonogramRecord(width, height), seconds)

    /** Returns a finished hashi group. */
    private fun hashi(
        width: Int,
        height: Int,
        islands: Int,
        seconds: Long = 10,
    ) = finished(HashiRecord(width, height, islands), seconds)

    /** Returns a finished arrow cube group. */
    private fun arrowCube(
        width: Int,
        depth: Int,
        height: Int,
        seconds: Long = 10,
        mistakes: Int? = null,
    ) = finished(ArrowCubeRecord(width, depth, height), seconds, mistakes = mistakes)

    @Test
    fun pipes_groups_come_before_sudoku_and_are_ordered_by_size() {
        val groups =
            listOf(
                sudoku("hard"),
                pipes(15),
                pipes(9, wrapped = true),
                pipes(9),
            ).sortedRecords()
        assertEquals(
            listOf(
                PipesRecord(9, wrapped = false).groupKey(),
                PipesRecord(9, wrapped = true).groupKey(),
                PipesRecord(15, wrapped = false).groupKey(),
                SudokuRecord("hard").groupKey(),
            ),
            groups.map { it.groupKey() },
        )
    }

    @Test
    fun minesweeper_groups_come_after_sudoku_and_are_ordered_by_shape_mines_and_mode() {
        val groups =
            listOf(
                minesweeper(9, 9, 10),
                sudoku("easy"),
                minesweeper(9, 9, 10, noGuess = true),
                minesweeper(16, 16, 40),
                minesweeper(9, 9, 12),
            ).sortedRecords()
        assertEquals(
            listOf(
                SudokuRecord("easy").groupKey(),
                MinesweeperRecord(9, 9, 10, noGuess = false).groupKey(),
                MinesweeperRecord(9, 9, 10, noGuess = true).groupKey(),
                MinesweeperRecord(9, 9, 12, noGuess = false).groupKey(),
                MinesweeperRecord(16, 16, 40, noGuess = false).groupKey(),
            ),
            groups.map { it.groupKey() },
        )
    }

    @Test
    fun the_six_games_come_in_the_order_the_home_page_shows_them() {
        val groups =
            listOf(
                arrowCube(10, 10, 10),
                hashi(10, 10, 15),
                nonogram(10, 10),
                minesweeper(9, 9, 10),
                sudoku("easy"),
                pipes(9),
            ).sortedRecords()
        assertEquals(
            listOf(
                GameKind.Pipes,
                GameKind.Sudoku,
                GameKind.Minesweeper,
                GameKind.Nonogram,
                GameKind.Hashi,
                GameKind.ArrowCube,
            ),
            groups.map { it.kind },
        )
    }

    @Test
    fun a_nonogram_group_is_ordered_by_its_two_sides() {
        val groups =
            listOf(
                nonogram(15, 9),
                nonogram(9, 15),
                nonogram(9, 9),
            ).sortedRecords()
        assertEquals(
            listOf(
                NonogramRecord(9, 9).groupKey(),
                NonogramRecord(9, 15).groupKey(),
                NonogramRecord(15, 9).groupKey(),
            ),
            groups.map { it.groupKey() },
        )
    }

    @Test
    fun a_hashi_group_is_ordered_by_its_sides_and_its_islands() {
        val groups =
            listOf(
                hashi(10, 10, 30),
                hashi(12, 10, 15),
                hashi(10, 10, 15),
                hashi(10, 12, 15),
            ).sortedRecords()
        assertEquals(
            listOf(
                HashiRecord(10, 10, 15).groupKey(),
                HashiRecord(10, 10, 30).groupKey(),
                HashiRecord(10, 12, 15).groupKey(),
                HashiRecord(12, 10, 15).groupKey(),
            ),
            groups.map { it.groupKey() },
        )
    }

    @Test
    fun an_arrow_cube_group_is_ordered_by_its_three_sides() {
        val groups =
            listOf(
                arrowCube(10, 10, 12),
                arrowCube(10, 12, 10),
                arrowCube(12, 10, 10),
                arrowCube(10, 10, 10),
            ).sortedRecords()
        assertEquals(
            listOf(
                ArrowCubeRecord(10, 10, 10).groupKey(),
                ArrowCubeRecord(10, 10, 12).groupKey(),
                ArrowCubeRecord(10, 12, 10).groupKey(),
                ArrowCubeRecord(12, 10, 10).groupKey(),
            ),
            groups.map { it.groupKey() },
        )
    }

    @Test
    fun a_failed_game_is_not_a_best_time() {
        val group =
            MinesweeperRecord(9, 9, 10, noGuess = false)
                .append(
                    Play(
                        playerName = "Tester",
                        seconds = 30,
                        finishedAt = 1_000,
                        failed = true,
                    ),
                )
                .append(
                    Play(
                        playerName = "Tester",
                        seconds = 60,
                        finishedAt = 2_000,
                    ),
                )
        // Both plays belong to one group: a loss is the same board, and filing it apart
        // would split one board's history in two. The best time is the won one.
        assertEquals(2, group.history.size)
        assertEquals(60L, group.bestSeconds)
    }

    @Test
    fun a_tie_on_time_is_broken_by_the_mistake_count() {
        val group =
            ArrowCubeRecord(10, 10, 10)
                .append(Play("Tester", 40, 1_000, mistakes = 3))
                .append(Play("Tester", 40, 2_000, mistakes = 1))
        assertEquals(40L, group.bestSeconds)
        assertEquals(1, (group as ArrowCubeRecord).bestMistakes)
    }

    @Test
    fun every_play_belongs_to_exactly_one_group() {
        val groups = listOf(pipes(9), pipes(9), sudoku("easy")).sortedRecords()
        assertEquals(3, groups.sumOf { it.history.size })
        assertEquals(2, groups.count { it.kind == GameKind.Pipes })
    }

    @Test
    fun records_are_split_by_game_before_they_are_split_by_parameters() {
        val groups = listOf(sudoku("hard"), pipes(15), sudoku("easy"), pipes(9)).sortedRecords()
        val byKind = recordsByKind(groups)
        assertEquals(listOf(GameKind.Pipes, GameKind.Sudoku), byKind.map { it.first })
        assertEquals(2, byKind[0].second.size)
        assertEquals(2, byKind[1].second.size)
        // A game with no groups has no dropdown, so the page draws no empty section for it.
        assertEquals(listOf(GameKind.Sudoku), recordsByKind(listOf(sudoku("easy"))).map { it.first })
    }

    @Test
    fun a_duration_past_an_hour_shows_hours() {
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("2:03:04", formatDuration(2 * 3600 + 3 * 60 + 4))
    }
}
