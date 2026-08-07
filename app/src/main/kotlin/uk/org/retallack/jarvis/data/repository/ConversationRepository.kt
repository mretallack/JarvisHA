package uk.org.retallack.jarvis.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.org.retallack.jarvis.data.ha.HaClient
import uk.org.retallack.jarvis.data.ha.model.ConversationRequest
import uk.org.retallack.jarvis.data.ha.model.ConversationResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Error types for conversation processing.
 */
sealed class ConversationError {
    data object NotConnected : ConversationError()
    data object NoValidTargets : ConversationError()
    data object NoIntentMatch : ConversationError()
    data class HaError(val code: String, val message: String? = null) : ConversationError()
    data class NetworkError(val throwable: Throwable) : ConversationError()
}

/**
 * Result of a conversation processing operation.
 */
sealed class ConversationResult {
    data class Success(
        val response: ConversationResponse,
        val speechText: String?,
    ) : ConversationResult()

    data class Error(val error: ConversationError) : ConversationResult()
}

/**
 * Repository for managing conversation interactions with Home Assistant.
 * Handles multi-turn conversations and error mapping.
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val haClient: HaClient,
) {
    private var currentConversationId: String? = null
    private var configuredAgentId: String? = null

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    /**
     * Process a user's text command via the HA Conversation API.
     * Supports multi-turn by tracking conversation_id.
     */
    suspend fun processText(
        text: String,
        language: String = "en",
    ): ConversationResult {
        if (!haClient.isConfigured) {
            return ConversationResult.Error(ConversationError.NotConnected)
        }

        _isProcessing.value = true
        return try {
            val request = ConversationRequest(
                text = text,
                language = language,
                conversationId = currentConversationId,
                agentId = configuredAgentId,
            )

            val response = haClient.processConversation(request)

            // Update conversation tracking
            currentConversationId = if (response.continueConversation) {
                response.conversationId
            } else {
                null
            }

            // Map response type to result
            mapResponse(response)
        } catch (e: Exception) {
            ConversationResult.Error(ConversationError.NetworkError(e))
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Map an HA response to our result type.
     */
    private fun mapResponse(response: ConversationResponse): ConversationResult {
        val responseType = response.response.responseType
        val speechText = response.response.speech?.plain?.speech

        return when (responseType) {
            "action_done", "query_answer" -> {
                ConversationResult.Success(
                    response = response,
                    speechText = speechText,
                )
            }

            "error" -> {
                val errorCode = response.response.data?.code
                val error = mapErrorCode(errorCode, speechText)
                ConversationResult.Error(error)
            }

            else -> {
                ConversationResult.Success(
                    response = response,
                    speechText = speechText,
                )
            }
        }
    }

    /**
     * Map HA error codes to our error types.
     */
    fun mapErrorCode(code: String?, message: String?): ConversationError {
        return when (code) {
            "no_valid_targets" -> ConversationError.NoValidTargets
            "no_intent_match" -> ConversationError.NoIntentMatch
            else -> ConversationError.HaError(code ?: "unknown", message)
        }
    }

    /**
     * Set the conversation agent to use.
     * @param agentId agent ID (e.g., "conversation.home_assistant" or an LLM agent ID)
     */
    fun setAgent(agentId: String?) {
        configuredAgentId = agentId
    }

    /**
     * Get the currently configured agent ID.
     */
    fun getAgent(): String? = configuredAgentId

    /**
     * Reset conversation state (clear multi-turn tracking).
     */
    fun resetConversation() {
        currentConversationId = null
    }

    /**
     * Whether a multi-turn conversation is active.
     */
    val isMultiTurn: Boolean get() = currentConversationId != null
}
