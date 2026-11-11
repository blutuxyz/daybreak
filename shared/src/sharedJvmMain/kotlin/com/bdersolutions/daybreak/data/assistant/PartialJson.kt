package com.bdersolutions.daybreak.data.assistant

/**
 * Reads a string field out of JSON that is still being written, so a website's code can be shown while the
 * model writes it. Pure, so it is tested.
 */
object PartialJson {
    /**
     * The value of the string field [key] so far: everything after its opening quote, unescaped, up to the
     * closing quote or the end of [raw] (a half-written escape is left out). Null when the field has not started.
     */
    fun string(raw: String, key: String): String? {
        val start = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"").find(raw) ?: return null
        val out = StringBuilder()
        var i = start.range.last + 1
        while (i < raw.length) {
            val c = raw[i]
            if (c == '"') return out.toString()
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            if (i + 1 >= raw.length) break
            val escaped = raw[i + 1]
            if (escaped == 'u') {
                if (i + 6 > raw.length) break
                val code = raw.substring(i + 2, i + 6).toIntOrNull(16) ?: break
                out.append(code.toChar())
                i += 6
                continue
            }
            out.append(
                when (escaped) {
                    'n' -> '\n'
                    't' -> '\t'
                    'r' -> '\r'
                    'b' -> '\b'
                    'f' -> '\u000C'
                    else -> escaped
                },
            )
            i += 2
        }
        return out.toString()
    }
}
