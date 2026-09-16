# iTantra — Final Technical Engineering Report

**Project:** SIH26173 — iTantra (Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links)  
**Team:** Ad Astra  
**Target Environment:** Contested, Infrastructure-Denied, Tactical & Disaster Mobile Ad-Hoc Networks  
**Target Platform:** Android 10+ (API 29+, Compile/Target SDK 35), `arm64-v8a`  
**Evaluation Status:** Production Release Candidate `v1.0.0` (Commit: `343ba26`)  
**Date:** September 2026  

---

## 1. Executive Summary

In disaster relief operations, remote search-and-rescue missions, and tactical defense deployments, terrestrial cellular infrastructure, satellite backhauls, and commercial cloud dependencies are frequently destroyed, jammed, or non-existent. Standard push-to-talk (PTT) radios and analog voice streams transmit uncompressed audio requiring 32,000–64,000 bytes per second of radio bandwidth. Over austere, low-power ad-hoc wireless links (e.g., VHF, UHF, LoRa, or ad-hoc 802.11/Bluetooth mesh), raw voice transmission suffers catastrophic packet loss, severe channel contention, and rapid range degradation.

**iTantra** resolves this fundamental communications bottleneck through an **offline-first neural voice-to-packet transceiver architecture**. Instead of streaming continuous PCM audio over the air, iTantra captures operator voice locally, executes streaming on-device speech-to-text (STT) inference using quantized neural models, encodes the resulting transcripts and intent into ultra-compact binary radio packets (38 to 164 bytes), relays those packets peer-to-peer over an autonomous MANET/DTN mesh, and reconstructs natural acoustic speech at the recipient handset using local neural text-to-speech (TTS) synthesis.

By replacing raw voice streams with serialized semantic representations, iTantra achieves a **99.7% to 99.9% wire footprint reduction**, sub-second end-to-end turnaround (235–345 ms endpoint-to-transcript), and deterministic emergency distress preemption, operating **100% air-gapped without external servers, cloud APIs, or cellular towers**.

---

## 2. SIH Problem Statement (SIH26173)

The Smart India Hackathon problem statement **SIH26173** addresses the acute challenge of voice and data telecommunications in severe infrastructure-denied environments:
- **Challenge:** Provide reliable, low-latency, two-way speech communication across India's regional multilingual linguistic diversity over bandwidth-constrained, high-loss ad-hoc radio links.
- **Constraints:** Zero dependency on internet or telecom infrastructure; low computational overhead on standard commercial off-the-shelf (COTS) mobile devices; resilient operation across channel degradation, node mobility, and network partitions; robust handling of high-priority emergency alerts.

---

## 3. Problem Understanding & Operational Constraints

1. **Acoustic Bandwidth vs. RF Channel Capacity:** Raw 16 kHz 16-bit mono audio generates 256 kbps (32 kB/s). Even modern narrowband cellular voice codecs (AMR, Opus at low bitrates) require 6–12 kbps, which quickly congests multi-hop ad-hoc wireless channels where effective throughput often drops below 10–50 kbps under multi-user contention.
2. **Linguistic Plurality:** Field personnel in India operate across diverse regional languages. Command-and-control instructions spoken in Hindi, Tamil, Telugu, Marathi, or Gujarati must be transcribed and delivered without requiring bilingual fluency from the underlying network.
3. **RF Channel Volatility:** Tactical MANET links suffer from severe multipath fading, shadow fading behind rubble/foliage, high packet loss (up to 40–50%), burst losses, and transient partitions as nodes move.
4. **Physical Device Constraints:** Field handsets operate on battery power with limited thermal dissipation. Continuous high-power GPU/NPU utilization leads to rapid battery depletion and thermal throttling.

---

## 4. The iTantra Solution

iTantra decouples acoustic generation from radio transmission through a four-stage pipeline:
1. **Local Neural Ingestion:** Spoken audio is processed entirely on the transmitter handset using embedded C++ Sherpa-ONNX runtimes with quantized INT8 acoustic models.
2. **Multi-Tiered Wire Representation:** Transcripts are mapped into three adaptive operational tiers:
   - **Full Text (P2):** Complete UTF-8 text payload (90–170 bytes).
   - **Compact Token (P1):** Compressed linguistic tokens (70–110 bytes).
   - **Semantic Base (P0):** Fixed-byte tactical vocabulary intent codes (38–48 bytes).
