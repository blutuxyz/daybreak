package com.bdersolutions.daybreak.ui.assistant

import com.bdersolutions.daybreak.ui.components.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
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
import com.bdersolutions.daybreak.ui.components.motionAnimationsEnabled
import kotlin.math.roundToInt

/**
 * An answer drawn from its Markdown blocks. Formulas appear as their LaTeX in a quiet monospace style:
 * this is what shows while an answer streams, and the placeholder until the KaTeX page has drawn them.
 * While [streaming], the newest characters fade in ([fresh]); there is no cursor.
 */
@Composable
internal fun AnswerText(blocks: List<Block>, streaming: Boolean, modifier: Modifier = Modifier, fresh: FreshText? = null) {
    val colors = MaterialTheme.colorScheme
    val spans = remember(colors) {
        InlineStyles(
            code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHighest, fontSize = 14.sp),
            math = SpanStyle(fontFamily = FontFamily.Monospace, color = colors.tertiary, fontSize = 14.sp),
            link = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)),
            text = colors.onSurface,
        )
    }
    val body = MaterialTheme.typography.bodyLarge
    // While streaming, the fade belongs to the last block that is text.
    val freshBlock = if (streaming) blocks.indexOfLast { it is Block.Paragraph || it is Block.Heading || it is Block.Items || it is Block.Quote } else -1
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            val tail = index == freshBlock
            when (block) {
                is Block.Paragraph -> Text(annotate(block.text, spans, tail, fresh), style = body)
                is Block.Heading -> Text(
                    annotate(block.text, spans, tail, fresh),
                    style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
                                annotate(item.text, spans, tail && itemIndex == block.items.lastIndex, fresh),
                                style = body,
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
                        annotate(block.text, spans, tail, fresh),
                        style = body,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                is Block.Code -> CodeBlock(language = block.language, code = block.code)
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

private data class InlineStyles(val code: SpanStyle, val math: SpanStyle, val link: TextLinkStyles, val text: Color)

/** [tail] marks the block whose end is the end of the streamed text; only that block gets the fade. */
private fun annotate(parts: List<Inline>, styles: InlineStyles, tail: Boolean, fresh: FreshText? = null): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        when (part) {
            is Inline.Text -> withStyle(
                SpanStyle(
                    fontWeight = if (part.bold) FontWeight.SemiBold else null,
                    fontStyle = if (part.italic) FontStyle.Italic else null,
                ),
            ) { append(part.text) }
            is Inline.Code -> withStyle(styles.code) { append(" ${part.code} ") }
            is Inline.Math -> withStyle(styles.math) { append(part.tex) }
            is Inline.Link -> withLink(LinkAnnotation.Url(part.url, styles.link)) { append(part.label) }
        }
    }
    // The characters of the newest change fade in while the answer streams.
    if (tail && fresh != null) {
        val end = length
        fresh.segments.forEach { run ->
            if (run.alpha < 1f) {
                val start = (end - run.fromEnd).coerceIn(0, end)
                val stop = (end - run.toEnd).coerceIn(0, end)
                if (start < stop) addStyle(SpanStyle(color = styles.text.copy(alpha = run.alpha)), start, stop)
            }
        }
    }
}

/** How long one released piece of text takes to fade in. */
internal const val FADE_NANOS = 380_000_000L

/** The share of the waiting text released each frame: a small backlog drains slowly, a big one faster. */
private const val RELEASE_SHARE = 0.15f

/** The most characters released in one frame, so a long burst still reads as a stream. */
private const val MAX_CHARS_PER_FRAME = 12

/**
 * How much of a streaming answer is on screen. The answer comes in bursts; instead of drawing each burst at once, the
 * text is released a few characters per frame, and it catches up faster the further it falls behind. A streaming
 * answer starts from nothing; a finished one, or one shown without motion, is shown whole.
 */
@Composable
internal fun rememberRevealedLength(length: Int, streaming: Boolean): Int {
    val paced = streaming && motionAnimationsEnabled()
    var shown by remember { mutableIntStateOf(if (paced) 0 else length) }
    LaunchedEffect(length, paced) {
        if (!paced) {
            shown = length
            return@LaunchedEffect
        }
        if (shown > length) shown = length
        while (shown < length) {
            withFrameNanos { }
            val backlog = length - shown
            shown += (backlog * RELEASE_SHARE).roundToInt().coerceIn(1, MAX_CHARS_PER_FRAME)
        }
    }
    return if (paced) minOf(shown, length) else length
}

