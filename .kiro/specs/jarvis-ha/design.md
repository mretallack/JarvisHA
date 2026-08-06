# JarvisHA - Technical Design

## Overview

JarvisHA is a native Android application built with Kotlin and Jetpack Compose that provides voice-first control of Home Assistant. The architecture prioritises offline capability, privacy, and zero Google dependency.

## Architecture Pattern

**MVVM + Repository + Use Cases** with Hilt dependency injection.

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer                             │
│  Jetpack Compose Screens + ViewModels                   │
├─────────────────────────────────────────────────────────┤
│                  Domain Layer                            │
│  Use Cases / Interactors                                │
├─────────────────────────────────────────────────────────┤
│                  Data Layer                              │
│  Repositories + Data Sources                            │
├──────────────┬──────────────┬───────────────────────────┤
│  HA REST API │ HA WebSocket │ Local DB (Room) + DataStore│
└──────────────┴──────────────┴───────────────────────────┘
```

## Technology Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Language | Kotlin | Android standard, coroutines for async |
| UI | Jetpack Compose + Material3 | Modern declarative UI |
| DI | Hilt | Standard Android DI, compile-time safe |
| Networking | OkHttp 5 + Retrofit | Battle-tested, native WebSocket support |
| Local DB | Room | Entity cache, conversation history, notifications |
| Preferences | Protobuf DataStore | Type-safe, async, structured settings |
| STT | Sherpa-ONNX / Vosk | Offline, no Google dependency |
| TTS | Sherpa-ONNX (Piper models) / eSpeak-NG | Offline neural TTS |
| Wake Word | OpenWakeWord via LiteRT | TFLite model inference |
| Serialisation | Kotlinx Serialization | JSON export/import, API responses |
| Testing | JUnit5 + Turbine + MockK | Kotlin-native mocking, Flow testing |
| Build | Gradle (Kotlin DSL) | Standard Android build |

## High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android App                               │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
│  │Dashboard │  │  Voice   │  │ Settings │  │Entity Browser │  │
│  │  Screen  │  │  Screen  │  │  Screen  │  │    Screen     │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬────────┘  │
│       │              │              │               │            │
│  ┌────┴──────────────┴──────────────┴───────────────┴────────┐  │
│  │                    ViewModels                              │  │
│  └────┬──────────────┬──────────────┬───────────────┬────────┘  │
│       │              │              │               │            │
│  ┌────┴────┐  ┌──────┴─────┐  ┌────┴────┐  ┌──────┴──────┐   │
│  │  HA     │  │   Voice    │  │ Config  │  │   Entity    │   │
│  │ Repo    │  │   Repo     │  │  Repo   │  │    Repo     │   │
│  └────┬────┘  └──────┬─────┘  └────┬────┘  └──────┬──────┘   │
│       │              │              │               │            │
│  ┌────┴────┐  ┌──────┴─────┐  ┌────┴────┐  ┌──────┴──────┐   │
│  │HA Client│  │ STT / TTS  │  │DataStore│  │  Room DB    │   │
│  │REST + WS│  │  Engines   │  │         │  │             │   │
│  └─────────┘  └────────────┘  └─────────┘  └─────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Background Services                          │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │   │
│  │  │ Wake Word   │  │  WebSocket   │  │  Notification  │  │   │
│  │  │  Service    │  │   Service    │  │    Service     │  │   │
│  │  └─────────────┘  └──────────────┘  └────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Module / Package Structure

```
uk.co.jarvis.ha/
├── app/                        # Application class, Hilt setup, navigation
├── ui/
│   ├── dashboard/              # Main dashboard screen
│   ├── voice/                  # Voice interaction screen
│   ├── settings/               # Settings screens
│   ├── entities/               # Entity browser
│   ├── setup/                  # Initial setup wizard
│   └── common/                 # Shared composables, theme
├── domain/
│   ├── model/                  # Domain models (Entity, Area, VoiceCommand)
│   └── usecase/                # Use cases (ControlEntity, QueryState, etc.)
├── data/
│   ├── ha/                     # HA API client (REST + WebSocket)
│   ├── voice/                  # STT/TTS/WakeWord engine abstractions
│   ├── db/                     # Room database (entities, history, notifications)
│   ├── preferences/            # DataStore (settings, favourites)
│   └── export/                 # Export/import logic
├── service/
│   ├── wakeword/               # Foreground service for wake word detection
│   ├── websocket/              # Persistent WebSocket connection service
│   └── notification/           # HA notification listener service
└── widget/                     # Home screen widgets
```

---

## Core Components

### 1. Home Assistant Client (`data.ha`)

#### REST Client

```kotlin
interface HaRestApi {
    @GET("api/")
    suspend fun checkConnection(): HaConfigResponse

