package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.account.ApiResult
import com.bdersolutions.daybreak.data.account.Plan
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * The desktop HTTP client against a small stand-in for the update server: the requests must have the shape
 * the server (docs/v2-spec.md) reads, and the answers must come back as the shared models.
 */
class DesktopHttpAccountApiTest {
    private class Seen(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private lateinit var server: HttpServer
    private val seen = mutableListOf<Seen>()
    private var status = 200
    private var answer = "{}"
    private lateinit var api: DesktopHttpAccountApi

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString() }
            seen += Seen(exchange.requestMethod, exchange.requestURI.rawPath, headers, exchange.requestBody.readBytes().decodeToString())
            val bytes = answer.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        api = DesktopHttpAccountApi("http://127.0.0.1:${server.address.port}/", installId = { "install-0123456789abcdef" })
    }

    @After
    fun stop() = server.stop(0)

    private val account = """{"accountId":"acc1","plan":"plus","planUntil":"2027-01-01T00:00:00Z","grandfathered":true,
        "webuntis":[{"id":"w1","label":"Schule"}],"proTrial":{"offered":true,"accepted":false,"running":false,"until":null}}"""

    @Test
    fun createSendsTheOldCodeAndReadsTheAnswer() = runBlocking {
        answer = """{"accountId":"acc1","plan":"free","restoreCode":"DA-K7M2-9QXP-4HTR","token":"tok","legacy":"granted"}"""
        status = 201

        val result = api.create("install-0123456789abcdef", "DB-K7M2-9QXP")

        val made = (result as ApiResult.Ok).value
        assertEquals("DA-K7M2-9QXP-4HTR", made.restoreCode)
        assertEquals("tok", made.token)
        assertEquals("granted", made.legacy)
        val request = seen.single()
        assertEquals("POST", request.method)
        assertEquals("/v1/account", request.path)
        assertEquals("install-0123456789abcdef", request.headers["x-daybreak-install"])
        assertTrue(request.body.contains("\"legacyUnlockCode\":\"DB-K7M2-9QXP\""))
    }

    @Test
    fun aSignedInCallCarriesTheTokenAndReadsThePlan() = runBlocking {
        answer = account

        val state = (api.account("tok-1") as ApiResult.Ok).value

        assertEquals(Plan.PLUS, state.plan)
        assertTrue(state.grandfathered)
        assertTrue(state.proTrial.offered)
        assertEquals("Schule", state.webuntis.single().label)
        assertEquals("Bearer tok-1", seen.single().headers["authorization"])
        assertEquals("GET", seen.single().method)
    }

    @Test
    fun bindSendsTheLoginAndUnbindIsADelete() = runBlocking {
        answer = """{"binding":{"id":"w1","label":"Schule"},"sharedWithOthers":true,"account":$account}"""
        status = 201
        val bound = (api.bind("tok", "mese.webuntis.com", "myschool", 4711L, "Schule") as ApiResult.Ok).value
        assertTrue(bound.sharedWithOthers)
        assertTrue(seen[0].body.contains("\"studentId\":4711"))
        assertEquals("/v1/account/webuntis", seen[0].path)

        status = 200
        answer = account
        api.unbind("tok", "w 1")
        assertEquals("DELETE", seen[1].method)
        assertEquals("/v1/account/webuntis/w%201", seen[1].path)
    }

    @Test
    fun checkoutSendsThePlanAndOnlyAcceptsAnHttpsAddress() = runBlocking {
        answer = """{"url":"https://checkout.stripe.com/c/pay/cs_test"}"""
        assertEquals("https://checkout.stripe.com/c/pay/cs_test", (api.checkout("tok", Plan.PRO) as ApiResult.Ok).value)
        assertTrue(seen.single().body.contains("\"plan\":\"pro\""))

        answer = """{"url":"http://not-safe.example/"}"""
        assertTrue(api.checkout("tok", Plan.PLUS) is ApiResult.Fail)
    }

    @Test
    fun serverErrorsBecomeTheirOwnErrors() = runBlocking {
        status = 401
        answer = """{"error":"unauthorized"}"""
        assertEquals(AccountError.UNAUTHORIZED, (api.account("old") as ApiResult.Fail).error)

        status = 404
        answer = """{"error":"unknown_code"}"""
        assertEquals(AccountError.UNKNOWN_CODE, (api.restore("DA-AAAA-BBBB-CCCC") as ApiResult.Fail).error)

        status = 409
        answer = """{"error":"limit"}"""
        assertEquals(AccountError.LIMIT, (api.bind("tok", "s", "x", 1L, "l") as ApiResult.Fail).error)

        status = 409
        answer = """{"error":"already_accepted"}"""
        assertEquals(AccountError.NOT_OFFERED, (api.acceptTrial("tok") as ApiResult.Fail).error)

        status = 429
        answer = "{}"
        assertEquals(AccountError.RATE_LIMITED, (api.account("tok") as ApiResult.Fail).error)
    }

    @Test
    fun anUnreachableServerIsOffline() = runBlocking {
        val closed = DesktopHttpAccountApi("http://127.0.0.1:1", installId = { "install-0123456789abcdef" })
        assertEquals(AccountError.OFFLINE, (closed.account("tok") as ApiResult.Fail).error)
        assertNull(null)
    }
}
