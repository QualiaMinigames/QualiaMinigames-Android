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
import androidx.compose.ui.graphics.StrokeCap
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
 * Every pipe file is a set of filled bands: one closed path per wall, drawn over the 24-unit
 * square of the `viewBox`. A straight is two vertical bands, a bend is two bands turning a
 * corner, a cross is four. The bands are what the file describes, and they are the only thing it
 * describes.
 *
 * # What it does not hold, and why that is the point
 *
 * The lumen — the space a pipe carries water through — is not in any file: it is the gap the
 * bands leave between them. That is deliberate, because the gap is exactly what has to change
 * colour. The walls are filled from the file in the pipe's colour; the gap is painted first, in
 * the colour of whatever the pipe carries, by stroking the line down the middle of the pipe.
 *
 * # Why the files are read rather than copied
 *
 * A canvas cannot draw an SVG file, but the file is nothing but path data and a canvas draws
 * paths. Reading them keeps the drawings the one place a shape is described: an edited file
 * changes the board, and no second record of the same geometry can drift away from it.
 *
 * # Why the colours stay outside
 *
 * The files are black, because a drawing has to be legible in a viewer. On the board a pipe is
 * the theme's ink — red on a loop — and its lumen is water where water reaches. Those are decided
 * per cell and per frame, so every path is read as a shape and coloured by the caller. The pump
 * is the one exception, and it keeps the two colours its file declares.
 */
object PipeArt {
    /** The side of the square the artwork is drawn in, which is its `viewBox`. */
    const val SIZE = 24f

    /** The middle of that square, where the arms of a piece meet. */
    const val CENTRE = 12f

    /**
     * The width of the gap the walls leave.
     *
     * Every file puts a wall's band between 7.65 and 9.65 and the opposite band between 14.35 and
     * 16.35, so the gap is 14.35 − 9.65. It is stated once here because it is the one dimension
     * the files do not contain: they draw the walls, and the gap is what lies between them.
     */
    const val LUMEN_WIDTH = 14.35f - 9.65f

    /**
     * Where the lumen stops at the edge of the square.
     *
     * A lumen painted to the very edge would run into the cell next door and, worse, across that
     * cell's own walls. Stopping it in the middle of the wall at the edge keeps it inside the
     * pipe it belongs to, and the wall it ends in covers the end.
     */
    const val LUMEN_EDGE = 8.65f

    /**
     * The radius of the round fitting a dead end opens into.
     *
     * The end's file draws that fitting as a ring seven units across with a two-unit wall, so
     * the space inside it is five.
     */
    const val END_BULB_RADIUS = 5f

    /** The shapes a pipe can take, named after the files they come from. */
    enum class Shape(val file: String) {
        /** A dead end: a tube that runs into a circular fitting. */
        End("pipe_end.svg"),

        /** One straight tube across the cell. */
        Straight("pipe_straight.svg"),

        /** A tube that turns a corner. */
        Bend("pipe_bend.svg"),

        /** A tube that branches in three directions. */
        Tee("pipe_tee.svg"),

        /** Two tubes crossing. */
        Cross("pipe_x.svg"),
    }

    /** The pump, the one drawing whose paths are a body and a mark rather than walls. */
    const val PUMP_FILE = "pump.svg"

    /** One filled band of a drawing. */
    class Band(
        /** The geometry, in the artwork's own units. */
        val path: Path,
        /** The colour the file declares for it, as ARGB. */
        val color: Int,
    )

    /** What one file holds. */
    class Drawing(
        /** Every band, in the order the file lists them. */
        val bands: List<Band>,
    )

    /** The drawings, once per process: parsing a file per cell per frame would be absurd. */
    private val cache = ConcurrentHashMap<String, Drawing>()

    /** Returns the drawing in [shape], parsing it on first use. */
    fun of(context: Context, shape: Shape): Drawing = cache.getOrPut(shape.file) { parse(context, shape.file) }

    /** Returns the pump's drawing. */
    fun pump(context: Context): Drawing = cache.getOrPut(PUMP_FILE) { parse(context, PUMP_FILE) }

