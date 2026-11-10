package com.bdersolutions.daybreak.ui.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown
import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown.Block
import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown.Inline

private const val CURSOR = "cursor"

/**
 * An answer drawn from its Markdown blocks. Formulas appear as their LaTeX in a quiet monospace style:
 * this is what shows while an answer streams, and the placeholder until the KaTeX page has drawn them.
 * While [streaming], a blinking cursor sits at the end of the last line.
 */
@Composable
internal fun AnswerText(blocks: List<Block>, streaming: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spans = remember(colors) {
        InlineStyles(
            code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHighest, fontSize = 14.sp),
            math = SpanStyle(fontFamily = FontFamily.Monospace, color = colors.tertiary, fontSize = 14.sp),
            link = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)),
        )
    }
    val body = MaterialTheme.typography.bodyLarge
    // The cursor goes into the last block that is text; otherwise it follows the blocks.
    val cursorBlock = if (streaming) blocks.indexOfLast { it is Block.Paragraph || it is Block.Heading || it is Block.Items || it is Block.Quote } else -1
    val cursorInline = remember { mapOf(CURSOR to InlineTextContent(Placeholder(9.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)) { InlineCursor() }) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (blocks.isEmpty() && streaming) InlineCursor(Modifier.width(9.dp).height(18.dp))
        blocks.forEachIndexed { index, block ->
            val cursor = index == cursorBlock
            when (block) {
                is Block.Paragraph -> Text(annotate(block.text, spans, cursor), style = body, inlineContent = cursorInline)
                is Block.Heading -> Text(
                    annotate(block.text, spans, cursor),
                    style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    inlineContent = cursorInline,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp),
                )
                is Block.Items -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { itemIndex, item ->
                        Row(modifier = Modifier.padding(start = (item.depth * 16).dp)) {
                            Text(
                                text = item.number?.let { "$it." } ?: if (item.depth == 0) "•" else "◦",
                                style = body,
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.widthIn(min = if (item.number != null) 24.dp else 16.dp),
                            )
                            Text(
                                annotate(item.text, spans, cursor && itemIndex == block.items.lastIndex),
                                style = body,
                                inlineContent = cursorInline,
                            )
                        }
                    }
                }
                is Block.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(colors.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
                    )
                    Text(
                        annotate(block.text, spans, cursor),
                        style = body,
                        color = colors.onSurfaceVariant,
                        inlineContent = cursorInline,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                is Block.Code -> Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceContainerHighest) {
                    Text(
                        text = block.code,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                        softWrap = false,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                is Block.Math -> Text(
                    text = block.tex,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.tertiary,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                is Block.Table -> AnswerTable(block, spans)
                Block.Rule -> HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        if (streaming && cursorBlock < 0 && blocks.isNotEmpty()) InlineCursor(Modifier.width(9.dp).height(18.dp))
    }
}

/** A table, column by column so every column is as wide as its widest cell; it scrolls sideways when wide. */
@Composable
private fun AnswerTable(table: Block.Table, spans: InlineStyles) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceContainer) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            table.header.indices.forEach { column ->
                Column {
                    Text(
                        annotate(table.header[column], spans, false),
                        style = style,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = false,
                        modifier = Modifier
                            .background(colors.surfaceContainerHighest)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    table.rows.forEach { row ->
                        Text(annotate(row[column], spans, false), style = style, softWrap = false, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

private data class InlineStyles(val code: SpanStyle, val math: SpanStyle, val link: TextLinkStyles)

private fun annotate(parts: List<Inline>, styles: InlineStyles, cursor: Boolean): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        when (part) {
            is Inline.Text -> withStyle(
                SpanStyle(
                    fontWeight = if (part.bold) FontWeight.SemiBold else null,
                    fontStyle = if (part.italic) FontStyle.Italic else null,
                ),
            ) { append(part.text) }
            is Inline.Code -> withStyle(styles.code) { append(" ${part.code} ") }
            is Inline.Math -> withStyle(styles.math) { append(part.tex) }
            is Inline.Link -> withLink(LinkAnnotation.Url(part.url, styles.link)) { append(part.label) }
        }
    }
    if (cursor) appendInlineContent(CURSOR, "▍")
}

/** The streaming cursor, sized by its placeholder. */
@Composable
private fun InlineCursor(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Box(modifier = modifier.fillMaxSize().padding(start = 2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}

/** Parsed once per text. */
@Composable
internal fun rememberAnswerBlocks(text: String): List<Block> = remember(text) { AnswerMarkdown.parse(text) }
