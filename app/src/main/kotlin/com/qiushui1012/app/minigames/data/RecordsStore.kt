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

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Everything the application keeps between runs, apart from the settings.
 *
 * One file rather than two: a record group and the game that produced it are written at the
 * same moment, and two files that are written together can disagree after a crash in between.
 * The snapshot of an unfinished game is the largest field, so a record written on top of it
 * keeps the file small.
 */
@kotlinx.serialization.Serializable
data class RecordsFile(
    /** Every finished game's group, one per set of parameters. */
    val groups: List<GameRecord> = emptyList(),
    /** The unfinished games, at most one per game. */
    val saves: List<SavedGame> = emptyList(),
)

/** The JSON the records are stored as. */
private val json =
    Json {
        // A field this build does not know is kept rather than dropped, so that a file written
        // by a newer build survives a downgrade with its data intact.
        ignoreUnknownKeys = true
        encodeDefaults = true
        // The type of a record group is part of the format rather than a default that could
        // shift with the library: a reader looks for `type` and a writer states it.
        classDiscriminator = "type"
    }

/**
 * The records file as the previous model wrote it.
 *
 * It exists only to read a file this build no longer writes. The key is decoded as one flat
 * object rather than as a sealed hierarchy with the old class names, because the discriminator
 * holds a fully qualified class name that is not part of this build's interface: matching the
 * simple name lets both spellings read, and keeps the old type names out of the public model.
 */
@kotlinx.serialization.Serializable
private data class LegacyRecordsFile(
    /** The finished games, oldest first, one entry per play. */
    val records: List<LegacyRecord> = emptyList(),
    /** The unfinished games, which keep their shape. */
    val saves: List<SavedGame> = emptyList(),
)

/** One play as the previous model wrote it. */
@kotlinx.serialization.Serializable
private data class LegacyRecord(
    /** The game and its parameters. */
    val key: LegacyRecordKey,
    /** The name the player played under. */
    val playerName: String,
    /** How long the game took, in whole seconds. */
    val seconds: Long,
    /** When it finished, as milliseconds since the Unix epoch. */
    val finishedAt: Long,
    /** The seed, or null for a play older than the field. */
    val seed: Long? = null,
    /** Whether the game was lost, or null for a play older than the field. */
    val failed: Boolean? = null,
    /** Whether the seed was typed. */
    val typedSeed: Boolean = false,
)

/**
 * The parameters of an old record, with every game's fields in one object.
 *
 * All the fields are nullable because one object describes five different keys; the fields a
 * key does not use are absent from its JSON.
 */
@kotlinx.serialization.Serializable
private data class LegacyRecordKey(
    /** The old class name, which is the discriminator and the only field every key has. */
    val type: String,
    /** The side of a pipes board. */
    val size: Int? = null,
    /** Whether a pipes board was wrapped. */
    val wrapped: Boolean? = null,
    /** The difficulty of a sudoku. */
    val difficulty: String? = null,
    /** The width of a minesweeper, nonogram or hashi board. */
    val width: Int? = null,
    /** The height of a minesweeper, nonogram or hashi board. */
    val height: Int? = null,
    /** The mines of a minesweeper board. */
    val mines: Int? = null,
    /** Whether a minesweeper board used the no-guess rules. */
    val noGuess: Boolean? = null,
    /** The islands of a hashi board. */
    val islands: Int? = null,
)

/** Writes and reads [RecordsFile]. */
object RecordsSerializer : Serializer<RecordsFile> {
    override val defaultValue: RecordsFile = RecordsFile()

    override suspend fun readFrom(input: InputStream): RecordsFile {
        val text = input.readBytes().decodeToString()
        return try {
            val element = json.parseToJsonElement(text).jsonObject
            if ("groups" in element) {
                val file = json.decodeFromJsonElement(RecordsFile.serializer(), element)
                // The best score can be derived from the history, so a file whose stored value
                // disagrees with its own plays is corrected on the way in rather than shown.
                file.copy(groups = file.groups.map { it.recomputeBest() })
            } else {
                // A file with no `groups` is one the previous model wrote. It is migrated in
                // memory and written back in the new shape on the next write, which is what
                // keeps the old file readable until something replaces it.
                val legacy = json.decodeFromJsonElement(LegacyRecordsFile.serializer(), element)
                RecordsFile(groups = migrate(legacy.records), saves = legacy.saves)
            }
        } catch (failure: SerializationException) {
            throw CorruptionException("The records file is not valid JSON.", failure)
        } catch (failure: IllegalArgumentException) {
            throw CorruptionException("The records file is not valid JSON.", failure)
        }
    }

    override suspend fun writeTo(
        t: RecordsFile,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(RecordsFile.serializer(), t).encodeToByteArray())
    }
}

/**
 * Groups the plays of an old file into the new records.
 *
 * The old file held one entry per play, so the same board appeared once per game; the new
 * model holds one entry per board with its plays inside. Grouping by the new key and appending
 * each play is what merges them, and [GameRecord.append] recomputes the best score as it goes,
 * so a migrated group and a group written by this build are the same value.
 */
private fun migrate(records: List<LegacyRecord>): List<GameRecord> {
    val groups = LinkedHashMap<String, GameRecord>()
    for (record in records) {
        val group = record.key.toRecord()
        val play =
            Play(
                playerName = record.playerName,
                seconds = record.seconds,
                finishedAt = record.finishedAt,
                seed = record.seed,
                failed = record.failed,
                typedSeed = record.typedSeed,
            )
        val key = group.groupKey()
        groups[key] = (groups[key] ?: group).append(play)
    }
    return groups.values.toList()
}

