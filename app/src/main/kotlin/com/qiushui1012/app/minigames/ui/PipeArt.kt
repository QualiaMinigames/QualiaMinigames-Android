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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.core.graphics.PathParser
import java.util.concurrent.ConcurrentHashMap

/**
 * The pipe shapes, read from the SVGs in `assets/`.
 *
 * # What the files hold
 *
 * Every pipe file is a set of filled paths over the 24-unit square of its `viewBox`: one path per
 * wall, and one per lumen — the space the walls enclose. The walls are drawn black and the lumen
 * white, which is how the two are told apart here: the colour says which part a path is, and the
 * caller replaces both with the colours of the moment.
 *
 * # Why the files are read rather than copied
 *
 * A canvas cannot draw an SVG file, but the file is nothing but path data and a canvas draws
 * paths. Reading them keeps the drawing the one place a shape is described: an edited file
 * changes the board, and no second record of the same geometry can drift away from it. The lumen
 * is part of the drawing for the same reason — it used to be reconstructed from the walls' gap,
 * which put the two halves of one shape in two places and made a corner between them approximate.
 *
 * # Why the colours come from the caller
 *
 * The files are drawn in black and white because a drawing has to be legible in a viewer. On the
 * board a pipe is the theme's ink — red on a loop — and its lumen is water where water reaches.
 * Those are decided per cell and per frame, so the black paths are painted in the wall colour and
 * the white ones in the lumen colour.
 *
 * # Which way a shape faces
 *
 * Each shape is drawn once, in one orientation, and the orientation is part of its declaration:
 * [Shape.sides] is the set of sides of the square its arms reach when the drawing is used as-is.
 * Every other direction is that drawing turned, which is what [pipeShapeOf] works out — so the
 * table that says how a piece turns is derived from the drawings rather than written beside them.
 */
object PipeArt {
    /** The side of the square the artwork is drawn in, which is its `viewBox`. */
    const val SIZE = 24f

    /** The middle of that square, where the arms of a piece meet. */
    const val CENTRE = 12f

    /**
     * The shapes a pipe can take.
     *
     * [file] is the drawing and [sides] is the sides of the square it reaches in the orientation
     * the file draws it in. Both are read from the file's own geometry: a bend whose two arms run
     * up and left is declared as up and left, and the code that turns a piece never has to restate
     * that.
     */
    enum class Shape(val file: String, val sides: Int) {
        /** A dead end: a tube that runs into a circular fitting. */
        End("pipe_end.svg", PipeSides.TOP),

        /** One straight tube across the cell, running up and down. */
        Straight("pipe_straight.svg", PipeSides.TOP or PipeSides.BOTTOM),

        /** A tube that turns a corner, between up and left. */
        Bend("pipe_bend.svg", PipeSides.TOP or PipeSides.LEFT),

        /** A tube that branches in three directions: up, down and left. */
        Tee("pipe_tee.svg", PipeSides.TOP or PipeSides.BOTTOM or PipeSides.LEFT),

        /** Two tubes crossing. */
        Cross(
            "pipe_x.svg",
            PipeSides.TOP or PipeSides.RIGHT or PipeSides.BOTTOM or PipeSides.LEFT,
        ),
    }

    /** The pump, the one drawing whose two paths are a body and a mark rather than walls. */
    const val PUMP_FILE = "pump.svg"

    /** One filled path of a drawing. */
    class Band(
        /** The geometry, in the artwork's own units. */
        val path: Path,
        /** Whether the file draws this path as the lumen rather than as a wall. */
        val isLumen: Boolean,
        /** The colour the file declares, which is what the pump's body keeps. */
        val color: Int,
    )

    /** What one file holds. */
    class Drawing(
        /** Every path, in the order the file lists them. */
        val bands: List<Band>,
    )

    /** The drawings, once per process: parsing a file per cell per frame would be absurd. */
    private val cache = ConcurrentHashMap<String, Drawing>()

    /** Returns the drawing in [shape], parsing it on first use. */
    fun of(context: Context, shape: Shape): Drawing = cache.getOrPut(shape.file) { parse(context, shape.file) }

    /** Returns the pump's drawing. */
    fun pump(context: Context): Drawing = cache.getOrPut(PUMP_FILE) { parse(context, PUMP_FILE) }

    /**
     * Reads one file into paths.
     *
     * Each file declares its colours in a `<style>` block keyed by class, so the classes are
     * resolved first and kept with each path. A path the file fills white is the lumen and one it
     * fills black is a wall; the file is drawn that way so that a reader can see which is which,
     * and that is exactly what this needs to know.
     */
    private fun parse(context: Context, file: String): Drawing {
        val text = context.assets.open(file).bufferedReader().use { it.readText() }
        val colors = readColors(text)
        val bands = mutableListOf<Band>()
        for (element in PathElement.findAll(text)) {
            val attributes = element.value
            val data = PathData.find(attributes)?.groupValues?.get(1) ?: continue
            val className = ClassName.find(attributes)?.groupValues?.get(1)
            val color = className?.let { colors[it] } ?: 0
            val parsed = PathParser.createPathFromPathData(data) ?: continue
            bands += Band(parsed.asComposePath(), isLumen = brightness(color) > 0.5f, color = color)
        }
        return Drawing(bands)
    }

    /** Reads the `<style>` block into a class name to colour map. */
    private fun readColors(text: String): Map<String, Int> {
        val colors = mutableMapOf<String, Int>()
        for (rule in StyleRule.findAll(text)) {
            val name = rule.groupValues[1]
            val value = Fill.find(rule.groupValues[2])?.groupValues?.get(1)?.trim() ?: continue
            colors[name] = parseColor(value)
        }
        return colors
    }

