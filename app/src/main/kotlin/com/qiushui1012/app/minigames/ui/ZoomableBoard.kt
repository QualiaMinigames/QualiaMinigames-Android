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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

/**
 * A board that can be moved and zoomed, and the shadow that says there is more in a direction.
 *
 * # One scale, computed once
 *
 * The board has a fixed size in board units and is drawn at `fit × zoom`, where `fit` is the
 * factor that makes it exactly fit the view. Hit testing, the pan limits and the shadow all
 * read that one product, so a tap and a drawn cell cannot disagree.
 *
 * # One gesture loop, not three
 *
 * A tap, a long press, a pinch and a drag are recognised by a single loop rather than by
 * `detect…Gestures` helpers stacked on one modifier. Stacked detectors compete for the same
 * pointer: the drag detector consumes the movement of every down, and the tap detector then
 * never sees an up it may report. The loop here decides what a gesture is from how many
 * pointers it has and how far they moved, which is the only way the four can coexist.
 *
 * A double tap is not one of them, and the board does not reset its zoom on one. A tap is what
 * turns a piece and what selects a cell, so two taps in a row are two moves: reading them as a
 * gesture of their own would undo the zoom the player had just set, on the very move they were
 * making. Pinching back out to the whole board is the way to leave a zoomed view.
 *
 * # The edge shadow
 *
 * When the drawn board is wider or taller than the view and its edge is not against the
 * corresponding edge of the view, a translucent band is drawn along that side of the view. Its
 * opacity is one constant: it says "there is more in this direction", and a band whose
 * strength grew with the hidden distance would become a second thing to read on top of a board
 * that is already dense. A side with nothing hidden draws nothing.
 */
