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
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.res.ResourcesCompat
import com.qiushui1012.app.minigames.AlertDark
import com.qiushui1012.app.minigames.AlertLight
import com.qiushui1012.app.minigames.DurationFont
import com.qiushui1012.app.minigames.SuccessDark
import com.qiushui1012.app.minigames.SuccessLight
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
    val success = if (dark) SuccessDark else SuccessLight
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
        // The outcome is a line above the board rather than a dialog: a player who lost wants
        // to read the board under it, and a dialog would cover the very thing it is about.
        val headline =
            when (board.phase) {
                MinesweeperPhase.Won -> stringResource(R.string.minesweeper_won)
                MinesweeperPhase.Lost -> stringResource(R.string.minesweeper_lost)
                else -> null
            }
        if (headline != null) {
            Text(
                headline,
                style = MaterialTheme.typography.titleLarge,
                color =
                    if (board.phase == MinesweeperPhase.Lost) {
                        alert
                    } else {
                        success
                    },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            )
        }
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
                remember(width, height, states, boardBackground, numberColor, divider, alert, numberTypeface) {
                    recordBoard(
                        width = width,
                        height = height,
                        states = states,
                        covered = CoveredCell,
                        boardBackground = boardBackground,
                        numberColor = numberColor,
                        divider = divider,
                        alert = alert,
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
                        val dragged = distanceDp >= FLAG_DP
                        pressed = null
                        flagAlpha = 0f
                        longPressed = false
                        if (index != null && wasLong) {
                            // A press that stayed put opens its cell; one that moved marks it.
                            // The two are the whole difference between opening and flagging,
                            // and a flag never opens: a cell the player has marked is a cell
                            // they have told the board is a mine.
                            if (dragged) {
                                if (board.toggleFlag(index)) {
                                    refresh()
                                }
                            } else {
                                open(index)
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
                    // The press feedback is the only layer above the recorded board: it is the
                    // cell a finger is on, and it has to cover whatever that cell shows until
                    // the finger says what it means.
                    val held = pressed
                    if (held != null && held in states.indices) {
                        val state = states[held]
                        if (state >= MinesweeperCell.COVERED && state != MinesweeperCell.MINE) {
                            drawRect(
                                color = if (longPressed) CoveredCellLongPressed else CoveredCellPressed,
                                topLeft = Offset((held % width).toFloat(), (held / width).toFloat()),
                                size = Size(1f, 1f),
                            )
                        }
                        // A flag a drag is carrying is drawn at the opacity the drag has
                        // reached, over the cell it started on: the board shows how far the
                        // finger has to go before the mark counts.
                        if (longPressed && state == MinesweeperCell.COVERED && flagAlpha > 0f) {
                            drawDragFlag(held, width, numberColor, flagAlpha)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Draws the flag a drag is carrying, at the opacity the drag has reached.
 *
 * It is drawn here rather than recorded into the picture because the opacity changes while the
 * finger moves, and a picture is recorded once per position: recording a new one per pixel of
 * travel would undo the reason the board is recorded at all.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDragFlag(
    index: Int,
    width: Int,
    colour: Color,
    alpha: Float,
) {
    val scale = MARK_SIZE / maxOf(FLAG_BOUNDS.width(), FLAG_BOUNDS.height())
    val offsetX = (1f - FLAG_BOUNDS.width() * scale) / 2f
    val offsetY = (1f - FLAG_BOUNDS.height() * scale) / 2f
    drawIntoCanvas { canvas ->
        val paint =
            android.graphics.Paint().apply {
                isAntiAlias = true
                color = colour.toArgb()
                this.alpha = (alpha * 255).toInt()
            }
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.translate(index % width + offsetX, index / width + offsetY)
        canvas.nativeCanvas.scale(scale, scale)
        canvas.nativeCanvas.translate(-FLAG_BOUNDS.left, -FLAG_BOUNDS.top)
        canvas.nativeCanvas.drawPath(FLAG_GLYPH, paint)
        canvas.nativeCanvas.restore()
    }
}

/**
 * Draws one mark in the middle of a cell.
 *
 * The glyph's path data is in the same units as the home card's drawing, so the board scales
 * it by the mark's size over the glyph's own bounds and moves it into the cell. Parsing the
 * path data once and drawing it as an ordinary path is what lets the mark be recorded into the
 * board's picture in the colour the board wants, which a tinted drawable could not be.
 */
private fun drawGlyph(
    canvas: android.graphics.Canvas,
    paint: Paint,
    glyph: android.graphics.Path,
    bounds: android.graphics.RectF,
    index: Int,
    width: Int,
) {
    // The glyph is scaled by one factor and then centred on its own bounds rather than pinned
    // by its top-left corner: the flag and the mine are different shapes, and a mark placed by
    // its corner sits off the middle of its cell by half the difference.
    val target = MARK_SIZE
    val scale = target / maxOf(bounds.width(), bounds.height())
    val drawnWidth = bounds.width() * scale
    val drawnHeight = bounds.height() * scale
    val offsetX = (1f - drawnWidth) / 2f
    val offsetY = (1f - drawnHeight) / 2f
    canvas.save()
    canvas.translate(index % width + offsetX, index / width + offsetY)
    canvas.scale(scale, scale)
    canvas.translate(-bounds.left, -bounds.top)
    canvas.drawPath(glyph, paint)
    canvas.restore()
}

/** The flag's path, in the units of the minesweeper mark the home card draws. */
private val FLAG_GLYPH: android.graphics.Path = android.graphics.Path().apply {
    val data =
        "M50.878 62.533 L50.878 50.066 L57.478 50.066 L57.771 51.533 L61.878 51.533 " +
            "L61.878 58.866 L56.744 58.866 L56.451 57.399 L52.344 57.399 L52.344 62.533 Z " +
            "M57.954 57.399 L60.411 57.399 L60.411 52.999 L56.561 52.999 L56.268 51.533 " +
            "L52.344 51.533 L52.344 55.933 L57.661 55.933 Z"
    fillFromData(this, data)
    fillType = android.graphics.Path.FillType.EVEN_ODD
}

/** The mine's path, in the units of the minesweeper mark the home card draws. */
private val MINE_GLYPH: android.graphics.Path = android.graphics.Path().apply {
    val data =
        "M33.717 43.741 C32.328 43.741 31.147 43.25 30.175 42.266 C29.203 41.283 28.717 40.097 " +
            "28.717 38.708 C28.717 37.319 29.197 36.144 30.158 35.183 C31.119 34.222 32.294 33.741 " +
            "33.683 33.741 L33.9 33.741 L34.35 32.958 C34.483 32.713 34.683 32.555 34.95 32.483 " +
            "C35.217 32.411 35.472 32.447 35.717 32.591 L36.217 32.875 L36.3 32.741 " +
            "C36.556 32.263 36.956 31.952 37.5 31.808 C38.044 31.663 38.556 31.73 39.033 32.008 " +
            "L39.617 32.341 L38.95 33.491 L38.367 33.158 C38.211 33.069 38.042 33.05 37.858 33.1 " +
            "C37.675 33.15 37.539 33.252 37.45 33.408 L37.367 33.541 L38.033 33.925 " +
            "C38.267 34.058 38.419 34.258 38.492 34.525 C38.564 34.791 38.533 35.041 38.4 35.275 " +
            "L37.95 36.075 C38.206 36.475 38.397 36.9 38.525 37.35 C38.653 37.8 38.717 38.263 " +
            "38.717 38.741 C38.717 40.13 38.231 41.311 37.258 42.283 C36.286 43.255 35.106 43.741 " +
            "33.717 43.741 Z " +
            "M33.717 42.408 C34.728 42.408 35.592 42.05 36.308 41.333 C37.025 40.616 37.383 39.752 " +
            "37.383 38.741 C37.383 38.397 37.336 38.058 37.242 37.725 C37.147 37.391 37.006 37.075 " +
            "36.817 36.775 L36.383 36.091 L37.083 34.891 L35.35 33.891 L34.65 35.091 L33.917 35.091 " +
            "C32.872 35.091 31.964 35.425 31.192 36.091 C30.419 36.758 30.033 37.63 30.033 38.708 " +
            "C30.033 39.73 30.392 40.602 31.108 41.325 C31.825 42.047 32.694 42.408 33.717 42.408 Z " +
            // The three sparks: one short arm at each of the mine's two upper quarters and one
            // at its upper right. They are separate subpaths of the same glyph, and a board
            // that left them out drew a plain circle.
            "M41.283 34.408 L41.283 33.075 L43.283 33.075 L43.283 34.408 Z " +
            "M37.617 30.741 L37.617 28.741 L38.95 28.741 L38.95 30.741 Z " +
            "M40.867 32.091 L39.933 31.158 L41.35 29.741 L42.283 30.675 Z"
    fillFromData(this, data)
    fillType = android.graphics.Path.FillType.EVEN_ODD
}

/** The bounds of the flag's path, which its mark is scaled to. */
private val FLAG_BOUNDS = android.graphics.RectF(50.878f, 50.066f, 61.878f, 62.533f)

/** The bounds of the mine's path, which its mark is scaled to. */
private val MINE_BOUNDS = android.graphics.RectF(28.717f, 28.741f, 43.283f, 43.741f)

/** Fills a path from SVG path data, which `PathParser` reads. */
private fun fillFromData(path: android.graphics.Path, data: String) {
    val parsed = androidx.core.graphics.PathParser.createPathFromPathData(data)
    requireNotNull(parsed) { "the mark's path data did not parse" }
    path.set(parsed)
}

/**
 * Records a board into one picture, in board units.
 *
 * # The order of the layers
 *
 * The cells are laid down first, then the grid over them, then the numbers, then the marks.
 * The marks have to be last: a covered cell is a filled square, and a flag drawn before it
 * would be painted over by the very cell it marks. That order is why the marks are recorded
 * here rather than drawn by the composition afterwards: a picture is one layer, and anything
 * drawn after it would sit above the press feedback the board puts on top.
 *
 * # Why the marks are described here rather than read from a vector file
 *
 * A `Picture` records the drawing commands of the moment it is made. A `VectorDrawable`'s tint
 * is applied when it draws, which is after the recording, so a mark recorded that way came out
 * in the file's own colour — or, when the viewport disagreed with the path, not at all. The two
 * marks are therefore built from the same path data the home card reads, as ordinary path
 * commands in the colour the board wants at the moment it records.
 *
 * # The two colours of a revealed mine
 *
 * A mine's cell is painted the warning red and the mine itself in the board's own background,
 * which is how the mark reads as a mine rather than as a red square: the same pair the home
 * card uses for its red cell.
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
    alert: Color,
    typeface: Typeface,
): Picture {
    val picture = Picture()
    val canvas = picture.beginRecording(width, height)
    val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill.apply { color = boardBackground.toArgb() })

    // A mine's cell is the warning red beneath a mark in the board's own colour, and every
    // other closed cell is the covered blue. The two are one pass with two paints rather than
    // a pass per cell.
    fill.color = covered.toArgb()
    for (index in states.indices) {
        if (states[index] == MinesweeperCell.MINE) {
            continue
        }
        if (states[index] >= MinesweeperCell.COVERED) {
            val column = (index % width).toFloat()
            val row = (index / width).toFloat()
            canvas.drawRect(column, row, column + 1f, row + 1f, fill)
        }
    }
    fill.color = alert.toArgb()
    for (index in states.indices) {
        if (states[index] == MinesweeperCell.MINE) {
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

    // The marks come last, over everything a cell has already drawn.
    val markPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    for (index in states.indices) {
        when (states[index]) {
            MinesweeperCell.FLAGGED -> {
                markPaint.color = numberColor.toArgb()
                drawGlyph(canvas, markPaint, FLAG_GLYPH, FLAG_BOUNDS, index, width)
            }

            MinesweeperCell.MINE -> {
                markPaint.color = boardBackground.toArgb()
                drawGlyph(canvas, markPaint, MINE_GLYPH, MINE_BOUNDS, index, width)
            }

            else -> Unit
        }
    }

    picture.endRecording()
    return picture
}
