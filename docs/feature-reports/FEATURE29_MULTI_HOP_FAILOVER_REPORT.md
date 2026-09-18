# Feature 29 — End-to-End Multi-Hop Relay, Failover & Recovery Validation

> **Project**: SIH26173 — iTantra (Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access)  
> **Evaluation**: End-to-End Multi-Hop Relay, Transport Failover, DTN Recovery & Impairment Simulation  
> **Date**: September 18, 2026  
> **Standard of Proof**: Multi-Agent Disjoint Verification, Physical Hardware Telemetry & Deterministic Execution  

---

## 1. Executive Summary & Verification Matrix

Feature 29 provides end-to-end evidence and hardening across the entire iTantra networking chain:
$$\text{FULL / COMPACT / SEMANTIC\_BASE / SEMANTIC\_ENHANCED / CONTEXT\_DELTA}$$
$$\to \text{HMAC/CRC} \to \text{QoS} \to \text{Route Selection} \to \text{Relay Forwarding} \to \text{DTN/Store-Forward} \to \text{Failover} \to \text{Recovery} \to \text{Delivery}$$

### Verification Classification Summary:
1. **Physical Dual-Handset Transport Failover & DTN**: **`PHYSICAL PASS`**
   - Physically executed on Samsung Galaxy A55 5G (`RZCY9396AGX`) and Samsung Galaxy Note 10 Lite (`RF8N927PM9N`).
   - Wi-Fi Direct disconnect triggered, confirmed offline local DTN retention (`LOCAL QUEUE: Radio offline`), reconnection, and recovery transmission without duplicate delivery.
2. **Deterministic 3-Node & 4-Node Multi-Hop Relay**: **`SYNTHETIC PASS`**
   - 15/15 deterministic tests passed in `Feature29MultiHopRelayTest.kt` verifying representation preservation, TTL expiry, duplicate suppression, loopback drops, HMAC hop invariance, and CRC link error detection.
3. **Network Impairment & Performance Benchmark**: **`SYNTHETIC PASS`**
   - 90 deterministic discrete-event simulation scenarios executed across bandwidth (10 kbps–1 Mbps), packet loss (0%–50%), jitter (0–500 ms), burst losses, and temporary partitions.

---

## 2. Agent 1 — Multi-Hop Relay Engineer: Deterministic Test Results

All 15 required core routing, relay, and representation preservation test cases are implemented in `app/src/test/java/org/sih/itantra/core/network/Feature29MultiHopRelayTest.kt`:

