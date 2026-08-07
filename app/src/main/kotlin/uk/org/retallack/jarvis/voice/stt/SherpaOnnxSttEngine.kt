package uk.org.retallack.jarvis.voice.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * STT engine using Sherpa-ONNX Whisper (offline/non-streaming) model.
 * Records audio, then processes it all at once for accurate transcription.
 *
 * Flow: tap mic → record audio → tap again (or silence) → process with Whisper → result
 */
@Singleton
class SherpaOnnxSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SttEngine {

    companion object {
        private const val TAG = "SherpaSTT"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 3200 // 200ms at 16kHz
        private const val MAX_RECORDING_SECONDS = 30
        private const val SILENCE_THRESHOLD = 500 // amplitude threshold for silence detection
        private const val SILENCE_DURATION_MS = 2000L // 2 seconds of silence = stop
    }

    private val _state = MutableStateFlow(SttState.UNINITIALIZED)
    override val state: StateFlow<SttState> = _state

    private val _results = MutableSharedFlow<SttResult>(extraBufferCapacity = 16)
    override val results: Flow<SttResult> = _results

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recognizer: OfflineRecognizer? = null
    private var modelDir: String? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Buffer to collect all audio samples during recording
    private val audioBuffer = mutableListOf<FloatArray>()

    override suspend fun initialize(modelPath: String): Boolean {
        // Don't re-initialize if already ready or listening
        if (_state.value == SttState.READY || _state.value == SttState.LISTENING) {
            Log.d(TAG, "Already initialized, skipping re-init")
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                modelDir = if (modelPath.isNotBlank()) {
                    modelPath
                } else {
                    File(context.filesDir, "models/stt").absolutePath
                }

                if (!isModelAvailable(modelDir!!)) {
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

                // Don't load model yet — load on demand when startListening is called
                // This saves ~90MB RAM when idle
                _state.value = SttState.READY
                Log.i(TAG, "Sherpa-ONNX Whisper ready (model at $modelDir, will load on demand)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Sherpa-ONNX Whisper", e)
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

    /** Load the Whisper model into memory (called on demand) */
    private fun loadModel(): OfflineRecognizer? {
        val dir = modelDir ?: return null
        return try {
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = SAMPLE_RATE,
                    featureDim = 80,
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$dir/encoder.onnx",
                        decoder = "$dir/decoder.onnx",
                        language = "en",
                        task = "transcribe",
                    ),
                    tokens = "$dir/tokens.txt",
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "whisper",
                ),
                decodingMethod = "greedy_search",
            )
            Log.d(TAG, "Loading Whisper model into memory...")
            OfflineRecognizer(config = config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Whisper model", e)
            null
        }
    }

    override suspend fun startListening() {
        if (_state.value != SttState.READY) {
            Log.w(TAG, "Cannot start listening - state is ${_state.value}")
            return
        }

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

        _state.value = SttState.LISTENING
        audioBuffer.clear()

        // Start recording on IO dispatcher
        recordingJob = scope.launch {
            recordAudio()
        }
    }

    @Suppress("MissingPermission")
    private suspend fun recordAudio() {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            CHUNK_SIZE * 2,
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
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
        Log.d(TAG, "AudioRecord started (Whisper mode), buffer size=$bufferSize")

        val shortBuffer = ShortArray(CHUNK_SIZE)
        var totalSamples = 0
        val maxSamples = SAMPLE_RATE * MAX_RECORDING_SECONDS
        var silenceStart = 0L
        var hasDetectedSpeech = false

        try {
            while (currentCoroutineContext().isActive && _state.value == SttState.LISTENING) {
                val shortsRead = record.read(shortBuffer, 0, CHUNK_SIZE)
                if (shortsRead <= 0) {
                    delay(10)
                    continue
                }

                // Convert to float and buffer
                val floatSamples = FloatArray(shortsRead) { i ->
                    shortBuffer[i] / 32768.0f
                }
                audioBuffer.add(floatSamples)
                totalSamples += shortsRead

                // Check audio level for silence detection
                val maxAmplitude = shortBuffer.take(shortsRead).maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
                if (maxAmplitude > SILENCE_THRESHOLD) {
                    hasDetectedSpeech = true
                    silenceStart = 0L
                    // Emit a "recording" indicator as partial result
                    val seconds = totalSamples / SAMPLE_RATE
                    _results.tryEmit(SttResult(text = "Recording... ${seconds}s", isFinal = false))
                } else if (hasDetectedSpeech) {
                    // Silence after speech
                    if (silenceStart == 0L) {
                        silenceStart = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - silenceStart > SILENCE_DURATION_MS) {
                        Log.d(TAG, "Silence detected after speech, stopping recording")
                        break
                    }
                }

                // Max recording limit
                if (totalSamples >= maxSamples) {
                    Log.d(TAG, "Max recording time reached")
                    break
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Recording cancelled")
        } finally {
            record.stop()
            record.release()
            audioRecord = null
            Log.d(TAG, "AudioRecord stopped. Total samples: $totalSamples (${totalSamples / SAMPLE_RATE}s)")
        }

        // Process the buffered audio with Whisper
        if (audioBuffer.isNotEmpty() && hasDetectedSpeech) {
            processBufferedAudio()
        } else {
            Log.d(TAG, "No speech detected in recording")
            _state.value = SttState.READY
        }
    }

    private suspend fun processBufferedAudio() {
        _state.value = SttState.PROCESSING
        _results.tryEmit(SttResult(text = "Processing...", isFinal = false))

        withContext(Dispatchers.IO) {
            try {
                // Load model on demand
                val rec = recognizer ?: loadModel()
                if (rec == null) {
                    _state.value = SttState.ERROR
                    _results.tryEmit(
                        SttResult(text = "Failed to load speech model", isFinal = true, confidence = 0f),
                    )
                    return@withContext
                }
                recognizer = rec

                // Combine all audio chunks into one array
                val totalSize = audioBuffer.sumOf { it.size }
                val allSamples = FloatArray(totalSize)
                var offset = 0
                for (chunk in audioBuffer) {
                    chunk.copyInto(allSamples, offset)
                    offset += chunk.size
                }
                audioBuffer.clear()

                Log.d(TAG, "Processing ${totalSize} samples (${totalSize / SAMPLE_RATE}s) with Whisper...")

                // Create stream and process
                val stream = rec.createStream()
                stream.acceptWaveform(allSamples, SAMPLE_RATE)
                rec.decode(stream)
                val result = rec.getResult(stream)
                val text = result.text.trim()

                Log.d(TAG, "Whisper result: '$text'")

                if (text.isNotEmpty()) {
                    _results.tryEmit(SttResult(text = text, isFinal = true, confidence = 1.0f))
                } else {
                    _results.tryEmit(SttResult(text = "No speech detected", isFinal = true, confidence = 0f))
                }

                // Release model from memory to prevent OOM when app is backgrounded
                recognizer = null
                Log.d(TAG, "Whisper model released from memory")

                _state.value = SttState.READY
            } catch (e: Exception) {
                Log.e(TAG, "Whisper processing failed", e)
                recognizer = null
                _state.value = SttState.ERROR
                _results.tryEmit(
                    SttResult(text = "Recognition failed: ${e.message}", isFinal = true, confidence = 0f),
                )
            }
        }
    }

    override suspend fun stopListening() {
        if (_state.value != SttState.LISTENING) return
        Log.d(TAG, "stopListening called - stopping recording and processing")

        // Cancel the recording job - this will trigger processBufferedAudio in the finally block
        recordingJob?.cancelAndJoin()
        recordingJob = null

        // Process whatever audio we captured
        if (audioBuffer.isNotEmpty()) {
            processBufferedAudio()
        } else {
            _state.value = SttState.READY
        }
    }

    override suspend fun processAudio(samples: ShortArray) {
        // Not used - this engine manages its own AudioRecord
    }

    override suspend fun release() {
        recordingJob?.cancelAndJoin()
        recordingJob = null
        recognizer = null
        audioBuffer.clear()
        _state.value = SttState.UNINITIALIZED
    }

    override fun isModelAvailable(modelPath: String): Boolean {
        val modelDir = File(modelPath)
        if (!modelDir.exists()) return false
        // Whisper needs: encoder.onnx, decoder.onnx, tokens.txt
        val requiredFiles = listOf("encoder.onnx", "decoder.onnx", "tokens.txt")
        return requiredFiles.all { File(modelDir, it).exists() }
    }
}
