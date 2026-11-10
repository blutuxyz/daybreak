package com.bdersolutions.daybreak.data.assistant

/**
 * The Markdown the assistant writes, read into blocks the app can draw: paragraphs, headings, lists,
 * quotes, code, tables and formulas. One parser for both ways an answer is drawn (Compose text, and the
 * KaTeX page when there are formulas), so they always agree on what is a formula.
 *
 * Formulas are found in every form models use: `$…$`, `$$…$$`, `\(…\)`, `\[…\]`, `\begin{…}…\end{…}`,
 * ```` ```latex ```` blocks, and a line that is nothing but LaTeX without any delimiter. A `$` counts as
 * a formula only where it opens and closes like one (no space inside the edges, no digit right after the
 * closing one), so "5$ und 10$" stays text. Tolerant of half-written input, because answers are parsed
 * again with every streamed piece.
 */
object AnswerMarkdown {
    sealed interface Block {
        data class Paragraph(val text: List<Inline>) : Block
        data class Heading(val level: Int, val text: List<Inline>) : Block

        /** A list; each item knows its own depth and marker, so nested and mixed lists need no tree. */
        data class Items(val items: List<Item>) : Block
        data class Quote(val text: List<Inline>) : Block
        data class Code(val language: String, val code: String) : Block

        /** A formula on its own line. [tex] is without delimiters. */
        data class Math(val tex: String) : Block
        data class Table(val header: List<List<Inline>>, val rows: List<List<List<Inline>>>) : Block
        data object Rule : Block
    }

    /** One list item. [number] is null for a bullet. */
    data class Item(val depth: Int, val number: Int?, val text: List<Inline>)

    sealed interface Inline {
        data class Text(val text: String, val bold: Boolean = false, val italic: Boolean = false) : Inline
        data class Code(val code: String) : Inline
        data class Math(val tex: String) : Inline
        data class Link(val label: String, val url: String) : Inline
    }

    fun parse(markdown: String): List<Block> = BlockParser(markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')).parse()

    fun hasMath(blocks: List<Block>): Boolean = blocks.any { block ->
        when (block) {
            is Block.Math -> true
            is Block.Paragraph -> block.text.containsMath()
            is Block.Heading -> block.text.containsMath()
            is Block.Quote -> block.text.containsMath()
            is Block.Items -> block.items.any { it.text.containsMath() }
            is Block.Table -> (block.header + block.rows.flatten()).any { it.containsMath() }
            is Block.Code, Block.Rule -> false
        }
    }

    private fun List<Inline>.containsMath() = any { it is Inline.Math }

    private val FENCE = Regex("^\\s*(```+|~~~+)\\s*([A-Za-z0-9_+-]*)\\s*$")
    private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val LIST_ITEM = Regex("^(\\s*)([-*+•]|\\d{1,3}[.)])\\s+(.*)$")
    private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")
    private val TABLE_SEPARATOR = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")
    private val BEGIN_ENV = Regex("^\\s*\\\\begin\\{([A-Za-z*]+)\\}")
    private val MATH_LANGUAGES = setOf("latex", "tex", "math", "katex")

    /** LaTeX commands that only appear in formulas; a line built of them and few words is a formula. */
    private val FORMULA_COMMAND = Regex(
        "\\\\(frac|dfrac|tfrac|sqrt|cdot|times|div|pm|mp|int|iint|sum|prod|lim|infty|alpha|beta|gamma|delta|Delta|" +
            "epsilon|varepsilon|theta|lambda|mu|pi|rho|sigma|Sigma|tau|phi|varphi|omega|Omega|leq?|geq?|neq|approx|equiv|" +
            "cdots|ldots|vec|overline|underline|hat|binom|log|ln|sin|cos|tan|exp|left|right|mathrm|text|Rightarrow|" +
            "rightarrow|Leftrightarrow|to|partial|nabla|circ|degree|quad)(?![A-Za-z])",
    )

    internal fun looksLikeFormula(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || '$' in trimmed || "\\(" in trimmed || "\\[" in trimmed || '`' in trimmed) return false
        if (!FORMULA_COMMAND.containsMatchIn(trimmed)) return false
        // What is left once commands and their braces are gone: a formula leaves hardly any words.
        val rest = trimmed
            .replace(Regex("\\\\text\\{[^}]*\\}"), " ")
            .replace(Regex("\\\\[A-Za-z]+"), " ")
            .replace(Regex("[{}^_]"), " ")
        val words = Regex("[A-Za-zÄÖÜäöüß]{3,}").findAll(rest).count()
        return words <= 1
    }

