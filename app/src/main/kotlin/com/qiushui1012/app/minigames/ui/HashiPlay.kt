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
import android.graphics.RectF
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.qiushui1012.app.minigames.SuccessDark
import com.qiushui1012.app.minigames.SuccessLight
import com.qiushui1012.app.minigames.rules.HashiBoard
import com.qiushui1012.app.minigames.rules.HashiBridge
import com.qiushui1012.app.minigames.rules.HashiIsland
import kotlin.math.abs

/** The side of an island, as a fraction of the points between them. */
private const val ISLAND_SIZE = 0.70f

/** The corner radius of an island, as a fraction of the points between them. */
private const val ISLAND_CORNER = 0.16f

/** The width of an island's outline, as a fraction of the points between them. */
private const val ISLAND_OUTLINE = 0.07f

/** The width of the grid line between two points, as a fraction of the points between them. */
private const val GRID_LINE = 0.03f

/** The width of a bridge, as a fraction of the points between them. */
private const val BRIDGE_LINE = 0.055f

/** The distance between the two lines of a double bridge, as a fraction of the points. */
private const val BRIDGE_GAP = 0.14f

/** The width of an island's clue number, as a fraction of the points between them. */
private const val ISLAND_TEXT_SIZE = 0.50f

/** The horizontal span of the solved mark, as a fraction of the points between them. */
private const val MARK_SPAN = 0.70f

/** The width of the solved mark, as a fraction of the points between them. */
private const val MARK_LINE = 0.06f

/** The angle of the solved mark from the horizontal, in radians. */
private const val MARK_ANGLE = 0.5236f

/** How far a drag has to travel before it names a second island, in points. */
private const val CONNECT_DISTANCE = 0.75f

/** The transparency of the preview bridge a drag carries. */
private const val PREVIEW_ALPHA = 0.5f

/** The colour an island is filled with while it is pressed or connected. */
private val Highlight = Color(0xFF7ED3C0)

/**
 * The hashi board, drawn and played.
 *
 * # What a player sees
 *
 * The points are joined by faint grid lines, the islands are rounded squares on them and the
 * bridges run between the centres under the islands, so a bridge that reaches an island ends
 * under its own square rather than against its edge. A single bridge is one line and a double
 * one is two parallel lines.
 *
 * # What the gestures mean
 *
 * A tap marks an island with a diagonal stroke, which is the player's own note that the island
 * is finished; the note is kept in the save and is not part of the solved check. A long press
 * that stays put does nothing at all, which is deliberate: the gesture that connects two islands
 * is a long press and a drag, and a press that never moved is the gesture being abandoned. A
 * long press that moves past [CONNECT_DISTANCE] in one of the four directions connects its own
 * island to the nearest island that way.
 *
 * # Why the highlight is not in the picture
 *
 * The board is recorded into a [Picture] once per position, but the highlight and the preview
 * bridge change with the finger. They are drawn above the picture instead, for the reason the
 * press feedback of minesweeper is: a picture recorded per pixel of travel would undo the reason
 * the board is recorded at all.
 */
