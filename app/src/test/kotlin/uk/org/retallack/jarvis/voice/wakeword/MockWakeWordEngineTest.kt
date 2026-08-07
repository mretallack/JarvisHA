package uk.org.retallack.jarvis.voice.wakeword

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class MockWakeWordEngineTest {

    private lateinit var engine: MockWakeWordEngine

    @BeforeEach
    fun setup() {
        engine = MockWakeWordEngine()
    }

    @Test
    fun `initial state is UNINITIALIZED`() {
        assertEquals(WakeWordState.UNINITIALIZED, engine.state.value)
    }

    @Test
    fun `initialize transitions to READY`() = runTest {
        assertTrue(engine.initialize())
        assertEquals(WakeWordState.READY, engine.state.value)
    }

    @Test
    fun `startListening transitions to LISTENING`() = runTest {
        engine.initialize()
        engine.startListening()
        assertEquals(WakeWordState.LISTENING, engine.state.value)
    }

    @Test
    fun `startListening in quiet hours transitions to PAUSED`() = runTest {
        engine.initialize()
        // Set quiet hours that cover now
        val now = LocalTime.now()
        engine.setQuietHours(
            QuietHoursConfig(
                enabled = true,
                startTime = now.minusMinutes(5),
                endTime = now.plusMinutes(5),
            ),
        )
        engine.startListening()
        assertEquals(WakeWordState.PAUSED, engine.state.value)
    }

    @Test
    fun `quiet hours disabled returns false`() {
        engine.setQuietHours(QuietHoursConfig(enabled = false))
        assertFalse(engine.isInQuietHours())
    }

    @Test
    fun `quiet hours enabled but outside range returns false`() {
        val now = LocalTime.now()
        engine.setQuietHours(
            QuietHoursConfig(
                enabled = true,
                startTime = now.plusHours(2),
                endTime = now.plusHours(4),
            ),
        )
        assertFalse(engine.isInQuietHours())
    }

    @Test
    fun `quiet hours enabled and within range returns true`() {
        val now = LocalTime.now()
        engine.setQuietHours(
            QuietHoursConfig(
                enabled = true,
                startTime = now.minusMinutes(10),
                endTime = now.plusMinutes(10),
            ),
        )
        assertTrue(engine.isInQuietHours())
    }

    @Test
    fun `quiet hours overnight crossing midnight works`() {
        // Test overnight quiet hours (e.g., 22:00 - 07:00)
        val now = LocalTime.now()
        // Set a range that wraps around midnight and includes current time
        engine.setQuietHours(
            QuietHoursConfig(
                enabled = true,
                startTime = now.minusMinutes(30),
                endTime = now.minusMinutes(60), // wraps = covers almost entire day
            ),
        )
        assertTrue(engine.isInQuietHours())
    }

    @Test
    fun `stopListening transitions to READY`() = runTest {
        engine.initialize()
        engine.startListening()
        engine.stopListening()
        assertEquals(WakeWordState.READY, engine.state.value)
    }

    @Test
    fun `simulateDetection emits detection event`() = runTest {
        engine.initialize()
        engine.startListening()

        engine.detections.test {
            engine.simulateDetection(0.95f)
            val detection = awaitItem()
            assertEquals(0.95f, detection.confidence)
            assertTrue(detection.timestamp > 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `release transitions to UNINITIALIZED`() = runTest {
        engine.initialize()
        engine.release()
        assertEquals(WakeWordState.UNINITIALIZED, engine.state.value)
    }
}