    @GET("api/config")
    suspend fun getConfig(): HaConfig

    @GET("api/states")
    suspend fun getAllStates(): List<HaEntityState>

    @GET("api/states/{entity_id}")
    suspend fun getState(@Path("entity_id") entityId: String): HaEntityState

    @POST("api/services/{domain}/{service}")
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body data: JsonObject
    ): List<HaEntityState>

    @POST("api/conversation/process")
    suspend fun processConversation(@Body request: ConversationRequest): ConversationResponse
}
```

#### WebSocket Client

Manages persistent connection for:
- Authentication handshake
- Subscribing to `state_changed` events
- Entity/area/device registry queries
- Assist pipeline execution
- Service calls (alternative to REST)

```kotlin
class HaWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val connectionConfig: ConnectionConfig
) {
    // Connection state as Flow
    val connectionState: StateFlow<ConnectionState>
    
    // Incoming events as Flow
    val events: SharedFlow<HaEvent>
    
    suspend fun connect()
    suspend fun authenticate(token: String)
    suspend fun subscribeEvents(eventType: String): Int
    suspend fun callService(domain: String, service: String, data: JsonObject)
    suspend fun getEntityRegistry(): List<EntityRegistryEntry>
    suspend fun getAreaRegistry(): List<AreaRegistryEntry>
    suspend fun getDeviceRegistry(): List<DeviceRegistryEntry>
    suspend fun runAssistPipeline(request: AssistPipelineRequest): Flow<PipelineEvent>
    fun disconnect()
}
```

#### Connection Manager

Handles local/remote URL selection and reconnection:

```kotlin
class ConnectionManager(
    private val networkMonitor: NetworkMonitor,
    private val config: ConnectionConfig
) {
    // Determines whether to use local or external URL
    val activeUrl: StateFlow<String>
    
    // Monitors network changes and triggers URL re-evaluation
    fun startMonitoring()
}
```

---

### 2. Voice Pipeline (`data.voice`)

The voice pipeline is modular — each stage (wake word, STT, intent, TTS) has a pluggable engine interface.

#### Voice Pipeline Flow

```
┌──────────┐    ┌─────┐    ┌────────────────────┐    ┌─────┐    ┌─────────┐
│Wake Word │───>│ STT │───>│ Intent Processing  │───>│ TTS │───>│ Speaker │
│(LiteRT)  │    │     │    │(HA Conversation API)│    │     │    │         │
└──────────┘    └─────┘    └────────────────────┘    └─────┘    └─────────┘
     │              │              │                       │
     │              │              │                       │
  TFLite        Sherpa-ONNX    HA REST/WS             Sherpa-ONNX
  Model         or Vosk        or Local Fallback      (Piper) or eSpeak
```

#### STT Engine Interface

```kotlin
interface SttEngine {
    val engineName: String
    val isOffline: Boolean
    val isAvailable: StateFlow<Boolean>
    
    suspend fun initialise()
    fun startListening(): Flow<SttResult>
    fun stopListening()
    fun destroy()
}

sealed class SttResult {
    data class Partial(val text: String) : SttResult()
    data class Final(val text: String, val confidence: Float) : SttResult()
    data class Error(val message: String) : SttResult()
}
```

Implementations:
- `VoskSttEngine` — Offline, streaming, ~50MB model
- `SherpaOnnxSttEngine` — Offline, streaming (zipformer) or batch (whisper)
- `HaWyomingSttEngine` — Server-side via Assist Pipeline WebSocket

#### TTS Engine Interface

```kotlin
interface TtsEngine {
    val engineName: String
    val isOffline: Boolean
    
    suspend fun initialise()
    suspend fun speak(text: String): Flow<TtsState>
    fun stop()
    fun destroy()
}

sealed class TtsState {
    object Synthesising : TtsState()
    object Playing : TtsState()
    object Done : TtsState()
    data class Error(val message: String) : TtsState()
}
```

Implementations:
- `SherpaOnnxTtsEngine` — Piper VITS models, offline neural TTS
- `ESpeakTtsEngine` — Lightweight fallback, always available
- `HaWyomingTtsEngine` — Server-side via Assist Pipeline

#### Wake Word Engine

```kotlin
class WakeWordEngine(
    private val modelPath: String,
    private val sensitivity: Float = 0.5f
) {
    val detectionEvents: SharedFlow<WakeWordDetection>
    
    fun startDetection()
    fun stopDetection()
    fun updateSensitivity(sensitivity: Float)
}