| # | Test Case | Scope / Topology | Evaluated Invariant | Result |
|---|---|---|---|---|
| **01** | `test01_fullForwarding` | 3-node (`A->B->C`) & 4-node (`A->B->C->D`) | Byte-for-byte UTF-8 payload preservation, monotonic TTL decrement, `FLAG_FORWARDED` addition | **PASS** |
| **02** | `test02_compactForwarding` | 4-node (`A->B->C->D`) | Compressed byte stream preserved across all intermediate hops; lossless decompression at destination | **PASS** |
| **03** | `test03_semanticBaseForwarding` | 4-node (`A->B->C->D`) | Exact 8-byte binary representation preserved without intermediate text expansion | **PASS** |
| **04** | `test04_semanticEnhancedForwarding` | 4-node (`A->B->C->D`) | Composite base layer + text enhancement preserved bit-for-bit across hops | **PASS** |
| **05** | `test05_contextDeltaForwarding` | 4-node (`A->B->C->D`) | Monotonic context versioning, count delta, severity, and subtype update applied at destination | **PASS** |
| **06** | `test06_ttlExpiry` | Intermediate Hop (Node B) | Packet with TTL=1 dropped at intermediate router (`DROP_TTL_EXPIRED`); prevents infinite looping | **PASS** |
| **07** | `test07_duplicatePacketSuppression` | Intermediate Hop (Node B) | Second arrival of `(sourceDeviceId, sequenceNumber)` dropped (`DROP_DUPLICATE`) | **PASS** |
| **08** | `test08_relayLoopPrevention` | Local Router (Node B) | Echo packet with `sourceDeviceId == localDeviceId` dropped immediately (`DROP_SELF`) | **PASS** |
| **09** | `test09_staleContext` | Destination (Node C) | Older version delta ($v2 \le v3$) rejected gracefully (`REJECTED STALE`) | **PASS** |
| **10** | `test10_missingContext` | Destination (Node C) | Unknown context ID triggers graceful textual fallback without application crash | **PASS** |
| **11** | `test11_conflictingContext` | Destination (Node C) | Conflicting delta rejected (`REJECTED CONFLICT`), retaining active context | **PASS** |
| **12** | `test12_invalidHmac` | Relay & End-to-End | HMAC remains valid across hops (router hop invariance); payload tampering or wrong key rejected | **PASS** |
| **13** | `test13_corruptedCrc` | Link Hop | Bit flip in serialized packet detected by CRC-32 (`CorruptPacketException`) | **PASS** |
| **14** | `test14_representationDowngrade` | Transmitter Decision | Downgrades from DELTA to BASE or SEMANTIC to COMPACT only when confidence $< 0.85$ or context absent | **PASS** |
| **15** | `test15_semanticMeaning` | 4-node (`A->B->C->D`) | Emergency category, subtype, severity, count, and sector remain strictly unchanged across hops | **PASS** |

---

## 3. Agent 2 — Physical Transport Failover & Recovery Evidence

### Physical Testbed:
- **Phone A**: Samsung Galaxy A55 5G (`RZCY9396AGX`), Android 16 (Node 209070)
- **Phone B**: Samsung Galaxy Note 10 Lite (`RF8N927PM9N`), Android 12 (Node 477124)
- **Transport**: Native Android Wi-Fi Direct P2P (Wi-Fi P2P Group Owner / Client) + Local DTN Storage

### Observed Physical Sequence:
1. **Wi-Fi Direct Active**: Phone A and Phone B established direct P2P link on port 42889. Initial packet exchange verified.
2. **Intentional Transport Severing**: Wi-Fi Direct group disconnect triggered via `disconnect_wifidirect`.
3. **Failover to Local DTN**:
   - `CommunicationHealthState` transitioned from `HEALTHY` to `OFFLINE`.
   - UI banner displayed: `OFFLINE • MESSAGES WILL BE QUEUED LOCALLY`.
   - DTN inspection: `LOCAL QUEUE: Radio offline: Message will be held in local DTN storage until a transport connects.`
   - Queue action button updated to: `QUEUE FOR DELIVERY: Local DTN storage (Offline)`.
4. **Link Restoration & Recovery**:
   - Reconnected Wi-Fi Direct link (`connect_wifidirect --es target_node_id 209070`).
   - Post-recovery packet (`POST_RECOVERY_CONFIRMATION`) transmitted and received.
   - Message received without duplication (`DeliveryState: RECEIVED`, `ForwardingAction: DIRECT`).

### Physical Artifacts:
- **Phone A Screen**: `feature29_physical_failover_phoneA.png` (Live transmission post recovery)
- **Phone B Screen**: `feature29_physical_failover_phoneB.png` (Local DTN offline buffering state)

---

## 4. Agent 3 — Network Impairment & Performance Benchmark

Evaluated across 90 configurations using `scratch/run_network_impairment_benchmark.py`:

