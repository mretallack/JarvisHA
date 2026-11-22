package uk.org.retallack.jarvis

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import uk.org.retallack.jarvis.voice.AppForegroundState

@HiltAndroidApp
class JarvisApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // The wake word service runs in a dedicated process (see AndroidManifest).
        // When that process is started by the system, Application.onCreate() runs in
        // it too - we must not run any main-process setup there.
        if (isWakeWordProcess()) {
            return
        }

        AppForegroundState.register(this)

        // NOTE: we deliberately do NOT start WakeWordService from here.
        //
        // Application.onCreate() runs *before* any activity is visible. On Android 14+
        // a microphone foreground service started while the app is in the background is
        // denied microphone access, so starting the listener here produced a service
        // that ran but was effectively deaf ("Foreground service started from background
        // can not have ... microphone access"). The listener is armed from
        // MainActivity.onStart() instead, which runs with a visible activity - the only
        // context in which the microphone is granted. See WakeWordNotifications.
    }

    /** True when this process is the dedicated {@code :wakeword} process. */
    private fun isWakeWordProcess(): Boolean =
        currentProcessName() == "$packageName:wakeword"

    private fun currentProcessName(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            // Best-effort fallback for API 26/27: read the process name from /proc.
            try {
                java.io.File("/proc/self/cmdline")
                    .readText()
                    .trim('\u0000', '\n', ' ')
            } catch (_: Exception) {
                null
            }
        }
}
