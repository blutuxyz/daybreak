package com.bdersolutions.daybreak.data.account

/** The account API of a build without an update server address: every call fails as unavailable. */
object OfflineAccountApi : AccountApi {
    private val none = ApiResult.Fail(AccountError.UNAVAILABLE)

    override suspend fun create(legacyInstallId: String?, legacyUnlockCode: String?) = none
    override suspend fun restore(restoreCode: String) = none
    override suspend fun account(token: String) = none
    override suspend fun bind(token: String, server: String, school: String, studentId: Long, label: String) = none
    override suspend fun unbind(token: String, slotId: String) = none
    override suspend fun acceptTrial(token: String) = none
    override suspend fun checkout(token: String, plan: Plan) = none
    override suspend fun portal(token: String) = none
    override suspend fun deleteAccount(token: String) = none
}
