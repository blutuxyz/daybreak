package com.bdersolutions.daybreak.data.sync

import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate

enum class LessonChangeType {
    CANCELLED,
    ROOM_CHANGED,
    TEACHER_CHANGED,
    SUBSTITUTION,
    ADDED,
}

data class LessonChange(
    val type: LessonChangeType,
    val lesson: UntisLesson,
    val previousRoom: String = "",
    val previousTeacher: String = "",
)

/**
 * Compares only weeks that already existed in the cache. A freshly downloaded week
 * must not trigger a burst of "new lesson" notifications on first login.
 */
object LessonChangeDetector {
    fun detect(
        previous: Map<LocalDate, CachedWeek>,
        current: Map<LocalDate, CachedWeek>,
    ): List<LessonChange> {
        val changes = mutableListOf<LessonChange>()
        val commonWeeks = previous.keys.intersect(current.keys)
        for (weekStart in commonWeeks) {
            val oldLessons = previous[weekStart]?.lessons.orEmpty()
            val newLessons = current[weekStart]?.lessons.orEmpty()
            val oldByKey = oldLessons.associateBy { key(it) }

            for (lesson in newLessons) {
                val old = oldByKey[key(lesson)] ?: run {
                    changes += LessonChange(LessonChangeType.ADDED, lesson)
                    continue
                }

                when {
                    old.status != LessonStatus.CANCELLED && lesson.status == LessonStatus.CANCELLED -> {
                        changes += LessonChange(
                            LessonChangeType.CANCELLED,
                            lesson,
                            previousRoom = old.room,
                            previousTeacher = old.teacher,
                        )
                    }

                    old.room != lesson.room -> {
                        changes += LessonChange(
                            LessonChangeType.ROOM_CHANGED,
                            lesson,
                            previousRoom = old.room,
                            previousTeacher = old.teacher,
                        )
                    }

                    old.teacher != lesson.teacher -> {
                        changes += LessonChange(
                            LessonChangeType.TEACHER_CHANGED,
                            lesson,
                            previousRoom = old.room,
                            previousTeacher = old.teacher,
                        )
                    }

                    old.start != lesson.start ||
                        old.end != lesson.end ||
                        old.subject != lesson.subject ||
                        old.status != lesson.status ||
                        old.substitutionText != lesson.substitutionText -> {
                        changes += LessonChange(
                            LessonChangeType.SUBSTITUTION,
                            lesson,
                            previousRoom = old.room,
                            previousTeacher = old.teacher,
                        )
                    }
                }
            }
        }

        return changes.sortedWith(compareBy<LessonChange> { it.lesson.date }.thenBy { it.lesson.start })
    }

    private fun key(lesson: UntisLesson): String {
        if (lesson.id != 0L) return "${lesson.id}|${lesson.date}"
        return "${lesson.date}|${lesson.start}|${lesson.subject}"
    }
}
