package uk.org.retallack.jarvis.voice.wakeword

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.data.db.ConversationSync
import uk.org.retallack.jarvis.voice.AppForegroundState
import javax.inject.Inject

/**
 * Foreground service that continuously listens for the "Hey Jarvis" wake word.
 *
 * Based on Dicio's approach:
 * - Foreground Service with START_STICKY for persistence
 * - AudioRecord with VOICE_RECOGNITION source at 16kHz mono
 * - Continuous loop reading audio frames and passing to wake word engine
 * - 4-second backoff between detections (prevent echo triggers)
 * - On detection: launch MainActivity with FLAG_ACTIVITY_NEW_TASK
 * - On Android 10+: use full-screen notification if can't start activity from background
 * - Low-priority notification channel
 * - Stop action in notification
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject
    lateinit var wakeWordEngine: WakeWordEngine

    @Inject
    lateinit var sttEngine: uk.org.retallack.jarvis.voice.stt.SttEngine

    @Inject
    lateinit var ttsEngine: uk.org.retallack.jarvis.voice.tts.TtsEngine

    @Inject
    lateinit var conversationRepository: uk.org.retallack.jarvis.data.repository.ConversationRepository

    @Inject
    lateinit var connectionRepository: uk.org.retallack.jarvis.data.repository.ConnectionRepository

    @Inject
    lateinit var haClient: uk.org.retallack.jarvis.data.ha.HaClient

    @Inject
    lateinit var modelManager: uk.org.retallack.jarvis.voice.ModelManager

    @Inject
    lateinit var wakeWordPreferences: WakeWordPreferences

    @Inject
    lateinit var messageDao: uk.org.retallack.jarvis.data.db.dao.ConversationMessageDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listeningJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastDetectionTime: Long = 0L

    companion object {
        private const val TAG = "WakeWordService"

        const val NOTIFICATION_CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "uk.org.retallack.jarvis.STOP_WAKE_WORD"
        const val ACTION_WAKE_WORD = "uk.org.retallack.jarvis.ACTION_WAKE_WORD"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE = 1152 // 72ms at 16kHz (matches OpenWakeWord mel input)
        private const val DETECTION_BACKOFF_MS = 15000L

        @Volatile
        private var running = false

        /**
         * Start the wake word service.
         * Checks RECORD_AUDIO permission before starting.
         */
        fun start(context: Context) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "RECORD_AUDIO permission not granted, cannot start")
                return
            }
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the wake word service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            context.stopService(intent)
        }

        /**
         * Check if the service is currently running.
         */
        fun isRunning(): Boolean = running
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        running = true
        Log.i(TAG, "WakeWordService created (process=${currentProcessName()})")
    }

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            packageName
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop action received")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start foreground service due to background restriction", t)
            running = false
            stopSelf()
            return START_NOT_STICKY
        }

        startListening()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "WakeWordService destroyed")
        running = false
        stopListening()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        if (listeningJob?.isActive == true) return

        listeningJob = serviceScope.launch {
            // Initialize TTS for speaking responses
            ttsEngine.initialize("")

            val initialized = wakeWordEngine.initialize()
            if (!initialized) {
                Log.e(TAG, "Failed to initialize wake word engine")
                stopSelf()
                return@launch
            }

            // Apply the user's stored sensitivity, otherwise the engine silently uses its
            // built-in default and the setting has no effect.
            try {
                val sensitivity = wakeWordPreferences.getSensitivity()
                wakeWordEngine.setSensitivity(sensitivity)
                Log.i(TAG, "Applied wake word sensitivity: $sensitivity")
            } catch (e: Exception) {
                Log.w(TAG, "Could not load wake word sensitivity, using default", e)
            }

            wakeWordEngine.startListening()
            captureAudio()
        }
    }

    private fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        releaseAudioRecord()
        serviceScope.launch {
            wakeWordEngine.stopListening()
            wakeWordEngine.release()
        }
    }

    private fun releaseAudioRecord() {
        audioRecord?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio record", e)
            }
        }
        audioRecord = null
    }

    /**
     * Suspend the wake-word listener and release the microphone.
     *
     * The wake-word detector and the STT engine both open an AudioRecord on the same
     * input source. Holding two concurrent AudioRecord sessions makes the second one
     * receive attenuated/silent audio, which made Whisper hallucinate. So we must fully
     * give up the mic while the STT engine is capturing.
     */
    private suspend fun pauseWakeWordCapture() {
        Log.d(TAG, "Pausing wake word capture to hand the mic to STT")
        listeningJob?.cancel()
        listeningJob = null
        releaseAudioRecord()
        wakeWordEngine.stopListening()
    }

    /** Resume wake-word listening after STT has finished with the microphone. */
    private fun resumeWakeWordCapture() {
        Log.d(TAG, "Resuming wake word capture")
        startListening()
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
            stopSelf()
            return
        }

        val bufferSize = maxOf(minBufferSize * 2, FRAME_SIZE * 2 * 2) // At least 2 frames
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            stopSelf()
            return
        }

        audioRecord = record
        record.startRecording()
        Log.i(TAG, "Audio capture started (16kHz, mono, 16-bit)")

        val buffer = ShortArray(FRAME_SIZE)

        val scope = kotlinx.coroutines.coroutineScope {
            while (isActive) {
                val read = record.read(buffer, 0, FRAME_SIZE)
                if (read > 0) {
                    val samples = if (read == FRAME_SIZE) buffer else buffer.copyOf(read)

                    // Check quiet hours
                    if (wakeWordEngine.isInQuietHours()) {
                        continue
                    }

                    // Process audio
                    wakeWordEngine.processAudio(samples)

                    // Check for detection
                    if (wakeWordEngine.state.value == WakeWordState.DETECTED) {
                        onWakeWordDetected()
                        // Reset state back to LISTENING so we don't re-trigger
                        wakeWordEngine.startListening()
                    }
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: $read")
                    break
                }
            }
        }
    }

    private fun onWakeWordDetected() {
        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < DETECTION_BACKOFF_MS) {
            Log.d(TAG, "Detection within backoff period, ignoring")
            return
        }
        lastDetectionTime = now

        Log.i(TAG, "Wake word detected! Processing in background...")

        // Play a short vibration AND tone to indicate wake word heard
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
            Log.d(TAG, "Vibration triggered")
        } catch (e: Exception) {
            Log.w(TAG, "Could not vibrate", e)
        }

        // Wait for vibration to be felt before playing beep
        Thread.sleep(250)

        // Play a short beep to tell user to speak now
        try {
            val toneGenerator = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_MUSIC, 100
            )
            toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 300)
            // Delay to let user hear the beep before STT starts
            Thread.sleep(300)
            toneGenerator.release()
            Log.d(TAG, "Beep played")
        } catch (e: Exception) {
            Log.w(TAG, "Could not play tone", e)
        }

        // Process entirely in background: STT → HA → TTS
        serviceScope.launch {
            // Mark an active session so AppForegroundState keeps the STT model resident
            // while this headless process handles the command (it has no visible UI).
            AppForegroundState.beginActiveSession()
            try {
                // Hand the microphone over to the STT engine: holding two concurrent
                // AudioRecord sessions on the same source starves the second one.
                pauseWakeWordCapture()
                try {
                    processWakeWordCommand()
                } finally {
                    // The main process's Room observer can't see writes from this process,
                    // so tell it to re-read the conversation history.
                    ConversationSync.notifyChanged(applicationContext)
                    resumeWakeWordCapture()
                }
            } finally {
                AppForegroundState.endActiveSession()
            }

            // Increase backoff after processing to prevent re-trigger
            lastDetectionTime = System.currentTimeMillis()
        }
    }

    /**
     * Runs the STT → Home Assistant → TTS pipeline for a detected wake word.
     *
     * Must be called inside an [AppForegroundState] active session so the STT model is
     * allowed to stay loaded for the duration.
     */
    private suspend fun processWakeWordCommand() {
        try {
                // Configure HA client if needed
                if (!haClient.isConfigured) {
                    val config = connectionRepository.getConnectionConfig()
                    if (config != null) {
                        haClient.configure(config.url, config.token)
                    } else {
                        Log.e(TAG, "No HA connection configured")
                        ttsEngine.speak("Home Assistant not configured")
                        return
                    }
                }

                // Initialize STT if needed. This can take ~2 s when the model is cold, and
                // it used to block the microphone from opening - so the start of the user's
                // sentence was lost. Kick it off concurrently instead so audio capture
                // begins almost immediately; processBufferedAudio waits for it if needed.
                val modelDir = modelManager.getSttModelDir().absolutePath
                val modelLoadJob = serviceScope.launch(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    sttEngine.initialize(modelDir)
                    Log.d(TAG, "Model ready in ${System.currentTimeMillis() - t0}ms (concurrent with capture)")
                }

                // IMPORTANT: subscribe to the result flow *before* starting to listen.
                // The engine emits the final result while processing finishes, so a
                // collector started afterwards would race and miss it entirely - which
                // caused every wake-word command to end in "I didn't catch that".
                val finalTextDeferred = kotlinx.coroutines.CompletableDeferred<String?>()
                // The results flow has replay=1, so it may hand us a *stale* result from
                // a previous utterance. Ignore anything emitted before this session began.
                val sessionStartMs = System.currentTimeMillis()
                val collectorJob = serviceScope.launch {
                    try {
                        sttEngine.results.collect { result ->
                            if (!result.isFinal) return@collect
                            // Skip replayed results from a previous session.
                            if (result.timestampMs > 0 && result.timestampMs < sessionStartMs) {
                                Log.d(TAG, "Ignoring stale replayed STT result: '${result.text}'")
                                return@collect
                            }
                            if (result.confidence > 0f && result.text.isNotBlank()) {
                                Log.d(TAG, "Wake-word STT result: '${result.text}'")
                                finalTextDeferred.complete(result.text)
                            } else if (!finalTextDeferred.isCompleted) {
                                // Engine-reported failure (e.g. "No speech detected")
                                Log.d(TAG, "Wake-word STT final with no text: '${result.text}'")
                                finalTextDeferred.complete(null)
                            }
                        }
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        // Collector cancelled on completion below - expected.
                    }
                }

                // Start listening - this captures audio until silence detected
                Log.d(TAG, "Starting STT listening...")
                sttEngine.startListening()

                // Wait for the STT to finish (it stops on silence or manual stop).
                // Poll the state until it's no longer LISTENING.
                val maxListenMs = 15000L // 15 second max
                val startTime = System.currentTimeMillis()
                while (sttEngine.state.value == uk.org.retallack.jarvis.voice.stt.SttState.LISTENING ||
                    sttEngine.state.value == uk.org.retallack.jarvis.voice.stt.SttState.PROCESSING) {
                    kotlinx.coroutines.delay(200)
                    if (System.currentTimeMillis() - startTime > maxListenMs) {
                        Log.w(TAG, "STT timeout, forcing stop")
                        sttEngine.stopListening()
                        break
                    }
                }

                // Now wait for the result (already being collected from before listening).
                val finalText: String? = try {
                    kotlinx.coroutines.withTimeout(10000L) {
                        finalTextDeferred.await()
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.d(TAG, "Timed out waiting for STT result")
                    null
                } finally {
                    collectorJob.cancel()
                }

                val recognisedText = finalText
                if (recognisedText.isNullOrBlank()) {
                    Log.d(TAG, "No speech detected after wake word")
                    ttsEngine.speak("I didn't catch that")
                    return
                }

                // Clean and process command
                val cleanedText = recognisedText.trim()
                    .removeSuffix(".").removeSuffix(",")
                    .removeSuffix("!").removeSuffix("?")
                    .trim().lowercase()
                    // Remove "hey jarvis" from the beginning if whisper captured it
                    .removePrefix("hey jarvis,").removePrefix("hey jarvis")
                    .trim()

                // Filter out Whisper artifacts (silence markers, sound effects)
                if (cleanedText.isBlank() || cleanedText.startsWith("[") || cleanedText.startsWith("(")) {
                    Log.d(TAG, "Filtered out non-speech: '$cleanedText'")
                    ttsEngine.speak("I didn't catch that. Please try again after the beep.")
                    return
                }

                Log.i(TAG, "Processing command: '$cleanedText'")

                // Save user message to chat
                messageDao.insert(uk.org.retallack.jarvis.data.db.entity.ConversationMessageDb(
                    text = cleanedText, isUser = true
                ))
                // Show it in the UI immediately, before the slower HA round-trip.
                ConversationSync.notifyChanged(applicationContext)

                // Send to HA
                val result = conversationRepository.processText(cleanedText)

                when (result) {
                    is uk.org.retallack.jarvis.data.repository.ConversationResult.Success -> {
                        val responseText = uk.org.retallack.jarvis.data.ha.model.VerboseResponseBuilder.build(result.response)
                        Log.i(TAG, "HA response: '$responseText'")
                        // Save response to chat
                        messageDao.insert(uk.org.retallack.jarvis.data.db.entity.ConversationMessageDb(
                            text = responseText, isUser = false
                        ))
                        ttsEngine.speak(responseText)
                    }
                    is uk.org.retallack.jarvis.data.repository.ConversationResult.Error -> {
                        val errorText = when (result.error) {
                            is uk.org.retallack.jarvis.data.repository.ConversationError.NotConnected ->
                                "Not connected to Home Assistant"
                            is uk.org.retallack.jarvis.data.repository.ConversationError.NoValidTargets ->
                                "No matching devices found"
                            is uk.org.retallack.jarvis.data.repository.ConversationError.NoIntentMatch ->
                                "Command not understood"
                            is uk.org.retallack.jarvis.data.repository.ConversationError.HaError ->
                                "Error from Home Assistant"
                            is uk.org.retallack.jarvis.data.repository.ConversationError.NetworkError ->
                                "Connection error"
                        }
                        Log.w(TAG, "HA error: $errorText")
                        // Save error to chat
                        messageDao.insert(uk.org.retallack.jarvis.data.db.entity.ConversationMessageDb(
                            text = errorText, isUser = false, isError = true
                        ))
                        ttsEngine.speak(errorText)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing wake word command", e)
                try { ttsEngine.speak("Sorry, something went wrong") } catch (_: Exception) {}
            }
    }

    private fun wakeScreen() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            @Suppress("DEPRECATION")
            val wl = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "$TAG:WakeWordDetection",
            )
            wl.acquire(3000L) // 3 seconds to turn screen on
            wakeLock = wl
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
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
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Tap notification to open app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Stop action
        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("JarvisHA")
            .setContentText("Listening for \"Hey Jarvis\"…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
