package uk.org.retallack.jarvis.data.db

import android.content.Context
import android.content.Intent

/**
 * Signals to the main process that the conversation history has changed.
 *
 * The wake-word service runs in a dedicated `:wakeword` process (see the manifest) so its
 * lightweight TFLite listener is not co-located with the heavy ONNX STT heap. A side
 * effect is that when it writes a chat message, the main process's Room observer is not
 * notified: Room's [androidx.room.InvalidationTracker] is per-process.
 *
 * Without this, the chat list only refreshed when the screen was recreated (i.e. when the
 * user navigated away and back).
 *
 * The main process registers a receiver for [ACTION_CONVERSATION_CHANGED] and re-reads the
 * history when it arrives.
 */
object ConversationSync {
    const val ACTION_CONVERSATION_CHANGED =
        "uk.org.retallack.jarvis.ACTION_CONVERSATION_CHANGED"

    /** Called from the `:wakeword` process after writing a message. */
    fun notifyChanged(context: Context) {
        val intent = Intent(ACTION_CONVERSATION_CHANGED).setPackage(context.packageName)
        context.applicationContext.sendBroadcast(intent)
    }
}
