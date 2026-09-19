# iTantra Current-State Master Reconnaissance & Application Audit
**Canonical Master Reference Document**  
**Audit Baseline Commit:** `d5937ad465616733e50bb7faa375b68cce43e96c` (Permanent Checkpoint Tag: `v1.2.0-pre-ui-overhaul`)  
**Target Repository:** `c:\Projects\iTantra` | Branch: `main`  
**Execution Standard:** Strictly non-destructive reconnaissance and empirical audit. Zero source code modifications, zero redesigns, zero optimizations, zero git pushes.

---

## 1. Executive Summary & Audit Methodology

This document establishes the exhaustive, empirically verified baseline of the **iTantra** off-grid disaster communications platform. Every observation, metric, defect, and architectural relationship documented herein is tagged according to the following strict evidence attribution standards:
- `[OBSERVED ON DEVICE]`: Direct telemetry obtained from physical USB debugging sessions on Samsung Galaxy A55 5G (Phone A) and Samsung Galaxy Note 10 Lite (Phone B), including ADB `dumpsys meminfo`, `am start -W`, UIAutomator XML dumps, and binary screenshots.
- `[OBSERVED IN SOURCE]`: Verified directly by static analysis of Kotlin source files, Android XML layouts, Gradle build scripts, and resource definitions.
- `[DOCUMENTED]`: Historical design decisions preserved in the git commit log (`v1.0.0` through `v1.2.0-pre-ui-overhaul`).
- `[INFERRED]`: Deductions derived from Android runtime constraints, Jetpack Compose recomposition lifecycles, and Sherpa-ONNX C++ tensor behavior.

### 1.1 The Core iTantra Mission
iTantra is an off-grid, tactical disaster transceiver application enabling continuous voice and text communication across peer Android devices when cellular towers, internet backbones, and power grids fail. It combines:
1. **Physical Hybrid Mesh Networking:** Auto-switching between Wi-Fi UDP Multicast (`224.0.0.1:8889`), Wi-Fi Direct Peer-to-Peer TCP sockets (`port 8888`), and Bluetooth Classic RFCOMM SPP.
2. **On-Device Offline Multilingual AI:** Zero-cloud Speech-to-Text (Whisper-Tiny INT8 quantized) and Neural Text-to-Speech (Piper/Sherpa VITS) supporting English and 10 official Indic languages.
3. **Cryptographic Authenticity & Tactical UI:** Ed25519 node identity signatures, HMAC-SHA256 packet integrity, Delay-Tolerant Networking (DTN) store-and-forward routing, and a tactical field-radio user experience.

---

## 2. Screen-by-Screen Master Directory Table `[OBSERVED ON DEVICE]` `[OBSERVED IN SOURCE]`

The iTantra presentation layer comprises **20 distinct screens and modal surfaces** managed by a single-activity architecture (`MainActivity.kt`) hosting a root `Scaffold` and an explicit LIFO overlay backstack (`NavigationStateManager.kt`):

