package uk.org.retallack.jarvis.voice.stt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * STT engine using Android's SpeechRecognizer API.
 * Delegates to whatever recognition service the user has installed
 * (FUTO Voice Input, Whisper, Vosk, etc.).
 *
 * IMPORTANT: SpeechRecognizer must be created and used on the main thread.
 */
@Singleton
class AndroidSpeechRecognizerSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SttEngine {

    private val _state = MutableStateFlow(SttState.UNINITIALIZED)
    override val state: StateFlow<SttState> = _state

    private val _results = MutableSharedFlow<SttResult>(extraBufferCapacity = 16)
    override val results: Flow<SttResult> = _results

    private var speechRecognizer: SpeechRecognizer? = null
    private var serviceComponent: ComponentName? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SttState.LISTENING
        }

        override fun onBeginningOfSpeech() {
            // Already in LISTENING state
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could be used for audio level visualization
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Raw audio buffer - not needed
        }

        override fun onEndOfSpeech() {
            _state.value = SttState.PROCESSING
        }

        override fun onError(error: Int) {
            val errorMessage = mapErrorCode(error)
            _state.value = SttState.ERROR
            _results.tryEmit(SttResult(text = errorMessage, isFinal = true, confidence = 0f))
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                val confidence = confidences?.firstOrNull() ?: 1.0f
                _results.tryEmit(SttResult(text = text, isFinal = true, confidence = confidence))
            }
            _state.value = SttState.READY
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                if (text.isNotBlank()) {
                    _results.tryEmit(SttResult(text = text, isFinal = false))
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Reserved for future use
        }
    }

    /**
     * Set the target recognition service.
     * Call before [startListening] to change the service.
     */
    fun setServiceComponent(component: ComponentName?) {
        serviceComponent = component
    }

    override suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = SttState.ERROR
                return@withContext false
            }

            speechRecognizer?.destroy()

            // If no specific service set, try to find one automatically
            if (serviceComponent == null) {
                val discovery = SttServiceDiscovery(context)
                val services = discovery.getAvailableServices()
                if (services.isNotEmpty()) {
                    serviceComponent = services.first().componentName
                }
            }

            speechRecognizer = if (serviceComponent != null) {
                SpeechRecognizer.createSpeechRecognizer(context, serviceComponent!!)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(recognitionListener)
            _state.value = SttState.READY
            true
        } catch (e: Exception) {
            _state.value = SttState.ERROR
            false
        }
    }

    override suspend fun startListening() = withContext(Dispatchers.Main) {
        if (_state.value != SttState.READY) return@withContext

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
            // State will transition to LISTENING via onReadyForSpeech callback
        } catch (e: Exception) {
            _state.value = SttState.ERROR
        }
    }

    override suspend fun processAudio(samples: ShortArray) {
        // Not used with SpeechRecognizer - it handles its own audio capture
    }

    override suspend fun stopListening() = withContext(Dispatchers.Main) {
        if (_state.value == SttState.LISTENING) {
            speechRecognizer?.stopListening()
            _state.value = SttState.PROCESSING
        }
    }

    override suspend fun release() = withContext(Dispatchers.Main) {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = SttState.UNINITIALIZED
    }

    override fun isModelAvailable(modelPath: String): Boolean {
        // SpeechRecognizer doesn't need local models - always available if service exists
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    private fun mapErrorCode(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Unknown error ($error)"
    }
}
