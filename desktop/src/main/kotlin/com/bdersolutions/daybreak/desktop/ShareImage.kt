package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.schedule.ShareDay
import com.bdersolutions.daybreak.data.schedule.ShareLabels
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Draws the days as a picture for sharing: the same layout as the Android app's share image, a
 * 1080 pixel wide card with a dawn gradient header, the days with their lessons, and the app name.
 */
fun renderTimetableImage(title: String, appTitle: String, days: List<ShareDay>, labels: ShareLabels): BufferedImage {
    val width = 1080
    val margin = 56
    val headerHeight = 220
    val rowHeight = 62
    val dayGap = 40
    val headingHeight = 76
    val footer = 84

    val body = days.sumOf { day -> headingHeight + rowHeight * day.lines.size.coerceAtLeast(1) + dayGap }
    val height = headerHeight + 36 + body + footer

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color.decode("#FFFAF8F5")
    g.fillRect(0, 0, width, height)

    // Header: the sky of the app logo, indigo to orange.
    g.paint = GradientPaint(0f, 0f, Color.decode("#FF1A1D4A"), width.toFloat(), headerHeight.toFloat(), Color.decode("#FFE0607E"))
    g.fillRect(0, 0, width, headerHeight)
    val titleFont = Font(Font.SANS_SERIF, Font.BOLD, 64)
    g.font = titleFont
    g.color = Color.WHITE
    g.drawString(ellipsize(g, title, titleFont, (width - 2 * margin).toFloat()), margin.toFloat(), 118f)

    val ink = Color.decode("#FF1D1B20")
    val muted = Color.decode("#FF8A8590")
    val accent = Color.decode("#FF5A3F9E")
    val regular = Font(Font.SANS_SERIF, Font.PLAIN, 40)
    val bold = Font(Font.SANS_SERIF, Font.BOLD, 40)
    val accentFont = Font(Font.SANS_SERIF, Font.BOLD, 42)

    var y = headerHeight + 36f
    for (day in days) {
        y += headingHeight - 18f
        g.font = accentFont
        g.color = accent
        g.drawString(labels.dateHeading(day.date), margin.toFloat(), y)
        y += 18f
        if (day.lines.isEmpty()) {
            y += rowHeight - 20f
            g.font = regular
            g.color = muted
            g.drawString(labels.noLessons, margin.toFloat(), y)
            y += 20f
        }
        for (line in day.lines) {
            y += rowHeight - 20f
            g.font = regular
            g.color = muted
            g.drawString("${line.start.format(clock)}–${line.end.format(clock)}", margin.toFloat(), y)
            val textX = margin + 270f
            g.font = bold
            g.color = if (line.cancelled) muted else ink
            g.drawString(line.title, textX, y)
            val titleWidth = g.fontMetrics.stringWidth(line.title)
            // A struck-through title for a cancelled lesson, as the Android picture shows it.
            if (line.cancelled) {
                val strike = y - g.fontMetrics.ascent / 3f
                g.drawLine(textX.toInt(), strike.toInt(), (textX + titleWidth).toInt(), strike.toInt())
            }
            val detail = listOf(line.details, if (line.cancelled) "(${labels.cancelled})" else "")
                .filter { it.isNotBlank() }.joinToString(" ")
            val detailX = textX + titleWidth + 24f
            val room = width - margin - detailX
            if (room > 60f && detail.isNotBlank()) {
                g.font = regular
                g.color = muted
                g.drawString(ellipsize(g, detail, regular, room), detailX, y)
            }
            y += 20f
        }
        y += dayGap
    }
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 34)
    g.color = Color.decode("#FFB5B0BA")
    g.drawString(appTitle, margin.toFloat(), (height - 36).toFloat())
    g.dispose()
    return image
}

/** Saves the picture where the person chooses; returns false when they cancel. */
fun saveTimetableImage(image: BufferedImage, suggestedName: String): Boolean {
    val target: File = pickSaveTarget(suggestedName) ?: return false
    return ImageIO.write(image, "png", target)
}

private fun ellipsize(g: java.awt.Graphics2D, text: String, font: Font, maxWidth: Float): String {
    val metrics = g.getFontMetrics(font)
    if (metrics.stringWidth(text) <= maxWidth) return text
    var cut = text.length
    while (cut > 0 && metrics.stringWidth(text.substring(0, cut) + "…") > maxWidth) cut--
    return text.substring(0, cut) + "…"
}
