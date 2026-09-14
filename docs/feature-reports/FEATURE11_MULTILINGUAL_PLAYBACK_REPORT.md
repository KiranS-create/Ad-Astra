# Feature 11: Multilingual Playback & Language-Aware TTS — Implementation Report

**Feature:** Feature 11 — Multilingual Playback & Language-Aware TTS  
**Module:** Feature 11 — Multilingual Playback Engine  
**Date:** 2026-09-12  
**Status:** COMPLETE & VERIFIED (523/523 TESTS PASSING, PHYSICAL PHONE A SMOKE TESTED)  

---

## 1. Executive Summary

Feature 11 equips iTantra (Smart India Hackathon 2026, PS SIH26173) with intelligent, language-aware offline Text-to-Speech (TTS) synthesis and playback for received tactical messages across 10 constitutional Indic languages and English.

In tactical and disaster environments, team members and regional command operate across diverse languages (Hindi, Tamil, Telugu, Malayalam, Bengali, Gujarati, Marathi, Kannada, Odia, English). Feature 11 ensures that when a message is received, the system determines the message language, verifies whether the corresponding offline TTS model is installed locally on disk, and routes the audio synthesis to that specific voice model without falling back silently to an incorrect language.

### Core Engineering Principles:
1. **Accurate Offline Voice Routing:** Dynamically maps 10 Indic languages and English to offline neural ONNX voice models (`Piper` / `MMS`) with their respective sample rates (16,000 Hz or 22,050 Hz).
2. **Deterministic Resolution Hierarchy:** 
   1. Message explicit metadata
   2. Peer node language metadata
   3. Active app language fallback
   4. Explicit `UnknownLanguage` / `Unavailable` (CRITICAL: Never silently fall back to Hindi when another language is specified).
3. **Honest Readiness Reporting:** If an offline model pack is not installed on disk, the UI honestly displays `TTS UNAVAILABLE` with tactical amber badging, rather than misrepresenting model readiness or playing an incorrect voice.
4. **Emergency Priority Queue Handling:** Distress/emergency alerts bypass normal playback queues (`QUEUE_FLUSH`), preemption occurs immediately, and playback is routed with high-priority alarm audio attributes.
5. **Single-Active Model Policy:** Low/mid-range tactical handsets have strict RAM constraints. When a voice model of a different language is loaded, the previous resident ONNX model is cleanly unloaded/evicted to avoid out-of-memory errors.
6. **Tactical UI Integration:** Received message bubbles display a compact tactical language badge (`HI · TTS READY`, `TA · TTS READY`, `ML · TTS UNAVAILABLE`, `?? · LANGUAGE UNKNOWN`) and an interactive play/stop audio indicator.

---

## 2. Architecture & File Matrix

```
+-----------------------------------------------------------------------------------+
|                              RECEIVED PACKET / MESSAGE                             |
|              (id, peer, text, language = IndicLanguage.TAMIL, isDistress)          |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                                 TtsVoiceResolver                                  |
|   1. Check Message Language Metadata (TAMIL)                                      |
|   2. Query TtsVoiceRegistry for TAMIL profile                                      |
|   3. Verify Model Assets on Disk via ModelAssetManager                            |
+-----------------------------------------+-----------------------------------------+
                                          |
                   +----------------------+----------------------+
                   |                                             |
            [Assets Ready]                               [Assets Missing]
                   |                                             |
                   v                                             v
       TtsResolutionResult.Ready                    TtsResolutionResult.Unavailable
  (voiceProfile, sampleRateHz = 22050)                      (language = TAMIL)
                   |                                             |
                   v                                             v
         "TA · TTS READY" Badge                     "TA · TTS UNAVAILABLE" Badge
                   |                                             |
                   v                                             x (Playback disabled)
    TransceiverViewModel.playMessageVoice(...)
                   |
                   v
+-----------------------------------------------------------------------------------+
|                        SherpaOnnxTtsEngine / AudioTrack                           |
|       - Single-active model policy (evict previous language ONNX if changed)       |
|       - Synthesize audio samples                                                  |
|       - Write to AudioTrack (QUEUE_ADD for normal, QUEUE_FLUSH for distress)       |
+-----------------------------------------------------------------------------------+
```

### Files Created:
1. **`app/src/main/java/org/sih/itantra/core/tts/TtsLanguage.kt`**:
   - Enum covering `HINDI`, `GUJARATI`, `MARATHI`, `KANNADA`, `MALAYALAM`, `TAMIL`, `TELUGU`, `ODIA`, `BENGALI`, `ENGLISH`, `UNKNOWN`.
   - ISO-639-3 codes, 2-letter badge codes, numeric IDs, and bidirectional `IndicLanguage` mapping.
2. **`app/src/main/java/org/sih/itantra/core/tts/TtsVoiceProfile.kt`**:
   - Model profile data class: `voiceId`, `language`, `displayName`, `engineType`, `modelFileName`, `tokensFileName`, `sampleRateHz`, `speakerId`.
   - Engine type enum: `PIPER`, `MMS`, `MIMIC3`, `SYSTEM_FALLBACK`, `NONE`.
