package uk.org.retallack.jarvis.voice

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.org.retallack.jarvis.voice.stt.MockSttEngine
import uk.org.retallack.jarvis.voice.stt.SttEngine
import uk.org.retallack.jarvis.voice.tts.MockTtsEngine
import uk.org.retallack.jarvis.voice.tts.TtsEngine
import uk.org.retallack.jarvis.voice.wakeword.MockWakeWordEngine
import uk.org.retallack.jarvis.voice.wakeword.WakeWordEngine
import javax.inject.Singleton

/**
 * DI module that provides voice pipeline bindings.
 * Uses mock implementations during development.
 * Swap to real implementations (SherpaOnnxSttEngine, etc.) when device testing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindSttEngine(impl: MockSttEngine): SttEngine

    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: MockTtsEngine): TtsEngine

    @Binds
    @Singleton
    abstract fun bindWakeWordEngine(impl: MockWakeWordEngine): WakeWordEngine
}
