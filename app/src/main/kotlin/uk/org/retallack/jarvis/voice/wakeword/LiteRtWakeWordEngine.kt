package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wake word detection engine using LiteRT (TFLite) with OpenWakeWord models.
 * Loads bundled "Hey Jarvis" model from APK assets.
 * NOTE: Full implementation requires TFLite runtime and device testing.
 */
@Singleton
class LiteRtWakeWordEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : WakeWordEngine {

    private val _state = MutableStateFlow(WakeWordState.UNINITIALIZED)
    override val state: StateFlow<WakeWordState> = _state

    private val _detections = MutableSharedFlow<WakeWordDetection>(extraBufferCapacity = 8)
    override val detections: Flow<WakeWordDetection> = _detections

    private var sensitivity: Float = 0.5f
    private var quietHoursConfig = QuietHoursConfig()

    companion object {
        private const val MODEL_FILENAME = "hey_jarvis.tflite"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 1280 // 80ms at 16kHz
    }

    override suspend fun initialize(): Boolean {
        return try {
            // TODO: Load TFLite model from assets
            // val modelBuffer = context.assets.open(MODEL_FILENAME).use { ... }
            // interpreter = Interpreter(modelBuffer)
            _state.value = WakeWordState.ERROR // Not yet implemented
            false
        } catch (e: Exception) {
            _state.value = WakeWordState.ERROR
            false
        }
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
        if (_state.value != WakeWordState.LISTENING) return
        // TODO: Run inference on audio frame
        // - Accumulate samples into FRAME_SIZE chunks
        // - Normalize to float32
        // - Run TFLite model
        // - Check output probability against sensitivity threshold
        // - If detected, emit WakeWordDetection and enter cooldown
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
            now.isAfter(start) && now.isBefore(end)
        } else {
            now.isAfter(start) || now.isBefore(end)
        }
    }

    override suspend fun release() {
        // TODO: Close TFLite interpreter
        _state.value = WakeWordState.UNINITIALIZED
    }
}
