# iTantra — Current Feature & Functional Inventory

**Baseline Checkpoint:** `v1.2.0-pre-ui-overhaul` (`d5937ad465616733e50bb7faa375b68cce43e96c`)  
**Scope:** Complete functional inventory of Features 1 through 35+, navigation pathways, dependency maps, permission models, and physical validation statuses.

---

## 1. Master Feature Matrix (Features 1–35+)

| Feature # | Feature Name | Primary User Purpose | Entry Point / Navigation Path | Key Source Files | ViewModel & Repositories | Validation Tier | Test Evidence |
|---|---|---|---|---|---|---|---|
| **F01** | **Tactical Chats Home** | View active conversational threads, unread counters, and peer message recency | Bottom Tab 1 (`CHATS`) | `ChatsHomeScreen.kt`, `ChatsHomeViewModel.kt` | `TransceiverViewModel`, `MessageHistoryStore` | `IMPLEMENTED + UNIT TESTED` | 8 tests (`ChatsHomeTest`) |
| **F02** | **Tactical Individual Chat** | Point-to-point text and tactical voice messaging with a specific peer node | Chats Home -> tap peer card | `IndividualChatScreen.kt`, `TranscriptBubble.kt` | `TransceiverViewModel`, `MessageHistoryStore` | `IMPLEMENTED + UNIT TESTED` | 11 tests (`IndividualChatTest`) |
| **F03** | **Tactical Contacts Directory** | Maintain directory of known tactical nodes, callsigns, and cryptographic trust levels | Chats Home -> Contacts icon | `ContactsScreen.kt`, `ContactCard.kt`, `ContactRepository.kt` | `TransceiverViewModel`, `ContactRepository` | `IMPLEMENTED + UNIT TESTED` | 14 tests (`ContactsTest`) |
| **F04** | **Nearby Devices Live Discovery** | Discover proximate iTantra handsets across Wi-Fi multicast and Bluetooth | Contacts Screen -> `NEARBY SCAN` button | `NearbyDevicesScreen.kt`, `NearbyDeviceCard.kt`, `NearbyDeviceRepository.kt` | `TransceiverViewModel`, `NearbyDeviceRepository` | `IMPLEMENTED + UNIT TESTED` | 14 tests (`NearbyDevicesTest`) |
| **F05** | **Offline Global Search** | Search through cached messages, contacts, and raw packet metadata completely offline | Chats Home -> Search icon | `GlobalSearchScreen.kt`, `SearchResultCard.kt`, `SearchRepository.kt` | `TransceiverViewModel`, `SearchRepository` | `IMPLEMENTED + UNIT TESTED` | 12 tests (`GlobalSearchTest`) |
| **F06** | **Radio Message States** | Track transmission lifecycle: SENT, RELAYED, DELIVERED, FAILED, ACKED | Live traffic bubbles in Radio & Individual Chat | `RadioMessageState.kt`, `RadioMessageStateIndicator.kt` | `TransceiverViewModel`, `MessageHistoryStore` | `IMPLEMENTED + UNIT TESTED` | 15 tests (`RadioMessageStateTest`) |
| **F07** | **Message Technical Inspector** | Dissect wire frames: View hex payload, header flags, HMAC signature, CRC32, latency | Chat Screen -> tap message card | `MessageTechnicalInspectorCard.kt`, `MessageTechnicalSection.kt` | `TransceiverViewModel` | `IMPLEMENTED + UNIT TESTED` | 16 tests (`MessageTechnicalInspectorTest`) |
| **F08** | **Mesh Topology Canvas** | Visualize ad-hoc network graph, active nodes, link RSSI, and multi-hop paths | Diagnostics -> `COMMUNICATION HEALTH` -> `VIEW MESH TOPOLOGY` | `MeshTopologyScreen.kt`, `MeshTopologyCanvas.kt` | `TransceiverViewModel`, `DiagnosticsRepository` | `IMPLEMENTED + UNIT TESTED` | 18 tests (`MeshTopologyTest`) |
| **F09** | **Message Journey Trace** | Trace packet journey across network hops from origin to intermediate relays to destination | Technical Inspector -> `VIEW JOURNEY →` button | `MessageJourneyScreen.kt`, `MessageJourneyTimeline.kt`, `MessageJourneyMapper.kt` | `TransceiverViewModel`, `MessageHistoryStore` | `IMPLEMENTED + UNIT TESTED` | 14 tests (`MessageJourneyTest`) |
| **F10** | **Offline QR Node Pairing** | Cryptographically pair two offline handsets via optical camera QR scanning | Contacts Screen -> `QR PAIRING` button | `QrPairingScreen.kt`, `ScanNodeQrScreen.kt`, `MyNodeQrScreen.kt`, `QrScannerView.kt` | `TransceiverViewModel`, `ContactRepository` | `IMPLEMENTED + PHYSICAL VALIDATION` | 7 tests (`QrPairingValidatorTest`, `QrCameraPermissionStateTest`); Physically verified on Phone A & B |
| **F11** | **Multilingual Playback & TTS** | On-device neural acoustic text-to-speech synthesis across 10 Indic languages | Automatic upon receiving tactical text or PTT transcript | `PiperVitsSpeechSynthesizer.kt`, `TtsVoiceResolver.kt`, `AudioTrackPlayer.kt` | `TransceiverViewModel` | `IMPLEMENTED + UNIT TESTED` | 12 tests (`TtsLanguageRoutingTest`, `TtsVoiceResolverTest`); Audibly verified on hardware |
| **F12** | **Emergency Distress SOS** | Broadcast high-priority emergency distress beacon with GPS coordinates and siren | Radio Screen -> `SEND DISTRESS` button | `EmergencyComposer.kt`, `EmergencyConfirmationDialog.kt`, `EmergencyBanner.kt` | `TransceiverViewModel`, `LocationProviderHelper` | `IMPLEMENTED + UNIT TESTED` | 18 tests (`EmergencyDistressPacketTest`); Verified in live radio stream |
| **F13** | **Communication Health Panel** | Truthful real-time network telemetry: Health Score, Delivery Rate, Loss %, Transport Bandwidth | Diagnostics -> `COMMUNICATION HEALTH →` | `CommunicationHealthScreen.kt`, `OverallHealthBanner.kt`, `CommunicationHealthMapper.kt` | `TransceiverViewModel`, `DiagnosticsRepository` | `IMPLEMENTED + UNIT TESTED` | 15 tests (`CommunicationHealthMapperTest`) |
| **F14** | **Adaptive Network-State UI** | Dynamic status pills and banners responding to network quality: CONNECTED, DEGRADED, OFFLINE | Global top status header across all screens | `AdaptiveNetworkUiMapper.kt`, `DiscoveryStatusBanner.kt` | `TransceiverViewModel` | `IMPLEMENTED + UNIT TESTED` | 16 tests (`AdaptiveNetworkUiMapperTest`) |
| **F15** | **10-Minute Message Retention** | Automatic tactical security scrubbing of ephemeral radio messages after 10 minutes | Background lifecycle worker | `MessageRetentionManager.kt`, `MessageRecord.kt` | `TransceiverViewModel`, `MessageHistoryStore` | `IMPLEMENTED + UNIT TESTED` | 14 tests (`MessageRetentionTest`) |
| **F16A** | **Two-Pass Streaming STT** | Low-latency speech recognition streaming first-pass tokens and finalizing second pass | PTT button hold and speak | `TwoPassSpeechPipeline.kt`, `SherpaOnnxSpeechRecognizer.kt` | `TransceiverViewModel` | `IMPLEMENTED + SYNTHETIC VALIDATION` | 12 tests (`TwoPassSpeechPipelineTest`); Benchmarked on ARM64 |
| **F16B** | **Adaptive Two-Pass VBR** | Variable bitrate encoding degrading representation progressively under channel congestion | Dynamic during packet transmission | `AdaptiveTwoPassVbrEngine.kt` | `TransceiverViewModel` | `IMPLEMENTED + UNIT TESTED` | 14 tests (`AdaptiveTwoPassVbrTest`) |
| **F17** | **Targeted Speech Refinement** | Refines ambiguous or low-confidence acoustic segments during pauses in speech | Automatic during speech transcription | `TargetedRefinementEngine.kt` | `TransceiverViewModel` | `IMPLEMENTED + UNIT TESTED` | 12 tests (`TargetedRefinementTest`) |
| **F18** | **Semantic Base + Enhancement** | Encodes standard military/disaster phrases into ultra-compact 8-byte semantic base tokens | Automatic when transcript matches tactical dictionary | `SemanticBaseEnhancementEngine.kt`, `SemanticBase.kt` | `TransceiverViewModel` | `IMPLEMENTED + UNIT TESTED` | 18 tests (`SemanticBaseEnhancementTest`) |
| **F19** | **Shared Context & Confidence Engine**| Decentralized shared situational awareness cache with confidence scoring (0-100) | Automatic on receiving contextual updates | `SharedContextStore.kt`, `ContextDelta.kt` | `TransceiverViewModel`, `SharedContextStore` | `IMPLEMENTED + UNIT TESTED` | 22 tests (`SharedContextTest`) |
| **F20** | **Context-Aware Multi-Hop Relay** | Application-layer store-and-forward routing with epidemic flooding and duplicate suppression | Core mesh forwarding in `ManetNodeService` | `ContextAwareRelayRouter.kt`, `DtnStore.kt` | `ManetCoordinator` | `IMPLEMENTED + SYNTHETIC VALIDATION` | 47 tests (`ContextAwareRelayTest`, `ManetRouterTest`) |
| **F21** | **10-Language Speech Benchmark** | Standardized WER/CER evaluation across 250 utterances in 10 Indic languages | Automated benchmark runner script | `TenLanguageSpeechBenchmarkTest.kt`, `scripts/run_feature21_benchmark.py` | Benchmark harness | `IMPLEMENTED + SYNTHETIC VALIDATION` | 28 tests (`TenLanguageSpeechBenchmarkTest`) |
| **F22** | **Network Impairment Benchmark** | 60-scenario simulation injecting 10k-1M bandwidth, 5-50% loss, 50-500ms jitter | Automated benchmark runner script | `NetworkResilienceTest.kt`, `scripts/run_feature22_benchmark.py` | Benchmark harness | `IMPLEMENTED + SYNTHETIC VALIDATION` | 16 tests (`NetworkResilienceTest`) |
| **F23** | **Security Audit & Fuzzing** | Adversarial negative testing: HMAC verification, CRC corruption, 500+ malformed fuzzing buffers | Security test suite | `SecurityAuditTest.kt`, `PacketFuzzSafetyTest.kt` | Security package | `IMPLEMENTED + AUTOMATED ONLY` | 62 tests (`SecurityAuditTest`) |
| **F24** | **Tactical Edge Resource Profiling**| On-device CPU, RAM RSS, battery current, and thermal profiling across 44 phases | Automated profile runner script | `ResourceBenchmarkTest.kt`, `scripts/run_feature24_benchmark.py` | Profiling harness | `IMPLEMENTED + PHYSICAL VALIDATION` | 16 tests; 44 runs on Phone A & B (`feature24_resource_results.json`) |
| **F25** | **Physical Dual-Handset Validation**| End-to-end physical radio verification: Wi-Fi UDP & Bluetooth SPP audio exchange | Dual handsets (Galaxy A55 & Note 10 Lite) | `Wave1IntegrationTest.kt`, `ad-astra-sih-2026-demo.mp4` | Production app | `IMPLEMENTED + PHYSICAL VALIDATION` | Dual handset physical link verified over phone hotspot & Bluetooth |
| **F26** | **Release Hardening & CI** | Production build stabilization, R8 shrinking, signing, and GitHub Actions CI workflow | `.github/workflows/android.yml`, `build.gradle.kts` | Build toolchain | Production release APK | `IMPLEMENTED + AUTOMATED ONLY` | Release APK compiled (`app-release.apk`, ~929.8 MB) |
| **F27** | **Final SIH Documentation** | Truthful evidence consolidation, claim audit, and architectural reports | `docs/`, `FINAL_FEATURE_STATUS.md`, `CLAIM_AUDIT.md` | Documentation suite | Root docs | `DOCUMENTATION ONLY` | Full repository audit & evidence matrix |
| **F28** | **Routerless Wi-Fi Direct P2P** | Direct phone-to-phone high-throughput transport without router, hotspot, or internet | Settings -> Active Transports -> Wi-Fi Direct P2P | `WifiDirectTransport.kt`, `WifiDirectManager.kt` | `TransceiverViewModel`, `TransportManager` | `IMPLEMENTED + PHYSICAL VALIDATION` | 14 tests (`WifiDirectTransportTest`); Physically validated on Phone A & B on port 42889 |
| **F29** | **End-to-End Multi-Hop & Failover**| Synthetic 3-node/4-node relay validation + physical dual-handset transport failover & DTN | Core routing & physical handsets | `Feature29MultiHopRelayTest.kt`, `TransportFailoverDtnTest.kt` | `ContextAwareRelayRouter`, `DtnStore` | `SYNTHETIC (Multi-Hop) / PHYSICAL (Failover)` | 15 tests (`Feature29MultiHopRelayTest`), 8 tests (`TransportFailoverDtnTest`), 90-scenario benchmark |
| **F33** | **Reliability & Soak Testing** | Multi-hour stability testing: memory leak detection, socket churn, buffer stress | Automated soak test runner script | `scripts/run_feature33_soak.py`, `feature33_soak_results.json` | Test harness | `IMPLEMENTED + SYNTHETIC VALIDATION` | 100 iterations, 0 crashes, 0 memory leaks (`FEATURE33_RELIABILITY_SOAK_REPORT.md`) |
| **F35** | **Multilingual Tactical Quality** | Indic tactical domain token reranker and optimized inference threading | PTT Speech Pipeline | `TacticalDomainReranker.kt`, `SherpaOnnxSpeechRecognizer.kt` | `TransceiverViewModel` | `IMPLEMENTED + SYNTHETIC VALIDATION` | Benchmarked across 10 Indic languages (`FEATURE35_STT_QUALITY_REPORT.md`) |

