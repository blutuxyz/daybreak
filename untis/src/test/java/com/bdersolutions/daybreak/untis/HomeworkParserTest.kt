package com.bdersolutions.daybreak.untis

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeworkParserTest {

    @Test
    fun readsHomeworkAndTakesTheSubjectFromTheLesson() {
        val json = JsonParser.parseString(
            """{"homeWorks":[{"id":5,"lessonId":42,"date":20261102,"dueDate":20261106,""" +
                """"text":"Seite 12","remark":"Übung 3","completed":true}],""" +
                """"lessonsById":{"42":{"id":42,"subject":"ETE"}}}""",
        )

        val homework = HomeworkParser.parse(json).single()

        assertEquals(5L, homework.id)
        assertEquals("ETE", homework.subject)
        assertEquals(LocalDate.of(2026, 11, 6), homework.due)
        assertEquals(LocalDate.of(2026, 11, 2), homework.assigned)
        assertEquals("Seite 12", homework.text)
        assertEquals("Übung 3", homework.remark)
        assertTrue(homework.completed)
    }

    @Test
    fun skipsEntriesWithoutADueDateOrText() {
        val json = JsonParser.parseString(
            """{"homeWorks":[{"id":1,"text":"no date"},{"id":2,"dueDate":20261106},""" +
                """{"id":3,"dueDate":20261106,"text":"ok"}],"lessonsById":{}}""",
        )

        assertEquals(listOf(3L), HomeworkParser.parse(json).map { it.id })
    }

    @Test
    fun emptyResponsesGiveEmptyLists() {
        assertTrue(HomeworkParser.parse(JsonParser.parseString("""{"homeWorks":[],"lessonsById":{}}""")).isEmpty())
        assertTrue(MessageParser.parse(JsonParser.parseString("""{"messages":[]}""")).isEmpty())
    }

    @Test
    fun readsMessages() {
        val json = JsonParser.parseString(
            """{"messages":[{"id":9,"subject":"Schulfrei","text":"Am Freitag entfällt der Unterricht."},""" +
                """{"id":10}]}""",
        )

        val message = MessageParser.parse(json).single()

        assertEquals(9L, message.id)
        assertEquals("Schulfrei", message.subject)
        assertEquals("Am Freitag entfällt der Unterricht.", message.text)
    }
}
