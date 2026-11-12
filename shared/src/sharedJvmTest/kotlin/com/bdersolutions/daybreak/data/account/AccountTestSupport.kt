package com.bdersolutions.daybreak.data.account

/** In-memory [AccountStorage] for tests. */
class FakeAccountStorage(
    var stored: AccountCredentials? = null,
    var state: AccountState? = null,
    var legacyData: LegacySupport = LegacySupport(),
) : AccountStorage {
    override fun credentials() = stored
    override fun saveCredentials(credentials: AccountCredentials?) { stored = credentials }
    override fun cachedState() = state
    override fun saveState(state: AccountState?) { this.state = state }
    override fun legacy() = legacyData
    override fun clearLegacy() { legacyData = LegacySupport() }
}

/**
 * A fake of the update server's account endpoints, enough to test the app's logic against the contract
 * of docs/v2-spec.md: one account, a token that can be revoked, three WebUntis slots.
 */
class FakeAccountApi : AccountApi {
    var offline = false
    var plan = Plan.FREE
    var planUntil: String? = null
    var grandfathered = false
    var trialOffered = false
    var validToken = "token-1"
    val slots = mutableListOf<WebUntisSlot>()
    var restoreCode = "DA-K7M2-9QXP-4HTR"
    var legacyAnswer = "none"
    var createdWithLegacyCode: String? = null
    var createdWithInstallId: String? = null
    var calls = 0
    private var counter = 1

    fun view() = AccountState(
        accountId = "acc1",
        plan = plan,
        planUntil = planUntil,
        grandfathered = grandfathered,
        webuntis = slots.toList(),
        proTrial = ProTrial(offered = trialOffered),
    )

    private fun <T> guarded(token: String?, block: () -> ApiResult<T>): ApiResult<T> {
        calls++
        if (offline) return ApiResult.Fail(AccountError.OFFLINE)
        if (token != null && token != validToken) return ApiResult.Fail(AccountError.UNAUTHORIZED)
        return block()
    }

    override suspend fun create(legacyInstallId: String?, legacyUnlockCode: String?) = guarded<CreatedAccount>(null) {
        createdWithInstallId = legacyInstallId
        createdWithLegacyCode = legacyUnlockCode
        if (legacyAnswer == "granted") {
            grandfathered = true
            trialOffered = true
        }
        ApiResult.Ok(CreatedAccount(view(), restoreCode, validToken, legacyAnswer))
    }

    override suspend fun restore(restoreCode: String) = guarded<SignedIn>(null) {
        if (restoreCode != this.restoreCode) {
            ApiResult.Fail(AccountError.UNKNOWN_CODE)
        } else {
            validToken = "token-${++counter}"
            ApiResult.Ok(SignedIn(view(), validToken))
        }
    }

    override suspend fun account(token: String) = guarded(token) { ApiResult.Ok(view()) }

    override suspend fun bind(token: String, server: String, school: String, studentId: Long, label: String) = guarded<Bound>(token) {
        when {
            slots.size >= 3 -> ApiResult.Fail(AccountError.LIMIT)
            slots.any { it.label == label } -> ApiResult.Fail(AccountError.DUPLICATE)
            else -> {
                val slot = WebUntisSlot("slot${slots.size + 1}", label)
                slots += slot
                ApiResult.Ok(Bound(slot, view(), sharedWithOthers = false))
            }
        }
    }

    override suspend fun unbind(token: String, slotId: String) = guarded(token) {
        if (slots.removeAll { it.id == slotId }) ApiResult.Ok(view()) else ApiResult.Fail(AccountError.BAD_REQUEST)
    }

    var hasSubscription = false

    override suspend fun checkout(token: String, plan: Plan) = guarded(token) {
        if (plan == Plan.FREE) ApiResult.Fail(AccountError.BAD_REQUEST) else ApiResult.Ok("https://pay.example/${plan.wire}")
    }

    override suspend fun portal(token: String) = guarded(token) {
        if (hasSubscription) ApiResult.Ok("https://pay.example/portal") else ApiResult.Fail(AccountError.NO_SUBSCRIPTION)
    }

    var deleted = false

    override suspend fun deleteAccount(token: String) = guarded(token) {
        deleted = true
        ApiResult.Ok(Unit)
    }

    override suspend fun acceptTrial(token: String) = guarded(token) {
        if (!trialOffered) {
            ApiResult.Fail(AccountError.NOT_OFFERED)
        } else {
            trialOffered = false
            plan = Plan.PRO
            ApiResult.Ok(view())
        }
    }
}
