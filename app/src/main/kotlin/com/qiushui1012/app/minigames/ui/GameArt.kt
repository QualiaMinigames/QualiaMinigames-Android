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
import androidx.compose.ui.graphics.asComposePath
import androidx.core.graphics.PathParser
import com.qiushui1012.app.minigames.data.GameKind
import java.util.concurrent.ConcurrentHashMap

/**
 * The drawings of the three games, read from the SVGs in `assets/`.
 *
 * # What the files hold
 *
 * `pipes.svg`, `sudoku.svg` and `minesweeper.svg` are the marks of the games themselves: filled
 * shapes on a 72-unit square, with white where the mark leaves a gap. A class in the files'
 * `<style>` block says which is which, and a path is painted in the colour its class declares,
 * so a mark that is edited in a drawing program changes here without a second record of its
 * geometry.
 *
 * # Why the colours come from the caller
 *
 * The files are black and white because a drawing has to be legible in a viewer, and the
 * minesweeper file adds a red and a grey of its own. On a card the black is the theme's ink,
 * the white is the card's own surface, the red is the warning tone a board already uses for a
 * loop and the grey is the same tone the theme uses for a secondary surface. Each band
 * therefore says which of the four it is, and the card decides what that tone means in the
 * scheme it is drawing.
 */
object GameArt {
    /** The side of the square the artwork is drawn in, which is its `viewBox`. */
    const val SIZE = 72f

    /**
     * The tone a band of a mark is painted in.
     *
     * A file declares a colour per class, and this is what that colour means on a card. The
     * classification is a property of the colour alone, so a file that adds a shade gets the
     * band it asked for without the card learning a new name.
     */
    enum class Tone {
        /** The mark's line work: black in the files. */
        Ink,

        /** The card's own surface: white in the files. */
        Surface,

        /** The warning colour a board uses for a loop and a conflict: the red in a file. */
        Alert,

        /** A recessed surface: the grey in a file. */
        Neutral,
    }

    /**
     * One path of a mark.
     *
     * [fill] and [stroke] are null when the file does not paint that part of the path, and
     * otherwise name the tone the colour the file gives it stands for. A cell of the sudoku
     * mark is both: a white fill inside a black outline.
     */
    class Band(
        /** The geometry, in the artwork's own units. */
        val path: Path,
        /** The tone the file fills the path with, or null when it does not fill it. */
        val fill: Tone?,
        /** The tone the file outlines the path with, or null when it does not outline it. */
        val stroke: Tone?,
        /** The width of that outline, in the artwork's own units. */
        val strokeWidth: Float,
    )

    /** The file a game's mark is drawn in. */
    fun file(kind: GameKind): String =
        when (kind) {
            GameKind.Pipes -> "pipes.svg"
            GameKind.Sudoku -> "sudoku.svg"
            GameKind.Minesweeper -> "minesweeper.svg"
        }

    /** The drawings, once per process: parsing a file per frame would be absurd. */
    private val cache = ConcurrentHashMap<String, List<Band>>()

    /** Returns the bands of a game's mark, parsing its file on first use. */
    fun of(
        context: Context,
        kind: GameKind,
    ): List<Band> = cache.getOrPut(file(kind)) { parse(context, file(kind)) }

    /**
     * Reads one file into bands.
     *
     * Each file declares its colours in a `<style>` block keyed by class, so the classes are
     * resolved first and a path takes the fill and the outline its class gives it. A path the
     * file fills white is painted with the surface colour, which is what makes the gap in a
     * mark a gap rather than a second tone.
     */
    private fun parse(
        context: Context,
        file: String,
    ): List<Band> {
        val text = context.assets.open(file).bufferedReader().use { it.readText() }
        val styles = readStyles(text)
        return PathElement.findAll(text).mapNotNull { element ->
            val attributes = element.value
            val data = PathData.find(attributes)?.groupValues?.get(1) ?: return@mapNotNull null
            val style = ClassName.find(attributes)?.groupValues?.get(1)?.let { styles[it] } ?: Style()
            val parsed = PathParser.createPathFromPathData(data)?.asComposePath() ?: return@mapNotNull null
            Band(parsed, style.fill, style.stroke, style.strokeWidth)
        }.toList()
    }

    /** What a class paints: a fill, an outline, or both. */
    private class Style(
        /** The tone of the fill, or null when there is no fill. */
        val fill: Tone? = null,
        /** The tone of the outline, or null when there is no outline. */
        val stroke: Tone? = null,
        /** The width of the outline, in the artwork's own units. */
        val strokeWidth: Float = 0f,
    )

    /** Reads the `<style>` block into a class name to [Style] map. */
    private fun readStyles(text: String): Map<String, Style> {
        val styles = mutableMapOf<String, Style>()
        for (rule in StyleRule.findAll(text)) {
            val name = rule.groupValues[1]
            val body = rule.groupValues[2]
            val fill = Fill.find(body)?.groupValues?.get(1)?.trim()
            val stroke = Stroke.find(body)?.groupValues?.get(1)?.trim()
            val width = StrokeWidth.find(body)?.groupValues?.get(1)?.toFloatOrNull()
            styles[name] =
                Style(
                    fill = fill?.takeUnless(::isNone)?.let(::toneOf),
                    stroke = stroke?.takeUnless(::isNone)?.let(::toneOf),
                    strokeWidth = width ?: 0f,
                )
        }
        return styles
    }

    /** Returns whether an SVG paint value paints nothing. */
    private fun isNone(value: String): Boolean = value.equals("none", ignoreCase = true)

    /**
     * Returns the tone an SVG colour stands for.
     *
     * The four tones are told apart by what the colour is rather than by which file it came
     * from, so a mark that adds a shade in a drawing program is classified by the same rule as
     * the marks that came before it. Red is a signal and is recognised by its hue; the greys
     * are told apart by how light they are, and anything else is the ink.
     */
    fun toneOf(value: String): Tone {
        val hex = value.removePrefix("#")
        val rgb = hex.toLongOrNull(16) ?: return Tone.Ink
        val red = ((rgb shr 16) and 0xFF).toFloat() / 255f
        val green = ((rgb shr 8) and 0xFF).toFloat() / 255f
        val blue = (rgb and 0xFF).toFloat() / 255f
        val brightest = maxOf(red, green, blue)
        val darkest = minOf(red, green, blue)
        val saturation = if (brightest == 0f) 0f else (brightest - darkest) / brightest
        val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
        return when {
            saturation > 0.5f && red > green && red > blue -> Tone.Alert
            saturation < 0.2f && luminance in 0.5f..0.85f -> Tone.Neutral
            luminance < 0.5f -> Tone.Ink
            else -> Tone.Surface
        }
    }

    /** Matches one `<style>` rule for a class. */
    private val StyleRule = Regex("""\.([\w-]+)\s*\{([^}]*)\}""")

    /** Matches the fill inside a rule. */
    private val Fill = Regex("""fill:\s*([^;]+)""")

    /** Matches the outline inside a rule. */
    private val Stroke = Regex("""(?:^|[;\s])stroke:\s*([^;]+)""")

    /** Matches the width of an outline inside a rule. */
    private val StrokeWidth = Regex("""stroke-width:\s*([\d.]+)""")

    /** Matches one `<path …/>` element. */
    private val PathElement = Regex("""<path\b[^>]*/>""")

    /** Matches the geometry of a path element. */
    private val PathData = Regex("""d="([^"]+)"""")

    /** Matches the class of a path element. */
    private val ClassName = Regex("""class="([\w-]+)"""")
}
