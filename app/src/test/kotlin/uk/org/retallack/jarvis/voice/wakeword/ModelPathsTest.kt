package uk.org.retallack.jarvis.voice.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelPathsTest {

    @Test
    fun `allModels returns the single microWakeWord model`() {
        assertEquals(1, ModelPaths.allModels().size)
    }

    @Test
    fun `allModels contains the wake word model`() {
        assertTrue(ModelPaths.allModels().contains(ModelPaths.WAKE_WORD_MODEL))
    }

    @Test
    fun `allAssets contains model and manifest`() {
        val assets = ModelPaths.allAssets()
        assertTrue(assets.contains(ModelPaths.WAKE_WORD_MODEL))
        assertTrue(assets.contains(ModelPaths.WAKE_WORD_MANIFEST))
        assertEquals(2, assets.size)
    }

    @Test
    fun `assets live in the wakeword directory`() {
        ModelPaths.allAssets().forEach { path ->
            assertTrue(path.startsWith("wakeword/"), "Path $path should start with wakeword/")
        }
    }

    @Test
    fun `model paths have tflite extension`() {
        ModelPaths.allModels().forEach { path ->
            assertTrue(path.endsWith(".tflite"), "Path $path should end with .tflite")
        }
    }

    @Test
    fun `wake word model has expected path`() {
        assertEquals("wakeword/hey_jarvis.tflite", ModelPaths.WAKE_WORD_MODEL)
    }

    @Test
    fun `wake word manifest has expected path`() {
        assertEquals("wakeword/hey_jarvis.json", ModelPaths.WAKE_WORD_MANIFEST)
    }

    @Test
    fun `wakeword asset dir matches model paths`() {
        assertTrue(ModelPaths.WAKE_WORD_MODEL.startsWith("${ModelPaths.WAKEWORD_ASSET_DIR}/"))
        assertTrue(ModelPaths.WAKE_WORD_MANIFEST.startsWith("${ModelPaths.WAKEWORD_ASSET_DIR}/"))
    }
}