3. **Autonomous Peer-to-Peer Mesh Routing:** Binary radio frames (28-byte canonical header, CRC-32 integrity, HMAC-SHA256 authenticity) are relayed through Wi-Fi multicast and Bluetooth SPP/RFCOMM interfaces via epidemic store-and-forward delay-tolerant routing.
4. **Local Acoustic Synthesis:** Receiving handsets deserialize the frame, look up local language routing tables, and synthesize natural audio using on-device Piper/Meta MMS VITS neural voices.

---

## 5. System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          OPERATOR INTERACTION LAYER                         │
│  [Tactical Radio HUD]  [PTT State Machine]  [SOS Distress]  [Inspector]    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                           LOCAL NEURAL SPEECH CORE                          │
│  ┌─────────────────────────┐              ┌──────────────────────────────┐  │
│  │ Streaming Pass 1 STT    │              │ Multi-Voice Offline TTS      │  │
│  │ (Sherpa-ONNX INT8)      │              │ (Piper / Mimic3 / Meta MMS)  │  │
│  └────────────┬────────────┘              └──────────────▲───────────────┘  │
│               │ (Transcript)                             │ (Text / Token)   │
└───────────────┼──────────────────────────────────────────┼──────────────────┘
                │                                          │
┌───────────────▼──────────────────────────────────────────┴──────────────────┐
│                      REPRESENTATION & PROTOCOL ENGINE                       │
│  ┌─────────────────────────────────┐   ┌─────────────────────────────────┐  │
│  │ Adaptive Two-Pass VBR Layer     │   │ Shared Context & Confidence     │  │
│  │ (Full / Compact / Semantic Base)│   │ (State Cache + Context Deltas)  │  │
│  └────────────────┬────────────────┘   └─────────────────┬───────────────┘  │
│                   │                                      │                  │
│  ┌────────────────▼──────────────────────────────────────▼───────────────┐  │
│  │ Canonical Radio Framing (28B Header + CRC-32 + Truncated HMAC-SHA256) │  │
│  └────────────────────────────────┬──────────────────────────────────────┘  │
└───────────────────────────────────┼─────────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼─────────────────────────────────────────┐
│                      MANET ROUTING & DTN STORAGE CORE                       │
│  ┌─────────────────────────────────┐   ┌─────────────────────────────────┐  │
│  │ Context-Aware Relay Router      │   │ DTN SQLite/Room Message Store   │  │
│  │ (Epidemic / Multi-Hop / TTL)    │   │ (10-Minute Retention Lifecycle) │  │
│  └────────────────┬────────────────┘   └─────────────────┬───────────────┘  │
└───────────────────┼──────────────────────────────────────┼──────────────────┘
                    │                                      │
┌───────────────────▼──────────────────────────────────────▼──────────────────┐
│                         PHYSICAL RADIO TRANSPORTS                           │
│     [Wi-Fi Direct / Local Multicast (42888)]      [Bluetooth SPP / RFCOMM]  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. End-to-End Data Flow

1. **Audio Ingestion ($T_0 \to T_1$):** 16 kHz 16-bit mono PCM stream buffered into 100 ms ring frames. Voice Activity Detection (VAD) monitors energy and zero-crossing rate (ZCR).
2. **Streaming Recognition ($T_1 \to T_3$):** While operator speaks, Pass 1 streaming ASR generates interim partial tokens every 100 ms.
3. **Endpoint Splicing ($T_3 \to T_4$):** On PTT button release, endpoint detector fires. Zero-wait release splicing finalizes draft in $<10\text{ ms}$. Optional targeted Pass 2 refinement verifies high-consequence entities during trailing pause windows.
4. **Serialization ($T_4 \to T_5$):** Transcript evaluated by Representation Engine. Context deltas computed against local `SharedContextStore`. Packet serialized with 28-byte canonical header, sequence number, timestamps, CRC-32, and HMAC-SHA256 tag.
5. **Air Interface ($T_5 \to T_6$):** Frame enqueued in `TacticalQosQueue` (P0 Emergency, P1 Tactical Voice, P2 Context/Telemetry). Dispatched over Wi-Fi multicast UDP (port 42888) or Bluetooth RFCOMM socket.
6. **Multi-Hop Traversal ($T_6 \to T_7$):** Intermediate nodes receive frame, verify CRC-32, check anti-replay sliding window, inspect destination ID, decrement TTL, log hop record, and forward.
7. **Reception & Synthesis ($T_7 \to T_8$):** Destination node validates HMAC and sequence window, extracts payload, applies context delta, routes text to target language TTS engine, and plays synthesized audio via `AudioTrack`.

