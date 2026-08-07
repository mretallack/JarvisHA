# JarvisHA — v1.0 Implementation Tasks

## Phase 1: Project Scaffolding

- [x] 1.1 Create Android project with Gradle (Kotlin DSL, package `uk.org.retallack.jarvis`)
- [x] 1.2 Configure build.gradle: minSdk 26, targetSdk 34, Compose BOM, Material3
- [x] 1.3 Add Hilt DI setup (Application class, Hilt plugin)
- [x] 1.4 Add version catalog (libs.versions.toml) with all pinned dependencies
- [x] 1.5 Configure reproducible build settings (deterministic ZIP, no timestamps)
- [x] 1.6 Set up module/package structure per design doc
- [x] 1.7 Add ktlint and Android lint configuration
- [x] 1.8 Verify CI pipeline runs (lint + empty test pass + APK builds)
- [x] 1.9 Add JaCoCo coverage configuration

## Phase 2: HA Connection

- [x] 2.1 Create `ConnectionConfig` data class and Protobuf DataStore schema
- [x] 2.2 Implement `HaRestApi` (Retrofit + OkHttp) — `GET /api/`, `GET /api/config`
- [x] 2.3 Implement connection test (validate URL + token, return HA version)
- [x] 2.4 Implement `HaWebSocketClient` — connect, authenticate, handle reconnection with backoff
- [x] 2.5 Implement WebSocket event subscription (`state_changed`)
- [x] 2.6 Implement entity registry fetch via WebSocket (`config/entity_registry/list_for_display`)
- [x] 2.7 Implement area registry fetch (`config/area_registry/list`)
- [x] 2.8 Implement token storage in Android Keystore
- [x] 2.9 Write unit tests: connection validation, WebSocket auth flow
- [x] 2.10 Write integration tests: MockWebServer for REST, mock WebSocket for event subscription

## Phase 3: Entity Management

- [x] 3.1 Create Room database schema (entities, areas, aliases)
- [x] 3.2 Implement `EntityRepository` — populate from WebSocket registry, update on state_changed
- [x] 3.3 Implement entity favourites (star/unstar, persisted in Room)
- [x] 3.4 Implement alias management — push to HA via `config/entity_registry/update`
- [x] 3.5 Implement alias sync from HA (pull current aliases into local cache)
- [x] 3.6 Write unit tests: entity cache CRUD, alias push logic
- [x] 3.7 Write integration tests: Room queries, WebSocket registry fetch mock

## Phase 4: Voice Pipeline — STT

- [x] ~~4.1 Integrate Sherpa-ONNX Android library~~ (REFACTORED: now fallback only)
- [x] ~~4.2 Implement `SherpaOnnxSttEngine`~~ (REFACTORED: kept as fallback)
- [x] 4.3 Implement streaming recognition with partial results
- [x] 4.4 Implement VAD (voice activity detection) for end-of-speech
- [x] ~~4.5 Implement model manager~~ (REFACTORED: only needed for Sherpa-ONNX fallback)
- [x] 4.6 Implement model storage (app internal storage, check existence on launch)
- [x] 4.7 Write unit tests: engine state machine (init → listening → partial → final)
- [x] 4.8 Write integration test: mock audio input → verify transcription flow
- [x] 4.9 ~~**NEW** Implement `AndroidSpeechRecognizerSttEngine` using `android.speech.SpeechRecognizer` API~~ **OBSOLETE** — replaced by Sherpa-ONNX integrated approach (SpeechRecognizer has ERROR_INSUFFICIENT_PERMISSIONS on Android 13+)
- [x] 4.10 ~~**NEW** Enumerate installed recognition services, allow user to select in settings~~ **OBSOLETE** — replaced by Sherpa-ONNX integrated approach
- [x] 4.11 ~~**NEW** Handle case where no recognition service is installed (show install prompt)~~ **OBSOLETE** — replaced by Sherpa-ONNX integrated approach
- [x] 4.12 ~~**NEW** Write unit tests for SpeechRecognizer engine wrapper~~ **OBSOLETE** — replaced by Sherpa-ONNX integrated approach
- [x] 4.13 Add sherpa-onnx-android dependency to build.gradle (local AAR from GitHub releases)
- [x] 4.14 Implement real SherpaOnnxSttEngine with AudioRecord capture (16kHz mono)
- [x] 4.15 Implement streaming recognition with partial results via Flow
- [x] 4.16 Implement VAD (voice activity detection) for end-of-speech
- [x] 4.17 Implement ModelDownloader for STT model (HuggingFace, progress via Flow)
- [x] 4.18 Update setup wizard model download screen with real download logic
- [x] 4.19 Update VoiceScreen mic tap to use integrated STT (remove intent-based)
- [ ] 4.20 Test on device with real audio