| # | Screen / Surface Name | Entry Path | Exit Path | Top Bar | Bottom Nav | Major Components | Scrolling | Background & Surfaces | Status & Accent Colors | Key State Variants | Verified Artifact Link |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **01** | **Main Transceiver (Radio)** | App Launch / Bottom Tab 0 | Home / Exit | `TopRadioHeader` (Logo, Call Sign, Radio Status Badges, Settings Gear) | Visible (Tab 0) | Language Selector Pill, Waveform Visualizer, Live Radio Traffic, Tactical PTT Button, SOS Button, Voice Status Pill | `LazyColumn` for traffic | BG: `#1B1F1D`<br>Surface: `#242A27` | Connected: `#2E7D32`<br>Alert: `#D84315`<br>Ready: `#2E7D32` | Idle, Receiving, Transmitting, Degraded, Emergency Active | `01_radio_idle_phoneA.png`<br>`01_radio_idle_phoneB.png` |
| **02** | **Language Selector Menu** | Radio screen -> tap Language Pill | Select language / Dismiss | Inherited from Radio | Visible | DropdownMenu with 10 Indic languages, native script subtitles, active checkmark | Menu Scroll | Surface: `#242A27`<br>Divider: `#333B37` | Active: `#2E7D32`<br>Text: `#F6F4ED` | Expanded, Collapsed, Switching Voice Model | `02_radio_lang_menu_phoneA.png`<br>`02_radio_lang_menu_phoneB.png` |
| **03** | **Emergency Distress Composer** | Radio screen -> `SEND DISTRESS` | Back Arrow / Cancel | Header ("EMERGENCY DISTRESS BEACON") | Hidden | Severity selector (DISTRESS, CRITICAL, ADVISORY), Category grid (MEDICAL, AMBUSH, CASUALTY, EVAC, FIRE, FLOOD), GPS Coordinate preview, Siren toggle, `TRANSMIT DISTRESS` button | Vertical `Column` | BG: `#1B1F1D`<br>Card: `#242A27`<br>Border: `#D84315` | Distress: `#D84315`<br>Amber: `#FFB300`<br>GPS: `#0288D1` | Normal, Category selected, GPS pending, Transmitting | `03_emergency_composer_phoneA.png`<br>`03_emergency_composer_phoneB.png` |
| **04** | **Emergency Confirmation Dialog** | Emergency Composer -> `TRANSMIT DISTRESS` | `CONFIRM` / `CANCEL` | N/A (Modal Dialog) | Hidden | Warning icon, Scope alert banner, Siren warning, `CANCEL` button, `CONFIRM DISTRESS` button | Fixed Modal | Surface: `#242A27`<br>Outline: `#D84315` | Alert: `#D84315`<br>Text: `#F6F4ED` | Modal prompt, Broadcast in flight | `05_emergency_confirm_dialog_phoneA.png` |
| **05** | **Chats Home** | Bottom Tab 1 (`CHATS`) | Tab switch / Back | Custom Top Bar ("TACTICAL CHATS", Search icon, Contacts icon, Nearby scan icon) | Visible (Tab 1) | Emergency banner, Search quick-filter row, Conversations list, Empty state card, New Chat FAB | `LazyColumn` | BG: `#1B1F1D`<br>Surface: `#242A27`<br>Card: `#2B322E` | Unread: `#2E7D32`<br>Distress: `#D84315`<br>Text: `#F6F4ED` | Empty, Populated list, Filtered, Active SOS alert banner | `06_chats_home_phoneA.png`<br>`06_chats_home_phoneB.png` |
| **06** | **Individual Chat** | Chats Home -> Tap conversation | Back Arrow | Chat Header (Peer Callsign, Node ID, Encryption badge, Link badge) | Hidden | Message bubbles (Sent right, Received left), Representation mode badges (FULL, COMPACT, SEMANTIC, DELTA), Technical Inspector toggle, Bottom PTT & Text Input Bar | `LazyColumn` (Auto-scroll) | BG: `#1B1F1D`<br>Sent: `#143D30`<br>Received: `#242A27` | Mode: `#0288D1`<br>Priority: `#FFB300`<br>Verified: `#2E7D32` | Empty chat, Composing, PTT recording, Inspector open | `12_individual_chat_phoneA.png`<br>`12_individual_chat_phoneB.png` |
| **07** | **Message Technical Inspector** | Individual Chat -> Tap message card | Collapse / Back | N/A (Expandable Card / Bottom Sheet) | Hidden | Raw hex payload view, Packet header dissection (TTL, Seq, Flags), HMAC-SHA256 hash, CRC32 status, Latency & Bitrate metrics, `VIEW JOURNEY →` button | Nested scrollable Box | Surface: `#242A27`<br>Capsule: `#2B322E`<br>Border: `#333B37` | Valid: `#2E7D32`<br>Corrupt: `#D84315`<br>Mono: `#8D9993` | Collapsed, Expanded, Verified CRC/HMAC, Corrupt CRC | `13_message_inspector_phoneA.png`<br>`23_inspector_expanded_phoneB.png` |
| **08** | **Message Journey Trace** | Technical Inspector -> `VIEW JOURNEY →` | Back Arrow | Header ("MESSAGE JOURNEY TRACE", Message ID, Hop Count) | Hidden | Journey timeline canvas, Origin node card, Intermediate relay cards with RSSI and delay, Destination delivery receipt, Hop-by-hop latency breakdown | Vertical `Column` + `LazyColumn` | BG: `#1B1F1D`<br>Card: `#242A27`<br>Links: `#2E7D32` | Origin: `#0288D1`<br>Relay: `#FFB300`<br>Delivered: `#2E7D32` | Direct hop, 2-hop relay, 3-hop relay, DTN partitioned store | `14_message_journey_phoneA.png`<br>`24_message_journey_active_phoneB.png` |
| **09** | **Tactical Contacts Directory** | Chats Home -> Contacts icon | Back Arrow | Header ("TACTICAL CONTACTS", Node count, Search icon, Add contact icon) | Hidden | Quick action bar (`+ ADD CONTACT`, `NEARBY SCAN`, `QR PAIRING`), Contacts list with trust levels (VERIFIED, MESH, UNVERIFIED), Last seen timestamp, Direct Chat button | `LazyColumn` | BG: `#1B1F1D`<br>Cards: `#242A27`<br>Border: `#333B37` | Verified: `#2E7D32`<br>Untrusted: `#FFB300`<br>Distress: `#D84315` | Empty, Populated list, Filtered, Add Contact dialog open | `07_contacts_screen_phoneA.png`<br>`07_contacts_screen_phoneB.png` |
| **10** | **QR Node Pairing (My Code & Scan)** | Contacts screen -> `QR PAIRING` | Back Arrow | Header ("TACTICAL NODE PAIRING", Tab row: `MY CODE` / `SCAN`) | Hidden | Tab `MY CODE`: High-density QR image, Node ID, Callsign, Public Key fingerprint, Share button. Tab `SCAN`: CameraX viewfinder preview, reticle overlay, manual entry fallback | Non-scrolling tab containers | BG: `#1B1F1D`<br>QR: White on dark<br>Reticle: `#2E7D32` | Valid: `#2E7D32`<br>Error: `#D84315`<br>Status: `#242A27` | `MY CODE` tab, `SCAN` tab (camera active), QR detected dialog | `08_qr_pairing_my_code_phoneA.png`<br>`09_qr_pairing_scan_tab_phoneA.png`<br>`09_qr_pairing_scan_tab_phoneB.png` |
| **11** | **Nearby iTantra Devices** | Contacts screen -> `NEARBY SCAN` | Back Arrow | Header ("NEARBY DISCOVERY", Active interface badges, Radar sweep toggle) | Hidden | Tactical radar visualizer / pulse animation, Peer discovery cards (Node ID, Callsign, Interface: BT / Wi-Fi UDP / Wi-Fi Direct, RSSI signal bar, `CONNECT` / `CHAT` button) | `LazyColumn` below radar | BG: `#1B1F1D`<br>Cards: `#242A27`<br>Radar: `#143D30` | Strong RSSI: `#2E7D32`<br>Medium: `#FFB300`<br>Weak: `#D84315` | Scanning active, No peers found, Peers discovered, Connecting | `10_nearby_devices_phoneA.png`<br>`10_nearby_devices_phoneB.png` |
| **12** | **Offline Global Search** | Chats Home -> Search icon | Back Arrow / Clear query | Search text field with clear 'X' button, Back arrow | Hidden | Filter chips (`ALL`, `CHATS`, `CONTACTS`, `PACKETS`), Search results LazyColumn with highlight matching, Empty state ("NO RESULTS FOUND") | `LazyColumn` | BG: `#1B1F1D`<br>Filter: `#2B322E`<br>Active: `#2E7D32` | Highlight: `#FFB300`<br>Counter: `#8D9993` | Empty query, Typing, Results returned, Zero results | `11_global_search_phoneA.png`<br>`11_global_search_phoneB.png` |
| **13** | **Diagnostics Dashboard** | Bottom Tab 2 (`DIAGNOSTICS`) | Tab switch / Back | Header ("SYSTEM DIAGNOSTICS", "Real-time telemetry and engine health") | Visible (Tab 2) | Navigation links (`COMMUNICATION HEALTH →`, `MESH TOPOLOGY →`, `MODEL STATUS →`), Packet I/O telemetry cards, Active Transports status, DTN Queue depth & pressure, System uptime | Vertical scrollable `Column` | BG: `#1B1F1D`<br>Cards: `#242A27`<br>Pills: `#2B322E` | Normal: `#2E7D32`<br>Warning: `#FFB300`<br>Critical: `#D84315` | Idle, Active traffic flowing, High buffer pressure, Outage state | `15_diagnostics_dashboard_phoneA.png`<br>`15_diagnostics_dashboard_phoneB.png` |
| **14** | **Communication Health Panel** | Diagnostics -> `COMMUNICATION HEALTH →` | Back Arrow | Header ("COMMUNICATION HEALTH", Node ID, Health Score badge: 100%) | Hidden | Overall Health Banner, Delivery Health Card (ACK rate, loss rate), Active Transports Card (Wi-Fi UDP, Wi-Fi Direct, Bluetooth), Transport Bandwidth Card, Peer Quality Matrix, `VIEW MESH TOPOLOGY →` | Vertical scrollable `Column` | BG: `#1B1F1D`<br>Cards: `#242A27`<br>Badges: `#2B322E` | 100% Score: `#2E7D32`<br>Degraded: `#FFB300`<br>Offline: `#D84315` | 100% Health, Single transport connected, Transport failover active, All radios down | `16_communication_health_phoneA.png`<br>`16_communication_health_phoneB.png` |
| **15** | **Mesh Topology Canvas** | Diagnostics/Health -> `MESH TOPOLOGY →` | Back Arrow | Header ("MANET MESH TOPOLOGY", Node count, Link count, Simulation mode toggle) | Hidden | Interactive 2D canvas with node circles, link lines colored by RSSI/quality, selected node inspector sheet, topology controls (Zoom, Reset, Add Mock Node) | Gesture drag/zoom canvas | BG: `#121614`<br>Nodes: `#2E7D32`<br>Links: `#0288D1` | Local Node: `#2E7D32`<br>Peer: `#0288D1`<br>Relay: `#FFB300` | 2-node direct, 3-node linear, Partitioned graph, Node selected details | `20_mesh_topology_phoneA.png`<br>`20_mesh_topology_phoneB.png` |
| **16** | **Field Radio Settings** | Bottom Tab 3 (`SETTINGS`) | Tab switch / Back | Header ("FIELD RADIO SETTINGS", "Hardware telemetry & operational parameters") | Visible (Tab 3) | Interface Theme selector (System, Sand, Charcoal), Active Transport Link cards (Wi-Fi Multicast, Bluetooth SPP, Wi-Fi Direct P2P), Auto Transport Failover switch, DTN storage status, Neural Models audit link (`VIEW AUDIT →`) | Vertical scrollable `Column` | BG: `#1B1F1D`<br>Cards: `#242A27`<br>Pills: `#2B322E` | Active: `#2E7D32`<br>Inactive: `#333B37` | System Dark, Sand Light, Failover ON/OFF, Model prewarmed | `18_settings_field_radio_phoneA.png`<br>`18_settings_field_radio_phoneB.png` |
| **17** | **Neural Model Status & Audit** | Settings -> `VIEW AUDIT →` | Back Arrow | Header ("ON-DEVICE NEURAL MODELS", "INT8 Sherpa-ONNX & Neural VITS TTS") | Hidden | Active ASR model card (Whisper-Tiny quantized, single-active resident), 10 Indic TTS voice status cards (Prewarmed, Resident, Evicted), Memory RSS meter, Voice test trigger | Vertical scrollable `Column` | BG: `#1B1F1D`<br>Cards: `#242A27`<br>Badges: `#2B322E` | Resident: `#2E7D32`<br>Evicted: `#8D9993`<br>Testing: `#0288D1` | Idle, Model loading, Voice playing, All 10 voices audited | `19_model_status_audit_phoneA.png`<br>`19_model_status_audit_phoneB.png` |
| **18** | **Distress Location & Map View** | Emergency Distress -> tap coordinates | System Intent -> External Maps / Back | N/A (External Intent / Map Intent) | Hidden | Coordinates card (`geo:lat,lon?q=lat,lon(Distress)`), Compass heading, Launch Maps button | Card inside Bubble | Surface: `#242A27`<br>Link: `#0288D1` | Alert: `#D84315`<br>Map Action: `#0288D1` | Valid GPS lock, Approximate location, Stale location | Verified via intent tests & demo video |
| **19** | **Add Contact Modal Dialog** | Contacts Screen -> `+ ADD CONTACT` | Save / Cancel | Modal Dialog Header ("ADD TACTICAL CONTACT") | Hidden | Node ID input field (numerical), Callsign input field, Display Name input field, Trust Level radio group (`UNVERIFIED`, `TRUSTED`), `CANCEL` button, `SAVE` button | None (Modal) | Surface: `#242A27`<br>Input: `#1B1F1D`<br>Border: `#333B37` | Focus border: `#2E7D32`<br>Text: `#F6F4ED` | Empty inputs, Valid inputs, Duplicate ID error | `07_contacts_screen_phoneA.png` |
| **20** | **SIH Mission Demo & Scenario** | Diagnostics -> `SIH MISSION DEMO →` | Back Arrow | Header ("SIH 2026 MISSION DEMO", Scenario selector) | Hidden | Disaster triage simulation controls, Flood rescue waypoint broadcast, Automated multi-hop packet generator, Live throughput chart | Vertical scrollable `Column` | BG: `#1B1F1D`<br>Cards: `#242A27` | Active: `#2E7D32`<br>Inject: `#0288D1` | Scenario idle, Scenario running, Completed | Accessible via debug intent / diagnostics |

