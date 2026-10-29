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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Draws records as an image the player can keep.
 *
 * # Why this is written against `android.graphics` rather than the composition
 *
 * An image is not a screen: it is drawn once, at a width and a height decided by the number of
 * records rather than by the size of the window, and it must be the same image whatever the
 * window is doing at the time. Drawing it with the platform's own primitives also means it can
 * be produced without a composition at all, which is what lets the export run while the records
 * screen is scrolled somewhere else.
 *
 * # Nothing is written past the edge of the card
 *
 * Every string is placed at a y that was reserved for it, and the heights are constants rather
 * than the result of measuring one line after another. Two strings that both landed near the
 * bottom of the card used to overlap, which is a picture that says the finish time twice, in
 * two sizes, on top of itself.
 *
 * # Two layouts
 *
 * One record is a card: the game, the time, the player and when it finished, with enough room
 * to read as a thing rather than as a row of a table. Several records are one column: a header,
 * then one line per record. The column grows with the number of records and is never cut, paged
 * or scaled to fit anything.
 */
object RecordImage {
    /** The width of every image, in pixels. */
    private const val WIDTH = 1080

    /** The margin around the content. */
    private const val MARGIN = 64f

    /** The padding inside a card. */
    private const val PADDING = 56f

    /** The corner radius of a single record's card. */
    private const val CARD_RADIUS = 40f

    /** The height of a single record's card, which is one line per fact plus the padding. */
    private const val CARD_HEIGHT = 720f

    /** The height of the header above a column of records. */
    private const val COLUMN_HEADER = 260f

    /** The height of one line of a column. */
    private const val ROW_HEIGHT = 96f

    /** The height of the strip below a column, where the export time is written. */
    private const val COLUMN_FOOTER = 180f

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

    /**
     * Returns the image of one record.
     *
     * [label] reads a record's parameters in the language the interface is in, so an exported
     * card says "简单" or "Easy" for the same board rather than the storage name. [unknown]
     * is what an unnamed player is called, for the same reason: a dash is not a word.
     */
    fun single(
        record: Record,
        label: (com.qiushui1012.app.minigames.data.RecordKey) -> String,
        unknown: String,
        exportedAt: String,
    ): Bitmap {
        val height = CARD_HEIGHT.toInt()
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(plate)

        val card = RectF(MARGIN, MARGIN, WIDTH - MARGIN, height - MARGIN)
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, paint(color = surface))

        val left = MARGIN + PADDING
        val title = paint(color = ink, size = 64f, bold = true)
        val value = paint(color = ink, size = 120f, bold = true)
        val detail = paint(color = muted, size = 44f)

        canvas.drawText(label(record.key), left, MARGIN + 130f, title)
        canvas.drawText(formatDuration(record.seconds), left, MARGIN + 290f, value)
        canvas.drawText(record.playerName.ifBlank { unknown }, left, MARGIN + 370f, detail)
        canvas.drawText(formatStamp(record.finishedAt), left, MARGIN + 434f, detail)
        // The export time sits at the bottom of the card, below the finish time rather than
        // beside it, with the card's own padding between them.
        canvas.drawText(exportedAt, left, height - MARGIN - PADDING, detail)

        return bitmap
    }

    /**
     * Returns the image of several records, one line each.
     *
     * The image is as tall as the records are: nothing is left out and nothing is scaled to fit
     * a fixed frame.
     */
    fun several(
        records: List<Record>,
        label: (com.qiushui1012.app.minigames.data.RecordKey) -> String,
        unknown: String,
        exportedAt: String,
        headers: List<String>,
    ): Bitmap {
        val height = severalHeight(records.size)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(plate)

        val heading = paint(color = ink, size = 56f, bold = true)
        canvas.drawText(exportedAt, MARGIN, MARGIN + 70f, heading)

        // Four columns: player, game, time, finished. The widths are fractions of the usable
        // width rather than fixed offsets, so a long game name cannot push the time off the
        // right edge.
        val usable = WIDTH - MARGIN * 2
        val columns =
            floatArrayOf(
                MARGIN,
                MARGIN + usable * 0.30f,
                MARGIN + usable * 0.62f,
                MARGIN + usable * 0.80f,
            )
        val column = paint(color = muted, size = 34f)
        headers.forEachIndexed { index, header ->
            if (index < columns.size) {
                canvas.drawText(header, columns[index], MARGIN + 156f, column)
            }
        }

        val body = paint(color = ink, size = 38f)
        val rule = paint(color = Color.argb(60, 0x44, 0x54, 0x5A), stroke = 2f)

        records.forEachIndexed { index, record ->
            val top = COLUMN_HEADER + ROW_HEIGHT * index
            val baseline = top + 48f
            canvas.drawText(record.playerName.ifBlank { unknown }, columns[0], baseline, body)
            canvas.drawText(label(record.key), columns[1], baseline, body)
            canvas.drawText(formatDuration(record.seconds), columns[2], baseline, body)
            canvas.drawText(formatStamp(record.finishedAt), columns[3], baseline, body)
            canvas.drawLine(MARGIN, top + ROW_HEIGHT - 12f, WIDTH - MARGIN, top + ROW_HEIGHT - 12f, rule)
        }

        return bitmap
    }

    /** Returns the height [several] would produce, which a caller sizes its preview by. */
    fun severalHeight(count: Int): Int = (COLUMN_HEADER + ROW_HEIGHT * count + COLUMN_FOOTER).toInt()

    /** Returns the width every image has. */
    fun width(): Int = WIDTH

    /** Builds a paint, so each call site says only what it changes. */
    private fun paint(
        color: Int,
        size: Float = 40f,
        bold: Boolean = false,
        stroke: Float = 0f,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
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
