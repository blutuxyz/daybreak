package com.bdersolutions.daybreak.untis

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ClassServiceParserTest {

    @Test
    fun readsEntriesWithNestedObjects() {
        val json = JsonParser.parseString(
            """{"data":{"classServices":[{"id":3,"classServiceType":{"name":"Klassenordner"},""" +
                """"student":{"displayName":"Johannes Prantl"},"klasse":{"name":"3AHMBA"},""" +
                """"startDate":"2026-11-02","endDate":"2026-11-08","text":"Woche 45"}]}}""",
        )

        val service = ClassServiceParser.parse(json).single()

        assertEquals(3L, service.id)
        assertEquals("Klassenordner", service.service)
        assertEquals("Johannes Prantl", service.student)
        assertEquals("3AHMBA", service.className)
        assertEquals(LocalDate.of(2026, 11, 2), service.start)
        assertEquals(LocalDate.of(2026, 11, 8), service.end)
        assertEquals("Woche 45", service.text)
    }

    @Test
    fun resolvesIdsThroughLookupLists() {
        val json = JsonParser.parseString(
            """{"data":{"classServices":[{"id":1,"classServiceId":7,"studentId":9,"klasseId":2,""" +
                """"startDate":20261102,"endDate":20261227}],""" +
                """"classServiceTypes":[{"id":7,"name":"Klassensprecher"}],""" +
                """"students":[{"id":9,"foreName":"Stefan","longName":"Moser"}],""" +
                """"klassen":[{"id":2,"name":"3AHMBA"}]}}""",
        )

        val service = ClassServiceParser.parse(json).single()

        assertEquals("Klassensprecher", service.service)
        assertEquals("Stefan Moser", service.student)
        assertEquals("3AHMBA", service.className)
        assertEquals(LocalDate.of(2026, 12, 27), service.end)
    }

    @Test
    fun skipsUnreadableEntriesAndHandlesEmptyOrUnknownShapes() {
        val json = JsonParser.parseString("""{"data":{"classServices":[{"id":1},{"serviceName":"Tafeldienst","studentName":"Ben"}]}}""")

        assertEquals(listOf("Tafeldienst"), ClassServiceParser.parse(json).map { it.service })
        assertTrue(ClassServiceParser.parse(JsonParser.parseString("""{"loginError":"","state":"LOGIN_ERROR"}""")).isEmpty())
        assertTrue(ClassServiceParser.parse(JsonParser.parseString("""{"data":{"classServices":[]}}""")).isEmpty())
    }
}
