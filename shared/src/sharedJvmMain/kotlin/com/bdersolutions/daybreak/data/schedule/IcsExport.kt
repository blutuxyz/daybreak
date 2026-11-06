package com.bdersolutions.daybreak.data.schedule

import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Makes an iCalendar file (RFC 5545) from lessons and exams, for the supporter extra "export to a
 * calendar". Pure and shared by the Android and the desktop app; the platforms only save or share
 * the text.
 *
 * - Cancelled lessons and the synthetic holiday blocks are left out: nothing takes place then.
 * - Lessons that follow each other without a gap and share subject, teacher, room and class become
 *   one event (a double lesson is one entry in the calendar).
 * - Times are written in UTC, so no VTIMEZONE block is needed and every calendar app shows the right
 *   local time.
 * - Lines are folded at 75 bytes and text is escaped as the standard demands.
 */
object IcsExport {
    private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** Wording that depends on the app's language. */
    data class Labels(
        val calendarName: String,
        val examPrefix: String,
        val teacher: String,
        val className: String,
        val substituted: String,
    )

    fun build(
        lessons: Collection<UntisLesson>,
        exams: Collection<UntisExam>,
        labels: Labels,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): String {
        val out = ArrayList<String>()
        out += "BEGIN:VCALENDAR"
        out += "VERSION:2.0"
        out += "PRODID:-//Daybreak//Timetable//EN"
        out += "CALSCALE:GREGORIAN"
        out += "METHOD:PUBLISH"
        out += "X-WR-CALNAME:${escape(labels.calendarName)}"

        val inRange = lessons.filter { !it.isCancelled && !it.isHoliday && it.date in from..to }
        for (block in merged(inRange)) {
            out += "BEGIN:VEVENT"
            out += "UID:${block.uid}@daybreak"
            out += "DTSTAMP:${stamp.format(now)}"
            out += "DTSTART:${utc(block.start, zone)}"
            out += "DTEND:${utc(block.end, zone)}"
            out += "SUMMARY:${escape(block.title)}"
            if (block.room.isNotBlank()) out += "LOCATION:${escape(block.room)}"
            val description = block.description(labels)
            if (description.isNotBlank()) out += "DESCRIPTION:${escape(description)}"
            out += "END:VEVENT"
        }

        for (exam in exams.filter { it.date in from..to }.sortedWith(compareBy({ it.date }, { it.start }))) {
            out += "BEGIN:VEVENT"
            out += "UID:exam-${exam.id}-${exam.date}@daybreak"
            out += "DTSTAMP:${stamp.format(now)}"
            out += "DTSTART:${utc(LocalDateTime.of(exam.date, exam.start), zone)}"
            out += "DTEND:${utc(LocalDateTime.of(exam.date, exam.end), zone)}"
            val name = listOf(exam.subject, exam.name).filter { it.isNotBlank() }.distinct().joinToString(" ")
            out += "SUMMARY:${escape("${labels.examPrefix}: $name".trim().trimEnd(':'))}"
            if (exam.text.isNotBlank()) out += "DESCRIPTION:${escape(exam.text)}"
            out += "END:VEVENT"
        }

        out += "END:VCALENDAR"
        return out.joinToString(separator = "") { fold(it) + "\r\n" }
    }

    /** One entry of the calendar: a lesson, or several that were joined. */
    internal data class Block(
        val first: UntisLesson,
        val start: LocalDateTime,
        val end: LocalDateTime,
    ) {
        val title: String get() = first.subjectLong.ifBlank { first.subject }.ifBlank { "?" }
        val room: String get() = first.room
        val uid: String get() = "lesson-${first.id}-${first.date}-${first.start}"

        fun description(labels: Labels): String = buildList {
            if (first.teacher.isNotBlank()) add("${labels.teacher}: ${first.teacher}")
            if (first.className.isNotBlank()) add("${labels.className}: ${first.className}")
            if (first.hasSubstitution) add(labels.substituted)
            first.substitutionText.takeIf { it.isNotBlank() }?.let(::add)
            first.lessonText.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString("\n")
    }

    internal fun merged(lessons: List<UntisLesson>): List<Block> {
        val sorted = lessons.sortedWith(compareBy({ it.date }, { it.start }, { it.id }))
        val blocks = ArrayList<Block>()
        for (lesson in sorted) {
            val previous = blocks.lastOrNull()
            val joins = previous != null &&
                previous.first.date == lesson.date &&
                previous.end == lesson.startsAt &&
                previous.first.subject == lesson.subject &&
                previous.first.teacher == lesson.teacher &&
                previous.first.room == lesson.room &&
                previous.first.className == lesson.className
            if (joins) {
                blocks[blocks.lastIndex] = previous!!.copy(end = lesson.endsAt)
            } else {
                blocks += Block(lesson, lesson.startsAt, lesson.endsAt)
            }
        }
        return blocks
    }

    private fun utc(local: LocalDateTime, zone: ZoneId): String =
        stamp.format(local.atZone(zone).toInstant())

    /** Backslash, semicolon, comma and line breaks, as RFC 5545 section 3.3.11 requires. */
    internal fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

    /** Folds a content line to at most 75 bytes per line, never inside a multi-byte character. */
    internal fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val result = StringBuilder()
        var bytes = 0
        var limit = 75
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > limit) {
                result.append("\r\n ")
                bytes = 0
                limit = 74
            }
            result.appendCodePoint(codePoint)
            bytes += size
            index += chars
        }
        return result.toString()
    }
}