    /** Reads `#rrggbb` into an ARGB integer, which is the only form these files use. */
    private fun parseColor(value: String): Int {
        val hex = value.removePrefix("#")
        val rgb = hex.toLongOrNull(16) ?: return 0
        return when (hex.length) {
            6 -> (0xFF000000L or rgb).toInt()
            8 -> rgb.toInt()
            else -> 0
        }
    }

    /** Returns how light a colour is, from zero for black to one for white. */
    private fun brightness(argb: Int): Float {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return (0.299f * red + 0.587f * green + 0.114f * blue) / 255f
    }

    /** Matches one `<style>` rule for a class. */
    private val StyleRule = Regex("""\.([\w-]+)\s*\{([^}]*)\}""")

    /** Matches the fill inside a rule. */
    private val Fill = Regex("""fill:\s*([^;]+)""")

    /** Matches one `<path …/>` element. */
    private val PathElement = Regex("""<path\b[^>]*/>""")

    /** Matches the geometry of a path element. */
    private val PathData = Regex("""d="([^"]+)"""")

    /** Matches the class of a path element. */
    private val ClassName = Regex("""class="([\w-]+)"""")
}

/** The four sides of a cell, as a mask that matches the rules' own side constants. */
object PipeSides {
    /** The top side. */
    const val TOP: Int = 1

    /** The right side. */
    const val RIGHT: Int = 2

    /** The bottom side. */
    const val BOTTOM: Int = 4

    /** The left side. */
    const val LEFT: Int = 8
}

/**
 * Draws one pipe.
 *
 * [cellX] and [cellY] are the cell's top-left corner; [turns] is how many quarter turns clockwise
 * the drawing takes to face the way the piece joins.
 *
 * The paths are filled in the order the file lists them, which is the order they were drawn in:
 * walls first, then the lumen that lies between them. Nothing is stroked, so a cell is drawn from
 * the shapes the file describes and every edge of it is anti-aliased by the canvas.
 */
fun DrawScope.drawPipe(
    context: Context,
    cellX: Float,
    cellY: Float,
    shape: PipeArt.Shape,
    turns: Int,
    wall: Color,
    lumen: Color,
) {
    val drawing = PipeArt.of(context, shape)
    translate(cellX, cellY) {
        scale(1f / PipeArt.SIZE, pivot = Offset.Zero) {
            rotate(turns * 90f, pivot = Offset(PipeArt.CENTRE, PipeArt.CENTRE)) {
                for (band in drawing.bands) {
                    drawPath(
                        path = band.path,
                        color = if (band.isLumen) lumen else wall,
                    )
                }
            }
        }
    }
}

/**
 * Draws the pump on a cell.
 *
 * The file draws two faces: the square the fitting stands on, and the pump on it. The square keeps
 * the colour the file declares, because a pump is a fitting rather than a pipe and should not
 * change with the theme; the pump is painted in [mark], which the caller sets to the water colour,
 * because the water is what it moves.
 *
 * The two are told apart by the order the file lists them in. Measuring would not work: the pump's
 * bounds are nearly as wide as the square's.
 */
fun DrawScope.drawPump(
    context: Context,
    cellX: Float,
    cellY: Float,
    mark: Color,
) {
    val drawing = PipeArt.pump(context)
    if (drawing.bands.isEmpty()) {
        return
    }
    translate(cellX, cellY) {
        scale(1f / PipeArt.SIZE, pivot = Offset.Zero) {
            drawing.bands.forEachIndexed { index, band ->
                val color = if (index == drawing.bands.lastIndex) mark else Color(band.color)
                drawPath(path = band.path, color = color)
            }
        }
    }
}

/**
 * Returns the shape a piece takes and the quarter turns that point it the way it joins.
 *
 * The answer is worked out from the drawings: each shape declares the sides it reaches in the
 * orientation its file draws it in, and a piece is that shape turned until its sides are the ones
 * the piece joins. Nothing restates the orientation, which is why a drawing and the table that
 * turns it cannot disagree.
 *
 * Returns null for a mask that is not one of the shapes, which cannot come out of the rules.
 */
fun pipeShapeOf(mask: Int): Pair<PipeArt.Shape, Int>? {
    if (mask == 0) {
        return null
    }
    for (turns in 0..3) {
        for (shape in PipeArt.Shape.entries) {
            if (rotateSides(shape.sides, turns) == mask) {
                return shape to turns
            }
        }
    }
    return null
}

/** Returns a set of sides after [turns] quarter turns clockwise. */
fun rotateSides(sides: Int, turns: Int): Int {
    var result = 0
    for (side in listOf(PipeSides.TOP, PipeSides.RIGHT, PipeSides.BOTTOM, PipeSides.LEFT)) {
        if (sides and side != 0) {
            result = result or rotateSide(side, turns)
        }
    }
    return result
}

/** Returns one side after [turns] quarter turns clockwise. */
private fun rotateSide(side: Int, turns: Int): Int {
    var current = side
    repeat(((turns % 4) + 4) % 4) {
        current =
            when (current) {
                PipeSides.TOP -> PipeSides.RIGHT
                PipeSides.RIGHT -> PipeSides.BOTTOM
                PipeSides.BOTTOM -> PipeSides.LEFT
                else -> PipeSides.TOP
            }
    }
    return current
}
