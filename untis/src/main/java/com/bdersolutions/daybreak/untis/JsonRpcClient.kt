package com.bdersolutions.daybreak.untis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class JsonRpcClient(
    private val rawSink: RawResponseSink,
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun call(
        server: String,
        school: String,
        method: String,
        params: JsonArray,
        recordRaw: Boolean = true,
    ): JsonElement = withContext(Dispatchers.IO) {
        val base = UntisEndpoints.normalizeServer(server)
        val url = base.toHttpUrl()
            .newBuilder()
            .addPathSegments("WebUntis/jsonrpc_intern.do")
            .addQueryParameter("school", school)
            .build()

        val payload = JsonObject().apply {
            addProperty("id", method)
            addProperty("jsonrpc", "2.0")
            addProperty("method", method)
            add("params", params)
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "UntisMobileAndroid")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw UntisNetworkException("Could not reach the WebUntis server.", error)
        }

        response.use { received ->
            val body = try {
                received.body?.string().orEmpty()
            } catch (error: IOException) {
                throw UntisNetworkException("The server response could not be read.", error)
            }

            if (recordRaw) {
                rawSink.onResponse(
                    method = method,
                    receivedAt = System.currentTimeMillis(),
                    body = body.take(MAX_RAW_BODY_CHARS),
                )
            }

            if (!received.isSuccessful) {
                val apiError = runCatching { parseError(body) }.getOrNull()
                if (received.code == 401 || received.code == 403) {
                    throw UntisAuthException(
                        apiError?.second ?: "WebUntis rejected the login.",
                        apiError?.first,
                    )
                }
                throw UntisProtocolException(
                    apiError?.second ?: "WebUntis returned HTTP ${received.code}.",
                    apiError?.first,
                )
            }

            val root = try {
                JsonParser.parseString(body)
            } catch (error: Exception) {
                throw UntisParseException("WebUntis returned an invalid response.", error)
            }
            val json = root as? JsonObject
                ?: throw UntisParseException("WebUntis returned an unexpected response.")

            val error = json.objectOrNull("error")
            if (error != null) {
                val code = error.intOrNull("code")
                val message = error.stringOrNull("message", "data") ?: "WebUntis returned an error."
                val requiresSecondFactor = code == CODE_SECOND_FACTOR ||
                    message.contains("second factor", ignoreCase = true) ||
                    message.contains("token", ignoreCase = true)
                throw UntisAuthException(
                    message = message,
                    apiCode = code,
                    requiresSecondFactor = requiresSecondFactor,
                )
            }

            val result = json.get("result")
                ?: throw UntisParseException("WebUntis returned no result.")
            if (result.isJsonNull) {
                throw UntisNoDataException("WebUntis returned no data.")
            }
            result
        }
    }

    /**
     * GET on the school's WebUntis REST API with a bearer [token] (from `getAuthToken`). The body goes
     * to the debug log under [label]; the token never does.
     */
    suspend fun getRest(
        server: String,
        path: String,
        query: Map<String, String>,
        token: String,
        label: String,
    ): JsonElement = withContext(Dispatchers.IO) {
        val url = UntisEndpoints.normalizeServer(server).toHttpUrl().newBuilder().apply {
            addPathSegments(path.trim('/'))
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "UntisMobileAndroid")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw UntisNetworkException("Could not reach the WebUntis server.", error)
        }
        response.use { received ->
            val text = try {
                received.body?.string().orEmpty()
            } catch (error: IOException) {
                throw UntisNetworkException("The server response could not be read.", error)
            }
            rawSink.onResponse(label, System.currentTimeMillis(), text.take(MAX_RAW_BODY_CHARS))
            if (received.code == 401) throw UntisAuthException("WebUntis rejected the token.")
            if (!received.isSuccessful) {
                val message = runCatching {
                    JsonParser.parseString(text).asJsonObject.stringOrNull("errorMessage")
                }.getOrNull()
                throw UntisProtocolException(message ?: "WebUntis returned HTTP ${received.code}.")
            }
            runCatching { JsonParser.parseString(text) }.getOrNull()
                ?: throw UntisParseException("WebUntis returned an invalid response.")
        }
    }

    /**
     * POST of a multipart form with one JSON part, the way the WebUntis web app sends a message: the
     * JSON travels as the part [partName]. Uses a bearer [token]. The answer goes to the debug log
     * under [label]; the message text and the token never do.
     */
    suspend fun postJsonPart(
        server: String,
        path: String,
        token: String,
        label: String,
        partName: String,
        json: JsonElement,
    ): JsonElement = withContext(Dispatchers.IO) {
        val url = UntisEndpoints.normalizeServer(server).toHttpUrl().newBuilder()
            .addPathSegments(path.trim('/'))
            .build()
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(partName, "blob", json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "UntisMobileAndroid")
            .header("Authorization", "Bearer $token")
            .post(form)
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw UntisNetworkException("Could not reach the WebUntis server.", error)
        }
        response.use { received ->
            val text = try {
                received.body?.string().orEmpty()
            } catch (error: IOException) {
                throw UntisNetworkException("The server response could not be read.", error)
            }
            rawSink.onResponse(label, System.currentTimeMillis(), text.take(MAX_RAW_BODY_CHARS))
            if (received.code == 401) throw UntisAuthException("WebUntis rejected the token.")
            if (!received.isSuccessful) {
                val message = runCatching {
                    JsonParser.parseString(text).asJsonObject.stringOrNull("errorMessage")
                }.getOrNull()
                throw UntisProtocolException(message?.takeIf { it.isNotBlank() } ?: "WebUntis returned HTTP ${received.code}.")
            }
            runCatching { JsonParser.parseString(text) }.getOrNull() ?: JsonObject()
        }
    }

    private fun parseError(body: String): Pair<Int?, String>? {
        val root = runCatching { JsonParser.parseString(body) as JsonObject }.getOrNull() ?: return null
        val error = root.objectOrNull("error") ?: return null
        return error.intOrNull("code") to (error.stringOrNull("message") ?: "WebUntis error")
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RAW_BODY_CHARS = 250_000
        private const val CODE_SECOND_FACTOR = -8519

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