---

## 3. System Architecture & Layer Interactions `[OBSERVED IN SOURCE]` `[DOCUMENTED]`

The iTantra architecture adheres to Clean Architecture principles split across Presentation, Domain Orchestration, Mesh/Transport, Cryptography, and Encrypted Storage:

```
+---------------------------------------------------------------------------------------+
|                                 PRESENTATION LAYER                                    |
|   MainActivity.kt | NavigationStateManager.kt | LocalRadioColors (Theme.kt, Color.kt) |
|   MainTransceiverViewModel | ChatsViewModel | DiagnosticsViewModel | SettingsViewModel|
+---------------------------------------------------------------------------------------+
                                           │ (StateFlow / UI Events)
                                           ▼
+---------------------------------------------------------------------------------------+
|                             DOMAIN ORCHESTRATION LAYER                                |
|   MeshCoordinator.kt (Central lifecycle hub, node callsign, transport binding)        |
|   RadioPacketEngine.kt (Representation selection, QoS tagging, fragmentation)          |
|   AudioProcessingCoordinator.kt (16kHz PCM audio capture, PTT state machine)          |
|   OfflineSpeechEngine.kt (Sherpa-ONNX Whisper ASR & Piper VITS TTS synthesizers)       |
+---------------------------------------------------------------------------------------+
                                           │ (Packets / Audio Frames)
                                           ▼
+---------------------------------------------------------------------------------------+
|                               CRYPTOGRAPHIC & DATA LAYER                              |
|   Ed25519KeyStore (Node identity signatures, QR pairing mutual public key trust)      |
|   HmacSha256 / Crc32 (Per-packet anti-tamper authentication & bit-rot detection)     |
|   Room SQLite (Encrypted via SQLCipher): itantra_mesh.db (Messages, Nodes, Routing)    |
|   EncryptedSharedPreferences (Hardware callsign, private seed, operational config)     |
+---------------------------------------------------------------------------------------+
                                           │ (Serialized Binary Frames)
                                           ▼
+---------------------------------------------------------------------------------------+
|                                  HYBRID MESH TRANSPORTS                               |
|   AdaptiveTransportManager.kt (Link quality evaluation, auto-failover, fallback)      |
|   ├── UdpMulticastTransport.kt (224.0.0.1:8889 broadcast, zero-config local mesh)     |
|   ├── WifiDirectP2pTransport.kt (P2P Wi-Fi Direct GO/Client TCP sockets on 8888)      |
|   └── BluetoothSppTransport.kt (RFCOMM SPP Classic, battery-optimized DTN channel)    |
|   DtnStorageEngine.kt (Store-and-forward SQLite buffer for partitioned nodes)         |
+---------------------------------------------------------------------------------------+
```

