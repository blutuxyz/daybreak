package com.bdersolutions.daybreak.data.assistant

import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisLesson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Who wrote a chat message. TOOL messages carry the result of a tool the app ran; they are not shown. */
enum class ChatRole { USER, ASSISTANT, TOOL }

/** A picture or file attached to a question. [base64] is what is sent; it lives in memory only. */
data class ChatAttachment(
    val kind: Kind,
    val name: String,
    val mime: String,
    val base64: String,
    val sizeBytes: Long,
) {
    enum class Kind { IMAGE, FILE }

    // The data is long; keep it out of logs and toString.
    override fun toString(): String = "ChatAttachment($kind, $name, $mime, $sizeBytes bytes)"
}

/** A tool the model wants the app to run. [arguments] is the JSON the model wrote. */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/** One message of the assistant chat. The chat lives in memory only and is never stored. */
data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    /** For [ChatRole.TOOL]: the call this is the result of. */
    val toolCallId: String? = null,
    /** For [ChatRole.ASSISTANT]: what the model reasoned before answering, when it shows that. Never sent back. */
    val reasoning: String = "",
)

/** A model the admin allows, as the app shows it in the picker. */
data class AssistantModel(val id: String, val label: String)

/** The answer of `GET /v1/assistant/models`. */
data class AssistantModels(
    val showSwitcher: Boolean,
    val models: List<AssistantModel>,
    val defaultModel: String?,
    /** The reasoning-effort picker, when the admin turned it on. */
    val showEffort: Boolean = false,
    val efforts: List<String> = emptyList(),
    val defaultEffort: String? = null,
)

/** What the server streams back, one event at a time. */
sealed interface AssistantEvent {
    data class Text(val text: String) : AssistantEvent
    data class Reasoning(val text: String) : AssistantEvent
    data class ToolCalls(val calls: List<ToolCall>) : AssistantEvent
    data object Done : AssistantEvent
    data class Failure(val reason: AssistantFailure) : AssistantEvent
}

/** Why an answer did not come. Each has its own text in the app. */
enum class AssistantFailure {
    /** No connection to the server. */
    OFFLINE,

    /** Too many questions in a short time. */
    BUSY,

    /** OpenRouter or the model's provider failed, or the assistant is switched off on the server. */
    UPSTREAM,

    /** Not available right now. Also what a used-up daily budget looks like: the app does not say more. */
    UNAVAILABLE,

    /** The question or chat was not accepted (too long, for example). */
    REJECTED,
}

/**
 * The request body and the server-sent events of `POST /v1/assistant` (update-server/src/assistant.js).
 * Pure, so it is unit-tested and the same for every client.
 */
object AssistantProtocol {
    const val MAX_MESSAGES = 48
    const val MAX_TEXT_CHARS = 4000
    const val MAX_TOOL_CHARS = 20_000
    const val MAX_CONTEXT_CHARS = 40_000

    fun requestBody(
        messages: List<ChatMessage>,
        context: String,
        language: String,
        today: LocalDate,
        model: String? = null,
        unlockCode: String? = null,
        effort: String? = null,
    ): String {
        val sent = prepared(messages)
        // Pictures and files are sent with the newest question that has some; older ones become a note,
        // so a long chat does not send the same picture again and again.
        // A reopened chat keeps only the names of PDFs and text files; those cannot be sent again.
        val withData = sent.lastOrNull { message -> message.role == ChatRole.USER && message.attachments.any { it.base64.isNotBlank() } }?.id
        val array = JsonArray()
        sent.forEach { message ->
            val json = JsonObject()
            when (message.role) {
                ChatRole.USER -> {
                    json.addProperty("role", "user")
                    var text = message.text.trim()
                    if (message.attachments.isNotEmpty() && message.id != withData) {
                        val names = message.attachments.joinToString { it.name }
                        text = listOf(text, "[earlier attachment: $names]").filter { it.isNotBlank() }.joinToString("\n")
                    }
                    json.addProperty("text", text.take(MAX_TEXT_CHARS))
                    if (message.id == withData) {
                        val attachments = JsonArray()
                        message.attachments.filter { it.base64.isNotBlank() }.take(4).forEach { attachment ->
                            attachments.add(
                                JsonObject().apply {
                                    addProperty("type", if (attachment.kind == ChatAttachment.Kind.IMAGE) "image" else "file")
                                    addProperty("name", attachment.name.take(120))
                                    addProperty("mime", attachment.mime)
                                    addProperty("data", attachment.base64)
                                },
                            )
                        }
                        json.add("attachments", attachments)
                    }
                }

                ChatRole.ASSISTANT -> {
                    json.addProperty("role", "assistant")
                    json.addProperty("text", message.text.take(MAX_TOOL_CHARS))
                    if (message.toolCalls.isNotEmpty()) {
                        val calls = JsonArray()
                        message.toolCalls.forEach { call ->
                            calls.add(
                                JsonObject().apply {
                                    addProperty("id", call.id)
                                    addProperty("name", call.name)
                                    addProperty("arguments", call.arguments.take(8000))
                                },
                            )
                        }
                        json.add("toolCalls", calls)
                    }
                }

                ChatRole.TOOL -> {
                    json.addProperty("role", "tool")
                    json.addProperty("toolCallId", message.toolCallId)
                    json.addProperty("content", message.text.take(MAX_TOOL_CHARS))
                }
            }
            array.add(json)
        }
        return JsonObject().apply {
            addProperty("language", if (language == "de") "de" else "en")
            addProperty("today", today.toString())
            addProperty("context", context.take(MAX_CONTEXT_CHARS))
            if (!model.isNullOrBlank()) addProperty("model", model)
            if (!unlockCode.isNullOrBlank()) addProperty("unlockCode", unlockCode)
            if (!effort.isNullOrBlank()) addProperty("effort", effort)
            add("messages", array)
        }.toString()
    }

