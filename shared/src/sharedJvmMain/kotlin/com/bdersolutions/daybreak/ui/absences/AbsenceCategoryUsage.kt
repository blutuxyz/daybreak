package com.bdersolutions.daybreak.ui.absences

import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.Duration

/**
 * A user-defined absence limit for one subject, e.g. "WEPT: at most 72 lessons".
 * Only primitives, so Gson can store it without adapters.
 */
data class AbsenceCategory(
    val id: String,
    val name: String,
    val subject: String,
    val maxLessons: Int,
    /**
     * When above 0 the limit is this share of the subject's lessons held so far, and [maxLessons]
     * is unused. Gson leaves it 0 for categories stored before percent limits existed.
     */
    val maxPercent: Int = 0,
) {
    val isPercent: Boolean get() = maxPercent > 0
}

/** How many lessons of a subject were missed, split by excuse status. */
data class CategoryUsage(
    val excused: Int = 0,
    val unexcused: Int = 0,
    /** Lessons of the subject held so far this school year (cancelled ones excluded). */
    val held: Int = 0,
) {
    val total: Int get() = excused + unexcused

    /** Missed share of the held lessons, 0 to 100. */
    val percent: Double get() = percentOf(total, held)
}

fun percentOf(missed: Int, held: Int): Double =
    if (held <= 0) 0.0 else missed * 100.0 / held

/**
 * The categories in the order of [ids]. Ids that no category has are ignored, and categories that
 * [ids] leaves out keep their relative order at the end, so a stale order never loses a category.
 */
fun List<AbsenceCategory>.reorderedBy(ids: List<String>): List<AbsenceCategory> {
    val byId = associateBy { it.id }
    val wanted = ids.distinct().mapNotNull(byId::get)
    val taken = wanted.map { it.id }.toSet()
    return wanted + filter { it.id !in taken }
}

/** A lesson subject can hold several comma-joined subjects ("Mat, Eng"). */
fun lessonSubjects(subject: String): List<String> =
    subject.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** The sorted, distinct subjects that appear in [lessons]. Cancelled lessons still count as "has". */
fun studentSubjects(lessons: Collection<UntisLesson>): List<String> =
    lessons.flatMap { lessonSubjects(it.subject) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

fun UntisLesson.hasSubject(subject: String): Boolean =
    lessonSubjects(this.subject).any { it.equals(subject.trim(), ignoreCase = true) }

/**
 * Counts the held lessons of [subject] that overlap an absence: one lesson is one missed lesson
 * ("Fehlstunde"). WebUntis absences carry no subject, so the lessons are the only link.
 *
 * - Cancelled lessons are skipped, because nothing was missed.
 * - A lesson counts once however many absences overlap it. It is excused when any overlapping
 *   absence is excused.
 * - Touching intervals do not overlap: an absence ending at 13:15 misses no lesson starting 13:15.
 */
fun countMissedLessons(
    absences: List<UntisAbsence>,
    lessons: Collection<UntisLesson>,
    subject: String,
): CategoryUsage {
    if (subject.isBlank()) return CategoryUsage()
    var excused = 0
    var unexcused = 0
    var held = 0
    val seen = HashSet<Triple<Long, java.time.LocalDate, java.time.LocalTime>>()
    for (lesson in lessons) {
        if (lesson.isCancelled || !lesson.hasSubject(subject)) continue
        if (!seen.add(Triple(lesson.id, lesson.date, lesson.start))) continue
        held++
        val begin = lesson.startsAt
        val finish = lesson.endsAt
        val overlapping = absences.filter { it.start < finish && it.end > begin }
        if (overlapping.isEmpty()) continue
        if (overlapping.any { it.excused }) excused++ else unexcused++
    }
    return CategoryUsage(excused = excused, unexcused = unexcused, held = held)
}

/**
 * How many held lessons of [subject] each absence covers, by absence id. Absences that cover none
 * are left out, so the keys are exactly the absences that belong to the subject. This feeds the
 * list under a category card.
 */
fun missedLessonsByAbsence(
    absences: List<UntisAbsence>,
    lessons: Collection<UntisLesson>,
    subject: String,
): Map<Long, Int> {
    if (absences.isEmpty() || subject.isBlank()) return emptyMap()
    val counts = HashMap<Long, Int>()
    val seen = HashSet<Triple<Long, java.time.LocalDate, java.time.LocalTime>>()
    for (lesson in lessons) {
        if (lesson.isCancelled || !lesson.hasSubject(subject)) continue
        if (!seen.add(Triple(lesson.id, lesson.date, lesson.start))) continue
        val begin = lesson.startsAt
        val finish = lesson.endsAt
        for (absence in absences) {
            if (absence.start < finish && absence.end > begin) {
                counts[absence.id] = (counts[absence.id] ?: 0) + 1
            }
        }
    }
    return counts
}

/** Sums of an absence list, shared by the Total card and the filter chips. */
data class AbsenceTotals(
    val total: Duration,
    val excused: Duration,
) {
    val unexcused: Duration get() = total.minus(excused)
}

/** [windows] clips each absence to the school days' first and last lesson, see [countedDuration]. */
fun absenceTotals(absences: List<UntisAbsence>, windows: SchoolDayWindows? = null): AbsenceTotals {
    var total = Duration.ZERO
    var excused = Duration.ZERO
    for (absence in absences) {
        val counted = absence.countedDuration(windows)
        total = total.plus(counted)
        if (absence.excused) excused = excused.plus(counted)
    }
    return AbsenceTotals(total, excused)
}

/** The length of one school lesson in minutes, which is how the school counts missed time. */
const val SCHOOL_LESSON_MINUTES = 50

/**
 * Missed [minutes] as school lessons of [SCHOOL_LESSON_MINUTES] minutes, with at most one decimal and
 * the separator of [locale]: 105 minutes are "2,1" in German, 100 minutes are "2".
 */
fun formatSchoolLessons(minutes: Long, locale: java.util.Locale): String {
    val format = java.text.NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return format.format(minutes.toDouble() / SCHOOL_LESSON_MINUTES)
}
