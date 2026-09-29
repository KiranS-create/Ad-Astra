# iTantra — Machine Learning Models Guide

This document describes the on-device offline Speech-to-Text (STT) and Text-to-Speech (TTS) models used by **iTantra** for 10 Indian languages.

---

## 1. Overview of Model Distribution

To keep the repository lightweight and adhere to GitHub's 100 MB per-file hosting limit:
- **Sub-100 MB models** (including the multilingual Whisper-Tiny INT8 STT engine and 7 Piper/Mimic3 TTS models) are version-controlled directly in this repository under `app/src/main/assets/models/`.
- **Large TTS models (>100 MB)** (Kannada, Tamil, and Odia VITS-MMS models at ~114 MB each) are distributed separately via **GitHub Releases** or upstream Hugging Face repositories.

---

## 2. Models Shipped Directly in the Repository

The following models are already present in `app/src/main/assets/models/` and require no additional downloads:

| Task | Language | Model Engine | Model File(s) | Size | License | Source |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **STT** | Multilingual (HI, GU, MR, KN, ML, TA, TE, BN, EN) | Whisper-Tiny (INT8 Quantized) | `tiny-encoder.int8.onnx`<br/>`tiny-decoder.int8.onnx`<br/>`tiny-tokens.txt` | 12.9 MB<br/>85.7 MB<br/>1.2 MB | MIT | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (OpenAI Whisper) |
| **TTS** | **English (en)** | VITS Piper (Lessac Medium) | `en_US-lessac-medium.onnx`<br/>`tokens.txt` | 60.3 MB | MIT | [rhasspy/piper-voices](https://github.com/rhasspy/piper-voices) |
| **TTS** | **Hindi (hi)** | VITS Piper (Rohan Medium) | `hi_IN-rohan-medium.onnx`<br/>`tokens.txt` | 60.0 MB | MIT | [rhasspy/piper-voices](https://github.com/rhasspy/piper-voices) |
| **TTS** | **Gujarati (gu)** | VITS Mimic3 (CMU Indic Low) | `gu_IN-cmu-indic_low.onnx`<br/>`tokens.txt` | 72.8 MB | Open Source | [MycroftAI/mimic3-voices](https://github.com/MycroftAI/mimic3-voices) |
| **TTS** | **Marathi (mr)** | VITS Piper (Google Medium) | `mr_IN-google-medium.onnx`<br/>`tokens.txt` | 73.2 MB | Apache-2.0 | [rhasspy/piper-voices](https://github.com/rhasspy/piper-voices) |
| **TTS** | **Malayalam (ml)** | VITS Piper (Arjun Medium) | `ml_IN-arjun-medium.onnx`<br/>`tokens.txt` | 60.0 MB | MIT | [rhasspy/piper-voices](https://github.com/rhasspy/piper-voices) |
| **TTS** | **Telugu (te)** | VITS Piper (Maya Medium) | `te_IN-maya-medium.onnx`<br/>`tokens.txt` | 60.0 MB | MIT | [rhasspy/piper-voices](https://github.com/rhasspy/piper-voices) |
| **TTS** | **Bengali (bn)** | VITS Piper (Google Medium) | `bn_BD-google-medium.onnx`<br/>`tokens.txt` | 73.2 MB | Apache-2.0 | [rhasspy/piper-voices](https://github.com/rhasspy/piper-voices) |

---

## 3. Models Distributed Separately (> 100 MB)

These three VITS Meta MMS models exceed GitHub's 100 MB file limit. Their token mappings (`tokens.txt`) are included in the repository; only the binary `model.onnx` weights must be obtained if you wish to build from source with offline Kannada, Tamil, or Odia neural voice synthesis:

| Language | Model Name | Expected Local Path | File Size | Source Repository | License |
| :--- | :--- | :--- | :---: | :--- | :--- |
| **Kannada (kn)** | VITS Meta MMS Kannada | `app/src/main/assets/models/tts/vits-mms-kn/model.onnx` | 114.0 MB | [willwade/mms-tts-multilingual-models-onnx (kan)](https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/tree/main/kan) | CC-BY-NC 4.0 |
| **Tamil (ta)** | VITS Meta MMS Tamil | `app/src/main/assets/models/tts/vits-mms-ta/model.onnx` | 114.0 MB | [willwade/mms-tts-multilingual-models-onnx (tam)](https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/tree/main/tam) | CC-BY-NC 4.0 |
| **Odia (or)** | VITS Meta MMS Odia | `app/src/main/assets/models/tts/vits-mms-or/model.onnx` | 114.0 MB | [willwade/mms-tts-multilingual-models-onnx (ory)](https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/tree/main/ory) | CC-BY-NC 4.0 |

> **Note on App Fallback:** If any of these three `.onnx` files is omitted during a custom build, the app automatically falls back to the device's native Android offline TTS engine for that language. No crash or hang occurs.
