package com.bdersolutions.daybreak.data.assistant

import com.bdersolutions.daybreak.data.assistant.AssistantContext.DAY
import com.bdersolutions.daybreak.data.assistant.AssistantContext.TIME
import com.bdersolutions.daybreak.ui.absences.SchoolDayWindows
import com.bdersolutions.daybreak.ui.absences.countedDuration
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Something the assistant shows the student as a card instead of writing it out: a day of the timetable,
 * exams, or the absences. Every card is built from the student's real data by a tool on the phone, never
 * from the model's text, and is kept in saved chats so it reads the same later.
 */
sealed interface AssistantCard

/**
 * A website the assistant wrote (show_website): one HTML file with its CSS and JavaScript inside. The student
 * sees a card with the title, and a screen with a Preview and the Code.
 */
data class WebsiteCard(val title: String, val html: String) : AssistantCard {
    // The page can be long; keep it out of logs and toString.
    override fun toString(): String = "WebsiteCard($title, ${html.length} chars)"
}

/** Exams in a date range (show_exams), oldest first, with the lesson's teacher and room when one matches. */
data class ExamCard(
    val from: LocalDate,
    val to: LocalDate,
    val exams: List<Exam>,
) : AssistantCard {
    data class Exam(
        val date: LocalDate,
        val start: LocalTime,
        val end: LocalTime,
        /** The short subject name, which the lesson colours are keyed by. */
        val subject: String,
        val subjectName: String,
        /** What the school called it ("Schularbeit 2"). */
        val name: String = "",
        /** What the school entered as the topic or material, plain text. */
        val text: String = "",
        val teacher: String = "",
        val room: String = "",
    )
}

/**
 * The absences of this school year (show_absences). Times are the time that counts, as on the Absences
 * tab: a whole recorded day is clipped to that day's lessons. [recordedMinutes] is what was entered.
 */
data class AbsenceCard(
    val countedMinutes: Long,
    val recordedMinutes: Long,
    val excusedMinutes: Long,
    val openMinutes: Long,
    val excusedCount: Int,
    val openCount: Int,
    /** Newest first. */
    val entries: List<Entry>,
) : AssistantCard {
    data class Entry(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val countedMinutes: Long,
        val excused: Boolean,
        val reason: String = "",
        val text: String = "",
    )
}

/** Builds [ExamCard]s. Pure, so it is tested. */
object ExamCards {
    const val MAX_EXAMS = 12

    fun build(from: LocalDate, to: LocalDate, exams: List<UntisExam>, lessonsByDate: Map<LocalDate, List<UntisLesson>>): ExamCard {
        val chosen = exams
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sortedWith(compareBy({ it.date }, { it.start }))
            .take(MAX_EXAMS)
        return ExamCard(from, to, chosen.map { exam -> entry(exam, lessonsByDate[exam.date].orEmpty()) })
    }

    /** One exam as a line for the model. */
    fun line(exam: ExamCard.Exam): String = buildString {
        append("${exam.date.format(DAY)} ${exam.start.format(TIME)}-${exam.end.format(TIME)} ${exam.subjectName.ifBlank { exam.subject }.ifBlank { "?" }}")
        if (exam.name.isNotBlank()) append(": ${exam.name}")
        if (exam.teacher.isNotBlank()) append(", teacher ${exam.teacher}")
        if (exam.room.isNotBlank()) append(", room ${exam.room}")
        if (exam.text.isNotBlank()) append(" | text: ${exam.text.replace('\n', ' ')}")
    }

    private fun entry(exam: UntisExam, lessons: List<UntisLesson>): ExamCard.Exam {
        // The lesson the exam is written in gives the teacher, room and long subject name.
        val overlapping = lessons.filter { !it.isHoliday && it.start < exam.end && exam.start < it.end }
        val lesson = overlapping.firstOrNull { exam.subject.isNotBlank() && it.subject.equals(exam.subject, ignoreCase = true) }
            ?: overlapping.firstOrNull { !it.isCancelled }.takeIf { exam.subject.isBlank() }
        val subject = exam.subject.ifBlank { lesson?.subject.orEmpty() }
        return ExamCard.Exam(
            date = exam.date,
            start = exam.start,
            end = exam.end,
            subject = subject,
            subjectName = lesson?.subjectLong?.ifBlank { null } ?: subject,
            name = plainText(exam.name).replace('\n', ' ').take(120),
            text = plainText(exam.text).take(1200),
            teacher = lesson?.teacher.orEmpty(),
            room = lesson?.room.orEmpty(),
        )
    }
}

/** Builds the [AbsenceCard]. Pure, so it is tested. */
object AbsenceCards {
    const val MAX_ENTRIES = 40

    fun build(absences: List<UntisAbsence>, windows: SchoolDayWindows?): AbsenceCard {
        // Paired by position, not looked up: two entries can be equal.
        val counted = absences.sortedByDescending { it.start }.map { it to it.countedDuration(windows).toMinutes() }
        val (excused, open) = counted.partition { it.first.excused }
        return AbsenceCard(
            countedMinutes = counted.sumOf { it.second },
            recordedMinutes = counted.sumOf { it.first.duration.toMinutes() },
            excusedMinutes = excused.sumOf { it.second },
            openMinutes = open.sumOf { it.second },
            excusedCount = excused.size,
            openCount = open.size,
            entries = counted.take(MAX_ENTRIES).map { (absence, minutes) ->
                AbsenceCard.Entry(
                    start = absence.start,
                    end = absence.end,
                    countedMinutes = minutes,
                    excused = absence.excused,
                    reason = absence.reasonName.trim(),
                    text = plainText(absence.text).replace('\n', ' ').take(160),
                )
            },
        )
    }

    /** One entry as a line for the model. */
    fun line(entry: AbsenceCard.Entry): String = buildString {
        append("${entry.start.toLocalDate().format(DAY)} ${entry.start.toLocalTime().format(TIME)}-")
        if (entry.end.toLocalDate() != entry.start.toLocalDate()) append("${entry.end.toLocalDate().format(DAY)} ")
        append(entry.end.toLocalTime().format(TIME))
        append(", counts ${entry.countedMinutes} min")
        append(if (entry.excused) ", excused" else ", not excused")
        if (entry.reason.isNotBlank()) append(", ${entry.reason}")
        if (entry.text.isNotBlank()) append(" (${entry.text})")
    }
}

/** School text without HTML: line breaks kept, tags dropped, the common entities decoded. */
internal fun plainText(raw: String): String = raw
    .replace(Regex("(?i)<\\s*br\\s*/?>|</\\s*(p|div|li)\\s*>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'")
    .lines().joinToString("\n") { it.trim() }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
