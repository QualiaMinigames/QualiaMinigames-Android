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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.qiushui1012.app.minigames.AlertDark
import com.qiushui1012.app.minigames.AlertLight
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.rules.ArrowCubeBlock
import com.qiushui1012.app.minigames.rules.ArrowCubeBoard
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** The flight duration the fade threshold refers to; twice this is when the fallback starts. */
private const val FLY_MILLIS = 1500f

/** How long a taken block fades after it has failed to leave the render area, in milliseconds. */
private const val FLY_FADE_MILLIS = 500f

/** The contact phase after the constant approach, kept from the old outbound duration. */
private const val BUMP_CONTACT_MILLIS = 50f

/** How long a blocked block takes to return, in milliseconds. */
private const val BUMP_BACK_MILLIS = 75f

/** How long a taken block still guards against another tap after it starts to fly. */
private const val FLY_TAP_DELAY_MILLIS = 75f

/** How wide the shadow band of the three-dimensional board is, in dp. */
private const val ARROW_SHADOW_DP = 20f

/** How thick a face outline is, as a fraction of one projected cell. */
private const val OUTLINE_PER_CELL = 0.025f

/** The smallest outline that still reads as a line, in pixels. */
private const val OUTLINE_MIN_PX = 0.6f

/** The largest outline, in dp, so a zoomed face does not turn into a frame. */
private const val OUTLINE_MAX_DP = 1.2f

/** How long one red flash lasts, in milliseconds. */
private const val FLASH_PERIOD_MILLIS = 500f

/** How many times a blocked block and its blocker flash. */
private const val FLASH_CYCLES = 2

/** How far a finger travels before a press is a drag rather than a tap, in dp. */
private const val TAP_SLOP_DP = 8f

/** How little a two-finger gesture has to change before a frame is applied, in dp. */
private const val PINCH_SLOP_DP = 1f

/** How many degrees one dp of drag turns the camera. */
private const val ROTATE_PER_DP = 0.4f

/** The frame steps an animation is drawn from. */
private sealed interface Motion {
    /** The block the animation is about. */
    val block: ArrowCubeBlock

    /** The faces of the block while it moves, all six so the camera can cull them each frame. */
    val faces: List<ArrowCubeFace>

    /** How long the whole animation lasts, in milliseconds. */
    val durationMillis: Float

    /** A block leaving the board after a successful take. */
    data class Flying(
        override val block: ArrowCubeBlock,
        /** The six faces the block had while it was still in the board. */
        override val faces: List<ArrowCubeFace>,
    ) : Motion {
        override val durationMillis: Float = FLY_MILLIS * 2f + FLY_FADE_MILLIS
    }

    /** A blocked block bumping into its blocker and coming back. */
    data class Bumping(
        override val block: ArrowCubeBlock,
        /** The block in its way. */
        val blocker: ArrowCubeBlock,
        /** How many empty cells lie between the two. */
        val gap: Int,
        /** How long the constant-speed approach takes, in milliseconds. */
        val travelMillis: Float,
        /** All six faces of the blocked block. */
        override val faces: List<ArrowCubeFace>,
        /** The faces the slot left by the block uncovers while it is away. */
        val cavity: List<ArrowCubeFace>,
    ) : Motion {
        override val durationMillis: Float =
            max(travelMillis + BUMP_CONTACT_MILLIS + BUMP_BACK_MILLIS, FLASH_PERIOD_MILLIS * FLASH_CYCLES)
    }
}

/** One motion and the clock reading it started at. */
private data class ActiveAnimation(
    /** What is moving. */
    val motion: Motion,
    /** The frame clock value when the motion started. */
    val startedAtNanos: Long,
) {
    /** How far the motion has travelled at [nowNanos], in milliseconds. */
    fun elapsedMillis(nowNanos: Long): Float = ((nowNanos - startedAtNanos) / 1_000_000f).coerceAtLeast(0f)
}

