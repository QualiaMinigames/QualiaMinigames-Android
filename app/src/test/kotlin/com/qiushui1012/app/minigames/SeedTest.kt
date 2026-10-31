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

import com.qiushui1012.app.minigames.rules.MinesweeperMode
import com.qiushui1012.app.minigames.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test

/**
 * What a player types into the seed field and into the mine field.
 *
 * Both are text fields that accept more than their obvious reading: a seed is a number or a
 * word, and a mine count is a count or a share. The rules of that reading live here rather
 * than in a dialog, so they can be checked without a screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeedTest {
    @Test
    fun a_number_is_itself() {
        assertEquals(ResolvedSeed(123, typed = true), resolveSeed("123", 7))
        assertEquals(ResolvedSeed(123, typed = true), resolveSeed("000123", 7))
    }

    @Test
    fun a_number_above_the_signed_range_keeps_its_bits() {
        // A `u64` seed is stored in a `Long`, and half of its values do not fit a `Long`'s
        // range. A number that does fit a `u64` is still that number rather than a hash of its
        // digits, because the field promises a number means a number.
        val resolved = resolveSeed("18446744073709551615", 7)
        assertTrue(resolved.typed)
        assertEquals(-1L, resolved.seed)
    }

    @Test
    fun a_number_too_large_for_a_u64_is_hashed_as_text() {
        val resolved = resolveSeed("18446744073709551616", 7)
        assertTrue(resolved.typed)
        assertEquals(Rules.seedFromText("18446744073709551616"), resolved.seed)
    }

    @Test
    fun anything_that_is_not_a_number_is_hashed() {
        for (text in listOf("-1", "+1", "1e6", "seed", "12 34")) {
            val resolved = resolveSeed(text, 7)
            assertTrue("${text} is not a number", resolved.typed)
            assertEquals("${text} was not hashed", Rules.seedFromText(text), resolved.seed)
        }
    }

    @Test
    fun an_empty_field_is_a_random_game() {
        assertEquals(ResolvedSeed(99, typed = false), resolveSeed("", 99))
        assertEquals(ResolvedSeed(99, typed = false), resolveSeed("   ", 99))
    }

    @Test
    fun a_field_is_trimmed_before_it_is_read() {
        assertEquals(ResolvedSeed(123, typed = true), resolveSeed("  123  ", 7))
        // The hash is of the trimmed text, so a stray space does not change a word's seed.
        assertEquals(Rules.seedFromText("seed"), resolveSeed(" seed ", 7).seed)
    }
}

/** What the mine field means, and the params a form is allowed to start. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MinesweeperParamsTest {
    @Test
    fun a_share_is_read_one_digit_at_a_time() {
        assertEquals(123, parsePerMille("12.3"))
        assertEquals(120, parsePerMille("12"))
        assertEquals(150, parsePerMille("15.0"))
        assertEquals(100, parsePerMille("10"))
        assertEquals(250, parsePerMille("25"))
    }

    @Test
    fun a_share_the_field_cannot_show_is_refused() {
        for (text in listOf("", "12.34", "-1", "12.", ".3", "1.2.3", "abc")) {
            assertNull(text, parsePerMille(text))
        }
    }

    @Test
    fun a_share_is_written_with_one_decimal_place_only_when_it_has_one() {
        assertEquals("12.3%", formatPerMille(123))
        assertEquals("12%", formatPerMille(120))
        assertEquals("25%", formatPerMille(250))
    }

    @Test
    fun the_field_reads_a_count_and_a_share_apart() {
        assertEquals(CountOrPercent.Count(10), parseMinesInput("10"))
        assertEquals(CountOrPercent.Percent(123), parseMinesInput("12.3%"))
        assertNull(parseMinesInput(""))
        assertNull(parseMinesInput("abc"))
    }

    @Test
    fun a_share_and_a_count_convert_between_each_other() {
        assertEquals(123, perMilleForCount(81, 10))
        assertEquals(10, minesForPerMille(81, 123))
        assertEquals(156, perMilleForCount(256, 40))
        assertEquals(40, minesForPerMille(256, 156))
    }

    @Test
    fun a_snap_point_is_taken_within_one_thousandth() {
        assertEquals(123, snapPerMille(124))
        assertEquals(123, snapPerMille(122))
        assertNull(snapPerMille(125))
        assertEquals(2, nearestSnapIndex(150))
    }

    @Test
    fun the_params_are_the_ones_the_rules_accept() {
        assertTrue(minesweeperParamsValid(9, 9, 10, 123, MinesweeperMode.Normal))
        // A width below the smallest board, a mine count of zero and a share above the ceiling
        // are each refused rather than clamped: the form is where a value is corrected.
        assertFalse(minesweeperParamsValid(3, 9, 10, 123, MinesweeperMode.Normal))
        assertFalse(minesweeperParamsValid(9, 9, 0, 123, MinesweeperMode.Normal))
        assertFalse(minesweeperParamsValid(9, 9, 10, 260, MinesweeperMode.Normal))
        // The no-guess ceiling is lower than the normal one.
        assertFalse(minesweeperParamsValid(16, 16, 66, 220, MinesweeperMode.NoGuess))
        assertTrue(minesweeperParamsValid(16, 16, 50, 156, MinesweeperMode.NoGuess))
        // A random board's mines have to fit outside the first click's window.
        assertFalse(minesweeperParamsValid(4, 4, 13, 250, MinesweeperMode.Normal))
    }
}
