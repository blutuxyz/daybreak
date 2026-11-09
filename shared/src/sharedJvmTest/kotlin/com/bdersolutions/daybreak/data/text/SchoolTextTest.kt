package com.bdersolutions.daybreak.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolTextTest {
    @Test
    fun tagsAndEntitiesAreHtml() {
        assertTrue(SchoolText.looksLikeHtml("Heute <b>kein</b> Unterricht"))
        assertTrue(SchoolText.looksLikeHtml("Zeile 1<br/>Zeile 2"))
        assertTrue(SchoolText.looksLikeHtml("Eltern &amp; Schüler"))
        assertTrue(SchoolText.looksLikeHtml("<p style=\"color:red\">Achtung</p>"))
    }

    @Test
    fun plainTextIsNotHtml() {
        assertFalse(SchoolText.looksLikeHtml("Note < 3 heißt bestanden"))
        assertFalse(SchoolText.looksLikeHtml("Raum 204 -> 105"))
        assertFalse(SchoolText.looksLikeHtml("Bitte Laptop mitbringen\nmfg"))
    }

    @Test
    fun newlinesBecomeBreaksOnlyWithoutBlockTags() {
        assertEquals("a<br>b", SchoolText.htmlWithLineBreaks("a\r\nb"))
        assertEquals("<p>a</p>\n<p>b</p>", SchoolText.htmlWithLineBreaks("<p>a</p>\n<p>b</p>"))
        assertEquals("a<br/>\nb", SchoolText.htmlWithLineBreaks("a<br/>\nb"))
    }
}
