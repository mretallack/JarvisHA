package uk.org.retallack.jarvis.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.data.db.dao.ConversationMessageDao
import uk.org.retallack.jarvis.data.db.entity.ConversationMessageDb
import uk.org.retallack.jarvis.data.repository.ConversationRepository
import uk.org.retallack.jarvis.data.repository.ConversationResult
import uk.org.retallack.jarvis.voice.stt.SttEngine
import uk.org.retallack.jarvis.voice.stt.SttResult
import uk.org.retallack.jarvis.voice.stt.SttState
import uk.org.retallack.jarvis.voice.tts.TtsEngine
import javax.inject.Inject

enum class VoiceUiMode {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
}

data class ChatMessage(
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val entityIds: List<String> = emptyList(),
    val entityNames: List<String> = emptyList(),
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine,
    private val messageDao: ConversationMessageDao,
    private val connectionRepository: uk.org.retallack.jarvis.data.repository.ConnectionRepository,
    private val haClient: uk.org.retallack.jarvis.data.ha.HaClient,
) : ViewModel() {

    private val _mode = MutableStateFlow(VoiceUiMode.IDLE)
    val mode: StateFlow<VoiceUiMode> = _mode.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    // Track entity info for response messages (msgId -> (entityIds, entityNames))
    private val responseEntityInfo = mutableMapOf<Long, Pair<List<String>, List<String>>>()

    val messages: StateFlow<List<ChatMessage>> = messageDao.getRecentMessages(50)
        .map { messages ->
            messages.map { msg ->
                val entityInfo = responseEntityInfo[msg.id]
                ChatMessage(
                    id = msg.id,
                    text = msg.text,
                    isUser = msg.isUser,
                    timestamp = msg.timestamp,
                    isError = msg.isError,
                    entityIds = entityInfo?.first ?: emptyList(),
                    entityNames = entityInfo?.second ?: emptyList(),
                )
            }.reversed() // Show oldest first
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var wakeWordTriggered = false

    init {
        configureHaClient()
        initializeEngines()
        observeSttResults()
    }

    private fun configureHaClient() {
        viewModelScope.launch {
            val config = connectionRepository.getConnectionConfig()
            if (config != null) {
                haClient.configure(config.url, config.token)
                android.util.Log.d("JarvisVoice", "HaClient configured with ${config.url}")
            } else {
                android.util.Log.w("JarvisVoice", "No HA connection config found")
            }
        }
    }

    private fun initializeEngines() {
        viewModelScope.launch {
            sttEngine.initialize("")
            ttsEngine.initialize("")
        }
    }

    private fun observeSttResults() {
        viewModelScope.launch {
            sttEngine.results.collect { result ->
                handleSttResult(result)
            }
        }
    }

    private suspend fun handleSttResult(result: SttResult) {
        if (result.isFinal) {
            _partialText.value = ""
            // Don't process STT errors as commands
            if (result.text.isNotBlank() && result.confidence > 0f) {
                processCommand(result.text)
            } else if (result.confidence == 0f && result.text.isNotBlank()) {
                // This is an error message from the STT engine, display it
                messageDao.insert(
                    ConversationMessageDb(text = result.text, isUser = false, isError = true),
                )
                _mode.value = VoiceUiMode.ERROR
            } else {
                _mode.value = VoiceUiMode.IDLE
            }
        } else {
            _partialText.value = result.text
        }
    }

    fun onMicTap() {
        wakeWordTriggered = false
        when (_mode.value) {
            VoiceUiMode.IDLE, VoiceUiMode.ERROR -> startListening()
            VoiceUiMode.LISTENING -> stopListening()
            VoiceUiMode.SPEAKING -> {
                viewModelScope.launch { ttsEngine.stop() }
                _mode.value = VoiceUiMode.IDLE
            }
            else -> { /* ignore during processing */ }
        }
    }

    fun onWakeWordDetected() {
        wakeWordTriggered = true
        startListening()
    }

    private fun startListening() {
        viewModelScope.launch {
            // If engine is in error state, re-initialize first
            if (sttEngine.state.value == SttState.ERROR ||
                sttEngine.state.value == SttState.UNINITIALIZED
            ) {
                sttEngine.initialize("")
            }
            _mode.value = VoiceUiMode.LISTENING
            _partialText.value = ""
            sttEngine.startListening()
        }
    }

    private fun stopListening() {
        viewModelScope.launch {
            sttEngine.stopListening()
        }
    }

    private fun processCommand(text: String) {
        viewModelScope.launch {
            // Save user message
            messageDao.insert(ConversationMessageDb(text = text, isUser = true))
            _mode.value = VoiceUiMode.PROCESSING

            val result = conversationRepository.processText(text)

            when (result) {
                is ConversationResult.Success -> {
                    val responseText = result.speechText ?: "Done"

                    // Extract entity info from response data
                    val successTargets = result.response.response.data?.success ?: emptyList()
                    val entityIds = successTargets.mapNotNull { it.id }
                    val entityNames = successTargets.mapNotNull { it.name }

                    messageDao.insert(
                        ConversationMessageDb(text = responseText, isUser = false),
                    )

                    // Store entity info for the most recent message mapping
                    val msgId = messageDao.getCount().toLong()
                    responseEntityInfo[msgId] = entityIds to entityNames

                    // Only speak if triggered by wake word
                    if (wakeWordTriggered && responseText.isNotBlank()) {
                        _mode.value = VoiceUiMode.SPEAKING
                        ttsEngine.speak(responseText)
                    }

                    // Handle multi-turn
                    if (result.response.continueConversation) {
                        _mode.value = VoiceUiMode.LISTENING
                        sttEngine.startListening()
                    } else {
                        _mode.value = VoiceUiMode.IDLE
                    }
                }

                is ConversationResult.Error -> {
                    val errorText = when (result.error) {
                        is uk.org.retallack.jarvis.data.repository.ConversationError.NotConnected ->
                            "Not connected to Home Assistant"
                        is uk.org.retallack.jarvis.data.repository.ConversationError.NoValidTargets ->
                            "No matching devices found"
                        is uk.org.retallack.jarvis.data.repository.ConversationError.NoIntentMatch ->
                            "Command not understood"
                        is uk.org.retallack.jarvis.data.repository.ConversationError.HaError ->
                            "Error: ${(result.error as uk.org.retallack.jarvis.data.repository.ConversationError.HaError).message ?: "Unknown"}"
                        is uk.org.retallack.jarvis.data.repository.ConversationError.NetworkError ->
                            "Connection error"
                    }
                    messageDao.insert(
                        ConversationMessageDb(text = errorText, isUser = false, isError = true),
                    )
                    _mode.value = VoiceUiMode.ERROR
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            messageDao.deleteAll()
        }
    }
}