/**
 * The arrow cube board: a three-dimensional scene the player rotates, zooms and taps.
 *
 * # Why the scene is drawn by hand
 *
 * Compose has no three-dimensional stage, and adding one would mean a graphics library for a
 * scene made of a few thousand quads. The camera is orthographic, which makes the projection a
 * dot product and the sorting a comparison; the drawing is a `Canvas`, so the board shares the
 * theme and the font with the rest of the application.
 *
 * # Why the gesture loop is one loop
 *
 * A tap, a one-finger drag and a two-finger pinch are told apart by how many pointers are down
 * and how far they moved. Stacking `detect…Gestures` would let one detector consume the movement
 * the next one needs, which is the same reason `ZoomableBoard` uses a single loop. The loop is
 * keyed on the board's shape alone; the camera it reads is the state of the moment.
 */
@Composable
fun ArrowCubePlay(
    board: ArrowCubeBoard,
    onMistakes: (Int) -> Unit,
    onSolved: () -> Unit,
) {
    val density = LocalDensity.current.density
    val boardSize = remember(board) { BoardSize(board.width, board.depth, board.height) }
    val initialBlocks = remember(board) { board.blocks() }
    var blocks by remember(board) { mutableStateOf(initialBlocks) }
    val occupancy = remember(board) { ArrowCubeScene.occupancyOf(boardSize, initialBlocks) }
    // A counter rather than the block list as the key: the list holds a record per block, and
    // comparing it on every frame of an animation would walk thousands of objects to decide
    // that the faces it was asked about are the same ones.
    var facesVersion by remember(board) { mutableIntStateOf(0) }
    val faces = remember(board, facesVersion) { ArrowCubeScene.buildFaces(boardSize, blocks, occupancy) }

    var view by remember { mutableStateOf(IntSize.Zero) }
    var yaw by remember { mutableFloatStateOf(35f) }
    var pitch by remember { mutableFloatStateOf(30f) }
    var fitDistance by remember { mutableFloatStateOf(0f) }
    var distance by remember { mutableFloatStateOf(0f) }
    val viewport = Viewport(view.width.toFloat(), view.height.toFloat())
    val camera = if (view.width > 0 && view.height > 0) OrbitCamera(boardSize, viewport, yaw, pitch, distance) else null

    // Motions are a list, not one slot: a click outside a flight's path is allowed, and a
    // second successful take a moment after the first has to fly its own block rather than
    // replace the first one. The clock below drives all of them.
    val animations = remember(board) { mutableStateListOf<ActiveAnimation>() }
    var frameNanos by remember(board) { mutableLongStateOf(0L) }
    var solvedNotified by remember(board) { mutableStateOf(false) }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = MaterialTheme.colorScheme.background
    // The three bands swap between the schemes: a top face is the plain surface in the light
    // scheme and the raised variant in the dark one, where the plain surface is darker than the
    // variant and would read as the bottom.
    val topColor = if (dark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val sideColor = if (dark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    val bottomColor = if (dark) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.outlineVariant
    val outline = MaterialTheme.colorScheme.outline
    val arrowColor = MaterialTheme.colorScheme.onSurface
    val flashColor = if (dark) AlertDark else AlertLight
    val shadowColor =
        if (dark) {
            Color.White.copy(alpha = 0.07f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        }
    val description = stringResource(R.string.cd_arrow_cube_board)
    val flySpeed = remember(boardSize) { ArrowCubeScene.flySpeed(boardSize) }
    val bumpSpeed = remember(boardSize) { ArrowCubeScene.bumpSpeed(boardSize) }
    // The frame clock below outlives a composition that changes the camera, and the off-screen
    // test has to see the camera of the frame it is testing.
    val currentCameraState = rememberUpdatedState(camera)

    // A restored game can already be finished: there is nothing to animate, and the screen
    // still has to record the play.
    LaunchedEffect(board) {
        if (board.isSolved()) {
            onSolved()
        }
    }

    // One frame clock for every motion. The time comes from the frame callback rather than
    // from a fixed step, so a slow frame moves the animations further instead of making them
    // run slow; coming back from the background starts a new interval rather than jumping.
    LaunchedEffect(board, animations.isNotEmpty()) {
        if (animations.isEmpty()) {
            return@LaunchedEffect
        }
        while (true) {
            val now = withFrameNanos { it }
            frameNanos = now
            val cameraNow = currentCameraState.value
            val finished =
                animations.filter { animation ->
                    val elapsed = animation.elapsedMillis(now)
                    val motion = animation.motion
                    if (motion is Motion.Flying) {
                        elapsed >= motion.durationMillis ||
                            (cameraNow != null && flightOutside(motion, elapsed, cameraNow, flySpeed))
                    } else {
                        elapsed >= motion.durationMillis
                    }
                }
            if (finished.isNotEmpty()) {
                animations.removeAll(finished.toSet())
                // The board is recorded when the last motion is over, not when the particular
                // flight that took the last block happens to finish: another block may still be
                // in the air.
                if (!solvedNotified && board.isSolved() && animations.isEmpty()) {
                    solvedNotified = true
                    onSolved()
                }
            }
            if (animations.isEmpty()) {
                break
            }
        }
    }

    /** Returns the camera as it stands now, for a gesture that outlives one composition. */
    fun currentCamera(): OrbitCamera? {
        if (view.width <= 0 || view.height <= 0 || distance <= 0f) {
            return null
        }
        return OrbitCamera(boardSize, Viewport(view.width.toFloat(), view.height.toFloat()), yaw, pitch, distance)
    }

    /** Takes a block out, or starts the failure animation. */
    fun tapAt(position: Offset) {
        val now = System.nanoTime()
        // A successful take still guards its first 75 ms; after that it no longer blocks a
        // click anywhere, including one on the block it is flying away from.
        if (animations.any { it.motion is Motion.Flying && it.elapsedMillis(now) < FLY_TAP_DELAY_MILLIS }) {
            return
        }
        val current = currentCamera() ?: return
        val index =
            pickBlock(
                ray = current.ray(ScreenPoint(position.x, position.y)),
                board = boardSize,
                occupancy = occupancy,
            ) ?: return
        // A blocked take is the only click a bump may guard: a failure whose impact point is
        // on the moving block's path would restart the same animation under the player's
        // finger. Failures elsewhere and every successful take go through.
        val wouldFail = ArrowCubeScene.blocker(boardSize, blocks[index], occupancy) != null
        if (
            wouldFail &&
            animations.any { animation ->
                val bump = animation.motion
                bump is Motion.Bumping &&
                    animation.elapsedMillis(now) < bump.durationMillis &&
                    bumpPathContains(bump, position, current)
            }
        ) {
            return
        }
        val result = board.take(index)
        onMistakes(board.mistakes)
        if (result.removed) {
            val taken = blocks[index]
            // The block is drawn whole while it flies: the faces it showed in place were the
            // exposed ones, and a block on its way out is no longer covered by its neighbours.
            val takenFaces = ArrowCubeScene.blockFaces(taken)
            blocks = blocks.mapIndexed { slot, block -> if (slot == index) block.copy(removed = true) else block }
            for (cell in ArrowCubeScene.cellsOf(boardSize, taken)) {
                occupancy[cell] = -1
            }
            facesVersion += 1
            // A bump of this block, or of the block it was hitting, has nothing left to move.
            animations.removeAll { animation ->
                val bump = animation.motion
                bump is Motion.Bumping && (bump.block.index == index || bump.blocker.index == index)
            }
            // A bump that stays alive may have uncovered a neighbour this take just removed.
            // Rebuild its cavity from the new occupancy so no face of a gone block is painted.
            for (slot in animations.indices) {
                val animation = animations[slot]
                val bump = animation.motion as? Motion.Bumping ?: continue
                if (bump.gap <= 0) {
                    continue
                }
                animations[slot] =
                    animation.copy(
                        motion =
                            bump.copy(
                                cavity = ArrowCubeScene.cavityFaces(boardSize, bump.block, blocks, occupancy),
                            ),
                    )
            }
            animations.add(ActiveAnimation(Motion.Flying(taken, takenFaces), System.nanoTime()))
            return
        }
        val blockerIndex = result.blocker ?: return
        animations.removeAll { animation ->
            val bump = animation.motion
            bump is Motion.Bumping && bump.block.index == blocks[index].index
        }
        animations.add(
            ActiveAnimation(
                Motion.Bumping(
                    block = blocks[index],
                    blocker = blocks[blockerIndex],
                    gap = result.gap,
                    // The constant part ends at the blocker; the contact phase keeps the old
                    // outbound duration and the return keeps its own, so the total matches.
                    travelMillis = if (result.gap > 0) result.gap * 1000f / bumpSpeed else 0f,
                    // A moving block turns, because the camera may keep turning while it moves.
                    // All six faces are kept here so the back-face pass can choose the visible
                    // ones every frame; keeping only the exposed ones would let the player see
                    // through the block once it leaves the neighbours that hid its sides.
                    faces = ArrowCubeScene.blockFaces(blocks[index]),
                    cavity =
                        if (result.gap > 0) {
                            ArrowCubeScene.cavityFaces(boardSize, blocks[index], blocks, occupancy)
                        } else {
                            emptyList()
                        },
                ),
                System.nanoTime(),
            ),
        )
    }

    /** Turns the camera by a drag, in dp, and keeps it outside the box. */
    fun rotateBy(dx: Float, dy: Float) {
        yaw = wrapDegrees(yaw - dx * ROTATE_PER_DP)
        pitch = clampPitch(pitch + dy * ROTATE_PER_DP)
        val minimum = minDistance(boardSize, yaw, pitch)
        distance = distance.coerceIn(minimum.coerceAtMost(fitDistance), fitDistance)
    }

    /** Applies one frame of a two-finger gesture. */
    fun pinchBy(
        centreDelta: Offset,
        spacing: Float,
        lastSpacing: Float,
    ) {
        val centreDp = ScreenPoint(centreDelta.x / density, centreDelta.y / density)
        yaw = wrapDegrees(yaw - centreDp.x * ROTATE_PER_DP)
        pitch = clampPitch(pitch + centreDp.y * ROTATE_PER_DP)
        val minimum = minDistance(boardSize, yaw, pitch)
        if (spacing >= PINCH_SLOP_DP * density && lastSpacing >= PINCH_SLOP_DP * density) {
            val currentZoom = fitDistance / distance
            val target = fitDistance / (currentZoom * spacing / lastSpacing)
            distance = target.coerceIn(minimum.coerceAtMost(fitDistance), fitDistance)
        } else {
            distance = distance.coerceIn(minimum.coerceAtMost(fitDistance), fitDistance)
        }
    }

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    view = size
                    if (fitDistance == 0f && size.width > 0 && size.height > 0) {
                        fitDistance = fitDistance(boardSize)
                        distance = fitDistance
                    }
                }
                .semantics { contentDescription = description }
                .pointerInput(board.width, board.depth, board.height) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        var last = start
                        var moved = false
                        var twoFingers = false
                        var hadTwoFingers = false
                        var lastCentre = start
                        var lastSpacing = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                break
                            }
                            if (pressed.size >= 2) {
                                val first = pressed[0].position
                                val second = pressed[1].position
                                val centre = (first + second) / 2f
                                val spacing = (first - second).getDistance()
                                if (!twoFingers) {
                                    twoFingers = true
                                    hadTwoFingers = true
                                    moved = true
                                    lastCentre = centre
                                    lastSpacing = spacing
                                } else {
                                    val centreMoveDp = (centre - lastCentre).getDistance() / density
                                    val spacingMoveDp = abs(spacing - lastSpacing) / density
                                    if (centreMoveDp >= PINCH_SLOP_DP || spacingMoveDp >= PINCH_SLOP_DP) {
                                        pinchBy(centre - lastCentre, spacing, lastSpacing)
                                        lastCentre = centre
                                        lastSpacing = spacing
                                    }
                                }
                            } else {
                                val position = pressed.first().position
                                if (hadTwoFingers) {
                                    // One finger left the pinch. Its position becomes the new
                                    // baseline, and the gesture stays moved: the lift is not a
                                    // tap, and the camera must not jump to the old start point.
                                    hadTwoFingers = false
                                    twoFingers = false
                                    last = position
                                    moved = true
                                } else {
                                    if (!moved && (position - start).getDistance() / density > TAP_SLOP_DP) {
                                        moved = true
                                    }
                                    if (moved) {
                                        rotateBy((position - last).x / density, (position - last).y / density)
                                    }
                                    last = position
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                        if (!moved && !hadTwoFingers) {
                            tapAt(start)
                        }
                    }
                },
    ) {
        if (distance <= 0f) {
            return@Canvas
        }
        val current = camera ?: return@Canvas
        drawRect(background)
        val outlineWidth = (current.scale * OUTLINE_PER_CELL).coerceIn(OUTLINE_MIN_PX, OUTLINE_MAX_DP * density)

        val active = animations.toList()
        val now = frameNanos
        // A bump with no gap does not leave its cell, so its faces stay in the static list and
        // the flash is painted over them. A bump with a gap and a take both move their block:
        // the six faces are culled again with the camera of this frame, which is what keeps a
        // turn during the animation from exposing the blocks behind it.
        val movingBlocks =
            active.mapNotNull { animation ->
                val bump = animation.motion as? Motion.Bumping
                if (bump != null && bump.gap > 0) bump.block.index else null
            }.toSet()
        val cavity =
            active
                .mapNotNull { (it.motion as? Motion.Bumping)?.takeIf { bump -> bump.gap > 0 }?.cavity }
                .flatten()
                .distinctBy { it.block to it.normal }
        val staticFaces =
            ArrowCubeScene.visibleFaces(faces + cavity, current).filter { face ->
                face.face.block !in movingBlocks
            }
        val movedFaces =
            active.flatMap { animation ->
                val motion = animation.motion
                val travels = motion is Motion.Flying || (motion is Motion.Bumping && motion.gap > 0)
                if (!travels) {
                    emptyList()
                } else {
                    val offset = offsetOf(motion, animation.elapsedMillis(now), flySpeed)
                    ArrowCubeScene.visibleFaces(
                        motion.faces.map { ArrowCubeScene.withOffset(it, offset) },
                        current,
                    )
                }
            }
        val drawFaces =
            if (movedFaces.isEmpty()) {
                staticFaces
            } else {
                (staticFaces + movedFaces).sortedWith(
                    compareByDescending<ProjectedArrowCubeFace> { it.sortDepth }.thenBy { it.face.block },
                )
            }

        clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
            for (projected in drawFaces) {
                val flying =
                    active.firstOrNull { animation ->
                        val motion = animation.motion
                        motion is Motion.Flying && motion.block.index == projected.face.block
                    }
                val alpha =
                    flying?.let { flightAlpha(it.motion, it.elapsedMillis(now)) } ?: 1f
                if (alpha <= 0f) {
                    continue
                }
                val path = quadPath(projected)
                val flash =
                    active
                        .filter { it.motion is Motion.Bumping }
                        .maxOfOrNull { animation ->
                            flashAlpha(animation.motion, projected.face.block, animation.elapsedMillis(now))
                        } ?: 0f
                drawPath(
                    path,
                    color = toneColor(projected.face.tone, topColor, sideColor, bottomColor),
                    alpha = alpha,
                    style = Fill,
                )
                projected.face.decal?.let { decal ->
                    val arrow =
                        ArrowCubeScene.decalPoints(projected.face).map(current::project).toPath()
                    if (arrow != null) {
                        // An arrow belongs to its face; clipping is the last guarantee that a
                        // rounding error at a long face's end cannot paint on a neighbour.
                        clipPath(path) { drawPath(arrow, color = arrowColor, alpha = alpha, style = Fill) }
                    }
                }
                if (flash > 0f) {
                    drawPath(path, color = flashColor.copy(alpha = flash * alpha), style = Fill)
                }
                drawPath(
                    path,
                    color = outline,
                    alpha = alpha,
                    style = Stroke(width = outlineWidth, join = StrokeJoin.Miter),
                )
            }
        }

        // The shadow is computed from every face that survived the back-face pass, including
        // the ones the viewport is about to remove: it is the extent of the model, not of the
        // visible slice, that says which directions still hold something.
        val kept =
            faces
                .asSequence()
                .filter { it.normal.dot(current.basis.forward) < 0f }
                .map { face -> face.corners.map(current::project) }
                .toList()
        if (kept.isNotEmpty()) {
            val left = kept.minOf { corners -> corners.minOf { it.x } }
            val top = kept.minOf { corners -> corners.minOf { it.y } }
            val right = kept.maxOf { corners -> corners.maxOf { it.x } }
            val bottom = kept.maxOf { corners -> corners.maxOf { it.y } }
            drawEdgeShadows(
                originX = left,
                originY = top,
                drawnWidth = right - left,
                drawnHeight = bottom - top,
                color = shadowColor,
                thickness = ARROW_SHADOW_DP.dp.toPx(),
            )
        }
    }
}

