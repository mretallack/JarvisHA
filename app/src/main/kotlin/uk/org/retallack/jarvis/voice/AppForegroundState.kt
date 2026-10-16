package uk.org.retallack.jarvis.voice

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks whether the STT model is currently *allowed* to be kept resident in memory.
 *
 * This exists to keep the process's memory footprint down: holding the ~160 MB
 * Sherpa-ONNX/Whisper model while the app is idle and in the background makes the
 * process a prime target for the OS low-memory killer.
 *
 * Two independent signals contribute:
 *
 * 1. **Visible activities in this process.** Set by [register] from the activity
 *    lifecycle. In the dedicated `:wakeword` process there are no activities, so this
 *    is always false there.
 *
 * 2. **Active wake-word sessions.** The `:wakeword` process is a headless foreground
 *    service and legitimately needs the model while handling a command, even though it
 *    has no UI. [beginActiveSession] / [endActiveSession] tell us that.
 *
 * The model is released as soon as a command finishes, to keep the listener process as
 * small as possible. This device is under heavy memory pressure and the low-memory killer
 * will reap a large background process; a small listener survives. The cost is a ~2.5 s
 * model reload on the next command, which is preferable to the listener being killed.
 *
 * Note the distinction from a naive "is the UI visible" check: no UI in this process does
 * NOT mean the app is idle - a wake-word service handling a command is very much active.
 */
object AppForegroundState {

    private val startedActivities = AtomicInteger(0)

    /** Number of wake-word command sessions currently in flight. */
    private val activeSessions = AtomicInteger(0)

    @Volatile
    private var hasVisibleActivity = false

    /** True when the STT model may be kept warm in this process. */
    val isAppForeground: Boolean
        get() = hasVisibleActivity || activeSessions.get() > 0

    /** True when a wake-word command is actively being processed. */
    val hasActiveSession: Boolean get() = activeSessions.get() > 0

    /**
     * Mark the start of a wake-word command session. Keeps the model resident while the
     * service is working, regardless of whether any UI is visible.
     */
    fun beginActiveSession() {
        activeSessions.incrementAndGet()
    }

    /** Mark the end of a wake-word command session. */
    fun endActiveSession() {
        activeSessions.updateAndGet { if (it > 0) it - 1 else 0 }
    }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    hasVisibleActivity = startedActivities.incrementAndGet() > 0
                }

                override fun onActivityStopped(activity: Activity) {
                    hasVisibleActivity = startedActivities.updateAndGet { if (it > 0) it - 1 else 0 } > 0
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
