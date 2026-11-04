package com.bdersolutions.daybreak.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.debug.RawLogStore
import com.bdersolutions.daybreak.data.notifications.LessonNotificationManager
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.repository.TimetableSyncStatus
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.AppSettings
import com.bdersolutions.daybreak.data.settings.MotionStyle
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.data.sync.SyncScheduler
import com.bdersolutions.daybreak.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val repository: TimetableRepository,
    private val scheduler: SyncScheduler,
    private val lessonNotificationManager: LessonNotificationManager,
    private val rawLogStore: RawLogStore,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val session: StateFlow<DaybreakSession?> = sessionStore.session
    val syncStatus: StateFlow<TimetableSyncStatus> = repository.status

    private val _events = MutableSharedFlow<Throwable>(extraBufferCapacity = 4)
    val events: SharedFlow<Throwable> = _events

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

    fun setAccent(accent: AccentColor) {
        settingsRepository.setAccent(accent)
    }

    fun setMotionStyle(style: MotionStyle) {
        settingsRepository.setMotionStyle(style)
    }

    fun setStartView(view: StartView) {
        settingsRepository.setStartView(view)
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
            result.error?.let { _events.tryEmit(it) }
        }
    }

    fun logout() {
        scheduler.cancelAll()
        sessionStore.clear()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clear()
                rawLogStore.clear()
                widgetUpdater.update()
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(
                settingsRepository = container.settingsRepository,
                sessionStore = container.sessionStore,
                repository = container.timetableRepository,
                scheduler = container.syncScheduler,
                lessonNotificationManager = container.lessonNotificationManager,
                rawLogStore = container.rawLogStore,
                widgetUpdater = container.widgetUpdater,
            ) as T
        }
    }
}
