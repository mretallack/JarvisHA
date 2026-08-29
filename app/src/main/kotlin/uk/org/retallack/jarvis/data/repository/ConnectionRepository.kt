package uk.org.retallack.jarvis.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.org.retallack.jarvis.security.EncryptedTokenStore
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionConfig(
    val url: String,
    val token: String,
)

data class QuietHoursSettings(
    val enabled: Boolean = false,
    val startTime: String = "22:00",
    val endTime: String = "07:00",
)


data class SttSettings(
    val silenceDurationMs: Int = 2000,
    val silenceThreshold: Int = 500,
    val maxRecordingSeconds: Int = 30,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

@Singleton
class ConnectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedTokenStore: EncryptedTokenStore,
) {
    private object Keys {
        val HA_URL = stringPreferencesKey("ha_url")
        val STT_SILENCE_DURATION_MS = intPreferencesKey("stt_silence_duration_ms")
        val STT_SILENCE_THRESHOLD = intPreferencesKey("stt_silence_threshold")
        val STT_MAX_RECORDING_SECONDS = intPreferencesKey("stt_max_recording_seconds")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START = stringPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = stringPreferencesKey("quiet_hours_end")
    }

    val connectionConfig: Flow<ConnectionConfig?> = context.dataStore.data.map { prefs ->
        val url = prefs[Keys.HA_URL]
        val token = encryptedTokenStore.getToken()
        if (url != null && token != null) {
            ConnectionConfig(url = url, token = token)
        } else {
            null
        }
    }

    suspend fun getConnectionConfig(): ConnectionConfig? {
        return connectionConfig.first()
    }

    suspend fun saveConnectionConfig(url: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HA_URL] = url.trimEnd('/')
        }
        if (token.isNotBlank()) {
            encryptedTokenStore.saveToken(token.trim())
        }
    }

    suspend fun clearConnectionConfig() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.HA_URL)
        }
        encryptedTokenStore.clearToken()
    }

    suspend fun isConfigured(): Boolean {
        return getConnectionConfig() != null
    }

    val quietHoursSettings: Flow<QuietHoursSettings> = context.dataStore.data.map { prefs ->
        QuietHoursSettings(
            enabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
            startTime = prefs[Keys.QUIET_HOURS_START] ?: "22:00",
            endTime = prefs[Keys.QUIET_HOURS_END] ?: "07:00",
        )
    }

    suspend fun getQuietHoursSettings(): QuietHoursSettings {
        return quietHoursSettings.first()
    }

    suspend fun saveQuietHoursSettings(settings: QuietHoursSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QUIET_HOURS_ENABLED] = settings.enabled
            prefs[Keys.QUIET_HOURS_START] = settings.startTime
            prefs[Keys.QUIET_HOURS_END] = settings.endTime
        }
    }

    val sttSettings: Flow<SttSettings> = context.dataStore.data.map { prefs ->
        SttSettings(
            silenceDurationMs = prefs[Keys.STT_SILENCE_DURATION_MS] ?: 2000,
            silenceThreshold = prefs[Keys.STT_SILENCE_THRESHOLD] ?: 500,
            maxRecordingSeconds = prefs[Keys.STT_MAX_RECORDING_SECONDS] ?: 30,
        )
    }

    suspend fun getSttSettings(): SttSettings {
        return sttSettings.first()
    }

    suspend fun saveSttSettings(settings: SttSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STT_SILENCE_DURATION_MS] = settings.silenceDurationMs
            prefs[Keys.STT_SILENCE_THRESHOLD] = settings.silenceThreshold
            prefs[Keys.STT_MAX_RECORDING_SECONDS] = settings.maxRecordingSeconds
        }
    }
}
