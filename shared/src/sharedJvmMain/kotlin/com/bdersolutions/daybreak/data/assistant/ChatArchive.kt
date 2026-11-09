package com.bdersolutions.daybreak.data.assistant

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One saved chat, as it is kept on the device. */
data class SavedChat(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    /** Drafted messages by tool call id, with what became of them (DraftState.Status names). */
    val drafts: Map<String, SavedDraft> = emptyMap(),
    /** Where [title] came from: the first question, an AI title, or the student. Only AUTO is ever replaced. */
    val titleSource: TitleSource = TitleSource.AUTO,
)

enum class TitleSource { AUTO, AI, MANUAL }

data class SavedDraft(val draft: MessageDraft, val status: String)

/** A line of the chat history. */
data class ChatSummary(val id: Long, val title: String, val updatedAt: Long)

/**
 * Turns saved chats into JSON and back. Pictures are kept so a chat reads the same later; PDFs and text
 * files keep only their name (they can be large and are seldom looked at again). Tolerant: a broken
 * entry is skipped, a broken file is null.
 */
object ChatArchive {
    private const val TITLE_CHARS = 60

    /** The first question, shortened; or the first attachment's name; or "Chat". */
    fun titleFor(messages: List<ChatMessage>): String {
        val first = messages.firstOrNull { it.role == ChatRole.USER } ?: return "Chat"
        val text = first.text.replace(Regex("\\s+"), " ").trim()
        val title = text.ifBlank { first.attachments.firstOrNull()?.name.orEmpty() }.ifBlank { "Chat" }
        return if (title.length <= TITLE_CHARS) title else title.take(TITLE_CHARS - 1).trimEnd() + "…"
    }

    fun encode(chat: SavedChat): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("id", chat.id)
        addProperty("title", chat.title)
        addProperty("titleSource", chat.titleSource.name)
        addProperty("updatedAt", chat.updatedAt)
        add("messages", JsonArray().also { array -> chat.messages.forEach { array.add(encodeMessage(it)) } })
        add(
            "drafts",
            JsonArray().also { array ->
                chat.drafts.values.forEach { saved ->
                    array.add(
                        JsonObject().apply {
                            addProperty("callId", saved.draft.callId)
                            addProperty("recipientUserId", saved.draft.recipientUserId)
                            addProperty("recipientName", saved.draft.recipientName)
                            addProperty("subject", saved.draft.subject)
                            addProperty("body", saved.draft.body)
                            addProperty("status", saved.status)
                        },
                    )
                }
            },
        )
    }.toString()

    fun decode(text: String?): SavedChat? {
        if (text.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return null
        return runCatching {
            val messages = root.getAsJsonArray("messages")?.mapNotNull { runCatching { decodeMessage(it.asJsonObject) }.getOrNull() }.orEmpty()
            val drafts = root.getAsJsonArray("drafts")?.mapNotNull { element ->
                runCatching {
                    val o = element.asJsonObject
                    val draft = MessageDraft(
                        callId = o.get("callId").asString,
                        recipientUserId = o.get("recipientUserId").asLong,
                        recipientName = o.get("recipientName").asString,
                        subject = o.get("subject").asString,
                        body = o.get("body").asString,
                    )
                    draft.callId to SavedDraft(draft, o.get("status").asString)
                }.getOrNull()
            }.orEmpty().toMap()
            SavedChat(
                id = root.get("id").asLong,
                title = root.get("title")?.asString ?: titleFor(messages),
                updatedAt = root.get("updatedAt")?.asLong ?: 0L,
                messages = messages,
                drafts = drafts,
                titleSource = runCatching { TitleSource.valueOf(root.get("titleSource").asString) }.getOrDefault(TitleSource.AUTO),
            )
        }.getOrNull()
    }

    fun encodeIndex(chats: List<ChatSummary>): String = JsonArray().also { array ->
        chats.forEach { chat ->
            array.add(
                JsonObject().apply {
                    addProperty("id", chat.id)
                    addProperty("title", chat.title)
                    addProperty("updatedAt", chat.updatedAt)
                },
            )
        }
    }.toString()

    fun decodeIndex(text: String?): List<ChatSummary> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JsonParser.parseString(text).asJsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching {
                val o = element.asJsonObject
                ChatSummary(o.get("id").asLong, o.get("title").asString, o.get("updatedAt").asLong)
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    private fun encodeMessage(message: ChatMessage) = JsonObject().apply {
        addProperty("id", message.id)
        addProperty("role", message.role.name)
        addProperty("text", message.text)
        message.toolCallId?.let { addProperty("toolCallId", it) }
        if (message.toolCalls.isNotEmpty()) {
            add(
                "toolCalls",
                JsonArray().also { array ->
                    message.toolCalls.forEach { call ->
                        array.add(
                            JsonObject().apply {
                                addProperty("id", call.id)
                                addProperty("name", call.name)
                                addProperty("arguments", call.arguments)
                            },
                        )
                    }
                },
            )
        }
        if (message.attachments.isNotEmpty()) {
            add(
                "attachments",
                JsonArray().also { array ->
                    message.attachments.forEach { attachment ->
                        array.add(
                            JsonObject().apply {
                                addProperty("kind", attachment.kind.name)
                                addProperty("name", attachment.name)
                                addProperty("mime", attachment.mime)
                                addProperty("size", attachment.sizeBytes)
                                // Pictures stay viewable; other files keep only their name.
                                if (attachment.kind == ChatAttachment.Kind.IMAGE) addProperty("data", attachment.base64)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun decodeMessage(o: JsonObject): ChatMessage = ChatMessage(
        id = o.get("id").asLong,
        role = ChatRole.valueOf(o.get("role").asString),
        text = o.get("text")?.asString.orEmpty(),
        toolCallId = o.get("toolCallId")?.asString,
        toolCalls = o.getAsJsonArray("toolCalls")?.map { element ->
            val call = element.asJsonObject
            ToolCall(call.get("id").asString, call.get("name").asString, call.get("arguments")?.asString ?: "{}")
        }.orEmpty(),
        attachments = o.getAsJsonArray("attachments")?.map { element ->
            val a = element.asJsonObject
            ChatAttachment(
                kind = ChatAttachment.Kind.valueOf(a.get("kind").asString),
                name = a.get("name").asString,
                mime = a.get("mime").asString,
                base64 = a.get("data")?.asString.orEmpty(),
                sizeBytes = a.get("size")?.asLong ?: 0L,
            )
        }.orEmpty(),
    )
}