/** Returns the world offset of an animation at a moment. */
private fun offsetOf(
    motion: Motion,
    elapsed: Float,
    flySpeed: Float,
): Vec3 =
    when (motion) {
        is Motion.Flying -> {
            // Linear from the first frame: a block that still shows after twice the old
            // duration is handled by the fade, not by slowing down its last visible part.
            directionVector(motion.block.direction) * (flySpeed * elapsed / 1000f)
        }

        is Motion.Bumping -> {
            if (motion.gap <= 0) {
                Vec3.ZERO
            } else {
                val distance = motion.gap - 1f / 64f
                val travel = motion.travelMillis
                when {
                    elapsed <= travel -> {
                        directionVector(motion.block.direction) *
                            (distance * (elapsed / travel).coerceIn(0f, 1f))
                    }

                    elapsed <= travel + BUMP_CONTACT_MILLIS -> {
                        directionVector(motion.block.direction) * distance
                    }

                    else -> {
                        val back =
                            ((elapsed - travel - BUMP_CONTACT_MILLIS) / BUMP_BACK_MILLIS).coerceIn(0f, 1f)
                        directionVector(motion.block.direction) * (distance * (1f - easeOutCubic(back)))
                    }
                }
            }
        }
    }

/** Returns the opacity of a flash on a block at a moment. */
private fun flashAlpha(
    motion: Motion?,
    block: Int,
    elapsed: Float,
): Float {
    if (motion !is Motion.Bumping) {
        return 0f
    }
    if (block != motion.block.index && block != motion.blocker.index) {
        return 0f
    }
    if (elapsed >= FLASH_PERIOD_MILLIS * FLASH_CYCLES) {
        return 0f
    }
    val cycle = (elapsed % FLASH_PERIOD_MILLIS) / FLASH_PERIOD_MILLIS
    return sin(cycle * PI).toFloat() * 0.55f
}

