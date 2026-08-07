package uk.org.retallack.jarvis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.data.ha.HaClient
import uk.org.retallack.jarvis.data.repository.ConnectionRepository
import uk.org.retallack.jarvis.voice.stt.SttEngine
import uk.org.retallack.jarvis.voice.tts.TtsEngine
import javax.inject.Inject

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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val haClient: HaClient,
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val config = connectionRepository.getConnectionConfig()
            _uiState.value = _uiState.value.copy(
                isConnected = haClient.isConfigured,
                haUrl = config?.url ?: "",
                sttModelAvailable = sttEngine.isModelAvailable(""),
                ttsModelAvailable = ttsEngine.isModelAvailable(""),
            )
        }
    }
}
