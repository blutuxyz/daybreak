package com.bdersolutions.daybreak.data.branding

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Changes the name under the app icon, and the icon picture, by switching which `activity-alias` of
 * MainActivity is the launcher entry. Exactly one alias is enabled at a time. The app is not restarted; the launcher
 * usually shows the new name within a few seconds.
 */
class LauncherNameSwitcher(private val context: Context) {
    private val packageManager = context.packageManager

    private data class Entry(val preset: AppNamePreset, val style: AppIconStyle)

    private val entries = AppNamePreset.entries.flatMap { preset -> AppIconStyle.entries.map { Entry(preset, it) } }

    private fun component(entry: Entry) =
        ComponentName(context.packageName, "$ALIAS_PACKAGE.${aliasNameFor(entry.preset, entry.style)}")

    /** The default alias is enabled in the manifest, the others are not, until they are set explicitly. */
    private fun isEnabled(entry: Entry): Boolean =
        when (packageManager.getComponentEnabledSetting(component(entry))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                entry.preset == AppNamePreset.DAYBREAK && entry.style == AppIconStyle.STANDARD
            else -> false
        }

    /** Makes the alias for [preset] and [style] the launcher entry. Does nothing if it already is the only one. */
    fun apply(preset: AppNamePreset, style: AppIconStyle) {
        val target = Entry(preset, style)
        try {
            val alreadyRight = isEnabled(target) && entries.none { it != target && isEnabled(it) }
            if (alreadyRight) return
            // Enable the new entry first, so the app never has no icon in between.
            set(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            entries.filter { it != target }
                .forEach { set(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not change the launcher name", error)
        }
    }

    private fun set(entry: Entry, state: Int) {
        packageManager.setComponentEnabledSetting(component(entry), state, PackageManager.DONT_KILL_APP)
    }

    private companion object {
        const val TAG = "LauncherNameSwitcher"
        const val ALIAS_PACKAGE = "com.bdersolutions.daybreak"
    }
}
