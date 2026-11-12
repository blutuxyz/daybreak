package com.bdersolutions.daybreak

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.bdersolutions.daybreak.data.account.AccountManager
import com.bdersolutions.daybreak.data.account.AccountPrefs
import com.bdersolutions.daybreak.data.account.Access
import com.bdersolutions.daybreak.data.account.AndroidAccountStorage
import com.bdersolutions.daybreak.data.account.Entitlement
import com.bdersolutions.daybreak.data.account.HttpAccountApi
import com.bdersolutions.daybreak.data.account.OfflineAccountApi
import com.bdersolutions.daybreak.data.account.Plan
import com.bdersolutions.daybreak.data.assistant.AssistantApi
import com.bdersolutions.daybreak.data.assistant.AssistantIdentity
import com.bdersolutions.daybreak.data.assistant.ChatStore
import com.bdersolutions.daybreak.data.attachments.AttachmentDownloader
import com.bdersolutions.daybreak.data.cache.AbsenceLessonStore
import com.bdersolutions.daybreak.data.cache.TimetableCacheStore
import com.bdersolutions.daybreak.data.debug.RawLogStore
import com.bdersolutions.daybreak.data.haptics.Haptics
import com.bdersolutions.daybreak.data.haptics.VibratorHapticOutput
import com.bdersolutions.daybreak.data.legal.LegalTextRepository
import com.bdersolutions.daybreak.data.legal.SharedPrefsLegalTextCache
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
    val launcherNameSwitcher = LauncherNameSwitcher(appContext)


    val secretVault = SecretVault(appContext)

    // The AI assistant asks the same server, which holds the API key; null without that address.
    val assistantIdentity = AssistantIdentity(appContext)

    // The Daybreak account (docs/v2-spec.md): plan, bound WebUntis accounts, restore code. The plan is what
    // unlocks features; every screen asks `access`, never the account itself. A build without an update
    // server address cannot reach any server, so nothing is locked there (self-built apps and development).
    val accountAvailable: Boolean = BuildConfig.UPDATE_API_URL.isNotBlank()
    val accountStorage = AndroidAccountStorage(appContext, secretVault)
    val accountPrefs = AccountPrefs(appContext)
    val accountManager = AccountManager(
        api = if (accountAvailable) {
            HttpAccountApi(BuildConfig.UPDATE_API_URL, installId = { assistantIdentity.installId })
        } else {
            OfflineAccountApi
        },
        storage = accountStorage,
    )

    /** Lives as long as the process, for work that follows settings in the background. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True while the full-screen "new version" page is up, so other pop-ups wait for it. */
    val updatePageVisible = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** What the plan allows right now, for the whole app. Read with `access.value.allows(Entitlement.X)`. */
    val access: StateFlow<Access> = accountManager.snapshot
        .map { if (accountAvailable) it.access else Access(Plan.PRO) }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.Eagerly, if (accountAvailable) accountManager.snapshot.value.access else Access(Plan.PRO))

    // Saved assistant chats, only on this device; deleted on logout.
    val chatStore = ChatStore(appContext)
    val assistantApi: AssistantApi? = BuildConfig.UPDATE_API_URL.takeIf { it.isNotBlank() }?.let {
        AssistantApi(
            it,
            installId = { assistantIdentity.installId },
            accountToken = { accountStorage.credentials()?.token },
        )
    }

    // The assistant's settings tools. Lazy, because the schedulers it uses are declared further down.
    val assistantSettings: com.bdersolutions.daybreak.data.settings.SettingsAssistantPort by lazy {
        com.bdersolutions.daybreak.data.settings.SettingsAssistantPort(
            settings = settingsRepository,
            paid = { access.value.allows(Entitlement.APP_NAME) },
            syncScheduler = syncScheduler,
            reminderScheduler = reminderScheduler,
            updateScheduler = updateScheduler,
            ensureNotificationChannel = { lessonNotificationManager.ensureChannel() },
        )
    }

    // Whether the introduction was finished (see TutorialStore). Kept apart from the session.
    val tutorialStore = TutorialStore(appContext)
    val dawnIntroStore = com.bdersolutions.daybreak.data.assistant.DawnIntroStore(appContext)
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

    // The privacy policy and terms the app shows and asks to accept: the server's copy, or the shipped one.
    // Debug builds always show the shipped copy (like the server notices), so a development build is not
    // changed by a text that is live on the server. Declared after updateApi, which it reads.
    val legalTexts = LegalTextRepository(
        fetch = updateApi?.let { api ->
            val fetchLegal: suspend (String) -> String = { language -> api.legalBody(language) }
            fetchLegal
        },
        cache = SharedPrefsLegalTextCache(appContext),
        enabled = BuildConfig.SERVER_NOTICES,
    )
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
        // The name under the icon follows the plan: back to "Daybreak" without Plus (or supporter status).
        val chosen = settingsRepository.current()
        launcherNameSwitcher.apply(
            AppNaming.effectivePreset(access.value.allows(Entitlement.APP_NAME), chosen.appNamePreset),
            AppNaming.effectiveIconStyle(access.value.allows(Entitlement.APP_ICON), chosen.appIconStyle),
        )
        // The plan is read from the server again at every start (the cached one works until then, and offline).
        if (accountAvailable) appScope.launch { accountManager.refresh() }
        // The privacy policy and terms: the current text from the server (the shipped copy is used until then).
        appScope.launch { legalTexts.refresh() }
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
