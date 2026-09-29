# iTantra — Current-State Software Architecture & Data Flow Map

**Baseline Checkpoint:** `v1.2.0-pre-ui-overhaul` (`d5937ad465616733e50bb7faa375b68cce43e96c`)  
**Package Root:** `org.sih.itantra`  
**Architectural Style:** Reactive Unidirectional Data Flow (UDF) + Layered Clean Architecture (Presentation -> Coordinator/Domain -> Service/Mesh -> Transport/Persistence/ML).

---

## 1. End-to-End Architectural Hierarchy

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       PRESENTATION LAYER (Jetpack Compose)                             │
│   MainActivity • MainTransceiverScreen • ChatsHomeScreen • IndividualChatScreen • ContactsScreen       │
│   NearbyDevicesScreen • GlobalSearchScreen • DiagnosticsScreen • CommunicationHealthScreen             │
│   MeshTopologyScreen • SettingsScreen • ModelStatusScreen • EmergencyComposer • QrPairingScreen       │
└──────────────────────────────────────────────────┬─────────────────────────────────────────────────────┘
                                                   │ Observes StateFlows / Dispatches UI Events
                                                   ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       VIEWMODEL LAYER                                                  │
│   TransceiverViewModel                                                                                 │
│   - StateFlow<RadioState>, StateFlow<List<MessageRecord>>, StateFlow<CommunicationHealthState>         │
│   - StateFlow<NearbyDevicesState>, StateFlow<ContactsState>, StateFlow<AppThemeMode>                   │
│   - Manages AudioRecord PTT recording, STT dispatch, TTS playback, and UI navigation state             │
└──────────────────────────────────────────────────┬─────────────────────────────────────────────────────┘
                                                   │ Coordinates Operations
                                                   ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       COORDINATOR & REPOSITORY LAYER                                   │
│   ManetCoordinator • MessageHistoryStore • ContactRepository • NearbyDeviceRepository                 │
│   SearchRepository • DiagnosticsRepository • SharedContextStore                                        │
└──────────────────────┬──────────────────────────────────────────────────────────┬──────────────────────┘
                       │ Starts / Binds Service                                   │ Dispatches Packets
                       ▼                                                          ▼
┌──────────────────────────────────────────────┐       ┌─────────────────────────────────────────────────┐
│              SERVICE LAYER                   │       │               MESH ROUTING & QOS                │
│   ManetNodeService                           │       │   ContextAwareRelayRouter                       │
│   - foregroundServiceType="connectedDevice"  │◄─────►│   - Dynamic route discovery & epidemic relay    │
│   - Persistent MANET execution               │       │   - Duplicate packet suppression cache          │
│   - Wakelock & low-power sleep policy        │       │   - Loop prevention (DROP_SELF, TTL expiry)     │
│   - Foreground notification channel          │       │   DtnStore (Store-and-Forward Buffer)           │
│                                              │       │   QoSManager (DISTRESS > ALERT > IMPORTANT)     │
└──────────────────────────────────────────────┘       └──────────────────────────┬──────────────────────┘
                                                                                  │ Wire Framing
                                                                                  ▼
                                                       ┌─────────────────────────────────────────────────┐
                                                       │          SECURITY & PROTOCOL SERIALIZATION      │
                                                       │   PacketSerializer (TLV wire binary format)     │
                                                       │   PacketAuthenticator (HMAC-SHA256 invariance)  │
                                                       │   CRC32 Checksum Engine                         │
                                                       └──────────────────────────┬──────────────────────┘
                                                                                  │ Physical Transport
                                                                                  ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       HYBRID TRANSPORT LAYER                                           │
│   WifiTransport              WifiDirectTransport              BluetoothTransport        Loopback       │
│   (UDP Multicast port 42888) (P2P Wi-Fi Direct port 42889)    (RFCOMM SPP UUID 00001101) (Unit Testing)│
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component-by-Component Architectural Audit

### 2.1 Presentation & Navigation
- **`MainActivity.kt` (`org.sih.itantra.presentation`):**
  - **What it does:** Main launch entry point (`android:exported="true"`, `launchMode="singleTask"`), binds `TransceiverViewModel`, manages system permissions, initializes `NavigationStateManager`, handles external debug/testing intents (`onNewIntent`), and renders the root Compose container.
  - **Window Flags:** Sets `FLAG_KEEP_SCREEN_ON` in `onCreate` to prevent screen lock during tactical radio operations.
  - **Soft Input:** Configured with `windowSoftInputMode="adjustResize"` in manifest.
  - **Lifecycle & Destruction:** Clears viewmodel observers on destruction. Background MANET routing survives UI destruction via `ManetNodeService(stopWithTask = false)`.