    private class BlockParser(private val lines: List<String>) {
        private var index = 0
        private val blocks = mutableListOf<Block>()
        private val paragraph = mutableListOf<String>()

        fun parse(): List<Block> {
            while (index < lines.size) {
                val line = lines[index]
                when {
                    line.isBlank() -> {
                        flush()
                        index++
                    }
                    FENCE.matches(line) -> fence()
                    line.trim().startsWith("$$") -> delimited("$$", "$$")
                    line.trim().startsWith("\\[") -> delimited("\\[", "\\]")
                    BEGIN_ENV.containsMatchIn(line) -> environment()
                    HEADING.matches(line) -> {
                        flush()
                        val match = HEADING.matchEntire(line)!!
                        blocks += Block.Heading(match.groupValues[1].length, inlines(match.groupValues[2]))
                        index++
                    }
                    RULE.matches(line) -> {
                        flush()
                        blocks += Block.Rule
                        index++
                    }
                    QUOTE.matches(line) -> quote()
                    LIST_ITEM.matches(line) && !RULE.matches(line) -> list()
                    line.trim().startsWith("|") && index + 1 < lines.size && TABLE_SEPARATOR.matches(lines[index + 1]) -> table()
                    looksLikeFormula(line) -> {
                        flush()
                        blocks += Block.Math(line.trim())
                        index++
                    }
                    else -> {
                        paragraph += line
                        index++
                    }
                }
            }
            flush()
            return blocks
        }

        private fun flush() {
            if (paragraph.isEmpty()) return
            val text = paragraph.joinToString("\n") { it.trim() }
            paragraph.clear()
            if (text.isNotBlank()) blocks += Block.Paragraph(inlines(text))
        }

        private fun fence() {
            flush()
            val open = FENCE.matchEntire(lines[index])!!
            val marker = open.groupValues[1]
            val language = open.groupValues[2].lowercase()
            index++
            val body = mutableListOf<String>()
            while (index < lines.size && !lines[index].trim().startsWith(marker.take(3))) body += lines[index++]
            if (index < lines.size) index++ // the closing fence; a half-written block runs to the end
            val code = body.joinToString("\n").trimEnd()
            blocks += if (language in MATH_LANGUAGES) Block.Math(stripDelimiters(code)) else Block.Code(language, code)
        }

        /** `$$…$$` or `\[…\]`, on one line or over several. Text after the closing delimiter is a new paragraph. */
        private fun delimited(open: String, close: String) {
            flush()
            val first = lines[index].trim().removePrefix(open)
            val sameLine = first.indexOf(close)
            if (sameLine >= 0) {
                blocks += Block.Math(first.substring(0, sameLine).trim())
                val after = first.substring(sameLine + close.length).trim()
                index++
                if (after.isNotEmpty()) paragraph += after
                return
            }
            val body = mutableListOf(first)
            index++
            while (index < lines.size) {
                val line = lines[index]
                val end = line.indexOf(close)
                if (end >= 0) {
                    body += line.substring(0, end)
                    val after = line.substring(end + close.length).trim()
                    index++
                    if (after.isNotEmpty()) paragraph += after
                    break
                }
                body += line
                index++
            }
            blocks += Block.Math(body.joinToString("\n").trim())
        }

        private fun environment() {
            flush()
            val name = BEGIN_ENV.find(lines[index])!!.groupValues[1]
            val end = "\\end{$name}"
            val body = mutableListOf<String>()
            while (index < lines.size) {
                val line = lines[index++]
                body += line
                if (end in line) break
            }
            blocks += Block.Math(body.joinToString("\n").trim())
        }

        private fun quote() {
            flush()
            val body = mutableListOf<String>()
            while (index < lines.size) {
                val match = QUOTE.matchEntire(lines[index]) ?: break
                body += match.groupValues[1]
                index++
            }
            blocks += Block.Quote(inlines(body.joinToString("\n").trim()))
        }