    /**
     * The chat as the server accepts it: empty messages dropped, every tool call followed by its result
     * (a call that never ran, because the student stopped or an error came, gets a "cancelled" result),
     * nothing after the last question or result, starting with a question, and at most [MAX_MESSAGES]
     * (older turns are cut off at a question, so call and result stay together).
     */
    fun prepared(messages: List<ChatMessage>): List<ChatMessage> {
        val kept = messages.filter { message ->
            when (message.role) {
                ChatRole.USER -> message.text.isNotBlank() || message.attachments.isNotEmpty()
                ChatRole.ASSISTANT -> message.text.isNotBlank() || message.toolCalls.isNotEmpty()
                ChatRole.TOOL -> message.toolCallId != null
            }
        }
        val out = mutableListOf<ChatMessage>()
        var index = 0
        while (index < kept.size) {
            val message = kept[index]
            when (message.role) {
                ChatRole.TOOL -> index++ // a result without its call (handled with the call below)
                ChatRole.USER -> {
                    out += message
                    index++
                }

                ChatRole.ASSISTANT -> {
                    out += message
                    index++
                    if (message.toolCalls.isNotEmpty()) {
                        val results = mutableMapOf<String, ChatMessage>()
                        while (index < kept.size && kept[index].role == ChatRole.TOOL) {
                            kept[index].toolCallId?.let { results.putIfAbsent(it, kept[index]) }
                            index++
                        }
                        message.toolCalls.forEach { call ->
                            out += results[call.id]
                                ?: ChatMessage(message.id, ChatRole.TOOL, "Not run: cancelled.", toolCallId = call.id)
                        }
                    }
                }
            }
        }
        while (out.isNotEmpty() && out.last().role == ChatRole.ASSISTANT) out.removeAt(out.lastIndex)
        while (out.isNotEmpty() && out.first().role != ChatRole.USER) out.removeAt(0)
        if (out.size <= MAX_MESSAGES) return out
        var start = out.size - MAX_MESSAGES
        while (start < out.size && out[start].role != ChatRole.USER) start++
        return if (start < out.size) out.subList(start, out.size).toList() else listOf(out.last { it.role == ChatRole.USER })
    }

    /** Reads one `data:` line of the event stream; null for anything that is not an event. */
    fun parseEvent(line: String): AssistantEvent? {
        if (!line.startsWith("data:")) return null
        val json = runCatching { JsonParser.parseString(line.removePrefix("data:").trim()).asJsonObject }.getOrNull()
            ?: return null
        return when (json.string("type")) {
            "text" -> json.string("text")?.let { AssistantEvent.Text(it) }
            "reasoning" -> json.string("text")?.let { AssistantEvent.Reasoning(it) }
            "tool_calls" -> {
                val calls = (json.get("calls") as? JsonArray)?.mapNotNull { element ->
                    val call = element as? JsonObject ?: return@mapNotNull null
                    val id = call.string("id") ?: return@mapNotNull null
                    val name = call.string("name") ?: return@mapNotNull null
                    ToolCall(id, name, call.string("arguments") ?: "{}")
                }.orEmpty()
                if (calls.isEmpty()) null else AssistantEvent.ToolCalls(calls)
            }
            "done" -> AssistantEvent.Done
            "error" -> AssistantEvent.Failure(failureFor(json.string("code")))
            else -> null
        }
    }

