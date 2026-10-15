package uk.org.retallack.jarvis

import android.app.Application
import android.os.Build
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.voice.AppForegroundState
import uk.org.retallack.jarvis.voice.wakeword.WakeWordPreferences
import uk.org.retallack.jarvis.voice.wakeword.WakeWordService

@HiltAndroidApp
class JarvisApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WakeWordEntryPoint {
        fun wakeWordPreferences(): WakeWordPreferences
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // The wake word service runs in a dedicated process (see AndroidManifest).
        // When that process is started by the system, Application.onCreate() runs in
        // it too - we must NOT start WakeWordService from there (starting a foreground
        // service from a freshly-spawned background process would be rejected).
        // Only the main process auto-starts the listener.
        if (isWakeWordProcess()) {
            return
        }

        AppForegroundState.register(this)
        autoStartWakeWordService()
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

    /**
     * If wake word was previously enabled, auto-start the foreground service.
     * This handles app restart / phone reboot scenarios.
     */
    private fun autoStartWakeWordService() {
        appScope.launch(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@JarvisApplication,
                    WakeWordEntryPoint::class.java,
                )
                val prefs = entryPoint.wakeWordPreferences()
                if (prefs.getEnabled()) {
                    WakeWordService.start(this@JarvisApplication)
                }
            } catch (_: Exception) {
                // Don't crash on startup if DataStore isn't available
            }
        }
    }
}
