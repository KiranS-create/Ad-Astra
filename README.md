# SIH26173 — iTantra
### Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links

**Smart India Hackathon 2026 / ISRO Problem Statement SIH26173**  
*Autonomous Offline Android Application & Neural Transceiver Architecture*

---

## 1. Executive Summary

In disaster management, tactical defense, and remote ISRO telemetry zones, wireless communications links suffer from extreme bandwidth constraints, intermittent packet drop, and high latency. Transmitting raw voice audio requires **256,000 bps (32,000 bytes/second for 16kHz 16-bit PCM)**, quickly saturating narrowband channels.

**iTantra** fundamentally re-engineers radio communication:
> **Never transmit raw voice over the communication channel.**  
> Transmit compact, error-corrected text packets (<35 bytes framing) over local Wi-Fi or Bluetooth, and reconstruct natural audio at the receiver using on-device neural Text-to-Speech.

### Core Value Proposition:
- **~99.8% Bandwidth Reduction**: A 3-second spoken sentence takes **96,000 bytes** in raw voice, but only **~170 bytes** with iTantra.
- **100% Offline Local Intelligence**: Absolutely zero reliance on cloud APIs (No OpenAI, Gemini, Google Cloud Speech, Azure, or AWS).
- **10 Indian Languages Supported**: Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English.
- **Hardware Agnostic**: Runs smoothly on low/mid-range Android hardware (minSdk 26).

---

## 2. End-to-End Pipeline Architecture

```
PHONE A (Transmitter)
  Microphone Capture (16kHz 16-bit Mono PCM)
  ↓
  Adaptive Energy & ZCR Voice Activity Detection (VAD)
  ↓
  On-Device Offline Speech-to-Text (STT)
  ↓
  Pause-Aware Sentence Finalization (Danda '।', '.', '?', '!')
  ↓
  Adaptive Deflate Compression (Avoids expansion on short Indic text)
  ↓
  iTantra Compact Binary Radio Protocol Framer (31-byte header + CRC-32)
  ↓
  Local Wireless Transport (Wi-Fi UDP Broadcast / Bluetooth SPP / Serial LoRa)

CHANNEL (Low Bitrate Radio Link: ~450 bps)
  Compact Packet (~170 Bytes)

PHONE B (Receiver)
  Transport Receiver & CRC-32 Integrity Validation
  ↓
  Adaptive Decompressor & Sequence Reassembly
  ↓
  Priority Gating (DISTRESS / ALERT / IMPORTANT / NORMAL)
  ↓
  On-Device Offline Text-to-Speech (TTS)
  ↓
  Emergency Acoustic Alarm (for Alerts) & Intelligible Audio Playback
```

---

## 3. Supported Languages (10 Official Languages)

| Language | ISO Code | Script | Offline STT Engine | Offline TTS Engine |
|---|---|---|---|---|
| **Hindi** | `hi` | Devanagari | IndicConformer / System Offline ASR | MMS-TTS Hindi / System Offline TTS |
| **Gujarati** | `gu` | Gujarati | IndicConformer / System Offline ASR | MMS-TTS / System Offline TTS |
| **Marathi** | `mr` | Devanagari | IndicConformer / System Offline ASR | MMS-TTS / System Offline TTS |
| **Kannada** | `kn` | Kannada | IndicConformer / System Offline ASR | MMS-TTS / System Offline TTS |
| **Malayalam** | `ml` | Malayalam | IndicConformer / System Offline ASR | MMS-TTS / System Offline TTS |
| **Tamil** | `ta` | Tamil | IndicConformer / System Offline ASR | MMS-TTS / System Offline TTS |
| **Telugu** | `te` | Telugu | IndicConformer / System Offline ASR | MMS-TTS / System Offline TTS |
| **Odia** | `or` | Odia | IndicConformer / System Offline ASR | MMS-TTS / System Offline TTS |
| **Bengali** | `bn` | Bengali | IndicConformer / System Offline ASR | MMS-TTS / System Offline TTS |
| **English** | `en` | Latin | IndicConformer / System Offline ASR | Piper / System Offline TTS |

