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
    /** Returns a finished pipes game. */
    private fun pipes(seconds: Long) =
        Record(RecordKey.Pipes(9, wrapped = false), "Tester", seconds, 1_700_000_000_000)

    /** Reads a record's parameters back as a label. */
    private val label: (RecordKey) -> String = { key ->
        when (key) {
            is RecordKey.Pipes -> "${key.size}x${key.size}"
            is RecordKey.Sudoku -> key.difficulty
        }
    }

    @Test
    fun asingle_record_is_a_card_of_the_export_width() {
        val bitmap = RecordImage.single(pipes(90), label, "exported")
        assertEquals(RecordImage.width(), bitmap.width)
        assertTrue("the card has no height", bitmap.height > 0)
    }

    @Test
    fun a_column_grows_with_the_number_of_records() {
        val few = RecordImage.several(listOf(pipes(90)), label, "exported")
        val many = RecordImage.several(List(10) { pipes(90L + it) }, label, "exported")
        assertTrue("the column did not grow", many.height > few.height)
        assertEquals(RecordImage.width(), many.width)
    }

    @Test
    fun the_column_height_matches_what_the_layout_reports() {
        val records = List(7) { pipes(60) }
        val bitmap = RecordImage.several(records, label, "exported")
        assertEquals(RecordImage.severalHeight(records.size), bitmap.height)
    }

    @Test
    fun an_empty_column_is_still_a_valid_image() {
        val bitmap = RecordImage.several(emptyList(), label, "exported")
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }
}
