package com.bdersolutions.daybreak.ui.absences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.repository.AbsenceLessonHistory
import com.bdersolutions.daybreak.data.repository.LessonHistory
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.untis.UntisAbsence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

sealed interface AbsencesUiState {
    data object Loading : AbsencesUiState

    data class Loaded(
        val absences: List<UntisAbsence>,
        val reasons: Map<Long, String>,
    ) : AbsencesUiState

    data class Failed(val error: Throwable) : AbsencesUiState
}

/**
 * The absence categories and how much of each limit is used. [usage] is empty until the lessons of
 * the whole school year are known; [loading] covers that wait.
 */
data class CategoriesUiState(
    val categories: List<AbsenceCategory> = emptyList(),
    /** Subjects the student can pick for a category. */
    val subjects: List<String> = emptyList(),
    /** Missed lessons by category id. */
    val usage: Map<String, CategoryUsage> = emptyMap(),
    /**
     * Per category id: the lessons each absence covers, by absence id. Only absences that cover at
     * least one lesson of the subject appear, so this also says which absences to list.
     */
    val lessonsByAbsence: Map<String, Map<Long, Int>> = emptyMap(),
    /** Ids of categories whose subject is not in the student's lessons any more. */
    val missingSubject: Set<String> = emptySet(),
    val loading: Boolean = false,
    /** Weeks of lessons that could not be loaded, so the counts may be too low. */
    val failedWeeks: Int = 0,
)

/** 1 August of the school year that contains [today] (German school years start in summer). */
fun schoolYearStartFor(today: LocalDate): LocalDate =
    if (today.monthValue >= 8) LocalDate.of(today.year, 8, 1) else LocalDate.of(today.year - 1, 8, 1)

