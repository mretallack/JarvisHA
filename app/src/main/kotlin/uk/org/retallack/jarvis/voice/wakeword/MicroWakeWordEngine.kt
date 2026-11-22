package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.org.retallack.jarvis.microwakeword.MicroWakeWord
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wake word engine backed by microWakeWord.
 *
 * microWakeWord does all the work (audio frontend, int8 inference, sliding window
 * detection) in a single native engine; this class just owns its lifecycle and adapts it
 * to the [WakeWordEngine] interface.
 *
 * Replaces the previous OpenWakeWord engine. See
 * docs/microwakeword-migration-plan.md.
 */
@Singleton
class MicroWakeWordEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : WakeWordEngine {

    private val _state = MutableStateFlow(WakeWordState.UNINITIALIZED)
    override val state: StateFlow<WakeWordState> = _state

    private val _detections = MutableSharedFlow<WakeWordDetection>(extraBufferCapacity = 8)
    override val detections: Flow<WakeWordDetection> = _detections

    private var sensitivity: Float = 0.5f
    private var quietHoursConfig = QuietHoursConfig()

    private var modelConfig: MicroWakeWordModelConfig? = null

    /** Direct ByteBuffer of the model; safe to reuse across engine instances (copied in JNI). */
    private var modelBuffer: ByteBuffer? = null

    private var detector: MicroWakeWord? = null

    /** The cutoff the current [detector] was created with, so we can detect changes. */
    private var appliedCutoff: Float = Float.NaN

    override suspend fun initialize(): Boolean {
        return try {
            val config = MicroWakeWordModelConfig.load(context, ModelPaths.WAKE_WORD_MANIFEST)
            modelConfig = config
            modelBuffer = loadModelBuffer(
                config.modelAssetPath(ModelPaths.WAKEWORD_ASSET_DIR),
            )
            _state.value = WakeWordState.READY
            Log.i(TAG, "microWakeWord ready (model=${config.model})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise microWakeWord", e)
            _state.value = WakeWordState.ERROR
            false
        }
    }

    override suspend fun startListening() {
        if (isInQuietHours()) {
            _state.value = WakeWordState.PAUSED
            return
        }

        val config = modelConfig
        val buffer = modelBuffer
        if (config == null || buffer == null) {
            Log.e(TAG, "startListening() before a successful initialize()")
            _state.value = WakeWordState.ERROR
            return
        }

        val cutoff = detectionCutoff(sensitivity, config.micro.probabilityCutoff)

        try {
            if (detector == null || cutoff != appliedCutoff) {
                // The cutoff is a constructor argument of the native engine, so a change
                // in sensitivity requires a fresh instance.
                detector?.close()
                detector = MicroWakeWord(
                    modelBuffer = buffer,
                    featureStepSizeMs = config.micro.featureStepSize,
                    probabilityCutoff = cutoff,
                    slidingWindowSize = config.micro.slidingWindowSize,
                ).also { warmUp(it) }
                appliedCutoff = cutoff
                Log.i(
                    TAG,
                    "microWakeWord listening (cutoff=$cutoff, window=${config.micro.slidingWindowSize})",
                )
            } else {
                // Same parameters: reset state and re-prime the frontend noise estimate.
                detector!!.reset()
                warmUp(detector!!)
            }
            _state.value = WakeWordState.LISTENING
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start microWakeWord", t)
            _state.value = WakeWordState.ERROR
        }
    }

    override suspend fun stopListening() {
        _state.value = WakeWordState.READY
    }

    override suspend fun processAudio(samples: ShortArray) {
        val engine = detector ?: return
        val detected = try {
            engine.processAudio(samples)
        } catch (t: Throwable) {
            Log.e(TAG, "microWakeWord inference failed", t)
            _state.value = WakeWordState.ERROR
            return
        }
        if (detected) {
            Log.i(TAG, "Wake word detected")
            _state.value = WakeWordState.DETECTED
            _detections.tryEmit(WakeWordDetection(confidence = appliedCutoff))
        }
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0f, 1f)
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
        detector?.close()
        detector = null
        appliedCutoff = Float.NaN
        _state.value = WakeWordState.UNINITIALIZED
    }

    /**
     * Feed the freshly created (or reset) engine a few seconds of very quiet synthetic
     * noise, faster than real time.
     *
     * The frontend needs 1-2 s of background audio to prime its noise-reduction estimate,
     * and the engine ignores the first [WARM_UP_CHUNKS] inference slices regardless. Without
     * this, a wake word spoken immediately after the listener starts is often missed.
     * Mirrors Home Assistant's `WakeWordListener` warm-up.
     */
    private fun warmUp(engine: MicroWakeWord) {
        val noise = Random(WARM_UP_SEED)
        val chunk = ShortArray(WARM_UP_CHUNK_SAMPLES)
        repeat(WARM_UP_CHUNKS) {
            for (i in chunk.indices) {
                chunk[i] = (noise.nextGaussian() * WARM_UP_NOISE_STDDEV).toInt().toShort()
            }
            engine.processAudio(chunk)
        }
    }

    /** Maps the model into a direct ByteBuffer, which JNI requires. */
    private fun loadModelBuffer(assetPath: String): ByteBuffer {
        // Requires the asset to be stored uncompressed (see app/build.gradle.kts
        // `noCompress += "tflite"`), otherwise openFd() throws.
        val afd = context.assets.openFd(assetPath)
        return afd.use {
            val fis = FileInputStream(it.fileDescriptor)
            val mapped = fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                it.startOffset,
                it.declaredLength,
            )
            // Closing the stream does not unmap the buffer on Linux; the mapping lives
            // until the ByteBuffer is garbage collected.
            fis.close()
            mapped.order(ByteOrder.nativeOrder())
        }
    }

    companion object {
        private const val TAG = "MicroWakeWordEngine"

        /** 4 s of warm-up audio, in 10 ms chunks. Mirrors Home Assistant. */
        private const val WARM_UP_CHUNKS = 400
        private const val WARM_UP_CHUNK_SAMPLES = 160

        /** Quiet, well below speech. */
        private const val WARM_UP_NOISE_STDDEV = 25.0

        /** Fixed seed so warm-up is reproducible. */
        private const val WARM_UP_SEED = 42L

        /**
         * Maps the user's sensitivity slider to a microWakeWord probability cutoff.
         *
         * Single source of truth - used both to configure the engine and to log it, so the
         * reported cutoff can never drift from the one actually applied.
         *
         * The slider keeps its counter-intuitive OpenWakeWord semantics: LOWER sensitivity =
         * HIGHER cutoff = FEWER false positives. The default (0.5) is the model's own
         * validated `probability_cutoff` from its manifest; the slider nudges it by +/-0.1.
         */
        fun detectionCutoff(sensitivity: Float, manifestCutoff: Float): Float =
            (manifestCutoff + (0.5f - sensitivity.coerceIn(0f, 1f)) * 0.2f).coerceIn(0.5f, 0.99f)
    }
}
