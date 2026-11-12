package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.platform.KeyValueStore
import com.bdersolutions.daybreak.untis.RawResponseSink
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** One raw API response, as the debug page lists it. Requests and credentials are never stored. */
data class DesktopRawEntry(
    val id: Long,
    val method: String,
    val receivedAt: Long,
    val body: String,
)

/**
 * The last raw API responses, kept in a file in the app data folder, so the debug page can show
 * them after a restart. A ring buffer of [MAX_ENTRIES], as the Android app keeps it.
 */
class DesktopRawLog(private val file: File) : RawResponseSink {
    private val lock = Any()
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<DesktopRawEntry>> = _entries.asStateFlow()

    override fun onResponse(method: String, receivedAt: Long, body: String) {
        val entry = DesktopRawEntry(
            id = receivedAt + (0..999).random(),
            method = method,
            receivedAt = receivedAt,
            body = body.take(MAX_BODY_CHARS),
        )
        synchronized(lock) {
            val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
            _entries.value = updated
            persist(updated)
        }
    }

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            runCatching { file.delete() }
        }
    }

    private fun load(): List<DesktopRawEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JsonParser.parseString(file.readText()) as JsonArray
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                DesktopRawEntry(
                    id = obj.get("id")?.asLong ?: 0L,
                    method = obj.get("method")?.asString ?: "",
                    receivedAt = obj.get("receivedAt")?.asLong ?: 0L,
                    body = obj.get("body")?.asString.orEmpty(),
                )
            }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    private fun persist(entries: List<DesktopRawEntry>) {
        runCatching {
            val array = JsonArray()
            entries.forEach { entry ->
                array.add(JsonObject().apply {
                    addProperty("id", entry.id)
                    addProperty("method", entry.method)
                    addProperty("receivedAt", entry.receivedAt)
                    addProperty("body", entry.body)
                })
            }
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(array.toString())
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 20
        const val MAX_BODY_CHARS = 120_000
    }
}

/**
 * The privacy policy version the person accepted. Kept apart from the session, so logging out does
 * not ask again. [LEGAL_VERSION] mirrors `LegalTerms.VERSION` in the Android app; change both together.
 */
class DesktopTermsStore(private val prefs: KeyValueStore) {
    private val _acceptedVersion = MutableStateFlow(prefs.getInt(KEY_VERSION, NONE))
    val acceptedVersion: StateFlow<Int> = _acceptedVersion.asStateFlow()

    /** True when the accepted version still covers the current texts. */
    fun isAccepted(): Boolean = _acceptedVersion.value != NONE && _acceptedVersion.value >= LEGAL_VERSION

    /** Accepted before, but a newer text has come since. */
    fun isUpdate(): Boolean = _acceptedVersion.value != NONE && _acceptedVersion.value < LEGAL_VERSION

    fun accept(now: Long = System.currentTimeMillis()) {
        prefs.edit().putInt(KEY_VERSION, LEGAL_VERSION).putLong(KEY_AT, now).commit()
        _acceptedVersion.value = LEGAL_VERSION
    }

    private companion object {
        const val NONE = 0
        const val KEY_VERSION = "accepted_version"
        const val KEY_AT = "accepted_at"
    }
}

/**
 * The version of the privacy policy and terms of use of the desktop app. It follows the Android
 * app's `LegalTerms.VERSION` in spirit, but the desktop texts are versioned on their own: raise it
 * whenever the meaning of the desktop texts (AppStrings `legal_*`) changes. 7 (Daybreak 2.0.0): Daybreak
 * account with restore code, bound WebUntis accounts (salted hash only), synced data, plans paid through
 * Stripe, Ko-fi only as a donation link. The contact address is `LegalTerms.CONTACT_EMAIL`.
 */
const val LEGAL_VERSION = 7

/**
 * Counts taps on the "System" theme option. Ten taps unlock the debug page until the app restarts,
 * as the Android settings do (the state lives in memory only).
 */
class DebugUnlock {
    private var taps = 0
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun tapSystemTheme() {
        if (_unlocked.value) return
        taps++
        if (taps >= UNLOCK_TAPS) _unlocked.value = true
    }

    private companion object {
        const val UNLOCK_TAPS = 10
    }
}

