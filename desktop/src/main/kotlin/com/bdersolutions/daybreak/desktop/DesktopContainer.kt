package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.account.AccountManager
import com.bdersolutions.daybreak.data.account.Access
import com.bdersolutions.daybreak.data.account.Plan
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Builds the shared repositories for the desktop app, with files in %APPDATA%\Daybreak. The same
 * classes run on Android through AppContainer; only the storage behind them differs.
 */
class DesktopContainer(
    val dataDirectory: File = defaultDataDirectory(),
) {
    /** Lives as long as the app: the plan flow is shared from here. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    /** What the account flow remembers: install id, migration and trial prompts, which login is in which slot. */
    val accountPrefs = DesktopAccountPrefs(PropertiesStore(File(dataDirectory, "account_flow.properties")))

    /** The 1.x Ko-fi data (`support.properties`) is read once to carry it over to the account, then cleared. */
    val accountStorage = DesktopAccountStorage(
        preferences = PropertiesStore(File(dataDirectory, "account.properties")),
        vault = if (isWindows()) WindowsSecretStore(dataDirectory, "account.dpapi") else DesktopSecretStore(dataDirectory, "account"),
        legacyPreferences = PropertiesStore(File(dataDirectory, "support.properties")),
    )

    /** A build without an update server cannot sell or check plans: everything is then unlocked. */
    val accountAvailable: Boolean = updater.build.updateApiUrl.isNotBlank()

    /** The Daybreak account: create or restore, plan, bound WebUntis accounts, the Pro trial. */
    val accountManager = AccountManager(
        api = if (accountAvailable) DesktopHttpAccountApi(updater.build.updateApiUrl, accountPrefs::installId) else DesktopOfflineAccountApi,
        storage = accountStorage,
    )

    /** What the plan allows right now. Read with `access.value.allows(Entitlement.X)`. */
    val access: StateFlow<Access> = accountManager.snapshot
        .map { if (accountAvailable) it.access else Access(Plan.PRO) }
        .stateIn(appScope, SharingStarted.Eagerly, if (accountAvailable) accountManager.snapshot.value.access else Access(Plan.PRO))

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
