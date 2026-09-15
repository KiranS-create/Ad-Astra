<div align="center">

# AD ASTRA

### iTantra — Offline Multilingual Voice-to-Packet MANET Transceiver

**Smart India Hackathon 2026** • **Problem Statement:** `SIH26173` • **Team:** Ad Astra

[![Unit Tests](https://img.shields.io/badge/Unit%20Tests-678%2B%20Passing-brightgreen.svg)]()
[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20(API%2029%2B)-blue.svg)]()
[![Mesh Networking](https://img.shields.io/badge/Mesh-Wi--Fi%20%7C%20Bluetooth%20SPP-blueviolet.svg)]()
[![Air-Gapped](https://img.shields.io/badge/Zero%20Cloud-100%25%20Air--Gapped-success.svg)]()
[![Wire Footprint](https://img.shields.io/badge/Wire%20Payload-38--164%20Bytes-informational.svg)]()
[![Offline STT](https://img.shields.io/badge/STT-Whisper--Tiny%20INT8-orange.svg)]()
[![Offline TTS](https://img.shields.io/badge/TTS-Piper%20%7C%20Mimic3%20%7C%20MMS-purple.svg)]()
[![Licensing](https://img.shields.io/badge/Code-MIT%20%2F%20Open%20Models-lightgrey.svg)](THIRD_PARTY_LICENSES.md)

<br/>

<img src="docs/assets/screenshots/dark/01-main-transceiver.png" width="340" alt="iTantra Main Transceiver — Charcoal (Dark) Theme" />

<br/>

**[Demo Video](docs/assets/demo/ad-astra-sih-2026-demo.mp4)** • **[Architecture](docs/ARCHITECTURE.md)** • **[Protocol Spec](docs/PROTOCOL.md)** • **[Testing Guide](docs/TESTING.md)** • **[Model Setup](docs/MODELS.md)** • **[Feature Reports](docs/feature-reports/)** • **[Third-Party Licenses](THIRD_PARTY_LICENSES.md)** • **[Latest Release](https://github.com/KiranS-create/Ad-Astra/releases/tag/v1.0.0-sih26173)**

</div>

---

## Demo Video

> **[Watch the Demonstration Video (MP4)](docs/assets/demo/ad-astra-sih-2026-demo.mp4)**

A live physical two-phone demonstration of the Ad Astra / iTantra offline transceiver:
- **Dual-Device Communication:** Real-time push-to-talk (PTT) voice exchange between physical Android devices.
- **Speech-First Neural Workflow:** On-device quantized Whisper INT8 speech-to-text and local acoustic voice reconstruction with zero cloud dependency.
- **Compact Binary Radio Framing:** Adaptive packet transmission (`COMPACT P0 47B–77B`) with sub-second airtime latency.
- **Emergency Distress Protocol:** High-priority distress broadcast with attached GPS coordinates and automated audible alert.
- **Diagnostics & Radio Telemetry:** Real-time channel activity, authentication markers, and packet inspect telemetry.

---

## 1. Overview

In disaster zones, remote search-and-rescue operations, and tactical field deployments, cellular networks and cloud connectivity are often damaged or completely nonexistent. Standard walkie-talkies and digital voice streams require high-bandwidth radio channels (~32,000 bytes/sec for raw PCM audio), which suffer from severe packet loss and channel congestion over low-power ad-hoc wireless links.

**iTantra** resolves this bottleneck by implementing an **offline-first neural transceiver architecture**:
1. **On-Device Speech Recognition:** Spoken audio is captured locally and converted to text using an on-device quantized INT8 Whisper model.
2. **Compact Binary Radio Framing:** Transcripts are encoded into compact binary radio packets (38 to 164 bytes) with a fixed 28-byte canonical header, CRC-32 integrity checks, and optional HMAC-SHA256 authentication.
3. **Off-Grid Mesh Routing (MANET & DTN):** Packets travel peer-to-peer over Wi-Fi multicast and Bluetooth SPP links using autonomous multi-hop store-and-forward routing.
4. **Local Neural Voice Reconstruction:** Receiving nodes reconstruct natural audible speech using local neural VITS acoustic models (Piper, Mimic3, Meta MMS) without requiring internet or cloud servers.

---

## 2. Core Architecture

```
  [Speaker's Voice]
         │
         ▼
 ┌───────────────────────────────────────────────────────────┐
 │ 1. Real-Time VAD & Frame Buffering (16 kHz 16-bit PCM)    │
 └─────────────────────────┬─────────────────────────────────┘
                           │
                           ▼
 ┌───────────────────────────────────────────────────────────┐
 │ 2. Streaming Pass 1 STT (Sherpa-ONNX Whisper INT8)        │
 │    + Silence Window Refinement (>= 250ms pauses)          │
 │    + Zero-Wait Release Splicing (< 10ms at PTT release)   │
 └─────────────────────────┬─────────────────────────────────┘
                           │
                           ▼
 ┌───────────────────────────────────────────────────────────┐
 │ 3. Adaptive Representation Engine                         │
 │    [FULL: 90-170B] [COMPACT: 70-110B] [SEMANTIC: 38-48B]  │
 └─────────────────────────┬─────────────────────────────────┘
                           │
                           ▼
 ┌───────────────────────────────────────────────────────────┐
 │ 4. Canonical Radio Framing & Transport                    │
 │    Fixed 28B Header + CRC-32 + Optional HMAC-SHA256       │
 │    Wi-Fi Multicast (Port 42888) & Bluetooth RFCOMM / SPP  │
 └─────────────────────────┬─────────────────────────────────┘
                           │
                           ▼
 ┌───────────────────────────────────────────────────────────┐
 │ 5. Multi-Hop MANET Relay & Local DTN Message Store (Room) │
 └─────────────────────────┬─────────────────────────────────┘
                           │
                           ▼
 ┌───────────────────────────────────────────────────────────┐
 │ 6. Local Neural Speech Synthesis (Piper / Mimic3 / MMS)   │
 └─────────────────────────┬─────────────────────────────────┘
                           │
                           ▼
  [Audible Voice Output to Recipient]
```

*For complete architectural specifications, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).*

---

## 3. Physical Device Interface

The application interface is built with **Jetpack Compose**, designed for rapid high-stress tactical operations:

| 1. Main Transceiver HUD | 2. Tactical Chat & Voice Playback | 3. Technical Packet Inspector |
|:---:|:---:|:---:|
| <img src="docs/assets/screenshots/dark/01-main-transceiver.png" width="240" alt="PTT Walkie Talkie — Charcoal Dark" /> | <img src="docs/assets/screenshots/dark/07-tactical-chat.png" width="240" alt="Tactical Chat — Charcoal Dark" /> | <img src="docs/assets/screenshots/dark/08-message-inspector.png" width="240" alt="Packet Inspector — Charcoal Dark" /> |
| Push-to-Talk HUD with real-time audio telemetry, Indic language selector & bottom navigation | Emergency priority cards, playback controls & delivery markers | Byte-level radio header analysis, hex dump & CRC/HMAC state |

| 4. Network Health & Telemetry | 5. Tactical Contacts Directory | 6. Radio & Relay Settings |
|:---:|:---:|:---:|
| <img src="docs/assets/screenshots/light/05-communication-health.png" width="240" alt="Health Diagnostics — Sand Light" /> | <img src="docs/assets/screenshots/dark/06-peer-discovery.png" width="240" alt="Tactical Contacts — Charcoal Dark" /> | <img src="docs/assets/screenshots/light/04-settings.png" width="240" alt="Radio Settings — Sand Light" /> |
| Transport telemetry, packet loss, bandwidth & route graphs | Peer directory with node callsigns, mesh contact management | Autonomous mesh relay toggle, 10 Indic languages & HMAC key |

---

## 4. Key Capabilities & Technical Highlights

### 4.1 Two-Pass Speech Pipeline & Zero-Wait Splicing
- **Continuous Pass 1:** Decodes streaming audio frames as the operator speaks.
- **Selective Pass 2 Refinement:** When natural speech pauses ($\ge 250\text{ ms}$) occur, high-value tokens (emergency codes, numbers, coordinates) are evaluated and refined in the background.
- **Zero-Wait Finalization:** When the Push-to-Talk (PTT) button is released, in-flight background tasks cancel immediately and completed refinements splice into the final transcript, handing the packet to the transmission layer in **$< 10\text{ ms}$** (measured local CPU release splice and dispatch latency).

### 4.2 Adaptive Representation Wire Footprints
To adapt to varying radio channel qualities, iTantra adjusts message serialization dynamically:

| Mode | Wire Footprint | Savings vs Raw Audio | Primary Use Case |
|---|:---:|:---:|---|
| `FULL_VOICE_TEXT` | 90–170 Bytes | $> 99.5\%$ | Standard conversational speech |
| `COMPACT_VOICE` | 70–110 Bytes | $> 99.7\%$ | Bandwidth-limited links |
| `SEMANTIC_BASE` | 40–48 Bytes | $> 99.8\%$ | Structured tactical commands (8-byte payload) |
| `CONTEXT_DELTA` | 38–46 Bytes | $> 99.85\%$ | Incremental state updates (6–7 byte payload) |

### 4.3 10-Language Multilingual Support
All models execute fully on-device without external cloud connectivity:

| Language | Code | Speech-to-Text (STT) | Text-to-Speech (TTS) Engine |
|---|:---:|---|---|
| **English** | `en` | Whisper-Tiny INT8 | Piper VITS (`en_US-lessac-medium`) |
| **Hindi** | `hi` | Whisper-Tiny INT8 | Piper VITS (`hi_IN-hindi-medium`) |
| **Marathi** | `mr` | Whisper-Tiny INT8 | Piper VITS (`mr_IN-marathi-medium`) |
| **Malayalam** | `ml` | Whisper-Tiny INT8 | Piper VITS (`ml_IN-malayalam-medium`) |
| **Telugu** | `te` | Whisper-Tiny INT8 | Piper VITS (`te_IN-telugu-medium`) |
| **Bengali** | `bn` | Whisper-Tiny INT8 | Piper VITS (`bn_IN-bengali-medium`) |
| **Gujarati** | `gu` | Whisper-Tiny INT8 | Mimic3 VITS (`gu_IN-cpc_female-low`) |
| **Kannada** | `kn` | Whisper-Tiny INT8 | Meta MMS VITS / System TTS Fallback |
| **Tamil** | `ta` | Whisper-Tiny INT8 | Meta MMS VITS / System TTS Fallback |
| **Odia** | `or` | Whisper-Tiny INT8 | Meta MMS VITS / System TTS Fallback |

---

## 5. Evaluation & Build Guide

### Fastest Evaluation Path (Pre-Built APK)
For hackathon evaluators and jury members wishing to run the application immediately without compiling from source:
1. **Download APK:** Download the pre-built [`app-debug.apk`](https://github.com/KiranS-create/Ad-Astra/releases/tag/v1.0.0-sih26173) from Release `v1.0.0-sih26173` (contains all bundled on-device model weights).
2. **Install on Two Devices:** Install the APK on two physical Android handsets (`adb install -r app-debug.apk` or transfer via USB).
3. **Grant Permissions:** Grant Microphone and Nearby Devices / Location permissions on first launch.
4. **Run Two-Phone Off-Grid Demo:** Connect both devices to the same Wi-Fi network/hotspot (or pair over Bluetooth SPP) and hold Push-to-Talk to transmit offline voice packets.

---

### Building from Source (Optional)

#### Prerequisites
- **Android Studio Ladybug (2024.2+)** or IntelliJ IDEA with Android plugin
- **JDK 17** (configured via `JAVA_HOME`)
- **Android SDK API 34** with Build Tools `34.0.0`
- Physical Android device running **Android 10+ (API 29+)** with ARM64 architecture

#### 1. Clone the Repository
```bash
git clone https://github.com/KiranS-create/Ad-Astra.git
cd Ad-Astra
```

#### 2. Run Automated Unit Tests
```bash
# Windows
.\gradlew.bat testDebugUnitTest

# Linux / macOS
./gradlew testDebugUnitTest
```
*All 678+ unit tests will execute and verify protocol framing, speech splicing, representation encoding, and mesh routing.*

#### 3. Build & Install Debug APK
```bash
# Build the APK
.\gradlew.bat assembleDebug

# Install to connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 6. Physical Verification Setup

The project has been physically validated across two ARM64 Android smartphones in an off-grid environment:
- **Device A:** Samsung Galaxy Note 10 Lite (Android 13, Exynos 9810, 6 GB RAM)
- **Device B:** Samsung Galaxy A55 5G (Android 14, Exynos 1480, 8 GB RAM)

**Verified Behaviors:**
- [x] Push-to-Talk speech recognition to packet transmission queue in $< 10\text{ ms}$ after release (local CPU splicing and dispatch).
- [x] Wi-Fi UDP multicast packet delivery over ad-hoc local hotspots without internet access.
- [x] Bluetooth Classic SPP serial transmission between paired devices.
- [x] Multi-hop mesh relay forwarding with loop suppression and TTL decrement.
- [x] Offline neural voice synthesis on receiving handset across 9 Indian languages + English.


*For complete test procedures and logs, refer to [docs/TESTING.md](docs/TESTING.md).*

---

## 7. Project Documentation

```
Ad-Astra/
├── app/                        # Android application (Jetpack Compose, Neural STT/TTS, Mesh & DTN)
├── docs/
│   ├── ARCHITECTURE.md         # 6-layer neural transceiver stack & design trade-offs
│   ├── IMPLEMENTATION_STATUS.md# Phase milestones & technical implementation summary
│   ├── MODELS.md               # On-device model quantization, asset management & licenses
│   ├── PROTOCOL.md             # 28-byte canonical header, frame specs & wire layouts
│   ├── TESTING.md              # Testbed methodology, unit test suite & device validation
│   ├── assets/
│   │   ├── demo/               # SIH 2026 two-device demonstration video
│   │   ├── screenshots/        # Canonical light & dark UI screenshot archives
│   │   └── social-preview.png  # Social preview card
│   └── feature-reports/        # Technical engineering reports & benchmarks
├── README.md                   # Primary project overview & evaluation guide
├── CITATION.cff                # Academic & technical citation metadata
├── SECURITY.md                 # Vulnerability reporting & security boundaries
└── THIRD_PARTY_LICENSES.md     # Third-party notices & open-source model licenses
```

- **[System Architecture](docs/ARCHITECTURE.md):** Deep dive into the 6-layer neural transceiver stack and architectural trade-offs.
- **[Protocol Specification](docs/PROTOCOL.md):** 28-byte canonical header layout, message types, CRC32, HMAC, and DTN state machines.
- **[Testing Methodology](docs/TESTING.md):** Unit test suite organization, physical device testbed, and test scenarios.
- **[Model Setup & Management](docs/MODELS.md):** Model quantization details, storage layout, and MMS fallback guidelines.
- **[Implementation Status](docs/IMPLEMENTATION_STATUS.md):** Complete development phase breakdown and verification summary.
- **[Feature & Engineering Reports](docs/feature-reports/):** 15 technical reports covering protocol benchmarks, adaptive representation, and tactical mesh subsystems.
- **[Third-Party Licenses](THIRD_PARTY_LICENSES.md):** Open-source licensing notices for runtime engines, libraries, and model weights.
- **[Security Policy](SECURITY.md):** Threat model, HMAC authentication boundaries, and vulnerability disclosure.

---

## 8. Team & Hackathon Information

- **Competition:** Smart India Hackathon 2026
- **Problem Statement ID:** `SIH26173`
- **Team Name:** Ad Astra
- **Project Name:** iTantra

---

## 9. License & Citations

The software and protocol implementations in this repository are licensed under the **MIT License** (see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for third-party library and neural model licenses).

If you use or reference this work, please cite it using [CITATION.cff](CITATION.cff).
