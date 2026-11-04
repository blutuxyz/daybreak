package com.bdersolutions.daybreak.data.update

import android.content.Context

/** Small bits of update state that must outlive the process. Plain values, no secrets. */
class UpdateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When the server was last asked, in epoch milliseconds. */
    var lastCheckAt: Long
        get() = preferences.getLong(KEY_LAST_CHECK, 0L)
        set(value) = preferences.edit().putLong(KEY_LAST_CHECK, value).apply()

    /** The newest version the user has been told about in "What's new". 0 until first run. */
    var lastSeenVersionCode: Long
        get() = preferences.getLong(KEY_LAST_SEEN, 0L)
        set(value) = preferences.edit().putLong(KEY_LAST_SEEN, value).apply()

    /** The newest version a notification was posted for, so it is posted once. */
    var notifiedVersionCode: Long
        get() = preferences.getLong(KEY_NOTIFIED, 0L)
        set(value) = preferences.edit().putLong(KEY_NOTIFIED, value).apply()

    /** The release history from the last time it was fetched. Works offline. */
    fun releases(): List<ReleaseInfo> = UpdateJson.parseReleases(preferences.getString(KEY_RELEASES, null))

    fun saveReleases(releases: List<ReleaseInfo>) {
        preferences.edit().putString(KEY_RELEASES, UpdateJson.toJson(releases)).apply()
    }

    private companion object {
        const val PREFS_NAME = "daybreak_updates"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_LAST_SEEN = "last_seen_version_code"
        const val KEY_NOTIFIED = "notified_version_code"
        const val KEY_RELEASES = "releases"
    }
}
