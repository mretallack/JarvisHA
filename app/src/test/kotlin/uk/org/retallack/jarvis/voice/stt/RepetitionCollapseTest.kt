package uk.org.retallack.jarvis.voice.stt

import org.junit.Assert.assertEquals
import org.junit.Test

class RepetitionCollapseTest {

    @Test
    fun `collapses whole-phrase repetition loop`() {
        val repeated = List(31) { "turn kitchen radio on" }.joinToString(" ")
        assertEquals(
            "turn kitchen radio on",
            SherpaOnnxSttEngine.collapseRepetitions(repeated),
        )
    }

    @Test
    fun `collapses two-word repetition loop`() {
        val repeated = List(10) { "hello world" }.joinToString(" ")
        assertEquals("hello world", SherpaOnnxSttEngine.collapseRepetitions(repeated))
    }

    @Test
    fun `leaves normal sentence untouched`() {
        val text = "turn on the kitchen radio please"
        assertEquals(text, SherpaOnnxSttEngine.collapseRepetitions(text))
    }

    @Test
    fun `leaves short text untouched`() {
        assertEquals("yes", SherpaOnnxSttEngine.collapseRepetitions("yes"))
        assertEquals("turn it on", SherpaOnnxSttEngine.collapseRepetitions("turn it on"))
    }

    @Test
    fun `handles blank input`() {
        assertEquals("", SherpaOnnxSttEngine.collapseRepetitions(""))
        assertEquals("   ", SherpaOnnxSttEngine.collapseRepetitions("   "))
    }

    @Test
    fun `does not collapse a sentence with only an accidental double word`() {
        val text = "turn the the radio on"
        // Only two repeats - below the confidence threshold for phrase looping.
        assertEquals("turn the radio on", SherpaOnnxSttEngine.collapseRepetitions(text))
    }

    @Test
    fun `preserves genuine distinct phrases`() {
        val text = "turn on the kitchen radio and play music"
        assertEquals(text, SherpaOnnxSttEngine.collapseRepetitions(text))
    }
}
