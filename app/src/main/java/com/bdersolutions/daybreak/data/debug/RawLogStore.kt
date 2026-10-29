package com.bdersolutions.daybreak.data.debug

import android.content.Context
import com.bdersolutions.daybreak.untis.RawResponseSink
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class RawLogEntry(
    val id: Long,
    val method: String,
    val receivedAt: Long,
    val body: String,
)

/**
 * A small, persisted ring buffer of raw API responses for troubleshooting.
 * Requests and credentials are never written here.
 */
class RawLogStore(context: Context) : RawResponseSink {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()
    private val _entries = MutableStateFlow(loadEntries())
    val entries: StateFlow<List<RawLogEntry>> = _entries.asStateFlow()

    override fun onResponse(method: String, receivedAt: Long, body: String) {
        val entry = RawLogEntry(
            id = receivedAt + (0..999).random(),
            method = method,
            receivedAt = receivedAt,
            body = body.take(MAX_BODY_CHARS),
        )
        synchronized(lock) {
            val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
            _entries.value = updated
            persist(updated)
        }
    }

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            runCatching { file.delete() }
        }
    }

    private fun loadEntries(): List<RawLogEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JsonParser.parseString(file.readText()) as JsonArray
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                RawLogEntry(
                    id = obj.get("id")?.asLong ?: 0L,
                    method = obj.get("method")?.asString ?: "",
                    receivedAt = obj.get("receivedAt")?.asLong ?: 0L,
                    body = obj.get("body")?.asString.orEmpty(),
                )
            }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    private fun persist(entries: List<RawLogEntry>) {
        runCatching {
            val array = JsonArray()
            entries.forEach { entry ->
                array.add(JsonObject().apply {
                    addProperty("id", entry.id)
                    addProperty("method", entry.method)
                    addProperty("receivedAt", entry.receivedAt)
                    addProperty("body", entry.body)
                })
            }
            val temporary = File(file.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(array.toString())
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "raw_api_responses.json"
        const val MAX_ENTRIES = 20
        const val MAX_BODY_CHARS = 120_000
    }
}
