package uk.org.retallack.jarvis.voice.wakeword

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

/**
 * State of the wake word engine.
 */
enum class WakeWordState {
    /** Not initialized. */
    UNINITIALIZED,

    /** Ready but not listening. */
    READY,

    /** Actively listening for wake word. */
    LISTENING,

    /** Wake word detected - handing off to STT. */
    DETECTED,

    /** In cooldown period after detection. */
    COOLDOWN,

    /** Paused (quiet hours active). */
    PAUSED,

    /** Error state. */
    ERROR,
}

/**
 * Configuration for quiet hours.
 */
data class QuietHoursConfig(
    val enabled: Boolean = false,
    val startTime: LocalTime = LocalTime.of(22, 0),
    val endTime: LocalTime = LocalTime.of(7, 0),
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=Monday, 7=Sunday
)

/**
 * Interface for wake word detection engines.
 * The primary implementation uses LiteRT (TFLite) with OpenWakeWord models.
 */
interface WakeWordEngine {

    /** Current state of the engine. */
    val state: StateFlow<WakeWordState>

    /** Emits events when wake word is detected. */
    val detections: Flow<WakeWordDetection>

    /**
     * Initialize the engine with model.
     * @return true if initialization succeeded
     */
    suspend fun initialize(): Boolean

    /**
     * Start listening for wake word.
     */
    suspend fun startListening()

    /**
     * Stop listening.
     */
    suspend fun stopListening()

    /**
     * Process audio samples for wake word detection.
     * @param samples PCM 16-bit audio (mono, 16kHz)
     */
    suspend fun processAudio(samples: ShortArray)

    /**
     * Set detection sensitivity.
     * @param sensitivity value 0.0 to 1.0 (higher = more sensitive, more false positives)
     */
    fun setSensitivity(sensitivity: Float)

    /**
     * Configure quiet hours.
     */
    fun setQuietHours(config: QuietHoursConfig)

    /**
     * Check if currently in quiet hours.
     */
    fun isInQuietHours(): Boolean

    /**
     * Release all resources.
     */
    suspend fun release()
}

/**
 * Wake word detection event.
 */
data class WakeWordDetection(
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
)
