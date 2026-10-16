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
 * Restarts the wake-word listener after a reboot.
 *
 * The app is expected to keep listening for "Hey Jarvis" across restarts, but without this
 * receiver the service only came back when the user happened to open the app.
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

        Log.i(TAG, "Boot/package-replaced received ($action); restoring wake word listener")

        // Broadcast receivers must not block, so hand off to a one-shot scope.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = EntryPointAccessors
                    .fromApplication(context.applicationContext, PrefsEntryPoint::class.java)
                    .wakeWordPreferences()
                if (prefs.getEnabled()) {
                    WakeWordService.start(context.applicationContext)
                } else {
                    Log.i(TAG, "Wake word disabled in settings; not starting")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore wake word listener after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
