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

import com.qiushui1012.app.minigames.rules.Rules

/**
 * The seed a game is started from, and whether the player typed it.
 *
 * The number is what the rules take. The flag is what a record keeps, so that a history can
 * say a seed was chosen rather than produced: both are numbers, and only the player knows
 * which of them they meant.
 */
data class ResolvedSeed(
    /** The seed the rules are called with. */
    val seed: Long,
    /** Whether the seed came from the field rather than from the clock. */
    val typed: Boolean,
)

/**
 * Turns what a player typed into a seed.
 *
 * The field accepts anything, because a seed is a number a player reads out of a record and
 * types back: an empty field is a random game, a field of digits that fits a `u64` is that
 * number, and anything else — a negative number, a leading plus, a number too large, a word —
 * is hashed as text. Hashing rather than refusing is what makes every seed a record can hold
 * a seed a player can type again.
 *
 * [fallback] is the seed a random game starts from, which the dialog takes when it opens so
 * that the moment the button is pressed cannot change the board.
 */
fun resolveSeed(text: String, fallback: Long): ResolvedSeed {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return ResolvedSeed(fallback, typed = false)
    }
    // A text of decimal digits that fits a `u64` is that number. It is parsed as a `ULong`
    // because the seed is a `u64` and half of its range does not fit a `Long`: skipping that
    // half would hash a number the player typed as a decimal number, which is the one thing
    // the field promises not to do.
    val digits = trimmed.isNotEmpty() && trimmed.all { it in '0'..'9' }
    val number = if (digits) trimmed.toULongOrNull() else null
    if (number != null) {
        return ResolvedSeed(number.toLong(), typed = true)
    }
    return ResolvedSeed(Rules.seedFromText(trimmed), typed = true)
}
