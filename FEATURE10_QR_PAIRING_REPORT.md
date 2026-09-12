# Feature 10: Offline QR Node / Contact Pairing — Implementation Report

**Feature:** Feature 10 — Offline QR Node / Contact Pairing  
**Agent:** Feature Agent 10  
**Date:** 2026-09-12  
**Status:** COMPLETE & PHYSICALLY VALIDATED ON PHONE A  

---

## 1. Executive Summary

Feature 10 introduces a battle-hardened, 100% offline QR-based contact and node identity exchange mechanism for the iTantra tactical MANET communicator (Smart India Hackathon 2026, PS SIH26173).

In tactical, disaster, and defense operations, typing a 6-digit node identifier, callsign, and language profile manually while under field constraints introduces severe operational friction and human error. Feature 10 solves this by enabling rapid visual exchange between handsets without requiring any internet connection, cloud infrastructure, or external proprietary QR libraries.

### Key Architectural Tenets:
1. **Zero-Secret Guarantee:** The QR payload encodes **PUBLIC IDENTITY METADATA ONLY** (`nodeId`, `callsign`, `displayName`, `supportedLanguages`). It strictly **NEVER** contains HMAC keys, private asymmetric keys, session tokens, network seeds, or cryptographic credentials.
2. **Unverified Trust Model:** All contacts added via QR scanning are strictly assigned `ContactAuthStatus.UNVERIFIED`. Visual pairing establishes contact presence, not cryptographic authentication.
3. **Pure Kotlin ISO/IEC 18004 Engine:** QR matrix generation is implemented natively in pure Kotlin with Reed-Solomon GF(256) error correction (Levels M/L), eliminating all third-party cloud or binary dependencies.
4. **Deterministic Versioned Wire Format:** Payloads use a versioned, pipe-delimited format (`ITANTRA:1|<nodeId>|<callsign>|<displayName>|<languages>`) bounded strictly to 512 bytes with defensive character sanitization.
5. **Fail-Safe Offline Fallback:** A built-in manual code/clipboard paste dialog allows identity exchange in low-light, camera-impaired, or non-camera tactical scenarios.
6. **Strict Validation Pipeline:** Self-node scans (`"This is this device"`) and duplicate contacts are immediately rejected with clear operator feedback before touching storage.
7. **Complete Isolation:** Core messaging, radio protocols, Nearby discovery, and frozen activities (`MainActivity.kt`, `BottomNavBar.kt`, `TransceiverViewModel.kt`) remain completely untouched.

---

## 2. Identity Architecture & Domain Integration

Feature 10 builds directly upon the foundational `ContactIdentity` and `ContactRepository` models introduced in Feature 3 without altering their persistence schemas:

```
+-------------------------------------------------------------+
|                      LOCAL NODE STATE                       |
|    (NodeId: 209070, Callsign: NODE ALPHA, Langs: hi, en)    |
+------------------------------+------------------------------+
                               | QrIdentityPayload.fromLocalNode(...)
                               v
+-------------------------------------------------------------+
|                 QrIdentityCodec.encode(...)                 |
|      Output: "ITANTRA:1|209070|NODE ALPHA|Alpha Unit|hi,en" |
+------------------------------+------------------------------+
                               | QrMatrixEncoder.encodeToBitmap(...)
                               v
+-------------------------------------------------------------+
|               PURE KOTLIN QR MATRIX GENERATOR               |
|        (ISO/IEC 18004 Byte Mode, Reed-Solomon GF(256))       |
+------------------------------+------------------------------+
                               | Visual Scan or Manual Paste
                               v
+-------------------------------------------------------------+
|                 QrPairingValidator.validate                 |
|   |-- Check 1: Size <= 512 bytes                            |
|   |-- Check 2: Parse Version & Fields                       |
|   |-- Check 3: Reject Self-Node (scannedId == localId)      |
|   +-- Check 4: Reject Duplicate (scannedId in repo)         |
+------------------------------+------------------------------+
                               | ValidationResult.Success
                               v
+-------------------------------------------------------------+
|                 QrPairingConfirmation Dialog                |
|    Amber Warning: "TRUST STATE: UNVERIFIED"                 |
+------------------------------+------------------------------+
                               | Operator Taps [ADD CONTACT]
                               v
+-------------------------------------------------------------+
|                      ContactRepository                      |
|   addContact(identity.copy(authStatus = UNVERIFIED))        |
+-------------------------------------------------------------+
```

