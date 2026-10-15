package uk.org.retallack.jarvis.voice.wakeword

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the wake-word detection threshold formula.
 *
 * The threshold is used both to gate detections and to log the value, so it must come
 * from a single source of truth - previously the logged threshold used a different
 * formula from the one actually applied, which made false-positive debugging misleading.
 */
class WakeWordThresholdTest {

    @Test
    fun `default sensitivity maps to expected threshold`() {
        assertEquals(0.725f, LiteRtWakeWordEngine.detectionThreshold(0.5f), 0.0001f)
    }

    @Test
    fun `lowest sensitivity is most strict`() {
        assertEquals(0.95f, LiteRtWakeWordEngine.detectionThreshold(0f), 0.0001f)
    }

    @Test
    fun `highest sensitivity is most permissive`() {
        assertEquals(0.5f, LiteRtWakeWordEngine.detectionThreshold(1f), 0.0001f)
    }

    @Test
    fun `threshold decreases monotonically as sensitivity rises`() {
        var previous = Float.MAX_VALUE
        var s = 0f
        while (s <= 1f) {
            val threshold = LiteRtWakeWordEngine.detectionThreshold(s)
            assert(threshold < previous) { "threshold should fall as sensitivity rises" }
            previous = threshold
            s += 0.1f
        }
    }

    @Test
    fun `out-of-range sensitivity is clamped`() {
        assertEquals(
            LiteRtWakeWordEngine.detectionThreshold(0f),
            LiteRtWakeWordEngine.detectionThreshold(-1f),
            0.0001f,
        )
        assertEquals(
            LiteRtWakeWordEngine.detectionThreshold(1f),
            LiteRtWakeWordEngine.detectionThreshold(2f),
            0.0001f,
        )
    }
}