    /** Maps the server's error code to a [AssistantFailure]. A used-up budget ("limit") looks like any outage. */
    fun failureFor(code: String?): AssistantFailure = when (code) {
        "upstream" -> AssistantFailure.UPSTREAM
        "busy", "too_many_requests" -> AssistantFailure.BUSY
        "bad_request", "context_too_long", "message_too_long", "too_many_messages", "bad_attachment",
        "attachments_too_large", "empty_message" -> AssistantFailure.REJECTED
        else -> AssistantFailure.UNAVAILABLE
    }

    fun failureForStatus(status: Int): AssistantFailure = when (status) {
        429 -> AssistantFailure.BUSY
        400, 413 -> AssistantFailure.REJECTED
        else -> AssistantFailure.UNAVAILABLE
    }

    /** The body of `POST /v1/assistant/title`: the first question and answer of a chat, shortened. */
    fun titleRequestBody(question: String, answer: String, language: String, model: String? = null): String = JsonObject().apply {
        addProperty("language", if (language == "de") "de" else "en")
        addProperty("question", question.trim().take(600))
        addProperty("answer", answer.trim().take(1500))
        if (!model.isNullOrBlank()) addProperty("model", model)
    }.toString()

    /** Reads `{"title": "…"}`; null for anything else or a blank title. */
    fun parseTitle(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        return json.string("title")?.replace(Regex("\\s+"), " ")?.trim()?.take(60)?.takeIf { it.isNotBlank() }
    }

    /** Reads `GET /v1/assistant/models`; null for anything unusable. */
    fun parseModels(body: String?): AssistantModels? {
        if (body.isNullOrBlank()) return null
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val models = (json.get("models") as? JsonArray)?.mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val id = model.string("id") ?: return@mapNotNull null
            AssistantModel(id, model.string("label") ?: id)
        }.orEmpty()
        val show = json.get("showSwitcher")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: models.isNotEmpty()
        val efforts = (json.get("efforts") as? JsonArray)?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty()
        return AssistantModels(
            showSwitcher = show && models.size > 1,
            models = models,
            defaultModel = json.string("defaultModel"),
            showEffort = (json.get("showEffort")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false) && efforts.isNotEmpty(),
            efforts = efforts,
            defaultEffort = json.string("defaultEffort"),
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}

/**
 * Turns the student's cached data into the short plain-text summary the assistant answers from. Only what
 * a question about the own school week needs goes in: lessons (subject, time, teacher's short name, room,
 * changes, notes), exams, homework, holidays and absences. No name, no login, no school address. Older or
 * later dates, and everything in more detail, the model asks for through the tools ([AssistantToolRunner]).
 */
object AssistantContext {
    internal val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", java.util.Locale.ENGLISH)
    internal val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Weeks around today that are sent: last week, this week and the next two. */
    private const val WEEKS_BEFORE = 1L
    private const val WEEKS_AFTER = 2L

