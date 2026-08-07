package uk.org.retallack.jarvis.data.ha

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import uk.org.retallack.jarvis.data.ha.model.ConversationRequest
import uk.org.retallack.jarvis.data.ha.model.ConversationResponse
import uk.org.retallack.jarvis.data.ha.model.HaApiStatus
import uk.org.retallack.jarvis.data.ha.model.HaConfig
import uk.org.retallack.jarvis.data.ha.model.HaEntityState

interface HaRestApi {

    @GET("api/")
    suspend fun checkConnection(): HaApiStatus

    @GET("api/config")
    suspend fun getConfig(): HaConfig

    @GET("api/states")
    suspend fun getAllStates(): List<HaEntityState>

    @POST("api/conversation/process")
    suspend fun processConversation(@Body request: ConversationRequest): ConversationResponse
}
