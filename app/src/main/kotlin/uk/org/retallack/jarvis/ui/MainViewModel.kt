package uk.org.retallack.jarvis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.data.repository.ConnectionRepository
import uk.org.retallack.jarvis.ui.theme.ThemeMode
import uk.org.retallack.jarvis.ui.theme.ThemeRepository
import uk.org.retallack.jarvis.voice.stt.SherpaOnnxSttEngine
import uk.org.retallack.jarvis.voice.stt.SttEngine
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val themeRepository: ThemeRepository,
    private val sttEngine: SttEngine,
) : ViewModel() {

    private val _isSetupComplete = MutableStateFlow(false)
    val isSetupComplete: StateFlow<Boolean> = _isSetupComplete.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themeRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    init {
        checkSetupStatus()
    }

    /**
     * Called from the activity lifecycle to inform the STT engine whether the app is
     * visible. The Whisper model is released when the app goes to the background so the
     * process stays lightweight and isn't killed by the OS low-memory killer.
     */
    fun onAppForegroundStateChanged(isForeground: Boolean) {
        if (sttEngine is SherpaOnnxSttEngine) {
            sttEngine.onAppForegroundStateChanged(isForeground)
        }
    }

    private fun checkSetupStatus() {
        viewModelScope.launch {
            _isSetupComplete.value = connectionRepository.isConfigured()
        }
    }

    fun markSetupComplete() {
        _isSetupComplete.value = true
    }
}
