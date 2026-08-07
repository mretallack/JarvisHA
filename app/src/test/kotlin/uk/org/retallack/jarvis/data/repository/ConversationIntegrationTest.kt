package uk.org.retallack.jarvis.data.repository

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
import uk.org.retallack.jarvis.data.ha.HaClient

/**
 * Integration tests for ConversationRepository using MockWebServer
 * to simulate full round-trip conversation processing with HA.
 */
class ConversationIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var haClient: HaClient
    private lateinit var repository: ConversationRepository
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        haClient = HaClient(json)
        haClient.configure(mockWebServer.url("/").toString(), "test-token")
        repository = ConversationRepository(haClient)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `action_done round-trip - turn on light`() = runBlocking {
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
                        "conversation_id": "conv-001",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val result = repository.processText("turn on the living room light")

        if (result is ConversationResult.Error) {
            val errMsg = when (val err = result.error) {
                is ConversationError.NetworkError -> "NetworkError: ${err.throwable}"
                is ConversationError.NotConnected -> "NotConnected"
                is ConversationError.NoValidTargets -> "NoValidTargets"
                is ConversationError.NoIntentMatch -> "NoIntentMatch"
                is ConversationError.HaError -> "HaError: ${err.code} - ${err.message}"
            }
            error("Expected Success but got Error: $errMsg")
        }
        val success = result as ConversationResult.Success
        assertEquals("Turned on the living room light", success.speechText)
        assertEquals("action_done", success.response.response.responseType)
        assertFalse(repository.isMultiTurn)

        // Verify the request was correctly formatted
        val request = mockWebServer.takeRequest()
        assertEquals("/api/conversation/process", request.path)
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("turn on the living room light"), "Body should contain text: $body")
    }

    @Test
    fun `error round-trip - no valid targets`() = runBlocking {
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
                                    "speech": "Sorry, I am not able to find the requested device"
                                }
                            },
                            "card": {},
                            "data": {
                                "code": "no_valid_targets",
                                "success": [],
                                "failed": []
                            }
                        },
                        "conversation_id": "conv-002",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val result = repository.processText("turn on the dinosaur")

        assertTrue(result is ConversationResult.Error)
        assertEquals(
            ConversationError.NoValidTargets,
            (result as ConversationResult.Error).error,
        )
    }

    @Test
    fun `error round-trip - no intent match`() = runBlocking {
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
                                    "speech": "Sorry, I don't understand that command"
                                }
                            },
                            "card": {},
                            "data": {
                                "code": "no_intent_match",
                                "success": [],
                                "failed": []
                            }
                        },
                        "conversation_id": "conv-003",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val result = repository.processText("what is the meaning of life")

        assertTrue(result is ConversationResult.Error)
        assertEquals(
            ConversationError.NoIntentMatch,
            (result as ConversationResult.Error).error,
        )
    }

    @Test
    fun `multi-turn round-trip - continue then complete`() = runBlocking {
        // First response: continue conversation
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "response": {
                            "response_type": "query_answer",
                            "speech": {
                                "plain": {
                                    "speech": "Which light would you like me to turn on?"
                                }
                            },
                            "card": {}
                        },
                        "conversation_id": "multi-turn-001",
                        "continue_conversation": true
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val result1 = repository.processText("turn on the light")
        assertTrue(result1 is ConversationResult.Success)
        assertEquals("Which light would you like me to turn on?", (result1 as ConversationResult.Success).speechText)
        assertTrue(repository.isMultiTurn)

        // Second response: conversation complete
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
                                    "speech": "Done, turned on the bedroom light"
                                }
                            },
                            "card": {},
                            "data": {
                                "success": [
                                    {"name": "Bedroom Light", "type": "light", "id": "light.bedroom"}
                                ],
                                "failed": []
                            }
                        },
                        "conversation_id": "multi-turn-001",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val result2 = repository.processText("the bedroom one")
        assertTrue(result2 is ConversationResult.Success)
        assertEquals("Done, turned on the bedroom light", (result2 as ConversationResult.Success).speechText)
        assertFalse(repository.isMultiTurn)

        // Verify conversation_id was passed in second request
        mockWebServer.takeRequest() // first request
        val secondRequest = mockWebServer.takeRequest()
        val body = secondRequest.body.readUtf8()
        assertTrue(body.contains("multi-turn-001"))
    }

    @Test
    fun `agent_id is included in request when configured`() = runBlocking {
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
                        "conversation_id": "conv-agent-001",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        repository.setAgent("conversation.chatgpt")
        repository.processText("turn off all lights")

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("conversation.chatgpt"))
    }

    @Test
    fun `network error produces NetworkError result`() = runBlocking {
        // Shut down the server to simulate network error
        mockWebServer.shutdown()

        val result = repository.processText("turn on the light")

        assertTrue(result is ConversationResult.Error)
        val error = (result as ConversationResult.Error).error
        assertTrue(error is ConversationError.NetworkError)
    }

    @Test
    fun `query_answer round-trip`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "response": {
                            "response_type": "query_answer",
                            "speech": {
                                "plain": {
                                    "speech": "The temperature in the living room is 21.5 degrees"
                                }
                            },
                            "card": {}
                        },
                        "conversation_id": "conv-query-001",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val result = repository.processText("what's the temperature in the living room")

        assertTrue(result is ConversationResult.Success)
        val success = result as ConversationResult.Success
        assertEquals("The temperature in the living room is 21.5 degrees", success.speechText)
        assertEquals("query_answer", success.response.response.responseType)
    }

    @Test
    fun `partial failure with failed targets`() = runBlocking {
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
                                    "speech": "Turned on 2 lights, 1 failed"
                                }
                            },
                            "card": {},
                            "data": {
                                "success": [
                                    {"name": "Living Room", "type": "light", "id": "light.living_room"},
                                    {"name": "Kitchen", "type": "light", "id": "light.kitchen"}
                                ],
                                "failed": [
                                    {"name": "Garage", "type": "light", "id": "light.garage"}
                                ]
                            }
                        },
                        "conversation_id": "conv-partial-001",
                        "continue_conversation": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )

        val result = repository.processText("turn on all lights")

        assertTrue(result is ConversationResult.Success)
        val success = result as ConversationResult.Success
        assertEquals(2, success.response.response.data?.success?.size)
        assertEquals(1, success.response.response.data?.failed?.size)
    }
}
