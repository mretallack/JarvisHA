package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenWakeWord TFLite inference engine.
 * Uses 3 models: melspectrogram → embedding → hey_jarvis
 *
 * Based on Dicio's OwwModel implementation (proven working on Android).
 * Models are loaded from app internal files (copied from assets on first run).
 */
@Singleton
class LiteRtWakeWordEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : WakeWordEngine {

    companion object {
        private const val TAG = "LiteRtWakeWordEngine"

        // mel model shape is [1,x] -> [1,1,floor((x-512)/160)+1,32]
        const val MEL_INPUT_COUNT = 512 + 160 * 4 // 1152 samples @ 16kHz = 72ms
        const val MEL_OUTPUT_COUNT = (MEL_INPUT_COUNT - 512) / 160 + 1 // = 5
        const val MEL_FEATURE_SIZE = 32

        // emb model shape is [1,76,32,1] -> [1,1,1,96]
        const val EMB_INPUT_COUNT = 76
        const val EMB_OUTPUT_COUNT = 1
        const val EMB_FEATURE_SIZE = 96

        // wake model shape is [1,16,96] -> [1,1]
        const val WAKE_INPUT_COUNT = 16

        private const val MODELS_ASSET_DIR = "models"
        private const val MEL_MODEL = "melspectrogram.tflite"
        private const val EMB_MODEL = "embedding_model.tflite"
        private const val WAKE_MODEL = "hey_jarvis.tflite"
    }

    private val _state = MutableStateFlow(WakeWordState.UNINITIALIZED)
    override val state: StateFlow<WakeWordState> = _state

    private val _detections = kotlinx.coroutines.flow.MutableSharedFlow<WakeWordDetection>(extraBufferCapacity = 8)
    override val detections: kotlinx.coroutines.flow.Flow<WakeWordDetection> = _detections

    private var sensitivity: Float = 0.5f
    private var quietHoursConfig = QuietHoursConfig()

    private var melInterpreter: Interpreter? = null
    private var embInterpreter: Interpreter? = null
    private var wakeInterpreter: Interpreter? = null

    private var accumulatedMelOutputs: Array<Array<FloatArray>> = Array(EMB_INPUT_COUNT) { arrayOf() }
    private var accumulatedEmbOutputs: Array<FloatArray> = Array(WAKE_INPUT_COUNT) { floatArrayOf() }

    private var isClosed = false

    override suspend fun initialize(): Boolean {
        try {
            // Copy models from assets to files dir (TFLite Interpreter needs File, not InputStream)
            val modelsDir = File(context.filesDir, "wakeword_models")
            modelsDir.mkdirs()

            val melFile = copyAssetToFile("$MODELS_ASSET_DIR/$MEL_MODEL", File(modelsDir, MEL_MODEL))
            val embFile = copyAssetToFile("$MODELS_ASSET_DIR/$EMB_MODEL", File(modelsDir, EMB_MODEL))
            val wakeFile = copyAssetToFile("$MODELS_ASSET_DIR/$WAKE_MODEL", File(modelsDir, WAKE_MODEL))

            // Load models exactly like Dicio does
            melInterpreter = loadModel(melFile, intArrayOf(1, MEL_INPUT_COUNT))

            try {
                embInterpreter = loadModel(embFile)
            } catch (t: Throwable) {
                melInterpreter?.close()
                melInterpreter = null
                throw t
            }

            try {
                wakeInterpreter = loadModel(wakeFile)
            } catch (t: Throwable) {
                melInterpreter?.close()
                embInterpreter?.close()
                melInterpreter = null
                embInterpreter = null
                throw t
            }

            // Reset accumulators
            accumulatedMelOutputs = Array(EMB_INPUT_COUNT) { arrayOf() }
            accumulatedEmbOutputs = Array(WAKE_INPUT_COUNT) { floatArrayOf() }
            isClosed = false

            _state.value = WakeWordState.READY
            Log.i(TAG, "OpenWakeWord models loaded successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OpenWakeWord", e)
            _state.value = WakeWordState.ERROR
            return false
        }
    }

