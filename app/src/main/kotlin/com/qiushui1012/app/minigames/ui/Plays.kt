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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qiushui1012.app.minigames.AlertDark
import com.qiushui1012.app.minigames.AlertLight
import com.qiushui1012.app.minigames.EnteredDigit
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.Water
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.rules.PipesBoard
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.PipesSide
import com.qiushui1012.app.minigames.rules.SudokuBoard
import android.graphics.Paint

/** The size a sudoku digit is drawn at, as a fraction of a cell. */
private const val DIGIT_TEXT_SIZE = 0.62f

/** The size a sudoku note is drawn at, as a fraction of a cell. */
private const val NOTE_TEXT_SIZE = 0.22f

/**
 * The colour of the pipes board's lines, frame and cells alike.
 *
 * One colour for both is what makes the board read as a grid: the frame is the same line as the
 * cells it closes, not a heavier border around them. It is a pale blue rather than the theme's
 * ink because a pipe's walls are already ink, and a grid in the same colour would merge with the
 * pieces it separates.
 */
private val BoardLine = Color(0xFFD0E2E7)

/**
 * The width of a board line, in board units.
 *
 * The frame and the cell dividers share it. At the widths a phone draws a board, a frame heavier
 * than the cells would look like a picture frame; the same weight makes the grid one thing.
 */
private const val BOARD_LINE = 0.02f

/** The width of a box divider, as a fraction of a cell. */
private const val BOX_DIVIDER = 0.030f

/** The width of a cell divider, as a fraction of a cell. */
private const val CELL_DIVIDER = 0.008f

/**
 * The pipes board, drawn and played.
 *
 * # What a player sees
 *
 * A pipe is two walls with a lumen between them. The walls are the theme's foreground, the
 * lumen is the board's own background when no water is flowing through it and blue when
 * there is. A loop changes only the walls, to red: the lumen keeps whatever it held, so a
 * loop under water still shows its water and a loop on dry board shows none.
 *
 * Drawing the lumen over the walls rather than under them is what makes one stroke do for
 * both: the wall colour shows as the two edges the lumen does not cover.
 *
 * # A tap and a press
 *
 * A tap turns a piece a quarter turn clockwise and a long press turns it back, which is the
 * pair a mouse offers with its left button and its Ctrl key. Neither waits for the other: the
 * long press is handled by the same gesture detector, which reports it instead of the tap.
 */
