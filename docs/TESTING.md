# Testing & Verification Methodology — iTantra

**Project:** iTantra (Ad Astra)  
**Smart India Hackathon 2026** • **Problem Statement:** SIH26173  

---

## 1. Verification Strategy

The iTantra codebase is validated through a dual-track testing strategy:
1. **Automated Unit & Integration Test Suites:** Continuous unit verification of protocol framing, CRC/HMAC cryptographic integrity, speech state machines, adaptive representations, and store-and-forward routing logic.
2. **Physical Multi-Device Hardware Validation:** End-to-end operational testing across real physical Android devices in disconnected, off-grid environments over Wi-Fi multicast and Bluetooth SPP links.

---

## 2. Automated Test Suite

### 2.1 Overview
The project contains **678+ automated unit tests** executed via the Gradle test runner.

To execute the full test suite locally:
```bash
./gradlew testDebugUnitTest
```

### 2.2 Core Test Suites & Coverage Areas

| Subsystem | Primary Test Classes | Verification Scope |
|---|---|---|
| **Radio Framing & Integrity** | `RadioPacketTest`, `PacketFramingTest`, `Crc32Test`, `HmacAuthenticatorTest` | Fixed 28B header layout, endianness, bit corruption rejection, HMAC-SHA256 signature verification |
| **Speech Recognition & Splicing** | `TargetedTwoPassSpeechEngineTest`, `TargetedRefinementPolicyTest`, `ZeroWaitSplicerTest` | Streaming pass 1 tokens, pause detection ($\ge 250\text{ ms}$), candidate refinement, $< 10\text{ ms}$ release splice |
| **Adaptive Representation** | `AdaptiveRepresentationPolicyTest`, `SharedContextEngineTest`, `ContextDeltaTest` | Dynamic mode selection (FULL, COMPACT, SEMANTIC), 8B semantic base, 6–7B delta updates |
| **MANET Routing & DTN** | `AutonomousRelayTest`, `SequenceCacheTest`, `DtnMessageStoreTest` | TTL decrement, multi-hop duplicate suppression, offline SQLite storage, delivery state transitions |
| **Voice Synthesis Fallback** | `TtsEngineSelectorTest`, `IndicVoiceConfigTest` | Piper/Mimic3 model loading, Meta MMS ONNX path resolution, Android System TTS graceful fallback |

---

## 3. Physical Hardware Testbed

Physical multi-device testing was conducted using two dedicated Android devices in an air-gapped test environment:

```
+------------------------------------+       +------------------------------------+
|            Device A                |       |            Device B                |
|  Samsung Galaxy Note 10 Lite       | <---> |  Samsung Galaxy A55 5G             |
|  OS: Android 13 (ARM64-v8a)        | Wi-Fi |  OS: Android 14 (ARM64-v8a)        |
|  RAM: 6 GB / SoC: Exynos 9810      |   /   |  RAM: 8 GB / SoC: Exynos 1480      |
|  Node ID: 0x00000001               |  BT   |  Node ID: 0x00000002               |
+------------------------------------+       +------------------------------------+
```

---

## 4. Physical Test Scenarios & Results

### 4.1 Scenario 1: Push-to-Talk (PTT) Voice Pipeline & Zero-Wait Splicing
- **Test Objective:** Verify that releasing the PTT button produces an outbound packet in $< 10\text{ ms}$ without blocking on model inference.
- **Procedure:** Spoke tactical emergency phrases in English and Hindi into Phone A while recording timestamp telemetry at PTT release and socket dispatch.
- **Result:** **PASSED.** In-flight pass 2 background jobs were terminated or spliced cleanly, and outbound radio frames were generated and dispatched within 4–8 ms.

### 4.2 Scenario 2: Bandwidth Footprint Verification
- **Test Objective:** Verify packet wire footprints across FULL, COMPACT, SEMANTIC_BASE, and CONTEXT_DELTA modes.
- **Observed Wire Footprints:**
  - `FULL_VOICE_TEXT`: 92–164 bytes
  - `COMPACT_VOICE`: 74–108 bytes
  - `SEMANTIC_BASE`: 40–48 bytes
  - `CONTEXT_DELTA`: 38–46 bytes
- **Result:** **PASSED.** Achieved a $> 99.8\%$ bandwidth reduction compared to streaming 16 kHz uncompressed PCM audio.

### 4.3 Scenario 3: Ad-Hoc Multi-Hop Relay & Loop Suppression
- **Test Objective:** Validate that intermediate nodes forward broadcast packets with decremented TTL and reject duplicate packets.
- **Procedure:** Transmitted sequence-numbered packets from Phone A through Phone B to a listening monitor; reflected packets back toward Phone A.
- **Result:** **PASSED.** Phone B decremented TTL by 1 and forwarded the frame. Reflected packets were dropped immediately via the LRU sequence cache.

### 4.4 Scenario 4: Offline Multilingual Neural Synthesis
- **Test Objective:** Verify offline speech synthesis across Indian languages without internet connectivity.
- **Languages Verified:** English, Hindi, Marathi, Gujarati, Malayalam, Telugu, Bengali (via on-device Piper/Mimic3 ONNX models), and Kannada, Tamil, Odia (via MMS / System TTS fallback).
- **Result:** **PASSED.** Audio output played clearly through the physical phone speaker with zero cloud requests.
