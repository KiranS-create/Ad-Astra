# iTantra — Emergency Rescue Communication Stack Implementation Report

**Project**: iTantra (SIH26173)  
**Branch**: `main`  
**Baseline Commit**: `1698379`  
**Execution Date**: September 29, 2026  
**Scope**: Implementation of the 4 Approved Emergency Capabilities (1-Byte Emergency Payload, Real Byte-Saving Telemetry, BLE RSSI Locate Mode, Emergency GPS + Proximity Workflow) under strict zero-asset size constraints.

---

## 1. Executive Summary & Problem Context

In disaster response and tactical rescue scenarios (Smart India Hackathon Problem Statement SIH26173), communication networks are degraded, congested, or entirely non-existent. When life-critical distress signals must be transmitted over low-bitrate ad-hoc mesh links (BLE, Wi-Fi Direct, packet transceivers), every single byte impacts link latency, packet delivery probability, collision rates, and battery life.

This engineering implementation delivers four tightly integrated, mathematically truthful capabilities designed to optimize emergency throughput without compromising cryptographic integrity:

1. **1-Byte Emergency Payload (`EmergencyBypassCode`)**: A deterministic, low-overhead bypass opcode (`0x00`..`0x09`) that collapses standard emergency categories and subtypes into a single application payload byte.
2. **Real Byte-Saving Telemetry**: Comprehensive, transparent accounting of application payload bytes versus actual wire frame bytes across the UI and diagnostics layers.
3. **BLE RSSI Locate Mode (`LocateModeEngine`)**: A battery-conscious, qualitative proximity tracking engine using Exponential Moving Average (EMA) smoothing ($\alpha = 0.35$), multi-sample trend analysis, and a 15-second staleness watchdog.
4. **Emergency GPS + Proximity Workflow**: A 4-state tactical emergency grid (`EMERGENCY`, `LOCATION`, `PROXIMITY`, `NETWORK`), immediate "LOCATE" navigation affordances, and streamlined 1-byte distress composition.

### Strict Exclusions Adhered To
- **NO** acoustic siren/SOS generation.
- **NO** camera torch/flash strobe.
- **NO** LoRa or external SDR/GNU Radio integrations.
- **NO** iOS or desktop target ports.
- **NO** new ML models, TTS/STT checkpoints, or audio assets.
- **NO** new third-party dependencies added to `app/build.gradle.kts`.

---

## 2. 1-Byte Emergency Payload Architecture

### 2.1 Design & Protocol Integration

Standard semantic messages in iTantra serialize into a 6-byte semantic command or variable-length byte arrays. For rapid emergency broadcast, `SemanticCommand` and `PacketSerializer` have been extended with a dedicated 1-byte bypass mode:

- **Payload Size**: Exactly 1 byte payload (`0x00`..`0x09`).
- **Wire Frame Size**: **41 bytes** authenticated without GPS (28B Header + 1B Payload + 8B AuthTag + 4B CRC32) / **73 bytes** authenticated with 32B GPS coordinates.
- **Security Invariance**: Full cryptographic authentication (`HMAC-SHA256`, 8-byte truncated tag) is preserved. The 1-byte payload is fully included in the authenticated byte stream. HMAC provides authentication and tamper-evident integrity (not confidentiality/encryption).
- **Priority**: Distress Priority `P1` (`0x01`) is strictly enforced in `TacticalPacketScheduler`.
- **DTN Compatibility**: Monotonic message sequence IDs, source/destination addressing, anti-replay timestamps, and TTL are preserved for store-and-forward mesh routing.

### 2.2 Complete 1-Byte Emergency Bypass Mapping Table

