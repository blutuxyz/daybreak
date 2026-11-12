package com.bdersolutions.daybreak.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.debug.RawLogStore
import com.bdersolutions.daybreak.data.haptics.HapticCapabilities
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.haptics.HapticPreset
import com.bdersolutions.daybreak.data.haptics.HapticSettings
import com.bdersolutions.daybreak.data.haptics.Haptics
import com.bdersolutions.daybreak.data.notifications.LessonNotificationManager
import com.bdersolutions.daybreak.data.repository.AbsenceLessonHistory
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.repository.TimetableSyncStatus
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.AbsenceUnit
import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.AppSettings
import com.bdersolutions.daybreak.data.settings.LessonColorSettings
import com.bdersolutions.daybreak.data.settings.WidgetSettings
import com.bdersolutions.daybreak.data.settings.TimetableDisplay
import com.bdersolutions.daybreak.data.settings.MotionStyle
import com.bdersolutions.daybreak.data.schedule.IcsExport
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.settings.SubjectColorMode
import com.bdersolutions.daybreak.data.settings.TextScale
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.data.branding.AppIconStyle
import com.bdersolutions.daybreak.data.reminders.ReminderNotificationManager
import com.bdersolutions.daybreak.data.notices.ServerNotices
import com.bdersolutions.daybreak.data.reminders.ReminderScheduler
import com.bdersolutions.daybreak.data.branding.AppNamePreset
import com.bdersolutions.daybreak.data.branding.LauncherNameSwitcher
import com.bdersolutions.daybreak.data.account.Access
import com.bdersolutions.daybreak.data.account.Entitlement
import com.bdersolutions.daybreak.data.sync.SyncScheduler
import com.bdersolutions.daybreak.data.update.ReleaseInfo
import com.bdersolutions.daybreak.data.update.UpdateManager
import com.bdersolutions.daybreak.data.update.UpdateScheduler
import com.bdersolutions.daybreak.data.update.UpdateState
import com.bdersolutions.daybreak.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** Taps on the theme "System" option that reveal the debug log. */
const val DEBUG_UNLOCK_TAPS = 10

