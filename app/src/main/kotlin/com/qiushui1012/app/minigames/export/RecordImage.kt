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

package com.qiushui1012.app.minigames.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.qiushui1012.app.minigames.data.Record
import com.qiushui1012.app.minigames.data.RecordKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Draws records as an image the player can keep.
 *
 * # Why this is written against `android.graphics` rather than the composition
 *
 * An image is not a screen: it is drawn once, at a width and a height decided by the records,
 * and it must be the same image whatever the window is doing at the time.
 *
 * # Where the text goes
 *
 * Every line is placed by the top of its own line box, and the baseline is derived from the
 * paint's own font metrics. Constants that guessed a baseline put two strings on top of each
 * other at the bottom of a card, which is a picture that says the finish time twice, in two
 * sizes, in the same place.
 *
 * # Two layouts
 *
 * One record is a card: the game's name as the title, its parameters beside the title, then the
 * time, the player and when it finished. Several records are a card holding a table whose
 * columns are as wide as their contents require, so the image is as wide as the table rather
 * than a fixed size with slack at its sides.
 *
 * Both put the export time outside the card, at the bottom of the image and centred: it is a
 * note about the file, not a fact about the game.
 *
 * # Why the text is antialiased and subpixel-positioned
 *
 * An exported image is looked at on a screen, where a glyph drawn at a whole-pixel baseline
 * and without antialiasing is a glyph with a staircase edge. [Paint.isSubpixelText] places a
 * glyph at the fraction of a pixel its layout calls for, and [Paint.isLinearText] keeps the
 * layout in its unrounded form; the two together are what makes text at this size read as text
 * rather than as a bitmap of text.
 */
object RecordImage {
    /** The width of a single record's image, in pixels. */
    private const val WIDTH = 1080

    /** The margin between the image's edge and the card. */
    private const val MARGIN = 64f

    /** The padding inside a card. */
    private const val PADDING = 56f

    /** The corner radius of a card. */
    private const val CARD_RADIUS = 40f

    /** The plate of the mark: the background everything sits on. */
    private val plate = Color.rgb(0xEA, 0xF2, 0xF4)

    /** The line work of the mark. */
    private val ink = Color.rgb(0x09, 0x10, 0x12)

    /** The muted ink of secondary text. */
    private val muted = Color.rgb(0x44, 0x54, 0x5A)

    /** The white a card sits on. */
    private val surface = Color.WHITE

    /** How a record's finish time is written. */
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** The gap between the game's name and its parameters on the title line. */
    private const val TITLE_GAP = 20f

    /** The font size of a game's name, which is the title of either layout. */
    private const val TITLE_SIZE = 64f

    /** The font size of the parameters beside a game's name. */
    private const val PARAMS_SIZE = 34f

    /** The height of the title line. */
    private const val TITLE_LINE = 92f

    /** The font size of the time, which is the largest fact on a single card. */
    private const val TIME_SIZE = 128f

    /** The height of the time's line. */
    private const val TIME_LINE = 172f

    /** The font size of a player's name and a finish time. */
    private const val DETAIL_SIZE = 42f

    /** The height of one detail line. */
    private const val DETAIL_LINE = 62f

    /** The font size of the export time below the card. */
    private const val EXPORT_SIZE = 30f

    /** The gap between a card and the export time below it. */
    private const val EXPORT_GAP = 48f

    /** The height of the export time's line. */
    private const val EXPORT_LINE = 40f

    /** The padding of one table cell, on each side. */
    private const val CELL_PADDING = 28f

    /** The height of a table row. */
    private const val ROW_HEIGHT = 78f

    /** The height of the table's heading row. */
    private const val HEADING_HEIGHT = 52f

    /** The gap between the title of a table and its heading row. */
    private const val TABLE_GAP = 30f

    /** The font size of a table's heading row. */
    private const val HEADING_SIZE = 32f

    /** The font size of a table's body. */
    private const val BODY_SIZE = 36f

    /** The gap between a table cell's game name and its parameters. */
    private const val CELL_GAP = 14f

