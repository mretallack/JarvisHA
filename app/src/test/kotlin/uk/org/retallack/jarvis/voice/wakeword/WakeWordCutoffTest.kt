package uk.org.retallack.jarvis.voice.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the microWakeWord detection-cutoff formula.
 *
 * The cutoff is used both to configure the native engine and to log it, so it must come
 * from a single source of truth - otherwise the logged value can drift from the one
 * actually applied, which makes false-positive debugging misleading.
 *
 * Slider semantics are counter-intuitive and deliberately preserved from the previous
 * engine: LOWER sensitivity = HIGHER cutoff = FEWER false positives.
 */
class WakeWordCutoffTest {

    /** `hey_jarvis` v2 manifest value. */
    private val heyJarvisCutoff = 0.97f

    @Test
    fun `default sensitivity uses the model's validated cutoff`() {
        assertEquals(0.97f, MicroWakeWordEngine.detectionCutoff(0.5f, heyJarvisCutoff), 0.0001f)
    }

    @Test
    fun `lowest sensitivity is the most strict`() {
        // +0.1, clamped to the maximum cutoff.
        assertEquals(0.99f, MicroWakeWordEngine.detectionCutoff(0f, heyJarvisCutoff), 0.0001f)
    }

    @Test
    fun `highest sensitivity is the most permissive`() {
        assertEquals(0.87f, MicroWakeWordEngine.detectionCutoff(1f, heyJarvisCutoff), 0.0001f)
    }

    @Test
    fun `cutoff never rises as sensitivity rises`() {
        var previous = Float.MAX_VALUE
        var s = 0f
        while (s <= 1f) {
            val cutoff = MicroWakeWordEngine.detectionCutoff(s, heyJarvisCutoff)
            assertTrue(cutoff <= previous) { "cutoff should not rise as sensitivity rises" }
            previous = cutoff
            s += 0.05f
        }
    }

    @Test
    fun `cutoff falls strictly while unclamped`() {
        // Above the ceiling (s >= 0.4 for a 0.97 manifest) the mapping is a strict linear
        // decrease; only the ends clamp.
        var previous = Float.MAX_VALUE
        var s = 0.5f
        while (s <= 1f) {
            val cutoff = MicroWakeWordEngine.detectionCutoff(s, heyJarvisCutoff)
            assertTrue(cutoff < previous) { "cutoff should fall strictly at sensitivity $s" }
            previous = cutoff
            s += 0.05f
        }
    }

    @Test
    fun `out-of-range sensitivity is clamped`() {
        assertEquals(
            MicroWakeWordEngine.detectionCutoff(0f, heyJarvisCutoff),
            MicroWakeWordEngine.detectionCutoff(-1f, heyJarvisCutoff),
            0.0001f,
        )
        assertEquals(
            MicroWakeWordEngine.detectionCutoff(1f, heyJarvisCutoff),
            MicroWakeWordEngine.detectionCutoff(2f, heyJarvisCutoff),
            0.0001f,
        )
    }

    @Test
    fun `cutoff stays within the allowed range for a low manifest value`() {
        // 0.5 - 0.1 = 0.4 would be below the floor, so it clamps to 0.5.
        assertEquals(0.5f, MicroWakeWordEngine.detectionCutoff(1f, 0.5f), 0.0001f)
        assertEquals(0.6f, MicroWakeWordEngine.detectionCutoff(0f, 0.5f), 0.0001f)
    }

    @Test
    fun `cutoff never leaves the 0_5 to 0_99 range`() {
        for (manifest in listOf(0.2f, 0.5f, 0.75f, 0.97f, 0.99f)) {
            var s = 0f
            while (s <= 1f) {
                val cutoff = MicroWakeWordEngine.detectionCutoff(s, manifest)
                assertTrue(cutoff in 0.5f..0.99f) { "cutoff $cutoff out of range" }
                s += 0.05f
            }
        }
    }
}
