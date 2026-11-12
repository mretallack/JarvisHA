package uk.org.retallack.jarvis.voice.wakeword

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * The "wake word paused — tap to resume" notification.
 *
 * Why this exists (Android 14+ / enforced on newer releases):
 *
 * A foreground service of type `microphone` **cannot be launched from a
 * `BOOT_COMPLETED` receiver** once the app targets API 34+, and — going back to
 * Android 11 — any microphone service started while the app is in the background is
 * denied microphone access entirely:
 *
 * ```
 * W ActivityManager: Foreground service started from background can not have
 *   location/camera/microphone access: service ...WakeWordService
 * ```
 *
 * The listener therefore cannot be armed from the boot receiver: the service starts
 * "deaf". The only context in which the microphone is actually granted is a
 * foreground one — a visible activity, a widget tap, or a notification tap (see
 * "Exemptions from restrictions on while-in-use permissions" in the Android docs).
 *
 * So instead of starting the service from the background, we post this notification.
 * Tapping it opens [uk.org.retallack.jarvis.ui.MainActivity], which arms the listener
 * from a visible-activity context where the microphone is available.
 */
object WakeWordNotifications {

    private const val TAG = "WakeWordNotifications"

    const val RESUME_CHANNEL_ID = "wake_word_resume_channel"
    const val RESUME_NOTIFICATION_ID = 1002

    /**
     * Post the "tap to resume" prompt. Safe to call repeatedly; the notification id is
     * fixed so it replaces any existing prompt rather than stacking.
     */
    fun postResumeNotification(context: Context) {
        try {
            createChannel(context)

            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: Intent(context, uk.org.retallack.jarvis.ui.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val notification = NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
                .setContentTitle("Wake word paused")
                .setContentText("Tap to resume listening for \"Hey Jarvis\".")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            context.getSystemService(NotificationManager::class.java)
                .notify(RESUME_NOTIFICATION_ID, notification)
            Log.i(TAG, "Posted 'tap to resume' notification")
        } catch (e: Exception) {
            Log.w(TAG, "Could not post resume notification", e)
        }
    }

    /** Dismiss the prompt once the listener has been armed successfully. */
    fun cancelResumeNotification(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)
                .cancel(RESUME_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel resume notification", e)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RESUME_CHANNEL_ID,
                "Wake Word Paused",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    "Prompt shown when the wake-word listener needs to be resumed"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
