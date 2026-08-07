package uk.org.retallack.jarvis.voice.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * State of the TTS engine.
 */
enum class TtsState {
    /** Not initialized. */
    UNINITIALIZED,

    /** Ready to synthesize speech. */
    READY,

    /** Currently synthesizing/speaking. */
    SPEAKING,

    /** Error state. */
    ERROR,
}

/**
 * Interface for text-to-speech engines.
 * Implementations include Piper via Sherpa-ONNX and eSpeak-NG fallback.
 */
interface TtsEngine {

    /** Current state of the engine. */
    val state: StateFlow<TtsState>

    /**
     * Initialize the engine with model files.
     * @param modelPath path to the TTS model directory
     * @return true if initialization succeeded
     */
    suspend fun initialize(modelPath: String): Boolean

    /**
     * Speak the given text.
     * @param text text to synthesize and play
     * @param interrupt if true, stops any current speech before starting
     */
    suspend fun speak(text: String, interrupt: Boolean = true)

    /**
     * Stop any ongoing speech playback.
     */
    suspend fun stop()

    /**
     * Release all resources.
     */
    suspend fun release()

    /**
     * Check if TTS model is available.
     */
    fun isModelAvailable(modelPath: String): Boolean
}
