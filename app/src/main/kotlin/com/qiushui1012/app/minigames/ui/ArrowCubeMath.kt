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

import com.qiushui1012.app.minigames.rules.ArrowCubeDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The camera and picking arithmetic of an arrow cube board.
 *
 * This file is deliberately free of Compose and of Android: every function is a plain number
 * calculation, which is what lets the camera be tested on the JVM rather than only by looking
 * at a device. The drawing code that consumes it is in `ArrowCubeScene.kt`.
 *
 * # The projection
 *
 * The scene uses an orthographic projection, not a perspective one. The two anchors of a pinch
 * are the board's top-left and bottom-right corners, and under an orthographic projection two
 * opposite corners are symmetric about the screen centre: scaling about that centre moves both
 * of them by the same ratio, so both stay under the fingers. Under perspective the nearer corner
 * grows faster than the farther one and the two cannot both be held.
 *
 * # Coordinates
 *
 * A board point is in cells: x towards the east, y towards the north, z upwards, with the centre
 * of the board at the world origin. That origin is what the camera orbits; there is no pan.
 */

/** A point or direction in board space, in cells. */
data class Vec3(
    /** The position along the east axis. */
    val x: Float,
    /** The position along the north axis. */
    val y: Float,
    /** The position along the up axis. */
    val z: Float,
) {
    /** Returns this vector plus another. */
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    /** Returns this vector minus another. */
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

    /** Returns this vector scaled by a factor. */
    operator fun times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)

    /** Returns the dot product with another vector. */
    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    /** Returns the cross product with another vector. */
    fun cross(other: Vec3): Vec3 =
        Vec3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x,
        )

    /** Returns the length of this vector. */
    fun length(): Float = sqrt(x * x + y * y + z * z)

    /** Returns this vector scaled to unit length. */
    fun normalized(): Vec3 {
        val length = length()
        return if (length < EPSILON) ZERO else Vec3(x / length, y / length, z / length)
    }

    companion object {
        /** The vector with no length. */
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

/** A point on the screen, in pixels from the view's top-left corner. */
data class ScreenPoint(
    /** The horizontal position. */
    val x: Float,
    /** The vertical position. */
    val y: Float,
)

/** The size of the drawing area, in pixels. */
data class Viewport(
    /** The width in pixels. */
    val width: Float,
    /** The height in pixels. */
    val height: Float,
) {
    /** The centre of the view, which is where the board's centre is projected. */
    val centre: ScreenPoint
        get() = ScreenPoint(width / 2f, height / 2f)
}

/** The dimensions of a board, in cells, and the box arithmetic they imply. */
data class BoardSize(
    /** The number of cells along x. */
    val width: Int,
    /** The number of cells along y. */
    val depth: Int,
    /** The number of cells along z. */
    val height: Int,
) {
    init {
        require(width > 0 && depth > 0 && height > 0) { "a board has a positive extent on every axis" }
    }

    /** The centre of the board, which the camera orbits. */
    val centre: Vec3
        get() = Vec3(width / 2f, depth / 2f, height / 2f)

    /** The radius of the sphere around the board, which is half its diagonal. */
    val radius: Float
        get() = 0.5f * sqrt((width * width + depth * depth + height * height).toFloat())

    /** The longest side, in cells. */
    val longestSide: Int
        get() = max(width, max(depth, height))
}

/** The three axes of the camera, all unit length. */
data class CameraBasis(
    /** The direction the camera looks in. */
    val forward: Vec3,
    /** The direction of the screen's right edge. */
    val right: Vec3,
    /** The direction of the screen's top edge. */
    val up: Vec3,
)

/** The direction a block's arrow points, as a unit vector in board space. */
fun directionVector(direction: ArrowCubeDirection): Vec3 =
    when (direction) {
        ArrowCubeDirection.East -> Vec3(1f, 0f, 0f)
        ArrowCubeDirection.West -> Vec3(-1f, 0f, 0f)
        ArrowCubeDirection.North -> Vec3(0f, 1f, 0f)
        ArrowCubeDirection.South -> Vec3(0f, -1f, 0f)
        ArrowCubeDirection.Up -> Vec3(0f, 0f, 1f)
        ArrowCubeDirection.Down -> Vec3(0f, 0f, -1f)
    }

/** Returns an angle folded into `[-180, 180)`. */
fun wrapDegrees(angle: Float): Float {
    var wrapped = angle
    while (wrapped >= 180f) {
        wrapped -= 360f
    }
    while (wrapped < -180f) {
        wrapped += 360f
    }
    return wrapped
}

/** Returns a pitch clamped to the range the camera allows. */
fun clampPitch(pitch: Float): Float = pitch.coerceIn(-90f, 90f)

/**
 * Returns the camera's basis vectors for a yaw and a pitch, both in degrees.
 *
 * `pitch` turns the eye up from the horizon and `yaw` turns it from the north. At exactly
 * ±90 degrees the forward vector is parallel to the world's up vector and a cross product
 * with it would be zero; the horizontal right vector is built from `yaw` instead, which is
 * what makes the top face rotate in the screen as `yaw` changes at that pitch.
 */
fun cameraBasis(
    yaw: Float,
    pitchDegrees: Float,
): CameraBasis {
    val yawRadians = Math.toRadians(yaw.toDouble()).toFloat()
    val pitch = clampPitch(pitchDegrees)
    val pitchRadians = Math.toRadians(pitch.toDouble()).toFloat()
    val cosine = cos(pitchRadians)
    val sine = sin(pitchRadians)
    val eyeDirection = Vec3(cosine * sin(yawRadians), -cosine * cos(yawRadians), sine)
    val forward = eyeDirection * -1f
    return if (abs(cosine) > 1e-6f) {
        val right = forward.cross(Vec3(0f, 0f, 1f)).normalized()
        CameraBasis(forward = forward, right = right, up = right.cross(forward))
    } else {
        val right = Vec3(cos(yawRadians), sin(yawRadians), 0f).normalized()
        CameraBasis(forward = forward, right = right, up = right.cross(forward))
    }
}

/** Returns the scale that makes the board fit the view with a small margin. */
fun fitScale(
    board: BoardSize,
    viewport: Viewport,
): Float = min(viewport.width, viewport.height) / (2f * board.radius) * 0.95f

/**
 * Returns the camera distance at which the board is drawn at its full size.
 *
 * It is a distance along the view rather than a size on screen because the camera is
 * orthographic: the distance does not change the projection at all. It is used as the upper
 * bound of the zoom, so that the unzoomed board is the smallest picture the camera shows.
 */
fun fitDistance(board: BoardSize): Float = max(board.radius * 3f, 1f)

/**
 * Returns the distance from the board's centre to its bounding box along a direction.
 *
 * The camera has to stay outside the solid box, so this is the lower bound of its distance
 * minus the two cells of clearance. A direction that is parallel to a face only leaves through
 * the faces it points at, which is what the per-axis minimum expresses.
 */
fun boxExitDistance(
    board: BoardSize,
    direction: Vec3,
): Float {
    var exit = Float.MAX_VALUE
    if (abs(direction.x) > EPSILON) {
        exit = min(exit, (board.width / 2f) / abs(direction.x))
    }
    if (abs(direction.y) > EPSILON) {
        exit = min(exit, (board.depth / 2f) / abs(direction.y))
    }
    if (abs(direction.z) > EPSILON) {
        exit = min(exit, (board.height / 2f) / abs(direction.z))
    }
    return if (exit == Float.MAX_VALUE) 0f else exit
}

/** Returns the closest the camera may come while staying outside the solid board. */
fun minDistance(
    board: BoardSize,
    yaw: Float,
    pitch: Float,
): Float {
    val basis = cameraBasis(yaw, pitch)
    val eyeDirection = basis.forward * -1f
    return boxExitDistance(board, eyeDirection) + 2f
}

/**
 * An orthographic camera looking at the centre of a board.
 *
 * The distance is clamped to the range that keeps the camera outside the solid box and no
 * farther than the unzoomed distance: a zoom below one would draw the board smaller than its
 * fitted size, which the scene never does. [zoom] is the ratio the screen gains over that
 * fitted size.
 */
class OrbitCamera(
    /** The board the camera looks at. */
    val board: BoardSize,
    /** The view the board is projected into. */
    val viewport: Viewport,
    yawDegrees: Float,
    pitchDegrees: Float,
    distanceFromCentre: Float,
) {
    /** The yaw, folded into `[-180, 180)`. */
    val yaw: Float = wrapDegrees(yawDegrees)

    /** The pitch, clamped to ±90 degrees. */
    val pitch: Float = clampPitch(pitchDegrees)

    /** The camera's axes. */
    val basis: CameraBasis = cameraBasis(yaw, pitch)

    /** The scale at which the unzoomed board fits the view. */
    val fitScale: Float = fitScale(board, viewport)

    /** The distance of the unzoomed camera. */
    val fitDistance: Float = fitDistance(board)

    /** The closest the camera may come without entering the solid board. */
    val minimumDistance: Float = minDistance(board, yaw, pitch)

    /** The distance the camera actually stands at. */
    val distance: Float = distanceFromCentre.coerceIn(minimumDistance.coerceAtMost(fitDistance), fitDistance)

    /** The factor the board is enlarged by over its fitted size. */
    val zoom: Float = fitDistance / distance

    /** The scale from board cells to pixels. */
    val scale: Float = fitScale * zoom

    /** Returns a camera with another attitude and the same distance. */
    fun withAttitude(
        yawDegrees: Float,
        pitchDegrees: Float,
    ): OrbitCamera = OrbitCamera(board, viewport, yawDegrees, pitchDegrees, distance)

    /** Returns a camera at another distance, clamped to the range it may occupy. */
    fun withDistance(distanceFromCentre: Float): OrbitCamera = OrbitCamera(board, viewport, yaw, pitch, distanceFromCentre)

    /** Projects a board point onto the screen. */
    fun project(point: Vec3): ScreenPoint {
        val relative = point - board.centre
        val centre = viewport.centre
        return ScreenPoint(
            centre.x + scale * relative.dot(basis.right),
            centre.y - scale * relative.dot(basis.up),
        )
    }

    /** Returns how far along the view a board point lies; larger is farther. */
    fun depth(point: Vec3): Float = (point - board.centre).dot(basis.forward)

    /**
     * Returns the ray a screen point sees along.
     *
     * An orthographic camera has no eye point: every screen point names one line parallel to
     * the forward vector. The line is started outside the board's bounding sphere, so a pick
     * cannot begin in the middle of the box and miss a face behind the first cell.
     */
    fun ray(screen: ScreenPoint): Ray {
        val centre = viewport.centre
        val rightOffset = (screen.x - centre.x) / scale
        val upOffset = (screen.y - centre.y) / scale
        val point = board.centre + basis.right * rightOffset - basis.up * upOffset
        val start = point - basis.forward * (board.radius * 3f + 2f)
        return Ray(start, basis.forward)
    }
}

/** A line in board space: a start point and a unit direction. */
data class Ray(
    /** Where the line starts. */
    val origin: Vec3,
    /** Which way the line runs. */
    val direction: Vec3,
)

/**
 * Returns the block the ray hits first, or null when it misses the board.
 *
 * This is a three-dimensional DDA: the ray is stepped from one cell boundary to the next, in
 * whichever axis comes first, and every cell it enters is looked up. The walk is bounded by the
 * number of cells on the three axes plus the two entry cells, so a malformed ray cannot spin
 * forever. The occupancy array holds a block index per cell, or a negative value for a cell a
 * block has left.
 */
fun pickBlock(
    ray: Ray,
    board: BoardSize,
    occupancy: IntArray,
): Int? {
    val origin = ray.origin
    val direction = ray.direction
    var enter = 0f
    var exit = Float.MAX_VALUE
    for (axis in 0..2) {
        val position = when (axis) {
            0 -> origin.x
            1 -> origin.y
            else -> origin.z
        }
        val component = when (axis) {
            0 -> direction.x
            1 -> direction.y
            else -> direction.z
        }
        val size = when (axis) {
            0 -> board.width.toFloat()
            1 -> board.depth.toFloat()
            else -> board.height.toFloat()
        }
        if (abs(component) < EPSILON) {
            if (position < 0f || position > size) {
                return null
            }
        } else {
            var near = (0f - position) / component
            var far = (size - position) / component
            if (near > far) {
                val swap = near
                near = far
                far = swap
            }
            enter = max(enter, near)
            exit = min(exit, far)
        }
    }
    if (enter > exit) {
        return null
    }

    val start = enter + 1e-4f
    var cellX = floor(origin.x + direction.x * start).toInt()
    var cellY = floor(origin.y + direction.y * start).toInt()
    var cellZ = floor(origin.z + direction.z * start).toInt()
    val stepX = when {
        direction.x > EPSILON -> 1
        direction.x < -EPSILON -> -1
        else -> 0
    }
    val stepY = when {
        direction.y > EPSILON -> 1
        direction.y < -EPSILON -> -1
        else -> 0
    }
    val stepZ = when {
        direction.z > EPSILON -> 1
        direction.z < -EPSILON -> -1
        else -> 0
    }
    var maxX =
        if (stepX == 0) Float.MAX_VALUE else ((if (stepX > 0) cellX + 1 else cellX) - origin.x) / direction.x
    var maxY =
        if (stepY == 0) Float.MAX_VALUE else ((if (stepY > 0) cellY + 1 else cellY) - origin.y) / direction.y
    var maxZ =
        if (stepZ == 0) Float.MAX_VALUE else ((if (stepZ > 0) cellZ + 1 else cellZ) - origin.z) / direction.z
    val deltaX = if (stepX == 0) Float.MAX_VALUE else abs(1f / direction.x)
    val deltaY = if (stepY == 0) Float.MAX_VALUE else abs(1f / direction.y)
    val deltaZ = if (stepZ == 0) Float.MAX_VALUE else abs(1f / direction.z)

    repeat(board.width + board.depth + board.height + 3) {
        if (cellX !in 0 until board.width || cellY !in 0 until board.depth || cellZ !in 0 until board.height) {
            return null
        }
        val block = occupancy[cellX + board.width * (cellY + board.depth * cellZ)]
        if (block >= 0) {
            return block
        }
        when {
            maxX <= maxY && maxX <= maxZ -> {
                cellX += stepX
                maxX += deltaX
            }

            maxY <= maxZ -> {
                cellY += stepY
                maxY += deltaY
            }

            else -> {
                cellZ += stepZ
                maxZ += deltaZ
            }
        }
    }
    return null
}

/** The tolerance under which a vector or a component is treated as zero. */
private const val EPSILON = 1e-6f
