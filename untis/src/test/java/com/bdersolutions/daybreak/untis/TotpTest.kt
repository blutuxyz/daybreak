package com.bdersolutions.daybreak.untis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TotpTest {
    @Test
    fun matchesKnownWebUntisVector() {
        assertEquals(
            439266,
            Totp.generate("ABCDEFGHIJKLMNOP", 1_537_524_711_916L),
        )
    }

    @Test
    fun matchesRfc6238Sha1Vector() {
        // RFC 6238 test secret "12345678901234567890", truncated to six digits.
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals(287082, Totp.generate(secret, 59_000L))
    }

    @Test
    fun acceptsLowercaseAndSpaces() {
        assertEquals(
            Totp.generate("ABCDEFGHIJKLMNOP", 1_537_524_711_916L),
            Totp.generate("abcd efgh ijkl mnop", 1_537_524_711_916L),
        )
    }

    @Test
    fun rejectsInvalidBase32() {
        assertThrows(IllegalArgumentException::class.java) {
            Totp.generate("not-a-valid-secret!", 0L)
        }
    }
}
