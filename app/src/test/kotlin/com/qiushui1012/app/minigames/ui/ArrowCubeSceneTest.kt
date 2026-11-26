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
