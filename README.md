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

## Features (v1.0)

- 🎙️ "Hey Jarvis" wake word (on-device, background listening with quiet hours)
- 🗣️ Offline speech recognition (Sherpa-ONNX streaming)
- 🔊 Offline neural TTS (Piper via Sherpa-ONNX)
- 🏠 Voice control of all HA entities via Conversation API (~185ms response)
- 💬 Chat-style conversation history with multi-turn support
- ⭐ Entity browser with favourites and alias management
- 🏷️ Add voice shortcuts — aliases pushed to HA for instant recognition
- 📤 Export/import configuration for backup and multi-device sync
- 🔒 Biometric auth for sensitive operations (locks, alarms, covers)
- 📱 Home screen mic-button widget
- 🌙 Quiet hours (disable wake word on schedule)

## Why Not Just Use the HA Companion App?

The official Home Assistant Companion app has Assist support, but with a fundamentally different architecture:

| | HA Companion App | JarvisHA |
|---|---|---|
| **STT** | Server-side (Whisper on your HA server) | **On-device** (Sherpa-ONNX/Vosk) |
| **TTS** | Server-side (Piper on your HA server) | **On-device** (Piper via Sherpa-ONNX) |
| **Wake word** | On-device (microWakeWord, experimental) | On-device (OpenWakeWord TFLite) |
| **Requires Google Play** | Yes | No — F-Droid / APK |
| **Offline voice** | Wake word only — commands need server | Full STT/TTS offline |
| **Intent processing** | HA Conversation API | HA Conversation API (same, ~185ms) |
| **Entity aliases** | HA web UI only | In-app: browse, add aliases, push to HA |

**The key insight:** JarvisHA does STT and TTS on the phone, sending only lightweight text to HA's Conversation API (~185ms response). The Companion app streams audio to your server for Whisper processing, which is slower and requires a powerful HA server. Once Android 18+ exposes hardware-level wake word APIs (EU DMA, August 2027), the battery concern is solved too.

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
| STT | Sherpa-ONNX (offline) |
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
