# SIH26173 — Architectural Decisions & Trade-Offs

**Project**: SIH26173 — iTantra  
**Scope**: Technical Decision Records & Architecture Comparison

---

## 1. Text Transmission vs Raw Audio / Neural Codecs

### Decision:
Transmit finalized, error-corrected text packets over the wireless physical link, rather than transmitting compressed speech waveforms or neural audio codec tokens.

### Rationale:
- **Bandwidth**: 16kHz 16-bit PCM voice requires 32,000 bytes/second (256 kbps). Even low-bitrate neural codecs (such as EnCodec or DAC) require 1,500 – 6,000 bps. In contrast, an entire conversational sentence in UTF-8 requires only 40–180 bytes, translating to an effective transmission bitrate of **<500 bps**.
- **Packet Loss Resilience**: A dropped 100-byte audio frame causes audible clicks or dropped syllables. In text transmission, standard ARQ (Automatic Repeat Request) or small packet retransmissions are trivial because the entire payload fits within a single network MTU.

---

## 2. On-Device Offline ML vs Cloud Speech APIs

### Decision:
Strictly enforce on-device local inference for all VAD, STT, and TTS subsystems.

### Rationale:
- **Compliance**: The SIH / ISRO problem statement explicitly bans cloud dependencies.
- **Tactical Survivability**: In disaster relief and defense operations, cellular infrastructure and backhaul internet are commonly destroyed. Local processing guarantees operational readiness.
- **Privacy & Security**: Spoken communications never leave the local radio node.

---

## 3. Wi-Fi UDP Broadcast vs Wi-Fi Direct (P2P)

### Decision:
Use **UDP broadcast on port 42888 over local Wi-Fi / mobile hotspot mesh** as the primary Wi-Fi baseline.

### Rationale:
- Wi-Fi Direct (P2P) requires multi-step negotiation (Group Owner negotiation, WPS pin/push dialogs, DHCP lease timeouts), often taking 10–25 seconds to establish a session, and frequently fails on heterogeneous Android chipsets.
- UDP broadcast over an open mobile hotspot works instantaneously (<10ms discovery), supports 1-to-many team broadcasts (crucial for disaster rescue teams), and requires zero configuration.

---

## 4. Bluetooth Classic RFCOMM SPP vs Bluetooth Low Energy (BLE)

### Decision:
Implement **Bluetooth Classic RFCOMM Serial Port Profile (SPP)**.

### Rationale:
- BLE has strict L2CAP MTU limits (typically 20–23 bytes without MTU negotiation) and high connection-interval jitter.
- RFCOMM SPP provides a reliable stream socket identical to hardware UART, making it directly compatible with future serial LoRa / SDR external transceivers.

---

## 5. Non-Neural VAD Baseline vs Neural VAD

### Decision:
Deploy an **Adaptive Energy + Zero Crossing Rate (ZCR) VAD** as the real-time endpointing front-end.

### Rationale:
- Operates in <0.05ms per frame with zero dynamic memory allocation.
- Conserves CPU and battery on low-end phones during long idle listening periods.
- Adapts noise floor dynamically to background ambient sound.
