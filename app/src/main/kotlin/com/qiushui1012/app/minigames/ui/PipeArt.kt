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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.core.graphics.PathParser
import java.util.concurrent.ConcurrentHashMap

/**
 * The pipe shapes, read from the SVGs in `assets/`.
 *
 * # A canvas cannot draw a file, but it can draw what the file holds
 *
 * An SVG is not an image format a canvas renders; it is a description of shapes. The shapes in
 * these files are `M … L … C …` commands, which is exactly what a [Path] is made of, so the file
 * is read and its commands become paths. Nothing about the drawings is copied into Kotlin: the
 * files stay the one place the shapes are described, and editing a file changes the board.
 *
 * # The colours come from the theme, not the file
 *
 * The files are drawn in black and white because a drawing has to be legible in a viewer. On the
 * board a pipe is the theme's ink — red on a loop — and the space between its walls is water when
 * water reaches the cell. Those are decided when the cell is drawn, so every path here is read as
 * a shape and given its colour by the caller.
 *
 * # How one drawing becomes one pipe
 *
 * Every path in a pipe file is a wall's centre line. Painting all of them twice reproduces the
 * drawing in any two colours: once at the width of the pipe, in the colour of what flows through
 * it, and once at the file's own wall width, in the pipe's colour. The second stroke covers the
 * middle of the first, and what shows between the walls is the first one. That is why the water
 * inside a pipe needs no second copy of the shape.
 */
object PipeArt {
    /** The side of the square the artwork is drawn in, which is its `viewBox`. */
    const val SIZE = 24f

    /**
     * The distance between the two axes a pipe's walls are drawn on.
     *
     * Every file in `assets/` puts them at 8.65 and 15.35 of its 24-unit square, which is what
     * makes the five shapes fit together: an arm of one meets an arm of its neighbour because
     * both were drawn on the same pair of axes.
     */
    private const val WALL_AXIS_GAP = 15.35f - 8.65f

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

    /** The pump, which is the one drawing made of filled faces rather than of walls. */
    const val PUMP_FILE = "pump.svg"

    /** One path of a drawing, with the stroke style the file gives it. */
    class Face(
        /** The geometry, in the artwork's own units. */
        val path: Path,
        /** The width the file strokes this path with, or zero when the file only fills it. */
        val strokeWidth: Float,
        /** How the file ends this path. */
        val cap: StrokeCap,
        /** How the file joins this path's segments. */
        val join: StrokeJoin,
    ) {
        /** Whether the file fills this path rather than only stroking it. */
        val isFilled: Boolean get() = strokeWidth <= 0f
    }

