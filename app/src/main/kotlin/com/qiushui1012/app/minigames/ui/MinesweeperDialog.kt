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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import com.qiushui1012.app.minigames.MinesweeperPreset
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.SnapLabels
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.cellsOf
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.formatPerMille
import com.qiushui1012.app.minigames.inputPerMilleMax
import com.qiushui1012.app.minigames.minesForPerMille
import com.qiushui1012.app.minigames.minesweeperModeFromStorageName
import com.qiushui1012.app.minigames.minesweeperParamsValid
import com.qiushui1012.app.minigames.nearestSnapIndex
import com.qiushui1012.app.minigames.parseMinesInput
import com.qiushui1012.app.minigames.parsePerMille
import com.qiushui1012.app.minigames.perMilleForCount
import com.qiushui1012.app.minigames.resolveSeed
import com.qiushui1012.app.minigames.rules.MinesweeperMode
import com.qiushui1012.app.minigames.rules.Rules
import com.qiushui1012.app.minigames.sliderPerMilleMax
import com.qiushui1012.app.minigames.sliderPerMilleMin
import com.qiushui1012.app.minigames.snapPerMille
import com.qiushui1012.app.minigames.toStorageName
import kotlinx.coroutines.launch

/** The most digits a size field accepts, which is what the largest board needs. */
private const val MAX_SIZE_DIGITS = 3

/** The share a form starts from when the settings hold nothing it can read. */
private const val DEFAULT_PER_MILLE = 123/**
 * The dialog that starts a minesweeper game.
 *
 * # The order of the controls
 *
 * The three presets come first, because they are what most games are: one tap fills in a
 * shape and a density that players already know by name. The width and the height follow as a
 * field above a slider each, then the mine field with its helper line, then the rules, the
 * player's name and the seed.
 *
 * # Why the mine field is a text field
 *
 * It accepts two things a slider cannot say: a share of the board, written with a percent
 * sign, and a count that the share would only approximate. The helper line under it says the
 * other one of the two, so whichever was typed the player can read what it came to.
 *
 * # Why the slider stops at 35
 *
 * The board goes to 128 cells on a side, which no slider can land on: 94 values over a
 * phone's width is a value every three pixels. The slider covers the range a game is actually
 * played at and the field covers the rest, which is the same division the pipes dialog makes
 * over its own 4 to 35 range.
 */
