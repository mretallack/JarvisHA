package uk.org.retallack.jarvis.data.ha.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HaApiStatus(
    val message: String,
)

@Serializable
data class HaConfig(
    val version: String,
    val location_name: String? = null,
    val currency: String? = null,
    @SerialName("unit_system") val unitSystem: HaUnitSystem? = null,
)

@Serializable
data class HaUnitSystem(
    val temperature: String? = null,
    val length: String? = null,
    val mass: String? = null,
    val volume: String? = null,
)

@Serializable
data class HaEntityState(
    @SerialName("entity_id") val entityId: String,
    val state: String,
    val attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    @SerialName("last_changed") val lastChanged: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
)

@Serializable
data class ConversationRequest(
    val text: String,
    val language: String = "en",
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
)

@Serializable
data class ConversationResponse(
    val response: ResponseData,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("continue_conversation") val continueConversation: Boolean = false,
)

@Serializable
data class ResponseData(
    val speech: SpeechData? = null,
    val card: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val language: String? = null,
    @SerialName("response_type") val responseType: String, // action_done, query_answer, error
    val data: ResponsePayload? = null,
)

@Serializable
data class SpeechData(
    val plain: SpeechPlain? = null,
)

@Serializable
data class SpeechPlain(
    val speech: String,
    @SerialName("extra_data") val extraData: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class ResponsePayload(
    val success: List<TargetInfo> = emptyList(),
    val failed: List<TargetInfo> = emptyList(),
    val code: String? = null, // error code like "no_valid_targets", "no_intent_match"
)

@Serializable
data class TargetInfo(
    val name: String? = null,
    val type: String? = null,
    val id: String? = null,
)