/** Converts one old key into the typed record group of the game it names. */
private fun LegacyRecordKey.toRecord(): GameRecord {
    // The old discriminator is the class name, which the serialization library may spell with
    // dots or with the module's own separator before the nested class. Taking the last segment
    // after either keeps the reader independent of that spelling.
    val name =
        type
            .lowercase()
            .substringAfterLast('.')
            .substringAfterLast('$')
    return when (name) {
        "pipes" -> PipesRecord(size = size ?: missing("size"), wrapped = wrapped ?: false)
        "sudoku" -> SudokuRecord(difficulty = difficulty ?: missing("difficulty"))
        "minesweeper" ->
            MinesweeperRecord(
                width = width ?: missing("width"),
                height = height ?: missing("height"),
                mines = mines ?: missing("mines"),
                noGuess = noGuess ?: false,
            )

        "nonogram" -> NonogramRecord(width = width ?: missing("width"), height = height ?: missing("height"))
        "hashi" ->
            HashiRecord(
                width = width ?: missing("width"),
                height = height ?: missing("height"),
                islands = islands ?: missing("islands"),
            )

        else -> throw SerializationException("The records file names an unknown game: $type")
    }
}

/** Reports a field an old record is missing. */
private fun missing(field: String): Nothing =
    throw SerializationException("The records file is missing a record's $field")

/**
 * The records and the saved games, stored in a DataStore.
 *
 * A corrupt file is replaced rather than allowed to stop the application: the records are
 * the only thing in it, and an application that refuses to open because a record cannot be
 * parsed is worse off than one that starts with no records and says nothing about it. The
 * replacement is done by the DataStore, which also keeps the corrupt file aside.
 */
class RecordsStore(private val store: DataStore<RecordsFile>) {
    /** Every finished game's group, in the order the records page shows them. */
    val records: Flow<List<GameRecord>> = store.data.map { it.groups.sortedRecords() }

    /** The unfinished games. */
    val saves: Flow<List<SavedGame>> = store.data.map { it.saves }

    /** Returns the unfinished game of a kind, if there is one. */
    fun save(kind: GameKind): Flow<SavedGame?> = store.data.map { file -> file.saves.firstOrNull { it.kind == kind } }

    /**
     * Adds one play and clears the save of the game it belongs to.
     *
     * The incoming group is the one the screen built from the game's parameters, and its
     * history holds the one play that just finished. A group of the same parameters is
     * appended to rather than replaced, and its best score is recomputed from the merged
     * history; a group of new parameters starts on its own.
     */
    suspend fun addPlay(record: GameRecord) {
        val play = record.history.lastOrNull() ?: return
        store.updateData { file ->
            val existing = file.groups.firstOrNull { it.groupKey() == record.groupKey() }
            val groups =
                if (existing == null) {
                    file.groups + record.recomputeBest()
                } else {
                    file.groups.map { group ->
                        if (group.groupKey() == record.groupKey()) {
                            group.append(play)
                        } else {
                            group
                        }
                    }
                }
            file.copy(
                groups = groups,
                saves = file.saves.filterNot { it.kind == record.kind },
            )
        }
    }

    /** Replaces the saved game of a kind, adding it when there is none. */
    suspend fun putSave(save: SavedGame) {
        store.updateData { file ->
            val others = file.saves.filterNot { it.kind == save.kind }
            file.copy(saves = others + save)
        }
    }

    /** Drops the saved game of a kind. */
    suspend fun clearSave(kind: GameKind) {
        store.updateData { file -> file.copy(saves = file.saves.filterNot { it.kind == kind }) }
    }

    /**
     * Removes the selected plays.
     *
     * A group whose history empties is removed with its last play: a card that says no game
     * was played is a card that says nothing. A group that keeps plays recomputes its best
     * score, because the best play may be the one that was deleted.
     */
    suspend fun delete(refs: Set<PlayRef>) {
        store.updateData { file ->
            file.copy(
                groups =
                    file.groups.mapNotNull { group ->
                        val key = group.groupKey()
                        val remaining =
                            group.history.filterNot { play ->
                                PlayRef(key, play.finishedAt, play.seconds) in refs
                            }
                        when {
                            remaining.size == group.history.size -> group
                            remaining.isEmpty() -> null
                            else -> group.withHistory(remaining)
                        }
                    },
            )
        }
    }

    /**
     * Removes every record.
     *
     * The saved games are left alone: the button that clears the records says so, and a
     * game in progress is not a record yet.
     */
    suspend fun deleteAllRecords() {
        store.updateData { it.copy(groups = emptyList()) }
    }
}

/** The name the records file is stored under. */
private const val RECORDS_FILE = "records.json"

/** The scope the DataStore does its own writing in, which outlives any screen. */
private val recordsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

/** Returns the records store of this application. */
fun createRecordsStore(context: Context): RecordsStore {
    val store =
        DataStoreFactory.create(
            serializer = RecordsSerializer,
            // A file that cannot be read is replaced, so a corrupt one costs the records it
            // held rather than the application.
            corruptionHandler = ReplaceFileCorruptionHandler { RecordsFile() },
            scope = recordsScope,
            produceFile = { context.dataStoreFile(RECORDS_FILE) },
        )
    return RecordsStore(store)
}
