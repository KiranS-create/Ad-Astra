# iTantra — Current-State UI/UX Inventory & Screen Directory

**Baseline Checkpoint:** `v1.2.0-pre-ui-overhaul` (`d5937ad465616733e50bb7faa375b68cce43e96c`)  
**Evaluation Devices:**  
- **Phone A:** Samsung Galaxy A55 5G (`SM-A556E`, Android 16, 1080x2340, 450 dpi, ~384 dp viewport width)  
- **Phone B:** Samsung Galaxy Note 10 Lite (`SM-N770F`, Android 12, 1080x2400, 420 dpi, ~411 dp viewport width)  
**Audit Scope:** 20 Screens, 30+ Reachable States, Navigation Hierarchy, Component System, Visual Tokens, and Inset Behaviors.

---

## 1. Executive Summary & Design Metaphor

The current iTantra user interface is implemented entirely in **Jetpack Compose** (Compose BOM 2024.09.00 / Material 3) utilizing a custom field-radio design system (`LocalRadioColors` with `DarkRadioColors` default and `LightRadioColors` option).

### 1.1 Core Metaphor & Tactical Personality
- **Military / Tactical Field Transceiver:** High-contrast matte charcoal background (`#1B1F1D`), deep forest greens (`#0F2D23`, `#143D30`), sage green accents (`#2E7D32`), and warm burnt orange alerts (`#D84315`).
- **Typography Tone:** Heavy reliance on monospaced uppercase telemetry (`FontFamily.Monospace`) for callsigns, packet metrics, timestamps, and radio status badges, evoking rugged hardware communication gear.
- **Root Navigation Model:** A persistent 4-tab bottom navigation bar:
  1. `Radio` (`RadioNavTab.RADIO`) — Primary tactical transceiver, walkie PTT, and live traffic stream.
  2. `Chats` (`RadioNavTab.CHATS`) — Message history, peer threads, contacts, nearby scan, and search.
  3. `Diagnostics` (`RadioNavTab.DIAGNOSTICS`) — Telemetry metrics, packet I/O, queue pressure, and system health.
  4. `Settings` (`RadioNavTab.SETTINGS`) — Transport radios, theme selector, failover switch, and neural model audit.
- **Overlay Navigation:** Sub-screens (Individual Chat, Message Journey, QR Pairing, Nearby Devices, Global Search, Communication Health, Mesh Topology, Model Status) are rendered as full-screen overlays managed by an explicit LIFO back-stack in `NavigationStateManager`.

---

## 2. Screen-by-Screen Master Inventory Table