data class WakeWordDetection(
    val keyword: String,
    val confidence: Float,
    val timestamp: Long
)
```

Uses LiteRT (TensorFlow Lite) to run the "Hey Jarvis" `.tflite` model. Audio is captured via Android's `AudioRecord` API at 16kHz mono, fed through the model in small frames.

---

### 3. Intent Processing

#### Primary: HA Conversation API

All voice commands are sent to Home Assistant's Conversation API as the primary intent engine. This means:
- **No custom sentence pattern matching needed for online mode**
- HA handles entity name resolution, area matching, and command parsing
- Supports whatever conversation agent the user has configured (built-in, LLM, custom)
- Multi-turn conversations via `conversation_id`

```kotlin
data class ConversationRequest(
    val text: String,
    val language: String,
    val conversationId: String? = null,
    val agentId: String? = null
)

data class ConversationResponse(
    val response: ResponseData,
    val conversationId: String
)

data class ResponseData(
    val speechPlain: String,      // Text to speak
    val responseType: ResponseType, // action_done, query_answer, error
    val data: ResponsePayload?
)
```

#### Fallback: Local Intent Matcher

When HA is unreachable, a simple local intent matcher handles basic commands:

```kotlin
class LocalIntentMatcher(
    private val entityCache: EntityCache
) {
    fun match(text: String): LocalIntent?
}

sealed class LocalIntent {
    data class TurnOn(val entityId: String) : LocalIntent()
    data class TurnOff(val entityId: String) : LocalIntent()
    data class Toggle(val entityId: String) : LocalIntent()
    data class QueryState(val entityId: String) : LocalIntent()
    data class SetValue(val entityId: String, val value: Any) : LocalIntent()
}
```

The local matcher uses:
- Cached entity friendly names + user-defined aliases
- Simple keyword extraction (turn on/off, open/close, lock/unlock)
- Fuzzy string matching (Levenshtein distance) for entity name resolution
- Number extraction for values

---

### 4. Entity Management (`data.db` + `domain.model`)

#### Entity Cache (Room Database)

```kotlin
@Entity(tableName = "entities")
data class CachedEntity(
    @PrimaryKey val entityId: String,
    val domain: String,
    val friendlyName: String,
    val state: String,
    val attributes: String,  // JSON blob
    val areaId: String?,
    val deviceId: String?,
    val isFavourite: Boolean = false,
    val isHidden: Boolean = false,
    val lastUpdated: Long
)

@Entity(tableName = "areas")
data class CachedArea(
    @PrimaryKey val areaId: String,
    val name: String,
    val floorId: String?
)

@Entity(tableName = "aliases")
data class EntityAlias(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityId: String,
    val alias: String,
    val pushedToHa: Boolean = false  // tracks sync state
)
```

#### Alias Management Flow

```
User taps "Add voice shortcut" on "Living Room End Light"
User types "reading light"
    │
    ├─> App calls WebSocket: config/entity_registry/update
    │   { entity_id: "light.living_room_end", aliases: [...existing, "reading light"] }
    │   HA Conversation API now understands "reading light" immediately
    │
    └─> App stores alias locally (pushedToHa = true)
        Used for offline fallback matching when HA unreachable

On "Sync from HA":
    ├─> Fetch entity registry with current aliases
    └─> Update local cache to match server state
```

#### Auto-Discovery Flow

```
App connects to HA
    │
    ├─> WebSocket: config/entity_registry/list_for_display
    ├─> WebSocket: config/area_registry/list  
    ├─> WebSocket: config/device_registry/list
    ├─> WebSocket: homeassistant/expose_entity/list
    │
    └─> Populate Room DB with all entities, areas, devices
         │
         └─> Subscribe to state_changed events
              │
              └─> Real-time updates to cached entities