@Composable
fun MinesweeperNewGameDialog(
    container: AppContainer,
    settings: Settings,
    onDismiss: () -> Unit,
    onStart: (GameInProgress) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val minimum = Rules.minesweeperMinSize()
    val maximum = Rules.minesweeperMaxSize()
    // The seed a random game starts from is taken when the dialog opens, so that the moment
    // the button is pressed cannot change the board the player is looking at.
    val fallbackSeed = remember { System.currentTimeMillis() }

    var width by remember { mutableStateOf(settings.lastMinesweeperWidth.coerceIn(minimum, maximum)) }
    var widthText by remember { mutableStateOf(width.toString()) }
    var height by remember { mutableStateOf(settings.lastMinesweeperHeight.coerceIn(minimum, maximum)) }
    var heightText by remember { mutableStateOf(height.toString()) }
    var mode by remember { mutableStateOf(minesweeperModeFromStorageName(settings.lastMinesweeperMode)) }
    // The share is the form's own state; the field shows it formatted, and a count typed into
    // the field is converted once and kept as the share it came to. The settings keep the
    // share as its own integer, so a value they cannot hold is the default rather than a
    // second reading of the same text.
    var perMille by remember {
        mutableStateOf(settings.lastMinesweeperPercent.toIntOrNull() ?: DEFAULT_PER_MILLE)
    }
    var minesText by remember { mutableStateOf(formatPerMille(perMille)) }
    // Whether the field currently holds a count rather than a share, which is what decides
    // which of the two the helper line says.
    var typedCount by remember { mutableStateOf(false) }
    var playerName by remember { mutableStateOf(settings.playerName) }
    var seedText by remember { mutableStateOf("") }

    val cells = cellsOf(width, height)
    val mines = minesForPerMille(cells, perMille.coerceIn(sliderPerMilleMin(), sliderPerMilleMax(mode)))
    val valid = minesweeperParamsValid(width, height, mines, perMille, mode)
    val sliderMax = sliderPerMilleMax(mode)
    val sliderMin = sliderPerMilleMin()

    /** Keeps the share inside the range the chosen rules allow. */
    fun clampPerMille(value: Int): Int = value.coerceIn(sliderMin, inputPerMilleMax(mode))

    /** Writes the helper line and the field from the current share. */
    fun showShare() {
        typedCount = false
        minesText = formatPerMille(perMille)
    }

    /**
     * Keeps the field and the helper line in step with a change of shape or of rules.
     *
     * The share is the thing the form holds, so a change of width, height or mode does not
     * move it: the mine count it comes to follows the board. What has to be rewritten is the
     * field, and only when it is spelling a count, because a count is a number about a board
     * of a particular size and the size has just changed.
     *
     * [newCells] is the shape being moved to, passed rather than read from the state the
     * caller is about to write: a Compose state read in the same event still holds its old
     * value.
     */
    fun afterShapeChange(newCells: Int) {
        perMille = clampPerMille(perMille)
        if (typedCount) {
            minesText = minesForPerMille(newCells, perMille).toString()
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
                    startMinesweeper(
                        scope = scope,
                        container = container,
                        width = width,
                        height = height,
                        mines = mines,
                        mode = mode,
                        perMille = perMille,
                        seedText = seedText,
                        fallbackSeed = fallbackSeed,
                        playerName = playerName,
                        onStart = onStart,
                    )
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                for (preset in MinesweeperPreset.entries) {
                    ChoiceButton(stringResource(preset.label), selected = false) {
                        width = preset.width
                        widthText = width.toString()
                        height = preset.height
                        heightText = height.toString()
                        // A preset states the density it is named for, so the share is set
                        // rather than kept, and the field is rewritten whatever it was
                        // spelling: a count typed against the last board is not this one's.
                        perMille = preset.perMille.coerceIn(sliderMin, inputPerMilleMax(mode))
                        showShare()
                    }
                }
            }

            SizeControl(
                label = stringResource(R.string.minesweeper_width_range),
                value = width,
                text = widthText,
                minimum = minimum,
                maximum = maximum,
                note = stringResource(R.string.minesweeper_slider_ticks),
                onText = { typed ->
                    widthText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                    widthText.toIntOrNull()?.let { typedWidth ->
                        if (typedWidth in minimum..maximum) {
                            width = typedWidth
                            afterShapeChange(typedWidth * height)
                        }
                    }
                },
                onSlider = {
                    width = it
                    widthText = it.toString()
                    afterShapeChange(it * height)
                },
            )

            SizeControl(
                label = stringResource(R.string.minesweeper_height_range),
                value = height,
                text = heightText,
                minimum = minimum,
                maximum = maximum,
                note = stringResource(R.string.minesweeper_slider_ticks),
                onText = { typed ->
                    heightText = typed.filter { it.isDigit() }.take(MAX_SIZE_DIGITS)
                    heightText.toIntOrNull()?.let { typedHeight ->
                        if (typedHeight in minimum..maximum) {
                            height = typedHeight
                            afterShapeChange(width * typedHeight)
                        }
                    }
                },
                onSlider = {
                    height = it
                    heightText = it.toString()
                    afterShapeChange(width * it)
                },
            )

            OutlinedTextField(
                value = minesText,
                onValueChange = { typed ->
                    minesText = typed
                    when (val parsed = parseMinesInput(typed)) {
                        is com.qiushui1012.app.minigames.CountOrPercent.Count -> {
                            typedCount = parsed.count >= 0
                            perMille = clampPerMille(perMilleForCount(cells, parsed.count))
                        }

                        is com.qiushui1012.app.minigames.CountOrPercent.Percent -> {
                            typedCount = false
                            perMille = clampPerMille(parsed.perMille)
                        }

                        null -> Unit
                    }
                },
                label = { Text(stringResource(R.string.minesweeper_mines)) },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            // The helper says the other spelling of the same number, so a player who typed a
            // share reads the count it came to and one who typed a count reads its share. It
            // sits between the field and the slider, where it belongs to the field above it.
            Text(
                if (typedCount) {
                    stringResource(R.string.minesweeper_mines_helper_percent, formatPerMille(perMille))
                } else {
                    stringResource(R.string.minesweeper_mines_helper_count, minesForPerMille(cells, perMille))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MinesSlider(
                perMille = perMille,
                mode = mode,
                onValueChange = { value ->
                    perMille = clampPerMille(value)
                    typedCount = false
                    minesText = formatPerMille(perMille)
                },
            )

            Text(stringResource(R.string.minesweeper_mode), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ChoiceButton(stringResource(R.string.minesweeper_mode_normal), mode == MinesweeperMode.Normal) {
                    mode = MinesweeperMode.Normal
                    // The no-guess ceiling is lower than the normal one only on the way in,
                    // so a share the chosen rules no longer allow is pulled back to the edge
                    // they do allow — and the field, which shows the share, is rewritten with
                    // it rather than left saying the old value.
                    perMille = clampPerMille(perMille)
                    showShare()
                }
                ChoiceButton(stringResource(R.string.minesweeper_mode_no_guess), mode == MinesweeperMode.NoGuess) {
                    mode = MinesweeperMode.NoGuess
                    perMille = clampPerMille(perMille)
                    showShare()
                }
            }
            Text(
                if (mode == MinesweeperMode.NoGuess) {
                    stringResource(R.string.minesweeper_mode_no_guess_hint)
                } else {
                    stringResource(R.string.minesweeper_mode_normal_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mode == MinesweeperMode.NoGuess && perMille >= Rules.minesweeperNoGuessWarnPerMille()) {
                Text(
                    stringResource(R.string.minesweeper_mode_no_guess_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text(stringResource(R.string.dialog_player_name)) },
                placeholder = { Text(stringResource(R.string.dialog_player_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SeedField(value = seedText, onValueChange = { seedText = it })
        }
    }
}

/** The seed field the three dialogs share, so its label and hint cannot drift apart. */
@Composable
fun SeedField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.dialog_seed)) },
        placeholder = { Text(stringResource(R.string.dialog_seed_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The mine slider, which snaps to the eight named densities.
 *
 * The value is continuous over the range the rules allow, and a value within one thousandth of
 * a percent of a snap point becomes that point. The label under it names the nearest point
 * rather than listing all eight: eight labels side by side is a wall of text on a phone.
 */
@Composable
private fun MinesSlider(
    perMille: Int,
    mode: MinesweeperMode,
    onValueChange: (Int) -> Unit,
) {
    val minimum = sliderPerMilleMin()
    val maximum = sliderPerMilleMax(mode)
    val table = Rules.minesweeperSnapPerMille()
    Slider(
        // A value outside the range holds the slider at the end it passed: a share above the
        // no-guess ceiling that the field accepted still shows where the slider would be.
        value = perMille.coerceIn(minimum, maximum).toFloat(),
        onValueChange = { raw ->
            val rounded = raw.toInt()
            onValueChange(snapPerMille(rounded) ?: rounded)
        },
        valueRange = minimum.toFloat()..maximum.toFloat(),
        steps = 0,
    )
    val index = nearestSnapIndex(perMille.coerceIn(minimum, maximum))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(
            stringResource(SnapLabels[index]),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (table.getOrNull(index) != perMille) {
            Text(
                formatPerMille(perMille),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Starts a new minesweeper game and remembers the parameters it was started with. */
@Suppress("LongParameterList")
private fun startMinesweeper(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    width: Int,
    height: Int,
    mines: Int,
    mode: MinesweeperMode,
    perMille: Int,
    seedText: String,
    fallbackSeed: Long,
    playerName: String,
    onStart: (GameInProgress) -> Unit,
) {
    val typed = seedText.trim().isNotEmpty()
    val resolved = resolveSeed(seedText, fallbackSeed)
    val board =
        if (typed) {
            // A seed a player typed opens the board it names at once, mine under the first
            // cell included: that is what makes the seed reproducible.
            Rules.minesweeperFromSeed(width, height, mines, mode, resolved.seed)
        } else {
            Rules.newMinesweeper(width, height, mines, mode, resolved.seed)
        } ?: return
    scope.launch {
        container.settings.setLastMinesweeper(width, height, perMille.toString(), mode.toStorageName())
        container.records.clearSave(GameKind.Minesweeper)
    }
    onStart(GameInProgress.Minesweeper(board, playerName.trim(), 0, typed))
}
