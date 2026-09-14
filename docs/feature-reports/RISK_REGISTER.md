# SIH26173 — Project Risk Register

| Risk ID | Description | Severity | Likelihood | Mitigation Strategy | Current Status |
|---|---|---|---|---|---|
| **RSK-01** | Android manufacturers restrict background AudioRecord capture | HIGH | MEDIUM | Keep active Transceiver in foreground service with explicit notification and WAKE_LOCK. | MITIGATED |
| **RSK-02** | Multicast packet filtering on certain Wi-Fi routers/hotspots | MEDIUM | MEDIUM | Acquired Android `WifiManager.MulticastLock` and enabled UDP broadcast fallback (`255.255.255.255`). | MITIGATED |
| **RSK-03** | Memory exhaustion on low-end 2GB RAM Android phones | HIGH | LOW | Enforced strict lazy loading, zero-allocation ring buffers for audio, and avoided resident models for non-active languages. | MITIGATED |
| **RSK-04** | Short Indic text payload expansion when compressed with Deflate | LOW | HIGH | Implemented `AdaptiveCompressor`: evaluates compressed vs raw byte count; discards compression if payload expands. | MITIGATED & VERIFIED |
| **RSK-05** | Missing offline language voice data on unconfigured consumer devices | MEDIUM | MEDIUM | Provided multi-engine fallback: offline system TTS voices + direct mathematical PCM alert tone generator for guaranteed audible alerts. | MITIGATED |
| **RSK-06** | Audio feedback loop in Continuous Conversation mode | MEDIUM | MEDIUM | Transceiver coordinator automatically mutes local VAD processing while receiving or playing remote speech. | MITIGATED |
