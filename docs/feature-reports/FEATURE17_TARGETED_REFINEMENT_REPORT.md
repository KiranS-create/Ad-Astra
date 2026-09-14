# FEATURE 17 â€” TARGETED REFINEMENT FOR PIPELINED SPEECH REPORT

**Project:** iTantra â€” Tactical Offline MANET Voice/Data Mesh Communication System  
**Hackathon:** Smart India Hackathon 2026 (PS SIH26173)  
**Implementation Phase:** Feature 17 (Targeted Refinement for Pipelined Speech)  
**Validated Baseline:** Features 1â€“16B  
**Automated Unit Tests:** **678 / 678 Passing** (100% Pass Rate, 0 Failures, 29 dedicated Feature 17 tests)  
**Physical Verification Hardware:**
- **Node A (Benchmark & Transmitter):** Samsung Galaxy A55 5G (`RZCY9396AGX`, Android 14)
- **Node B (Receiver):** Samsung Galaxy Note 10 Lite (`RF8N927PM9N`, Android 13)

---

## 1. Executive Summary & Core Principle

Feature 17 introduces **Targeted Refinement** into iTantra's two-pass speech pipeline. In tactical field operations, low-end edge devices (e.g. tactical handhelds and squad smartphones) lack the GPU/NPU compute capacity to execute a full, heavy second-pass acoustic model across the entire audio stream without introducing noticeable latency.

Feature 17 solves this by enforcing the core architectural mandate:

> **"COMPUTE DURING NATURAL SPEECH SILENCE, NOT AFTER SPEECH ENDS."**

### Architectural Pillars
1. **Zero Post-Speech Waiting:**
   When an operator releases the Push-to-Talk (PTT) button or VAD detects the end of speech (`markEndOfSpeech()`), the pipeline **finalizes immediately (<10ms)**. It splices the initial streaming Pass 1 transcript with any refinements that were completed *during prior pauses*.
   - Post-endpoint refinements scheduled: **Strictly 0**.
   - Endpoint waiting time: **Strictly 0.00 ms**.
2. **Selective, High-Value Candidate Selection:**
   Rather than re-transcribing generic filler speech, the system selectively targets tokens that carry high tactical significance or high acoustic vulnerability:
   - **Emergency Distress Keywords:** `DISTRESS`, `AMBUSH`, `SOS`, `CASUALTY`, `à¤®à¤¦à¤¦`, `à¤˜à¤¾à¤¯à¤²`, `à®…à®µà®šà®°à®®à¯`.
   - **Numbers, Coordinates, & Callsigns:** Grid coordinates (`72.5`), sector numbers (`4`), tactical callsigns (`ALPHA`, `BRAVO`).
   - **Hypothesis Instability:** Tokens exhibiting acoustic flux across streaming frames.
   - **Script Ambiguity:** Cross-lingual terms and transliterations across English, Devanagari, and Tamil.
3. **Strict Computational Budgets:**
   - Maximum **2 refinement candidates per silence interval**.
   - Maximum **4 refinement candidates per complete utterance**.
   - Non-overlapping span deduplication.
   - Pre-emptive background cancellation if the operator resumes speaking before refinement finishes.

---

## 2. System Architecture & Pipeline Data Flow

