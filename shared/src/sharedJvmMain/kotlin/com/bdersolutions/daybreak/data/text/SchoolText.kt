package com.bdersolutions.daybreak.data.text

/**
 * Text from WebUntis (notices of the day, messages) is sometimes HTML (`<b>`, `<br/>`, `&amp;`)
 * and sometimes plain text with line breaks. This decides which, and prepares HTML so that its
 * plain line breaks survive the HTML parser.
 */
object SchoolText {
    private val TAG = Regex("""</?\s*[a-zA-Z][a-zA-Z0-9]*(\s[^<>]*)?/?\s*>""")
    private val ENTITY = Regex("""&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);""")
    private val BREAKING_TAG = Regex("""<\s*(br|p|div|li|ul|ol|h[1-6]|table|tr)\b""", RegexOption.IGNORE_CASE)

    /** True when [text] holds HTML tags or entities. A lone "<" or "a < b" is not HTML. */
    fun looksLikeHtml(text: String): Boolean = TAG.containsMatchIn(text) || ENTITY.containsMatchIn(text)

    /**
     * [text] ready for an HTML parser. If it has no tags that break lines itself, its newlines are
     * turned into `<br>`, since a parser would fold them into spaces.
     */
    fun htmlWithLineBreaks(text: String): String {
        val normalized = text.replace("\r\n", "\n").trim()
        return if (BREAKING_TAG.containsMatchIn(normalized)) normalized else normalized.replace("\n", "<br>")
    }
}
