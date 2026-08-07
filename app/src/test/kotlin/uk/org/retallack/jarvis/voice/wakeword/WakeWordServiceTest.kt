package uk.org.retallack.jarvis.voice.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class WakeWordServiceTest {

    @Test
    fun `NOTIFICATION_CHANNEL_ID has expected value`() {
        assertEquals("wake_word_channel", WakeWordService.NOTIFICATION_CHANNEL_ID)
    }

    @Test
    fun `NOTIFICATION_ID has expected value`() {
        assertEquals(1001, WakeWordService.NOTIFICATION_ID)
    }

    @Test
    fun `ACTION_WAKE_WORD_DETECTED has expected value`() {
        assertEquals(
            "uk.org.retallack.jarvis.WAKE_WORD_DETECTED",
            WakeWordService.ACTION_WAKE_WORD_DETECTED,
        )
    }

    @Test
    fun `EXTRA_CONFIDENCE has expected value`() {
        assertNotNull(WakeWordService.EXTRA_CONFIDENCE)
        assertEquals("confidence", WakeWordService.EXTRA_CONFIDENCE)
    }

    @Test
    fun `EXTRA_TIMESTAMP has expected value`() {
        assertNotNull(WakeWordService.EXTRA_TIMESTAMP)
        assertEquals("timestamp", WakeWordService.EXTRA_TIMESTAMP)
    }
}