- **`NavigationStateManager.kt` / `AppNavigation.kt` (`org.sih.itantra.presentation.navigation`):**
  - **What it does:** Deterministic, type-safe navigation state manager that maintains:
    1. Root bottom navigation tab (`currentTab: RadioNavTab`, defaulting to `RADIO`).
    2. Explicit, LIFO back-stack (`backStack: SnapshotStateList<ScreenDestination>`) for full-screen feature overlays.
  - **Back Handling:** Connected to Compose `BackHandler(enabled = navManager.canNavigateBack)` which pops the overlay back-stack before system back exits the app.
  - **Destinations Supported:** `Chat(peerId)`, `Contacts`, `NearbyDevices`, `GlobalSearch`, `MessageJourney(messageId)`, `QrPairing`, `ModelAudit`, `ManetDemo`, `SihDemo`, `CommunicationHealth`.

### 2.2 ViewModel Layer (`TransceiverViewModel.kt`)
- **What it does:** The primary architectural orchestrator for all UI screens. Binds domain events to Compose state.
- **StateFlows Owned:**
  - `radioState: StateFlow<RadioState>` (IDLE, LISTENING, TRANSMITTING, RECEIVING, ERROR).
  - `networkState: StateFlow<NetworkState>` (CONNECTED, DEGRADED, LIMITED, CONGESTED, WAITING_FOR_ROUTE, DTN_STORED, OFFLINE).
  - `selectedLanguage: StateFlow<IndicLanguage>` (Default: Hindi `IndicLanguage.HINDI`).
  - `messageHistory: StateFlow<List<MessageRecord>>` (Live message stream).
  - `themeMode: StateFlow<AppThemeMode>` (SYSTEM, LIGHT, DARK).
  - `emergencyState: StateFlow<EmergencyState>` (IDLE, ACTIVE, BROADCASTING).
  - `communicationHealth: StateFlow<CommunicationHealthState>` (Score, ACK rate, peer quality).
- **Coroutines Context:** Operates in `viewModelScope` with dispatch to `Dispatchers.IO` for socket I/O, database persistence, and disk writes; `Dispatchers.Default` for Sherpa-ONNX neural inference and audio processing.

### 2.3 Coordinator & Background Service
- **`ManetCoordinator.kt` (`org.sih.itantra.core.coordinator`):**
  - **What it does:** Bridges ViewModel actions to the `ContextAwareRelayRouter` and active `TransportManager`.
  - **State Observed:** Listens to incoming transport frames, routes them through packet authentication and CRC validation, and updates `MessageHistoryStore` and `DiagnosticsRepository`.
  - **Threading:** Launches dedicated background coroutines on `Dispatchers.IO`.
- **`ManetNodeService.kt` (`org.sih.itantra.service`):**
  - **What it does:** Android Foreground Service with `foregroundServiceType="connectedDevice"` ensuring the iTantra node remains active as a store-and-forward relay even when the UI is closed or backgrounded.
  - **Android 15 Compatibility:** Selected `connectedDevice` specifically to bypass the 6-hour cumulative execution timeout imposed on `dataSync` services in API 35.
  - **Lifecycle:** Started with `startForegroundService()`; posts persistent low-priority status notification. `stopWithTask="false"` keeps the service alive across app task removal.

### 2.4 Protocol & Mesh Routing
- **`Packet.kt` & `PacketSerializer.kt` (`org.sih.itantra.core.protocol`):**
  - **Header Structure:** 26-byte canonical header:
    - `magic` (2 bytes: `0x49 0x54` -> "IT")
    - `version` (1 byte: `0x01`)
    - `msgType` (1 byte: TEXT, AUDIO_CHUNK, SOS, ACK, DELTA, COMPACT, SEMANTIC)
    - `priority` (1 byte: DISTRESS=3, ALERT=2, IMPORTANT=1, NORMAL=0)
    - `ttl` (1 byte: Default 7 hops, decremented at each relay)
    - `flags` (1 byte: BIT 0 = Forwarded, BIT 1 = Compressed, BIT 2 = Encrypted, BIT 3 = Urgent)
    - `sequenceNumber` (2 bytes: monotonic per node)
    - `timestamp` (8 bytes: epoch millis)
    - `sourceDeviceId` (4 bytes: 32-bit unique integer)
    - `destinationDeviceId` (4 bytes: 32-bit unique integer or 0xFFFFFFFF for broadcast)
    - `payloadLength` (2 bytes: Short unsigned)
  - **Payload:** Binary variable-length payload.
  - **Trailer:** 4-byte CRC32 + 32-byte HMAC-SHA256 signature.
- **`PacketAuthenticator.kt` (`org.sih.itantra.security`):**
  - **Router Invariance:** Calculates HMAC-SHA256 over canonical header and payload while masking mutable hop fields (`ttl`, `FLAG_FORWARDED`, and `crc32`). This allows intermediate relay nodes to decrement TTL and forward packets without invalidating the cryptographic origin signature.
- **`ContextAwareRelayRouter.kt` (`org.sih.itantra.core.mesh`):**
  - **Loop Prevention:** Checks `packet.sourceDeviceId == localNodeId` (`DROP_SELF`) to eliminate self-forwarding loops.
  - **Duplicate Suppression:** Sliding memory window (`seenPacketsCache`) caching `(sourceDeviceId, sequenceNumber)` for 15 minutes (`DROP_DUPLICATE`).
  - **TTL Depletion:** Discards packets when `ttl <= 0` (`DROP_TTL_EXPIRED`).
  - **Representation Preservation:** Relays `FULL`, `COMPACT`, `SEMANTIC_BASE`, `SEMANTIC_ENHANCED`, and `CONTEXT_DELTA` bit-for-bit without decompression or representation mutation at intermediate hops.