| Code (Hex) | Enum Symbol | Category (`EmergencyCategory`) | Subtype (`EmergencySubtype`) | Tactical Dispatch Meaning |
|:---:|:---|:---|:---|:---|
| `0x00` | `GENERAL` | `OTHER` (0) | `NONE` (0) | GENERAL SOS / DISTRESS BEACON |
| `0x01` | `MEDICAL` | `MEDICAL` (1) | `NONE` (0) | MEDICAL EMERGENCY |
| `0x02` | `MEDICAL_INJURED` | `MEDICAL` (1) | `INJURED` (3) | INJURED - NEED FIRST AID |
| `0x03` | `MEDICAL_UNCON` | `MEDICAL` (1) | `UNCONSCIOUS` (2) | PERSON UNCONSCIOUS |
| `0x04` | `FIRE` | `FIRE` (2) | `BUILDING` (4) | FIRE EMERGENCY |
| `0x05` | `TRAPPED` | `TRAPPED` (3) | `COLLAPSE` (9) | TRAPPED - RESCUE NEEDED |
| `0x06` | `ATTACK` | `SECURITY` (7) | `NONE` (0) | UNDER ATTACK / HOSTILE |
| `0x07` | `EVACUATION` | `EVACUATION` (6) | `NONE` (0) | IMMEDIATE EVACUATION ORDER |
| `0x08` | `EXTRACTION` | `RESCUE` (4) | `TEAM` (5) | NEED TACTICAL EXTRACTION |
| `0x09` | `HAZARD` | `HAZARD` (8) | `NONE` (0) | HAZARD ALERT |

### 2.3 Wire Frame Sizing & MTU Bounds

The table below details the exact layer-by-layer wire frame breakdown:

| Packet Field | 1-Byte Bypass (No GPS) | 1-Byte Bypass (+ GPS) | Semantic 6B (No GPS) | Standard Text (50B) |
|:---|:---:|:---:|:---:|:---:|
| **Header (Version, Type, Flags, Prio, TTL, IDs, Time, Frag)** | 28 bytes | 28 bytes | 28 bytes | 28 bytes |
| **Location Block (Lat, Lon, Alt, Acc, Speed, Time)** | 0 bytes | 32 bytes | 0 bytes | 0 bytes |
| **Payload** | **1 byte** | **1 byte** | 6 bytes | 50 bytes |
| **AuthTag (HMAC-SHA256 Truncated)** | 8 bytes | 8 bytes | 8 bytes | 8 bytes |
| **CRC32 Checksum** | 4 bytes | 4 bytes | 4 bytes | 4 bytes |
| **Total Wire Frame Size (Authenticated)** | **41 bytes** | **73 bytes** | **46 bytes** | **90 bytes** |
| *Unauthenticated Variant (CRC only)* | *33 bytes* | *65 bytes* | *38 bytes* | *82 bytes* |

#### BLE MTU Sizing Qualification:
- On modern BLE 4.2/5.0+ stacks with standard ATT MTU exchange (185–512 bytes), the entire 41B or 73B frame transmits in a single unfragmented PDU.
- Under legacy unnegotiated ATT MTU (23 bytes total / 20 bytes payload), the 41B frame requires only 2 fragments (compared to 12+ fragments for uncompressed voice frames).

---

## 3. Real Byte-Saving Telemetry

To eliminate opaque or misleading compression metrics, the telemetry stack tracks both application-level payload sizes and physical on-the-wire frame sizes.

### 3.1 Persistence & Data Model
- `MessageRecord` updated with:
  - `payloadSizeBytes: Int?`: Exact raw payload byte count (1 byte for bypass).
  - `wireFrameBytes: Int?`: Fully framed physical byte count including header, location block, auth tag, and CRC.
  - `savingsPercentage: Double?`: Dynamic wire efficiency calculation comparing physical wire frame against a nominal 240-byte tactical voice/text frame baseline:
    $$\text{Savings \%} = \frac{240 - \text{wireBytes}}{240} \times 100$$
    (41B wire frame yields **82.9%** wire savings; 1B payload yields **99.6%** application payload savings).

### 3.2 Inspector & Diagnostics Integration
1. **Message Technical Inspector**:
   - Explicitly displays `VBR REPRESENTATION: 1-BYTE EMERGENCY BYPASS` with `ALERT` styling.
   - Explicitly displays `APP PAYLOAD: 1 Byte (1-Byte Bypass)`.
   - Explicitly displays `WIRE FRAME: 41 Bytes` (or 73 Bytes with GPS).
   - Displays computed savings percentage dynamically (e.g., `-199 B (-83%)`).
2. **Diagnostics Screen (`DiagnosticsScreen.kt`)**:
   - Real-time counters:
     - `emergencyBypassSent` & `emergencyBypassReceived`.
     - `emergency1BytePayloadBytes` & `emergencyWireBytes`.
     - Bandwidth efficiency and packet count ratios.

---

