package com.bdersolutions.daybreak.untis

import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP with the parameters used by the WebUntis mobile API:
 * SHA-1, 30 seconds, six digits.
 */
object Totp {
    private const val PERIOD_SECONDS = 30L

    fun generate(secret: String, epochMillis: Long = System.currentTimeMillis()): Int {
        val key = Base32.decode(secret)
        require(key.isNotEmpty()) { "The WebUntis key is empty" }

        val counter = epochMillis / (PERIOD_SECONDS * 1000L)
        val buffer = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(buffer)

        val offset = hash[hash.lastIndex].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        return binary % 1_000_000
    }

    /** Returns a zero-padded code, useful for display and tests. */
    fun generateString(secret: String, epochMillis: Long = System.currentTimeMillis()): String =
        generate(secret, epochMillis).toString().padStart(6, '0')
}

internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray {
        val normalized = input
            .uppercase(Locale.ROOT)
            .replace(" ", "")
            .replace("-", "")
            .trimEnd('=')
        if (normalized.isEmpty()) return ByteArray(0)
        if (normalized.any { it !in ALPHABET }) {
            throw IllegalArgumentException("Invalid base32 secret")
        }

        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (character in normalized) {
            val value = ALPHABET.indexOf(character)
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
