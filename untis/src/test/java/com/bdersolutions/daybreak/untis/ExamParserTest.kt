package com.bdersolutions.daybreak.untis

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExamParserTest {

    @Test
    fun readsExamsFromAnObjectWithAnExamsArray() {
        val json = JsonParser.parseString(
            """{"exams":[{"id":7,"examDate":20261110,"startTime":800,"endTime":845,""" +
                """"subject":"M","name":"Schularbeit","text":"Kapitel 3"}]}""",
        )

        val exam = ExamParser.parse(json).single()

        assertEquals(7L, exam.id)
        assertEquals(LocalDate.of(2026, 11, 10), exam.date)
        assertEquals(LocalTime.of(8, 0), exam.start)
        assertEquals(LocalTime.of(8, 45), exam.end)
        assertEquals("M", exam.subject)
        assertEquals("Schularbeit", exam.name)
        assertEquals("Kapitel 3", exam.text)
    }

    @Test
    fun readsTheShapeReturnedByTheLiveServer() {
        val json = JsonParser.parseString(
            """{"type":"STUDENT","id":2603,"exams":[{"id":16219,"examType":"Test",""" +
                """"startDateTime":"2026-11-06T07:55Z","endDateTime":"2026-11-06T08:45Z",""" +
                """"subjectId":8,"name":" ETE Test 3AHMBA","text":" ETE Test 3AHMBA",""" +
                """"invigilators":[{"id":24,"startTime":"T07:55","endTime":"T08:45"}]}]}""",
        )

        val exam = ExamParser.parse(json).single()

        assertEquals(LocalDate.of(2026, 11, 6), exam.date)
        assertEquals(LocalTime.of(7, 55), exam.start)
        assertEquals(LocalTime.of(8, 45), exam.end)
        assertEquals("", exam.subject)
        assertEquals("ETE Test 3AHMBA", exam.name)
    }

    @Test
    fun fallsBackToTheExamTypeWhenThereIsNoName() {
        val json = JsonParser.parseString(
            """[{"examType":"Diktat","startDateTime":"2026-11-10T11:35Z","endDateTime":"2026-11-10T12:20Z"}]""",
        )

        assertEquals("Diktat", ExamParser.parse(json).single().name)
    }

    @Test
    fun skipsUnreadableEntriesAndDefaultsTheEndTime() {
        val json = JsonParser.parseString(
            """[{"subject":"M"},{"examDate":20261110,"startTime":900,"subject":"E"}]""",
        )

        val exams = ExamParser.parse(json)

        assertEquals(1, exams.size)
        assertEquals(LocalTime.of(9, 45), exams.single().end)
        assertEquals("E", exams.single().subject)
    }

    @Test
    fun anUnknownShapeGivesAnEmptyList() {
        assertTrue(ExamParser.parse(JsonParser.parseString("""{"something":1}""")).isEmpty())
    }
}
