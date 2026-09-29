# iTantra Current Design System & UI Token Audit
**Audit Baseline Commit:** `d5937ad465616733e50bb7faa375b68cce43e96c` (Tag: `v1.2.0-pre-ui-overhaul`)  
**Scope:** Design tokens, colors, typography scales, shape geometries, component patterns, and visual fragmentation across the presentation layer.

---

## 1. Executive Summary & Design System Philosophy

The visual design language of iTantra is modeled after **tactical military/disaster field radio equipment** ("Field Radio aesthetic"). It relies on an organic palette of deep forest greens, desert sand surfaces, rugged charcoal containers, and high-contrast amber/orange alert indicators. 

However, because features were added incrementally across 38 distinct engineering phases, the presentation layer suffers from **design token drift and severe component styling fragmentation**:
- **570 instances** of inline `fontFamily = FontFamily.Monospace` scattered across 62 presentation files.
- **670 instances** of inline `fontSize = XX.sp` overrides instead of consuming Material 3 typography tokens.
- **13 competing corner radii** (`0.dp`, `1.dp`, `2.dp`, `3.dp`, `4.dp`, `6.dp`, `8.dp`, `10.dp`, `12.dp`, `14.dp`, `16.dp`, `20.dp`, `24.dp`) without a cohesive shape hierarchy.
- Absence of a centralized `Typography` configuration passed into `MaterialTheme` in `Theme.kt`.

---

## 2. Color Palette & Token Architecture

The color system is declared in `org.sih.itantra.presentation.theme.Color.kt` and managed via a custom `RadioColors` data class in `Theme.kt`, provided through `CompositionLocalProvider(LocalRadioColors provides radioColors)`.

### 2.1 Core Tactical Palette Table

| Token Name | Hex Code | Visual Swatch | Semantic Role in Tactical UI | Inconsistencies / Alias Notes |
|---|---|---|---|---|
| `ColorForest` | `#0F2D23` | Deep Pine Green | Primary military brand color; headers; dark surface accents | Hardcoded in `DarkColorScheme.secondary` and `LightColorScheme.primary` |
| `ColorSage` | `#2E7D32` | Muted Foliage Green | Success state, transmission active, primary accent | Also aliased to `ColorSuccess` and `RadarGreen` |
| `ColorSand` | `#F6F4ED` | Warm Ivory / Off-White | Primary light mode background, dark mode primary text | Re-used as `TextPrimaryDark` and `onSecondary` |
| `ColorSandSurface` | `#EBE7DC` | Soft Khaki Stone | Light mode card and container surface | Paired with `ColorSandBorder` |
| `ColorSandBorder` | `#DCD6C7` | Muted Putty Grey | Light mode outlines, dividing rules, card borders | Reused across light mode chips |
| `ColorSandCapsule` | `#E4DFD3` | Desert Drab Neutral | Light mode chip, pill, and button backgrounds | Subtle contrast against `ColorSandSurface` |
| `ColorCharcoal` | `#1B1F1D` | Deep Matte Charcoal | Dark mode root background, primary field radio body | Aliased as `TacticalBackground` |
| `ColorCharcoalSurface` | `#242A27` | Dark Slate Olive | Dark mode container surfaces, cards, message bubbles | Aliased as `TacticalSurface` |
| `ColorCharcoalBorder` | `#333B37` | Dark Industrial Grey | Dark mode card borders, divider rules | Aliased as `TacticalBorder` |
| `ColorCharcoalCapsule` | `#2B322E` | Deep Olive Charcoal | Dark mode chip backgrounds, pill containers | Aliased as `TacticalSurfaceHighlight` |
| `ColorAlert` | `#D84315` | Burnt Terracotta / Rust | Emergency SOS distress, listening PTT state, errors | Aliased as `DistressRed` and `danger` |
| `ColorWarning` | `#FFB300` | Amber Hazard Yellow | Warning badges, degraded transport state, stale routes | Aliased as `AlertAmber` |
| `ColorSignalBlue` | `#0288D1` | Muted Industrial Cyan | Diagnostic telemetry, Bluetooth state, network links | Aliased as `SignalBlue` |
| `RadarGreenDim` | `#1B5E20` | Dark Forest Green | Secondary radar concentric grid rings | Dedicated radar visualizer color |

### 2.2 Text Hierarchy Tokens

| Token Name | Hex Code | Intended Contrast Role | Current Usage Defect |
|---|---|---|---|
| `TextPrimaryDark` | `#F6F4ED` | Dark mode primary labels, transcripts, sender titles | Overridden inline in 100+ files with explicit color args |
| `TextSecondaryDark` | `#8D9993` | Dark mode subtitles, timestamps, RSSI metrics | Frequently replaced with `Color.Gray` or `Color.White.copy(0.7f)` |
| `TextTertiaryDark` | `#5A635E` | Dark mode inactive icons, subtle borders, metadata | Frequently replaced with `Color.DarkGray` |
| `TextPrimaryLight` | `#1B1F1D` | Light mode primary high-contrast text | Rarely tested in physical field runs |
| `TextSecondaryLight` | `#5A625E` | Light mode secondary metadata | Used in light theme preview cards |
| `TextTertiaryLight` | `#8D9590` | Light mode disabled/dim text | Used in light theme preview cards |

---

## 3. Typography Scale & Monospace Fragmentation Analysis

### 3.1 The Monospace Dilemma
In `Theme.kt`, `ITantraTheme` initializes `MaterialTheme`:
```kotlin
CompositionLocalProvider(LocalRadioColors provides radioColors) {
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```
Because no `typography = ...` parameter is supplied to `MaterialTheme`, the app falls back to standard Material 3 Roboto. To evoke a tactical radio aesthetic, developers manually applied `fontFamily = FontFamily.Monospace` directly to individual `Text` composables throughout the app.

