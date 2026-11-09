package com.bdersolutions.daybreak.ui.assistant

import com.bdersolutions.daybreak.data.assistant.ChatMessage
import com.bdersolutions.daybreak.data.assistant.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
