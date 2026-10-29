package com.bdersolutions.daybreak.untis

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class QrLoginData(
    val server: String,
    val school: String,
    val username: String,
    val secret: String,
)

/**
 * Parses the payload of the QR code shown under
 * WebUntis → Profil → Freigaben → Zugriff über Untis Mobile.
 *
 * Example:
 * untis://setschool?url=https%3A%2F%2Fschool.webuntis.com&school=school&user=user&key=ABCDEF
 */
object UntisQrParser {
    fun parse(raw: String): QrLoginData {
        val trimmed = raw.trim()
        require(trimmed.startsWith("untis://setschool", ignoreCase = true)) {
            "This is not an Untis Mobile login QR code."
        }

        val queryStart = trimmed.indexOf('?')
        require(queryStart >= 0) { "The QR code does not contain login parameters." }
        val query = trimmed.substring(queryStart + 1)
        val values = parseQuery(query)

        val server = values["url"] ?: values["server"] ?: values["apiurl"]
        val school = values["school"] ?: values["schoolname"]
        val user = values["user"] ?: values["username"]
        val secret = values["key"] ?: values["secret"]

        require(!server.isNullOrBlank()) { "The QR code does not contain a WebUntis server." }
        require(!school.isNullOrBlank()) { "The QR code does not contain a school name." }
        require(!user.isNullOrBlank()) { "The QR code does not contain a user name." }
        require(!secret.isNullOrBlank()) { "The QR code does not contain a key." }

        return QrLoginData(
            server = UntisEndpoints.normalizeServer(server),
            school = school.trim(),
            username = user.trim(),
            secret = secret.trim(),
        )
    }

    fun parseOrNull(raw: String): QrLoginData? = runCatching { parse(raw) }.getOrNull()

    private fun parseQuery(query: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (part in query.split('&')) {
            if (part.isBlank()) continue
            val separator = part.indexOf('=')
            val key = if (separator >= 0) part.substring(0, separator) else part
            val value = if (separator >= 0) part.substring(separator + 1) else ""
            if (key.isBlank()) continue
            result[decode(key).lowercase()] = decode(value)
        }
        return result
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        value
    }
}

object UntisEndpoints {
    /** Accepts a host or full URL and always returns an https base URL. */
    fun normalizeServer(raw: String): String {
        var value = raw.trim()
        require(value.isNotEmpty()) { "The WebUntis server is empty." }
        value = value.substringBefore('?').substringBefore('#')
        value = when {
            value.startsWith("https://", ignoreCase = true) -> value.substring(8)
            value.startsWith("http://", ignoreCase = true) -> value.substring(7)
            else -> value
        }
        value = value.trimEnd('/')
        val hostAndPath = value.substringBefore('/')
        val host = hostAndPath.substringBefore(':')
        val port = hostAndPath.substringAfter(':', "")
        require(host.isNotBlank() && host.none { it.isWhitespace() }) { "The WebUntis server is invalid." }
        require(host.contains('.') || host == "localhost") { "The WebUntis server is invalid." }
        return if (port.isBlank()) "https://$host" else "https://$host:$port"
    }
}
