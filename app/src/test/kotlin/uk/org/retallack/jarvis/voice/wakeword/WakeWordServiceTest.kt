package uk.org.retallack.jarvis.voice.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `ACTION_STOP has expected value`() {
        assertEquals(
            "uk.org.retallack.jarvis.STOP_WAKE_WORD",
            WakeWordService.ACTION_STOP,
        )
    }

    @Test
    fun `ACTION_WAKE_WORD has expected value`() {
        assertEquals(
            "uk.org.retallack.jarvis.ACTION_WAKE_WORD",
            WakeWordService.ACTION_WAKE_WORD,
        )
    }

    @Test
    fun `isRunning returns false when service not started`() {
        assertFalse(WakeWordService.isRunning())
    }
}