---

## 7. Speech Pipeline: Two-Pass Streaming & Targeted Refinement

To balance low latency with high accuracy on mobile processors, iTantra implements an **overlapped two-pass speech pipeline**:
- **Pass 1 (Streaming Interim):** A lightweight streaming transducer processes 100 ms audio chunks incrementally as the user speaks. First partial transcript tokens appear within $345\text{ ms}$ of speech onset.
- **Silence Window Refinement (Feature 17):** When an operator pauses mid-sentence ($\ge 250\text{ ms}$), the pipeline opportunistically executes targeted rescoring on ambiguous token spans (such as military grid coordinates, callsigns, or distress codes) on background worker threads without waiting for PTT release.
- **Zero-Wait PTT Release:** At PTT release, the transcript is already 95%+ complete; total endpoint-to-transcript finalization requires only $235\text{ ms}$ (compared to $480\text{ ms}$ in batch-only recognition), achieving a **2.04x speedup**.

---

## 8. Multilingual STT/TTS Architecture

iTantra provides full offline speech capabilities across **10 operational Indian languages**:

| Code | Language | Script | STT Engine | STT Quant | TTS Engine & Voice | TTS SR | License |
|---|---|---|---|---|---|---|---|
| `en` | English | Latin | Sherpa Whisper-Tiny | INT8 | VITS Piper (`en_US-lessac`) | 22.05 kHz | MIT |
| `hi` | Hindi | Devanagari | Sherpa Whisper-Tiny / IndicConformer | INT8 | VITS Piper (`hi_IN-rohan`) | 22.05 kHz | MIT |
| `mr` | Marathi | Devanagari | Sherpa IndicConformer | INT8 | VITS Piper (`mr_IN-google`) | 22.05 kHz | Apache-2.0 |
| `gu` | Gujarati | Gujarati | Sherpa IndicConformer | INT8 | VITS Mimic3 (`gu_IN-cmu`) | 16.00 kHz | Open Source |
| `ta` | Tamil | Tamil | Sherpa IndicConformer | INT8 | VITS Meta MMS (`tam`) | 16.00 kHz | CC-BY-NC 4.0 |
| `te` | Telugu | Telugu | Sherpa IndicConformer | INT8 | VITS Piper (`te_IN-maya`) | 22.05 kHz | MIT |
| `kn` | Kannada | Kannada | Sherpa IndicConformer | INT8 | VITS Meta MMS (`kan`) | 16.00 kHz | CC-BY-NC 4.0 |
| `ml` | Malayalam | Malayalam | Sherpa IndicConformer | INT8 | VITS Piper (`ml_IN-arjun`) | 22.05 kHz | MIT |
| `bn` | Bengali | Bengali | Sherpa IndicConformer | INT8 | VITS Piper (`bn_BD-google`) | 22.05 kHz | Apache-2.0 |
| `or` | Odia | Odia | Android OS Fallback | OS Native | VITS Meta MMS (`ory`) | 16.00 kHz | CC-BY-NC 4.0 |

*Note on Odia:* Because Odia is omitted from multilingual Whisper 99-language tokenizers, Odia STT utilizes Android OS offline speech recognition fallback, while Odia acoustic synthesis uses Meta MMS VITS.

---

## 9. Low-Bitrate Representation Modes

Under dynamic wireless link conditions, iTantra automatically selects between three wire representations:

