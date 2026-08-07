package uk.org.retallack.jarvis.voice.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that continuously listens for the "Hey Jarvis" wake word.
 *
 * - Creates AudioRecord (16kHz, mono, 16-bit PCM)
 * - Feeds audio to WakeWordEngine
 * - On detection: broadcasts intent and wakes screen if off
 * - Respects quiet hours via WakeWordEngine
 * - Uses START_STICKY for auto-restart
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject
    lateinit var wakeWordEngine: WakeWordEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var audioRecordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "WakeWordService"
        const val NOTIFICATION_CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_WAKE_WORD_DETECTED = "uk.org.retallack.jarvis.WAKE_WORD_DETECTED"
        const val EXTRA_CONFIDENCE = "confidence"
        const val EXTRA_TIMESTAMP = "timestamp"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2

        fun startService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopListening()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        serviceScope.launch {
            val initialized = wakeWordEngine.initialize()
            if (!initialized) {
                Log.e(TAG, "Failed to initialize wake word engine")
                stopSelf()
                return@launch
            }
            wakeWordEngine.startListening()
        }

        // Observe detections
        wakeWordEngine.detections
            .onEach { detection -> onWakeWordDetected(detection) }
            .launchIn(serviceScope)

        // Start audio capture
        audioRecordJob = serviceScope.launch(Dispatchers.IO) {
            captureAudio()
        }
    }

    private fun stopListening() {
        audioRecordJob?.cancel()
        audioRecordJob = null
        audioRecord?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio record", e)
            }
        }
        audioRecord = null
        serviceScope.launch {
            wakeWordEngine.stopListening()
        }
    }

    @Suppress("MissingPermission")
    private suspend fun captureAudio() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid min buffer size: $minBufferSize")
            return
        }

        val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return
        }

        audioRecord = record
        record.startRecording()

        val frameSize = 1280 // 80ms at 16kHz
        val buffer = ShortArray(frameSize)

        val scope = kotlinx.coroutines.coroutineScope {
            while (isActive) {
                val read = record.read(buffer, 0, frameSize)
                if (read > 0) {
                    val samples = if (read == frameSize) buffer else buffer.copyOf(read)
                    wakeWordEngine.processAudio(samples)
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: $read")
                    break
                }
            }
        }
    }

    private fun onWakeWordDetected(detection: WakeWordDetection) {
        Log.i(TAG, "Wake word detected! confidence=${detection.confidence}")

        // Wake the screen if off
        wakeScreen()

        // Broadcast detection
        val intent = Intent(ACTION_WAKE_WORD_DETECTED).apply {
            setPackage(packageName)
            putExtra(EXTRA_CONFIDENCE, detection.confidence)
            putExtra(EXTRA_TIMESTAMP, detection.timestamp)
        }
        sendBroadcast(intent)
    }

    /**
     * Task 13.5: Wake screen on detection.
     * Acquires a wake lock briefly to turn screen on.
     */
    private fun wakeScreen() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            @Suppress("DEPRECATION")
            val wl = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "$TAG:WakeWordWakeLock",
            )
            wl.acquire(3000L) // 3 seconds to turn screen on
            wakeLock = wl
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Wake Word Listening",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notification shown while listening for \"Hey Jarvis\""
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("JarvisHA")
                .setContentText("Listening for \"Hey Jarvis\"...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("JarvisHA")
                .setContentText("Listening for \"Hey Jarvis\"...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }
}
