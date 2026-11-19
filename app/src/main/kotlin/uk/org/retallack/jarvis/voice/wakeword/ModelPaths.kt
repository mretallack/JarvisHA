package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context

/**
 * Provides paths to bundled microWakeWord assets in the APK.
 *
 * microWakeWord ships a single streaming TFLite model plus a JSON manifest (the same
 * format ESPHome / Home Assistant use). The manifest carries the detection parameters
 * (`probability_cutoff`, `feature_step_size`, `sliding_window_size`).
 *
 * The previous OpenWakeWord setup needed three models (melspectrogram, embedding,
 * wake word) because its feature extraction was itself a pair of TFLite models.
 * microWakeWord does feature extraction in native code, so only the classifier is
 * bundled. See docs/microwakeword-migration-plan.md.
 */
object ModelPaths {

    /** Assets directory holding the microWakeWord model and manifest. */
    const val WAKEWORD_ASSET_DIR = "wakeword"

    /** The microWakeWord streaming classifier model. */
    const val WAKE_WORD_MODEL = "$WAKEWORD_ASSET_DIR/hey_jarvis.tflite"

    /** The model's manifest (detection configuration). */
    const val WAKE_WORD_MANIFEST = "$WAKEWORD_ASSET_DIR/hey_jarvis.json"

    /** Model files (`.tflite`) required for wake word detection. */
    fun allModels(): List<String> = listOf(WAKE_WORD_MODEL)

    /** All bundled wake-word assets, model and manifest. */
    fun allAssets(): List<String> = listOf(WAKE_WORD_MODEL, WAKE_WORD_MANIFEST)

    /**
     * Verifies that all required wake-word assets exist in the APK.
     * @param context Application context for asset access
     * @return true if all assets are accessible
     */
    fun verifyModelsExist(context: Context): Boolean {
        return try {
            allAssets().all { path ->
                context.assets.open(path).use { it.available() > 0 }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Loads an asset file into a ByteArray.
     * @param context Application context
     * @param path Relative path within assets (e.g. "wakeword/hey_jarvis.tflite")
     */
    fun loadModelFromAssets(context: Context, path: String): ByteArray {
        return context.assets.open(path).use { it.readBytes() }
    }
}
