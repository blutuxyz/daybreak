package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.account.AccountApi
import com.bdersolutions.daybreak.data.account.AccountCredentials
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.account.AccountParser
import com.bdersolutions.daybreak.data.account.AccountState
import com.bdersolutions.daybreak.data.account.AccountStorage
import com.bdersolutions.daybreak.data.account.ApiResult
import com.bdersolutions.daybreak.data.account.Bound
import com.bdersolutions.daybreak.data.account.CreatedAccount
import com.bdersolutions.daybreak.data.account.LegacySupport
import com.bdersolutions.daybreak.data.account.Plan
import com.bdersolutions.daybreak.data.account.ProTrial
import com.bdersolutions.daybreak.data.account.SignedIn
import com.bdersolutions.daybreak.data.account.WebUntisSlot
import com.bdersolutions.daybreak.platform.KeyValueStore
import com.bdersolutions.daybreak.platform.SecretStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

/**
 * The account endpoints of the update server over HTTP, like the Android `HttpAccountApi` (docs/v2-spec.md).
 * Every call carries the install id; a signed-in call also carries the token. Nothing is logged: not the
 * token and not the restore code. A network or server problem becomes an [AccountError], never an exception.
 */
class DesktopHttpAccountApi(
    baseUrl: String,
    private val installId: () -> String,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
) : AccountApi {
    private val base = baseUrl.trim().trimEnd('/')

    override suspend fun create(legacyInstallId: String?, legacyUnlockCode: String?): ApiResult<CreatedAccount> {
        val body = JsonObject().apply {
            if (!legacyInstallId.isNullOrBlank()) addProperty("legacyInstallId", legacyInstallId)
            if (!legacyUnlockCode.isNullOrBlank()) addProperty("legacyUnlockCode", legacyUnlockCode)
        }
        return call("POST", "/v1/account", null, body, AccountParser::created)
    }

    override suspend fun restore(restoreCode: String): ApiResult<SignedIn> =
        call("POST", "/v1/account/restore", null, JsonObject().apply { addProperty("restoreCode", restoreCode) }, AccountParser::signedIn)

    override suspend fun account(token: String): ApiResult<AccountState> =
        call("GET", "/v1/account", token, null, AccountParser::state)

    override suspend fun bind(token: String, server: String, school: String, studentId: Long, label: String): ApiResult<Bound> {
        val body = JsonObject().apply {
            addProperty("server", server)
            addProperty("school", school)
            addProperty("studentId", studentId)
            addProperty("label", label)
        }
        return call("POST", "/v1/account/webuntis", token, body, AccountParser::bound)
    }

    override suspend fun unbind(token: String, slotId: String): ApiResult<AccountState> =
        call("DELETE", "/v1/account/webuntis/" + URLEncoder.encode(slotId, StandardCharsets.UTF_8).replace("+", "%20"), token, null, AccountParser::state)

    override suspend fun acceptTrial(token: String): ApiResult<AccountState> =
        call("POST", "/v1/account/trial/accept", token, JsonObject(), AccountParser::state)

    override suspend fun checkout(token: String, plan: Plan): ApiResult<String> =
        call("POST", "/v1/billing/checkout", token, JsonObject().apply { addProperty("plan", plan.wire) }, AccountParser::url)

    override suspend fun portal(token: String): ApiResult<String> =
        call("POST", "/v1/billing/portal", token, JsonObject(), AccountParser::url)

    override suspend fun deleteAccount(token: String): ApiResult<Unit> =
        call("DELETE", "/v1/account", token, null, AccountParser::noContent)

    private suspend fun <T> call(
        method: String,
        path: String,
        token: String?,
        body: JsonObject?,
        parse: (String?) -> T?,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body.toString())
            val builder = HttpRequest.newBuilder(URI.create(base + path))
                .header("Accept", "application/json")
                .header("X-Daybreak-Install", installId())
                .timeout(Duration.ofSeconds(25))
            if (body != null) builder.header("Content-Type", "application/json; charset=utf-8")
            if (token != null) builder.header("Authorization", "Bearer $token")
            val response = client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                ApiResult.Fail(AccountParser.errorFor(response.statusCode(), response.body()))
            } else {
                parse(response.body())?.let { ApiResult.Ok(it) } ?: ApiResult.Fail(AccountError.UNAVAILABLE)
            }
        } catch (_: IOException) {
            ApiResult.Fail(AccountError.OFFLINE)
        } catch (_: InterruptedException) {
            ApiResult.Fail(AccountError.OFFLINE)
        } catch (_: IllegalArgumentException) {
            ApiResult.Fail(AccountError.UNAVAILABLE)
        }
    }
}

/** The account API of a build without an update server address: every call fails as unavailable. */
object DesktopOfflineAccountApi : AccountApi {
    private val none = ApiResult.Fail(AccountError.UNAVAILABLE)

    override suspend fun create(legacyInstallId: String?, legacyUnlockCode: String?) = none
    override suspend fun restore(restoreCode: String) = none
    override suspend fun account(token: String) = none
    override suspend fun bind(token: String, server: String, school: String, studentId: Long, label: String) = none
    override suspend fun unbind(token: String, slotId: String) = none
    override suspend fun acceptTrial(token: String) = none
    override suspend fun deleteAccount(token: String) = none
    override suspend fun checkout(token: String, plan: Plan) = none
    override suspend fun portal(token: String) = none
}

/**
 * Keeps the Daybreak account on this computer: the sign-in token and the restore code in [vault] (Windows
 * DPAPI, like the WebUntis key), the account id and the last answer of the server in [preferences] (no
 * secrets). It is kept apart from the WebUntis session: logging out of WebUntis does not sign out of the account.
 *
 * It also reads what a 1.x desktop left about Ko-fi (`support.properties`: `paid`, `unlock_code`) and clears it
 * once the account has taken it over.
 */