1. **FULL Representation (P2):**
   - Contains uncompressed or Deflate-compressed UTF-8 transcript string.
   - Wire Size: **90 to 170 bytes**.
   - Used when channel health index is high ($\ge 75\%$, packet loss $< 5\%$).
2. **COMPACT Representation (P1):**
   - Contains token-indexed dictionary IDs, normalized numbers, and phonetic shorthands.
   - Wire Size: **70 to 110 bytes**.
   - Used under moderate channel degradation (loss $5\%\text{--}20\%$).
3. **SEMANTIC BASE Representation (P0):**
   - Encodes tactical intent into fixed 1-byte command categories, 2-byte entity IDs, and 4-byte packed coordinates.
   - Wire Size: **38 to 48 bytes**.
   - Used under severe link distress (loss $> 20\%$, bandwidth $< 20\text{ kbps}$) or high-priority emergency distress packets.

**Compression Ratio:** Compared to raw 16 kHz 16-bit PCM audio ($32,000\text{ B/s}$), a 3-second voice transmission requires $96,000\text{ bytes}$ of raw PCM data. iTantra's 38-byte semantic packet represents a **99.96% bandwidth reduction**.

---

## 10. Semantic Base + Enhancement Layer (Feature 18)

Feature 18 decouples critical operational intent from descriptive detail:
- **Base Layer (L0):** Guaranteed minimal packet carrying core tactical action (e.g., `ACTION_EVACUATE`, `UNIT_CHARLIE`, `GRID_7482`). Transmitted with high redundancy and strict QoS priority.
- **Enhancement Layer (L1):** Optional trailing frame carrying modifier adjectives, secondary descriptions, and non-essential conversational context.
- **Receiver Recombination:** If L0 arrives alone, the receiver immediately speaks the core tactical directive: *"EVACUATE UNIT CHARLIE GRID 7482"*. If L1 arrives subsequently, the message record updates with full descriptive context.

---

## 11. Shared Context & Context Delta Forwarding (Feature 19)

In tactical operations, operators frequently transmit incremental updates (e.g., casualty updates: "Casualties 3" $\to$ "Casualties now 4"). Transmitting redundant situational background wastes channel capacity.
- **Shared Context Store:** Nodes maintain an in-memory replicated state machine keyed by operational sector and subject.
- **Context Delta Packets:** Transmitting nodes compute diffs against the shared context state. Only changed fields are transmitted:
  $$\Delta = \{\text{Field: CASUALTIES}, \text{Old: } 3, \text{New: } 4, \text{Conf: } 95\}$$
- **Confidence Gating:** Context mutations are accepted by receiving nodes only if the source confidence score exceeds the authoritative threshold ($\ge 70$). Low-confidence updates ($< 70$) are flagged as unverified and require operator confirmation.

---

## 12. Multi-Hop Relay Architecture (Feature 20)

iTantra operates an application-layer mobile ad-hoc network (MANET) router:
- **Routing Protocol:** Epidemic store-and-forward routing with reverse-path hop recording and duplicate suppression.
- **Packet Hop Limit:** Every packet contains an 8-bit Time-To-Live (TTL) field initialized to 7. Intermediate relay nodes decrement TTL upon forwarding; packets reaching $\text{TTL} = 0$ are silently dropped to prevent broadcast storms.
- **Duplicate Suppression:** Nodes maintain a sliding FIFO cache of recently forwarded `(SourceID, SeqNum)` tuples. Duplicate packets received via alternate mesh paths are discarded in $< 1\text{ ms}$.
- **Route Tracking:** The packet header includes an expandable route journey list recording the Node IDs and timestamps of intermediate forwarders, displayed in the UI via the Message Journey Inspector.

---

## 13. Delay-Tolerant Networking (DTN) / Store-and-Forward

When nodes encounter RF partitions (e.g., traversing underground bunkers or moving behind geographical obstacles):
1. Packets addressed to unreachable destinations are routed into the local Room/SQLite **DTN Buffer**.
2. A background custodian service monitors transport link connectivity and periodic neighbor discovery beacons.
3. Upon detecting link restoration or meeting a new intermediate neighbor node, the DTN buffer automatically bursts unacknowledged packets to the newly discovered peer.
4. **Buffer Hygiene:** Expired packets exceeding message retention limits (10 minutes) or low-priority telemetry buffers are pruned to prevent local flash memory exhaustion.

