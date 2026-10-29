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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.qiushui1012.app.minigames.AlertDark
import com.qiushui1012.app.minigames.AlertLight
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.WaterDark
import com.qiushui1012.app.minigames.WaterLight
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.rules.PipesBoard
import com.qiushui1012.app.minigames.rules.PipesLayout
import com.qiushui1012.app.minigames.rules.PipesSide
import com.qiushui1012.app.minigames.rules.SudokuBoard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * The density the board's text is measured against.
 *
 * The board is drawn in its own units and scaled by the canvas transform, so a size in sp`n * has to mean a size in board units. A density of one makes it exactly that; the window's
 * own density would turn the digit into something hundreds of cells wide.
 */
private val BoardTextDensity = Density(1f, 1f)

/** The width of the board's frame, in board units. */
private const val BOARD_FRAME = 0.03f

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
    var revision by remember { mutableIntStateOf(0) }
    // The pump stands still, so its cell is read once rather than on every frame.
    val waterStart = remember(board) { board.waterStart() }
    // The drawings live in `assets/`, and reading them needs the context that holds them.
    val context = LocalContext.current
    var loopCells by remember { mutableStateOf(board.loopCells().toSet()) }
    var waterCells by remember { mutableStateOf(board.waterCells().toSet()) }
    var solved by remember { mutableStateOf(board.isSolved()) }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wall = MaterialTheme.colorScheme.onBackground
    val loopWall = if (dark) AlertDark else AlertLight
    val water = if (dark) WaterDark else WaterLight
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
        revision += 1
        loopCells = board.loopCells().toSet()
        waterCells = board.waterCells().toSet()
        solved = board.isSolved()
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
                content = { _ ->
                    // Reading `revision` is what redraws the board after a move: the pieces
                    // themselves are read from the rules object on every frame, and a frame
                    // that did not read the revision would be cached.
                    @Suppress("UNUSED_EXPRESSION")
                    revision
                    // The frame is drawn first, so a piece's rounded ends sit against a line
                    // that closes the board rather than fading into the page.
                    drawRect(
                        color = wall,
                        topLeft = Offset.Zero,
                        size = Size(size.toFloat(), size.toFloat()),
                        style = Stroke(width = BOARD_FRAME),
                    )
                    for (row in 0 until size) {
                        for (column in 0 until size) {
                            val index = row * size + column
                            val piece = board.piece(index)
                            val placement = pipeShapeOf(piece) ?: continue
                            val onLoop = index in loopCells
                            val wet = index in waterCells
                            drawPipe(
                                context = context,
                                cellX = column.toFloat(),
                                cellY = row.toFloat(),
                                shape = placement.first,
                                turns = placement.second,
                                wall = if (onLoop) loopWall else wall,
                                lumen = if (wet) water else boardBackground,
                            )
                        }
                    }
                    // The pump goes over the pieces: it marks the cell the water comes from,
                    // and a piece that covered it would hide the one cell the board is about.
                    //
                    // Its body keeps the colour its file declares, because a pump is a fitting
                    // rather than a pipe and should not change with the theme. The drop on it is
                    // painted in the water colour: the drop is what the pump feeds.
                    drawPump(
                        context = context,
                        cellX = (waterStart % size).toFloat(),
                        cellY = (waterStart / size).toFloat(),
                        mark = water,
                    )
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
    val textMeasurer = rememberTextMeasurer()
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
    val enteredColor = MaterialTheme.colorScheme.primary
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
                content = { _ ->
                    @Suppress("UNUSED_EXPRESSION")
                    revision
                    val chosen = selected
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
                                measurer = textMeasurer,
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
    measurer: TextMeasurer,
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
    val cellSize = 1f
    val centre = Offset(column + 0.5f, row + 0.5f)

    if (digit != 0) {
        val color =
            when {
                index in errors -> errorColor
                board.isGiven(index) -> givenColor
                else -> enteredColor
            }
        val style =
            TextStyle(
                color = color,
                // The board is drawn in its own units and scaled by the canvas transform, so
                // the text has to be measured in those units too. Measuring against a density
                // of one makes one `sp` one board unit, which is one cell; the default density
                // would make the digit hundreds of cells wide, because a board unit is not a
                // pixel.
                fontSize = (cellSize * 0.62f).sp,
                fontWeight = if (board.isGiven(index)) FontWeight.SemiBold else FontWeight.Normal,
            )
        val layout = measurer.measure(digit.toString(), style, density = BoardTextDensity)
        // The board is drawn in board units, so the text is measured at the size it would
        // have in a cell one unit wide and then placed at the cell's centre. The scale of the
        // whole board is applied by the caller's transform.
        drawText(
            textLayoutResult = layout,
            topLeft =
                Offset(
                    centre.x - layout.size.width / 2f,
                    centre.y - layout.size.height / 2f,
                ),
        )
        return
    }

    val notes = board.notes(index)
    if (notes == 0) {
        return
    }
    val noteStyle = TextStyle(color = noteColor, fontSize = (cellSize * 0.22f).sp)
    for (noteDigit in 1..9) {
        if (notes and (1 shl noteDigit) == 0) {
            continue
        }
        val noteRow = (noteDigit - 1) / 3
        val noteColumn = (noteDigit - 1) % 3
        val layout = measurer.measure(noteDigit.toString(), noteStyle, density = BoardTextDensity)
        drawText(
            textLayoutResult = layout,
            topLeft =
                Offset(
                    column + (noteColumn + 0.5f) * (cellSize / 3f) - layout.size.width / 2f,
                    row + (noteRow + 0.5f) * (cellSize / 3f) - layout.size.height / 2f,
                ),
        )
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Button(onClick = onToggleNotes) {
                Text(stringResource(R.string.sudoku_notes))
            }
            Button(onClick = onErase) {
                Text(stringResource(R.string.sudoku_erase))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            for (digit in 1..9) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Button(onClick = { onDigit(digit) }) {
                        Text(digit.toString())
                    }
                }
            }
        }
    }
}