    fun build(
        cache: TimetableCache,
        now: LocalDateTime,
        homework: List<UntisHomework> = emptyList(),
        maxChars: Int = AssistantProtocol.MAX_CONTEXT_CHARS,
    ): String {
        val today = now.toLocalDate()
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from = thisMonday.minusWeeks(WEEKS_BEFORE)
        val to = thisMonday.plusWeeks(WEEKS_AFTER + 1).minusDays(1)

        val out = StringBuilder()
        out.appendLine("Now: ${now.toLocalDate().format(DAY)} ${now.toLocalTime().format(TIME)}")

        val weeks = cache.weeks.values
            .filter { !it.weekStart.isBefore(from) && !it.weekStart.isAfter(to) }
            .sortedBy { it.weekStart }
        out.appendLine()
        if (weeks.isEmpty()) {
            out.appendLine("Lessons: none loaded yet (use get_lessons).")
        } else {
            out.appendLine(
                "Loaded weeks (Monday): " + weeks.joinToString { it.weekStart.toString() } +
                    ". For other weeks use get_lessons.",
            )
            out.appendLine("Lessons:")
            weeks.forEach { week ->
                val byDay = week.lessons.filterNot { it.isHoliday }.groupBy { it.date }.toSortedMap()
                if (byDay.isEmpty()) out.appendLine("  week of ${week.weekStart}: no lessons")
                byDay.forEach { (date, lessons) ->
                    out.appendLine("  ${date.format(DAY)}${if (date == today) " (today)" else ""}")
                    lessons.sortedWith(compareBy({ it.start }, { it.subject })).forEach { lesson ->
                        out.appendLine("    ${lessonLine(lesson)}")
                    }
                }
            }
        }

        val exams = cache.exams.filter { !it.date.isBefore(today.minusDays(7)) }.sortedBy { it.date }
        out.appendLine()
        if (exams.isEmpty()) {
            out.appendLine("Exams: none in the cache (use get_exams).")
        } else {
            out.appendLine("Exams:")
            exams.take(30).forEach { exam ->
                out.append("  ${exam.date.format(DAY)} ${exam.start.format(TIME)}-${exam.end.format(TIME)} ${exam.subject.ifBlank { "?" }}")
                if (exam.name.isNotBlank()) out.append(": ${exam.name.oneLine()}")
                if (exam.text.isNotBlank()) out.append(" (${exam.text.oneLine()})")
                out.appendLine()
            }
        }

        val dueHomework = homework.filter { !it.due.isBefore(today.minusDays(1)) }.sortedBy { it.due }
        if (dueHomework.isNotEmpty()) {
            out.appendLine()
            out.appendLine("Homework:")
            dueHomework.take(40).forEach { item -> out.appendLine("  ${homeworkLine(item)}") }
        }

        val holidays = cache.holidays.filter { !it.end.isBefore(today) }.sortedBy { it.start }
        if (holidays.isNotEmpty()) {
            out.appendLine()
            out.appendLine("Holidays and free days:")
            holidays.take(12).forEach { holiday ->
                val name = holiday.displayName.ifBlank { "free day" }
                if (holiday.start == holiday.end) {
                    out.appendLine("  ${holiday.start.format(DAY)}: $name")
                } else {
                    out.appendLine("  ${holiday.start.format(DAY)} to ${holiday.end.format(DAY)}: $name")
                }
            }
        }

        if (cache.absences.isNotEmpty()) {
            val total = cache.absences.fold(Duration.ZERO) { sum, absence -> sum + absence.duration }
            val unexcused = cache.absences.count { !it.excused }
            out.appendLine()
            out.appendLine(
                "Absences: ${cache.absences.size} entries, ${total.toMinutes()} minutes recorded, $unexcused not excused (details: get_absences).",
            )
        }

        val text = out.toString().trimEnd()
        return if (text.length <= maxChars) text else text.take(maxChars - 20).trimEnd() + "\n[cut off]"
    }

    /** One lesson as one line: time, subject, teacher, room, and in brackets every change and note. */
    internal fun lessonLine(lesson: UntisLesson): String {
        val out = StringBuilder()
        out.append("${lesson.start.format(TIME)}-${lesson.end.format(TIME)} ")
        out.append(lesson.subjectLong.ifBlank { lesson.subject }.ifBlank { "?" })
        if (lesson.subjectLong.isNotBlank() && lesson.subject.isNotBlank() && lesson.subject != lesson.subjectLong) {
            out.append(" (${lesson.subject})")
        }
        if (lesson.teacher.isNotBlank()) out.append(", teacher ${lesson.teacher}")
        if (lesson.room.isNotBlank()) out.append(", room ${lesson.room}")
        val notes = buildList {
            if (lesson.isCancelled) add("CANCELLED")
            if (lesson.isIrregular) add("irregular/extra lesson")
            if (lesson.hasRoomChange) add("room changed from ${lesson.originalRoom}")
            if (lesson.hasTeacherChange) add("substitute, normally ${lesson.originalTeacher}")
            if (lesson.substitutionText.isNotBlank()) add("note: ${lesson.substitutionText.oneLine()}")
            if (lesson.lessonText.isNotBlank()) add("lesson text: ${lesson.lessonText.oneLine()}")
            if (lesson.info.isNotBlank()) add("info: ${lesson.info.oneLine()}")
        }
        if (notes.isNotEmpty()) out.append(" [${notes.joinToString("; ")}]")
        return out.toString()
    }

    internal fun homeworkLine(item: UntisHomework): String {
        val out = StringBuilder("due ${item.due.format(DAY)} ${item.subject.ifBlank { "?" }}: ${item.text.oneLine()}")
        if (item.remark.isNotBlank()) out.append(" (${item.remark.oneLine()})")
        if (item.completed) out.append(" [done]")
        return out.toString()
    }

    internal fun String.oneLine(max: Int = 240): String =
        replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(max)
}
