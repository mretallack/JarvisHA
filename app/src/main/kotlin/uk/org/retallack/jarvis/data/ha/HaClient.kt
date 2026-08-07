package uk.org.retallack.jarvis.data.ha

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uk.org.retallack.jarvis.data.ha.model.ConversationRequest
import uk.org.retallack.jarvis.data.ha.model.ConversationResponse
import uk.org.retallack.jarvis.data.ha.model.HaApiStatus
import uk.org.retallack.jarvis.data.ha.model.HaConfig
import uk.org.retallack.jarvis.data.ha.model.HaEntityState
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HaClient @Inject constructor(
    private val json: Json,
) {
    private var api: HaRestApi? = null
    private var currentBaseUrl: String? = null
    private var currentToken: String? = null

    fun configure(baseUrl: String, token: String) {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        if (normalizedUrl == currentBaseUrl && token == currentToken) return

        currentBaseUrl = normalizedUrl
        currentToken = token

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(token))
            .build()

        val contentType = "application/json".toMediaType()
        api = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(HaRestApi::class.java)
    }

    val isConfigured: Boolean get() = api != null

    suspend fun checkConnection(): HaApiStatus {
        return requireApi().checkConnection()
    }

    suspend fun getConfig(): HaConfig {
        return requireApi().getConfig()
    }

    suspend fun getAllStates(): List<HaEntityState> {
        return requireApi().getAllStates()
    }

    suspend fun processConversation(request: ConversationRequest): ConversationResponse {
        return requireApi().processConversation(request)
    }

    private fun requireApi(): HaRestApi {
        return api ?: throw IllegalStateException("HaClient not configured. Call configure() first.")
    }

    private class AuthInterceptor(private val token: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()
            return chain.proceed(request)
        }
    }
}
