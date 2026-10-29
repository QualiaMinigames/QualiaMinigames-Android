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
import kotlin.math.roundToInt

/**
 * Draws records as an image the player can keep.
 *
 * # Why this is written against `android.graphics` rather than the composition
 *
 * An image is not a screen: it is drawn once, at a width and a height that are decided by
 * the number of records rather than by the size of the window, and it must be the same image
 * whatever the window is doing at the time. Drawing it with the same primitives the rest of
 * the platform uses also means it can be produced without a composition at all, which is what
 * lets the export run while the records screen is scrolled somewhere else.
 *
 * # Two layouts
 *
 * One record is drawn as a card: the game, the player, the time and when it finished, with
 * enough room to read as a thing rather than as a row of a table. Several records are drawn
 * as one column: a header, then one line per record. The height of the column follows the
 * number of records, and it is never cut, paged or scaled down to fit something else.
 */
object RecordImage {
    /** The width of every image, in pixels. */
    private const val WIDTH = 1080

    /** The margin around the content. */
    private const val MARGIN = 64f

    /** The corner radius of a single record's card. */
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

    /**
     * Returns the image of one record.
     *
     * [label] reads a record's parameters in the language the interface is in, so that an
     * exported card says "接水管" or "Pipes" for the same board.
     */
    fun single(record: Record, label: (RecordKey) -> String, exportedAt: String): Bitmap {
        val height = 640
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(plate)

        val card = RectF(MARGIN, MARGIN, WIDTH - MARGIN, height - MARGIN)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface }
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, cardPaint)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(label(record.key), MARGIN + 56f, MARGIN + 140f, title)

        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 120f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(formatDuration(record.seconds), MARGIN + 56f, MARGIN + 300f, value)

        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 44f
        }
        val player = record.playerName.ifBlank { "—" }
        canvas.drawText(player, MARGIN + 56f, MARGIN + 380f, detail)
        canvas.drawText(formatStamp(record.finishedAt), MARGIN + 56f, MARGIN + 444f, detail)
        canvas.drawText(exportedAt, MARGIN + 56f, height - MARGIN - 56f, detail)

        return bitmap
    }

    /**
     * Returns the image of several records, one line each.
     *
     * The image is as tall as the records are: nothing is left out and nothing is scaled to
     * fit a fixed frame.
     */
    fun several(records: List<Record>, label: (RecordKey) -> String, exportedAt: String): Bitmap {
        val header = 260
        val rowHeight = 96
        val height = header + rowHeight * records.size + 180
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(plate)

        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(exportedAt, MARGIN, MARGIN + 70f, heading)

        val column = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 36f
        }
        val columns = floatArrayOf(MARGIN, MARGIN + 300f, MARGIN + 640f, MARGIN + 860f)
        canvas.drawText(TABLE_HEADER, MARGIN, MARGIN + 160f, column)

        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 40f }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 40f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0x44, 0x54, 0x5A); strokeWidth = 2f }

        records.forEachIndexed { index, record ->
            val top = header + rowHeight * index
            canvas.drawText(record.playerName.ifBlank { "—" }, columns[0], top + 48f, name)
            canvas.drawText(label(record.key), columns[1], top + 48f, body)
            canvas.drawText(formatDuration(record.seconds), columns[2], top + 48f, body)
            canvas.drawText(formatStamp(record.finishedAt), columns[3], top + 48f, body)
            canvas.drawLine(MARGIN, top + rowHeight - 12f, WIDTH - MARGIN, top + rowHeight - 12f, line)
        }

        return bitmap
    }

    /** The header the several-record layout draws. */
    private const val TABLE_HEADER = "Player · Game · Time · Finished"

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

    /** Returns the height [several] would produce, which a caller sizes its preview by. */
    fun severalHeight(count: Int): Int = 260 + 96 * count + 180

    /** Returns the width every image has. */
    fun width(): Int = WIDTH


}