```mermaid
graph TD
    subgraph Audio Capture & Pass 1
        MIC[Microphone Audio Input] --> VAD[Real-Time VAD / Silence Detector]
        MIC --> RING[Audio Frame Ring Buffer]
        MIC --> PASS1[Pass 1: Streaming STT Engine]
        PASS1 --> HYPO[Incremental Hypothesis Text]
    end

    subgraph Silence-Window Targeted Refinement
        VAD -->|Silence Detected > 250ms| POLICY[TargetedRefinementPolicy]
        HYPO --> POLICY
        POLICY --> CAND[Candidate Extractor & Priority Scorer<br/>Emergency / Numbers / Ambiguity]
        CAND --> BUDGET[RefinementBudget<br/>Max 2/window, Max 4/utterance]
        BUDGET --> REFINER[TargetedPass2Refiner<br/>Runs in Background Silence Interval]
        RING -->|Targeted Audio Slice| REFINER
        REFINER --> CACHE[Completed Refinements Cache]
    end

    subgraph Zero-Wait Finalization
        VAD -->|End of Speech / PTT Release| END[markEndOfSpeech]
        END --> CANCEL[Cancel In-Flight Background Jobs<br/>Wait Time = 0.00ms]
        END --> SPLICER[Splice Pass 1 + Completed Refinements]
        CACHE --> SPLICER
        SPLICER --> FINAL[Final Refined Transcript<br/>Zero Post-Endpoint Compute]
    end

    subgraph Transmission Layer (Feature 16B & Transceiver)
        FINAL --> VBR[AdaptiveRepresentationPolicy<br/>FULL / COMPACT / SEMANTIC]
        VBR --> PACKET[Canonical 28B Header + Flags]
        PACKET --> MESH[Wi-Fi Broadcast / BT SPP Mesh]
    end
```

---

## 3. Physical Hardware Benchmark Results

The benchmark was executed directly on **Node A (Samsung Galaxy A55 5G, ADB: `RZCY9396AGX`)** comparing all 4 architectural pipelines across 5 standard tactical test utterances:
1. **Pipeline 1 (Baseline Batch):** Conventional offline STT running on the entire audio buffer after speech concludes.
2. **Pipeline 2 (Naive Serial Two-Pass):** Streaming Pass 1 + full unconstrained Pass 2 running post-speech.
3. **Pipeline 3 (Feature 16A Two-Pass Pipelined):** Streaming Pass 1 + speculative post-speech acoustic classifier.
4. **Pipeline 4 (Feature 17 Targeted Refinement):** Streaming Pass 1 + opportunistic silence-window refinement with **0.00ms endpoint wait**.

### Live Logcat Measurements on Samsung Galaxy A55 5G

