package uk.org.retallack.jarvis.voice.tts

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock TTS engine for development and testing.
 * Simulates speech synthesis with configurable delays.
 */
@Singleton
class MockTtsEngine @Inject constructor() : TtsEngine {

    private val _state = MutableStateFlow(TtsState.UNINITIALIZED)
    override val state: StateFlow<TtsState> = _state

    /** Track last spoken text for testing. */
    var lastSpokenText: String? = null
        private set

    /** Simulated speaking duration per character in ms. */
    var msPerCharacter: Long = 50

    override suspend fun initialize(modelPath: String): Boolean {
        _state.value = TtsState.READY
        return true
    }

    override suspend fun speak(text: String, interrupt: Boolean) {
        if (_state.value == TtsState.UNINITIALIZED) return
        if (interrupt && _state.value == TtsState.SPEAKING) {
            _state.value = TtsState.READY
        }

        _state.value = TtsState.SPEAKING
        lastSpokenText = text

        // Simulate speaking duration based on text length
        val duration = text.length * msPerCharacter
        delay(duration.coerceAtMost(3000)) // Cap at 3 seconds

        _state.value = TtsState.READY
    }

    override suspend fun stop() {
        if (_state.value == TtsState.SPEAKING) {
            _state.value = TtsState.READY
        }
    }

    override suspend fun release() {
        _state.value = TtsState.UNINITIALIZED
    }

    override fun isModelAvailable(modelPath: String): Boolean = true
}