@Composable
fun PipesPlay(
    board: PipesBoard,
    settings: Settings,
    onSolved: () -> Unit,
) {
    // The pump stands still, so its cell is read once rather than on every frame.
    val waterStart = remember(board) { board.waterStart() }
    // The drawings live in `assets/`, and reading them needs the context that holds them.
    val context = LocalContext.current
    var loopCells by remember { mutableStateOf(board.loopCells().toSet()) }
    var waterCells by remember { mutableStateOf(board.waterCells().toSet()) }
    var solved by remember { mutableStateOf(board.isSolved()) }
    // Every cell's piece, read in one call rather than one per cell: the drawing would otherwise
    // cross the language boundary 1225 times for a single frame of the largest board.
    var pieces by remember(board) { mutableStateOf(board.pieces()) }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wall = MaterialTheme.colorScheme.onBackground
    val loopWall = if (dark) AlertDark else AlertLight
    val water = Water
    // An empty pipe shows the board rather than a lighter panel: a white lumen would put a
    // second colour into every cell and read as a highlight instead of as an empty pipe.
    val boardBackground = MaterialTheme.colorScheme.background
    val size = board.size

    LaunchedEffect(solved) {
        if (solved) {
            onSolved()
        }
    }

    fun afterMove() {
        loopCells = board.loopCells().toSet()
        waterCells = board.waterCells().toSet()
        solved = board.isSolved()
        pieces = board.pieces()
    }

    // The board is recorded into a picture once per position rather than redrawn piece by piece
    // every frame. A picture is a list of drawing commands, not pixels, so it stays sharp at any
    // zoom the player pinches to.
    val picture =
        remember(context, size, pieces, loopCells, waterCells, waterStart, wall, loopWall, water, boardBackground) {
            recordPipes(
                context = context,
                size = size,
                pieces = pieces,
                loopCells = loopCells,
                waterCells = waterCells,
                waterStart = waterStart,
                wall = wall,
                loopWall = loopWall,
                water = water,
                boardBackground = boardBackground,
                gridLine = BoardLine,
                gridWidth = BOARD_LINE,
            )
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(Space.m)) {
            ZoomableBoard(
                boardWidth = size.toFloat(),
                boardHeight = size.toFloat(),
                minCellDp = 40f,
                columns = size,
                rows = size,
                onTap = { row, column ->
                    board.turnClockwise(row * size + column)
                    afterMove()
                },
                onLongPress = { row, column ->
                    board.turnAntiClockwise(row * size + column)
                    afterMove()
                },
                content = {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPicture(picture)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The sudoku board, drawn and played.
 *
 * # What a player sees
 *
 * A given is drawn at full weight and a digit the player entered at a lighter one, which is
 * the difference between a number that is fixed and one that can be taken back. The selected
 * cell, its row, its column and its box are shaded behind the digits.
 *
 * A digit that repeats in a unit is drawn in the error colour. That is the whole of the
 * feedback: the rules compare the board against itself rather than against the solution, so a
 * wrong digit that conflicts with nothing yet says nothing, and the puzzle stays a puzzle.
 */
@Composable
fun SudokuPlay(
    board: SudokuBoard,
    settings: Settings,
    onSolved: () -> Unit,
) {
    val context = LocalContext.current
    // One paint for the whole board, set per digit: the numbers are drawn by the board rather
    // than by a text measurer, for the reason [BoardText] gives.
    val digitPaint =
        remember(context) {
            boardNumberPaint(BoardText.typeface(context), DIGIT_TEXT_SIZE)
        }
    var selected by remember { mutableStateOf<Int?>(null) }
    var notesMode by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var conflicts by remember { mutableStateOf(board.conflictCells().toSet()) }
    var solved by remember { mutableStateOf(board.isSolved()) }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ink = MaterialTheme.colorScheme.onBackground
    val errorColor = if (dark) AlertDark else AlertLight
    // The selected cell is the ink at low opacity rather than the primary container: the
    // container is the same tone as the row and column shading, which would leave the one
    // cell the player is working in indistinguishable from its neighbours.
    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val peerColor = MaterialTheme.colorScheme.surfaceVariant
    val givenColor = MaterialTheme.colorScheme.onBackground
    val enteredColor = EnteredDigit
    val noteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val boardBackground = MaterialTheme.colorScheme.surface
    val divider = MaterialTheme.colorScheme.outline

    LaunchedEffect(solved) {
        if (solved) {
            onSolved()
        }
    }

    fun afterMove() {
        revision += 1
        conflicts = board.conflictCells().toSet()
        solved = board.isSolved()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(Space.m)) {
            ZoomableBoard(
                boardWidth = 9f,
                boardHeight = 9f,
                minCellDp = 34f,
                columns = 9,
                rows = 9,
                onTap = { row, column ->
                    selected = row * 9 + column
                },
                content = { scale ->
                    @Suppress("UNUSED_EXPRESSION")
                    revision
                    val chosen = selected
                    // One board unit is `scale` screen pixels, which is the size the shading is
                    // drawn at. The digits are drawn in board units rather than in screen
                    // pixels: a paint sized in board units keeps the glyph at the same fraction
                    // of its cell at every zoom, and [BoardText.centre] measures the ink at a
                    // hundred times that size, so the placement's rounding stays below a pixel.
                    val pixelsPerUnit = scale
                    val digits = Paint(digitPaint)
                    // The board's own background is laid down once, before the shading: the
                    // shading is a tint over the board, so a fill on top of it would erase it
                    // and the selected cell would look exactly like every other one.
                    drawRect(color = boardBackground, topLeft = Offset.Zero, size = Size(9f, 9f))
                    for (row in 0 until 9) {
                        for (column in 0 until 9) {
                            val index = row * 9 + column
                            val shade =
                                when {
                                    chosen == index -> selectedColor
                                    chosen != null && sharesUnit(chosen, index) -> peerColor
                                    else -> Color.Transparent
                                }
                            if (shade != Color.Transparent) {
                                drawRect(
                                    color = shade,
                                    topLeft = Offset(column.toFloat(), row.toFloat()),
                                    size = Size(1f, 1f),
                                )
                            }
                        }
                    }
                    drawGrid(divider = divider)
                    for (row in 0 until 9) {
                        for (column in 0 until 9) {
                            drawCellText(
                                paint = digits,
                                row = row,
                                column = column,
                                board = board,
                                errors = conflicts,
                                ink = ink,
                                errorColor = errorColor,
                                givenColor = givenColor,
                                enteredColor = enteredColor,
                                noteColor = noteColor,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        SudokuControls(
            notesMode = notesMode,
            onToggleNotes = { notesMode = !notesMode },
            onDigit = { digit ->
                val index = selected
                if (index != null) {
                    if (notesMode) {
                        board.toggleNote(index, digit)
                    } else {
                        board.enter(index, digit)
                    }
                    afterMove()
                }
            },
            onErase = {
                selected?.let {
                    board.clear(it)
                    afterMove()
                }
            },
        )
    }
}

/** Whether two cells share a row, a column or a box. */
private fun sharesUnit(first: Int, second: Int): Boolean {
    if (first == second) {
        return false
    }
    val firstRow = first / 9
    val firstColumn = first % 9
    val secondRow = second / 9
    val secondColumn = second % 9
    return firstRow == secondRow || firstColumn == secondColumn ||
        (firstRow / 3 == secondRow / 3 && firstColumn / 3 == secondColumn / 3)
}

/** Draws the lines that separate cells, with the boxes drawn heavier. */
private fun DrawScope.drawGrid(divider: Color) {
    for (line in 0..9) {
        val heavy = line % 3 == 0
        val width = if (heavy) BOX_DIVIDER else CELL_DIVIDER
        drawLine(
            color = divider,
            start = Offset(line.toFloat(), 0f),
            end = Offset(line.toFloat(), 9f),
            strokeWidth = width,
        )
        drawLine(
            color = divider,
            start = Offset(0f, line.toFloat()),
            end = Offset(9f, line.toFloat()),
            strokeWidth = width,
        )
    }
}

/** Draws one cell's digit or, when it is empty, its notes. */
private fun DrawScope.drawCellText(
    paint: android.graphics.Paint,
    row: Int,
    column: Int,
    board: SudokuBoard,
    errors: Set<Int>,
    ink: Color,
    errorColor: Color,
    givenColor: Color,
    enteredColor: Color,
    noteColor: Color,
) {
    val index = row * 9 + column
    val digit = board.digit(index)

    if (digit != 0) {
        val color =
            when {
                index in errors -> errorColor
                board.isGiven(index) -> givenColor
                else -> enteredColor
            }
        // One weight for every digit, given or entered. A given is told apart by its colour
        // alone: a second weight made a digit the player typed read as something else.
        paint.textSize = DIGIT_TEXT_SIZE
        paint.color = color.toArgb()
        drawCentredText(paint, digit.toString(), column + 0.5f, row + 0.5f)
        return
    }

    val notes = board.notes(index)
    if (notes == 0) {
        return
    }
    paint.textSize = NOTE_TEXT_SIZE
    paint.color = noteColor.toArgb()
    for (noteDigit in 1..9) {
        if (notes and (1 shl noteDigit) == 0) {
            continue
        }
        val noteRow = (noteDigit - 1) / 3
        val noteColumn = (noteDigit - 1) % 3
        drawCentredText(
            paint,
            noteDigit.toString(),
            column + (noteColumn + 0.5f) / 3f,
            row + (noteRow + 0.5f) / 3f,
        )
    }
}

/**
 * Draws text so that its ink is centred on a point, in board units.
 *
 * The pen is placed on the baseline, so the offset from [BoardText.centre] is added to the
 * point rather than subtracted: the offset's `y` is how far below the point the baseline has to
 * sit for the ink's middle to land on it.
 */
private fun DrawScope.drawCentredText(
    paint: android.graphics.Paint,
    text: String,
    centreX: Float,
    centreY: Float,
) {
    val offset = BoardText.centre(text, paint)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(text, centreX + offset.x, centreY + offset.y, paint)
    }
}

/** The digits and the note toggle below a sudoku board. */
@Composable
private fun SudokuControls(
    notesMode: Boolean,
    onToggleNotes: () -> Unit,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
) {
    // A rounded rectangle rather than the pill a Material button is by default: the controls are
    // a keypad under a square board, and a row of pills read as a row of unrelated buttons.
    val key = MaterialTheme.shapes.small
    val outline = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The note toggle says whether notes are on by being filled when they are and
            // outlined when they are not. This is issue #1: a button whose only state was the
            // label left the player unable to tell which mode the next digit would go in.
            Button(
                onClick = onToggleNotes,
                shape = key,
                border = if (notesMode) null else BorderStroke(1.dp, outline),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (notesMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (notesMode) MaterialTheme.colorScheme.onPrimary else outline,
                    ),
                elevation = null,
            ) {
                Text(stringResource(R.string.sudoku_notes))
            }
            // Erase sits at the far end of the row, away from the note toggle: the two are not a
            // pair, and a control that clears a cell should not sit next to the one a thumb
            // reaches for while entering digits.
            Box(Modifier.weight(1f))
            OutlinedButton(onClick = onErase, shape = key) {
                Text(stringResource(R.string.sudoku_erase))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            for (digit in 1..9) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { onDigit(digit) },
                        shape = key,
                        contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.s),
                    ) {
                        Text(digit.toString())
                    }
                }
            }
        }
    }
}
