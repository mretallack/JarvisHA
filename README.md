# JarvisHA

A voice-first Android app for controlling Home Assistant — built for privacy, offline capability, and degoogled phones.

## What is this?

JarvisHA is a standalone Android voice assistant designed exclusively for Home Assistant control. Unlike general-purpose assistants, it focuses on doing one thing well: letting you control your smart home by voice.

**Key principles:**
- **No Google** — works on LineageOS, GrapheneOS, CalyxOS without Play Services
- **Privacy-first** — all voice processing on-device by default, only talks to YOUR HA instance
- **Offline capable** — wake word, STT, and TTS all work without internet
- **Easy setup** — auto-discovers entities from HA, no manual mapping required
- **HA Conversation API** — uses HA's native intent system (supports built-in, LLM, custom agents)

## Features (Planned)

- 🎙️ "Hey Jarvis" wake word (always-on background listening)
- 🗣️ Offline speech recognition (Sherpa-ONNX / Vosk)
- 🔊 Offline neural TTS (Piper via Sherpa-ONNX)
- 🏠 Full HA entity control (lights, media, climate, locks, vacuum, covers, sensors, and more)
- 📊 Live dashboard with real-time entity states via WebSocket
- 🗺️ Area and floor-based commands
- ⭐ Favourites and quick-action buttons
- 📤 Export/import configuration for backup and multi-device sync
- 🔔 Notifications from HA (no Firebase/FCM)
- 🔒 Biometric auth for sensitive operations (locks, alarms)
- 📱 Home screen widgets

## Status

**Early development** — currently in spec/design phase.

See [`.kiro/specs/jarvis-ha/`](.kiro/specs/jarvis-ha/) for:
- [`requirements.md`](.kiro/specs/jarvis-ha/requirements.md) — Full requirements spec (EARS notation)
- [`design.md`](.kiro/specs/jarvis-ha/design.md) — Technical architecture and component design

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Networking | OkHttp 5 (REST + WebSocket) |
| Database | Room |
| STT | Sherpa-ONNX / Vosk (offline) |
| TTS | Piper via Sherpa-ONNX / eSpeak-NG |
| Wake Word | OpenWakeWord via LiteRT (TFLite) |

## Requirements

- Android 8.0+ (API 26)
- Home Assistant instance with a long-lived access token
- No Google Play Services required

## Building

_Build instructions will be added once implementation begins._

## Contributing

_Contribution guidelines will be added once the project structure is in place._

## License

MIT — see [LICENSE](LICENSE).
