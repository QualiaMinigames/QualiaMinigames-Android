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

import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.res.ResourcesCompat
import com.qiushui1012.app.minigames.AlertDark
import com.qiushui1012.app.minigames.AlertLight
import com.qiushui1012.app.minigames.DurationFont
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.Water
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.rules.MinesweeperBoard
import com.qiushui1012.app.minigames.rules.MinesweeperCell
import com.qiushui1012.app.minigames.rules.MinesweeperPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The colour a covered cell is drawn in.
 *
 * It is the water a solved pipe carries, which is what makes the two boards read as one
 * application: an unopened cell is the same pale blue the pipes' lumen is filled with.
 */
private val CoveredCell = Water

/** The colour a covered cell darkens to while a finger rests on it. */
private val CoveredCellPressed = Color(0xFF9BC2CD)

/** The colour a covered cell darkens to once a long press has taken hold. */
private val CoveredCellLongPressed = Color(0xFF8EBAC7)

/** The width of a cell's divider, as a fraction of a cell. */
private const val CELL_LINE = 0.045f

/** The side of a flag or a mine, as a fraction of a cell. */
private const val MARK_SIZE = 0.68f

/** The distance at which a long press stops being an opening and becomes a flag, in dp. */
private const val FLAG_DP = 8f

/** The distance at which the flag being dragged is fully opaque, in dp. */
private const val FULLY_OPAQUE_DP = 40f

/** The number of cells above which a first click is worth a notice while it runs. */
private const val SLOW_BOARD_CELLS = 4_000

/** How long a first click may take before the board says it is generating. */
private const val GENERATING_MILLIS = 200L

/**
 * The minesweeper board, drawn and played.
 *
 * # What a player sees
 *
 * A covered cell is the pale blue the pipes' water is drawn in and an open one is the board's
 * own background with a number in it. A flag is the theme's ink and a mine is the warning red,
 * so both read in either scheme. The numbers are set in the monospaced face every duration is
 * written in, because a board of numbers is read by comparing them.
 *
 * # One picture per position
 *
 * A 128 by 128 board is 16384 cells, and redrawing them every frame is what a zoom gesture
 * cannot afford. The board is therefore recorded into a [Picture] whenever its position
 * changes, and a zoom or a pan only transforms the recorded commands, which are a list of
 * drawing operations rather than a bitmap: the board stays sharp at any zoom.
 *
 * # The first click
 *
 * A random board is generated when the first cell is opened, and on the largest no-guess board
 * that is a search of up to sixty-four candidates. The screen says so while it runs, because a
 * board that appeared a second later without a word would look like a dropped tap.
 *
 * # The press
 *
 * A press darkens its cell one step, and a long press darkens it a second step. A long press
 * that then drags is a flag whose opacity grows with the distance; a release below the
 * threshold opens the cell instead, and a release above it flags it. A drag that never waited
 * for the long press is left to the zoom and pan of [ZoomableBoard].
 */
