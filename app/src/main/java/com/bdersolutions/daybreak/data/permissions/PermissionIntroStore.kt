package com.bdersolutions.daybreak.data.permissions

import android.content.Context

/** The permissions the app asks for at its first launch, in the order it asks them. */
enum class PermissionStep { NOTIFICATIONS, UNKNOWN_SOURCES }

/**
 * The permissions the app needs, asked once at the first launch so that no prompt pops up during use.
 * [neededSteps] is the pure rule: notifications on Android 13 and newer when they are not allowed, and
 * "install unknown apps" for a build that is not from Google Play when the in-app updater is configured.
 */
object PermissionIntro {
    fun neededSteps(
        notificationsAllowed: Boolean,
        needsNotificationPermission: Boolean,
        sideloaded: Boolean,
        updatesConfigured: Boolean,
        canInstallUnknownApps: Boolean,
    ): List<PermissionStep> = buildList {
        if (needsNotificationPermission && !notificationsAllowed) add(PermissionStep.NOTIFICATIONS)
        if (sideloaded && updatesConfigured && !canInstallUnknownApps) add(PermissionStep.UNKNOWN_SOURCES)
    }
}

/** Remembers that the permission screen has been completed. Plain value, no secrets. */
class PermissionIntroStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDone(): Boolean = preferences.getBoolean(KEY_DONE, false)

    fun markDone() {
        preferences.edit().putBoolean(KEY_DONE, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "daybreak_permissions"
        const val KEY_DONE = "done"
    }
}
