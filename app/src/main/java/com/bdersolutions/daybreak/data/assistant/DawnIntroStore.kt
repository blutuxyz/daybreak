package com.bdersolutions.daybreak.data.assistant

import android.content.Context

/**
 * The rules of the one-time full-screen page "Daybreak AI is now Dawn". Everyone who had the app before the release
 * that introduced the name ([VERSION]) sees it once, even if they closed an earlier notice. A new install does not.
 */
object DawnNotice {
    const val VERSION = 2

    /** True while the notice of [VERSION] is still due, given the version stored on the device. */
    fun needed(storedVersion: Int): Boolean = storedVersion < VERSION

    /** A fresh install has the same first-install and last-update time; an updated app does not. */
    fun isFreshInstall(firstInstallTime: Long, lastUpdateTime: Long): Boolean = firstInstallTime == lastUpdateTime
}

/** Remembers which notice version the device has seen. Kept in its own preferences. */
class DawnIntroStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun needsNotice(): Boolean = DawnNotice.needed(preferences.getInt(KEY_VERSION, 0))

    /** Closed, or not needed (a new install): the notice of the current version is done. */
    fun markShown() {
        preferences.edit().putInt(KEY_VERSION, DawnNotice.VERSION).apply()
    }

    private companion object {
        const val PREFS_NAME = "daybreak_dawn_intro"
        const val KEY_VERSION = "notice_version"
    }
}