---

## 2. User Navigation Flow Maps

### 2.1 Flow A: Primary Radio & Emergency Distress
```
MainTransceiverScreen (Tab 0)
  ├── Press PTT (Hold to Record -> VAD -> STT -> Representation -> Radio Broadcast)
  ├── Tap Language Pill -> Language Selector Dropdown (10 Indic Languages)
  ├── Tap Settings Gear -> Field Radio Settings (Tab 3)
  └── Tap SEND DISTRESS
        │
        ▼
      EmergencyComposer (Select Category: AMBUSH / MEDICAL / FIRE / FLOOD)
        │
        ▼
      EmergencyConfirmationDialog (Modal Red Alert: "BROADCAST EMERGENCY DISTRESS?")
        │
        ▼
      Emergency Active State (SOS beaconing, audio siren, red banner across app)
        │
        ▼
      Tap Distress GPS Coordinates -> Intent -> External Maps / Offline Navigation
```

### 2.2 Flow B: Tactical Chats, Inspector & Journey
```
ChatsHomeScreen (Tab 1)
  ├── Search Icon -> GlobalSearchScreen
  ├── Contacts Icon -> ContactsScreen
  ├── Nearby Icon -> NearbyDevicesScreen
  └── Tap Conversation Card
        │
        ▼
      IndividualChatScreen
        ├── PTT Mic Button (Hold to send voice to specific peer)
        ├── Text Input Field + Send Button
        └── Tap Message Bubble / Card
              │
              ▼
            MessageTechnicalInspector (Hex Payload, HMAC, CRC32, TTL, Latency)
              │
              ▼
            MessageJourneyScreen (Hop-by-hop node trace, link delay, RSSI)
```

