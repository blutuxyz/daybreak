package com.bdersolutions.daybreak.data.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a device keeps to stay signed in to the Daybreak account. */
data class AccountCredentials(
    val accountId: String,
    /** The sign-in token of this device (`Authorization: Bearer`). */
    val token: String,
    /** Shown on the account page; null if this device was signed in before the code was kept. */
    val restoreCode: String?,
)

/** What a 1.x install knew about Ko-fi: carried over once, when the account is made. */
data class LegacySupport(val paid: Boolean = false, val unlockCode: String? = null)

/** Where the account lives on a device. The Android version encrypts the token and the restore code. */
interface AccountStorage {
    fun credentials(): AccountCredentials?
    fun saveCredentials(credentials: AccountCredentials?)
    fun cachedState(): AccountState?
    fun saveState(state: AccountState?)
    fun legacy(): LegacySupport
    fun clearLegacy()
}

/** The account as the screens see it. [access] is what the plan allows (see [Entitlement]). */
data class AccountSnapshot(
    val credentials: AccountCredentials?,
    val state: AccountState?,
    val legacyPaid: Boolean,
    val access: Access,
) {
    val signedIn: Boolean get() = credentials != null
}

/**
 * The one place that talks to the account endpoints and keeps the answer: create or restore an account,
 * refresh the plan, bind or unbind WebUntis accounts, take the Pro trial. [snapshot] is what the app
 * reads; it holds the cached state, so the plan also works offline (until its end date has passed).
 *
 * Pure apart from [AccountApi] and [AccountStorage], so it is unit-tested with fakes and shared with the desktop app.
 */
class AccountManager(
    private val api: AccountApi,
    private val storage: AccountStorage,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<AccountSnapshot> = _snapshot.asStateFlow()

    private fun read(): AccountSnapshot {
        val credentials = storage.credentials()
        val state = if (credentials != null) storage.cachedState() else null
        val legacy = storage.legacy()
        return AccountSnapshot(credentials, state, legacy.paid, Access.of(state, legacy.paid, clock()))
    }

    /** Recomputes the access (a plan may have ended while the app was open). */
    fun tick() {
        _snapshot.value = read()
    }

    /** The old Ko-fi data, if this install has any and no account took it over yet. */
    fun legacy(): LegacySupport = storage.legacy()

    /**
     * Makes a new account. [installId] is the id the assistant used until now. The old Ko-fi code travels
     * along; the server decides whether it was paid, and the answer ([CreatedAccount.legacy]) says so.
     */
    suspend fun create(installId: String?): ApiResult<CreatedAccount> = lock.withLock {
        val legacy = storage.legacy()
        val result = api.create(installId, legacy.unlockCode)
        if (result is ApiResult.Ok) {
            val made = result.value
            storage.saveCredentials(AccountCredentials(made.state.accountId, made.token, made.restoreCode))
            storage.saveState(made.state)
            // The server has decided about the old code; from now on the account holds that.
            storage.clearLegacy()
            _snapshot.value = read()
        }
        result
    }

    /** Signs this device in to an existing account with its restore code. */
    suspend fun restore(code: String): ApiResult<SignedIn> = lock.withLock {
        val normalized = AccountParser.normalizeRestoreCode(code) ?: return@withLock ApiResult.Fail(AccountError.UNKNOWN_CODE)
        val result = api.restore(normalized)
        if (result is ApiResult.Ok) {
            storage.saveCredentials(AccountCredentials(result.value.state.accountId, result.value.token, normalized))
            storage.saveState(result.value.state)
            _snapshot.value = read()
        }
        result
    }

    /**
     * Asks the server for the account again (plan, bound accounts, trial). A token the server no longer
     * knows is renewed from the kept restore code; if that fails too, the device is signed out.
     * Offline or a server error leaves the cached state as it was.
     */
    suspend fun refresh(): AccountError? = lock.withLock {
        val credentials = storage.credentials() ?: return@withLock null
        when (val result = api.account(credentials.token)) {
            is ApiResult.Ok -> {
                storage.saveState(result.value)
                _snapshot.value = read()
                null
            }

            is ApiResult.Fail -> {
                if (result.error != AccountError.UNAUTHORIZED) return@withLock result.error
                val code = credentials.restoreCode
                if (code != null) {
                    val again = api.restore(code)
                    if (again is ApiResult.Ok) {
                        storage.saveCredentials(credentials.copy(token = again.value.token))
                        storage.saveState(again.value.state)
                        _snapshot.value = read()
                        return@withLock null
                    }
                    if (again is ApiResult.Fail && again.error != AccountError.UNKNOWN_CODE) return@withLock again.error
                }
                signOutLocked()
                AccountError.UNAUTHORIZED
            }
        }
    }

    /** Binds a WebUntis account to the Daybreak account (at most three). */
    suspend fun bind(server: String, school: String, studentId: Long, label: String): ApiResult<Bound> = lock.withLock {
        val credentials = storage.credentials() ?: return@withLock ApiResult.Fail(AccountError.UNAUTHORIZED)
        val result = api.bind(credentials.token, server, school, studentId, label)
        if (result is ApiResult.Ok) accept(result.value.state)
        result
    }

    suspend fun unbind(slotId: String): ApiResult<AccountState> = lock.withLock {
        val credentials = storage.credentials() ?: return@withLock ApiResult.Fail(AccountError.UNAUTHORIZED)
        api.unbind(credentials.token, slotId).also { if (it is ApiResult.Ok) accept(it.value) }
    }

    /** Starts the three months of Pro for a grandfathered account. */
    suspend fun acceptTrial(): ApiResult<AccountState> = lock.withLock {
        val credentials = storage.credentials() ?: return@withLock ApiResult.Fail(AccountError.UNAUTHORIZED)
        api.acceptTrial(credentials.token).also { if (it is ApiResult.Ok) accept(it.value) }
    }

    /** The address of Stripe Checkout for [plan], to open in the browser. Signed-in accounts only. */
    suspend fun checkoutUrl(plan: Plan): ApiResult<String> {
        val credentials = storage.credentials() ?: return ApiResult.Fail(AccountError.UNAUTHORIZED)
        return api.checkout(credentials.token, plan)
    }

    /** The address of the Stripe page to change or cancel the subscription. */
    suspend fun portalUrl(): ApiResult<String> {
        val credentials = storage.credentials() ?: return ApiResult.Fail(AccountError.UNAUTHORIZED)
        return api.portal(credentials.token)
    }

    /** Signs this device out. The account and its plan stay on the server; the restore code signs in again. */
    suspend fun signOut() = lock.withLock { signOutLocked() }

    private fun signOutLocked() {
        storage.saveCredentials(null)
        storage.saveState(null)
        _snapshot.value = read()
    }

    private fun accept(state: AccountState) {
        storage.saveState(state)
        _snapshot.value = read()
    }
}

/**
 * When the grandfathered Pro trial is offered again. Old supporters see a popup on the first launch after
 * migrating; if they dismiss it, a reminder is pinned at the top of More for [BANNER_DAYS] days. Pure: the
 * app stores only [dismissedAtMillis].
 */
object TrialOfferPolicy {
    const val BANNER_DAYS = 14

    enum class Prompt { NONE, POPUP, BANNER }

    fun prompt(offered: Boolean, dismissedAtMillis: Long?, nowMillis: Long): Prompt = when {
        !offered -> Prompt.NONE
        dismissedAtMillis == null -> Prompt.POPUP
        nowMillis - dismissedAtMillis < BANNER_DAYS * DAY_MS -> Prompt.BANNER
        else -> Prompt.NONE
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
