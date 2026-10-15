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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.org.retallack.jarvis.data.repository.ConnectionRepository
import uk.org.retallack.jarvis.data.repository.SttSettings
import uk.org.retallack.jarvis.voice.AppForegroundState
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
    private val connectionRepository: ConnectionRepository,
) : SttEngine {

    companion object {
        private const val TAG = "SherpaSTT"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 3200 // 200ms at 16kHz

        /**
         * Collapse Whisper's runaway repetition loops.
         *
         * Whisper occasionally fails to emit an end-of-text token and repeats the same
         * phrase many times, e.g. "turn kitchen radio on turn kitchen radio on ..." x31.
         * Sending that to Home Assistant guarantees "No matching devices found".
         *
         * If the whole string is a whole-number repetition of a shorter phrase, shrink it
         * to a single occurrence. If there is no such exact period, fall back to removing
         * immediate duplicate consecutive words.
         */
        internal fun collapseRepetitions(text: String): String {
            if (text.isBlank()) return text

            val words = text.split(" ").filter { it.isNotBlank() }
            if (words.isEmpty()) return text

            collapseWordLoop(words)?.let { return it }

            // Fallback: dedupe immediately-repeated consecutive words.
            val out = mutableListOf<String>()
            for (word in words) {
                if (out.isEmpty() || !out.last().equals(word, ignoreCase = true)) {
                    out.add(word)
                }
            }
            return out.joinToString(" ")
        }

        /**
         * If [words] is a whole-number repetition of a shorter token sequence, return a
         * single occurrence. Otherwise null.
         */
        private fun collapseWordLoop(words: List<String>): String? {
            val n = words.size
            if (n < 4) return null
            for (period in 1..(n / 2)) {
                if (n % period != 0) continue
                val rep = n / period
                if (rep < 3) continue // require at least 3 repeats to be confident
                var matches = true
                for (i in period until n) {
                    if (!words[i].equals(words[i % period], ignoreCase = true)) {
                        matches = false
                        break
                    }
                }
                if (matches) {
                    Log.d(TAG, "Collapsed Whisper repetition loop: ${words.size} words -> $period")
                    return words.subList(0, period).joinToString(" ")
                }
            }
            return null
        }
    }

    private val _state = MutableStateFlow(SttState.UNINITIALIZED)
    override val state: StateFlow<SttState> = _state

    private val _results = MutableSharedFlow<SttResult>(replay = 1, extraBufferCapacity = 16)
    override val results: Flow<SttResult> = _results

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelLoadMutex = kotlinx.coroutines.sync.Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var modelDir: String? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var idleJob: Job? = null
    private val idleTimeoutMs = 30000L // Release model after 30 seconds of inactivity

    /**
     * True when speech was actually detected during the current recording.
     * Used to avoid transcribing clips that contain no speech - Whisper hallucinates
     * (repetition loops / "[ Inaudible ]") when fed near-silent audio.
     */
    @Volatile
    private var hasDetectedSpeechThisRecording = false

    // Configurable settings loaded from DataStore on each startListening()
    private var currentSettings: SttSettings = SttSettings()

    // Buffer to collect all audio samples during recording
    private val audioBuffer = mutableListOf<FloatArray>()

    override suspend fun initialize(modelPath: String): Boolean {
        return modelLoadMutex.withLock {
            // Already loaded (e.g. capture started first and awaitRecognizer beat us here).
            if (recognizer != null) {
                Log.d(TAG, "Already initialized, skipping re-init")
                if (_state.value == SttState.UNINITIALIZED) _state.value = SttState.READY
                return@withLock true
            }

            return@withLock withContext(Dispatchers.IO) {
                try {
                    modelDir = if (modelPath.isNotBlank()) {
                        modelPath
                    } else {
                        modelDir ?: File(context.filesDir, "models/stt").absolutePath
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

                    // Pre-load Whisper model so the first utterance isn't delayed.
                    Log.i(TAG, "Pre-loading Whisper model into memory...")
                    recognizer = loadModel()
                    if (recognizer == null) {
                        _state.value = SttState.ERROR
                        _results.tryEmit(
                            SttResult(text = "Failed to load speech model", isFinal = true, confidence = 0f)
                        )
                        return@withContext false
                    }
                    if (_state.value != SttState.LISTENING && _state.value != SttState.PROCESSING) {
                        _state.value = SttState.READY
                    }
                    Log.i(TAG, "Sherpa-ONNX Whisper ready and pre-loaded (model at $modelDir)")
                    scheduleIdleRelease()
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
    }
    /** Load the Whisper model into memory (called on demand) */    private fun scheduleIdleRelease() {
        idleJob?.cancel()

        // Never keep the model resident while the app is in the background. Holding
        // ~160 MB of native heap in a background process makes it a prime target for
        // the OS low-memory killer.
        val busy = _state.value == SttState.LISTENING || _state.value == SttState.PROCESSING
        if (!busy && !AppForegroundState.isAppForeground) {
            Log.d(TAG, "App not in foreground, releasing Whisper model to free memory")
            releaseModel()
            return
        }

        idleJob = scope.launch {
            delay(idleTimeoutMs)
            if (_state.value == SttState.READY) {
                Log.d(TAG, "Idle timeout reached, releasing Whisper model to free memory")
                releaseModel()
            }
        }
    }

    /** Release the native Whisper model and reset to UNINITIALIZED. */
    private fun releaseModel() {
        idleJob?.cancel()
        idleJob = null
        recognizer = null
        if (_state.value == SttState.READY || _state.value == SttState.ERROR) {
            _state.value = SttState.UNINITIALIZED
        }
    }

    /**
     * Called when the app moves between foreground and background.
     *
     * On backgrounding we immediately drop the Whisper model so the process stays
     * lightweight and is far less likely to be killed while running the wake-word
     * service. On returning to the foreground we transparently re-initialise so the
     * first voice command is still fast.
     */
    fun onAppForegroundStateChanged(isForeground: Boolean) {
        if (isForeground) {
            if (_state.value == SttState.UNINITIALIZED) {
                scope.launch { initialize(modelDir ?: "") }
            }
        } else if (!AppForegroundState.hasActiveSession &&
            _state.value != SttState.LISTENING &&
            _state.value != SttState.PROCESSING
        ) {
            Log.d(TAG, "App backgrounded, releasing Whisper model to free memory")
            releaseModel()
        }
    }

    /**
     * Return a ready recognizer, loading it if necessary.
     *
     * Serialised with a mutex so that a concurrent [initialize] (triggered when capture
     * starts before the model is warm) can't cause two loads at once.
     */
    private suspend fun awaitRecognizer(): OfflineRecognizer? {
        recognizer?.let { return it }
        return modelLoadMutex.withLock {
            recognizer ?: loadModel()?.also {
                recognizer = it
            }
        }
    }

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
                    numThreads = 4,
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
        // Start capturing audio immediately - the model may still be loading in parallel.
        // Making the microphone wait for a ~2 s model load meant losing the start of the
        // user's sentence (and left the input stream in a dead state on some devices).
        if (_state.value == SttState.ERROR) {
            Log.w(TAG, "Cannot start listening - engine in ERROR state")
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

        // Load latest settings from DataStore
        currentSettings = connectionRepository.getSttSettings()
        Log.d(TAG, "STT settings: silence=${currentSettings.silenceDurationMs}ms, threshold=${currentSettings.silenceThreshold}, max=${currentSettings.maxRecordingSeconds}s")

        idleJob?.cancel()
        _state.value = SttState.LISTENING
        audioBuffer.clear()
        hasDetectedSpeechThisRecording = false

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
        val maxSamples = SAMPLE_RATE * currentSettings.maxRecordingSeconds
        var silenceStart = 0L
        var hasDetectedSpeech = false
        var peakAmplitude = 0
        var levelLogCounter = 0

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

                // Diagnostic: report the peak level periodically so we can tell whether
                // the microphone is actually delivering audio (vs a silent/contended
                // input stream) without needing to dump raw PCM.
                peakAmplitude = maxOf(peakAmplitude, maxAmplitude)
                if (++levelLogCounter % 25 == 0) {
                    Log.d(
                        TAG,
                        "Mic level: peak=$peakAmplitude (threshold=${currentSettings.silenceThreshold}), " +
                            "speechDetected=$hasDetectedSpeech, samples=${totalSamples / SAMPLE_RATE}s",
                    )
                }

                if (maxAmplitude > currentSettings.silenceThreshold) {
                    hasDetectedSpeech = true
                    hasDetectedSpeechThisRecording = true
                    silenceStart = 0L
                    // Emit a "recording" indicator as partial result
                    val seconds = totalSamples / SAMPLE_RATE
                    // _results.tryEmit(SttResult(text = "Recording... ${seconds}s", isFinal = false))
                } else if (hasDetectedSpeech) {
                    // Silence after speech
                    if (silenceStart == 0L) {
                        silenceStart = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - silenceStart > currentSettings.silenceDurationMs) {
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
        // _results.tryEmit(SttResult(text = "Processing...", isFinal = false))

        withContext(Dispatchers.IO) {
            try {
                // Load the model on demand, waiting for any in-flight load to finish. When
                // capture starts before the model is ready, this is what bridges the gap.
                val rec = awaitRecognizer()
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
                val text = collapseRepetitions(result.text.trim())

                Log.d(TAG, "Whisper result: '$text'")

                val now = System.currentTimeMillis()
                if (text.isNotEmpty()) {
                    _results.tryEmit(
                        SttResult(text = text, isFinal = true, confidence = 1.0f, timestampMs = now),
                    )
                } else {
                    _results.tryEmit(
                        SttResult(
                            text = "No speech detected",
                            isFinal = true,
                            confidence = 0f,
                            timestampMs = now,
                        ),
                    )
                }

                // Keep model resident temporarily, then schedule release if idle
                Log.d(TAG, "Whisper model kept resident in memory")

                _state.value = SttState.READY
                scheduleIdleRelease()
            } catch (e: Exception) {
                Log.e(TAG, "Whisper processing failed", e)
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

        // Process whatever audio we captured - but only if it actually contains speech.
        // Transcribing a near-silent buffer makes Whisper hallucinate repetition loops.
        if (audioBuffer.isNotEmpty() && hasDetectedSpeechThisRecording) {
            processBufferedAudio()
        } else {
            Log.d(TAG, "stopListening: no speech detected, skipping transcription")
            audioBuffer.clear()
            _state.value = SttState.READY
            _results.tryEmit(
                SttResult(
                    text = "No speech detected",
                    isFinal = true,
                    confidence = 0f,
                    timestampMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun processAudio(samples: ShortArray) {
        // Not used - this engine manages its own AudioRecord
    }

    override suspend fun release() {
        recordingJob?.cancelAndJoin()
        recordingJob = null
        idleJob?.cancel()
        idleJob = null
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
