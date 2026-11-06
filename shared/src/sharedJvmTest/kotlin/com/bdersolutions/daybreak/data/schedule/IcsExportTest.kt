package com.bdersolutions.daybreak.data.schedule

import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IcsExportTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val vienna = ZoneId.of("Europe/Vienna")
    private val labels = IcsExport.Labels("Daybreak", "Test", "Lehrkraft", "Klasse", "Vertretung")
    private val now = Instant.parse("2026-11-06T12:00:00Z")

    private fun build(lessons: List<UntisLesson>, exams: List<UntisExam> = emptyList()) =
        IcsExport.build(lessons, exams, labels, monday, monday.plusDays(6), vienna, now)

    private fun lesson(
        id: Long,
        start: LocalTime,
        end: LocalTime,
        status: LessonStatus = LessonStatus.REGULAR,
        room: String = "R1",
    ) = UntisLesson(
        id = id, date = monday, start = start, end = end,
        subject = "MA", subjectLong = "Mathematik", teacher = "Mey", room = room, className = "3A", status = status,
    )

    @Test
    fun aLessonBecomesAnEventInUtcWithRoomAndTeacher() {
        val text = build(listOf(lesson(1, LocalTime.of(7, 55), LocalTime.of(8, 45))))

        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
        // 07:55 in Vienna in November is 06:55 UTC.
        assertTrue(text.contains("DTSTART:20261109T065500Z\r\n"))
        assertTrue(text.contains("DTEND:20261109T074500Z\r\n"))
        assertTrue(text.contains("SUMMARY:Mathematik\r\n"))
        assertTrue(text.contains("LOCATION:R1\r\n"))
        assertTrue(text.contains("DESCRIPTION:Lehrkraft: Mey\\nKlasse: 3A\r\n"))
    }

    @Test
    fun cancelledLessonsAreLeftOut() {
        val text = build(listOf(lesson(1, LocalTime.of(8, 0), LocalTime.of(8, 50), LessonStatus.CANCELLED)))

        assertFalse(text.contains("BEGIN:VEVENT"))
    }

    @Test
    fun aDoubleLessonIsOneEventButADifferentRoomIsNot() {
        val double = build(
            listOf(
                lesson(1, LocalTime.of(8, 0), LocalTime.of(8, 50)),
                lesson(2, LocalTime.of(8, 50), LocalTime.of(9, 40)),
            ),
        )
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(double).count())
        assertTrue(double.contains("DTEND:20261109T084000Z"))

        val moved = build(
            listOf(
                lesson(1, LocalTime.of(8, 0), LocalTime.of(8, 50)),
                lesson(2, LocalTime.of(8, 50), LocalTime.of(9, 40), room = "R2"),
            ),
        )
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(moved).count())
    }

    @Test
    fun onlyTheRequestedRangeIsExported() {
        val outside = lesson(1, LocalTime.of(8, 0), LocalTime.of(8, 50)).copy(date = monday.plusDays(10))

        assertFalse(build(listOf(outside)).contains("BEGIN:VEVENT"))
    }

    @Test
    fun examsAreExportedWithAPrefix() {
        val exam = UntisExam(7, monday, LocalTime.of(9, 0), LocalTime.of(10, 0), "MA", name = "Schularbeit")

        val text = build(emptyList(), listOf(exam))

        assertTrue(text.contains("SUMMARY:Test: MA Schularbeit\r\n"))
    }

    @Test
    fun specialCharactersAreEscapedAndLongLinesFoldedBySizeInBytes() {
        assertEquals("a\\,b\\;c\\\\d\\ne", IcsExport.escape("a,b;c\\d\ne"))

        val long = "SUMMARY:" + "ä".repeat(60)
        val folded = IcsExport.fold(long)
        val lines = folded.split("\r\n")
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertTrue(lines.drop(1).all { it.startsWith(" ") })
        assertEquals(long, lines.joinToString("") { it.removePrefix(" ") })
    }
}
