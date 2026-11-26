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
    /**
     * The camera depth of the face's centre, which is what it is sorted by.
     *
     * A long face's farthest corner is farther than the face itself; using it as the key can
     * put a near two-cell face behind a face it actually covers. The mean of the corners is
     * the centre for a parallelogram and keeps a long face in its place.
     */
    val sortDepth: Float,
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
            faces.addAll(exposedFaces(board, block, occupancy))
        }
        return faces
    }

    /**
     * Returns the block in an arrow's path, or null when the way is clear.
     *
     * This is the read-only half of the take rule: the screen asks it before a tap so that a
     * blocked take can be recognised without counting a mistake, and only then lets the board
     * perform the take that records one.
     */
    fun blocker(
        board: BoardSize,
        block: ArrowCubeBlock,
        occupancy: IntArray,
    ): Int? {
        val cells = cellsOf(board, block)
        val positive = isPositive(block.direction)
        val axis = axisOf(block.direction)
        val front = if (positive) cells.last() else cells.first()
        val width = board.width
        val depth = board.depth
        var x = front % width
        var y = (front / width) % depth
        var z = front / (width * depth)
        val dx = if (axis == ArrowCubeAxis.X) (if (positive) 1 else -1) else 0
        val dy = if (axis == ArrowCubeAxis.Y) (if (positive) 1 else -1) else 0
        val dz = if (axis == ArrowCubeAxis.Z) (if (positive) 1 else -1) else 0
        while (true) {
            x += dx
            y += dy
            z += dz
            if (x !in 0 until board.width || y !in 0 until board.depth || z !in 0 until board.height) {
                return null
            }
            val occupant = occupancy[x + width * (y + depth * z)]
            if (occupant >= 0) {
                return occupant
            }
        }
    }

    /** Returns the faces of one block that the board leaves exposed. */
    fun exposedFaces(
        board: BoardSize,
        block: ArrowCubeBlock,
        occupancy: IntArray,
    ): List<ArrowCubeFace> {
        val cells = cellCoordinates(block)
        return normals.mapNotNull { normal ->
            if (isExposed(board, cells, normal, occupancy)) faceOf(block, normal) else null
        }
    }

    /**
     * Returns the faces a bumping block uncovers while it is away from its cells.
     *
     * A bump does not remove the block from the board, so the board's own face list still has
     * the neighbours covered by it. While the block is displaced, those neighbours look into
     * the slot the block left, and the faces around that slot have to be drawn or the player
     * sees through the block to whatever is behind it. The faces are the ones facing the slot,
     * which is the opposite of the direction from the slot to the neighbour.
     */
    fun cavityFaces(
        board: BoardSize,
        block: ArrowCubeBlock,
        blocks: List<ArrowCubeBlock>,
        occupancy: IntArray,
    ): List<ArrowCubeFace> {
        val faces = ArrayList<ArrowCubeFace>()
        val seen = HashSet<Pair<Int, Vec3>>()
        for ((x, y, z) in cellCoordinates(block)) {
            for (normal in normals) {
                val nx = x + normal.x.toInt()
                val ny = y + normal.y.toInt()
                val nz = z + normal.z.toInt()
                if (nx !in 0 until board.width || ny !in 0 until board.depth || nz !in 0 until board.height) {
                    continue
                }
                val occupant = occupancy[nx + board.width * (ny + board.depth * nz)]
                if (occupant < 0 || occupant == block.index) {
                    continue
                }
                val facing = normal * -1f
                // A long neighbour face that is already exposed through another cell is in the
                // board's face list as one whole quad; adding the same quad again would draw its
                // outline twice.
                if (isExposed(board, cellCoordinates(blocks[occupant]), facing, occupancy)) {
                    continue
                }
                if (seen.add(occupant to facing)) {
                    faces.add(faceOf(blocks[occupant], facing))
                }
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
     * The sort key is the camera depth of a face's centre. A face's farthest corner belongs to
     * the face but is farther than the face as a whole; a two-cell face can have a far corner
     * behind a smaller face that it actually covers, which would paint the smaller face on top.
     * The corners' mean is the centre for a parallelogram. Faces with the same depth are
     * ordered by their block index so that two frames of one board paint the same face in the
     * same order.
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
                ProjectedArrowCubeFace(
                    face = face,
                    corners = corners,
                    sortDepth = face.corners.map(camera::depth).average().toFloat(),
                )
            }
            .filter { face -> !isOutside(face.corners, camera.viewport) }
            .sortedWith(compareByDescending<ProjectedArrowCubeFace> { it.sortDepth }.thenBy { it.face.block })
            .toList()

    /**
     * Returns the nine points of an arrow on a face, in board space.
     *
     * The shape is the Material Icons arrow (`arrow_left_alt`), pointing along the arrow's
     * direction in the face plane. The head keeps the icon's proportions; a two-cell face
     * lengthens the shaft instead of stretching the head, so a long block reads as the same
     * arrow with more tail. The points are the icon's outline measured in face units, so a
     * one-cell face carries the whole arrow and a two-cell face carries a longer shaft.
     */
    fun decalPoints(face: ArrowCubeFace): List<Vec3> {
        val decal = face.decal ?: return emptyList()
        val centre = face.corners.reduce(Vec3::plus) * 0.25f
        val along = directionVector(decal.direction)
        return arrowOutline(decal.length).map { (u, v) ->
            centre + along * u + decal.across * v
        }
    }

    /** Returns the points of the arrow outline for a face that is [cells] cells long. */
    private fun arrowOutline(cells: Int): List<Pair<Float, Float>> {
        // A one-cell face keeps the icon's full reach. A longer face keeps the head as it is
        // and moves both ends outwards a little less than a whole cell, so the arrow keeps a
        // margin at the ends of the face instead of touching its neighbours.
        val reach = if (cells == 1) ARROW_TIP else ARROW_LONG_REACH_PER_CELL * cells
        val extension = reach - ARROW_TIP
        return ARROW_ICON.mapIndexed { index, (u, v) ->
            val tail = index == ARROW_TAIL_UPPER || index == ARROW_TAIL_LOWER
            if (tail) {
                -reach to v
            } else {
                u + extension to v
            }
        }
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
            sortDepth = face.corners.map(camera::depth).average().toFloat(),
        )

    /** Returns whether a direction runs towards larger coordinates on its axis. */
    private fun isPositive(direction: ArrowCubeDirection): Boolean =
        when (direction) {
            ArrowCubeDirection.East, ArrowCubeDirection.North, ArrowCubeDirection.Up -> true
            ArrowCubeDirection.West, ArrowCubeDirection.South, ArrowCubeDirection.Down -> false
        }

    /** Returns the axis a direction runs along. */
    private fun axisOf(direction: ArrowCubeDirection): ArrowCubeAxis =
        when (direction) {
            ArrowCubeDirection.East, ArrowCubeDirection.West -> ArrowCubeAxis.X
            ArrowCubeDirection.North, ArrowCubeDirection.South -> ArrowCubeAxis.Y
            ArrowCubeDirection.Up, ArrowCubeDirection.Down -> ArrowCubeAxis.Z
        }

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

    /** Returns whether a face of a block sees an empty cell or the edge beyond it. */
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
            val nx = x + dx
            val ny = y + dy
            val nz = z + dz
            if (nx !in 0 until board.width || ny !in 0 until board.depth || nz !in 0 until board.height) {
                return true
            }
            // The cell of a two-cell block on the other side of an internal face is occupied by
            // the same block, so only an empty or outside neighbour exposes the face.
            if (occupancy[nx + board.width * (ny + board.depth * nz)] < 0) {
                return true
            }
        }
        return false
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

    /** How many cells a taken block travels each second: 55% of the board's longest side. */
    fun flySpeed(board: BoardSize): Float = 0.55f * board.longestSide

    /** How many cells a blocked block travels each second: 40% of the board's longest side. */
    fun bumpSpeed(board: BoardSize): Float = 0.40f * board.longestSide
}