/** Returns the opacity of a flying block at a moment. */
private fun flightAlpha(
    motion: Motion,
    elapsed: Float,
): Float = if (motion is Motion.Flying) flightFadeAlpha(elapsed) else 1f

/** Returns the fade applied after the fallback threshold, independent of the motion kind. */
internal fun flightFadeAlpha(elapsed: Float): Float {
    val fadeStart = FLY_MILLIS * 2f
    if (elapsed <= fadeStart) {
        return 1f
    }
    return (1f - (elapsed - fadeStart) / FLY_FADE_MILLIS).coerceIn(0f, 1f)
}

/** Returns whether every corner of a flying block is outside the render area. */
private fun flightOutside(
    motion: Motion.Flying,
    elapsed: Float,
    camera: OrbitCamera,
    flySpeed: Float,
): Boolean {
    val offset = directionVector(motion.block.direction) * (flySpeed * elapsed / 1000f)
    val corners = motion.faces.flatMap { face -> face.corners.map { camera.project(it + offset) } }
    if (corners.isEmpty()) {
        return true
    }
    return corners.maxOf { it.x } < 0f ||
        corners.minOf { it.x } > camera.viewport.width ||
        corners.maxOf { it.y } < 0f ||
        corners.minOf { it.y } > camera.viewport.height
}

