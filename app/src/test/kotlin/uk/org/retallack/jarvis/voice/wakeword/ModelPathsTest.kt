package uk.org.retallack.jarvis.voice.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelPathsTest {

    @Test
    fun `allModels returns three model paths`() {
        val models = ModelPaths.allModels()
        assertEquals(3, models.size)
    }

    @Test
    fun `allModels contains wake word model`() {
        val models = ModelPaths.allModels()
        assertTrue(models.contains(ModelPaths.WAKE_WORD_MODEL))
    }

    @Test
    fun `allModels contains melspectrogram model`() {
        val models = ModelPaths.allModels()
        assertTrue(models.contains(ModelPaths.MELSPECTROGRAM_MODEL))
    }

    @Test
    fun `allModels contains embedding model`() {
        val models = ModelPaths.allModels()
        assertTrue(models.contains(ModelPaths.EMBEDDING_MODEL))
    }

    @Test
    fun `model paths start with models directory`() {
        ModelPaths.allModels().forEach { path ->
            assertTrue(path.startsWith("models/"), "Path $path should start with models/")
        }
    }

    @Test
    fun `model paths have tflite extension`() {
        ModelPaths.allModels().forEach { path ->
            assertTrue(path.endsWith(".tflite"), "Path $path should end with .tflite")
        }
    }

    @Test
    fun `wake word model has correct path`() {
        assertEquals("models/hey_jarvis.tflite", ModelPaths.WAKE_WORD_MODEL)
    }

    @Test
    fun `melspectrogram model has correct path`() {
        assertEquals("models/melspectrogram.tflite", ModelPaths.MELSPECTROGRAM_MODEL)
    }

    @Test
    fun `embedding model has correct path`() {
        assertEquals("models/embedding_model.tflite", ModelPaths.EMBEDDING_MODEL)
    }
}
