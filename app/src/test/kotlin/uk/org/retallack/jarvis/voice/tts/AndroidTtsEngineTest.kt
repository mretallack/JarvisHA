package uk.org.retallack.jarvis.voice.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AndroidTtsEngineTest {

    private lateinit var context: Context
    private lateinit var engine: AndroidTtsEngine

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        engine = AndroidTtsEngine(context)
    }

    @Test
    fun `initial state is UNINITIALIZED`() {
        assertEquals(TtsState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `isModelAvailable returns false when not initialized`() {
        assertFalse(engine.isModelAvailable("/any/path"))
    }

    @Test
    fun `default shouldSpeak is true`() {
        assertTrue(engine.shouldSpeak)
    }

    @Test
    fun `default speechRate is 1_0`() {
        assertEquals(1.0f, engine.speechRate)
    }

    @Test
    fun `default pitch is 1_0`() {
        assertEquals(1.0f, engine.pitch)
    }

    @Test
    fun `speak does nothing when uninitialized`() = runTest {
        engine.speak("Hello")
        assertEquals(TtsState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `speak does nothing when shouldSpeak is false`() = runTest {
        engine.shouldSpeak = false
        // Even if somehow state was ready, speak should be a no-op
        engine.speak("Hello")
        assertEquals(TtsState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `stop from non-speaking state does nothing`() = runTest {
        engine.stop()
        assertEquals(TtsState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `release from uninitialized state stays UNINITIALIZED`() = runTest {
        engine.release()
        assertEquals(TtsState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `speechRate can be set`() {
        engine.speechRate = 1.5f
        assertEquals(1.5f, engine.speechRate)
    }

    @Test
    fun `pitch can be set`() {
        engine.pitch = 0.8f
        assertEquals(0.8f, engine.pitch)
    }

    @Test
    fun `shouldSpeak can be toggled`() {
        engine.shouldSpeak = false
        assertFalse(engine.shouldSpeak)
        engine.shouldSpeak = true
        assertTrue(engine.shouldSpeak)
    }
}
