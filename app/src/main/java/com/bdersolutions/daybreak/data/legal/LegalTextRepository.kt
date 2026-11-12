package com.bdersolutions.daybreak.data.legal

import android.content.Context
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** One titled block of the privacy policy or the terms, as the server sends it. */
data class LegalTextSection(val key: String, val title: String, val body: String)

/**
 * The text on screen and its version, always taken together. [sections] is null for the copy the app
 * ships with (the string resources); otherwise it is the text the server sent for this language.
 */
data class LegalText(val version: Int, val sections: List<LegalTextSection>?)

/** The answer of `GET /v1/legal`: the version, the language and the sections. */
data class RemoteLegalText(val version: Int, val language: String, val sections: List<LegalTextSection>)

object RemoteLegalJson {
    /** Reads an answer of the server. Null unless it has the right sections in the right order, all filled in. */
    fun parse(body: String?): RemoteLegalText? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val version = root.get("version").asInt
            val language = root.get("lang").asString
            val sections = root.getAsJsonArray("sections").map { element ->
                val section = element.asJsonObject
                LegalTextSection(section.get("key").asString, section.get("title").asString, section.get("body").asString)
            }
            val valid = version >= 1 &&
                language in LANGUAGES &&
                sections.map { it.key } == LegalTerms.SECTION_KEYS &&
                sections.all { it.title.isNotBlank() && it.body.isNotBlank() }
            if (valid) RemoteLegalText(version, language, sections) else null
        } catch (_: Exception) {
            null
        }
    }

    private val LANGUAGES = setOf("de", "en")
}

/** Keeps the last answer of the server per language. Plain text, no secrets. */
interface LegalTextCache {
    fun cached(language: String): String?
    fun save(language: String, body: String)
}

class SharedPrefsLegalTextCache(context: Context) : LegalTextCache {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun cached(language: String): String? = preferences.getString(key(language), null)

    override fun save(language: String, body: String) {
        preferences.edit().putString(key(language), body).apply()
    }

    private fun key(language: String) = "text_$language"

    private companion object {
        const val PREFS_NAME = "daybreak_legal"
    }
}

/**
 * The privacy policy and terms the app shows and asks the user to accept. The app starts with the copy it
 * ships with; [refresh] asks the server for the current text. A server text is used only when its version
 * is at least the shipped one, so an old cached answer can never hide a newer shipped text. Offline, the
 * last answer for the language stays in use.
 *
 * [fetch] reads `GET /v1/legal?lang=…` and returns the body; it throws an IOException when the server
 * cannot be reached. [enabled] is false in debug builds, which always show the shipped copy.
 */
class LegalTextRepository(
    private val fetch: (suspend (String) -> String)?,
    private val cache: LegalTextCache,
    private val enabled: Boolean = true,
) {
    private val lock = Mutex()
    private var language = "de"
    private val _current = MutableStateFlow(compute())

    /** The text to show and to accept. */
    val current: StateFlow<LegalText> = _current.asStateFlow()

    /** Sets the language of the app ("de" or "en"; anything else counts as German, like the server). */
    fun setLanguage(code: String) {
        val next = if (code == "en") "en" else "de"
        if (next == language) return
        language = next
        _current.value = compute()
    }

    /** Asks the server for the text in the current language. An unreachable server keeps the cached text. */
    suspend fun refresh() {
        val fetch = fetch ?: return
        if (!enabled) return
        lock.withLock {
            val requested = language
            val body = try {
                fetch(requested)
            } catch (_: IOException) {
                return
            }
            val remote = RemoteLegalJson.parse(body) ?: return
            cache.save(remote.language, body)
            _current.value = compute()
        }
    }

    private fun compute(): LegalText {
        val remote = if (enabled) RemoteLegalJson.parse(cache.cached(language)) else null
        return if (remote != null && remote.version >= LegalTerms.VERSION) {
            LegalText(remote.version, remote.sections)
        } else {
            LegalText(LegalTerms.VERSION, null)
        }
    }
}