### 4.1 Bandwidth vs. Latency (5% Loss, 50 ms Jitter)
| Bandwidth | CONTEXT_DELTA (35B) | SEMANTIC_BASE (39B) | COMPACT (63B) | FULL (79B) | Bitrate Benefit |
|---|---|---|---|---|---|
| **10 kbps** | **57.7 ms** | **62.2 ms** | **83.2 ms** | **95.8 ms** | **1.66x faster than FULL** |
| **20 kbps** | **37.1 ms** | **40.9 ms** | **52.6 ms** | **59.1 ms** | **1.59x faster than FULL** |
| **50 kbps** | **34.5 ms** | **37.9 ms** | **47.7 ms** | **34.4 ms** | **Optimal Link Utilization** |
| **100 kbps** | **40.4 ms** | **40.4 ms** | **34.6 ms** | **34.9 ms** | **Bounded propagation** |
| **1 Mbps** | **33.2 ms** | **40.3 ms** | **32.3 ms** | **24.9 ms** | **Overhead negligible** |

### 4.2 Loss Rate Resilience (50 kbps, 50 ms Jitter)
| Loss Rate | Representation | Delivery Rate | Retransmissions | Avg Latency (ms) | Total Bytes Sent |
|---|---|---|---|---|---|
| **0%** | **CONTEXT_DELTA** | 100.0% | 0 | 26.8 ms | 4,300 B |
| **0%** | **FULL** | 100.0% | 0 | 29.6 ms | 8,700 B |
| **10%** | **CONTEXT_DELTA** | 100.0% | 13 | 57.3 ms | 4,859 B |
| **10%** | **FULL** | 100.0% | 12 | 55.3 ms | 9,744 B |
| **25%** | **CONTEXT_DELTA** | 100.0% | 36 | 114.9 ms | 5,848 B |
| **25%** | **FULL** | 100.0% | 40 | 119.2 ms | 12,180 B |
| **50%** | **CONTEXT_DELTA** | **97.0%** | 106 | 280.4 ms | **8,729 B (-55% vs FULL)** |
| **50%** | **FULL** | **100.0%** | 125 | 440.2 ms | **19,575 B** |

### 4.3 Burst Loss & 30s Partition DTN Recovery
- **Burst Loss (20% Gilbert-Elliott, Burst Size = 3)**:
  - CONTEXT_DELTA delivered 100.0% with 28 retransmissions (total 5,504 B).
  - FULL delivered 98.0% with 34 retransmissions (total 11,658 B).
- **Temporary 30-Second Link Partition**:
  - In all representations, 100% of packets generated during the outage were retained in the local DTN buffer.
  - Upon channel recovery, queues drained immediately with **100.0% delivery** and **0% duplicates**.

---

## 5. Agent 4 — QA / Truthfulness Auditor Matrix

| Claim Item | Evaluated Reality | Standard Qualification | Audit Verdict |
|---|---|---|---|
| **Wi-Fi Direct Validation** | Single-hop P2P link between Phone A & B | Physically verified on actual hardware | **PHYSICAL PASS** |
| **Multi-Hop Relay** | 3-node (`A->B->C`) and 4-node (`A->B->C->D`) | Validated via deterministic JVM unit tests | **SYNTHETIC PASS** |
| **Failover / DTN Retention** | Link severing and queue buffering on hardware | Confirmed via live UI states and logcat | **PHYSICAL PASS** |
| **HMAC Security** | HMAC-SHA256 authentication tag (8-byte truncated) | Origin authentication and tamper detection; NOT payload encryption | **AUDITED & QUALIFIED** |
| **CRC-32 Error Detection** | IEEE 802.3 CRC-32 link checksum | RF bit flip detection; non-cryptographic | **AUDITED & QUALIFIED** |
| **Delivery Guarantee** | Stop-and-Wait ARQ + 10-minute DTN buffer | Reliable best-effort MANET/DTN; NOT guaranteed under permanent partition | **AUDITED & QUALIFIED** |

---

## 6. Integrator Build & Regression Verification

- **Unit Test Suite**: Passed (`Feature29MultiHopRelayTest` 15/15 passed)
- **Assembly Artifacts**:
  - `app-debug.apk`: Built successfully
  - `app-release-unsigned.apk`: Built successfully
- **Repository Cleanliness**: No uncommitted work; strictly disjoint files; zero push to remote.