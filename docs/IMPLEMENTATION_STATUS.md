# SIH26173 — iTantra Implementation Status

**Project**: SIH26173 — iTantra (Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links)  
**Target**: Smart India Hackathon 2026 / ISRO-oriented Offline Android Application  
**Date**: September 2026  

---

## Current Status Summary

| Item | Status |
|---|---|
| Pipeline Architecture | Mic → VAD → Hindi STT → Finalizer → Protocol → Transport → Hindi TTS → Playback (100% Implemented) |
| Active Phase | **Phase 25 Complete (Real Neural Sherpa-ONNX Hindi STT & TTS Integration)** |
| Neural Runtimes | Sherpa-ONNX 1.13.7 AAR (Native JNI: `arm64-v8a`, `armeabi-v7a`, `x86_64`) |
| Neural Models | Whisper-Tiny INT8 STT (103.5 MB) + VITS Piper Rohan Medium Hindi TTS (66.5 MB) Bundled in APK |
| Toolchain | Gradle 8.10.2 / AGP 8.7.2 / Kotlin 2.0.21 / Java 19 / SDK 35 / Jetpack Compose Material 3 |
| Build Status | **BUILD SUCCESSFUL** (`app-debug.apk` [276.9 MB] with 362 model asset files) |
| Tests Passing | **21 / 21 Tests Passed (100% Success Rate, 0 Failures, 0 Errors)** |

---

## Phase Breakdown

- [x] **PHASE 0 — Workspace Audit**: Audited repository, research documents, and toolchains.
- [x] **PHASE 1 — Architecture and Project Foundation**:
  - Gradle 8.10.2 configured with JDK 19, UTF-8 BOM-free `gradle.properties` (3GB heap, 768MB Metaspace).
  - AGP 8.7.2, Kotlin 2.0.21, Jetpack Compose Material 3, AndroidX Lifecycle 2.8.7.
  - `AndroidManifest.xml` configured with runtime permissions for Audio, Wi-Fi, Bluetooth, WakeLock, Haptics.
- [x] **PHASE 2 — Domain/Core Interfaces**: `Transport`, `SpeechRecognizer`, `TextSynthesizer`, `AudioRecorder`, `VadDetector`, `SentenceFinalizer`.
- [x] **PHASE 3 — Audio Capture**: 16kHz 16-bit Mono PCM `AudioRecord` pipeline with ring buffers (`core.audio.AudioRecorder`).
- [x] **PHASE 4 — VAD / Pause Detection**: Adaptive Energy & ZCR speech detector with configurable pause/stoppage threshold (`core.vad.VadDetector`).
- [x] **PHASE 5 — STT Engine**: Offline Indic speech recognition abstraction with `EXTRA_PREFER_OFFLINE` guarantee, Indic language routing, and test pipelines (`core.stt.OfflineSpeechRecognizer`).
- [x] **PHASE 6 — Sentence Segmentation**: Pause-aware, Indian punctuation (danda `।`, `॥`, `?`, `!`), and linguistic segmenter (`core.stt.SentenceFinalizer`).
- [x] **PHASE 7 — Message Protocol**: Compact 31-byte binary radio frame, CRC-32 integrity, adaptive compression, fragmentation (`core.protocol.PacketSerializer`, `core.protocol.AdaptiveCompressor`, `core.protocol.PacketFragmenter`).
- [x] **PHASE 8 — Wi-Fi Transport**: Offline UDP broadcast / multicast mesh on port 42888, plus peer-to-peer unicast (`core.transport.WifiTransport`).
- [x] **PHASE 9 — Bluetooth Transport**: Bluetooth Classic RFCOMM SPP socket implementation (`core.transport.BluetoothTransport`).
- [x] **PHASE 10 — TTS Engine**: Offline synthesis with Android offline voice data and VITS/MMS pipeline (`core.tts.OfflineTtsEngine`).
- [x] **PHASE 11 — Audio Playback**: AudioTrack streaming playback with priority queue management (`core.audio.AudioPlayer`).
- [x] **PHASE 12 — PTT State Machine**: 10-state lifecycle coordinator with tactile feedback (`core.session.PttStateMachine`).
- [x] **PHASE 13 — Continuous Conversation Mode**: Power-efficient hands-free speech loop with echo-gating feedback prevention (`core.session.TransceiverCoordinator`).
- [x] **PHASE 14 — Alert/Distress System**: High-priority alert preemption, max-volume non-interruptible siren tone generator (`core.tts.AlertToneGenerator`).
- [x] **PHASE 15 — Pairing & Discovery**: Wi-Fi LAN & Bluetooth peer discovery management.
- [x] **PHASE 16 — UI / UX**: Tactical military radio console in Jetpack Compose (`presentation.screens.MainTransceiverScreen`).
- [x] **PHASE 17 — Diagnostics**: Real-time telemetry, packet metrics, latency breakdown (`presentation.screens.DiagnosticsScreen`).
- [x] **PHASE 18 — Benchmark Mode**: Empirical latency and bandwidth measurement dashboard (`presentation.screens.BenchmarkScreen`).
- [x] **PHASE 19 — History & Persistence**: Local message store with latency records (`core.persistence.MessageHistoryStore`).
- [x] **PHASE 20 — Security Baseline**: CRC-32 integrity, packet length bounds validation, replay protection.
- [x] **PHASE 21 — Integration**: Master coordinator connecting Audio → VAD → STT → Protocol → Transport → TTS → Playback.
- [x] **PHASE 22 — Automated Unit Tests**: Complete unit test suite with 100% pass rate.
- [x] **PHASE 23 — Build & Release Optimization**: `app-debug.apk` built and packaged successfully.
- [x] **PHASE 24 — SIH Demo Readiness Audit**: Completed judge-friendly demo walkthrough and artifacts.
- [x] **PHASE 25 — Real Neural Sherpa-ONNX Hindi Integration**:
  - Downloaded and verified official `sherpa-onnx-1.13.7.aar` (contains JNI libraries for `arm64-v8a`, `armeabi-v7a`, `x86_64`).
  - Bundled quantized INT8 Whisper-Tiny multilingual STT model (`tiny-encoder.int8.onnx`, `tiny-decoder.int8.onnx`, `tiny-tokens.txt`) in `assets/models/stt/whisper-tiny/`.
  - Bundled VITS Piper Rohan Medium Hindi neural TTS model (`hi_IN-rohan-medium.onnx`, `tokens.txt`, `espeak-ng-data/`) in `assets/models/tts/vits-piper-hi/`.
  - Created `ModelAssetManager.kt` for zero-lag extraction from APK assets to internal device filesystem.
  - Implemented `SherpaOnnxSpeechRecognizer.kt` with PCM-to-float normalizer, greedy search decoding, and Devanagari transcription.
  - Implemented `SherpaOnnxTtsEngine.kt` with 22.05kHz neural audio synthesis and AudioTrack playback.
  - Created `NeuralSpeechRouter.kt` and `NeuralTtsRouter.kt` for seamless Hindi neural routing and graceful fallback.
  - Added "TEST NEURAL HINDI" action in UI to demonstrate real on-device synthesis and packet generation.
  - Added `NeuralConversionTest.kt` verifying numerical audio reconstruction, Hindi packet roundtrip, and model registry reporting.
  - All 21 tests pass; `app-debug.apk` (276.9 MB) compiled and verified.
