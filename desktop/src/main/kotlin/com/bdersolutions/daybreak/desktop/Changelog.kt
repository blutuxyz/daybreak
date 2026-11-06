package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One block of release notes. The notes are Markdown written for a GitHub release: headings and bullets. */
sealed interface ChangelogBlock {
    data class Heading(val text: String) : ChangelogBlock
    data class Bullet(val text: String) : ChangelogBlock
    data class Paragraph(val text: String) : ChangelogBlock
}

/** Only what the release notes use: `#` headings, `-` or `*` bullets, and plain paragraphs. */
fun parseChangelog(markdown: String): List<ChangelogBlock> {
    val blocks = ArrayList<ChangelogBlock>()
    for (raw in markdown.replace("\r\n", "\n").lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("#") -> blocks += ChangelogBlock.Heading(line.trimStart('#').trim())
            line.startsWith("- ") || line.startsWith("* ") -> blocks += ChangelogBlock.Bullet(line.drop(2).trim())
            else -> blocks += ChangelogBlock.Paragraph(line)
        }
    }
    return blocks
}

/**
 * Text with `**bold**` and `` `code` `` turned into styling. With [boldLead] a short label before the
 * first colon ("Windows app: ...") is bold too, which makes a list of notes easy to scan.
 */
fun changelogInline(text: String, boldLead: Boolean, codeBackground: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var rest = text
        if (boldLead) {
            val colon = rest.indexOf(": ")
            if (colon in 1..MAX_LEAD && !rest.substring(0, colon).contains('`') && !rest.substring(0, colon).contains("**")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rest.substring(0, colon + 1)) }
                rest = rest.substring(colon + 1)
            }
        }
        var i = 0
        while (i < rest.length) {
            when {
                rest.startsWith("**", i) -> {
                    val end = rest.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rest.substring(i + 2, end)) }
                        i = end + 2
                    } else {
                        append(rest[i]); i++
                    }
                }
                rest[i] == '`' -> {
                    val end = rest.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                            append(rest.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(rest[i]); i++
                    }
                }
                else -> { append(rest[i]); i++ }
            }
        }
    }

private const val MAX_LEAD = 32

/** The release notes of an update, drawn as headings and bullets in a box that scrolls. */
@Composable
fun ChangelogView(markdown: String, maxHeight: Dp = 320.dp, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseChangelog(markdown) }
    if (blocks.isEmpty()) return
    val scroll = rememberScrollState()
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier = modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is ChangelogBlock.Heading -> Text(
                        block.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp),
                    )
                    is ChangelogBlock.Bullet -> Row(verticalAlignment = Alignment.Top) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            changelogInline(block.text, boldLead = true, codeBackground = codeBackground),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    is ChangelogBlock.Paragraph -> Text(
                        changelogInline(block.text, boldLead = false, codeBackground = codeBackground),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).width(8.dp).heightIn(max = maxHeight),
        )
    }
}
