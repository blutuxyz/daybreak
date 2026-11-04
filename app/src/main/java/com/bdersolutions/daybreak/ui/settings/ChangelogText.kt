package com.bdersolutions.daybreak.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** One block of a release's notes. */
sealed interface ChangelogBlock {
    data class Heading(val text: String, val level: Int) : ChangelogBlock
    data class Bullet(val text: String) : ChangelogBlock
    data class Paragraph(val text: String) : ChangelogBlock
}

/**
 * Splits GitHub release notes (Markdown) into headings, bullets and paragraphs. It reads only what
 * release notes use; anything else shows as plain text. Lines that carry no information for the
 * reader, like "Full Changelog: <link>" and horizontal rules, are dropped.
 */
fun parseChangelog(markdown: String): List<ChangelogBlock> {
    val blocks = mutableListOf<ChangelogBlock>()
    val paragraph = StringBuilder()

    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += ChangelogBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    for (raw in markdown.lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            line.all { it == '-' || it == '*' || it == '_' } && line.length >= 3 -> flush()
            line.contains("Full Changelog", ignoreCase = true) && line.contains("http") -> flush()
            line.startsWith("#") -> {
                flush()
                val hashes = line.takeWhile { it == '#' }.length
                val text = line.drop(hashes).trim()
                if (text.isNotEmpty()) blocks += ChangelogBlock.Heading(text, hashes.coerceAtMost(3))
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                flush()
                blocks += ChangelogBlock.Bullet(line.drop(2).trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    flush()
    return blocks
}

private val inlinePattern = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\([^)]*\)""")

/** Bold, inline code and links (the link text only) inside one line. */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in inlinePattern.findAll(text)) {
        append(text.substring(last, match.range.first))
        val (bold, code, link) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            else -> append(link)
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

/** Release notes as text: headings, bullets and paragraphs in the app's type styles. */
@Composable
fun ChangelogText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = parseChangelog(markdown)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ChangelogBlock.Heading -> Text(
                    text = inlineMarkdown(block.text),
                    style = if (block.level == 1) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )

                is ChangelogBlock.Bullet -> Row {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        text = inlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is ChangelogBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
