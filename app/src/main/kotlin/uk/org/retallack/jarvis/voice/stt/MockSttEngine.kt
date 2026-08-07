package uk.org.retallack.jarvis.voice.stt

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock STT engine for development and testing.
 * Simulates speech recognition with configurable responses.
 */
@Singleton
class MockSttEngine @Inject constructor() : SttEngine {

    private val _state = MutableStateFlow(SttState.UNINITIALIZED)
    override val state: StateFlow<SttState> = _state

    private val _results = MutableSharedFlow<SttResult>(extraBufferCapacity = 16)
    override val results: Flow<SttResult> = _results

    private var audioSampleCount = 0
    private var isInitialized = false

    /** Configurable response for testing. */
    var mockTranscription: String = "turn on the living room light"

    /** Delay before producing result (simulates processing). */
    var mockProcessingDelayMs: Long = 500

    override suspend fun initialize(modelPath: String): Boolean {
        _state.value = SttState.READY
        isInitialized = true
        return true
    }

    override suspend fun startListening() {
        if (!isInitialized) {
            _state.value = SttState.ERROR
            return
        }
        audioSampleCount = 0
        _state.value = SttState.LISTENING
    }

    override suspend fun processAudio(samples: ShortArray) {
        if (_state.value != SttState.LISTENING) return
        audioSampleCount += samples.size

        // Emit partial results as audio accumulates
        if (audioSampleCount > 16000) { // ~1 second of audio at 16kHz
            val words = mockTranscription.split(" ")
            val partialWordCount = minOf(
                words.size,
                (audioSampleCount / 16000).coerceAtLeast(1),
            )
            val partial = words.take(partialWordCount).joinToString(" ")
            _results.emit(SttResult(text = partial, isFinal = false))
        }

        // Simulate VAD end-of-speech after ~3 seconds
        if (audioSampleCount > 48000) {
            _state.value = SttState.PROCESSING
            delay(mockProcessingDelayMs)
            _results.emit(SttResult(text = mockTranscription, isFinal = true))
            _state.value = SttState.READY
        }
    }

    override suspend fun stopListening() {
        if (_state.value == SttState.LISTENING) {
            _state.value = SttState.PROCESSING
            delay(mockProcessingDelayMs)
            _results.emit(SttResult(text = mockTranscription, isFinal = true))
            _state.value = SttState.READY
        }
    }

    override suspend fun release() {
        _state.value = SttState.UNINITIALIZED
        isInitialized = false
    }

    override fun isModelAvailable(modelPath: String): Boolean = true
}
