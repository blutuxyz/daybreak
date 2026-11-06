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
                if (isPermissionDenied(apiError?.first, apiError?.second)) {
                    throw UntisPermissionException(apiError?.second ?: "Not allowed for this account.", apiError?.first)
                }
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
                if (isPermissionDenied(code, message)) throw UntisPermissionException(message, code)
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
        recordRaw: Boolean = true,
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
            if (recordRaw) rawSink.onResponse(label, System.currentTimeMillis(), text.take(MAX_RAW_BODY_CHARS))
            if (received.code == 401) throw UntisAuthException("WebUntis rejected the token.")
            // 403 on the REST API: the token is good, the school does not allow this view to students.
            if (received.code == 403) throw UntisPermissionException("HTTP 403")
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
            // Bytes, not a String: a String body would add "; charset=utf-8" to the part type, and the
            // web app sends the part as plain application/json.
            .addFormDataPart(
                partName,
                "blob",
                json.toString().toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType()),
            )
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
            if (received.code == 403) throw UntisPermissionException("HTTP 403")
            if (!received.isSuccessful) throw UntisProtocolException(describeFailure(received.code, text))
            runCatching { JsonParser.parseString(text) }.getOrNull() ?: JsonObject()
        }
    }

    /**
     * What the server said about a refused request: its message, its error code and the names of
     * the fields it did not accept. Never the request, so no message text ends up in an error.
     */
    private fun describeFailure(status: Int, body: String): String {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        val parts = buildList {
            root?.stringOrNull("errorMessage")?.takeIf { it.isNotBlank() }?.let(::add)
            root?.stringOrNull("errorCode")?.let { add("($it)") }
            root?.arrayOrNull("validationErrors")?.mapNotNull { entry ->
                val error = entry.asJsonObjectOrNull() ?: return@mapNotNull null
                listOfNotNull(error.stringOrNull("path"), error.stringOrNull("errorMessage")).joinToString(": ")
            }?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString("; ")) }
        }
        return "HTTP $status" + if (parts.isEmpty()) "" else " " + parts.joinToString(" ")
    }

    /**
     * Downloads a file from a signed storage address into [out] and returns its size. The address
     * carries its own signature, so no WebUntis token is sent to the storage server. Only https is
     * accepted. Only the status and size go to the debug log, never the address or the content.
     */
    suspend fun downloadFile(
        url: String,
        headers: Map<String, String>,
        out: java.io.OutputStream,
        maxBytes: Long,
    ): Long = withContext(Dispatchers.IO) {
        val target = runCatching { url.toHttpUrl() }.getOrNull()
            ?: throw UntisParseException("The download address is not valid.")
        if (target.scheme != "https") throw UntisProtocolException("The download address is not https.")
        val request = Request.Builder()
            .url(target)
            .header("User-Agent", "UntisMobileAndroid")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        // A large file can take longer than the 35 seconds that the normal calls get.
        val downloader = client.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        val response = try {
            downloader.newCall(request).execute()
        } catch (error: IOException) {
            throw UntisNetworkException("Could not reach the file server.", error)
        }
        response.use { received ->
            if (!received.isSuccessful) {
                rawSink.onResponse("attachment-download", System.currentTimeMillis(), "HTTP ${received.code}")
                throw UntisProtocolException("The file server returned HTTP ${received.code}.")
            }
            val body = received.body ?: throw UntisParseException("The file server sent no file.")
            val declared = body.contentLength()
            if (declared > maxBytes) throw UntisProtocolException("The file is too large.")
            var total = 0L
            try {
                val buffer = ByteArray(16 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw UntisProtocolException("The file is too large.")
                        out.write(buffer, 0, read)
                    }
                }
            } catch (error: IOException) {
                throw UntisNetworkException("The download was interrupted.", error)
            }
            rawSink.onResponse("attachment-download", System.currentTimeMillis(), "HTTP ${received.code}, $total bytes")
            total
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

        /** WebUntis: "no right for timetable" and the generic "not allowed". */
        private val PERMISSION_CODES = setOf(-8509, -7004)
        private val PERMISSION_WORDS = Regex(
            "no right|not allowed|no permission|not permitted|permission denied|access denied|forbidden|" +
                "keine berechtigung|nicht berechtigt|keine rechte",
            RegexOption.IGNORE_CASE,
        )

        /** True when the answer says the account may not do this, as opposed to not being signed in. */
        internal fun isPermissionDenied(code: Int?, message: String?): Boolean =
            code in PERMISSION_CODES || (message != null && PERMISSION_WORDS.containsMatchIn(message))

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
