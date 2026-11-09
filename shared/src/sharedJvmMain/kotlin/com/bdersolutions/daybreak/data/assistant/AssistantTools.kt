package com.bdersolutions.daybreak.data.assistant

import com.bdersolutions.daybreak.data.assistant.AssistantContext.DAY
import com.bdersolutions.daybreak.data.assistant.AssistantContext.TIME
import com.bdersolutions.daybreak.data.assistant.AssistantContext.oneLine
import com.bdersolutions.daybreak.untis.RecipientGroup
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.untis.UntisRecipient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

/** The student's data the tools read. The app backs it with the repositories; tests with a fake. */
interface AssistantData {
    /** Lessons between two days (both included), loading weeks that are not cached; null if none could be loaded. */
    suspend fun lessons(from: LocalDate, to: LocalDate): List<UntisLesson>?

    suspend fun exams(from: LocalDate, to: LocalDate): List<UntisExam>

    suspend fun homework(from: LocalDate, to: LocalDate): List<UntisHomework>

    fun absences(): List<UntisAbsence>

    suspend fun teachers(): List<UntisRecipient>

    /** False while the developer switched writing messages off (RemoteFeatures.MESSAGES_SEND). */
    val canSendMessages: Boolean
}

/** A message the model drafted. Nothing is sent until the student confirms it in the chat. */
data class MessageDraft(
    val callId: String,
    val recipientUserId: Long,
    val recipientName: String,
    val subject: String,
    val body: String,
)

/** What running a tool came to. */
sealed interface ToolOutcome {
    /** The text that goes back to the model. */
    data class Result(val content: String) : ToolOutcome

    /** A message to send; the student has to confirm it first. */
    data class Confirm(val draft: MessageDraft) : ToolOutcome
}

/**
 * Runs the assistant's tools (update-server/src/assistant.js lists them) on the student's data. The
 * results are short plain text for the model. Nothing here sends anything: [ToolOutcome.Confirm] hands a
 * drafted message to the chat, which sends it only after the student taps "Send".
 */