    /** What one file holds. */
    class Drawing(
        /** Every path, in the order the file lists them. */
        val faces: List<Face>,
        /** The widest stroke the file uses, which is the width of one wall. */
        val wallWidth: Float,
        /**
         * The outer width of the pipe the file draws.
         *
         * The two walls sit either side of a centre line, so the pipe is as wide as the gap
         * between them plus one wall at each edge. It is measured from the file rather than
         * stated, so a redrawn shape brings its own proportions.
         */
        val pipeWidth: Float,
        /**
         * The width of the space inside the pipe, between its two walls.
         *
         * It is the axis gap the walls sit on, which every file shares. Stroking a wall's line
         * this wide reaches exactly to the inside edge of the other wall, so painting the pipe's
         * body wider and then its inside narrower leaves each wall as thick as the file draws it.
         */
        val lumenWidth: Float,
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
     * The classes the file declares are resolved first: a drawing tool writes the geometry on
     * the element and its appearance in a `<style>` block, so the stroke width and the caps are
     * in the stylesheet rather than on the path.
     */
    private fun parse(context: Context, file: String): Drawing {
        val text = context.assets.open(file).bufferedReader().use { it.readText() }
        val styles = readStyles(text)
        val faces = mutableListOf<Face>()
        for (element in PathElement.findAll(text)) {
            val attributes = element.value
            val data = PathData.find(attributes)?.groupValues?.get(1) ?: continue
            val className = ClassName.find(attributes)?.groupValues?.get(1)
            val style = className?.let { styles[it] } ?: Style.Default
            val parsed = PathParser.createPathFromPathData(data) ?: continue
            faces += Face(parsed.asComposePath(), style.strokeWidth, style.cap, style.join)
        }
        val wallWidth = faces.filter { !it.isFilled }.maxOfOrNull { it.strokeWidth } ?: 0f
        // The pipe's outer width is the axis gap the walls sit on plus one wall at each edge.
        // It is not measured from a drawing's bounding box: a bend's walls reach the edges of the
        // square in both directions, so its box is the whole cell while the pipe is one gap wide.
        // The gap is the artwork's own geometry, constant across the five files, so it is stated
        // once here; the wall's width comes from the file, which is what a redrawn shape changes.
        val pipeWidth = WALL_AXIS_GAP + wallWidth
        return Drawing(faces, wallWidth, pipeWidth, WALL_AXIS_GAP - wallWidth)
    }

    /** How one class draws. */
    private class Style(val strokeWidth: Float, val cap: StrokeCap, val join: StrokeJoin) {
        companion object {
            /** What a path without a class is drawn as: filled, which is a face. */
            val Default = Style(0f, StrokeCap.Butt, StrokeJoin.Miter)
        }
    }

    /** Reads the `<style>` block into a class name to how-it-is-drawn map. */
    private fun readStyles(text: String): Map<String, Style> {
        val rules = mutableMapOf<String, Style>()
        for (rule in StyleRule.findAll(text)) {
            val name = rule.groupValues[1]
            val body = rule.groupValues[2]
            val width = StrokeWidth.find(body)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val cap =
                when (Cap.find(body)?.groupValues?.get(1)) {
                    "round" -> StrokeCap.Round
                    "square" -> StrokeCap.Square
                    else -> StrokeCap.Butt
                }
            val join =
                when (Join.find(body)?.groupValues?.get(1)) {
                    "round" -> StrokeJoin.Round
                    "bevel" -> StrokeJoin.Bevel
                    else -> StrokeJoin.Miter
                }
            // A rule with a stroke is a wall; one without is a filled face, which is what
            // the pump is made of. That is the whole difference between the two drawings.
            val stroked = body.contains("stroke:")
            rules[name] = Style(if (stroked) width else 0f, cap, join)
        }
        return rules
    }

    /** Matches one `<style>` rule for a class. */
    private val StyleRule = Regex("""\.([\w-]+)\s*\{([^}]*)\}""")

    /** Matches the stroke width inside a rule. */
    private val StrokeWidth = Regex("""stroke-width:\s*([\d.]+)""")

    /** Matches the line cap inside a rule. */
    private val Cap = Regex("""stroke-linecap:\s*(\w+)""")

    /** Matches the line join inside a rule. */
    private val Join = Regex("""stroke-linejoin:\s*(\w+)""")

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
 * Returns the shape a piece takes and the quarter turns that point it the way it joins.
 *
 * Each shape is drawn once in the files, in one orientation, so a piece is that drawing turned.
 * The turns are counted clockwise from the orientation the file uses, which is what makes every
 * rotation of every piece a pair of numbers rather than another drawing.
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


/**
 * Draws one pipe, from the shapes its file holds.
 *
 * [cellX] and [cellY] are the cell's top-left corner. The artwork's square is laid on the cell,
 * turned [turns] quarter turns clockwise about the cell's centre, so one drawing serves every
 * orientation of a piece.
 *
 * The drawing is painted twice: first in [lumen], at the width the pipe spans, and then in
 * [wall], at the width of a wall. The second pass covers the edges of the first, and what is
 * left showing between the walls is the lumen — the water, on a cell water reaches.
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
            rotate(turns * 90f, pivot = Offset(PipeArt.SIZE / 2f, PipeArt.SIZE / 2f)) {
                // The pipe itself: every wall's line painted at the full width, in the pipe's
                // colour. The union of those strokes is the pipe's outline, walls included.
                for (face in drawing.faces) {
                    drawPath(
                        path = face.path,
                        color = wall,
                        style =
                            Stroke(
                                width = drawing.pipeWidth,
                                cap = StrokeCap.Butt,
                                join = StrokeJoin.Round,
                            ),
                    )
                }
                // Then what the pipe carries, drawn on the same lines but only as wide as the
                // space between the walls. The wall colour is left showing as the two edges this
                // does not cover, which is the whole of the pipe's outline.
                for (face in drawing.faces) {
                    drawPath(
                        path = face.path,
                        color = lumen,
                        style =
                            Stroke(
                                width = drawing.lumenWidth,
                                cap = StrokeCap.Butt,
                                join = StrokeJoin.Round,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Draws the pump on a cell.
 *
 * The pump's file is built the other way round from a pipe's: its paths are faces with a fill,
 * not walls with a stroke. The first path is the rounded square and the second is the drop on
 * it, which is why the two colours are taken in the order the file lists them.
 */
fun DrawScope.drawPump(
    context: Context,
    cellX: Float,
    cellY: Float,
    body: Color,
    mark: Color,
) {
    val drawing = PipeArt.pump(context)
    val faces = drawing.faces.filter { it.isFilled }
    if (faces.isEmpty()) {
        return
    }
    translate(cellX, cellY) {
        scale(1f / PipeArt.SIZE, pivot = Offset.Zero) {
            drawPath(faces.first().path, body)
            faces.drop(1).forEach { drawPath(it.path, mark) }
        }
    }
}