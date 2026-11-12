package com.bdersolutions.daybreak.data.account

import android.content.Context
import com.bdersolutions.daybreak.data.security.SecretVault
import com.google.gson.Gson

/**
 * Keeps the Daybreak account on this phone: the sign-in token and the restore code encrypted with the same
 * Keystore key as the WebUntis mobile key (see [SecretVault]), the last answer of the server as plain JSON (it
 * holds no secrets). Kept apart from the WebUntis session: logging out of WebUntis does not sign out of the account.
 *
 * It also reads what a 1.x install left about Ko-fi (the `daybreak_support` preferences) and deletes it once the
 * account has taken it over ([clearLegacy]).
 */
class AndroidAccountStorage(context: Context, private val vault: SecretVault) : AccountStorage {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    override fun credentials(): AccountCredentials? {
        val id = preferences.getString(KEY_ID, null) ?: return null
        val token = vault.decrypt(preferences.getString(KEY_TOKEN, null)) ?: return null
        return AccountCredentials(id, token, vault.decrypt(preferences.getString(KEY_RESTORE, null)))
    }

    @Synchronized
    override fun saveCredentials(credentials: AccountCredentials?) {
        val edit = preferences.edit()
        if (credentials == null) {
            edit.remove(KEY_ID).remove(KEY_TOKEN).remove(KEY_RESTORE)
        } else {
            val token = vault.encrypt(credentials.token)
            // Without the Keystore there is nothing safe to keep: the device stays signed out.
            if (token == null) {
                edit.remove(KEY_ID).remove(KEY_TOKEN).remove(KEY_RESTORE).commit()
                return
            }
            edit.putString(KEY_ID, credentials.accountId).putString(KEY_TOKEN, token)
            credentials.restoreCode?.let { edit.putString(KEY_RESTORE, vault.encrypt(it)) } ?: edit.remove(KEY_RESTORE)
        }
        edit.commit()
    }

    @Synchronized
    override fun cachedState(): AccountState? =
        preferences.getString(KEY_STATE, null)?.let { runCatching { gson.fromJson(it, StoredState::class.java) }.getOrNull() }?.toState()

    @Synchronized
    override fun saveState(state: AccountState?) {
        val edit = preferences.edit()
        if (state == null) edit.remove(KEY_STATE) else edit.putString(KEY_STATE, gson.toJson(StoredState.of(state)))
        edit.apply()
    }

    override fun legacy(): LegacySupport {
        val old = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        return LegacySupport(
            paid = old.getBoolean("paid", false),
            unlockCode = old.getString("unlock_code", null)?.takeIf { it.isNotBlank() },
        )
    }

    override fun clearLegacy() {
        appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // Plain classes for Gson (the shared models have no no-arg constructors).
    private class StoredSlot(val id: String = "", val label: String = "")

    private class StoredState(
        val accountId: String = "",
        val plan: String = "free",
        val planUntil: String? = null,
        val grandfathered: Boolean = false,
        val slots: List<StoredSlot> = emptyList(),
        val trialOffered: Boolean = false,
        val trialAccepted: Boolean = false,
        val trialRunning: Boolean = false,
        val trialUntil: String? = null,
    ) {
        fun toState() = if (accountId.isBlank()) null else AccountState(
            accountId = accountId,
            plan = Plan.parse(plan),
            planUntil = planUntil,
            grandfathered = grandfathered,
            webuntis = slots.filter { it.id.isNotBlank() }.map { WebUntisSlot(it.id, it.label) },
            proTrial = ProTrial(trialOffered, trialAccepted, trialRunning, trialUntil),
        )

        companion object {
            fun of(state: AccountState) = StoredState(
                state.accountId, state.plan.wire, state.planUntil, state.grandfathered,
                state.webuntis.map { StoredSlot(it.id, it.label) },
                state.proTrial.offered, state.proTrial.accepted, state.proTrial.running, state.proTrial.until,
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "daybreak_account"
        const val LEGACY_PREFS = "daybreak_support"
        const val KEY_ID = "account_id"
        const val KEY_TOKEN = "token"
        const val KEY_RESTORE = "restore_code"
        const val KEY_STATE = "state"
    }
}

/**
 * What the app remembers around the account flow, in plain values: which WebUntis logins this phone has linked
 * to a Daybreak account (linking is required, see `DaybreakApp`) and when the person dismissed the Pro-trial
 * popup (the reminder at the top of More runs two weeks from then, see [TrialOfferPolicy]).
 */
class AccountPrefs(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether the WebUntis login behind [key] was linked to a Daybreak account from this phone. The server
     * keeps only a hash, so it cannot tell which login a slot belongs to; this phone remembers it.
     */
    fun isLinked(key: String): Boolean = preferences.getBoolean(KEY_LINKED + key, false)

    fun setLinked(key: String, linked: Boolean) {
        preferences.edit().apply {
            if (linked) putBoolean(KEY_LINKED + key, true) else remove(KEY_LINKED + key)
        }.apply()
    }

    var trialDismissedAt: Long?
        get() = preferences.getLong(KEY_TRIAL_DISMISSED, 0L).takeIf { it > 0L }
        set(value) = preferences.edit().apply { if (value == null) remove(KEY_TRIAL_DISMISSED) else putLong(KEY_TRIAL_DISMISSED, value) }.apply()

    /**
     * The server never says which WebUntis login a slot belongs to (it keeps only a hash), so this phone
     * remembers which slot it bound for which login ([key] is server, school and student id).
     */
    /** Whether the person has confirmed that the restore code of [accountId] is saved somewhere safe. */
    fun codeConfirmed(accountId: String): Boolean = preferences.getBoolean(KEY_CODE + accountId, false)

    fun confirmCode(accountId: String) {
        preferences.edit().putBoolean(KEY_CODE + accountId, true).apply()
    }

    fun slotFor(key: String): String? = preferences.getString(KEY_SLOT + key, null)

    fun setSlot(key: String, slotId: String?) {
        preferences.edit().apply { if (slotId == null) remove(KEY_SLOT + key) else putString(KEY_SLOT + key, slotId) }.apply()
    }

    private companion object {
        const val KEY_SLOT = "slot:"
        const val KEY_CODE = "code_confirmed:"
        const val KEY_LINKED = "linked:"
        const val PREFS_NAME = "daybreak_account_flow"
        const val KEY_TRIAL_DISMISSED = "trial_dismissed_at"
    }
}
