# JarvisHA - Requirements

## Overview

JarvisHA is an Android voice assistant application purpose-built for controlling Home Assistant. It operates entirely without Google Play Services, making it suitable for degoogled phones (LineageOS, GrapheneOS, CalyxOS). The app prioritises privacy through local processing, communicates only with the user's own Home Assistant instance, and is distributed via F-Droid or direct APK sideloading.

## Guiding Principles

- **No Google Dependencies**: Zero reliance on Google Play Services, Google STT, Google TTS, or any Google APIs
- **Privacy-First**: All voice processing local by default; network traffic only to user's own HA instance
- **Offline-Capable**: Core functionality (wake word, STT, TTS) works without network connectivity
- **Open Source**: All components must be FOSS-compatible (no proprietary blobs like Porcupine)
- **Accessible**: Follows Android accessibility guidelines, usable by screen reader users
- **No Dicio Code Copying**: This is NOT a fork of Dicio. No source code may be copied from the dicio-android repository. Third-party libraries that Dicio also uses (Vosk, Sherpa-ONNX, OpenWakeWord, OkHttp, etc.) are fine — they are independent projects. The app must be written from scratch.
- **Easy Configuration**: Entity mapping and setup must be dramatically simpler than Dicio's one-at-a-time manual mapping approach. Auto-discovery with bulk operations is required.
- **Portable Configuration**: All settings must be exportable/importable for backup, migration, and sharing between devices.

---

## 1. Connection & Authentication

### User Story 1.1: Initial Setup

As a user, I want to connect JarvisHA to my Home Assistant instance so that I can control my smart home by voice.

**Acceptance Criteria:**

- [ ] User can enter HA instance URL (local IP or external domain)
- [ ] User can authenticate via long-lived access token
- [ ] App validates the connection by calling `GET /api/` and displays HA version on success
- [ ] App fetches and caches HA configuration via `GET /api/config`
- [ ] Setup wizard guides user through the complete onboarding flow

**Requirements (EARS):**

- WHEN the user enters a Home Assistant URL and access token, THE SYSTEM SHALL validate the connection by querying the HA REST API health endpoint and display the connected HA version.
- WHEN authentication fails (401/403 response), THE SYSTEM SHALL display a clear error message indicating invalid credentials and prompt the user to re-enter the token.
- WHEN the HA instance URL is unreachable, THE SYSTEM SHALL display a timeout error within 10 seconds and suggest checking network connectivity.

### User Story 1.2: Local & Remote Access

As a user, I want to configure both local and remote HA URLs so that the app works on my home network and when away.

**Acceptance Criteria:**

- [ ] User can configure a local URL (e.g., `http://192.168.1.x:8123`)
- [ ] User can configure an external URL (e.g., `https://ha.example.com`)
- [ ] App automatically detects which network it's on and selects the appropriate URL
- [ ] Local connection is preferred when available (lower latency, no external exposure)

**Requirements (EARS):**

- WHEN the device is connected to the configured home network (by SSID or subnet), THE SYSTEM SHALL use the local HA URL for all API communication.
- WHEN the device is NOT on the home network AND an external URL is configured, THE SYSTEM SHALL use the external HA URL.
- WHEN the device transitions between networks, THE SYSTEM SHALL re-evaluate the connection target within 5 seconds.
- THE SYSTEM SHALL support both HTTP (local) and HTTPS (remote) connections.

### User Story 1.3: WebSocket Connection

As a user, I want real-time updates from my HA instance so that the app always shows current entity states.

**Acceptance Criteria:**

- [ ] App maintains a persistent WebSocket connection to HA
- [ ] WebSocket authenticates using the stored access token
- [ ] App subscribes to `state_changed` events for real-time entity updates
- [ ] Connection auto-reconnects with exponential backoff on disconnection
- [ ] Ping/pong heartbeat keeps the connection alive

**Requirements (EARS):**

- WHEN the app is in the foreground, THE SYSTEM SHALL maintain an authenticated WebSocket connection to `ws(s)://HOST/api/websocket`.
- WHEN the WebSocket connection drops, THE SYSTEM SHALL attempt reconnection with exponential backoff (1s, 2s, 4s, 8s, max 60s).
- WHEN a `state_changed` event is received, THE SYSTEM SHALL update the local entity cache within 100ms.
- THE SYSTEM SHALL send a WebSocket ping every 30 seconds and treat missing pong as a disconnection.

---

## 2. Voice Input (Speech-to-Text)

### User Story 2.1: Local STT with Vosk

As a user, I want offline speech recognition so that voice commands work without internet access.

**Acceptance Criteria:**

- [ ] App bundles or downloads Vosk small model (~50MB) for English
- [ ] Real-time streaming recognition with partial results displayed as user speaks
- [ ] Recognition completes within 500ms of user stopping speech
- [ ] Works fully offline with no network connectivity
- [ ] User can download additional language models

**Requirements (EARS):**

- WHEN the user selects Vosk as the STT engine, THE SYSTEM SHALL perform all speech recognition locally on the device without any network requests.
- WHEN Vosk is active and the user speaks, THE SYSTEM SHALL display partial recognition results in real-time (streaming mode).
- WHEN voice activity ends (VAD detects silence), THE SYSTEM SHALL produce a final transcription within 500ms.
- THE SYSTEM SHALL support downloading and managing multiple Vosk language models.

### User Story 2.2: Local STT with Sherpa-ONNX

As a user, I want high-accuracy offline speech recognition using modern models so that my commands are understood correctly.

**Acceptance Criteria:**

- [ ] App integrates Sherpa-ONNX library for on-device STT
- [ ] Supports streaming models (zipformer) for real-time recognition
- [ ] Supports non-streaming models (Whisper tiny/base) for higher accuracy
- [ ] User can select between speed-optimised and accuracy-optimised models
- [ ] Model manager allows downloading/deleting models

**Requirements (EARS):**

- WHEN the user selects Sherpa-ONNX as the STT engine, THE SYSTEM SHALL perform speech recognition using the selected ONNX model locally on the device.
- WHEN a streaming model (zipformer) is selected, THE SYSTEM SHALL provide real-time partial transcription results as the user speaks.
- WHEN a non-streaming model (Whisper) is selected, THE SYSTEM SHALL process the complete utterance after VAD detects end-of-speech and return results within 3 seconds on a mid-range device.
- THE SYSTEM SHALL provide a model manager UI showing available models with size, language, and accuracy/speed trade-off information.

### User Story 2.3: Server-side STT via HA Wyoming/Assist Pipeline

As a user, I want to optionally use my HA server's faster-whisper for STT so I get the best accuracy when on my home network.

**Acceptance Criteria:**

- [ ] App can stream audio to HA via the Assist Pipeline WebSocket API (`assist_pipeline/run` with `start_stage: "stt"`)
- [ ] Audio streamed as binary WebSocket frames with handler ID prefix
- [ ] Receives `stt-end` event with transcribed text
- [ ] Falls back to local STT if HA connection is unavailable
- [ ] User can configure preferred pipeline in settings

**Requirements (EARS):**

- WHEN the user selects HA Wyoming/Pipeline as the STT engine AND the HA WebSocket connection is active, THE SYSTEM SHALL stream microphone audio to HA using `assist_pipeline/run` with `start_stage: "stt"` and `end_stage: "intent"`.
- WHEN audio is being streamed, THE SYSTEM SHALL prefix each binary WebSocket frame with the `stt_binary_handler_id` received in the `run-start` event.
- WHEN the HA server returns an `stt-end` event, THE SYSTEM SHALL extract the transcribed text from `stt_output.text`.
- WHEN the HA connection is unavailable AND server-side STT is selected, THE SYSTEM SHALL automatically fall back to the configured local STT engine and notify the user of the fallback.

