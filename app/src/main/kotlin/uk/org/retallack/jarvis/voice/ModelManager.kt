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

        // STT Model (Sherpa-ONNX Whisper tiny.en - offline, good accuracy, fits in memory)
        private const val STT_MODEL_BASE =
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main"
        private const val STT_ENCODER_URL = "$STT_MODEL_BASE/tiny.en-encoder.int8.onnx"
        private const val STT_DECODER_URL = "$STT_MODEL_BASE/tiny.en-decoder.int8.onnx"
        private const val STT_TOKENS_URL = "$STT_MODEL_BASE/tiny.en-tokens.txt"

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
                android.util.Log.d("ModelManager", "Starting download: ${modelFile.filename} from ${modelFile.url}")
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
                android.util.Log.d("ModelManager", "Finished download: ${modelFile.filename} (${File(dir, modelFile.filename).length() / 1024}KB)")
                completed++
            } catch (e: IOException) {
                android.util.Log.e("ModelManager", "Failed to download ${modelFile.filename}", e)
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
                android.util.Log.d("ModelManager", "Starting download: ${modelFile.filename} from ${modelFile.url}")
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
                android.util.Log.d("ModelManager", "Finished download: ${modelFile.filename} (${File(dir, modelFile.filename).length() / 1024}KB)")
                completed++
            } catch (e: IOException) {
                android.util.Log.e("ModelManager", "Failed to download ${modelFile.filename}", e)
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
        // Use a separate client with longer timeouts for model downloads
        val downloadClient = okHttpClient.newBuilder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val maxRetries = 3
        var lastException: IOException? = null

        for (attempt in 1..maxRetries) {
            try {
                val existingBytes = if (destination.exists()) destination.length() else 0L

                val requestBuilder = Request.Builder().url(url)
                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                    android.util.Log.d("ModelManager", "Resuming download from byte $existingBytes")
                }

                val response = downloadClient.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful && response.code != 206) {
                    // If range not supported and we have partial file, start fresh
                    if (response.code == 416 || (existingBytes > 0 && response.code == 200)) {
                        response.close()
                        destination.delete()
                        // Retry without range header
                        val freshResponse = downloadClient.newCall(
                            Request.Builder().url(url).build()
                        ).execute()
                        if (!freshResponse.isSuccessful) {
                            throw IOException("HTTP ${freshResponse.code}: ${freshResponse.message}")
                        }
                        writeResponseToFile(freshResponse, destination, 0L, onProgress)
                    } else {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                } else {
                    val append = response.code == 206
                    writeResponseToFile(response, destination, if (append) existingBytes else 0L, onProgress)
                }

                // Success - log final size
                android.util.Log.d(
                    "ModelManager",
                    "Download complete: ${destination.name} (${destination.length() / 1024}KB)"
                )
                return
            } catch (e: IOException) {
                lastException = e
                android.util.Log.w(
                    "ModelManager",
                    "Download attempt $attempt/$maxRetries failed for ${destination.name}: ${e.message}"
                )
                if (attempt < maxRetries) {
                    val backoffMs = (1L shl attempt) * 1000L // 2s, 4s
                    Thread.sleep(backoffMs)
                }
            }
        }

        throw lastException ?: IOException("Download failed after $maxRetries attempts")
    }

    private fun writeResponseToFile(
        response: okhttp3.Response,
        destination: File,
        existingBytes: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val body = response.body ?: throw IOException("Empty response body")
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) contentLength + existingBytes else -1L
        var downloaded = existingBytes
        var lastLoggedPercent = -1

        body.byteStream().use { input ->
            val outputStream = if (existingBytes > 0) {
                java.io.FileOutputStream(destination, true)
            } else {
                destination.outputStream()
            }
            outputStream.use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    onProgress(downloaded, totalBytes)

                    // Log progress every 10%
                    if (totalBytes > 0) {
                        val percent = ((downloaded * 100) / totalBytes).toInt()
                        val bucket = (percent / 10) * 10
                        if (bucket > lastLoggedPercent) {
                            lastLoggedPercent = bucket
                            android.util.Log.d(
                                "ModelManager",
                                "Download ${destination.name}: $bucket% ($downloaded/$totalBytes bytes)"
                            )
                        }
                    }
                }
            }
        }
    }

    private data class ModelFile(
        val filename: String,
        val url: String,
    )
}
