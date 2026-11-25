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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.DurationFont
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.ArrowCubeRecord
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.GameRecord
import com.qiushui1012.app.minigames.data.HashiRecord
import com.qiushui1012.app.minigames.data.MinesweeperRecord
import com.qiushui1012.app.minigames.data.NonogramRecord
import com.qiushui1012.app.minigames.data.PipesRecord
import com.qiushui1012.app.minigames.data.Play
import com.qiushui1012.app.minigames.data.PlayRef
import com.qiushui1012.app.minigames.data.SudokuRecord
import com.qiushui1012.app.minigames.data.groupKey
import com.qiushui1012.app.minigames.data.kind
import com.qiushui1012.app.minigames.data.ref
import com.qiushui1012.app.minigames.difficultyFromStorageName
import com.qiushui1012.app.minigames.export.ImageExport
import com.qiushui1012.app.minigames.export.RecordImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How a finish time is written in the list. */
private val listStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** The narrowest a record card may be drawn before the grid uses fewer columns. */
private val CardMinWidth = 340.dp

/**
 * Every finished game, grouped by the parameters it was played with.
 *
 * # The order of a group
 *
 * The best time comes first and the games follow, newest first. The groups themselves are
 * ordered the same way every time, so a group does not move when a game is added to it: the
 * store returns them already sorted, and this page draws them in that order.
 *
 * # Selecting and exporting
 *
 * A selection mode exists because the two exports are different pictures. One play is a card,
 * several are a table, and the player chooses which by how many are selected.
 *
 * # Where the actions live
 *
 * Clearing and selecting sit in the header, right-aligned, and the selection's own actions
 * appear beside them once it is on: the page has no second bar to scroll past, and the two
 * states are never both offering the same verb. Clearing asks first; the confirmation is a
 * dialog of [ShellDialog], so it covers the navigation like every other dialog.
 */
