package uk.org.retallack.jarvis.voice.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sherpa-ONNX based TTS engine using Piper voice models.
 * NOTE: Full implementation requires native library and model files.
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor() : TtsEngine {

    private val _state = MutableStateFlow(TtsState.UNINITIALIZED)
    override val state: StateFlow<TtsState> = _state

    override suspend fun initialize(modelPath: String): Boolean {
        // TODO: Initialize Sherpa-ONNX TTS with Piper model
        // - Load VITS model (.onnx)
        // - Load lexicon and tokens files
        // - Configure audio output (AudioTrack)
        _state.value = TtsState.ERROR
        return false
    }

    override suspend fun speak(text: String, interrupt: Boolean) {
        if (_state.value == TtsState.UNINITIALIZED) return
        if (interrupt) stop()

        _state.value = TtsState.SPEAKING
        // TODO: Generate audio samples from text
        // TODO: Play through AudioTrack with audio focus request
        _state.value = TtsState.READY
    }

    override suspend fun stop() {
        // TODO: Stop AudioTrack playback
        if (_state.value == TtsState.SPEAKING) {
            _state.value = TtsState.READY
        }
    }

    override suspend fun release() {
        // TODO: Release AudioTrack and native resources
        _state.value = TtsState.UNINITIALIZED
    }

    override fun isModelAvailable(modelPath: String): Boolean {
        val modelDir = File(modelPath)
        return modelDir.exists() && modelDir.listFiles()?.any { it.extension == "onnx" } == true
    }
}
