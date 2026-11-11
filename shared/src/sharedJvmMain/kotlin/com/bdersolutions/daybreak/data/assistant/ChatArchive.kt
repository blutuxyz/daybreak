package com.bdersolutions.daybreak.data.assistant

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    /** Cards the assistant showed (a day, exams, absences), by tool call id. */
    val cards: Map<String, AssistantCard> = emptyMap(),
)

enum class TitleSource { AUTO, AI, MANUAL }

data class SavedDraft(val draft: MessageDraft, val status: String)

/**
 * What of a chat has to be written, given what was last saved. Opening a chat must not count as using it:
 * otherwise every chat you only looked at jumped to the top of the history as "just now".
 */
object ChatSaving {
    /**
     * [candidate] is the chat as it is now, stamped with the current time. Returns what to save, or null
     * when nothing changed. A new name alone keeps the old time, so renaming does not reorder the history
     * (as renaming from the history list already does); new messages, drafts or cards take the new time.
     */
    fun toSave(lastSaved: SavedChat?, candidate: SavedChat): SavedChat? {
        if (lastSaved == null || lastSaved.id != candidate.id) return candidate
        val sameContent = lastSaved.messages == candidate.messages && lastSaved.drafts == candidate.drafts && lastSaved.cards == candidate.cards
        val sameName = lastSaved.title == candidate.title && lastSaved.titleSource == candidate.titleSource
        return when {
            sameContent && sameName -> null
            sameContent -> candidate.copy(updatedAt = lastSaved.updatedAt)
            else -> candidate
        }
    }
}

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
        if (chat.cards.isNotEmpty()) {
            add("cards", JsonObject().also { cards -> chat.cards.forEach { (callId, card) -> cards.add(callId, encodeAnyCard(card)) } })
        }
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
            val cards = root.getAsJsonObject("cards")?.entrySet()?.mapNotNull { (callId, element) ->
                runCatching { callId to decodeAnyCard(element.asJsonObject) }.getOrNull()
            }.orEmpty().toMap()
            SavedChat(
                cards = cards,
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

    /** A card with its kind; a card saved before there were kinds is a day. */
    private fun encodeAnyCard(card: AssistantCard): JsonObject = when (card) {
        is DayCard -> encodeCard(card).apply { addProperty("kind", "day") }
        is ExamCard -> encodeExams(card).apply { addProperty("kind", "exams") }
        is AbsenceCard -> encodeAbsences(card).apply { addProperty("kind", "absences") }
        is WebsiteCard -> JsonObject().apply {
            addProperty("kind", "website")
            addProperty("title", card.title)
            addProperty("html", card.html)
        }
    }

    private fun decodeAnyCard(o: JsonObject): AssistantCard = when (o.get("kind")?.asString) {
        "exams" -> decodeExams(o)
        "absences" -> decodeAbsences(o)
        "website" -> WebsiteCard(o.get("title")?.asString.orEmpty(), o.get("html")?.asString.orEmpty())
        else -> decodeCard(o)
    }

    private fun encodeExams(card: ExamCard) = JsonObject().apply {
        addProperty("from", card.from.toString())
        addProperty("to", card.to.toString())
        add(
            "exams",
            JsonArray().also { array ->
                card.exams.forEach { exam ->
                    array.add(
                        JsonObject().apply {
                            addProperty("date", exam.date.toString())
                            addProperty("start", exam.start.toString())
                            addProperty("end", exam.end.toString())
                            addProperty("subject", exam.subject)
                            addProperty("subjectName", exam.subjectName)
                            addProperty("name", exam.name)
                            addProperty("text", exam.text)
                            addProperty("teacher", exam.teacher)
                            addProperty("room", exam.room)
                        },
                    )
                }
            },
        )
    }

    private fun decodeExams(o: JsonObject): ExamCard {
        fun JsonObject.text(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return ExamCard(
            from = LocalDate.parse(o.get("from").asString),
            to = LocalDate.parse(o.get("to").asString),
            exams = o.getAsJsonArray("exams")?.mapNotNull { element ->
                runCatching {
                    val e = element.asJsonObject
                    ExamCard.Exam(
                        date = LocalDate.parse(e.get("date").asString),
                        start = LocalTime.parse(e.get("start").asString),
                        end = LocalTime.parse(e.get("end").asString),
                        subject = e.text("subject"),
                        subjectName = e.text("subjectName"),
                        name = e.text("name"),
                        text = e.text("text"),
                        teacher = e.text("teacher"),
                        room = e.text("room"),
                    )
                }.getOrNull()
            }.orEmpty(),
        )
    }

    private fun encodeAbsences(card: AbsenceCard) = JsonObject().apply {
        addProperty("countedMinutes", card.countedMinutes)
        addProperty("recordedMinutes", card.recordedMinutes)
        addProperty("excusedMinutes", card.excusedMinutes)
        addProperty("openMinutes", card.openMinutes)
        addProperty("excusedCount", card.excusedCount)
        addProperty("openCount", card.openCount)
        add(
            "entries",
            JsonArray().also { array ->
                card.entries.forEach { entry ->
                    array.add(
                        JsonObject().apply {
                            addProperty("start", entry.start.toString())
                            addProperty("end", entry.end.toString())
                            addProperty("countedMinutes", entry.countedMinutes)
                            addProperty("excused", entry.excused)
                            addProperty("reason", entry.reason)
                            addProperty("text", entry.text)
                        },
                    )
                }
            },
        )
    }

    private fun decodeAbsences(o: JsonObject): AbsenceCard {
        fun JsonObject.text(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return AbsenceCard(
            countedMinutes = o.get("countedMinutes").asLong,
            recordedMinutes = o.get("recordedMinutes").asLong,
            excusedMinutes = o.get("excusedMinutes").asLong,
            openMinutes = o.get("openMinutes").asLong,
            excusedCount = o.get("excusedCount").asInt,
            openCount = o.get("openCount").asInt,
            entries = o.getAsJsonArray("entries")?.mapNotNull { element ->
                runCatching {
                    val e = element.asJsonObject
                    AbsenceCard.Entry(
                        start = LocalDateTime.parse(e.get("start").asString),
                        end = LocalDateTime.parse(e.get("end").asString),
                        countedMinutes = e.get("countedMinutes").asLong,
                        excused = e.get("excused").asBoolean,
                        reason = e.text("reason"),
                        text = e.text("text"),
                    )
                }.getOrNull()
            }.orEmpty(),
        )
    }

    private fun encodeCard(card: DayCard) = JsonObject().apply {
        addProperty("date", card.date.toString())
        card.holiday?.let { addProperty("holiday", it) }
        add(
            "entries",
            JsonArray().also { array ->
                card.entries.forEach { entry ->
                    array.add(
                        JsonObject().apply {
                            addProperty("start", entry.start.toString())
                            addProperty("end", entry.end.toString())
                            addProperty("subject", entry.subject)
                            addProperty("subjectName", entry.subjectName)
                            addProperty("teacher", entry.teacher)
                            addProperty("room", entry.room)
                            if (entry.cancelled) addProperty("cancelled", true)
                            if (entry.irregular) addProperty("irregular", true)
                            if (entry.originalTeacher.isNotBlank()) addProperty("originalTeacher", entry.originalTeacher)
                            if (entry.originalRoom.isNotBlank()) addProperty("originalRoom", entry.originalRoom)
                            if (entry.note.isNotBlank()) addProperty("note", entry.note)
                            entry.exam?.let { addProperty("exam", it) }
                            addProperty("periods", entry.periods)
                            if (entry.replaces.isNotEmpty()) {
                                add(
                                    "replaces",
                                    JsonArray().also { replaced ->
                                        entry.replaces.forEach { lesson ->
                                            replaced.add(
                                                JsonObject().apply {
                                                    addProperty("start", lesson.start.toString())
                                                    addProperty("end", lesson.end.toString())
                                                    addProperty("subject", lesson.subject)
                                                    addProperty("subjectName", lesson.subjectName)
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            },
        )
    }

    private fun decodeCard(o: JsonObject): DayCard {
        fun JsonObject.text(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return DayCard(
            date = LocalDate.parse(o.get("date").asString),
            holiday = o.get("holiday")?.asString,
            entries = o.getAsJsonArray("entries")?.mapNotNull { element ->
                runCatching {
                    val e = element.asJsonObject
                    DayCard.Entry(
                        start = LocalTime.parse(e.get("start").asString),
                        end = LocalTime.parse(e.get("end").asString),
                        subject = e.text("subject"),
                        subjectName = e.text("subjectName"),
                        teacher = e.text("teacher"),
                        room = e.text("room"),
                        cancelled = e.get("cancelled")?.asBoolean == true,
                        irregular = e.get("irregular")?.asBoolean == true,
                        originalTeacher = e.text("originalTeacher"),
                        originalRoom = e.text("originalRoom"),
                        note = e.text("note"),
                        exam = e.get("exam")?.asString,
                        periods = e.get("periods")?.asInt ?: 1,
                        replaces = e.getAsJsonArray("replaces")?.mapNotNull { item ->
                            runCatching {
                                val r = item.asJsonObject
                                DayCard.Replaced(
                                    start = LocalTime.parse(r.get("start").asString),
                                    end = LocalTime.parse(r.get("end").asString),
                                    subject = r.text("subject"),
                                    subjectName = r.text("subjectName"),
                                )
                            }.getOrNull()
                        }.orEmpty(),
                    )
                }.getOrNull()
            }.orEmpty(),
        )
    }

    private fun encodeMessage(message: ChatMessage) = JsonObject().apply {
        addProperty("id", message.id)
        addProperty("role", message.role.name)
        addProperty("text", message.text)
        if (message.reasoning.isNotBlank()) addProperty("reasoning", message.reasoning)
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
        reasoning = o.get("reasoning")?.asString.orEmpty(),
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