/**
 * A run of characters that is still fading in. [fromEnd] and [toEnd] count back from the end of the text, so the
 * run keeps its place while more text is added after it.
 */
internal class FreshSegment(val fromEnd: Int, val toEnd: Int, val alpha: Float)

/** The runs of a streaming answer that are still fading in. Each one fades on its own clock. */
internal class FreshText(val segments: List<FreshSegment>) {
    /**
     * The opacity of the character [distance] places from the end (1 for the last one). Rounded to tenths, so a
     * fading formula page is only redrawn when the opacity visibly changes.
     */
    fun alphaAt(distance: Int): Float {
        var alpha = 1f
        for (run in segments) {
            if (distance > run.toEnd && distance <= run.fromEnd) alpha = minOf(alpha, run.alpha)
        }
        return (alpha * 10).roundToInt() / 10f
    }
}

private class FreshRun(val from: Int, val to: Int, val started: Long)

private class FreshHolder {
    var length = -1
    val runs = ArrayList<FreshRun>()
}

/**
 * Follows how the text of an answer grows. Every change adds a run for the characters that came with it; each run
 * fades in over [FADE_NANOS] from the moment it arrived. Text that arrives again while an older run is still
 * fading does not restart that run, so a fast stream does not keep the newest text invisible. Without motion,
 * or when [active] is false, nothing fades.
 */
@Composable
internal fun rememberFreshText(length: Int, active: Boolean, fadeNanos: Long = FADE_NANOS): FreshText {
    val animations = motionAnimationsEnabled()
    val holder = remember { FreshHolder() }
    val now = System.nanoTime()
    if (holder.length != length) {
        val previous = holder.length
        holder.length = length
        if (active && animations) {
            if (previous < 0) holder.runs += FreshRun(0, length, now)
            else if (length > previous) holder.runs += FreshRun(previous, length, now)
        }
    }
    holder.runs.removeAll { now - it.started >= fadeNanos }
    // Redraws the text once per frame while a run is still fading.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(length) {
        while (holder.runs.any { System.nanoTime() - it.started < fadeNanos }) {
            frame = withFrameNanos { it }
        }
    }
    @Suppress("UNUSED_EXPRESSION") frame
    val segments = holder.runs.map { run ->
        val t = ((now - run.started).toFloat() / fadeNanos).coerceIn(0f, 1f)
        FreshSegment(length - run.from, length - run.to, 1f - (1f - t) * (1f - t))
    }
    return FreshText(segments)
}

/** How long the next word takes to arrive when an opened chat's latest answer is played in. */
private const val WORD_NANOS = 2_000_000L

/** How long one word fades in when an opened chat's latest answer is played in: quick, so the answer keeps up. */
internal const val WORD_FADE_NANOS = 170_000_000L

/**
 * How much of a finished answer is on screen while it plays in (when a saved chat is opened): one word every
 * [WORD_NANOS], each fading in through [rememberFreshText]. Without [replay] the whole text shows at once.
 */
@Composable
internal fun rememberWordRevealedLength(text: String, replay: Boolean): Int {
    val paced = replay && motionAnimationsEnabled()
    var shown by remember(text) { mutableIntStateOf(if (paced) 0 else text.length) }
    LaunchedEffect(text, paced) {
        if (!paced) {
            shown = text.length
            return@LaunchedEffect
        }
        var last = withFrameNanos { it }
        var carry = 0L
        while (shown < text.length) {
            val now = withFrameNanos { it }
            carry += now - last
            last = now
            while (carry >= WORD_NANOS && shown < text.length) {
                carry -= WORD_NANOS
                shown = nextWordStart(text, shown)
            }
        }
    }
    return if (paced) minOf(shown, text.length) else text.length
}

/** Where the word after [from] starts, or the end of the text. */
internal fun nextWordStart(text: String, from: Int): Int {
    var i = from
    while (i < text.length && !text[i].isWhitespace()) i++
    while (i < text.length && text[i].isWhitespace()) i++
    return i
}

/** Parsed once per text. */
@Composable
internal fun rememberAnswerBlocks(text: String): List<Block> = remember(text) { AnswerMarkdown.parse(text) }