@Composable
fun ZoomableBoard(
    /** The board's size in board units, which is also its size at scale 1. */
    boardWidth: Float,
    boardHeight: Float,
    /** The narrowest a cell may be drawn, in dp, which sets the largest zoom. */
    minCellDp: Float,
    /** The number of cells across, which is what a tap is converted to. */
    columns: Int,
    /** The number of cells down. */
    rows: Int,
    /** Draws the board, given the scale to draw it at. */
    content: DrawScope.(scale: Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Called with the cell under a tap. */
    onTap: (row: Int, column: Int) -> Unit = { _, _ -> },
    /**
     * Called with the cell under a long press, once the press has taken hold.
     *
     * A board that reports the press itself through [onPressChanged] leaves this alone, so
     * that one gesture is not reported twice.
     */
    onLongPress: (row: Int, column: Int) -> Unit = { _, _ -> },
    /**
     * Called while a long press is held and again when it is released.
     *
     * [row] and [column] are the cell the press started on, or null when it started off the
     * board; [distanceDp] is how far the finger has travelled since; [released] says whether
     * this call is the release. A board that turns a long press into a drag listens to this
     * instead of to [onLongPress]: a press that becomes a drag is not a tap, and a board that
     * read it as either would act twice on one gesture.
     */
    onPressChanged: ((row: Int?, column: Int, distanceDp: Float, released: Boolean) -> Unit)? = null,
) {
    val density = LocalDensity.current
    // Read outside the canvas: a DrawScope is not a composable scope, so a theme value has to
    // be taken here and closed over.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var view by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // The gesture loop is keyed on the board's shape alone, so it is not restarted on every
    // zoom step. The values it reads are therefore held in state that the loop reads afresh.
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val tapHandler by rememberUpdatedState(onTap)
    val pressHandler by rememberUpdatedState(onLongPress)
    val pressChanged by rememberUpdatedState(onPressChanged)
    val densityValue = density.density

    val fit =
        if (view.width == 0 || boardWidth == 0f) {
            1f
        } else {
            min(view.width / boardWidth, view.height / boardHeight)
        }
    val minZoom = 1f
    val maxZoom =
        if (boardWidth == 0f) {
            1f
        } else {
            // The scale at which one cell is `minCellDp` wide, divided by the fit scale, which
            // is what the zoom multiplier has to reach.
            val cellAtOne = boardWidth / columns
            val needed = minCellDp * density.density / cellAtOne
            max(minZoom, needed / fit)
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // The size of the box is what drawing and hit testing both measure against.
                .onSizeChanged { view = it }
                .pointerInput(boardWidth, boardHeight, rows, columns, pressChanged != null) {
                    // A press is held long enough to be deliberate and no longer. The platform's
                    // own timeout is a third longer than a board wants: on a grid a player
                    // presses and moves on within a second, and waiting out the system figure
                    // makes a flag feel like it is not coming.
                    val longPressMillis = viewConfiguration.longPressTimeoutMillis * 2 / 3
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startedAt = down.position
                        var last = startedAt
                        var moved = false
                        var pointerCount = 1
                        var zooming = false
                        // The finger may lift while the long-press window is still open. That
                        // up event is read inside the window and is the only one this gesture
                        // will ever see, so the drag loop below must not wait for another: it
                        // would block until the next finger went down, and that finger's own
                        // tap would then be read as the end of this gesture. Every tap after
                        // the first would be late by one, which on a sudoku is a digit landing
                        // in the cell selected by the previous tap.
                        var released = false

                        // A long press is the same gesture as a tap until it has lasted long
                        // enough to be one; the time is bounded rather than awaited forever so
                        // that a drag can still start from the same down. The window running
                        // out is what a long press *is*: the loop below only ever leaves early
                        // by reporting that the gesture became something else, so a timeout is
                        // the press taking hold and a value is the gesture being a tap, a drag
                        // or a pinch after all.
                        val interrupted =
                            withTimeoutOrNull(longPressMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        released = true
                                        return@withTimeoutOrNull true
                                    }
                                    pointerCount = pressed.size
                                    if (pointerCount > 1) {
                                        zooming = true
                                    }
                                    last = event.calculateCentroid(useCurrent = true) ?: last
                                    if (zooming || event.calculateZoom() != 1f) {
                                        return@withTimeoutOrNull true
                                    }
                                    if ((event.changes.first().position - startedAt).getDistance() >
                                        viewConfiguration.touchSlop
                                    ) {
                                        moved = true
                                        return@withTimeoutOrNull true
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE")
                                true
                            }
                        val longPress = interrupted == null && !released && !moved && !zooming

                        if (longPress) {
                            val pressedCell =
                                cellAt(startedAt, view, boardWidth, boardHeight, currentZoom, currentPan, rows, columns)
                            // A board that says it follows the press itself gets the press and
                            // the drag that may follow it; one that does not gets a single
                            // long-press report, which is what the pipes and sudoku boards use.
                            val tracker = pressChanged
                            if (tracker == null) {
                                pressedCell?.let { (row, column) -> pressHandler(row, column) }
                                while (awaitPointerEvent(PointerEventPass.Main).changes.any { it.pressed }) {
                                    // Drain the rest of the gesture.
                                }
                                return@awaitEachGesture
                            }
                            tracker(pressedCell?.first, pressedCell?.second ?: 0, 0f, false)
                            var lastPosition = startedAt
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    tracker(
                                        pressedCell?.first,
                                        pressedCell?.second ?: 0,
                                        (lastPosition - startedAt).getDistance() / densityValue,
                                        true,
                                    )
                                    break
                                }
                                lastPosition = event.calculateCentroid(useCurrent = true) ?: lastPosition
                                tracker(
                                    pressedCell?.first,
                                    pressedCell?.second ?: 0,
                                    (lastPosition - startedAt).getDistance() / densityValue,
                                    false,
                                )
                                event.changes.forEach { it.consume() }
                            }
                            return@awaitEachGesture
                        }

                        // From here the gesture is a drag, a pinch, or a tap. The loop is only
                        // entered when there is still something to read, which the flag above
                        // decides.
                        while (!released) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                break
                            }
                            pointerCount = pressed.size
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                moved = true
                            }
                            if (pointerCount > 1 || zoomChange != 1f) {
                                val next = (currentZoom * zoomChange).coerceIn(minZoom, maxZoom)
                                val scaleChange = next / currentZoom
                                val base = centroid ?: last
                                val newPan =
                                    currentPan + panChange + (currentPan - base) * (scaleChange - 1f)
                                pan =
                                    clampPan(
                                        newPan,
                                        view,
                                        boardWidth * fit * next,
                                        boardHeight * fit * next,
                                    )
                                zoom = next
                            } else if (panChange != Offset.Zero) {
                                pan =
                                    clampPan(
                                        currentPan + panChange,
                                        view,
                                        boardWidth * fit * currentZoom,
                                        boardHeight * fit * currentZoom,
                                    )
                            }
                            event.changes.forEach { it.consume() }
                        }

                        if (!moved && pointerCount == 1) {
                            cellAt(startedAt, view, boardWidth, boardHeight, currentZoom, currentPan, rows, columns)
                                ?.let { (row, column) -> tapHandler(row, column) }
                        }
                    }
                }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (view.width == 0 || boardWidth == 0f) {
                return@Canvas
            }
            val scale = fit * zoom
            val originX = (size.width - boardWidth * scale) / 2f + pan.x
            val originY = (size.height - boardHeight * scale) / 2f + pan.y

            // A zoomed board is larger than the view, and without this it is drawn past the
            // view's edges: pieces appear over the header and the status bar, and the shadow
            // bands — which mark the view's own edges — end up in the middle of the picture.
            // The clip is the view, so the board ends exactly where the shadows begin and a
            // band always lies on the board it is saying more of.
            clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                withTransform({
                    translate(originX, originY)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    content(scale)
                }
            }

            drawEdgeShadows(
                originX = originX,
                originY = originY,
                drawnWidth = boardWidth * scale,
                drawnHeight = boardHeight * scale,
                color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.12f),
                thickness = SHADOW_DP.dp.toPx(),
            )
        }
    }
}

