package uk.org.retallack.jarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State of the audio player.
 */
enum class AudioPlayerState {
    IDLE,
    PLAYING,
    STOPPED,
}

/**
 * Handles PCM audio playback for TTS output.
 * - Plays raw PCM audio data via AudioTrack
 * - Manages audio focus (request/abandon)
 * - Supports stop/interrupt
 */
@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val DEFAULT_SAMPLE_RATE = 22050 // Piper TTS default
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _state = MutableStateFlow(AudioPlayerState.IDLE)
    val state: StateFlow<AudioPlayerState> = _state

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private val mutex = Mutex()

    /**
     * Play PCM audio data (16-bit mono).
     * @param samples PCM 16-bit audio samples
     * @param sampleRate Sample rate in Hz (default 22050 for Piper TTS)
     */
    suspend fun play(samples: ShortArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        mutex.withLock {
            stopInternal()
            if (!requestAudioFocus()) return
            playInternal(samples, sampleRate)
        }
    }

    /**
     * Play PCM audio data from a ByteArray (16-bit mono, little-endian).
     * @param data Raw PCM bytes
     * @param sampleRate Sample rate in Hz
     */
    suspend fun playBytes(data: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        val samples = ShortArray(data.size / 2)
        java.nio.ByteBuffer.wrap(data)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(samples)
        play(samples, sampleRate)
    }

    /**
     * Stop any ongoing playback and release resources.
     */
    suspend fun stop() {
        mutex.withLock {
            stopInternal()
        }
    }

    /**
     * Release all resources. Call when no longer needed.
     */
    suspend fun release() {
        mutex.withLock {
            stopInternal()
            abandonAudioFocus()
        }
    }

    private suspend fun playInternal(samples: ShortArray, sampleRate: Int) {
        withContext(Dispatchers.IO) {
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
                .coerceAtLeast(samples.size * 2)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                _state.value = AudioPlayerState.IDLE
                return@withContext
            }

            audioTrack = track
            _state.value = AudioPlayerState.PLAYING

            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) {
                        _state.value = AudioPlayerState.IDLE
                        t?.stop()
                        t?.release()
                        audioTrack = null
                        abandonAudioFocus()
                    }

                    override fun onPeriodicNotification(t: AudioTrack?) {}
                },
            )
            track.play()
        }
    }

    private fun stopInternal() {
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            } catch (e: IllegalStateException) {
                // Already released
            }
        }
        audioTrack = null
        _state.value = AudioPlayerState.STOPPED
        abandonAudioFocus()
        _state.value = AudioPlayerState.IDLE
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        -> {
                            stopInternal()
                        }
                    }
                }
                .build()
            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasAudioFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        stopInternal()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasAudioFocus
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        hasAudioFocus = false
    }
}
