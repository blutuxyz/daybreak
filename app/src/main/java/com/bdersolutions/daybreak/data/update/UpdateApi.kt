package com.bdersolutions.daybreak.data.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the update server (update-server/ in this repository). It never talks to GitHub.
 * [baseUrl] is the server's address, for example https://updates.example.com.
 */
class UpdateApi(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val base = baseUrl.trim().trimEnd('/')

    /** The newest release, or null when the server has none yet. */
    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        get("/v1/latest").use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw UpdateException("The update server answered ${response.code}")
            val body = response.body?.string().orEmpty()
            UpdateJson.parseRelease(body) ?: throw UpdateException("The update server sent an unusable answer")
        }
    }

    /**
     * The server's status answer as it came: the minimum app version and the messages. Read it with
     * [com.bdersolutions.daybreak.data.notices.ServerStatusJson].
     */
    /**
     * The privacy policy and terms (GET /v1/legal) in German or English; any other language gets German.
     * Only the language is sent, no identifier. Read it with [com.bdersolutions.daybreak.data.legal.RemoteLegalJson].
     */
    suspend fun legalBody(language: String): String = withContext(Dispatchers.IO) {
        val lang = if (language == "en") "en" else "de"
        get("/v1/legal?lang=$lang").use { response ->
            if (!response.isSuccessful) throw UpdateException("The update server answered ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    suspend fun statusBody(): String = withContext(Dispatchers.IO) {
        get("/v1/status").use { response ->
            if (!response.isSuccessful) throw UpdateException("The update server answered ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    /**
     * A picture of a message sheet (GET /v1/message-image/:id), at most [MAX_IMAGE_BYTES] bytes. Null when the
     * server has no such picture or cannot be reached: the sheet then simply has no picture.
     */
    suspend fun messageImage(id: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!id.matches(IMAGE_ID)) return@withContext null
        try {
            get("/v1/message-image/$id").use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
                val bytes = body.byteStream().readNBytes(MAX_IMAGE_BYTES + 1)
                bytes.takeIf { it.size <= MAX_IMAGE_BYTES }
            }
        } catch (_: UpdateException) {
            null
        }
    }

    /** The release history with changelogs, newest first. */
    suspend fun releases(limit: Int = 20): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        get("/v1/releases?limit=$limit").use { response ->
            if (!response.isSuccessful) throw UpdateException("The update server answered ${response.code}")
            UpdateJson.parseReleases(response.body?.string())
        }
    }

    /**
     * Opens the APK of [release], starting at byte [offset] so an interrupted download can resume.
     * The caller closes the response. The server's address is always the configured one: only the
     * path of the link the server sent is used.
     */
    fun openApk(release: ReleaseInfo, offset: Long): Response {
        val apk = release.apk ?: throw UpdateException("This release has no APK")
        val path = apk.url.toHttpUrlOrNull()?.encodedPath?.takeIf { it.startsWith("/v1/apk/") }
            ?: "/v1/apk/${release.tag}"
        val request = Request.Builder()
            .url(base + path)
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        return try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            Log.w(TAG, "Could not open the APK", error)
            throw UpdateException("The update server could not be reached", error)
        }
    }

    private fun get(path: String): Response {
        val request = Request.Builder().url(base + path).header("Accept", "application/json").build()
        return try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            Log.w(TAG, "Could not reach the update server for $path", error)
            throw UpdateException("The update server could not be reached", error)
        }
    }

    private companion object {
        const val TAG = "UpdateApi"
        const val MAX_IMAGE_BYTES = 3 * 1024 * 1024
        val IMAGE_ID = Regex("[0-9a-f]{12}")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }
}
