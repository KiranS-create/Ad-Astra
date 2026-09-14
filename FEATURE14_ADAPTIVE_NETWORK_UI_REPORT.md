# FEATURE 14 — ADAPTIVE NETWORK-STATE UI REPORT
**iTantra Tactical MANET Radio System**
**Smart India Hackathon 2026 · PS SIH26173**

---

## Executive Summary

Feature 14 delivers an operator-centric, strictly truthful **Adaptive Network-State UI** for iTantra. The primary goal of Feature 14 is to transition the communication user experience from a generic, passive notice system to an active, truthful system where all UI interaction affordances dynamically adapt to the real physical state of the underlying network hardware, transports, queues, and MANET routes.

### Primary States & Adaptive Behavior

| Canonical Network Mode | Physical / Logical State | Banner Display | Composer Action Pill | PTT Button Adaptation | Strict Operator Expectation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`HEALTHY`** | Active transports connected, direct line-of-sight peer reachable, 0 backpressure. | `● LINK HEALTHY · 2 TRANSPORTS ACTIVE [ NOMINAL ]` | `DIRECT` | `HOLD TO TALK` *(Real-time transmission)* | Direct link confirmed. Transmissions deliver in real-time. |
| **`LIMITED`** | Single transport active or running broadcast fallback socket with reduced direct peers. | `⌁ LIMITED LINK · BROADCAST / FEW REACHABLE PEERS [ LIMITED ]` | `BROADCAST` | `HOLD TO TALK` *(Broadcast reachability)* | Reduced reachability: Sending via broadcast. Delivery unconfirmed until ACK received. |
| **`DEGRADED`** | Link quality degraded, retries active, or high transmission loss. | `▲ LINK DEGRADED · BACKPRESSURE / RETRIES [ DEGRADED ]` | `RETRY` | `HOLD TO TALK` *(Retries active)* | Degraded link: Delivery retries active. Unconfirmed until ACK received. |
| **`OFFLINE`** | All physical radio interfaces (Wi-Fi, Bluetooth) disconnected / off. | `✕ OFFLINE · MESSAGES WILL BE QUEUED LOCALLY [ OFFLINE ]` | `LOCAL QUEUE` | `QUEUE FOR DELIVERY` *(Local DTN storage)* | Radio offline: Message held in local DTN storage until a transport connects. **Never implies delivery.** |
| **`CONGESTED`** | QoS transmission queue experiencing backpressure (>20 pkts or congestion signal). | `⚡ NETWORK CONGESTED · 25 PKTS QUEUED [ CONGESTED ]` | `BUFFERED` | `HOLD TO TALK (QUEUED)` *(QoS queue backpressure)* | QoS backpressure: Normal messages delayed. Emergency alerts pre-empt immediately. |
| **`WAITING_FOR_ROUTE`**| Transports active, but peer destination is currently unreachable in routing table. | `⌕ WAITING FOR ROUTE · RETAINING MESSAGE [ DISCOVERY ]` | `WAIT ROUTE` | `RETAIN UNTIL ROUTE` *(Awaiting route discovery)* | Destination unreachable: Message retained locally pending MANET route resolution. **Distinguished from FAILED.** |
| **`DTN_STORED`** | Message bundle stored in Delay-Tolerant Networking store pending data ferry. | `⏸ DTN STORED · WILL FORWARD WHEN ROUTE RETURNS [ STORED ]` | `DTN STORE` | `STORE & FORWARD (DTN)` *(Buffered with 10m TTL)* | DTN store active: Bundle stored locally. Will auto-forward on next encounter. **Distinguished from DELIVERED.** |

---

## 1. Strict Tactical Truthfulness Contract

In military, disaster response, and tactical operations, misleading status indicators cost lives. Feature 14 enforces a strict truthfulness contract across the entire codebase:

1. **Zero False Delivery Promises:**
   - When the radio is OFFLINE or messages are held in local queues, the UI explicitly states: `"Message will be held in local DTN storage until a transport connects."`
   - It **never** labels a message as "sent" or implies transmission when the packet was merely buffered.
2. **Distinct Route Waiting vs. Delivery Failure:**
   - When a peer route is absent but radios are operational, the message is labeled `WAITING FOR ROUTE` and the banner reads `"WAITING FOR ROUTE · RETAINING MESSAGE"`.
   - It is **strictly distinguished from `FAILED`**, so operators understand the system is still actively discovering a route and retaining the message.
3. **Distinct DTN Buffering vs. Acknowledged Delivery:**
   - Bundles stored in the Delay-Tolerant Networking store are labeled `STORED FOR FORWARDING` with badge `[ STORED ]`.
   - It is **strictly distinguished from `DELIVERED / ACK`**, ensuring operators know the packet is deferred and waiting for a routing relay or data ferry encounter.
