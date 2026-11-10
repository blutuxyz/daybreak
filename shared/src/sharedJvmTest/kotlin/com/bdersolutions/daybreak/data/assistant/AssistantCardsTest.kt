package com.bdersolutions.daybreak.data.assistant

import com.bdersolutions.daybreak.ui.absences.SchoolDayWindows
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCardsTest {
    private val tuesday = LocalDate.of(2026, 11, 10)

    private fun lesson(date: LocalDate, start: String, end: String, subject: String, long: String = "", teacher: String = "", room: String = "", status: LessonStatus = LessonStatus.REGULAR) =
        UntisLesson(1, date, LocalTime.parse(start), LocalTime.parse(end), subject, subjectLong = long, teacher = teacher, room = room, status = status)

    @Test
    fun examsGetTheirLessonsTeacherAndRoomAndPlainText() {
        val exams = listOf(
            UntisExam(2, tuesday.plusDays(3), LocalTime.of(10, 40), LocalTime.of(12, 10), "MANL", name = "Schularbeit"),
            UntisExam(1, tuesday, LocalTime.of(11, 35), LocalTime.of(12, 25), "E1", name = "Test", text = "smü<br>future forms &amp; tenses"),
            UntisExam(3, tuesday.plusDays(90), LocalTime.of(8, 0), LocalTime.of(9, 0), "NW2"),
        )
        val lessons = mapOf(tuesday to listOf(lesson(tuesday, "11:35", "12:25", "E1", long = "Englisch", teacher = "KRAB", room = "R12")))
        val card = ExamCards.build(tuesday, tuesday.plusDays(60), exams, lessons)

        assertEquals("oldest first, only the range", listOf("E1", "MANL"), card.exams.map { it.subject })
        val english = card.exams[0]
        assertEquals("Englisch", english.subjectName)
        assertEquals("KRAB", english.teacher)
        assertEquals("R12", english.room)
        assertEquals("smü\nfuture forms & tenses", english.text)
        assertEquals("no lesson known: the short name and nothing made up", "MANL", card.exams[1].subjectName)
        assertEquals("", card.exams[1].teacher)
    }

    @Test
    fun absencesCountOnlyTheLessonsOfAWholeRecordedDay() {
        val wednesday = LocalDate.of(2026, 9, 23)
        val absences = listOf(
            // Two whole days entered as 07:55 to 17:30, excused.
            UntisAbsence(1, LocalDateTime.of(wednesday, LocalTime.of(7, 55)), LocalDateTime.of(wednesday.plusDays(1), LocalTime.of(17, 30)), true, null, "Krank", "Anruf Mutter"),
            UntisAbsence(2, LocalDateTime.of(2026, 10, 19, 11, 35), LocalDateTime.of(2026, 10, 19, 13, 15), false, null, "", ""),
        )
        val windows = SchoolDayWindows.from(
            listOf(
                lesson(wednesday, "07:55", "13:15", "M"),
                lesson(wednesday.plusDays(1), "07:55", "11:30", "D"),
                lesson(LocalDate.of(2026, 10, 19), "07:55", "16:00", "E"),
            ),
        )
        val card = AbsenceCards.build(absences, windows)

        assertEquals("newest first", 100L, card.entries[0].countedMinutes)
        assertEquals("320 + 215 minutes of lessons, not two whole recorded days", 535L, card.entries[1].countedMinutes)
        assertEquals(635L, card.countedMinutes)
        assertEquals(535L, card.excusedMinutes)
        assertEquals(100L, card.openMinutes)
        assertEquals(1, card.openCount)
        assertTrue(card.recordedMinutes > card.countedMinutes)
        assertEquals("Anruf Mutter", card.entries[1].text)
    }

    @Test
    fun withoutLessonsTheRecordedTimeCounts() {
        val absence = UntisAbsence(1, LocalDateTime.of(2026, 10, 19, 11, 35), LocalDateTime.of(2026, 10, 19, 13, 15), false, null, "", "")
        assertEquals(100L, AbsenceCards.build(listOf(absence), null).countedMinutes)
        assertNull(AbsenceCards.build(emptyList(), null).entries.firstOrNull())
    }
}
