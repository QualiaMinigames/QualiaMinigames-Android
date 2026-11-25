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

import com.qiushui1012.app.minigames.rules.ArrowCubeAxis
import com.qiushui1012.app.minigames.rules.ArrowCubeBlock
import com.qiushui1012.app.minigames.rules.ArrowCubeDirection
import kotlin.math.abs
import kotlin.math.max

/** Which face of the block a quadrilateral is, as far as the colour is concerned. */
enum class ArrowCubeFaceTone {
    /** A face whose normal points up. */
    Top,

    /** A face whose normal is horizontal. */
    Side,

    /** A face whose normal points down. */
    Bottom,
}

/**
 * The arrow painted on a face.
 *
 * [length] is the extent of the face along the arrow, in cells: one for a one-cell block and
 * two for a two-cell block, whose long face carries one arrow stretched over both cells rather
 * than a short arrow on each. [across] is the unit direction perpendicular to the arrow inside
 * the face, which is what the head's two wings are measured along.
 */
data class ArrowCubeDecal(
    /** The direction the arrow points. */
    val direction: ArrowCubeDirection,
    /** The extent of the face along the arrow, in cells. */
    val length: Int,
    /** The unit direction across the arrow in the face plane. */
    val across: Vec3,
)

/**
 * One exposed quadrilateral of one block.
 *
 * A face is kept only when at least one cell behind it is empty or outside the board: a face
 * completely covered by another block is never generated, which is what keeps a full 30 by 30
 * by 30 board at the six outer walls rather than at six faces per block.
 */
data class ArrowCubeFace(
    /** The index of the block the face belongs to. */
    val block: Int,
    /** The four corners, in board cells. */
    val corners: List<Vec3>,
    /** The outward normal. */
    val normal: Vec3,
    /** Which colour band the face belongs to. */
    val tone: ArrowCubeFaceTone,
    /** The arrow painted on it, or null when the arrow is not perpendicular to the face. */
    val decal: ArrowCubeDecal?,
)

/** A face after the camera has projected and culled it. */
data class ProjectedArrowCubeFace(
    /** The face the projection came from. */
    val face: ArrowCubeFace,
    /** Its four corners on the screen. */
    val corners: List<ScreenPoint>,
    /** The largest camera depth of its corners, which is what it is sorted by. */
    val maxDepth: Float,
)

/**
 * The faces of an arrow cube board: building them, culling them and painting their arrows.
 *
 * The order of work is the order the release plan states. The occupancy grid says which faces
 * are on the surface; the view direction removes the ones facing away; the projection removes
 * the ones entirely outside the view; only then are the survivors sorted and drawn. A face
 * that is covered, facing away, or off screen never reaches the painter.
 */
object ArrowCubeScene {
    /** The outward normals of a box's six faces. */
    private val normals =
        listOf(
            Vec3(1f, 0f, 0f),
            Vec3(-1f, 0f, 0f),
            Vec3(0f, 1f, 0f),
            Vec3(0f, -1f, 0f),
            Vec3(0f, 0f, 1f),
            Vec3(0f, 0f, -1f),
        )

    /** Returns the block index of every cell, or -1 for a cell no block occupies. */
    fun occupancyOf(
        board: BoardSize,
        blocks: List<ArrowCubeBlock>,
    ): IntArray {
        val occupancy = IntArray(board.width * board.depth * board.height) { -1 }
        for (block in blocks) {
            if (block.removed) {
                continue
            }
            for (cell in cellsOf(board, block)) {
                occupancy[cell] = block.index
            }
        }
        return occupancy
    }

    /** Returns the flat cells a block covers. */
    fun cellsOf(
        board: BoardSize,
        block: ArrowCubeBlock,
    ): List<Int> {
        val cell = block.x + board.width * (block.y + board.depth * block.z)
        return if (block.length == 2) {
            listOf(cell, cell + stepIndex(board, block.axis))
        } else {
            listOf(cell)
        }
    }

    /** Returns the face of every block that the board leaves exposed. */
    fun buildFaces(
        board: BoardSize,
        blocks: List<ArrowCubeBlock>,
        occupancy: IntArray,
    ): List<ArrowCubeFace> {
        val faces = ArrayList<ArrowCubeFace>()
        for (block in blocks) {
            if (block.removed) {
                continue
            }
            val cells = cellCoordinates(block)
            for (normal in normals) {
                if (!isExposed(board, cells, normal, occupancy)) {
                    continue
                }
                faces.add(faceOf(block, normal))
            }
        }
        return faces
    }