### User Story 2.4: STT Engine Selection

As a user, I want to choose and switch between STT engines so I can balance accuracy, speed, and network usage.

**Acceptance Criteria:**

- [ ] Settings screen lists all available STT engines (Vosk, Sherpa-ONNX, HA Wyoming)
- [ ] User can set a primary and fallback STT engine
- [ ] App indicates which engines are available offline vs require network
- [ ] Switching engines does not require app restart

**Requirements (EARS):**

- THE SYSTEM SHALL provide a settings UI to select the primary STT engine from: Vosk, Sherpa-ONNX (streaming), Sherpa-ONNX (Whisper), HA Wyoming Pipeline.
- THE SYSTEM SHALL allow configuration of a fallback STT engine that activates when the primary engine is unavailable.
- WHEN the primary STT engine fails to initialise or becomes unavailable, THE SYSTEM SHALL switch to the fallback engine within 2 seconds and display a notification.
- THE SYSTEM SHALL clearly label each engine option with: offline capability, expected accuracy level, and model size requirements.

---

## 3. Voice Output (Text-to-Speech)

### User Story 3.1: Local TTS with Piper (via Sherpa-ONNX)

As a user, I want natural-sounding voice responses that work offline so the assistant feels conversational without requiring internet.

**Acceptance Criteria:**

- [ ] App integrates Sherpa-ONNX TTS with Piper VITS voice models
- [ ] At least one English voice model bundled or auto-downloaded on first use
- [ ] Speech synthesis completes within 1 second for typical response sentences
- [ ] Audio plays through device speaker or connected Bluetooth audio
- [ ] Multiple voice models available for download (different languages, quality levels)

**Requirements (EARS):**

- WHEN a text response is received from the intent engine, THE SYSTEM SHALL synthesise speech locally using the selected Piper voice model via Sherpa-ONNX.
- WHEN Piper TTS is active, THE SYSTEM SHALL produce audio output within 1 second for sentences under 50 words on a mid-range device.
- THE SYSTEM SHALL support downloading additional Piper voice models from HuggingFace, showing model name, language, quality tier (low/medium/high), and file size.
- THE SYSTEM SHALL play synthesised audio through the active audio output (speaker, Bluetooth, wired headset) respecting Android audio focus.

### User Story 3.2: Server-side TTS via HA Wyoming/Assist Pipeline

As a user, I want to optionally use my HA server's TTS engine for the highest quality voice responses.

**Acceptance Criteria:**

- [ ] App can request TTS from HA via Assist Pipeline (`end_stage: "tts"`)
- [ ] Receives TTS audio URL from `tts-end` event and plays it
- [ ] Falls back to local TTS if HA connection is unavailable
- [ ] Supports streaming TTS playback (start playing before full audio is downloaded)

**Requirements (EARS):**

- WHEN HA Wyoming TTS is selected AND the intent is processed via HA Conversation API, THE SYSTEM SHALL request TTS by setting `end_stage: "tts"` in the pipeline run.
- WHEN a `tts-end` event is received with a URL, THE SYSTEM SHALL download and play the audio file from the provided URL.
- WHEN the HA connection is unavailable AND server-side TTS is selected, THE SYSTEM SHALL fall back to local Piper TTS.
- THE SYSTEM SHALL begin audio playback as soon as sufficient data is buffered (streaming playback).

### User Story 3.3: eSpeak Fallback TTS

As a user, I want a lightweight fallback TTS that works immediately without downloading models, even on low-storage devices.

**Acceptance Criteria:**

- [ ] eSpeak-NG integrated as a zero-download fallback TTS engine
- [ ] Activates automatically if no Piper models are downloaded yet
- [ ] Supports 100+ languages out of the box
- [ ] Minimal storage footprint (< 5MB)

**Requirements (EARS):**

- THE SYSTEM SHALL include eSpeak-NG as a built-in fallback TTS engine requiring no additional downloads.
- WHEN no Piper voice model is available (first launch or storage cleared), THE SYSTEM SHALL use eSpeak-NG for voice output until a Piper model is downloaded.
- WHEN the user explicitly selects eSpeak-NG as their TTS engine, THE SYSTEM SHALL use it regardless of Piper model availability.

### User Story 3.4: TTS Engine Selection

As a user, I want to choose my preferred TTS engine and voice so I can customise the assistant's personality.

**Acceptance Criteria:**

- [ ] Settings screen lists all available TTS engines (Piper/Sherpa-ONNX, HA Wyoming, eSpeak-NG)
- [ ] User can select voice within each engine (e.g., different Piper voices)
- [ ] User can adjust speech rate and pitch
- [ ] Audio preview (test sentence) available in settings

**Requirements (EARS):**

- THE SYSTEM SHALL provide a TTS settings UI with engine selection, voice selection, speech rate (0.5x–2.0x), and pitch adjustment.
- WHEN the user taps "Test Voice", THE SYSTEM SHALL synthesise and play a sample sentence using the currently selected engine and voice.
- THE SYSTEM SHALL remember the last selected TTS engine and voice across app restarts.

---

## 4. Wake Word Detection

### User Story 4.1: On-Device Wake Word with OpenWakeWord

As a user, I want to activate the assistant hands-free by saying "Hey Jarvis" so I don't need to touch my phone.

**Acceptance Criteria:**

- [ ] App bundles a pre-trained "Hey Jarvis" TFLite model for OpenWakeWord
- [ ] Model loaded via LiteRT (TensorFlow Lite runtime) — no full TensorFlow dependency
- [ ] Detection latency < 500ms from end of wake word utterance
- [ ] False-accept rate < 1 per 12 hours of background listening
- [ ] Wake word detection runs in a foreground service with minimal battery drain

**Requirements (EARS):**

- THE SYSTEM SHALL bundle a "Hey Jarvis" OpenWakeWord TFLite model as the default wake word.
- WHEN the wake word service is enabled, THE SYSTEM SHALL continuously monitor microphone audio for the configured wake word using an on-device OpenWakeWord TFLite model via LiteRT.
- WHEN the wake word is detected, THE SYSTEM SHALL activate the STT listening mode within 500ms and provide audible/haptic feedback.
- THE SYSTEM SHALL consume less than 3% battery per hour during continuous wake word monitoring.
- THE SYSTEM SHALL run wake word detection in an Android foreground service with a persistent notification indicating listening status.

### User Story 4.2: Custom Wake Words

As a user, I want to add alternative wake words so I can personalise my assistant.

**Acceptance Criteria:**

- [ ] User can import custom TFLite wake word models (trained via openWakeWord tooling)
- [ ] Multiple wake words can be active simultaneously
- [ ] Settings display detection sensitivity slider
- [ ] Default "Hey Jarvis" model always available

**Requirements (EARS):**

- THE SYSTEM SHALL bundle the "Hey Jarvis" TFLite model as the default and always-available wake word.
- WHEN the user imports a custom TFLite wake word model file, THE SYSTEM SHALL validate the model format and add it to the available wake words.
- THE SYSTEM SHALL support activating up to 3 wake word models simultaneously.
- THE SYSTEM SHALL provide a sensitivity slider (0.0–1.0) to adjust the detection threshold, with a default value of 0.5.

### User Story 4.3: Wake Word Background Service

As a user, I want wake word detection to work even when the app is in the background or the screen is off.

**Acceptance Criteria:**

