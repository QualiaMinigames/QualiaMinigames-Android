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

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.qiushui1012.app.minigames.R

/**
 * The face and the placement every board's numbers use.
 *
 * # One face for four boards
 *
 * Minesweeper, sudoku, nonogram and hashi all write numbers on their cells, and a board whose
 * digits were set in a different face from the board beside it read as a different application.
 * The face is therefore loaded here, once per [Context], and every board asks this object for
 * it rather than reaching for the font resource itself.
 *
 * # Why the ink is measured
 *
 * A glyph is drawn from its baseline and its advance, and neither is the middle of the shape a
 * player sees: a face reserves room above its capitals for accents and below its baseline for
 * descenders, and a digit uses neither. A digit centred on its advance therefore sits high and
 * wide of the middle of its cell. The placement here measures the ink's own box and returns the
 * offset that puts that box on the cell's centre.
 *
 * # Why the measurement is scaled
 *
 * Text bounds come back in whole pixels of whatever size the paint holds. Measured at the size
 * a board draws at, which is a fraction of one board unit, one pixel of error is most of a cell;
 * measured at [MEASURING_TEXT_SIZE] and scaled down, the rounding is far below what a screen can
 * show. This is the reason minesweeper grew the same code in the first place.
 */
object BoardText {
    /** The size a glyph's ink is measured at, in board units. */
    const val MEASURING_TEXT_SIZE = 100f

    /** The faces, once per context: loading a font file per frame would be absurd. */
    private val faces = java.util.concurrent.ConcurrentHashMap<Context, Typeface>()

    /**
     * Returns the face the boards write their numbers in.
     *
     * A build without the font resource falls back to the platform's monospaced face rather
     * than to the default one: a board of numbers is read by comparing them, and a
     * proportional face makes two rows of the same length take different widths.
     */
    fun typeface(context: Context): Typeface =
        faces.getOrPut(context) {
            ResourcesCompat.getFont(context, R.font.cascadia_code) ?: Typeface.MONOSPACE
        }

    /**
     * Returns the offset that puts a glyph's ink on the centre of a cell.
     *
     * [textSize] is the size the glyph is drawn at, in the same units as the cell, and the
     * answer is in those units. The offset is measured at [MEASURING_TEXT_SIZE] and scaled,
     * which is what keeps the result below a screen's resolution.
     */
    fun centre(text: String, paint: Paint): InkOffset {
        val measuring = Paint(paint).apply { textSize = MEASURING_TEXT_SIZE }
        val box = Rect()
        measuring.getTextBounds(text, 0, text.length, box)
        val scale = paint.textSize / MEASURING_TEXT_SIZE
        // Bounds report ink above the baseline as negative, so the baseline that centres the
        // ink sits half the ink's height above the centre plus the ink's own bottom.
        val x = -(box.left + box.right) * scale / 2f
        val y = (box.height() / 2f - box.bottom) * scale
        return InkOffset(x, y)
    }
}

/** A horizontal and vertical offset in board units. */
data class InkOffset(
    /** The distance to the right. */
    val x: Float,
    /** The distance down, where a positive value moves the text down. */
    val y: Float,
)

/**
 * Returns a paint for a board's numbers.
 *
 * A single place so that the four boards agree on anti-aliasing and on subpixel positioning:
 * a digit is a fraction of a cell, and a paint that rounded its pen to whole pixels would put
 * two digits of one row at different offsets.
 */
fun boardNumberPaint(
    typeface: Typeface,
    textSize: Float,
): Paint =
    Paint().apply {
        isAntiAlias = true
        this.typeface = typeface
        this.textSize = textSize
        style = Paint.Style.FILL
        // The pen is placed on the ink's box rather than on an advance width, which only works
        // when the pen is not asked to centre the advance itself.
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }
