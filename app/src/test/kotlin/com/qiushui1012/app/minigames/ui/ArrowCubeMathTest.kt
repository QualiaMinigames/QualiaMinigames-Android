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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arrow cube camera and picking arithmetic.
 *
 * The camera is the part of the scene that can be checked without drawing it: a projection is
 * a formula, and a formula that puts a corner in the wrong place is a tap that takes the wrong
 * block. The tests use the board's own corners and a few hand-computed projections rather than
 * goldens, because a golden would fail on a change of the margin and say nothing about why.
 */
class ArrowCubeMathTest {
    /** The board most tests use. */
    private val board = BoardSize(10, 10, 10)

    /** The view most tests use. */
    private val viewport = Viewport(1000f, 800f)

    /** Asserts that two floats agree to a small tolerance. */
    private fun assertClose(
        expected: Float,
        actual: Float,
        delta: Float = 1e-3f,
    ) {
        assertEquals(expected, actual, delta)
    }

    /** Asserts that a vector has no NaN component. */
    private fun assertFinite(vector: Vec3) {
        assertFalse("x is NaN", vector.x.isNaN())
        assertFalse("y is NaN", vector.y.isNaN())
        assertFalse("z is NaN", vector.z.isNaN())
    }

    @Test
    fun a_yaw_of_zero_looks_north_and_a_yaw_of_ninety_looks_west() {
        val north = cameraBasis(0f, 0f)
        assertClose(0f, north.forward.x)
        assertClose(1f, north.forward.y)
        assertClose(0f, north.forward.z)

        val west = cameraBasis(90f, 0f)
        assertClose(-1f, west.forward.x)
        assertClose(0f, west.forward.y)
        assertClose(0f, west.forward.z)
    }

    @Test
    fun a_pitch_of_ninety_degrees_produces_a_usable_basis() {
        for (pitch in listOf(90f, -90f)) {
            for (yaw in listOf(0f, 35f, 180f)) {
                val basis = cameraBasis(yaw, pitch)
                for (vector in listOf(basis.forward, basis.right, basis.up)) {
                    assertFinite(vector)
                    assertClose(1f, vector.length())
                }
                assertClose(0f, basis.forward.dot(basis.right))
                assertClose(0f, basis.forward.dot(basis.up))
                assertClose(0f, basis.right.dot(basis.up))
            }
        }
    }

    @Test
    fun a_pitch_of_ninety_degrees_lets_yaw_rotate_the_top_face_on_screen() {
        val facing = cameraBasis(0f, 90f)
        val turned = cameraBasis(90f, 90f)
        assertClose(-1f, facing.forward.z)
        assertClose(-1f, turned.forward.z)
        // Same view direction, different screen axes: this is what makes the top face spin as
        // the player turns the camera at the pole.
        assertFalse(facing.right.x == turned.right.x && facing.right.y == turned.right.y)
    }

    @Test
    fun the_board_centre_projects_to_the_centre_of_the_view() {
        val camera = OrbitCamera(board, viewport, 35f, 30f, 100f)
        val centre = camera.project(board.centre)
        assertClose(viewport.centre.x, centre.x)
        assertClose(viewport.centre.y, centre.y)
    }

    @Test
    fun a_known_corner_projects_where_the_formula_says() {
        // At yaw 0 and pitch 0 the camera looks north: the board's south-west-bottom corner is
        // to the left and below the centre, by the board's half extent times the fit scale.
        val camera = OrbitCamera(board, viewport, 0f, 0f, fitDistance(board))
        val point = camera.project(Vec3(0f, 0f, 0f))
        val scale = fitScale(board, viewport)
        assertClose(500f - 5f * scale, point.x)
        assertClose(400f + 5f * scale, point.y)
    }

    @Test
    fun an_angle_of_one_eighty_wraps_to_minus_one_eighty() {
        assertEquals(-180f, wrapDegrees(180f), 0f)
        assertEquals(-180f, wrapDegrees(-180f), 0f)
        assertEquals(0f, wrapDegrees(360f), 0f)
        assertEquals(170f, wrapDegrees(530f), 0f)
    }

    @Test
    fun a_pitch_beyond_the_poles_is_clamped() {
        assertEquals(90f, clampPitch(120f), 0f)
        assertEquals(-90f, clampPitch(-120f), 0f)
        assertEquals(45f, clampPitch(45f), 0f)
    }

    @Test
    fun the_camera_distance_never_exceeds_the_unzoomed_distance() {
        val camera = OrbitCamera(board, viewport, 35f, 30f, 10_000f)
        assertClose(camera.fitDistance, camera.distance)
        assertClose(1f, camera.zoom)
    }

    @Test
    fun the_camera_distance_never_enters_the_solid_board() {
        val camera = OrbitCamera(board, viewport, 35f, 30f, 0f)
        assertTrue(camera.distance >= minDistance(board, 35f, 30f))
    }

    @Test
    fun halving_the_distance_doubles_the_zoom() {
        val camera = OrbitCamera(board, viewport, 35f, 30f, fitDistance(board))
        assertEquals(1f, camera.zoom, 1e-4f)
        val closer = camera.withDistance(camera.distance / 2f)
        assertEquals(2f, closer.zoom, 1e-3f)
        assertClose(camera.distance / 2f, closer.distance)
    }

    @Test
    fun a_corner_keeps_its_place_under_a_pinch_about_the_centre() {
        // The two anchors of a pinch are the board's top-left and bottom-right corners. Under
        // the orthographic projection both are symmetric about the centre, so doubling the zoom
        // doubles the offset of both: they end up under fingers that moved apart by the same
        // ratio.
        val base = OrbitCamera(board, viewport, 35f, 30f, fitDistance(board))
        val corner = Vec3(0f, 0f, 0f)
        val first = base.project(corner)
        val doubled = base.withDistance(base.distance / 2f)
        val second = doubled.project(corner)
        assertClose(2f * (first.x - viewport.centre.x), second.x - viewport.centre.x)
        assertClose(2f * (first.y - viewport.centre.y), second.y - viewport.centre.y)
    }

    @Test
    fun a_ray_from_above_the_board_hits_its_top_cell() {
        val occupancy = IntArray(board.width * board.depth * board.height) { -1 }
        val top = 5 + board.width * (5 + board.depth * 9)
        occupancy[top] = 7
        val camera = OrbitCamera(board, viewport, 0f, 90f, fitDistance(board))
        val hit = pickBlock(camera.ray(viewport.centre), board, occupancy)
        assertEquals(7, hit)
    }

    @Test
    fun a_ray_from_the_side_hits_the_first_cell_it_meets() {
        val occupancy = IntArray(board.width * board.depth * board.height) { -1 }
        val near = 9 + board.width * (5 + board.depth * 5)
        occupancy[near] = 4
        val camera = OrbitCamera(board, viewport, 90f, 0f, fitDistance(board))
        assertEquals(4, pickBlock(camera.ray(viewport.centre), board, occupancy))
    }

    @Test
    fun a_ray_that_misses_the_board_returns_null() {
        val occupancy = IntArray(board.width * board.depth * board.height) { -1 }
        val camera = OrbitCamera(board, viewport, 35f, 30f, fitDistance(board))
        val miss = ScreenPoint(-10_000f, -10_000f)
        assertNull(pickBlock(camera.ray(miss), board, occupancy))
    }
}
