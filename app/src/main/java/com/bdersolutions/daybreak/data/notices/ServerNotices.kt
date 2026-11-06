package com.bdersolutions.daybreak.data.notices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

/** How serious a message is. It only decides the title and order of its pop-up. */
enum class NoticeLevel {
    INFO,
    WARNING,
    CRITICAL,

    ;

    companion object {
        fun of(raw: String?): NoticeLevel = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: INFO
    }
}

/**
 * A short message from the developer, for example "WebUntis is down". [de] and [en] may be blank,
 * but not both.
 */
data class ServerMessage(
    val id: String,
    val level: NoticeLevel,
    val de: String,
    val en: String,
    /** When the message stops being shown, in epoch milliseconds; null for never. */
    val untilMillis: Long?,
) {
    /** The text in the app's language, or the other one when that is blank. */
    fun textFor(german: Boolean): String {
        val preferred = if (german) de else en
        val fallback = if (german) en else de
        return preferred.ifBlank { fallback }
    }
}

/**
 * What the update server says (GET /v1/status): the oldest app version that may still be used, and
 * the messages. The minimum is null when there is none.
 */
data class ServerStatus(
    val minVersionName: String?,
    val minVersionCode: Long?,
    val messages: List<ServerMessage>,
)

/** Reads the server's status answer. Tolerant: a broken message is skipped, a broken answer is null. */
object ServerStatusJson {
    fun parse(body: String?): ServerStatus? {
        if (body.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() as? JsonObject ?: return null
        return runCatching {
            val code = root.long("minVersionCode")?.takeIf { it > 0 }
            ServerStatus(
                minVersionName = root.string("minVersionName") ?: code?.let { versionNameOf(it) },
                minVersionCode = code,
                messages = root.array("messages").mapNotNull { (it as? JsonObject)?.let { obj -> messageFrom(obj) } },
            )
        }.getOrNull()
    }

    /** 10203 becomes "1.2.3", the same formula as the server and the release workflow. */
    fun versionNameOf(code: Long): String = "${code / 10_000}.${(code / 100) % 100}.${code % 100}"

    private fun messageFrom(obj: JsonObject): ServerMessage? {
        val id = obj.string("id") ?: return null
        val de = obj.string("de").orEmpty()
        val en = obj.string("en").orEmpty()
        if (de.isBlank() && en.isBlank()) return null
        val until = obj.string("until")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        return ServerMessage(
            id = id,
            level = NoticeLevel.of(obj.string("level")),
            de = de,
            en = en,
            untilMillis = until,
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
}

/** Decides whether the installed app is too old to be used. */
object UpdateGate {
    /** True when the installed version is older than the minimum the server requires. */
    fun isRequired(installedVersionCode: Long, requiredVersionCode: Long?): Boolean =
        requiredVersionCode != null && installedVersionCode < requiredVersionCode
}

/** Chooses the messages to show: not dismissed, not over, the most serious first. */
object MessageFilter {
    fun pending(messages: List<ServerMessage>, dismissed: Set<String>, now: Long): List<ServerMessage> =
        messages
            .filter { it.id !in dismissed && (it.untilMillis == null || it.untilMillis > now) }
            .sortedByDescending { it.level.ordinal }
}
