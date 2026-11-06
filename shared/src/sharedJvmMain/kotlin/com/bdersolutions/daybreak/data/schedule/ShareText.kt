package com.bdersolutions.daybreak.data.schedule

import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The words the shared text needs, so this file stays free of Android resources. */
data class ShareLabels(
    val noLessons: String,
    val cancelled: String,
    val room: (String) -> String,
    val dateHeading: (LocalDate) -> String,
)

/** One line of the shared timetable. */
data class ShareLine(val start: LocalTime, val end: LocalTime, val title: String, val details: String, val cancelled: Boolean)

/** One day of the shared timetable. */
data class ShareDay(val date: LocalDate, val lines: List<ShareLine>)

/**
 * Turns timetable days into text that reads well pasted into a chat. Double lessons are one line,
 * cancelled lessons are marked, and the room and teacher come after the subject.
 */
object ShareText {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun day(date: LocalDate, lessons: List<UntisLesson>, labels: ShareLabels): ShareDay =
        ShareDay(
            date,
            NowAndNext.runs(lessons.filter { it.date == date }, includeCancelled = true).map { run ->
                val lesson = run.lesson
                val details = listOfNotNull(
                    lesson.room.takeIf { it.isNotBlank() }?.let(labels.room),
                    lesson.teacher.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                ShareLine(
                    start = run.start,
                    end = run.end,
                    title = lesson.subject.ifBlank { lesson.subjectLong }.ifBlank { "?" },
                    details = details,
                    cancelled = lesson.isCancelled,
                )
            },
        )

    /** The text for [days], one heading per day. A day without lessons says so. */
    fun format(days: List<ShareDay>, labels: ShareLabels): String =
        days.joinToString("\n\n") { day ->
            val heading = labels.dateHeading(day.date)
            if (day.lines.isEmpty()) {
                "$heading\n${labels.noLessons}"
            } else {
                heading + "\n" + day.lines.joinToString("\n") { line ->
                    val time = "${line.start.format(timeFormat)}–${line.end.format(timeFormat)}"
                    val body = listOf(line.title, line.details).filter { it.isNotBlank() }.joinToString(" · ")
                    if (line.cancelled) "$time  $body (${labels.cancelled})" else "$time  $body"
                }
            }
        }
}