3. **`app/src/main/java/org/sih/itantra/core/tts/TtsVoiceRegistry.kt`**:
   - Central repository mapping `TtsLanguage` to offline ONNX voice model configurations.
   - Sample rates: 22,050 Hz (Piper models) or 16,000 Hz (MMS models).
   - Verifies disk availability via `ModelAssetManager`.
4. **`app/src/main/java/org/sih/itantra/core/tts/TtsVoiceResolver.kt`**:
   - Deterministic 4-tier resolution engine returning `TtsResolutionResult.Ready`, `Unavailable`, or `UnknownLanguage`.
   - Emergency distress priority flag logic.
5. **`app/src/main/java/org/sih/itantra/core/tts/TtsPlaybackState.kt`**:
   - Comprehensive state progression: `IDLE`, `LOADING_VOICE`, `SYNTHESIZING`, `PLAYING`, `COMPLETED`, `UNAVAILABLE`, `FAILED`.
   - `MessagePlaybackState` state holder with query helpers (`isPlaying`, `isBusy`, `canPlay`).
6. **`app/src/main/java/org/sih/itantra/presentation/components/MessageLanguageBadge.kt`**:
   - Tactical Composable chip displaying language code and readiness (`HI · TTS READY`, `TA · TTS READY`, `ML · TTS UNAVAILABLE`, `?? · LANGUAGE UNKNOWN`).
   - Tactical color coding (Emerald green for ready, tactical amber for unavailable, muted slate for unknown).
7. **`app/src/main/java/org/sih/itantra/presentation/components/TtsPlaybackIndicator.kt`**:
   - Tactical animation sub-bar displaying active TTS progression, volume/wave animation, and retry/dismiss affordances.
8. **`app/src/test/java/org/sih/itantra/core/tts/TtsVoiceResolverTest.kt`**:
   - 10 comprehensive unit tests covering all 10 Indic languages, sample rates, metadata priority, fallbacks, missing asset handling, and idempotence.
9. **`app/src/test/java/org/sih/itantra/core/tts/TtsLanguageRoutingTest.kt`**:
   - 5 unit tests covering `MessageRecord` routing, distress priority preemption, and no silent Hindi fallback.
10. **`app/src/test/java/org/sih/itantra/core/tts/TtsPlaybackStateTest.kt`**:
    - 7 unit tests covering state lifecycle progressions and utility predicates.

### Files Modified:
1. **`app/src/main/java/org/sih/itantra/presentation/viewmodel/TransceiverViewModel.kt`**:
   - Integrated `TtsVoiceRegistry` and `TtsVoiceResolver`.
   - Added `_messagePlaybackState` and public `messagePlaybackState: StateFlow<MessagePlaybackState>`.
   - Added `playMessageVoice(messageId, text, language, isEmergency)` and `stopVoicePlayback()`.
2. **`app/src/main/java/org/sih/itantra/presentation/screens/IndividualChatScreen.kt`**:
   - Wired `playbackState`, `ttsRegistry`, `MessageLanguageBadge`, and `TtsPlaybackIndicator` into `ChatMessageBubble`.
   - Play/stop button dynamically bound to message playback state.
3. **`app/src/test/java/org/sih/itantra/presentation/IndividualChatTest.kt`**:
   - Added 2 Compose integration tests verifying language badge rendering and playback state reactivity.

---

## 3. Verification & Test Baseline

### Automated Unit Test Suite (`./gradlew testDebugUnitTest --rerun-tasks`):
- **Total Tests Executed:** 523
- **Total Passed:** 523
- **Failures:** 0
- **Ignored:** 0
- **Success Rate:** 100%

### Test Matrix Breakdown:
| Test Suite | Tests | Result |
| :--- | :---: | :---: |
| `TtsVoiceResolverTest` | 10 | **PASSED** |
| `TtsLanguageRoutingTest` | 5 | **PASSED** |
| `TtsPlaybackStateTest` | 7 | **PASSED** |
| `IndividualChatTest` | 6 | **PASSED** |
| Prior Regression Suites (Features 1–10) | 495 | **PASSED** |
| **Total** | **523** | **100% PASSED** |

---

## 4. Physical Device Smoke Test (Phone A: `RZCY9396AGX`)

- **Hardware:** Samsung Galaxy A55 5G (`RZCY9396AGX`), Android 14.
- **Verification Type:** Build, install, runtime launch, and UI rendering smoke test. (Audio playback testing on physical device was explicitly deferred as instructed).
- **Results:**
  - Build & APK packaging completed cleanly (`./gradlew installDebug`).
  - Application launched cleanly without runtime exceptions.
  - Navigated from `ChatsHomeScreen` to `IndividualChatScreen`.
  - Zero crashes or memory warnings reported in Logcat.