@Composable
fun HashiPlay(
    board: HashiBoard,
    settings: com.qiushui1012.app.minigames.data.Settings,
    onSolved: () -> Unit,
) {
    val context = LocalContext.current
    val typeface = remember(context) { BoardText.typeface(context) }
    val width = board.width
    val height = board.height
    var islands by remember(board) { mutableStateOf(board.islands()) }
    var bridges by remember(board) { mutableStateOf(board.bridges()) }
    var solved by remember(board) { mutableStateOf(board.isSolved()) }
    // The islands that can no longer reach their clue: one carrying more bridges than it allows,
    // or one whose remaining directions cannot hold the bridges it still needs. The screen paints
    // them red, which is the only signal it gives about a mistake.
    var stranded by remember(board) { mutableStateOf(board.strandedIslands().toSet()) }
    // The island a finger is on, the island a drag has reached, and where the finger is in board
    // units. The three are drawn above the picture, so they are state rather than locals.
    var pressed by remember(board) { mutableStateOf<Int?>(null) }
    var target by remember(board) { mutableStateOf<Int?>(null) }
    var dragging by remember(board) { mutableStateOf(false) }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val boardBackground = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val outline = MaterialTheme.colorScheme.outlineVariant
    val islandFill = MaterialTheme.colorScheme.surface
    val success = if (dark) SuccessDark else SuccessLight
    val alert = if (dark) AlertDark else AlertLight

    LaunchedEffect(solved) {
        if (solved) {
            onSolved()
        }
    }

    /** Reads the board afresh, which is what a move has changed. */
    fun refresh() {
        islands = board.islands()
        bridges = board.bridges()
        solved = board.isSolved()
        stranded = board.strandedIslands().toSet()
    }

    /**
     * Returns the island a board position is nearest to, within half a point of its centre.
     *
     * The walk is over every island rather than over a grid lookup: the largest board holds a
     * few hundred islands, and one comparison per island on a gesture is cheaper than keeping a
     * second index of them in step with the board. The position is in the lattice the drawing
     * uses, where an island's centre is one unit in from the edge.
     */
    fun islandAt(x: Float, y: Float): Int? =
        islands
            .withIndex()
            .filter { (_, island) ->
                abs(island.column + 0.5f - x) <= 0.5f && abs(island.row + 0.5f - y) <= 0.5f
            }
            .minByOrNull { (_, island) ->
                val dx = island.column + 0.5f - x
                val dy = island.row + 0.5f - y
                dx * dx + dy * dy
            }?.index

    /**
     * Returns the island a drag from another one names, or null when it names none.
     *
     * The direction is taken from the displacement rather than from the island the finger is
     * over: a drag that has gone far enough in one direction connects to the nearest island that
     * way, whatever the finger is over at the moment it is released. The position is in the
     * lattice the drawing uses, where an island's centre is one unit in from the edge.
     */
    fun broughtTo(start: Int, x: Float, y: Float): Int? {
        val origin = islands.getOrNull(start) ?: return null
        val dx = x - (origin.column + 0.5f)
        val dy = y - (origin.row + 0.5f)
        if (abs(dx) < CONNECT_DISTANCE && abs(dy) < CONNECT_DISTANCE) {
            return null
        }
        val horizontal = abs(dx) >= abs(dy)
        return islands
            .withIndex()
            .filter { (index, island) ->
                if (index == start) {
                    return@filter false
                }
                if (horizontal) {
                    island.row == origin.row && (island.column - origin.column) * dx > 0f
                } else {
                    island.column == origin.column && (island.row - origin.row) * dy > 0f
                }
            }
            .minByOrNull { (_, island) ->
                if (horizontal) abs(island.column - origin.column) else abs(island.row - origin.row)
            }?.index
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (solved) {
            Text(
                stringResource(R.string.hashi_won),
                style = MaterialTheme.typography.titleLarge,
                color = success,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            )
        }
        val description = stringResource(R.string.cd_hashi_board)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(Space.m)
                    .semantics { contentDescription = description },
        ) {
            // The islands that are lit: the one a finger is on and, once a drag has named a
            // second one, that one too. The set is part of the picture's key rather than a layer
            // above it, so the highlight is the island's own fill and the clue and the outline
            // stay on top of it.
            val litIsland = pressed
            val litTarget = if (dragging) target else null
            val picture =
                remember(
                    width,
                    height,
                    islands,
                    bridges,
                    litIsland,
                    litTarget,
                    stranded,
                    boardBackground,
                    ink,
                    outline,
                    islandFill,
                    success,
                    alert,
                    typeface,
                ) {
                    recordHashi(
                        width = width,
                        height = height,
                        islands = islands,
                        bridges = bridges,
                        lit = setOfNotNull(litIsland, litTarget),
                        stranded = stranded,
                        boardBackground = boardBackground,
                        ink = ink,
                        grid = outline,
                        islandFill = islandFill,
                        litFill = Highlight,
                        success = success,
                        alert = alert,
                        typeface = typeface,
                    )
                }
            ZoomableBoard(
                // Each lattice point is the centre of one square, so the board is `width` units
                // across and the first point's centre is half a unit in from the edge. The
                // lattice lines then run half a unit past the outermost points on every side.
                boardWidth = width.toFloat(),
                boardHeight = height.toFloat(),
                minCellDp = 28f,
                columns = width,
                rows = height,
                cellWidth = 1f,
                cellHeight = 1f,
                cellOrigin = Offset(0.5f, 0.5f),
                onTap = { row, column ->
                    // A tap toggles the player's own mark, which is a note and not a rule: the
                    // board answers whether the island changed, and the solved check ignores it.
                    islandAt(column + 0.5f, row + 0.5f)?.let { index ->
                        if (board.toggleMark(index)) {
                            refresh()
                        }
                    }
                },
                onPressChanged = { row, column, _, released ->
                    if (released) {
                        val start = pressed
                        val reached = target
                        // The drag state is read before it is cleared: a press that never moved
                        // does nothing at all, which is what makes the long press and the drag
                        // one gesture rather than two.
                        val dragged = dragging
                        pressed = null
                        target = null
                        dragging = false
                        if (start != null && reached != null && dragged) {
                            if (board.connect(start, reached)) {
                                refresh()
                            }
                        }
                    } else {
                        pressed = row?.let { islandAt(column + 0.5f, it + 0.5f) }
                        target = null
                        dragging = false
                    }
                },
                onDragPosition = { x, y ->
                    val start = pressed ?: return@ZoomableBoard
                    val reached = broughtTo(start, x, y)
                    if (reached != target) {
                        target = reached
                    }
                    dragging = dragging || reached != null
                },
                content = {
                    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPicture(picture) }
                    // The preview bridge is drawn above the recorded board because it follows
                    // the finger. The highlight is not: it is the island's own fill colour, and a
                    // wash drawn over the picture would sit on top of the clue and the outline the
                    // island is read by. The picture is recorded again with the highlighted
                    // islands' fill swapped, which is what keeps the number and the border
                    // legible while the island is lit.
                    val start = pressed
                    val reached = target
                    if (start != null && dragging && reached != null) {
                        drawPreviewBridge(islands[start], islands[reached])
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Draws the translucent bridge a drag is proposing, between two islands. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewBridge(
    from: HashiIsland,
    to: HashiIsland,
) {
    val startX = from.column + 0.5f
    val startY = from.row + 0.5f
    val endX = to.column + 0.5f
    val endY = to.row + 0.5f
    // A new connection is one bridge, and the preview shows one line rather than guessing
    // whether the player means a double: the count is the player's next press.
    drawLine(
        color = Highlight.copy(alpha = PREVIEW_ALPHA),
        start = Offset(startX, startY),
        end = Offset(endX, endY),
        strokeWidth = BRIDGE_LINE,
    )
}

/**
 * Records a hashi board into one picture, in board units.
 *
 * The layers are the grid, the bridges, the islands, their numbers and their marks, which is the
 * order a bridge under an island needs: a bridge recorded after an island would cross the face
 * the clue is written on.
 */
/**
 * Records a hashi board into one picture, in board units.
 *
 * The layers are the grid, the bridges, the islands, their numbers and their marks, which is the
 * order a bridge under an island needs: a bridge recorded after an island would cross the face
 * the clue is written on.
 *
 * [lit] is the set of islands that are being pressed or have been named by a drag. They are
 * filled with [litFill] instead of the normal colour, and everything else about them, the outline
 * and the clue included, is drawn exactly as it is on the others. A highlight painted over the
 * finished picture would hide the very number the player is reading.
 *
 * [stranded] is the set of islands that can no longer reach their clue. Their outline, their
 * number and their solved stroke are painted in [alert], which is the same red a sudoku conflict
 * and a closed pipe loop are drawn in: one board's "this is wrong" is every board's.
 */
@Suppress("LongParameterList")
private fun recordHashi(
    width: Int,
    height: Int,
    islands: List<HashiIsland>,
    bridges: List<HashiBridge>,
    lit: Set<Int>,
    stranded: Set<Int>,
    boardBackground: Color,
    ink: Color,
    grid: Color,
    islandFill: Color,
    litFill: Color,
    success: Color,
    alert: Color,
    typeface: Typeface,
): Picture {
    val picture = Picture()
    // An island stands on a lattice point, so the point (column, row) is drawn at the centre of
    // the square `column` by `row`: the half-square around it is the margin the lattice lines run
    // into. A board of `width` points is therefore `width` units across, and a line drawn from
    // edge to edge sticks out half a point past the outermost islands on both sides.
    val canvas = picture.beginRecording(width, height)
    val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill.apply { color = boardBackground.toArgb() })

    // The faint lattice, through the points the islands stand on.
    val line = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = GRID_LINE }
    line.color = grid.toArgb()
    for (column in 0 until width) {
        val x = column + 0.5f
        canvas.drawLine(x, 0f, x, height.toFloat(), line)
    }
    for (row in 0 until height) {
        val y = row + 0.5f
        canvas.drawLine(0f, y, width.toFloat(), y, line)
    }

    // The bridges, under the islands. An island's centre is one cell in from the drawing area's
    // edge, which is where its lattice line crosses the other one.
    val bridge = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = BRIDGE_LINE }
    bridge.color = ink.toArgb()
    for (joined in bridges) {
        val from = islands.getOrNull(joined.a) ?: continue
        val to = islands.getOrNull(joined.b) ?: continue
        val startX = from.column + 0.5f
        val startY = from.row + 0.5f
        val endX = to.column + 0.5f
        val endY = to.row + 0.5f
        if (joined.count <= 1) {
            canvas.drawLine(startX, startY, endX, endY, bridge)
        } else {
            // A double bridge is two lines parallel to the centre line, offset to either side.
            val dx = endX - startX
            val dy = endY - startY
            val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val offsetX = -dy / length * BRIDGE_GAP / 2f
            val offsetY = dx / length * BRIDGE_GAP / 2f
            canvas.drawLine(startX + offsetX, startY + offsetY, endX + offsetX, endY + offsetY, bridge)
            canvas.drawLine(startX - offsetX, startY - offsetY, endX - offsetX, endY - offsetY, bridge)
        }
    }

    // The islands, their numbers and their marks.
    val islandPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    val outlinePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = ISLAND_OUTLINE }
    val corner = ISLAND_CORNER
    val box = RectF()
    val number = boardNumberPaint(typeface, ISLAND_TEXT_SIZE)
    val mark = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = MARK_LINE
        strokeCap = Paint.Cap.ROUND
        color = success.toArgb()
    }
    islands.forEachIndexed { index, island ->
        // An island is centred on its lattice point, which is one cell in from the drawing
        // area's edge: the square straddles the two lines that cross there.
        val centreX = island.column + 0.5f
        val centreY = island.row + 0.5f
        val inset = ISLAND_SIZE / 2f
        box.set(
            centreX - inset,
            centreY - inset,
            centreX + inset,
            centreY + inset,
        )
        // A lit island is filled with the highlight rather than washed with it, so its outline
        // and its clue are drawn on top exactly as they are on every other island.
        islandPaint.color = if (index in lit) litFill.toArgb() else islandFill.toArgb()
        canvas.drawRoundRect(box, corner, corner, islandPaint)
        // An island that can no longer reach its clue is drawn in the warning colour, outline and
        // number alike. It is the same signal a sudoku conflict and a closed pipe loop use, and it
        // says what no amount of refusing gestures could: this bridge cannot be part of an answer.
        outlinePaint.color = if (index in stranded) alert.toArgb() else ink.toArgb()
        canvas.drawRoundRect(box, corner, corner, outlinePaint)

        number.color = if (index in stranded) alert.toArgb() else ink.toArgb()
        val text = island.clue.toString()
        val offset = BoardText.centre(text, number)
        canvas.drawText(text, centreX + offset.x, centreY + offset.y, number)

        // The solved mark is a diagonal stroke across the island's face: the one thing on a
        // board that is the player's own note rather than the puzzle's. It follows the island into
        // the warning colour, because a red square with a green stroke through it would read as
        // two different verdicts on one island.
        if (island.marked) {
            mark.color = if (index in stranded) alert.toArgb() else success.toArgb()
            val half = MARK_SPAN / 2f
            val rise = kotlin.math.tan(MARK_ANGLE) * half
            canvas.drawLine(
                centreX - half,
                centreY + rise,
                centreX + half,
                centreY - rise,
                mark,
            )
        }
    }

    picture.endRecording()
    return picture
}