    /**
     * Reads one file into bands.
     *
     * Each file declares its colours in a `<style>` block keyed by class, so the classes are
     * resolved first and kept with each path. A pipe ignores them and paints every band its own
     * colour; the pump keeps them, because its body and its mark are two colours that mean two
     * things.
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
            bands += Band(parsed.asComposePath(), color)
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
 * The lumen is painted first, because it is not in the file: it is the gap between the bands, and
 * stroking a line down the middle of the pipe at that width is what fills the gap. The bands are
 * then filled from the file, and land exactly where the drawing puts them.
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
    val centre = Offset(PipeArt.CENTRE, PipeArt.CENTRE)
    val arms = armsOf(shape, turns)
    translate(cellX, cellY) {
        scale(1f / PipeArt.SIZE, pivot = Offset.Zero) {
            rotate(turns * 90f, pivot = Offset(PipeArt.CENTRE, PipeArt.CENTRE)) {
                // The gap, painted as a tube running from the middle to each arm. A disc at the
                // middle joins them: two strokes meeting at a right angle leave a notch without
                // it, and the disc is the same round joint the file gives the walls it draws.
                drawCircle(color = lumen, radius = PipeArt.LUMEN_WIDTH / 2f, center = centre)
                for (arm in arms) {
                    drawLine(
                        color = lumen,
                        start = centre,
                        end = arm,
                        strokeWidth = PipeArt.LUMEN_WIDTH,
                        cap = StrokeCap.Butt,
                    )
                }
                // A dead end opens into a round fitting, and the gap fills it too. The fitting is
                // wider than the tube, so it is a disc of its own rather than another stroke.
                if (shape == PipeArt.Shape.End) {
                    drawCircle(
                        color = lumen,
                        radius = PipeArt.END_BULB_RADIUS,
                        center = centre,
                    )
                }
                // The walls, filled exactly as the file draws them.
                for (band in drawing.bands) {
                    drawPath(path = band.path, color = wall)
                }
            }
        }
    }
}

/**
 * Returns where each of a piece's arms reaches, in the artwork's units.
 *
 * A pipe runs to the middle of each side of the square, so an arm's end is one of the four
 * midpoints. The arms are named in the orientation the files use, then turned with the drawing,
 * which is why one drawing serves every direction a piece can face.
 */
private fun armsOf(shape: PipeArt.Shape, turns: Int): List<Offset> {
    // The sides each drawing joins, as the files draw them: the straight runs up and down, the
    // bend turns between up and left, the tee lacks the right side, the cross has all four, and
    // an end runs up into its fitting.
    val sides =
        when (shape) {
            PipeArt.Shape.Straight -> listOf(PipeSides.TOP, PipeSides.BOTTOM)
            PipeArt.Shape.Bend -> listOf(PipeSides.TOP, PipeSides.LEFT)
            PipeArt.Shape.Tee -> listOf(PipeSides.TOP, PipeSides.LEFT, PipeSides.BOTTOM)
            PipeArt.Shape.Cross -> listOf(PipeSides.TOP, PipeSides.RIGHT, PipeSides.BOTTOM, PipeSides.LEFT)
            PipeArt.Shape.End -> listOf(PipeSides.TOP)
        }
    return sides.map { armEnd(rotateSide(it, turns)) }
}

/** Returns the midpoint of a side of the square. */
/**
 * Returns where a piece's lumen stops on a side of the square.
 *
 * Not the edge: the lumen ends in the middle of the wall that runs along that edge, which is
 * where the pipe's own opening is. Running it to the edge would carry it into the neighbouring
 * cell and paint over that cell's walls.
 */
private fun armEnd(side: Int): Offset =
    when (side) {
        PipeSides.TOP -> Offset(PipeArt.CENTRE, PipeArt.LUMEN_EDGE)
        PipeSides.RIGHT -> Offset(PipeArt.SIZE - PipeArt.LUMEN_EDGE, PipeArt.CENTRE)
        PipeSides.BOTTOM -> Offset(PipeArt.CENTRE, PipeArt.SIZE - PipeArt.LUMEN_EDGE)
        else -> Offset(PipeArt.LUMEN_EDGE, PipeArt.CENTRE)
    }

/** Returns a side after [turns] quarter turns clockwise. */
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

/**
 * Draws the pump on a cell.
 *
 * The file draws two faces: the rounded square the fitting stands on, and the drop on it. The
 * square keeps the colour the file declares, because a pump is a fitting rather than a pipe and
 * should not change with the theme; the drop is painted in [mark], which the caller sets to the
 * water colour, because the drop is what the pump feeds.
 *
 * The two are told apart by the order the file lists them in, not by their size: the drop's
 * bounds are as wide as the square's, so measuring would pick the wrong one.
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
                // The drop is the face drawn on top, which is the last one in the file; the body
                // is the square under it. The order is the file's own, and it is more reliable
                // than measuring: the drop's bounds are nearly as wide as the square's.
                val isMark = index == drawing.bands.lastIndex
                drawPath(path = band.path, color = if (isMark) mark else Color(band.color))
            }
        }
    }
}

/**
 * Returns the shape a piece takes and the quarter turns that point it the way it joins.
 *
 * Each shape is drawn once, in one orientation, so a piece is that drawing turned. The turns are
 * counted clockwise from the file's own orientation, which makes every rotation of every piece a
 * pair of numbers rather than another drawing.
 *
 * Returns null for a mask that is not one of the shapes, which cannot come out of the rules.
 */
fun pipeShapeOf(sides: Int): Pair<PipeArt.Shape, Int>? =
    when (Integer.bitCount(sides)) {
        1 ->
            when (sides) {
                PipeSides.TOP -> PipeArt.Shape.End to 0
                PipeSides.RIGHT -> PipeArt.Shape.End to 1
                PipeSides.BOTTOM -> PipeArt.Shape.End to 2
                PipeSides.LEFT -> PipeArt.Shape.End to 3
                else -> null
            }

        2 ->
            when (sides) {
                // The straight is drawn up and down.
                PipeSides.TOP or PipeSides.BOTTOM -> PipeArt.Shape.Straight to 0
                PipeSides.LEFT or PipeSides.RIGHT -> PipeArt.Shape.Straight to 1
                // The bend is drawn turning between up and left.
                PipeSides.TOP or PipeSides.LEFT -> PipeArt.Shape.Bend to 0
                PipeSides.TOP or PipeSides.RIGHT -> PipeArt.Shape.Bend to 1
                PipeSides.BOTTOM or PipeSides.RIGHT -> PipeArt.Shape.Bend to 2
                PipeSides.BOTTOM or PipeSides.LEFT -> PipeArt.Shape.Bend to 3
                else -> null
            }

        3 ->
            when (sides) {
                // The tee is drawn joining up, left and down: the right side is the one it lacks.
                PipeSides.TOP or PipeSides.LEFT or PipeSides.BOTTOM -> PipeArt.Shape.Tee to 0
                PipeSides.TOP or PipeSides.RIGHT or PipeSides.LEFT -> PipeArt.Shape.Tee to 1
                PipeSides.RIGHT or PipeSides.BOTTOM or PipeSides.LEFT -> PipeArt.Shape.Tee to 2
                PipeSides.TOP or PipeSides.RIGHT or PipeSides.BOTTOM -> PipeArt.Shape.Tee to 3
                else -> null
            }

        4 -> PipeArt.Shape.Cross to 0

        else -> null
    }