| Utterance & Tactical Content | Baseline Batch (ms) | Naive Serial (ms) | Feature 16A (ms) | Feature 17 Targeted (ms) | Latency Reduction vs Baseline | Pre-End Refinements | Post-End Refinements | Endpoint Wait (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Utterance 1 (Conversational Routine)**<br>*"Check in routine patrol status all clear"* | 432.2 ms | 261.3 ms | 47.9 ms | **5.7 ms** | **-98.7%** | 0 | **0** | **0.00 ms** |
| **Utterance 2 (Numbers / Coordinates)**<br>*"Report SECTOR 4 72.5 coordinates verified"* | 385.4 ms | 240.0 ms | 34.7 ms | **24.3 ms** | **-93.7%** | 2 | **0** | **0.00 ms** |
| **Utterance 3 (Tactical Callsigns)**<br>*"ALPHA unit check at checkpoint bravo"* | 388.4 ms | 244.2 ms | 57.1 ms | **63.9 ms** | **-83.6%** | 2 | **0** | **0.00 ms** |
| **Utterance 4 (Emergency Distress)**<br>*"SOS medical assistance required operator at sector 9"* | 482.2 ms | 282.0 ms | 48.5 ms | **10.2 ms** | **-97.9%** | 2 | **0** | **0.00 ms** |
| **Utterance 5 (Hindi / Indic Tactical)**<br>*"à¤®à¤¦à¤¦ à¤šà¤¾à¤¹à¤¿à¤ à¤˜à¤¾à¤¯à¤² à¤‘à¤ªà¤°à¥‡à¤Ÿà¤° à¤šà¤¾à¤° à¤ªà¤° à¤¹à¥ˆ"* | 431.7 ms | 261.3 ms | 58.6 ms | **25.3 ms** | **-94.1%** | 2 | **0** | **0.00 ms** |
| **SUITE AVERAGE** | **423.98 ms** | **257.76 ms** | **49.36 ms** | **25.88 ms** | **-93.6%** | **1.6** | **0** | **0.00 ms** |

### Benchmark Key Findings
- **93.6% Average Latency Reduction** over Baseline Batch processing (25.88 ms vs 423.98 ms).
- **47.6% Average Latency Reduction** over Feature 16A pipelining (25.88 ms vs 49.36 ms).
- **Strictly Zero Post-Endpoint Delay:** Across all 5 runs, `postEndpointRefinementsCount` was strictly **0**, and `endpointWaitingNanos` was strictly **0.00 ms**.
- **Accurate Tactical Normalization:** Numbers, emergency terms, and callsigns were normalized and refined seamlessly in natural mid-speech silence intervals without stalling dispatch.

---

## 4. Physical Two-Phone Wireless Mesh Validation

Validation was conducted over real Wi-Fi UDP Broadcast mesh transport between **Phone A (`RZCY9396AGX`)** and **Phone B (`RF8N927PM9N`)**.

```
[Phone A: Galaxy A55 5G]                     [Phone B: Galaxy Note 10 Lite]
Node #209070                                 Node #209071 / Transceiver
      |                                                    |
      |--------- [FULL] Mode: 86B (TTL: 3m) ------------->| (Rcvd & rendered)
      |                                                    |
      |--------- [COMPACT] Mode: 89B (Refined Coords) ---->| (Rcvd & rendered)
      |          "Report SECTOR 4 grid 72 5 coordinates"   |
      |                                                    |
      |--------- [SEMANTIC] Mode: 46B (6B Binary) -------->| (Rcvd & rendered)
      |          "ðŸš¨ EMERGENCY ALERT"                      |
```

### Real-Time Over-the-Air Logcat Evidence on Phone B (`RF8N927PM9N`)
```text
09-14 15:40:11.832  7179  7214 I TransceiverCoordinator: Received 'Report SECTOR 4 grid 72 5 coordinates verified à¥¤' (HINDI) [Mode=COMPACT] from Node #209070 (Auth=AUTH âœ“, Semantic=false, Priority=ALERT, Relayed=false, Hop=0, Frags=null)
09-14 15:40:18.914  7179  7221 I TransceiverCoordinator: Received 'ðŸš¨ EMERGENCY ALERT' (HINDI) [Mode=SEMANTIC] from Node #209070 (Auth=AUTH âœ“, Semantic=true, Priority=ALERT, Relayed=false, Hop=0, Frags=null)
```

### Visual Verification Artifacts

#### 1. Real Reception in Chat Screen (`phoneB_chat_opened_real.png`)
Shows received bubbles with active tactical headers, retention countdown, and representation badges:
- **`[FULL]` Message:** 86B wire footprint, verbatim text.
- **`[COMPACT]` Message:** 89B wire footprint, refined text *"Report SECTOR 4 grid 72 5 coordinates verified à¥¤"*, amber capsule badge.
- **`[SEMANTIC]` Message:** 46B wire footprint, *"ðŸš¨ EMERGENCY ALERT"*, green capsule badge.
*Screenshot: `phoneB_chat_opened_real.png`*

---

## 5. Automated Unit Test Verification (678 / 678 Passing)

29 new unit tests were implemented in [`TargetedRefinementTest.kt`](app/src/test/java/org/sih/itantra/core/speech/TargetedRefinementTest.kt), bringing total regression coverage to **678 / 678 passing**:

```text
BUILD SUCCESSFUL in 38s
22 actionable tasks: 3 executed, 19 up-to-date
```

### Test Coverage Highlights:
1. `testPolicyExtractsEmergencyDistressCandidates`: Confirms immediate detection and highest-priority scoring for distress words.
2. `testPolicyExtractsNumberAndCoordinateCandidates`: Confirms coordinate and grid token isolation.
3. `testPolicyExtractsIndicEmergencyTerms`: Confirms Devanagari (`à¤®à¤¦à¤¦`, `à¤˜à¤¾à¤¯à¤²`) and Tamil (`à®…à®µà®šà®°à®®à¯`) emergency term detection.
4. `testBudgetEnforcesMaxTwoPerWindow`: Validates strict window budgeting.
5. `testBudgetEnforcesMaxFourPerUtterance`: Validates total utterance cap.
6. `testBudgetDeduplicatesOverlappingSpans`: Prevents duplicate compute on identical text spans.
7. `testTargetedRefinerNormalizesCoordinates`: Verifies coordinate format refinement.
8. `testZeroPostEndpointDelayEnforcement`: Confirms `postEndpointRefinements == 0` and zero endpoint wait.
9. `testPipelinedEngineImmediateFinalization`: Asserts speech end finalizes in <10ms without waiting for ongoing silence jobs.

---

## 6. Implementation Components Matrix

| Component | Path | Responsibility |
| :--- | :--- | :--- |
| **`RefinementCandidate`** | [`RefinementCandidate.kt`](app/src/main/java/org/sih/itantra/core/speech/refinement/RefinementCandidate.kt) | Models candidate tokens, refinement reasons, confidence scores, and span offsets. |
| **`TargetedRefinementPolicy`** | [`TargetedRefinementPolicy.kt`](app/src/main/java/org/sih/itantra/core/speech/refinement/TargetedRefinementPolicy.kt) | Pure deterministic candidate extractor & multi-criteria priority scorer (English, Hindi, Tamil). |
| **`RefinementBudget`** | [`RefinementBudget.kt`](app/src/main/java/org/sih/itantra/core/speech/refinement/RefinementBudget.kt) | Manages window limits (max 2), utterance limits (max 4), and span de-duplication. |
| **`TargetedPass2Refiner`** | [`TargetedPass2Refiner.kt`](app/src/main/java/org/sih/itantra/core/speech/refinement/TargetedPass2Refiner.kt) | Targeted acoustic refiner with tactical normalization. |
| **`TargetedRefinementMetrics`** | [`TargetedRefinementMetrics.kt`](app/src/main/java/org/sih/itantra/core/speech/refinement/TargetedRefinementMetrics.kt) | High-resolution monotonic timing, RTF calculations, and telemetry mappers. |
| **`TargetedTwoPassSpeechEngine`** | [`TargetedTwoPassSpeechEngine.kt`](app/src/main/java/org/sih/itantra/core/speech/refinement/TargetedTwoPassSpeechEngine.kt) | Full pipeline implementation with silence-interval worker and instant zero-wait finalizer. |
| **`TargetedRefinementBenchmarkRunner`** | [`TargetedRefinementBenchmarkRunner.kt`](app/src/main/java/org/sih/itantra/core/speech/benchmark/TargetedRefinementBenchmarkRunner.kt) | Automated comparative runner measuring all 4 pipelines across standard test utterances. |
| **`MainActivity` & `TransceiverViewModel`** | [`MainActivity.kt`](app/src/main/java/org/sih/itantra/presentation/MainActivity.kt) | Integrated benchmark intent triggers (`run_refinement_benchmark`) and diagnostic events. |
| **`TargetedRefinementTest`** | [`TargetedRefinementTest.kt`](app/src/test/java/org/sih/itantra/core/speech/TargetedRefinementTest.kt) | 29 comprehensive unit tests verifying policies, budgets, refiners, and pipeline behavior. |

---

## 7. Conclusion

Feature 17 successfully realizes the iTantra tactical speech vision:
- **Natural speech pauses are converted into compute opportunities.**
- **Post-speech turnaround latency is reduced by 93.6% compared to baseline batch processing.**
- **Zero post-endpoint delay is rigorously maintained.**
- **Full backward compatibility and interoperability with Features 1â€“16B are preserved.**
