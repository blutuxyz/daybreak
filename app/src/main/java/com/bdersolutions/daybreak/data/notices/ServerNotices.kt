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
    val switches: RemoteSwitches = RemoteSwitches(),
)

/**
 * The developer's switches, read from the server on every start. A switch that is off turns its part
 * of the app off for everyone without a new release. Everything is on until the server says otherwise.
 */
data class RemoteSwitches(
    /** The whole in-app update feature: checks, notifications, the update screen and the gate. */
    val updates: Boolean = true,
    /** Only the required-version gate. */
    val requiredVersion: Boolean = true,
    /** The message pop-ups. */
    val messages: Boolean = true,
    /** Single features that are turned off; the ids are in [RemoteFeatures]. */
    val disabledFeatures: Set<String> = emptySet(),
) {
    fun featureOn(id: String): Boolean = id !in disabledFeatures
}

/** The ids of single app features the server can turn off. The app checks each one where it starts. */
object RemoteFeatures {
    const val MESSAGES_SEND = "messages.send"
    const val SUPPORT_POPUP = "support.popup"
    const val REMINDERS = "reminders"
    const val ASSISTANT = "assistant"
}

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
                switches = switchesFrom(root),
            )
        }.getOrNull()
    }

    /** 10203 becomes "1.2.3", the same formula as the server and the release workflow. */
    fun versionNameOf(code: Long): String = "${code / 10_000}.${(code / 100) % 100}.${code % 100}"

    private fun switchesFrom(root: JsonObject): RemoteSwitches {
        val switches = (root.get("switches") as? JsonObject) ?: JsonObject()
        val disabled = root.array("disabledFeatures")
            .mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
            .toSet()
        return RemoteSwitches(
            updates = switches.bool("updates"),
            requiredVersion = switches.bool("requiredVersion"),
            messages = switches.bool("messages"),
            disabledFeatures = disabled,
        )
    }

    private fun JsonObject.bool(name: String): Boolean {
        val value = get(name)
        return if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) value.asBoolean else true
    }

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
    /**
     * True when the installed version is older than the minimum the server requires. Switching off the
     * updates or the required version in [switches] lifts the gate at once.
     */
    fun isRequired(
        installedVersionCode: Long,
        requiredVersionCode: Long?,
        switches: RemoteSwitches = RemoteSwitches(),
    ): Boolean =
        switches.updates && switches.requiredVersion &&
            requiredVersionCode != null && installedVersionCode < requiredVersionCode
}

/** Chooses the messages to show: not dismissed, not over, the most serious first. */
object MessageFilter {
    fun pending(messages: List<ServerMessage>, dismissed: Set<String>, now: Long): List<ServerMessage> =
        messages
            .filter { it.id !in dismissed && (it.untilMillis == null || it.untilMillis > now) }
            .sortedByDescending { it.level.ordinal }
}
