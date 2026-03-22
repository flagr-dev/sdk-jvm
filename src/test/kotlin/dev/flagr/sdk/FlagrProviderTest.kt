package dev.flagr.sdk

import dev.flagr.sdk.openfeature.FlagrProvider
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.exceptions.InvalidContextError
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlagrProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: FlagrProvider

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        provider.shutdown()
        server.shutdown()
    }

    private fun enqueueInit(json: String) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: init\ndata: $json\n\n")
        )
    }

    @Test
    fun `getBooleanEvaluation returns correct value`() = runBlocking {
        enqueueInit("""{"dark-mode":{"state":"enabled","enabled_list":[]}}""")
        provider = FlagrProvider("sdk_live_test", server.url("/").toString())
        delay(300)

        val ctx = ImmutableContext("user-123")
        val result = provider.getBooleanEvaluation("dark-mode", false, ctx)
        assertTrue(result.value)
    }

    @Test
    fun `getBooleanEvaluation returns default for unknown flag`() = runBlocking {
        enqueueInit("{}")
        provider = FlagrProvider("sdk_live_test", server.url("/").toString())
        delay(300)

        val ctx = ImmutableContext("user-1")
        val result = provider.getBooleanEvaluation("missing", true, ctx)
        assertTrue(result.value)
    }

    @Test
    fun `throws when targetingKey is missing`() = runBlocking {
        enqueueInit("{}")
        provider = FlagrProvider("sdk_live_test", server.url("/").toString())
        delay(300)

        assertFailsWith<InvalidContextError> {
            provider.getBooleanEvaluation("any", false, null)
        }
    }

    @Test
    fun `partial rollout via OpenFeature context`() = runBlocking {
        enqueueInit("""{"beta":{"state":"partially_enabled","enabled_list":["org-42"]}}""")
        provider = FlagrProvider("sdk_live_test", server.url("/").toString())
        delay(300)

        assertTrue(provider.getBooleanEvaluation("beta", false, ImmutableContext("org-42")).value)
        assertFalse(provider.getBooleanEvaluation("beta", false, ImmutableContext("org-99")).value)
    }

    @Test
    fun `metadata name is flagr`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        provider = FlagrProvider("sdk_live_test", server.url("/").toString())
        assertEquals("flagr", provider.getMetadata().name)
    }
}
