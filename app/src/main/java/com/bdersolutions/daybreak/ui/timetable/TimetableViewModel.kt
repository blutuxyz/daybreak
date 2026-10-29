package com.bdersolutions.daybreak.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.repository.RefreshResult
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.repository.TimetableSyncStatus
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.sync.SyncScheduler
import com.bdersolutions.daybreak.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class TimetableViewModel(
    private val repository: TimetableRepository,
    sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    val cache: StateFlow<com.bdersolutions.daybreak.data.cache.TimetableCache> = repository.cache
    val status: StateFlow<TimetableSyncStatus> = repository.status
    val session: StateFlow<DaybreakSession?> = sessionStore.session

    private val _events = MutableSharedFlow<Throwable>(extraBufferCapacity = 4)
    val events: SharedFlow<Throwable> = _events

    private val requestsInFlight = mutableSetOf<LocalDate>()

    init {
        viewModelScope.launch {
            syncScheduler.schedule(settingsRepository.current())
        }
        ensureWeek(LocalDate.now(), force = false)
    }

    fun ensureWeek(date: LocalDate, force: Boolean = false) {
        val week = repository.weekStartFor(date)
        if (!force && week in requestsInFlight) return
        if (force) requestsInFlight += week
        viewModelScope.launch {
            if (!force) requestsInFlight += week
            try {
                val result = repository.ensureWeek(date, force = force)
                if (result.successCount > 0) {
                    widgetUpdater.update()
                }
                if (force && result.error != null) {
                    _events.tryEmit(result.error)
                }
            } catch (error: Throwable) {
                if (force) _events.tryEmit(error)
            } finally {
                requestsInFlight -= week
            }
        }
    }

    fun refresh(date: LocalDate) {
        viewModelScope.launch {
            val result: RefreshResult? = runCatching {
                repository.refreshWeeks(
                    listOf(
                        repository.weekStartFor(date),
                        repository.weekStartFor(date).plusWeeks(1),
                    ),
                )
            }.getOrElse {
                _events.tryEmit(it)
                null
            }
            if (result?.successCount ?: 0 > 0) {
                widgetUpdater.update()
            }
            result?.error?.let { _events.tryEmit(it) }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TimetableViewModel::class.java))
            return TimetableViewModel(
                repository = container.timetableRepository,
                sessionStore = container.sessionStore,
                settingsRepository = container.settingsRepository,
                syncScheduler = container.syncScheduler,
                widgetUpdater = container.widgetUpdater,
            ) as T
        }
    }
}
