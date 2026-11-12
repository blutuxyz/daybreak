package com.bdersolutions.daybreak.data.account

import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The account endpoints of the update server over HTTP (docs/v2-spec.md). Every call carries the install id
 * (`X-Daybreak-Install`); calls of a signed-in account also carry the token. Nothing here is logged: not the
 * token, not the restore code. A network or server problem becomes an [AccountError], never an exception.
 */
class HttpAccountApi(
    baseUrl: String,
    private val installId: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
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
        call("DELETE", "/v1/account/webuntis/${java.net.URLEncoder.encode(slotId, "UTF-8")}", token, null, AccountParser::state)

    override suspend fun acceptTrial(token: String): ApiResult<AccountState> =
        call("POST", "/v1/account/trial/accept", token, JsonObject(), AccountParser::state)

    override suspend fun checkout(token: String, plan: Plan): ApiResult<String> =
        call("POST", "/v1/billing/checkout", token, JsonObject().apply { addProperty("plan", plan.wire) }, AccountParser::url)

    override suspend fun portal(token: String): ApiResult<String> =
        call("POST", "/v1/billing/portal", token, JsonObject(), AccountParser::url)

    private suspend fun <T> call(
        method: String,
        path: String,
        token: String?,
        body: JsonObject?,
        parse: (String?) -> T?,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val builder = try {
            Request.Builder().url(base + path)
        } catch (_: IllegalArgumentException) {
            return@withContext ApiResult.Fail(AccountError.UNAVAILABLE)
        }
        builder.header("Accept", "application/json").header("X-Daybreak-Install", installId())
        if (token != null) builder.header("Authorization", "Bearer $token")
        builder.method(method, body?.toString()?.toRequestBody(JSON))
        try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string()
                if (!response.isSuccessful) return@use ApiResult.Fail(AccountParser.errorFor(response.code, text))
                parse(text)?.let { ApiResult.Ok(it) } ?: ApiResult.Fail(AccountError.UNAVAILABLE)
            }
        } catch (_: IOException) {
            ApiResult.Fail(AccountError.OFFLINE)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
