# Wake Word & STT Reliability — Investigation Log

Running log of the voice pipeline problems on the Moto G52 test device and what has been
tried. **Read this before changing wake-word or STT behaviour.**

Device context that drives most of this: **3.6 GB RAM, 2.6 GB swap, chronically
memory-starved.** The kernel low-memory killer (LMK) fires constantly and reaps
background apps indiscriminately (Firefox, Telegram, Nextcloud, weather apps, etc.).

---

## Symptom summary

1. App silently stops after a while — no crash, no ANR.
2. "Hey Jarvis" recognised, but every command answered "I didn't catch that."
3. Chat list only updated when navigating away and back.
4. First word of a spoken command was clipped.
5. Wake word died and never came back until the app was manually opened.

---

## Findings and fixes (in the order they were found)

### 1. App killed by the OS (LOW_MEMORY)

**Evidence:** `dumpsys activity exit-info` showed **every** JarvisHA exit as
`reason=3 (LOW_MEMORY)`, rss 89–350 MB. Live `lowmemorykiller` lines confirmed.

**Cause:** the app held ~160 MB of Sherpa-ONNX/Whisper native heap while running the
wake-word foreground service, in a process the LMK considered expendable.

**Tried:**
| Change | Result |
|---|---|
| Wake word moved to dedicated `:wakeword` process | ✅ Works — listener survives main app death |
| Removed `android:largeHeap="true"` | ✅ No benefit lost; reduced process ceiling |
| `AppForegroundState` to release model when backgrounded | ✅ Worked, but caused the bug in §5 |

**Side effect:** a headless process has no activities, so `isAppForeground` was always
false there and the model was released the instant it loaded. Fixed by also tracking
active wake-word *sessions*, not just visible activities.

---

### 2. "I didn't catch that" on every command

**Evidence:** log showed recognition completing, then `finalText == null`.

**Cause:** `WakeWordService` subscribed to `sttEngine.results` **after** listening
finished. The result had already been emitted, so the collector raced and always missed it.

**Fix:** subscribe *before* `startListening()`, collect into a `CompletableDeferred`,
await with a timeout. Also added `timestampMs` to `SttResult` to discard stale results
replayed from the engine's `replay = 1` buffer.

**Result:** ✅ Works.

---

### 3. Whisper repetition loops

**Evidence:** `Whisper result: 'turn kitchen radio on turn kitchen radio on ... (x31)'`
→ Home Assistant replied "No matching devices found" (it cannot parse a 200-word string).
A silent clip produced `'[ Inaudible ]'`.

**Cause:** Whisper fails to emit an end-of-text token on poor-quality audio and loops.

**Tried:**
| Change | Result |
|---|---|
| `collapseRepetitions()` — collapse whole-string / immediate repeats | ✅ Works (7 unit tests) |
| Skip transcription when VAD saw no speech | ✅ Works — stopped the `[ Inaudible ]` hallucination |

**Important:** this was a *symptom* fix. The real cause was §4.

---

### 4. Near-silent microphone (root cause of #3)

**Evidence:** on a failed run, `Mic level: peak=133` for a full 15 s — essentially room
noise. Good runs showed `peak=4692` / `peak=6746` / `peak=13037`.

**Cause:** the wake-word service held an `AudioRecord`, then the STT engine opened a
**second** one on the same source. Android starves the second session.

**Fix:** `pauseWakeWordCapture()` releases the wake-word `AudioRecord` before STT
captures, and `resumeWakeWordCapture()` restores it afterwards.

**Result:** ✅ Works. peak went from ~133 → thousands.

---

### 5. First word clipped / prompt beep recorded

**Evidence:**
```
20:39:59  Wake word detected
20:40:02  Beep played - microphone is live
20:40:07  Mic level: peak=13037, speechDetected=true
```
Earlier, the beep landed in the recorded buffer and set `hasDetectedSpeech` immediately,
so VAD timing and the Whisper window started on the tone, not the speech.

