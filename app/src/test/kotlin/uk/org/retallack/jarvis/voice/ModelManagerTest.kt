package uk.org.retallack.jarvis.voice

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModelManagerTest {

    private lateinit var modelManager: ModelManager
    private lateinit var context: Context
    private lateinit var okHttpClient: OkHttpClient

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)
        every { context.filesDir } returns tempDir
        modelManager = ModelManager(context, okHttpClient)
    }

    @Test
    fun `getSttModelDir returns correct directory`() {
        val dir = modelManager.getSttModelDir()
        assertEquals(File(tempDir, "models/stt"), dir)
    }

    @Test
    fun `getTtsModelDir returns correct directory`() {
        val dir = modelManager.getTtsModelDir()
        assertEquals(File(tempDir, "models/tts"), dir)
    }

    @Test
    fun `isSttModelAvailable returns false when directory does not exist`() {
        assertFalse(modelManager.isSttModelAvailable())
    }

    @Test
    fun `isTtsModelAvailable returns false when directory does not exist`() {
        assertFalse(modelManager.isTtsModelAvailable())
    }

    @Test
    fun `isSttModelAvailable returns false when files are missing`() {
        val dir = modelManager.getSttModelDir()
        dir.mkdirs()
        // Only create one file, not all required
        File(dir, "encoder.onnx").createNewFile()
        assertFalse(modelManager.isSttModelAvailable())
    }

    @Test
    fun `isTtsModelAvailable returns false when files are missing`() {
        val dir = modelManager.getTtsModelDir()
        dir.mkdirs()
        File(dir, "model.onnx").createNewFile()
        assertFalse(modelManager.isTtsModelAvailable())
    }

    @Test
    fun `sttDownloadState initial state is not complete`() {
        val state = modelManager.sttDownloadState.value
        assertFalse(state.isComplete)
        assertEquals("STT", state.modelName)
    }

    @Test
    fun `ttsDownloadState initial state is not complete`() {
        val state = modelManager.ttsDownloadState.value
        assertFalse(state.isComplete)
        assertEquals("TTS", state.modelName)
    }

    @Test
    fun `deleteSttModel removes directory`() {
        val dir = modelManager.getSttModelDir()
        dir.mkdirs()
        File(dir, "test.onnx").createNewFile()

        modelManager.deleteSttModel()
        assertFalse(dir.exists())
    }

    @Test
    fun `deleteTtsModel removes directory`() {
        val dir = modelManager.getTtsModelDir()
        dir.mkdirs()
        File(dir, "test.onnx").createNewFile()

        modelManager.deleteTtsModel()
        assertFalse(dir.exists())
    }

    @Test
    fun `downloadProgress fraction is zero when totalBytes is unknown`() {
        val progress = ModelManager.DownloadProgress(
            modelName = "test",
            totalBytes = -1,
            bytesDownloaded = 100,
        )
        assertEquals(0f, progress.progressFraction)
    }

    @Test
    fun `downloadProgress fraction is calculated correctly`() {
        val progress = ModelManager.DownloadProgress(
            modelName = "test",
            totalBytes = 1000,
            bytesDownloaded = 500,
        )
        assertEquals(0.5f, progress.progressFraction)
    }

    @Test
    fun `downloadSttModel returns non-null flow`() {
        val flow = modelManager.downloadSttModel()
        assertNotNull(flow)
    }

    @Test
    fun `downloadTtsModel returns non-null flow`() {
        val flow = modelManager.downloadTtsModel()
        assertNotNull(flow)
    }
}
