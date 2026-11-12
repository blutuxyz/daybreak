package com.bdersolutions.daybreak.data.assistant

import com.bdersolutions.daybreak.data.assistant.AssistantContext.DAY
import com.bdersolutions.daybreak.data.assistant.AssistantContext.TIME
import com.bdersolutions.daybreak.data.assistant.AssistantContext.oneLine
import com.bdersolutions.daybreak.ui.absences.SchoolDayWindows
import com.bdersolutions.daybreak.ui.absences.absenceWeekStarts
import com.bdersolutions.daybreak.untis.RecipientGroup
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.untis.UntisRecipient
import com.bdersolutions.daybreak.data.settings.AssistantSettingsPort
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import com.bdersolutions.daybreak.data.schedule.LessonRun
import com.bdersolutions.daybreak.data.schedule.NowAndNext
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

/** An amount of minutes as hours for the model and the summary: "20 hours", or "1.5 hours" when not whole. */
internal fun hoursText(minutes: Long): String =
    if (minutes % 60 == 0L) "${minutes / 60} hours" else String.format(java.util.Locale.ROOT, "%.1f hours", minutes / 60.0)

/** The student's data the tools read. The app backs it with the repositories; tests with a fake. */
interface AssistantData {
    /** Lessons between two days (both included), loading weeks that are not cached; null if none could be loaded. */
    suspend fun lessons(from: LocalDate, to: LocalDate): List<UntisLesson>?

    suspend fun exams(from: LocalDate, to: LocalDate): List<UntisExam>

    suspend fun homework(from: LocalDate, to: LocalDate): List<UntisHomework>

    /** The student's absences of this school year, or null when they could not be loaded. */
    suspend fun absences(): List<UntisAbsence>?

    /**
     * What teachers entered in the class register for these lessons (the lesson topic, "Lehrstoff"), by
     * lesson id. Empty when the school does not show it to students or it could not be loaded.
     */
    suspend fun lessonTopics(lessons: List<UntisLesson>): Map<Long, String> = emptyMap()

    suspend fun teachers(): List<UntisRecipient>

    /** False while the developer switched writing messages off (RemoteFeatures.MESSAGES_SEND). */
    val canSendMessages: Boolean

    /** False while the student's plan does not include message drafts (Pro). */
    val draftsInPlan: Boolean get() = true
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

    /** Something shown to the student as a card; [content] goes back to the model. */
    data class Card(val card: AssistantCard, val content: String) : ToolOutcome
}

/**
 * A day of the timetable the assistant shows as a card (show_timetable). It is made from the student's
 * real lessons, never from what the model wrote, so the card cannot be wrong where the text might be.
 * Double lessons are one entry. Kept in saved chats, so it reads the same later.
 */
data class DayCard(
    val date: LocalDate,
    val entries: List<Entry>,
    /** The holiday or free day it falls in, if any. */
    val holiday: String? = null,
) : AssistantCard {
    data class Entry(
        val start: LocalTime,
        val end: LocalTime,
        /** The short subject name, which the lesson colours are keyed by. */
        val subject: String,
        /** The name to show: the long subject name, or the short one. */
        val subjectName: String,
        val teacher: String = "",
        val room: String = "",
        val cancelled: Boolean = false,
        val irregular: Boolean = false,
        /** The teacher or room the lesson normally has, when it changed. */
        val originalTeacher: String = "",
        val originalRoom: String = "",
        /** The substitution text or the teacher's info, one line. */
        val note: String = "",
        /** The exam written in this lesson, if any. */
        val exam: String? = null,
        val periods: Int = 1,
        /** The cancelled lessons this block takes the place of (a school event, a substitute lesson). */
        val replaces: List<Replaced> = emptyList(),
    )

    /** A cancelled lesson shown under the block that replaces it. */
    data class Replaced(val start: LocalTime, val end: LocalTime, val subject: String, val subjectName: String)
}

/**
 * Runs the assistant's tools (update-server/src/assistant.js lists them) on the student's data. The
 * results are short plain text for the model. Nothing here sends anything: [ToolOutcome.Confirm] hands a
 * drafted message to the chat, which sends it only after the student taps "Send".
 */
