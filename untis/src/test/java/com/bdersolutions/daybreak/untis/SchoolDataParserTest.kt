package com.bdersolutions.daybreak.untis

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SchoolDataParserTest {
    @Test
    fun parsesAbsencesWithCombinedDateTimesAndReasonObject() {
        val json = """
            {"absences":[{
              "id": 7,
              "startDateTime": "2026-11-03T08:00:00",
              "endDateTime": "2026-11-03T09:30:00",
              "excused": false,
              "reason": {"id": 51, "name": "krank"},
              "text": ""
            }]}
        """.trimIndent()

        val absences = SchoolDataParser.absences(JsonParser.parseString(json))

        assertEquals(1, absences.size)
        val absence = absences.single()
        assertEquals(7L, absence.id)
        assertEquals(LocalDateTime.of(2026, 11, 3, 8, 0), absence.start)
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 30), absence.end)
        assertFalse(absence.excused)
        assertEquals(51L, absence.reasonId)
        assertEquals("krank", absence.reasonName)
    }

    @Test
    fun parsesSeparateDateAndTimeFieldsAndNumericReason() {
        val json = """
            {"absences":[{
              "id": 8,
              "startDate": 20261104,
              "endDate": 20261104,
              "startTime": 1015,
              "endTime": 1145,
              "isExcused": true,
              "reason": 52,
              "reasonText": "Arzt"
            }]}
        """.trimIndent()

        val absence = SchoolDataParser.absences(JsonParser.parseString(json)).single()

        assertEquals(LocalDateTime.of(2026, 11, 4, 10, 15), absence.start)
        assertEquals(LocalDateTime.of(2026, 11, 4, 11, 45), absence.end)
        assertTrue(absence.excused)
        assertEquals(52L, absence.reasonId)
        assertEquals("", absence.reasonName)
        assertEquals("Arzt", absence.text)
    }

    @Test
    fun skipsEntriesWithoutAStartDateAndReturnsEmptyListForNoAbsences() {
        val json = """{"absences":[{"id": 9, "text": "no date"}]}"""
        assertTrue(SchoolDataParser.absences(JsonParser.parseString(json)).isEmpty())
        assertTrue(SchoolDataParser.absences(JsonParser.parseString("""{"absences":[]}""")).isEmpty())
    }

    @Test
    fun readsDirectoryAndAbsenceReasonsFromMasterData() {
        val json = """
            {"timetable":{"periods":[]},"masterData":{
              "klassen":[{"id":3,"name":"10a","longName":"Klasse 10a"}],
              "teachers":[{"id":5,"name":"MUE","longName":"Müller"}],
              "rooms":[{"id":9,"name":"204"}],
              "absenceReasons":[{"id":51,"name":"krank","longName":"Krankheit"}]
            }}
        """.trimIndent()

        val directory = SchoolDataParser.directory(JsonParser.parseString(json))

        assertEquals("Klasse 10a", directory.classes.single().displayName)
        assertEquals(ElementType.CLASS, directory.classes.single().type)
        assertEquals("Müller", directory.teachers.single().displayName)
        assertEquals("204", directory.rooms.single().displayName)
        assertEquals("Krankheit", directory.absenceReasons[51L])
        assertNull(directory.absenceReasons[1L])
    }

    @Test
    fun readsHolidaysAndFlagsDateNames() {
        val json = """
            {"masterData":{"holidays":[
              {"id":629,"name":"Weihnachtsferien","longName":"Weihnachtsferien","startDate":"2026-12-24","endDate":"2027-01-06"},
              {"id":96,"name":"10.5.","longName":"10.5.2018","startDate":"2018-05-10","endDate":"2018-05-10"},
              {"id":1,"name":"kaputt"}
            ]}}
        """.trimIndent()

        val holidays = SchoolDataParser.directory(JsonParser.parseString(json)).holidays

        assertEquals(2, holidays.size)
        assertEquals(java.time.LocalDate.of(2018, 5, 10), holidays[0].start)
        assertTrue(holidays[0].hasDateName)
        assertEquals("Weihnachtsferien", holidays[1].displayName)
        assertFalse(holidays[1].hasDateName)
        assertEquals(java.time.LocalDate.of(2027, 1, 6), holidays[1].end)
    }

}
