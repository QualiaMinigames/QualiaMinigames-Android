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

package com.qiushui1012.app.minigames.export

import android.graphics.Bitmap
import android.graphics.Typeface
import com.qiushui1012.app.minigames.data.Record
import com.qiushui1012.app.minigames.data.RecordKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The record images, drawn against Robolectric's graphics.
 *
 * The assertions are about shape rather than about pixels: an image that is the wrong size is
 * the failure that matters, because it is what cuts a record off or leaves a column of blank
 * space at its end. A pixel comparison would fail on every change of a margin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordImageTest {
    /** The column headings the several-record layout draws. */
    private val HEADERS = listOf("Player", "Game", "Time", "Finished")

    /** Returns a finished pipes game. */
    private fun pipes(seconds: Long) =
        Record(RecordKey.Pipes(9, wrapped = false), "Tester", seconds, 1_700_000_000_000, seed = 4_242)

    /** Returns a record's game name. */
    private val name: (RecordKey) -> String = { key ->
        when (key) {
            is RecordKey.Pipes -> "Pipes"
            is RecordKey.Sudoku -> "Sudoku"
        }
    }

    /** Reads a record's parameters back as a label. */
    private val params: (RecordKey) -> String = { key ->
        when (key) {
            is RecordKey.Pipes -> "${key.size}x${key.size}"
            is RecordKey.Sudoku -> key.difficulty
        }
    }

    /** Returns a single record's image with the defaults every test shares. */
    private fun single(record: Record) =
        RecordImage.single(record, name, params, "Unnamed", "exported", Typeface.MONOSPACE)

    /** Returns a column's image with the defaults every test shares. */
    private fun several(records: List<Record>) =
        RecordImage.several(records, name, params, "Unnamed", "exported", "Play records", HEADERS, Typeface.MONOSPACE)

    @Test
    fun asingle_record_is_a_card_of_the_export_width() {
        val bitmap = single(pipes(90))
        assertEquals(RecordImage.width(), bitmap.width)
        assertTrue("the card has no height", bitmap.height > 0)
    }

    @Test
    fun a_single_record_has_room_for_its_facts_and_the_export_time() {
        // The layout places the time, the player and the finish time one below the other and
        // the export time below the card: a height that fits a card alone would clip the last
        // of them, which is the defect the fixed constants used to hide.
        val short = single(pipes(1))
        val long = single(pipes(100_000))
        assertEquals("the card is as tall as its facts", short.height, long.height)
        assertTrue("the export time has its own strip", short.height > 520)
    }

    @Test
    fun a_column_grows_with_the_number_of_records() {
        val few = several(listOf(pipes(90)))
        val many = several(List(10) { pipes(90L + it) })
        assertTrue("the column did not grow", many.height > few.height)
    }

    @Test
    fun a_column_is_as_wide_as_its_table_and_no_wider() {
        val narrow = several(listOf(pipes(90)))
        val wide = several(listOf(pipes(90).copy(playerName = "A player with a very long name indeed")))
        assertTrue("a wider cell widens the image", wide.width > narrow.width)
        assertTrue("the image is wider than its content needs", narrow.width < RecordImage.width())
    }

    @Test
    fun an_empty_column_is_still_a_valid_image() {
        val bitmap = several(emptyList())
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }
}
