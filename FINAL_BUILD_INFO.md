# iTantra — Final Build & Release Information

**Project:** SIH26173 — iTantra (Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access)  
**Release Target:** SIH Production Release Candidate `v1.0.0`  
**Application ID:** `org.sih.itantra` • **Version Code:** `1` • **Version Name:** `1.0.0`  
**Date:** September 2026  

---

## 1. Source Control Metadata

| Property | Value |
|---|---|
| **Repository** | `https://github.com/KiranS-create/Ad-Astra.git` |
| **Current Branch** | `main` |
| **Commit Hash** | `343ba26ecd065ba3d9fdfcc18be2cbaf61829c7a` |
| **Commit Message** | `fix(qr): repair camera scanner initialization` |
| **Release Tag** | `v1.0.0` (`f8e6db40b36f706beb24567b30441e30715b14b0`) |
| **Remote Sync Status** | `origin/main` is identical to local `main` (`HEAD = 343ba26`) |
| **Working Tree Status** | Clean (documentation changes tracked) |

---

## 2. Compiled Application Artifacts

| Artifact | Relative Path | File Size (Bytes) | File Size (MB) | Build Timestamp | Status |
|---|---|:---:|:---:|:---:|:---:|
| **Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | 989,213,632 | **943.39 MB** | 16-09-2026 07:16:45 | Verified |
| **Release APK** | `app/build/outputs/apk/release/app-release.apk` | 974,982,208 | **929.82 MB** | 16-09-2026 07:39:02 | Verified |

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
- **Result:** **`BUILD SUCCESSFUL in 31s`**
- **Test Metrics:**
  - **Total Test Suites:** 72 test classes
  - **Total Tests Executed:** **903**
  - **Passed:** **903 (100.0%)**
  - **Failed:** **0**
  - **Skipped:** **0**

### 4.2 Debug Build Compilation
- **Command:** `.\gradlew.bat assembleDebug`
- **Result:** **`BUILD SUCCESSFUL in 30s`**

### 4.3 Release Build Compilation
- **Command:** `.\gradlew.bat assembleRelease`
- **Result:** **`BUILD SUCCESSFUL in 5m 20s`**
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
