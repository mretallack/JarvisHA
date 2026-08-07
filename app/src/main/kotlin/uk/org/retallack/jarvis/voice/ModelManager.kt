package uk.org.retallack.jarvis.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages STT and TTS model downloads.
 * Downloads models from upstream repositories (HuggingFace / GitHub)
 * to app internal storage for offline use.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    companion object {
        /** Directory within app internal storage for models. */
        private const val MODELS_DIR = "models"

        // STT Model (Sherpa-ONNX streaming Zipformer 20M, English)
        // These are individual model files from the sherpa-onnx-streaming-zipformer-en-20M-2023-02-17 model
        private const val STT_MODEL_BASE =
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main"
        private const val STT_ENCODER_URL = "$STT_MODEL_BASE/encoder-epoch-99-avg-1.onnx"
        private const val STT_DECODER_URL = "$STT_MODEL_BASE/decoder-epoch-99-avg-1.onnx"
        private const val STT_JOINER_URL = "$STT_MODEL_BASE/joiner-epoch-99-avg-1.onnx"
        private const val STT_TOKENS_URL = "$STT_MODEL_BASE/tokens.txt"

        // TTS Model (Piper via Sherpa-ONNX)
        // Placeholder URLs — update when selecting final voice
        private const val TTS_MODEL_URL =
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx"
        private const val TTS_MODEL_DATA_URL =
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx.json"
        private const val TTS_TOKENS_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-vits-piper-en_US-amy-medium/resolve/main/tokens.txt"
        private const val TTS_ESPEAK_DATA_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-vits-piper-en_US-amy-medium/resolve/main/espeak-ng-data.tar.bz2"
    }

    /**
     * Download progress state for a model set.
     */
    data class DownloadProgress(
        val modelName: String,
        val currentFile: String = "",
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = -1,
        val filesCompleted: Int = 0,
        val totalFiles: Int = 0,
        val isComplete: Boolean = false,
        val error: String? = null,
    ) {
        val progressFraction: Float
            get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    }

    /** STT model files to download. */
    private val sttModelFiles = listOf(
        ModelFile("encoder.onnx", STT_ENCODER_URL),
        ModelFile("decoder.onnx", STT_DECODER_URL),
        ModelFile("joiner.onnx", STT_JOINER_URL),
        ModelFile("tokens.txt", STT_TOKENS_URL),
    )

    /** TTS model files to download. */
    private val ttsModelFiles = listOf(
        ModelFile("model.onnx", TTS_MODEL_URL),
        ModelFile("model.onnx.json", TTS_MODEL_DATA_URL),
        ModelFile("tokens.txt", TTS_TOKENS_URL),
        ModelFile("espeak-ng-data.tar.bz2", TTS_ESPEAK_DATA_URL),
    )

    private val _sttDownloadState = MutableStateFlow(
        DownloadProgress(modelName = "STT"),
    )
    val sttDownloadState: StateFlow<DownloadProgress> = _sttDownloadState

    private val _ttsDownloadState = MutableStateFlow(
        DownloadProgress(modelName = "TTS"),
    )
    val ttsDownloadState: StateFlow<DownloadProgress> = _ttsDownloadState

    /** Returns the directory where STT models are stored. */
    fun getSttModelDir(): File {
        return File(context.filesDir, "$MODELS_DIR/stt")
    }

    /** Returns the directory where TTS models are stored. */
    fun getTtsModelDir(): File {
        return File(context.filesDir, "$MODELS_DIR/tts")
    }

    /** Check if STT models are already downloaded. */
    fun isSttModelAvailable(): Boolean {
        val dir = getSttModelDir()
        return dir.exists() && sttModelFiles.all { File(dir, it.filename).exists() }
    }

    /** Check if TTS models are already downloaded. */
    fun isTtsModelAvailable(): Boolean {
        val dir = getTtsModelDir()
        return dir.exists() && ttsModelFiles.all { File(dir, it.filename).exists() }
    }

    /**
     * Download STT model files with progress reporting.
     * @return Flow of download progress updates
     */
    fun downloadSttModel(): Flow<DownloadProgress> = flow {
        val dir = getSttModelDir()
        dir.mkdirs()

        val progress = DownloadProgress(
            modelName = "STT",
            totalFiles = sttModelFiles.size,
        )
        emit(progress)

        var completed = 0
        for (modelFile in sttModelFiles) {
            val current = progress.copy(
                currentFile = modelFile.filename,
                filesCompleted = completed,
                bytesDownloaded = 0,
                totalBytes = -1,
            )
            emit(current)

            try {
                downloadFile(
                    url = modelFile.url,
                    destination = File(dir, modelFile.filename),
                ) { downloaded, total ->
                    val updated = current.copy(
                        bytesDownloaded = downloaded,
                        totalBytes = total,
                    )
                    _sttDownloadState.value = updated
                }
                completed++
            } catch (e: IOException) {
                val error = progress.copy(
                    error = "Failed to download ${modelFile.filename}: ${e.message}",
                )
                _sttDownloadState.value = error
                emit(error)
                return@flow
            }
        }

        val complete = progress.copy(
            filesCompleted = completed,
            totalFiles = sttModelFiles.size,
            isComplete = true,
        )
        _sttDownloadState.value = complete
        emit(complete)
    }.flowOn(Dispatchers.IO)

    /**
     * Download TTS model files with progress reporting.
     * @return Flow of download progress updates
     */
    fun downloadTtsModel(): Flow<DownloadProgress> = flow {
        val dir = getTtsModelDir()
        dir.mkdirs()

        val progress = DownloadProgress(
            modelName = "TTS",
            totalFiles = ttsModelFiles.size,
        )
        emit(progress)

        var completed = 0
        for (modelFile in ttsModelFiles) {
            val current = progress.copy(
                currentFile = modelFile.filename,
                filesCompleted = completed,
                bytesDownloaded = 0,
                totalBytes = -1,
            )
            emit(current)

            try {
                downloadFile(
                    url = modelFile.url,
                    destination = File(dir, modelFile.filename),
                ) { downloaded, total ->
                    val updated = current.copy(
                        bytesDownloaded = downloaded,
                        totalBytes = total,
                    )
                    _ttsDownloadState.value = updated
                }
                completed++
            } catch (e: IOException) {
                val error = progress.copy(
                    error = "Failed to download ${modelFile.filename}: ${e.message}",
                )
                _ttsDownloadState.value = error
                emit(error)
                return@flow
            }
        }

        val complete = progress.copy(
            filesCompleted = completed,
            totalFiles = ttsModelFiles.size,
            isComplete = true,
        )
        _ttsDownloadState.value = complete
        emit(complete)
    }.flowOn(Dispatchers.IO)

    /**
     * Delete downloaded STT models (free space).
     */
    fun deleteSttModel() {
        getSttModelDir().deleteRecursively()
        _sttDownloadState.value = DownloadProgress(modelName = "STT")
    }

    /**
     * Delete downloaded TTS models (free space).
     */
    fun deleteTtsModel() {
        getTtsModelDir().deleteRecursively()
        _ttsDownloadState.value = DownloadProgress(modelName = "TTS")
    }

    private fun downloadFile(
        url: String,
        destination: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val totalBytes = body.contentLength()
        var downloaded = 0L

        body.byteStream().use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    onProgress(downloaded, totalBytes)
                }
            }
        }
    }

    private data class ModelFile(
        val filename: String,
        val url: String,
    )
}