@Composable
fun MinesweeperPlay(
    board: MinesweeperBoard,
    onStarted: () -> Unit,
    onCountersChanged: (left: Int, total: Int) -> Unit,
    onWon: () -> Unit,
    onLost: () -> Unit,
) {
    val context = LocalContext.current
    val numberTypeface =
        remember(context) {
            ResourcesCompat.getFont(context, R.font.cascadia_code) ?: Typeface.MONOSPACE
        }
    // The two marks are vector painters rather than drawables painted into the picture: a
    // picture records what a drawable looks like at the moment it is recorded, and a
    // drawable's tint is applied when it draws rather than when the command is recorded, so a
    // mark whose tint was set after the recording came out in the wrong colour or not at all.
    // The painters are drawn through the canvas transform in the composition instead, which
    // also keeps them sharp at any zoom.
    val flagMark = painterResource(R.drawable.ic_flag)
    val mineMark = painterResource(R.drawable.ic_bomb)
    var states by remember(board) { mutableStateOf(board.cellsState()) }
    var phase by remember(board) { mutableStateOf(board.phase) }
    var fellBack by remember(board) { mutableStateOf(board.fellBack) }
    // A board that has not been laid down is being generated while a first click is held, and
    // a generation that outlasts a couple of frames says so rather than leaving the press
    // unanswered.
    var notice by remember(board) { mutableStateOf(false) }
    var busy by remember(board) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // The cell a finger is on, the opacity its flag has reached, and whether the long press
    // that turns a press into a mark has taken hold. All three are drawn, so they are state
    // rather than locals of the gesture loop.
    var pressed by remember(board) { mutableStateOf<Int?>(null) }
    var flagAlpha by remember(board) { mutableFloatStateOf(0f) }
    var longPressed by remember(board) { mutableStateOf(false) }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val boardBackground = MaterialTheme.colorScheme.background
    val numberColor = MaterialTheme.colorScheme.onBackground
    val alert = if (dark) AlertDark else AlertLight
    val divider = MaterialTheme.colorScheme.outline

    LaunchedEffect(phase) {
        when (phase) {
            MinesweeperPhase.Won -> onWon()
            MinesweeperPhase.Lost -> onLost()
            else -> Unit
        }
    }

    /** Reads the board afresh, which is what a move has changed. */
    fun refresh() {
        states = board.cellsState()
        phase = board.phase
        fellBack = board.fellBack
        // The header draws the counter, so the board reports it rather than drawing it twice.
        onCountersChanged(board.remainingMines, board.mineCount)
    }

    /**
     * Opens a cell, generating the board first when it has not been generated yet.
     *
     * The generation runs away from the drawing thread: a no-guess board of the largest size
     * is a search of up to sixty-four candidates, and running it on the thread that draws the
     * screen would freeze the frame the press is shown in. The notice is armed for the same
     * length of time the work is allowed to run quietly: a board generated inside it never
     * shows a word, and one that outlasts it says what is happening.
     */
    fun open(index: Int) {
        if (busy || board.phase == MinesweeperPhase.Won || board.phase == MinesweeperPhase.Lost) {
            return
        }
        if (board.phase != MinesweeperPhase.Pending) {
            board.reveal(index)
            refresh()
            return
        }
        busy = true
        scope.launch {
            // The notice is a child of this coroutine rather than a second one in the same
            // scope: cancelling it is what guarantees a board generated quickly never leaves
            // the word on screen after its own work has finished.
            val noticeJob =
                launch {
                    delay(GENERATING_MILLIS)
                    notice = true
                }
            withContext(Dispatchers.Default) {
                board.reveal(index)
            }
            noticeJob.cancel()
            busy = false
            notice = false
            refresh()
            onStarted()
        }
    }

    val width = board.width
    val height = board.height

    Column(modifier = Modifier.fillMaxSize()) {
        if (fellBack) {
            Text(
                stringResource(R.string.minesweeper_fell_back),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
            )
        }
        if (notice) {
            Text(
                stringResource(R.string.minesweeper_generating),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(Space.m)) {
            val picture =
                remember(width, height, states, boardBackground, numberColor, divider, numberTypeface) {
                    recordBoard(
                        width = width,
                        height = height,
                        states = states,
                        covered = CoveredCell,
                        boardBackground = boardBackground,
                        numberColor = numberColor,
                        divider = divider,
                        typeface = numberTypeface,
                    )
                }
            ZoomableBoard(
                boardWidth = width.toFloat(),
                boardHeight = height.toFloat(),
                minCellDp = 36f,
                columns = width,
                rows = height,
                onTap = { row, column -> open(row * width + column) },
                onPressChanged = { row, column, distanceDp, released ->
                    val index = row?.let { it * width + column }
                    if (released) {
                        val wasLong = longPressed
                        pressed = null
                        flagAlpha = 0f
                        longPressed = false
                        if (index != null && wasLong) {
                            if (distanceDp < FLAG_DP) {
                                open(index)
                            } else if (board.toggleFlag(index)) {
                                refresh()
                            }
                        }
                    } else {
                        pressed = index
                        longPressed = true
                        flagAlpha = ((distanceDp - FLAG_DP) / (FULLY_OPAQUE_DP - FLAG_DP)).coerceIn(0f, 1f)
                    }
                },
                content = {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPicture(picture)
                    }
                    // The marks go over the recorded board rather than into it: a flag and a
                    // mine are vector paintings, and a picture is a recording of draw commands
                    // that a tint set afterwards cannot reach.
                    for (index in states.indices) {
                        when (states[index]) {
                            MinesweeperCell.FLAGGED ->
                                drawMark(flagMark, index, width, numberColor, 1f)

                            MinesweeperCell.MINE ->
                                drawMark(mineMark, index, width, alert, 1f)

                            else -> Unit
                        }
                    }
                    val held = pressed
                    if (held != null && held in states.indices) {
                        drawRect(
                            color = if (longPressed) CoveredCellLongPressed else CoveredCellPressed,
                            topLeft = Offset((held % width).toFloat(), (held / width).toFloat()),
                            size = Size(1f, 1f),
                        )
                        val state = states[held]
                        if (longPressed && state == MinesweeperCell.COVERED && flagAlpha > 0f) {
                            drawMark(flagMark, held, width, numberColor, flagAlpha)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Draws one mark in the middle of a cell, in the painter's own vector form.
 *
 * The painter is drawn inside a transform that moves it to its cell and scales it to a
 * fraction of the cell, so one painter serves every cell and stays sharp at any zoom: the
 * mark is a list of vector commands rather than a bitmap of one. [colour] is applied as a
 * tint, which is what lets one file stand for both the ink a flag is drawn in and the warning
 * red a mine is.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMark(
    painter: Painter,
    index: Int,
    width: Int,
    colour: Color,
    alpha: Float,
) {
    if (alpha <= 0f) {
        return
    }
    val inset = (1f - MARK_SIZE) / 2f
    withTransform({
        translate((index % width).toFloat() + inset, (index / width).toFloat() + inset)
        scale(MARK_SIZE, MARK_SIZE, pivot = Offset.Zero)
    }) {
        with(painter) {
            draw(Size(1f, 1f), alpha = alpha, colorFilter = ColorFilter.tint(colour))
        }
    }
}

/**
 * Records a board's cells, grid and numbers into one picture, in board units.
 *
 * The cells are painted in a single pass over two paint colours rather than one paint per cell,
 * and the numbers are set in the duration face: a board of numbers is read by comparing them,
 * and a proportional face makes `1` narrower than `8`.
 *
 * The flags and the mines are not part of the picture: they are vector painters, and a
 * picture records the drawing commands of the moment it is made, which a tint applied later
 * cannot reach. The composition draws them over the recorded board instead.
 */
@Suppress("LongParameterList")
private fun recordBoard(
    width: Int,
    height: Int,
    states: List<Int>,
    covered: Color,
    boardBackground: Color,
    numberColor: Color,
    divider: Color,
    typeface: Typeface,
): Picture {
    val picture = Picture()
    val canvas = picture.beginRecording(width, height)
    val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill.apply { color = boardBackground.toArgb() })
    fill.color = covered.toArgb()
    for (index in states.indices) {
        if (states[index] >= MinesweeperCell.COVERED && states[index] != MinesweeperCell.MINE) {
            val column = (index % width).toFloat()
            val row = (index / width).toFloat()
            canvas.drawRect(column, row, column + 1f, row + 1f, fill)
        }
    }

    // The grid is drawn over the covered cells rather than under them: a covered cell is a
    // filled square, and a line under it is a line nobody sees.
    val line = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = CELL_LINE }
    line.color = divider.toArgb()
    for (column in 0..width) {
        canvas.drawLine(column.toFloat(), 0f, column.toFloat(), height.toFloat(), line)
    }
    for (row in 0..height) {
        canvas.drawLine(0f, row.toFloat(), width.toFloat(), row.toFloat(), line)
    }

    val number = Paint().apply {
        isAntiAlias = true
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
        textSize = 0.62f
        isSubpixelText = true
    }
    number.color = numberColor.toArgb()
    for (row in 0 until height) {
        for (column in 0 until width) {
            val state = states.getOrNull(row * width + column) ?: continue
            if (state !in 1..MinesweeperCell.MAX_ADJACENT) {
                continue
            }
            // The baseline is placed by the face's own ascent and descent, so the digit sits
            // on the middle of its cell whatever the font's metrics are.
            val baseline = row + 0.5f - (number.ascent() + number.descent()) / 2f
            canvas.drawText(state.toString(), column + 0.5f, baseline, number)
        }
    }

    picture.endRecording()
    return picture
}
