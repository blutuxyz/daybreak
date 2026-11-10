package com.bdersolutions.daybreak.untis

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodDataParserTest {
    @Test
    fun readsTheTopicOfEachPeriod() {
        val json = JsonParser.parseString(
            """
            {"referencedStudents": [],
             "dataByTTId": {
               "101": {"ttId": 101, "topic": {"id": 5, "text": " Ohmsches Gesetz, S. 84-90 "}, "homeWorks": []},
               "102": {"ttId": 102, "topic": null},
               "103": {"topic": "Gedichtanalyse"},
               "104": {"ttId": 104, "topic": {"text": ""}}
             }}
            """,
        )
        assertEquals(mapOf(101L to "Ohmsches Gesetz, S. 84-90", 103L to "Gedichtanalyse"), PeriodDataParser.topics(json))
    }

    @Test
    fun unknownShapesGiveNothing() {
        assertTrue(PeriodDataParser.topics(JsonParser.parseString("[]")).isEmpty())
        assertTrue(PeriodDataParser.topics(JsonParser.parseString("""{"other": 1}""")).isEmpty())
    }
}