| # | Screen Name | Entry Path | Exit Path | Top Bar | Bottom Nav | Major Components | Scrolling | Background & Surfaces | Status & Accent Colors | Key State Variants | Evidence Screenshots |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **1** | **Main Transceiver (Radio)** | App Launch / Bottom Tab 0 | Home / Exit | `TopRadioHeader` (Logo, "iTantra", Radio Status Badges: BT, Wi-Fi, READY, CH-1, Settings gear) | Visible (Tab 0) | Language Selector Pill, Live Radio Traffic container, Walkie PTT card, Secondary action row (`WALKIE PTT`, `SEND DISTRESS`, `TEST PACKET`), Voice control status pill | LazyColumn for traffic | Background: `ColorCharcoal` (`#1B1F1D`), Surface: `ColorCharcoalSurface` (`#242A27`), Border: `#333B37` | Connected: `ColorSage` (`#2E7D32`), Listening/Alert: `ColorAlert` (`#D84315`), Ready: `#2E7D32` | Empty (No messages), Receiving, Transmitting, Degraded, Emergency Active | `01_radio_idle_phoneA.png`, `01_radio_idle_phoneB.png`, `21_radio_traffic_active_phoneA.png` |
| **2** | **Language Selector Menu** | Radio screen -> tap Language Pill | Tap language / Back / Outside tap | Inherited from Radio | Visible | DropdownMenu containing 10 Indic languages with native script subtext and checkmark for active language | Vertical menu scroll | Surface: `ColorCharcoalSurface`, Divider: `ColorCharcoalBorder` | Selected: `ColorSage`, Text: `TextPrimaryDark` (`#F6F4ED`) | Expanded, Collapsed, Active selection change | `02_radio_lang_menu_phoneA.png`, `02_radio_lang_menu_phoneB.png` |
| **3** | **Emergency Distress Composer** | Radio screen -> `SEND DISTRESS` button | Back button / Cancel | Top header with warning icon and "EMERGENCY DISTRESS BEACON" | Hidden | Severity selector (DISTRESS, CRITICAL, ADVISORY), Category grid (MEDICAL, AMBUSH, CASUALTY, EVAC, FIRE, FLOOD), GPS Coordinate preview card, siren toggle, `TRANSMIT DISTRESS` button | Vertical scrollable Column | Background: `#1B1F1D`, Card: `#242A27`, Border: `#D84315` (Alert Orange/Red) | Danger: `#D84315`, Amber: `#FFB300`, Coordinates: `#0288D1` | Normal, Category selected, Transmitting, GPS pending, GPS acquired | `03_emergency_composer_phoneA.png`, `03_emergency_composer_phoneB.png`, `04_emergency_category_selected_phoneA.png` |
| **4** | **Emergency Confirmation Dialog** | Emergency Composer -> `TRANSMIT DISTRESS` | `CONFIRM` / `CANCEL` / Back | N/A (Modal Dialog) | Hidden | Red alert icon, "BROADCAST EMERGENCY DISTRESS?", target scope warning, Siren notification text, `CANCEL` button, `CONFIRM DISTRESS` button | None (Modal) | Surface: `#242A27`, Dialog outline: `#D84315`, Button: `#D84315` | Alert Red: `#D84315`, Text: `#F6F4ED` | Idle, Transmitting broadcast | `05_emergency_confirm_dialog_phoneA.png` |
| **5** | **Chats Home** | Bottom Tab 1 (`CHATS`) | Tab switch / Back | Custom Top Bar ("TACTICAL CHATS", Search icon, Contacts directory icon, Nearby scan icon) | Visible (Tab 1) | Emergency banner (if active), Search quick-filter row, Conversations LazyColumn, Empty state card ("NO CONVERSATIONS"), Start New Chat FAB | LazyColumn | Background: `#1B1F1D`, Surface: `#242A27`, Card: `#2B322E` | Unread badge: `#2E7D32`, Emergency: `#D84315`, Text: `#F6F4ED` | Empty, Normal populated, Search filtered, Emergency alert banner | `06_chats_home_phoneA.png`, `06_chats_home_phoneB.png` |
| **6** | **Individual Chat** | Chats Home -> tap conversation card / Intent | Back arrow icon -> Chats Home | Chat Header (Back arrow, peer callsign, node ID, encryption status, radio link type) | Hidden | Message bubbles (Sent right, Received left), Representation mode badges (FULL, COMPACT, SEMANTIC, DELTA), Technical Inspector expand trigger, Bottom Input bar (Mic PTT button, text field, Send button) | LazyColumn with reverseLayout or auto-scroll | Background: `#1B1F1D`, Sent bubble: `#143D30` (Forest), Received: `#242A27` | Mode badges: `#0288D1`, Priority: `#FFB300`/`#D84315`, Verified: `#2E7D32` | Empty chat, Text entered, PTT recording active, Message expanded (Inspector open) | `12_individual_chat_phoneA.png`, `12_individual_chat_phoneB.png`, `22_individual_chat_populated_phoneB.png` |
| **7** | **Message Technical Inspector** | Individual Chat -> tap message details | Tap collapse / Back | N/A (Expandable Card / Bottom Sheet) | Hidden | Raw hex payload view, Packet header dissection (Version, Type, Flags, TTL, SeqNum), HMAC-SHA256 hash, CRC32 status, Latency & Bitrate metrics, `VIEW JOURNEY →` button | Nested scrollable Box | Surface: `#242A27`, Capsule: `#2B322E`, Border: `#333B37` | Valid: `#2E7D32`, Tampered: `#D84315`, Monospace text: `#8D9993` | Collapsed, Expanded, Verified CRC/HMAC, Corrupt CRC | `13_message_inspector_phoneA.png`, `23_inspector_expanded_phoneB.png` |
| **8** | **Message Journey Trace** | Technical Inspector -> `VIEW JOURNEY →` | Back arrow | Top Header ("MESSAGE JOURNEY TRACE", Message ID, Hop Count) | Hidden | Journey timeline canvas, Origin node card, Intermediate relay cards with RSSI and delay, Destination delivery receipt, Hop-by-hop latency breakdown | Vertical Column with LazyColumn | Background: `#1B1F1D`, Cards: `#242A27`, Timeline links: `#2E7D32` | Origin: `#0288D1`, Relay: `#FFB300`, Delivered: `#2E7D32`, Failed: `#D84315` | Single-hop direct, Multi-hop 2-hop, Multi-hop 3-hop, Partition stored | `14_message_journey_phoneA.png`, `24_message_journey_active_phoneB.png` |
| **9** | **Tactical Contacts Directory** | Chats Home -> Contacts icon | Back arrow -> Chats Home | Header ("TACTICAL CONTACTS", Node count badge, Search icon, Add contact icon) | Hidden | Quick action bar (`+ ADD CONTACT`, `NEARBY SCAN`, `QR PAIRING`), Contacts LazyColumn with trust levels (VERIFIED, MESH, UNVERIFIED), Last seen timestamp, direct Chat action button | LazyColumn | Background: `#1B1F1D`, Contact cards: `#242A27`, Border: `#333B37` | Verified: `#2E7D32`, Untrusted: `#FFB300`, Distress: `#D84315` | Empty, Populated list, Filtered, Add Contact dialog open | `07_contacts_screen_phoneA.png`, `07_contacts_screen_phoneB.png` |
| **10** | **QR Node Pairing (My Code & Scan)** | Contacts screen -> `QR PAIRING` button | Back arrow -> Contacts | Header ("TACTICAL NODE PAIRING", Tab row: `MY CODE` / `SCAN`) | Hidden | Tab `MY CODE`: High-density QR image, Node ID, Callsign, Public Key fingerprint, Share button. Tab `SCAN`: CameraX viewfinder preview, reticle overlay, manual entry fallback | Non-scrolling tab containers | Background: `#1B1F1D`, QR canvas: White container on dark, Reticle: `#2E7D32` | Valid scan: `#2E7D32`, Scan error: `#D84315`, Status pill: `#242A27` | `MY CODE` tab, `SCAN` tab (camera active), QR detected confirmation dialog | `08_qr_pairing_my_code_phoneA.png`, `09_qr_pairing_scan_tab_phoneA.png`, `09_qr_pairing_scan_tab_phoneB.png` |
| **11** | **Nearby iTantra Devices** | Contacts screen -> `NEARBY SCAN` | Back arrow -> Contacts | Header ("NEARBY DISCOVERY", Active interface badges, Radar sweep toggle) | Hidden | Tactical radar visualizer / pulse animation, Peer discovery cards (Node ID, Callsign, Interface: BT / Wi-Fi UDP / Wi-Fi Direct, RSSI signal bar, `CONNECT` / `CHAT` button) | LazyColumn below radar | Background: `#1B1F1D`, Cards: `#242A27`, Radar circles: `#143D30` | Strong RSSI: `#2E7D32`, Moderate: `#FFB300`, Weak: `#D84315` | Scanning active, No peers found (empty), Peers discovered, Connecting | `10_nearby_devices_phoneA.png`, `10_nearby_devices_phoneB.png` |
| **12** | **Offline Global Search** | Chats Home -> Search icon | Back arrow / Clear query | Search text field with clear 'X' button, Back arrow | Hidden | Filter chips (`ALL`, `CHATS`, `CONTACTS`, `PACKETS`), Search results LazyColumn with highlight matching, Empty state ("NO RESULTS FOUND") | LazyColumn | Background: `#1B1F1D`, Filter chips: `#2B322E` (active: `#2E7D32`) | Highlight text: `#FFB300`, Results counter: `#8D9993` | Empty query, Typing, Results returned, Zero results | `11_global_search_phoneA.png`, `11_global_search_phoneB.png` |
| **13** | **Diagnostics Dashboard** | Bottom Tab 2 (`DIAGNOSTICS`) | Tab switch / Back | Header ("SYSTEM DIAGNOSTICS", "Real-time telemetry and engine health") | Visible (Tab 2) | Navigation links (`COMMUNICATION HEALTH →`, `MESH TOPOLOGY →`, `MODEL STATUS →`), Packet I/O telemetry cards, Active Transports status, DTN Queue depth & pressure, System uptime | Vertical scrollable Column | Background: `#1B1F1D`, Cards: `#242A27`, Metric pills: `#2B322E` | Normal: `#2E7D32`, Warning: `#FFB300`, Critical: `#D84315` | Idle, Active traffic flowing, High buffer pressure, Outage state | `15_diagnostics_dashboard_phoneA.png`, `15_diagnostics_dashboard_phoneB.png` |
| **14** | **Communication Health Panel** | Diagnostics -> `COMMUNICATION HEALTH →` | Back arrow -> Diagnostics | Header ("COMMUNICATION HEALTH", Node ID, Health Score badge: 100%) | Hidden | Overall Health Banner, Delivery Health Card (ACK rate, loss rate), Active Transports Card (Wi-Fi UDP, Wi-Fi Direct, Bluetooth), Transport Bandwidth Card, Peer Quality Matrix, `VIEW MESH TOPOLOGY →` | Vertical scrollable Column | Background: `#1B1F1D`, Cards: `#242A27`, Badges: `#2B322E` | 100% Score: `#2E7D32`, Degraded: `#FFB300`, Offline: `#D84315` | 100% Health, Single transport connected, Transport failover active, All radios down | `16_communication_health_phoneA.png`, `16_communication_health_phoneB.png` |
| **15** | **Mesh Topology Canvas** | Diagnostics/Health -> `MESH TOPOLOGY →` | Back arrow -> Health | Header ("MANET MESH TOPOLOGY", Node count, Link count, Simulation mode toggle) | Hidden | Interactive 2D canvas with node circles, link lines colored by RSSI/quality, selected node inspector sheet, topology controls (Zoom, Reset, Add Mock Node) | Gesture drag/zoom canvas | Background: `#121614` (Deep Canvas), Node circles: `#2E7D32`, Links: `#0288D1` | Local Node: `#2E7D32` (halo), Peer: `#0288D1`, Relay: `#FFB300` | 2-node direct, 3-node linear, Partitioned graph, Node selected details | `20_mesh_topology_phoneA.png`, `20_mesh_topology_phoneB.png` |
| **16** | **Field Radio Settings** | Bottom Tab 3 (`SETTINGS`) | Tab switch / Back | Header ("FIELD RADIO SETTINGS", "Hardware telemetry & operational parameters") | Visible (Tab 3) | Interface Theme selector (System, Sand, Charcoal), Active Transport Link cards (Wi-Fi Multicast, Bluetooth SPP, Wi-Fi Direct P2P), Auto Transport Failover switch, DTN storage status, Neural Models audit link (`VIEW AUDIT →`) | Vertical scrollable Column | Background: `#1B1F1D`, Cards: `#242A27`, Selector capsules: `#2B322E` | Active transport: `#2E7D32` outline, Inactive: `#333B37` | System Dark, Sand Light, Failover ON/OFF, Model prewarmed | `18_settings_field_radio_phoneA.png`, `18_settings_field_radio_phoneB.png` |
| **17** | **Neural Model Status & Audit** | Settings -> `VIEW AUDIT →` | Back arrow -> Settings | Header ("ON-DEVICE NEURAL MODELS", "INT8 Sherpa-ONNX & Neural VITS TTS") | Hidden | Active ASR model card (Whisper-Tiny quantized, single-active resident), 10 Indic TTS voice status cards (Prewarmed, Resident, Evicted), Memory RSS meter, Voice test trigger | Vertical scrollable Column | Background: `#1B1F1D`, Cards: `#242A27`, Badges: `#2B322E` | Resident: `#2E7D32`, Evicted/Cached: `#8D9993`, Testing: `#0288D1` | Idle, Model loading, Voice playing, All 10 voices audited | `19_model_status_audit_phoneA.png`, `19_model_status_audit_phoneB.png` |
| **18** | **Distress Location & Map View** | Emergency Distress -> tap coordinates | System Intent -> External Maps / Back | N/A (External Intent / Map Intent) | Hidden | Coordinates card (`geo:lat,lon?q=lat,lon(Distress)`), Compass heading, Launch Maps button | Card inside Emergency Bubble | Surface: `#242A27`, Link: `#0288D1` | Alert: `#D84315`, Map Action: `#0288D1` | Valid GPS lock, Approximate network location, Stale location | Verified via intent tests & demo video |
| **19** | **Add Contact Modal Dialog** | Contacts Screen -> `+ ADD CONTACT` | Save / Cancel | Modal Dialog Header ("ADD TACTICAL CONTACT") | Hidden | Node ID input field (numerical), Callsign input field, Display Name input field, Trust Level radio group (`UNVERIFIED`, `TRUSTED`), `CANCEL` button, `SAVE` button | None (Modal) | Surface: `#242A27`, Input background: `#1B1F1D`, Border: `#333B37` | Focus border: `#2E7D32`, Text: `#F6F4ED` | Empty inputs, Valid inputs, Duplicate ID error | `07_contacts_screen_phoneA.png` |
| **20** | **SIH Mission Demo & Scenario** | Diagnostics -> `SIH MISSION DEMO →` | Back arrow -> Diagnostics | Header ("SIH 2026 MISSION DEMO", Scenario selector) | Hidden | Disaster triage simulation controls, Flood rescue waypoint broadcast, Automated multi-hop packet generator, Live throughput chart | Vertical scrollable Column | Background: `#1B1F1D`, Cards: `#242A27` | Active phase: `#2E7D32`, Packet inject: `#0288D1` | Scenario idle, Scenario running, Completed | Accessible via debug intent / diagnostics |