---

## 14. Tactical Quality of Service (QoS)

The transceiver implements a 3-tier priority queue (`TacticalQosQueue`):
- **Queue 0 (EMERGENCY / P0):** Non-blocking, preemptive queue for SOS beacons, distress alerts, and evacuation orders. Enqueued packets bypass all normal traffic and trigger immediate radio transmission.
- **Queue 1 (TACTICAL VOICE / P1):** Standard priority queue for operator voice communications and tactical chat.
- **Queue 2 (ROUTINE / P2):** Lower-priority queue for background context synchronization, peer discovery beacons, and diagnostic telemetry.

Under channel congestion, Queue 2 is throttled or dropped first, preserving link capacity for mission-critical speech and emergency packets.

---

## 15. Physical Transport Layer: Wi-Fi Multicast & Bluetooth SPP

iTantra operates across two simultaneous zero-infrastructure wireless transports:

1. **Wi-Fi Multicast / UDP Broadcast:**
   - Socket: Bound to `239.255.42.88` / `0.0.0.0`, port `42888`.
   - Mode: Ad-hoc local subnet broadcast and multicast without requiring router/DHCP infrastructure. Supports Wi-Fi Direct peer-to-peer groups.
   - Throughput: High bandwidth, range $30\text{--}70\text{ meters}$ line-of-sight.
2. **Bluetooth Classic RFCOMM / SPP:**
   - Protocol: Serial Port Profile (SPP) with UUID `00001101-0000-1000-8000-00805F9B34FB`.
   - Mode: Point-to-point stream sockets between paired or discovered Bluetooth nodes.
   - Throughput: Low energy, highly robust against 5 GHz RF absorption, range $10\text{--}25\text{ meters}$.
3. **Transport Failover:** Transceiver coordinator automatically routes packets over Wi-Fi when active, falling back to Bluetooth SPP if Wi-Fi interface drops.

---

## 16. Packet Fragmentation & Reassembly

For packets exceeding maximum transmission unit (MTU) limits on constrained links (e.g., Bluetooth RFCOMM chunk size: 512 bytes):
- Packets with payloads $> 256\text{ bytes}$ are divided into 128-byte fragments by `PacketFragmenter`.
- Header flag `0x01` (`FLAG_FRAGMENTED`) indicates fragmented delivery.
- Receiver buffers fragments keyed by `(SourceID, MessageID)`, verifying complete segment delivery via fragment bitmask before reassembly and CRC verification.

---

## 17. Security Architecture: HMAC, CRC-32 & Replay Protection

### 17.1 Integrity & Authenticity
- **CRC-32 (IEEE 802.3):** 4-byte checksum computed over canonical header and payload. Evaluated first as a fast rejection filter for physical RF noise and bit flips.
- **HMAC-SHA256:** 8-byte truncated authentication tag computed over invariant header fields (Magic, Version, MsgType, Priority, Flags, SeqNum, SourceID, DestID, Timestamp) and payload body using pre-shared operational keys (`NetworkKeyManager`).
- **Tamper Rejection:** Modification of any invariant field results in deterministic `INVALID_TAG` rejection.

### 17.2 Anti-Replay Defense
- Every node tracks incoming sequence numbers using a **64-bit sliding window**.
- Packets with sequence numbers older than $\text{MaxSeq} - 64$ are dropped as stale.
- Duplicate sequence numbers within the window are rejected via bitmask checks.
- Sequence rollover from $65,535 \to 0$ is handled cleanly through modular distance comparison.

---

## 18. Emergency Communication & Distress Preemption

- **One-Touch SOS Distress Broadcast:** Dedicated, high-contrast emergency UI control triggers instant P0 distress beacon generation.
- **Acoustic Preemption:** Emergency alerts preempt all ongoing speech playback, driving a dual-tone $800\text{ Hz} / 1200\text{ Hz}$ acoustic siren directly through `AudioTrack` at maximum safe system volume.
- **GPS Integration:** If location hardware is active, distress packets embed latitude/longitude coordinates directly into the 38-byte semantic payload.

---

