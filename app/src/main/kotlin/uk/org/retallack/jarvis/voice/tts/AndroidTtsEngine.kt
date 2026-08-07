package uk.org.retallack.jarvis.voice.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * TTS engine using Android's TextToSpeech API.
 * Works with whatever TTS engine the user has installed
 * (eSpeak-NG, RHVoice, Piper, etc.).
 */
@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsEngine {

    private val _state = MutableStateFlow(TtsState.UNINITIALIZED)
    override val state: StateFlow<TtsState> = _state

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /** Speech rate multiplier (1.0 = normal). */
    var speechRate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    /** Pitch multiplier (1.0 = normal). */
    var pitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    /**
     * Controls whether the engine actually produces audio output.
     * When false, the engine reports success without speaking (silent mode for mic-tap activation).
     * When true, audio is produced (wake word activation).
     */
    var shouldSpeak: Boolean = true

    override suspend fun initialize(modelPath: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts = engine
                    isInitialized = true
                    engine?.language = Locale.getDefault()
                    engine?.setSpeechRate(speechRate)
                    engine?.setPitch(pitch)
                    engine?.let { setupUtteranceListener(it) }
                    _state.value = TtsState.READY
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                } else {
                    _state.value = TtsState.ERROR
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }

            continuation.invokeOnCancellation {
                engine?.shutdown()
            }
        }
    }

    private fun setupUtteranceListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = TtsState.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                _state.value = TtsState.READY
            }

            @Deprecated("Deprecated in API level 21")
            override fun onError(utteranceId: String?) {
                _state.value = TtsState.ERROR
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _state.value = TtsState.ERROR
            }
        })
    }

    override suspend fun speak(text: String, interrupt: Boolean) {
        if (!isInitialized || _state.value == TtsState.UNINITIALIZED) return

        if (!shouldSpeak) {
            // Silent mode - don't produce audio output
            return
        }

        if (interrupt) {
            stop()
        }

        withContext(Dispatchers.Main) {
            val utteranceId = UUID.randomUUID().toString()
            _state.value = TtsState.SPEAKING

            @Suppress("DEPRECATION")
            val result = tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId,
            )

            if (result != TextToSpeech.SUCCESS) {
                _state.value = TtsState.READY
            }
            // State transitions handled by UtteranceProgressListener
        }
    }

    override suspend fun stop() {
        if (_state.value == TtsState.SPEAKING) {
            tts?.stop()
            _state.value = TtsState.READY
        }
    }

    override suspend fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _state.value = TtsState.UNINITIALIZED
    }

    override fun isModelAvailable(modelPath: String): Boolean {
        // Android TTS doesn't need local models
        return isInitialized
    }
}