### Models Delivered (`org.sih.itantra.core.pairing`):
- **`QrIdentityPayload`**: Data carrier holding `nodeId: Int`, `callsign: String`, `displayName: String?`, `supportedLanguages: List<IndicLanguage>`, and `version: Int = 1`. Provides `toContactIdentity(notes, authStatus = UNVERIFIED)` to guarantee unverified trust assignment.
- **`QrIdentityCodec`**: Deterministic encoder and parser with bounds validation, delimiter stripping, and exception safety (`decodeOrNull`, `decodeOrThrow`, `tryDecode`).
- **`QrPairingValidator`**: Validates raw scanned payloads against the local node identity and `ContactRepository`. Returns sealed `ValidationResult` states (`Success`, `SelfNode`, `Duplicate`, `Invalid`).
- **`QrMatrixEncoder`**: Self-contained ISO/IEC 18004 matrix generator with pure Kotlin math and Android `Bitmap` rendering.

---

## 3. Deterministic Wire Format Specification

The QR wire format is intentionally human-readable, minimal, and deterministic:

```
ITANTRA:<version>|<nodeId>|<callsign>|<displayName>|<languages>
```

### Field Breakdown:
| Field Index | Name | Constraint / Type | Example | Description |
| :--- | :--- | :--- | :--- | :--- |
| Header | Prefix | Literal `ITANTRA:` | `ITANTRA:1` | Protocol magic prefix and wire format version |
| 1 | `nodeId` | 1 to 6-digit decimal integer | `884411` | Unique network node address |
| 2 | `callsign` | 1-32 uppercase alphanumeric chars | `BRAVO SCOUT` | Tactical radio identifier |
| 3 | `displayName` | Optional string (pipes stripped) | `Tactical Recon Unit` | Human-readable operator or unit name |
| 4 | `languages` | Comma-delimited language codes | `hi,en,pa` | Indic language capability tags |

### Encoding Guarantees:
- **Maximum Length:** 512 bytes strictly enforced (`PAYLOAD_TOO_LARGE` rejection).
- **Sanitization:** Delimiter pipe characters (`|`) in callsign and display name are stripped during encoding.
- **Fallback Tolerances:** Missing display names fall back cleanly to `NODE #<nodeId>`. Unknown language tags default gracefully to Hindi (`hi`).

---

## 4. Security Guarantees & Zero-Secret Verification

In tactical operations, displaying a QR code exposes it to optical interception by anyone in line of sight (including drone cameras, observation posts, and bystanders).

### Cryptographic Security Assertions:
1. **Zero Secret Leakage:**
   - No private keys (Ed25519/ECDSA/RSA) are encoded.
   - No pre-shared HMAC keys (`PacketAuthenticator`) are encoded.
   - No network cryptographic seeds, session tokens, or routing credentials are included.
2. **Replay & Impersonation Mitigation:**
   - Since the QR code contains only public identity metadata, an intercepted QR code gives an adversary **zero cryptographic ability** to spoof authenticated packets or decrypt radio traffic.
3. **Unverified Trust Enforcement:**
   - Adding a contact via QR strictly records `ContactAuthStatus.UNVERIFIED`.
   - The UI displays a high-visibility tactical amber warning:
     > `TRUST STATE: UNVERIFIED`  
     > `Adding this contact does not verify its cryptographic identity. Operator must verify out-of-band.`
   - Operators cannot bypass this unverified status during QR pairing.

---

## 5. Pure Kotlin ISO/IEC 18004 QR Matrix Generator

To fulfill the zero-cloud, 100% offline requirement without bloating APK size with heavy third-party SDKs, a pure Kotlin QR generator (`QrMatrixEncoder`) was designed from first principles:

1. **Encoding Mode:** ISO/IEC 18004 8-bit Byte Mode.
2. **Dynamic Version Selection:** Versions 1 through 10 (21x21 up to 57x57 matrix size) dynamically selected based on payload byte length and error correction capacity.
3. **Error Correction:** Reed-Solomon Polynomial division over Galois Field GF(2^8) with irreducible polynomial x^8 + x^4 + x^3 + x^2 + 1 (0x11D).
4. **Pattern Generation:**
   - Three 7x7 Finder patterns with 1-module separators.
   - Timing patterns (alternating black/white modules on row 6 and column 6).
   - Standard Alignment patterns computed per ISO/IEC 18004 Table E.1.
   - Mask pattern 0 applied ((r + c) mod 2 = 0) with 15-bit format information sequence (BCH (15,5) encoded with mask 0x5412).
5. **Rendering:** Pure Android Canvas/Bitmap rendering with configurable quiet zones, background colors (Tactical Dark `0xFF111413` or White), and foreground modules (Tactical Mint `0xFF68D391`, Tactical Green `0xFF2E7D32`, or Black).

---