### 2.3 Flow C: Tactical Contacts, Nearby Discovery & QR Pairing
```
ContactsScreen
  ├── Quick Action: + ADD CONTACT -> AddContactDialog (Node ID, Callsign, Trust Level)
  ├── Quick Action: NEARBY SCAN -> NearbyDevicesScreen (Tactical Radar, RSSI bars)
  └── Quick Action: QR PAIRING
        │
        ▼
      QrPairingScreen (Tab Container)
        ├── Tab "MY CODE": High-density tactical QR display + Node credentials
        └── Tab "SCAN": CameraX optical viewfinder -> QR detected -> Contact added
```

### 2.4 Flow D: Diagnostics, Communication Health & Mesh Topology
```
DiagnosticsScreen (Tab 2)
  ├── Packet Counters, Transport Links, DTN Queue Depth
  ├── Button: MODEL STATUS → -> ModelStatusScreen (Whisper-Tiny, 10 Indic TTS voices)
  └── Button: COMMUNICATION HEALTH →
        │
        ▼
      CommunicationHealthScreen (Health Score %, Delivery %, Active Radios)
        │
        ▼
      Button: VIEW MESH TOPOLOGY →
        │
        ▼
      MeshTopologyScreen (Interactive 2D graph canvas, node dragging, link status)
```

