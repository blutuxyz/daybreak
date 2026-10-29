package com.bdersolutions.daybreak.data.sync

import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LessonChangeDetectorTest {
    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun detectsCancellationRoomAndTeacherChanges() {
        val oldLesson = lesson(room = "A101", teacher = "MUE")
        val cancelled = oldLesson.copy(status = LessonStatus.CANCELLED)
        val roomChanged = oldLesson.copy(room = "B202")
        val teacherChanged = oldLesson.copy(teacher = "SCH")

        val detections = LessonChangeDetector.detect(
            previous = mapOf(monday to week(oldLesson)),
            current = mapOf(monday to week(cancelled, roomChanged, teacherChanged)),
        )

        assertEquals(3, detections.size)
        assertTrue(detections.any { it.type == LessonChangeType.CANCELLED })
        assertTrue(detections.any { it.type == LessonChangeType.ROOM_CHANGED })
        assertTrue(detections.any { it.type == LessonChangeType.TEACHER_CHANGED })
    }

    @Test
    fun ignoresNewWeeksToAvoidFirstSyncNoise() {
        val newWeek = week(lesson())
        val changes = LessonChangeDetector.detect(
            previous = emptyMap(),
            current = mapOf(monday to newWeek),
        )

        assertTrue(changes.isEmpty())
    }

    @Test
    fun detectsNewLessonInsideKnownWeek() {
        val existing = lesson(id = 1, subject = "MA")
        val added = lesson(id = 2, subject = "EN", start = LocalTime.of(9, 0))
        val changes = LessonChangeDetector.detect(
            previous = mapOf(monday to week(existing)),
            current = mapOf(monday to week(existing, added)),
        )

        assertEquals(1, changes.size)
        assertEquals(LessonChangeType.ADDED, changes.single().type)
        assertEquals("EN", changes.single().lesson.subject)
    }

    private fun week(vararg lessons: UntisLesson): CachedWeek =
        CachedWeek(monday, lessons.toList(), System.currentTimeMillis())

    private fun lesson(
        id: Long = 1,
        subject: String = "MA",
        start: LocalTime = LocalTime.of(8, 0),
        room: String = "A101",
        teacher: String = "MUE",
    ): UntisLesson = UntisLesson(
        id = id,
        date = monday,
        start = start,
        end = start.plusMinutes(45),
        subject = subject,
        teacher = teacher,
        room = room,
    )
}
