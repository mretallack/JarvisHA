package uk.org.retallack.jarvis.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AudioPlayerStateTest {

    @Test
    fun `AudioPlayerState has expected values`() {
        val states = AudioPlayerState.entries
        assertEquals(3, states.size)
    }

    @Test
    fun `AudioPlayerState IDLE exists`() {
        assertNotNull(AudioPlayerState.IDLE)
    }

    @Test
    fun `AudioPlayerState PLAYING exists`() {
        assertNotNull(AudioPlayerState.PLAYING)
    }

    @Test
    fun `AudioPlayerState STOPPED exists`() {
        assertNotNull(AudioPlayerState.STOPPED)
    }
}
