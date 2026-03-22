package dev.flagr.sdk

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlagrClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: FlagrClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    private fun sseResponse(vararg lines: String): MockResponse {
        val body = lines.joinToString("\n", postfix = "\n")
        return MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)
            // Keep connection open long enough for the test
            .setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun initEvent(json: String) = sseResponse(
        "event: init",
        "data: $json",
        "",
    )

    @Test
    fun `isEnabled returns false for unknown flag before cache is seeded`() {
        // Delay the server response so cache starts empty
        server.enqueue(MockResponse().setResponseCode(200).setHeadersDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS))
        client = FlagrClient("sdk_live_test", server.url("/").toString(), OkHttpClient())
        assertFalse(client.isEnabled("unknown", "t"))
    }

    @Test
    fun `isEnabled resolves correctly after init event`() = runBlocking {
        server.enqueue(
            initEvent("""{"checkout":{"state":"enabled","enabled_list":[]}}""")
        )
        client = FlagrClient("sdk_live_test", server.url("/").toString(), OkHttpClient())
        delay(300) // allow SSE to be processed
        assertTrue(client.isEnabled("checkout", "any-tenant"))
    }

    @Test
    fun `partial rollout - tenant in list is enabled, others disabled`() = runBlocking {
        server.enqueue(
            initEvent("""{"beta":{"state":"partially_enabled","enabled_list":["user-1"]}}""")
        )
        client = FlagrClient("sdk_live_test", server.url("/").toString(), OkHttpClient())
        delay(300)
        assertTrue(client.isEnabled("beta", "user-1"))
        assertFalse(client.isEnabled("beta", "user-2"))
    }

    @Test
    fun `onChange fires immediately with current value`() = runBlocking {
        server.enqueue(
            initEvent("""{"flag-a":{"state":"enabled","enabled_list":[]}}""")
        )
        client = FlagrClient("sdk_live_test", server.url("/").toString(), OkHttpClient())
        delay(300)

        val received = mutableListOf<Boolean>()
        val sub = client.onChange("flag-a", "t") { received.add(it) }
        assertEquals(listOf(true), received)
        sub.unsubscribe()
    }

    @Test
    fun `onChange fires on flag_update event`() = runBlocking {
        // Init: disabled. Then update to enabled.
        server.enqueue(
            sseResponse(
                "event: init",
                """data: {"flag-b":{"state":"disabled","enabled_list":[]}}""",
                "",
                "event: flag_update",
                """data: {"flag_key":"flag-b","state":"enabled","enabled_list":[]}""",
                "",
            )
        )
        client = FlagrClient("sdk_live_test", server.url("/").toString(), OkHttpClient())

        val received = mutableListOf<Boolean>()
        val sub = client.onChange("flag-b", "t") { received.add(it) }
        delay(500)

        // Immediate call fires with whatever value is in cache at subscribe time,
        // then the update event fires once the SSE is processed.
        assertTrue(received.last()) // eventually true after flag_update
        sub.unsubscribe()
    }

    @Test
    fun `reconnects after server disconnect`() = runBlocking {
        // First response closes immediately; second has the real init
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(
            initEvent("""{"f":{"state":"enabled","enabled_list":[]}}""")
        )
        client = FlagrClient("sdk_live_test", server.url("/").toString(), OkHttpClient())
        delay(6_000) // wait past the 5s reconnect delay
        assertTrue(client.isEnabled("f", "t"))
    }

    @Test
    fun `stops retrying on 401`() = runBlocking {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(401))
        }
        client = FlagrClient("sdk_live_test", server.url("/").toString(), OkHttpClient())
        delay(300)
        // Only one request should have been made (no retry after 401)
        assertEquals(1, server.requestCount)
    }
}
