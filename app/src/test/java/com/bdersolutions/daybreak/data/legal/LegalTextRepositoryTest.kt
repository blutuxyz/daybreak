package com.bdersolutions.daybreak.data.legal

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalTextRepositoryTest {
    private class MemoryCache : LegalTextCache {
        val entries = mutableMapOf<String, String>()
        override fun cached(language: String): String? = entries[language]
        override fun save(language: String, body: String) {
            entries[language] = body
        }
    }

    /** An answer of the server with every section, in the app's order. */
    private fun answer(version: Int, lang: String = "de", title: String = "Titel", skip: String? = null): String {
        val sections = LegalTerms.SECTION_KEYS.filter { it != skip }.joinToString(",") { key ->
            """{"key":"$key","title":"$title $key","body":"Text $key"}"""
        }
        return """{"version":$version,"lang":"$lang","sections":[$sections]}"""
    }

    @Test
    fun theShippedCopyIsShownBeforeTheServerWasAsked() {
        val repository = LegalTextRepository(fetch = null, cache = MemoryCache())
        assertEquals(LegalTerms.VERSION, repository.current.value.version)
        assertNull(repository.current.value.sections)
    }

    @Test
    fun aNewerServerTextIsShownAndItsVersionIsAccepted() = runBlocking {
        val repository = LegalTextRepository(fetch = { answer(LegalTerms.VERSION + 1) }, cache = MemoryCache())
        repository.refresh()
        assertEquals(LegalTerms.VERSION + 1, repository.current.value.version)
        assertNotNull(repository.current.value.sections)
    }

    @Test
    fun aServerTextOfTheSameVersionIsShown() = runBlocking {
        val repository = LegalTextRepository(fetch = { answer(LegalTerms.VERSION) }, cache = MemoryCache())
        repository.refresh()
        assertNotNull(repository.current.value.sections)
    }

    @Test
    fun anOlderServerTextNeverHidesTheShippedText() = runBlocking {
        val repository = LegalTextRepository(fetch = { answer(LegalTerms.VERSION - 1) }, cache = MemoryCache())
        repository.refresh()
        assertEquals(LegalTerms.VERSION, repository.current.value.version)
        assertNull(repository.current.value.sections)
    }

    @Test
    fun anOfflineDeviceKeepsTheLastTextItFetched() = runBlocking {
        val cache = MemoryCache()
        LegalTextRepository(fetch = { answer(LegalTerms.VERSION + 1) }, cache = cache).refresh()

        // Next start, no network: the cached text is still used.
        val offline = LegalTextRepository(fetch = { throw IOException("offline") }, cache = cache)
        offline.refresh()
        assertEquals(LegalTerms.VERSION + 1, offline.current.value.version)
    }

    @Test
    fun anIncompleteAnswerIsIgnored() = runBlocking {
        val repository = LegalTextRepository(fetch = { answer(LegalTerms.VERSION + 1, skip = "terms") }, cache = MemoryCache())
        repository.refresh()
        assertNull(repository.current.value.sections)
    }

    @Test
    fun theLanguageDecidesWhichCachedTextIsUsed() = runBlocking {
        val repository = LegalTextRepository(
            fetch = { lang -> answer(LegalTerms.VERSION + 1, lang = lang, title = if (lang == "en") "Terms" else "Titel") },
            cache = MemoryCache(),
        )
        repository.refresh()
        assertEquals("Titel what", repository.current.value.sections!!.first().title)

        repository.setLanguage("en")
        assertNull(repository.current.value.sections)
        repository.refresh()
        assertEquals("Terms what", repository.current.value.sections!!.first().title)
    }

    @Test
    fun turnedOffMeansTheShippedTextAndNoRequest() = runBlocking {
        var asked = false
        val repository = LegalTextRepository(
            fetch = { asked = true; answer(LegalTerms.VERSION + 1) },
            cache = MemoryCache(),
            enabled = false,
        )
        repository.refresh()
        assertFalse(asked)
        assertNull(repository.current.value.sections)
    }

    @Test
    fun theParserReadsTheSectionsInTheAppOrder() {
        val parsed = RemoteLegalJson.parse(answer(3, lang = "en"))
        assertNotNull(parsed)
        assertEquals(LegalTerms.SECTION_KEYS, parsed!!.sections.map { it.key })
        assertTrue(RemoteLegalJson.parse("not json") == null)
        assertTrue(RemoteLegalJson.parse(answer(3).replace("\"key\":\"what\"", "\"key\":\"zzz\"")) == null)
    }
}