/**
 * Returns whether [position] lies on the screen path of a bump.
 *
 * The path is the block swept from its cell to the blocker and back; the screen union of that
 * sweep is the convex hull of the block's projected corners at the two ends. A click in that
 * hull would hit the moving block or the face it is crossing and would restart the same
 * animation, which is the one case a bump still has to ignore.
 */
private fun bumpPathContains(
    bump: Motion.Bumping,
    position: Offset,
    camera: OrbitCamera,
): Boolean {
    if (bump.gap <= 0) {
        return false
    }
    val direction = directionVector(bump.block.direction)
    val distance = bump.gap.toFloat()
    val points = ArrayList<ScreenPoint>()
    for (face in bump.faces) {
        for (corner in face.corners) {
            points.add(camera.project(corner))
            points.add(camera.project(corner + direction * distance))
        }
    }
    val hull = convexHull(points)
    return hull.size >= 3 && insideConvex(hull, ScreenPoint(position.x, position.y))
}

/** Returns the convex hull of screen points, without duplicate or collinear corners. */
private fun convexHull(points: List<ScreenPoint>): List<ScreenPoint> {
    val unique = points.distinctBy { it.x to it.y }.sortedWith(compareBy({ it.x }, { it.y }))
    if (unique.size < 3) {
        return unique
    }

    fun cross(
        origin: ScreenPoint,
        a: ScreenPoint,
        b: ScreenPoint,
    ): Float = (a.x - origin.x) * (b.y - origin.y) - (a.y - origin.y) * (b.x - origin.x)

    val lower = ArrayList<ScreenPoint>()
    for (point in unique) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), point) <= 0f) {
            lower.removeAt(lower.lastIndex)
        }
        lower.add(point)
    }
    val upper = ArrayList<ScreenPoint>()
    for (point in unique.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), point) <= 0f) {
            upper.removeAt(upper.lastIndex)
        }
        upper.add(point)
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

