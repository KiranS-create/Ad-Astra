# iTantra — SIH 2026 Judge Technical Quick Reference

**Problem Statement:** `SIH26173` • **Team:** Ad Astra • **Project:** iTantra  
**Purpose:** Fast, authoritative, technically precise answers to evaluator and jury inquiries during technical review.

---

### 1. What is technically novel about iTantra?
Unlike standard walkie-talkie apps that stream continuous compressed/raw audio over the air, iTantra implements an **offline voice-to-packet neural transceiver**:
- Spoken voice is converted to text/tokens on-device via quantized neural models.
- Only a **38 to 164-byte binary radio frame** is transmitted over the wireless mesh.
- Natural acoustic voice is re-synthesized locally at the receiving node.
- This achieves a **99.7% to 99.9% wire bandwidth reduction**, enabling reliable multi-hop voice communications over austere radio channels where conventional audio streaming collapses.

---

### 2. Why must the system operate 100% offline?
In disaster zones (earthquakes, floods) and tactical defense environments, civilian cell towers, fiber backhauls, and internet infrastructure are destroyed, jammed, or unreachably distant. First responders and soldiers cannot rely on cloud STT/TTS APIs (e.g., Google Cloud, Whisper API). iTantra runs **100% air-gapped on COTS smartphone hardware** with zero external network dependencies.

---

### 3. Why is low bitrate essential over ad-hoc wireless links?
Raw 16 kHz 16-bit audio generates **32,000 bytes/sec** (256 kbps). In ad-hoc multi-hop meshes, packet collisions, hidden-terminal problems, and channel contention scale exponentially with packet size and airtime duration. An airtime burst of $10\text{ ms}$ (for a 40-byte packet) has an order-of-magnitude lower probability of collision than a $3\text{--}5\text{ second}$ continuous audio stream.

---

### 4. What is Semantic Compression (Feature 18)?
In severe link degradation (packet loss $> 25\%$, bandwidth $< 20\text{ kbps}$), transmitting full conversational text is wasteful. Semantic compression tokenizes tactical intent into fixed 1-byte command categories, 2-byte entity IDs, and 4-byte packed coordinates. The entire operational directive is encoded in **38 to 48 bytes**. The receiver reconstructs standard tactical phraseology (e.g., *"EVACUATE UNIT CHARLIE GRID 7482"*).

---

### 5. Why use Context Deltas (Feature 19)?
Tactical teams frequently transmit incremental updates (e.g., casualty numbers changing from 3 to 4, search sectors advancing). Re-transmitting unchanged background context wastes spectrum. Context Deltas transmit only the mathematical state difference ($\Delta$). Receiving nodes update their replicated situational state machine, saving up to 75% of payload bytes. Updates are gated behind a strict confidence threshold ($\ge 70$).

---

### 6. How does Multi-Hop Relay work (Feature 20)?
iTantra implements an application-layer mobile ad-hoc network (MANET) router:
- Packets carry an 8-bit Time-To-Live (TTL) field (default 7).
- Intermediate nodes inspect destination IDs, decrement TTL, log hop records in the packet journey header, and forward.
- A sliding FIFO cache of `(SourceID, SeqNum)` tuples provides **$< 1\text{ ms}$ duplicate suppression**, preventing broadcast loops and storming.

---

### 7. How does Delay-Tolerant Networking (DTN) work?
When an intermediate or destination node is unreachable (due to terrain obstruction or RF partitioning):
- Packets are safely enqueued in a local Room/SQLite **DTN Buffer**.
- A background custodian service monitors periodic peer discovery beacons.
- When an ad-hoc connection is re-established, the buffer automatically bursts stored packets to the newly met node (epidemic store-and-forward).
- A 10-minute message retention policy prevents storage exhaustion.

---

### 8. How are packets authenticated and protected against tampering?
Every canonical frame carries:
1. **CRC-32 (IEEE 802.3):** 4-byte early link corruption check to discard RF bit flips.
2. **HMAC-SHA256:** Truncated 8-byte authentication tag computed over invariant header fields (Magic, Version, Type, Priority, SeqNum, SourceID, DestID, Timestamp) and payload body using pre-shared operational network keys (`NetworkKeyManager`).
3. **64-Bit Anti-Replay Sliding Window:** Rejects replayed, delayed, or out-of-window sequence numbers.

---

### 9. What happens when packets are lost in transit?
- For point-to-point voice/data: An adaptive Stop-and-Wait ARQ retransmission protocol attempts up to 3 retransmissions.
- For severe link drops: The packet transitions to the DTN buffer for opportunistic store-and-forward.
- In Feature 22 testing: With **25% random packet loss**, iTantra maintains a **96.8% delivery success rate** with a median latency of $48.5\text{ ms}$.

---

### 10. What happens if a Context Delta arrives with missing prior context?
If a receiving node receives a Context Delta referencing an unknown or expired context ID:
- The delta is safely quarantined.
- The receiving node issues an automatic context synchronization request (`MSG_CONTEXT_REQ`) to the transmitting peer.
- The sender replies with a full state snapshot (`FULL_SNAPSHOT`), reconciling the local store before applying the delta.