        private fun list() {
            flush()
            val items = mutableListOf<Item>()
            var pending: Triple<Int, Int?, StringBuilder>? = null
            fun close() {
                pending?.let { (depth, number, text) -> items += Item(depth, number, inlines(text.toString().trim())) }
                pending = null
            }
            while (index < lines.size) {
                val line = lines[index]
                val match = LIST_ITEM.matchEntire(line)
                if (match != null && !RULE.matches(line)) {
                    close()
                    val indent = match.groupValues[1].replace("\t", "    ").length
                    val marker = match.groupValues[2]
                    val number = marker.dropLast(1).toIntOrNull()?.takeIf { marker.last() == '.' || marker.last() == ')' }
                    pending = Triple((indent / 2).coerceAtMost(3), number, StringBuilder(match.groupValues[3]))
                    index++
                } else if (line.isNotBlank() && line.first().isWhitespace() && pending != null) {
                    // A wrapped item continues on an indented line.
                    pending!!.third.append('\n').append(line.trim())
                    index++
                } else if (line.isBlank() && index + 1 < lines.size && LIST_ITEM.matches(lines[index + 1])) {
                    index++ // a loose list: blank lines between items
                } else {
                    break
                }
            }
            close()
            blocks += Block.Items(items)
        }

        private fun table() {
            flush()
            fun cells(line: String): List<List<Inline>> =
                line.trim().removePrefix("|").removeSuffix("|").split(Regex("(?<!\\\\)\\|")).map { inlines(it.trim().replace("\\|", "|")) }
            val header = cells(lines[index])
            index += 2
            val rows = mutableListOf<List<List<Inline>>>()
            while (index < lines.size && lines[index].trim().startsWith("|")) {
                val row = cells(lines[index++])
                rows += List(header.size) { column -> row.getOrElse(column) { emptyList() } }
            }
            blocks += Block.Table(header, rows)
        }
    }

    private fun stripDelimiters(tex: String): String {
        val trimmed = tex.trim()
        for ((open, close) in listOf("$$" to "$$", "\\[" to "\\]", "$" to "$", "\\(" to "\\)")) {
            if (trimmed.length > open.length + close.length && trimmed.startsWith(open) && trimmed.endsWith(close)) {
                return trimmed.substring(open.length, trimmed.length - close.length).trim()
            }
        }
        return trimmed
    }

    /** Inline Markdown: formulas first (nothing inside them is Markdown), then code, links and emphasis. */
    fun inlines(text: String): List<Inline> = merge(InlineParser(text).parse(bold = false, italic = false))

    private class InlineParser(private val text: String) {
        fun parse(bold: Boolean, italic: Boolean, from: Int = 0, to: Int = text.length): List<Inline> {
            val out = mutableListOf<Inline>()
            val plain = StringBuilder()
            fun emitPlain() {
                if (plain.isNotEmpty()) out += Inline.Text(plain.toString(), bold, italic)
                plain.clear()
            }
            var i = from
            while (i < to) {
                val c = text[i]
                // An escaped character is literal (\$, \*, \_), except the formula openers \( and \[.
                if (c == '\\' && i + 1 < to && text[i + 1] in "$*_`|#[]") {
                    if (text[i + 1] == '[') {
                        val end = text.indexOf("\\]", i + 2).takeIf { it in 0 until to }
                        if (end != null) {
                            emitPlain(); out += Inline.Math(text.substring(i + 2, end).trim()); i = end + 2; continue
                        }
                    }
                    plain.append(text[i + 1]); i += 2; continue
                }
                if (c == '\\' && i + 1 < to && text[i + 1] == '(') {
                    val end = text.indexOf("\\)", i + 2).takeIf { it in 0 until to }
                    if (end != null) {
                        emitPlain(); out += Inline.Math(text.substring(i + 2, end).trim()); i = end + 2; continue
                    }
                }
                if (c == '$') {
                    val math = dollarMath(i, to)
                    if (math != null) {
                        emitPlain(); out += Inline.Math(math.first); i = math.second; continue
                    }
                }
                if (c == '`') {
                    val end = text.indexOf('`', i + 1).takeIf { it in 0 until to }
                    if (end != null && end > i + 1) {
                        emitPlain(); out += Inline.Code(text.substring(i + 1, end)); i = end + 1; continue
                    }
                }
                if (c == '[') {
                    val link = link(i, to)
                    if (link != null) {
                        emitPlain(); out += Inline.Link(link.first, link.second); i = link.third; continue
                    }
                }
                if (text.startsWith("http://", i) || text.startsWith("https://", i)) {
                    var end = i
                    while (end < to && !text[end].isWhitespace() && text[end] !in "<>\"") end++
                    while (end > i && text[end - 1] in ".,;:!?)") end--
                    emitPlain(); out += Inline.Link(text.substring(i, end), text.substring(i, end)); i = end; continue
                }
                if (c == '*' || c == '_') {
                    val double = i + 1 < to && text[i + 1] == c
                    val marker = if (double) "$c$c" else "$c"
                    val close = closing(marker, i + marker.length, to)
                    if (close != null && canOpen(i, marker, to)) {
                        emitPlain()
                        out += parse(bold = bold || double, italic = italic || !double, from = i + marker.length, to = close)
                        i = close + marker.length
                        continue
                    }
                }
                if (text.startsWith("<br>", i, ignoreCase = true) || text.startsWith("<br/>", i, ignoreCase = true)) {
                    plain.append('\n'); i += if (text.startsWith("<br>", i, ignoreCase = true)) 4 else 5; continue
                }
                plain.append(c)
                i++
            }
            emitPlain()
            return out
        }