## Phase 5: Voice Pipeline — TTS

- [x] ~~5.1 Implement `SherpaOnnxTtsEngine`~~ (OBSOLETE: replaced by Android TextToSpeech)
- [x] 5.2 Implement audio playback (AudioTrack or MediaPlayer, respect audio focus)
- [x] ~~5.3 Implement model download for TTS~~ (OBSOLETE: no models needed)
- [x] ~~5.4 Implement eSpeak-NG fallback~~ (OBSOLETE: system handles this)
- [x] 5.5 Implement TTS behaviour: speak on wake word activation, silent on mic tap
- [x] 5.6 Write unit tests: TTS state machine, audio focus handling
- [x] 5.7 **NEW** Implement `AndroidTtsEngine` using `android.speech.tts.TextToSpeech` API
- [x] 5.8 **NEW** Implement speech rate and pitch configuration
- [x] 5.9 **NEW** Handle case where no TTS engine is installed (fall back to text display)
- [x] 5.10 **NEW** Write unit tests for Android TTS wrapper

## Phase 6: Voice Pipeline — Wake Word

- [x] 6.1 Implement `WakeWordEngine` using LiteRT (TFLite) with "Hey Jarvis" model
- [x] 6.2 Bundle "Hey Jarvis" TFLite model in APK assets
- [x] 6.3 Implement `WakeWordService` (foreground service, persistent notification)
- [x] 6.4 Implement sensitivity configuration
- [x] 6.5 Implement quiet hours (schedule-based disable)
- [x] 6.6 Implement wake word → activate STT handoff
- [x] 6.7 Implement cooldown period (prevent re-trigger from echo)
- [x] 6.8 Write unit tests: detection callback, quiet hours logic, cooldown
- [ ] 6.9 Write integration test: service lifecycle (start, detect, stop)

## Phase 7: Intent Processing (HA Conversation API)

- [x] 7.1 Implement `ConversationRepository` — send text to `conversation/process` via WebSocket
- [x] 7.2 Handle response types: `action_done`, `query_answer`, `error`
- [x] 7.3 Implement multi-turn: track `conversation_id`, re-activate STT on `continue_conversation`
- [x] 7.4 Implement agent selection (default `conversation.home_assistant`, configurable)
- [x] 7.5 Implement error handling: `no_valid_targets`, `no_intent_match`, connection errors
- [x] 7.6 Write unit tests: response parsing, multi-turn state, error mapping
- [x] 7.7 Write integration tests: MockWebServer conversation round-trips

## Phase 8: UI — Setup Wizard

- [x] 8.1 Create wizard navigation (Compose NavHost, step-by-step)
- [x] 8.2 Welcome screen
- [x] 8.3 Connection screen (URL + token input, test button, success/error feedback)
- [x] 8.4 Model download screen (consent explanation, progress bar, skip option)
- [x] 8.5 Wake word screen (enable toggle, battery warning, sensitivity)
- [x] 8.6 Quiet hours screen (time picker, days selection) — only if wake word enabled
- [x] 8.7 Done screen (summary, "say Hey Jarvis or tap mic")
- [x] 8.8 Persist "setup complete" flag, skip wizard on subsequent launches
- [ ] 8.9 Write UI tests: wizard flow navigation, validation

## Phase 9: UI — Voice Tab (Main Screen)

