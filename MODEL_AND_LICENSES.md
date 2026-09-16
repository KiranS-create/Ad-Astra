# iTantra — Machine Learning Models & Licensing Audit

**Project:** SIH26173 — iTantra (Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access)  
**Target:** Regulatory, Intellectual Property, and Licensing Compliance Audit  
**Date:** September 2026 • **Repository State:** `343ba26` (`main`)  

---

## 1. Executive Summary

iTantra packages on-device machine learning models to enable 100% air-gapped, sovereign operation without internet connectivity. To maintain strict intellectual property transparency, this document provides an authoritative audit of all speech-to-text (ASR) and text-to-speech (TTS) neural network weights, native runtime libraries, and third-party dependencies incorporated into the codebase.

---

## 2. Neural Model Inventory & Legal Classification

Every model asset located in `app/src/main/assets/models/` has been audited for origin, architecture, size, license terms, and redistribution rights:

| Language | Task | Model Name / Identifier | Runtime / Framework | File Size | License Type | Redistribution Permitted? | Commercial Use Permitted? | Upstream Source & Attribution |
|:---:|:---:|:---|:---|:---:|:---:|:---:|:---:|:---|
| **Multilingual** (9 Langs) | **STT** | `Whisper-Tiny` (INT8 Quantized) | Sherpa-ONNX / ONNX Runtime | 102.7 MB | **MIT License** | **YES** | **YES** | OpenAI Whisper / k2-fsa Sherpa-ONNX. Attribution: OpenAI. |
| **English (`en`)** | **TTS** | VITS Piper (`en_US-lessac-medium`) | Piper / ONNX Runtime | 63.2 MB | **MIT License** | **YES** | **YES** | Rhasspy Piper Voices (Lessac corpus). Attribution: Piper Project. |
| **Hindi (`hi`)** | **TTS** | VITS Piper (`hi_IN-rohan-medium`) | Piper / ONNX Runtime | 62.9 MB | **MIT License** | **YES** | **YES** | Rhasspy Piper Voices (Rohan corpus). Attribution: Piper Project. |
| **Marathi (`mr`)** | **TTS** | VITS Piper (`mr_IN-google-medium`) | Piper / ONNX Runtime | 76.7 MB | **Apache-2.0** | **YES** | **YES** | Rhasspy Piper Voices (Google Crowdsourced Indic). Attribution: Google LLC / Piper. |
| **Malayalam (`ml`)** | **TTS** | VITS Piper (`ml_IN-arjun-medium`) | Piper / ONNX Runtime | 62.9 MB | **MIT License** | **YES** | **YES** | Rhasspy Piper Voices (Arjun corpus). Attribution: Piper Project. |
| **Telugu (`te`)** | **TTS** | VITS Piper (`te_IN-maya-medium`) | Piper / ONNX Runtime | 62.9 MB | **MIT License** | **YES** | **YES** | Rhasspy Piper Voices (Maya corpus). Attribution: Piper Project. |
| **Bengali (`bn`)** | **TTS** | VITS Piper (`bn_BD-google-medium`) | Piper / ONNX Runtime | 76.7 MB | **Apache-2.0** | **YES** | **YES** | Rhasspy Piper Voices (Google Crowdsourced Indic). Attribution: Google LLC / Piper. |
| **Gujarati (`gu`)** | **TTS** | VITS Mimic3 (`gu_IN-cmu-indic_low`) | Mimic3 / ONNX Runtime | 76.3 MB | **LGPL-3.0** | **YES** (with source) | **YES** (under LGPL) | Mycroft AI Mimic3 Project (CMU Indic). Attribution: CMU / Mycroft AI. |
| **Kannada (`kn`)** | **TTS** | VITS Meta MMS (`vits-mms-kn`) | Meta MMS / ONNX Runtime | 114.0 MB | **CC-BY-NC 4.0** | **YES** (Non-Commercial) | **NO (Non-Commercial)** | Meta AI Research (Massively Multilingual Speech). Attribution: Meta AI. |
| **Tamil (`ta`)** | **TTS** | VITS Meta MMS (`vits-mms-ta`) | Meta MMS / ONNX Runtime | 114.0 MB | **CC-BY-NC 4.0** | **YES** (Non-Commercial) | **NO (Non-Commercial)** | Meta AI Research (Massively Multilingual Speech). Attribution: Meta AI. |
| **Odia (`or`)** | **TTS** | VITS Meta MMS (`vits-mms-or`) | Meta MMS / ONNX Runtime | 114.0 MB | **CC-BY-NC 4.0** | **YES** (Non-Commercial) | **NO (Non-Commercial)** | Meta AI Research (Massively Multilingual Speech). Attribution: Meta AI. |

