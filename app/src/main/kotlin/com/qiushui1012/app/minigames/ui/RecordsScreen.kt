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

package com.qiushui1012.app.minigames.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.Record
import com.qiushui1012.app.minigames.data.RecordKey
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.difficultyFromStorageName
import com.qiushui1012.app.minigames.export.ImageExport
import com.qiushui1012.app.minigames.export.RecordImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How a finish time is written in the list. */
private val listStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * Every finished game, grouped by the parameters it was played with.
 *
 * # The order of a group
 *
 * The best time comes first and the games follow, newest first. The groups themselves are
 * ordered the same way every time, so a group does not move when a game is added to it.
 *
 * # Selecting and exporting
 *
 * A selection mode exists because the two exports are different pictures. One record is a
 * card, several are a column, and the player chooses which by how many are selected. The
 * column's height follows the number of records: it is never cut, paged or scaled to fit
 * anything.
 */
@Composable
fun RecordsScreen(
    container: AppContainer,
    settings: Settings,
    bottomInset: Dp,
) {
    val records by container.records.records.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportedAtLabel = stringResource(R.string.records_exported_at, currentStamp())
    val unknownPlayer = stringResource(R.string.records_unknown_player)
    // The label is the one the list uses, in the language the interface is in. A record's key
    // carries a storage spelling (`easy`, `hard`), and an exported card that said `easy` beside
    // a screen that said `简单` would be a picture of a different application. The pairs are
    // resolved here, inside the composition, because the drawing runs outside it.
    val labels: Map<RecordKey, String> = records.associate { record -> record.key to keyLabel(record.key) }
    val label: (RecordKey) -> String = { key -> labels[key] ?: key.toString() }
    val headers =
        listOf(
            stringResource(R.string.records_column_player),
            stringResource(R.string.records_column_game),
            stringResource(R.string.records_column_time),
            stringResource(R.string.records_column_finished),
        )

    fun export(selection: List<Record>) {
        if (selection.isEmpty()) {
            return
        }
        scope.launch {
            val saved =
                withContext(Dispatchers.Default) {
                    val bitmap =
                        if (selection.size == 1) {
                            RecordImage.single(selection.first(), label, unknownPlayer, exportedAtLabel)
                        } else {
                            RecordImage.several(selection, label, unknownPlayer, exportedAtLabel, headers)
                        }
                    ImageExport.save(context, bitmap, "qualia-minigames-${System.currentTimeMillis()}.png")
                }
            if (saved == null) {
                message = context.getString(R.string.records_export_failed)
            } else {
                message = context.getString(R.string.records_export_done, saved.displayPath)
                shareTarget = saved.uri
                selecting = false
                selected = emptySet()
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        ScreenHeader(
            title = stringResource(R.string.records_title),
            actions = {
                if (records.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selecting = !selecting
                            selected = emptySet()
                        },
                    ) {
                        Text(
                            stringResource(
                                if (selecting) R.string.records_clear_selection else R.string.records_select,
                            ),
                        )
                    }
                }
            },
        )

        if (records.isEmpty()) {
            Text(stringResource(R.string.records_empty), style = MaterialTheme.typography.bodyMedium)
        } else if (selecting) {
            SelectionBar(
                count = selected.size,
                onSelectAll = {
                    selected =
                        if (selected.size == records.size) {
                            emptySet()
                        } else {
                            records.map { it.id() }.toSet()
                        }
                },
                onExport = { export(records.filter { it.id() in selected }) },
                onDelete = {
                    scope.launch {
                        container.records.delete(selected)
                        selected = emptySet()
                        selecting = false
                    }
                },
            )
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            shareTarget?.let { uri ->
                TextButton(onClick = { shareImage(context, uri) }) {
                    Text(stringResource(R.string.records_export_share))
                }
            }
        }

        if (records.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                TextButton(onClick = { confirmingClear = true }) {
                    Text(stringResource(R.string.records_delete_all))
                }
            }
        }

        for ((key, group) in groupRecords(records)) {
            RecordGroup(
                key = key,
                records = group,
                selecting = selecting,
                selected = selected,
                onToggle = { id ->
                    selected = if (id in selected) selected - id else selected + id
                },
            )
        }

        Box(modifier = Modifier.padding(bottom = bottomInset))
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.records_delete_all)) },
            text = { Text(stringResource(R.string.records_delete_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.records.deleteAllRecords() }
                        confirmingClear = false
                    },
                ) {
                    Text(stringResource(R.string.records_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

/** The bar that appears while records are selected. */
@Composable
private fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text("$count", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll) { Text(stringResource(R.string.records_select_all)) }
            TextButton(onClick = onExport, enabled = count > 0) { Text(stringResource(R.string.records_export)) }
            TextButton(onClick = onDelete, enabled = count > 0) { Text(stringResource(R.string.records_delete)) }
        }
    }
}

/** One group of records: its parameters, its best time, and the games themselves. */
@Composable
private fun RecordGroup(
    key: RecordKey,
    records: List<Record>,
    selecting: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val best = records.minByOrNull { it.seconds }
    SectionCard(title = keyLabel(key)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.records_best), style = MaterialTheme.typography.labelLarge)
            Text(formatDuration(best?.seconds ?: 0), style = MaterialTheme.typography.labelLarge)
        }
        Text(stringResource(R.string.records_history), style = MaterialTheme.typography.labelLarge)
        for (record in records.sortedByDescending { it.finishedAt }) {
            val id = record.id()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (selecting) {
                                Modifier.toggleable(value = id in selected, onValueChange = { onToggle(id) })
                            } else {
                                Modifier
                            },
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (selecting) {
                    Checkbox(checked = id in selected, onCheckedChange = { onToggle(id) })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.playerName.ifBlank { stringResource(R.string.records_unknown_player) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        listStamp.format(Instant.ofEpochMilli(record.finishedAt).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatDuration(record.seconds), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Groups records by the parameters they were played with, in a stable order. */
fun groupRecords(records: List<Record>): List<Pair<RecordKey, List<Record>>> =
    records
        .groupBy { it.key }
        .toList()
        .sortedWith(
            compareBy(
                { (key, _) -> if (key is RecordKey.Pipes) 0 else 1 },
                { (key, _) -> if (key is RecordKey.Pipes) key.size else 0 },
                { (key, _) -> if (key is RecordKey.Pipes && key.wrapped) 1 else 0 },
                { (key, _) -> if (key is RecordKey.Sudoku) key.difficulty else "" },
            ),
        )

/** Returns the label of a record's parameters. */
@Composable
private fun keyLabel(key: RecordKey): String =
    when (key) {
        is RecordKey.Pipes ->
            stringResource(
                R.string.home_pipes_summary,
                key.size,
                stringResource(if (key.wrapped) R.string.pipes_layout_wrapped else R.string.pipes_layout_flat),
            )

        is RecordKey.Sudoku ->
            stringResource(R.string.home_sudoku_summary, difficultyLabel(difficultyFromStorageName(key.difficulty)))
    }

/** Returns a duration as `m:ss`, or `h:mm:ss` past an hour. */
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%d:%02d".format(minutes, remainder)
    }
}

/** Returns the current time in the same form the records list writes finish times in. */
private fun currentStamp(): String = listStamp.format(Instant.now().atZone(ZoneId.systemDefault()))

/** Hands an image to whatever the player has that reads images. */
private fun shareImage(context: android.content.Context, uri: android.net.Uri) {
    val intent =
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(android.content.Intent.createChooser(intent, null))
}
