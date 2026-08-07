package uk.org.retallack.jarvis.data.ha

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HaClientIntegrationTest {

    private lateinit var haClient: HaClient
    private lateinit var mockWebServer: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeEach
    fun setup() {
        haClient = HaClient(json)
        mockWebServer = MockWebServer()
        mockWebServer.start()
        haClient.configure(mockWebServer.url("/").toString(), "test-token-123")
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `checkConnection returns status from HA API`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message": "API running."}""")
                .addHeader("Content-Type", "application/json"),
        )

        val status = haClient.checkConnection()
        assertEquals("API running.", status.message)

        val request = mockWebServer.takeRequest()
        assertEquals("/api/", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `getConfig returns HA configuration`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "version": "2024.12.1",
                        "location_name": "Home",
                        "currency": "GBP",
                        "unit_system": {
                            "temperature": "°C",
                            "length": "km",
                            "mass": "kg",
                            "volume": "L"
                        }
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val config = haClient.getConfig()
        assertEquals("2024.12.1", config.version)
        assertEquals("Home", config.location_name)
        assertEquals("GBP", config.currency)
        assertEquals("°C", config.unitSystem?.temperature)
    }

    @Test
    fun `getAllStates returns entity state list`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                        {
                            "entity_id": "light.living_room",
                            "state": "on",
                            "attributes": {},
                            "last_changed": "2024-01-01T00:00:00Z",
                            "last_updated": "2024-01-01T00:00:00Z"
                        },
                        {
                            "entity_id": "switch.kitchen",
                            "state": "off",
                            "attributes": {},
                            "last_changed": "2024-01-01T00:00:00Z",
                            "last_updated": "2024-01-01T00:00:00Z"
                        }
                    ]
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val states = haClient.getAllStates()
        assertEquals(2, states.size)
        assertEquals("light.living_room", states[0].entityId)
        assertEquals("on", states[0].state)
        assertEquals("switch.kitchen", states[1].entityId)
        assertEquals("off", states[1].state)
    }

    @Test
    fun `processConversation sends request and parses action_done response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "response": {
                            "response_type": "action_done",
                            "speech": {
                                "plain": {
                                    "speech": "Turned on the living room light"
                                }
                            },
                            "card": {},
                            "data": {
                                "success": [
                                    {"name": "Living Room Light", "type": "light", "id": "light.living_room"}
                                ],
                                "failed": []
                            }
                        },
                        "conversation_id": "conv-abc-123",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val response = haClient.processConversation(
            uk.org.retallack.jarvis.data.ha.model.ConversationRequest(
                text = "turn on the living room light",
                language = "en",
            ),
        )

        assertEquals("action_done", response.response.responseType)
        assertEquals("Turned on the living room light", response.response.speech?.plain?.speech)
        assertEquals("conv-abc-123", response.conversationId)
        assertEquals(false, response.continueConversation)
        assertEquals(1, response.response.data?.success?.size)
        assertEquals("Living Room Light", response.response.data?.success?.first()?.name)

        val request = mockWebServer.takeRequest()
        assertEquals("/api/conversation/process", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("turn on the living room light"))
    }

    @Test
    fun `processConversation handles error response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "response": {
                            "response_type": "error",
                            "speech": {
                                "plain": {
                                    "speech": "Sorry, I couldn't find that device"
                                }
                            },
                            "card": {},
                            "data": {
                                "code": "no_valid_targets",
                                "success": [],
                                "failed": []
                            }
                        },
                        "conversation_id": "conv-err-456",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val response = haClient.processConversation(
            uk.org.retallack.jarvis.data.ha.model.ConversationRequest(
                text = "turn on the fridge",
                language = "en",
            ),
        )

        assertEquals("error", response.response.responseType)
        assertEquals("no_valid_targets", response.response.data?.code)
    }

    @Test
    fun `processConversation sends agent_id when specified`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "response": {
                            "response_type": "action_done",
                            "speech": {"plain": {"speech": "Done"}},
                            "card": {}
                        },
                        "conversation_id": "conv-agent-789",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        haClient.processConversation(
            uk.org.retallack.jarvis.data.ha.model.ConversationRequest(
                text = "turn on the light",
                language = "en",
                agentId = "conversation.chatgpt",
            ),
        )

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("conversation.chatgpt"))
    }

    @Test
    fun `HTTP error throws exception`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(401).setBody("Unauthorized"),
        )

        assertThrows<Exception> {
            haClient.checkConnection()
        }
    }
}
