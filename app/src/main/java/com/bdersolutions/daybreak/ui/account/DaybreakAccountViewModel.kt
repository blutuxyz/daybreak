package com.bdersolutions.daybreak.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.account.AccountSnapshot
import com.bdersolutions.daybreak.data.account.ApiResult
import com.bdersolutions.daybreak.data.account.CreatedAccount
import com.bdersolutions.daybreak.data.session.DaybreakSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the account screens show besides the account itself. */
data class AccountFlowState(
    val busy: Boolean = false,
    val error: AccountError? = null,
    /** Set right after an account was made: the restore code is shown once, prominently. */
    val created: CreatedAccount? = null,
    /** A bound WebUntis account that another Daybreak account has bound as well. */
    val sharedWarning: Boolean = false,
    /** The slot id of the current WebUntis login, if it is bound from this phone. */
    val boundSlotId: String? = null,
    /** The Pro trial was just started. */
    val trialStarted: Boolean = false,
)

/**
 * The logic behind the migration page, the account page and the trial offer. All calls go through
 * [com.bdersolutions.daybreak.data.account.AccountManager]; this class only holds what the screens
 * show while a call runs (busy, error, the code of a new account).
 */
class DaybreakAccountViewModel(private val container: AppContainer) : ViewModel() {
    private val manager = container.accountManager
    private val prefs = container.accountPrefs

    val snapshot: StateFlow<AccountSnapshot> = manager.snapshot

    private val _state = MutableStateFlow(AccountFlowState())
    val state: StateFlow<AccountFlowState> = _state.asStateFlow()

    /** An install used before 2.0 with a Ko-fi payment that has not been carried over yet. */
    val legacySupporter: Boolean get() = manager.legacy().paid

    fun refresh() {
        viewModelScope.launch { manager.refresh() }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun call(block: suspend () -> AccountError?) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val error = block()
            _state.value = _state.value.copy(busy = false, error = error)
        }
    }

    fun create() = call {
        when (val result = manager.create(container.assistantIdentity.installId)) {
            is ApiResult.Ok -> {
                _state.value = _state.value.copy(created = result.value)
                null
            }

            is ApiResult.Fail -> result.error
        }
    }

    fun restore(code: String) = call {
        when (val result = manager.restore(code)) {
            is ApiResult.Ok -> null
            is ApiResult.Fail -> result.error
        }
    }

    /** The restore code was shown and the person went on. */
    fun acknowledgeCreated() {
        _state.value = _state.value.copy(created = null)
    }

    fun loadBound(session: DaybreakSession?) {
        val remembered = session?.let { prefs.slotFor(keyOf(it)) }
        // A slot this phone remembers only counts while the server still lists it: after the account was
        // replaced, or the slot was removed from another device, the phone must offer to connect again.
        val known = snapshot.value.state?.webuntis?.any { it.id == remembered } == true
        if (remembered != null && !known && snapshot.value.state != null && session != null) prefs.setSlot(keyOf(session), null)
        _state.value = _state.value.copy(boundSlotId = remembered?.takeIf { known || snapshot.value.state == null })
    }

    fun bind(session: DaybreakSession, label: String) = call {
        // The account is named by what the school's own token says (school id and user id), not by the
        // login name or the e-mail, which can change. Only a hash of it is kept on the server.
        val identity = try {
            val untis = container.sessionStore.timetableSession() ?: return@call AccountError.UNAVAILABLE
            container.untisApi.fetchIdentity(untis)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: com.bdersolutions.daybreak.untis.UntisNetworkException) {
            return@call AccountError.OFFLINE
        } catch (_: Exception) {
            return@call AccountError.UNAVAILABLE
        }
        when (val result = manager.bind(session.server, identity.tenantId, identity.userId, label.trim().ifBlank { session.schoolName })) {
            is ApiResult.Ok -> {
                prefs.setSlot(keyOf(session), result.value.slot.id)
                _state.value = _state.value.copy(
                    boundSlotId = result.value.slot.id,
                    sharedWarning = result.value.sharedWithOthers,
                )
                null
            }

            is ApiResult.Fail -> result.error
        }
    }

    fun unbind(slotId: String, session: DaybreakSession?) = call {
        when (val result = manager.unbind(slotId)) {
            is ApiResult.Ok -> {
                if (session != null && prefs.slotFor(keyOf(session)) == slotId) prefs.setSlot(keyOf(session), null)
                _state.value = _state.value.copy(
                    boundSlotId = _state.value.boundSlotId?.takeIf { it != slotId },
                    sharedWarning = false,
                )
                null
            }

            is ApiResult.Fail -> result.error
        }
    }

    fun acceptTrial() = call {
        when (val result = manager.acceptTrial()) {
            is ApiResult.Ok -> {
                _state.value = _state.value.copy(trialStarted = true)
                null
            }

            is ApiResult.Fail -> result.error
        }
    }

    fun signOut() {
        viewModelScope.launch {
            manager.signOut()
            _state.value = AccountFlowState()
        }
    }

    private fun keyOf(session: DaybreakSession) = "${session.server}|${session.school}|${session.studentId}"

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DaybreakAccountViewModel::class.java))
            return DaybreakAccountViewModel(container) as T
        }
    }
}