**Cause:** the beep played *before* the mic was ready (the code did
`Thread.sleep(250)` + `Thread.sleep(300)` + setup, then recorded), so the prompt was
"speak now" while nothing was listening. The beep audio was then captured.

**Fix (all five):**
1. `discardAudioUntil()` — drop captured audio during the beep window
2. Capture starts **before** the beep, so the mic is genuinely live when you hear it
3. `warmUp()` / model load happens **before** the beep
4. Beep audio no longer influences `hasDetectedSpeech`
5. Model load moved to a dedicated thread (`whisper-model-loader`) so it no longer
   competes with the audio path on `Dispatchers.IO`

**Result:** ✅ Works. `'What time is it?'` recognised in full, first word intact, stop
on silence at 5 s instead of running to the 13 s max.

**Note:** on a cold start there is now a **~2.5 s pause before the beep** (the honest
prompt). This is a deliberate trade — see §7.

---

### 6. Chat list didn't update live

**Evidence:** messages were written by `:wakeword` but the UI (main process) was stale
until the screen was recreated.

**Cause:** Room's `InvalidationTracker` is **per-process**. Cross-process writes don't
notify the other process's observers.

**Fix:** `ConversationSync.notifyChanged()` broadcasts `ACTION_CONVERSATION_CHANGED`
from the writing process; the UI registers a receiver and restarts its Room observation.
Signalled immediately after the user message, and again when the command completes.

**Result:** ✅ Works.

---

### 7. Wake word model retention / memory trade-off ⚠️ IMPORTANT

Two attempts, one of which **failed in production**:

**Attempt A — keep model warm for 10 minutes.**
- Rationale: avoid a ~2.5 s reload on every command.
- Added `wakeWordServiceRunning` signal to `AppForegroundState` + a pre-warm at service start.
- **RESULT: FAILED.** `:wakeword` grew to **387 MB** and was **killed** at 15:12
  (`reason=3 LOW_MEMORY, rss=159MB`). Wake word was then dead for ~4 hours because
  nothing restarted it.
- **REVERTED.**

**Attempt B — current state: 30 s idle timeout, no pre-warm.**
- `idleTimeoutMs = 30 * 1000L`
- Model releases ~30 s after each command. Measured: native heap 234 MB → 34 MB,
  PSS 148 MB.
- **RESULT: ✅ Stable.** Process stays small (the ~100–134 MB range that survived a
  full day previously).

**Current behaviour:**
```
command → model resident (~240 MB native) → released 30 s later (~34 MB)
```

**Subtlety:** `scheduleIdleRelease()` is called while the session is still marked active,
so it takes the *timer* branch rather than releasing immediately. This is why release is
30 s, not instant — and it means a follow-up command within 30 s is warm. Intentional,
but note it if you change the session lifecycle.

---

### 8. Wake word dead after being killed / after reboot

**Evidence:** after the 15:12 kill, wake word never returned until the app was opened.
`JarvisApplication.autoStartWakeWordService()` claimed to handle "phone reboot", but there
was **no boot receiver**.

**Fixes:**
- `START_STICKY` + null-intent handling, so the system restarts the service after an LMK
  kill. Verified on device: `stopIfKilled=false`.
- `onTaskRemoved()` — swipe-away keeps the listener running.
- `BootReceiver` + `RECEIVE_BOOT_COMPLETED` for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
  `QUICKBOOT_POWERON`. Verified registered in `dumpsys package`.

**Result:** ✅ Verified on device.

---

### 9. Wake word sensitivity setting was inert

Three separate bugs:

1. **Never persisted.** The setup wizard's slider only updated in-memory UI state;
   `completeSetup()` saved only the HA url/token. The DataStore key existed but nothing
   read or wrote it.
2. **Never applied.** `WakeWordService` never called `wakeWordEngine.setSensitivity()`, so
   the engine kept its built-in default of `0.5`.
3. **The log lied about the threshold.** `isDetected()` used
   `0.95f - (sensitivity * 0.45f)` but the log printed `0.9f - (sensitivity * 0.8f)`.
   At the default that meant logging **0.50** while actually applying **0.725**.

