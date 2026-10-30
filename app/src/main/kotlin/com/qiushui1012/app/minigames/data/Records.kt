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

import kotlinx.serialization.Serializable

/**
 * Which game a record belongs to.
 *
 * A sealed hierarchy rather than a free string, because the records screen groups by the
 * parameters a game is played with, and those parameters belong to the game that has them:
 * a pipes game has no difficulty, and a sudoku game has no size.
 */
@Serializable
sealed interface RecordKey {
    /** A pipes game, identified by the board it was played on. */
    @Serializable
    data class Pipes(
        /** The number of cells on a side. */
        val size: Int,
        /** Whether the opposite edges were joined. */
        val wrapped: Boolean,
    ) : RecordKey

    /** A sudoku game, identified by its difficulty. */
    @Serializable
    data class Sudoku(
        /** The difficulty, by the core's own name. */
        val difficulty: String,
    ) : RecordKey
}

/** One finished game. */
@Serializable
data class Record(
    /** The game it was, with the parameters it was played with. */
    val key: RecordKey,
    /** The name the player played under, or empty when none was given. */
    val playerName: String,
    /** How long the game took, in whole seconds. */
    val seconds: Long,
    /** When it finished, as milliseconds since the Unix epoch. */
    val finishedAt: Long,
    /**
     * The seed the puzzle was generated from, or null for a record written before seeds were
     * kept.
     *
     * The seed is what makes a finished game reproducible, and it is deliberately not part of
     * [RecordKey]: two games on the same board are the same group, and a seed per record would
     * split that group into one group per puzzle.
     */
    val seed: Long? = null,
) {
    /**
     * Returns a stable identifier for this record.
     *
     * The repository needs one so that a selection survives a list that is rebuilt: two
     * records with the same key and the same finish time are the same game, and a game
     * cannot finish twice at the same millisecond.
     */
    fun id(): String = "${key.hashCode()}-$finishedAt-$seconds"
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

/** Which of the two games a saved game belongs to. */
enum class GameKind {
    /** Pipes. */
    Pipes,
    /** Sudoku. */
    Sudoku,
}