class SettingsViewModel(
    private val chatStore: com.bdersolutions.daybreak.data.assistant.ChatStore,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val repository: TimetableRepository,
    private val scheduler: SyncScheduler,
    private val lessonNotificationManager: LessonNotificationManager,
    private val rawLogStore: RawLogStore,
    private val widgetUpdater: WidgetUpdater,
    private val schoolData: SchoolDataRepository,
    private val absenceLessonHistory: AbsenceLessonHistory,
    private val updateManager: UpdateManager,
    private val updateScheduler: UpdateScheduler,
    private val haptics: Haptics,
    /** What the plan allows; the extras below check it in one place ([Entitlement]). */
    val access: StateFlow<Access>,
    private val launcherNameSwitcher: LauncherNameSwitcher,
    private val reminderScheduler: ReminderScheduler,
    private val reminderNotifier: ReminderNotificationManager,
    private val serverNotices: ServerNotices,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val session: StateFlow<DaybreakSession?> = sessionStore.session
    val syncStatus: StateFlow<TimetableSyncStatus> = repository.status

    /** The student's cached timetable, for previews such as the widget's. */
    val timetableCache: StateFlow<com.bdersolutions.daybreak.data.cache.TimetableCache> = repository.cache

    private val _events = MutableSharedFlow<Throwable>(extraBufferCapacity = 4)
    val events: SharedFlow<Throwable> = _events

    /** Emits when a manual sync finished without errors. */
    private val _syncSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val syncSucceeded: SharedFlow<Unit> = _syncSucceeded

    /**
     * The subjects in the student's own timetable, from the cached weeks. The school's master
     * data also lists subjects of other classes, which are not shown here. Sorted, so the list
     * is stable.
     */
    val subjects: StateFlow<List<String>> = repository.cache.map { cache ->
        cache.weeks.values
            .flatMap { week -> week.lessons.map { it.subject } }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Updates ----

    /** False when the app was built without an update server address; the screen is hidden then. */
    val updatesConfigured: Boolean get() = updateManager.isConfigured

    /** False while the developer has the in-app update feature switched off. */
    val updatesEnabled: Boolean get() = serverNotices.switches.updates
    val updateState: StateFlow<UpdateState> = updateManager.state
    val updateReleases: StateFlow<List<ReleaseInfo>> = updateManager.releases
    val currentVersionName: String get() = updateManager.currentVersionName
    val currentVersionCode: Long get() = updateManager.currentVersionCode
    val lastUpdateCheckAt: Long get() = updateManager.lastCheckAt

    fun canInstallUpdates(): Boolean = updateManager.installer.canInstall()
    fun installPermissionIntent() = updateManager.installer.permissionSettingsIntent()

    fun checkForUpdates() {
        // Nothing is checked while the developer has the update feature switched off.
        if (!updatesEnabled) return
        viewModelScope.launch { updateManager.check() }
    }

    fun refreshUpdateReleases() {
        viewModelScope.launch { updateManager.refreshReleases() }
    }

    fun downloadUpdate() = updateManager.downloadInBackground()
    fun cancelUpdateDownload() = updateManager.cancelDownload()
    fun installUpdate() = updateManager.install()

    fun setAutoCheckUpdates(enabled: Boolean) {
        settingsRepository.setAutoCheckUpdates(enabled)
        updateScheduler.schedule(enabled, settingsRepository.current().updateCheckHours)
    }

    /** How often the background check looks for a new version; the plan changes at once. */
    fun setUpdateCheckHours(hours: Int) {
        settingsRepository.setUpdateCheckHours(hours)
        updateScheduler.schedule(settingsRepository.current().autoCheckUpdates, settingsRepository.current().updateCheckHours)
    }

    fun updateLessonColors(transform: (LessonColorSettings) -> LessonColorSettings) {
        settingsRepository.updateLessonColors(transform)
    }

    private val _debugUnlocked = MutableStateFlow(false)

    /**
     * Whether the debug log is shown. It stays hidden until the "System" theme option has been
     * tapped [DEBUG_UNLOCK_TAPS] times. The state lives in this ViewModel, so it is hidden again
     * after the app restarts.
     */
    val debugUnlocked: StateFlow<Boolean> = _debugUnlocked.asStateFlow()
    private var systemThemeTaps = 0

    /** Counts a tap and returns how many have been made, or 0 when the log is unlocked already. */
    fun onSystemThemeTapped(): Int {
        if (_debugUnlocked.value) return 0
        systemThemeTaps++
        if (systemThemeTaps >= DEBUG_UNLOCK_TAPS) _debugUnlocked.value = true
        return systemThemeTaps
    }

    // ---- Haptics ----

    val hapticCapabilities: HapticCapabilities get() = haptics.capabilities
    fun systemTouchFeedbackOn(): Boolean = haptics.systemTouchFeedbackOn()

    /** Changes the haptic settings. The preset name follows the result: it stays, or becomes Custom. */
    fun editHaptics(transform: (HapticSettings) -> HapticSettings) {
        settingsRepository.updateHaptics { transform(it).normalized() }
    }

    fun applyHapticPreset(preset: HapticPreset) {
        settingsRepository.updateHaptics { it.withPreset(preset).normalized() }
    }

    /** Plays [event] as it would sound with [with], even when it is switched off, so sliders can be felt. */
    fun previewHaptic(event: HapticEvent, with: HapticSettings = settingsRepository.current().haptics) {
        haptics.preview(event, with)
    }

    fun setLanguage(language: AppLanguage) {
        settingsRepository.setLanguage(language)
        lessonNotificationManager.ensureChannel()
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(Locale.forLanguageTag(language.tag).toLanguageTag()),
        )
    }

    fun setSyncEnabled(enabled: Boolean) {
        settingsRepository.setSyncEnabled(enabled)
        scheduler.schedule(settingsRepository.current())
    }

    fun setSyncInterval(minutes: Int) {
        settingsRepository.setSyncInterval(minutes)
        scheduler.schedule(settingsRepository.current())
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settingsRepository.setNotificationsEnabled(enabled)
        lessonNotificationManager.ensureChannel()
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsRepository.setDynamicColor(enabled)
    }

    fun setAmoledBlack(enabled: Boolean) {
        settingsRepository.setAmoledBlack(enabled)
    }

    fun setAccent(accent: AccentColor) {
        settingsRepository.setAccent(accent)
    }

    fun setMotionStyle(style: MotionStyle) {
        settingsRepository.setMotionStyle(style)
    }

    fun setAbsenceUnit(unit: AbsenceUnit) {
        settingsRepository.setAbsenceUnit(unit)
    }

    fun setShowNowNext(show: Boolean) = settingsRepository.setShowNowNext(show)

    /** Changes the widget settings; the widget is redrawn by [com.bdersolutions.daybreak.AppContainer]. */
    fun updateWidget(transform: (WidgetSettings) -> WidgetSettings) = settingsRepository.updateWidget(transform)

    fun updateDisplay(transform: (TimetableDisplay) -> TimetableDisplay) = settingsRepository.updateDisplay(transform)

    fun setMorningSummaryEnabled(enabled: Boolean) {
        settingsRepository.setMorningSummaryEnabled(enabled)
        reminderScheduler.schedule()
    }

    fun setMorningLeadMinutes(minutes: Int) {
        settingsRepository.setMorningLeadMinutes(minutes)
        reminderScheduler.reschedule()
    }

    fun setExamRemindersEnabled(enabled: Boolean) {
        settingsRepository.setExamRemindersEnabled(enabled)
        reminderScheduler.schedule()
    }

    /** Sends a sample of a reminder now, so the person can see what it looks like. */
    fun sendTestMorningSummary() = viewModelScope.launch { reminderNotifier.sendMorningPreview() }

    fun sendTestEveningReminder() = viewModelScope.launch { reminderNotifier.sendEveningPreview() }

    /** Plus extra: the name under the app icon. Ignored while the plan does not include it. */
    fun setAppNamePreset(preset: AppNamePreset) {
        if (!access.value.allows(Entitlement.APP_NAME)) return
        // Only stored here. The launcher entry itself is switched by RestartActivity once the app has
        // exited (and by AppContainer.start() on the next start): switching it while the app is open
        // makes some phones close it without restarting.
        settingsRepository.setAppNamePreset(preset)
    }

    /** Plus extra: the picture of the app icon. Ignored while the plan does not include it. */
    fun setAppIconStyle(style: AppIconStyle) {
        if (!access.value.allows(Entitlement.APP_ICON)) return
        settingsRepository.setAppIconStyle(style)
    }

    /**
     * Plus extra: the timetable of this week and the next [weekCount] - 1 as iCalendar text.
     * Missing weeks are fetched first (fresh ones are not asked again). Fails when nothing could be
     * loaded; a week that is missing among others is simply left out.
     */
    suspend fun buildCalendar(weekCount: Int, labels: IcsExport.Labels): String {
        check(access.value.allows(Entitlement.CALENDAR_EXPORT)) { "Calendar export is part of Plus." }
        val first = repository.weekStartFor(LocalDate.now())
        val weeks = (0 until weekCount).map { first.plusWeeks(it.toLong()) }
        var failure: Throwable? = null
        for (week in weeks) {
            failure = repository.ensureWeek(week).error ?: failure
        }
        val cache = repository.cache.value
        val lessons = weeks.flatMap { cache.weeks[it]?.lessons.orEmpty() }
        if (lessons.isEmpty()) throw failure ?: IllegalStateException("No timetable loaded.")
        val last = first.plusWeeks(weekCount.toLong()).minusDays(1)
        return IcsExport.build(lessons, cache.exams, labels, first, last)
    }

    /** Plus extra: the title shown in the app. Ignored while the plan does not include it. */
    fun setCustomAppName(name: String) {
        if (!access.value.allows(Entitlement.APP_NAME)) return
        settingsRepository.setCustomAppName(name)
    }

    fun setStartView(view: StartView) {
        settingsRepository.setStartView(view)
    }

    fun setSubjectColorMode(mode: SubjectColorMode) {
        settingsRepository.setSubjectColorMode(mode)
    }

    fun setTextScale(scale: TextScale) {
        settingsRepository.setTextScale(scale)
    }

    fun syncNow() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val thisWeek = repository.weekStartFor(today)
            val result = runCatching {
                repository.refreshWeeks(listOf(thisWeek, thisWeek.plusWeeks(1)))
            }.getOrElse {
                _events.tryEmit(it)
                return@launch
            }
            if (result.successCount > 0) {
                widgetUpdater.update()
            }
            val error = result.error
            if (error == null) {
                _syncSucceeded.tryEmit(Unit)
            } else {
                _events.tryEmit(error)
            }
        }
    }

    fun logout() {
        scheduler.cancelAll()
        reminderScheduler.cancelAll()
        sessionStore.clear()
        settingsRepository.clearAbsenceCategories()
        settingsRepository.clearFavoriteTargets()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clear()
                absenceLessonHistory.clear()
                schoolData.clear()
                rawLogStore.clear()
                chatStore.clear()
                widgetUpdater.update()
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(
                chatStore = container.chatStore,
                settingsRepository = container.settingsRepository,
                sessionStore = container.sessionStore,
                repository = container.timetableRepository,
                scheduler = container.syncScheduler,
                lessonNotificationManager = container.lessonNotificationManager,
                rawLogStore = container.rawLogStore,
                widgetUpdater = container.widgetUpdater,
                schoolData = container.schoolData,
                absenceLessonHistory = container.absenceLessonHistory,
                updateManager = container.updateManager,
                updateScheduler = container.updateScheduler,
                haptics = container.haptics,
                access = container.access,
                launcherNameSwitcher = container.launcherNameSwitcher,
                reminderScheduler = container.reminderScheduler,
                reminderNotifier = container.reminderNotifier,
                serverNotices = container.serverNotices,
            ) as T
        }
    }
}
