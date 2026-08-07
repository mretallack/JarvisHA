package uk.org.retallack.jarvis.voice.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sherpa-ONNX based STT engine for offline speech recognition.
 * Uses AudioRecord to capture audio and feeds it to the OnlineRecognizer
 * for streaming speech-to-text with endpoint detection.
 */
@Singleton
class SherpaOnnxSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SttEngine {

    companion object {
        private const val TAG = "SherpaSTT"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 3200 // 200ms at 16kHz
        private const val SILENCE_TIMEOUT_MS = 2000L // 2 seconds of no new text = done
    }

    private val _state = MutableStateFlow(SttState.UNINITIALIZED)
    override val state: StateFlow<SttState> = _state

    private val _results = MutableSharedFlow<SttResult>(extraBufferCapacity = 16)
    override val results: Flow<SttResult> = _results

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun initialize(modelPath: String): Boolean {
        // Don't re-initialize if already ready or listening
        if (_state.value == SttState.READY || _state.value == SttState.LISTENING) {
            Log.d(TAG, "Already initialized, skipping re-init")
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                val modelDir = if (modelPath.isNotBlank()) {
                    modelPath
                } else {
                    // Default model directory
                    File(context.filesDir, "models/stt").absolutePath
                }

                // Check if model files exist
                if (!isModelAvailable(modelDir)) {
                    Log.w(TAG, "Model files not found at $modelDir")
                    _state.value = SttState.ERROR
                    _results.tryEmit(
                        SttResult(
                            text = "STT model not downloaded. Please download from Settings.",
                            isFinal = true,
                            confidence = 0f,
                        ),
                    )
                    return@withContext false
                }

                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(
                        sampleRate = SAMPLE_RATE,
                        featureDim = 80,
                    ),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = "$modelDir/encoder.onnx",
                            decoder = "$modelDir/decoder.onnx",
                            joiner = "$modelDir/joiner.onnx",
                        ),
                        tokens = "$modelDir/tokens.txt",
                        numThreads = 2,
                        debug = false,
                        provider = "cpu",
                        modelType = "zipformer",
                    ),
                    endpointConfig = EndpointConfig(
                        rule1 = EndpointRule(false, 2.4f, 0.0f),
                        rule2 = EndpointRule(true, 1.2f, 0.0f),
                        rule3 = EndpointRule(false, 0.0f, 20.0f),
                    ),
                    enableEndpoint = true,
                    decodingMethod = "greedy_search",
                )

                recognizer = OnlineRecognizer(config = config)
                _state.value = SttState.READY
                Log.i(TAG, "Sherpa-ONNX recognizer initialized from $modelDir")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Sherpa-ONNX", e)
                _state.value = SttState.ERROR
                _results.tryEmit(
                    SttResult(
                        text = "STT initialization failed: ${e.message}",
                        isFinal = true,
                        confidence = 0f,
                    ),
                )
                false
            }
        }
    }

    override suspend fun startListening() {
        if (_state.value != SttState.READY) {
            Log.w(TAG, "Cannot start listening - state is ${_state.value}")
            return
        }

        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            _state.value = SttState.ERROR
            _results.tryEmit(
                SttResult(text = "Microphone permission required", isFinal = true, confidence = 0f),
            )
            return
        }

        val rec = recognizer ?: run {
            Log.e(TAG, "Recognizer not initialized")
            _state.value = SttState.ERROR
            return
        }

        _state.value = SttState.LISTENING

        // Create a fresh stream for this recognition session
        stream = rec.createStream()

        // Start audio recording on IO dispatcher
        recordingJob = scope.launch {
            captureAndRecognize(rec)
        }
    }

    @Suppress("MissingPermission")
    private suspend fun captureAndRecognize(rec: OnlineRecognizer) {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            CHUNK_SIZE * 2, // At least one chunk worth of buffer
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            _state.value = SttState.ERROR
            _results.tryEmit(
                SttResult(text = "Failed to access microphone", isFinal = true, confidence = 0f),
            )
            return
        }

        audioRecord = record
        record.startRecording()
        Log.d(TAG, "AudioRecord started, buffer size=$bufferSize")

        val shortBuffer = ShortArray(CHUNK_SIZE)
        var lastTextChange = System.currentTimeMillis()
        var lastText = ""

        try {
            val currentStream = stream ?: return

            // Feed a pre-buffer of silence to warm up the recognizer
            // This prevents the first word from being missed.
            // We need ~300ms of silence (4800 samples at 16kHz) for the model to be ready.
            val silenceFrames = 3 // Feed 3 chunks of silence (~600ms)
            val silenceBuffer = FloatArray(CHUNK_SIZE) { 0.0f }
            repeat(silenceFrames) {
                currentStream.acceptWaveform(silenceBuffer, SAMPLE_RATE)
                while (rec.isReady(currentStream)) {
                    rec.decode(currentStream)
                }
            }

            while (currentCoroutineContext().isActive && _state.value == SttState.LISTENING) {
                val shortsRead = record.read(shortBuffer, 0, CHUNK_SIZE)
                if (shortsRead <= 0) {
                    delay(10)
                    continue
                }

                // Convert short samples to float (sherpa-onnx expects float in [-1, 1])
                val floatSamples = FloatArray(shortsRead) { i ->
                    shortBuffer[i] / 32768.0f
                }

                // Feed audio to the stream
                currentStream.acceptWaveform(floatSamples, SAMPLE_RATE)

                // Decode while ready
                while (rec.isReady(currentStream)) {
                    rec.decode(currentStream)
                }

                // Get current result
                val result = rec.getResult(currentStream)
                val currentText = result.text.trim()

                if (currentText.isNotEmpty()) {
                    if (currentText != lastText) {
                        lastText = currentText
                        lastTextChange = System.currentTimeMillis()
                        // Emit partial result
                        _results.tryEmit(SttResult(text = currentText, isFinal = false))
                    }

                    // Check for endpoint (built-in VAD)
                    if (rec.isEndpoint(currentStream)) {
                        Log.d(TAG, "Endpoint detected, text: $currentText")
                        // Emit final result
                        _results.tryEmit(
                            SttResult(text = currentText, isFinal = true, confidence = 1.0f),
                        )
                        // Reset for potential continuation
                        rec.reset(currentStream)
                        lastText = ""
                        // Stop listening after endpoint
                        _state.value = SttState.READY
                        break
                    }
                } else {
                    // No text yet, check silence timeout if we previously had text
                    if (lastText.isNotEmpty()) {
                        val silenceDuration = System.currentTimeMillis() - lastTextChange
                        if (silenceDuration > SILENCE_TIMEOUT_MS) {
                            Log.d(TAG, "Silence timeout, finalizing: $lastText")
                            _results.tryEmit(
                                SttResult(
                                    text = lastText,
                                    isFinal = true,
                                    confidence = 1.0f,
                                ),
                            )
                            _state.value = SttState.READY
                            break
                        }
                    }

                    // Also check endpoint for silence-only case
                    if (rec.isEndpoint(currentStream)) {
                        if (lastText.isNotEmpty()) {
                            _results.tryEmit(
                                SttResult(
                                    text = lastText,
                                    isFinal = true,
                                    confidence = 1.0f,
                                ),
                            )
                        }
                        rec.reset(currentStream)
                        lastText = ""
                        _state.value = SttState.READY
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during recognition", e)
            if (lastText.isNotEmpty()) {
                _results.tryEmit(SttResult(text = lastText, isFinal = true, confidence = 0.8f))
            }
            _state.value = SttState.ERROR
        } finally {
            record.stop()
            record.release()
            audioRecord = null
            Log.d(TAG, "AudioRecord stopped and released")
        }
    }

    override suspend fun processAudio(samples: ShortArray) {
        // Not used - this engine manages its own AudioRecord
        // But support external audio feeding if needed
        val currentStream = stream ?: return
        val rec = recognizer ?: return

        val floatSamples = FloatArray(samples.size) { i ->
            samples[i] / 32768.0f
        }
        currentStream.acceptWaveform(floatSamples, SAMPLE_RATE)

        while (rec.isReady(currentStream)) {
            rec.decode(currentStream)
        }

        val result = rec.getResult(currentStream)
        if (result.text.isNotBlank()) {
            _results.tryEmit(SttResult(text = result.text.trim(), isFinal = false))
        }
    }

    override suspend fun stopListening() {
        if (_state.value != SttState.LISTENING) return
        Log.d(TAG, "stopListening called")

        _state.value = SttState.PROCESSING

        // Stop the recording job
        recordingJob?.cancelAndJoin()
        recordingJob = null

        // Get final result from stream
        val currentStream = stream
        val rec = recognizer
        if (currentStream != null && rec != null) {
            currentStream.inputFinished()

            // Decode any remaining audio
            while (rec.isReady(currentStream)) {
                rec.decode(currentStream)
            }

            val result = rec.getResult(currentStream)
            if (result.text.isNotBlank()) {
                _results.tryEmit(
                    SttResult(text = result.text.trim(), isFinal = true, confidence = 1.0f),
                )
            }
        }

        stream = null
        _state.value = SttState.READY
    }

    override suspend fun release() {
        recordingJob?.cancelAndJoin()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        stream?.release()
        stream = null
        recognizer?.release()
        recognizer = null
        _state.value = SttState.UNINITIALIZED
        Log.i(TAG, "Sherpa-ONNX engine released")
    }

    override fun isModelAvailable(modelPath: String): Boolean {
        val modelDir = File(modelPath)
        if (!modelDir.exists()) return false
        val requiredFiles = listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt")
        return requiredFiles.all { File(modelDir, it).exists() }
    }
}