### 3.2 Quantitative Evidence of Typography Drift
- **Total occurrences of `fontFamily = FontFamily.Monospace`:** **`570`** across **`62`** files.
- **Total occurrences of inline `fontSize = ...sp` overrides:** **`670`** across **`62`** files.
- **Sample Distribution of Inline Font Sizes:**
  - `9.sp`, `10.sp`: Telemetry labels, packet bytes counters (e.g. `RadioTranscriptRow.kt`, `MessageRadioTelemetry.kt`).
  - `11.sp`, `11.5.sp`: Secondary node IDs, RSSI dBm values (e.g. `NearbyDeviceCard.kt`).
  - `12.sp`, `13.sp`: Subtitles, channel status, timestamps.
  - `14.sp`: Message body transcripts, dialogue contents.
  - `16.sp`, `18.sp`: Section headers, dialog headers.
  - `24.sp`, `32.sp`: Top radio callsign and SOS emergency titles.

### 3.3 Negative User Impact
1. **Broken Dynamic Font Scaling:** Manual `fontSize = 11.sp` without matching `lineHeight` breaks when a visually impaired user enables Android system font enlargement (1.2x or 1.5x scale), causing characters to clip or vertical text overlapping.
2. **Maintenance Complexity:** Changing the visual density or font family requires editing over 600 distinct lines of code instead of a single theme configuration.

---

## 4. Geometry & Shape System Fragmentation

An audit of `RoundedCornerShape` usages reveals an uncontrolled proliferation of corner radii:

```
Distribution of 13 Competing Corner Radii:
[0.dp]   ████ (Used in QR screens, borders, flat cards)
[1.dp]   █ (Individual chat divider)
[2.dp]   ██ (Visualizer bars, Manet demo chips)
[3.dp]   ██ (TTS playback indicator, settings chips)
[4.dp]   ████████████████ (Status tags, mini buttons, small chips)
[6.dp]   ████████████ (Contact badges, transcript bubbles)
[8.dp]   ████████████████████████ (Standard cards, text fields)
[10.dp]  ████████████ (Confirmation dialogs, headers)
[12.dp]  ████████████████████ (Large container cards, banners)
[14.dp]  ████ (Settings section cards, nearby cards)
[16.dp]  ██████ (Emergency alert dialog, QR scan frame)
[20.dp]  ██ (Search pills)
[24.dp]  ████ (Pills, callsign headers, avatar circles)
```

### Proposed Shape Token Rationalization for Future Overhaul
| Token Role | Recommended Value | Target Usage |
|---|---|---|
| `ShapeNone` | `0.dp` | Full-width banners, edge-to-edge camera viewfinders |
| `ShapeSmall` | `4.dp` | Telemetry tags, status badges, RSSI chips, progress bars |
| `ShapeMedium` | `8.dp` | Standard buttons, dialog action buttons, text input fields |
| `ShapeLarge` | `12.dp` | Content cards, message bubbles, settings sections, dialogs |
| `ShapePill` | `50%` (Circle / Pill) | PTT trigger button, distress button, active search bar |

---

## 5. Component Taxonomy & Visual Anatomy

### 5.1 Push-To-Talk (PTT) Controls
- **`RadioPttControl.kt` & `TacticalPttButton.kt`:**
  - States: `IDLE` (Deep Charcoal / Green border), `LISTENING / TRANSMITTING` (Burnt Terracotta Orange pulsating glow), `PROCESSING / STT` (Amber breathing animation).
  - Haptic feedback: Long-press trigger with audio click sound effect.
  - Waveform feedback: Real-time 20-bar Canvas audio amplitude visualizer rendered directly above the PTT circle.

### 5.2 Distress & Emergency Components
- **`EmergencyDistressButton.kt` & `EmergencyBanner.kt`:**
  - High-visibility safety element: High-contrast `ColorAlert` (#D84315) background with white bold uppercase lettering.
  - Requires 2-second press-and-hold confirmation dialog to prevent accidental triggers.
  - Sends high-priority QoS level 3 broadcast packets across all active radio transports simultaneously.

### 5.3 Radar & Topology Visualizers
- **`NearbyDevicesScreen.kt` (Radar Canvas):**
  - Custom Canvas rendering concentric green rings at -90 dBm, -70 dBm, -50 dBm thresholds.
  - Blip animations: Pulsing circles representing discovered peer handsets with distance determined by signal strength.
- **`MeshTopologyCanvas.kt`:**
  - Force-directed graph rendering active mesh nodes as tactical circular icons connected by colored vector lines representing transport medium (Green = Wi-Fi UDP, Blue = Bluetooth SPP, Orange = Wi-Fi Direct).

---

## 6. Actionable UI Overhaul Blueprint

When the upcoming UI overhaul begins, the following foundational architecture should be established:

1. **Create `Type.kt`:** Define a cohesive `Typography` object containing `TacticalTypography`:
   - `displaySmall`, `titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`, `labelSmall`.
   - Pass this directly into `MaterialTheme(typography = AppTypography)`.
2. **Enforce Unified Shapes:** Define `AppShapes` using Material 3 `Shapes(small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(12.dp))`.
3. **Consolidate Custom Colors:** Formalize `RadioColors` into standard Material 3 `ColorScheme` extension properties or a typed composition local without ad-hoc hex code duplication.
4. **Eliminate Word-Wrap Defect in Settings:** Redesign the transport selection cards using responsive flex layouts (`FlowRow`) with standardized badge heights.