```

No manual mapping required. The HA Conversation API handles entity resolution by name. The local cache is for:
- Dashboard display (real-time state)
- Offline fallback matching
- Favourites and UI organisation

---

### 5. Export / Import System (`data.export`)

#### Export Format

```json
{
  "schema_version": 1,
  "app_version": "1.0.0",
  "exported_at": "2026-08-06T18:00:00Z",
  "sections": ["connection", "favourites", "aliases", "quick_actions", "dashboard", "preferences"],
  "connection": {
    "local_url": "http://192.168.1.100:8123",
    "external_url": "https://ha.example.com",
    "home_ssid": "MyHomeWiFi"
  },
  "favourites": ["light.living_room", "switch.coffee_maker", "climate.thermostat"],
  "aliases": {
    "light.living_room_ceiling": ["big light", "main light"],
    "switch.plug_3": ["coffee maker", "coffee"]
  },
  "quick_actions": [
    {
      "name": "All Lights Off",
      "icon": "lightbulb_off",
      "domain": "light",
      "service": "turn_off",
      "target": {"area_id": "living_room"}
    }
  ],
  "dashboard": {
    "layout": "grid",
    "groups": [
      {"name": "Living Room", "entities": ["light.living_room", "media_player.tv"]}
    ]
  },
  "preferences": {
    "stt_engine": "sherpa_onnx",
    "tts_engine": "piper",
    "wake_word_enabled": true,
    "wake_word_sensitivity": 0.5,
    "theme": "system",
    "language": "en"
  }
}
```

#### Export/Import Logic

```kotlin
class ConfigExporter(
    private val configRepo: ConfigRepository,
    private val entityRepo: EntityRepository
) {
    suspend fun exportFull(): ExportData
    suspend fun exportPartial(sections: Set<ExportSection>): ExportData
    fun serialiseToJson(data: ExportData): String
}

class ConfigImporter(
    private val configRepo: ConfigRepository,
    private val entityRepo: EntityRepository
) {
    fun validateJson(json: String): ImportValidation
    suspend fun importReplace(data: ExportData)
    suspend fun importMerge(data: ExportData)
}

enum class ExportSection {
    CONNECTION, FAVOURITES, ALIASES, QUICK_ACTIONS, DASHBOARD, PREFERENCES
}
```

---

### 6. Background Services

#### Wake Word Service

```kotlin
@AndroidEntryPoint
class WakeWordService : LifecycleService() {
    // Foreground service with persistent notification
    // Holds AudioRecord + WakeWordEngine
    // On detection: broadcasts intent to activate voice UI
    // Respects DND settings
    // START_STICKY for auto-restart
}
```

#### WebSocket Service

```kotlin
@AndroidEntryPoint
class WebSocketService : LifecycleService() {
    // Maintains persistent WebSocket to HA
    // Handles authentication + reconnection with backoff
    // Dispatches state_changed events to EntityRepository
    // Supports notification delivery from HA
}
```

#### Notification Service

Notifications from HA are received via the persistent WebSocket connection (no FCM needed):
- Subscribe to `mobile_app` notification events
- Or use a custom event type the user configures in HA automations
- Display as standard Android notifications with channels and priority

---

### 7. Voice Interaction Sequence

```
User says "Hey Jarvis, turn off the living room lights"

1. WakeWordService detects "Hey Jarvis" via TFLite model
2. Service broadcasts wake detection → Voice UI activates
3. STT engine starts listening (Sherpa-ONNX streaming)
4. Partial results displayed: "turn off the..." → "turn off the living room..." → "turn off the living room lights"
5. VAD detects silence → final transcription produced
6. Text sent to HA Conversation API: POST conversation/process { text: "turn off the living room lights" }
7. HA resolves intent, calls light.turn_off for area "living_room"
8. Response received: { speech: "Turned off 3 lights", type: "action_done" }
9. TTS engine speaks response (Piper)
10. Dashboard updates via WebSocket state_changed events (lights show "off")
```

---

### 8. Offline Sequence

```
User says "Hey Jarvis, turn off the kitchen"
(HA server unreachable)

1. Wake word detected → STT activates (Sherpa-ONNX, works offline)
2. Transcription: "turn off the kitchen"
3. HA Conversation API call fails (connection refused)
4. Fallback to LocalIntentMatcher:
   - Extracts intent: "turn off"
   - Extracts target: "the kitchen" → fuzzy match → area "kitchen"
   - Resolves entities in kitchen area from cache
