# JarvisHA — v1.0 Implementation Tasks

## Phase 1: Project Scaffolding

- [ ] 1.1 Create Android project with Gradle (Kotlin DSL, package `uk.org.retallack.jarvis`)
- [ ] 1.2 Configure build.gradle: minSdk 26, targetSdk 34, Compose BOM, Material3
- [ ] 1.3 Add Hilt DI setup (Application class, Hilt plugin)
- [ ] 1.4 Add version catalog (libs.versions.toml) with all pinned dependencies
- [ ] 1.5 Configure reproducible build settings (deterministic ZIP, no timestamps)
- [ ] 1.6 Set up module/package structure per design doc
- [ ] 1.7 Add ktlint and Android lint configuration
- [ ] 1.8 Verify CI pipeline runs (lint + empty test pass + APK builds)
- [ ] 1.9 Add JaCoCo coverage configuration

## Phase 2: HA Connection

- [ ] 2.1 Create `ConnectionConfig` data class and Protobuf DataStore schema
- [ ] 2.2 Implement `HaRestApi` (Retrofit + OkHttp) — `GET /api/`, `GET /api/config`
- [ ] 2.3 Implement connection test (validate URL + token, return HA version)
- [ ] 2.4 Implement `HaWebSocketClient` — connect, authenticate, handle reconnection with backoff
- [ ] 2.5 Implement WebSocket event subscription (`state_changed`)
- [ ] 2.6 Implement entity registry fetch via WebSocket (`config/entity_registry/list_for_display`)
- [ ] 2.7 Implement area registry fetch (`config/area_registry/list`)
- [ ] 2.8 Implement token storage in Android Keystore
- [ ] 2.9 Write unit tests: connection validation, WebSocket auth flow
- [ ] 2.10 Write integration tests: MockWebServer for REST, mock WebSocket for event subscription

## Phase 3: Entity Management

- [ ] 3.1 Create Room database schema (entities, areas, aliases)
- [ ] 3.2 Implement `EntityRepository` — populate from WebSocket registry, update on state_changed
- [ ] 3.3 Implement entity favourites (star/unstar, persisted in Room)
- [ ] 3.4 Implement alias management — push to HA via `config/entity_registry/update`
- [ ] 3.5 Implement alias sync from HA (pull current aliases into local cache)
- [ ] 3.6 Write unit tests: entity cache CRUD, alias push logic
- [ ] 3.7 Write integration tests: Room queries, WebSocket registry fetch mock

## Phase 4: Voice Pipeline — STT

- [ ] 4.1 Integrate Sherpa-ONNX Android library
- [ ] 4.2 Implement `SherpaOnnxSttEngine` (implements `SttEngine` interface)
- [ ] 4.3 Implement streaming recognition with partial results
- [ ] 4.4 Implement VAD (voice activity detection) for end-of-speech
- [ ] 4.5 Implement model manager — download from upstream with progress, consent UI
- [ ] 4.6 Implement model storage (app internal storage, check existence on launch)
- [ ] 4.7 Write unit tests: engine state machine (init → listening → partial → final)
- [ ] 4.8 Write integration test: mock audio input → verify transcription flow

## Phase 5: Voice Pipeline — TTS

- [ ] 5.1 Implement `SherpaOnnxTtsEngine` with Piper voice models
- [ ] 5.2 Implement audio playback (AudioTrack or MediaPlayer, respect audio focus)
- [ ] 5.3 Implement model download for TTS (bundled in same download as STT or separate)
- [ ] 5.4 Implement eSpeak-NG fallback (bundled, no download needed)
- [ ] 5.5 Implement TTS behaviour: speak on wake word activation, silent on mic tap
- [ ] 5.6 Write unit tests: TTS state machine, audio focus handling

## Phase 6: Voice Pipeline — Wake Word

- [ ] 6.1 Implement `WakeWordEngine` using LiteRT (TFLite) with "Hey Jarvis" model
- [ ] 6.2 Bundle "Hey Jarvis" TFLite model in APK assets
- [ ] 6.3 Implement `WakeWordService` (foreground service, persistent notification)
- [ ] 6.4 Implement sensitivity configuration
- [ ] 6.5 Implement quiet hours (schedule-based disable)
- [ ] 6.6 Implement wake word → activate STT handoff
- [ ] 6.7 Implement cooldown period (prevent re-trigger from echo)
- [ ] 6.8 Write unit tests: detection callback, quiet hours logic, cooldown
- [ ] 6.9 Write integration test: service lifecycle (start, detect, stop)

## Phase 7: Intent Processing (HA Conversation API)

- [ ] 7.1 Implement `ConversationRepository` — send text to `conversation/process` via WebSocket
- [ ] 7.2 Handle response types: `action_done`, `query_answer`, `error`
- [ ] 7.3 Implement multi-turn: track `conversation_id`, re-activate STT on `continue_conversation`
- [ ] 7.4 Implement agent selection (default `conversation.home_assistant`, configurable)
- [ ] 7.5 Implement error handling: `no_valid_targets`, `no_intent_match`, connection errors
- [ ] 7.6 Write unit tests: response parsing, multi-turn state, error mapping
- [ ] 7.7 Write integration tests: MockWebServer conversation round-trips

