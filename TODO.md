# TODO

## Known Issues

1. **Long delay after stopping speech before command response** — After silence is detected and recording stops, there's a noticeable delay before the HA response. This may be Whisper processing time (loading model + inference). Investigate whether model can stay loaded, or if a smaller/faster model helps.

2. **Can we use streaming STT?** — Currently using Whisper (batch mode: record all → process all at once). Streaming would give real-time word display as user speaks. The Zipformer streaming model we tried earlier had accuracy issues ("back door" → "battor"). Options:
   - Try a larger streaming model (more accurate but more RAM)
   - Hybrid: stream for display, Whisper for final result
   - Investigate Moonshine (smaller, faster alternative to Whisper)
   - Accept batch mode but optimise processing speed

## Pending

- Build instructions to be added to README
- Contribution guidelines to be added to README
- Wake word TFLite inference needs tuning (false positive rate)
- Vibration on wake word detection not always felt
- App doesn't come to foreground on wake word (Android 14+ restriction)
- Download progress UI sometimes doesn't update percentage
