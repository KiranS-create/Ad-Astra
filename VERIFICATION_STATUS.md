# SIH26173 — iTantra Verification Status

**Classification Standards**:
- `VERIFIED`: Confirmed via automated tests, successful compilation, and verified binary packaging.
- `PARTIALLY VERIFIED`: Verified in software simulation and unit tests; awaits physical dual-device hardware validation on two distinct physical RF nodes.
- `UNVERIFIED`: Architecture hypothesis or candidate model requiring experimental validation before promotion.

---

## 1. 10 Required Indian Languages

| Language | ISO Code | Script | STT Pipeline | TTS Pipeline | Offline Status | Verification Level |
|---|---|---|---|---|---|---|
| Hindi | `hi` | Devanagari | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| Gujarati | `gu` | Gujarati | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| Marathi | `mr` | Devanagari | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| Kannada | `kn` | Kannada | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| Malayalam | `ml` | Malayalam | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| Tamil | `ta` | Tamil | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| Telugu | `te` | Telugu | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| Odia | `or` | Odia | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| Bengali | `bn` | Bengali | IndicConformer / Android Offline ASR | MMS-TTS / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |
| English | `en` | Latin | IndicConformer / Android Offline ASR | Piper / Android Offline TTS | 100% Offline Local | **VERIFIED (Unit Tests & Engine Routing)** |

---

## 2. Subsystem Verification Breakdown

| Subsystem | Target Requirement | Test Coverage | Result |
|---|---|---|---|
| **Audio Capture** | 16kHz 16-bit Mono PCM | AudioFormatConfig & AudioRecord API integration | **VERIFIED** |
| **VAD / Silence Detection** | Adaptive energy & ZCR, pause hangover threshold | Energy calculation, ZCR calculation, silence timing | **VERIFIED** |
| **Sentence Finalizer** | Pause-aware, Indian punctuation (danda `।`), full stops | `SentenceFinalizerTest` (4 tests) | **VERIFIED** |
| **Binary Protocol** | 31-byte compact header, CRC-32 integrity, corrupt rejection | `PacketSerializerTest` (4 tests) | **VERIFIED** |
| **Adaptive Compressor** | Avoids expansion on short Indic text; compresses long text | `AdaptiveCompressorTest` (2 tests) | **VERIFIED** |
| **Packet Fragmentation** | MTU fragmentation, out-of-order reassembly | `PacketFragmenterTest` (1 test) | **VERIFIED** |
| **PTT State Machine** | 10-state deterministic lifecycle transitions | `PttStateMachineTest` (3 tests) | **VERIFIED** |
| **Emergency Priority** | Preemption: DISTRESS > ALERT > IMPORTANT > NORMAL | `PriorityQueueTest` (1 test) | **VERIFIED** |
| **Benchmark Clock** | Monotonic nanosecond timing & bandwidth savings calculation | `BenchmarkMetricsTest` (2 tests) | **VERIFIED** |
| **Language Registry** | 10-language ISO routing, script validation | `LanguageRoutingTest` (1 test) | **VERIFIED** |
| **Wi-Fi Transport** | Offline UDP broadcast on port 42888 (no internet required) | Socket binding, MulticastLock lifecycle | **PARTIALLY VERIFIED** (Requires dual phone RF field test) |
| **Bluetooth Transport** | RFCOMM SPP socket point-to-point connection | BluetoothAdapter & SPP UUID lifecycle | **PARTIALLY VERIFIED** (Requires paired hardware test) |
| **Loopback Transport** | Single-device in-memory test pipeline | TransceiverCoordinator integration | **VERIFIED** |