- **`DtnStore.kt` (`org.sih.itantra.core.mesh`):**
  - Store-and-forward buffer: Capacity capped at 50 packets or 512 KB, expiry TTL 300,000 ms (5 minutes). Evicts oldest NORMAL packets first when buffer pressure reaches limit.

### 2.5 Hybrid Transports
1. **`WifiTransport.kt`:**
   - Transport: UDP Multicast socket on `224.0.0.251:42888`.
   - Use: Zero-configuration local subnet ad-hoc mesh via phone mobile hotspot or shared Wi-Fi.
2. **`WifiDirectTransport.kt`:**
   - Transport: Android native Wi-Fi P2P (`WifiP2pManager`) listening on TCP socket port `42889`.
   - Use: Routerless direct phone-to-phone transport without hotspot or infrastructure. Group Owner acts as socket server; client connects and streams binary packets.
3. **`BluetoothTransport.kt`:**
   - Transport: Bluetooth Classic RFCOMM / SPP using UUID `00001101-0000-1000-8000-00805F9B34FB`.
   - Use: Low-power short-range fallback link when Wi-Fi is unavailable or radio-silent.

---

## 3. Data Flow Pipelines

### 3.1 Push-to-Talk (PTT) Speech Transmission Flow
```
[User presses PTT]
  │
  ▼
AudioRecord (16 kHz, 16-bit mono PCM) -> AudioRecordHelper
  │
  ▼
VAD / Energy Gate -> Speech Frames
  │
  ▼
Sherpa-ONNX Streaming ASR (Whisper-Tiny INT8) -> Raw Hypothesis
  │
  ▼
TacticalDomainReranker -> Domain Canonicalization (Callsigns, Mil Terms)
  │
  ▼
Representation Classifier (Confidence >= 80 -> SemanticBase, 40-79 -> Compact Deflate, < 40 -> Full Audio)
  │
  ▼
Packet Construction (Header + Payload + IndicLanguage)
  │
  ▼
PacketAuthenticator (HMAC-SHA256 signature generated)
  │
  ▼
CRC32 Checksum appended
  │
  ▼
QoS Priority Queue (MessagePriority.NORMAL / IMPORTANT / ALERT / DISTRESS)
  │
  ▼
Active Transport Dispatcher (Wi-Fi Direct -> Wi-Fi UDP -> Bluetooth)
  │
  ▼
Physical RF Transmission
```

### 3.2 Incoming Packet Reception & Playback Flow
```
Physical RF Reception (UDP 42888 / TCP 42889 / RFCOMM SPP)
  │
  ▼
Frame Demultiplexing & CRC32 Validation (Corrupt packets dropped)
  │
  ▼
HMAC-SHA256 Origin Signature Validation (Tampered packets dropped)
  │
  ▼
Duplicate Suppression (seenPacketsCache check -> drops duplicate seq)
  │
  ▼
Route Destination Check (If destination != localNodeId -> ContextAwareRelayRouter forwards packet)
  │
  ▼
Destination Delivery -> MessageHistoryStore records packet
  │
  ▼
Payload Decoder (Full Text / Deflate Decompress / Semantic Token Lookup / Delta Patch)
  │
  ▼
Neural VITS TTS Synthesis (Piper / Mimic3 / Meta MMS based on IndicLanguage)
  │
  ▼
AudioTrack Playback -> Device Speaker
```

---

## 4. Architectural Debt & Anti-Patterns Identified

1. **Monolithic TransceiverViewModel:**
   - `TransceiverViewModel.kt` currently exceeds **1,200 lines of code** and manages state for Radio, Chats, Contacts, Nearby Devices, Diagnostics, Theme, Speech Recording, TTS playback, and Intent routing simultaneously.
   - *Future Target:* Decompose into scoped ViewModels (`RadioViewModel`, `ChatsViewModel`, `ContactsViewModel`, `DiagnosticsViewModel`, `SettingsViewModel`).
2. **Synchronous Asset Unpacking:**
   - Speech model assets (~886 MB) are checked and unpacked from assets on first run. While asynchronous, model prewarming can cause memory spikes (>1 GB PSS) on lower-tier devices.
3. **Hardcoded Monospace Styling in Presentation Layer:**
   - Presentation composables do not consume a centralized typography hierarchy. Over 650 inline declarations of `FontFamily.Monospace` bypass theme styling.
4. **Duplicated Component Implementations:**
   - `NearbyDevicesActivity` and `NearbyDevicesScreen`, `ContactsActivity` and `ContactsScreen`, `GlobalSearchActivity` and `GlobalSearchScreen` maintain parallel implementations (one as standalone debug activity, one as Compose overlay screen).
