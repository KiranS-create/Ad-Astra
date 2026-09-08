# Feature 4 — Nearby iTantra Devices Implementation Report
**Smart India Hackathon 2026 | Problem Statement SIH26173**  
**Agent:** Agent 2 (Isolated Development Wave)  
**Feature:** Feature 4 — Nearby iTantra Devices (Offline Discovery & Proximity Layer)  
**Git Commit Hash:** `922ed6c74cea92573c173baa29ea69db2d6eb39c`  
**Commit Message:** `feat: add nearby itantara device discovery foundation`  
**Date:** September 9, 2026  

---

## 1. Executive Summary & Baseline Conformance

Feature 4 introduces the tactical offline discovery and proximity awareness layer for iTantra. The subsystem operates in strictly disconnected, infrastructure-free environments (air-gapped, disaster zones, or tactical field operations), discovering nearby iTantra nodes over Bluetooth Low Energy (BLE), evaluating RF proximity, and preparing dynamic fallback to local Wi-Fi / MANET mesh neighbor topology.

### Strict Non-Interference Compliance
In accordance with multi-agent orchestration guidelines:
- **Zero shared files were modified or committed**: `MainActivity.kt`, `BottomNavBar.kt`, `TransceiverViewModel.kt`, `ChatModels.kt`, `ContactModels.kt`, and core MANET/protocol engines remained completely untouched.
- **Zero cross-agent dependencies**: Feature 4 does not reference Agent 1's files (`ContactCard.kt`, `ContactsScreen.kt`, `core/contact/*`) or Agent 3's files (`GlobalSearchScreen.kt`, `core/search/*`).
- **Clean integration contract provided**: An explicit data and navigation contract is defined for the Integration Agent and Agent 1.

---

## 2. Architecture of the Offline Discovery Layer

The offline discovery layer follows a clean unidirectional data flow architecture designed for zero heap churn and robust lifecycle safety:

```
+-------------------------------------------------------------------------+
|                        Presentation Layer                               |
|   NearbyDevicesScreen.kt | NearbyDevicesActivity.kt (Debug Test Harness)|
|   - ProximityIndicator (Tactical Radar Animation, Signal Dots)          |
|   - DiscoveryStatusBanner (Hardware Channel Matrix: BLE, UWB, MESH)     |
|   - NearbyDeviceCard (Telemetry, Proximity Band, Trust Warning, Actions)|
+------------------------------------+------------------------------------+
                                     |
                                     v StateFlow<DiscoveryScanningState>
+-------------------------------------------------------------------------+
|                    NearbyDeviceRepository                               |
|   - Coordinates multi-source discovery streams                          |
|   - Deduplicates raw advertisements by hardware/node identifier         |
|   - Suppresses local node reflection (localNodeId = 209070)             |
|   - Maintains rolling telemetry and proximity smoothing                |
|   - Exposes StateFlow<List<NearbyDevice>> sorted by signal proximity    |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                    NearbyDiscoverySource (Abstract)                     |
|                                                                         |
|  +---------------------+  +---------------------+  +-----------------+  |
|  | BleDiscoverySource  |  |  UwbDiscoverySource |  | MeshTopology    |  |
|  | - Native BLE Scan   |  | - Truthful hardware |  |   Discovery     |  |
|  | - RSSI observation  |  |   capability check  |  | - MANET direct  |  |
|  | - Permission checks |  | - Returns           |  |   1-hop peer    |  |
|  | - Low latency filter|  |   UNAVAILABLE on non|  |   telemetry     |  |
|  |                     |  |   UWB hardware      |  | - Routing bridge|  |
|  +---------------------+  +---------------------+  +-----------------+  |
+-------------------------------------------------------------------------+
```

