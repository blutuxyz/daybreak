package com.bdersolutions.daybreak.data.support

import com.google.gson.JsonParser

/** What asking the server about a payment came to. */
enum class PaymentCheck {
    /** The server has a payment for the code. */
    PAID,

    /** The server answered, and has no payment for the code (yet). */
    NOT_FOUND,

    /** The server could not be reached or answered something unusable. */
    UNAVAILABLE,
}

/** Reads the answer of `GET /v1/support/status`, the same for the Android and the desktop client. */
object PaymentStatusParser {
    /** Reads `{"paid": true|false}`; anything else is [PaymentCheck.UNAVAILABLE]. */
    fun parse(body: String?): PaymentCheck {
        if (body.isNullOrBlank()) return PaymentCheck.UNAVAILABLE
        return try {
            val paid = JsonParser.parseString(body).asJsonObject.get("paid")
            when {
                paid == null || !paid.isJsonPrimitive || !paid.asJsonPrimitive.isBoolean -> PaymentCheck.UNAVAILABLE
                paid.asBoolean -> PaymentCheck.PAID
                else -> PaymentCheck.NOT_FOUND
            }
        } catch (_: RuntimeException) {
            PaymentCheck.UNAVAILABLE
        }
    }
}
