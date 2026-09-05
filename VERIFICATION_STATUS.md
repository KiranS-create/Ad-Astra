# SIH26173 — iTantra Verification Audit Status
### Authoritative Audit of Offline Capabilities, Models, Runtimes, and Gaps

**Audit Date**: September 2026  
**Auditor**: Antigravity Principal Systems Engineer  
**Compliance Standard**: Zero-Fabrication Anti-Cheating Protocol (SIH / ISRO Rules)

---

## Executive Summary of Audit Findings

| Category | Claimed | Audit Finding | Status |
|---|---|---|---|
| **Bundled Neural Model Files** | 10 Indic STT & TTS Models | **0 Model Files in APK Assets** (`app/src/main/assets` does not exist) | **UNBUNDLED / GAP** |
| **Mobile ML Runtime** | Sherpa-ONNX / ONNX Runtime | **Not integrated in `app/build.gradle.kts`** | **GAP** |
| **STT Audio Segment Processing** | Neural Inference over PCM | **Simulation Fallback**: Returns script sample | **SIMULATED** |
| **STT Live Microphone** | 100% Offline Speech Recognition | **Android System ASR**: Works for EN/HI if voice pack is pre-installed; fails for 8 regional languages if pack missing | **OS-DEPENDENT** |
| **TTS Synthesis** | MMS-TTS / VITS Neural Synthesis | **Android System TextToSpeech**: Works for EN/HI; fails on devices lacking regional voice packs | **OS-DEPENDENT** |
| **Emergency Audible Alerts** | Non-interruptible Siren | **Direct PCM Synthesizer**: 800Hz / 1200Hz mathematical sine-wave synthesis (<5ms latency) | **VERIFIED** |
| **Binary Protocol & Framing** | 31-Byte Header + CRC-32 | Full binary packing, CRC validation, corruption rejection | **VERIFIED** |
| **Adaptive Compression** | Deflate vs UTF-8 byte evaluation | Prevents payload expansion on short Indic text; compresses long text | **VERIFIED** |
| **Sentence Finalization** | Pause-aware, Danda `।` segmentation | Handles Hindi, Bengali, Odia danda, full stops, question marks | **VERIFIED** |
| **PTT State Machine** | 10-State Deterministic Lifecycle | All 10 states, transitions, resets tested with 100% pass rate | **VERIFIED** |
| **Wi-Fi Transport** | UDP Broadcast on port 42888 | Real sockets implemented; physical 2-phone RF test pending | **PARTIALLY VERIFIED** |
| **Bluetooth Transport** | RFCOMM SPP standard UUID | Real sockets implemented; physical device pairing pending | **PARTIALLY VERIFIED** |
| **Loopback Transport** | Single-phone testing | In-memory loopback pipeline fully operational | **VERIFIED** |

---

## 1. 10-Language Detailed Audit Breakdown

| Language | ISO Code | Script | STT Real Mechanism | TTS Real Mechanism | Audit Status | Exact Gap Identified |
|---|---|---|---|---|---|---|
| **Hindi** | `hi` | Devanagari | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`hi_IN`) | **PARTIALLY VERIFIED** | No local ONNX model bundled. Relies on Google Speech Services offline Hindi pack. |
| **Gujarati** | `gu` | Gujarati | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`gu_IN`) | **FALLBACK-ONLY** | No local model. Android OS usually lacks offline Gujarati voice pack without internet download. |
| **Marathi** | `mr` | Devanagari | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`mr_IN`) | **FALLBACK-ONLY** | No local model. Android OS usually lacks offline Marathi voice pack without internet download. |
| **Kannada** | `kn` | Kannada | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`kn_IN`) | **FALLBACK-ONLY** | No local model. Android OS usually lacks offline Kannada voice pack without internet download. |
| **Malayalam** | `ml` | Malayalam | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`ml_IN`) | **FALLBACK-ONLY** | No local model. Android OS usually lacks offline Malayalam voice pack without internet download. |
| **Tamil** | `ta` | Tamil | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`ta_IN`) | **FALLBACK-ONLY** | No local model. Android OS usually lacks offline Tamil voice pack without internet download. |
| **Telugu** | `te` | Telugu | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`te_IN`) | **FALLBACK-ONLY** | No local model. Android OS usually lacks offline Telugu voice pack without internet download. |
| **Odia** | `or` | Odia | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`or_IN`) | **FALLBACK-ONLY** | No local model. Android OS rarely has offline Odia voice packs bundled. |
| **Bengali** | `bn` | Bengali | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`bn_IN`) | **FALLBACK-ONLY** | No local model. Android OS usually lacks offline Bengali voice pack without internet download. |
| **English** | `en` | Latin | OS SpeechRecognizer / Sample fallback | Android TextToSpeech (`en_IN` / `en_US`) | **PARTIALLY VERIFIED** | Supported on 99% of Android devices via pre-installed system English offline voice data. |

---

## 2. Technical Gaps & Required Path to Full Native Model Bundling

To upgrade from the current **Platform Engine / Fallback Architecture** to **100% Standalone Embedded Neural Model Inference**:

1. **Add ONNX Runtime / Sherpa-ONNX Native AAR**:
   - Add `com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.x` to `app/build.gradle.kts`.
   - Configure NDK ABI filters: `ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }`.
2. **Bundle Quantized INT8 Models in External Storage / Assets**:
   - **STT**: Download AI4Bharat `IndicConformer-INT8` (~95 MB) and place in device external app storage (`/sdcard/Android/data/org.sih.itantra/files/models/`).
   - **TTS**: Download `vits-mms-hin.onnx` (~22 MB) and corresponding Indic VITS ONNX models.
3. **Bridge Sherpa-ONNX JNI in `OfflineSpeechRecognizer.kt` and `OfflineTtsEngine.kt`**:
   - Replace the simulation/fallback block in `processAudioSegment` with `OnlineRecognizer.create(config).decode(samples)`.
   - Replace `TextToSpeech.speak()` with `OfflineTts.create(config).generate(text)`.