/** Returns whether a point is inside or on a convex polygon in either winding order. */
private fun insideConvex(
    hull: List<ScreenPoint>,
    point: ScreenPoint,
): Boolean {
    var sign = 0
    for (index in hull.indices) {
        val a = hull[index]
        val b = hull[(index + 1) % hull.size]
        val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
        if (abs(cross) < 1e-4f) {
            continue
        }
        val current = if (cross > 0f) 1 else -1
        if (sign == 0) {
            sign = current
        } else if (sign != current) {
            return false
        }
    }
    return true
}

/** Returns the colour of a face band under the current scheme. */
private fun toneColor(
    tone: ArrowCubeFaceTone,
    top: Color,
    side: Color,
    bottom: Color,
): Color =
    when (tone) {
        ArrowCubeFaceTone.Top -> top
        ArrowCubeFaceTone.Side -> side
        ArrowCubeFaceTone.Bottom -> bottom
    }

/** Returns the path of a projected quadrilateral. */
private fun quadPath(face: ProjectedArrowCubeFace): Path {
    val path = Path()
    if (face.corners.isEmpty()) {
        return path
    }
    path.moveTo(face.corners[0].x, face.corners[0].y)
    for (index in 1 until face.corners.size) {
        path.lineTo(face.corners[index].x, face.corners[index].y)
    }
    path.close()
    return path
}

/** Returns the path of a projected arrow, or null when there is none to draw. */
private fun List<ScreenPoint>.toPath(): Path? {
    if (isEmpty()) {
        return null
    }
    val path = Path()
    path.moveTo(this[0].x, this[0].y)
    for (index in 1 until size) {
        path.lineTo(this[index].x, this[index].y)
    }
    path.close()
    return path
}

/** Returns `1 - (1 - t)³`, the easing of both the take and the bump. */
private fun easeOutCubic(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    val rest = 1f - clamped
    return 1f - rest * rest * rest
}
