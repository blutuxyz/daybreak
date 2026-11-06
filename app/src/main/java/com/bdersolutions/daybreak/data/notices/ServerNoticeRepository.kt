package com.bdersolutions.daybreak.data.notices

import android.content.Context
import com.bdersolutions.daybreak.data.update.UpdateApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * The last answer of the update server and the ids of the messages the user has dismissed. Plain
 * values, no secrets; kept apart from the session so that signing out does not reset them.
 */
class ServerNoticeStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun cachedStatusBody(): String? = preferences.getString(KEY_STATUS, null)

    fun saveStatusBody(body: String) {
        preferences.edit().putString(KEY_STATUS, body).apply()
    }

    fun dismissed(): Set<String> = preferences.getStringSet(KEY_DISMISSED, null).orEmpty().toSet()

    fun dismiss(id: String) {
        preferences.edit().putStringSet(KEY_DISMISSED, dismissed() + id).apply()
    }

    private companion object {
        const val PREFS_NAME = "daybreak_notices"
        const val KEY_STATUS = "status_body"
        const val KEY_DISMISSED = "dismissed_ids"
    }
}

/**
 * The minimum app version and the messages from the update server. The last answer is kept, so the
 * minimum version still holds when the phone is offline. [api] is null when no update server is
 * configured; then there is no minimum and no message.
 */
class ServerNotices(
    private val api: UpdateApi?,
    private val store: ServerNoticeStore,
    /**
     * False in debug builds (see `daybreak.debugServerNotices` in app/build.gradle.kts): the server is
     * never asked and an answer cached earlier is ignored, so a development build is not stopped by
     * the production minimum version or switched off by a remote switch.
     */
    private val enabled: Boolean = true,
) {
    private val refreshLock = Mutex()

    private val _status = MutableStateFlow(if (enabled) ServerStatusJson.parse(store.cachedStatusBody()) else null)

    /** The last known status, or null before the server has ever been asked. */
    val status: StateFlow<ServerStatus?> = _status.asStateFlow()

    private val _dismissed = MutableStateFlow(store.dismissed())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    /** Asks the server again. An unreachable server or an unusable answer keeps the old status. */
    suspend fun refresh() {
        if (!enabled) return
        val api = api ?: return
        refreshLock.withLock {
            val body = try {
                api.statusBody()
            } catch (_: IOException) {
                return
            }
            val status = ServerStatusJson.parse(body) ?: return
            store.saveStatusBody(body)
            _status.value = status
        }
    }

    /** The developer's switches as last received; everything is on before the first answer. */
    val switches: RemoteSwitches get() = _status.value?.switches ?: RemoteSwitches()

    /** False while the developer has turned the single feature [id] off. */
    fun featureOn(id: String): Boolean = switches.featureOn(id)

    /** Remembers that the user has read [id]; it is not shown again. */
    fun dismiss(id: String) {
        store.dismiss(id)
        _dismissed.value = store.dismissed()
    }

}
