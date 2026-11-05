package com.bdersolutions.daybreak.untis

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.LocalDateTime

/**
 * Reads the message box of WebUntis (`/api/rest/view/v1/messages`). The lists and the single
 * message are read tolerantly: an entry that cannot be read is skipped.
 */
internal object MailParser {

    fun parseList(folder: MailFolder, result: JsonElement): List<UntisMail> {
        val root = result.asJsonObjectOrNull() ?: return emptyList()
        val key = when (folder) {
            MailFolder.INBOX -> "incomingMessages"
            MailFolder.SENT -> "sentMessages"
            MailFolder.DRAFTS -> "draftMessages"
        }
        val items = root.arrayOrNull(key) ?: return emptyList()
        return items.mapNotNull { element -> element.asJsonObjectOrNull()?.let(::parseEntry) }
    }

    fun parseMessage(result: JsonElement): UntisMail? = result.asJsonObjectOrNull()?.let(::parseEntry)

    private fun parseEntry(obj: JsonObject): UntisMail? {
        val id = obj.longOrNull("id") ?: return null
        val subject = obj.stringOrNull("subject").orEmpty().trim()
        val preview = plainText(obj.stringOrNull("contentPreview").orEmpty())
        val content = plainText(obj.stringOrNull("content").orEmpty())
        val recipients = obj.arrayOrNull("recipientPersons")
            ?.mapNotNull { it.asJsonObjectOrNull()?.let(::parsePerson) }
            .orEmpty()
        return UntisMail(
            id = id,
            subject = subject,
            preview = preview,
            content = content,
            sender = obj.objectOrNull("sender")?.let(::parsePerson),
            recipients = recipients,
            sentAt = parseTime(obj.stringOrNull("sentDateTime", "lastModifiedDateTime", "creationDateTime")),
            read = obj.booleanOrNull("isMessageRead") ?: true,
            hasAttachments = obj.booleanOrNull("hasAttachments") ?: false,
            attachments = parseAttachments(obj),
        )
    }

    /** Attached files sit in `storageAttachments` (id and name); `attachments` has the same shape. */
    private fun parseAttachments(obj: JsonObject): List<UntisAttachment> =
        listOf("storageAttachments", "attachments").flatMap { key ->
            obj.arrayOrNull(key)?.mapNotNull { element ->
                val file = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val name = file.stringOrNull("name", "fileName")?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                UntisAttachment(id = file.stringOrNull("id").orEmpty(), name = name)
            }.orEmpty()
        }

    private fun parsePerson(obj: JsonObject): UntisMailPerson? {
        val name = obj.stringOrNull("displayName", "name")?.trim().orEmpty()
        if (name.isBlank()) return null
        return UntisMailPerson(userId = obj.longOrNull("userId", "id") ?: 0L, name = name)
    }

    private fun parseTime(value: String?): LocalDateTime? =
        value?.let { runCatching { LocalDateTime.parse(it.removeSuffix("Z").substringBefore('+')) }.getOrNull() }

    /** Message texts are plain text; markup is removed in case a school sends some anyway. */
    private fun plainText(raw: String): String =
        raw.replace(Regex("(?i)<br\\s*/?>|</p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
}

/** Reads `messages/recipients/static/persons`: groups of people, each with a name and user id. */
internal object RecipientParser {

    fun parse(result: JsonElement): List<UntisRecipient> {
        val groups = result.asJsonArrayOrNull() ?: return emptyList()
        return groups.flatMap { element ->
            val obj = element.asJsonObjectOrNull() ?: return@flatMap emptyList()
            val group = when (obj.stringOrNull("type")) {
                "CLASS_TEACHERS" -> RecipientGroup.CLASS_TEACHER
                "TEACHERS" -> RecipientGroup.TEACHER
                else -> RecipientGroup.OTHER
            }
            obj.arrayOrNull("persons")?.mapNotNull { person ->
                val entry = person.asJsonObjectOrNull() ?: return@mapNotNull null
                val userId = entry.longOrNull("userId") ?: return@mapNotNull null
                val name = entry.stringOrNull("displayName", "name")?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val tags = entry.arrayOrNull("tags")
                    ?.mapNotNull { tag ->
                        tag.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() }
                    }
                    .orEmpty()
                UntisRecipient(userId = userId, name = name, group = group, tags = tags)
            }.orEmpty()
        }
    }
}

/** Reads `messages/{id}/attachmentstorageurl`: a signed `downloadUrl` and the headers it needs. */
internal object AttachmentLinkParser {

    fun parse(result: JsonElement): UntisDownloadLink? {
        val root = result.asJsonObjectOrNull() ?: return null
        val url = root.stringOrNull("downloadUrl", "url")?.takeIf { it.isNotBlank() } ?: return null
        val headers = linkedMapOf<String, String>()
        val raw = root.get("additionalHeaders")
        when {
            raw != null && raw.isJsonArray -> raw.asJsonArray.forEach { entry ->
                val pair = entry.asJsonObjectOrNull() ?: return@forEach
                val name = pair.stringOrNull("key", "name") ?: return@forEach
                val value = pair.stringOrNull("value") ?: return@forEach
                headers[name] = value
            }
            raw != null && raw.isJsonObject -> raw.asJsonObject.entrySet().forEach { (name, value) ->
                if (value.isJsonPrimitive) headers[name] = value.asString
            }
        }
        // The Host header names the server the address already points to; the client sets it itself.
        headers.keys.removeAll { it.equals("host", ignoreCase = true) }
        return UntisDownloadLink(url = url, headers = headers)
    }
}