## 6. Viewfinder & Fallback Architecture

The scanning UI (`QrScannerView`) provides dual-mode input:

1. **Optical HUD Reticle:**
   - Tactical green corner brackets framing the scanning region.
   - Sweeping amber/orange laser animation indicating live viewfinder state.
   - Camera permission handler with graceful degraded-mode support.
   - Torch/flashlight toggle for low-light tactical environments.
2. **Manual Input / Clipboard Paste Modal:**
   - Accessible via the prominent `[ MANUAL CODE / PASTE FALLBACK ]` HUD button.
   - Allows operators to paste or type raw payload strings directly.
   - Supports 1-click clipboard paste (`[ PASTE FROM CLIPBOARD ]`).
   - Essential for field handsets with damaged cameras, obscured lenses, or in radio silence where optical display is prohibited.

---

## 7. Validation & Rejection Pipeline

Scanned or pasted payloads pass through `QrPairingValidator.validateScannedQr()`:

```kotlin
when (val result = QrPairingValidator.validateScannedQr(payload, localNodeId, contactRepository)) {
    is ValidationResult.Success -> {
        // Show confirmation dialog with UNVERIFIED badge
        showConfirmation(result.payload)
    }
    is ValidationResult.SelfNode -> {
        // Reject self-scan
        showAlert("SELF-NODE DETECTED", "This QR code belongs to this device (#${result.nodeId}).")
    }
    is ValidationResult.Duplicate -> {
        // Reject existing contact
        showAlert("CONTACT ALREADY EXISTS", "Node #${result.existingContact.nodeId} is already in directory.")
    }
    is ValidationResult.Invalid -> {
        // Reject corrupted or foreign payloads
        showAlert("INVALID QR CODE", "Payload error: ${result.reason}")
    }
}
```

---

## 8. Presentation Components Delivered

| Component | File Path | Purpose |
| :--- | :--- | :--- |
| `QrIdentityCard` | `presentation/components/QrIdentityCard.kt` | Tactical card rendering the on-device generated QR bitmap, callsign, node ID badge, language tags, and clipboard copy action. |
| `QrPairingConfirmation` | `presentation/components/QrPairingConfirmation.kt` | Modal dialog showing peer identity, amber UNVERIFIED badge, security advisory, and "ADD CONTACT" action. |
| `QrScannerView` | `presentation/components/QrScannerView.kt` | Tactical viewfinder with animated sweeping laser line, HUD brackets, torch toggle, and manual input modal. |
| `MyNodeQrScreen` | `presentation/screens/MyNodeQrScreen.kt` | Local operator screen broadcasting own identity QR code with security guarantee details. |
| `ScanNodeQrScreen` | `presentation/screens/ScanNodeQrScreen.kt` | Scanning workflow orchestrator integrating camera viewfinder, confirmation dialog, and rejection alerts. |
| `QrPairingActivity` | `presentation/QrPairingActivity.kt` | Standalone 2-tab debug activity (`MY QR` / `SCAN PEER`) registered in `app/src/debug/AndroidManifest.xml`. |

---

## 9. Comprehensive Automated Test Matrix

All 19 unit test cases across `org.sih.itantra.core.pairing.*` passed with 100% success:

### 9.1 `QrIdentityCodecTest` (10 Tests)
| # | Test Case | Target Requirement | Result |
| :--- | :--- | :--- | :--- |
| 1 | `testDeterministicEncoding` | Format matches `ITANTRA:1|<nodeId>|<callsign>|<name>|<langs>` | **PASSED** |
| 2 | `testDecodingValidPayload` | Full payload parses into `QrIdentityPayload` | **PASSED** |
| 3 | `testDecodingWithSpecialCharacters` | Pipe characters in names are safely sanitized | **PASSED** |
| 4 | `testDecodingMinimalPayload` | Empty display name falls back to `NODE #<nodeId>` | **PASSED** |
| 5 | `testUnknownLanguagesDefaultToHindi` | Unrecognized language tags default safely to Hindi | **PASSED** |
| 6 | `testPayloadTooLargeRejected` | Strings > 512 bytes fail validation | **PASSED** |
| 7 | `testInvalidPrefixRejected` | Non-`ITANTRA:` payloads rejected | **PASSED** |
| 8 | `testUnsupportedVersionRejected` | Version numbers other than 1 are rejected | **PASSED** |
| 9 | `testMalformedFieldsRejected` | Missing pipe fields rejected | **PASSED** |
| 10 | `testInvalidNodeIdRejected` | Non-numeric or negative node IDs rejected | **PASSED** |