/** How wide the edge shadow is, in dp. */
private const val SHADOW_DP = 28f

/** How far the drawn board may be moved before a side is pinned to the view. */
private fun clampPan(
    pan: Offset,
    view: IntSize,
    drawnWidth: Float,
    drawnHeight: Float,
): Offset {
    // A board smaller than the view stays centred; one larger may be moved until its own edges
    // reach the view's.
    val maxX = max(0f, (drawnWidth - view.width) / 2f)
    val maxY = max(0f, (drawnHeight - view.height) / 2f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/** Draws a band along every side of the view whose board edge is hidden behind it. */
private fun DrawScope.drawEdgeShadows(
    originX: Float,
    originY: Float,
    drawnWidth: Float,
    drawnHeight: Float,
    color: Color,
    thickness: Float,
) {
    val right = originX + drawnWidth
    val bottom = originY + drawnHeight
    // A pixel of slack: a board that fits exactly lands on the edge with a rounding error, and
    // a shadow that flickers on at every resize is worse than none.
    val slack = 1f

    if (originX < -slack) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(color, Color.Transparent), startX = 0f, endX = thickness),
            topLeft = Offset.Zero,
            size = Size(thickness, size.height),
        )
    }
    if (originY < -slack) {
        drawRect(
            brush = Brush.verticalGradient(listOf(color, Color.Transparent), startY = 0f, endY = thickness),
            topLeft = Offset.Zero,
            size = Size(size.width, thickness),
        )
    }
    if (right > size.width + slack) {
        drawRect(
            brush =
                Brush.horizontalGradient(
                    listOf(Color.Transparent, color),
                    startX = size.width - thickness,
                    endX = size.width,
                ),
            topLeft = Offset(size.width - thickness, 0f),
            size = Size(thickness, size.height),
        )
    }
    if (bottom > size.height + slack) {
        drawRect(
            brush =
                Brush.verticalGradient(
                    listOf(Color.Transparent, color),
                    startY = size.height - thickness,
                    endY = size.height,
                ),
            topLeft = Offset(0f, size.height - thickness),
            size = Size(size.width, thickness),
        )
    }
}

/** Returns the cell a point falls in, or null when it misses the board. */
private fun cellAt(
    position: Offset,
    view: IntSize,
    boardWidth: Float,
    boardHeight: Float,
    zoom: Float,
    pan: Offset,
    rows: Int,
    columns: Int,
): Pair<Int, Int>? {
    if (view.width == 0 || boardWidth == 0f) {
        return null
    }
    val fit = min(view.width / boardWidth, view.height / boardHeight)
    val scale = fit * zoom
    val originX = (view.width - boardWidth * scale) / 2f + pan.x
    val originY = (view.height - boardHeight * scale) / 2f + pan.y
    val cellWidth = boardWidth * scale / columns
    val cellHeight = boardHeight * scale / rows
    val column = ((position.x - originX) / cellWidth).toInt()
    val row = ((position.y - originY) / cellHeight).toInt()
    if (column !in 0 until columns || row !in 0 until rows) {
        return null
    }
    return row to column
}