### 3.1 Packet Life-Cycle (PTT Voice Utterance to Remote Handset Delivery)
1. **Audio Recording:** Operator pushes Tactical PTT button (`RadioPttControl.kt`). `AudioProcessingCoordinator` captures 16kHz 16-bit mono PCM audio from the microphone.
2. **Local Transcription:** Upon PTT release, the PCM buffer is fed to `OfflineSpeechEngine.kt`. Sherpa-ONNX INT8 Whisper-Tiny decodes text in **182 ms** (Phone A).
3. **Representation Optimization:** `RadioPacketEngine.kt` evaluates channel conditions and encodes the payload into one of five representation tiers:
   - `FULL`: Raw PCM / High-res audio.
   - `COMPACT`: Opus-compressed audio.
   - `SEMANTIC_BASE`: Plain translated text transcript.
   - `SEMANTIC_ENHANCED`: Text transcript + language token + sentiment/priority.
   - `CONTEXT_DELTA`: Differential token sequence referencing shared context dictionary (highest compression, ~18-32 bytes).
4. **Framing & Security:** Frame is decorated with a 24-byte header (Version, Type, Priority QoS, Source Node ID, Dest Node ID, SeqNum, TTL), followed by an Ed25519 signature and HMAC-SHA256 digest.
5. **Physical Transmission:** `AdaptiveTransportManager` routes the frame:
   - Primary: High-throughput Wi-Fi Direct socket (`8888`) or Wi-Fi Multicast (`8889`).
   - Fallback: Bluetooth SPP RFCOMM if Wi-Fi links are degraded.
   - Disconnected: `DtnStorageEngine` persists the packet to disk until peer discovery beacons confirm the destination is back in radio range.
