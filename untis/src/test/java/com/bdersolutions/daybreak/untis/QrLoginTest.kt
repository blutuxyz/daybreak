package com.bdersolutions.daybreak.untis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrLoginTest {
    @Test
    fun parsesNativeQrPayload() {
        val raw = "untis://setschool?url=https%3A%2F%2Fschool.webuntis.com&school=schule&user=max.mustermann&key=ABCDEFGHIJKLMNOP&schoolNumber=42"

        val result = UntisQrParser.parse(raw)

        assertEquals("https://school.webuntis.com", result.server)
        assertEquals("schule", result.school)
        assertEquals("max.mustermann", result.username)
        assertEquals("ABCDEFGHIJKLMNOP", result.secret)
    }

    @Test
    fun acceptsBareHostInUrl() {
        val result = UntisQrParser.parse(
            "untis://setschool?url=school.webuntis.com&school=schule&user=user&key=ABCD",
        )

        assertEquals("https://school.webuntis.com", result.server)
    }

    @Test
    fun rejectsNonUntisPayload() {
        assertNull(UntisQrParser.parseOrNull("https://example.com"))
        assertNull(UntisQrParser.parseOrNull("untis://setschool?school=schule"))
    }

    @Test
    fun normalizesManualServerInput() {
        assertEquals("https://school.webuntis.com", UntisEndpoints.normalizeServer("https://school.webuntis.com/WebUntis"))
        assertEquals("https://school.webuntis.com:8443", UntisEndpoints.normalizeServer("school.webuntis.com:8443/WebUntis"))
    }
}