@Composable
fun RecordsScreen(
    container: AppContainer,
    bottomInset: Dp,
    onRequestDialog: (ShellDialog) -> Unit,
) {
    val records by container.records.records.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<PlayRef>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    var shareTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    // Expanded is the state a group starts in, so the set holds the collapsed ones rather than
    // the open ones: a new game's group should not appear open only after it has been seen.
    var collapsed by remember { mutableStateOf<Set<GameKind>>(emptySet()) }

    // A selection is about plays that exist. When one is deleted — by the confirmation this
    // page opens, or by anything else — the page drops it from the selection, and a selection
    // that empties itself leaves the selection mode: a bar over no plays is a bar that can
    // only be dismissed.
    val present = records.flatMap { group -> group.history.map { it.ref(group.groupKey()) } }.toSet()
    LaunchedEffect(present) {
        selected = selected intersect present
        if (selected.isEmpty()) {
            selecting = false
        }
    }

    val exportedAtLabel = stringResource(R.string.records_exported_at, currentStamp())
    val unknownPlayer = stringResource(R.string.records_unknown_player)
    // The label is the one the list uses, in the language the interface is in. A record holds
    // its parameters as numbers, and an exported card that said them in another language would
    // be a picture of a different application. The values are resolved here, inside the
    // composition, because the drawing runs outside it.
    val labels: Map<String, String> = records.associate { group -> group.groupKey() to group.keyLabel() }
    val label: (GameRecord) -> String = { group -> labels[group.groupKey()] ?: "" }
    val names: Map<String, String> = records.associate { group -> group.groupKey() to gameKindLabel(group.kind) }
    val name: (GameRecord) -> String = { group -> names[group.groupKey()] ?: "" }
    val headers =
        listOf(
            stringResource(R.string.records_column_player),
            stringResource(R.string.records_column_game),
            stringResource(R.string.records_column_time),
            stringResource(R.string.records_column_finished),
        )
    val exportTitle = stringResource(R.string.records_export_title)
    val failedLabel = stringResource(R.string.records_failed)
    val typedSeedLabel = stringResource(R.string.records_typed_seed)
    val mistakesLabel: (Int) -> String = { count -> context.getString(R.string.records_mistakes, count) }
    val durationTypeface =
        remember(context) {
            ResourcesCompat.getFont(context, R.font.cascadia_code) ?: android.graphics.Typeface.MONOSPACE
        }

    fun export(selection: List<Pair<GameRecord, Play>>) {
        if (selection.isEmpty()) {
            return
        }
        // The two marks a play can carry are resolved here, where the language is known, for
        // the same reason the parameters are: the drawing runs outside the composition.
        val failedLabel = failedLabel
        val typedLabel = typedSeedLabel
        scope.launch {
            val saved =
                withContext(Dispatchers.Default) {
                    val bitmap =
                        if (selection.size == 1) {
                            val (group, play) = selection.first()
                            RecordImage.single(
                                group = group,
                                play = play,
                                name = name,
                                params = label,
                                unknown = unknownPlayer,
                                exportedAt = exportedAtLabel,
                                durationTypeface = durationTypeface,
                                failed = failedLabel,
                                typed = typedLabel,
                                mistakes = mistakesLabel,
                            )
                        } else {
                            RecordImage.several(
                                entries = selection,
                                name = name,
                                params = label,
                                unknown = unknownPlayer,
                                exportedAt = exportedAtLabel,
                                title = exportTitle,
                                headers = headers,
                                durationTypeface = durationTypeface,
                                failed = failedLabel,
                                typed = typedLabel,
                                mistakes = mistakesLabel,
                            )
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // A card keeps at least [CardMinWidth], and the grid gives it more when the window has
        // it: three columns on a tablet, one on a phone held upright.
        val columns = (maxWidth / CardMinWidth).toInt().coerceIn(1, 3)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.l, vertical = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            RecordsHeader(
                title = stringResource(R.string.records_title),
                // Only a selection can need a line of its own: "clear" and "select" are two
                // short words, while a selection offers five controls.
                ownLine = selecting,
                actions = {
                    if (records.isNotEmpty()) {
                        if (selecting) {
                            SelectionActions(
                                count = selected.size,
                                onSelectAll = {
                                    selected =
                                        if (selected.size == present.size) {
                                            emptySet()
                                        } else {
                                            present
                                        }
                                },
                                onExport = {
                                    export(
                                        records.flatMap { group ->
                                            group.history
                                                .filter { it.ref(group.groupKey()) in selected }
                                                .map { group to it }
                                        },
                                    )
                                },
                                // Deleting asks first, exactly as clearing does: both destroy
                                // plays that no longer exist anywhere else, and a selection
                                // made with one tap should not be undone with another.
                                onDelete = { onRequestDialog(ShellDialog.DeleteRecords(selected)) },
                                onCancel = {
                                    selecting = false
                                    selected = emptySet()
                                },
                            )
                        } else {
                            TextButton(
                                onClick = { onRequestDialog(ShellDialog.ClearRecords) },
                            ) {
                                Text(stringResource(R.string.records_delete_all))
                            }
                            TextButton(
                                onClick = {
                                    selecting = true
                                    selected = emptySet()
                                },
                            ) {
                                Text(stringResource(R.string.records_select))
                            }
                        }
                    }
                },
            )

            if (records.isEmpty()) {
                Text(stringResource(R.string.records_empty), style = MaterialTheme.typography.bodyMedium)
            }

            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                shareTarget?.let { uri ->
                    TextButton(onClick = { shareImage(context, uri) }) {
                        Text(stringResource(R.string.records_export_share))
                    }
                }
            }

            for ((kind, groups) in recordsByKind(records)) {
                val expanded = kind !in collapsed
                KindHeader(
                    name = gameKindLabel(kind),
                    expanded = expanded,
                    onToggle = {
                        collapsed =
                            if (expanded) {
                                collapsed + kind
                            } else {
                                collapsed - kind
                            }
                    },
                )
                if (expanded) {
                    for (row in groups.chunked(columns)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                            for (group in row) {
                                Box(Modifier.weight(1f)) {
                                    RecordGroup(
                                        group = group,
                                        selecting = selecting,
                                        selected = selected,
                                        onToggle = { ref ->
                                            selected = if (ref in selected) selected - ref else selected + ref
                                        },
                                        onOpen = { play -> onRequestDialog(ShellDialog.RecordDetail(group, play)) },
                                    )
                                }
                            }
                            repeat(columns - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.padding(bottom = bottomInset))
        }
    }
}

/**
 * The header of the records page.
 *
 * The actions sit at the right end of the title's line, which is where the two of them belong
 * whatever the window is: the title is one short word, so "clear" and "select" fit beside it
 * even on the narrowest phone. A selection offers five controls at once, and those only fit
 * beside a heading on a wide window; when [ownLine] says they do not, they take a line of their
 * own — still right-aligned, so the corner they are in does not move.
 */
@Composable
private fun RecordsHeader(
    title: String,
    ownLine: Boolean,
    actions: @Composable () -> Unit,
) {
    if (ownLine) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                actions()
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

/**
 * The controls a selection offers, left to right: how many are selected, select all, export,
 * delete, and the way out.
 *
 * Export and delete are disabled while nothing is selected, which is the rule the selection bar
 * has always followed: there is nothing to export or delete yet.
 */
@Composable
private fun SelectionActions(
    count: Int,
    onSelectAll: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
            )
        }
        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.records_select_all)) }
        TextButton(onClick = onExport, enabled = count > 0) { Text(stringResource(R.string.records_export)) }
        TextButton(onClick = onDelete, enabled = count > 0) { Text(stringResource(R.string.records_delete)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.records_clear_selection)) }
    }
}

