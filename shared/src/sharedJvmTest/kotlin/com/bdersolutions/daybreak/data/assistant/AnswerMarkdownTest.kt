package com.bdersolutions.daybreak.data.assistant

import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown.Block
import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown.Inline
import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMarkdownTest {
    private fun text(value: String, bold: Boolean = false, italic: Boolean = false) = Inline.Text(value, bold, italic)

    @Test
    fun readsEmphasisCodeAndLinks() {
        assertEquals(
            listOf(text("Raum "), text("R101", bold = true), text(" um "), text("8:00", italic = true), text(", siehe "), Inline.Code("Mehr"), text(".")),
            AnswerMarkdown.inlines("Raum **R101** um *8:00*, siehe `Mehr`."),
        )
        assertEquals(listOf(text("Sieh "), Inline.Link("hier", "https://example.org/a")), AnswerMarkdown.inlines("Sieh [hier](https://example.org/a)"))
        assertEquals(listOf(text("2*3 = 6 und snake_case_name")), AnswerMarkdown.inlines("2*3 = 6 und snake_case_name"))
        assertEquals(listOf(text("ein halbes **Wort")), AnswerMarkdown.inlines("ein halbes **Wort"))
    }

    @Test
    fun findsFormulasInEveryFormButNotPrices() {
        assertEquals(listOf(text("Es gilt "), Inline.Math("a^2+b^2=c^2"), text(".")), AnswerMarkdown.inlines("Es gilt \$a^2+b^2=c^2\$."))
        assertEquals(listOf(text("Es gilt "), Inline.Math("x_1 * x_2")), AnswerMarkdown.inlines("Es gilt \\(x_1 * x_2\\)"))
        assertEquals(listOf(text("Das kostet 5$ und 10$, oder $5 bis $10.")), AnswerMarkdown.inlines("Das kostet 5$ und 10$, oder $5 bis $10."))
        assertEquals(listOf(text("nur \$x\$ als Text")), AnswerMarkdown.inlines("nur \\\$x\\\$ als Text"))

        assertEquals(listOf(Block.Math("\\frac{a}{b}")), AnswerMarkdown.parse("$$\n\\frac{a}{b}\n$$"))
        assertEquals(listOf(Block.Math("x = 2"), Block.Paragraph(listOf(text("fertig")))), AnswerMarkdown.parse("\\[ x = 2 \\] fertig"))
        assertEquals(listOf(Block.Math("E = mc^2")), AnswerMarkdown.parse("```latex\n\$\$E = mc^2\$\$\n```"))
        val aligned = AnswerMarkdown.parse("\\begin{aligned}\nx &= 1 \\\\\ny &= 2\n\\end{aligned}")
        assertEquals(listOf(Block.Math("\\begin{aligned}\nx &= 1 \\\\\ny &= 2\n\\end{aligned}")), aligned)
        assertTrue(AnswerMarkdown.hasMath(aligned))
    }

    @Test
    fun aLineOfBareLatexIsAFormulaButProseIsNot() {
        assertEquals(
            listOf(Block.Paragraph(listOf(text("Die Lösung:"))), Block.Math("x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}")),
            AnswerMarkdown.parse("Die Lösung:\nx = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}"),
        )
        assertFalse(AnswerMarkdown.looksLikeFormula("Teile durch \\frac{1}{2} und rechne dann weiter"))
        assertFalse(AnswerMarkdown.hasMath(AnswerMarkdown.parse("Morgen hast du **Mathe** in R101.")))
    }

    @Test
    fun readsBlocks() {
        val blocks = AnswerMarkdown.parse(
            """
            ### Morgen

            - **Mathe** 08:00
              in R101
            - Englisch
              1. Vokabeln
              2. Text

            > Hinweis vom Lehrer

            | Fach | Raum |
            |------|------|
            | M | R101 |

            ---
            ```kotlin
            val x = 1
            ```
            """.trimIndent(),
        )
        assertEquals(Block.Heading(3, listOf(text("Morgen"))), blocks[0])
        assertEquals(
            Block.Items(
                listOf(
                    Item(0, null, listOf(text("Mathe", bold = true), text(" 08:00\nin R101"))),
                    Item(0, null, listOf(text("Englisch"))),
                    Item(1, 1, listOf(text("Vokabeln"))),
                    Item(1, 2, listOf(text("Text"))),
                ),
            ),
            blocks[1],
        )
        assertEquals(Block.Quote(listOf(text("Hinweis vom Lehrer"))), blocks[2])
        assertEquals(Block.Table(listOf(listOf(text("Fach")), listOf(text("Raum"))), listOf(listOf(listOf(text("M")), listOf(text("R101"))))), blocks[3])
        assertEquals(Block.Rule, blocks[4])
        assertEquals(Block.Code("kotlin", "val x = 1"), blocks[5])
    }

    @Test
    fun halfWrittenInputDoesNotBreak() {
        assertEquals(listOf(Block.Code("", "val x")), AnswerMarkdown.parse("```\nval x"))
        assertEquals(listOf(Block.Math("\\frac{1}")), AnswerMarkdown.parse("$$\n\\frac{1}"))
        assertEquals(listOf(Block.Paragraph(listOf(text("Es gilt \$a^2")))), AnswerMarkdown.parse("Es gilt \$a^2"))
    }
}
