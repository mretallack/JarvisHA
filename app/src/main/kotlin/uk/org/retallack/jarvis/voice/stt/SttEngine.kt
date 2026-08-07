package uk.org.retallack.jarvis.voice.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * State of the STT engine.
 */
enum class SttState {
    /** Not initialized or released. */
    UNINITIALIZED,

    /** Initialized and ready to accept audio. */
    READY,

    /** Actively listening and processing audio. */
    LISTENING,

    /** Processing final result (VAD detected end of speech). */
    PROCESSING,

    /** Error state - must be re-initialized. */
    ERROR,
}

/**
 * Result from STT recognition.
 */
data class SttResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
)

/**
 * Interface for speech-to-text engines.
 * Implementations include Sherpa-ONNX (offline) and potentially others.
 */
interface SttEngine {

    /** Current state of the engine. */
    val state: StateFlow<SttState>

    /** Flow of recognition results (partial and final). */
    val results: Flow<SttResult>

    /**
     * Initialize the engine with model files.
     * @param modelPath path to the model directory
     * @return true if initialization succeeded
     */
    suspend fun initialize(modelPath: String): Boolean

    /**
     * Start listening for speech.
     * Audio should be fed via [processAudio].
     */
    suspend fun startListening()

    /**
     * Feed audio data to the recognizer.
     * @param samples PCM 16-bit audio samples (mono, 16kHz)
     */
    suspend fun processAudio(samples: ShortArray)

    /**
     * Stop listening and finalize recognition.
     */
    suspend fun stopListening()

    /**
     * Release all resources held by the engine.
     */
    suspend fun release()

    /**
     * Check if the required model is available.
     */
    fun isModelAvailable(modelPath: String): Boolean
}
