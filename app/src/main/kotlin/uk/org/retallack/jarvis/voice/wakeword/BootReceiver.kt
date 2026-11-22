package uk.org.retallack.jarvis.voice.wakeword

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms the wake-word listener after a reboot or app update.
 *
 * **We deliberately do not start [WakeWordService] here.** On Android 14+ a
 * `microphone`-type foreground service cannot be launched from a `BOOT_COMPLETED`
 * receiver, and any microphone service started while the app is in the background is
 * denied microphone access. Starting the service here produced a listener that ran but
 * could not hear anything:
 *
 * ```
 * W ActivityManager: Foreground service started from background can not have
 *   location/camera/microphone access: service ...WakeWordService
 * ```
 *
 * Instead we post a "tap to resume" notification. Tapping it opens the app, which arms
 * the listener from a visible-activity context — the only context in which the
 * microphone is actually granted. See [WakeWordNotifications].
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PrefsEntryPoint {
        fun wakeWordPreferences(): WakeWordPreferences
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        Log.i(TAG, "Boot/package-replaced received ($action); prompting user to resume listener")

        // Broadcast receivers must not block, so hand off to a one-shot scope.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = EntryPointAccessors
                    .fromApplication(context.applicationContext, PrefsEntryPoint::class.java)
                    .wakeWordPreferences()
                if (prefs.getEnabled()) {
                    // Cannot start the microphone service from here (see class doc).
                    // Ask the user to bring the app forward instead.
                    WakeWordNotifications.postResumeNotification(context.applicationContext)
                } else {
                    Log.i(TAG, "Wake word disabled in settings; not prompting")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not post wake word resume prompt after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
