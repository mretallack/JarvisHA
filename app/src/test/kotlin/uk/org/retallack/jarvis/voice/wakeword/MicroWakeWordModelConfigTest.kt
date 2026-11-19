package uk.org.retallack.jarvis.voice.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards parsing of the ESPHome / Home Assistant microWakeWord manifest format.
 *
 * The manifest is the source of the detection parameters, so a silent parsing failure
 * would change detection behaviour without any visible error.
 */
class MicroWakeWordModelConfigTest {

    /** The exact manifest shipped in assets/wakeword/hey_jarvis.json. */
    private val heyJarvisJson = """
        {
          "type": "micro",
          "wake_word": "Hey Jarvis",
          "author": "Kevin Ahrendt",
          "website": "https://www.kevinahrendt.com/",
          "model": "hey_jarvis.tflite",
          "trained_languages": ["en"],
          "version": 2,
          "micro": {
            "probability_cutoff": 0.97,
            "feature_step_size": 10,
            "sliding_window_size": 5,
            "tensor_arena_size": 22860,
            "minimum_esphome_version": "2024.7.0"
          }
        }
    """.trimIndent()

    @Test
    fun `parses the wake word name`() {
        assertEquals("Hey Jarvis", MicroWakeWordModelConfig.parse(heyJarvisJson).wakeWord)
    }

    @Test
    fun `parses the model file name`() {
        assertEquals("hey_jarvis.tflite", MicroWakeWordModelConfig.parse(heyJarvisJson).model)
    }

    @Test
    fun `parses the version`() {
        assertEquals(2, MicroWakeWordModelConfig.parse(heyJarvisJson).version)
    }

    @Test
    fun `parses the detection parameters`() {
        val micro = MicroWakeWordModelConfig.parse(heyJarvisJson).micro
        assertEquals(0.97f, micro.probabilityCutoff, 0.0001f)
        assertEquals(10, micro.featureStepSize)
        assertEquals(5, micro.slidingWindowSize)
    }

    @Test
    fun `parses trained languages`() {
        assertEquals(listOf("en"), MicroWakeWordModelConfig.parse(heyJarvisJson).trainedLanguages)
    }

    @Test
    fun `resolves the model asset path`() {
        val config = MicroWakeWordModelConfig.parse(heyJarvisJson)
        assertEquals(
            "wakeword/hey_jarvis.tflite",
            config.modelAssetPath(ModelPaths.WAKEWORD_ASSET_DIR),
        )
    }

    @Test
    fun `ignores unknown fields`() {
        // tensor_arena_size / minimum_esphome_version are present but not modelled.
        val config = MicroWakeWordModelConfig.parse(heyJarvisJson)
        assertEquals("Kevin Ahrendt", config.author)
    }
}