/**
 * The dropdown above a game's cards: its name, and whether its cards are open.
 *
 * One dropdown per game rather than one per parameter: a player looking for their sudoku times
 * should not have to fold away every pipes board first.
 */
@Composable
private fun KindHeader(
    name: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

/** One group of a game's plays: its parameters, its best time, and the plays themselves. */
@Composable
private fun RecordGroup(
    group: GameRecord,
    selecting: Boolean,
    selected: Set<PlayRef>,
    onToggle: (PlayRef) -> Unit,
    onOpen: (Play) -> Unit,
) {
    SectionCard(
        header = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                // The parameters are the card's title, and the best time stands beside them:
                // the two are what a player compares this card with the next one by.
                Text(
                    group.keyLabel(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.records_best),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        // A group that has only lost plays has no best time, and `0:00` would
                        // be a time nobody played: the dash says there is nothing to show.
                        group.bestSeconds?.let(::formatDuration) ?: stringResource(R.string.records_no_best),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = DurationFont,
                    )
                    val bestMistakes = group.bestMistakes()
                    if (bestMistakes != null) {
                        Text(
                            stringResource(R.string.records_mistakes, bestMistakes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) {
        Text(stringResource(R.string.records_history), style = MaterialTheme.typography.labelLarge)
        for (play in group.history.sortedByDescending { it.finishedAt }) {
            val ref = play.ref(group.groupKey())
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (selecting) {
                                Modifier.toggleable(value = ref in selected, onValueChange = { onToggle(ref) })
                            } else {
                                Modifier.clickableRow { onOpen(play) }
                            },
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (selecting) {
                    Checkbox(checked = ref in selected, onCheckedChange = { onToggle(ref) })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        play.playerName.ifBlank { stringResource(R.string.records_unknown_player) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    RecordMarks(play)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (play.failed == true) {
                            stringResource(R.string.records_failed)
                        } else {
                            formatDuration(play.seconds)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = DurationFont,
                        color =
                            if (play.failed == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    // Only arrow cube counts mistakes, and a play of another game has none: the
                    // line is drawn from the play rather than from the game's name.
                    play.mistakes?.let { mistakes ->
                        Text(
                            stringResource(R.string.records_mistakes, mistakes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Returns the mistakes of the play that holds a group's best time, for the game that counts them. */
private fun GameRecord.bestMistakes(): Int? =
    if (this is ArrowCubeRecord) bestMistakes else null

/**
 * The line under a play's player: when it finished, and how it was started.
 *
 * A hand-picked seed is a fact a list of times cannot show, so it is written here rather than
 * left to the detail dialog. The outcome is not repeated here: it is the time column beside
 * this line that says "failed", and a row that said it twice would say nothing twice.
 */
@Composable
private fun RecordMarks(play: Play) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(
            listStamp.format(Instant.ofEpochMilli(play.finishedAt).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (play.typedSeed) {
            Text(
                stringResource(R.string.records_typed_seed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Makes a row openable by a tap. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

/**
 * The dialog that shows one play in full.
 *
 * Everything the play holds is here, including the seed: the list is a summary, and the seed
 * is the one field that says which puzzle this was. A play with a seed offers to copy it,
 * because a seed is meant to be typed into the field of the next game and retyping eighteen
 * digits from a screenshot is not what the record is for.
 */
@Composable
fun RecordDetailDialog(
    group: GameRecord,
    play: Play,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    val copied = stringResource(R.string.records_seed_copied)
    var copiedShown by remember { mutableStateOf(false) }
    // The badge clears itself: a word that stayed on screen would be a statement about the
    // clipboard rather than about the tap that filled it.
    LaunchedEffect(copiedShown) {
        if (copiedShown) {
            delay(2_000)
            copiedShown = false
        }
    }
    ModalForm(
        title = stringResource(R.string.records_detail_title),
        onDismiss = onDismiss,
        dismissLabel = null,
        confirm = { ModalConfirm(stringResource(R.string.action_close), onClick = onDismiss) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            LabeledValue(stringResource(R.string.records_detail_game), gameKindLabel(group.kind))
            LabeledValue(stringResource(R.string.records_detail_params), group.keyLabel())
            LabeledValue(
                stringResource(R.string.records_column_player),
                play.playerName.ifBlank { stringResource(R.string.records_unknown_player) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stringResource(R.string.records_column_time), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(
                        if (play.failed == true) {
                            stringResource(R.string.records_failed)
                        } else if (group.kind == GameKind.ArrowCube) {
                            stringResource(R.string.arrow_cube_won)
                        } else {
                            stringResource(R.string.minesweeper_won)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (play.failed == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Text(
                        formatDuration(play.seconds),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = DurationFont,
                    )
                    play.mistakes?.let { mistakes ->
                        Text(
                            stringResource(R.string.records_mistakes, mistakes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            LabeledValue(
                stringResource(R.string.records_column_finished),
                listStamp.format(Instant.ofEpochMilli(play.finishedAt).atZone(ZoneId.systemDefault())),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stringResource(R.string.records_detail_seed), style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Text(
                        // A play written before seeds were kept has none, and inventing one
                        // would be a number that does not generate this puzzle.
                        play.seed?.toString() ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = DurationFont,
                        modifier = Modifier.weight(1f),
                    )
                    if (copiedShown) {
                        Text(
                            copied,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            play.seed?.let { seed ->
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        context.getString(R.string.dialog_seed),
                                        seed.toString(),
                                    ),
                                )
                                copiedShown = true
                            }
                        },
                        enabled = play.seed != null,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_content_copy),
                            contentDescription = stringResource(R.string.cd_copy_seed),
                        )
                    }
                }
                // The hand-typed mark belongs to the seed it describes, so it sits under the
                // seed rather than in a field of its own: a second label would read as a
                // second fact rather than as a note about this one.
                if (play.typedSeed) {
                    Text(
                        stringResource(R.string.records_typed_seed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The dialog that asks before the selected plays are deleted.
 *
 * [count] is how many are about to go, which the question quotes: "delete 3 records?" is a
 * question a player can answer, and "delete the selected records?" is one they have to count
 * for themselves before they dare answer it.
 */
@Composable
fun DeleteRecordsDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalForm(
        title = stringResource(R.string.records_delete),
        onDismiss = onDismiss,
        confirm = { ModalConfirm(stringResource(R.string.records_delete), onClick = onConfirm) },
    ) {
        Text(stringResource(R.string.records_delete_confirm, count))
    }
}

/** The dialog that asks before every record is deleted. */
@Composable
fun ClearRecordsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalForm(
        title = stringResource(R.string.records_delete_all),
        onDismiss = onDismiss,
        confirm = { ModalConfirm(stringResource(R.string.records_delete), onClick = onConfirm) },
    ) {
        Text(stringResource(R.string.records_delete_all_confirm))
    }
}

/** Groups record groups by the game they belong to, in the order the home page shows the games. */
fun recordsByKind(records: List<GameRecord>): List<Pair<GameKind, List<GameRecord>>> =
    GameKind.entries.mapNotNull { kind ->
        val ofKind = records.filter { it.kind == kind }
        if (ofKind.isEmpty()) null else kind to ofKind
    }

/** Returns the label of a game, which is its name in the interface's language. */
@Composable
fun gameKindLabel(kind: GameKind): String =
    stringResource(
        when (kind) {
            GameKind.Pipes -> R.string.home_pipes_name
            GameKind.Sudoku -> R.string.home_sudoku_name
            GameKind.Minesweeper -> R.string.home_minesweeper_name
            GameKind.Nonogram -> R.string.home_nonogram_name
            GameKind.Hashi -> R.string.home_hashi_name
            GameKind.ArrowCube -> R.string.home_arrow_cube_name
        },
    )

/**
 * Returns the label of a record group's parameters.
 *
 * This is the one place the parameters of a game are written, and the home card, the game
 * header, the records page and the exported image all read it: four spellings of "9 × 9, 10
 * mines" that drifted apart would be four answers to one question.
 */
@Composable
fun GameRecord.keyLabel(): String =
    when (this) {
        is PipesRecord ->
            stringResource(
                R.string.home_pipes_summary,
                size,
                stringResource(if (wrapped) R.string.pipes_layout_wrapped else R.string.pipes_layout_flat),
            )

        is SudokuRecord ->
            stringResource(R.string.home_sudoku_summary, difficultyLabel(difficultyFromStorageName(difficulty)))

        is MinesweeperRecord ->
            stringResource(
                R.string.home_minesweeper_summary,
                width,
                height,
                mines,
                stringResource(
                    if (noGuess) R.string.minesweeper_mode_no_guess else R.string.minesweeper_mode_normal,
                ),
            )

        is NonogramRecord -> stringResource(R.string.home_nonogram_summary, width, height)

        is HashiRecord -> stringResource(R.string.home_hashi_summary, width, height, islands)

        is ArrowCubeRecord -> stringResource(R.string.home_arrow_cube_summary, width, depth, height)
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
