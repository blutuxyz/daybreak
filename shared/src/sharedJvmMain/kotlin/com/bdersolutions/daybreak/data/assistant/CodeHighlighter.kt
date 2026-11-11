package com.bdersolutions.daybreak.data.assistant

/**
 * Splits code into coloured pieces for the chat's code blocks and the website's code view. It is a small
 * scanner, not a parser: keywords, strings, comments, numbers, and for markup the tags and attributes. Pure,
 * so it is tested. Joining the texts of [tokenize] gives the code back exactly.
 */
object CodeHighlighter {
    enum class Kind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, TAG, ATTRIBUTE }

    data class Token(val kind: Kind, val text: String)

    private enum class Family { GENERIC, MARKUP, CSS, JSON }

    private val kotlin = setOf(
        "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when", "for", "while", "do", "try",
        "catch", "finally", "throw", "import", "package", "is", "as", "in", "null", "true", "false", "this", "super",
        "override", "private", "public", "protected", "internal", "data", "sealed", "companion", "init", "suspend", "typealias",
    )
    private val java = setOf(
        "public", "private", "protected", "static", "final", "class", "interface", "return", "if", "else", "for", "while",
        "do", "switch", "case", "break", "continue", "new", "try", "catch", "finally", "throw", "throws", "import", "package",
        "extends", "implements", "void", "int", "long", "double", "float", "boolean", "char", "byte", "short", "this", "super",
        "null", "true", "false", "abstract", "enum", "instanceof",
    )
    private val python = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "is", "import", "from", "as",
        "with", "try", "except", "finally", "raise", "pass", "lambda", "None", "True", "False", "yield", "async", "await",
        "global", "nonlocal", "break", "continue",
    )
    private val c = setOf(
        "int", "char", "float", "double", "void", "long", "short", "unsigned", "return", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "struct", "class", "public", "private", "static", "const", "new", "delete",
        "true", "false", "null", "nullptr", "using", "namespace", "typedef", "include", "template", "bool", "var", "string",
    )
    private val sql = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "table", "join", "left",
        "right", "inner", "on", "group", "by", "order", "having", "limit", "and", "or", "not", "null", "as", "distinct",
    )
    private val shell = setOf("if", "then", "else", "fi", "for", "while", "do", "done", "case", "esac", "function", "echo", "export", "return")
    private val javascript = setOf(
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
        "new", "class", "extends", "import", "export", "from", "default", "async", "await", "try", "catch", "finally", "throw",
        "typeof", "instanceof", "this", "super", "null", "undefined", "true", "false", "yield", "interface", "type", "enum",
        "implements", "of", "in",
    )
    private val all = kotlin + java + python + c + sql + shell + javascript
    private val hashLanguages = setOf("python", "py", "sh", "bash", "shell", "zsh")
    private val noSlashLanguages = setOf("python", "py", "sh", "bash", "shell", "zsh", "sql")

    fun tokenize(code: String, language: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val name = language.trim().lowercase()
        when (familyOf(name)) {
            Family.MARKUP -> markup(code, tokens)
            Family.CSS -> scan(code, tokens, emptySet(), slashComments = true, hashComments = false, css = true)
            Family.JSON -> scan(code, tokens, setOf("true", "false", "null"), slashComments = false, hashComments = false, css = false)
            Family.GENERIC -> scan(
                code,
                tokens,
                keywordsFor(name),
                slashComments = name !in noSlashLanguages,
                hashComments = name in hashLanguages,
                css = false,
            )
        }
        return merged(tokens)
    }

    private fun familyOf(name: String): Family = when (name) {
        "html", "htm", "xml", "svg", "xhtml" -> Family.MARKUP
        "css", "scss", "less" -> Family.CSS
        "json" -> Family.JSON
        else -> Family.GENERIC
    }

    private fun keywordsFor(name: String): Set<String> = when (name) {
        "kotlin", "kt" -> kotlin
        "java" -> java
        "python", "py" -> python
        "c", "cpp", "c++", "h", "cs", "csharp", "c#" -> c
        "sql" -> sql
        "sh", "bash", "shell", "zsh" -> shell
        "js", "javascript", "jsx", "ts", "typescript", "tsx" -> javascript
        else -> all
    }

    /** Neighbouring pieces of the same kind become one, so the list stays short. */
    private fun merged(tokens: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        for (token in tokens) {
            val last = out.lastOrNull()
            if (last != null && last.kind == token.kind) out[out.lastIndex] = Token(last.kind, last.text + token.text) else out += token
        }
        return out
    }

    private fun scan(
        code: String,
        out: MutableList<Token>,
        keywords: Set<String>,
        slashComments: Boolean,
        hashComments: Boolean,
        css: Boolean,
    ) {
        val n = code.length
        var i = 0
        fun emit(kind: Kind, from: Int, to: Int) {
            if (to > from) out += Token(kind, code.substring(from, to))
        }
        while (i < n) {
            val c = code[i]
            when {
                slashComments && code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    emit(Kind.COMMENT, i, end)
                    i = end
                }
                (slashComments || css) && code.startsWith("/*", i) -> {
                    val close = code.indexOf("*/", i + 2)
                    val end = if (close < 0) n else close + 2
                    emit(Kind.COMMENT, i, end)
                    i = end
                }
                hashComments && c == '#' -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    emit(Kind.COMMENT, i, end)
                    i = end
                }
                c == '"' || c == '\'' || c == '`' -> {
                    val end = stringEnd(code, i)
                    emit(Kind.STRING, i, end)
                    i = end
                }
                c.isDigit() && (i == 0 || !isWordChar(code[i - 1])) -> {
                    var end = i
                    while (end < n && (isWordChar(code[end]) || code[end] == '.')) end++
                    emit(Kind.NUMBER, i, end)
                    i = end
                }
                c.isLetter() || c == '_' -> {
                    var end = i
                    while (end < n && isWordChar(code[end])) end++
                    val word = code.substring(i, end)
                    val kind = when {
                        word in keywords -> Kind.KEYWORD
                        css && nextNonSpace(code, end) == ':' -> Kind.ATTRIBUTE
                        else -> Kind.PLAIN
                    }
                    emit(kind, i, end)
                    i = end
                }
                else -> {
                    emit(Kind.PLAIN, i, i + 1)
                    i++
                }
            }
        }
    }

    private fun markup(code: String, out: MutableList<Token>) {
        val n = code.length
        var i = 0
        fun emit(kind: Kind, from: Int, to: Int) {
            if (to > from) out += Token(kind, code.substring(from, to))
        }
        while (i < n) {
            when {
                code.startsWith("<!--", i) -> {
                    val close = code.indexOf("-->", i + 4)
                    val end = if (close < 0) n else close + 3
                    emit(Kind.COMMENT, i, end)
                    i = end
                }
                code[i] == '<' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '/' || code[i + 1] == '!') -> {
                    // The tag name, with the "<", an optional "/" and "!" (as in <!DOCTYPE).
                    var end = i + 1
                    while (end < n && (code[end] == '/' || code[end] == '!')) end++
                    while (end < n && (isWordChar(code[end]) || code[end] == '-')) end++
                    val tagName = code.substring(i, end)
                    emit(Kind.TAG, i, end)
                    i = end
                    // The attributes, until the closing ">".
                    while (i < n && code[i] != '>') {
                        val ch = code[i]
                        when {
                            ch == '"' || ch == '\'' -> {
                                val quoteEnd = stringEnd(code, i)
                                emit(Kind.STRING, i, quoteEnd)
                                i = quoteEnd
                            }
                            ch.isLetter() || ch == '_' || ch == '-' || ch == ':' || ch == '@' -> {
                                var wordEnd = i
                                while (wordEnd < n && (isWordChar(code[wordEnd]) || code[wordEnd] == '-' || code[wordEnd] == ':' || code[wordEnd] == '@')) wordEnd++
                                val kind = if (nextNonSpace(code, wordEnd) == '=') Kind.ATTRIBUTE else Kind.PLAIN
                                emit(kind, i, wordEnd)
                                i = wordEnd
                            }
                            else -> {
                                emit(Kind.PLAIN, i, i + 1)
                                i++
                            }
                        }
                    }
                    if (i < n) {
                        emit(Kind.PLAIN, i, i + 1)
                        i++
                    }
                    // The text of a <script> or <style> element is code of its own language.
                    val name = tagName.trimStart('/', '!').lowercase()
                    if (!tagName.startsWith("</") && (name == "script" || name == "style") && i < n) {
                        val close = code.indexOf("</$name", i, ignoreCase = true).let { if (it < 0) n else it }
                        if (close > i) {
                            val body = code.substring(i, close)
                            if (name == "style") {
                                scan(body, out, emptySet(), slashComments = true, hashComments = false, css = true)
                            } else {
                                scan(body, out, javascript, slashComments = true, hashComments = false, css = false)
                            }
                            i = close
                        }
                    }
                }
                else -> {
                    // Text between tags, up to the next "<".
                    var end = i + 1
                    while (end < n && code[end] != '<') end++
                    emit(Kind.PLAIN, i, end)
                    i = end
                }
            }
        }
    }

    private fun isWordChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'

    private fun nextNonSpace(code: String, from: Int): Char? {
        var j = from
        while (j < code.length && code[j] == ' ') j++
        return code.getOrNull(j)
    }

    /** The index just after the string that starts at [start]; a single-line string ends at the line break. */
    private fun stringEnd(code: String, start: Int): Int {
        val quote = code[start]
        var j = start + 1
        while (j < code.length) {
            val ch = code[j]
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == quote) return j + 1
            if (ch == '\n' && quote != '`') return j
            j++
        }
        return code.length
    }
}