- [ ] Foreground service keeps wake word detection active when app is backgrounded
- [ ] Works with screen off and device locked
- [ ] User can enable/disable background listening from quick settings tile
- [ ] Service survives app process death (restarts automatically)
- [ ] Respects Do Not Disturb and focus modes

**Requirements (EARS):**

- WHEN the user enables background wake word detection, THE SYSTEM SHALL start an Android foreground service that persists across app lifecycle events.
- WHEN the device screen is off AND wake word is detected, THE SYSTEM SHALL wake the screen and show the listening UI.
- WHEN the device is in Do Not Disturb mode AND "respect DND" is enabled in settings, THE SYSTEM SHALL suppress wake word detection.
- THE SYSTEM SHALL provide a Quick Settings tile to toggle wake word listening on/off.
- WHEN the wake word service is terminated by the system, THE SYSTEM SHALL automatically restart it within 10 seconds using Android's `START_STICKY` mechanism.

---

## 5. Entity Control by Type

### User Story 5.1: Lights

As a user, I want to control my lights by voice including brightness, colour, and colour temperature.

**Acceptance Criteria:**

- [ ] Turn on/off/toggle lights by name or area
- [ ] Set brightness by percentage ("dim the kitchen to 30%")
- [ ] Set colour by name ("make it red") or RGB values
- [ ] Set colour temperature ("set to warm white")
- [ ] Activate light effects ("set disco effect")
- [ ] Control transition time

**Requirements (EARS):**

- WHEN the user issues a light on/off/toggle command, THE SYSTEM SHALL call `light.turn_on`, `light.turn_off`, or `light.toggle` with the resolved entity target.
- WHEN the user specifies a brightness percentage, THE SYSTEM SHALL include `brightness` (mapped from 0–100% to 1–255) in the service call data.
- WHEN the user specifies a colour name, THE SYSTEM SHALL resolve it to `rgb_color` and include it in the service call.
- WHEN the user specifies colour temperature (warm/cool/neutral or Kelvin value), THE SYSTEM SHALL include `color_temp_kelvin` in the service call, clamped to the entity's supported range.

### User Story 5.2: Switches

As a user, I want to control simple on/off devices like plugs and switches by voice.

**Acceptance Criteria:**

- [ ] Turn on/off/toggle switches by name or area
- [ ] Query switch state ("is the coffee maker on?")

**Requirements (EARS):**

- WHEN the user issues a switch command, THE SYSTEM SHALL call `switch.turn_on`, `switch.turn_off`, or `switch.toggle` with the target entity.
- WHEN the user queries a switch state, THE SYSTEM SHALL read the entity state and respond with a natural language confirmation.

### User Story 5.3: Media Players

As a user, I want to control my media players including playback, volume, and source selection.

**Acceptance Criteria:**

- [ ] Play/pause/stop/next/previous track
- [ ] Set volume by percentage or relative (up/down)
- [ ] Mute/unmute
- [ ] Select input source with fuzzy matching
- [ ] Query now-playing information (title, artist)
- [ ] Select sound mode

**Requirements (EARS):**

- WHEN the user issues a media playback command, THE SYSTEM SHALL call the appropriate `media_player` service (media_play, media_pause, media_stop, media_next_track, media_previous_track).
- WHEN the user sets volume to a percentage, THE SYSTEM SHALL call `media_player.volume_set` with `volume_level` mapped from 0–100% to 0.0–1.0.
- WHEN the user requests a source change, THE SYSTEM SHALL fuzzy-match the spoken source name against the entity's `source_list` attribute and call `media_player.select_source` with the best match.
- WHEN the user asks "what's playing", THE SYSTEM SHALL read `media_title`, `media_artist`, and `media_album_name` attributes and synthesise a spoken response.

### User Story 5.4: Climate / HVAC

As a user, I want to control my thermostat and air conditioning by voice.

**Acceptance Criteria:**

- [ ] Set target temperature by degrees
- [ ] Set HVAC mode (heat, cool, auto, off)
- [ ] Set fan mode (low, medium, high, auto)
- [ ] Set preset mode (eco, away, comfort, sleep)
- [ ] Query current temperature and HVAC state
- [ ] Set target humidity (if supported)

**Requirements (EARS):**

- WHEN the user sets a temperature, THE SYSTEM SHALL call `climate.set_temperature` with the spoken value, respecting the entity's `min_temp` and `max_temp` bounds.
- WHEN the user sets an HVAC mode, THE SYSTEM SHALL validate the mode against the entity's `hvac_modes` list and call `climate.set_hvac_mode`.
- WHEN the user queries temperature, THE SYSTEM SHALL read `current_temperature` and `temperature` (target) attributes and speak both values with units.
- WHEN the user sets a preset mode, THE SYSTEM SHALL validate against `preset_modes` and call `climate.set_preset_mode`.

### User Story 5.5: Covers (Blinds, Garage Doors, Shutters)

As a user, I want to open, close, and position my covers by voice.

**Acceptance Criteria:**

- [ ] Open/close covers by name
- [ ] Set position by percentage
- [ ] Stop cover movement
- [ ] Control tilt position
- [ ] Query current position

**Requirements (EARS):**

- WHEN the user issues an open/close command for a cover entity, THE SYSTEM SHALL call `cover.open_cover` or `cover.close_cover`.
- WHEN the user specifies a position percentage, THE SYSTEM SHALL call `cover.set_cover_position` with `position` (0=closed, 100=open).
- WHEN the user says "stop", THE SYSTEM SHALL call `cover.stop_cover` to halt movement.
- WHEN the user queries a cover state, THE SYSTEM SHALL report the state (open/closed/opening/closing) and current position percentage if available.

### User Story 5.6: Locks

As a user, I want to lock and unlock my doors by voice with appropriate security measures.

**Acceptance Criteria:**

- [ ] Lock/unlock by name
- [ ] Support PIN code entry when required by the lock entity
- [ ] Query lock state
- [ ] Optional voice PIN confirmation before unlocking (security setting)

**Requirements (EARS):**

- WHEN the user issues a lock command, THE SYSTEM SHALL call `lock.lock` with the target entity.
- WHEN the user issues an unlock command AND the entity requires a code (`code_format` is not None), THE SYSTEM SHALL prompt the user for the code before calling `lock.unlock`.
- WHEN the "require confirmation for unlock" setting is enabled, THE SYSTEM SHALL ask the user to confirm before executing unlock commands.
- WHEN the user queries lock state, THE SYSTEM SHALL report locked/unlocked/jammed status.

### User Story 5.7: Vacuum Cleaners

As a user, I want to control my robot vacuum by voice including room-specific cleaning.

**Acceptance Criteria:**

- [ ] Start/stop/pause cleaning
- [ ] Send vacuum to dock/home
- [ ] Locate vacuum
- [ ] Set fan speed/suction mode
- [ ] Clean specific rooms (if supported by vacuum)
- [ ] Query vacuum state (cleaning, docked, error)

**Requirements (EARS):**

- WHEN the user says "start the vacuum", THE SYSTEM SHALL call `vacuum.start`.
- WHEN the user says "send the vacuum home" or "dock", THE SYSTEM SHALL call `vacuum.return_to_base`.
- WHEN the user requests room-specific cleaning, THE SYSTEM SHALL resolve room names against available rooms and call the appropriate service with segment/room IDs.
- WHEN the user queries vacuum state, THE SYSTEM SHALL report the current activity (cleaning, docked, idle, paused, returning, error).

### User Story 5.8: Fans

As a user, I want to control my fans including speed and oscillation.

**Acceptance Criteria:**

- [ ] Turn on/off/toggle fans
- [ ] Set speed by percentage
- [ ] Set preset mode (eco, breeze, auto)
- [ ] Toggle oscillation
- [ ] Set direction (forward/reverse)