class AbsencesViewModel(
    private val schoolData: SchoolDataRepository,
    private val settingsRepository: SettingsRepository,
    private val timetable: TimetableRepository,
    private val lessonHistory: AbsenceLessonHistory,
) : ViewModel() {
    val schoolYearStart: LocalDate = schoolYearStartFor(LocalDate.now())

    private val _state = MutableStateFlow<AbsencesUiState>(AbsencesUiState.Loading)
    val state: StateFlow<AbsencesUiState> = _state.asStateFlow()

    private val absences = MutableStateFlow<List<UntisAbsence>?>(null)
    private val history = MutableStateFlow<LessonHistory?>(null)

    private val _windows = MutableStateFlow<SchoolDayWindows?>(null)

    /**
     * First and last held lesson of each school day with an absence, so a whole-day absence the
     * teacher wrote as 07:55 to 17:30 counts only the hours of the lessons. Null until loaded; the
     * screen then counts the recorded time.
     */
    val windows: StateFlow<SchoolDayWindows?> = _windows.asStateFlow()
    private var historyJob: Job? = null

    val categories: StateFlow<CategoriesUiState> = combine(
        settingsRepository.settings.map { it.absenceCategories }.distinctUntilChanged(),
        absences,
        history,
        timetable.cache,
    ) { categories, absenceList, lessonSet, cache ->
        val lessons = lessonSet?.lessons.orEmpty() + cache.weeks.values.flatMap { it.lessons }
        val subjects = studentSubjects(lessons)
        val ready = absenceList != null && lessonSet != null
        CategoriesUiState(
            categories = categories,
            subjects = subjects,
            usage = if (ready) {
                categories.associate { it.id to countMissedLessons(absenceList!!, lessonSet!!.lessons, it.subject) }
            } else {
                emptyMap()
            },
            lessonsByAbsence = if (ready) {
                categories.associate { it.id to missedLessonsByAbsence(absenceList!!, lessonSet!!.lessons, it.subject) }
            } else {
                emptyMap()
            },
            missingSubject = if (ready) {
                categories.filter { category ->
                    subjects.none { it.equals(category.subject, ignoreCase = true) }
                }.map { it.id }.toSet()
            } else {
                emptySet()
            },
            loading = categories.isNotEmpty() && !ready,
            failedWeeks = lessonSet?.failedWeeks ?: 0,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CategoriesUiState())

    init {
        load()
    }

    private val _refreshing = MutableStateFlow(false)

    /** True while a pull to refresh reloads absences that are already shown. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshErrors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)

    /** A refresh that failed while absences were already shown; they stay on screen. */
    val refreshErrors: SharedFlow<Throwable> = _refreshErrors.asSharedFlow()

    private var loadJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true) return
        // Absences that are already on screen stay there while they reload, instead of giving way
        // to a spinner; a failed reload then keeps them and only reports the error.
        val shown = _state.value as? AbsencesUiState.Loaded
        loadJob = viewModelScope.launch {
            if (shown == null) _state.value = AbsencesUiState.Loading else _refreshing.value = true
            try {
                val directory = schoolData.loadDirectory()
                val loaded = schoolData.absences(schoolYearStart, LocalDate.now())
                absences.value = loaded
                loadWindows(loaded)
                loadHistory(force = true)
                _state.value = AbsencesUiState.Loaded(loaded, directory.absenceReasons)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (shown == null) _state.value = AbsencesUiState.Failed(error) else _refreshErrors.tryEmit(error)
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun loadWindows(loaded: List<UntisAbsence>) {
        viewModelScope.launch {
            _windows.value = try {
                val weeks = absenceWeekStarts(loaded)
                if (weeks.isEmpty()) {
                    null
                } else {
                    SchoolDayWindows.from(lessonHistory.lessonsOfWeeks(weeks).lessons)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            }
        }
    }

    /** Creates a category, or changes the one with [id]. */
    fun saveCategory(id: String?, name: String, subject: String, maxLessons: Int, maxPercent: Int) {
        val percent = maxPercent.takeIf { it in 1..100 } ?: 0
        if (subject.isBlank() || (percent == 0 && maxLessons <= 0)) return
        // A percent limit leaves the lesson count unused, and the other way round.
        val lessons = if (percent > 0) 0 else maxLessons
        val cleanName = name.trim().ifEmpty { subject.trim() }
        settingsRepository.updateAbsenceCategories { list ->
            if (id == null) {
                list + AbsenceCategory(UUID.randomUUID().toString(), cleanName, subject.trim(), lessons, percent)
            } else {
                list.map {
                    if (it.id == id) {
                        it.copy(name = cleanName, subject = subject.trim(), maxLessons = lessons, maxPercent = percent)
                    } else {
                        it
                    }
                }
            }
        }
        loadHistory(force = false)
    }

    fun deleteCategory(id: String) {
        settingsRepository.updateAbsenceCategories { list -> list.filterNot { it.id == id } }
    }

    /**
     * The subject picker lists the subjects of the whole school year, which needs the same lessons
     * as the counting. Without a category yet they are not loaded, so opening the picker does it.
     */
    fun loadSubjects() {
        loadHistory(force = false, evenWithoutCategories = true)
    }

    /** Saves the order the user dragged the categories into. */
    fun reorderCategories(ids: List<String>) {
        settingsRepository.updateAbsenceCategories { list -> list.reorderedBy(ids) }
    }

    /**
     * Loads the lessons of the school year, but only when a category needs them: without
     * categories the screen makes no extra requests.
     */
    private fun loadHistory(force: Boolean, evenWithoutCategories: Boolean = false) {
        if (!evenWithoutCategories && settingsRepository.current().absenceCategories.isEmpty()) return
        if (!force && history.value != null) return
        if (historyJob?.isActive == true) {
            if (!force) return
            historyJob?.cancel()
        }
        historyJob = viewModelScope.launch {
            history.value = try {
                lessonHistory.lessons(schoolYearStart, LocalDate.now())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Fall back to the cached weeks and say that the counts may be too low.
                LessonHistory(
                    lessons = timetable.cache.value.weeks.values.flatMap { it.lessons },
                    failedWeeks = 1,
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AbsencesViewModel::class.java))
            return AbsencesViewModel(
                schoolData = container.schoolData,
                settingsRepository = container.settingsRepository,
                timetable = container.timetableRepository,
                lessonHistory = container.absenceLessonHistory,
            ) as T
        }
    }
}
