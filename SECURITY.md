# Security Policy & Operational Considerations

**Project:** iTantra (Ad Astra)  
**Smart India Hackathon 2026** • **Problem Statement:** SIH26173  

---

## 1. Prototype Scope & Intended Use

iTantra is an engineering prototype and proof-of-concept developed for the Smart India Hackathon 2026. It is designed to demonstrate offline, low-bandwidth neural voice communication over ad-hoc wireless mesh networks (Wi-Fi and Bluetooth) in disaster recovery and tactical search-and-rescue simulations.

---

## 2. Security Architecture & Threat Model

### 2.1 Packet Integrity & Authentication
- **Error Detection:** Every packet contains a 32-bit CRC-32 (IEEE 802.3) checksum to detect transmission noise, bit-flips, and framing truncation.
- **Message Authenticity:** When pre-shared operational keys are configured in Radio Settings, packets append a truncated 12-byte HMAC-SHA256 authentication tag. Packets failing HMAC verification are discarded prior to processing.
- **Anti-Replay / Loop Suppression:** Intermediate and receiving nodes track sequence numbers per `(SourceID, SeqNum)` in an in-memory cache to prevent duplicate processing and broadcast amplification.

### 2.2 Privacy & Local Execution
- **Zero Cloud Data Exfiltration:** All acoustic feature extraction, speech recognition (Whisper INT8), token compression, and neural synthesis (VITS) execute entirely on-device via native ONNX runtime libraries.
- **No Cloud Dependencies:** The application does not require internet connectivity, remote telemetry services, or third-party cloud APIs during operation.

---

## 3. Reporting a Vulnerability

If you discover a potential security concern or vulnerability in this repository, please reach out via GitHub Issues or contact the project maintainers directly with a detailed description of the issue and reproduction steps.