---

## 3. Hidden & Debug-Only Navigation Paths

1. **Standalone Test Activities (Declared strictly in `app/src/debug/AndroidManifest.xml`):**
   - `ContactsActivity` (`org.sih.itantra.presentation.ContactsActivity`)
   - `NearbyDevicesActivity` (`org.sih.itantra.presentation.NearbyDevicesActivity`)
   - `GlobalSearchActivity` (`org.sih.itantra.presentation.GlobalSearchActivity`)
   - `MessageJourneyActivity` (`org.sih.itantra.presentation.MessageJourneyActivity`)
   - `MeshTopologyActivity` (`org.sih.itantra.presentation.MeshTopologyActivity`)
   - `QrPairingActivity` (`org.sih.itantra.presentation.QrPairingActivity`)
   *Note:* These activities exist to facilitate isolated automated instrumented tests and are excluded from release builds.
2. **Debug Test Intents in `MainActivity.kt`:**
   - Intent action `send_test_packet`: Broadcasts a synthetic test packet to port 42888/42889.
   - Intent action `set_language`: Forcibly sets language from ADB without touching UI.
   - Intent action `open_chat`: Directly deep-links to a specific peer conversation overlay.
   *Note:* Gated behind `if (BuildConfig.DEBUG)` to prevent external intent spoofing in production release builds.