4. **Emergency Priority Bypass Affordance:**
   - In `CONGESTED` and `OFFLINE` modes, the emergency action `[ ⚠ DISTRESS ]` remains prominent, high-contrast, and fully discoverable.
   - An explicit priority bypass callout informs operators:
     - Congested: `"EMERGENCY DISTRESS PRE-EMPTS CONGESTION QUEUE (PRIORITY 1)"`
     - Offline: `"Distress will queue at Priority 1 and burst on reconnection"`

---

## 2. Canonical Presentation Delivery Labels

Feature 14 unifies all radio message state badges beneath message bubbles using `AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel`:

```
QUEUED            -> "SCHEDULED"
SENDING           -> "TRANSMITTING"
ACK_PENDING       -> "AWAITING ACK"
ACKNOWLEDGED      -> "DELIVERED / ACK"
RELAYED           -> "RELAYED"
DTN_STORED        -> "STORED FOR FORWARDING"
WAITING_FOR_ROUTE -> "WAITING FOR ROUTE"
FAILED            -> "FAILED"
RECEIVED          -> "RECEIVED"
UNKNOWN           -> "STATUS UNKNOWN"
```

---

## 3. Architecture & Code Implementation Matrix

1. **Data Models (`org.sih.itantra.core.network.AdaptiveNetworkUiState.kt`):**
   - `AdaptiveNetworkMode`: 7 primary modes + UNKNOWN.
   - `AdaptiveComposerState`: Dynamic PTT labels, subtext, action pill, emergency bypass notice, and expectation text.
   - `AdaptiveNetworkBannerState`: Dynamic banner text, badge label, mode accent color, and visibility.
   - `AdaptiveNetworkUiState`: Consolidated immutable snapshot consumed by `IndividualChatScreen`.
2. **Domain Mapper (`org.sih.itantra.core.network.AdaptiveNetworkUiMapper.kt`):**
   - Pure, deterministic projection consuming canonical `CommunicationHealthState`, `IndividualChatHeaderState`, and `MessageRecord` history.
   - Zero state-machine duplication, zero additional caches, zero network protocol changes.
3. **Tactical UI Components:**
   - `NetworkContextBanner.kt`: Embedded beneath the chat header; displays mode symbol, uppercase tactical summary, and badge pill.
   - `AdaptiveNetworkNotice.kt`: Embedded directly above the composer row; renders action pill, expectation description, and emergency bypass callout.
   - `MessageRadioStateIndicator.kt`: Updated to use canonical delivery state labels (`resolveAdaptiveDeliveryLabel`).
   - `IndividualChatScreen.kt`: Observes `communicationHealthState`, derives `adaptiveUiState`, injects `NetworkContextBanner` and `AdaptiveNetworkNotice`, adapts PTT button label/subtext, and adjusts quick-send button semantics.

---

## 4. Test Verification Suite (580 / 580 Passing)

Unit tests in `AdaptiveNetworkUiMapperTest.kt` comprehensively validate:
- All 10 canonical delivery label mappings.
- Deterministic behavior in `HEALTHY`, `LIMITED`, `DEGRADED`, `OFFLINE`, `CONGESTED`, `WAITING_FOR_ROUTE`, and `DTN_STORED` modes.
- Strict assertions confirming absence of false "sent" / "delivered" claims in offline and buffered states.
- Emergency priority notices and queue pre-emption messaging.
- Route summaries across direct, relayed, recently heard, DTN, and disconnected peers.

```
Total Test Count: 580 / 580 passing
Failures: 0
Errors: 0
Build: BUILD SUCCESSFUL in 29s
```

---

## 5. Physical Smoke Test on Phone A (`RZCY9396AGX`, Samsung Galaxy A55 5G)

Deployed debug APK to Phone A and verified live on hardware:
1. **Live Adaptive Chat Screen:**
   - `NetworkContextBanner` rendered: `⌁ LIMITED LINK · BROADCAST / FEW REACHABLE PEERS` with `[ LIMITED ]` badge.
   - `AdaptiveNetworkNotice` rendered: `[ BROADCAST ] Reduced reachability: Sending via broadcast. Delivery unconfirmed until ACK received.`
   - PTT Button rendered: `HOLD TO TALK` with subtext `Broadcast reachability`.
   - Message Bubble state rendered: `◌ AWAITING ACK 13:08`.
2. **Technical Packet Inspector:**
   - Tap on message bubble smoothly expanded progressive disclosure inspector with full cryptographic, priority, and route details.
3. **Emergency Distress Workflow:**
   - Tapped `[ ⚠ DISTRESS ]`: Emergency Distress composer modal overlay opened instantly with tactical categories, GPS fix telemetry, and priority pre-emption.
