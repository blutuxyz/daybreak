package com.bdersolutions.daybreak.data.notices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeSheetTest {
    private val playStore = """
        {"messages":[{"id":"play1","level":"info","de":"","en":"","until":null,"sheet":{
          "titleDe":"Daybreak gibt es jetzt im Play Store","titleEn":"Daybreak is now available on the Play Store",
          "image":{"kind":"preset","value":"playstore","shape":"icon"},
          "style":"brand","dismissible":true,"platform":"android","installSource":"sideloaded",
          "buttons":[
            {"de":"Im Play Store laden","en":"Download from the Play Store","action":"url","url":"https://play.google.com/store/apps/details?id=x","style":"filled","closes":true,
             "infoDe":"","infoEn":"Please uninstall the current Daybreak version first if installation from the Play Store fails."},
            {"de":"Mit der APK weitermachen","en":"Continue using the APK","action":"dismiss","url":"","style":"text","closes":true,
             "infoDe":"","infoEn":"Auto updates still work as usual."}
          ]}}]}
    """.trimIndent()

    @Test
    fun readsTheTitleThePictureTheLookAndTheButtons() {
        val message = ServerStatusJson.parse(playStore)!!.messages.single()
        val sheet = message.sheet!!

        assertEquals("Daybreak is now available on the Play Store", sheet.title(german = false))
        assertEquals("Daybreak gibt es jetzt im Play Store", sheet.title(german = true))
        assertEquals(NoticeImage("preset", "playstore", NoticeImageShape.ICON), sheet.image)
        assertEquals(NoticeSheetStyle.BRAND, sheet.style)
        assertEquals(2, sheet.buttons.size)
        assertEquals("https://play.google.com/store/apps/details?id=x", sheet.buttons[0].url)
        assertEquals(NoticeButtonStyle.FILLED, sheet.buttons[0].style)
        assertEquals("Download from the Play Store", sheet.buttons[0].label(german = false))
        assertTrue(sheet.buttons[0].info(german = false).startsWith("Please uninstall"))
        assertNull(sheet.buttons[1].url)
        assertEquals("Auto updates still work as usual.", sheet.buttons[1].info(german = false))
        // A note in only one language is shown in the other one too.
        assertEquals("Auto updates still work as usual.", sheet.buttons[1].info(german = true))
    }

    @Test
    fun aTitleAloneIsEnoughTextButNothingAtAllIsNot() {
        assertEquals(1, ServerStatusJson.parse(playStore)!!.messages.size)
        val empty = ServerStatusJson.parse("""{"messages":[{"id":"x","level":"info","sheet":{"style":"brand","buttons":[]}}]}""")!!
        assertTrue(empty.messages.isEmpty())
    }

    @Test
    fun onlyAnHttpsAddressEverOpens() {
        val status = ServerStatusJson.parse(
            """
            {"messages":[{"id":"m","level":"info","de":"x","sheet":{"buttons":[
              {"de":"A","action":"url","url":"http://insecure.example"},
              {"de":"B","action":"url","url":"intent://evil"},
              {"de":"C","action":"dismiss","url":"https://ignored.example"},
              {"de":"D","action":"url","url":"https://ok.example/a"}]}}]}
            """.trimIndent(),
        )!!

        assertEquals(listOf(null, null, null, "https://ok.example/a"), status.messages.single().sheet!!.buttons.map { it.url })
    }

    @Test
    fun aButtonWithoutALabelIsSkippedAndUnknownValuesFallBack() {
        val sheet = ServerStatusJson.parse(
            """{"messages":[{"id":"m","level":"info","de":"x","sheet":{"style":"weird","image":{"kind":"url","value":"https://x"},"buttons":[{"action":"url","url":"https://a.example"},{"de":"Go","style":"odd"}]}}]}""",
        )!!.messages.single().sheet!!

        assertEquals(NoticeSheetStyle.PLAIN, sheet.style)
        // Only a built-in or an uploaded picture is accepted; a link to elsewhere is not.
        assertNull(sheet.image)
        assertEquals(1, sheet.buttons.size)
        assertEquals(NoticeButtonStyle.FILLED, sheet.buttons[0].style)
        assertTrue(sheet.dismissible)
        assertEquals("all", sheet.platform)
    }

    @Test
    fun theAudienceDecidesWhoSeesIt() {
        val message = ServerStatusJson.parse(playStore)!!.messages.single()
        val apk = NoticeAudience(platform = "android", installSource = InstallSource.SIDELOADED)
        val fromPlay = NoticeAudience(platform = "android", installSource = InstallSource.PLAY)
        val desktop = NoticeAudience(platform = "desktop", installSource = InstallSource.SIDELOADED)

        assertEquals(1, MessageFilter.pending(listOf(message), emptySet(), 0L, apk).size)
        // After the move to the Play Store the announcement is not shown to the Play Store install itself.
        assertTrue(MessageFilter.pending(listOf(message), emptySet(), 0L, fromPlay).isEmpty())
        assertTrue(MessageFilter.pending(listOf(message), emptySet(), 0L, desktop).isEmpty())
        // Without an audience nothing is filtered out for it.
        assertEquals(1, MessageFilter.pending(listOf(message), emptySet(), 0L).size)
    }

    @Test
    fun aPlainMessageIsForEveryone() {
        val plain = ServerMessage("p", NoticeLevel.INFO, "Hallo", "Hello", null)
        assertEquals(1, MessageFilter.pending(listOf(plain), emptySet(), 0L, NoticeAudience(installSource = InstallSource.PLAY)).size)
        assertFalse(plain.sheet != null)
    }
}
