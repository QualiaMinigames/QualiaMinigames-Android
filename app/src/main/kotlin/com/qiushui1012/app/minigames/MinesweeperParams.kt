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

/**
 * What the mine field of a minesweeper form means.
 *
 * The field accepts two spellings of the same thing: a number of mines, and a share of the
 * board written with a percent sign. Which one was typed is kept rather than flattened,
 * because the helper line under the field says the other one: a player who typed a count
 * reads the share it is, and a player who typed a share reads the count it comes to.
 */
sealed interface MinesInput {
    /** A number of mines. */
    data class Count(
        /** The number the field spelled. */
        val mines: Int,
    ) : MinesInput

    /** A share of the board, in thousandths of a percent. */
    data class Percent(
        /** The share, where `123` is 12.3%. */
        val perMille: Int,
    ) : MinesInput
}

/** The widest share a mine field accepts, in thousandths of a percent. */
fun inputPerMilleMax(mode: MinesweeperMode): Int =
    if (mode == MinesweeperMode.NoGuess) {
        Rules.minesweeperNoGuessInputPerMilleMax()
    } else {
        Rules.minesweeperInputPerMilleMax()
    }

/** The widest share the mine slider covers, in thousandths of a percent. */
fun sliderPerMilleMax(mode: MinesweeperMode): Int =
    if (mode == MinesweeperMode.NoGuess) {
        Rules.minesweeperNoGuessSliderPerMilleMax()
    } else {
        Rules.minesweeperSliderPerMilleMax()
    }

/** The narrowest share the mine slider covers, in thousandths of a percent. */
fun sliderPerMilleMin(): Int = Rules.minesweeperSliderPerMilleMin()

/** Returns the number of cells a board of this shape has. */
fun cellsOf(width: Int, height: Int): Int = width * height

/** Returns the share a number of mines is of a board, in thousandths of a percent. */
fun perMilleForCount(cells: Int, mines: Int): Int =
    if (cells <= 0) {
        0
    } else {
        ((mines.toLong() * 1000 + cells / 2) / cells).toInt()
    }

/** Returns the number of mines a share of a board comes to. */
fun minesForPerMille(cells: Int, perMille: Int): Int =
    if (cells <= 0) {
        0
    } else {
        Rules.minesweeperMinesForPerMille(cells, perMille)
    }

/**
 * Reads what a player typed into the mine field.
 *
 * A trailing percent sign means the number is a share, and a plain number means a count. The
 * share is read a digit at a time into thousandths of a percent rather than through a float:
 * `12.3` becomes `123` exactly, and a value at a snap point stays at it instead of landing one
 * thousandth away and reading as a different density.
 *
 * Returns null when the text is neither: an empty field, a sign, a second decimal point, or a
 * share with more than one decimal place.
 */
fun parseMinesInput(text: String): MinesInput? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    return if (trimmed.endsWith("%")) {
        parsePerMille(trimmed.dropLast(1))?.let(MinesInput::Percent)
    } else {
        trimmed.takeIf { value -> value.all { it in '0'..'9' } }?.toIntOrNull()?.let(MinesInput::Count)
    }
}

/**
 * Reads a share into thousandths of a percent.
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
    // A tenth of a percent is one unit of the thousandth-of-a-percent scale times ten: the
    // scale's unit is 0.1%, so a whole percent is ten of them.
    val tenth = if (fraction.isEmpty()) 0 else fraction.toInt()
    return wholeValue * 10 + tenth
}

/** Returns a share as the field writes it, with one decimal place only when it has one. */
fun formatPerMille(perMille: Int): String {
    val whole = perMille / 10
    val tenth = perMille % 10
    return if (tenth == 0) "$whole%" else "$whole.$tenth%"
}

/**
 * Returns the snap point a share takes, or null when it stays where it is.
 *
 * The table is the core's own, so a slider and the rules cannot disagree about where the
 * named densities are.
 */
fun snapPerMille(perMille: Int): Int? =
    Rules.minesweeperSnapPerMille().firstOrNull { kotlin.math.abs(it - perMille) <= 1 }

/** Returns the index of the snap point a share is nearest to. */
fun nearestSnapIndex(perMille: Int): Int {
    val table = Rules.minesweeperSnapPerMille()
    var best = 0
    for (index in table.indices) {
        if (kotlin.math.abs(table[index] - perMille) < kotlin.math.abs(table[best] - perMille)) {
            best = index
        }
    }
    return best
}

/** The most mines a random board of this shape can hold, which is what a first click leaves. */
fun maxRandomMines(width: Int, height: Int): Int {
    val protected = if (width < 7 || height < 7) 4 else 9
    return cellsOf(width, height) - protected
}

/** Whether a form's numbers are a game the rules will accept. */
fun minesweeperParamsValid(
    width: Int,
    height: Int,
    mines: Int,
    perMille: Int,
    mode: MinesweeperMode,
): Boolean {
    val minimum = Rules.minesweeperMinSize()
    val maximum = Rules.minesweeperMaxSize()
    if (width !in minimum..maximum || height !in minimum..maximum) {
        return false
    }
    val cells = cellsOf(width, height)
    if (mines < 1 || mines >= cells) {
        return false
    }
    if (perMille < Rules.minesweeperInputPerMilleMin() || perMille > inputPerMilleMax(mode)) {
        return false
    }
    return mines <= maxRandomMines(width, height)
}

/** The three densities a form's preset buttons fill in, with their shape. */
enum class MinesweeperPreset(
    /** The number of cells across. */
    val width: Int,
    /** The number of cells down. */
    val height: Int,
    /** The share of the board that is mines, in thousandths of a percent. */
    val perMille: Int,
    /** The label's string resource. */
    val label: Int,
) {
    /** The classic beginner board: 9 by 9 at 12.3%, which is ten mines. */
    Beginner(9, 9, 123, R.string.minesweeper_preset_beginner),

    /** The classic intermediate board: 16 by 16 at 15.6%, which is forty mines. */
    Intermediate(16, 16, 156, R.string.minesweeper_preset_intermediate),

    /** The classic expert board: 30 by 16 at 20.6%, which is ninety-nine mines. */
    Expert(30, 16, 206, R.string.minesweeper_preset_expert),
}

/** The name of each snap point, in the order the core's table lists them. */
val SnapLabels: List<Int> =
    listOf(
        R.string.minesweeper_snap_casual,
        R.string.minesweeper_snap_beginner,
        R.string.minesweeper_snap_standard,
        R.string.minesweeper_snap_intermediate,
        R.string.minesweeper_snap_hard,
        R.string.minesweeper_snap_expert_classic,
        R.string.minesweeper_snap_expert,
        R.string.minesweeper_snap_extreme,
    )