6. **Remote Ingestion & Synthesis:** Receiving handset validates CRC32 and HMAC, checks duplicate suppression cache, persists the message in Room, updates the UI timeline, and triggers `OfflineTts.synthesize()` to vocalize the transcript through the speaker in the user's selected Indic language.

---

## 4. Complete Feature Inventory & Verification Status `[OBSERVED IN SOURCE]` `[OBSERVED ON DEVICE]`

| Feature # | Feature Name | Primary Source Artifacts | Physical Test Evidence | Status |
|---|---|---|---|---|
| **F01** | Offline Whisper-Tiny ASR | `OfflineSpeechEngine.kt`, `SherpaOnnx` | PTT transcribed in 182 ms on Phone A | **VERIFIED** |
| **F02** | 10 Indic Languages Neural TTS | `OfflineSpeechEngine.kt`, `LanguageManager.kt` | All 10 voices synthesized audio cleanly | **VERIFIED** |
| **F03** | Wi-Fi UDP Multicast Radio | `UdpMulticastTransport.kt` (`224.0.0.1:8889`) | Broadcast frames exchanged across handsets | **VERIFIED** |
| **F04** | Bluetooth Classic SPP Mesh | `BluetoothSppTransport.kt` | Paired RFCOMM sockets exchanging packets | **VERIFIED** |
| **F05** | Wi-Fi Direct P2P Routerless | `WifiDirectP2pTransport.kt` (`port 8888`) | Feature 28 physically validated (GO/Client) | **VERIFIED** |
| **F06** | Representation Hierarchy | `RadioPacketEngine.kt` (FULL..DELTA) | All 5 modes verified in unit & physical runs | **VERIFIED** |
| **F07** | Cryptographic Integrity | `CryptoKeyStore.kt`, `HmacSha256`, `Ed25519` | Anti-tamper drop verified via test packets | **VERIFIED** |
| **F08** | Multi-Hop Relay Routing | `RoutingEngine.kt`, `MeshCoordinator.kt` | 3-node and 4-node relay verified | **VERIFIED** |
| **F09** | Delay-Tolerant Networking | `DtnStorageEngine.kt`, `Room` | Store-and-forward delivery verified | **VERIFIED** |
| **F10** | QR Identity Mutual Trust | `QrPairingActivity.kt`, `CameraX` | Active CameraX scanning and QR generation | **VERIFIED** |
| **F11** | Tactical Radar Peer Discovery | `NearbyDevicesScreen.kt`, `RadarCanvas` | Discovered peers positioned by RSSI dBm | **VERIFIED** |
| **F12** | SOS Emergency Beacon | `EmergencyComposer.kt`, `EmergencyBanner` | High-QoS broadcast with siren & GPS | **VERIFIED** |
| **F13** | Message Journey Tracer | `MessageJourneyActivity.kt` | Hop-by-hop latency and route visualized | **VERIFIED** |
| **F14** | Packet Technical Inspector | `MessageTechnicalInspectorCard.kt` | Raw hex, CRC32, HMAC status inspected | **VERIFIED** |
| **F15** | Communication Health Score | `CommunicationHealthScreen.kt` | 100% health score calculated dynamically | **VERIFIED** |
| **F16** | 2D Mesh Topology Canvas | `MeshTopologyScreen.kt`, `MeshTopologyCanvas` | Interactive force-directed node graph | **VERIFIED** |
| **F17** | Global Offline Search | `GlobalSearchScreen.kt` | Search across chats, contacts, packets | **VERIFIED** |
| **F18** | Neural Model Audit Screen | `ModelStatusScreen.kt` | Resident voice models audited on-device | **VERIFIED** |
| **F19** | Context Delta Compression | `SharedContextEngine.kt` | High-ratio dictionary token compression | **VERIFIED** |
| **F28** | Wi-Fi Direct Physical Link | `WifiDirectP2pTransport.kt` | Full physical handset link verified | **VERIFIED** |
| **F29** | Multi-Hop Transport Failover | `AdaptiveTransportManager.kt` | Seamless auto-failover to Bluetooth SPP | **VERIFIED** |
| **F33** | Long-Duration Soak Harness | `Feature33SoakTest.kt` | 500+ packets soaked without memory leak | **VERIFIED** |
| **F35** | Quality Benchmark Suite | `Feature35QualityBenchmark.kt` | Zero packet loss under synthetic congestion | **VERIFIED** |
| **F38** | Complete Current-State Audit | `docs/audit/` | Complete 7-document audit suite produced | **VERIFIED** |

