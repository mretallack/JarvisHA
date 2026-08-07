package uk.org.retallack.jarvis.data.ha

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HaClientTest {

    private lateinit var haClient: HaClient
    private lateinit var mockWebServer: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeEach
    fun setup() {
        haClient = HaClient(json)
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `isConfigured returns false before configure called`() {
        assertFalse(haClient.isConfigured)
    }

    @Test
    fun `isConfigured returns true after configure called`() {
        haClient.configure(mockWebServer.url("/").toString(), "test-token")
        assertTrue(haClient.isConfigured)
    }

    @Test
    fun `requireApi throws when not configured`() {
        assertThrows<IllegalStateException> {
            runBlocking { haClient.checkConnection() }
        }
    }

    @Test
    fun `configure normalizes URL with trailing slash`() {
        haClient.configure(mockWebServer.url("").toString().trimEnd('/'), "token")
        assertTrue(haClient.isConfigured)
    }

    @Test
    fun `configure skips reconfiguration for same url and token`() {
        val url = mockWebServer.url("/").toString()
        haClient.configure(url, "token1")
        assertTrue(haClient.isConfigured)
        // This should not throw or re-create the API
        haClient.configure(url, "token1")
        assertTrue(haClient.isConfigured)
    }

    @Test
    fun `configure reconfigures for different token`() {
        val url = mockWebServer.url("/").toString()
        haClient.configure(url, "token1")
        haClient.configure(url, "token2")
        assertTrue(haClient.isConfigured)
    }

    @Test
    fun `auth interceptor adds bearer token header`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message": "API running."}""")
                .addHeader("Content-Type", "application/json"),
        )

        haClient.configure(mockWebServer.url("/").toString(), "my-secret-token")
        haClient.checkConnection()

        val request = mockWebServer.takeRequest()
        assertEquals("Bearer my-secret-token", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Content-Type"))
    }

    @Test
    fun `checkConnection parses API status`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message": "API running."}""")
                .addHeader("Content-Type", "application/json"),
        )

        haClient.configure(mockWebServer.url("/").toString(), "token")
        val status = haClient.checkConnection()

        assertEquals("API running.", status.message)
    }
}