    /**
     * Returns the image of one record.
     *
     * [name] is the game's name and [params] its parameters, both in the language the
     * interface is in, so an exported card says "简单" or "Easy" for the same board rather
     * than the storage name. [unknown] is what an unnamed player is called, for the same
     * reason: a dash is not a word. [durationTypeface] is the monospaced face the time is
     * written in, and [failed] and [typed] are the two words the card adds to say how the game
     * ended and how it was started.
     */
    fun single(
        record: Record,
        name: (RecordKey) -> String,
        params: (RecordKey) -> String,
        unknown: String,
        exportedAt: String,
        durationTypeface: Typeface,
        failed: String? = null,
        typed: String? = null,
    ): Bitmap {
        val cardTop = MARGIN
        val contentTop = cardTop + PADDING
        val titleTop = contentTop
        val timeTop = titleTop + TITLE_LINE
        val playerTop = timeTop + TIME_LINE
        val finishedTop = playerTop + DETAIL_LINE
        val cardBottom = finishedTop + DETAIL_LINE + PADDING
        val exportTop = cardBottom + EXPORT_GAP
        val height = (exportTop + EXPORT_LINE + MARGIN).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(plate)

        val card = RectF(MARGIN, cardTop, WIDTH - MARGIN, cardBottom)
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, paint(color = surface))

        val left = MARGIN + PADDING
        val title = paint(color = ink, size = TITLE_SIZE, bold = true)
        val detail = paint(color = muted, size = DETAIL_SIZE)
        val titleText = name(record.key)
        canvas.text(titleText, left, titleTop, title)
        // The parameters stand beside the name rather than under it: the name is the title of
        // the card and the parameters qualify it, which is one line's worth of reading. They
        // sit on the title's own baseline so that the two do not float against each other.
        var paramsLeft = left + title.measureText(titleText) + TITLE_GAP
        val paramsPaint = paint(color = muted, size = PARAMS_SIZE)
        canvas.textBeside(params(record.key), paramsLeft, titleTop, title, paramsPaint)
        // A hand-picked seed is said on the parameters' own line, right after them: it is a
        // note about the puzzle rather than about the game's outcome, and the time below is
        // where the outcome belongs.
        if (record.typedSeed && typed != null) {
            paramsLeft += paramsPaint.measureText(params(record.key)) + TITLE_GAP
            canvas.textBeside(typed, paramsLeft, titleTop, title, paramsPaint)
        }

        // A lost game's time is still a fact, so the word stands beside it rather than
        // replacing it: the card says what happened and how long it took to happen.
        val timePaint = paint(color = ink, size = TIME_SIZE, bold = true, typeface = durationTypeface)
        if (record.failed == true && failed != null) {
            val label = paint(color = ink, size = DETAIL_SIZE, bold = true)
            var timeLeft = left
            val labelBaselineGap = (TIME_LINE - DETAIL_LINE) / 2f
            canvas.text(failed, timeLeft, timeTop + labelBaselineGap, label)
            timeLeft += label.measureText(failed) + TITLE_GAP
            canvas.text(formatDuration(record.seconds), timeLeft, timeTop, timePaint)
        } else {
            canvas.text(formatDuration(record.seconds), left, timeTop, timePaint)
        }
        canvas.text(record.playerName.ifBlank { unknown }, left, playerTop, detail)
        canvas.text(formatStamp(record.finishedAt), left, finishedTop, detail)

        // Read out of the card's own geometry, so the note cannot drift onto the card.
        canvas.centred(exportedAt, WIDTH / 2f, exportTop, paint(color = muted, size = EXPORT_SIZE))

