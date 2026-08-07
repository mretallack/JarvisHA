package uk.org.retallack.jarvis.voice.stt

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MockSttEngineTest {

    private lateinit var engine: MockSttEngine

    @BeforeEach
    fun setup() {
        engine = MockSttEngine()
    }

    @Test
    fun `initial state is UNINITIALIZED`() {
        assertEquals(SttState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `initialize transitions to READY`() = runTest {
        val result = engine.initialize("/fake/model/path")
        assertTrue(result)
        assertEquals(SttState.READY, engine.state.value)
    }

    @Test
    fun `startListening transitions to LISTENING after init`() = runTest {
        engine.initialize("/fake")
        engine.startListening()
        assertEquals(SttState.LISTENING, engine.state.value)
    }

    @Test
    fun `startListening without init goes to ERROR`() = runTest {
        engine.startListening()
        assertEquals(SttState.ERROR, engine.state.value)
    }

    @Test
    fun `stopListening produces final result`() = runTest {
        engine.mockTranscription = "hello world"
        engine.mockProcessingDelayMs = 10
        engine.initialize("/fake")
        engine.startListening()

        engine.results.test {
            engine.stopListening()
            val result = awaitItem()
            assertTrue(result.isFinal)
            assertEquals("hello world", result.text)
        }

        assertEquals(SttState.READY, engine.state.value)
    }

    @Test
    fun `processAudio emits partial results after threshold`() = runTest {
        engine.mockTranscription = "turn on the living room light"
        engine.mockProcessingDelayMs = 10
        engine.initialize("/fake")
        engine.startListening()

        engine.results.test {
            // Feed 2 seconds of audio (32000 samples at 16kHz)
            engine.processAudio(ShortArray(20000))

            val partial = awaitItem()
            assertEquals(false, partial.isFinal)
            assertTrue(partial.text.isNotEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `processAudio emits final result after VAD threshold`() = runTest {
        engine.mockTranscription = "test utterance"
        engine.mockProcessingDelayMs = 10
        engine.initialize("/fake")
        engine.startListening()

        engine.results.test {
            // Feed enough audio to trigger VAD end-of-speech (>48000 samples)
            engine.processAudio(ShortArray(50000))

            // Should get partial then final
            val firstResult = awaitItem()
            // The final result
            if (!firstResult.isFinal) {
                val finalResult = awaitItem()
                assertTrue(finalResult.isFinal)
                assertEquals("test utterance", finalResult.text)
            } else {
                assertEquals("test utterance", firstResult.text)
            }

            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(SttState.READY, engine.state.value)
    }

    @Test
    fun `release transitions to UNINITIALIZED`() = runTest {
        engine.initialize("/fake")
        engine.release()
        assertEquals(SttState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `isModelAvailable returns true for mock`() {
        assertTrue(engine.isModelAvailable("/any/path"))
    }
}
