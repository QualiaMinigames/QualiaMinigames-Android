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

import androidx.datastore.core.DataStore
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The records store's logic.
 *
 * The store is exercised through an in-memory [DataStore] rather than a real file. A file
 * would test the library's write path, which is DataStore's own and is already covered where
 * it lives; what this file has to prove is that the operations compose — that finishing a
 * game clears its save and leaves the other game's, that a best time is per parameters, that
 * a deletion removes only what was selected.
 */
private class MemoryRecordsStore : DataStore<RecordsFile> {
    private val state = MutableStateFlow(RecordsFile())

    override val data = state

    override suspend fun updateData(transform: suspend (t: RecordsFile) -> RecordsFile): RecordsFile {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** The records store, against an in-memory DataStore. */
class RecordsStoreTest {
    /** Returns a store over an empty in-memory DataStore. */
    private fun newStore(): RecordsStore = RecordsStore(MemoryRecordsStore())

    /** Returns a finished pipes game. */
    private fun pipes(seconds: Long, name: String = "Tester", finishedAt: Long = 1_000) =
        Record(RecordKey.Pipes(9, wrapped = false), name, seconds, finishedAt)

    @Test
    fun an_empty_store_holds_nothing() = runBlocking {
        val store = newStore()
        assertEquals(emptyList<Record>(), store.records.first())
        assertEquals(emptyList<SavedGame>(), store.saves.first())
    }

    @Test
    fun a_record_survives_a_write_and_a_read() = runBlocking {
        val store = newStore()
        store.addRecord(pipes(90))
        val records = store.records.first()
        assertEquals(1, records.size)
        assertEquals(90, records.single().seconds)
        assertEquals("Tester", records.single().playerName)
    }

    @Test
    fun the_best_time_is_the_lowest_one_of_a_key() = runBlocking {
        val store = newStore()
        store.addRecord(pipes(120))
        store.addRecord(pipes(75))
        store.addRecord(pipes(200))
        assertEquals(75L, store.best(RecordKey.Pipes(9, wrapped = false)).first())
    }

    @Test
    fun a_best_time_is_per_parameters() = runBlocking {
        val store = newStore()
        store.addRecord(pipes(120))
        store.addRecord(Record(RecordKey.Pipes(9, wrapped = true), "Tester", 30, 2_000))
        assertEquals(120L, store.best(RecordKey.Pipes(9, wrapped = false)).first())
        assertEquals(30L, store.best(RecordKey.Pipes(9, wrapped = true)).first())
    }

    @Test
    fun a_key_without_records_has_no_best_time() = runBlocking {
        val store = newStore()
        assertNull(store.best(RecordKey.Sudoku("hard")).first())
    }

    @Test
    fun a_saved_game_replaces_the_previous_one_of_the_same_game() = runBlocking {
        val store = newStore()
        store.putSave(SavedGame(GameKind.Pipes, byteArrayOf(1, 2, 3), "Tester", 5, 10))
        store.putSave(SavedGame(GameKind.Sudoku, byteArrayOf(4), "Tester", 6, 11))
        store.putSave(SavedGame(GameKind.Pipes, byteArrayOf(9), "Tester", 7, 12))
        val saves = store.saves.first()
        assertEquals(2, saves.size)
        assertEquals(1, saves.count { it.kind == GameKind.Pipes })
        assertTrue(saves.first { it.kind == GameKind.Pipes }.snapshot.contentEquals(byteArrayOf(9)))
    }

    @Test
    fun finishing_a_game_clears_its_save_and_keeps_the_other_games() = runBlocking {
        val store = newStore()
        store.putSave(SavedGame(GameKind.Pipes, byteArrayOf(1), "Tester", 5, 10))
        store.putSave(SavedGame(GameKind.Sudoku, byteArrayOf(2), "Tester", 6, 11))
        store.addRecord(pipes(60))
        assertNull(store.save(GameKind.Pipes).first())
        assertNotNull(store.save(GameKind.Sudoku).first())
        assertEquals(1, store.records.first().size)
    }

    @Test
    fun deleting_records_keeps_the_ones_that_were_not_selected() = runBlocking {
        val store = newStore()
        val first = pipes(60, finishedAt = 1)
        val second = pipes(70, finishedAt = 2)
        store.addRecord(first)
        store.addRecord(second)
        store.delete(setOf(first.id()))
        assertEquals(listOf(second), store.records.first())
    }

    @Test
    fun clearing_the_records_keeps_the_unfinished_games() = runBlocking {
        val store = newStore()
        store.addRecord(pipes(60))
        store.putSave(SavedGame(GameKind.Sudoku, byteArrayOf(1), "Tester", 5, 10))
        store.deleteAllRecords()
        assertEquals(emptyList<Record>(), store.records.first())
        assertEquals(1, store.saves.first().size)
    }

    @Test
    fun a_record_identifier_is_stable_and_distinct() {
        val first = pipes(60, finishedAt = 1_000)
        val same = pipes(60, finishedAt = 1_000)
        val later = pipes(60, finishedAt = 2_000)
        assertEquals(first.id(), same.id())
        assertTrue(first.id() != later.id())
    }

    @Test
    fun the_serializer_round_trips_a_file() {
        val file =
            RecordsFile(
                records = listOf(pipes(60), Record(RecordKey.Sudoku("hard"), "Tester", 30, 5)),
                saves = listOf(SavedGame(GameKind.Pipes, byteArrayOf(1, 2, 3), "Tester", 5, 10)),
            )
        val bytes =
            java.io.ByteArrayOutputStream().also { output ->
                runBlocking { RecordsSerializer.writeTo(file, output) }
            }.toByteArray()
        val read = runBlocking { RecordsSerializer.readFrom(bytes.inputStream()) }
        assertEquals(file.records, read.records)
        assertEquals(1, read.saves.size)
        assertTrue(read.saves.single().snapshot.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test(expected = androidx.datastore.core.CorruptionException::class)
    fun a_file_that_is_not_json_is_reported() {
        runBlocking { RecordsSerializer.readFrom("{ not json".byteInputStream()) }
    }

    @Test(expected = IOException::class)
    fun the_serializer_reports_a_stream_it_cannot_write_to() {
        val failing =
            object : java.io.OutputStream() {
                override fun write(b: Int) {
                    throw IOException("this stream refuses")
                }
            }
        runBlocking { RecordsSerializer.writeTo(RecordsFile(), failing) }
    }
}
