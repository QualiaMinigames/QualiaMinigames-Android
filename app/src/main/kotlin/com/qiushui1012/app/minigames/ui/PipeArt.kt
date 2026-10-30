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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.toArgb
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
 * Returns every piece's walls, one path per side mask, in cell units.
 *
 * The board draws a few thousand cells from at most fourteen masks, so the geometry of a mask is
 * built once and reused. Nothing here depends on a cell's position or on the board's zoom: a
 * caller translates the path, and the canvas transform does the rest.
 *
 * The cache is per process and is keyed by nothing but the kind of path, because the shapes come
 * from files that do not change while the application runs.
 */
fun pipeWalls(context: Context): Map<Int, Path> = wallCache.getOrPut(EMPTY) { build(context, lumen = false) }

/** The lumens of every piece, in cell units, built the same way as [pipeWalls]. */
fun pipeLumens(context: Context): Map<Int, Path> = lumenCache.getOrPut(EMPTY) { build(context, lumen = true) }

/** A marker key: both caches hold exactly one entry per process. */
private const val EMPTY = ""

/** The walls of every mask, built on first use. */
private val wallCache = ConcurrentHashMap<String, Map<Int, Path>>()

/** The lumens of every mask, built on first use. */
private val lumenCache = ConcurrentHashMap<String, Map<Int, Path>>()

/** Builds the paths of one kind over every mask the rules can produce. */
private fun build(context: Context, lumen: Boolean): Map<Int, Path> {
    val paths = HashMap<Int, Path>(14)
    for (mask in 1..14) {
        val placement = pipeShapeOf(mask) ?: continue
        val drawing = PipeArt.of(context, placement.first)
        val path = Path()
        val matrix =
            Matrix().apply {
                scale(1f / PipeArt.SIZE, 1f / PipeArt.SIZE)
                // A rotation turns about the origin, so the piece is moved to the centre first
                // and back afterwards: that is the same pivot the board's own drawing used.
                translate(PipeArt.CENTRE, PipeArt.CENTRE)
                rotateZ(placement.second * 90f)
                translate(-PipeArt.CENTRE, -PipeArt.CENTRE)
            }
        for (band in drawing.bands) {
            if (band.isLumen != lumen) {
                continue
            }
            val bandPath = Path()
            bandPath.addPath(band.path)
            bandPath.transform(matrix)
            path.addPath(bandPath)
        }
        paths[mask] = path
    }
    return paths
}

/**
 * Records a whole pipes board into one picture, in board units.
 *
 * A board is static between moves: the pieces only change when the player turns one, and the
 * water only spreads then too. Drawing it as several thousand paths every frame was the largest
 * cost on a large board, so the finished board is recorded once and drawn as a single picture.
 * The record is in board units, which keeps it sharp at any zoom: a picture is a list of vector
 * commands, not pixels.
 *
 * The paths are painted in four passes — plain walls, loop walls, dry lumens, wet lumens — so the
 * paint's colour is set four times rather than once per cell.
 */
fun recordPipes(
    context: Context,
    size: Int,
    pieces: List<Int>,
    loopCells: Set<Int>,
    waterCells: Set<Int>,
    waterStart: Int,
    wall: Color,
    loopWall: Color,
    water: Color,
    boardBackground: Color,
    gridLine: Color,
    gridWidth: Float,
): Picture {
    val picture = Picture()
    val native = picture.beginRecording(size, size)
    val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    val walls = pipeWalls(context)
    val lumens = pipeLumens(context)
    val gridPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = gridWidth }

    // The grid is drawn first, under the pieces: a wall covers the line it sits on, so a grid
    // drawn over the board would cut into every piece that reaches an edge.
    gridPaint.color = gridLine.toArgb()
    for (line in 1 until size) {
        native.drawLine(line.toFloat(), 0f, line.toFloat(), size.toFloat(), gridPaint)
        native.drawLine(0f, line.toFloat(), size.toFloat(), line.toFloat(), gridPaint)
    }
    // The frame sits on the boundary itself rather than inside it, so its two halves lie either
    // side of the edge and the board ends where its pieces do.
    native.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gridPaint)

    val cells = size * size
    for (pass in 0..3) {
        paint.color =
            when (pass) {
                0 -> wall.toArgb()
                1 -> loopWall.toArgb()
                2 -> boardBackground.toArgb()
                else -> water.toArgb()
            }
        val lumen = pass >= 2
        val wet = pass == 3
        val path = if (lumen) lumens else walls
        for (index in 0 until minOf(cells, pieces.size)) {
            if ((index in loopCells) != (pass == 1)) {
                continue
            }
            if (lumen && (index in waterCells) != wet) {
                continue
            }
            val piecePath = path[pieces[index]] ?: continue
            native.save()
            native.translate((index % size).toFloat(), (index / size).toFloat())
            native.drawPath(piecePath.asAndroidPath(), paint)
            native.restore()
        }
    }

    // The pump goes over the pieces: it marks the cell the water comes from, and a piece that
    // covered it would hide the one cell the board is about. Its square is drawn larger than a
    // pipe's lumen, so it covers the piece beneath it rather than being cut by it.
    drawPumpInto(context, native, (waterStart % size).toFloat(), (waterStart / size).toFloat(), water)
    picture.endRecording()
    return picture
}

/** Draws the pump into [canvas] at a cell, in board units. */
private fun drawPumpInto(context: Context, canvas: Canvas, cellX: Float, cellY: Float, mark: Color) {
    val drawing = PipeArt.pump(context)
    val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    canvas.save()
    canvas.translate(cellX, cellY)
    canvas.scale(1f / PipeArt.SIZE, 1f / PipeArt.SIZE)
    drawing.bands.forEachIndexed { index, band ->
        paint.color = if (index == drawing.bands.lastIndex) mark.toArgb() else band.color
        canvas.drawPath(band.path.asAndroidPath(), paint)
    }
    canvas.restore()
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