**Requirements (EARS):**

- WHEN the user sets fan speed, THE SYSTEM SHALL call `fan.set_percentage` with the spoken percentage value.
- WHEN the user requests oscillation, THE SYSTEM SHALL call `fan.oscillate` with `oscillating: true/false`.
- WHEN the user sets a fan direction, THE SYSTEM SHALL call `fan.set_direction` with "forward" or "reverse".

### User Story 5.9: Sensors & Binary Sensors (Read-Only)

As a user, I want to query sensor values and binary sensor states by voice.

**Acceptance Criteria:**

- [ ] Query any sensor value ("what's the temperature in the bedroom?")
- [ ] Response includes value and unit of measurement
- [ ] Query binary sensor state ("is the front door open?")
- [ ] Response uses device-class-appropriate language (open/closed for doors, detected/clear for motion)

**Requirements (EARS):**

- WHEN the user queries a sensor entity, THE SYSTEM SHALL read the entity state and `unit_of_measurement` attribute and speak the value with appropriate units.
- WHEN the user queries a binary sensor, THE SYSTEM SHALL translate the on/off state to device-class-appropriate language (e.g., "open"/"closed" for door, "motion detected"/"clear" for motion).
- THE SYSTEM SHALL support querying all sensor device classes: temperature, humidity, battery, power, energy, illuminance, pressure, and others.

### User Story 5.10: Person Tracking

As a user, I want to ask where family members are by voice.

**Acceptance Criteria:**

- [ ] Query person location ("where is John?")
- [ ] Reports zone name (home, work) or "away"
- [ ] "Who is home?" queries all person entities

**Requirements (EARS):**

- WHEN the user asks where a person is, THE SYSTEM SHALL read the person entity state and respond with the zone name or "not home".
- WHEN the user asks "who is home", THE SYSTEM SHALL query all person entities and list those with state "home".

### User Story 5.11: Cameras

As a user, I want to view camera feeds and take snapshots by voice.

**Acceptance Criteria:**

- [ ] Display camera stream in the app UI
- [ ] Take a snapshot on command
- [ ] Show camera entity picture in dashboard

**Requirements (EARS):**

- WHEN the user requests to view a camera, THE SYSTEM SHALL display the camera entity's stream URL in a video player within the app.
- WHEN the user requests a snapshot, THE SYSTEM SHALL call `camera.snapshot` and display the result.

### User Story 5.12: Alarm Control Panel

As a user, I want to arm and disarm my alarm system by voice with appropriate security.

**Acceptance Criteria:**

- [ ] Arm home / arm away / arm night / disarm
- [ ] Support PIN code when required
- [ ] Query alarm state
- [ ] Require confirmation before disarming (configurable)

**Requirements (EARS):**

- WHEN the user issues an alarm arm command, THE SYSTEM SHALL call the appropriate service (alarm_arm_home, alarm_arm_away, alarm_arm_night) with code if `code_arm_required` is true.
- WHEN the user issues a disarm command AND the entity requires a code, THE SYSTEM SHALL prompt for the code before calling `alarm_control_panel.alarm_disarm`.
- WHEN the user queries alarm state, THE SYSTEM SHALL report the current state (disarmed, armed_home, armed_away, armed_night, triggered, pending).

### User Story 5.13: Scenes

As a user, I want to activate scenes by voice to set predefined moods and configurations.

**Acceptance Criteria:**

- [ ] Activate scenes by name ("activate movie night")
- [ ] Fuzzy match scene names
- [ ] Support transition time if specified

**Requirements (EARS):**

- WHEN the user requests a scene activation, THE SYSTEM SHALL call `scene.turn_on` with the resolved scene entity.
- WHEN the user specifies a transition time, THE SYSTEM SHALL include `transition` in the service call data.

### User Story 5.14: Scripts

As a user, I want to run and stop scripts by voice.

**Acceptance Criteria:**

- [ ] Run scripts by name ("run the morning routine")
- [ ] Stop running scripts
- [ ] Query if a script is currently running

**Requirements (EARS):**

- WHEN the user requests to run a script, THE SYSTEM SHALL call `script.turn_on` or `script.<script_name>` for the matched script entity.
- WHEN the user requests to stop a script, THE SYSTEM SHALL call `script.turn_off` for the matched entity.

### User Story 5.15: Automations

As a user, I want to enable, disable, and trigger automations by voice.

**Acceptance Criteria:**

- [ ] Enable/disable automations
- [ ] Manually trigger automations
- [ ] Query automation state (enabled/disabled, last triggered)

**Requirements (EARS):**

- WHEN the user enables an automation, THE SYSTEM SHALL call `automation.turn_on`.
- WHEN the user disables an automation, THE SYSTEM SHALL call `automation.turn_off`.
- WHEN the user triggers an automation, THE SYSTEM SHALL call `automation.trigger`.

### User Story 5.16: Input Helpers

As a user, I want to control input helpers (booleans, numbers, selects, text) by voice.

**Acceptance Criteria:**

- [ ] Toggle input_booleans ("turn on guest mode")
- [ ] Set input_number values ("set target temperature to 22")
- [ ] Select input_select options ("set house mode to away")
- [ ] Set input_text values

**Requirements (EARS):**

- WHEN the user toggles an input_boolean, THE SYSTEM SHALL call `input_boolean.turn_on`, `input_boolean.turn_off`, or `input_boolean.toggle`.
- WHEN the user sets an input_number value, THE SYSTEM SHALL validate against `min`/`max` attributes and call `input_number.set_value`.
- WHEN the user selects an input_select option, THE SYSTEM SHALL fuzzy-match against the `options` attribute and call `input_select.select_option`.

### User Story 5.17: Buttons, Numbers, and Selects

As a user, I want to press buttons, set number values, and select options on device entities.

**Acceptance Criteria:**

- [ ] Press button entities ("press the restart button")
- [ ] Set number entity values
- [ ] Select options on select entities

**Requirements (EARS):**

- WHEN the user presses a button entity, THE SYSTEM SHALL call `button.press`.
- WHEN the user sets a number entity value, THE SYSTEM SHALL validate against `min`/`max`/`step` and call `number.set_value`.
- WHEN the user selects an option on a select entity, THE SYSTEM SHALL fuzzy-match against `options` and call `select.select_option`.

### User Story 5.18: Humidifiers & Water Heaters

As a user, I want to control humidifiers and water heaters by voice.

**Acceptance Criteria:**

- [ ] Turn on/off humidifiers
- [ ] Set target humidity percentage
- [ ] Set humidifier mode
- [ ] Set water heater temperature
- [ ] Set water heater operation mode

**Requirements (EARS):**

- WHEN the user sets humidity, THE SYSTEM SHALL call `humidifier.set_humidity` with the value clamped to `min_humidity`/`max_humidity`.
- WHEN the user sets water heater temperature, THE SYSTEM SHALL call `water_heater.set_temperature` clamped to `min_temp`/`max_temp`.
- WHEN the user sets a mode, THE SYSTEM SHALL validate against `available_modes` or `operation_list` and call the appropriate set_mode service.

### User Story 5.19: Remotes

As a user, I want to control IR/RF remotes and activities by voice.

**Acceptance Criteria:**

- [ ] Turn on/off remote activities
- [ ] Send commands to devices via remote entities

**Requirements (EARS):**

- WHEN the user activates a remote activity, THE SYSTEM SHALL call `remote.turn_on` with the specified `activity`.
- WHEN the user sends a remote command, THE SYSTEM SHALL call `remote.send_command` with the resolved command name.

