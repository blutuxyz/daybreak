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

/** How a message sheet looks: a plain one, or one with a glow behind the picture in the brand or a warning colour. */
enum class NoticeSheetStyle {
    PLAIN,
    BRAND,
    WARNING,
    CRITICAL,

    ;

    companion object {
        fun of(raw: String?): NoticeSheetStyle = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PLAIN
    }
}

enum class NoticeImageShape { ICON, BANNER }

enum class NoticeButtonStyle {
    FILLED,
    TONAL,
    TEXT,

    ;

    companion object {
        fun of(raw: String?): NoticeButtonStyle = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FILLED
    }
}

/**
 * The picture of a sheet: [kind] "preset" names a picture that ships inside the app ([value] "playstore"),
 * "upload" names a picture the developer uploaded to the server ([value] is its id).
 */
data class NoticeImage(val kind: String, val value: String, val shape: NoticeImageShape) {
    val isUpload: Boolean get() = kind == "upload"
}

/** One button of a sheet. A button with a [url] opens it in the browser; one without just closes the sheet. */
data class NoticeButton(
    val de: String,
    val en: String,
    val url: String?,
    val style: NoticeButtonStyle,
    /** Whether pressing it closes the sheet (and counts as read). */
    val closes: Boolean,
    /** A small note under the button, for example "Auto updates still work as usual". */
    val infoDe: String,
    val infoEn: String,
) {
    fun label(german: Boolean): String = pick(german, de, en)
    fun info(german: Boolean): String = pick(german, infoDe, infoEn)
}

/** Which installs of the app may see a message. "play" and "sideloaded" are read from the installer of the app. */
enum class InstallSource { SIDELOADED, PLAY }

/** What this app is, for choosing the messages meant for it. */
data class NoticeAudience(val platform: String = "android", val installSource: InstallSource = InstallSource.SIDELOADED)

/**
 * The part of a message that makes it a sheet from the bottom: a title, a picture, a look, who sees it and the
 * buttons. A message without one is shown as a plain sheet with the text and an OK button.
 */
data class NoticeSheet(
    val titleDe: String,
    val titleEn: String,
    val image: NoticeImage?,
    val style: NoticeSheetStyle,
    /** False: no swiping away, no tapping outside; a button has to close it. */
    val dismissible: Boolean,
    /** "all", "android" or "desktop". */
    val platform: String,
    /** "any", "sideloaded" or "play". */
    val installSource: String,
    val buttons: List<NoticeButton>,
) {
    fun title(german: Boolean): String = pick(german, titleDe, titleEn)

    val hasTitle: Boolean get() = titleDe.isNotBlank() || titleEn.isNotBlank()

    /** True when this app (the platform and where it was installed from) is meant to see it. */
    fun isFor(audience: NoticeAudience): Boolean {
        val platformOk = platform == "all" || platform.equals(audience.platform, ignoreCase = true)
        val sourceOk = when (installSource) {
            "sideloaded" -> audience.installSource == InstallSource.SIDELOADED
            "play" -> audience.installSource == InstallSource.PLAY
            else -> true
        }
        return platformOk && sourceOk
    }
}

private fun pick(german: Boolean, de: String, en: String): String {
    val preferred = if (german) de else en
    val fallback = if (german) en else de
    return preferred.ifBlank { fallback }
}

/**
 * A short message from the developer, for example "WebUntis is down". [de] and [en] may be blank,
 * but not both, unless the [sheet] has a title.
 */
data class ServerMessage(
    val id: String,
    val level: NoticeLevel,
    val de: String,
    val en: String,
    /** When the message stops being shown, in epoch milliseconds; null for never. */
    val untilMillis: Long?,
    /** The sheet part (title, picture, buttons, audience); null for a plain message. */
    val sheet: NoticeSheet? = null,
) {
    /** The text in the app's language, or the other one when that is blank. */
    fun textFor(german: Boolean): String = pick(german, de, en)
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
        val sheet = (obj.get("sheet") as? JsonObject)?.let { sheetFrom(it) }
        if (de.isBlank() && en.isBlank() && sheet?.hasTitle != true) return null
        val until = obj.string("until")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        return ServerMessage(
            id = id,
            level = NoticeLevel.of(obj.string("level")),
            de = de,
            en = en,
            untilMillis = until,
            sheet = sheet,
        )
    }

    /** Reads the sheet part. Tolerant: unknown values fall back to plain defaults, a button without a label is skipped. */
    private fun sheetFrom(obj: JsonObject): NoticeSheet {
        val image = (obj.get("image") as? JsonObject)?.let { image ->
            val kind = image.string("kind")
            val value = image.string("value")
            if ((kind == "preset" || kind == "upload") && value != null) {
                NoticeImage(
                    kind = kind,
                    value = value,
                    shape = if (image.string("shape").equals("banner", ignoreCase = true)) NoticeImageShape.BANNER else NoticeImageShape.ICON,
                )
            } else {
                null
            }
        }
        val buttons = obj.array("buttons").mapNotNull { element ->
            val button = element as? JsonObject ?: return@mapNotNull null
            val de = button.string("de").orEmpty()
            val en = button.string("en").orEmpty()
            if (de.isBlank() && en.isBlank()) return@mapNotNull null
            // Only an https address ever opens; anything else just closes the sheet.
            val url = button.string("url")?.takeIf { button.string("action") == "url" && it.startsWith("https://") }
            NoticeButton(
                de = de,
                en = en,
                url = url,
                style = NoticeButtonStyle.of(button.string("style")),
                closes = button.get("closes")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                infoDe = button.string("infoDe").orEmpty(),
                infoEn = button.string("infoEn").orEmpty(),
            )
        }
        return NoticeSheet(
            titleDe = obj.string("titleDe").orEmpty(),
            titleEn = obj.string("titleEn").orEmpty(),
            image = image,
            style = NoticeSheetStyle.of(obj.string("style")),
            dismissible = obj.get("dismissible")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
            platform = obj.string("platform") ?: "all",
            installSource = obj.string("installSource") ?: "any",
            buttons = buttons,
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
    /**
     * [audience] (what this app is) leaves out messages meant for another platform or another way of installing.
     * Without it nothing is left out for that reason.
     */
    fun pending(
        messages: List<ServerMessage>,
        dismissed: Set<String>,
        now: Long,
        audience: NoticeAudience? = null,
    ): List<ServerMessage> =
        messages
            .filter { it.id !in dismissed && (it.untilMillis == null || it.untilMillis > now) }
            .filter { audience == null || it.sheet?.isFor(audience) != false }
            .sortedByDescending { it.level.ordinal }
}