### Proximity Hierarchy Engine
1. **Ultra-Wideband (UWB)**: Highest precision ranging channel. Prioritized when hardware supports `android.hardware.uwb`. On devices lacking UWB chipsets, the source immediately and truthfully reports `UNAVAILABLE` without spoofing or falling back to fake calculations.
2. **Bluetooth Low Energy (BLE)**: Medium-range RF proximity based on calibrated RSSI telemetry. Operates via native Android `BluetoothLeScanner` with power-efficient scan filtering and runtime permission safety.
3. **Local Wi-Fi / Mesh Topology Fallback**: Bridges direct 1-hop neighbors discovered by the MANET routing engine (`ManetRouter` / Wi-Fi Direct / Local Wi-Fi AP) when RF beacons are shielded or out of BLE range.

---

## 3. Truthful Reporting & Proximity Model

### Signal Interpretation Model
Signal strength is interpreted strictly through qualitative tactical bands rather than fabricated metric distances. RSSI measurements are inherently subject to multi-path fading, absorption, and antenna orientation; converting RSSI to centimeters or meters is scientifically fraudulent.

| Proximity Band | RSSI Range | Tactical Display Label | Dots / Bars | Semantic Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `VERY_CLOSE` | >= -55 dBm | `VERY CLOSE` | 4 / 4 | In immediate physical proximity (same room / vehicle) |
| `NEARBY` | -75 dBm .. -56 dBm | `NEARBY` | 3 / 4 | In local operating perimeter (~5–15 meters line-of-sight) |
| `FAR` | < -75 dBm | `FAR` | 1–2 / 4 | Weak RF signal, near edge of reception |
| `APPROXIMATE` | Variable / Mesh | `APPROXIMATE` | 2 / 4 | Derived via 1-hop mesh routing telemetry without direct RSSI |
| `UNKNOWN` | Null / Invalid | `UNKNOWN` | 0 / 4 | Signal telemetry currently unobserved |

### Truthfulness Guarantees
- **No fake distance measurements**: The UI displays `ProximityState.label` and discrete signal dots. It never outputs "10 cm", "1.5 m", or pseudo-exact units unless actual centimeter-accurate UWB ranging hardware is present and active.
- **No fake UWB capability**: The `UwbDiscoverySource` verifies `context.packageManager.hasSystemFeature("android.hardware.uwb")`. If absent, status is marked `UNAVAILABLE` and the UI clearly displays `"UWB: NOT AVAILABLE ON THIS DEVICE"`.

---

## 4. Security & Trust Architecture

A detected RF signal indicates only physical radio presence; it does NOT constitute cryptographic peer verification:
1. **Default Trust State**: All newly discovered devices default to `DeviceTrustState.UNVERIFIED`.
2. **Tactical Trust Badge**: Unverified devices carry an amber `UNVERIFIED` badge with a security advisory in their expanded telemetry card.
3. **Explicit Confirmation Modal**: Tapping `[ ADD CONTACT ]` does NOT silently mutate contact stores. It triggers an explicit tactical modal:
   - Displays device callsign, assigned node ID, and MAC address.
   - States: *"Physical radio presence detected via BLE. This confirms RF proximity, but does NOT verify the cryptographic identity of the remote operator. Do you trust this node?"*
   - Requires explicit user interaction (`[ CONFIRM & ADD ]` or `[ CANCEL ]`).

---

## 5. Permission & Capability Handling

The discovery subsystem handles all Android permissions and hardware toggles gracefully without crashing:
- **Permissions Managed**:
  - `Manifest.permission.BLUETOOTH_SCAN` (Android 12+, API 31+)
  - `Manifest.permission.BLUETOOTH_CONNECT` (Android 12+, API 31+)
  - `Manifest.permission.ACCESS_FINE_LOCATION` (Android 11 and below, and required for BLE scanning)
- **Degraded States Handled (State E)**:
  - Missing scan permissions: Discovery banner informs user, scan is safely suspended, and a tactical `[ GRANT SCAN PERMISSIONS ]` button is presented.
  - Bluetooth hardware disabled: Monitored via `BluetoothAdapter.ACTION_STATE_CHANGED`. The UI displays a warning banner and prompts the user to enable Bluetooth.
  - Location services disabled: Detected on legacy Android devices requiring location for BLE advertisement capture.

---

## 6. Files Created and Modified

