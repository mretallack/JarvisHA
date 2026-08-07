package uk.org.retallack.jarvis.voice.wakeword

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock wake word engine for development and testing.
 */
@Singleton
class MockWakeWordEngine @Inject constructor() : WakeWordEngine {

    private val _state = MutableStateFlow(WakeWordState.UNINITIALIZED)
    override val state: StateFlow<WakeWordState> = _state

    private val _detections = MutableSharedFlow<WakeWordDetection>(extraBufferCapacity = 8)
    override val detections: Flow<WakeWordDetection> = _detections

    private var sensitivity: Float = 0.5f
    private var quietHoursConfig = QuietHoursConfig()
    private var cooldownMs: Long = 2000L
    private var lastDetectionTime: Long = 0L

    override suspend fun initialize(): Boolean {
        _state.value = WakeWordState.READY
        return true
    }

    override suspend fun startListening() {
        if (_state.value != WakeWordState.READY) return
        if (isInQuietHours()) {
            _state.value = WakeWordState.PAUSED
            return
        }
        _state.value = WakeWordState.LISTENING
    }

    override suspend fun stopListening() {
        _state.value = WakeWordState.READY
    }

    override suspend fun processAudio(samples: ShortArray) {
        // Mock: no actual wake word detection
    }

    /**
     * Simulate a detection (for testing).
     */
    suspend fun simulateDetection(confidence: Float = 0.95f) {
        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < cooldownMs) return
        if (_state.value != WakeWordState.LISTENING) return

        lastDetectionTime = now
        _state.value = WakeWordState.DETECTED
        _detections.emit(WakeWordDetection(confidence = confidence))

        // Enter cooldown
        _state.value = WakeWordState.COOLDOWN
        delay(cooldownMs)
        _state.value = WakeWordState.LISTENING
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0.0f, 1.0f)
    }

    override fun setQuietHours(config: QuietHoursConfig) {
        this.quietHoursConfig = config
    }

    override fun isInQuietHours(): Boolean {
        if (!quietHoursConfig.enabled) return false

        val now = LocalTime.now()
        val today = java.time.LocalDate.now().dayOfWeek.value
        if (today !in quietHoursConfig.daysOfWeek) return false

        val start = quietHoursConfig.startTime
        val end = quietHoursConfig.endTime

        return if (start.isBefore(end)) {
            // Same day: e.g., 22:00 to 23:00
            now.isAfter(start) && now.isBefore(end)
        } else {
            // Overnight: e.g., 22:00 to 07:00
            now.isAfter(start) || now.isBefore(end)
        }
    }

    override suspend fun release() {
        _state.value = WakeWordState.UNINITIALIZED
    }
}
