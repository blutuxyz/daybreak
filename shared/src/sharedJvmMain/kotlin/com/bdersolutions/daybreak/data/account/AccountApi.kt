package com.bdersolutions.daybreak.data.account

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One WebUntis account bound to the Daybreak account: the server's id and the short label the person chose. */
data class WebUntisSlot(val id: String, val label: String)

/** The Pro trial offered to old Ko-fi supporters (`proTrial` of `GET /v1/account`). */
data class ProTrial(
    val offered: Boolean = false,
    val accepted: Boolean = false,
    val running: Boolean = false,
    val until: String? = null,
)

/** What `GET /v1/account` says about the account. No hashes and no tokens: the server never sends them. */
data class AccountState(
    val accountId: String,
    val plan: Plan = Plan.FREE,
    val planUntil: String? = null,
    val grandfathered: Boolean = false,
    val webuntis: List<WebUntisSlot> = emptyList(),
    val proTrial: ProTrial = ProTrial(),
)

/** Why a call to the account endpoints did not work. Each has its own text in the app. */
enum class AccountError {
    /** No connection. */
    OFFLINE,

    /** The token is not (or no longer) accepted: sign in again with the restore code. */
    UNAUTHORIZED,

    /** The restore code does not belong to an account. */
    UNKNOWN_CODE,

    /** Three WebUntis accounts are bound already. */
    LIMIT,

    /** This WebUntis account is bound to the account already. */
    DUPLICATE,

    /** The Pro trial is not offered to this account, or was taken. */
    NOT_OFFERED,

    /** The Stripe customer portal needs a subscription first. */
    NO_SUBSCRIPTION,

    /** Too many tries in a short time. */
    RATE_LIMITED,

    /** The server did not accept the input. */
    BAD_REQUEST,

    /** Anything else: the server is down or answered something unusable. */
    UNAVAILABLE,
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Fail(val error: AccountError) : ApiResult<Nothing>
}

/** `POST /v1/account`: the new account, its restore code (shown once) and this device's token. */
data class CreatedAccount(
    val state: AccountState,
    val restoreCode: String,
    val token: String,
    /** What the server made of the old Ko-fi code: `none`, `granted`, `claimed` or `unpaid`. */
    val legacy: String,
)

/** `POST /v1/account/restore`: this device is signed in. */
data class SignedIn(val state: AccountState, val token: String)

/** `POST /v1/account/webuntis`: the new slot and the account afterwards. */
data class Bound(val slot: WebUntisSlot, val state: AccountState, val sharedWithOthers: Boolean)

/**
 * The account endpoints of the update server, as the apps use them (docs/v2-spec.md). The HTTP version is
 * `HttpAccountApi` in the Android app; tests use a fake. Every call can fail with an [AccountError]; none
 * throws for a network or server problem.
 */
interface AccountApi {
    suspend fun create(legacyInstallId: String?, legacyUnlockCode: String?): ApiResult<CreatedAccount>

    suspend fun restore(restoreCode: String): ApiResult<SignedIn>

    suspend fun account(token: String): ApiResult<AccountState>

    suspend fun bind(token: String, server: String, school: String, studentId: Long, label: String): ApiResult<Bound>

    suspend fun unbind(token: String, slotId: String): ApiResult<AccountState>

    suspend fun acceptTrial(token: String): ApiResult<AccountState>

    /** `POST /v1/billing/checkout`: the Stripe Checkout address for [plan] (PLUS or PRO), to open in the browser. */
    suspend fun checkout(token: String, plan: Plan): ApiResult<String>

    /** `POST /v1/billing/portal`: the Stripe page where a subscriber changes the card or cancels. */
    suspend fun portal(token: String): ApiResult<String>
}

/** Reads the answers of the account endpoints. Tolerant: a missing field gets its free default. */
object AccountParser {
    fun state(body: String?): AccountState? = obj(body)?.let(::stateFrom)

    fun created(body: String?): CreatedAccount? {
        val json = obj(body) ?: return null
        val state = stateFrom(json) ?: return null
        val code = json.string("restoreCode") ?: return null
        val token = json.string("token") ?: return null
        return CreatedAccount(state, code, token, json.string("legacy") ?: "none")
    }

    fun signedIn(body: String?): SignedIn? {
        val json = obj(body) ?: return null
        val state = stateFrom(json) ?: return null
        return SignedIn(state, json.string("token") ?: return null)
    }

    fun bound(body: String?): Bound? {
        val json = obj(body) ?: return null
        val binding = json.get("binding") as? JsonObject ?: return null
        val slot = slotFrom(binding) ?: return null
        val state = (json.get("account") as? JsonObject)?.let(::stateFrom) ?: return null
        return Bound(slot, state, json.get("sharedWithOthers")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false)
    }

    /** Reads `{"url": "https://…"}` (checkout and portal); only https addresses are accepted. */
    fun url(body: String?): String? =
        obj(body)?.string("url")?.takeIf { it.startsWith("https://") }

    /** The error code of an answer (`{"error": "limit"}`), or null. */
    fun errorCode(body: String?): String? = obj(body)?.string("error")

    /** Maps an HTTP status and the body's error code to an [AccountError]. */
    fun errorFor(status: Int, body: String?): AccountError {
        val code = errorCode(body)
        return when {
            status == 401 -> AccountError.UNAUTHORIZED
            status == 429 -> AccountError.RATE_LIMITED
            status == 404 && code == "unknown_code" -> AccountError.UNKNOWN_CODE
            status == 409 && code == "limit" -> AccountError.LIMIT
            status == 409 && code == "duplicate" -> AccountError.DUPLICATE
            status == 409 && code == "no_subscription" -> AccountError.NO_SUBSCRIPTION
            status == 409 && (code == "not_offered" || code == "already_accepted") -> AccountError.NOT_OFFERED
            status == 400 -> AccountError.BAD_REQUEST
            else -> AccountError.UNAVAILABLE
        }
    }

    /**
     * The restore code as the server keeps it (`DA-XXXX-XXXX-XXXX`), from what a person typed or pasted
     * (any case, with or without dashes and spaces). Null when it cannot be a code.
     */
    fun normalizeRestoreCode(text: String?): String? {
        val compact = text.orEmpty().uppercase().filterNot { it == '-' || it.isWhitespace() }
        if (!Regex("^DA[2-9A-HJKMNP-Z]{12}$").matches(compact)) return null
        val body = compact.drop(2)
        return "DA-${body.take(4)}-${body.substring(4, 8)}-${body.drop(8)}"
    }

    private fun stateFrom(json: JsonObject): AccountState? {
        val id = json.string("accountId") ?: return null
        val trial = json.get("proTrial") as? JsonObject
        return AccountState(
            accountId = id,
            plan = Plan.parse(json.string("plan")),
            planUntil = json.string("planUntil"),
            grandfathered = json.bool("grandfathered"),
            webuntis = (json.get("webuntis") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::slotFrom) }.orEmpty(),
            proTrial = ProTrial(
                offered = trial?.bool("offered") ?: false,
                accepted = trial?.bool("accepted") ?: false,
                running = trial?.bool("running") ?: false,
                until = trial?.string("until"),
            ),
        )
    }

    private fun slotFrom(json: JsonObject): WebUntisSlot? {
        val id = json.string("id") ?: return null
        return WebUntisSlot(id, json.string("label") ?: "WebUntis")
    }

    private fun obj(body: String?): JsonObject? {
        if (body.isNullOrBlank()) return null
        return runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(name: String): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false
}
