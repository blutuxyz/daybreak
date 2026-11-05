package com.bdersolutions.daybreak.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.untis.UntisClassService
import com.bdersolutions.daybreak.untis.UntisMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The school's notices of today. */
data class AccountUiState(
    val loading: Boolean = true,
    val messages: List<UntisMessage> = emptyList(),
    val error: Throwable? = null,
)

/** Class services need a password, so they are only loaded when the user asks for them. */
sealed interface ClassServicesState {
    data object Idle : ClassServicesState
    data object Loading : ClassServicesState
    data class Loaded(val services: List<UntisClassService>) : ClassServicesState
    data class Failed(val error: Throwable) : ClassServicesState
}

class AccountViewModel(
    private val schoolData: SchoolDataRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val _classServices = MutableStateFlow<ClassServicesState>(ClassServicesState.Idle)
    val classServices: StateFlow<ClassServicesState> = _classServices.asStateFlow()

    /** Whether the password is stored, so the class services load without asking. */
    val passwordSaved: StateFlow<Boolean> = schoolData.passwordSaved

    /** Loads the notices again. The page calls this each time it opens. */
    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = try {
                AccountUiState(loading = false, messages = schoolData.messages(LocalDate.now()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AccountUiState(loading = false, error = error)
            }
        }
    }

    /**
     * Shows the class services of this week and the next weeks. A typed [password] is used and,
     * once it worked, stored; with null the stored password is used.
     */
    fun loadClassServices(password: String?) {
        if (_classServices.value == ClassServicesState.Loading) return
        if (password == null && !passwordSaved.value) return
        _classServices.value = ClassServicesState.Loading
        viewModelScope.launch {
            val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            _classServices.value = try {
                ClassServicesState.Loaded(schoolData.classServices(password, monday, monday.plusDays(60)))
                    // A typed password is stored now, so the web notices can load with it too.
                    .also { if (password != null) load() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ClassServicesState.Failed(error)
            }
        }
    }

    /** Removes the stored password. The class services are asked for again next time. */
    fun forgetPassword() {
        schoolData.forgetPassword()
        _classServices.value = ClassServicesState.Idle
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AccountViewModel::class.java))
            return AccountViewModel(container.schoolData) as T
        }
    }
}