- [x] 9.1 Create Voice screen with chat-style message list
- [x] 9.2 Implement conversation history ViewModel (Room-backed)
- [x] 9.3 Implement mic FAB button with state indicator (idle/listening/processing/speaking)
- [x] 9.4 Implement real-time partial STT text display
- [x] 9.5 Implement response rendering (HA speech text, affected entities)
- [x] 9.6 Make entity names in responses tappable → navigate to entity detail
- [x] 9.7 Implement waveform/pulse animation during listening
- [x] 9.8 Implement error state display ("HA unavailable", "command not understood")
- [ ] 9.9 Write UI tests: message display, state transitions

## Phase 10: UI — Entities Tab

- [x] 10.1 Create entity browser screen (list with search + filter)
- [x] 10.2 Implement area/domain grouping
- [x] 10.3 Implement entity detail screen (name, state, area, aliases)
- [x] 10.4 Implement "Add voice shortcut" UI (text field → push alias to HA)
- [x] 10.5 Implement star/favourite toggle
- [x] 10.6 Implement alias sync button ("Sync from HA")
- [ ] 10.7 Write UI tests: search, filter, alias add flow

## Phase 11: UI — Settings

- [x] 11.1 Create settings navigation and screens
- [x] 11.2 Connection settings (edit URL, token, test)
- [x] 11.3 Voice settings (STT model info, TTS voice info, download status)
- [x] 11.4 Wake word settings (enable/disable, sensitivity slider, quiet hours)
- [x] 11.5 Conversation agent selector (list agents from HA, show latency indication)
- [x] 11.6 Security settings (sensitive domain list, biometric toggle)
- [x] 11.7 Theme selection (system/dark/light)
- [x] 11.8 About screen (version, licenses, links)

## Phase 12: Export / Import

- [x] 12.1 Implement `ConfigExporter` — serialise settings/favourites/aliases to JSON
- [x] 12.2 Implement `ConfigImporter` — validate, preview, merge/replace
- [x] 12.3 Implement export UI (SAF file picker + share intent)
- [x] 12.4 Implement import UI (file picker, preview, confirm)
- [x] 12.5 Exclude access token from export
- [x] 12.6 Include schema version for forward compatibility
- [x] 12.7 Write unit tests: serialisation round-trip, schema validation
- [x] 12.8 Write integration tests: full export → import → verify state

## Phase 13: Lock Screen & Security

- [x] 13.1 Implement sensitive domain detection (lock, alarm_control_panel, cover)
- [x] 13.2 Integrate AndroidX Biometric library
- [x] 13.3 Implement biometric prompt before sensitive service calls
- [x] 13.4 Implement configurable sensitive domain list in settings
- [x] 13.5 Implement screen wake on wake word detection (when screen off)
- [x] 13.6 Write unit tests: sensitive domain check, biometric gate logic

## Phase 14: Widget

- [x] 14.1 Create mic-button AppWidget (single button, launches voice mode)
- [x] 14.2 Implement widget configuration (size, appearance)
- [ ] 14.3 Test widget on various launchers

## Phase 15: Integration & Polish

- [ ] 15.1 End-to-end testing: wake word → STT → HA API → TTS → chat history
- [ ] 15.2 Battery profiling: measure wake word service drain
- [ ] 15.3 Memory profiling: ensure < 150MB RAM usage
- [ ] 15.4 Test on degoogled ROM (LineageOS or GrapheneOS emulator/device)
- [ ] 15.5 Test with no network (verify "HA unavailable" gracefully)
- [x] 15.6 Add content descriptions to all interactive elements (accessibility)
- [ ] 15.7 Verify system font scaling works correctly
- [x] 15.8 Add app icon and splash screen
- [ ] 15.9 Update fastlane metadata (screenshots, description)
- [ ] 15.10 Verify reproducible build (CI passes reproducibility check)
- [ ] 15.11 Tag `v1.0.0` release

## Phase 16: Release

- [ ] 16.1 Create signed release APK
- [ ] 16.2 Create GitHub Release with APK and changelog
- [ ] 16.3 Submit to F-Droid (merge request to fdroiddata with metadata)
- [ ] 16.4 Update README with installation instructions
