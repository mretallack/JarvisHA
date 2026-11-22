# Investigation: STT Speed & Performance Analysis

## Overview
Speech-to-Text (STT) processing in JarvisHA is handled primarily by **`SherpaOnnxSttEngine`**, which utilizes **Sherpa-ONNX** running an offline **Whisper Tiny (`tiny.en-encoder.int8.onnx` & `tiny.en-decoder.int8.onnx`)** model.

---

## Identified Bottlenecks & Architectural Observations

### 1. On-Demand Model Loading & Unloading
* **Implementation:** In `SherpaOnnxSttEngine.kt`, the Whisper model is loaded into memory via `loadModel()` every time speech recognition starts, and is explicitly set to `null` (released) immediately after transcription completes in `processBufferedAudio()`.
* **Impact:** 
  * Loading large ONNX models (`.onnx` encoder and decoder files) from internal storage on every utterance incurs a **massive CPU and disk I/O latency penalty** (often taking several seconds depending on device storage speed).
  * Repeated allocation and deallocation also causes GC pressure and overhead.

### 2. Number of CPU Threads
* **Implementation:** `numThreads = 2` is hardcoded in `OfflineModelConfig`.
* **Impact:** 
  * On modern multi-core mobile processors (e.g., 6 to 8 cores), limiting NN inference to 2 threads may underutilize available CPU performance during ONNX matrix multiplications. 
  * Conversely, too many threads can cause thread contention and context-switching overhead. Testing with 4 threads or dynamic core detection could optimize inference speed.

### 3. Whisper Model Choice (`whisper-tiny.en`)
* **Implementation:** Uses Whisper Tiny quantized int8.
* **Impact:** 
  * While Whisper Tiny is much faster than Base/Small, Whisper is inherently a heavier sequence-to-sequence model compared to CTC-based models (like Sherpa's Zipformer, Transducer, or Vosk/Futo). 
  * Whisper decodes autoregressively token-by-token, which is computationally expensive on mobile CPUs, particularly for longer audio snippets.

### 4. Audio Capture & Recording Buffering
* **Implementation:** Audio is recorded via `AudioRecord` at 16kHz mono into an in-memory `MutableList<FloatArray>` chunk by chunk, waiting for silence detection (`silenceDurationMs`) or manual stop before feeding the entire buffer into Whisper at once.
* **Impact:** If `silenceDurationMs` is set too high or the user pauses briefly while speaking, the recording window remains open longer. Additionally, processing the entire accumulated audio buffer in one monolithic batch call can delay the first token emission.

---

## Recommendations for Improvement

1. **Persistent Model Instance (Keep-Warm / Caching):**
   * Keep the `OfflineRecognizer` instance loaded in memory (or cache it after first use with a timeout/lifecycle management) instead of loading and unloading on every single utterance. This eliminates the multi-second model load overhead per interaction.
2. **Optimize Thread Count (`numThreads`):**
   * Experiment with increasing `numThreads` to 4 on multi-core devices or making it configurable based on available runtime processors (`Runtime.getRuntime().availableProcessors()`).
3. **Explore Streaming / Alternative Engines:**
   * Evaluate Sherpa-ONNX streaming transducer models (e.g., Zipformer transducer) which emit tokens incrementally as speech arrives, rather than waiting for utterance completion and doing batch sequence-to-sequence decoding.
4. **Tune VAD and Silence Thresholds:**
   * Review default `silenceDurationMs` and audio sensitivity settings in `SttSettings` so recording finishes promptly when speech stops.

---

## Optimizing the Full STT → Home Assistant Pipeline (Non-Streaming Focus)

If you wish to speed up the end-to-end pipeline (from finishing speech to receiving the Home Assistant response) without adopting streaming STT models, several targeted options exist across model execution, local inference hardware acceleration, VAD tuning, and network dispatch:

### A. Eliminate Model Load/Unload Overhead (The #1 Latency Culprit)
* **Current state:** `SherpaOnnxSttEngine` calls `loadModel()` right before decoding and sets `recognizer = null` right after. Every single command pays the heavy cost of reading `encoder.onnx` and `decoder.onnx` from flash storage and initializing the ONNX runtime.
* **Speedup Option:** Implement a **Warm Cache with LRU / Idle Timeout** (e.g., keep the model in memory for 30–60 seconds after use before releasing, or keep it loaded while the app is foregrounded). This instantly cuts several seconds off every voice command after the first one.

### B. Hardware Acceleration (NNAPI / GPU / XNNPACK)
* **Current state:** `provider = "cpu"` is hardcoded in `OfflineModelConfig`.
* **Speedup Option:** Enable **NNAPI** (`provider = "nnapi"`) or **XNNPACK** if supported by Sherpa-ONNX and the Android device. Offloading matrix math to the NPU/GPU or optimized CPU kernels can significantly reduce Whisper inference time.

### C. Optimize Whisper Decoding Parameters
* **Current state:** Uses `decodingMethod = "greedy_search"` with `numThreads = 2`.
* **Speedup Option:** 
  * Tune `numThreads` to match device core count (e.g., 4 threads).
  * Ensure compiler/runtime optimizations (like quantized int8 models, which are already used) are fully leveraged without unnecessary memory synchronization bottlenecks.

### D. Tighten Voice Activity Detection (VAD) & Silence Duration
* **Current state:** Relies on amplitude thresholding and `silenceDurationMs` before recording stops and processing begins.
* **Speedup Option:** Reduce `silenceDurationMs` slightly (e.g., from default down to ~400–500ms) so that processing begins immediately when the user finishes speaking, avoiding dead air at the tail end of the audio buffer.

### E. Pipeline Concurrency & Pre-Connection Warmup
* **Current state:** Sequential execution: Stop recording → Transcribe audio → Send HTTP POST to Home Assistant conversation API.
* **Speedup Option:** Ensure OkHttpClient connection pooling (`HaClient`) is kept warm with Keep-Alive so TCP/TLS handshakes to Home Assistant take 0ms when the STT text is finally ready to dispatch.