## Phase 8: UI — Setup Wizard

- [ ] 8.1 Create wizard navigation (Compose NavHost, step-by-step)
- [ ] 8.2 Welcome screen
- [ ] 8.3 Connection screen (URL + token input, test button, success/error feedback)
- [ ] 8.4 Model download screen (consent explanation, progress bar, skip option)
- [ ] 8.5 Wake word screen (enable toggle, battery warning, sensitivity)
- [ ] 8.6 Quiet hours screen (time picker, days selection) — only if wake word enabled
- [ ] 8.7 Done screen (summary, "say Hey Jarvis or tap mic")
- [ ] 8.8 Persist "setup complete" flag, skip wizard on subsequent launches
- [ ] 8.9 Write UI tests: wizard flow navigation, validation

## Phase 9: UI — Voice Tab (Main Screen)

- [ ] 9.1 Create Voice screen with chat-style message list
- [ ] 9.2 Implement conversation history ViewModel (Room-backed)
- [ ] 9.3 Implement mic FAB button with state indicator (idle/listening/processing/speaking)
- [ ] 9.4 Implement real-time partial STT text display
- [ ] 9.5 Implement response rendering (HA speech text, affected entities)
- [ ] 9.6 Make entity names in responses tappable → navigate to entity detail
- [ ] 9.7 Implement waveform/pulse animation during listening
- [ ] 9.8 Implement error state display ("HA unavailable", "command not understood")
- [ ] 9.9 Write UI tests: message display, state transitions

## Phase 10: UI — Entities Tab

- [ ] 10.1 Create entity browser screen (list with search + filter)
- [ ] 10.2 Implement area/domain grouping
- [ ] 10.3 Implement entity detail screen (name, state, area, aliases)
- [ ] 10.4 Implement "Add voice shortcut" UI (text field → push alias to HA)
- [ ] 10.5 Implement star/favourite toggle
- [ ] 10.6 Implement alias sync button ("Sync from HA")
- [ ] 10.7 Write UI tests: search, filter, alias add flow

## Phase 11: UI — Settings

- [ ] 11.1 Create settings navigation and screens
- [ ] 11.2 Connection settings (edit URL, token, test)
- [ ] 11.3 Voice settings (STT model info, TTS voice info, download status)
- [ ] 11.4 Wake word settings (enable/disable, sensitivity slider, quiet hours)
- [ ] 11.5 Conversation agent selector (list agents from HA, show latency indication)
- [ ] 11.6 Security settings (sensitive domain list, biometric toggle)
- [ ] 11.7 Theme selection (system/dark/light)
- [ ] 11.8 About screen (version, licenses, links)

## Phase 12: Export / Import

- [ ] 12.1 Implement `ConfigExporter` — serialise settings/favourites/aliases to JSON
- [ ] 12.2 Implement `ConfigImporter` — validate, preview, merge/replace
- [ ] 12.3 Implement export UI (SAF file picker + share intent)
- [ ] 12.4 Implement import UI (file picker, preview, confirm)
- [ ] 12.5 Exclude access token from export
- [ ] 12.6 Include schema version for forward compatibility
- [ ] 12.7 Write unit tests: serialisation round-trip, schema validation
- [ ] 12.8 Write integration tests: full export → import → verify state

## Phase 13: Lock Screen & Security

- [ ] 13.1 Implement sensitive domain detection (lock, alarm_control_panel, cover)
- [ ] 13.2 Integrate AndroidX Biometric library
- [ ] 13.3 Implement biometric prompt before sensitive service calls
- [ ] 13.4 Implement configurable sensitive domain list in settings
- [ ] 13.5 Implement screen wake on wake word detection (when screen off)
- [ ] 13.6 Write unit tests: sensitive domain check, biometric gate logic

## Phase 14: Widget

- [ ] 14.1 Create mic-button AppWidget (single button, launches voice mode)
- [ ] 14.2 Implement widget configuration (size, appearance)
- [ ] 14.3 Test widget on various launchers

## Phase 15: Integration & Polish

- [ ] 15.1 End-to-end testing: wake word → STT → HA API → TTS → chat history
- [ ] 15.2 Battery profiling: measure wake word service drain
- [ ] 15.3 Memory profiling: ensure < 150MB RAM usage
- [ ] 15.4 Test on degoogled ROM (LineageOS or GrapheneOS emulator/device)
- [ ] 15.5 Test with no network (verify "HA unavailable" gracefully)
- [ ] 15.6 Add content descriptions to all interactive elements (accessibility)
- [ ] 15.7 Verify system font scaling works correctly
- [ ] 15.8 Add app icon and splash screen
- [ ] 15.9 Update fastlane metadata (screenshots, description)
- [ ] 15.10 Verify reproducible build (CI passes reproducibility check)
- [ ] 15.11 Tag `v1.0.0` release

## Phase 16: Release

- [ ] 16.1 Create signed release APK
- [ ] 16.2 Create GitHub Release with APK and changelog
- [ ] 16.3 Submit to F-Droid (merge request to fdroiddata with metadata)
- [ ] 16.4 Update README with installation instructions
