package uk.org.retallack.jarvis.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.data.ha.HaClient
import uk.org.retallack.jarvis.data.repository.ConnectionRepository
import uk.org.retallack.jarvis.voice.ModelManager
import javax.inject.Inject

data class SetupUiState(
    val haUrl: String = "",
    val haToken: String = "",
    val isTestingConnection: Boolean = false,
    val connectionSuccess: Boolean = false,
    val connectionError: String? = null,
    val haVersion: String? = null,
    val wakeWordEnabled: Boolean = true,
    val sensitivity: Float = 0.5f,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val setupComplete: Boolean = false,
    val isDownloadingSttModel: Boolean = false,
    val sttModelDownloaded: Boolean = false,
    val sttDownloadProgress: Float = 0f,
    val sttDownloadError: String? = null,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val haClient: HaClient,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = connectionRepository.getConnectionConfig()
            if (config != null) {
                _uiState.value = _uiState.value.copy(
                    haUrl = config.url,
                    haToken = config.token,
                )
            }
            // Check if models are already downloaded
            _uiState.value = _uiState.value.copy(
                sttModelDownloaded = modelManager.isSttModelAvailable(),
            )
        }
        // Observe real-time download progress from ModelManager's StateFlow
        viewModelScope.launch {
            modelManager.sttDownloadState.collect { progress ->
                if (_uiState.value.isDownloadingSttModel) {
                    _uiState.value = _uiState.value.copy(
                        sttDownloadProgress = if (progress.totalBytes > 0) {
                            progress.bytesDownloaded.toFloat() / progress.totalBytes
                        } else {
                            // Fall back to file-level progress
                            if (progress.totalFiles > 0) {
                                progress.filesCompleted.toFloat() / progress.totalFiles
                            } else {
                                0f
                            }
                        },
                    )
                }
            }
        }
    }

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            haUrl = url,
            connectionSuccess = false,
            connectionError = null,
        )
    }

    fun updateToken(token: String) {
        _uiState.value = _uiState.value.copy(
            haToken = token,
            connectionSuccess = false,
            connectionError = null,
        )
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.haUrl.isBlank() || state.haToken.isBlank()) {
            _uiState.value = state.copy(connectionError = "URL and token are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingConnection = true,
                connectionError = null,
            )
            try {
                haClient.configure(state.haUrl, state.haToken)
                val config = haClient.getConfig()
                connectionRepository.saveConnectionConfig(state.haUrl, state.haToken)
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionSuccess = true,
                    haVersion = config.version,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionSuccess = false,
                    connectionError = e.message ?: "Connection failed",
                )
            }
        }
    }

    fun downloadSttModel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingSttModel = true,
                sttDownloadError = null,
                sttDownloadProgress = 0f,
            )
            try {
                modelManager.downloadSttModel().collect { progress ->
                    _uiState.value = _uiState.value.copy(
                        sttDownloadProgress = progress.progressFraction,
                    )
                    if (progress.isComplete) {
                        _uiState.value = _uiState.value.copy(
                            isDownloadingSttModel = false,
                            sttModelDownloaded = true,
                        )
                    }
                    if (progress.error != null) {
                        _uiState.value = _uiState.value.copy(
                            isDownloadingSttModel = false,
                            sttDownloadError = progress.error,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloadingSttModel = false,
                    sttDownloadError = e.message ?: "Download failed",
                )
            }
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(wakeWordEnabled = enabled)
    }

    fun setSensitivity(sensitivity: Float) {
        _uiState.value = _uiState.value.copy(sensitivity = sensitivity)
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(quietHoursEnabled = enabled)
    }

    fun setQuietHoursStart(time: String) {
        _uiState.value = _uiState.value.copy(quietHoursStart = time)
    }

    fun setQuietHoursEnd(time: String) {
        _uiState.value = _uiState.value.copy(quietHoursEnd = time)
    }

    fun completeSetup() {
        viewModelScope.launch {
            connectionRepository.saveConnectionConfig(
                _uiState.value.haUrl,
                _uiState.value.haToken,
            )
            _uiState.value = _uiState.value.copy(setupComplete = true)
        }
    }
}
