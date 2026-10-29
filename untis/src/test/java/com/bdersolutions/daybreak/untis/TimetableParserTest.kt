package com.bdersolutions.daybreak.untis

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimetableParserTest {
    @Test
    fun parsesMobilePeriodsAndChanges() {
        val json = """
            {
              "masterData": {
                "timeStamp": 123456,
                "subjects": [{"id": 1, "name": "MA", "longName": "Mathematik"}],
                "teachers": [{"id": 10, "name": "MUE"}, {"id": 11, "name": "SCH"}],
                "rooms": [{"id": 20, "name": "A101"}, {"id": 21, "name": "B202"}],
                "klassen": [{"id": 30, "name": "1A"}]
              },
              "timetable": {
                "periods": [{
                  "id": 100,
                  "startDateTime": "2026-10-29T08:00",
                  "endDateTime": "2026-10-29T08:45",
                  "elements": [
                    {"id": 1, "type": "SUBJECT"},
                    {"id": 11, "type": "TEACHER", "orgId": 10},
                    {"id": 21, "type": "ROOM", "orgId": 20},
                    {"id": 30, "type": "CLASS"}
                  ],
                  "is": ["CANCELLED"],
                  "text": {"lesson": "Algebra", "substitution": "Entfall"}
                }]
              }
            }
        """.trimIndent()

        val timetable = TimetableParser.parse(JsonParser.parseString(json))
        assertEquals(123456L, timetable.masterDataTimestamp)
        assertEquals(1, timetable.lessons.size)

        val lesson = timetable.lessons.single()
        assertEquals(LocalDate.of(2026, 10, 29), lesson.date)
        assertEquals(LocalTime.of(8, 0), lesson.start)
        assertEquals(LocalTime.of(8, 45), lesson.end)
        assertEquals("MA", lesson.subject)
        assertEquals("Mathematik", lesson.subjectLong)
        assertEquals("SCH", lesson.teacher)
        assertEquals("B202", lesson.room)
        assertEquals("1A", lesson.className)
        assertEquals("A101", lesson.originalRoom)
        assertEquals("MUE", lesson.originalTeacher)
        assertTrue(lesson.hasRoomChange)
        assertTrue(lesson.hasTeacherChange)
        assertEquals(LessonStatus.CANCELLED, lesson.status)
        assertEquals("Entfall", lesson.substitutionText)
    }

    @Test
    fun acceptsLegacyShapesAndSkipsBrokenPeriods() {
        val json = """
            {
              "periods": [
                {
                  "date": 20261030,
                  "startTime": 800,
                  "endTime": "0900",
                  "su": [{"id": 3, "name": "EN"}],
                  "te": [{"id": 7, "name": "DOE"}],
                  "ro": [{"id": 8, "name": "C3"}],
                  "kl": [{"id": 2, "name": "2B"}],
                  "is": "IRREGULAR",
                  "substText": "Vertretung"
                },
                {
                  "startDateTime": "not-a-date",
                  "endDateTime": "also-not-a-date"
                }
              ]
            }
        """.trimIndent()

        val timetable = TimetableParser.parse(JsonParser.parseString(json))
        assertEquals(1, timetable.lessons.size)
        val lesson = timetable.lessons.single()
        assertEquals(LocalDate.of(2026, 10, 30), lesson.date)
        assertEquals(LocalTime.of(8, 0), lesson.start)
        assertEquals(LocalTime.of(9, 0), lesson.end)
        assertEquals("EN", lesson.subject)
        assertEquals("DOE", lesson.teacher)
        assertEquals("C3", lesson.room)
        assertEquals("2B", lesson.className)
        assertEquals(LessonStatus.IRREGULAR, lesson.status)
        assertEquals("Vertretung", lesson.substitutionText)
    }
}
