package com.bdersolutions.daybreak.data.schedule

import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDateTime
import java.util.Locale

/** One hit of a timetable search: a lesson, and the exam that falls on it if there is one. */
data class SearchHit(
    val lesson: UntisLesson,
    val exam: UntisExam? = null,
    /** True while the lesson has not ended yet. Upcoming hits come first. */
    val upcoming: Boolean,
)

/**
 * Searches the lessons the app has cached ("When is my next Maths lesson?"). Every word of the query
 * has to appear in the subject, its long name, the teacher, the room, the class, an exam's name or a
 * substitution note. Upcoming lessons come first, soonest first; past ones follow, newest first.
 */
object LessonSearch {
    const val MAX_HITS = 60

    fun search(
        lessons: List<UntisLesson>,
        exams: List<UntisExam>,
        query: String,
        now: LocalDateTime,
    ): List<SearchHit> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val hits = lessons
            .asSequence()
            .filter { !it.isHoliday }
            .map { lesson -> lesson to examOn(lesson, exams) }
            .filter { (lesson, exam) ->
                val text = haystack(lesson, exam)
                words.all { text.contains(it) }
            }
            .map { (lesson, exam) -> SearchHit(lesson, exam, upcoming = lesson.endsAt.isAfter(now)) }
            .toList()

        val upcoming = hits.filter { it.upcoming }.sortedBy { it.lesson.startsAt }
        val past = hits.filter { !it.upcoming }.sortedByDescending { it.lesson.startsAt }
        return (upcoming + past).take(MAX_HITS)
    }

    private fun haystack(lesson: UntisLesson, exam: UntisExam?): String =
        listOf(
            lesson.subject, lesson.subjectLong, lesson.teacher, lesson.room, lesson.className,
            lesson.substitutionText, lesson.lessonText, exam?.name.orEmpty(), exam?.text.orEmpty(),
        ).joinToString(" ").lowercase(Locale.ROOT)

    /** An exam belongs to a lesson on its day that overlaps it in time and, if it names one, in subject. */
    private fun examOn(lesson: UntisLesson, exams: List<UntisExam>): UntisExam? =
        exams.firstOrNull { exam ->
            exam.date == lesson.date &&
                exam.start < lesson.end && exam.end > lesson.start &&
                (exam.subject.isBlank() || exam.subject.equals(lesson.subject, ignoreCase = true))
        }
}
