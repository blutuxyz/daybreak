package com.bdersolutions.daybreak.data.notices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServerNoticesTest {
    @Test
    fun parsesTheMinimumVersionAndTheMessages() {
        val status = ServerStatusJson.parse(
            """
            {"minVersionName":"1.2.0","minVersionCode":10200,"messages":[
              {"id":"a1","level":"critical","de":"WebUntis ist gerade nicht erreichbar.","en":"","until":"2026-11-07T17:00:00.000Z"},
              {"id":"b2","level":"nonsense","de":"","en":"Hello"}
            ]}
            """.trimIndent(),
        )!!

        assertEquals("1.2.0", status.minVersionName)
        assertEquals(10200L, status.minVersionCode)
        assertEquals(2, status.messages.size)
        assertEquals(NoticeLevel.CRITICAL, status.messages[0].level)
        assertEquals(Instant.parse("2026-11-07T17:00:00Z").toEpochMilli(), status.messages[0].untilMillis)
        assertEquals(NoticeLevel.INFO, status.messages[1].level)
        assertNull(status.messages[1].untilMillis)
    }

    @Test
    fun noMinimumMeansNoRequiredUpdate() {
        val status = ServerStatusJson.parse("""{"minVersionName":null,"minVersionCode":null,"messages":[]}""")!!

        assertNull(status.minVersionName)
        assertNull(status.minVersionCode)
        assertFalse(UpdateGate.isRequired(installedVersionCode = 10100, requiredVersionCode = status.minVersionCode))
    }

    @Test
    fun aMissingVersionNameIsWorkedOutFromTheCode() {
        val status = ServerStatusJson.parse("""{"minVersionCode":10203,"messages":[]}""")!!

        assertEquals("1.2.3", status.minVersionName)
        assertEquals("2.0.1", ServerStatusJson.versionNameOf(20001))
    }

    @Test
    fun unusableAnswersGiveNothing() {
        assertNull(ServerStatusJson.parse(null))
        assertNull(ServerStatusJson.parse("   "))
        assertNull(ServerStatusJson.parse("[]"))
        assertNull(ServerStatusJson.parse("not json at all"))
    }

    @Test
    fun aMessageWithoutIdOrText_isSkipped() {
        val status = ServerStatusJson.parse(
            """{"messages":[{"level":"info","de":"no id"},{"id":"x","level":"info"},{"id":"ok","de":"yes"}]}""",
        )!!

        assertEquals(listOf("ok"), status.messages.map { it.id })
    }

    @Test
    fun theGateOnlyStopsAppsOlderThanTheMinimum() {
        assertTrue(UpdateGate.isRequired(installedVersionCode = 10199, requiredVersionCode = 10200))
        assertFalse(UpdateGate.isRequired(installedVersionCode = 10200, requiredVersionCode = 10200))
        assertFalse(UpdateGate.isRequired(installedVersionCode = 10300, requiredVersionCode = 10200))
        assertFalse(UpdateGate.isRequired(installedVersionCode = 1, requiredVersionCode = null))
    }

    @Test
    fun pendingMessagesSkipDismissedAndEndedOnesAndPutTheSeriousFirst() {
        val now = Instant.parse("2026-11-06T12:00:00Z").toEpochMilli()
        val info = ServerMessage("info", NoticeLevel.INFO, "a", "", null)
        val critical = ServerMessage("critical", NoticeLevel.CRITICAL, "b", "", null)
        val dismissed = ServerMessage("dismissed", NoticeLevel.CRITICAL, "c", "", null)
        val ended = ServerMessage("ended", NoticeLevel.CRITICAL, "d", "", now - 1)
        val running = ServerMessage("running", NoticeLevel.WARNING, "e", "", now + 60_000)

        val pending = MessageFilter.pending(
            messages = listOf(info, critical, dismissed, ended, running),
            dismissed = setOf("dismissed"),
            now = now,
        )

        assertEquals(listOf("critical", "running", "info"), pending.map { it.id })
    }

    @Test
    fun theTextFallsBackToTheOtherLanguage() {
        val german = ServerMessage("m", NoticeLevel.INFO, de = "", en = "English only", untilMillis = null)
        val both = ServerMessage("n", NoticeLevel.INFO, de = "Deutsch", en = "English", untilMillis = null)

        assertEquals("English only", german.textFor(german = true))
        assertEquals("English only", german.textFor(german = false))
        assertEquals("Deutsch", both.textFor(german = true))
        assertEquals("English", both.textFor(german = false))
    }
}
