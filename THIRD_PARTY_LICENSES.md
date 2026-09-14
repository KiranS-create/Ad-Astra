# Third-Party Notices & Licensing

**Project:** iTantra (Ad Astra)  
**Smart India Hackathon 2026** • **Problem Statement:** SIH26173  

---

## 1. Project Codebase

The custom application source code, protocol definitions, and architecture developed for **iTantra** are created by **Team Ad Astra** for the Smart India Hackathon 2026.

---

## 2. Open-Source Libraries & Dependencies

| Component / Library | Origin / Author | License | Usage |
|---|---|---|---|
| **Sherpa-ONNX** | Next-gen Kaldi Project | Apache License 2.0 | Native speech recognition and TTS runtime |
| **ONNX Runtime** | Microsoft Corporation | MIT License | High-performance inference engine for ONNX models |
| **Android Jetpack & Compose** | Google LLC / AOSP | Apache License 2.0 | Modern declarative UI, Navigation, and Lifecycle |
| **Room Persistence Library** | Google LLC / AOSP | Apache License 2.0 | SQLite database abstraction for DTN message storage |
| **Kotlin Coroutines** | JetBrains s.r.o. | Apache License 2.0 | Asynchronous concurrency and pipeline coordination |

---

## 3. Acoustic & Language Model Weights

The pre-trained neural network weights packaged or referenced with this repository originate from upstream research projects and carry their respective licenses:

| Model | Project / Maintainer | Upstream License | Repository Status |
|---|---|---|---|
| **Whisper-Tiny (Multilingual INT8)** | OpenAI / Sherpa-ONNX | MIT License | Bundled in repository assets (`assets/models/`) |
| **Piper VITS Voices (EN, HI, MR, ML, TE, BN)** | Piper TTS Project | MIT / Open Source | Bundled in repository assets (`assets/models/`) |
| **Mimic3 VITS Voice (GU)** | Mycroft AI | LGPL / Open Source | Bundled in repository assets (`assets/models/`) |
| **Meta MMS Voices (KN, TA, OR)** | Meta AI Research | Creative Commons Attribution-NonCommercial 4.0 (CC-BY-NC 4.0) | Optional / Release Asset download (`models_phone_b.tar`) with System TTS fallback |

---

## 4. Notice Regarding Non-Commercial Model Weights

The Meta MMS voice models are distributed under the **CC-BY-NC 4.0** license and are intended solely for academic research, evaluation, and hackathon demonstration purposes. Production or commercial distributions should ensure adherence to all upstream licensing restrictions or substitute commercial-compliant voice models.
