package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.support.PaymentCheck
import com.bdersolutions.daybreak.data.support.PaymentStatusParser
import com.bdersolutions.daybreak.data.support.SupportCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Asks the update server whether an unlock code was paid for, like the Android `SupportApi`. Only the
 * code is sent; the server never learns who the computer belongs to.
 */
class DesktopSupportApi(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    suspend fun check(code: String): PaymentCheck = withContext(Dispatchers.IO) {
        if (!SupportCode.isValid(code)) return@withContext PaymentCheck.UNAVAILABLE
        try {
            val uri = URI.create("$base/v1/support/status?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8))
            val request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(25))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) PaymentCheck.UNAVAILABLE else PaymentStatusParser.parse(response.body())
        } catch (_: IOException) {
            PaymentCheck.UNAVAILABLE
        } catch (_: IllegalArgumentException) {
            PaymentCheck.UNAVAILABLE
        } catch (_: InterruptedException) {
            PaymentCheck.UNAVAILABLE
        }
    }
}
