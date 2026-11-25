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

package com.qiushui1012.app.minigames.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One finished game.
 *
 * A play is the fact a record group collects: who played, how long it took, when it finished
 * and how it started. The seed and the outcome are optional because a file written before
 * those fields existed holds plays without them, and a missing value is read as "not kept"
 * rather than as a default that would be a claim about the game.
 */
@Serializable
data class Play(
    /** The name the player played under, or empty when none was given. */
    val playerName: String,
    /** How long the game took, in whole seconds. */
    val seconds: Long,
    /** When it finished, as milliseconds since the Unix epoch. */
    val finishedAt: Long,
    /** The seed the puzzle was generated from, or null when it was not kept. */
    val seed: Long? = null,
    /** Whether the game was lost, or null for a play written before losses were kept. */
    val failed: Boolean? = null,
    /** Whether the seed was typed by the player rather than generated. */
    val typedSeed: Boolean = false,
    /** How many takes met a blocker, which only arrow cube keeps. */
    val mistakes: Int? = null,
)

/**
 * One game's finished plays, filed under the parameters they were played with.
 *
 * A group is the unit the records page shows: its parameters name the card, its best time is
 * what other groups are compared by, and its history is the plays themselves. The hierarchy
 * is typed rather than a common record with a free-form key because the parameters belong to
 * the game that has them, and a record group can then carry the best score of its own kind.
 *
 * The JSON discriminator is `type`, written by the serializer configured in
 * [RecordsSerializer]; each subtype names its own codec with [SerialName].
 */
@Serializable
sealed interface GameRecord {
    /** The best time in the history, in whole seconds, or null when nothing finished. */
    val bestSeconds: Long?

    /** Every play of this group, oldest first. */
    val history: List<Play>
}

/** The record group of a pipes board. */
@Serializable
@SerialName("pipes")
data class PipesRecord(
    /** The number of cells on a side. */
    val size: Int,
    /** Whether the opposite edges were joined. */
    val wrapped: Boolean,
    override val bestSeconds: Long? = null,
    override val history: List<Play> = emptyList(),
) : GameRecord

/** The record group of a sudoku difficulty. */
@Serializable
@SerialName("sudoku")
data class SudokuRecord(
    /** The difficulty, by the core's own name. */
    val difficulty: String,
    override val bestSeconds: Long? = null,
    override val history: List<Play> = emptyList(),
) : GameRecord

/** The record group of a minesweeper board. */
@Serializable
@SerialName("minesweeper")
data class MinesweeperRecord(
    /** The number of cells across. */
    val width: Int,
    /** The number of cells down. */
    val height: Int,
    /** How many mines the board holds. */
    val mines: Int,
    /** Whether the board was generated under the no-guess rules. */
    val noGuess: Boolean,
    override val bestSeconds: Long? = null,
    override val history: List<Play> = emptyList(),
) : GameRecord

/** The record group of a nonogram board. */
@Serializable
@SerialName("nonogram")
data class NonogramRecord(
    /** The number of cells across. */
    val width: Int,
    /** The number of cells down. */
    val height: Int,
    override val bestSeconds: Long? = null,
    override val history: List<Play> = emptyList(),
) : GameRecord

/** The record group of a hashi board. */
@Serializable
@SerialName("hashi")
data class HashiRecord(
    /** The number of points across. */
    val width: Int,
    /** The number of points down. */
    val height: Int,
    /** How many islands the board holds. */
    val islands: Int,
    override val bestSeconds: Long? = null,
    override val history: List<Play> = emptyList(),
) : GameRecord

/** The record group of an arrow cube box. */
@Serializable
@SerialName("arrow_cube")
data class ArrowCubeRecord(
    /** The number of cells along x. */
    val width: Int,
    /** The number of cells along y. */
    val depth: Int,
    /** The number of cells along z. */
    val height: Int,
    override val bestSeconds: Long? = null,
    /** The mistakes of the play that holds [bestSeconds], or null when no play finished. */
    val bestMistakes: Int? = null,
    override val history: List<Play> = emptyList(),
) : GameRecord

/** Which game a record group belongs to. */
val GameRecord.kind: GameKind
    get() =
        when (this) {
            is PipesRecord -> GameKind.Pipes
            is SudokuRecord -> GameKind.Sudoku
            is MinesweeperRecord -> GameKind.Minesweeper
            is NonogramRecord -> GameKind.Nonogram
            is HashiRecord -> GameKind.Hashi
            is ArrowCubeRecord -> GameKind.ArrowCube
        }

/**
 * Returns the identity of a record group.
 *
 * The key is built from the record's type and its parameters only, so two groups that describe
 * the same game are merged even though their histories differ. The separator is a character no
 * stored parameter can hold, which keeps `1:23` from colliding with `12:3`.
 */
fun GameRecord.groupKey(): String =
    when (this) {
        is PipesRecord -> "pipes|$size|$wrapped"
        is SudokuRecord -> "sudoku|$difficulty"
        is MinesweeperRecord -> "minesweeper|$width|$height|$mines|$noGuess"
        is NonogramRecord -> "nonogram|$width|$height"
        is HashiRecord -> "hashi|$width|$height|$islands"
        is ArrowCubeRecord -> "arrow_cube|$width|$depth|$height"
    }

/**
 * Adds one play to a group and returns the group with its best score recomputed.
 *
 * The history is kept in finish order, which is the order a screen reads it in and the order a
 * tie in finish time resolves against. A group that is updated without recomputing its best
 * would show a time that its own history contradicts, so the two updates are one function.
 */