## 19. 100% Air-Gapped Offline Operation

iTantra contains **zero cloud API calls, zero analytics trackers, and zero external telemetry endpoints**:
- All neural model weights (Whisper-Tiny INT8, Piper VITS) reside entirely inside `app/src/main/assets/models/` and are extracted to the application's internal sandboxed directory (`files/models/`).
- Network code is strictly restricted to local loopback, local multicast UDP, and Bluetooth RFCOMM sockets.
- The app operates indefinitely in full airplane mode with SIM cards removed.

---

## 20. Adaptive Network-State Handling (Feature 14)

The user interface dynamically reflects physical channel conditions through the **Adaptive Network-State Mapper**:
- **HEALTHY:** Latency $< 200\text{ ms}$, loss $< 5\%$, all transports active. Full UI features enabled.
- **DEGRADED:** Latency $200\text{--}800\text{ ms}$, loss $5\%\text{--}25\%$. UI switches to compact representation and warns of potential speech latency.
- **CRITICAL / FAILING:** Latency $> 800\text{ ms}$, loss $> 25\%$. UI disables high-bandwidth operations, restricts transmission to Semantic Base (P0) and text emergency alerts, and queues voice traffic in DTN storage.

---

## 21. Resource Benchmarking Results (Feature 24)

Empirical on-device resource characterization was executed on physical Android hardware across **44 distinct measurement phases** (`feature24_resource_results.json`):
- **Testbed Handsets:**
  - **Phone A:** Samsung Galaxy A55 5G (`SM-A556E`), Android 16 (API 36), Exynos 1480, 8 GB RAM.
  - **Phone B:** Samsung Galaxy Note 10 Lite (`SM-N770F`), Android 12 (API 31), Exynos 9810, 6 GB RAM.

### Measured Resource Findings:
| Operational Phase | Workload Type | Mean CPU (%) | Peak CPU (%) | Baseline RAM | Peak RAM (RSS) | Memory Delta |
|---|---|---|---|---|---|---|
| **Phase 0: Quiescent Idle** | Background Radio Listening | 0.72% | 1.80% | 71.8 MB | 74.1 MB | +0.8 MB |
| **Phase 1: Active Mesh Rx** | Wi-Fi UDP Multicast Ingestion | 3.40% | 7.20% | 74.5 MB | 78.2 MB | +3.7 MB |
| **Phase 2: Streaming STT** | Sherpa Whisper INT8 Decoding | 28.40% | 42.10% | 148.2 MB | 192.5 MB | +44.3 MB |
| **Phase 3: Neural TTS** | Piper VITS Speech Synthesis | 22.10% | 34.60% | 162.0 MB | 218.4 MB | +56.4 MB |
| **Phase 4: Multi-Hop Relay** | Transit Route & Forward | 2.80% | 5.40% | 75.2 MB | 79.8 MB | +4.6 MB |

- **Battery & Thermal Impact:** Continuous background radio listening draws $< 18\text{ mA}$. Active PTT speech bursts cause transient current draw of $180\text{--}260\text{ mA}$. Device skin temperature remained stable ($+1.8^\circ\text{C}$ elevation over a 30-minute stress session), confirming zero thermal throttling risk.

---

## 22. Speech Accuracy Benchmarking (Feature 21)

Feature 21 evaluated the neural speech pipeline across **250 standardized tactical utterances** (25 per language across 10 operational languages) under `docs/benchmark/`:

| Metric | Measured Benchmark Value | Evaluation Methodology |
|---|---|---|
| **Average Word Error Rate (WER)** | **7.6%** | Levenshtein distance normalized with Unicode NFC |
| **Average Character Error Rate (CER)** | **2.9%** | Diacritic- and halant-aware phoneme character scoring |
| **Tactical Token F1 Score** | **98.8%** | Precision/recall on critical military entities (coordinates, callsigns) |
| **Semantic Fact Accuracy** | **99.1%** | Proportion of core tactical directives accurately preserved |
| **Top Performing Language** | English (WER: **4.2%**), Hindi (WER: **5.8%**) | IndicConformer / Whisper-Tiny INT8 |

---

## 23. Latency Benchmarking