**Fixes:** persist from setup; load and apply in the service; single-source
`LiteRtWakeWordEngine.detectionThreshold()` used by both decision and log (5 unit tests);
sensitivity control added to Settings.

**Result:** ✅ Verified: `Sensitivity set to 0.5 (detection threshold 0.725)`.

**⚠️ Direction is counter-intuitive:** LOWER sensitivity = HIGHER threshold = FEWER false
positives. Slider **left** = fewer false triggers; **right** = more responsive.

| Sensitivity | Threshold | False triggers |
|---|---|---|
| 0% | 0.95 | fewest |
| 50% (default) | 0.725 | — |
| 100% | 0.50 | most |

---

## Known outstanding issues

1. **False positives.** Observed scores of **0.97/0.98** against a 0.5–0.79 threshold.
   These are genuinely confident detections, so the sensitivity slider **cannot** fix
   them — even at 0% (threshold 0.95) they pass. Next step would be requiring **N
   consecutive frames** above threshold rather than a single frame.
2. **App never comes to the foreground on wake.** `ACTION_WAKE_WORD` is declared and
   handled in `MainActivity` but **nothing ever sends it**.
3. **Diagnostic logging still present.** `Mic level: peak=...` every ~5 s in
   `SherpaOnnxSttEngine`, plus phase markers. Useful for debugging; should be gated
   behind a debug flag or removed before release.
4. **Cold-start delay.** ~2.5 s between detection and the beep when the model isn't warm.
   Could be reduced by a *bounded* keep-warm (e.g. 2 min) if memory allows — but see §7,
   this already went wrong once. Do not raise it without measuring the process size.
5. **Device memory pressure is the underlying constraint.** JarvisHA can be made small,
   but the LMK on this device reaps apps aggressively regardless.

---

## Debugging technique that worked

Pull the app's own logs with a large-enough buffer:

```bash
adb logcat -b all -G 4M          # default 256 KiB gets overwritten constantly
adb logcat -b all -c             # clear before a test
# ...reproduce...
adb logcat -d -v threadtime | grep -E "SherpaSTT|WakeWordService|LiteRt"
```

Forensics when logs have rotated:
```bash
adb shell dumpsys activity exit-info uk.org.retallack.jarvis   # kill reasons
adb shell dumpsys meminfo uk.org.retallack.jarvis:wakeword     # process size
adb shell dumpsys activity services uk.org.retallack.jarvis    # stopIfKilled, FGS state
adb shell dumpsys package  uk.org.retallack.jarvis | grep -A3 BootReceiver
```

**Key log markers to look for:**

| Marker | Meaning |
|---|---|
| `STT warm before prompt: true (took Nms)` | model loaded; >2000ms means cold |
| `Capture started; prompting user now` | mic is live |
| `Beep played - microphone is live` | prompt is honest |
| `Capture window open (beep discarded)` | beep excluded from recording |
| `Mic level: peak=...` | healthy is thousands; <200 means dead/contended mic |
| `Silence detected after speech` | clean endpoint (good) |
| `Max recording time reached` | VAD never found the end (suspect beep bleed) |
| `Idle timeout reached, releasing` | model released, next command is cold |
| `Restarted by system after being killed` | sticky restart worked |
| `Collapsing Whisper repetition loop` | repetition guard fired |

---

## Guidance for future changes

- **Do not increase model retention without measuring `:wakeword` RSS.** It got killed at
  387 MB. Verified stable at ~148 MB PSS / 34 MB native heap idle.
- **Keep the model load off the audio path.** It must not block the `AudioRecord` opening.
- **Never subscribe to `sttEngine.results` after capture starts** — the result races.
- **Always clear the log buffer before a reproduction** (`adb logcat -b all -c`), and use
  `-b all -G 4M`. The default buffer is far too small.
- **Reinstalling the APK kills both processes** and does not restart the listener until
  the app is launched. Expect "wake word dead" right after an install.
