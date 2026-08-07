package uk.org.retallack.jarvis.data.ha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
}

@Singleton
class HaWebSocketClient @Inject constructor(
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var token: String? = null
    private var baseUrl: String? = null
    private val msgId = AtomicInteger(1)
    private val pendingResponses = mutableMapOf<Int, CompletableDeferred<JsonObject>>()

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState

    private val _events = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val events: SharedFlow<JsonObject> = _events

    private var reconnectAttempt = 0
    private var shouldReconnect = false

    fun configure(baseUrl: String, token: String) {
        this.baseUrl = baseUrl.trimEnd('/')
        this.token = token
    }

    fun connect() {
        if (_connectionState.value == WsConnectionState.CONNECTED ||
            _connectionState.value == WsConnectionState.CONNECTING
        ) return

        shouldReconnect = true
        doConnect()
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    suspend fun sendCommand(type: String, additionalData: Map<String, Any> = emptyMap()): JsonObject {
        val id = msgId.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pendingResponses[id] = deferred

        val msg = buildJsonObject {
            put("id", id)
            put("type", type)
            additionalData.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }

        webSocket?.send(msg.toString())
            ?: throw IllegalStateException("WebSocket not connected")

        return deferred.await()
    }

    suspend fun subscribeEvents(eventType: String): Int {
        val id = msgId.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pendingResponses[id] = deferred

        val msg = buildJsonObject {
            put("id", id)
            put("type", "subscribe_events")
            put("event_type", eventType)
        }

        webSocket?.send(msg.toString())
        deferred.await()
        return id
    }

    private fun doConnect() {
        val url = baseUrl ?: return
        val wsUrl = url.replace("https://", "wss://").replace("http://", "ws://") + "/api/websocket"

        _connectionState.value = WsConnectionState.CONNECTING

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, WsListener())
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectAttempt++
        val delay = minOf(60_000L, (1000L * (1 shl minOf(reconnectAttempt, 6))))
        _connectionState.value = WsConnectionState.RECONNECTING

        scope.launch {
            delay(delay)
            if (shouldReconnect) doConnect()
        }
    }

    private inner class WsListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = WsConnectionState.AUTHENTICATING
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                handleMessage(text)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = WsConnectionState.DISCONNECTED
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.value = WsConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private suspend fun handleMessage(text: String) {
        val msg = json.parseToJsonElement(text).jsonObject
        val type = msg["type"]?.jsonPrimitive?.content ?: return

        when (type) {
            "auth_required" -> {
                val authMsg = buildJsonObject {
                    put("type", "auth")
                    put("access_token", token ?: "")
                }
                webSocket?.send(authMsg.toString())
            }

            "auth_ok" -> {
                _connectionState.value = WsConnectionState.CONNECTED
                reconnectAttempt = 0
            }

            "auth_invalid" -> {
                _connectionState.value = WsConnectionState.DISCONNECTED
                shouldReconnect = false
            }

            "result" -> {
                val id = msg["id"]?.jsonPrimitive?.content?.toIntOrNull()
                if (id != null) {
                    pendingResponses.remove(id)?.complete(msg)
                }
            }

            "event" -> {
                _events.emit(msg)
            }
        }
    }
}
