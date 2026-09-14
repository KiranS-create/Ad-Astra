# iTantra
### Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links

**SIH 2026**  
**Problem Statement:** SIH26173  
**Team:** Ad Astra  

---

## 1. Project Overview

In tactical, disaster-relief, and off-grid remote operations, conventional digital voice communications saturate narrow wireless channels (a 3-second 16kHz 16-bit PCM voice transmission requires ~96,000 bytes).

**iTantra** implements a neural transceiver architecture designed to operate over constrained, low-bitrate radio links:
1. Spoken voice is captured and transcribed locally on the transmitter using an on-device Speech-to-Text (STT) engine.
2. The recognized text or tactical command is framed into an ultra-compact binary packet (46 to 170 bytes, including 28-byte canonical header, CRC32, and optional HMAC authentication).
3. The packet is transmitted over off-grid wireless transports (Wi-Fi UDP broadcast, Bluetooth RFCOMM SPP, or point-to-point mesh).
4. The receiver decodes the packet and synthesizes intelligible speech locally using on-device neural Text-to-Speech (TTS).

---

## 2. Supported Languages

iTantra supports 10 languages with offline execution:

| Language | Code | Script | Offline STT Engine | Offline TTS Engine |
| :--- | :---: | :--- | :--- | :--- |
| **Hindi** | `hi` | Devanagari | Whisper-Tiny INT8 / IndicConformer | VITS Piper (Rohan Medium) |
| **English** | `en` | Latin | Whisper-Tiny INT8 / IndicConformer | VITS Piper (Lessac Medium) |
| **Gujarati** | `gu` | Gujarati | Whisper-Tiny INT8 / IndicConformer | VITS Mimic3 (CMU Indic Low) |
| **Marathi** | `mr` | Devanagari | Whisper-Tiny INT8 / IndicConformer | VITS Piper (Google Medium) |
| **Kannada** | `kn` | Kannada | Whisper-Tiny INT8 / IndicConformer | VITS Meta MMS Kannada* |
| **Malayalam** | `ml` | Malayalam | Whisper-Tiny INT8 / IndicConformer | VITS Piper (Arjun Medium) |
| **Tamil** | `ta` | Tamil | Whisper-Tiny INT8 / IndicConformer | VITS Meta MMS Tamil* |
| **Telugu** | `te` | Telugu | Whisper-Tiny INT8 / IndicConformer | VITS Piper (Maya Medium) |
| **Odia** | `or` | Odia | Android OS Fallback | VITS Meta MMS Odia* |
| **Bengali** | `bn` | Bengali | Whisper-Tiny INT8 / IndicConformer | VITS Piper (Google Medium) |

*\*Note: Kannada, Tamil, and Odia TTS weights exceed GitHub's 100 MB per-file limit and are distributed via GitHub Releases or external download. See [docs/MODELS.md](docs/MODELS.md) for details.*

---

## 3. Offline Operation & Security

- **100% Offline Processing:** All neural inference (ONNX Runtime via Sherpa-ONNX) runs strictly on-device without cloud API dependencies.
- **Protocol Integrity:** 28-byte fixed framing, CRC-32 wire error detection, and optional HMAC-SHA256 authentication for anti-tamper security.
- **Adaptive VBR & Shared Context:** Supports full text, compressed text, semantic base emergency commands, and ultra-compact 6–7 byte context deltas for situational updates.

---

## 4. Android System Requirements

- **Minimum OS:** Android 8.0 (API level 26)
- **Target OS:** Android 15 (API level 35)
- **Architecture:** `arm64-v8a` / `armeabi-v7a`
- **Permissions Required:** Microphone (Audio recording), Bluetooth (Connect & Scan), Wi-Fi (Multicast lock / Local Hotspot socket)

---

## 5. Obtaining and Installing the APK

For evaluators and jury members who want to test iTantra directly without compiling:
1. Download the pre-built demo APK (`app-debug.apk`) from the **[Releases](https://github.com/KiranS-create/iTantra/releases)** section.
2. Enable installation from unknown sources on your Android device if prompted.
3. Install via `adb` or transfer the file to the phone:
   ```bash
   adb install -r app-debug.apk
   ```

---

## 6. Building from Source

### Prerequisites
- JDK 17 or JDK 19
- Android SDK (Platform `android-35`, Build Tools `35.0.0`)
- Gradle 8.10.2 (wrapper included)

### 1. Model Asset Setup
Sub-100 MB models are already included in the repository under `app/src/main/assets/models/`. If building with offline support for Kannada, Tamil, or Odia MMS neural voices, download the corresponding `model.onnx` files as documented in **[docs/MODELS.md](docs/MODELS.md)**. If omitted, the app will automatically fall back to the Android system TTS engine for those three languages.

### 2. Run Test Suite
```bash
./gradlew testDebugUnitTest
```

### 3. Build APK
```bash
./gradlew assembleDebug
```
The resulting APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 7. Two-Phone Field Demo Setup

### Setup Option A: Local Wi-Fi (Recommended)
1. On **Phone A**, enable Wi-Fi Hotspot (Mobile data / internet access is **not** required).
2. Connect **Phone B** to Phone A's hotspot.
3. Launch **iTantra** on both devices.
4. Set Transport to **WI-FI** (port 42888 UDP broadcast).
5. Select a language (e.g., Hindi) on Phone A.
6. Press and hold **HOLD TO TALK** (or tap **DISTRESS**), speak a tactical message, and release.
7. **Observation on Phone B:**
   - The packet is received over the air.
   - The received message appears in the chat transcript.
   - Phone B synthesizes and plays the voice message in the selected language.

### Setup Option B: Bluetooth SPP
1. Pair Phone A and Phone B in Android Bluetooth settings.
2. Launch iTantra, select **BT SPP** transport, and connect to the paired peer.
3. Transmit messages point-to-point.

### Setup Option C: Single-Device Loopback
1. In iTantra settings, select **LOOP** transport.
2. Transmit via PTT or Distress. The app will record, classify, frame, loop back into the receiver pipeline, and speak via the local speaker.

---

## 8. Known Limitations & Constraints

1. **Acoustic Noise:** In heavy background noise, local VAD and acoustic decoding accuracy depend on microphone hardware quality and physical proximity to the speaker.
2. **Wi-Fi / Bluetooth Range:** Without external SDR or dedicated VHF/UHF hardware, communication range is constrained by standard smartphone Wi-Fi and Bluetooth antennas (~10 to 80 meters unobstructed).
3. **On-Device Compute:** Neural inference latency varies by processor. On modern mid-range devices (e.g., Exynos 1480, Snapdragon 778G), Pass-1 transcription occurs in under 200ms; entry-level chipsets may experience longer processing times.
4. **TTS Model Coverage:** High-fidelity VITS voices for Kannada, Tamil, and Odia require downloading external MMS model files (~114 MB each) as outlined in [docs/MODELS.md](docs/MODELS.md).
