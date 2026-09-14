# iTantra — Feature & Engineering Reports Index

**Smart India Hackathon 2026** • **Problem Statement:** `SIH26173` • **Team:** Ad Astra

This directory contains technical engineering reports, architectural decision records, and verification audits produced during the development of the **iTantra** offline multilingual transceiver.

---

## 1. Tactical UI & Mesh Transceiver Features

| Report | Subsystem | Focus Area |
|---|---|---|
| [FEATURE6_RADIO_MESSAGE_STATES_REPORT.md](FEATURE6_RADIO_MESSAGE_STATES_REPORT.md) | Radio State Machine | Delivery confirmation, retry states, and delivery status indicators |
| [FEATURE7_MESSAGE_INSPECTOR_REPORT.md](FEATURE7_MESSAGE_INSPECTOR_REPORT.md) | Packet Diagnostics | Inline byte-level inspector, hex dump analysis, CRC-32 and HMAC state |
| [FEATURE8_MESH_TOPOLOGY_REPORT.md](FEATURE8_MESH_TOPOLOGY_REPORT.md) | Mesh Routing | Peer discovery graphs, hop distance matrix, link quality assessment |
| [FEATURE9_MESSAGE_JOURNEY_REPORT.md](FEATURE9_MESSAGE_JOURNEY_REPORT.md) | Packet Tracing | Multi-hop journey timeline, node relay audit, end-to-end latency |
| [FEATURE10_QR_PAIRING_REPORT.md](FEATURE10_QR_PAIRING_REPORT.md) | Security & Pairing | Air-gapped out-of-band node pairing and cryptographic identity exchange |
| [FEATURE11_MULTILINGUAL_PLAYBACK_REPORT.md](FEATURE11_MULTILINGUAL_PLAYBACK_REPORT.md) | Neural Speech Synthesis | Multilingual speech synthesis queue, speed scaling, audio focus handling |
| [FEATURE12_EMERGENCY_CHAT_REPORT.md](FEATURE12_EMERGENCY_CHAT_REPORT.md) | Tactical Messaging | Distress priority channel preemption, siren tone synthesis, emergency cards |
| [FEATURE13_COMMUNICATION_HEALTH_REPORT.md](FEATURE13_COMMUNICATION_HEALTH_REPORT.md) | Link Diagnostics | Real-time link telemetry, packet error rate, RSSI tracking, route quality |
| [FEATURE14_ADAPTIVE_NETWORK_UI_REPORT.md](FEATURE14_ADAPTIVE_NETWORK_UI_REPORT.md) | Adaptive Presentation | Link-aware UI indicators, fallback warnings, transmission bandwidth throttling |
| [FEATURE15_MESSAGE_RETENTION_REPORT.md](FEATURE15_MESSAGE_RETENTION_REPORT.md) | DTN Storage | Delay-Tolerant Networking store-and-forward, SQLite/Room retention policies |

---

## 2. Two-Pass Speech Pipeline & Neural Refinements

| Report | Subsystem | Focus Area |
|---|---|---|
| [FEATURE16A_TWO_PASS_SPEECH_BENCHMARK_REPORT.md](FEATURE16A_TWO_PASS_SPEECH_BENCHMARK_REPORT.md) | Speech Pipeline | Empirical benchmark comparing streaming INT8 STT with zero-wait splicing |
| [FEATURE16B_ADAPTIVE_VBR_REPORT.md](FEATURE16B_ADAPTIVE_VBR_REPORT.md) | Protocol Encoding | Variable bitrate packet encoding scaling between 38B semantic and 164B full text |
| [FEATURE17_TARGETED_REFINEMENT_REPORT.md](FEATURE17_TARGETED_REFINEMENT_REPORT.md) | STT Refinement | Natural pause detection ($\ge 250\text{ ms}$) and background keyword token refinement |
| [FEATURE18_SEMANTIC_BASE_ENHANCEMENT_REPORT.md](FEATURE18_SEMANTIC_BASE_ENHANCEMENT_REPORT.md) | Representation Engine | Fixed-byte tactical vocabulary encoding (38–48 bytes) for degraded links |
| [FEATURE19_SHARED_CONTEXT_CONFIDENCE_REPORT.md](FEATURE19_SHARED_CONTEXT_CONFIDENCE_REPORT.md) | Verification Layer | Context validation and confidence scoring across multi-hop relay chains |

---

## 3. Core Subsystem Implementation Reports

| Report | Subsystem | Focus Area |
|---|---|---|
| [CONTACTS_IMPLEMENTATION_REPORT.md](CONTACTS_IMPLEMENTATION_REPORT.md) | Tactical Directory | Node callsign resolution, peer address management, contact cards |
| [GLOBAL_SEARCH_IMPLEMENTATION_REPORT.md](GLOBAL_SEARCH_IMPLEMENTATION_REPORT.md) | Search Engine | In-memory full-text search across messages, node IDs, callsigns, and transcripts |
| [NEARBY_DEVICES_IMPLEMENTATION_REPORT.md](NEARBY_DEVICES_IMPLEMENTATION_REPORT.md) | Peer Discovery | BLE advertisement scanning, Wi-Fi LAN multicast listener, radar UI |
| [NAVIGATION_ACCESSIBILITY_REPORT.md](NAVIGATION_ACCESSIBILITY_REPORT.md) | Accessibility | Jetpack Compose content descriptions, minimum touch targets, dark/light contrast |
| [INTEGRATION_WAVE1_REPORT.md](INTEGRATION_WAVE1_REPORT.md) | Subsystem Integration | End-to-end validation connecting Audio → VAD → STT → Protocol → Mesh → TTS |

---

## 4. Architectural & Compliance Records

| Report | Focus Area |
|---|---|
| [ARCHITECTURAL_TRADEOFFS.md](ARCHITECTURAL_TRADEOFFS.md) | Design rationale comparing text packets vs raw audio, offline vs cloud ML, UDP vs Wi-Fi Direct |
| [VERIFICATION_STATUS.md](VERIFICATION_STATUS.md) | Authoritative verification audit of offline capabilities, ONNX runtimes, and 10-language models |
| [RISK_REGISTER.md](RISK_REGISTER.md) | Technical risk assessment, severity ratings, and implemented mitigation mechanisms |
| [SIH_DEMO_SCRIPT.md](SIH_DEMO_SCRIPT.md) | Step-by-step 3–5 minute live demonstration walkthrough for the SIH evaluation jury |