        return bitmap
    }

    /**
     * Returns the image of several records, one row each.
     *
     * The table decides the image: every column is as wide as the widest of its heading and its
     * cells, so nothing is truncated and nothing is given room it does not use. The image is
     * therefore as wide as the table plus the card's padding and the margins, and as tall as
     * the records require.
     */
    fun several(
        records: List<Record>,
        name: (RecordKey) -> String,
        params: (RecordKey) -> String,
        unknown: String,
        exportedAt: String,
        title: String,
        headers: List<String>,
        durationTypeface: Typeface,
        failed: String? = null,
        typed: String? = null,
    ): Bitmap {
        val headingPaint = paint(color = muted, size = HEADING_SIZE)
        val bodyPaint = paint(color = ink, size = BODY_SIZE)
        val timePaint = paint(color = ink, size = BODY_SIZE, typeface = durationTypeface)
        val paramsPaint = paint(color = muted, size = PARAMS_SIZE - 2f)
        // A lost game's time cell leads with the word, so the column is measured with it: a
        // column sized for a duration alone would clip the word off the front of its row.
        val timeText: (Record) -> String = { record ->
            if (record.failed == true && failed != null) {
                "$failed ${formatDuration(record.seconds)}"
            } else {
                formatDuration(record.seconds)
            }
        }
        val paramsText: (Record) -> String = { record ->
            if (record.typedSeed && typed != null) {
                "${params(record.key)} $typed"
            } else {
                params(record.key)
            }
        }

        // One cell per column, already reduced to the width it asks for.
        val gameWidth: (Record) -> Float = { record ->
            bodyPaint.measureText(name(record.key)) + CELL_GAP + paramsPaint.measureText(paramsText(record))
        }
        val widths =
            floatArrayOf(
                columnWidth(headers[0].widthIn(headingPaint), records.map { it.playerName.ifBlank { unknown }.widthIn(bodyPaint) }),
                columnWidth(headers[1].widthIn(headingPaint), records.map(gameWidth)),
                columnWidth(headers[2].widthIn(headingPaint), records.map { timeText(it).widthIn(timePaint) }),
                columnWidth(headers[3].widthIn(headingPaint), records.map { formatStamp(it.finishedAt).widthIn(bodyPaint) }),
            )
        val tableWidth = widths.sum()
        val cardWidth = tableWidth + PADDING * 2
        val imageWidth = (cardWidth + MARGIN * 2).toInt()

        val cardTop = MARGIN
        val titleTop = cardTop + PADDING
        val headingTop = titleTop + TITLE_LINE + TABLE_GAP
        val rowsTop = headingTop + HEADING_HEIGHT
        val cardBottom = rowsTop + ROW_HEIGHT * records.size + PADDING
        val exportTop = cardBottom + EXPORT_GAP
        val height = (exportTop + EXPORT_LINE + MARGIN).toInt()

        val bitmap = Bitmap.createBitmap(imageWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(plate)

        val card = RectF(MARGIN, cardTop, imageWidth - MARGIN, cardBottom)
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, paint(color = surface))

        val tableLeft = MARGIN + PADDING
        canvas.text(title, tableLeft, titleTop, paint(color = ink, size = TITLE_SIZE, bold = true))

        var x = tableLeft
        headers.forEachIndexed { index, header ->
            canvas.cell(header, x + CELL_PADDING, headingTop, headingPaint)
            x += widths[index]
        }

        val rule = paint(color = Color.argb(40, 0x44, 0x54, 0x5A), stroke = 2f)
        records.forEachIndexed { index, record ->
            val top = rowsTop + ROW_HEIGHT * index
            var column = tableLeft
            canvas.cell(record.playerName.ifBlank { unknown }, column + CELL_PADDING, top, bodyPaint)
            column += widths[0]
            // The game column is the single card's title line in miniature: the name, then its
            // parameters in a smaller, muted face on the same baseline.
            val gameName = name(record.key)
            canvas.cell(gameName, column + CELL_PADDING, top, bodyPaint)
            canvas.cellBeside(
                paramsText(record),
                column + CELL_PADDING + bodyPaint.measureText(gameName) + CELL_GAP,
                top,
                bodyPaint,
                paramsPaint,
            )
            column += widths[1]
            canvas.cell(timeText(record), column + CELL_PADDING, top, timePaint)
            column += widths[2]
            canvas.cell(formatStamp(record.finishedAt), column + CELL_PADDING, top, bodyPaint)
            canvas.drawLine(
                tableLeft,
                top + ROW_HEIGHT - 8f,
                tableLeft + tableWidth,
                top + ROW_HEIGHT - 8f,
                rule,
            )
        }

        canvas.centred(exportedAt, imageWidth / 2f, exportTop, paint(color = muted, size = EXPORT_SIZE))

        return bitmap
    }

    /** Returns the width of a single record's image. */
    fun width(): Int = WIDTH

    /**
     * Returns the width a column needs: its widest cell, plus the cell padding.
     *
     * The heading is measured as one of the cells, so a column is never narrower than its
     * title. [cells] are already measured widths, so the same function does for a column of
     * plain text and for the game column, whose cell is a name and its parameters together.
     */
    private fun columnWidth(
        headingWidth: Float,
        cells: List<Float>,
    ): Float = maxOf(headingWidth, cells.maxOrNull() ?: 0f) + CELL_PADDING * 2

    /** Returns how wide a string is in a paint. */
    private fun String.widthIn(paint: Paint): Float = paint.measureText(this)

    /**
     * Draws a string with the top of its line box at [top].
     *
     * The baseline is the paint's own ascent below the top, so every string is placed by the
     * box it occupies rather than by a number guessed per call site.
     */
    private fun Canvas.text(
        text: String,
        x: Float,
        top: Float,
        paint: Paint,
    ) {
        drawText(text, x, baselineOf(top, paint), paint)
    }

    /** Draws a string on the baseline another string's line box puts its own on. */
    private fun Canvas.textBeside(
        text: String,
        x: Float,
        top: Float,
        line: Paint,
        paint: Paint,
    ) {
        drawText(text, x, baselineOf(top, line), paint)
    }

    /**
     * Draws a table cell, vertically centred in a row whose top is [rowTop].
     *
     * A row is a fixed height and the faces in it are not, so a cell is placed by the middle
     * of its own line box rather than by the top: aligning two cells of different sizes by
     * their tops leaves one of them visibly low.
     */
    private fun Canvas.cell(
        text: String,
        x: Float,
        rowTop: Float,
        paint: Paint,
    ) {
        val metrics = paint.fontMetrics
        val top = rowTop + (ROW_HEIGHT - (metrics.descent - metrics.ascent)) / 2f
        drawText(text, x, top - metrics.ascent, paint)
    }

    /** Draws a table cell's second string on the same baseline as its first. */
    private fun Canvas.cellBeside(
        text: String,
        x: Float,
        rowTop: Float,
        line: Paint,
        paint: Paint,
    ) {
        val metrics = line.fontMetrics
        val top = rowTop + (ROW_HEIGHT - (metrics.descent - metrics.ascent)) / 2f
        drawText(text, x, top - metrics.ascent, paint)
    }

    /** Returns the baseline a line whose box starts at [top] is drawn on. */
    private fun baselineOf(
        top: Float,
        paint: Paint,
    ): Float = top - paint.fontMetrics.ascent

    /** Draws a string centred on [centreX] with the top of its line box at [top]. */
    private fun Canvas.centred(
        text: String,
        centreX: Float,
        top: Float,
        paint: Paint,
    ) {
        drawText(text, centreX - paint.measureText(text) / 2f, baselineOf(top, paint), paint)
    }

    /**
     * Builds a paint, so each call site says only what it changes.
     *
     * The antialiasing smooths a glyph's edges, and the subpixel flag places each of them at
     * the fraction of a pixel its layout calls for instead of snapping it to whole pixels —
     * at these sizes that is the difference between text and a staircase of text.
     */
    private fun paint(
        color: Int,
        size: Float = 40f,
        bold: Boolean = false,
        stroke: Float = 0f,
        typeface: Typeface? = null,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            textSize = size
            isSubpixelText = true
            isFilterBitmap = true
            this.typeface =
                when {
                    typeface != null -> if (bold) Typeface.create(typeface, Typeface.BOLD) else typeface
                    bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    else -> Typeface.DEFAULT
                }
            if (stroke > 0f) {
                style = Paint.Style.STROKE
                strokeWidth = stroke
            }
        }

    /** Returns a duration as `m:ss`, or `h:mm:ss` past an hour. */
    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainder = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, remainder)
        } else {
            "%d:%02d".format(minutes, remainder)
        }
    }

    /** Returns a finish time in the device's own zone. */
    private fun formatStamp(milliseconds: Long): String =
        stamp.format(Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault()))
}
