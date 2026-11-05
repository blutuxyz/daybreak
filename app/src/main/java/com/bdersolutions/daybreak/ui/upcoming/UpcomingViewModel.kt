package com.bdersolutions.daybreak.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Homework and exams that are still ahead. Each list has its own error, so one failing request
 * does not hide the other list.
 */
data class UpcomingUiState(
    val loading: Boolean = true,
    val homework: List<UntisHomework> = emptyList(),
    val exams: List<UntisExam> = emptyList(),
    val homeworkError: Throwable? = null,
    val examsError: Throwable? = null,
)

class UpcomingViewModel(
    private val schoolData: SchoolDataRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UpcomingUiState())
    val state: StateFlow<UpcomingUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val today = LocalDate.now()
            val (homework, exams) = coroutineScope {
                val homeworkJob = async {
                    runCatching { schoolData.homework(today, today.plusDays(HOMEWORK_DAYS)) }
                }
                val examsJob = async {
                    runCatching { schoolData.exams(today, today.plusDays(EXAM_DAYS)) }
                }
                homeworkJob.await() to examsJob.await()
            }
            _state.value = UpcomingUiState(
                loading = false,
                homework = homework.getOrNull().orEmpty().filter { !it.due.isBefore(today) }.sortedWith(
                    compareBy({ it.due }, { it.subject.lowercase() }),
                ),
                exams = exams.getOrNull().orEmpty().filter { !it.date.isBefore(today) }.sortedWith(
                    compareBy({ it.date }, { it.start }),
                ),
                homeworkError = homework.exceptionOrNull(),
                examsError = exams.exceptionOrNull(),
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UpcomingViewModel::class.java))
            return UpcomingViewModel(container.schoolData) as T
        }
    }

    private companion object {
        const val HOMEWORK_DAYS = 90L
        const val EXAM_DAYS = 180L
    }
}
