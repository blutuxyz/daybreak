package com.bdersolutions.daybreak

import android.app.Application
import android.os.Build
import java.io.File

class DaybreakApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The restart screen runs in its own process and needs none of the app (see RestartActivity).
        if (RestartActivity.isRestartProcess(currentProcessName())) return
        container = AppContainer(this)
        container.start()
    }

    private fun currentProcessName(): String? =
        if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trim { it == '\u0000' || it.isWhitespace() } }.getOrNull()
        }
}
