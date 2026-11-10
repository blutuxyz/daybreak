package com.bdersolutions.daybreak.data.assistant

import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.RecipientGroup
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHoliday
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.untis.UntisRecipient
import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val now = LocalDateTime.of(monday, LocalTime.of(10, 15))

    private fun lesson(
        id: Long,
        date: LocalDate,
        start: String,
        end: String,
        subject: String,
        status: LessonStatus = LessonStatus.REGULAR,
        room: String = "R101",
        originalRoom: String = "",
        holiday: Boolean = false,
        lessonText: String = "",
        info: String = "",
    ) = UntisLesson(
        id = id,
        date = date,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        subject = subject,
        teacher = "MÜL",
        room = room,
        status = status,
        originalRoom = originalRoom,
        isHoliday = holiday,
        lessonText = lessonText,
        info = info,
    )

    private val lessons = listOf(
        lesson(1, monday, "08:00", "08:45", "M"),
        lesson(2, monday, "08:45", "09:30", "D", status = LessonStatus.CANCELLED),
        lesson(3, monday.plusDays(1), "09:45", "10:30", "E", room = "R201", originalRoom = "R102"),
        lesson(4, monday.plusDays(2), "08:00", "15:00", "Ferien", holiday = true),
        lesson(5, monday.minusDays(7), "08:00", "08:45", "M", info = "SA am 12.11.: Bruchrechnung, Buch S. 40-52"),
        lesson(6, monday.minusDays(14), "08:00", "08:45", "M", lessonText = "Einführung Brüche"),
    )

    private val cache = TimetableCache(
        weeks = mapOf(
            monday to CachedWeek(weekStart = monday, lessons = lessons.take(4), updatedAt = 0),
            monday.minusWeeks(10) to CachedWeek(
                weekStart = monday.minusWeeks(10),
                lessons = listOf(lesson(9, monday.minusWeeks(10), "08:00", "08:45", "OLD")),
                updatedAt = 0,
            ),
        ),
        exams = listOf(
            UntisExam(1, monday.plusDays(3), LocalTime.of(8, 0), LocalTime.of(9, 30), "M", name = "Schulaufgabe 2"),
        ),
        absences = listOf(
            UntisAbsence(1, now.minusDays(3), now.minusDays(3).plusMinutes(90), excused = false, reasonId = null, reasonName = "Krank", text = ""),
        ),
        holidays = listOf(
            UntisHoliday(1, "WF", "Weihnachtsferien", LocalDate.of(2026, 12, 23), LocalDate.of(2027, 1, 6)),
        ),
    )

    @Test
    fun contextHoldsLessonsChangesExamsAndAbsences() {
        val homework = listOf(UntisHomework(1, "E", null, monday.plusDays(1), "Workbook p. 12", completed = true))
        val text = AssistantContext.build(cache, now, homework, absences = cache.absences)
        assertTrue(text.contains("Now: Mon 2026-11-09 10:15"))
        assertTrue(text.contains("Mon 2026-11-09 (today)"))
        assertTrue(text.contains("08:00-08:45 M, teacher MÜL, room R101"))
        assertTrue(text.contains("08:45-09:30 D, teacher MÜL, room R101 [CANCELLED]"))
        assertTrue(text.contains("room changed from R102"))
        assertTrue(text.contains("Thu 2026-11-12 08:00-09:30 M: Schulaufgabe 2"))
        assertTrue(text.contains("due Tue 2026-11-10 E: Workbook p. 12 [done]"))
        assertTrue(text.contains("Weihnachtsferien"))
        assertTrue(text.contains("Absences this school year: 1 entries, 90 minutes recorded, 1 not excused"))
        assertFalse("holiday blocks are made for display only", text.contains("Ferien,"))
        assertFalse("weeks far from today are left out", text.contains("OLD"))
    }

    @Test
    fun contextNeverTakesTheCachedWeeksForAllAbsences() {
        // The cache holds an absence, but only the school-year list counts; unknown is not "none".
        assertTrue(AssistantContext.build(cache, now).contains("Absences: not in this summary (call get_absences)."))
        assertTrue(AssistantContext.build(cache, now, absences = emptyList()).contains("Absences this school year: none recorded."))
    }

    @Test
    fun contextSaysWhenNothingIsLoadedAndIsCutToSize() {
        assertTrue(AssistantContext.build(TimetableCache(), now).contains("Lessons: none loaded yet"))
        val cut = AssistantContext.build(cache, now, maxChars = 200)
        assertTrue(cut.length <= 200)
        assertTrue(cut.endsWith("[cut off]"))
    }

    private fun user(id: Long, text: String = "q$id", attachments: List<ChatAttachment> = emptyList()) =
        ChatMessage(id, ChatRole.USER, text, attachments = attachments)

    private fun assistant(id: Long, text: String = "a$id", calls: List<ToolCall> = emptyList()) =
        ChatMessage(id, ChatRole.ASSISTANT, text, toolCalls = calls)

    private fun tool(id: Long, callId: String) = ChatMessage(id, ChatRole.TOOL, "result", toolCallId = callId)

    @Test
    fun preparedFillsMissingToolResultsAndEndsWithAQuestionOrResult() {
        val chat = listOf(
            assistant(0, "greeting"),
            user(1),
            assistant(2, "", listOf(ToolCall("c1", "get_exams", "{}"), ToolCall("c2", "get_homework", "{}"))),
            tool(3, "c1"),
            user(4, " "),
            user(5),
            assistant(6, "half answer"),
        )
        val sent = AssistantProtocol.prepared(chat)
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.TOOL, ChatRole.USER), sent.map { it.role })
        assertEquals("c2", sent[3].toolCallId)
        assertEquals("Not run: cancelled.", sent[3].text)
    }

    @Test
    fun preparedKeepsTheNewestAndCutsAtAQuestion() {
        val chat = (1L..60L).flatMap { i ->
            listOf(user(i * 10), assistant(i * 10 + 1, "", listOf(ToolCall("c$i", "get_exams", "{}"))), tool(i * 10 + 2, "c$i"))
        } + user(9999)
        val sent = AssistantProtocol.prepared(chat)
        assertTrue(sent.size <= AssistantProtocol.MAX_MESSAGES)
        assertEquals(ChatRole.USER, sent.first().role)
        assertEquals(9999L, sent.last().id)
    }

    @Test
    fun requestBodySendsAttachmentsOnlyWithTheNewestQuestion() {
        val picture = ChatAttachment(ChatAttachment.Kind.IMAGE, "board.jpg", "image/jpeg", "aGVsbG8=", 5)
        val sheet = ChatAttachment(ChatAttachment.Kind.FILE, "sheet.pdf", "application/pdf", "JVBERg==", 4)
        val body = AssistantProtocol.requestBody(
            listOf(user(1, "first", listOf(picture)), assistant(2), user(3, "", listOf(sheet))),
            context = "ctx",
            language = "de",
            today = monday,
            model = "anthropic/claude-haiku-5.5",
            unlockCode = "DB-AAAA-BBBB",
        )
        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("de", json.get("language").asString)
        assertEquals("anthropic/claude-haiku-5.5", json.get("model").asString)
        assertEquals("DB-AAAA-BBBB", json.get("unlockCode").asString)
        val messages = json.getAsJsonArray("messages")
        val first = messages[0].asJsonObject
        assertNull(first.get("attachments"))
        assertTrue(first.get("text").asString.contains("[earlier attachment: board.jpg]"))
        val last = messages[2].asJsonObject.getAsJsonArray("attachments")[0].asJsonObject
        assertEquals("file", last.get("type").asString)
        assertEquals("JVBERg==", last.get("data").asString)
    }

    @Test
    fun requestBodyCarriesToolCallsAndResults() {
        val body = AssistantProtocol.requestBody(
            listOf(user(1), assistant(2, "", listOf(ToolCall("c1", "get_exams", "{\"start_date\":\"2026-11-09\"}"))), tool(3, "c1")),
            context = "",
            language = "en",
            today = monday,
        )
        val messages = JsonParser.parseString(body).asJsonObject.getAsJsonArray("messages")
        val call = messages[1].asJsonObject.getAsJsonArray("toolCalls")[0].asJsonObject
        assertEquals("get_exams", call.get("name").asString)
        assertEquals("c1", messages[2].asJsonObject.get("toolCallId").asString)
        assertEquals("tool", messages[2].asJsonObject.get("role").asString)
    }

    @Test
    fun parsesServerEventsAndModels() {
        assertEquals(AssistantEvent.Text("Hallo"), AssistantProtocol.parseEvent("""data: {"type":"text","text":"Hallo"}"""))
        assertEquals(AssistantEvent.Done, AssistantProtocol.parseEvent("""data: {"type":"done"}"""))
        assertEquals(
            AssistantEvent.ToolCalls(listOf(ToolCall("c1", "get_exams", "{}"))),
            AssistantProtocol.parseEvent("""data: {"type":"tool_calls","calls":[{"id":"c1","name":"get_exams","arguments":"{}"}]}"""),
        )
        assertEquals(AssistantEvent.Failure(AssistantFailure.UPSTREAM), AssistantProtocol.parseEvent("""data: {"type":"error","code":"upstream"}"""))
        // A used-up budget looks like any other outage.
        assertEquals(AssistantEvent.Failure(AssistantFailure.UNAVAILABLE), AssistantProtocol.parseEvent("""data: {"type":"error","code":"limit"}"""))
        assertNull(AssistantProtocol.parseEvent(": keep-alive"))
        assertNull(AssistantProtocol.parseEvent("data: not json"))

        val models = AssistantProtocol.parseModels("""{"showSwitcher":true,"models":[{"id":"a/one","label":"One"},{"id":"b/two","label":"Two"}],"defaultModel":"a/one"}""")!!
        assertTrue(models.showSwitcher)
        assertEquals("a/one", models.defaultModel)
        assertFalse(AssistantProtocol.parseModels("""{"showSwitcher":false,"models":[],"defaultModel":null}""")!!.showSwitcher)
        assertNull(AssistantProtocol.parseModels("nope"))
    }

    private inner class FakeData(override val canSendMessages: Boolean = true) : AssistantData {
        override suspend fun lessons(from: LocalDate, to: LocalDate) = lessons.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        override suspend fun exams(from: LocalDate, to: LocalDate) = cache.exams.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        override suspend fun homework(from: LocalDate, to: LocalDate) = emptyList<UntisHomework>()
        override suspend fun absences(): List<UntisAbsence>? = cache.absences
        override suspend fun lessonTopics(lessons: List<UntisLesson>) =
            lessons.filter { it.id == 6L }.associate { it.id to "Brüche erweitern und kürzen" }
        override suspend fun teachers() = listOf(
            UntisRecipient(42, "Müller", RecipientGroup.TEACHER, listOf("M")),
            UntisRecipient(7, "Hausmeister", RecipientGroup.OTHER),
        )
    }

    private fun runTool(name: String, args: String = "{}", data: AssistantData = FakeData()) =
        runBlocking { AssistantToolRunner(data) { monday }.run(ToolCall("c1", name, args)) }

    private fun resultOf(outcome: ToolOutcome) = (outcome as ToolOutcome.Result).content

    @Test
    fun toolsReadLessonsExamsAndNotes() {
        val lessonsText = resultOf(runTool("get_lessons", """{"start_date":"2026-11-09","end_date":"2026-11-11"}"""))
        assertTrue(lessonsText.contains("08:45-09:30 D, teacher MÜL, room R101 [CANCELLED]"))
        assertFalse(lessonsText.contains("Ferien"))
        assertTrue(resultOf(runTool("get_exams")).contains("Thu 2026-11-12 08:00-09:30 M: Schulaufgabe 2"))

        val notes = resultOf(runTool("get_lesson_notes", """{"subject":"m"}"""))
        assertTrue(notes.contains("SA am 12.11.: Bruchrechnung, Buch S. 40-52"))
        assertTrue(notes.indexOf("Bruchrechnung") < notes.indexOf("Einführung Brüche"))
        assertTrue("class register topics are read", notes.contains("topic: Brüche erweitern und kürzen"))
        assertTrue(resultOf(runTool("get_lesson_notes", """{"subject":"Physik"}""")).contains("Subjects in the timetable"))
        assertTrue(resultOf(runTool("get_absences")).contains("this school year: 1 entries, 90 minutes recorded"))
        assertTrue(resultOf(runTool("nope")).startsWith("Unknown tool"))
    }

    @Test
    fun draftingNeverSendsAndOnlyAllowsListedTeachers() {
        val teachers = resultOf(runTool("list_teachers"))
        assertTrue(teachers.contains("user_id 42: Müller"))
        assertFalse(teachers.contains("Hausmeister"))

        val outcome = runTool("draft_message", """{"recipient_user_id":42,"subject":"Krank","body":"Ich bin heute krank."}""")
        assertEquals(MessageDraft("c1", 42, "Müller", "Krank", "Ich bin heute krank."), (outcome as ToolOutcome.Confirm).draft)

        assertTrue(resultOf(runTool("draft_message", """{"recipient_user_id":7,"subject":"x","body":"y"}""")).contains("Nothing was sent"))
        assertTrue(resultOf(runTool("draft_message", """{"recipient_user_id":42,"subject":"","body":"y"}""")).contains("Nothing was sent"))
        val off = runTool("draft_message", """{"recipient_user_id":42,"subject":"x","body":"y"}""", FakeData(canSendMessages = false))
        assertTrue(resultOf(off).contains("switched off"))
    }

    @Test
    fun absencesThatCouldNotBeLoadedAreNotReportedAsNone() {
        val offline = object : AssistantData by FakeData() {
            override suspend fun absences(): List<UntisAbsence>? = null
        }
        val text = resultOf(runTool("get_absences", data = offline))
        assertTrue(text.startsWith("Error"))
        assertFalse(text.contains("No absences"))
    }

    @Test
    fun showTimetableMakesACardFromTheRealLessons() {
        val day = monday.plusDays(3)
        val data = object : AssistantData by FakeData() {
            override suspend fun lessons(from: LocalDate, to: LocalDate) = listOf(
                lesson(20, day, "08:00", "08:45", "M"),
                lesson(21, day, "08:45", "09:30", "M"),
                lesson(22, day, "09:45", "10:30", "E", status = LessonStatus.CANCELLED),
                lesson(23, day, "10:30", "11:15", "D", room = "R201", originalRoom = "R102"),
            )
        }
        val outcome = runTool("show_timetable", """{"date":"2026-11-12"}""", data) as ToolOutcome.Card
        val entries = outcome.card.entries
        assertEquals(day, outcome.card.date)
        assertEquals(3, entries.size)
        assertEquals(2, entries[0].periods)
        assertEquals(LocalTime.of(9, 30), entries[0].end)
        assertEquals("Schulaufgabe 2", entries[0].exam)
        assertTrue(entries[1].cancelled)
        assertEquals("R102", entries[2].originalRoom)
        assertTrue(outcome.content.contains("Do not list the lessons again"))
        assertTrue(outcome.content.contains("Cancelled with nothing in their place"))
    }
    @Test
    fun aSchoolEventShowsOnceWithTheLessonsItReplacesUnderIt() {
        // Like a real day: an event from 07:55 to 13:15 instead of four lessons (one of them double), then
        // a lesson in the afternoon and one cancelled lesson that nothing replaces.
        val day = monday.plusDays(2)
        val cancelled = LessonStatus.CANCELLED
        val data = object : AssistantData by FakeData() {
            override suspend fun lessons(from: LocalDate, to: LocalDate) = listOf(
                lesson(30, day, "07:55", "08:45", "TMB", status = cancelled),
                lesson(31, day, "07:55", "13:15", "SV", status = LessonStatus.IRREGULAR, info = "SV"),
                lesson(32, day, "08:45", "09:35", "TMB", status = cancelled),
                lesson(33, day, "09:50", "10:40", "FI", status = cancelled),
                lesson(34, day, "12:25", "13:15", "D", status = cancelled),
                lesson(35, day, "14:05", "14:55", "GGP"),
                lesson(36, day, "15:00", "15:50", "KOP", status = cancelled),
            )
        }
        val card = (runTool("show_timetable", """{"date":"2026-11-11"}""", data) as ToolOutcome.Card).card
        assertEquals(listOf("SV", "GGP", "KOP"), card.entries.map { it.subject })
        val event = card.entries[0]
        assertEquals(LocalTime.of(13, 15), event.end)
        assertEquals("the double lesson stays one entry", listOf("TMB", "FI", "D"), event.replaces.map { it.subject })
        assertEquals(LocalTime.of(9, 35), event.replaces[0].end)
        assertEquals("a note that only repeats the subject is dropped", "", event.note)
        assertTrue(card.entries[2].cancelled)
        assertTrue(card.entries[2].replaces.isEmpty())
    }
}