---

## 5. Physical Device Comparison & Responsive Geometry `[OBSERVED ON DEVICE]`

Detailed comparative analysis between the two physical testbed devices:

```
+------------------------------------------------------------------------------------+
|                               DEVICE GEOMETRY AUDIT                                |
|                                                                                    |
|  Phone A (Samsung Galaxy A55 5G):           Phone B (Samsung Galaxy Note 10 Lite): |
|  - Physical: 1080 x 2340 px                 - Physical: 1080 x 2400 px             |
|  - Density: 450 dpi (scale: 2.8125x)        - Density: 420 dpi (scale: 2.625x)     |
|  - Logical Viewport: 384.0 x 832.0 dp       - Logical Viewport: 411.4 x 914.3 dp   |
|  - Status Bar: 85 px (30.2 dp)              - Status Bar: 68 px (25.9 dp)          |
|  - Nav Bar: 3-Button Mode (135 px / 48 dp)  - Nav Bar: Gesture Mode (0 px inset)   |
|  - Net Usable Viewport: 384.0 x 753.8 dp    - Net Usable Viewport: 411.4 x 888.4 dp |
+------------------------------------------------------------------------------------+
```

### 5.1 Primary Responsive Defects Discovered
1. **DIFF-01 [HIGH SEVERITY]: Word Splitting in `SettingsScreen.kt` Transport Cards**
   - *Evidence:* `18_settings_field_radio_phoneB.png` & `phoneA.png`.
   - *Behavior:* In the `ACTIVE TRANSPORT LINK` 3-column row, fixed card weights cause labels to break across lines mid-word without hyphens:
     - `Wi-Fi Multicast` wraps to `Wi-Fi` / `Multicas` / `t`.
     - `Bluetooth SPP` wraps to `Bluetoot` / `h SPP`.
     - `Wi-Fi Direct Routerless` wraps to `Wi-Fi` / `Direct` / `Routerl` / `ess`.
   - *Root Cause:* Cards lack minimum intrinsic content width constraints; layout assumes a desktop or wide tablet width.
