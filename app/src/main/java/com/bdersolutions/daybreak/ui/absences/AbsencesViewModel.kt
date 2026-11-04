package com.bdersolutions.daybreak.ui.absences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.untis.UntisAbsence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface AbsencesUiState {
    data object Loading : AbsencesUiState

    data class Loaded(
        val absences: List<UntisAbsence>,
        val reasons: Map<Long, String>,
    ) : AbsencesUiState

    data class Failed(val error: Throwable) : AbsencesUiState
}

/** 1 August of the school year that contains [today] (German school years start in summer). */
fun schoolYearStartFor(today: LocalDate): LocalDate =
    if (today.monthValue >= 8) LocalDate.of(today.year, 8, 1) else LocalDate.of(today.year - 1, 8, 1)

class AbsencesViewModel(
    private val schoolData: SchoolDataRepository,
) : ViewModel() {
    val schoolYearStart: LocalDate = schoolYearStartFor(LocalDate.now())

    private val _state = MutableStateFlow<AbsencesUiState>(AbsencesUiState.Loading)
    val state: StateFlow<AbsencesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AbsencesUiState.Loading
            _state.value = try {
                val directory = schoolData.loadDirectory()
                val absences = schoolData.absences(schoolYearStart, LocalDate.now())
                AbsencesUiState.Loaded(absences, directory.absenceReasons)
            } catch (error: Exception) {
                AbsencesUiState.Failed(error)
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AbsencesViewModel::class.java))
            return AbsencesViewModel(container.schoolData) as T
        }
    }
}