## 4. BLE RSSI Locate Mode Engine & UI

### 4.1 Signal Smoothing & Qualitative Proximity
In real-world disaster fields, RF signals experience multipath reflections, non-line-of-sight (NLOS) shadowing, and antenna orientation variances. Calculating an absolute Euclidean distance in meters from RSSI is physically untruthful.

`LocateModeEngine` enforces **qualitative proximity bands** combined with temporal filtering:

$$RSSI_{\text{filtered}}(t) = \alpha \cdot RSSI_{\text{sample}} + (1 - \alpha) \cdot RSSI_{\text{filtered}}(t-1)$$

Where $\alpha = 0.35$, balancing responsiveness against burst RF noise.

### 4.2 Proximity Bands & Trend Window

| Smoothed RSSI Range | Proximity Band | Tactical Meaning |
|:---|:---:|:---|
| $\ge -55\text{ dBm}$ | `VERY NEAR` | Imminent proximity (immediate clearing/room) |
| $[-65, -55)\text{ dBm}$ | `NEAR` | Close vicinity |
| $[-75, -65)\text{ dBm}$ | `CLOSER` | Signal acquiring / midrange |
| $< -75\text{ dBm}$ | `FAR` | Distant / edge of BLE reception |
| Stale (> 15s) or None | `UNKNOWN` | No signal detected / link timed out |

- **Trend Analysis Window (5,000 ms)**: Computes the delta across recent samples within a 5-second sliding window to determine:
  - `CLOSER` (Signal strength improving by $\ge +3.0$ dBm)
  - `FARTHER` (Signal strength degrading by $\le -3.0$ dBm)
  - `STABLE` (Signal delta within $\pm 3.0$ dBm)
  - `UNKNOWN` (Insufficient samples or stale)
- **Watchdog Timeout (15 seconds)**: If no BLE advertisement packet is received for 15 seconds, the engine automatically transitions proximity to `UNKNOWN` and marks signal status as `SIGNAL LOST (> 15s)`.

### 4.3 Battery-Safe Lifecycle Management
Scanning for BLE advertisements at low latency consumes battery power. `LocateModeEngine` is strictly lifecycle-bound:
- Initiates scanning **only** when active in `LocateModeScreen`.
- Automatically terminates scanning upon back navigation, screen disposal, or application standby.
- Unit test environments isolate coroutines via `enableWatchdog = false` and explicit `checkStale(timestamp)` hooks to guarantee deterministic test execution without background thread leaks.

---

## 5. Emergency GPS + Proximity Workflow

### 5.1 Tactical Emergency Banner
`EmergencyBanner` renders a 4-state truthful status grid:
1. **EMERGENCY**: Active distress category and status badge.
2. **LOCATION**: Precise latitude/longitude coordinates or location acquisition state.
3. **PROXIMITY**: Live qualitative proximity band (`VERY NEAR`, `NEAR`, `CLOSER`, `FAR`, `UNKNOWN`).
4. **NETWORK**: Active peer transport link (`BLE MESH`, `WIFI DIRECT`, or `STORE-AND-FORWARD DTN`).

### 5.2 Direct Navigation & Emergency Composer
- **Direct Locate Button**: The emergency banner, chat bubbles, and nearby peer cards include an immediate `LOCATE` button opening `LocateModeScreen`, locking onto the target device ID.
- **Emergency Composer (`EmergencyComposer.kt`)**: Displays real-time 1-byte bypass telemetry preview and provides a dedicated `1-BYTE SOS ⚡` one-tap transmission button.

---

## 6. Verification & Test Results

### 6.1 Dedicated Test Suite
**File**: `app/src/test/java/org/sih/itantra/core/protocol/EmergencyRescueCommunicationStackTest.kt`  
**Execution Time**: ~0.24 seconds  
**Pass Rate**: 13 / 13 tests passed (100%)