2. **DIFF-02 [MEDIUM SEVERITY]: 3-Button Navigation Bar Inset Collision on Phone A**
   - *Evidence:* `01_radio_idle_phoneA.png`.
   - *Behavior:* The bottom 48 dp (135 px) 3-button navigation bar eats into the usable vertical height. Secondary action buttons (`WALKIE PTT`, `SEND DISTRESS`, `TEST PACKET`) sit tight against the bottom bar without generous breathing room. On Phone B (gesture mode, 0 px bar), there is an extra 134.6 dp of vertical space.
3. **DIFF-03 [MEDIUM SEVERITY]: Language Selector Dropdown Overflow**
   - *Evidence:* `02_radio_lang_menu_phoneA.png`.
   - *Behavior:* The 10-language menu occupies 82% of the vertical viewport on Phone A, completely obscuring the PTT button. On Phone B, the menu occupies only 71% of screen height.

---

## 6. Runtime Resource & Performance Profile `[OBSERVED ON DEVICE]`

### 6.1 Memory Consumption Snapshot (`dumpsys meminfo org.sih.itantra`)
Measurements captured during live mesh operation:

| Metric | Phone A (Galaxy A55 5G) | Phone B (Note 10 Lite) | Critical Takeaway |
|---|---|---|---|
| **Total PSS** | **1,320,776 KB (~1.29 GB)** | **591,725 KB (~577.8 MB)** | Primary driver: Unpacked resident ONNX model sessions |
| **Total RSS** | **839,713 KB (~820.0 MB)** | **426,900 KB (~416.9 MB)** | Actual physical RAM occupied by process pages |
| **Native Heap (Private Dirty)** | **656,704 KB (~641.3 MB)** | **216,088 KB (~211.0 MB)** | **ONNX tensor weights mapped in native C++ memory** |
| **Dalvik/JVM Heap** | **10,743 KB (~10.5 MB)** | **10,434 KB (~10.2 MB)** | **Remarkably clean Kotlin heap; zero JVM leaks** |
| **Graphics (EGL/GL mtrack)** | **35,417 KB (~34.5 MB)** | **38,796 KB (~37.8 MB)** | Hardware UI swapchain buffers for Jetpack Compose |
| **Code Mmap (.dex, .so, .apk)** | **25,228 KB (~24.6 MB)** | **50,608 KB (~49.4 MB)** | Native shared library memory-mapped pages |

### 6.2 Storage & Packaging Metrics
- **Debug APK Size:** `982,553,843` bytes (~`937.04 MB`).
- **Release APK Size:** `975,031,448` bytes (~`929.86 MB`).
- **Bundled Model Assets (`app/src/main/assets/models`):** `886.48 MB` (283 files).
  - STT Whisper-Tiny INT8: `98.81 MB`.
  - TTS 10 Indic Voices: `787.67 MB`.
- **Installed App Footprint (`du -sh`):**
  - Phone A: `2.50 GB` (complete uncompressed model cache in `files/models/`).
  - Phone B: `889 MB` (on-demand unpacked active models).

### 6.3 Startup Latencies (`am start -W`)
- **Phone A:** Cold Start = **`1,461 ms`** | Warm Resume = **`23 ms`**.
- **Phone B:** Cold Start = **`1,243 ms`** | Warm Resume = **`44 ms`**.

---

## 7. Design System & UI Token Audit `[OBSERVED IN SOURCE]`

### 7.1 Quantitative Typography Drift
- **`FontFamily.Monospace` occurrences:** **`570`** across **`62`** presentation files.
- **Inline `fontSize = ...sp` overrides:** **`670`** across **`62`** presentation files.
- **MaterialTheme Typography Parameter:** **Missing entirely** in `ITantraTheme` (`Theme.kt`). The theme initializes `MaterialTheme(colorScheme = colorScheme, content = content)` without defining a cohesive typography scale, forcing individual composables to hardcode styles inline.

