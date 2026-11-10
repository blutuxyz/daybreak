package com.bdersolutions.daybreak.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatArchiveTest {
    @Test
    fun aChatSurvivesSavingWithPicturesButNotFileData() {
        val picture = ChatAttachment(ChatAttachment.Kind.IMAGE, "board.jpg", "image/jpeg", "aGVsbG8=", 5)
        val sheet = ChatAttachment(ChatAttachment.Kind.FILE, "sheet.pdf", "application/pdf", "JVBERg==", 4)
        val chat = SavedChat(
            id = 7,
            title = ChatArchive.titleFor(listOf(ChatMessage(1, ChatRole.USER, "Was ist x hoch 2?"))),
            updatedAt = 99,
            messages = listOf(
                ChatMessage(1, ChatRole.USER, "Was ist x hoch 2?", attachments = listOf(picture, sheet)),
                ChatMessage(2, ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("c1", "draft_message", "{}"))),
                ChatMessage(3, ChatRole.TOOL, "cancelled", toolCallId = "c1"),
            ),
            drafts = mapOf("c1" to SavedDraft(MessageDraft("c1", 42, "Müller", "Krank", "Text"), "CANCELLED")),
        )
        val back = ChatArchive.decode(ChatArchive.encode(chat))!!
        assertEquals(chat.copy(messages = chat.messages.mapIndexed { i, m -> if (i == 0) m.copy(attachments = listOf(picture, sheet.copy(base64 = ""))) else m }), back)
        assertEquals("Was ist x hoch 2?", back.title)
        assertNull(ChatArchive.decode("nope"))
        val index = listOf(ChatSummary(1, "a", 10), ChatSummary(2, "b", 20))
        assertEquals(index.reversed(), ChatArchive.decodeIndex(ChatArchive.encodeIndex(index)))
    }

    @Test
    fun aReopenedFileWithoutDataIsNotSentAgain() {
        val sheet = ChatAttachment(ChatAttachment.Kind.FILE, "sheet.pdf", "application/pdf", "", 4)
        val body = AssistantProtocol.requestBody(listOf(ChatMessage(1, ChatRole.USER, "Hi", attachments = listOf(sheet))), "", "de", java.time.LocalDate.of(2026, 11, 9))
        assert(!body.contains("\"attachments\""))
        assert(body.contains("[earlier attachment: sheet.pdf]"))
    }

    @Test
    fun titlesKeepWhereTheyCameFromAndTheServerAnswerIsRead() {
        val chat = SavedChat(1, "Mitternachtsformel", 5, listOf(ChatMessage(1, ChatRole.USER, "Loese die Gleichung")), titleSource = TitleSource.AI)
        assertEquals(TitleSource.AI, ChatArchive.decode(ChatArchive.encode(chat))!!.titleSource)
        assertEquals("Quadratische Gleichung", AssistantProtocol.parseTitle("""{"title":"  Quadratische   Gleichung "}"""))
        assertNull(AssistantProtocol.parseTitle("""{"title":"  "}"""))
        assertNull(AssistantProtocol.parseTitle(null))
    }

    @Test
    fun reasoningIsReadAndKeptButNeverSent() {
        assertEquals(AssistantEvent.Reasoning("Erst"), AssistantProtocol.parseEvent("""data: {"type":"reasoning","text":"Erst"}"""))
        val answer = ChatMessage(2, ChatRole.ASSISTANT, "Antwort", reasoning = "Gedanken")
        val chat = SavedChat(1, "t", 1, listOf(ChatMessage(1, ChatRole.USER, "Frage"), answer))
        assertEquals("Gedanken", ChatArchive.decode(ChatArchive.encode(chat))!!.messages[1].reasoning)
        val body = AssistantProtocol.requestBody(chat.messages + ChatMessage(3, ChatRole.USER, "Noch was"), "", "de", java.time.LocalDate.of(2026, 11, 9))
        assert(!body.contains("Gedanken"))
    }

    @Test
    fun everyKindOfCardIsKept() {
        val card = DayCard(
            date = java.time.LocalDate.of(2026, 11, 10),
            entries = listOf(
                DayCard.Entry(
                    java.time.LocalTime.of(8, 0), java.time.LocalTime.of(9, 30), "M", "Mathematik", "MÜL", "R101", periods = 2, exam = "Schulaufgabe",
                    replaces = listOf(DayCard.Replaced(java.time.LocalTime.of(8, 0), java.time.LocalTime.of(8, 45), "D", "Deutsch")),
                ),
                DayCard.Entry(java.time.LocalTime.of(9, 45), java.time.LocalTime.of(10, 30), "E", "Englisch", cancelled = true, originalRoom = "R102", note = "Aufgaben im Moodle"),
            ),
        )
        val chat = SavedChat(
            id = 1,
            title = "Heute",
            updatedAt = 1,
            messages = listOf(ChatMessage(1, ChatRole.USER, "Was habe ich heute?")),
            cards = mapOf(
                "c9" to card,
                "c10" to DayCard(card.date.plusDays(1), emptyList(), holiday = "Herbstferien"),
                "c11" to ExamCard(
                    card.date,
                    card.date.plusDays(60),
                    listOf(ExamCard.Exam(card.date, java.time.LocalTime.of(11, 35), java.time.LocalTime.of(12, 25), "E1", "Englisch", "Test", "future forms", "KRAB", "R12")),
                ),
                "c12" to AbsenceCard(
                    countedMinutes = 635, recordedMinutes = 2115, excusedMinutes = 535, openMinutes = 100, excusedCount = 1, openCount = 1,
                    entries = listOf(AbsenceCard.Entry(java.time.LocalDateTime.of(2026, 10, 19, 11, 35), java.time.LocalDateTime.of(2026, 10, 19, 13, 15), 100, false, "", "")),
                ),
            ),
        )
        assertEquals(chat, ChatArchive.decode(ChatArchive.encode(chat)))
    }

    @Test
    fun openingAChatSavesNothingAndRenamingKeepsItsPlace() {
        val saved = SavedChat(
            id = 3,
            title = "Was habe ich morgen?",
            updatedAt = 1_000,
            messages = listOf(ChatMessage(1, ChatRole.USER, "Was habe ich morgen?"), ChatMessage(2, ChatRole.ASSISTANT, "Mathe.")),
        )
        // Just opened: the same chat, only stamped with the time now.
        assertNull(ChatSaving.toSave(saved, saved.copy(updatedAt = 9_000)))

        val renamed = ChatSaving.toSave(saved, saved.copy(title = "Morgen", titleSource = TitleSource.MANUAL, updatedAt = 9_000))!!
        assertEquals("Morgen", renamed.title)
        assertEquals("a new name keeps the chat where it is", 1_000L, renamed.updatedAt)

        val asked = saved.copy(messages = saved.messages + ChatMessage(3, ChatRole.USER, "Und übermorgen?"), updatedAt = 9_000)
        assertEquals("a new question moves it to the top", 9_000L, ChatSaving.toSave(saved, asked)!!.updatedAt)

        assertEquals("a chat not saved yet is saved", asked, ChatSaving.toSave(null, asked))
        assertEquals("another chat is saved as it is", asked, ChatSaving.toSave(saved.copy(id = 4), asked))
    }
}