### User Story 5.20: Notifications

As a user, I want to send notifications to devices via voice commands.

**Acceptance Criteria:**

- [ ] Send notification with spoken message text
- [ ] Target specific notification services

**Requirements (EARS):**

- WHEN the user requests sending a notification, THE SYSTEM SHALL call `notify.send_message` with the spoken message text.

---

## 6. Entity Discovery & Management

### User Story 6.1: Auto-Discovery of All Entities

As a user, I want the app to automatically discover and import all my HA entities so I never need to manually type entity IDs.

**Acceptance Criteria:**

- [ ] App fetches all entities via WebSocket `config/entity_registry/list_for_display`
- [ ] Entities automatically organised by domain, area, and device
- [ ] Entity list refreshes automatically when new entities are added to HA
- [ ] User can browse all discovered entities in a searchable, filterable list
- [ ] Zero manual configuration required to start controlling entities — they just work out of the box

**Requirements (EARS):**

- WHEN the app connects to HA, THE SYSTEM SHALL automatically fetch the complete entity registry, area registry, and device registry.
- THE SYSTEM SHALL associate each entity with its area, floor, and device based on registry data.
- WHEN a new entity appears in HA, THE SYSTEM SHALL detect it via state_changed events and add it to the local cache automatically.
- THE SYSTEM SHALL NOT require any manual entity mapping for basic voice control — HA's Conversation API handles name resolution natively.

### User Story 6.2: Voice-Exposed Entities

As a user, I want to control which entities are accessible via voice so I don't accidentally trigger the wrong device.

**Acceptance Criteria:**

- [ ] App respects HA's "exposed to Assist" configuration
- [ ] User can additionally hide/show entities within the app
- [ ] Only exposed entities appear in the dashboard and local fallback matching
- [ ] User can bulk-select entities to expose/hide (select all in area, select by domain)

**Requirements (EARS):**

- WHEN fetching entities, THE SYSTEM SHALL query `homeassistant/expose_entity/list` and respect HA's exposure settings.
- THE SYSTEM SHALL provide a local override to additionally hide entities from the dashboard and local fallback matching.
- THE SYSTEM SHALL support bulk operations: expose/hide all entities in an area, expose/hide all entities of a domain.

### User Story 6.3: Area-Based Commands

As a user, I want to control all entities in an area with a single voice command.

**Acceptance Criteria:**

- [ ] "Turn off the living room" turns off all applicable entities in that area
- [ ] Area names resolved from HA area registry
- [ ] Area names fuzzy-matched against spoken input
- [ ] Floor-based commands also supported ("turn off downstairs")

**Requirements (EARS):**

- WHEN the user references an area name in a command, THE SYSTEM SHALL let HA's Conversation API handle area resolution natively.
- WHEN using offline fallback, THE SYSTEM SHALL resolve area names locally against the cached area registry using fuzzy matching.
- THE SYSTEM SHALL support floor-based grouping when HA floor registry data is available.

### User Story 6.4: Favourites & Quick Access

As a user, I want to mark frequently-used entities as favourites for quick access on the dashboard.

**Acceptance Criteria:**

- [ ] User can star/favourite entities from the entity browser
- [ ] Favourites appear on the main dashboard as quick-action cards
- [ ] Bulk-favourite: star all entities in an area or all of a type
- [ ] Reorder favourites by drag-and-drop

**Requirements (EARS):**

- THE SYSTEM SHALL allow users to mark any entity as a favourite, stored locally on-device.
- WHEN displaying the dashboard, THE SYSTEM SHALL show favourited entities as interactive quick-action cards.
- THE SYSTEM SHALL support bulk-favourite operations (select multiple entities at once).
- THE SYSTEM SHALL allow reordering favourite entities via drag-and-drop.

### User Story 6.5: Entity Aliases for Offline Fallback

As a user, I want to optionally set custom voice names for entities so offline fallback matching works with my natural language.

**Acceptance Criteria:**

- [ ] User can add one or more voice aliases per entity
- [ ] Aliases ONLY used for local offline fallback matching (HA handles online matching via its own alias system)
- [ ] Auto-suggest aliases based on HA friendly names (pre-filled, user can edit)
- [ ] Bulk import: fetch all HA entity friendly names as initial aliases with one tap

**Requirements (EARS):**

- THE SYSTEM SHALL allow users to define custom voice aliases for offline fallback matching (multiple aliases per entity supported).
- THE SYSTEM SHALL provide a "sync from HA" button that bulk-imports all entity friendly names as aliases.
- WHEN matching voice commands offline, THE SYSTEM SHALL check aliases in addition to the entity's `friendly_name` attribute.
- NOTE: Online voice matching is handled entirely by HA's Conversation API and its own alias/expose system — local aliases are only for offline fallback.

---

## 7. Dashboard / UI

### User Story 7.1: Live State Dashboard

As a user, I want a dashboard showing real-time entity states so I can see my home's status at a glance.

**Acceptance Criteria:**

- [ ] Dashboard displays entity states updated in real-time via WebSocket
- [ ] Entities grouped by area or custom groups
- [ ] State changes animate/highlight briefly when they occur
- [ ] Pull-to-refresh for manual state sync
- [ ] Supports both light and dark themes

**Requirements (EARS):**

- WHEN the dashboard is visible, THE SYSTEM SHALL display entity states that update in real-time as `state_changed` events are received via WebSocket.
- WHEN an entity state changes, THE SYSTEM SHALL briefly highlight the entity card (animation/colour flash) to draw attention.
- THE SYSTEM SHALL support grouping dashboard entities by area, domain, or user-defined custom groups.
- THE SYSTEM SHALL support system light/dark themes and optionally follow the device theme setting.

### User Story 7.2: Quick Action Buttons

As a user, I want tap-able quick actions for common commands so I can control things without voice.

**Acceptance Criteria:**

- [ ] Quick action grid on home screen (e.g., "All Lights Off", "Goodnight", "Movie Mode")
- [ ] User can create custom quick actions mapped to any HA service call or scene
- [ ] Quick actions support long-press for additional options (e.g., brightness slider for lights)

**Requirements (EARS):**

- THE SYSTEM SHALL display a configurable grid of quick action buttons on the main dashboard.
- WHEN the user taps a quick action, THE SYSTEM SHALL execute the configured service call or scene activation immediately.
- THE SYSTEM SHALL allow users to create custom quick actions specifying: name, icon, and HA service call (domain, service, entity, data).
- WHEN the user long-presses a controllable entity (light, cover, media_player), THE SYSTEM SHALL show a contextual control popup (slider for brightness/volume/position).

### User Story 7.3: Home Screen Widgets

As a user, I want Android home screen widgets for at-a-glance information and one-tap control.

**Acceptance Criteria:**

- [ ] Widget showing entity states (temperature, binary sensor states)
- [ ] Widget with quick action buttons (toggle lights, scenes)
- [ ] Widget for voice command activation (tap to speak)
- [ ] Widgets update at configurable intervals

**Requirements (EARS):**

- THE SYSTEM SHALL provide at least 3 widget types: state display widget, quick action widget, and voice activation widget.
- WHEN placed on the home screen, state display widgets SHALL update at least every 60 seconds via background refresh.
- WHEN the user taps a quick action widget, THE SYSTEM SHALL execute the configured service call without opening the full app.
- WHEN the user taps the voice widget, THE SYSTEM SHALL activate STT listening with the configured engine.

### User Story 7.4: Voice Interaction UI

As a user, I want clear visual feedback during voice interactions so I know when the assistant is listening, processing, and responding.

