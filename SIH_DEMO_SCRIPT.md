# iTantra — SIH 2026 Live Demonstration Script (~3 Minutes)

**Project:** SIH26173 — iTantra (Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access)  
**Team:** Ad Astra • **Evaluation Audience:** Smart India Hackathon Technical Jury  
**Target Duration:** Exactly 3 Minutes (180 Seconds)  
**Hardware Setup:** Two Android Handsets (Phone A: Samsung Galaxy A55 5G, Phone B: Samsung Galaxy Note 10 Lite) placed on the demo table in **Airplane Mode** (Cellular data & SIM disconnected; connected to Phone A's local mobile hotspot, or paired over Bluetooth Classic).  

---

## 1. Demonstration Modality Demarcation (Mandatory Truthfulness Contract)

To ensure absolute transparency with hackathon evaluators, every component of this demonstration is explicitly demarcated into one of three execution categories:

| Tag | Category | Explanation |
|:---:|:---|:---|
| **`[LIVE]`** | **Live Physical Execution** | Executed directly in front of judges on physical handsets (Phone A and Phone B) over physical ad-hoc RF links (local Wi-Fi networking via phone hotspot, or Bluetooth Classic SPP). *(Note: Wi-Fi Direct is not currently implemented).* |
| **`[RECORDED]`** | **Pre-Recorded Physical Evidence** | Supported by verified screen recordings and video artifacts archived in the repository (`docs/assets/demo/ad-astra-sih-2026-demo.mp4`). |
| **`[SIMULATED]`** | **Deterministic Harness Simulation** | Executed via on-device mock channels or automated test harnesses to demonstrate large-scale multi-hop graphs or severe RF impairment conditions without requiring physical RF chambers. |

---

## 2. Chronological Minute-by-Minute Script

### Phase 1: Problem & The Air-Gapped Neural Paradigm (0:00 – 0:30)
**Presenter Speaking:**
> *"Respected Judges, in disaster rescue operations and tactical defense deployments, cellular towers and cloud connectivity are the first assets to fail. Standard walkie-talkies stream raw audio requiring 32,000 bytes per second. Over constrained ad-hoc wireless links, this saturates the channel, causes extreme packet loss, and limits range.*
> 
> *Here are two commercial Android handsets in complete Airplane Mode with zero internet connectivity. This is **iTantra** — a neural voice-to-packet transceiver that replaces 32 kB/s raw voice streams with 38 to 164-byte binary radio packets, operating 100% offline."*

**Action:**
- Show both phones with Airplane Mode icon active in system status bar.
- Point to the tactical charcoal radio console on Phone A.

---

### Phase 2: Offline Multilingual Speech & Compact Transmission (0:30 – 1:05) `[LIVE]`
**Presenter Speaking:**
> *"I will now speak a tactical command in Hindi on Phone A using our compact push-to-talk button. Watch how streaming on-device speech-to-text transcribes my voice with zero cloud latency."*

**Action `[LIVE]`:**
1. Hold the rectangular PTT button on Phone A.
2. Speak clearly into the microphone:
   > *"यूनिट अल्फा, तुरंत सेक्टर चार में पहुंचें।"* (*"Unit Alpha, reach Sector 4 immediately."*)
3. Release the PTT button.
4. **Observe on Phone A HUD:** Streaming partials finalize in $< 10\text{ ms}$ of release. Packet is serialized into a 74-byte `COMPACT P1` frame.
5. **Observe on Phone B:** Within $< 50\text{ ms}$ over Wi-Fi multicast, Phone B receives the packet, displays the message card, and synthesizes natural Hindi voice output through its local neural Piper VITS voice model:
   > *(Phone B speaker audibly plays synthesized Hindi speech)*.

**Presenter Explaining:**
> *"Notice: No cellular network, no server API. Spoken Hindi on Phone A was transcribed on-device, encoded into 74 bytes of binary wire data, transmitted over local Wi-Fi multicast, and synthesized on Phone B as acoustic speech. That represents a **99.9% bandwidth reduction** compared to streaming raw voice."*

---

### Phase 3: Emergency Distress & Preemptive Siren (1:05 – 1:35) `[LIVE]`
**Presenter Speaking:**
> *"In a crisis, search-and-rescue teams cannot wait for channel queues. iTantra implements a preemptive Emergency Distress Protocol."*

**Action `[LIVE]`:**
1. Tap the dedicated high-contrast red **SEND DISTRESS** button on Phone A.
2. Confirm the safety dialog.
3. **Observe on Phone B:**
   - Normal chat UI is instantly preempted by a full-screen high-priority **EMERGENCY DISTRESS BEACON**.
   - An audible dual-tone $800\text{ Hz} / 1200\text{ Hz}$ acoustic siren triggers through Phone B's `AudioTrack` at maximum volume.
   - The distress card displays Phone A's ID, emergency timestamp, and embedded GPS coordinates.

**Presenter Explaining:**
> *"This P0 emergency packet is only 38 bytes. It preempted all routine queues, bypassed standard serialization, and drove an immediate audible acoustic alert."*

---

### Phase 4: Network Degradation, DTN & Multi-Hop Relay (1:35 – 2:10) `[LIVE + SIMULATED]`
**Presenter Speaking:**
> *"What happens when RF links degrade or nodes enter underground shelters? iTantra integrates Delay-Tolerant Networking (DTN) and MANET-style relaying."*

**Action `[LIVE]`:**
1. Disable Wi-Fi on Phone B to simulate sudden RF shadowing / link drop.
2. Speak a second message on Phone A: *"यूनिट अल्फा, स्थिति सामान्य है।"*
3. **Observe on Phone A:** Packet status transitions to `QUEUED_IN_DTN` (amber clock indicator). The packet is safely preserved in local Room/SQLite storage with a 10-minute operational retention lifecycle.
4. Re-enable Wi-Fi on Phone B.
5. Within 3 seconds, neighbor discovery detects Phone B rejoining the ad-hoc group; the DTN buffer automatically bursts the stored packet, delivering it to Phone B.

**Action `[SIMULATED]` (Briefly Show Screen):**
- Open **Mesh Topology Screen** (Settings $\to$ Topology) showing multi-hop routing graph.
- Point to reverse-path hop tracing:
  > *"In multi-hop topologies `[SIMULATED]`, intermediate nodes forward packets while decrementing TTL and logging hop journey records without broadcast storms."*

---

### Phase 5: Technical Packet Inspector & Security Layer (2:10 – 2:35) `[LIVE]`
**Presenter Speaking:**
> *"For field technicians, iTantra provides full protocol observability and tamper detection."*

**Action `[LIVE]`:**
1. On Phone A or B, tap any delivered message card $\to$ select **INSPECT PACKET**.
2. Show the **Message Technical Inspector**:
   - **Header Analysis:** 28-byte canonical header fields (`Magic: 0x5441`, `Type: COMPACT`, `Seq: 0x0004`, `Timestamp`, `Source`, `Dest`).
   - **Hex Dump:** Color-coded byte breakdown (Header in cyan, Payload in green, CRC-32 in amber, HMAC in magenta).
   - **Cryptographic Markers:** `CRC-32: VALID`, `HMAC-SHA256: VALID (Key: Alpha-01)`, `Replay Window: ACCEPTED`.

**Presenter Explaining:**
> *"Every frame carries CRC-32 for RF corruption detection and truncated HMAC-SHA256 for origin authenticity, verified against a 64-bit sliding anti-replay window."*

---

### Phase 6: Empirical Metrics & Closing Value Proposition (2:35 – 3:00)
**Presenter Speaking:**
> *"To summarize our verified engineering benchmarks:*
> - *Average Word Error Rate: **7.6%** across 10 Indian languages.*
> - *Endpoint-to-Transcript Latency: **235 ms**, a 2x speedup via our Overlapped Two-Pass pipeline.*
> - *Quiescent CPU: **0.72%**; Baseline RAM: **72 MB**.*
> - *903 automated unit tests passing at 100%.*
> 
> *iTantra delivers sovereign, infrastructure-independent tactical voice communications across India's linguistic landscape. Thank you, and we welcome your technical questions."*

---

## 3. Cue Card for Presenter / Device Operator

| Time | Presenter Dialogue | Device Operator Action |
|---|---|---|
| **0:00** | Problem statement, 32 kB/s vs 38 B | Display both phones in Airplane Mode |
| **0:30** | Push-to-Talk Hindi voice demonstration | Press PTT on Phone A, speak Hindi sentence |
| **0:50** | Explain bandwidth reduction & neural synthesis | Hold Phone B near judges to hear synthesized voice |
| **1:05** | Emergency distress protocol introduction | Tap red SOS DISTRESS on Phone A |
| **1:20** | Explain P0 preemption and siren | Mute siren on Phone B after 3 seconds |
| **1:35** | Network partition & DTN store-and-forward | Toggle Wi-Fi off on Phone B, send message, re-toggle Wi-Fi |
| **2:10** | Technical Inspector & security dissection | Tap message card $\to$ Message Inspector on Phone A |
| **2:35** | Empirical metrics summary & closing | Show Communication Health Panel / Benchmark Screen |
