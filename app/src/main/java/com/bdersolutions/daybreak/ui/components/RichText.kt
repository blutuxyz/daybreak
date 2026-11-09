package com.bdersolutions.daybreak.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.bdersolutions.daybreak.data.text.SchoolText

private val WEB_ADDRESS = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

private fun linkStyles(linkColor: Color) =
    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))

/**
 * Text from the school (a notice, a message) for display: HTML becomes styled text with working
 * links and line breaks, plain text keeps its line breaks and gets its web addresses linked.
 */
fun schoolRichText(raw: String, linkColor: Color): AnnotatedString =
    if (SchoolText.looksLikeHtml(raw)) {
        val parsed = AnnotatedString.fromHtml(SchoolText.htmlWithLineBreaks(raw), linkStyles(linkColor))
        // Paragraph tags leave blank lines at the ends.
        val start = parsed.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val end = parsed.text.indexOfLast { !it.isWhitespace() } + 1
        if (end > start) parsed.subSequence(start, end) else parsed
    } else {
        linkified(raw.trim(), linkColor)
    }

/** The plain words of school text, for one-line previews: HTML tags and entities removed. */
fun schoolPlainText(raw: String): String =
    if (SchoolText.looksLikeHtml(raw)) {
        AnnotatedString.fromHtml(SchoolText.htmlWithLineBreaks(raw)).text.replace(Regex("\\s+"), " ").trim()
    } else {
        raw.trim()
    }

/** [text] with every http(s) address turned into a tappable link. Trailing punctuation stays text. */
fun linkified(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in WEB_ADDRESS.findAll(text)) {
        val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'')
        val start = match.range.first
        append(text.substring(cursor, start))
        withLink(LinkAnnotation.Url(url, linkStyles(linkColor))) { append(url) }
        cursor = start + url.length
    }
    append(text.substring(cursor))
}
