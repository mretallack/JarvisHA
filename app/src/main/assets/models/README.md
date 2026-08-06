# Wake Word Models

## Source

All wake word models are from the [openWakeWord](https://github.com/dscripka/openWakeWord) project by David Scripka.

### Files

| File | Size | Purpose | Version |
|------|------|---------|---------|
| `melspectrogram.tflite` | 1.1MB | Audio preprocessing (mel spectrogram extraction) | v0.5.1 |
| `embedding_model.tflite` | 1.3MB | Audio embedding (feature extraction) | v0.5.1 |
| `hey_jarvis.tflite` | 303KB | Wake word detection model ("Hey Jarvis") | v0.1 |

### Download URLs

- melspectrogram: https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/melspectrogram.tflite
- embedding: https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/embedding_model.tflite
- hey_jarvis: https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/hey_jarvis_v0.1.tflite

### How It Works

OpenWakeWord uses a three-stage pipeline:
1. **melspectrogram.tflite** — converts raw audio (16kHz mono) into mel spectrogram features
2. **embedding_model.tflite** — converts mel features into a compact audio embedding
3. **hey_jarvis.tflite** — classifies the embedding as wake word detected or not

All three models run sequentially on each audio frame (~80ms of audio).

## License

```
Apache License 2.0

Copyright (c) 2022 David Scripka

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Updating Models

To update to newer versions:
1. Check https://github.com/dscripka/openWakeWord/releases for new preprocessing models
2. Check https://github.com/dscripka/openWakeWord/tree/main/openwakeword/resources/models for updated wake word models
3. Download and replace files in this directory
4. Test detection accuracy before committing
