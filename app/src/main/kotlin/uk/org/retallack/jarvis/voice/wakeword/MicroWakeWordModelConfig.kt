package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Configuration for a microWakeWord model.
 *
 * This is the JSON manifest format used by ESPHome / Home Assistant microWakeWord
 * models (https://github.com/esphome/micro-wake-word-models). Only the fields we need
 * are modelled; unknown fields are ignored.
 *
 * Example (`hey_jarvis.json`):
 * ```
 * { "wake_word": "Hey Jarvis", "model": "hey_jarvis.tflite", "version": 2,
 *   "micro": { "probability_cutoff": 0.97, "feature_step_size": 10, "sliding_window_size": 5 } }
 * ```
 */
@Serializable
data class MicroWakeWordModelConfig(
    @SerialName("wake_word") val wakeWord: String,
    val author: String = "",
    val website: String = "",
    val model: String,
    @SerialName("trained_languages") val trainedLanguages: List<String> = emptyList(),
    val version: Int = 0,
    val micro: MicroFrontendConfig,
) {
    /**
     * Full asset path to the model file, resolved relative to the assets root.
     * [wakewordDir] is supplied by the caller so this class stays free of magic strings.
     */
    fun modelAssetPath(wakewordDir: String): String = "$wakewordDir/$model"

    /**
     * microWakeWord detection parameters.
     *
     * @param probabilityCutoff Detection threshold (0.0-1.0). This is the value the
     *        model was validated against, not a UI default.
     * @param featureStepSize Step size in milliseconds for feature extraction
     * @param slidingWindowSize Number of frames averaged for detection
     */
    @Serializable
    data class MicroFrontendConfig(
        @SerialName("probability_cutoff") val probabilityCutoff: Float,
        @SerialName("feature_step_size") val featureStepSize: Int,
        @SerialName("sliding_window_size") val slidingWindowSize: Int,
    )

    companion object {
        private const val TAG = "MicroWakeWordConfig"

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses a manifest from a JSON string. */
        fun parse(content: String): MicroWakeWordModelConfig = json.decodeFromString(content)

        /** Loads and parses a manifest from APK assets. */
        fun load(context: Context, assetPath: String): MicroWakeWordModelConfig {
            val content = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val config = parse(content)
            Log.i(
                TAG,
                "Loaded model '${config.wakeWord}' v${config.version} " +
                    "(cutoff=${config.micro.probabilityCutoff}, " +
                    "step=${config.micro.featureStepSize}ms, " +
                    "window=${config.micro.slidingWindowSize})",
            )
            return config
        }
    }
}
