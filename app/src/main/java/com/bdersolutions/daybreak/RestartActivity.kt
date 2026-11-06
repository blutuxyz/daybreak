package com.bdersolutions.daybreak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bdersolutions.daybreak.data.branding.AppIconStyle
import com.bdersolutions.daybreak.data.branding.AppNamePreset
import com.bdersolutions.daybreak.data.branding.LauncherNameSwitcher

/**
 * Restarts the app so that a new launcher name or icon takes effect. It runs in its own process
 * (`:restart` in the manifest), like the well-known "process phoenix" trick, because the app cannot
 * restart itself from inside its own process: on some phones (Samsung, Xiaomi) the system closes the
 * app when its launcher entry changes, and a restart that lives in the same process dies with it.
 *
 * The order matters. The main process has already exited when this screen opens, so nothing of the
 * app is running while the launcher entry is switched here. Then the app is started again in a new
 * task, which also starts a fresh main process.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preset = AppNamePreset.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PRESET) }
        val style = AppIconStyle.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_STYLE) }
        if (preset != null && style != null) {
            try {
                LauncherNameSwitcher(applicationContext).apply(preset, style)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not switch the launcher entry", error)
            }
        }
        // Give the package manager a moment, then open the app through its (now enabled) launcher entry.
        Handler(Looper.getMainLooper()).postDelayed({
            val launch = packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            try {
                startActivity(launch)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not start the app again", error)
            }
            finish()
            // This process only exists to restart the app; do not leave it behind.
            Runtime.getRuntime().exit(0)
        }, SWITCH_SETTLE_MS)
    }

    companion object {
        private const val TAG = "RestartActivity"
        private const val EXTRA_PRESET = "preset"
        private const val EXTRA_STYLE = "style"
        private const val SWITCH_SETTLE_MS = 350L

        /** True in the `:restart` process, where the app's own start-up must not run. */
        fun isRestartProcess(processName: String?): Boolean = processName?.endsWith(":restart") == true

        fun intent(context: Context, preset: AppNamePreset, style: AppIconStyle): Intent =
            Intent(context, RestartActivity::class.java)
                .putExtra(EXTRA_PRESET, preset.name)
                .putExtra(EXTRA_STYLE, style.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