---

## 4. Binary Protocol Specification (`iTantra v1`)

| Offset | Field | Type | Size | Description |
|---|---|---|---|---|
| 0 | `magic` | bytes | 2 | `0x49 0x54` ('IT' for iTantra) |
| 2 | `version` | uint8 | 1 | Protocol version (`0x01`) |
| 3 | `msg_type` | uint8 | 1 | `1`=HELLO, `2`=SESSION, `3`=TEXT, `4`=ACK, `5`=ALERT, `6`=PING, `7`=PONG |
| 4 | `priority` | uint8 | 1 | `0`=NORMAL, `1`=IMPORTANT, `2`=ALERT, `3`=DISTRESS |
| 5 | `flags` | uint8 | 1 | Bit 0: Compressed, Bit 1: Fragmented, Bit 2: Requires ACK |
| 6 | `seq_num` | uint16 | 2 | Sequence counter for deduplication and ordering |
| 8 | `timestamp` | uint64 | 8 | Monotonic sender timestamp (ms) |
| 16 | `source_id` | uint32 | 4 | Unique sender device ID |
| 20 | `dest_id` | uint32 | 4 | Destination ID (`0xFFFFFFFF` = Broadcast) |
| 24 | `language_id` | uint8 | 1 | Language Enum ID (0=HI, 1=GU, 2=MR, 3=KN, 4=ML, 5=TA, 6=TE, 7=OR, 8=BN, 9=EN) |
| 25 | `payload_len` | uint16 | 2 | Payload byte length ($N$) |
| 27 | `payload` | bytes | $N$ | UTF-8 or compressed text bytes |
| $27+N$ | `crc32` | uint32 | 4 | CRC-32 integrity checksum over entire packet |

**Total Header Overhead**: Strictly **31 bytes** (27 bytes header + 4 bytes CRC-32).

---

## 5. Build and Installation Instructions

### Prerequisites
- JDK 17 or JDK 19 (`C:\Program Files\Java\jdk-19`)
- Android SDK with platform `android-35` and build-tools `35.0.0`
- Gradle 8.10.2 (included via `gradlew.bat`)

### 1. Run Automated Test Suite
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
$env:ANDROID_HOME = "C:\Users\kiran akash\AppData\Local\Android\Sdk"
.\gradlew.bat test --console=plain
```

### 2. Build Debug APK
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
$env:ANDROID_HOME = "C:\Users\kiran akash\AppData\Local\Android\Sdk"
.\gradlew.bat assembleDebug --console=plain
```
The installable APK will be produced at:
`app/build/outputs/apk/debug/app-debug.apk` (~16.7 MB).

### 3. Install on Android Device
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 6. Two-Phone Demo Setup

### Option A: Local Wi-Fi Mesh (Recommended for Hackathon)
1. Turn on Wi-Fi Hotspot on **Phone A** (Mobile data / internet is **NOT** needed).
2. Connect **Phone B** to Phone A's hotspot.
3. Open **iTantra** on both phones.
4. Set Transport to **WI-FI**.
5. Select **Hindi** (or another Indian language) on Phone A.
6. Press and hold the **PTT button** on Phone A and speak in Hindi:  
   *"हम राहत सामग्री के साथ उत्तर दिशा में आगे बढ़ रहे हैं।"*
7. Release PTT button.
8. **Observe Phone B**:
   - The compact text packet (~170 bytes) is received over UDP broadcast on port 42888.
   - Phone B's offline TTS synthesizer speaks the message in Hindi.
   - The transcript and measured end-to-end latency (~140ms) appear on Phone B's screen.

### Option B: Bluetooth SPP
1. Pair Phone A and Phone B in Android Bluetooth settings.
2. Open iTantra and select **BT SPP** transport.
3. Transmit messages point-to-point.

### Option C: Single-Device Verification (Loopback Mode)
1. In iTantra, select **LOOP** transport.
2. Press PTT or click **DISTRESS**.
3. The app transcribes the speech, packages the radio packet, loops it back into the receiver pipeline, and speaks it back through the phone speaker.
