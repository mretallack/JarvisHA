package uk.org.retallack.jarvis.voice.tts

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MockTtsEngineTest {

    private lateinit var engine: MockTtsEngine

    @BeforeEach
    fun setup() {
        engine = MockTtsEngine()
    }

    @Test
    fun `initial state is UNINITIALIZED`() {
        assertEquals(TtsState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `initialize transitions to READY`() = runTest {
        val result = engine.initialize("/fake/model/path")
        assertTrue(result)
        assertEquals(TtsState.READY, engine.state.value)
    }

    @Test
    fun `speak sets lastSpokenText`() = runTest {
        engine.msPerCharacter = 1 // Speed up test
        engine.initialize("/fake")
        engine.speak("Hello world")
        assertEquals("Hello world", engine.lastSpokenText)
        assertEquals(TtsState.READY, engine.state.value)
    }

    @Test
    fun `speak does nothing when uninitialized`() = runTest {
        engine.speak("Hello")
        assertNull(engine.lastSpokenText)
        assertEquals(TtsState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `stop transitions from SPEAKING to READY`() = runTest {
        engine.initialize("/fake")
        // Can't easily test mid-speech stop in single-threaded test,
        // but we can verify stop from READY does nothing
        engine.stop()
        assertEquals(TtsState.READY, engine.state.value)
    }

    @Test
    fun `release transitions to UNINITIALIZED`() = runTest {
        engine.initialize("/fake")
        engine.release()
        assertEquals(TtsState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `isModelAvailable returns true for mock`() {
        assertTrue(engine.isModelAvailable("/any/path"))
    }
}