**Acceptance Criteria:**

- [ ] Microphone button clearly indicates tap-to-speak
- [ ] Animated indicator during active listening
- [ ] Partial STT results displayed in real-time
- [ ] Processing spinner while waiting for HA response
- [ ] Response text displayed alongside audio playback
- [ ] Error states clearly communicated

**Requirements (EARS):**

- WHEN the STT engine is actively listening, THE SYSTEM SHALL display an animated audio waveform or pulsing indicator.
- WHEN partial STT results are available (streaming engines), THE SYSTEM SHALL display them in real-time in the UI.
- WHEN waiting for HA intent processing, THE SYSTEM SHALL display a loading indicator.
- WHEN the response is received, THE SYSTEM SHALL display the response text and highlight affected entities (success/failure).
- WHEN an error occurs at any stage, THE SYSTEM SHALL display a user-friendly error message with suggested remediation.

### User Story 7.5: Conversation History

As a user, I want to see a history of my voice interactions so I can review past commands and responses.

**Acceptance Criteria:**

- [ ] Scrollable conversation history (chat-style UI)
- [ ] Shows user command and assistant response
- [ ] Indicates which entities were affected
- [ ] Optionally stores history locally (configurable retention)

**Requirements (EARS):**

- THE SYSTEM SHALL maintain a conversation history showing user commands and assistant responses in a chat-style interface.
- WHEN a command is processed, THE SYSTEM SHALL log the transcribed text, response text, affected entities, and timestamp.
- THE SYSTEM SHALL allow users to configure history retention (off, 24 hours, 7 days, 30 days, unlimited) stored locally on-device.
- THE SYSTEM SHALL support multi-turn conversations using HA's `conversation_id` to maintain context between related commands.

---

## 8. HA Conversation API Integration

### User Story 8.1: Primary Intent Engine

As a user, I want my voice commands processed by HA's built-in conversation agent so I get the full power of HA's intent system.

**Acceptance Criteria:**

- [ ] All voice commands sent to `POST /api/conversation/process` or WebSocket equivalent
- [ ] Supports the built-in `home_assistant` agent and custom agents (e.g., LLM-based)
- [ ] Handles all response types: action_done, query_answer, error
- [ ] Multi-turn conversation support via conversation_id
- [ ] Language parameter sent matching user's configured language

**Requirements (EARS):**

- WHEN the STT engine produces a transcription, THE SYSTEM SHALL send it to HA via `conversation/process` (WebSocket preferred, REST fallback).
- WHEN the HA response type is `action_done`, THE SYSTEM SHALL speak the response text and display affected entities (success/failed lists).
- WHEN the HA response type is `query_answer`, THE SYSTEM SHALL speak the answer text.
- WHEN the HA response type is `error` with code `no_intent_match`, THE SYSTEM SHALL inform the user that the command was not understood and suggest rephrasing.
- WHEN `continue_conversation` is `true` in the response, THE SYSTEM SHALL automatically activate STT listening for a follow-up command, passing the received `conversation_id`.

### User Story 8.2: Pipeline Integration

As a user, I want to optionally use HA's full Assist Pipeline for end-to-end voice processing.

**Acceptance Criteria:**

- [ ] App can use `assist_pipeline/run` for full server-side processing
- [ ] Configurable which stages run locally vs on server
- [ ] Pipeline events displayed in real-time (stt-start, intent-start, tts-end, etc.)
- [ ] User can select which HA pipeline to use

**Requirements (EARS):**

- WHEN the user selects "Full HA Pipeline" mode, THE SYSTEM SHALL use `assist_pipeline/run` with appropriate start/end stages based on local capabilities.
- WHEN using partial pipeline (local STT + HA intent), THE SYSTEM SHALL set `start_stage: "intent"` and send transcribed text.
- THE SYSTEM SHALL list available HA pipelines via `assist_pipeline/pipeline/list` and allow the user to select one.
- WHEN pipeline events are received, THE SYSTEM SHALL update the UI in real-time to show processing progress.

### User Story 8.3: Custom Conversation Agents

As a user, I want to choose which conversation agent processes my commands (built-in HA, extended OpenAI, custom LLM, etc.).

**Acceptance Criteria:**

- [ ] Settings allow selecting conversation agent by ID
- [ ] Default to built-in `home_assistant` agent
- [ ] Support for extended/custom agents configured in HA

**Requirements (EARS):**

