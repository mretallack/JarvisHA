# Migration: OpenWakeWord → microWakeWord

Plan for replacing the OpenWakeWord (OWW) wake-word engine with microWakeWord, using an
NDK library module closely modelled on Home Assistant's Android app (Apache-2.0).

Status: **complete**. Branch: `investigate-stt-speed`. See `docs/wake-word-stt-investigation.md` §11.

## Why

OWW's pre-trained models are all v0.1 files from 2023, trained on synthetic speech. The
investigation log (`docs/wake-word-stt-investigation.md`, *Known outstanding issues #1*)
records confident false positives (scores 0.97/0.98) that the sensitivity slider cannot
fix. microWakeWord's models are actively maintained, used by ESPHome / Home Assistant
Voice, ship a VAD model, and are ~50 KB INT8 rather than ~2.7 MB of float models.

## The fundamental difference (and the cost)

| | OpenWakeWord (old) | microWakeWord (new) |
|---|---|---|
| Preprocessing | 2 **TFLite models** | TFLite-Micro **audio frontend** — C library |
| Classifier | `hey_jarvis.tflite` v0.1 (303 KB) | `hey_jarvis.tflite` v2 (52 KB INT8) |
| Runtime | pure Kotlin + `org.tensorflow:tensorflow-lite` | `tflite-micro` + `kissfft` via **JNI/NDK** |

microWakeWord's feature extractor is not a model, so the swap introduces **native code**
to the project for the first time. That is the bulk of the work.

## Components

### 1. New Android library module `:microwakeword`

Ported from HA's `microwakeword/` module, renaming the package
`io.homeassistant.companion.android.microwakeword` →
`uk.org.retallack.jarvis.microwakeword`.

```
microwakeword/
  build.gradle.kts                 # android library, NDK 27.2.12479018, CMake 3.22.1
  src/main/AndroidManifest.xml
  src/main/cpp/
    CMakeLists.txt                 # FetchContent: kissfft, tflite-micro, flatbuffers,
                                   #   gemmlowp, ruy (pinned URL + SHA256)
    Logging.h
    MicroFrontendWrapper.{h,cpp}   # 40-feature frontend, FIXED_POINT=16
    MicroWakeWordEngine.{h,cpp}    # int8 tensors, stride accumulation, sliding window
    MicroWakeWord_jni.cpp          # RegisterNatives; FindClass -> our package
  src/main/kotlin/.../microwakeword/MicroWakeWord.kt   # Closeable JNI wrapper
```

Differences from HA's module (deliberate):
- Package rename + `Timber` → `android.util.Log`.
- **HWASan block removed** from CMakeLists — it instruments debug builds and then
  requires a `wrap.sh`, which we do not ship. Debug must just run.
- `abiFilters += "arm64-v8a"` (matches the app; keeps build time down).

### 2. Models

Replace the three OWW assets with the ESPHome v2 model + manifest:

```
app/src/main/assets/wakeword/hey_jarvis.tflite   (52272 bytes, v2)
app/src/main/assets/wakeword/hey_jarvis.json
```
Manifest: `probability_cutoff 0.97`, `feature_step_size 10`, `sliding_window_size 5`.

Delete `app/src/main/assets/models/*` (melspectrogram, embedding_model, hey_jarvis,
README). Keep `noCompress += "tflite"`.

### 3. App integration

- **`MicroWakeWordModelConfig.kt`** (new) — parse the manifest JSON (kotlinx.serialization).
- **`MicroWakeWordEngine.kt`** (new) — implements `WakeWordEngine`; replaces
  `LiteRtWakeWordEngine.kt` (deleted).
  - `initialize()` loads the model into a **direct ByteBuffer** (JNI requires direct).
  - `startListening()` creates/reuses the native `MicroWakeWord`, **warms it up** with
    ~4 s of synthetic low-level noise (the frontend needs 1–2 s of background audio to
    prime its noise estimate; without it a wake word right after start is missed), then
    marks LISTENING.
  - `processAudio()` → `native.processAudio()`; on `true`, set DETECTED + emit.
  - `setSensitivity()` remaps the slider to `probability_cutoff` (the cutoff is a
    constructor arg, so the native engine is recreated on the next start).
- **`ModelPaths.kt`** — point at `wakeword/hey_jarvis.tflite` + JSON.
- **`VoiceModule.kt`** — bind the new engine.
- **`WakeWordService.kt`** — audio chunk `FRAME_SIZE` 1152 → **160 samples (10 ms)**;
  the rest of the pipeline (STT hand-off, mic release/resume, notifications) is unchanged.
- **`app/build.gradle.kts`** — `implementation(project(":microwakeword"))`; drop
  `libs.tensorflow.lite` (nothing else uses it).

### 4. Tests

- Unit: rewrite `ModelPathsTest` (new paths); replace `WakeWordThresholdTest` with a
  test of the new `detectionCutoff(sensitivity, manifestCutoff)` single-source function.
- Instrumented (`app/src/androidTest`): mirroring HA's `MicroWakeWordModelTest` — loads
  the model from assets, asserts silent audio does **not** trigger detection, asserts
  reset/close do not crash. This is the only way to exercise the native module.
- Nothing else referenced OWW constants.

### 5. Docs

- Update `docs/wake-word-stt-investigation.md` with a §11 describing the swap.
- Update `AGENTS.md` project notes (model retention numbers may change).

## Sensitivity mapping

`detectionCutoff(s, manifestCutoff) = (manifestCutoff + (0.5 - s) * 0.2).coerceIn(0.5, 0.99)`

Slider semantics are preserved: **lower sensitivity = higher cutoff = fewer false
positives**. Default (0.5) = the model's validated `probability_cutoff`; the slider nudges
±0.1 around it.

## Risks

- **NDK/CMake FetchContent** downloads pinned tarballs at configure time (network on first
  build). Largest risk; de-risked by building early.
- **Warm-up** changes detection timing at service start.
- **Cutoff semantics** differ from OWW (0.97 vs 0.725) — remapped and documented.
- Positive detection (an actual "Hey Jarvis") cannot be verified without speaking; the
  agent verifies init / no-false-positive / liveness and the user confirms by voice.

## Verification steps

1. `./gradlew :microwakeword:assembleDebug` — native module builds.
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest` — unit tests green.
3. Install to the Moto G52; launch; confirm logs: native lib loaded, engine created with
   reasonable stride/scale, "Audio capture started", no false detections while idle.
4. `:app:connectedDebugAndroidTest` for the instrumented native tests.
5. User says "Hey Jarvis" to confirm end-to-end.