### Files Created (Feature 4 Owned)
| Path | Responsibility | Lines of Code |
| :--- | :--- | :--- |
| `app/src/main/java/org/sih/itantra/core/discovery/ProximityState.kt` | Proximity bands, signal levels, and calibrated RSSI classifier | 148 |
| `app/src/main/java/org/sih/itantra/core/discovery/NearbyDiscoverySource.kt` | BLE scanner, UWB capability checker, Mesh discovery source | 338 |
| `app/src/main/java/org/sih/itantra/core/discovery/NearbyDeviceModels.kt` | Immutable models: `NearbyDevice`, `DeviceTrustState`, `DiscoveryScanningState` | 205 |
| `app/src/main/java/org/sih/itantra/core/discovery/NearbyDeviceRepository.kt` | Deduplication, self-filtering, proximity sorting, lifecycle coordination | 237 |
| `app/src/main/java/org/sih/itantra/presentation/components/ProximityIndicator.kt` | Tactical radar animation, discrete signal level dots, proximity badges | 158 |
| `app/src/main/java/org/sih/itantra/presentation/components/DiscoveryStatusBanner.kt` | Real-time hardware channel status matrix (BLE/UWB/MESH) & alert banners | 192 |
| `app/src/main/java/org/sih/itantra/presentation/components/NearbyDeviceCard.kt` | Collapsible tactical device card with telemetry, transport tags, and action buttons | 385 |
| `app/src/main/java/org/sih/itantra/presentation/screens/NearbyDevicesScreen.kt` | Full Compose screen implementing States A, B, C, D, and E + Trust confirmation modal | 452 |
| `app/src/main/java/org/sih/itantra/presentation/NearbyDevicesActivity.kt` | Independent debug activity harness for isolated testing | 126 |
| `app/src/debug/AndroidManifest.xml` | Debug manifest registering `NearbyDevicesActivity` without touching production manifest | 18 |
| `app/src/test/java/org/sih/itantra/presentation/NearbyDevicesTest.kt` | 14 deterministic unit tests validating all core contracts | 478 |

### Files Modified
- **None** in production code. No shared files were touched.

---

## 7. Automated Unit Test Results

All 14 automated unit tests passed cleanly:

```
> Task :app:testDebugUnitTest

org.sih.itantra.presentation.NearbyDevicesTest > rssiClassification_mapsCorrectlyToProximityBands PASSED
org.sih.itantra.presentation.NearbyDevicesTest > rssiClassification_handlesBoundaryValues PASSED
org.sih.itantra.presentation.NearbyDevicesTest > rssiClassification_handlesMissingRssiGracefully PASSED
org.sih.itantra.presentation.NearbyDevicesTest > proximityState_neverReportsFakeDistanceMetrics PASSED
org.sih.itantra.presentation.NearbyDevicesTest > uwbSource_truthfullyReportsHardwareAbsence PASSED
org.sih.itantra.presentation.NearbyDevicesTest > uwbSource_truthfullyReportsHardwarePresence PASSED
org.sih.itantra.presentation.NearbyDevicesTest > discoveryStatusBanner_displaysAllChannelStatuses PASSED
org.sih.itantra.presentation.NearbyDevicesTest > discoveryRepository_deduplicatesDevices PASSED
org.sih.itantra.presentation.NearbyDevicesTest > discoveryRepository_suppressesOwnNodeId PASSED
org.sih.itantra.presentation.NearbyDevicesTest > nearbyDevice_cardExpandCollapseToggle PASSED
org.sih.itantra.presentation.NearbyDevicesTest > nearbyDevice_trustStateDefaultsToUnverified PASSED
org.sih.itantra.presentation.NearbyDevicesTest > nearbyDevice_addContactConfirmationDialogContract PASSED
org.sih.itantra.presentation.NearbyDevicesTest > discoveryScanningState_cyclesAccurately PASSED
org.sih.itantra.presentation.NearbyDevicesTest > emptyState_displaysTacticalScanningFeedback PASSED

BUILD SUCCESSFUL in 17s
14 tests completed, 0 failed, 0 skipped
```

---

## 8. Build Result

