package uk.org.retallack.jarvis

import android.app.Application
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        autoStartWakeWordService()
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
