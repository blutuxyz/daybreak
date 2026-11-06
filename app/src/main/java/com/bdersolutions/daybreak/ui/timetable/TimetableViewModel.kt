package com.bdersolutions.daybreak.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.data.repository.RefreshResult
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.repository.TimetableSyncStatus
import com.bdersolutions.daybreak.data.repository.TimetableTarget
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.AppSettings
import com.bdersolutions.daybreak.data.settings.FavoriteTarget
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.settings.reorderedByKeys
import com.bdersolutions.daybreak.data.settings.withToggled
import com.bdersolutions.daybreak.data.sync.SyncScheduler
import com.bdersolutions.daybreak.untis.UntisDirectory
import com.bdersolutions.daybreak.untis.UntisElement
import com.bdersolutions.daybreak.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class TimetableViewModel(
    private val repository: TimetableRepository,
    sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    private val widgetUpdater: WidgetUpdater,
    private val schoolData: SchoolDataRepository,
) : ViewModel() {
    val status: StateFlow<TimetableSyncStatus> = repository.status
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val session: StateFlow<DaybreakSession?> = sessionStore.session

    private val _target = MutableStateFlow<TimetableTarget>(TimetableTarget.Own)
    val target: StateFlow<TimetableTarget> = _target.asStateFlow()

    val directory: StateFlow<UntisDirectory?> = schoolData.directory

    /** Weeks of the selected class, teacher or room, keyed by [TimetableTarget.Element.cacheKey]. */
    private val elementWeeks = MutableStateFlow<Map<String, CachedWeek>>(emptyMap())
    private val elementRequests = mutableSetOf<String>()

    /** What the timetable shows: the student's cache, or the loaded weeks of the selected element. */
    val displayCache: StateFlow<TimetableCache> = combine(
        repository.cache,
        _target,
        elementWeeks,
    ) { own, target, weeks ->
        when (target) {
            TimetableTarget.Own -> own
            is TimetableTarget.Element -> TimetableCache(
                studentId = own.studentId,
                weeks = weeks
                    .filterKeys { it.startsWith(target.cachePrefix) }
                    .values
                    .associateBy { it.weekStart },
                lastSyncAt = own.lastSyncAt,
                // Holidays are the same for the whole school, so every view shows them.
                holidays = own.holidays,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repository.cache.value)

    private val _events = MutableSharedFlow<Throwable>(extraBufferCapacity = 4)
    val events: SharedFlow<Throwable> = _events

    /** Emits once a manual refresh has loaded every week it asked for. */
    private val _refreshSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshSucceeded: SharedFlow<Unit> = _refreshSucceeded

    private val requestsInFlight = mutableSetOf<LocalDate>()

    init {
        viewModelScope.launch {
            syncScheduler.schedule(settingsRepository.current())
        }
        ensureWeek(LocalDate.now(), force = false)
        viewModelScope.launch {
            runCatching { schoolData.loadDirectory() }
        }
    }

    fun setTarget(target: TimetableTarget) {
        _target.value = target
    }

    /** The classes, teachers and rooms the user pinned, in their own order. */
    val favorites: StateFlow<List<FavoriteTarget>> = settingsRepository.settings
        .map { it.favoriteTargets }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.current().favoriteTargets)

    fun toggleFavorite(element: UntisElement) {
        settingsRepository.updateFavoriteTargets { it.withToggled(element) }
    }

    fun removeFavorite(key: String) {
        settingsRepository.updateFavoriteTargets { list -> list.filterNot { it.key == key } }
    }

    fun reorderFavorites(keys: List<String>) {
        settingsRepository.updateFavoriteTargets { it.reorderedByKeys(keys) }
    }

    fun ensureWeek(date: LocalDate, force: Boolean = false) {
        when (val current = _target.value) {
            TimetableTarget.Own -> ensureOwnWeek(date, force)
            is TimetableTarget.Element -> ensureElementWeek(current, date, force)
        }
    }

    fun refresh(date: LocalDate) {
        when (val current = _target.value) {
            TimetableTarget.Own -> refreshOwn(date)
            is TimetableTarget.Element -> refreshElement(current, date)
        }
    }

    private fun ensureOwnWeek(date: LocalDate, force: Boolean) {
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
                val failure = result.error
                if (force && failure != null) {
                    _events.tryEmit(failure)
                }
            } catch (error: Throwable) {
                if (force) _events.tryEmit(error)
            } finally {
                requestsInFlight -= week
            }
        }
    }

    private fun ensureElementWeek(target: TimetableTarget.Element, date: LocalDate, force: Boolean) {
        val week = repository.weekStartFor(date)
        val key = target.cacheKey(week)
        if (!force && (key in elementWeeks.value || key in elementRequests)) return
        elementRequests += key
        viewModelScope.launch {
            loadElementWeek(target, date)
        }
    }

    private fun refreshElement(target: TimetableTarget.Element, date: LocalDate) {
        viewModelScope.launch {
            val loaded = listOf(date, date.plusWeeks(1)).map { loadElementWeek(target, it) }
            if (loaded.all { it }) _refreshSucceeded.tryEmit(Unit)
        }
    }

    /** Loads one week of an element timetable. Returns false when it failed. */
    private suspend fun loadElementWeek(target: TimetableTarget.Element, date: LocalDate): Boolean {
        val week = repository.weekStartFor(date)
        val key = target.cacheKey(week)
        elementRequests += key
        return try {
            val lessons = schoolData.elementWeek(target.element, week)
            elementWeeks.update { it + (key to CachedWeek(week, lessons, System.currentTimeMillis())) }
            true
        } catch (error: Throwable) {
            _events.tryEmit(error)
            false
        } finally {
            elementRequests -= key
        }
    }

    private fun refreshOwn(date: LocalDate) {
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
            if (result != null && result.error == null) {
                _refreshSucceeded.tryEmit(Unit)
            }
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
                schoolData = container.schoolData,
            ) as T
        }
    }
}