---

## 3. Critical Licensing Flag: Meta MMS Models (CC-BY-NC 4.0)

> [!WARNING]
> **Non-Commercial Restriction on Kannada, Tamil, and Odia Neural Voice Models:**
> The neural TTS voice weights for Kannada (`vits-mms-kn`), Tamil (`vits-mms-ta`), and Odia (`vits-mms-or`) are derived from Meta AI's Massively Multilingual Speech (MMS) project and are released under the **Creative Commons Attribution-NonCommercial 4.0 International (CC-BY-NC 4.0)** license.
>
> - **Hackathon / Academic Eligibility:** Permitted. The Smart India Hackathon evaluation, academic testing, and research prototyping fall strictly within non-commercial, non-monetized evaluation scope.
> - **Defense / Commercial Procurement Requirement:** Prior to any commercial release, public government procurement, or defense production deployment, these three models **MUST be replaced** with commercially licensed voices (e.g., custom-trained VITS Piper models using open-license Indic speech datasets, or through commercial licensing agreements with copyright holders).
> - **Zero-Crash Graceful Fallback:** If any of these `.onnx` models are removed from the APK, iTantra automatically falls back to the native Android OS offline TTS engine for that language. No crash, ANR, or protocol failure occurs.

---

## 4. Native Runtime Libraries & Software Dependencies

All software libraries bundled within the release APK are standard permissive open-source packages:

| Library / Runtime | Version | License | Redistribution Terms | Usage in iTantra |
|---|---|---|---|---|
| **Sherpa-ONNX** | `1.13.7` (AAR) | **Apache-2.0** | Permitted with license notice | Native C++ ASR/TTS inference binding (`arm64-v8a`) |
| **ONNX Runtime** | `1.17.1` | **MIT License** | Permitted with copyright notice | Hardware-accelerated tensor graph execution engine |
| **Android Jetpack & Compose** | `2024.04.01` | **Apache-2.0** | Permitted | Modern UI components, Navigation, ViewModels |
| **Room Persistence Library** | `2.6.1` | **Apache-2.0** | Permitted | Local SQLite storage for DTN message caching |
| **ZXing Core** | `3.5.3` | **Apache-2.0** | Permitted | Offline optical QR code parsing and generation |
| **CameraX** | `1.4.1` | **Apache-2.0** | Permitted | Hardware camera viewfinder binding for QR node pairing |
| **Kotlin Standard Library** | `2.0.21` | **Apache-2.0** | Permitted | Core language runtime |

---

## 5. Required Actions for Commercial / Public Distribution

Before transition from Hackathon Prototype to Commercial / Defense Procurement:
1. **Substitute Meta MMS Models:** Train or acquire MIT/Apache-2.0 licensed VITS Piper models for Kannada, Tamil, and Odia to replace the CC-BY-NC 4.0 weights.
2. **Review LGPL-3.0 for Gujarati Voice:** Ensure that the Mimic3 voice binary is distributed in accordance with LGPL-3.0 relinking requirements, or retrain under Apache-2.0.
3. **Bundle License Text:** Ensure the compiled APK's "About / Legal Notices" screen renders the full license texts of Apache-2.0, MIT, and LGPL-3.0.
