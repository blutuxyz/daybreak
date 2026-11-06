package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.cache.AbsenceLessonStore
import com.bdersolutions.daybreak.data.cache.TimetableCacheStore
import com.bdersolutions.daybreak.data.repository.AbsenceLessonHistory
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.desktop.platform.DesktopSecretStore
import com.bdersolutions.daybreak.desktop.platform.DesktopTextFile
import com.bdersolutions.daybreak.desktop.platform.PropertiesStore
import com.bdersolutions.daybreak.desktop.platform.WindowsSecretStore
import com.bdersolutions.daybreak.untis.UntisApi
import java.io.File

/**
 * Builds the shared repositories for the desktop app, with files in %APPDATA%\Daybreak. The same
 * classes run on Android through AppContainer; only the storage behind them differs.
 */
class DesktopContainer(
    val dataDirectory: File = defaultDataDirectory(),
) {
    val settings = SettingsRepository(PropertiesStore(File(dataDirectory, "settings.properties")))

    /** Remembers the last day each reminder was sent, so nothing is announced twice. */
    val reminderStore = PropertiesStore(File(dataDirectory, "reminders.properties"))

    /** Checks the update server for a newer Windows build. */
    val updater = com.bdersolutions.daybreak.desktop.update.DesktopUpdater()

    val sessionStore = SessionStore(
        preferences = PropertiesStore(File(dataDirectory, "session.properties")),
        vault = if (isWindows()) WindowsSecretStore(dataDirectory) else DesktopSecretStore(dataDirectory),
    )
    /** The raw API responses, for the debug page. Recorded from the first request on. */
    val rawLog = DesktopRawLog(File(dataDirectory, RAW_LOG_FILE))
    val untisApi = UntisApi(rawSink = rawLog)

    /** Which privacy policy version was accepted on this computer. */
    val termsStore = DesktopTermsStore(PropertiesStore(File(dataDirectory, "terms.properties")))

    /** The support popup's counters. */
    val supportStore = DesktopSupportStore(PropertiesStore(File(dataDirectory, "support.properties")))

    /** Ten taps on the "System" theme unlock the debug page, as on Android. */
    val debugUnlock = DebugUnlock()
    val timetableCacheStore = TimetableCacheStore(DesktopTextFile(File(dataDirectory, TimetableCacheStore.FILE_NAME)))
    val timetable = TimetableRepository(
        cacheStore = timetableCacheStore,
        sessionStore = sessionStore,
        api = untisApi,
    )
    val schoolData = SchoolDataRepository(untisApi, sessionStore)
    val absenceHistory = AbsenceLessonHistory(
        store = AbsenceLessonStore(DesktopTextFile(File(dataDirectory, AbsenceLessonStore.FILE_NAME))),
        timetable = timetable,
        sessionStore = sessionStore,
        api = untisApi,
    )

    companion object {
        private const val RAW_LOG_FILE = "raw_api_responses.json"

        fun defaultDataDirectory(): File {
            System.getenv("DAYBREAK_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
            return File(base, "Daybreak")
        }

        fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
    }
}
