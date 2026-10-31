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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.formatPerMille
import com.qiushui1012.app.minigames.parseCountOrPercent
import com.qiushui1012.app.minigames.CountOrPercent
import com.qiushui1012.app.minigames.resolveSeed
import com.qiushui1012.app.minigames.rules.HashiBoard
import com.qiushui1012.app.minigames.rules.Rules
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The most digits a hashi size field accepts, which is what the largest board needs. */
private const val MAX_SIZE_DIGITS = 2

/** The island share a form starts from when the settings hold nothing it can read. */
private const val DEFAULT_PER_MILLE = 150

/**
 * The dialog that starts a hashi game.
 *
 * # The order of the controls
 *
 * The width and the height, then the island field with its helper line and its slider, then the
 * player's name and the seed. The sides are points, not cells: a hashi board of 10 by 10 has ten
 * points across, and an island stands on one of them.
 *
 * # Why the island field is a text field
 *
 * It accepts two spellings of one thing, exactly as the mine field does: a number of islands, and
 * a share of the points written with a percent sign. The helper line under it says the other
 * spelling, so a player who typed either can read what it came to, and the slider snaps to the
 * six five-percent steps the rules name.
 */
@Composable
fun HashiNewGameDialog(
    container: AppContainer,
    settings: Settings,
    onDismiss: () -> Unit,
    onStart: (GameInProgress) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val minimum = Rules.hashiMinPoints()
    val maximum = Rules.hashiMaxPoints()
    val fallbackSeed = remember { System.currentTimeMillis() }

    var width by remember { mutableStateOf(settings.lastHashiWidth.coerceIn(minimum, maximum)) }
    var widthText by remember { mutableStateOf(width.toString()) }
    var height by remember { mutableStateOf(settings.lastHashiHeight.coerceIn(minimum, maximum)) }
    var heightText by remember { mutableStateOf(height.toString()) }
    // The share is the form's own state; a count typed into the field is converted once and kept
    // as the share it came to, which is what keeps the slider and the field in step.
    var perMille by remember {
        mutableStateOf(settings.lastHashiPercent.toIntOrNull() ?: DEFAULT_PER_MILLE)
    }
    var islandsText by remember { mutableStateOf(formatPerMille(perMille)) }
    // Whether the field currently holds a count rather than a share, which decides which of the
    // two the helper line says.
    var typedCount by remember { mutableStateOf(false) }
    var playerName by remember { mutableStateOf(settings.playerName) }
    var seedText by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    val points = width * height
    val islands = islandsFor(points, perMille)
    val valid = hashiParamsValid(width, height, perMille, islands)

    /** Writes the helper line and the field from the current share. */
    fun showShare() {
        typedCount = false
        islandsText = formatPerMille(perMille)
    }

    /**
     * Keeps the field in step with a change of shape.
     *
     * The share is what the form holds, so a change of width or of height refreshes the island
     * count the share comes to rather than the share itself.
     */
    fun afterShapeChange() {
        if (typedCount) {
            islandsText = islandsFor(width * height, perMille).toString()
        }
    }

    ModalForm(
        title = stringResource(R.string.dialog_new_game_title),
        onDismiss = onDismiss,
        confirm = {
            ModalConfirm(
                label = stringResource(R.string.dialog_start),
                enabled = valid,
                onClick = {
                    failed = false
                    startHashi(
                        scope = scope,
                        container = container,
                        width = width,
                        height = height,
                        islands = islands,
                        perMille = perMille,
                        seedText = seedText,
                        fallbackSeed = fallbackSeed,
                        playerName = playerName,
                        onStarted = onStart,
                        onFailed = { failed = true },
                    )
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            SizeControl(
                label = stringResource(R.string.hashi_width_range),
                value = width,
                text = widthText,
                minimum = minimum,
                maximum = maximum,
                note = stringResource(R.string.hashi_slider_ticks),
                onText = { typed ->
                    widthText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                    widthText.toIntOrNull()?.let { typedWidth ->
                        if (typedWidth in minimum..maximum) {
                            width = typedWidth
                            afterShapeChange()
                        }
                    }
                },
                onSlider = {
                    width = it
                    widthText = it.toString()
                    afterShapeChange()
                },
            )
            SizeControl(
                label = stringResource(R.string.hashi_height_range),
                value = height,
                text = heightText,
                minimum = minimum,
                maximum = maximum,
                note = stringResource(R.string.hashi_slider_ticks),
                onText = { typed ->
                    heightText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                    heightText.toIntOrNull()?.let { typedHeight ->
                        if (typedHeight in minimum..maximum) {
                            height = typedHeight
                            afterShapeChange()
                        }
                    }
                },
                onSlider = {
                    height = it
                    heightText = it.toString()
                    afterShapeChange()
                },
            )

            OutlinedTextField(
                value = islandsText,
                onValueChange = { typed ->
                    islandsText = typed
                    when (val parsed = parseCountOrPercent(typed)) {
                        is CountOrPercent.Count -> {
                            typedCount = parsed.count >= 0
                            perMille = perMilleForIslands(points, parsed.count)
                        }

                        is CountOrPercent.Percent -> {
                            typedCount = false
                            perMille = parsed.perMille
                        }

                        null -> Unit
                    }
                },
                label = { Text(stringResource(R.string.hashi_islands)) },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (typedCount) {
                    stringResource(R.string.hashi_islands_helper_percent, formatPerMille(perMille))
                } else {
                    stringResource(R.string.hashi_islands_helper_count, islandsFor(points, perMille))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IslandSlider(
                perMille = perMille,
                onValueChange = { value ->
                    perMille = value
                    // A slider move is the player choosing a share, so the field is rewritten
                    // with it whether it was spelling a share or a count.
                    showShare()
                },
            )

            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text(stringResource(R.string.dialog_player_name)) },
                placeholder = { Text(stringResource(R.string.dialog_player_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SeedField(value = seedText, onValueChange = { seedText = it })
            if (failed) {
                Text(
                    stringResource(R.string.hashi_generate_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Returns the island count a share of a board's points comes to. */
private fun islandsFor(points: Int, perMille: Int): Int =
    if (points <= 0) {
        0
    } else {
        Rules.hashiIslandsForPerMille(points, perMille)
    }

/** Returns the share a number of islands is of a board, in thousandths. */
private fun perMilleForIslands(points: Int, islands: Int): Int =
    if (points <= 0) {
        0
    } else {
        ((islands.toLong() * 1000 + points / 2) / points).toInt()
    }

/** Whether a form's numbers are a board the rules will accept. */
private fun hashiParamsValid(width: Int, height: Int, perMille: Int, islands: Int): Boolean {
    if (width !in Rules.hashiMinPoints()..Rules.hashiMaxPoints()) {
        return false
    }
    if (height !in Rules.hashiMinPoints()..Rules.hashiMaxPoints()) {
        return false
    }
    if (perMille < Rules.hashiIslandPerMilleMin() || perMille > Rules.hashiIslandPerMilleMax()) {
        return false
    }
    return islands >= 2 && islands <= width * height
}

/**
 * The island slider, which snaps to the six five-percent steps the rules name.
 *
 * The value is continuous over the range the rules allow, and a value within five thousandths of
 * a snap point becomes that point. The label under it names the share the slider is at rather
 * than listing all six: six labels side by side is a wall of text on a phone.
 */
@Composable
private fun IslandSlider(
    perMille: Int,
    onValueChange: (Int) -> Unit,
) {
    val minimum = Rules.hashiIslandPerMilleMin()
    val maximum = Rules.hashiIslandPerMilleMax()
    val table = Rules.hashiIslandSnapPerMille()
    Slider(
        value = perMille.coerceIn(minimum, maximum).toFloat(),
        onValueChange = { raw ->
            val rounded = raw.toInt()
            onValueChange(table.firstOrNull { abs(it - rounded) <= 5 } ?: rounded)
        },
        valueRange = minimum.toFloat()..maximum.toFloat(),
        steps = 0,
    )
    Text(
        formatPerMille(perMille),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Generates a new hashi board and remembers the parameters it was started with. */
@Suppress("LongParameterList")
private fun startHashi(
    scope: CoroutineScope,
    container: AppContainer,
    width: Int,
    height: Int,
    islands: Int,
    perMille: Int,
    seedText: String,
    fallbackSeed: Long,
    playerName: String,
    onStarted: (GameInProgress) -> Unit,
    onFailed: () -> Unit,
) {
    val typed = seedText.trim().isNotEmpty()
    val resolved = resolveSeed(seedText, fallbackSeed)
    scope.launch {
        val board: HashiBoard? =
            withContext(Dispatchers.Default) { Rules.newHashi(width, height, islands, resolved.seed) }
        if (board == null) {
            onFailed()
            return@launch
        }
        container.settings.setLastHashi(width, height, perMille.toString())
        container.records.clearSave(GameKind.Hashi)
        onStarted(GameInProgress.Hashi(board, playerName.trim(), 0, typed))
    }
}
