package uk.org.retallack.jarvis.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionConfig(
    val url: String,
    val token: String,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

@Singleton
class ConnectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
    }

    val connectionConfig: Flow<ConnectionConfig?> = context.dataStore.data.map { prefs ->
        val url = prefs[Keys.HA_URL]
        val token = prefs[Keys.HA_TOKEN]
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
            prefs[Keys.HA_TOKEN] = token.trim()
        }
    }

    suspend fun clearConnectionConfig() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.HA_URL)
            prefs.remove(Keys.HA_TOKEN)
        }
    }

    suspend fun isConfigured(): Boolean {
        return getConnectionConfig() != null
    }
}
