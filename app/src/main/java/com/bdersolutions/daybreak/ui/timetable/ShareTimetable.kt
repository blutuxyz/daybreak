package com.bdersolutions.daybreak.ui.timetable

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.schedule.ShareDay
import com.bdersolutions.daybreak.data.schedule.ShareLabels
import com.bdersolutions.daybreak.data.schedule.ShareText
import com.bdersolutions.daybreak.untis.UntisLesson
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shares the timetable of some days as text or as a picture, through the Android share sheet. */
internal object ShareTimetable {
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun labels(context: Context): ShareLabels {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val heading = DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale)
        return ShareLabels(
            noLessons = context.getString(R.string.share_no_lessons),
            cancelled = context.getString(R.string.share_cancelled),
            room = { context.getString(R.string.share_room, it) },
            dateHeading = { it.format(heading) },
        )
    }

    fun days(context: Context, dates: List<LocalDate>, lessons: List<UntisLesson>): List<ShareDay> {
        val labels = labels(context)
        return dates.map { ShareText.day(it, lessons, labels) }
    }

    fun shareText(context: Context, title: String, days: List<ShareDay>) {
        val text = title + "\n\n" + ShareText.format(days, labels(context))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(chooser(context, send))
    }

    fun shareImage(context: Context, title: String, appTitle: String, days: List<ShareDay>) {
        val bitmap = render(context, title, appTitle, days)
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        // One file, replaced each time, so old pictures do not pile up in the cache.
        val file = File(directory, "timetable.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser(context, send))
    }

    private fun chooser(context: Context, send: Intent): Intent =
        Intent.createChooser(send, context.getString(R.string.share_chooser)).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Draws the days on a light card with a dawn-coloured header, 1080 pixels wide. */
    private fun render(context: Context, title: String, appTitle: String, days: List<ShareDay>): Bitmap {
        val labels = labels(context)
        val width = 1080
        val margin = 56f
        val headerHeight = 220f
        val rowHeight = 62f
        val dayGap = 40f
        val headingHeight = 76f

        val body = days.sumOf { day ->
            (headingHeight + rowHeight * day.lines.size.coerceAtLeast(1) + dayGap).toDouble()
        }.toFloat()
        val footer = 84f
        val height = (headerHeight + 36f + body + footer).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FFFAF8F5"))

        // Header: the sky of the app logo, indigo to orange.
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), headerHeight,
                intArrayOf(Color.parseColor("#FF1A1D4A"), Color.parseColor("#FF5A3F9E"), Color.parseColor("#FFE0607E")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerPaint)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(ellipsize(title, titlePaint, width - 2 * margin), margin, 118f, titlePaint)

        val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1D1B20"); textSize = 40f }
        val muted = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8A8590"); textSize = 40f }
        val accent = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF5A3F9E")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bold = TextPaint(ink).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

        var y = headerHeight + 36f
        for (day in days) {
            y += headingHeight - 18f
            canvas.drawText(labels.dateHeading(day.date), margin, y, accent)
            y += 18f
            if (day.lines.isEmpty()) {
                y += rowHeight - 20f
                canvas.drawText(labels.noLessons, margin, y, muted)
                y += 20f
            }
            for (line in day.lines) {
                y += rowHeight - 20f
                val paint = if (line.cancelled) TextPaint(muted).apply { flags = flags or Paint.STRIKE_THRU_TEXT_FLAG } else ink
                val time = "${line.start.format(CLOCK)}–${line.end.format(CLOCK)}"
                canvas.drawText(time, margin, y, muted)
                val textX = margin + 270f
                val titleWidth = bold.measureText(line.title)
                val strike = if (line.cancelled) TextPaint(paint).apply { typeface = bold.typeface } else bold
                canvas.drawText(line.title, textX, y, strike)
                val detailText = listOf(line.details, if (line.cancelled) "(${labels.cancelled})" else "")
                    .filter { it.isNotBlank() }.joinToString(" ")
                val detailX = textX + titleWidth + 24f
                val room = width - margin - detailX
                if (room > 60f && detailText.isNotBlank()) {
                    canvas.drawText(ellipsize(detailText, muted, room), detailX, y, muted)
                }
                y += 20f
            }
            y += dayGap
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB5B0BA"); textSize = 34f }
        canvas.drawText(appTitle, margin, height - 36f, footerPaint)
        return bitmap
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
}
