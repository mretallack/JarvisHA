package uk.org.retallack.jarvis.voice.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class WakeWordNotificationsTest {

    @Test
    fun `resume channel id has expected value`() {
        assertEquals("wake_word_resume_channel", WakeWordNotifications.RESUME_CHANNEL_ID)
    }

    @Test
    fun `resume notification id has expected value`() {
        assertEquals(1002, WakeWordNotifications.RESUME_NOTIFICATION_ID)
    }

    @Test
    fun `resume notification id differs from the listening notification id`() {
        // The listening notification is ongoing; the resume prompt is dismissable. They
        // must not share an id or the resume prompt would replace the listener's UI.
        assertNotEquals(
            WakeWordService.NOTIFICATION_ID,
            WakeWordNotifications.RESUME_NOTIFICATION_ID,
        )
    }
}
