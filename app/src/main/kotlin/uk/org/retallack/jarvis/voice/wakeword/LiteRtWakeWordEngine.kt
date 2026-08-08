package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Wake word detection engine using LiteRT (TFLite) with OpenWakeWord models.
 *
 * Current implementation: PLACEHOLDER using audio energy detection.
 * Detects sustained loud audio (amplitude > threshold for > 500ms) as a stand-in
 * for real wake word detection. This allows testing the full service lifecycle
 * end-to-end.
 *
 * TODO: Implement proper OpenWakeWord TFLite inference pipeline:
 *   1. melspectrogram.tflite: audio → mel features
 *   2. embedding_model.tflite: mel features → embeddings
 *   3. hey_jarvis.tflite: embeddings → detection score
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

    // Placeholder detection state
    private var consecutiveLoudFrames: Int = 0
    private var cooldownUntil: Long = 0L

    companion object {
        private const val TAG = "LiteRtWakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 1280 // 80ms at 16kHz

        // Placeholder thresholds
        // Energy threshold scales with sensitivity (higher sensitivity = lower threshold)
        private const val BASE_ENERGY_THRESHOLD = 3000
        // Number of consecutive loud frames needed (at 80ms per frame, 7 frames ≈ 560ms)
        private const val REQUIRED_LOUD_FRAMES = 7
        // Cooldown period between detections
        private const val COOLDOWN_MS = 4000L
    }

    override suspend fun initialize(): Boolean {
        return try {
            // Verify models exist in assets (for future TFLite implementation)
            val modelsExist = ModelPaths.verifyModelsExist(context)
            if (!modelsExist) {
                Log.w(TAG, "OpenWakeWord models not found in assets, using placeholder detection")
            } else {
                Log.i(TAG, "OpenWakeWord models verified in assets")
            }

            // TODO: Load TFLite models for real inference
            // val melModel = loadModel(ModelPaths.MELSPECTROGRAM_MODEL)
            // val embeddingModel = loadModel(ModelPaths.EMBEDDING_MODEL)
            // val wakeWordModel = loadModel(ModelPaths.WAKE_WORD_MODEL)

            _state.value = WakeWordState.READY
            Log.i(TAG, "Wake word engine initialized (placeholder mode)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake word engine", e)
            _state.value = WakeWordState.ERROR
            false
        }
    }

    override suspend fun startListening() {
        if (_state.value != WakeWordState.READY) {
            Log.w(TAG, "Cannot start listening, state=${_state.value}")
            return
        }
        if (isInQuietHours()) {
            _state.value = WakeWordState.PAUSED
            Log.i(TAG, "In quiet hours, pausing")
            return
        }
        _state.value = WakeWordState.LISTENING
        consecutiveLoudFrames = 0
        Log.i(TAG, "Listening for wake word")
    }

    override suspend fun stopListening() {
        _state.value = WakeWordState.READY
        consecutiveLoudFrames = 0
        Log.i(TAG, "Stopped listening")
    }

    override suspend fun processAudio(samples: ShortArray) {
        if (_state.value != WakeWordState.LISTENING) return

        // Check cooldown
        val now = System.currentTimeMillis()
        if (now < cooldownUntil) return

        // TODO: Replace this placeholder with real TFLite inference pipeline:
        //   1. Convert samples to float, normalize
        //   2. Run through melspectrogram model
        //   3. Run through embedding model
        //   4. Run through hey_jarvis model
        //   5. Check output score against threshold

        // PLACEHOLDER: Detect sustained loud audio as stand-in for wake word
        val energy = calculateRmsEnergy(samples)
        val threshold = getEnergyThreshold()

        if (energy > threshold) {
            consecutiveLoudFrames++
            if (consecutiveLoudFrames >= REQUIRED_LOUD_FRAMES) {
                // Detection!
                onDetection(energy.toFloat() / Short.MAX_VALUE)
            }
        } else {
            // Reset counter if audio drops below threshold
            consecutiveLoudFrames = 0
        }
    }

    private suspend fun onDetection(confidence: Float) {
        Log.i(TAG, "Wake word detected (placeholder)! confidence=$confidence")
        _state.value = WakeWordState.DETECTED
        _detections.emit(WakeWordDetection(confidence = confidence))

        // Enter cooldown
        cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
        consecutiveLoudFrames = 0

        // Brief pause then resume listening
        _state.value = WakeWordState.COOLDOWN
        delay(COOLDOWN_MS)
        if (_state.value == WakeWordState.COOLDOWN) {
            _state.value = WakeWordState.LISTENING
        }
    }

    /**
     * Calculate RMS energy of audio frame.
     */
    private fun calculateRmsEnergy(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            sum += sample.toDouble() * sample.toDouble()
        }
        return Math.sqrt(sum / samples.size)
    }

    /**
     * Get energy threshold adjusted by sensitivity.
     * Higher sensitivity = lower threshold = more sensitive.
     */
    private fun getEnergyThreshold(): Double {
        // Sensitivity 0.0 → threshold = BASE * 2 (less sensitive)
        // Sensitivity 0.5 → threshold = BASE (default)
        // Sensitivity 1.0 → threshold = BASE * 0.5 (more sensitive)
        val factor = 2.0 - (sensitivity * 1.5)
        return BASE_ENERGY_THRESHOLD * factor
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0.0f, 1.0f)
        Log.d(TAG, "Sensitivity set to $sensitivity (threshold=${getEnergyThreshold()})")
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
            // Overnight range (e.g., 22:00 to 07:00)
            now.isAfter(start) || now.isBefore(end)
        }
    }

    override suspend fun release() {
        // TODO: Close TFLite interpreters
        consecutiveLoudFrames = 0
        _state.value = WakeWordState.UNINITIALIZED
        Log.i(TAG, "Wake word engine released")
    }
}
