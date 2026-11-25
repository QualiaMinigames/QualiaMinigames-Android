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

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
 * game clears its save and leaves the other game's, that merging a play recomputes the best
 * score, that a deletion removes only what was selected.
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

    /** Returns a group with one finished pipes play. */
    private fun pipes(
        seconds: Long,
        finishedAt: Long = 1_000,
        name: String = "Tester",
    ): GameRecord =
        PipesRecord(9, wrapped = false).append(
            Play(playerName = name, seconds = seconds, finishedAt = finishedAt),
        )

    /** Returns a group with one finished sudoku play. */
    private fun sudoku(
        seconds: Long,
        finishedAt: Long = 1_000,
    ): GameRecord =
        SudokuRecord("hard").append(
            Play(playerName = "Tester", seconds = seconds, finishedAt = finishedAt),
        )

    @Test
    fun an_empty_store_holds_nothing() = runBlocking {
        val store = newStore()
        assertEquals(emptyList<GameRecord>(), store.records.first())
        assertEquals(emptyList<SavedGame>(), store.saves.first())
    }

    @Test
    fun a_play_survives_a_write_and_a_read() = runBlocking {
        val store = newStore()
        store.addPlay(pipes(90))
        val groups = store.records.first()
        assertEquals(1, groups.size)
        val play = groups.single().history.single()
        assertEquals(90, play.seconds)
        assertEquals("Tester", play.playerName)
    }

    @Test
    fun plays_of_one_board_join_one_group_and_its_best_is_recomputed() = runBlocking {
        val store = newStore()
        store.addPlay(pipes(120))
        store.addPlay(pipes(75, finishedAt = 2_000))
        store.addPlay(pipes(200, finishedAt = 3_000))
        val group = store.records.first().single()
        assertEquals(3, group.history.size)
        assertEquals(75L, group.bestSeconds)
        // The history is kept in finish order, which is what the page reads it in.
        assertEquals(listOf(120L, 75L, 200L), group.history.map { it.seconds })
    }

    @Test
    fun a_best_time_is_per_parameters() = runBlocking {
        val store = newStore()
        store.addPlay(pipes(120))
        store.addPlay(
            PipesRecord(9, wrapped = true).append(
                Play("Tester", 30, 2_000),
            ),
        )
        val groups = store.records.first()
        assertEquals(2, groups.size)
        assertEquals(120L, groups.first { it is PipesRecord && !it.wrapped }.bestSeconds)
        assertEquals(30L, groups.first { it is PipesRecord && it.wrapped }.bestSeconds)
    }

    @Test
    fun finishing_a_game_clears_its_save_and_keeps_the_other_games() = runBlocking {
        val store = newStore()
        store.putSave(SavedGame(GameKind.Pipes, byteArrayOf(1), "Tester", 5, 10))
        store.putSave(SavedGame(GameKind.Sudoku, byteArrayOf(2), "Tester", 6, 11))
        store.addPlay(pipes(60))
        assertNull(store.save(GameKind.Pipes).first())
        assertNotNull(store.save(GameKind.Sudoku).first())
        assertEquals(1, store.records.first().size)
    }

    @Test
    fun deleting_a_play_keeps_the_rest_and_recomputes_the_best() = runBlocking {
        val store = newStore()
        store.addPlay(pipes(60, finishedAt = 1))
        store.addPlay(pipes(90, finishedAt = 2))
        val group = store.records.first().single()
        val fastest = group.history.first()
        store.delete(setOf(fastest.ref(group.groupKey())))
        val remaining = store.records.first().single()
        assertEquals(1, remaining.history.size)
        assertEquals(90L, remaining.bestSeconds)
    }

    @Test
    fun deleting_every_play_of_a_group_removes_the_group() = runBlocking {
        val store = newStore()
        store.addPlay(pipes(60))
        store.addPlay(sudoku(30))
        val group = store.records.first().first { it.kind == GameKind.Pipes }
        store.delete(setOf(group.history.single().ref(group.groupKey())))
        assertEquals(listOf(GameKind.Sudoku), store.records.first().map { it.kind })
    }

    @Test
    fun clearing_the_records_keeps_the_unfinished_games() = runBlocking {
        val store = newStore()
        store.addPlay(pipes(60))
        store.putSave(SavedGame(GameKind.Sudoku, byteArrayOf(1), "Tester", 5, 10))
        store.deleteAllRecords()
        assertEquals(emptyList<GameRecord>(), store.records.first())
        assertEquals(1, store.saves.first().size)
    }

    @Test
    fun the_serializer_round_trips_a_file() {
        val file =
            RecordsFile(
                groups = listOf(pipes(60), sudoku(30)),
                saves = listOf(SavedGame(GameKind.Pipes, byteArrayOf(1, 2, 3), "Tester", 5, 10)),
            )
        val bytes =
            java.io.ByteArrayOutputStream().also { output ->
                runBlocking { RecordsSerializer.writeTo(file, output) }
            }.toByteArray()
        val read = runBlocking { RecordsSerializer.readFrom(bytes.inputStream()) }
        assertEquals(file.groups, read.groups)
        assertEquals(1, read.saves.size)
        assertTrue(read.saves.single().snapshot.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun the_file_is_written_with_a_group_array_and_a_type_discriminator() {
        val file = RecordsFile(groups = listOf(pipes(60)))
        val bytes =
            java.io.ByteArrayOutputStream().also { output ->
                runBlocking { RecordsSerializer.writeTo(file, output) }
            }.toByteArray()
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        assertTrue("the new format has a groups array", "groups" in root)
        assertTrue("the old format's records array is gone", "records" !in root)
        val group = root.getValue("groups").let { it as kotlinx.serialization.json.JsonArray }.first().jsonObject
        assertEquals("pipes", group.getValue("type").let { it as kotlinx.serialization.json.JsonPrimitive }.content)
    }

    @Test
    fun an_old_file_is_migrated_into_groups() {
        val old =
            """
            {"records":[
              {"key":{"type":"com.qiushui1012.app.minigames.data.RecordKey.Pipes","size":9,"wrapped":false},
               "playerName":"Tester","seconds":90,"finishedAt":1000,"seed":7,"failed":null,"typedSeed":false},
              {"key":{"type":"com.qiushui1012.app.minigames.data.RecordKey.Pipes","size":9,"wrapped":false},
               "playerName":"Tester","seconds":60,"finishedAt":2000,"seed":8,"failed":false,"typedSeed":true},
              {"key":{"type":"com.qiushui1012.app.minigames.data.RecordKey.Sudoku","difficulty":"hard"},
               "playerName":"Tester","seconds":30,"finishedAt":3000,"seed":9,"failed":null,"typedSeed":false}
            ],"saves":[]}
            """.trimIndent()
        val file = runBlocking { RecordsSerializer.readFrom(old.byteInputStream()) }
        assertEquals(2, file.groups.size)
        val pipes = file.groups.first { it.kind == GameKind.Pipes }
        assertEquals(2, pipes.history.size)
        assertEquals(60L, pipes.bestSeconds)
        assertEquals(7L, pipes.history.first().seed)
        assertTrue(pipes.history.any { it.typedSeed })
        val sudoku = file.groups.first { it.kind == GameKind.Sudoku }
        assertEquals(30L, sudoku.bestSeconds)
    }

    @Test
    fun a_migrated_file_is_written_back_in_the_new_shape() {
        val old =
            """
            {"records":[
              {"key":{"type":"com.qiushui1012.app.minigames.data.RecordKey.Sudoku","difficulty":"easy"},
               "playerName":"Tester","seconds":30,"finishedAt":1000},
              {"key":{"type":"com.qiushui1012.app.minigames.data.RecordKey.Sudoku","difficulty":"easy"},
               "playerName":"Tester","seconds":20,"finishedAt":2000}
            ],"saves":[]}
            """.trimIndent()
        val migrated = runBlocking { RecordsSerializer.readFrom(old.byteInputStream()) }
        assertEquals(1, migrated.groups.size)
        assertEquals(20L, migrated.groups.single().bestSeconds)
        val bytes =
            java.io.ByteArrayOutputStream().also { output ->
                runBlocking { RecordsSerializer.writeTo(migrated, output) }
            }.toByteArray()
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        assertTrue("groups" in root)
        assertTrue("records" !in root)
    }

    @Test
    fun a_legacy_key_of_an_unknown_game_is_reported() {
        val old =
            """
            {"records":[
              {"key":{"type":"com.qiushui1012.app.minigames.data.RecordKey.Chess"},
               "playerName":"Tester","seconds":30,"finishedAt":1000}
            ],"saves":[]}
            """.trimIndent()
        val failure =
            runCatching {
                runBlocking { RecordsSerializer.readFrom(old.byteInputStream()) }
            }.exceptionOrNull()
        assertTrue("a file naming an unknown game is corruption", failure is CorruptionException)
    }

    @Test
    fun a_stored_best_score_is_recomputed_from_the_history() {
        // A group can be edited between runs, and the best score is derivable from its plays:
        // a stored value that disagrees with them is corrected rather than shown.
        val json =
            """
            {"groups":[
              {"type":"pipes","size":9,"wrapped":false,"bestSeconds":1,
               "history":[{"playerName":"Tester","seconds":90,"finishedAt":1000}]},
              {"type":"arrow_cube","width":10,"depth":10,"height":10,"bestSeconds":1,"bestMistakes":9,
               "history":[{"playerName":"Tester","seconds":40,"finishedAt":1000,"mistakes":2}]}
            ],"saves":[]}
            """.trimIndent()
        val file = runBlocking { RecordsSerializer.readFrom(json.byteInputStream()) }
        assertEquals(90L, file.groups.first { it.kind == GameKind.Pipes }.bestSeconds)
        val cube = file.groups.first { it.kind == GameKind.ArrowCube } as ArrowCubeRecord
        assertEquals(40L, cube.bestSeconds)
        assertEquals(2, cube.bestMistakes)
    }

    @Test(expected = CorruptionException::class)
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
