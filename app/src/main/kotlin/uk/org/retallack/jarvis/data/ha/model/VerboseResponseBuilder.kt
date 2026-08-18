package uk.org.retallack.jarvis.data.ha.model

/**
 * Builds verbose, natural-sounding responses from HA Conversation API results.
 * Instead of "No, off" says "The back door is off".
 * Instead of "Turned on the switch" says "Turned on the living room".
 */
object VerboseResponseBuilder {

    fun build(response: ConversationResponse): String {
        val speech = response.response.speech?.plain?.speech ?: "Done"
        val responseType = response.response.responseType
        val targets = response.response.data?.success ?: emptyList()
        val entityName = targets.firstOrNull()?.name

        return when {
            // Query answers with entity context
            responseType == "query_answer" && entityName != null -> {
                buildQueryResponse(speech, entityName)
            }
            // Action done with entity context
            responseType == "action_done" && entityName != null && targets.size == 1 -> {
                buildActionResponse(speech, entityName)
            }
            // Multiple targets or no improvement possible
            else -> speech
        }
    }

    private fun buildQueryResponse(speech: String, entityName: String): String {
        val lowerSpeech = speech.lowercase()

        // Binary state responses: "No, off" / "Yes, on" → "The X is off/on"
        return when {
            lowerSpeech == "no, off" || lowerSpeech == "off" ->
                "The $entityName is off"
            lowerSpeech == "yes, on" || lowerSpeech == "on" ->
                "The $entityName is on"
            lowerSpeech == "no" ->
                "No, the $entityName is closed"
            lowerSpeech == "yes" ->
                "Yes, the $entityName is open"
            // Temperature/numeric: "21.5" → "The X is 21.5"
            lowerSpeech.toFloatOrNull() != null ->
                "The $entityName is $speech"
            // Already a full sentence, keep it
            else -> speech
        }
    }

    private fun buildActionResponse(speech: String, entityName: String): String {
        val lowerSpeech = speech.lowercase()

        return when {
            // "Turned on the switch" / "Turned on the light" → "Turned on the living room"
            lowerSpeech.startsWith("turned on") ->
                "Turned on the $entityName"
            lowerSpeech.startsWith("turned off") ->
                "Turned off the $entityName"
            // "Toggled the switch" → "Toggled the living room"
            lowerSpeech.startsWith("toggled") ->
                "Toggled the $entityName"
            // "Locked the lock" → "Locked the front door"
            lowerSpeech.startsWith("locked") ->
                "Locked the $entityName"
            lowerSpeech.startsWith("unlocked") ->
                "Unlocked the $entityName"
            lowerSpeech.startsWith("opened") ->
                "Opened the $entityName"
            lowerSpeech.startsWith("closed") ->
                "Closed the $entityName"
            // Already descriptive, keep it
            else -> speech
        }
    }
}