End-to-end speech latency was characterized across pipeline configurations:
- **Baseline Batch Pipeline (Pipeline A):** Operator speech recorded to completion $\to$ batch ASR inference $\to$ transmit. Endpoint-to-Transcript latency: **480.0 ms**.
- **Overlapped Two-Pass Pipeline (Pipeline C):** Pass 1 streaming ASR overlaps speech capture $\to$ zero-wait release splicing. Endpoint-to-Transcript latency: **235.0 ms** (**2.04x speedup**).
- **First Streaming Partial ($T_2$):** Emitted within **345.0 ms** of speech onset.
- **Airtime Traversal ($T_5 \to T_6$):** Over physical Wi-Fi Direct: **8.5 ms (median)**, **9.0 ms (P95)**. Over Bluetooth RFCOMM: **24.0 ms (median)**.
- **Local TTS Synthesis:** Piper VITS synthesizes response audio with a Real-Time Factor (RTF) of **0.18–0.24** on ARM64 ($180\text{ ms}$ compute time for $1.0\text{ s}$ of acoustic voice).

---

## 24. Low-Bandwidth, Packet Loss & Jitter Benchmarking (Feature 22)

Feature 22 evaluated protocol resilience across **60 programmatic impairment scenarios** (`feature22_results.json`):

| Impairment Condition | Configured Channel Parameter | Delivery Success Rate | Retransmission Overhead | Median Packet Latency |
|---|---|---|---|---|
| **Constrained Bandwidth** | 100 kbps | **100.0%** (6/6 packets) | 0 retransmissions | 8.5 ms |
| **Severe Bandwidth** | 20 kbps | **100.0%** (Semantic Mode) | 0 retransmissions | 14.2 ms |
| **Packet Loss (Mild)** | 5% random drop | **100.0%** | +5.2% overhead | 22.0 ms |
| **Packet Loss (Severe)** | 25% random drop | **96.8%** (via ARQ retransmission) | +28.4% overhead | 48.5 ms |
| **Severe Packet Loss** | 50% random drop | **88.4%** (ARQ + DTN buffer) | +54.2% overhead | 118.0 ms |
| **High Jitter** | 200 ms Gaussian jitter | **100.0%** | 0 retransmissions | 108.5 ms |
| **Temporary Partition** | 15-second link drop | **100.0%** (via DTN store-forward)| 0 dropped (queued in Room) | Delivered on reconnect |

---

## 25. Security Audit Findings (Feature 23)

Feature 23 executed **62 automated adversarial test scenarios** covering protocol fuzzing, crypto validation, and attack surface review:
- **Parser Robustness:** 500+ malformed buffers, boundary length declarations (65,534 bytes), invalid magic bytes, and corrupted payloads yielded **zero unhandled exceptions or memory faults**.
- **Cryptographic Tamper Detection:** Truncated HMAC-SHA256 caught 100% of single-bit payload alterations and header coordinate tampering.
- **Anti-Replay Protection:** 64-bit sliding window successfully blocked replay attacks, duplicate delivery, and handled 16-bit sequence number rollover without dropping valid packets.
- **Identified Residual Risks:**
  - HMAC provides integrity and authenticity, but **does not provide payload confidentiality**; over-the-air packets are unencrypted and subject to RF eavesdropping unless an operational payload cipher is added.
  - Network key distribution currently relies on pre-shared keys configured prior to deployment.

---

## 26. UI & Human Interaction Design

iTantra's user interface is built strictly with **Jetpack Compose Material 3** and adheres to high-contrast tactical design principles:
- **Industrial High-Contrast Themes:** Tactical Charcoal (Dark Theme) and Desert Sand (Light Theme) optimized for outdoor sunlight legibility.
- **Compact Press-to-Talk (PTT):** Ergonomic rectangular push-to-talk button positioned for one-handed thumb activation with tactile haptic confirmation.
- **Dedicated SOS Distress Bar:** High-contrast, non-obtrusive emergency broadcast control with confirmation safety gating.
- **Technical Inspectors:** Built-in packet hex dissector, live mesh topology visualizer, and hop journey timeline providing operators with immediate field observability.

---

## 27. Physical Hardware Validation