class DesktopAccountStorage(
    private val preferences: KeyValueStore,
    private val vault: SecretStore,
    private val legacyPreferences: KeyValueStore,
) : AccountStorage {
    private val gson = Gson()

    @Synchronized
    override fun credentials(): AccountCredentials? {
        val id = preferences.getString(KEY_ID, null) ?: return null
        val secret = vault.getStoredSecret()?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() } ?: return null
        val token = secret.get("token")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: return null
        val code = secret.get("restoreCode")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        return AccountCredentials(id, token, code)
    }

    @Synchronized
    override fun saveCredentials(credentials: AccountCredentials?) {
        if (credentials == null) {
            vault.clear()
            preferences.edit().remove(KEY_ID).commit()
            return
        }
        val secret = JsonObject().apply {
            addProperty("token", credentials.token)
            credentials.restoreCode?.let { addProperty("restoreCode", it) }
        }
        // Without a safe place for the secret the computer stays signed out.
        if (!vault.storeSecret(secret.toString())) {
            preferences.edit().remove(KEY_ID).commit()
            return
        }
        preferences.edit().putString(KEY_ID, credentials.accountId).commit()
    }

    @Synchronized
    override fun cachedState(): AccountState? =
        preferences.getString(KEY_STATE, null)
            ?.let { runCatching { gson.fromJson(it, StoredState::class.java) }.getOrNull() }
            ?.toState()

    @Synchronized
    override fun saveState(state: AccountState?) {
        val edit = preferences.edit()
        if (state == null) edit.remove(KEY_STATE) else edit.putString(KEY_STATE, gson.toJson(StoredState.of(state)))
        edit.commit()
    }

    override fun legacy(): LegacySupport = LegacySupport(
        paid = legacyPreferences.getBoolean(LEGACY_PAID, false),
        unlockCode = legacyPreferences.getString(LEGACY_CODE, null)?.takeIf { it.isNotBlank() },
    )

    override fun clearLegacy() {
        legacyPreferences.edit().clear().commit()
    }

    /** What a launch before 2.0 left behind, to tell an updated install from a fresh one. */
    fun hasLegacyTraces(): Boolean =
        legacyPreferences.getLong(LEGACY_FIRST_SEEN, Long.MIN_VALUE) != Long.MIN_VALUE ||
            legacyPreferences.getString(LEGACY_CODE, null) != null ||
            legacyPreferences.getBoolean(LEGACY_PAID, false)

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
        const val KEY_ID = "account_id"
        const val KEY_STATE = "state"
        // The keys the 1.x DesktopSupportStore wrote.
        const val LEGACY_PAID = "paid"
        const val LEGACY_CODE = "unlock_code"
        const val LEGACY_FIRST_SEEN = "first_seen_day"
    }
}

/**
 * What the desktop app remembers around the account flow: this computer's install id, whether it is an update
 * from 1.x (decided once), when the person put the migration off, when they dismissed the Pro-trial popup (the
 * reminder at the top of More then runs two weeks, see `TrialOfferPolicy`), and which WebUntis login is bound to
 * which slot (the server keeps only a hash, so it cannot say).
 */
class DesktopAccountPrefs(private val prefs: KeyValueStore) {
    /** A random id sent as `X-Daybreak-Install`; made on first use. */
    fun installId(): String {
        prefs.getString(KEY_INSTALL, null)?.takeIf { it.length >= 16 }?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL, id).commit()
        return id
    }

    /** Decided once: true for an install that has been used before 2.0. */
    fun isLegacyInstall(detect: () -> Boolean): Boolean {
        when (prefs.getInt(KEY_LEGACY, UNDECIDED)) {
            LEGACY -> return true
            FRESH -> return false
        }
        val value = detect()
        prefs.edit().putInt(KEY_LEGACY, if (value) LEGACY else FRESH).commit()
        return value
    }

    /** Epoch millis until which "Not now" holds the migration back; 0 when it was not put off. */
    var migrationDeferredUntil: Long
        get() = prefs.getLong(KEY_DEFERRED, 0L)
        set(value) {
            prefs.edit().putLong(KEY_DEFERRED, value).commit()
        }

    private val _trialDismissed = MutableStateFlow(prefs.getLong(KEY_TRIAL_DISMISSED, 0L).takeIf { it > 0L })

    /** When the Pro-trial popup was dismissed, as a flow so the reminder on More appears at once. */
    val trialDismissed: StateFlow<Long?> = _trialDismissed.asStateFlow()

    var trialDismissedAt: Long?
        get() = _trialDismissed.value
        set(value) {
            val edit = prefs.edit()
            if (value == null) edit.remove(KEY_TRIAL_DISMISSED) else edit.putLong(KEY_TRIAL_DISMISSED, value)
            edit.commit()
            _trialDismissed.value = value
        }

    fun slotFor(key: String): String? = prefs.getString(KEY_SLOT + key, null)

    fun setSlot(key: String, slotId: String?) {
        val edit = prefs.edit()
        if (slotId == null) edit.remove(KEY_SLOT + key) else edit.putString(KEY_SLOT + key, slotId)
        edit.commit()
    }

    private companion object {
        const val KEY_INSTALL = "install_id"
        const val KEY_LEGACY = "legacy_install"
        const val KEY_DEFERRED = "migration_deferred_until"
        const val KEY_TRIAL_DISMISSED = "trial_dismissed_at"
        const val KEY_SLOT = "slot:"
        const val UNDECIDED = 0
        const val LEGACY = 1
        const val FRESH = 2
    }
}