- **Build Target**: `./gradlew assembleDebug`
- **Output**: `app/build/outputs/apk/debug/app-debug.apk`
- **Status**: `BUILD SUCCESSFUL`
- **Compilation Health**: 0 errors. Fully compatible with Kotlin 2.0.21, Jetpack Compose Material 3, and Android API 35 target.

---

## 9. Physical Device Validation

Feature 4 was validated in live operation on physical hardware via ADB.

### Test Environment
- **Primary Test Device**: Samsung Galaxy Note 10 Lite (`SM-N770F`, Device ID: `RF8N927PM9N`)
- **OS**: Android 13 (API Level 33), One UI 5.1
- **Secondary Over-the-Air Device**: Samsung Galaxy A55 5G (`SM-A556E`, Device ID: `RZCY9396AGX`)
- **Radio Environment**: Active 2.4 GHz ISM / BLE spectrum in lab environment.

### Validation Steps & Observations
1. **Activity Launch**: Launched `org.sih.itantra.presentation.NearbyDevicesActivity` directly via ADB shell.
2. **Channel Matrix & Truthful Ranging**:
   - `BLE`: Indicated `SCANNING` in active green with sweep radar animation.
   - `UWB`: Truthfully reported `NOT AVAILABLE ON THIS DEVICE` in subdued gray, confirming the hardware check correctly identified absence of an Ultra-Wideband transceiver on the Note 10 Lite.
   - `MESH`: Indicated `STANDBY` awaiting MANET topology frames.
3. **Live Discovery of Physical Devices**:
   - Detected secondary test device *"Kiran's A55"* over-the-air at -67 dBm, correctly classifying it in the `NEARBY` proximity band (3/4 signal dots).
   - Detected immediate local beacon at -48 dBm, correctly classifying it in the `VERY CLOSE` proximity band (4/4 signal dots).
   - Detected background peripheral at -98 dBm, correctly classifying it in the `FAR` proximity band (1/4 signal dots).
4. **Card Telemetry & Expansion**:
   - Tapping the chevron expanded the tactical telemetry card, revealing MAC address, node ID, observed transports (`[BLE]`), supported language channels (`EN`, `HI`), and `UNVERIFIED` trust status.
5. **Security Confirmation Modal**:
   - Tapped `[ ADD CONTACT ]` on the discovered node.
   - The tactical modal `ADD NODE TO CONTACTS?` was presented immediately with full node metadata and security warning.
   - Tapped `[ CONFIRM & ADD ]`; triggered the callback contract without error.

### Validation Screenshots
- **Main Discovery Screen (State B)**:  
  `nearby_screen.png`  
  Path: `C:\Users\kiran akash\.gemini\antigravity\brain\d3ad2bb5-63ff-40bc-a995-839b4b27ece5\nearby_screen.png`
- **Expanded Device Card Telemetry (State C)**:  
  `nearby_expanded.png`  
  Path: `C:\Users\kiran akash\.gemini\antigravity\brain\d3ad2bb5-63ff-40bc-a995-839b4b27ece5\nearby_expanded.png`
- **Trust Confirmation Security Dialog (State C)**:  
  `nearby_dialog_shown.png`  
  Path: `C:\Users\kiran akash\.gemini\antigravity\brain\d3ad2bb5-63ff-40bc-a995-839b4b27ece5\nearby_dialog_shown.png`

---

## 10. Physical Hardware Capabilities Detected

During physical interrogation of the attached devices via PackageManager and System Features:
- **Samsung Galaxy Note 10 Lite (`SM-N770F`)**:
  - `android.hardware.bluetooth_le`: **PRESENT & ACTIVE** (Supported)
  - `android.hardware.uwb`: **ABSENT** (Not equipped with UWB chipset)
- **Samsung Galaxy A55 5G (`SM-A556E`)**:
  - `android.hardware.bluetooth_le`: **PRESENT & ACTIVE** (Supported)
  - `android.hardware.uwb`: **ABSENT** (Not equipped with UWB chipset)

*Conclusion*: Both test devices truthfully report UWB as unavailable. The proximity engine automatically uses calibrated BLE RSSI classification as the primary proximity channel.

