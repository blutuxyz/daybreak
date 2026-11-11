package com.bdersolutions.daybreak

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.bdersolutions.daybreak.data.assistant.AssistantApi
import com.bdersolutions.daybreak.data.assistant.AssistantIdentity
import com.bdersolutions.daybreak.data.assistant.ChatStore
import com.bdersolutions.daybreak.data.attachments.AttachmentDownloader
import com.bdersolutions.daybreak.data.cache.AbsenceLessonStore
import com.bdersolutions.daybreak.data.cache.TimetableCacheStore
import com.bdersolutions.daybreak.data.debug.RawLogStore
import com.bdersolutions.daybreak.data.haptics.Haptics
import com.bdersolutions.daybreak.data.haptics.VibratorHapticOutput
import com.bdersolutions.daybreak.data.legal.TermsStore
import com.bdersolutions.daybreak.data.notices.ServerNoticeStore
import com.bdersolutions.daybreak.data.notices.ServerNotices
import com.bdersolutions.daybreak.data.notifications.LessonNotificationManager
import com.bdersolutions.daybreak.data.repository.AbsenceLessonHistory
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.security.SecretVault
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.data.branding.AppNaming
import com.bdersolutions.daybreak.data.reminders.ReminderNotificationManager
import com.bdersolutions.daybreak.data.reminders.ReminderScheduler
import com.bdersolutions.daybreak.data.reminders.ReminderStore
import com.bdersolutions.daybreak.data.branding.LauncherNameSwitcher
import com.bdersolutions.daybreak.data.support.SupportApi
import com.bdersolutions.daybreak.data.support.SupportStore
import com.bdersolutions.daybreak.data.sync.SyncScheduler
import com.bdersolutions.daybreak.data.update.UpdateApi
import com.bdersolutions.daybreak.data.update.UpdateManager
import com.bdersolutions.daybreak.data.update.UpdateNotificationManager
import com.bdersolutions.daybreak.data.update.UpdateScheduler
import com.bdersolutions.daybreak.data.update.UpdateStore
import com.bdersolutions.daybreak.data.tutorial.TutorialStore
import com.bdersolutions.daybreak.platform.AtomicTextFile
import com.bdersolutions.daybreak.platform.SharedPreferencesStore
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.widget.WidgetUpdater
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private fun preferences(name: String) =
        SharedPreferencesStore(appContext.getSharedPreferences(name, Context.MODE_PRIVATE))

    private fun privateFile(name: String) = AtomicTextFile(File(appContext.filesDir, name))

    val settingsRepository = SettingsRepository(preferences(SettingsRepository.PREFS_NAME))

    // Whether the phone is online now; screens that need the server ask again when it comes back.
    val networkMonitor = com.bdersolutions.daybreak.data.network.NetworkMonitor(appContext)

    // One haptics engine for the whole app. It reads the settings on every call, so edits apply at once.
    val haptics = Haptics(
        settings = { settingsRepository.current().haptics },
        output = VibratorHapticOutput(appContext),
        systemAllows = { VibratorHapticOutput.systemTouchFeedbackOn(appContext) },
    )
    val termsStore = TermsStore(appContext)
    val supportStore = SupportStore(appContext)
    val launcherNameSwitcher = LauncherNameSwitcher(appContext)

    // Confirms a Ko-fi payment through the update server; null (no paid hide) without that address.
    val supportApi: SupportApi? = BuildConfig.UPDATE_API_URL.takeIf { it.isNotBlank() }?.let { SupportApi(it) }

    // The AI assistant asks the same server, which holds the API key; null without that address.
    val assistantIdentity = AssistantIdentity(appContext)
    // Saved assistant chats, only on this device; deleted on logout.
    val chatStore = ChatStore(appContext)
    val assistantApi: AssistantApi? = BuildConfig.UPDATE_API_URL.takeIf { it.isNotBlank() }?.let {
        AssistantApi(it, installId = { assistantIdentity.installId })
    }

    // Whether the introduction was finished (see TutorialStore). Kept apart from the session.
    val tutorialStore = TutorialStore(appContext)
    val secretVault = SecretVault(appContext)
    val sessionStore = SessionStore(preferences(SessionStore.PREFS_NAME), secretVault)
    val rawLogStore = RawLogStore(appContext)
    val untisApi = UntisApi(rawLogStore)
    val timetableCacheStore = TimetableCacheStore(privateFile(TimetableCacheStore.FILE_NAME))
    val timetableRepository = TimetableRepository(
        cacheStore = timetableCacheStore,
        sessionStore = sessionStore,
        api = untisApi,
    )
    val schoolData = SchoolDataRepository(untisApi, sessionStore)
    val attachmentDownloader = AttachmentDownloader(appContext, schoolData)
    val absenceLessonHistory = AbsenceLessonHistory(
        store = AbsenceLessonStore(privateFile(AbsenceLessonStore.FILE_NAME)),
        timetable = timetableRepository,
        sessionStore = sessionStore,
        api = untisApi,
    )
    val lessonNotificationManager = LessonNotificationManager(appContext, settingsRepository)
    val syncScheduler = SyncScheduler(appContext)
    val widgetUpdater = WidgetUpdater(appContext)

    // Updates come from the update server (update-server/). Without a configured address the
    // feature is off and the Updates screen is hidden.
    private val updateApi = BuildConfig.UPDATE_API_URL.takeIf { it.isNotBlank() }?.let { UpdateApi(it) }
    val updateManager = UpdateManager(
        context = appContext,
        api = updateApi,
        store = UpdateStore(appContext),
    )

    // The minimum app version and the messages (the same server). A too old app is stopped at the
    // "update required" screen; messages are shown once in a pop-up.
    // Debug builds skip it entirely (BuildConfig.SERVER_NOTICES), so a dev build is never blocked.
    val serverNotices = ServerNotices(
        api = updateApi,
        store = ServerNoticeStore(appContext),
        enabled = BuildConfig.SERVER_NOTICES,
    )
    val updateNotificationManager = UpdateNotificationManager(appContext, settingsRepository)
    val updateScheduler = UpdateScheduler(appContext)

    // Reminders: a summary before the first lesson and an evening note about exams and homework.
    val reminderStore = ReminderStore(appContext)
    val reminderNotifier = ReminderNotificationManager(
        context = appContext,
        settingsRepository = settingsRepository,
        timetable = timetableRepository,
        schoolData = schoolData,
        store = reminderStore,
    )
    val reminderScheduler = ReminderScheduler(
        context = appContext,
        settingsRepository = settingsRepository,
        timetable = timetableRepository,
        sessionStore = sessionStore,
        store = reminderStore,
        serverNotices = serverNotices,
    )

    /** Lives as long as the process, for work that follows settings in the background. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        // The widget draws with the app's colours, language and its own settings, so it is redrawn
        // when any of them changes (a moment later, so a run of taps redraws it once).
        // Android 12+ draws the splash screen before any app code runs, from the night mode it
        // knows for the app. Telling it the app's own choice keeps a dark app from opening with a
        // white splash (and the other way round). The activity handles the uiMode change itself.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appScope.launch {
                settingsRepository.settings
                    .map { it.themeMode }
                    .distinctUntilChanged()
                    .collect { mode ->
                        val night = when (mode) {
                            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                        }
                        runCatching { appContext.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(night) }
                    }
            }
        }
        appScope.launch {
            settingsRepository.settings
                .map { listOf(it.widget, it.themeMode, it.dynamicColor, it.amoledBlack, it.accent, it.language, it.lessonColors, it.subjectColorMode) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(400)
                .collect { widgetUpdater.update() }
        }
        // The name under the icon follows the supporter extra: back to "Daybreak" without a payment.
        val chosen = settingsRepository.current()
        launcherNameSwitcher.apply(
            AppNaming.effectivePreset(supportStore.paid.value, chosen.appNamePreset),
            AppNaming.effectiveIconStyle(supportStore.paid.value, chosen.appIconStyle),
        )
        lessonNotificationManager.ensureChannel()
        reminderNotifier.ensureChannels()
        if (sessionStore.isLoggedIn()) {
            syncScheduler.schedule(settingsRepository.current())
            reminderScheduler.schedule()
        }
        if (updateManager.isConfigured) {
            updateNotificationManager.ensureChannel()
            val settings = settingsRepository.current()
            updateScheduler.schedule(settings.autoCheckUpdates, settings.updateCheckHours)
        }
    }
}
