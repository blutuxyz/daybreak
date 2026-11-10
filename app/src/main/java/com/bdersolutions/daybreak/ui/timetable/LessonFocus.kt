package com.bdersolutions.daybreak.ui.timetable

import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalTime

/**
 * A request to show one lesson in the timetable, from outside it (an exam or a lesson on one of the
 * assistant's cards): the timetable goes to [date] and opens the details of the lesson that runs at that
 * time, preferring the one of [subject]. [id] makes the same lesson asked for twice a new request.
 */
data class LessonFocus(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String = "",
    val id: Long = System.nanoTime(),
) {
    /** The lesson of that day this points at: same time and subject, else held at that time, else any at that time. */
    fun match(lessons: List<UntisLesson>): UntisLesson? {
        val running = lessons.filter { it.date == date && !it.isHoliday && it.start < end && start < it.end }
        return running.firstOrNull { subject.isNotBlank() && it.subject.equals(subject, ignoreCase = true) && !it.isCancelled }
            ?: running.firstOrNull { subject.isNotBlank() && it.subject.equals(subject, ignoreCase = true) }
            ?: running.firstOrNull { !it.isCancelled }
            ?: running.firstOrNull()
    }
}