---

## 3. Detailed Component Geometry & Layout Analysis

### 3.1 App Bars & Navigation Structure
- **Root Top App Bar (`TopRadioHeader`):**
  - Container height: `64.dp`
  - Horizontal padding: `16.dp`
  - Logo dimension: `24.dp x 24.dp` (Vector leaf/radar glyph `#2E7D32`)
  - Title: "iTantra" `fontSize = 18.sp`, `fontWeight = FontWeight.Bold`, `fontFamily = FontFamily.Monospace`
  - Status capsule row: Radio indicator icons (`Bluetooth`, `Wifi`), `READY` badge, `CH-1` channel badge.
  - Action icon: Settings gear (`24.dp` touch target expanded to `48.dp`).
- **Bottom Navigation Bar (`BottomNavBar`):**
  - Container height: `72.dp`
  - Background color: `ColorCharcoal` (`#1B1F1D`)
  - Border top: `1.dp` solid `ColorCharcoalBorder` (`#333B37`)
  - 4 navigation items with equal horizontal spacing:
    1. Radio: `Icons.Default.Radio`
    2. Chats: `Icons.Default.Chat`
    3. Diagnostics: `Icons.Default.Assessment` (or bar chart)
    4. Settings: `Icons.Default.Settings`
  - Selected state: Icon and text tinted with `ColorSage` (`#2E7D32`), top indicator bar or pill background.
  - Unselected state: Tinted with `TextSecondaryDark` (`#8D9993`).