### 9.2 `QrPairingValidatorTest` (6 Tests)
| # | Test Case | Target Requirement | Result |
| :--- | :--- | :--- | :--- |
| 11 | `testValidNewContactReturnsSuccess` | Unregistered peer returns `ValidationResult.Success` | **PASSED** |
| 12 | `testSelfNodeScanRejected` | `scannedId == localNodeId` returns `ValidationResult.SelfNode` | **PASSED** |
| 13 | `testDuplicateContactRejected` | Peer already in `ContactRepository` returns `Duplicate` | **PASSED** |
| 14 | `testInvalidPayloadFormatRejected` | Malformed strings return `ValidationResult.Invalid` | **PASSED** |
| 15 | `testOversizedPayloadRejected` | Over-512-byte payloads return `ValidationResult.Invalid` | **PASSED** |
| 16 | `testUnverifiedTrustAssigned` | `payload.toContactIdentity()` strictly sets `authStatus = UNVERIFIED` | **PASSED** |

### 9.3 `QrMatrixEncoderTest` (3 Tests)
| # | Test Case | Target Requirement | Result |
| :--- | :--- | :--- | :--- |
| 17 | `testQrMatrixDimensionValid` | Validates standard matrix sizes (21 + 4(V-1)) | **PASSED** |
| 18 | `testFinderPatternsPlaced` | Verifies 7x7 finder patterns at all three corners | **PASSED** |
| 19 | `testDeterministicMatrixGeneration` | Identical inputs produce bit-for-bit identical matrices | **PASSED** |

### 9.4 Full Repository Regression Baseline
- **Total Project Unit Tests:** **496 / 496 PASSED (100% Success Rate)**.
- **Failures:** 0.
- **Skipped:** 0.
- **Regression Against Prior Features:** Zero regression across Features 1 through 9.

---

## 10. Physical Device Smoke-Testing on Phone A

Physical hardware verification was performed on **Phone A** (`Samsung Galaxy / RZCY9396AGX`):

1. **Deployment & Launch:**
   - `assembleDebug` completed in 36 seconds.
   - Installed via `adb install -r app-debug.apk`.
   - `am start -n org.sih.itantra/.presentation.QrPairingActivity` launched in 1.036s.
2. **Live Screen Validations:**
   - **`screen_pairing_latest.png` (MY QR Tab):** Crisp on-device generated QR matrix, callsign `NODE ALPHA`, badge `NODE #209070`, subtitle `Tactical Unit Alpha`, language pills (`HINDI`, `ENGLISH`), and `ZERO-SECRET GUARANTEE` advisory card.
   - **`screen_scan_latest.png` (SCAN PEER Tab):** Tactical HUD reticle with corner brackets, animated sweeping laser line, torch toggle, and fallback button.
   - **`screen_manual_open.png` (Manual Input Modal):** Field input dialog with `[PASTE FROM CLIPBOARD]` and `[DECODE & CONFIRM]` actions.
   - **`confirm5.png` (Confirmation Dialog):** Scanned identity `NODE #884411` (`BRAVO SCOUT`), high-visibility amber `TRUST STATE: UNVERIFIED` banner, and `[ADD CONTACT]` action.

---

## 11. Isolation & Architecture Preservation

Feature 10 was developed under strict parallel isolation rules:
- **`MainActivity.kt`**: Untouched.
- **`BottomNavBar.kt`**: Untouched.
- **`TransceiverViewModel.kt`**: Untouched.
- **`ChatRepository.kt`**: Untouched.
- **STT/TTS Services**: Untouched.
- **Radio Transceiver & Mesh Protocols**: Untouched.
- **Nearby Discovery Implementation**: Untouched.
- **Contacts Storage Schema**: Unmodified (`ContactIdentity` and `ContactRepository` formats preserved).

---

## 12. Verification Summary

```
======================================================================
FEATURE 10: OFFLINE QR NODE / CONTACT PAIRING — VERIFICATION SUMMARY
======================================================================
Codecs & Models:             PASSED (Pure Kotlin, ISO/IEC 18004, GF(256))
Security Guarantee:          PASSED (Zero Secrets, UNVERIFIED Trust Model)
Rejection Pipeline:          PASSED (Self-Node & Duplicate Prevention)
Viewfinder & Fallback:       PASSED (HUD Reticle + Manual Paste Modal)
Unit Tests (Pairing):        19 / 19 PASSED (100%)
Unit Tests (Full Project):   496 / 496 PASSED (100%, 0 Failures)
Physical Device (Phone A):   PASSED (Live Render, Scan HUD, Confirmation Dialog)
Architecture Isolation:      VERIFIED (Zero frozen files modified)
======================================================================
```