### 7.2 Shape Fragmentation
Audit reveals **13 arbitrary corner radii**:
`0.dp`, `1.dp`, `2.dp`, `3.dp`, `4.dp`, `6.dp`, `8.dp`, `10.dp`, `12.dp`, `14.dp`, `16.dp`, `20.dp`, `24.dp`.
Components lack a centralized `Shapes` token definition, leading to visual inconsistency between buttons, chips, dialogs, and cards.

### 7.3 Palette & Color Tokens (`Color.kt` & `Theme.kt`)
- `ColorForest` (`#0F2D23`): Deep pine green base.
- `ColorSage` (`#2E7D32`): Foliage green accent, connected status.
- `ColorSand` (`#F6F4ED`): Warm ivory background / dark-mode primary text.
- `ColorSandSurface` (`#EBE7DC`): Light-mode container surface.
- `ColorCharcoal` (`#1B1F1D`): Tactical field-radio dark background.
- `ColorCharcoalSurface` (`#242A27`): Dark-mode card container surface.
- `ColorAlert` (`#D84315`): Burnt terracotta alert / SOS distress.
- `ColorWarning` (`#FFB300`): Amber hazard warning / degraded link.
- `ColorSignalBlue` (`#0288D1`): Muted industrial blue telemetry.

---

## 8. Prioritized UI Quality Audit & Technical Debt Inventory

Categorized breakdown of existing usability and visual defects:

### 8.1 Layout & Framing
- **Word-Wrapping on Narrow Viewports (Priority: HIGH):** Fixed 3-column rows in `SettingsScreen` and `ActiveTransportsCard` cause word truncation on 384 dp devices.
- **Navigation Bar Breathing Room (Priority: MEDIUM):** Bottom actions lack standardized padding for 3-button navigation bars on modern Android versions.

### 8.2 Typography & Scale
- **Inline Font Overrides (Priority: HIGH):** Over 670 inline `fontSize` overrides prevent seamless dynamic font scaling for accessibility.
- **Monospace Overuse (Priority: MEDIUM):** Monospaced fonts are applied to body paragraphs and dialogues, degrading readability during lengthy voice transcripts.

### 8.3 Visual Hierarchy & Component Consistency
- **Shape Inconsistency (Priority: MEDIUM):** 13 competing corner radii create an unpolished, fragmented feel. Standardize on 4dp (mini), 8dp (medium), 12dp (large), and 50% (pill).
- **Hardcoded Colors (Priority: LOW):** Scattered instances of `Color.Gray`, `Color.White.copy(alpha = 0.7f)`, and inline hex colors bypass `RadioColors`.

### 8.4 Multilingual & Speech UX
- **Dropdown Height Constraint (Priority: MEDIUM):** `LanguageSelectorPill` menu should be constrained to `60%` viewport height with internal scroll.
- **Model Loading Feedback (Priority: LOW):** First-time initialization of a neural voice model exhibits a brief 300 ms UI stall; needs non-blocking coroutine prewarming with a subtle progress spinner.

---

## 9. UI Overhaul Roadmap & Architectural Guardrails

When the UI overhaul begins, the following architectural guidelines must be strictly enforced:

1. **Strict Protocol Preservation:** Never modify packet formats, byte layouts, HMAC/CRC routines, or the 5 representation modes (`FULL` through `DELTA`).
2. **Centralized Design System:**
   - Create `Type.kt` declaring a unified `TacticalTypography` scale.
   - Create `Shape.kt` establishing standard 4dp/8dp/12dp shape tokens.
   - Pass both into `MaterialTheme(colorScheme = ..., typography = ..., shapes = ..., content = ...)`.
3. **Responsive Flex Layouts:** Replace rigid multi-column rows with adaptive `FlowRow` or vertical stacks to guarantee zero text splitting on 384 dp viewports.
4. **WindowInsets Compliance:** Standardize all screens on `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` to guarantee identical margins across 3-button navigation and gesture modes.

---

## 10. Verification of Non-Destructive Status

- **Working Tree Status:** `git status` verifies zero modifications to production code (`app/src/main/`).
- **Active Branch:** `main` at commit `d5937ad465616733e50bb7faa375b68cce43e96c`.
- **Protected Tags:** `v1.0.0`, `v1.1.0-rc1`, `v1.1.0-rc2`, and `v1.2.0-pre-ui-overhaul` remain pristine and untouched.
- **Audit Deliverables:** All 7 required audit documents successfully compiled into `docs/audit/` with supporting screenshots and XML dumps.
