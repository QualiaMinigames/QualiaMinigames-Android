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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** How wide an edge shadow is, in dp. */
const val EDGE_SHADOW_DP = 28f

/**
 * Draws a band along every side of the view whose drawn rectangle is hidden behind it.
 *
 * The rectangle is the part of the board the player can see the extent of, which for a flat
 * board is the board itself and for a three-dimensional scene is the projected bounding box of
 * every face still in view. A side with nothing hidden draws nothing, so a board that fits the
 * view has no shadow at all: the band says "there is more in this direction", and a band over a
 * board that has no more would be a lie about the picture.
 *
 * The colour and the thickness are stated by the caller because the two boards draw at
 * different sizes: the flat boards use one colour per theme, and the three-dimensional scene
 * uses the same one so that a zoomed arrow cube reads like a zoomed nonogram.
 */
fun DrawScope.drawEdgeShadows(
    originX: Float,
    originY: Float,
    drawnWidth: Float,
    drawnHeight: Float,
    color: Color,
    thickness: Float,
) {
    val right = originX + drawnWidth
    val bottom = originY + drawnHeight
    // A pixel of slack: a drawn rectangle that fits exactly lands on the edge with a rounding
    // error, and a shadow that flickers on at every resize is worse than none.
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

/** Returns the thickness of an edge shadow in pixels. */
fun DrawScope.edgeShadowThickness(): Float = EDGE_SHADOW_DP.dp.toPx()
