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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qiushui1012.app.minigames.AlertDark
import com.qiushui1012.app.minigames.AlertLight
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.rules.ClueLine
import com.qiushui1012.app.minigames.rules.NonogramBoard
import com.qiushui1012.app.minigames.rules.NonogramCell

/** The width of an ordinary cell line, as a fraction of a cell. */
private const val THIN_LINE = 0.03f

/** The width of the line every fifth cell and the clue margin boundary use. */
private const val THICK_LINE = 0.06f

/** The width of the clue's digits, as a fraction of a cell. */
private const val CLUE_TEXT_SIZE = 0.55f

/** The width of one row-clue digit, as a fraction of a cell. */
private const val ROW_CLUE_ADVANCE = 0.62f

/** The height of one column-clue digit, as a fraction of a cell. */
private const val COLUMN_CLUE_ADVANCE = 0.8f

/** The padding between the clue margin and the board's edge, as a fraction of a cell. */
private const val CLUE_PADDING = 0.3f

/** The padding above or before the column clues, as a fraction of a cell. */
private const val COLUMN_CLUE_PADDING = 0.2f

/** The side of the cross a marked cell draws, as a fraction of a cell. */
private const val CROSS_SIZE = 0.52f

/** The width of the cross's strokes, as a fraction of a cell. */
private const val CROSS_LINE = 0.07f

/** The distance a long press has to travel before it is the mark and not the click, in dp. */
private const val MARK_DRAG_DP = 8f

/** The distance at which the cross being dragged is fully opaque, in dp. */
private const val FULLY_OPAQUE_DP = 40f

/**
 * The nonogram board, drawn and played.
 *
 * # What a player sees
 *
 * The clue list sits along the top and down the left, and the grid inside them. A filled cell
 * is the theme's ink, an empty one is the board's own background and a marked one carries a red
 * cross. The grid is drawn over both the cells and the clue margin, so a run of filled cells is
 * still counted cell by cell rather than read as one block; every fifth line is doubled, which
 * is what makes a 64 by 64 board countable at all.
 *
 * # What turns red
 *
 * A row or a column whose clue the player's cells can no longer match has its numbers painted
 * in the warning red. The board does not say which cell is wrong and does not fill in any other
 * cell: the player is told that a line can no longer be finished and works out the rest. The two
 * lists are read once per frame, which is once per position, rather than once per cell.
 *
 * # One picture per position
 *
 * A 64 by 64 board is 4096 cells, and redrawing them on every zoom step is what a pinch cannot
 * afford. The board is therefore recorded into a [Picture] whenever the position or the broken
 * lines change, and a zoom or a pan only transforms the recorded commands, so the board stays
 * sharp at any scale.
 */