class AssistantToolRunner(
    private val data: AssistantData,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun run(call: ToolCall): ToolOutcome = try {
        val args = runCatching { JsonParser.parseString(call.arguments.ifBlank { "{}" }).asJsonObject }.getOrElse { JsonObject() }
        when (call.name) {
            "get_lessons" -> lessons(args)
            "get_exams" -> exams(args)
            "get_lesson_notes" -> lessonNotes(args)
            "get_homework" -> homework(args)
            "get_absences" -> absences()
            "list_teachers" -> teachers()
            "draft_message" -> draft(call.id, args)
            else -> ToolOutcome.Result("Unknown tool ${call.name}.")
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // The model gets told, and can explain it; details stay on the phone.
        ToolOutcome.Result("Error: the data could not be loaded (${error.javaClass.simpleName}). The student may be offline.")
    }

    private suspend fun lessons(args: JsonObject): ToolOutcome {
        val start = args.date("start_date") ?: today()
        var end = args.date("end_date") ?: start
        if (end.isBefore(start)) return ToolOutcome.Result("Error: end_date is before start_date.")
        if (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > MAX_DAYS) end = start.plusDays(MAX_DAYS)
        val lessons = data.lessons(start, end)?.filterNot { it.isHoliday }
            ?: return ToolOutcome.Result("Error: the timetable for $start to $end could not be loaded.")
        if (lessons.isEmpty()) return ToolOutcome.Result("No lessons from ${start.format(DAY)} to ${end.format(DAY)}.")
        val out = StringBuilder("Lessons ${start.format(DAY)} to ${end.format(DAY)}:\n")
        lessons.groupBy { it.date }.toSortedMap().forEach { (date, dayLessons) ->
            out.appendLine(date.format(DAY))
            dayLessons.sortedBy { it.start }.forEach { out.appendLine("  ${AssistantContext.lessonLine(it)}") }
        }
        return ToolOutcome.Result(out.toString().trimEnd())
    }

    private suspend fun exams(args: JsonObject): ToolOutcome {
        val start = args.date("start_date") ?: today()
        val end = args.date("end_date") ?: start.plusDays(120)
        val exams = data.exams(start, end).sortedWith(compareBy({ it.date }, { it.start }))
        if (exams.isEmpty()) return ToolOutcome.Result("No exams from ${start.format(DAY)} to ${end.format(DAY)}.")
        return ToolOutcome.Result(
            exams.joinToString("\n", prefix = "Exams:\n") { exam ->
                buildString {
                    append("${exam.date.format(DAY)} ${exam.start.format(TIME)}-${exam.end.format(TIME)} ${exam.subject.ifBlank { "?" }}")
                    if (exam.name.isNotBlank()) append(": ${exam.name.oneLine()}")
                    if (exam.text.isNotBlank()) append(" | text: ${exam.text.oneLine(1000)}")
                }
            },
        )
    }

    private suspend fun lessonNotes(args: JsonObject): ToolOutcome {
        val subject = args.string("subject")?.trim().orEmpty()
        if (subject.isEmpty()) return ToolOutcome.Result("Error: subject is missing.")
        val weeksBack = (args.int("weeks_back") ?: 6).coerceIn(1, 12)
        val start = today().minusWeeks(weeksBack.toLong())
        // Teachers also write notes on lessons still to come ("test on Thursday: units 3 and 4").
        val end = today().plusWeeks(2)
        val lessons = data.lessons(start, end)
            ?: return ToolOutcome.Result("Error: the timetable could not be loaded.")
        val needle = subject.lowercase()
        val matching = lessons.filter { lesson ->
            !lesson.isHoliday && listOf(lesson.subject, lesson.subjectLong).any { it.isNotBlank() && (it.lowercase().contains(needle) || needle.contains(it.lowercase())) }
        }
        if (matching.isEmpty()) {
            val known = lessons.map { it.subjectLong.ifBlank { it.subject } }.filter { it.isNotBlank() }.distinct().sorted()
            return ToolOutcome.Result("No lessons of \"$subject\" found. Subjects in the timetable: ${known.joinToString()}.")
        }
        val notes = matching
            .filter { it.lessonText.isNotBlank() || it.info.isNotBlank() || it.substitutionText.isNotBlank() }
            .sortedByDescending { it.startsAt }
        if (notes.isEmpty()) {
            return ToolOutcome.Result(
                "${matching.size} lessons of \"$subject\" from ${start.format(DAY)} to ${end.format(DAY)}, but the teachers wrote no lesson text or info on any of them.",
            )
        }
        val out = StringBuilder("Notes on \"$subject\" lessons, newest first (${notes.size} of ${matching.size} lessons have notes):\n")
        notes.take(60).forEach { lesson ->
            out.append("${lesson.date.format(DAY)} ${lesson.start.format(TIME)}")
            if (lesson.lessonText.isNotBlank()) out.append(" | lesson text: ${lesson.lessonText.oneLine(800)}")
            if (lesson.info.isNotBlank()) out.append(" | info: ${lesson.info.oneLine(800)}")
            if (lesson.substitutionText.isNotBlank()) out.append(" | note: ${lesson.substitutionText.oneLine(300)}")
            out.appendLine()
        }
        return ToolOutcome.Result(out.toString().trimEnd())
    }

    private suspend fun homework(args: JsonObject): ToolOutcome {
        val start = args.date("start_date") ?: today().minusDays(7)
        val end = args.date("end_date") ?: today().plusDays(21)
        val items = data.homework(start, end).sortedBy { it.due }
        if (items.isEmpty()) return ToolOutcome.Result("No homework due from ${start.format(DAY)} to ${end.format(DAY)}.")
        return ToolOutcome.Result(items.take(60).joinToString("\n", prefix = "Homework:\n") { AssistantContext.homeworkLine(it) })
    }

    private fun absences(): ToolOutcome {
        val absences = data.absences().sortedByDescending { it.start }
        if (absences.isEmpty()) return ToolOutcome.Result("No absences recorded.")
        val total = absences.fold(Duration.ZERO) { sum, absence -> sum + absence.duration }
        val unexcused = absences.filterNot { it.excused }
        val out = StringBuilder(
            "Absences: ${absences.size} entries, ${total.toMinutes()} minutes recorded " +
                "(${unexcused.size} entries, ${unexcused.fold(Duration.ZERO) { s, a -> s + a.duration }.toMinutes()} minutes not excused). " +
                "A whole recorded day can be longer than the lessons of that day.\n",
        )
        absences.take(40).forEach { absence ->
            out.append("${absence.start.toLocalDate().format(DAY)} ${absence.start.toLocalTime().format(TIME)}-")
            if (absence.end.toLocalDate() != absence.start.toLocalDate()) out.append("${absence.end.toLocalDate()} ")
            out.append(absence.end.toLocalTime().format(TIME))
            out.append(if (absence.excused) ", excused" else ", not excused")
            if (absence.reasonName.isNotBlank()) out.append(", ${absence.reasonName.oneLine()}")
            out.appendLine()
        }
        return ToolOutcome.Result(out.toString().trimEnd())
    }

    private suspend fun teachers(): ToolOutcome {
        if (!data.canSendMessages) return ToolOutcome.Result("Writing messages is switched off in this app right now.")
        val people = data.teachers().filter { it.group != RecipientGroup.OTHER }
        if (people.isEmpty()) return ToolOutcome.Result("The school lets this student write to no teacher.")
        return ToolOutcome.Result(
            people.joinToString("\n", prefix = "Teachers the student may write to:\n") { person ->
                buildString {
                    append("user_id ${person.userId}: ${person.name}")
                    if (person.group == RecipientGroup.CLASS_TEACHER) append(" (class teacher)")
                    if (person.tags.isNotEmpty()) append(" – ${person.tags.joinToString()}")
                }
            },
        )
    }

    private suspend fun draft(callId: String, args: JsonObject): ToolOutcome {
        if (!data.canSendMessages) return ToolOutcome.Result("Writing messages is switched off in this app right now. Nothing was sent.")
        val userId = args.long("recipient_user_id") ?: return ToolOutcome.Result("Error: recipient_user_id is missing. Call list_teachers first.")
        val subject = args.string("subject")?.trim().orEmpty()
        val body = args.string("body")?.trim().orEmpty()
        if (subject.isEmpty() || body.isEmpty()) return ToolOutcome.Result("Error: subject and body are needed. Nothing was sent.")
        if (subject.length > 200 || body.length > 8000) return ToolOutcome.Result("Error: the message is too long. Nothing was sent.")
        // Only a teacher from the school's own list: a made-up id never reaches the send button.
        val recipient = data.teachers().firstOrNull { it.userId == userId && it.group != RecipientGroup.OTHER }
            ?: return ToolOutcome.Result("Error: user_id $userId is not a teacher this student may write to. Nothing was sent. Call list_teachers.")
        return ToolOutcome.Confirm(MessageDraft(callId, recipient.userId, recipient.name, subject, body))
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.long(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() ?: it.asString.toLongOrNull() }

    private fun JsonObject.int(name: String): Int? = long(name)?.toInt()

    private fun JsonObject.date(name: String): LocalDate? = string(name)?.let {
        try {
            LocalDate.parse(it.take(10))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    companion object {
        private const val MAX_DAYS = 31L

        /** What goes back to the model after the student answered a draft. */
        fun sentResult(draft: MessageDraft): String =
            "sent: the message \"${draft.subject}\" was sent to ${draft.recipientName}."

        fun cancelledResult(): String = "cancelled: the student did not send the message. Nothing was sent."

        fun failedResult(reason: String): String = "not sent: $reason"
    }
}
