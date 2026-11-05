package com.bdersolutions.daybreak.data.support

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What asking the server about a payment came to. */
enum class PaymentCheck {
    /** The server has a payment for the code. */
    PAID,

    /** The server answered, and has no payment for the code (yet). */
    NOT_FOUND,

    /** The server could not be reached or answered something unusable. */
    UNAVAILABLE,
}

/**
 * Asks the update server (update-server/) whether an unlock code was paid for. Only the code is sent;
 * the server never learns who the phone belongs to.
 */
class SupportApi(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val base = baseUrl.trim().trimEnd('/')

    suspend fun check(code: String): PaymentCheck = withContext(Dispatchers.IO) {
        if (!SupportCode.isValid(code)) return@withContext PaymentCheck.UNAVAILABLE
        val url = try {
            "$base/v1/support/status".toHttpUrl().newBuilder().addQueryParameter("code", code).build()
        } catch (_: IllegalArgumentException) {
            return@withContext PaymentCheck.UNAVAILABLE
        }
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use PaymentCheck.UNAVAILABLE
                parse(response.body?.string())
            }
        } catch (_: IOException) {
            PaymentCheck.UNAVAILABLE
        }
    }

    companion object {
        /** Reads `{"paid": true|false}`; anything else is [PaymentCheck.UNAVAILABLE]. */
        fun parse(body: String?): PaymentCheck {
            if (body.isNullOrBlank()) return PaymentCheck.UNAVAILABLE
            return try {
                val paid = JsonParser.parseString(body).asJsonObject.get("paid")
                when {
                    paid == null || !paid.isJsonPrimitive || !paid.asJsonPrimitive.isBoolean -> PaymentCheck.UNAVAILABLE
                    paid.asBoolean -> PaymentCheck.PAID
                    else -> PaymentCheck.NOT_FOUND
                }
            } catch (_: RuntimeException) {
                PaymentCheck.UNAVAILABLE
            }
        }
    }
}
