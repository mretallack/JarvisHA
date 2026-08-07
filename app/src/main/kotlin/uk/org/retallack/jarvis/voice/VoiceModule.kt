package uk.org.retallack.jarvis.voice

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.org.retallack.jarvis.voice.stt.SherpaOnnxSttEngine
import uk.org.retallack.jarvis.voice.stt.SttEngine
import uk.org.retallack.jarvis.voice.tts.AndroidTtsEngine
import uk.org.retallack.jarvis.voice.tts.TtsEngine
import uk.org.retallack.jarvis.voice.wakeword.MockWakeWordEngine
import uk.org.retallack.jarvis.voice.wakeword.WakeWordEngine
import javax.inject.Singleton

/**
 * DI module that provides voice pipeline bindings.
 * Uses Sherpa-ONNX for on-device STT (offline, integrated AudioRecord).
 * Uses Android TTS for speech output.
 * Mock implementations remain available for unit testing via direct instantiation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindSttEngine(impl: SherpaOnnxSttEngine): SttEngine

    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: AndroidTtsEngine): TtsEngine

    @Binds
    @Singleton
    abstract fun bindWakeWordEngine(impl: MockWakeWordEngine): WakeWordEngine
}
