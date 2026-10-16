# Agent Notes — JarvisHA

Guidance for AI agents working in this repository.

## Read this first

Before working on **wake word, STT, voice capture, or process/memory behaviour**, read:

- **[`docs/wake-word-stt-investigation.md`](docs/wake-word-stt-investigation.md)** —
  running log of the voice pipeline problems, everything tried, what worked, what
  **failed in production**, and the debugging technique that works.

That document is the single source of truth for this area. It records a change that was
made and had to be reverted because it caused the wake-word listener to be killed by the
OS — read it before touching model retention.

## Project

Voice-first Android assistant for Home Assistant. Kotlin, Jetpack Compose, MVVM +
Repository, Hilt, OkHttp, Room, Sherpa-ONNX (on-device Whisper STT), Android TTS,
OpenWakeWord via LiteRT/TFLite.

## Architecture points that matter

- **`WakeWordService` runs in a dedicated `:wakeword` process** (see `AndroidManifest.xml`).
  This is deliberate: it keeps the lightweight TFLite listener out of the same process as
  the heavy ONNX STT heap, so an LMK kill of one doesn't take down the other.
- **Room's invalidation is per-process.** Cross-process DB writes do not notify the other
  process's observers. Use `ConversationSync.notifyChanged()` for conversation updates.
- **The `:wakeword` process is headless** — it has no activities. Do not infer "app is
  idle" from "no visible activity"; `AppForegroundState` also tracks active sessions.
- **STT result delivery is a race trap.** Subscribe to `sttEngine.results` *before*
  starting capture, or the result is missed entirely. `SttResult.timestampMs` exists to
  discard stale replays.

## Testing device (Moto G52) is memory-constrained

3.6 GB RAM, 2.6 GB swap. The kernel LMK reaps background apps constantly and somewhat
indiscriminately. **Measure process size after any change to model retention:**

```bash
adb shell dumpsys meminfo uk.org.retallack.jarvis:wakeword
adb shell dumpsys activity exit-info uk.org.retallack.jarvis   # look for reason=3 LOW_MEMORY
```

Stable idle target: ~148 MB PSS / ~34 MB native heap. A previous change that held the
model at 387 MB RSS **was killed in normal use**.

## Debugging the voice pipeline

```bash
adb logcat -b all -G 4M      # the default 256 KiB buffer is far too small
adb logcat -b all -c         # clear before reproducing
# ...reproduce...
adb logcat -d -v threadtime | grep -E "SherpaSTT|WakeWordService|LiteRt"
```

See the log markers table in the investigation doc.

## Build & install

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Note: reinstalling kills both processes and does not restart the wake-word listener until
the app is launched — not a bug.

## Conventions

- `python3`, not `python`.
- Tests live in `app/src/test/kotlin/...`. 184 tests currently pass; keep them green.
- Prefer fixing the root cause over the symptom — the investigation log shows a case
  where a symptom fix (repetition collapse) masked a deeper cause (mic contention).