    /**
     * Returns all six faces of a block, whether or not the board covers them.
     *
     * This is what an animation draws: a block that is leaving its place is no longer covered
     * by its neighbours, so the faces it showed while it stood still are not the whole block.
     */
    fun blockFaces(block: ArrowCubeBlock): List<ArrowCubeFace> = normals.map { normal -> faceOf(block, normal) }

    /** Returns one face of a block, with the arrow it carries. */
    private fun faceOf(
        block: ArrowCubeBlock,
        normal: Vec3,
    ): ArrowCubeFace {
        val minX = block.x
        val minY = block.y
        val minZ = block.z
        val maxX = minX + if (block.axis == ArrowCubeAxis.X) block.length else 1
        val maxY = minY + if (block.axis == ArrowCubeAxis.Y) block.length else 1
        val maxZ = minZ + if (block.axis == ArrowCubeAxis.Z) block.length else 1
        val direction = directionVector(block.direction)
        val decal =
            if (abs(normal.dot(direction)) < 1e-6f) {
                ArrowCubeDecal(
                    direction = block.direction,
                    length = block.length,
                    across = normal.cross(direction).normalized(),
                )
            } else {
                null
            }
        return ArrowCubeFace(
            block = block.index,
            corners = cornersOf(minX, minY, minZ, maxX, maxY, maxZ, normal),
            normal = normal,
            tone = toneOf(normal),
            decal = decal,
        )
    }

    /**
     * Returns the faces that survive back-face and viewport culling, sorted back to front.
     *
     * The sort key is the largest camera depth of a face's corners, which is the corner nearest
     * the camera in an orthographic view; faces with the same depth are ordered by their block
     * index so that two frames of one board paint the same face in the same order.
     */
    fun visibleFaces(
        faces: List<ArrowCubeFace>,
        camera: OrbitCamera,
    ): List<ProjectedArrowCubeFace> =
        faces
            .asSequence()
            .filter { it.normal.dot(camera.basis.forward) < 0f }
            .map { face ->
                val corners = face.corners.map(camera::project)
                val depth = face.corners.maxOf(camera::depth)
                ProjectedArrowCubeFace(face = face, corners = corners, maxDepth = depth)
            }
            .filter { face -> !isOutside(face.corners, camera.viewport) }
            .sortedWith(compareByDescending<ProjectedArrowCubeFace> { it.maxDepth }.thenBy { it.face.block })
            .toList()

    /**
     * Returns the seven points of an arrow on a face, in board space.
     *
     * The polygon is a shaft with two wings: the tail, the two corners where the head starts,
     * the two wing tips and the point. The tail and the tip are measured along the arrow's
     * direction from the face's centre; the wings are measured across it. The values are the
     * proportions the release plan fixes, so an arrow on a one-cell face keeps its shape on a
     * two-cell face by being measured against the face's own long extent.
     */
    fun decalPoints(face: ArrowCubeFace): List<Vec3> {
        val decal = face.decal ?: return emptyList()
        val centre = face.corners.reduce(Vec3::plus) * 0.25f
        val along = directionVector(decal.direction)
        val length = decal.length.toFloat()
        val shape =
            listOf(
                -0.38f * length to -0.07f,
                0.10f * length to -0.07f,
                0.10f * length to -0.22f,
                0.40f * length to 0f,
                0.10f * length to 0.22f,
                0.10f * length to 0.07f,
                -0.38f * length to 0.07f,
            )
        return shape.map { (u, v) -> centre + along * u + decal.across * v }
    }

    /** Returns a copy of a face whose corners have been moved by an offset. */
    fun withOffset(
        face: ArrowCubeFace,
        offset: Vec3,
    ): ArrowCubeFace = face.copy(corners = face.corners.map { it + offset })

    /** Returns a projected face from a moved face, without culling it again. */
    fun project(
        face: ArrowCubeFace,
        camera: OrbitCamera,
    ): ProjectedArrowCubeFace =
        ProjectedArrowCubeFace(
            face = face,
            corners = face.corners.map(camera::project),
            maxDepth = face.corners.maxOf(camera::depth),
        )

