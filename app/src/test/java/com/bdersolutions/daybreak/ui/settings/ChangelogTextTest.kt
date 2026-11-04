package com.bdersolutions.daybreak.ui.settings

import com.bdersolutions.daybreak.ui.settings.ChangelogBlock.Bullet
import com.bdersolutions.daybreak.ui.settings.ChangelogBlock.Heading
import com.bdersolutions.daybreak.ui.settings.ChangelogBlock.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTextTest {
    @Test
    fun headingsBulletsAndParagraphsAreSeparated() {
        val blocks = parseChangelog(
            """
            ## What's new
            - Absence categories
            * Favourites in the picker

            Thanks for testing
            this release.
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                Heading("What's new", 2),
                Bullet("Absence categories"),
                Bullet("Favourites in the picker"),
                Paragraph("Thanks for testing this release."),
            ),
            blocks,
        )
    }

    @Test
    fun headingLevelIsCappedAtThree() {
        assertEquals(listOf(Heading("Deep", 3)), parseChangelog("##### Deep"))
    }

    @Test
    fun linesWithoutInformationAreDropped() {
        val blocks = parseChangelog(
            """
            - Fix
            ---
            **Full Changelog**: https://github.com/example/daybreak/compare/v1.0.0...v1.1.0
            """.trimIndent(),
        )

        assertEquals(listOf(Bullet("Fix")), blocks)
    }

    @Test
    fun emptyNotesGiveNoBlocks() {
        assertTrue(parseChangelog("").isEmpty())
        assertTrue(parseChangelog("\n  \n").isEmpty())
    }

    @Test
    fun inlineMarkdownKeepsTheTextAndMarksBoldAndCode() {
        val text = inlineMarkdown("Use **bold**, `code` and [a link](https://example.com) now")

        assertEquals("Use bold, code and a link now", text.text)
        assertEquals(2, text.spanStyles.size)
    }
}
