# iTantra — Final Build & Release Information

**Project:** SIH26173 — iTantra (Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access)  
**Release Target:** Pre-UI Overhaul Repository Preservation Checkpoint `v1.2.0-pre-ui-overhaul`  
**Application ID:** `org.sih.itantra` • **Version Code:** `1` • **Version Name:** `1.0.0`  
**Date:** September 2026  

---

## 1. Source Control Metadata

| Property | Value |
|---|---|
| **Repository** | `https://github.com/KiranS-create/Ad-Astra.git` |
| **Current Branch** | `main` |
| **Checkpoint Tag** | `v1.2.0-pre-ui-overhaul` |
| **Historical Release Tags** | `v1.0.0` (`f8e6db4`), `v1.1.0-rc1` (`88b2063`), `v1.1.0-rc2` (`b0ade9a`) |
| **Working Tree Status** | Clean |

---

## 2. Compiled Application Artifacts

| Artifact | Relative Path | File Size (Bytes) | File Size (MB) | Build Status |
|---|---|:---:|:---:|:---:|
| **Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | 982,553,843 | **937.04 MB** | Verified (BUILD SUCCESSFUL) |
| **Release APK** | `app/build/outputs/apk/release/app-release.apk` | 975,031,448 | **929.86 MB** | Verified (BUILD SUCCESSFUL) |

### Size Justification:
The ~929 MB release artifact is intentional and strictly necessary. It packages all on-device machine learning model assets within `assets/models/`:
- Multilingual quantized INT8 Whisper-Tiny STT encoder and decoder (102.7 MB).
- Native C++ Sherpa-ONNX shared libraries for `arm64-v8a` (`libsherpa-onnx-jni.so`, `libonnxruntime.so`).
- Seven neural Piper and Mimic3 VITS TTS voices (English, Hindi, Marathi, Gujarati, Malayalam, Telugu, Bengali) averaging 60–76 MB each.
- Three Meta MMS VITS voices (Kannada, Tamil, Odia) at 114 MB each.
This eliminates any requirement for runtime internet connectivity or post-installation model downloads.

---

## 3. Toolchain & Build Environment

| Parameter | Configuration |
|---|---|
| **Operating System** | Windows 11 / Linux (Ubuntu 22.04 in CI) |
| **Gradle Version** | `8.10.2` (via wrapper) |
| **Android Gradle Plugin (AGP)** | `8.7.2` |
| **Kotlin Version** | `2.0.21` (Language & API Version: 2.0) |
| **Java Runtime (Local)** | OpenJDK 19 (`C:\Program Files\Java\jdk-19`) |
| **Java Runtime (CI)** | Eclipse Temurin OpenJDK 17 |
| **Compile SDK Version** | `35` (Android 15) |
| **Target SDK Version** | `35` (Android 15) |
| **Minimum SDK Version** | `26` (Android 8.0 Oreo) |
| **NDK ABI Filters** | Strictly `["arm64-v8a"]` |
| **Compiler JVM Flags** | `-Xmx3072m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8` |
| **Compiler Execution Strategy** | `kotlin.compiler.execution.strategy=in-process` |

---

## 4. Test & Verification Execution Results

### 4.1 Automated JVM Unit Test Suite
- **Command:** `.\gradlew.bat testDebugUnitTest`
- **Result:** **`BUILD SUCCESSFUL in 36s`**
- **Test Metrics:**
  - **Total Tests Executed:** **966**
  - **Passed:** **966 (100.0%)**
  - **Failed:** **0**
  - **Skipped:** **0**

### 4.2 Debug Build Compilation
- **Command:** `.\gradlew.bat assembleDebug`
- **Result:** **`BUILD SUCCESSFUL in 15s`**

### 4.3 Release Build Compilation
- **Command:** `.\gradlew.bat assembleRelease`
- **Result:** **`BUILD SUCCESSFUL in 24s`**
- **Optimizations:** R8 full-mode shrinking, dead code elimination, resource shrinking, signing with debug key for immediate evaluation sideloading.

---

## 5. Continuous Integration (CI) Pipeline Status

A fully automated CI workflow is configured in `.github/workflows/android.yml`:
1. **Trigger:** Pushes and pull requests targeting `main`.
2. **Steps:**
   - Environment setup with Ubuntu-latest and Temurin JDK 17.
   - Gradle wrapper execution with build caching (`gradle/actions/setup-gradle@v3`).
   - Release manifest and resource validation via `lintVitalRelease`.
   - Automated debug compilation via `assembleDebug`.
   - Release candidate artifact compilation via `assembleRelease`.
   - Artifact archival: `app-release.apk` archived as a GitHub Actions workflow artifact with 7-day retention.

---

## 6. Known Release Smoke Test & Hardware Boundaries

1. **Sideloading Prerequisites:** Due to custom Android security policies on Samsung One UI, devices installing the APK via ADB or USB must have *“Install Unknown Apps”* permitted for the sideloading tool.
2. **USB Transfer Time:** Because the APK is ~929 MB, streaming install over USB 2.0 ADB cables requires $30\text{--}60\text{ seconds}$ per device (`adb install -r app-release.apk`).
3. **Hardware Test Fleet:** Physical release verification was conducted directly on:
   - Handset A: Samsung Galaxy A55 5G (`SM-A556E`, Android 16)
   - Handset B: Samsung Galaxy Note 10 Lite (`SM-N770F`, Android 12)
   Automated physical smoke testing across broader device matrices (e.g., Google Pixel, Xiaomi, OnePlus) has not been performed on a commercial device farm.
