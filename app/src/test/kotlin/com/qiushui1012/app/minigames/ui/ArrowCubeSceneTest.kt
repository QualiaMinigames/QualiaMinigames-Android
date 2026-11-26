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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The arrow cube faces: which ones exist, which ones survive the camera, and what their arrows
 * look like.
 *
 * The counts are the promise of the culling order: a full box shows six faces per outer cell
 * and nothing inside, and a block that was covered gains the six faces its removal exposes.
 * The arrow checks are about geometry rather than about pixels, because a decal in the wrong
 * place is a wrong direction, not a cosmetic defect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArrowCubeSceneTest {
    /** Returns a block of one cell with an arbitrary direction. */
    private fun single(
        board: BoardSize,
        index: Int,
        x: Int,
        y: Int,
        z: Int,
        direction: ArrowCubeDirection = ArrowCubeDirection.East,
    ): ArrowCubeBlock =
        ArrowCubeBlock(
            index = index,
            x = x,
            y = y,
            z = z,
            axis = ArrowCubeAxis.X,
            length = 1,
            direction = direction,
            removed = false,
        )

    /** Returns the faces of a board with the given blocks. */
    private fun faces(
        board: BoardSize,
        blocks: List<ArrowCubeBlock>,
    ): List<ArrowCubeFace> {
        val occupancy = ArrowCubeScene.occupancyOf(board, blocks)
        return ArrowCubeScene.buildFaces(board, blocks, occupancy)
    }

    @Test
    fun a_single_cell_block_has_six_faces_and_four_arrows() {
        val board = BoardSize(5, 5, 5)
        val block = single(board, 0, 2, 2, 2, ArrowCubeDirection.North)
        val all = faces(board, listOf(block))
        assertEquals(6, all.size)
        assertEquals(4, all.count { it.decal != null })
        assertNotNull(all.firstOrNull { it.tone == ArrowCubeFaceTone.Top })
        assertNotNull(all.firstOrNull { it.tone == ArrowCubeFaceTone.Bottom })
    }

    @Test
    fun a_two_cell_block_has_six_faces_and_four_long_arrows() {
        val board = BoardSize(5, 5, 5)
        val block =
            ArrowCubeBlock(
                index = 0,
                x = 2,
                y = 2,
                z = 2,
                axis = ArrowCubeAxis.X,
                length = 2,
                direction = ArrowCubeDirection.East,
                removed = false,
            )
        val all = faces(board, listOf(block))
        assertEquals(6, all.size)
        val arrows = all.filter { it.decal != null }
        assertEquals(4, arrows.size)
        assertTrue("every arrow face is a long face", arrows.all { it.decal?.length == 2 })
        for (face in arrows) {
            val decal = requireNotNull(face.decal)
            assertEquals(0f, face.normal.dot(directionVector(decal.direction)), 1e-6f)
        }
    }

    @Test
    fun a_full_box_shows_only_its_outer_faces() {
        // Thirty cells on a side as thirty thousand one-cell blocks: six faces per block would
        // be 162,000 quads, and the outer surface alone is 5,400. The count is the whole point
        // of the occupancy pass.
        val board = BoardSize(30, 30, 30)
        val blocks =
            (0 until board.width * board.depth * board.height).map { cell ->
                val x = cell % board.width
                val y = (cell / board.width) % board.depth
                val z = cell / (board.width * board.depth)
                single(board, cell, x, y, z)
            }
        val all = faces(board, blocks)
        assertEquals(6 * 30 * 30, all.size)
    }

    @Test
    fun taking_an_inner_block_out_exposes_six_faces() {
        val board = BoardSize(5, 5, 5)
        val blocks =
            (0 until board.width * board.depth * board.height).map { cell ->
                val x = cell % board.width
                val y = (cell / board.width) % board.depth
                val z = cell / (board.width * board.depth)
                single(board, cell, x, y, z)
            }
        val before = faces(board, blocks)
        assertEquals(6 * 5 * 5, before.size)
        val centre = 2 + board.width * (2 + board.depth * 2)
        val removed = blocks.toMutableList().also { it[centre] = it[centre].copy(removed = true) }
        val after = faces(board, removed)
        assertEquals("six new faces, and the removed block's own faces are gone", 6 * 5 * 5 + 6, after.size)
    }

    @Test
    fun only_faces_turned_towards_the_camera_survive_the_back_face_pass() {
        val board = BoardSize(10, 10, 10)
        val blocks =
            (0 until board.width * board.depth * board.height).map { cell ->
                val x = cell % board.width
                val y = (cell / board.width) % board.depth
                val z = cell / (board.width * board.depth)
                single(board, cell, x, y, z)
            }
        val all = faces(board, blocks)
        val camera = OrbitCamera(board, Viewport(1000f, 800f), 35f, 30f, 1_000f)
        val visible = ArrowCubeScene.visibleFaces(all, camera)
        assertTrue("the culling kept nothing", visible.isNotEmpty())
        assertTrue("a face turned away survived", visible.all { it.face.normal.dot(camera.basis.forward) < 0f })
        assertTrue("the culling kept the whole box", visible.size < all.size)
    }

    @Test
    fun faces_entirely_outside_the_view_are_removed() {
        val board = BoardSize(10, 10, 10)
        val blocks =
            (0 until board.width * board.depth * board.height).map { cell ->
                val x = cell % board.width
                val y = (cell / board.width) % board.depth
                val z = cell / (board.width * board.depth)
                single(board, cell, x, y, z)
            }
        val all = faces(board, blocks)
        val camera = OrbitCamera(board, Viewport(160f, 160f), 35f, 30f, 0f)
        val visible = ArrowCubeScene.visibleFaces(all, camera)
        assertTrue(visible.isNotEmpty())
        assertTrue(
            "a face outside the view survived",
            visible.all { face ->
                val left = face.corners.minOf { it.x }
                val right = face.corners.maxOf { it.x }
                val top = face.corners.minOf { it.y }
                val bottom = face.corners.maxOf { it.y }
                left <= 160f && right >= 0f && top <= 160f && bottom >= 0f
            },
        )
        assertTrue("the viewport cull removed nothing", visible.size < all.size)
    }

    @Test
    fun a_long_arrow_extends_only_its_shaft() {
        // The same Material arrow is used on both faces. A two-cell face must move the tail
        // backwards and leave the head untouched: a stretched head is a different symbol, and
        // the shaft is what the extra cell is there to lengthen.
        val board = BoardSize(5, 5, 5)
        val single =
            ArrowCubeBlock(
                index = 0,
                x = 2,
                y = 2,
                z = 2,
                axis = ArrowCubeAxis.X,
                length = 1,
                direction = ArrowCubeDirection.North,
                removed = false,
            )
        val long =
            ArrowCubeBlock(
                index = 0,
                x = 2,
                y = 2,
                z = 2,
                axis = ArrowCubeAxis.X,
                length = 2,
                direction = ArrowCubeDirection.East,
                removed = false,
            )

        fun coordinates(block: ArrowCubeBlock): List<Pair<Float, Float>> {
            val face =
                ArrowCubeScene
                    .buildFaces(board, listOf(block), ArrowCubeScene.occupancyOf(board, listOf(block)))
                    .first { it.normal.z > 0f }
            val decal = requireNotNull(face.decal)
            val centre = face.corners.reduce(Vec3::plus) * 0.25f
            val along = directionVector(decal.direction)
            val across = face.normal.cross(along)
            return ArrowCubeScene.decalPoints(face).map { point ->
                val relative = point - centre
                relative.dot(along) to relative.dot(across)
            }
        }

        val one = coordinates(single)
        val two = coordinates(long)
        assertEquals(9, one.size)
        assertEquals(9, two.size)

        val tipOne = one.maxOf { it.first }
        val tipTwo = two.maxOf { it.first }
        val wingOne = one.maxBy { it.second }
        val wingTwo = two.maxBy { it.second }
        assertEquals("the head keeps its length", tipOne - wingOne.first, tipTwo - wingTwo.first, 1e-4f)
        assertEquals(
            "the head keeps its width",
            wingOne.second - one.minOf { it.second },
            wingTwo.second - two.minOf { it.second },
            1e-4f,
        )

        val tailOne = one.filter { it.first < 0f }
        val tailTwo = two.filter { it.first < 0f }
        assertEquals(2, tailOne.size)
        assertEquals(2, tailTwo.size)
        assertEquals(
            "the shaft keeps its width",
            tailOne.maxOf { it.second } - tailOne.minOf { it.second },
            tailTwo.maxOf { it.second } - tailTwo.minOf { it.second },
            1e-4f,
        )
        assertEquals(0.8f, tipOne - one.minOf { it.first }, 1e-4f)
        assertEquals(1.44f, tipTwo - two.minOf { it.first }, 1e-4f)
        assertTrue(
            "a one-cell arrow stays inside its face",
            one.all { abs(it.first) <= 0.5f + 1e-4f && abs(it.second) <= 0.5f + 1e-4f },
        )
        assertTrue(
            "a two-cell arrow stays inside its face",
            two.all { abs(it.first) <= 1f + 1e-4f && abs(it.second) <= 0.5f + 1e-4f },
        )
    }

    @Test
    fun the_blocker_read_agrees_with_the_game_take() {
        val board = BoardSize(5, 5, 5)
        val rules = requireNotNull(com.qiushui1012.app.minigames.rules.Rules.newArrowCube(5, 5, 5, 11))
        val blocks = rules.blocks()
        val occupancy = ArrowCubeScene.occupancyOf(board, blocks)
        for (index in blocks.indices) {
            val fresh = requireNotNull(com.qiushui1012.app.minigames.rules.Rules.newArrowCube(5, 5, 5, 11))
            val freshBlocks = fresh.blocks()
            val taken = fresh.take(index)
            val read = ArrowCubeScene.blocker(board, freshBlocks[index], occupancy)
            assertEquals(
                "block $index",
                taken.blocker,
                read,
            )
        }
    }

    @Test
    fun the_blocker_read_follows_the_arrow_from_the_front_cell() {
        val board = BoardSize(5, 5, 5)
        val east = single(board, 0, 2, 2, 2, ArrowCubeDirection.East)
        val farEast = single(board, 1, 4, 2, 2, ArrowCubeDirection.East)
        val west = single(board, 0, 3, 2, 2, ArrowCubeDirection.West)
        val farWest = single(board, 1, 1, 2, 2, ArrowCubeDirection.West)
        val longEast =
            ArrowCubeBlock(
                index = 0,
                x = 1,
                y = 2,
                z = 2,
                axis = ArrowCubeAxis.X,
                length = 2,
                direction = ArrowCubeDirection.East,
                removed = false,
            )
        val longBlocker = single(board, 1, 4, 2, 2, ArrowCubeDirection.East)

        val eastOccupancy = ArrowCubeScene.occupancyOf(board, listOf(east, farEast))
        assertEquals(farEast.index, ArrowCubeScene.blocker(board, east, eastOccupancy))
        val westOccupancy = ArrowCubeScene.occupancyOf(board, listOf(west, farWest))
        assertEquals(farWest.index, ArrowCubeScene.blocker(board, west, westOccupancy))
        val longOccupancy = ArrowCubeScene.occupancyOf(board, listOf(longEast, longBlocker))
        assertEquals(longBlocker.index, ArrowCubeScene.blocker(board, longEast, longOccupancy))
        assertEquals(null, ArrowCubeScene.blocker(board, east, ArrowCubeScene.occupancyOf(board, listOf(east))))

        // A one-cell block may point along an axis other than its arbitrary stored axis: the
        // read has to follow the arrow, not that field.
        val north = single(board, 0, 2, 2, 2, ArrowCubeDirection.North)
        val farNorth = single(board, 1, 2, 4, 2, ArrowCubeDirection.North)
        assertEquals(
            farNorth.index,
            ArrowCubeScene.blocker(board, north, ArrowCubeScene.occupancyOf(board, listOf(north, farNorth))),
        )
    }

    @Test
    fun a_face_that_crosses_the_view_is_never_culled_by_the_viewport() {
        // Zooming in makes faces larger than the screen: a front-facing face that covers a
        // screen corner, or has a corner on screen, is visible and must survive the viewport
        // pass even though all four of its own corners may lie outside the screen.
        val board = BoardSize(10, 10, 10)
        val blocks =
            (0 until board.width * board.depth * board.height).map { cell ->
                val x = cell % board.width
                val y = (cell / board.width) % board.depth
                val z = cell / (board.width * board.depth)
                single(board, cell, x, y, z)
            }
        val all = faces(board, blocks)
        val camera = OrbitCamera(board, Viewport(1220f, 2350f), 35f, 30f, 1f)
        val visible = ArrowCubeScene.visibleFaces(all, camera).map { it.face.block to it.face.normal }.toSet()
        val viewportCorners =
            listOf(
                ScreenPoint(0f, 0f),
                ScreenPoint(1220f, 0f),
                ScreenPoint(1220f, 2350f),
                ScreenPoint(0f, 2350f),
            )

        fun insideQuad(
            point: ScreenPoint,
            quad: List<ScreenPoint>,
        ): Boolean {
            var sign = 0
            for (index in quad.indices) {
                val a = quad[index]
                val b = quad[(index + 1) % quad.size]
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

        for (face in all.filter { it.normal.dot(camera.basis.forward) < 0f }) {
            val corners = face.corners.map(camera::project)
            val hasCornerOnScreen =
                corners.any { it.x in 0f..1220f && it.y in 0f..2350f }
            val coversScreenCorner = viewportCorners.any { insideQuad(it, corners) }
            if (hasCornerOnScreen || coversScreenCorner) {
                assertTrue(
                    "block ${face.block} lost a face that crosses the view",
                    (face.block to face.normal) in visible,
                )
            }
        }
    }

    @Test
    fun the_face_list_matches_an_independent_cell_scan() {
        // The exposed set is defined by the cells, not by the block-level shortcut: a side of
        // a cell that looks at nothing is exposed, and a side that looks at another block is
        // not. This checks the shortcut against that definition over several random positions.
        val board = BoardSize(5, 5, 5)
        val normals =
            listOf(
                Vec3(1f, 0f, 0f),
                Vec3(-1f, 0f, 0f),
                Vec3(0f, 1f, 0f),
                Vec3(0f, -1f, 0f),
                Vec3(0f, 0f, 1f),
                Vec3(0f, 0f, -1f),
            )
        for (seed in 0L until 8L) {
            val game = requireNotNull(com.qiushui1012.app.minigames.rules.Rules.newArrowCube(5, 5, 5, seed))
            val blocks = game.blocks()
            val occupancy = ArrowCubeScene.occupancyOf(board, blocks)
            val expected = mutableSetOf<Pair<Int, Vec3>>()
            for (z in 0 until board.height) {
                for (y in 0 until board.depth) {
                    for (x in 0 until board.width) {
                        val cell = x + board.width * (y + board.depth * z)
                        val occupant = occupancy[cell]
                        if (occupant < 0) {
                            continue
                        }
                        for (normal in normals) {
                            val nx = x + normal.x.toInt()
                            val ny = y + normal.y.toInt()
                            val nz = z + normal.z.toInt()
                            val outside =
                                nx !in 0 until board.width ||
                                    ny !in 0 until board.depth ||
                                    nz !in 0 until board.height
                            val neighbour = if (outside) -1 else occupancy[nx + board.width * (ny + board.depth * nz)]
                            if (outside || neighbour < 0) {
                                expected.add(occupant to normal)
                            }
                        }
                    }
                }
            }
            val actual =
                ArrowCubeScene
                    .buildFaces(board, blocks, occupancy)
                    .map { it.block to it.normal }
                    .toSet()
            assertEquals("seed $seed", expected, actual)
        }
    }

    @Test
    fun a_long_face_is_exposed_when_only_one_of_its_cells_is_uncovered() {
        val board = BoardSize(5, 5, 5)
        val long =
            ArrowCubeBlock(
                index = 0,
                x = 1,
                y = 1,
                z = 1,
                axis = ArrowCubeAxis.X,
                length = 2,
                direction = ArrowCubeDirection.East,
                removed = false,
            )
        val aboveSecond = single(board, 1, 2, 1, 2)
        val occupancy = ArrowCubeScene.occupancyOf(board, listOf(long, aboveSecond))
        val faces = ArrowCubeScene.buildFaces(board, listOf(long, aboveSecond), occupancy)
        assertTrue(
            "the long top face is exposed through its first cell",
            faces.any { it.block == long.index && it.normal == Vec3(0f, 0f, 1f) },
        )
    }

    @Test
    fun every_exposed_cell_boundary_has_a_face_after_a_take() {
        // The face list is rebuilt from occupancy after a take. Every side of every remaining
        // cell that looks at an empty cell or out of the board has to be in that list, including
        // the side of a two-cell block that is only exposed through one of its cells.
        val board = BoardSize(5, 5, 5)
        val game = requireNotNull(com.qiushui1012.app.minigames.rules.Rules.newArrowCube(5, 5, 5, 7))
        var blocks = game.blocks()
        val occupancy = ArrowCubeScene.occupancyOf(board, blocks)
        val normals =
            listOf(
                Vec3(1f, 0f, 0f),
                Vec3(-1f, 0f, 0f),
                Vec3(0f, 1f, 0f),
                Vec3(0f, -1f, 0f),
                Vec3(0f, 0f, 1f),
                Vec3(0f, 0f, -1f),
            )

        fun check() {
            val present = ArrowCubeScene.buildFaces(board, blocks, occupancy).map { it.block to it.normal }.toSet()
            for (z in 0 until board.height) {
                for (y in 0 until board.depth) {
                    for (x in 0 until board.width) {
                        val cell = x + board.width * (y + board.depth * z)
                        val occupant = occupancy[cell]
                        if (occupant < 0) {
                            continue
                        }
                        for (normal in normals) {
                            val nx = x + normal.x.toInt()
                            val ny = y + normal.y.toInt()
                            val nz = z + normal.z.toInt()
                            val outside =
                                nx !in 0 until board.width ||
                                    ny !in 0 until board.depth ||
                                    nz !in 0 until board.height
                            val empty =
                                !outside &&
                                    occupancy[nx + board.width * (ny + board.depth * nz)] < 0
                            if (outside || empty) {
                                assertTrue(
                                    "cell $cell block $occupant lost its $normal face",
                                    (occupant to normal) in present,
                                )
                            }
                        }
                    }
                }
            }
        }

        check()
        val taken =
            (0 until blocks.size).firstNotNullOfOrNull { index ->
                val result = game.take(index)
                if (result.removed) index else null
            }
        val index = requireNotNull(taken)
        blocks = blocks.mapIndexed { slot, block -> if (slot == index) block.copy(removed = true) else block }
        for (cell in ArrowCubeScene.cellsOf(board, game.blocks()[index])) {
            occupancy[cell] = -1
        }
        check()
    }

    @Test
    fun a_bumping_block_uncovers_the_faces_around_its_slot() {
        // A bump does not remove the block, so the board's own list still hides the walls of
        // the slot it leaves. Those walls have to be added, or the player looks through the
        // moving block into the blocks behind it.
        val board = BoardSize(5, 5, 5)
        val blocks =
            (0 until board.width * board.depth * board.height)
                .filter { it != 2 + board.width * (2 + board.depth * 2) + 1 }
                .mapIndexed { index, cell ->
                    val x = cell % board.width
                    val y = (cell / board.width) % board.depth
                    val z = cell / (board.width * board.depth)
                    single(board, index, x, y, z)
                }
        val occupancy = ArrowCubeScene.occupancyOf(board, blocks)
        val centre = blocks.first { it.x == 2 && it.y == 2 && it.z == 2 }
        val cavity = ArrowCubeScene.cavityFaces(board, centre, blocks, occupancy)
        assertEquals("five walls remain, the open side has no face", 5, cavity.size)
        for (face in cavity) {
            val neighbour = face.corners.reduce(Vec3::plus) * 0.25f
            assertTrue(
                "a cavity face points into the slot",
                (neighbour - Vec3(2.5f, 2.5f, 2.5f)).dot(face.normal) < 0f,
            )
        }
    }

    @Test
    fun the_arrow_of_a_face_is_perpendicular_to_its_normal() {
        val board = BoardSize(5, 5, 5)
        val block = single(board, 0, 2, 2, 2, ArrowCubeDirection.Up)
        val all = faces(board, listOf(block))
        for (face in all) {
            val decal = face.decal ?: continue
            assertEquals(0f, abs(face.normal.dot(directionVector(decal.direction))), 1e-6f)
        }
    }
}