---

### 11. What happens when the network is severely congested?
The 3-tier `TacticalQosQueue` enforces strict traffic prioritization:
- **Queue 0 (EMERGENCY):** Preempts all other traffic; transmitted immediately.
- **Queue 1 (TACTICAL VOICE):** Prioritized voice communication frames.
- **Queue 2 (ROUTINE / TELEMETRY):** Throttled or dropped first during congestion to preserve link capacity.
- The Adaptive Network-State UI alerts the operator and automatically downshifts from FULL (P2) to SEMANTIC BASE (P0) representation.

---

### 12. What languages are supported and what are the models?
**10 Indian Languages Supported Fully Offline:**
- **English (`en`)** & **Hindi (`hi`):** Whisper-Tiny INT8 / IndicConformer + Piper VITS (22.05 kHz).
- **Marathi (`mr`), Malayalam (`ml`), Telugu (`te`), Bengali (`bn`):** IndicConformer INT8 + Piper VITS (22.05 kHz).
- **Gujarati (`gu`):** IndicConformer INT8 + Mimic3 VITS (16 kHz).
- **Kannada (`kn`), Tamil (`ta`), Odia (`or`):** IndicConformer INT8 + Meta MMS VITS (16 kHz) / System TTS Fallback.

---

### 13. How accurate is the Speech-to-Text engine?
Across the 250-utterance standardized tactical benchmark (Feature 21):
- **Average Word Error Rate (WER):** **7.6%**
- **Average Character Error Rate (CER):** **2.9%**
- **Tactical Critical Token F1 Score:** **98.8%** (military coordinates, numbers, callsigns)
- **Semantic Fact Accuracy:** **99.1%** (tactical intent preserved)

---

### 14. What latency was empirically measured?
- **Endpoint-to-Transcript:** **235.0 ms** via Overlapped Two-Pass Pipeline (**2.04x faster** than 480.0 ms batch baseline).
- **Streaming First Partial:** **345.0 ms** from speech onset.
- **Wi-Fi Multicast Airtime:** **8.5 ms (median)**.
- **Bluetooth RFCOMM Airtime:** **24.0 ms (median)**.
- **Neural TTS Synthesis:** Real-Time Factor (RTF) of **0.18–0.24** on ARM64 ($180\text{ ms}$ compute for $1\text{ second}$ of audio).

---

### 15. What physical hardware was tested?
- **Phone A:** Samsung Galaxy A55 5G (`SM-A556E`), Android 16 (API 36), Exynos 1480, 8 GB RAM.
- **Phone B:** Samsung Galaxy Note 10 Lite (`SM-N770F`), Android 12 (API 31), Exynos 9810, 6 GB RAM.
- **Verified on Hardware:** Physical Wi-Fi multicast UDP (port 42888), Bluetooth SPP sockets, CameraX optical QR scanning, on-device Whisper INT8 STT, Piper VITS playback, and physical CPU/RAM/Battery profiling (Feature 24).

---

### 16. What is physically validated vs. simulated?
- **Physically Validated on Hardware:**
  - Dual-phone direct RF link (PTT voice capture $\to$ transmission $\to$ neural playback).
  - Optical CameraX QR scanner node pairing.
  - On-device CPU, RAM, battery, and thermal profiling across 44 phases.
  - Emergency SOS siren preemption.
- **Simulated in Test Harnesses:**
  - Large-scale multi-hop topologies ($> 2$ intermediate hops).
  - Feature 22 network impairment injections (controlled 10–50% packet loss, 50–500 ms jitter, 20–100 kbps bandwidth caps via `SimulatedRadioChannel`).
  - Feature 23 adversarial fuzzing (500+ malformed buffers).

---

### 17. What are the known limitations?
1. **RF Line-of-Sight Range:** Smartphone antennas provide $30\text{--}70\text{ m}$ on Wi-Fi multicast and $10\text{--}25\text{ m}$ on Bluetooth. Operating over kilometers requires intermediate relay hops or external sub-GHz RF modems.
2. **Confidentiality:** Over-the-air packets are authenticated (HMAC-SHA256) but **unencrypted**. Anyone with an RF monitor on the channel can inspect plaintext payloads.
3. **Application Layer Routing:** Relaying occurs in Android user space, not at native 802.11s kernel/firmware layers.

---

### 18. Why is the Release APK ~929 MB?
To guarantee **100% offline, air-gapped sovereignty without requiring internet access post-installation**, the APK directly bundles:
- Quantized INT8 Whisper-Tiny acoustic encoder and decoder models.
- Multilingual IndicConformer acoustic weights and tokenizers.
- Native C++ Sherpa-ONNX JNI shared libraries (`libsherpa-onnx-jni.so`, `libonnxruntime.so` for `arm64-v8a`).
- Offline neural VITS Piper and Mimic3 voice assets for 7 languages.
This ensures first responders can install the APK in the field from a USB drive and operate immediately without cloud downloads.