        /** `$$…$$` or a `$…$` that really is a formula; returns the formula and the index after it. */
        private fun dollarMath(start: Int, to: Int): Pair<String, Int>? {
            if (text.startsWith("$$", start)) {
                val end = text.indexOf("$$", start + 2).takeIf { it in 0 until to } ?: return null
                val tex = text.substring(start + 2, end).trim()
                return if (tex.isEmpty()) null else tex to end + 2
            }
            val open = start + 1
            if (open >= to || text[open].isWhitespace()) return null
            var j = open
            while (j < to) {
                if (text[j] == '\\') {
                    j += 2; continue
                }
                if (text[j] == '\n' && j + 1 < to && text[j + 1] == '\n') return null
                if (text[j] == '$') {
                    val closes = !text[j - 1].isWhitespace() && (j + 1 >= to || !text[j + 1].isDigit())
                    if (closes && j > open) return text.substring(open, j) to j + 1
                    return null
                }
                j++
            }
            return null
        }

        private fun link(start: Int, to: Int): Triple<String, String, Int>? {
            val labelEnd = text.indexOf("](", start + 1).takeIf { it in 0 until to } ?: return null
            if ('\n' in text.substring(start, labelEnd)) return null
            val urlEnd = text.indexOf(')', labelEnd + 2).takeIf { it in 0 until to } ?: return null
            val url = text.substring(labelEnd + 2, urlEnd).trim()
            if (!url.startsWith("https://") && !url.startsWith("http://")) return null
            return Triple(text.substring(start + 1, labelEnd), url, urlEnd + 1)
        }

        /** An emphasis marker opens only before a non-space, and `_` only at the start of a word. */
        private fun canOpen(at: Int, marker: String, to: Int): Boolean {
            val next = at + marker.length
            if (next >= to || text[next].isWhitespace()) return false
            if (marker[0] == '_' && at > 0 && text[at - 1].isLetterOrDigit()) return false
            return true
        }

        private fun closing(marker: String, from: Int, to: Int): Int? {
            var j = from
            while (j < to) {
                when {
                    text[j] == '\\' -> j += 2
                    text[j] == '`' -> j = (text.indexOf('`', j + 1).takeIf { it in 0 until to } ?: j) + 1
                    text[j] == '$' -> j = dollarMath(j, to)?.second ?: (j + 1)
                    text.startsWith(marker, j) && j > from && !text[j - 1].isWhitespace() &&
                        (marker.length == 2 || j + 1 >= to || text[j + 1] != marker[0]) &&
                        (marker[0] != '_' || j + marker.length >= to || !text[j + marker.length].isLetterOrDigit()) -> return j
                    // A double marker inside a single one belongs to the inner text.
                    marker.length == 1 && text.startsWith("${marker}$marker", j) -> j += 2
                    else -> j++
                }
            }
            return null
        }
    }

    private fun merge(parts: List<Inline>): List<Inline> {
        val out = mutableListOf<Inline>()
        for (part in parts) {
            val last = out.lastOrNull()
            if (part is Inline.Text && last is Inline.Text && last.bold == part.bold && last.italic == part.italic) {
                out[out.lastIndex] = last.copy(text = last.text + part.text)
            } else if (!(part is Inline.Text && part.text.isEmpty())) {
                out += part
            }
        }
        return out
    }
}