- THE SYSTEM SHALL allow users to specify a conversation `agent_id` in settings.
- WHEN no agent_id is configured, THE SYSTEM SHALL omit the parameter (uses HA's default agent).
- THE SYSTEM SHALL provide an option to fetch and display available conversation agents from the HA instance.

---

## 9. Offline Fallback (Local Intent Matching)

### User Story 9.1: Basic Offline Commands

As a user, I want basic device control to work even when my HA server is unreachable so I'm not completely locked out.

**Acceptance Criteria:**

- [ ] Local intent matching for turn on/off/toggle commands
- [ ] Matches against cached entity names and states
- [ ] Queues commands for execution when HA reconnects
- [ ] Clear indication to user that commands are queued, not executed

**Requirements (EARS):**

- WHEN the HA connection is unavailable AND the user issues a voice command, THE SYSTEM SHALL attempt local intent matching against the cached entity list.
- WHEN a local intent match is found for a simple command (on/off/toggle), THE SYSTEM SHALL queue the service call and inform the user: "Command queued — will execute when Home Assistant reconnects."
- WHEN the HA connection is restored, THE SYSTEM SHALL execute all queued commands in order and report results.
- THE SYSTEM SHALL support local intent matching for: turn on, turn off, toggle, open, close, lock, unlock, and state queries (from cache).

### User Story 9.2: Cached State Queries

As a user, I want to query sensor states from the last-known cache when offline.

**Acceptance Criteria:**

- [ ] All entity states cached locally with timestamps
- [ ] Offline queries return cached values with "as of X minutes ago" qualifier
- [ ] Cache persists across app restarts

**Requirements (EARS):**

- THE SYSTEM SHALL maintain a local SQLite/Room database cache of all entity states, updated in real-time via WebSocket events.
- WHEN the user queries a sensor offline, THE SYSTEM SHALL respond with the cached value and the age of the data (e.g., "The bedroom temperature was 21 degrees as of 5 minutes ago").
- THE SYSTEM SHALL persist the entity cache across app restarts so cached data is available immediately on launch.

### User Story 9.3: Local Command Patterns

As a user, I want the offline intent matching to understand natural language patterns similar to how HA processes them.

**Acceptance Criteria:**

- [ ] Supports patterns: "turn on/off [entity]", "open/close [entity]", "lock/unlock [entity]"
- [ ] Entity name matching with fuzzy search (handles partial names, articles stripped)
- [ ] Number extraction for brightness/temperature ("set X to 50%")
- [ ] Domain-aware service mapping (cover → open/close, lock → lock/unlock)

**Requirements (EARS):**

- THE SYSTEM SHALL implement local intent parsing supporting at minimum: on/off/toggle, open/close, lock/unlock, set value, and state queries.
- WHEN matching entity names locally, THE SYSTEM SHALL strip articles (the, a, an), perform case-insensitive comparison, and support substring matching.
- WHEN a numeric value is spoken, THE SYSTEM SHALL extract it and map to the appropriate service parameter (brightness, temperature, position, volume).
- THE SYSTEM SHALL use domain-aware service mapping: cover entities use open_cover/close_cover, lock entities use lock/unlock, all others use turn_on/turn_off.

---

## 10. Notifications from HA

### User Story 10.1: Receive HA Notifications

As a user, I want to receive notifications from my HA instance on my phone so I'm informed of important events.

**Acceptance Criteria:**

- [ ] App registers as a notification target in HA (via `mobile_app` integration or custom webhook)
- [ ] Displays Android notifications for HA notification events
- [ ] Supports notification title, message, and optional image
- [ ] Notifications respect Android notification channels and priority levels
- [ ] No dependency on Firebase Cloud Messaging (FCM) or Google services

**Requirements (EARS):**

- THE SYSTEM SHALL register with the HA instance as a notification target without requiring Google Firebase/FCM.
- WHEN a notification event is received from HA, THE SYSTEM SHALL display an Android notification with the provided title and message.
- THE SYSTEM SHALL implement notifications via WebSocket subscription (subscribe to specific event types) or local push mechanism (persistent WebSocket connection).
- THE SYSTEM SHALL support notification actions (actionable notifications with buttons) that trigger HA service calls when tapped.
- WHEN a notification includes an image URL, THE SYSTEM SHALL download and display the image in the notification (expanded view).

### User Story 10.2: Event-Based Alerts

As a user, I want to subscribe to specific HA events and receive voice announcements for critical alerts.

**Acceptance Criteria:**

- [ ] User can configure which events trigger voice announcements
- [ ] Voice announcement plays through speaker even if app is in background
- [ ] Supports configurable event types (smoke alarm, door open, motion detected)
- [ ] Respects DND/quiet hours

**Requirements (EARS):**

- THE SYSTEM SHALL allow users to configure event subscriptions (entity_id + state change pattern) that trigger voice announcements.
- WHEN a subscribed event occurs AND the device is not in DND mode, THE SYSTEM SHALL synthesise and play a voice announcement through the device speaker.
- WHEN the device is in DND mode or within configured quiet hours, THE SYSTEM SHALL deliver alerts as silent notifications instead of voice announcements.
- THE SYSTEM SHALL support subscribing to HA triggers via WebSocket `subscribe_trigger` for complex event patterns.

### User Story 10.3: Notification History

As a user, I want to review past notifications within the app.

**Acceptance Criteria:**

- [ ] In-app notification log with timestamps
- [ ] Configurable retention period
- [ ] Ability to clear notification history

**Requirements (EARS):**

- THE SYSTEM SHALL maintain a local log of all received notifications with timestamp, title, message, and source entity.
- THE SYSTEM SHALL allow users to configure notification log retention (7 days, 30 days, 90 days).

---

## 11. Export / Import Configuration

### User Story 11.1: Export All Settings

As a user, I want to export my entire app configuration so I can back it up or transfer to another device.

**Acceptance Criteria:**

- [ ] Single "Export Configuration" button produces a JSON file
- [ ] Exported data includes: HA connection details (URL, NOT token), favourites, aliases, quick actions, dashboard layout, STT/TTS/wake word preferences, notification subscriptions, entity visibility overrides
- [ ] Access token explicitly EXCLUDED from export (security)
- [ ] User chooses export location via Android file picker (SAF)
- [ ] Export includes a version field for forward-compatibility

**Requirements (EARS):**

- WHEN the user taps "Export Configuration", THE SYSTEM SHALL generate a JSON file containing all app settings, favourites, aliases, quick actions, dashboard layout, and preferences.
- THE SYSTEM SHALL NOT include the HA access token or any secret material in the exported file.
- THE SYSTEM SHALL include a schema version number in the export to support future format migrations.
- THE SYSTEM SHALL use Android's Storage Access Framework (SAF) to let the user choose the save location.

### User Story 11.2: Import Configuration

As a user, I want to import a previously exported configuration to restore my setup quickly on a new device or after a reinstall.

**Acceptance Criteria:**

- [ ] "Import Configuration" button opens file picker to select a JSON export
- [ ] Import validates the file format and reports errors clearly
- [ ] User can choose to merge with existing config or replace entirely
- [ ] Import prompts for the access token (since it's not in the export)
- [ ] Preview what will be imported before applying

**Requirements (EARS):**

- WHEN the user selects an import file, THE SYSTEM SHALL validate the JSON schema and display a summary of what will be imported.
- WHEN importing, THE SYSTEM SHALL offer "Replace" (overwrite all settings) or "Merge" (keep existing, add missing) modes.
- WHEN the import contains connection details but no token, THE SYSTEM SHALL prompt the user to enter their access token to complete the setup.
- WHEN the import file has a newer schema version than the app supports, THE SYSTEM SHALL display a warning and refuse the import with an upgrade suggestion.

### User Story 11.3: Share Configuration Between Devices

As a user, I want to share my JarvisHA setup between multiple Android devices (phone, tablet, wall-mounted panel) easily.

**Acceptance Criteria:**

- [ ] Export file can be shared via any Android share mechanism (email, file manager, Bluetooth, etc.)
- [ ] Importing on another device produces an identical setup (minus token)
- [ ] Partial export supported: export only favourites, only quick actions, only dashboard layout

**Requirements (EARS):**

- THE SYSTEM SHALL support partial exports: the user can select which sections to include (connection, favourites, aliases, quick actions, dashboard, preferences).
- WHEN a partial export is imported, THE SYSTEM SHALL only modify the sections included in the export and leave other settings untouched.
- THE SYSTEM SHALL support Android's share intent for the export file, allowing the user to send it via any installed app.

---

## 12. Security

### User Story 12.1: Secure Token Storage

As a user, I want my HA access token stored securely so it can't be extracted by malicious apps.

**Acceptance Criteria:**

- [ ] Access token stored in Android Keystore (hardware-backed where available)
- [ ] Token never written to plain-text files or shared preferences
- [ ] Token not included in app backups
- [ ] Token cleared on app uninstall

**Requirements (EARS):**

- THE SYSTEM SHALL store the HA long-lived access token in the Android Keystore system, encrypted with a hardware-backed key where available.
- THE SYSTEM SHALL NOT write the access token to SharedPreferences, plain-text files, or any location included in Android backups.
- THE SYSTEM SHALL mark all sensitive data storage with `android:allowBackup="false"` to prevent token extraction via ADB backup.

### User Story 12.2: Local Network Preference

As a user, I want the app to prefer local network communication to minimise external exposure.

**Acceptance Criteria:**

- [ ] Local URL used whenever device is on the home network
- [ ] No data sent to any third-party servers (analytics, crash reporting, telemetry)
- [ ] All HA communication uses the most direct path available
- [ ] Optional: reject all connections if not on trusted network

**Requirements (EARS):**

- THE SYSTEM SHALL prefer the local HA URL over the external URL whenever the device is on the configured home network.
- THE SYSTEM SHALL NOT transmit any data to third-party servers. No analytics, no crash reporting, no telemetry.
- WHEN "local only" mode is enabled in settings, THE SYSTEM SHALL refuse to connect to the external URL and display a warning that HA is unreachable.

### User Story 12.3: Connection Security

As a user, I want my communication with HA to be secure, especially over external networks.

**Acceptance Criteria:**

- [ ] HTTPS required for external connections
- [ ] Optional certificate pinning for the HA instance
- [ ] Warning displayed if using HTTP on a non-local network
- [ ] Support for self-signed certificates (with user acknowledgment)

**Requirements (EARS):**

- WHEN connecting via external URL, THE SYSTEM SHALL require HTTPS and reject plain HTTP connections.
- WHEN connecting via local URL with HTTP, THE SYSTEM SHALL allow the connection but only if the device is confirmed on the local network.
- THE SYSTEM SHALL support custom CA certificates and self-signed certificates with explicit user acknowledgment of the security implications.
- THE SYSTEM SHALL display a security warning if the user configures an external URL without HTTPS.

### User Story 12.4: Authentication for Sensitive Operations

As a user, I want additional confirmation for security-sensitive operations like unlocking doors or disarming alarms.

**Acceptance Criteria:**

- [ ] Configurable list of "sensitive" entity domains (locks, alarm_control_panel by default)
- [ ] Sensitive operations require biometric/PIN confirmation before execution
- [ ] Voice PIN support for hands-free secure operations
- [ ] Timeout for elevated permissions (configurable, default 30 seconds)

**Requirements (EARS):**

- THE SYSTEM SHALL classify lock and alarm_control_panel entities as security-sensitive by default.
- WHEN a service call targets a security-sensitive entity, THE SYSTEM SHALL require additional authentication (device biometric, PIN, or voice PIN) before execution.
- THE SYSTEM SHALL allow users to add or remove entity domains from the security-sensitive list.
- WHEN biometric authentication succeeds, THE SYSTEM SHALL grant a configurable grace period (default 30 seconds) before requiring re-authentication.

### User Story 12.5: Voice Data Privacy

As a user, I want assurance that my voice recordings are never stored or transmitted beyond what's needed for recognition.

**Acceptance Criteria:**

- [ ] Audio data processed in real-time and immediately discarded after STT
- [ ] No voice recordings stored on device or sent to cloud services
- [ ] When using HA Wyoming, audio only sent to user's own HA server
- [ ] Transparent privacy indicator when microphone is active

**Requirements (EARS):**

- THE SYSTEM SHALL process audio data in streaming fashion and discard raw audio immediately after STT processing.
- THE SYSTEM SHALL NOT store voice recordings on the device filesystem.
- WHEN using HA Wyoming STT, THE SYSTEM SHALL only stream audio to the user's configured HA instance (no third-party endpoints).
- WHEN the microphone is active, THE SYSTEM SHALL display a visible privacy indicator (coloured dot or icon) at all times.

---

## 13. Accessibility

### User Story 13.1: Screen Reader Compatibility

As a visually impaired user, I want the app to work with Android TalkBack so I can navigate and control my home.

**Acceptance Criteria:**

- [ ] All UI elements have content descriptions
- [ ] Logical focus order for TalkBack navigation
- [ ] Custom actions announced clearly (e.g., "double tap to toggle light")
- [ ] State changes announced via accessibility events
- [ ] Voice interaction works alongside TalkBack

**Requirements (EARS):**

- THE SYSTEM SHALL provide meaningful `contentDescription` attributes for all interactive UI elements.
- THE SYSTEM SHALL ensure a logical tab/focus order that follows visual layout for TalkBack navigation.
- WHEN an entity state changes on the dashboard, THE SYSTEM SHALL announce the change via an Android accessibility event.
- THE SYSTEM SHALL ensure that voice command activation does not conflict with TalkBack gestures.

### User Story 13.2: Visual Accessibility

As a user with visual impairments, I want configurable text sizes and high contrast options.

**Acceptance Criteria:**

- [ ] Respects system font size settings
- [ ] High contrast mode available
- [ ] Minimum touch target size of 48dp
- [ ] Colour not used as sole indicator of state (icons/text supplement colour)
- [ ] Supports dynamic colour/Material You theming

**Requirements (EARS):**

- THE SYSTEM SHALL respect the Android system font size and display size settings.
- THE SYSTEM SHALL ensure all interactive elements meet the minimum 48dp touch target size.
- THE SYSTEM SHALL NOT use colour as the sole indicator of state — all states must be distinguishable by icon, text label, or pattern in addition to colour.
- THE SYSTEM SHALL provide a high-contrast theme option for users who need it.

### User Story 13.3: Motor Accessibility

As a user with motor impairments, I want the app to be fully usable by voice alone so I never need precise touch interactions.

**Acceptance Criteria:**

- [ ] All app functions accessible via voice commands (not just HA control)
- [ ] "Navigate to settings", "scroll down", "tap favourites" work as voice commands
- [ ] Large touch targets for essential buttons
- [ ] Support for external switch access devices

**Requirements (EARS):**

- THE SYSTEM SHALL support voice-based app navigation commands (e.g., "open settings", "go to dashboard", "scroll down").
- THE SYSTEM SHALL support Android Switch Access for users who interact via external switches.
- THE SYSTEM SHALL ensure the primary voice activation button (microphone) is prominently placed and at least 56dp in size.

### User Story 13.4: Hearing Accessibility

As a deaf or hard-of-hearing user, I want all voice responses also displayed visually so I can use the app without audio.

**Acceptance Criteria:**

- [ ] All TTS responses displayed as text simultaneously
- [ ] Visual confirmation for all actions (not just audio)
- [ ] Haptic feedback option for confirmations
- [ ] Visual wake word confirmation (flash/animation) as alternative to audio chime

**Requirements (EARS):**

- WHEN a TTS response is generated, THE SYSTEM SHALL always display the response text visually regardless of whether audio is playing.
- THE SYSTEM SHALL provide haptic feedback (vibration) as an alternative confirmation mechanism, configurable in settings.
- WHEN the wake word is detected, THE SYSTEM SHALL provide visual feedback (screen flash, icon animation) in addition to any audio indicator.
- THE SYSTEM SHALL support a "visual only" mode where all audio feedback is replaced with on-screen text and haptic vibration.

---

## Non-Functional Requirements

### Performance

- THE SYSTEM SHALL launch to an interactive state within 3 seconds on a mid-range Android device.
- THE SYSTEM SHALL begin processing voice input within 300ms of microphone activation.
- THE SYSTEM SHALL maintain a WebSocket connection with less than 100ms event propagation latency on local network.
- THE SYSTEM SHALL use less than 150MB of RAM during normal operation (excluding downloaded models).

### Compatibility

- THE SYSTEM SHALL support Android 8.0 (API 26) and above.
- THE SYSTEM SHALL function correctly on devices without Google Play Services.
- THE SYSTEM SHALL function on custom ROMs: LineageOS, GrapheneOS, CalyxOS, /e/OS.
- THE SYSTEM SHALL NOT require any proprietary libraries or Google Mobile Services.

### Distribution

- THE SYSTEM SHALL be distributable via F-Droid (meeting FOSS requirements, reproducible builds).
- THE SYSTEM SHALL be installable via direct APK sideloading.
- THE SYSTEM SHALL support automatic update checking (F-Droid repository or GitHub releases).
- THE SYSTEM SHALL be licensed under a FOSS-compatible license (GPLv3 or Apache 2.0).

### Battery & Resources

- THE SYSTEM SHALL consume less than 3% battery per hour during background wake word listening.
- THE SYSTEM SHALL consume less than 1% battery per hour when in background without wake word (WebSocket only).
- THE SYSTEM SHALL allow users to configure background behaviour (full service, WebSocket only, disabled).
- THE SYSTEM SHALL release microphone and audio resources when not actively in use (respecting other app's audio needs).

### Data Storage

- THE SYSTEM SHALL store all user data locally on-device only.
- THE SYSTEM SHALL support Android's scoped storage requirements.
- THE SYSTEM SHALL allow users to export/import app configuration (excluding secrets) for backup.
- THE SYSTEM SHALL allow users to clear all local data (cache, history, configuration) from settings.

### Localisation

- THE SYSTEM SHALL support English as the primary interface language.
- THE SYSTEM SHALL be structured for i18n to support additional UI languages in future.
- THE SYSTEM SHALL pass the user's configured language to HA's Conversation API.
- THE SYSTEM SHALL support STT/TTS in any language for which models are available.