    fun getFrameSize(): Int = MEL_INPUT_COUNT

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity
    }

    /**
     * Process one audio frame and return wake word detection score.
     * @param audioFrame 16-bit PCM samples, must be exactly MEL_INPUT_COUNT (1152) samples
     * @return detection probability (0.0 - 1.0), compare against threshold
     */
    fun processFrame(audioFrame: ShortArray): Float {
        synchronized(this) {
            if (isClosed) return 0.0f

            val mel = melInterpreter ?: return 0.0f
            val emb = embInterpreter ?: return 0.0f
            val wake = wakeInterpreter ?: return 0.0f

            if (audioFrame.size != MEL_INPUT_COUNT) {
                Log.w(TAG, "Frame size mismatch: expected $MEL_INPUT_COUNT, got ${audioFrame.size}")
                return 0.0f
            }

            // Convert to float [-1, 1]
            val floatAudio = FloatArray(MEL_INPUT_COUNT) { i ->
                audioFrame[i].toFloat() / 32768.0f
            }

            // Step 1: Mel spectrogram
            val melOutput = Array(MEL_OUTPUT_COUNT) { FloatArray(MEL_FEATURE_SIZE) }
            mel.run(arrayOf(floatAudio), arrayOf(arrayOf(melOutput)))

            // Shift and accumulate mel outputs
            for (i in 0 until EMB_INPUT_COUNT) {
                accumulatedMelOutputs[i] = if (i < EMB_INPUT_COUNT - MEL_OUTPUT_COUNT) {
                    accumulatedMelOutputs[i + MEL_OUTPUT_COUNT]
                } else {
                    melOutput[i - EMB_INPUT_COUNT + MEL_OUTPUT_COUNT]
                        .map { floatArrayOf((it / 10.0f) + 2.0f) }
                        .toTypedArray()
                }
            }

            // Not fully warmed up yet
            if (accumulatedMelOutputs[0].isEmpty()) return 0.0f

            // Step 2: Embedding
            val embOutput = Array(EMB_OUTPUT_COUNT) { FloatArray(EMB_FEATURE_SIZE) }
            emb.run(arrayOf(accumulatedMelOutputs), arrayOf(arrayOf(embOutput)))

            // Shift and accumulate embedding outputs
            for (i in 0 until WAKE_INPUT_COUNT) {
                accumulatedEmbOutputs[i] = if (i < WAKE_INPUT_COUNT - EMB_OUTPUT_COUNT) {
                    accumulatedEmbOutputs[i + EMB_OUTPUT_COUNT]
                } else {
                    embOutput[i - WAKE_INPUT_COUNT + EMB_OUTPUT_COUNT]
                }
            }

            // Not fully warmed up yet
            if (accumulatedEmbOutputs[0].isEmpty()) return 0.0f

            // Step 3: Wake word detection
            val wakeOutput = FloatArray(1)
            wake.run(arrayOf(accumulatedEmbOutputs), arrayOf(wakeOutput))

            return wakeOutput[0]
        }
    }

    fun isDetected(score: Float): Boolean {
        // Map sensitivity (0.0-1.0) to threshold (0.9-0.1)
        val threshold = 0.9f - (sensitivity * 0.8f)
        return score > threshold
    }

    override suspend fun startListening() {
        _state.value = WakeWordState.LISTENING
    }

    override suspend fun stopListening() {
        _state.value = WakeWordState.READY
    }

    override suspend fun processAudio(samples: ShortArray) {
        val score = processFrame(samples)
        if (isDetected(score)) {
            _state.value = WakeWordState.DETECTED
            _detections.tryEmit(WakeWordDetection(confidence = score))
        }
    }

    override fun setQuietHours(config: QuietHoursConfig) {
        quietHoursConfig = config
    }

    override fun isInQuietHours(): Boolean {
        if (!quietHoursConfig.enabled) return false
        val now = java.time.LocalTime.now()
        val start = quietHoursConfig.startTime
        val end = quietHoursConfig.endTime
        return if (start < end) {
            now in start..end
        } else {
            now >= start || now <= end
        }
    }

    override suspend fun release() {
        synchronized(this) {
            isClosed = true
            melInterpreter?.close()
            embInterpreter?.close()
            wakeInterpreter?.close()
            melInterpreter = null
            embInterpreter = null
            wakeInterpreter = null
            _state.value = WakeWordState.UNINITIALIZED
        }
    }

    private fun loadModel(modelFile: File, inputDims: IntArray? = null): Interpreter {
        val interpreter = Interpreter(modelFile)
        if (inputDims != null) {
            interpreter.resizeInput(0, inputDims)
        }
        interpreter.allocateTensors()
        return interpreter
    }

    private fun copyAssetToFile(assetPath: String, destFile: File): File {
        if (destFile.exists() && destFile.length() > 0) {
            return destFile // Already copied
        }
        destFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Copied asset $assetPath to ${destFile.absolutePath} (${destFile.length()} bytes)")
        return destFile
    }
}