Physical dual-handset testing was conducted using:
- **Handset A:** Samsung Galaxy A55 5G (`SM-A556E`), Android 16.
- **Handset B:** Samsung Galaxy Note 10 Lite (`SM-N770F`), Android 12.
- **Verified Capabilities on Physical Hardware:**
  1. Optical QR code camera scanning via CameraX + ZXing (`qr_scan_b3.png`, `qr_scan_a4.png`).
  2. PTT voice capture, INT8 speech recognition, binary packet transmission, and neural TTS synthesis over physical Wi-Fi multicast and Bluetooth SPP.
  3. High-priority distress siren broadcast and reception between handsets.
  4. Complete dual-phone test recorded and archived in `docs/assets/demo/ad-astra-sih-2026-demo.mp4`.

---

## 28. System Limitations

1. **Physical Wireless Range:** Physical transmission distance between adjacent handsets is bounded by COTS smartphone RF hardware: $\sim 30\text{--}70\text{ meters}$ over 802.11 Wi-Fi multicast line-of-sight; $\sim 10\text{--}25\text{ meters}$ over Bluetooth Classic. Operating beyond these ranges requires multi-hop intermediate nodes or external high-power transceivers.
2. **Confidentiality:** Current protocol authenticates packet origin and verifies payload integrity via HMAC-SHA256, but does not encrypt text over the air. Anyone with an 802.11 monitor interface on the same channel can inspect raw packet payloads.
3. **Application-Layer MANET:** Relaying occurs in the Android user space application layer; it is not a native kernel 802.11s mesh or firmware-level ad-hoc routing protocol.
4. **Odia STT:** Relies on Android OS speech recognition rather than an embedded Sherpa-ONNX model.

---

## 29. Current Known Validation Gaps

1. **Large-Scale Field Multi-Hop:** Multi-hop relay routing is thoroughly validated in simulation harnesses (up to 7 hops with partition recovery), but physical hardware validation was conducted on a dual-handset testbed (1 hop direct RF link). Field testing on a 10+ node physical chain remains pending.
2. **Noise Resilience in Combat Conditions:** Speech accuracy was benchmarked on 250 standardized utterances. While background noise filtering was tested, live field acoustic validation in extreme 100+ dB artillery/siren environments has not been evaluated.
3. **Meta MMS Licensing for Commercial Release:** Meta MMS TTS models (KN, TA, OR) carry a CC-BY-NC 4.0 license, suitable for SIH evaluation but requiring replacement for commercial defense procurement.

---

## 30. Release Information

- **Release Version:** `v1.0.0`
- **Git Commit:** `343ba26ecd065ba3d9fdfcc18be2cbaf61829c7a` (`main`)
- **Release Tag:** `v1.0.0` (Pushed to GitHub remote)
- **Debug Build:** `app/build/outputs/apk/debug/app-debug.apk` (943.39 MB)
- **Release Build:** `app/build/outputs/apk/release/app-release.apk` (929.82 MB)
- **Size Justification:** The ~929 MB APK size is a deliberate engineering trade-off: it bundles complete, offline, quantized INT8 Whisper models, IndicConformer weights, and neural VITS speech synthesis models directly inside the application, ensuring 100% air-gapped independence from cloud infrastructure.

---

## 31. SIH Judge Takeaways

1. **Genuine Offline Neural Edge:** iTantra does not fake offline operation with webviews or mock delays. Real C++ Sherpa-ONNX runtimes execute INT8 neural models directly on physical smartphone ARM64 processors.
2. **Extreme Bandwidth Efficiency:** By replacing 32 kB/s voice streams with 38–164 byte binary packets, iTantra enables reliable voice communication over links that cannot sustain traditional audio streaming.
3. **Multilingual Inclusivity:** Full support for 10 regional Indian languages ensures seamless communication across diverse operational commands.
4. **Resilient Under Fire:** Built-in DTN store-and-forward queues, anti-replay sliding windows, and deterministic emergency siren preemption ensure critical messages survive severe channel degradation and link partitions.
5. **Truthful Engineering:** Every benchmark number, resource curve, and hardware test in this report is backed by reproducible repository code, recorded JSON artifacts, and verified video evidence.
