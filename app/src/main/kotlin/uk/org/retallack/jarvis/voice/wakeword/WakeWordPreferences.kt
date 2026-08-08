package uk.org.retallack.jarvis.voice.wakeword

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.wakeWordPrefsStore by preferencesDataStore(name = "wake_word_prefs")

/**
 * Manages wake word preferences using DataStore.
 * Provides a single source of truth for wake word enabled state and sensitivity.
 */
@Singleton
class WakeWordPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("wake_word_enabled")
        val SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
    }

    val isEnabled: Flow<Boolean> = context.wakeWordPrefsStore.data
        .map { it[Keys.ENABLED] ?: false }

    val sensitivity: Flow<Float> = context.wakeWordPrefsStore.data
        .map { it[Keys.SENSITIVITY] ?: 0.5f }

    suspend fun getEnabled(): Boolean = isEnabled.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.wakeWordPrefsStore.edit { prefs ->
            prefs[Keys.ENABLED] = enabled
        }
    }

    suspend fun setSensitivity(value: Float) {
        context.wakeWordPrefsStore.edit { prefs ->
            prefs[Keys.SENSITIVITY] = value.coerceIn(0f, 1f)
        }
    }
}
