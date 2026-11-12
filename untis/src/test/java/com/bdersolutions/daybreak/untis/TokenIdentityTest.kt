package com.bdersolutions.daybreak.untis

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenIdentityTest {
    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        fun part(text: String) = encoder.encodeToString(text.toByteArray())
        return "${part("""{"kid":"123456789","alg":"RS256"}""")}.${part(payload)}.c2lnbmF0dXJl"
    }

    @Test
    fun readsTheSchoolAndTheUserFromTheClaims() {
        val token = jwt("""{"tenant_id":"1234567","user_id":4711,"person_id":99,"sub":"someone","iss":"webuntis","exp":2}""")

        assertEquals(UntisIdentity("1234567", 4711L), TokenIdentity.parse(token))
    }

    @Test
    fun theUserIdMayComeAsAStringAndTheTenantMayBeANumber() {
        assertEquals(UntisIdentity("1234567", 4711L), TokenIdentity.parse(jwt("""{"tenant_id":1234567,"user_id":"4711"}""")))
    }

    @Test
    fun theUsernameAndPersonIdAreNotPartOfIt() {
        // Two tokens of the same user, one with another username (a renamed login), are the same identity.
        val first = TokenIdentity.parse(jwt("""{"tenant_id":"77","user_id":5,"username":"old","person_id":1}"""))
        val second = TokenIdentity.parse(jwt("""{"tenant_id":"77","user_id":5,"username":"new","person_id":2}"""))
        assertEquals(first, second)
    }

    @Test
    fun anythingThatIsNotAUsableTokenGivesNothing() {
        assertNull(TokenIdentity.parse(null))
        assertNull(TokenIdentity.parse(""))
        assertNull(TokenIdentity.parse("opaque-token"))
        assertNull(TokenIdentity.parse("a.b.c"))
        assertNull(TokenIdentity.parse(jwt("""{"user_id":5}""")))
        assertNull(TokenIdentity.parse(jwt("""{"tenant_id":"77"}""")))
        assertNull(TokenIdentity.parse(jwt("""{"tenant_id":" ","user_id":5}""")))
        assertNull(TokenIdentity.parse(jwt("""{"tenant_id":"77","user_id":0}""")))
        assertNull(TokenIdentity.parse(jwt("""["not","an","object"]""")))
    }
}