/**
 * The outline of Google's Material Icons `arrow_left_alt`, in face units, pointing along +u.
 *
 * The points are the icon's own path measured from the middle of its box and scaled so the
 * arrow is 0.8 of a one-cell face long, with the icon's proportions kept. The two tail points
 * (indices 5 and 6) are the only ones a longer face moves; the head and the points where the
 * shaft meets it keep the icon's shape, so a long block gets a longer shaft rather than a
 * stretched head.
 *
 * Material Icons is Copyright Google LLC and licensed under Apache License 2.0.
 */
private val ARROW_ICON =
    listOf(
        0.09922f to 0.30078f,
        0.40000f to 0f,
        0.09922f to -0.30078f,
        0.04555f to -0.24837f,
        0.25648f to -0.03744f,
        -0.40000f to -0.03744f,
        -0.40000f to 0.03744f,
        0.25648f to 0.03744f,
        0.04555f to 0.24837f,
    )

/** The distance from the centre to the tip of a one-cell arrow. */
private const val ARROW_TIP = 0.4f

/** The reach of one cell of a long face, which leaves a margin at both ends. */
private const val ARROW_LONG_REACH_PER_CELL = 0.36f

/** The index of the first tail corner, which a longer face moves backwards. */
private const val ARROW_TAIL_UPPER = 5

/** The index of the second tail corner, which a longer face moves backwards. */
private const val ARROW_TAIL_LOWER = 6
