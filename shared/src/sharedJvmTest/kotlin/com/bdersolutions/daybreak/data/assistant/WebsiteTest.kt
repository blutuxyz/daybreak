package com.bdersolutions.daybreak.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteTest {
    @Test
    fun aHalfWrittenPageIsReadUpToWhereItStops() {
        val raw = "{\"title\":\"Snake\",\"html\":\"<!DOCTYPE html>\\n<p class=\\\"x\\\">Hi"
        assertEquals("Snake", PartialJson.string(raw, "title"))
        assertEquals("<!DOCTYPE html>\n<p class=\"x\">Hi", PartialJson.string(raw, "html"))
    }

    @Test
    fun aFieldThatHasNotStartedIsNull() {
        assertNull(PartialJson.string("{\"title\":\"Snake\"", "html"))
        assertNull(PartialJson.string("", "title"))
    }

    @Test
    fun aHalfWrittenEscapeIsLeftOut() {
        assertEquals("a", PartialJson.string("{\"html\":\"a\\", "html"))
        assertEquals("é", PartialJson.string("{\"html\":\"\\u00e9", "html"))
    }

    @Test
    fun aClosedFieldStopsAtItsQuote() {
        assertEquals("Snake", PartialJson.string("{\"title\":\"Snake\",\"html\":\"x\"}", "title"))
    }

    @Test
    fun theWebsiteCardSurvivesSavingAChat() {
        val card = WebsiteCard("Snake", "<!DOCTYPE html><title>Snake</title>")
        val chat = SavedChat(
            id = 7,
            title = "Snake",
            titleSource = TitleSource.AUTO,
            updatedAt = 1,
            messages = listOf(ChatMessage(1, ChatRole.USER, "Make a snake game")),
            cards = mapOf("call_1" to card),
        )
        val back = ChatArchive.decode(ChatArchive.encode(chat))
        assertEquals(card, back?.cards?.get("call_1"))
    }

    @Test
    fun aToolDeltaEventIsRead() {
        val event = AssistantProtocol.parseEvent("data: {\"type\":\"tool_delta\",\"index\":1,\"id\":\"c\",\"name\":\"show_website\",\"delta\":\"ab\"}")
        assertEquals(AssistantEvent.ToolDelta(1, "c", "show_website", "ab"), event)
    }

    @Test
    fun aWebsiteToolWithoutHtmlShowsNothing() {
        assertTrue(WebsiteCard("x", "").html.isEmpty())
    }

    @Test
    fun htmlIsHighlightedWithTagsAndAttributes() {
        val tokens = CodeHighlighter.tokenize("<a href=\"x\">hi</a>", "html")
        assertTrue(tokens.contains(CodeHighlighter.Token(CodeHighlighter.Kind.TAG, "<a")))
        assertTrue(tokens.contains(CodeHighlighter.Token(CodeHighlighter.Kind.ATTRIBUTE, "href")))
        assertTrue(tokens.contains(CodeHighlighter.Token(CodeHighlighter.Kind.STRING, "\"x\"")))
        assertTrue(tokens.contains(CodeHighlighter.Token(CodeHighlighter.Kind.TAG, "</a")))
    }

    @Test
    fun codeComesBackExactlyAfterHighlighting() {
        val samples = mapOf(
            "kotlin" to "fun main() {\n    val n = 42 // answer\n    println(\"hi\")\n}",
            "python" to "def f(x):\n    return x * 2  # double\n",
            "html" to "<!DOCTYPE html>\n<style>p { color: red; }</style>\n<script>let a = 1;</script>",
            "json" to "{\"a\": [1, true, null]}",
            "" to "plain text, with 'quotes' and #hash",
        )
        for ((language, code) in samples) {
            val joined = CodeHighlighter.tokenize(code, language).joinToString("") { it.text }
            assertEquals(code, joined)
        }
    }

    @Test
    fun keywordsAndCommentsAreRecognised() {
        val tokens = CodeHighlighter.tokenize("val n = 42 // answer", "kotlin")
        assertTrue(tokens.contains(CodeHighlighter.Token(CodeHighlighter.Kind.KEYWORD, "val")))
        assertTrue(tokens.contains(CodeHighlighter.Token(CodeHighlighter.Kind.NUMBER, "42")))
        assertTrue(tokens.contains(CodeHighlighter.Token(CodeHighlighter.Kind.COMMENT, "// answer")))
    }
}