5. Commands queued: [light.turn_off(kitchen), switch.turn_off(kitchen)]
6. TTS speaks: "Kitchen lights and switches will be turned off when Home Assistant reconnects"
7. When WebSocket reconnects → queued commands executed → results reported
```

---

### 9. Data Storage

| Store | Technology | Contents |
|-------|-----------|----------|
| Entity cache | Room (SQLite) | All entity states, areas, devices |
| Conversation history | Room | Past voice interactions |
| Notification log | Room | Received HA notifications |
| Settings | Protobuf DataStore | Connection config, engine preferences, UI prefs |
| Favourites & aliases | Room | User-defined favourites, voice aliases |
| Quick actions | Protobuf DataStore | User-defined quick action buttons |
| Secrets | Android Keystore | HA access token |
| STT/TTS models | App internal storage | Downloaded Vosk/Sherpa-ONNX/Piper models |
| Wake word models | App assets + internal storage | Bundled Hey Jarvis + custom models |

---

### 10. Security Design

- **Token storage**: Android Keystore with hardware-backed encryption where available
- **Network**: HTTPS enforced for external URLs; HTTP permitted only on verified local network
- **Sensitive operations**: Biometric prompt (AndroidX Biometric) required for lock/alarm entities
- **No backup**: `android:allowBackup="false"`, `android:fullBackupContent="false"`
- **No telemetry**: Zero analytics, crash reporting, or phone-home behaviour
- **Audio privacy**: Raw audio discarded after STT; never persisted or transmitted beyond configured HA server
- **Export security**: Access token explicitly excluded from config exports

---

### 11. Key Dependencies (Pinned Versions)

```kotlin
// Core Android
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
implementation("androidx.activity:activity-compose:1.9.1")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.08.00"))
implementation("androidx.compose.material3:material3")

// DI
implementation("com.google.dagger:hilt-android:2.51.1")
kapt("com.google.dagger:hilt-compiler:2.51.1")

// Networking
implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
implementation("com.squareup.retrofit2:retrofit:2.11.0")

// Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// DataStore
implementation("androidx.datastore:datastore:1.1.1")
implementation("com.google.protobuf:protobuf-javalite:4.27.3")

// STT
implementation("com.k2fsa.sherpa:sherpa-onnx-android:x.y.z")
// OR
implementation("com.alphacephei:vosk-android:0.3.47")

// TTS  
// Sherpa-ONNX (same library as STT - includes TTS support)
// eSpeak-NG bundled as native library

// Wake Word
implementation("org.tensorflow:tensorflow-lite:2.16.1")  // LiteRT

// Serialisation
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

// Biometric
implementation("androidx.biometric:biometric:1.2.0-alpha05")
```

---

### 12. Build & Distribution

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Build**: Gradle 8.x with Kotlin DSL
- **CI**: GitHub Actions (lint, test, build APK)
- **Distribution**: F-Droid metadata + GitHub Releases APK
- **Signing**: Release signing for F-Droid reproducible builds
- **No proprietary dependencies**: All libraries must be FOSS-compatible

---

### 13. Screen / Navigation Map

```
Setup Wizard (first launch)
    │
    └─> Main App
         ├── Dashboard (home)
         │   ├── Favourites grid (quick actions)
         │   ├── Area cards with live state
         │   └── FAB: voice command button
         │
         ├── Voice (full-screen interaction)
         │   ├── Waveform / listening indicator
         │   ├── Transcription display
         │   └── Response + affected entities
         │
         ├── Entities (browser)
         │   ├── Search / filter
         │   ├── Group by area / domain
         │   └── Entity detail → favourite / alias / hide
         │
         └── Settings
             ├── Connection (URLs, token, network)
             ├── Voice engines (STT, TTS, wake word)
             ├── Dashboard layout
             ├── Notifications (subscriptions)
             ├── Security (sensitive entities, biometric)
             ├── Export / Import
             └── About
```

---

### 14. Error Handling Strategy

| Scenario | Behaviour |
|----------|-----------|
| HA unreachable | Switch to offline mode, show status indicator, queue commands |
| WebSocket drops | Exponential backoff reconnect (1s→2s→4s...60s max) |
| STT engine fails | Fall back to secondary engine, notify user |
| TTS engine fails | Display text response, skip audio |
| Wake word model missing | Disable wake word, prompt user to configure |
| Invalid HA token | Show re-authentication prompt, disable API calls |
| Conversation API returns `no_intent_match` | Inform user command not understood, suggest rephrasing |
| Entity not found | Suggest similar entity names from cache |

---

### 15. Testing Strategy

| Level | Scope | Tools |
|-------|-------|-------|
| Unit | ViewModels, Use Cases, Repositories, Intent Matcher | JUnit5, MockK, Turbine |
| Integration | HA Client (mock WebSocket), Room DB queries | JUnit5, OkHttp MockWebServer |
| UI | Screen composables, navigation flows | Compose Testing, Robolectric |
| E2E | Full voice pipeline with mocked audio | AndroidX Test, Espresso |

Key test areas:
- Local intent matcher (fuzzy matching accuracy)
- WebSocket reconnection logic
- Export/import round-trip fidelity
- Offline fallback behaviour
- Entity cache consistency