    /** Returns the flat step between the two cells of a block on an axis. */
    private fun stepIndex(
        board: BoardSize,
        axis: ArrowCubeAxis,
    ): Int =
        when (axis) {
            ArrowCubeAxis.X -> 1
            ArrowCubeAxis.Y -> board.width
            ArrowCubeAxis.Z -> board.width * board.depth
        }

    /** Returns the coordinates of every cell of a block. */
    private fun cellCoordinates(block: ArrowCubeBlock): List<Triple<Int, Int, Int>> {
        val first = Triple(block.x, block.y, block.z)
        if (block.length != 2) {
            return listOf(first)
        }
        val second =
            when (block.axis) {
                ArrowCubeAxis.X -> Triple(block.x + 1, block.y, block.z)
                ArrowCubeAxis.Y -> Triple(block.x, block.y + 1, block.z)
                ArrowCubeAxis.Z -> Triple(block.x, block.y, block.z + 1)
            }
        return listOf(first, second)
    }

    /** Returns whether any cell of a block on a face sees an empty cell or the edge beyond it. */
    private fun isExposed(
        board: BoardSize,
        cells: List<Triple<Int, Int, Int>>,
        normal: Vec3,
        occupancy: IntArray,
    ): Boolean {
        val dx = normal.x.toInt()
        val dy = normal.y.toInt()
        val dz = normal.z.toInt()
        for ((x, y, z) in cells) {
            if (!onSide(cells, Triple(x, y, z), dx, dy, dz)) {
                continue
            }
            val nx = x + dx
            val ny = y + dy
            val nz = z + dz
            if (nx !in 0 until board.width || ny !in 0 until board.depth || nz !in 0 until board.height) {
                return true
            }
            if (occupancy[nx + board.width * (ny + board.depth * nz)] < 0) {
                return true
            }
        }
        return false
    }

    /** Returns whether a cell of a block lies on the face with the given outward normal. */
    private fun onSide(
        cells: List<Triple<Int, Int, Int>>,
        cell: Triple<Int, Int, Int>,
        dx: Int,
        dy: Int,
        dz: Int,
    ): Boolean {
        val (x, y, z) = cell
        return when {
            dx > 0 -> cells.none { it.first > x }
            dx < 0 -> cells.none { it.first < x }
            dy > 0 -> cells.none { it.second > y }
            dy < 0 -> cells.none { it.second < y }
            dz > 0 -> cells.none { it.third > z }
            dz < 0 -> cells.none { it.third < z }
            else -> false
        }
    }

    /** Returns the four corners of a box face, in a fixed order. */
    private fun cornersOf(
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int,
        normal: Vec3,
    ): List<Vec3> {
        val x0 = minX.toFloat()
        val y0 = minY.toFloat()
        val z0 = minZ.toFloat()
        val x1 = maxX.toFloat()
        val y1 = maxY.toFloat()
        val z1 = maxZ.toFloat()
        return when {
            normal.x > 0f -> listOf(Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x1, y0, z1))
            normal.x < 0f -> listOf(Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0))
            normal.y > 0f -> listOf(Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0))
            normal.y < 0f -> listOf(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1))
            normal.z > 0f -> listOf(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1))
            else -> listOf(Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y0, z0))
        }
    }

    /** Returns the colour band a face belongs to. */
    private fun toneOf(normal: Vec3): ArrowCubeFaceTone =
        when {
            normal.z > 0f -> ArrowCubeFaceTone.Top
            normal.z < 0f -> ArrowCubeFaceTone.Bottom
            else -> ArrowCubeFaceTone.Side
        }

    /** Returns whether every corner of a projected face lies past the same edge of the view. */
    private fun isOutside(
        corners: List<ScreenPoint>,
        viewport: Viewport,
    ): Boolean {
        if (corners.isEmpty()) {
            return true
        }
        val left = corners.all { it.x < 0f }
        val right = corners.all { it.x > viewport.width }
        val top = corners.all { it.y < 0f }
        val bottom = corners.all { it.y > viewport.height }
        return left || right || top || bottom
    }

    /** The longest side of a board, used by the animation to fly a block out of the view. */
    fun flyDistance(board: BoardSize): Float = max(board.radius * 2f, 4f * board.longestSide) + 2f
}
