package uk.org.retallack.jarvis.voice.stt

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.ActivityResultLauncher

/**
 * Helper to launch speech recognition via Intent (Activity-based).
 * This approach always works regardless of SpeechRecognizer binding permission issues
 * on Android 13+/14+/16+.
 *
 * Usage:
 * 1. Register the launcher in your Activity/Composable using rememberLauncherForActivityResult
 * 2. Call createRecognitionIntent() to get the intent
 * 3. Launch it and handle the result
 */
object SttIntentHelper {

    /**
     * Create an intent to launch speech recognition.
     * This uses ACTION_RECOGNIZE_SPEECH which launches the recognizer UI.
     */
    fun createRecognitionIntent(language: String = "en-GB"): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command...")
        }
    }

    /**
     * Extract the recognized text from the activity result.
     * @return The best match text, or null if recognition failed/cancelled.
     */
    fun extractResult(resultCode: Int, data: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return matches?.firstOrNull()
    }
}
