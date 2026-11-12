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

/** The steps of the required "create and link" page (see `LinkAccountScreen`). */
enum class LinkStage {
    /** A new Daybreak account is being made. */
    CREATING,

    /** The WebUntis login is being connected to the Daybreak account. */
    LINKING,

    /** Both are done: the restore code is shown. */
    LINKED,
}

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
    /** Where the required create-and-link page is; null while it waits for the person to start it. */
    val linkStage: LinkStage? = null,
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
            is ApiResult.Ok -> {
                // Signing in with the code shows that the person has it.
                prefs.confirmCode(result.value.state.accountId)
                null
            }
            is ApiResult.Fail -> result.error
        }
    }

    /** Whether the restore code of the signed-in account is confirmed as saved. */
    fun codeConfirmed(accountId: String): Boolean = prefs.codeConfirmed(accountId)

    /** The person confirmed that the code is saved (the reminder on the account page goes away). */
    fun confirmCodeSaved() {
        manager.snapshot.value.credentials?.accountId?.let { prefs.confirmCode(it) }
    }

    /** The restore code was shown and the person went on. */
    fun acknowledgeCreated() {
        _state.value = _state.value.copy(created = null, linkStage = null)
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
        link(session, label, remember = true)
    }

    /**
     * The required page: make a Daybreak account if this phone has none, then connect the WebUntis login
     * that is signed in. Both steps are one call, so the screen can show "creating" and then "linking".
     */
    fun createAndLink(session: DaybreakSession) = call {
        if (!manager.snapshot.value.signedIn) {
            _state.value = _state.value.copy(linkStage = LinkStage.CREATING)
            when (val result = manager.create(container.assistantIdentity.installId)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(created = result.value)
                is ApiResult.Fail -> {
                    _state.value = _state.value.copy(linkStage = null)
                    return@call result.error
                }
            }
        }
        _state.value = _state.value.copy(linkStage = LinkStage.LINKING)
        link(session, session.schoolName.take(40), remember = false).also { error ->
            _state.value = _state.value.copy(linkStage = if (error == null) LinkStage.LINKED else LinkStage.LINKING)
        }
    }

    /** Signs in with a restore code on the required page, then connects the WebUntis login as well. */
    fun restoreAndLink(session: DaybreakSession, code: String) = call {
        // The code may belong to another account than the one this phone remembered.
        prefs.setLinked(keyOf(session), false)
        _state.value = _state.value.copy(linkStage = LinkStage.LINKING)
        when (val result = manager.restore(code)) {
            is ApiResult.Ok -> Unit
            is ApiResult.Fail -> {
                _state.value = _state.value.copy(linkStage = null)
                return@call result.error
            }
        }
        link(session, session.schoolName.take(40), remember = false).also { error ->
            _state.value = _state.value.copy(linkStage = if (error == null) LinkStage.LINKED else LinkStage.LINKING)
        }
    }

    /**
     * The restore code was seen and the person went on: from now on this WebUntis login counts as linked, so
     * the required page stays away. It is only remembered here, not when the link call returns, so the code
     * page is still shown first.
     */
    fun markLinked(session: DaybreakSession) {
        prefs.setLinked(keyOf(session), true)
    }

    /**
     * Connects the signed-in WebUntis login to the Daybreak account. A login the account already holds counts
     * as linked: the server says `duplicate`, which is exactly what this page wants. [remember] stores the
     * marker for the required page; it stays false there until the restore code was shown.
     */
    private suspend fun link(session: DaybreakSession, label: String, remember: Boolean): AccountError? {
        // The account is named by what the school's own token says (school id and user id), not by the
        // login name or the e-mail, which can change. Only a hash of it is kept on the server.
        val identity = try {
            val untis = container.sessionStore.timetableSession() ?: return AccountError.UNAVAILABLE
            container.untisApi.fetchIdentity(untis)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: com.bdersolutions.daybreak.untis.UntisNetworkException) {
            return AccountError.OFFLINE
        } catch (_: Exception) {
            return AccountError.UNAVAILABLE
        }
        val cleanLabel = label.trim().ifBlank { session.schoolName }
        return when (val result = manager.bind(session.server, identity.tenantId, identity.userId, cleanLabel)) {
            is ApiResult.Ok -> {
                prefs.setSlot(keyOf(session), result.value.slot.id)
                if (remember) prefs.setLinked(keyOf(session), true)
                _state.value = _state.value.copy(
                    boundSlotId = result.value.slot.id,
                    sharedWarning = result.value.sharedWithOthers,
                )
                null
            }

            is ApiResult.Fail -> {
                if (result.error != AccountError.DUPLICATE) return result.error
                // Already on the account: find its slot by the label the server kept, if it still matches.
                val known = snapshot.value.state?.webuntis
                    ?.firstOrNull { it.label.equals(cleanLabel, ignoreCase = true) }
                if (known != null) prefs.setSlot(keyOf(session), known.id)
                if (remember) prefs.setLinked(keyOf(session), true)
                _state.value = _state.value.copy(boundSlotId = known?.id ?: _state.value.boundSlotId, sharedWarning = false)
                null
            }
        }
    }

    fun unbind(slotId: String, session: DaybreakSession?) = call {
        when (val result = manager.unbind(slotId)) {
            is ApiResult.Ok -> {
                if (session != null && prefs.slotFor(keyOf(session)) == slotId) {
                    prefs.setSlot(keyOf(session), null)
                    // Without a slot the login is no longer linked, so the required page comes back.
                    prefs.setLinked(keyOf(session), false)
                }
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

    /** Deletes the account on the server. On success the device is signed out; on failure the account stays. */
    fun deleteAccount() = call {
        when (val result = manager.deleteAccount()) {
            is ApiResult.Ok -> null
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
