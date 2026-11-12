package com.bdersolutions.daybreak.untis

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/**
 * Who a WebUntis account is, as the school's own token says: the school ([tenantId]) and the user ([userId]).
 * Both are ids the school server assigns; neither is an e-mail address or a name, so changing the e-mail in
 * WebUntis does not change them. This is what a Daybreak account binds (see docs/v2-spec.md).
 */
data class UntisIdentity(val tenantId: String, val userId: Long)

/**
 * Reads the claims of the token `getAuthToken` hands out (an RS256 JWT, 15 minutes). Only the payload is
 * read; the signature is not checked here, because the app asked its own school server for the token a
 * moment ago and the result is only used to name the account, never to grant anything.
 */
object TokenIdentity {
    /** The identity in [token], or null when it is not a JWT or lacks a school or a user id. */
    fun parse(token: String?): UntisIdentity? {
        val parts = token?.split('.') ?: return null
        if (parts.size != 3) return null
        val payload = runCatching {
            JsonParser.parseString(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).asJsonObject
        }.getOrNull() ?: return null
        val tenant = payload.text("tenant_id")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val user = payload.number("user_id")?.takeIf { it > 0 } ?: return null
        return UntisIdentity(tenant, user)
    }

    private fun JsonObject.text(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    /** A number written as a number or as a string of digits. */
    private fun JsonObject.number(name: String): Long? {
        val primitive = get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        return if (primitive.isNumber) primitive.asLong else primitive.asString.trim().toLongOrNull()
    }
}
