# iTantra — Technical Engineering Reports

**Smart India Hackathon 2026** • **Problem Statement:** SIH26173 • **Team:** Ad Astra

This directory contains technical engineering reports, architectural benchmarks, and validation records for the core subsystems of the **iTantra** offline multilingual transceiver.

---

## 1. Adaptive Neural Speech Pipeline & Representation

| Report | Focus Area | Key Metrics / Artifacts |
|---|---|---|
| [FEATURE16A_TWO_PASS_SPEECH_BENCHMARK_REPORT.md](FEATURE16A_TWO_PASS_SPEECH_BENCHMARK_REPORT.md) | Streaming Pass 1 STT + Selective Pass 2 Refinement | Zero-wait release splicing ($< 10\text{ ms}$ local CPU latency), RTF 0.18–0.24 |
| [FEATURE16B_ADAPTIVE_VBR_REPORT.md](FEATURE16B_ADAPTIVE_VBR_REPORT.md) | Variable Bitrate (VBR) Representation Scaling | Wire footprint scaling between 38B (semantic) and 164B (full text) |
| [FEATURE17_TARGETED_REFINEMENT_REPORT.md](FEATURE17_TARGETED_REFINEMENT_REPORT.md) | Silence Window ($\ge 250\text{ ms}$) Keyword Refinement | Targeted background token refinement for emergency codes and coordinates |
| [FEATURE18_SEMANTIC_BASE_ENHANCEMENT_REPORT.md](FEATURE18_SEMANTIC_BASE_ENHANCEMENT_REPORT.md) | Semantic Base + Enhancement Tactical Codec | Fixed-byte tactical vocabulary encoding (38–48 B) for severe link degradation |
| [FEATURE19_SHARED_CONTEXT_CONFIDENCE_REPORT.md](FEATURE19_SHARED_CONTEXT_CONFIDENCE_REPORT.md) | Shared Context Confidence & Multi-Hop Verification | Situational fact verification and confidence scoring across multi-hop relay chains |

---

## 2. Tactical Radio Telemetry & Diagnostics

| Report | Focus Area | Key Metrics / Artifacts |
|---|---|---|
| [FEATURE6_RADIO_MESSAGE_STATES_REPORT.md](FEATURE6_RADIO_MESSAGE_STATES_REPORT.md) | Radio Transmission Lifecycle State Machine | Deterministic state transitions (Pending, Transmitting, Acknowledged, Failed) |
| [FEATURE7_MESSAGE_INSPECTOR_REPORT.md](FEATURE7_MESSAGE_INSPECTOR_REPORT.md) | Technical Packet Inspector & Wire Dissector | Byte-level radio header analysis, hex dump inspection, CRC-32 and HMAC validation |
| [FEATURE13_COMMUNICATION_HEALTH_REPORT.md](FEATURE13_COMMUNICATION_HEALTH_REPORT.md) | Real-Time Transport Telemetry & Link Quality | Retransmission rate, round-trip latency, SNR, and route stability scoring |
| [FEATURE14_ADAPTIVE_NETWORK_UI_REPORT.md](FEATURE14_ADAPTIVE_NETWORK_UI_REPORT.md) | Link-Aware Dynamic UI Presentation | Adaptive UI indicators, channel congestion warnings, bandwidth throttling |

---

## 3. Mesh Networking & DTN Persistence

| Report | Focus Area | Key Metrics / Artifacts |
|---|---|---|
| [FEATURE8_MESH_TOPOLOGY_REPORT.md](FEATURE8_MESH_TOPOLOGY_REPORT.md) | Dynamic Peer Discovery & Hop Routing | Ad-hoc mesh routing tables, node hop distance matrix, neighbor tables |
| [FEATURE9_MESSAGE_JOURNEY_REPORT.md](FEATURE9_MESSAGE_JOURNEY_REPORT.md) | Packet Journey Hop Tracking & Route Timeline | Multi-hop audit trail, node relay timestamps, end-to-end traversal latency |
| [FEATURE15_MESSAGE_RETENTION_REPORT.md](FEATURE15_MESSAGE_RETENTION_REPORT.md) | Delay-Tolerant Networking (DTN) Storage | Store-and-forward SQLite/Room cache, 10-minute local message retention policy |

---

## 4. Security, Distress Preemption & Voice Synthesis

| Report | Focus Area | Key Metrics / Artifacts |
|---|---|---|
| [FEATURE10_QR_PAIRING_REPORT.md](FEATURE10_QR_PAIRING_REPORT.md) | Air-Gapped Out-of-Band Cryptographic Pairing | QR node identity exchange without secret leakage, replay attack protection |
| [FEATURE11_MULTILINGUAL_PLAYBACK_REPORT.md](FEATURE11_MULTILINGUAL_PLAYBACK_REPORT.md) | Multilingual Neural Voice Playback Engine | Dynamic audio focus management, playback speed scaling, language routing |
| [FEATURE12_EMERGENCY_CHAT_REPORT.md](FEATURE12_EMERGENCY_CHAT_REPORT.md) | High-Priority Emergency Distress Channel | Distress channel preemption, 800Hz/1200Hz acoustic siren synthesis, emergency cards |
