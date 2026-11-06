package com.bdersolutions.daybreak.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    @Test
    fun headingsBulletsAndParagraphsAreSeparated() {
        val blocks = parseChangelog("## What's new\r\n- One\n* Two\n\nSome text\n## Fixes\n- Three")

        assertEquals(
            listOf(
                ChangelogBlock.Heading("What's new"),
                ChangelogBlock.Bullet("One"),
                ChangelogBlock.Bullet("Two"),
                ChangelogBlock.Paragraph("Some text"),
                ChangelogBlock.Heading("Fixes"),
                ChangelogBlock.Bullet("Three"),
            ),
            blocks,
        )
    }

    @Test
    fun theLabelBeforeTheColonAndStarredTextAreBold() {
        val text = changelogInline("Windows app: press **Esc** to go back", boldLead = true, codeBackground = Color.Gray)

        assertEquals("Windows app: press Esc to go back", text.text)
        val bold = text.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.text.substring(it.start, it.end) }
        assertEquals(listOf("Windows app:", "Esc"), bold)
    }

    @Test
    fun codeIsMonospaceAndALongSentenceBeforeAColonIsNotALabel() {
        val code = changelogInline("Run `setup.exe` now", boldLead = false, codeBackground = Color.Gray)
        assertEquals("Run setup.exe now", code.text)
        assertTrue(code.spanStyles.any { it.item.fontFamily != null })

        val sentence = "This is a very long sentence that is not a label at all: it just has a colon"
        val plain = changelogInline(sentence, boldLead = true, codeBackground = Color.Gray)
        assertTrue(plain.spanStyles.none { it.item.fontWeight == FontWeight.Bold })
    }
}