class AssistantToolRunner(
    private val data: AssistantData,
    private val today: () -> LocalDate = { LocalDate.now() },
    /** The app's settings, for get_settings and change_settings. Null where there are none (tests). */
    private val settings: AssistantSettingsPort? = null,
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
            "show_timetable" -> showTimetable(args)
            "show_exams" -> showExams(args)
            "show_absences" -> showAbsences()
            "show_website" -> showWebsite(args)
            "get_settings" -> getSettings()
            "change_settings" -> changeSettings(call, args)
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
        // The class register topics of held lessons, for a short range (one request per range).
        val topics = if (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() <= TOPIC_DAYS) {
            data.lessonTopics(lessons.filter { !it.isCancelled && !it.date.isAfter(today()) })
        } else {
            emptyMap()
        }
        val out = StringBuilder("Lessons ${start.format(DAY)} to ${end.format(DAY)}:\n")
        lessons.groupBy { it.date }.toSortedMap().forEach { (date, dayLessons) ->
            out.appendLine(date.format(DAY))
            dayLessons.sortedBy { it.start }.forEach { out.appendLine("  ${AssistantContext.lessonLine(it, topics[it.id])}") }
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
        // The class register topic ("Lehrstoff") is where most teachers write what a lesson covered.
        val topics = data.lessonTopics(
            matching.filter { !it.isCancelled && !it.date.isAfter(today()) }.sortedByDescending { it.startsAt }.take(MAX_TOPIC_LESSONS),
        )
        val notes = matching
            .filter { it.lessonText.isNotBlank() || it.info.isNotBlank() || it.substitutionText.isNotBlank() || !topics[it.id].isNullOrBlank() }
            .sortedByDescending { it.startsAt }
        if (notes.isEmpty()) {
            return ToolOutcome.Result(
                "${matching.size} lessons of \"$subject\" from ${start.format(DAY)} to ${end.format(DAY)}, but the teachers wrote no topic, lesson text or info on any of them.",
            )
        }
        val out = StringBuilder("Notes on \"$subject\" lessons, newest first (${notes.size} of ${matching.size} lessons have notes):\n")
        notes.take(60).forEach { lesson ->
            out.append("${lesson.date.format(DAY)} ${lesson.start.format(TIME)}")
            topics[lesson.id]?.takeIf { it.isNotBlank() }?.let { out.append(" | topic: ${it.oneLine(800)}") }
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

    private suspend fun absences(): ToolOutcome {
        val absences = data.absences()
            ?: return ToolOutcome.Result("Error: the absences could not be loaded. The student may be offline; the Absences tab shows them.")
        if (absences.isEmpty()) return ToolOutcome.Result("No absences recorded this school year.")
        return ToolOutcome.Result(absenceText(AbsenceCards.build(absences, absenceWindows(absences))))
    }

    /** The absences as a card, with the same figures as the Absences tab; the model gets them as text. */
    private suspend fun showAbsences(): ToolOutcome {
        val absences = data.absences()
            ?: return ToolOutcome.Result("Error: the absences could not be loaded. The student may be offline. Nothing was shown.")
        val card = AbsenceCards.build(absences, absenceWindows(absences))
        val content = if (absences.isEmpty()) {
            "Shown to the student as a card: no absences recorded this school year."
        } else {
            "Shown to the student as a card with the totals and every entry:\n" + absenceText(card) +
                "\nDo not list the entries again. Add at most one or two short sentences, for example about open (not excused) absences."
        }
        return ToolOutcome.Card(card, content)
    }

    /** Every setting and its value, so the assistant knows what it can change. */
    private fun getSettings(): ToolOutcome {
        val port = settings ?: return ToolOutcome.Result("Settings cannot be read right now.")
        return ToolOutcome.Result(port.describe())
    }

    /**
     * Changes the settings the student asked for, all at once or none. The change is applied at once and shown in
     * the chat with an Undo, so the student can take it back.
     */
    private fun changeSettings(call: ToolCall, args: JsonObject): ToolOutcome {
        val port = settings ?: return ToolOutcome.Result("Error: settings cannot be changed right now. Nothing was changed.")
        val list = args.get("changes") as? JsonArray
        val changes = LinkedHashMap<String, String>()
        list?.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val path = item.string("path")?.trim().orEmpty()
            val value = item.get("value")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (path.isNotEmpty()) changes[path] = value
        }
        if (changes.isEmpty()) return ToolOutcome.Result("Error: no changes were given. Nothing was changed.")
        val plan = port.apply(changes)
        if (!plan.accepted) {
            return ToolOutcome.Result(
                "Error: nothing was changed. ${plan.errors.joinToString(" ")} Call get_settings for the accepted values, then call change_settings again.",
            )
        }
        if (plan.changes.isEmpty()) return ToolOutcome.Result("Those settings already have these values. Nothing was changed.")
        val summary = plan.changes.joinToString("; ") { "${it.path} from ${it.from} to ${it.to}" }
        return ToolOutcome.Card(
            SettingsChangeCard(call.id, plan.changes),
            "Changed ${plan.changes.size} setting(s): $summary. The student can undo this in the chat. Say briefly, in one or two sentences, what changed.",
        )
    }

    /** A website the model wrote, shown as a card with its preview and code. Nothing is run here. */
    private fun showWebsite(args: JsonObject): ToolOutcome {
        val html = args.string("html")?.trim().orEmpty()
        if (html.isEmpty()) {
            return ToolOutcome.Result("Error: no html was given, so nothing was shown. Call show_website again with the complete page.")
        }
        val title = args.string("title")?.replace(Regex("\\s+"), " ")?.trim()?.take(60)?.ifBlank { null } ?: "Website"
        return ToolOutcome.Card(
            WebsiteCard(title, html),
            "Shown to the student as a website card, which opens a preview and the code. Do not repeat the code. Say in one or two short sentences what the page does.",
        )
    }

    private fun absenceText(card: AbsenceCard): String = buildString {
        append("Absences this school year: ${card.excusedCount + card.openCount} entries. ")
        append("Time that counts (clipped to the lessons of each day, as on the Absences tab): ${hoursText(card.countedMinutes)}")
        append(" (${hoursText(card.excusedMinutes)} excused, ${hoursText(card.openMinutes)} not excused in ${card.openCount} entries)")
        if (card.recordedMinutes != card.countedMinutes) append("; recorded by the school: ${hoursText(card.recordedMinutes)}, since whole days are entered longer than the lessons")
        appendLine(". One school lesson is 50 minutes.")
        card.entries.forEach { appendLine(AbsenceCards.line(it)) }
    }.trimEnd()

    /** When school is on the days of [absences], so whole recorded days count only their lessons. */
    private suspend fun absenceWindows(absences: List<UntisAbsence>): SchoolDayWindows? {
        val weeks = absenceWeekStarts(absences).toList().takeLast(MAX_ABSENCE_WEEKS)
        if (weeks.isEmpty()) return null
        val lessons = mutableListOf<UntisLesson>()
        val known = mutableListOf<LocalDate>()
        for (week in weeks) {
            val loaded = runCatching { data.lessons(week, week.plusDays(6)) }.getOrNull() ?: continue
            lessons += loaded
            known += week
        }
        return SchoolDayWindows.from(lessons, known)
    }

    /** Exams as a card; the model gets them as text. Defaults to the next 60 days. */
    private suspend fun showExams(args: JsonObject): ToolOutcome {
        val start = args.date("start_date") ?: today()
        var end = args.date("end_date") ?: start.plusDays(DEFAULT_EXAM_DAYS)
        if (end.isBefore(start)) return ToolOutcome.Result("Error: end_date is before start_date. Nothing was shown.")
        if (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > MAX_EXAM_DAYS) end = start.plusDays(MAX_EXAM_DAYS)
        val exams = data.exams(start, end)
        val dates = exams.map { it.date }.distinct().sorted().take(ExamCards.MAX_EXAMS)
        val lessons = dates.associateWith { date -> runCatching { data.lessons(date, date) }.getOrNull().orEmpty() }
        val card = ExamCards.build(start, end, exams, lessons)
        val content = if (card.exams.isEmpty()) {
            "Shown to the student as a card: no exams from ${start.format(DAY)} to ${end.format(DAY)}."
        } else {
            buildString {
                appendLine("Shown to the student as a card with these exams (date, time, subject, teacher, room and the school's text):")
                card.exams.forEach { appendLine("  ${ExamCards.line(it)}") }
                append("Today is ${today().format(DAY)}. Do not list the exams again. Add at most one or two short sentences (how soon, what to study), or nothing.")
            }
        }
        return ToolOutcome.Card(card, content.trimEnd())
    }

    /** One day as a card for the student; the model gets the same lessons as text and is told not to repeat them. */
    private suspend fun showTimetable(args: JsonObject): ToolOutcome {
        val date = args.date("date") ?: today()
        val lessons = data.lessons(date, date)
            ?: return ToolOutcome.Result("Error: the timetable for ${date.format(DAY)} could not be loaded. Nothing was shown.")
        val exams = runCatching { data.exams(date, date) }.getOrDefault(emptyList())
        val card = DayCards.build(date, lessons, exams)
        val notes = lessons.filter { !it.isHoliday && it.lessonText.isNotBlank() }
        val content = buildString {
            append("Shown to the student as a card for ${date.format(DAY)}")
            if (card.holiday != null) append(" (${card.holiday})")
            if (card.entries.isEmpty()) {
                append(": no lessons.")
            } else {
                append(". The card already shows all of this, grouped the same way:\n")
                val held = card.entries.filterNot { it.cancelled }
                val alone = card.entries.filter { it.cancelled }
                if (held.isNotEmpty()) {
                    appendLine("Taking place:")
                    held.forEach { appendLine("  ${DayCards.line(it)}") }
                }
                if (alone.isNotEmpty()) {
                    appendLine("Cancelled with nothing in their place (free time):")
                    alone.forEach { appendLine("  ${DayCards.line(it)}") }
                }
                notes.forEach { appendLine("  lesson text ${it.start.format(TIME)} ${it.subject}: ${it.lessonText.oneLine()}") }
                append(
                    "Do not list the lessons again. Add at most one or two short sentences on what stands out " +
                        "(what replaces what, free time, exams, room changes), or nothing if the day is ordinary.",
                )
            }
        }
        return ToolOutcome.Card(card, content.trimEnd())
    }

    private suspend fun teachers(): ToolOutcome {
        if (!data.draftsInPlan) return ToolOutcome.Result(PLAN_DRAFTS)
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
        if (!data.draftsInPlan) return ToolOutcome.Result(PLAN_DRAFTS)
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
        private const val PLAN_DRAFTS = "Message drafts are part of the Pro plan, which this student does not have. Tell them so in one sentence and do not try again. Nothing was sent."
        private const val MAX_DAYS = 31L

        /** Ranges up to this many days get the class register topics (they cost one more request). */
        private const val TOPIC_DAYS = 14L
        private const val MAX_TOPIC_LESSONS = 80
        private const val DEFAULT_EXAM_DAYS = 60L
        private const val MAX_EXAM_DAYS = 180L

        /** Weeks of lessons loaded to clip whole recorded days; older ones keep the recorded time. */
        private const val MAX_ABSENCE_WEEKS = 20

        /** What goes back to the model after the student answered a draft. */
        fun sentResult(draft: MessageDraft): String =
            "sent: the message \"${draft.subject}\" was sent to ${draft.recipientName}."

        fun cancelledResult(): String = "cancelled: the student did not send the message. Nothing was sent."

        fun failedResult(reason: String): String = "not sent: $reason"
    }
}

/**
 * Builds the card of one day from the lessons and exams. Pure, so it is tested.
 *
 * Held lessons and cancelled ones are merged into double lessons separately, then each cancelled block is
 * put under the held block it overlaps most: a school event from 07:55 to 13:15 then shows once, with the
 * four lessons it replaces under it, instead of the cancelled lessons and the event interleaved by time.
 * Only a cancellation with nothing in its place keeps a row of its own, which is real free time.
 */
object DayCards {
    fun build(date: LocalDate, lessons: List<UntisLesson>, exams: List<UntisExam>): DayCard {
        val holiday = lessons.firstOrNull { it.isHoliday }?.let { it.subjectLong.ifBlank { it.subject } }
        val day = lessons.filter { it.date == date && !it.isHoliday }
        val held = NowAndNext.runs(day.filterNot { it.isCancelled })
        val cancelled = NowAndNext.runs(day.filter { it.isCancelled }, includeCancelled = true)

        val replacedBy = mutableMapOf<Int, MutableList<LessonRun>>()
        val alone = mutableListOf<LessonRun>()
        for (run in cancelled) {
            val host = held.indices
                .map { it to overlapMinutes(held[it], run) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first
            if (host != null) replacedBy.getOrPut(host) { mutableListOf() } += run else alone += run
        }

        val entries = held.mapIndexed { index, run ->
            entry(date, run, exams).copy(
                replaces = replacedBy[index].orEmpty().map { replaced ->
                    DayCard.Replaced(replaced.start, replaced.end, replaced.lesson.subject, replaced.lesson.subjectLong.ifBlank { replaced.lesson.subject })
                },
            )
        } + alone.map { entry(date, it, exams) }
        return DayCard(date, entries.sortedWith(compareBy({ it.start }, { it.cancelled })), holiday?.takeIf { it.isNotBlank() })
    }

    /** One entry as a line for the model. */
    fun line(entry: DayCard.Entry): String = buildString {
        append("${entry.start.format(TIME)}-${entry.end.format(TIME)} ${entry.subjectName.ifBlank { entry.subject }.ifBlank { "?" }}")
        if (entry.teacher.isNotBlank()) append(", teacher ${entry.teacher}")
        if (entry.room.isNotBlank()) append(", room ${entry.room}")
        val notes = buildList {
            if (entry.cancelled) add("CANCELLED")
            if (entry.irregular) add("irregular/extra lesson")
            if (entry.originalTeacher.isNotBlank()) add("substitute, normally ${entry.originalTeacher}")
            if (entry.originalRoom.isNotBlank()) add("room changed from ${entry.originalRoom}")
            if (entry.note.isNotBlank()) add("note: ${entry.note}")
            entry.exam?.let { add("EXAM: $it") }
            if (entry.replaces.isNotEmpty()) {
                add("in place of the cancelled " + entry.replaces.joinToString { "${it.subjectName} ${it.start.format(TIME)}-${it.end.format(TIME)}" })
            }
        }
        if (notes.isNotEmpty()) append(" [${notes.joinToString("; ")}]")
    }

    private fun entry(date: LocalDate, run: LessonRun, exams: List<UntisExam>): DayCard.Entry {
        val lesson = run.lesson
        val exam = exams.firstOrNull { exam ->
            exam.date == date && exam.start < run.end && run.start < exam.end &&
                (exam.subject.isBlank() || exam.subject.equals(lesson.subject, ignoreCase = true))
        }
        val subjectName = lesson.subjectLong.ifBlank { lesson.subject }
        // A note that only repeats the subject ("Schulveranstaltung" on a "Schulveranstaltung") says nothing.
        val note = lesson.substitutionText.ifBlank { lesson.info }.oneLine(160)
            .takeUnless { it.equals(subjectName, ignoreCase = true) || it.equals(lesson.subject, ignoreCase = true) }
            .orEmpty()
        return DayCard.Entry(
            start = run.start,
            end = run.end,
            subject = lesson.subject,
            subjectName = subjectName,
            teacher = lesson.teacher,
            room = lesson.room,
            cancelled = lesson.isCancelled,
            irregular = lesson.isIrregular,
            originalTeacher = if (lesson.hasTeacherChange) lesson.originalTeacher else "",
            originalRoom = if (lesson.hasRoomChange) lesson.originalRoom else "",
            note = note,
            exam = exam?.let { it.name.ifBlank { it.subject }.oneLine(80) },
            periods = run.periods,
        )
    }

    private fun overlapMinutes(a: LessonRun, b: LessonRun): Long {
        val start = maxOf(a.start, b.start)
        val end = minOf(a.end, b.end)
        return if (end > start) java.time.Duration.between(start, end).toMinutes() else 0L
    }
}
