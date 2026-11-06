package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.support.SupportCode
import com.bdersolutions.daybreak.data.support.SupportPromptPolicy
import com.bdersolutions.daybreak.data.support.SupportState
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
 * whenever the meaning of the desktop texts (AppStrings `legal_*`) changes. 6: the desktop app now has
 * the same supporter extras as Android, so a payment is checked with the update server.
 */
const val LEGAL_VERSION = 6

/**
 * The support popup's counters, in a properties file, and whether a Ko-fi payment was confirmed. As
 * on Android, a confirmed payment unlocks the supporter extras and stops the popup for good; the
 * unlock code the person writes into the Ko-fi message is random and made on first use.
 */
class DesktopSupportStore(private val prefs: KeyValueStore) {
    private val _paid = MutableStateFlow(prefs.getBoolean(KEY_PAID, false))

    /** True once the update server confirmed a payment for this computer's unlock code. */
    val paid: StateFlow<Boolean> = _paid.asStateFlow()

    /** This computer's unlock code. It is made on first use and then stays the same. */
    fun unlockCode(): String {
        prefs.getString(KEY_UNLOCK_CODE, null)?.takeIf { SupportCode.isValid(it) }?.let { return it }
        val code = SupportCode.generate()
        prefs.edit().putString(KEY_UNLOCK_CODE, code).commit()
        return code
    }

    fun markPaid(): SupportState = update { SupportPromptPolicy.afterPaid(it) }

    fun state(): SupportState = SupportState(
        firstSeenDay = prefs.getLong(KEY_FIRST_SEEN, SupportState.NEVER),
        openDays = prefs.getInt(KEY_OPEN_DAYS, 0),
        lastOpenDay = prefs.getLong(KEY_LAST_OPEN, SupportState.NEVER),
        shownCount = prefs.getInt(KEY_SHOWN_COUNT, 0),
        lastShownDay = prefs.getLong(KEY_LAST_SHOWN, SupportState.NEVER),
        optedOut = prefs.getBoolean(KEY_OPTED_OUT, false),
        paid = prefs.getBoolean(KEY_PAID, false),
    )

    fun recordOpen(today: Long): SupportState = update { SupportPromptPolicy.afterOpen(it, today) }

    fun markShown(today: Long): SupportState = update { SupportPromptPolicy.afterShown(it, today) }

    fun optOut(): SupportState = update { SupportPromptPolicy.afterOptOut(it) }

    private fun update(transform: (SupportState) -> SupportState): SupportState {
        val next = transform(state())
        prefs.edit()
            .putLong(KEY_FIRST_SEEN, next.firstSeenDay)
            .putInt(KEY_OPEN_DAYS, next.openDays)
            .putLong(KEY_LAST_OPEN, next.lastOpenDay)
            .putInt(KEY_SHOWN_COUNT, next.shownCount)
            .putLong(KEY_LAST_SHOWN, next.lastShownDay)
            .putBoolean(KEY_OPTED_OUT, next.optedOut)
            .putBoolean(KEY_PAID, next.paid)
            .commit()
        _paid.value = next.paid
        return next
    }

    private companion object {
        const val KEY_FIRST_SEEN = "first_seen_day"
        const val KEY_OPEN_DAYS = "open_days"
        const val KEY_LAST_OPEN = "last_open_day"
        const val KEY_SHOWN_COUNT = "shown_count"
        const val KEY_LAST_SHOWN = "last_shown_day"
        const val KEY_OPTED_OUT = "opted_out"
        const val KEY_PAID = "paid"
        const val KEY_UNLOCK_CODE = "unlock_code"
    }
}

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