fun GameRecord.append(play: Play): GameRecord {
    val updated = (history + play).sortedBy { it.finishedAt }
    return when (this) {
        is PipesRecord -> copy(history = updated)
        is SudokuRecord -> copy(history = updated)
        is MinesweeperRecord -> copy(history = updated)
        is NonogramRecord -> copy(history = updated)
        is HashiRecord -> copy(history = updated)
        is ArrowCubeRecord -> copy(history = updated)
    }.recomputeBest()
}

/**
 * Replaces the history and returns the group with its best score recomputed.
 *
 * Deleting a play needs to remove one entry without appending another, which is the one other
 * way a history changes; the best score follows the history in both directions.
 */
fun GameRecord.withHistory(history: List<Play>): GameRecord {
    val updated = history.sortedBy { it.finishedAt }
    return when (this) {
        is PipesRecord -> copy(history = updated)
        is SudokuRecord -> copy(history = updated)
        is MinesweeperRecord -> copy(history = updated)
        is NonogramRecord -> copy(history = updated)
        is HashiRecord -> copy(history = updated)
        is ArrowCubeRecord -> copy(history = updated)
    }.recomputeBest()
}

/**
 * Recomputes the best score from the history.
 *
 * A lost game has a time, but it is the time until the mine was opened; counting it would make
 * losing the way to a best time. The tie between equal times is broken by mistakes, which is
 * the only other number a play carries.
 */
fun GameRecord.recomputeBest(): GameRecord {
    val best = bestPlay(history)
    return when (this) {
        is PipesRecord -> copy(bestSeconds = best?.seconds)
        is SudokuRecord -> copy(bestSeconds = best?.seconds)
        is MinesweeperRecord -> copy(bestSeconds = best?.seconds)
        is NonogramRecord -> copy(bestSeconds = best?.seconds)
        is HashiRecord -> copy(bestSeconds = best?.seconds)
        is ArrowCubeRecord -> copy(bestSeconds = best?.seconds, bestMistakes = best?.mistakes)
    }
}

/** Returns the play a group's best score comes from, or null when no play counts. */
private fun bestPlay(history: List<Play>): Play? =
    history
        .filter { it.failed != true }
        .minWithOrNull(compareBy<Play> { it.seconds }.thenBy { it.mistakes ?: 0 })

/** Identifies one play inside its group, which is what a selection holds. */
data class PlayRef(
    /** The identity of the group the play belongs to. */
    val groupKey: String,
    /** When the play finished. */
    val finishedAt: Long,
    /** How long the play took. */
    val seconds: Long,
)

/** Returns the identity of a play in a group. */
fun Play.ref(groupKey: String): PlayRef = PlayRef(groupKey, finishedAt, seconds)

/** The order a group's parameters are compared in, game by game. */
private val recordComparator: Comparator<GameRecord> =
    compareBy<GameRecord> { kindOrder(it.kind) }
        .thenBy { if (it is PipesRecord) it.size else 0 }
        .thenBy { if (it is PipesRecord && it.wrapped) 1 else 0 }
        .thenBy { if (it is SudokuRecord) it.difficulty else "" }
        .thenBy { if (it is MinesweeperRecord) it.width else 0 }
        .thenBy { if (it is MinesweeperRecord) it.height else 0 }
        .thenBy { if (it is MinesweeperRecord) it.mines else 0 }
        .thenBy { if (it is MinesweeperRecord && it.noGuess) 1 else 0 }
        .thenBy { if (it is NonogramRecord) it.width else 0 }
        .thenBy { if (it is NonogramRecord) it.height else 0 }
        .thenBy { if (it is HashiRecord) it.width else 0 }
        .thenBy { if (it is HashiRecord) it.height else 0 }
        .thenBy { if (it is HashiRecord) it.islands else 0 }
        .thenBy { if (it is ArrowCubeRecord) it.width else 0 }
        .thenBy { if (it is ArrowCubeRecord) it.depth else 0 }
        .thenBy { if (it is ArrowCubeRecord) it.height else 0 }

/** Returns the groups in the order the records page shows them. */
fun List<GameRecord>.sortedRecords(): List<GameRecord> = sortedWith(recordComparator)

/** Returns where a game's groups stand in the records page. */
private fun kindOrder(kind: GameKind): Int =
    when (kind) {
        GameKind.Pipes -> 0
        GameKind.Sudoku -> 1
        GameKind.Minesweeper -> 2
        GameKind.Nonogram -> 3
        GameKind.Hashi -> 4
        GameKind.ArrowCube -> 5
    }

/**
 * A saved game that has not finished.
 *
 * The snapshot is the bytes the core writes, which the application stores without
 * interpreting: the format belongs to the rules, and a field read here would be a second
 * copy of it that drifts.
 */
@Serializable
data class SavedGame(
    /** The game the snapshot belongs to. */
    val kind: GameKind,
    /** The bytes the core wrote. */
    val snapshot: ByteArray,
    /** The name the player is playing under. */
    val playerName: String,
    /** How long the game has taken so far, in whole seconds. */
    val seconds: Long,
    /** When the game was last written, as milliseconds since the Unix epoch. */
    val savedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is SavedGame) {
            return false
        }
        return kind == other.kind &&
            snapshot.contentEquals(other.snapshot) &&
            playerName == other.playerName &&
            seconds == other.seconds &&
            savedAt == other.savedAt
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + snapshot.contentHashCode()
        result = 31 * result + playerName.hashCode()
        result = 31 * result + seconds.hashCode()
        result = 31 * result + savedAt.hashCode()
        return result
    }
}

/** Which game a saved game belongs to. */
enum class GameKind {
    /** Pipes. */
    Pipes,

    /** Sudoku. */
    Sudoku,

    /** Minesweeper. */
    Minesweeper,

    /** Nonogram. */
    Nonogram,

    /** Hashi. */
    Hashi,

    /** Arrow cube. */
    ArrowCube,
}