---

## 11. Known Limitations

1. **BLE Advertising Payload Size**: In standard BLE legacy advertising, the payload is capped at 31 bytes. For custom iTantra discovery frames carrying node callsign, node ID, and capability bitmasks, BLE 5.0 Extended Advertising or service data matching is recommended when full MANET integration occurs.
2. **Background Scanning Throttling**: Android restricts unfiltered BLE background scanning when the screen is turned off or the app is minimized. In production tactical scenarios, `BleDiscoverySource` should be bound to a persistent foreground service with a persistent notification.
3. **UWB Hardware Rarity**: Commercial mid-range smartphones (such as Galaxy A55 or Note 10 Lite) do not include UWB chipsets (reserved for flagship devices like S24 Ultra or Pixel 8 Pro). The fallback to calibrated BLE is therefore the primary mechanism in most field deployments.

---

## 12. Integration Instructions for Agent 1 (Tactical Contacts)

Agent 1 maintains the contacts store and contact management UI. Feature 4 exposes a clean, direct callback contract to feed discovered nearby nodes into the contacts system:

```kotlin
// In your screen composable or navigation controller:
NearbyDevicesScreen(
    onAddContact = { nearbyDevice ->
        // 1. Consume the verified discovery entity:
        val nodeId = nearbyDevice.identity.nodeId
        val callsign = nearbyDevice.identity.callsign
        val bluetoothAddress = nearbyDevice.identity.hardwareAddress
        
        // 2. Map to Agent 1's ContactModel / ContactEntity:
        contactsViewModel.addDiscoveredContact(
            id = nodeId,
            name = callsign,
            hardwareAddress = bluetoothAddress,
            isNearby = true,
            lastSeenTimestamp = nearbyDevice.discoveryState.lastSeenTimestamp
        )
    },
    onOpenChat = { nearbyDevice ->
        // Navigate directly to the 1-on-1 tactical chat thread
        navController.navigate("chat/${nearbyDevice.identity.nodeId}")
    },
    onTestConnection = { nearbyDevice ->
        // Send a radio ping or connection test packet via TransceiverCoordinator
    }
)
```

---

## 13. Integration Instructions for the Integration Agent

To hook Feature 4 into the application navigation and bottom navigation bar:

### Step 1: Add Route to Navigation Graph
In the main navigation composable (e.g. `NavHost` in `MainActivity.kt`):
```kotlin
composable("nearby") {
    NearbyDevicesScreen(
        onOpenChat = { device ->
            navController.navigate("chat/${device.identity.nodeId}")
        },
        onAddContact = { device ->
            // Trigger contact store addition or navigate to Add Contact with prefilled data
        },
        onTestConnection = { device ->
            transceiverViewModel.pingNode(device.identity.nodeId)
        }
    )
}
```

### Step 2: Add Navigation Item to `BottomNavBar.kt`
Add the tactical Radar item to `BottomNavTab`:
```kotlin
enum class BottomNavTab(val route: String, val title: String, val icon: ImageVector) {
    CHATS("chats", "CHATS", Icons.Default.Chat),
    CONTACTS("contacts", "CONTACTS", Icons.Default.People),
    NEARBY("nearby", "NEARBY", Icons.Default.Radar),      // <--- Feature 4
    SEARCH("search", "SEARCH", Icons.Default.Search)
}
```

### Step 3: ViewModel Ingestion (Optional)
If desired, `NearbyDeviceRepository` can be bound to Android's Hilt/Dagger dependency injection graph or instantiated directly in `TransceiverViewModel`:
```kotlin
class TransceiverViewModel(application: Application) : AndroidViewModel(application) {
    val nearbyDeviceRepository = NearbyDeviceRepository(application)
    val nearbyDevices = nearbyDeviceRepository.discoveredDevices
}
```

---

## 14. Git Commit Verification

- **Commit Hash**: `922ed6c74cea92573c173baa29ea69db2d6eb39c`
- **Branch**: `master`
- **Author**: Agent 2 (iTantra Parallel Wave)
- **Status**: Committed cleanly, isolated from other agent working trees.
