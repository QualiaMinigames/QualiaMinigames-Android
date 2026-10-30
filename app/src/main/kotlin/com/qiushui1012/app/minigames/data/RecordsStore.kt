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

/**
 * Everything the application keeps between runs, apart from the settings.
 *
 * One file rather than two: a record and the game that produced it are written at the same
 * moment, and two files that are written together can disagree after a crash in between.
 * The snapshot of an unfinished game is the largest field, so a record written on top of it
 * keeps the file small.
 */
@kotlinx.serialization.Serializable
data class RecordsFile(
    /** Every finished game, oldest first. */
    val records: List<Record> = emptyList(),
    /** The unfinished games, at most one per game. */
    val saves: List<SavedGame> = emptyList(),
)

/** The JSON the records are stored as. */
private val json = Json {
    // A field this build does not know is kept rather than dropped, so that a file written
    // by a newer build survives a downgrade with its data intact.
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Writes and reads [RecordsFile]. */
object RecordsSerializer : Serializer<RecordsFile> {
    override val defaultValue: RecordsFile = RecordsFile()

    override suspend fun readFrom(input: InputStream): RecordsFile =
        try {
            json.decodeFromString(RecordsFile.serializer(), input.readBytes().decodeToString())
        } catch (failure: SerializationException) {
            throw CorruptionException("The records file is not valid JSON.", failure)
        }

    override suspend fun writeTo(t: RecordsFile, output: OutputStream) {
        output.write(json.encodeToString(RecordsFile.serializer(), t).encodeToByteArray())
    }
}

/**
 * The records and the saved games, stored in a DataStore.
 *
 * A corrupt file is replaced rather than allowed to stop the application: the records are
 * the only thing in it, and an application that refuses to open because a record cannot be
 * parsed is worse off than one that starts with no records and says nothing about it. The
 * replacement is done by the DataStore, which also keeps the corrupt file aside.
 */
class RecordsStore(private val store: DataStore<RecordsFile>) {
    /** Every finished game, oldest first. */
    val records: Flow<List<Record>> = store.data.map { it.records }

    /** The unfinished games. */
    val saves: Flow<List<SavedGame>> = store.data.map { it.saves }

    /** Returns the unfinished game of a kind, if there is one. */
    fun save(kind: GameKind): Flow<SavedGame?> = store.data.map { file -> file.saves.firstOrNull { it.kind == kind } }

    /**
     * Returns the best time of a key, in whole seconds.
     *
     * Only finished games count: a lost game has a time, but it is the time until the mine was
     * opened, and comparing it with a cleared board would make losing the way to a best time.
     */
    fun best(key: RecordKey): Flow<Long?> =
        store.data.map { file -> file.records.filter { it.key == key && it.failed != true }.minOfOrNull { it.seconds } }

    /** Adds a finished game and clears the save of the game it belongs to. */
    suspend fun addRecord(record: Record) {
        val kind =
            when (record.key) {
                is RecordKey.Pipes -> GameKind.Pipes
                is RecordKey.Sudoku -> GameKind.Sudoku
                is RecordKey.Minesweeper -> GameKind.Minesweeper
            }
        store.updateData { file ->
            file.copy(
                records = file.records + record,
                saves = file.saves.filterNot { it.kind == kind },
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

    /** Removes the records with the given identifiers. */
    suspend fun delete(ids: Set<String>) {
        store.updateData { file -> file.copy(records = file.records.filterNot { it.id() in ids }) }
    }

    /**
     * Removes every record.
     *
     * The saved games are left alone: the button that clears the records says so, and a
     * game in progress is not a record yet.
     */
    suspend fun deleteAllRecords() {
        store.updateData { it.copy(records = emptyList()) }
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
