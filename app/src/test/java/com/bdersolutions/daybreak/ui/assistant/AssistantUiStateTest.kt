package com.bdersolutions.daybreak.ui.assistant

import com.bdersolutions.daybreak.data.assistant.AssistantFailure
import com.bdersolutions.daybreak.data.assistant.ChatMessage
import com.bdersolutions.daybreak.data.assistant.ChatRole
import com.bdersolutions.daybreak.data.assistant.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantUiStateTest {
    @Test
    fun finishingATurnClearsTheTypingDotsAndTheStreamingCursor() {
        val running = AssistantUiState(
            messages = listOf(ChatMessage(1, ChatRole.USER, "Fällt diese Woche etwas aus?")),
            waiting = true,
            streamingId = 2,
        )

        val finished = running.turnFinished()

        assertFalse(finished.waiting)
        assertNull(finished.streamingId)
        assertFalse(finished.busy)
        assertEquals(running.messages, finished.messages)
    }

    @Test
    fun finishingAnAlreadyFinishedTurnChangesNothing() {
        val done = AssistantUiState(messages = listOf(ChatMessage(1, ChatRole.USER, "Hallo")))

        assertEquals(done, done.turnFinished())
    }

    @Test
    fun retryingDropsTheHalfAnswerOfTheFailedRoundAndShowsTheDotsAgain() {
        val call = ToolCall("c1", "get_exams", "{}")
        val failed = AssistantUiState(
            messages = listOf(
                ChatMessage(1, ChatRole.USER, "Wann ist die nächste Arbeit?"),
                ChatMessage(2, ChatRole.ASSISTANT, "", toolCalls = listOf(call)),
                ChatMessage(3, ChatRole.TOOL, "[]", toolCallId = "c1"),
                ChatMessage(4, ChatRole.ASSISTANT, "Die nächste Arbeit ist am"),
            ),
            failure = AssistantFailure.OFFLINE,
        )

        val retried = failed.retrying()

        assertEquals(failed.messages.take(3), retried.messages)
        assertTrue(retried.waiting)
        assertNull(retried.failure)
        assertNull(retried.streamingId)
    }

    @Test
    fun retryingKeepsTheQuestionWhenNothingWasAnswered() {
        val failed = AssistantUiState(
            messages = listOf(ChatMessage(1, ChatRole.USER, "Hallo")),
            failure = AssistantFailure.UPSTREAM,
        )

        assertEquals(failed.messages, failed.retrying().messages)
    }
}
