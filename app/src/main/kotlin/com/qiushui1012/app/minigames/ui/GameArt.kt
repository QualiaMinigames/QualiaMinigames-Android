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
 * The drawings of the two games, read from the SVGs in `assets/`.
 *
 * # What the files hold
 *
 * `pipes.svg` and `sudoku.svg` are the marks of the games themselves: filled black shapes on a
 * 72-unit square, with white where the mark leaves a gap. A class in the files' `<style>` block
 * says which is which, and a path is painted in the colour its class declares, so a mark that
 * is edited in a drawing program changes here without a second record of its geometry.
 *
 * # Why the colours come from the caller
 *
 * The files are black and white because a drawing has to be legible in a viewer. On a card the
 * black is the theme's ink and the white is the card's own surface, so the same file reads
 * correctly in both schemes.
 */
object GameArt {
    /** The side of the square the artwork is drawn in, which is its `viewBox`. */
    const val SIZE = 72f

    /**
     * One path of a mark.
     *
     * [fill] and [stroke] are null when the file does not paint that part of the path, and
     * otherwise say whether the colour the file gives it is the dark one. A cell of the sudoku
     * mark is both: a white fill inside a black outline.
     */
    class Band(
        /** The geometry, in the artwork's own units. */
        val path: Path,
        /** Whether the file fills the path, and whether that fill is the dark tone. */
        val fill: Boolean?,
        /** Whether the file outlines the path, and whether that outline is the dark tone. */
        val stroke: Boolean?,
        /** The width of that outline, in the artwork's own units. */
        val strokeWidth: Float,
    )

    /** The file a game's mark is drawn in. */
    fun file(kind: GameKind): String =
        when (kind) {
            GameKind.Pipes -> "pipes.svg"
            GameKind.Sudoku -> "sudoku.svg"
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
        /** Whether the fill is the dark tone, or null when there is no fill. */
        val fill: Boolean? = null,
        /** Whether the outline is the dark tone, or null when there is no outline. */
        val stroke: Boolean? = null,
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
                    fill = fill?.let(::isDark),
                    stroke = stroke?.let(::isDark),
                    strokeWidth = width ?: 0f,
                )
        }
        return styles
    }

    /** Returns whether an SVG colour is the dark tone of a mark. */
    private fun isDark(value: String): Boolean {
        if (value.equals("none", ignoreCase = true)) {
            return false
        }
        val hex = value.removePrefix("#")
        val rgb = hex.toLongOrNull(16) ?: return true
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        return (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0 < 0.5
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