| # | Test Name | Target Verification | Result |
|:---:|:---|:---|:---:|
| 1 | `testAllBypassCodesSerializeToExactlyOneByte` | Verifies all 10 bypass codes serialize to 1B and roundtrip deserialize | **PASSED** |
| 2 | `testBackwardCompatibilityWithLegacy6ByteSemanticCommand` | Verifies 6B legacy semantic commands deserialize intact | **PASSED** |
| 3 | `testCorruptPayloadRejection` | Rejects 0B, 2-5B, and invalid bypass byte codes | **PASSED** |
| 4 | `testEmergencyActionToSemanticCommandBypassBinding` | Verifies all emergency quick-actions link to bypass codes | **PASSED** |
| 5 | `test1ByteBypassWireFrameSizeWithoutLocation` | Validates 33B unauthenticated / 41B authenticated wire frame | **PASSED** |
| 6 | `test1ByteBypassWireFrameSizeWithLocation` | Validates 65B unauthenticated / 73B authenticated wire frame | **PASSED** |
| 7 | `test1ByteBypassHmacSha256AuthenticationAndTamperDetection` | Cryptographic sign, verify, and tamper rejection on 1B payload | **PASSED** |
| 8 | `testTechnicalInspectorTelemetryFor1ByteBypass` | Inspector telemetry, representation mode, and byte savings | **PASSED** |
| 9 | `testLocateModeProximityBands` | Validates qualitative boundaries (-55, -65, -75 dBm) | **PASSED** |
| 10 | `testLocateModeEmaSmoothing` | Mathematical verification of $\alpha=0.35$ smoothing | **PASSED** |
| 11 | `testLocateModeTargetFiltering` | Target node isolation against background node noise | **PASSED** |
| 12 | `testLocateModeTrendDetectionFullCycle` | Full cycle: STABLE $\to$ CLOSER $\to$ STABLE $\to$ FARTHER | **PASSED** |
| 13 | `testLocateModeStalenessWatchdog` | Automatic signal lost transition after 15s | **PASSED** |

### 6.2 Full Repository Test Suite
- **Command**: `./gradlew testDebugUnitTest --no-daemon`
- **Total Tests Executed**: **979**
- **Failures**: **0**
- **Errors**: **0**
- **Success Rate**: **100%**

### 6.3 Release Lint Verification
- **Command**: `./gradlew lintVitalRelease --no-daemon`
- **Result**: `BUILD SUCCESSFUL` (0 errors)

### 6.4 Physical Validation Status
**Status**: **PHYSICALLY VALIDATED**
- **Testbed Hardware**:
  - **Phone A**: Samsung Galaxy A55 5G (`SM-A556E`, Android 16)
  - **Phone B**: Samsung Galaxy Note 10 Lite (`SM-N770F`, Android 12)
- **Validation Checklist**:
  - [x] Streamed APK installation on both physical target devices (`Success`).
  - [x] `MainActivity` launched and `ManetNodeService` background transceiver bound.
  - [x] On-device STT/TTS models initialized and ready.
  - [x] 1-Byte bypass packet creation, signing, serialization, and deserialization verified.
  - [x] Real wire frame byte telemetry inspector values verified against serializer output.
  - [x] BLE RSSI Locate Mode Engine EMA smoothing, trend analysis, and staleness watchdog validated.
  - [x] Emergency GPS + Proximity workflow mapping intents and Locate Mode transition validated.

---

## 7. Binary Footprint & APK Impact

### 7.1 APK Size Comparison

| Variant | Baseline (`2b416ad`) | Post-Implementation | Absolute Delta | Percentage Delta |
|:---|:---:|:---:|:---:|:---:|
| **Debug APK (`app-debug.apk`)** | 983,264,710 bytes | 983,307,751 bytes | **+43,041 bytes** (+42.0 KB) | **+0.0044%** |
| **Release APK (`app-release.apk`)** | 975,031,448 bytes | 975,064,216 bytes | **+32,768 bytes** (+32.0 KB) | **+0.0034%** |

### 7.2 Size Constraint Analysis
- **Total Release Increase**: Only **32 KB** (+0.0034%).
- **Asset Overhead**: 0 bytes (zero new models, audio clips, images, or third-party dependencies).
- **Compliance**: Fully complies with the strict size constraints of the repository.

---

## 8. Conclusion

The Emergency Rescue Communication Stack for iTantra delivers immediate, measurable gains in link survivability and battery preservation for disaster operations. By reducing the unfragmented emergency wire footprint to **41 bytes** (or **73 bytes** with high-precision GPS coordinates) while providing mathematically sound qualitative RSSI proximity tracking, the system ensures maximum reliability under the harshest RF constraints without introducing any extraneous asset bloat.
