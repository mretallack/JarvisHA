package uk.org.retallack.jarvis.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.org.retallack.jarvis.data.ha.HaClient
import uk.org.retallack.jarvis.data.ha.model.ConversationRequest
import uk.org.retallack.jarvis.data.ha.model.ConversationResponse
import uk.org.retallack.jarvis.data.ha.model.ResponseData
import uk.org.retallack.jarvis.data.ha.model.ResponsePayload
import uk.org.retallack.jarvis.data.ha.model.SpeechData
import uk.org.retallack.jarvis.data.ha.model.SpeechPlain
import uk.org.retallack.jarvis.data.ha.model.TargetInfo
import java.io.IOException

class ConversationRepositoryTest {

    private lateinit var haClient: HaClient
    private lateinit var repository: ConversationRepository

    @BeforeEach
    fun setup() {
        haClient = mockk(relaxed = true)
        repository = ConversationRepository(haClient)
    }

    @Test
    fun `processText returns error when not configured`() = runTest {
        every { haClient.isConfigured } returns false

        val result = repository.processText("turn on the light")

        assertTrue(result is ConversationResult.Error)
        assertEquals(
            ConversationError.NotConnected,
            (result as ConversationResult.Error).error,
        )
    }

    @Test
    fun `processText returns success for action_done`() = runTest {
        every { haClient.isConfigured } returns true
        val response = ConversationResponse(
            response = ResponseData(
                speech = SpeechData(plain = SpeechPlain(speech = "Turned on the light")),
                responseType = "action_done",
                data = ResponsePayload(
                    success = listOf(TargetInfo(name = "Living Room", type = "light", id = "light.living_room")),
                ),
            ),
            conversationId = "test-conv-123",
            continueConversation = false,
        )
        coEvery { haClient.processConversation(any()) } returns response

        val result = repository.processText("turn on the light")

        assertTrue(result is ConversationResult.Success)
        val success = result as ConversationResult.Success
        assertEquals("Turned on the light", success.speechText)
        assertEquals("action_done", success.response.response.responseType)
    }

    @Test
    fun `processText returns success for query_answer`() = runTest {
        every { haClient.isConfigured } returns true
        val response = ConversationResponse(
            response = ResponseData(
                speech = SpeechData(plain = SpeechPlain(speech = "The temperature is 22 degrees")),
                responseType = "query_answer",
            ),
            conversationId = "test-conv-456",
        )
        coEvery { haClient.processConversation(any()) } returns response

        val result = repository.processText("what's the temperature")

        assertTrue(result is ConversationResult.Success)
        val success = result as ConversationResult.Success
        assertEquals("The temperature is 22 degrees", success.speechText)
    }

    @Test
    fun `processText returns error for no_valid_targets`() = runTest {
        every { haClient.isConfigured } returns true
        val response = ConversationResponse(
            response = ResponseData(
                speech = SpeechData(plain = SpeechPlain(speech = "Unable to find entity")),
                responseType = "error",
                data = ResponsePayload(code = "no_valid_targets"),
            ),
            conversationId = "test-conv-789",
        )
        coEvery { haClient.processConversation(any()) } returns response

        val result = repository.processText("turn on the fridge")

        assertTrue(result is ConversationResult.Error)
        assertEquals(
            ConversationError.NoValidTargets,
            (result as ConversationResult.Error).error,
        )
    }

    @Test
    fun `processText returns error for no_intent_match`() = runTest {
        every { haClient.isConfigured } returns true
        val response = ConversationResponse(
            response = ResponseData(
                speech = SpeechData(plain = SpeechPlain(speech = "I don't understand")),
                responseType = "error",
                data = ResponsePayload(code = "no_intent_match"),
            ),
            conversationId = "test-conv-000",
        )
        coEvery { haClient.processConversation(any()) } returns response

        val result = repository.processText("blah blah blah")

        assertTrue(result is ConversationResult.Error)
        assertEquals(
            ConversationError.NoIntentMatch,
            (result as ConversationResult.Error).error,
        )
    }

    @Test
    fun `processText returns NetworkError on exception`() = runTest {
        every { haClient.isConfigured } returns true
        coEvery { haClient.processConversation(any()) } throws IOException("Connection lost")

        val result = repository.processText("turn on the light")

        assertTrue(result is ConversationResult.Error)
        val error = (result as ConversationResult.Error).error
        assertTrue(error is ConversationError.NetworkError)
        assertEquals("Connection lost", (error as ConversationError.NetworkError).throwable.message)
    }

    @Test
    fun `multi-turn conversation tracks conversation_id`() = runTest {
        every { haClient.isConfigured } returns true

        // First response indicates continue
        val response1 = ConversationResponse(
            response = ResponseData(
                speech = SpeechData(plain = SpeechPlain(speech = "Which light?")),
                responseType = "query_answer",
            ),
            conversationId = "multi-turn-123",
            continueConversation = true,
        )
        coEvery { haClient.processConversation(match { it.conversationId == null }) } returns response1

        val result1 = repository.processText("turn on the light")
        assertTrue(result1 is ConversationResult.Success)
        assertTrue(repository.isMultiTurn)

        // Second response with conversation_id passed
        val response2 = ConversationResponse(
            response = ResponseData(
                speech = SpeechData(plain = SpeechPlain(speech = "Done")),
                responseType = "action_done",
            ),
            conversationId = "multi-turn-123",
            continueConversation = false,
        )
        coEvery { haClient.processConversation(match { it.conversationId == "multi-turn-123" }) } returns response2

        val result2 = repository.processText("the living room one")
        assertTrue(result2 is ConversationResult.Success)
        assertFalse(repository.isMultiTurn)
    }

    @Test
    fun `resetConversation clears multi-turn state`() = runTest {
        every { haClient.isConfigured } returns true
        val response = ConversationResponse(
            response = ResponseData(
                speech = SpeechData(plain = SpeechPlain(speech = "Which light?")),
                responseType = "query_answer",
            ),
            conversationId = "multi-turn-999",
            continueConversation = true,
        )
        coEvery { haClient.processConversation(any()) } returns response

        repository.processText("turn on the light")
        assertTrue(repository.isMultiTurn)

        repository.resetConversation()
        assertFalse(repository.isMultiTurn)
    }

    @Test
    fun `setAgent configures agent_id`() {
        repository.setAgent("conversation.chatgpt")
        assertEquals("conversation.chatgpt", repository.getAgent())
    }

    @Test
    fun `setAgent with null clears agent`() {
        repository.setAgent("conversation.chatgpt")
        repository.setAgent(null)
        assertNull(repository.getAgent())
    }

    @Test
    fun `mapErrorCode maps known error codes`() {
        assertEquals(ConversationError.NoValidTargets, repository.mapErrorCode("no_valid_targets", null))
        assertEquals(ConversationError.NoIntentMatch, repository.mapErrorCode("no_intent_match", null))
    }

    @Test
    fun `mapErrorCode returns HaError for unknown codes`() {
        val error = repository.mapErrorCode("some_other_error", "Something went wrong")
        assertTrue(error is ConversationError.HaError)
        assertEquals("some_other_error", (error as ConversationError.HaError).code)
        assertEquals("Something went wrong", error.message)
    }
}