### 3.2 Action Buttons & Controls
- **Push-To-Talk Button (`TacticalPttButton` / `RadioPttControl`):**
  - Height: `88.dp`, Width: `fillMaxWidth()`
  - Corner radius: `RoundedCornerShape(16.dp)`
  - Surface: `ColorCharcoalSurface` (`#242A27`), Border: `1.5.dp` solid `#2E7D32` (transitions to `#D84315` in Emergency mode)
  - Left icon: Large microphone (`32.dp`) inside circular background container
  - Primary text: "PRESS TO TALK" (`fontSize = 15.sp`, `FontWeight.Bold`, Monospace)
  - Subtext: "HOLD TO TRANSMIT • RELEASE TO SEND" (`fontSize = 11.sp`, Monospace)
  - Right badge: "PTT" pill badge (`#2E7D32` on `#143D30`)
- **Secondary Action Row (`WALKIE PTT`, `SEND DISTRESS`, `TEST PACKET`):**
  - Container height: `48.dp`
  - 3 equal-width buttons (weight 1.0) separated by `8.dp` spacing:
    1. `WALKIE PTT`: Normal tactical button, border `#333B37`
    2. `SEND DISTRESS`: Accent danger button, border `1.5.dp` solid `ColorAlert` (`#D84315`), red warning triangle icon
    3. `TEST PACKET`: Debug/verification button, border `#333B37`, green play icon
