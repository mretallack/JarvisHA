package uk.org.retallack.jarvis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.data.ha.HaClient
import uk.org.retallack.jarvis.data.repository.ConnectionRepository
import uk.org.retallack.jarvis.data.repository.ConversationRepository
import uk.org.retallack.jarvis.ui.theme.ThemeMode
import uk.org.retallack.jarvis.ui.theme.ThemeRepository
import uk.org.retallack.jarvis.voice.stt.SttEngine
import uk.org.retallack.jarvis.voice.tts.TtsEngine
import javax.inject.Inject

data class ConversationAgent(
    val id: String,
    val name: String,
)

data class SettingsUiState(
    val isConnected: Boolean = false,
    val haVersion: String? = null,
    val haUrl: String = "",
    val sttModelAvailable: Boolean = false,
    val ttsModelAvailable: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val biometricEnabled: Boolean = false,
    val appVersion: String = "1.0.0",
    val availableAgents: List<ConversationAgent> = emptyList(),
    val selectedAgentId: String? = null,
    val isLoadingAgents: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val haClient: HaClient,
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine,
    private val conversationRepository: ConversationRepository,
    private val themeRepository: ThemeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themeRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    init {
        loadSettings()
        loadAgents()
        observeTheme()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val config = connectionRepository.getConnectionConfig()
            _uiState.value = _uiState.value.copy(
                isConnected = haClient.isConfigured,
                haUrl = config?.url ?: "",
                sttModelAvailable = sttEngine.isModelAvailable(""),
                ttsModelAvailable = ttsEngine.isModelAvailable(""),
                selectedAgentId = conversationRepository.getAgent(),
            )
        }
    }

    private fun observeTheme() {
        viewModelScope.launch {
            themeRepository.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
    }

    fun loadAgents() {
        if (!haClient.isConfigured) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAgents = true)
            try {
                // Fetch conversation agents from HA config
                val config = haClient.getConfig()
                // HA doesn't have a dedicated agents endpoint in the REST API.
                // Agents are entities with domain "conversation".
                // Fetch states and filter for conversation domain.
                val states = haClient.getAllStates()
                val agents = states
                    .filter { it.entityId.startsWith("conversation.") }
                    .map { state ->
                        val friendlyName = state.attributes["friendly_name"]
                            ?.let { element ->
                                element.toString().trim('"')
                            } ?: state.entityId

                        ConversationAgent(
                            id = state.entityId,
                            name = friendlyName,
                        )
                    }

                _uiState.value = _uiState.value.copy(
                    availableAgents = agents,
                    isLoadingAgents = false,
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingAgents = false)
            }
        }
    }

    fun selectAgent(agentId: String?) {
        conversationRepository.setAgent(agentId)
        _uiState.value = _uiState.value.copy(selectedAgentId = agentId)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
        }
    }
}