@Composable
fun NonogramPlay(
    board: NonogramBoard,
    settings: com.qiushui1012.app.minigames.data.Settings,
    onSolved: () -> Unit,
) {
    val context = LocalContext.current
    val typeface = remember(context) { BoardText.typeface(context) }
    val width = board.width
    val height = board.height
    var states by remember(board) { mutableStateOf(board.cellsState()) }
    var brokenRows by remember(board) { mutableStateOf(board.brokenRows().toSet()) }
    var brokenColumns by remember(board) { mutableStateOf(board.brokenColumns().toSet()) }
    var solved by remember(board) { mutableStateOf(board.isSolved()) }
    var pressed by remember(board) { mutableStateOf<Int?>(null) }
    var longPressed by remember(board) { mutableStateOf(false) }
    var crossAlpha by remember(board) { mutableFloatStateOf(0f) }
    val rowClues = remember(board) { board.rowClues() }
    val columnClues = remember(board) { board.columnClues() }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val boardBackground = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val alert = if (dark) AlertDark else AlertLight

    LaunchedEffect(solved) {
        if (solved) {
            onSolved()
        }
    }

    /** Reads the board afresh, which is what a move has changed. */
    fun refresh() {
        states = board.cellsState()
        // The two lists are read together and once: a row's feasibility depends on the cells
        // of other rows, so reading one per cell would be the same work over and over.
        brokenRows = board.brokenRows().toSet()
        brokenColumns = board.brokenColumns().toSet()
        solved = board.isSolved()
    }

    val rowMargin = rowMarginOf(rowClues)
    val columnMargin = columnMarginOf(columnClues)
    val boardWidth = width + rowMargin
    val boardHeight = height + columnMargin

    Column(modifier = Modifier.fillMaxSize()) {
        if (board.fellBack) {
            Text(
                stringResource(R.string.nonogram_fell_back),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
            )
        }
        if (solved) {
            Text(
                stringResource(R.string.nonogram_won),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            )
        }
        val description = stringResource(R.string.cd_nonogram_board)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(Space.m)
                    .semantics { contentDescription = description },
        ) {
            // The cell a finger is on is part of the picture's key: it is painted into the board
            // as that cell's own background, so the grid line and the cross of the cell stay on
            // top of it rather than being washed over.
            val lit = pressed
            val picture =
                remember(
                    width,
                    height,
                    states,
                    rowClues,
                    columnClues,
                    brokenRows,
                    brokenColumns,
                    lit,
                    longPressed,
                    boardBackground,
                    ink,
                    outline,
                    alert,
                    primary,
                    typeface,
                ) {
                    recordNonogram(
                        width = width,
                        height = height,
                        states = states,
                        rowClues = rowClues,
                        columnClues = columnClues,
                        brokenRows = brokenRows,
                        brokenColumns = brokenColumns,
                        lit = lit,
                        litAlpha = if (longPressed) 0.26f else 0.18f,
                        litFill = primary,
                        boardBackground = boardBackground,
                        ink = ink,
                        outline = outline,
                        alert = alert,
                        typeface = typeface,
                    )
                }
            ZoomableBoard(
                boardWidth = boardWidth,
                boardHeight = boardHeight,
                minCellDp = 28f,
                columns = width,
                rows = height,
                cellWidth = 1f,
                cellHeight = 1f,
                cellOrigin = Offset(rowMargin + 0.5f, columnMargin + 0.5f),
                onTap = { row, column ->
                    val index = row * width + column
                    if (board.toggleFilled(index)) {
                        refresh()
                    }
                },
                onPressChanged = { row, column, distanceDp, released ->
                    val index = row?.let { it * width + column }
                    if (released) {
                        val wasLong = longPressed
                        pressed = null
                        longPressed = false
                        crossAlpha = 0f
                        // A long press marks the cell as staying empty whichever way it went:
                        // the gesture means the same thing whether the finger stayed on the
                        // cell or wandered off it while the press was held.
                        if (wasLong && index != null && board.toggleMarked(index)) {
                            refresh()
                        }
                    } else {
                        pressed = index
                        longPressed = true
                        crossAlpha =
                            ((distanceDp - MARK_DRAG_DP) / (FULLY_OPAQUE_DP - MARK_DRAG_DP))
                                .coerceIn(0f, 1f)
                    }
                },
                content = {
                    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPicture(picture) }
                    // The press feedback is recorded into the board rather than washed over it:
                    // a tint laid on top would sit over the cross and the grid lines of the cell
                    // the player is pressing. Only the cross a drag is carrying is drawn here,
                    // because its opacity follows the finger and a picture is recorded once.
                    val held = pressed
                    if (held != null && held in states.indices && longPressed && crossAlpha > 0f) {
                        drawCross(
                            index = held,
                            width = width,
                            margin = rowMargin to columnMargin,
                            colour = alert,
                            alpha = crossAlpha,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Returns the width of the row-clue margin, in board units. */
private fun rowMarginOf(clues: List<ClueLine>): Float {
    val longest = clues.maxOfOrNull { it.values.size } ?: 1
    return longest * ROW_CLUE_ADVANCE + CLUE_PADDING
}

/** Returns the height of the column-clue margin, in board units. */
private fun columnMarginOf(clues: List<ClueLine>): Float {
    val longest = clues.maxOfOrNull { it.values.size } ?: 1
    return longest * COLUMN_CLUE_ADVANCE + COLUMN_CLUE_PADDING
}

/**
 * Records a nonogram into one picture, in board units.
 *
 * # The order of the layers
 *
 * The background, then the filled cells, the grid, the clue numbers and finally the crosses.
 * The grid is drawn over the filled cells on purpose: a run of filled cells would otherwise be
 * one block, and a nonogram is counted in cells. The segment of a line that falls inside a
 * filled cell is drawn in the board's own colour, which is what makes the cell visible against
 * its neighbours; the segment that falls in an empty cell is drawn in the outline colour.
 *
 * # Why the numbers are placed from their ink
 *
 * [BoardText.centre] measures the glyph rather than its advance, for the reason written there:
 * a digit placed on its advance sits high and wide of its cell. The clue numbers use the same
 * placement as the board's other numbers, so all four boards agree.
 *
 * # The cell being pressed
 *
 * [lit] is the cell a finger is on. It is painted as that cell's own background rather than
 * washed over the finished picture: a tint on top would cover the grid line and the cross of the
 * very cell it is answering for, and the player could no longer see what they were about to
 * change.
 */
@Suppress("LongParameterList")
private fun recordNonogram(
    width: Int,
    height: Int,
    states: List<Int>,
    rowClues: List<ClueLine>,
    columnClues: List<ClueLine>,
    brokenRows: Set<Int>,
    brokenColumns: Set<Int>,
    lit: Int?,
    litAlpha: Float,
    litFill: Color,
    boardBackground: Color,
    ink: Color,
    outline: Color,
    alert: Color,
    typeface: Typeface,
): Picture {
    val rowMargin = rowMarginOf(rowClues)
    val columnMargin = columnMarginOf(columnClues)
    val picture = Picture()
    val canvas = picture.beginRecording((width + rowMargin).toInt() + 1, (height + columnMargin).toInt() + 1)

    val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    canvas.drawRect(0f, 0f, width + rowMargin, height + columnMargin, fill.apply { color = boardBackground.toArgb() })

    // The filled cells, one pass.
    fill.color = ink.toArgb()
    for (index in states.indices) {
        if (states[index] != NonogramCell.FILLED) {
            continue
        }
        val column = (index % width) + rowMargin
        val row = (index / width) + columnMargin
        canvas.drawRect(column, row, column + 1f, row + 1f, fill)
    }

    // The cell a finger is on, painted over its own square before the grid goes down: the grid
    // line and the cross of that cell are then drawn on top of the tint rather than under it.
    // A filled cell keeps its ink and the tint is skipped there, because a washed ink cell reads
    // as a different state rather than as a press.
    if (lit != null && lit in states.indices && states[lit] != NonogramCell.FILLED) {
        val column = (lit % width) + rowMargin
        val row = (lit / width) + columnMargin
        canvas.drawRect(
            column,
            row,
            column + 1f,
            row + 1f,
            fill.apply { color = litFill.copy(alpha = litAlpha).toArgb() },
        )
    }

    val crossPaint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = alert.toArgb()
        }
    for (index in states.indices) {
        if (states[index] == NonogramCell.MARKED) {
            drawCrossOn(canvas, crossPaint, index, width, rowMargin, columnMargin)
        }
    }

    // The grid, in two colours: the segment inside a filled cell is the board's colour and the
    // segment outside one is the outline. Drawing the whole grid twice and clipping each pass to
    // the filled cells would be the same picture with more work; a segment is one cell long, so
    // it is coloured by the cell it lies in.
    val thin = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = THIN_LINE }
    val thick = Paint(thin).apply { strokeWidth = THICK_LINE }
    for (column in 0..width) {
        val x = column + rowMargin
        val paint = if (column % 5 == 0) thick else thin
        for (row in 0 until height) {
            val y = row + columnMargin
            val filledLeft = column > 0 && stateAt(states, width, row, column - 1) == NonogramCell.FILLED
            val filledRight = column < width && stateAt(states, width, row, column) == NonogramCell.FILLED
            paint.color = if (filledLeft || filledRight) boardBackground.toArgb() else outline.toArgb()
            canvas.drawLine(x, y, x, y + 1f, paint)
        }
    }
    for (row in 0..height) {
        val y = row + columnMargin
        val paint = if (row % 5 == 0) thick else thin
        for (column in 0 until width) {
            val x = column + rowMargin
            val filledAbove = row > 0 && stateAt(states, width, row - 1, column) == NonogramCell.FILLED
            val filledBelow = row < height && stateAt(states, width, row, column) == NonogramCell.FILLED
            paint.color = if (filledAbove || filledBelow) boardBackground.toArgb() else outline.toArgb()
            canvas.drawLine(x, y, x + 1f, y, paint)
        }
    }

    // The boundary between the clue margins and the board, which is what tells a player that the
    // numbers and the cells are two different things.
    thick.color = outline.toArgb()
    canvas.drawLine(rowMargin, columnMargin, rowMargin, height + columnMargin, thick)
    canvas.drawLine(rowMargin, columnMargin, width + rowMargin, columnMargin, thick)

    // The clue numbers. A broken line's numbers are the warning red, which is the only signal the
    // board gives about a mistake.
    val number = boardNumberPaint(typeface, CLUE_TEXT_SIZE)
    for (row in 0 until height) {
        val clue = rowClues.getOrNull(row)?.values ?: continue
        number.color = if (row in brokenRows) alert.toArgb() else ink.toArgb()
        // The row's numbers are right-aligned against the board, so the last one of every clue
        // sits in the same column and a long clue grows to the left.
        var x = rowMargin - CLUE_PADDING
        for (value in clue.asReversed()) {
            x -= ROW_CLUE_ADVANCE
            drawCentred(canvas, number, value.toString(), x + ROW_CLUE_ADVANCE / 2f, row + columnMargin + 0.5f)
        }
    }
    for (column in 0 until width) {
        val clue = columnClues.getOrNull(column)?.values ?: continue
        number.color = if (column in brokenColumns) alert.toArgb() else ink.toArgb()
        // The column's numbers are bottom-aligned against the board for the same reason.
        var y = columnMargin - COLUMN_CLUE_PADDING
        for (value in clue.asReversed()) {
            y -= COLUMN_CLUE_ADVANCE
            drawCentred(canvas, number, value.toString(), column + rowMargin + 0.5f, y + COLUMN_CLUE_ADVANCE / 2f)
        }
    }

    picture.endRecording()
    return picture
}

/** Returns the state of one cell, or the empty state when the index is off the board. */
private fun stateAt(
    states: List<Int>,
    width: Int,
    row: Int,
    column: Int,
): Int = states.getOrElse(row * width + column) { NonogramCell.EMPTY }

/**
 * Draws a digit so that its ink is centred on a point, in board units.
 *
 * The pen is placed on the baseline, so the offset from [BoardText.centre] is added to the
 * middle of the cell rather than subtracted: the offset's `y` is how far below the baseline's
 * own top the ink's middle is.
 */
private fun drawCentred(
    canvas: android.graphics.Canvas,
    paint: Paint,
    text: String,
    centreX: Float,
    centreY: Float,
) {
    val offset = BoardText.centre(text, paint)
    canvas.drawText(text, centreX + offset.x, centreY + offset.y, paint)
}

/** Draws the cross of a marked cell inside its square. */
private fun drawCrossOn(
    canvas: android.graphics.Canvas,
    paint: Paint,
    index: Int,
    width: Int,
    rowMargin: Float,
    columnMargin: Float,
) {
    val left = (index % width) + rowMargin
    val top = (index / width) + columnMargin
    drawCrossIn(canvas, paint, left, top)
}

/** Draws the two strokes of a cross inside the square whose top-left corner is given. */
private fun drawCrossIn(
    canvas: android.graphics.Canvas,
    paint: Paint,
    left: Float,
    top: Float,
) {
    val inset = (1f - CROSS_SIZE) / 2f
    val start = left + inset
    val end = left + 1f - inset
    val topStart = top + inset
    val bottomEnd = top + 1f - inset
    val stroke =
        Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = CROSS_LINE
            strokeCap = Paint.Cap.ROUND
        }
    canvas.drawLine(start, topStart, end, bottomEnd, stroke)
    canvas.drawLine(end, topStart, start, bottomEnd, stroke)
}

/** Draws the cross a drag is carrying, at the opacity the drag has reached. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCross(
    index: Int,
    width: Int,
    margin: Pair<Float, Float>,
    colour: Color,
    alpha: Float,
) {
    val left = (index % width) + margin.first
    val top = (index / width) + margin.second
    val inset = (1f - CROSS_SIZE) / 2f
    val stroke = CROSS_LINE
    // Two lines drawn by the Compose canvas rather than by the picture: the opacity changes with
    // the finger, and a picture is recorded once per position.
    drawLine(
        color = colour.copy(alpha = alpha),
        start = Offset(left + inset, top + inset),
        end = Offset(left + 1f - inset, top + 1f - inset),
        strokeWidth = stroke,
    )
    drawLine(
        color = colour.copy(alpha = alpha),
        start = Offset(left + 1f - inset, top + inset),
        end = Offset(left + inset, top + 1f - inset),
        strokeWidth = stroke,
    )
}
