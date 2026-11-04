package com.bdersolutions.daybreak.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.debug.RawLogEntry
import com.bdersolutions.daybreak.data.debug.RawLogStore
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.untis.ProbeResult
import com.bdersolutions.daybreak.untis.UntisApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

sealed interface ProbeUiState {
    data object Idle : ProbeUiState
    data object Running : ProbeUiState
    data object NoSession : ProbeUiState
    data class Done(val results: List<ProbeResult>) : ProbeUiState
    data class Failed(val message: String) : ProbeUiState
}

/** 1 August of the school year that contains [today] (German school years start in summer). */
private fun schoolYearStart(today: LocalDate): LocalDate =
    if (today.monthValue >= 8) LocalDate.of(today.year, 8, 1) else LocalDate.of(today.year - 1, 8, 1)

class DebugViewModel(
    private val rawLogStore: RawLogStore,
    private val untisApi: UntisApi,
    private val sessionStore: SessionStore,
) : ViewModel() {
    val entries: StateFlow<List<RawLogEntry>> = rawLogStore.entries

    private val _probe = MutableStateFlow<ProbeUiState>(ProbeUiState.Idle)
    val probe: StateFlow<ProbeUiState> = _probe.asStateFlow()

    fun clear() {
        rawLogStore.clear()
    }

    /**
     * Runs the diagnostic calls once. Their raw responses appear in [entries].
     */
    fun runProbe() {
        if (_probe.value == ProbeUiState.Running) return
        val session = sessionStore.timetableSession()
        if (session == null) {
            _probe.value = ProbeUiState.NoSession
            return
        }
        viewModelScope.launch {
            _probe.value = ProbeUiState.Running
            val today = LocalDate.now()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            _probe.value = try {
                val results = withContext(Dispatchers.IO) {
                    untisApi.runDiagnostics(
                        session = session,
                        timetableStart = weekStart,
                        timetableEnd = weekStart.plusDays(6),
                        absenceStart = schoolYearStart(today),
                        absenceEnd = today,
                    )
                }
                ProbeUiState.Done(results)
            } catch (error: Exception) {
                ProbeUiState.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DebugViewModel::class.java))
            return DebugViewModel(
                rawLogStore = container.rawLogStore,
                untisApi = container.untisApi,
                sessionStore = container.sessionStore,
            ) as T
        }
    }
}