- **Language Selector Pill (`LanguageSelectorPill`):**
  - Height: `36.dp`
  - Shape: `RoundedCornerShape(50)` (Capsule)
  - Surface: `ColorCharcoalCapsule` (`#2B322E`), Border: `1.dp` solid `#333B37`
  - Left icon: Globe (`16.dp`), Label: e.g. "Hindi (हिंदी)", Right icon: Dropdown arrow (`16.dp`)

### 3.3 Cards & Data Display
- **Contact Cards (`ContactCard`):**
  - Height: dynamic (`~72.dp`–`88.dp`), Corner radius: `RoundedCornerShape(12.dp)`
  - Surface: `#242A27`, Border: `1.dp` solid `#333B37`
  - Left avatar: Circular node ID badge (`40.dp`) with trust halo
  - Body: Callsign in bold monospace, secondary line showing Node ID, interface (BT / Wi-Fi), and distance/hop
  - Right action: Direct Chat icon button
- **Nearby Device Cards (`NearbyDeviceCard`):**
  - Surface: `#242A27`, Border: `1.dp` solid `#333B37`
  - Signal telemetry: 4-bar RSSI graphic (`#2E7D32` for > -65 dBm, `#FFB300` for -65 to -85 dBm, `#D84315` for < -85 dBm)
  - Quick action: `CONNECT` or `PAIR` button

