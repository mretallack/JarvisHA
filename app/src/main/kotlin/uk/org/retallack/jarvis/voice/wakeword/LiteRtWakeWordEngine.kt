package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wake word detection engine using TFLite with OpenWakeWord models.
 *
 * Implements the full OpenWakeWord inference pipeline:
 *   1. melspectrogram.tflite: raw audio (float32) → mel spectrogram features
 *   2. embedding_model.tflite: 76 mel frames → 96-dim embedding vector
 *   3. hey_jarvis.tflite: 16 embeddings → detection score [0..1]
 *
 * Based on Dicio's proven Android implementation of the same pipeline.
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

    // TFLite interpreters
    private var melInterpreter: Interpreter? = null
    private var embInterpreter: Interpreter? = null
    private var wakeInterpreter: Interpreter? = null

    // Pipeline buffers
    // Accumulated mel spectrogram frames: 76 frames × 32 bins × 1 channel
    // Stored as [76][32][1] to match embedding model input [1, 76, 32, 1]
    private var accumulatedMelFrames: Array<Array<FloatArray>>? = null

    // Accumulated embedding vectors: 16 frames × 96 dims
    // Stored as [16][96] to match wake model input [1, 16, 96]
    private var accumulatedEmbeddings: Array<FloatArray>? = null

    // Counter for how many mel frames we've accumulated (until buffer is full)
    private var melFramesFilled: Int = 0

    // Counter for how many embeddings we've accumulated (until buffer is full)
    private var embeddingsFilled: Int = 0

    // Cooldown state
    private var cooldownUntil: Long = 0L

    companion object {
        private const val TAG = "LiteRtWakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 1280 // 80ms at 16kHz

        // Mel spectrogram model parameters
        // Using 1280 samples produces floor((1280-512)/160)+1 = 5 mel frames
        private const val MEL_INPUT_COUNT = 1280
        private const val MEL_OUTPUT_COUNT = 5 // mel frames per audio frame
        private const val MEL_BINS = 32 // mel frequency bins

        // Embedding model parameters
        private const val EMB_INPUT_FRAMES = 76 // mel frames needed for one embedding
        private const val EMB_OUTPUT_SIZE = 96 // embedding dimension

        // Wake word model parameters
        private const val WAKE_INPUT_FRAMES = 16 // embeddings needed for one prediction
        private const val EMB_OUTPUT_COUNT = 1 // embeddings produced per frame

        // Detection parameters
        private const val COOLDOWN_MS = 4000L
        private const val BASE_THRESHOLD = 0.5f
    }

    override suspend fun initialize(): Boolean {
        return try {
            val modelsExist = ModelPaths.verifyModelsExist(context)
            if (!modelsExist) {
                Log.e(TAG, "OpenWakeWord models not found in assets")
                _state.value = WakeWordState.ERROR
                return false
            }

            // Load TFLite interpreters
            melInterpreter = loadInterpreter(ModelPaths.MELSPECTROGRAM_MODEL)
            embInterpreter = loadInterpreter(ModelPaths.EMBEDDING_MODEL)
            wakeInterpreter = loadInterpreter(ModelPaths.WAKE_WORD_MODEL)

            // Resize mel model input (dynamic input size)
            melInterpreter!!.resizeInput(0, intArrayOf(1, MEL_INPUT_COUNT))
            melInterpreter!!.allocateTensors()

            // Initialize buffers
            resetBuffers()

            _state.value = WakeWordState.READY
            Log.i(TAG, "Wake word engine initialized with OpenWakeWord models")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake word engine", e)
            _state.value = WakeWordState.ERROR
            false
        }
    }

    private fun loadInterpreter(modelPath: String): Interpreter {
        // Load model bytes from assets into a direct ByteBuffer
        val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(modelBytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(modelBytes)
            rewind()
        }

        val options = Interpreter.Options().apply {
            setNumThreads(2)
        }
        return Interpreter(buffer, options)
    }

    private fun resetBuffers() {
        // [76][32][1] - mel frames accumulated for embedding model
        accumulatedMelFrames = Array(EMB_INPUT_FRAMES) {
            Array(MEL_BINS) { FloatArray(1) { 0.0f } }
        }
        // [16][96] - embeddings accumulated for wake word model
        accumulatedEmbeddings = Array(WAKE_INPUT_FRAMES) {
            FloatArray(EMB_OUTPUT_SIZE) { 0.0f }
        }
        melFramesFilled = 0
        embeddingsFilled = 0
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
        Log.i(TAG, "Listening for wake word (OpenWakeWord pipeline active)")
    }

    override suspend fun stopListening() {
        _state.value = WakeWordState.READY
        Log.i(TAG, "Stopped listening")
    }

    override suspend fun processAudio(samples: ShortArray) {
        if (_state.value != WakeWordState.LISTENING) return

        // Check cooldown
        val now = System.currentTimeMillis()
        if (now < cooldownUntil) return

        val score = processFrame(samples)
        if (score > getDetectionThreshold()) {
            onDetection(score)
        }
    }

    /**
     * Process one audio frame through the full OpenWakeWord pipeline.
     * Thread-safe: synchronized to prevent concurrent buffer access.
     *
     * @param samples 16-bit PCM audio samples (1280 samples = 80ms at 16kHz)
     * @return wake word probability [0..1], or 0.0 if buffers not yet filled
     */
    @Synchronized
    private fun processFrame(samples: ShortArray): Float {
        val melInterp = melInterpreter ?: return 0.0f
        val embInterp = embInterpreter ?: return 0.0f
        val wakeInterp = wakeInterpreter ?: return 0.0f
        val melFrames = accumulatedMelFrames ?: return 0.0f
        val embeddings = accumulatedEmbeddings ?: return 0.0f

        // Step 1: Convert int16 samples to normalized float32
        val audioInput = Array(1) { FloatArray(MEL_INPUT_COUNT) }
        val count = minOf(samples.size, MEL_INPUT_COUNT)
        for (i in 0 until count) {
            audioInput[0][i] = samples[i].toFloat() / 32768.0f
        }

        // Step 2: Run mel spectrogram model
        // Output shape: [1, 1, T, 32] where T = MEL_OUTPUT_COUNT = 5
        val melOutput = Array(1) { Array(1) { Array(MEL_OUTPUT_COUNT) { FloatArray(MEL_BINS) } } }
        melInterp.run(audioInput, melOutput)

        // Step 3: Shift mel buffer left by MEL_OUTPUT_COUNT, append new frames with transform
        for (i in 0 until EMB_INPUT_FRAMES) {
            if (i < EMB_INPUT_FRAMES - MEL_OUTPUT_COUNT) {
                // Shift left
                melFrames[i] = melFrames[i + MEL_OUTPUT_COUNT]
            } else {
                // Append new transformed mel frame
                val melIdx = i - (EMB_INPUT_FRAMES - MEL_OUTPUT_COUNT)
                melFrames[i] = Array(MEL_BINS) { bin ->
                    floatArrayOf((melOutput[0][0][melIdx][bin] / 10.0f) + 2.0f)
                }
            }
        }

        // Track mel fill progress
        melFramesFilled = minOf(melFramesFilled + MEL_OUTPUT_COUNT, EMB_INPUT_FRAMES)

        // Step 4: If mel buffer not yet full, can't produce embeddings
        if (melFramesFilled < EMB_INPUT_FRAMES) {
            return 0.0f
        }

        // Step 5: Run embedding model
        // Input shape: [1, 76, 32, 1]
        val embInput = Array(1) { melFrames }
        // Output shape: [1, 1, 1, 96]
        val embOutput = Array(1) { Array(1) { Array(1) { FloatArray(EMB_OUTPUT_SIZE) } } }
        embInterp.run(embInput, embOutput)

        // Step 6: Shift embedding buffer left by 1, append new embedding
        for (i in 0 until WAKE_INPUT_FRAMES) {
            if (i < WAKE_INPUT_FRAMES - EMB_OUTPUT_COUNT) {
                embeddings[i] = embeddings[i + EMB_OUTPUT_COUNT]
            } else {
                embeddings[i] = embOutput[0][0][0].copyOf()
            }
        }

        // Track embedding fill progress
        embeddingsFilled = minOf(embeddingsFilled + EMB_OUTPUT_COUNT, WAKE_INPUT_FRAMES)

        // Step 7: If embedding buffer not yet full, can't produce predictions
        if (embeddingsFilled < WAKE_INPUT_FRAMES) {
            return 0.0f
        }

        // Step 8: Run wake word model
        // Input shape: [1, 16, 96]
        val wakeInput = Array(1) { embeddings }
        // Output shape: [1, 1]
        val wakeOutput = Array(1) { FloatArray(1) }
        wakeInterp.run(wakeInput, wakeOutput)

        val score = wakeOutput[0][0]
        if (score > 0.1f) {
            Log.d(TAG, "Wake word score: $score")
        }
        return score
    }

    /**
     * Get detection threshold adjusted by sensitivity.
     * Higher sensitivity = lower threshold = more sensitive.
     */
    private fun getDetectionThreshold(): Float {
        // Sensitivity 0.0 → threshold = 0.9 (very strict, few false positives)
        // Sensitivity 0.5 → threshold = 0.5 (balanced)
        // Sensitivity 1.0 → threshold = 0.1 (very sensitive, more false positives)
        return 0.9f - (sensitivity * 0.8f)
    }

    private suspend fun onDetection(confidence: Float) {
        Log.i(TAG, "Wake word detected! confidence=$confidence, threshold=${getDetectionThreshold()}")
        _state.value = WakeWordState.DETECTED
        _detections.emit(WakeWordDetection(confidence = confidence))

        // Enter cooldown
        cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS

        // Brief pause then resume listening
        _state.value = WakeWordState.COOLDOWN
        delay(COOLDOWN_MS)
        if (_state.value == WakeWordState.COOLDOWN) {
            _state.value = WakeWordState.LISTENING
        }
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0.0f, 1.0f)
        Log.d(TAG, "Sensitivity set to $sensitivity (threshold=${getDetectionThreshold()})")
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
        melInterpreter?.close()
        embInterpreter?.close()
        wakeInterpreter?.close()
        melInterpreter = null
        embInterpreter = null
        wakeInterpreter = null
        accumulatedMelFrames = null
        accumulatedEmbeddings = null
        _state.value = WakeWordState.UNINITIALIZED
        Log.i(TAG, "Wake word engine released")
    }
}
