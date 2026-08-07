package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context

/**
 * Provides paths to bundled wake word TFLite models in APK assets.
 * Models are stored under assets/models/ and accessed via AssetManager.
 */
object ModelPaths {

    private const val MODELS_DIR = "models"

    /** The main wake word detection model. */
    const val WAKE_WORD_MODEL = "$MODELS_DIR/hey_jarvis.tflite"

    /** Mel spectrogram feature extraction model. */
    const val MELSPECTROGRAM_MODEL = "$MODELS_DIR/melspectrogram.tflite"

    /** Audio embedding model for feature extraction. */
    const val EMBEDDING_MODEL = "$MODELS_DIR/embedding_model.tflite"

    /**
     * Returns all model file paths required for wake word detection.
     */
    fun allModels(): List<String> = listOf(
        WAKE_WORD_MODEL,
        MELSPECTROGRAM_MODEL,
        EMBEDDING_MODEL,
    )

    /**
     * Verifies that all required model files exist in assets.
     * @param context Application context for asset access
     * @return true if all models are accessible in assets
     */
    fun verifyModelsExist(context: Context): Boolean {
        return try {
            allModels().all { modelPath ->
                context.assets.open(modelPath).use { it.available() > 0 }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Loads a model file from assets into a ByteArray.
     * @param context Application context
     * @param modelPath Relative path within assets (e.g. "models/hey_jarvis.tflite")
     * @return ByteArray containing the model data
     */
    fun loadModelFromAssets(context: Context, modelPath: String): ByteArray {
        return context.assets.open(modelPath).use { it.readBytes() }
    }
}
