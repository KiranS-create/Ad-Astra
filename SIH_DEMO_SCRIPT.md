# SIH26173 — iTantra 3–5 Minute SIH Judging Demo Script

This script is engineered for the Smart India Hackathon 2026 / ISRO evaluation committee.

---

## Act 1: The Problem & Offline Proof (Minute 0:00 – 1:00)

1. **Demonstrate Zero Internet Dependency**:
   - Show both Phone A and Phone B in **Airplane Mode** (with Wi-Fi or Bluetooth enabled, but Cellular Data turned OFF).
   - Show the green glowing badge on the screen: **`100% OFFLINE AI READY`**.
   - Explain: *"Judges, iTantra runs 100% on-device. There are no calls to OpenAI, Google Cloud Speech, Azure, or AWS. If an earthquake or cyclone cuts fiber backhauls, iTantra continues working uninterrupted."*

2. **The Bandwidth Dilemma**:
   - Point to the Benchmark screen:
   - *"Standard walkie-talkies or VOIP apps transmit raw audio at 256,000 bits per second (32 KB/s). Over HF, VHF, LoRa, or satellite links, this is impossible. iTantra transmits only compact text packets, reducing the required bandwidth by 99.8%."*

---

## Act 2: Primary Walkie-Talkie Speech Pipeline (Minute 1:00 – 2:30)

1. **Select Hindi on Phone A**:
   - Tap `Hindi (हिंदी)`.
   - Ensure transport is set to `WI-FI` (connected via local hotspot or ad-hoc LAN).

2. **Hold PTT and Speak**:
   - Hold the tactical circular PTT button. The button pulses radar-green, and the live waveform visualizer activates.
   - Speak clearly: *"हम राहत सामग्री के साथ उत्तर दिशा में आगे बढ़ रहे हैं।"*
   - Release the PTT button.

3. **Observe Transmitter (Phone A)**:
   - Live transcription ticker displays: `TX >> हम राहत सामग्री के साथ उत्तर दिशा में आगे बढ़ रहे हैं।`
   - Shows: `Packet: 168 B (Audio saved: 94 KB) | 120ms`

4. **Observe Receiver (Phone B)**:
   - Phone B's speaker immediately synthesizes and announces the Hindi message in natural, intelligible speech.
   - The transcript window updates with an amber incoming bubble.
   - Show judges the exact packet size: **168 bytes total**!

---

## Act 3: Multilingual Indian Language Switching (Minute 2:30 – 3:30)

1. **Select Tamil or Telugu**:
   - Tap `Tamil (தமிழ்)` or `Telugu (తెలుగు)` on the language bar.
   - Switch to **Continuous Conversation Mode** (tap `MODE: CONTINUOUS`).
   - Speak in conversational cadence: *"நாங்கள் பாதுகாப்பாக உள்ளோம்."*
   - VAD automatically detects the voice start, waits for the 700ms pause, finalizes the sentence, transmits, and Phone B speaks the Tamil sentence automatically without holding any buttons!

---

## Act 4: Emergency Distress Preemption (Minute 3:30 – 4:15)

1. **Tap the Red DISTRESS Button on Phone A**:
   - A high-priority emergency alert packet (`PRIORITY = DISTRESS`) is broadcast.
   - **Phone B immediately sounds an acoustic two-tone emergency siren alarm (800Hz / 1200Hz)**.
   - The distress message is announced at maximum alarm stream volume.
   - Explain: *"Under distress conditions, iTantra preempts all queued traffic, sounds a hardware tone generator in <5 milliseconds, and enforces non-interruptible playback."*

---

## Act 5: Empirical Benchmark & Hardware Telemetry (Minute 4:15 – 5:00)

1. **Open the Benchmark Screen**:
   - Show the empirical bar chart:
     - Traditional Voice: **96,000 bytes** for a 3-second utterance.
     - iTantra Neural Transceiver: **168 bytes**.
     - Bandwidth Savings: **99.8% Reduction**.
   - Show measured latency breakdown:
     - VAD Detection: ~20ms
     - STT Inference: ~85ms
     - Packet Framing: ~1.2ms
     - Transport Transit: ~12ms
     - TTS Audio Synthesis: ~45ms
     - Total End-to-End Latency: **~163ms** (Real-Time Factor: 0.18).
2. **Conclude**:
   - *"iTantra satisfies all ISRO and Smart India Hackathon non-negotiable requirements: 100% offline, 10 Indian languages, ultra-low bandwidth, open-source architecture, and low-end mobile hardware optimization."*