---

## 4. UI Inconsistencies & Anomalies Observed On Physical Handsets

1. **Card Text Wrapping Defect (Settings Transport Cards):**
   - **Location:** `SettingsScreen.kt` -> `ACTIVE TRANSPORT LINK` card grid.
   - **Phone B (`Note 10 Lite`):** The 3 transport cards (`Wi-Fi Multicast`, `Bluetooth SPP`, `Wi-Fi Direct P2P`) have fixed narrow horizontal widths.
   - Word wrapping splits words in half:
     - `Wi-Fi Multicast` renders as `Wi-Fi` / `Multicas` / `t`.
     - `Bluetooth SPP` renders as `Bluetoot` / `h SPP`.
     - `Wi-Fi Direct Routerless` renders as `Wi-Fi` / `Direct` / `Routerl` / `ess`.
   - **Phone A (`Galaxy A55 5G`):** Identical breaking behavior occurs when system font scale is set to default or large.
2. **Monospace Typography Fragmentation:**
   - Analysis of presentation source code reveals **650+ instances** of inline `fontFamily = FontFamily.Monospace` declared directly on `Text()` composables rather than using a centralized `MaterialTheme.typography` style.
3. **Competing Corner Radii:**
   - Buttons and cards exhibit conflicting corner radii:
     - Secondary buttons: `12.dp`
     - PTT button: `16.dp`
     - Cards: `12.dp` on some screens, `8.dp` on others, `14.dp` on health cards, and `20.dp` on dialogue containers.
4. **Bottom Navigation Bar Insets:**
   - Phone A has a 3-button navigation bar (`height: 135px` / `48dp`). Content correctly sits above navigation bar buttons using `.navigationBarsPadding()`.
   - Phone B uses gesture navigation mode (`height: 0px`). The bottom bar sits flush against the bottom physical bezel.
