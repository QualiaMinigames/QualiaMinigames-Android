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

/**
 * What a field that accepts a count or a share of the board means.
 *
 * The mine field and the island field are the same control over different quantities: one asks
 * how many mines, the other how many islands, and both accept a plain number or a share written
 * with a percent sign. Which of the two was typed is kept rather than flattened, because the
 * helper line under the field says the other one: a player who typed a count reads the share it
 * is, and a player who typed a share reads the count it comes to.
 *
 * The parsing lives here once so that the two fields cannot disagree about what `12.3%` means.
 */
sealed interface CountOrPercent {
    /** A plain count. */
    data class Count(
        /** The number the field spelled. */
        val count: Int,
    ) : CountOrPercent

    /** A share of the board, in thousandths. */
    data class Percent(
        /** The share, where `123` is 12.3%. */
        val perMille: Int,
    ) : CountOrPercent
}

/**
 * Reads what a player typed into a count-or-share field.
 *
 * A trailing percent sign means the number is a share, and a plain number means a count. The
 * share is read a digit at a time into thousandths rather than through a float: `12.3` becomes
 * `123` exactly, and a value at a snap point stays at it instead of landing one thousandth away
 * and reading as a different step.
 *
 * Returns null when the text is neither: an empty field, a sign, a second decimal point, or a
 * share with more than one decimal place.
 */
fun parseCountOrPercent(text: String): CountOrPercent? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    return if (trimmed.endsWith("%")) {
        parsePerMille(trimmed.dropLast(1))?.let(CountOrPercent::Percent)
    } else {
        trimmed.takeIf { value -> value.all { it in '0'..'9' } }?.toIntOrNull()?.let(CountOrPercent::Count)
    }
}

/**
 * Reads a share into thousandths.
 *
 * The whole part is a percent and the single decimal digit is a tenth of a percent, so `12` is
 * `120` and `12.3` is `123`. Anything else — a fraction of a thousandth, a negative sign, a
 * trailing point, a second point — is refused, because the field accepts what it can display.
 */
fun parsePerMille(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val parts = trimmed.split('.')
    if (parts.size > 2) {
        return null
    }
    val whole = parts[0]
    if (whole.isEmpty() || !whole.all { it in '0'..'9' }) {
        return null
    }
    val fraction = if (parts.size == 2) parts[1] else ""
    if (fraction.length != (parts.size - 1) || !fraction.all { it in '0'..'9' }) {
        return null
    }
    val wholeValue = whole.toIntOrNull() ?: return null
    // A tenth of a percent is one unit of the thousandth scale times ten: the scale's unit is
    // 0.1%, so a whole percent is ten of them.
    val tenth = if (fraction.isEmpty()) 0 else fraction.toInt()
    return wholeValue * 10 + tenth
}

/** Returns a share as the field writes it, with one decimal place only when it has one. */
fun formatPerMille(perMille: Int): String {
    val whole = perMille / 10
    val tenth = perMille % 10
    return if (tenth == 0) "$whole%" else "$whole.$tenth%"
}
