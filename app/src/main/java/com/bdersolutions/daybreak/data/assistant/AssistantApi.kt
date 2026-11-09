package com.bdersolutions.daybreak.data.assistant

import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Asks the assistant on the update server (`POST /v1/assistant`). The server holds the Anthropic key and
 * passes the question on; this client sends the chat and the timetable summary and reads the answer as it
 * streams. Nothing here is logged: not the question, not the answer, not the timetable.
 */
class AssistantApi(
    baseUrl: String,
    private val installId: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // The first piece can take a while (the model reads the timetable); after that pieces come fast.
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES)
        .build(),
) {
    private val base = baseUrl.trim().trimEnd('/')
    private val url = "$base/v1/assistant"

    /** A short AI title for a new chat, or null (switched off, offline, over budget): the chat keeps its name. */
    suspend fun title(question: String, answer: String, language: String, model: String?): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$url/title")
            .header("Accept", "application/json")
            .header("X-Daybreak-Install", installId())
            .post(AssistantProtocol.titleRequestBody(question, answer, language, model).toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) null else AssistantProtocol.parseTitle(response.body?.string())
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** The models the server allows, or null when the assistant is off there (or unreachable). */
    suspend fun models(): AssistantModels? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$url/models").header("Accept", "application/json").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else AssistantProtocol.parseModels(response.body?.string())
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Streams the answer to the last user message in [messages]. The flow ends after [AssistantEvent.Done],
     * [AssistantEvent.Refusal] or [AssistantEvent.Failure]; cancelling it cancels the request.
     */
    fun ask(
        messages: List<ChatMessage>,
        context: String,
        language: String,
        model: String? = null,
        unlockCode: String? = null,
        effort: String? = null,
        today: LocalDate = LocalDate.now(),
    ): Flow<AssistantEvent> = callbackFlow {
        val body = AssistantProtocol.requestBody(messages, context, language, today, model, unlockCode, effort)
        val call = client.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .header("X-Daybreak-Install", installId())
                .post(body.toRequestBody(JSON))
                .build(),
        )
        // Reading blocks, so it runs in its own job; closing the flow cancels the call, which ends the read.
        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        send(AssistantEvent.Failure(AssistantProtocol.failureForStatus(response.code)))
                        return@use
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        send(AssistantEvent.Failure(AssistantFailure.UNAVAILABLE))
                        return@use
                    }
                    var finished = false
                    while (!finished) {
                        val line = source.readUtf8Line() ?: break
                        val event = AssistantProtocol.parseEvent(line) ?: continue
                        send(event)
                        finished = event is AssistantEvent.Done || event is AssistantEvent.Failure
                    }
                    // The stream ended without saying so: the connection broke off.
                    if (!finished) send(AssistantEvent.Failure(AssistantFailure.OFFLINE))
                }
            } catch (_: IOException) {
                if (!call.isCanceled()) trySend(AssistantEvent.Failure(AssistantFailure.OFFLINE))
            } catch (_: IllegalArgumentException) {
                trySend(AssistantEvent.Failure(AssistantFailure.UNAVAILABLE))
            }
            channel.close()
        }
        awaitClose { call.cancel() }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
