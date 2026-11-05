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
