package org.sih.itantra.presentation.theme

import androidx.compose.ui.graphics.Color

// Official iTantra Field Radio Palette (from verified hardware design system)
val ColorForest = Color(0xFF0F2D23)
val ColorSage = Color(0xFF2E7D32)
val ColorSand = Color(0xFFF6F4ED)
val ColorSandSurface = Color(0xFFEBE7DC)
val ColorSandBorder = Color(0xFFDCD6C7)
val ColorSandCapsule = Color(0xFFE4DFD3)

val ColorCharcoal = Color(0xFF1B1F1D)
val ColorCharcoalSurface = Color(0xFF242A27)
val ColorCharcoalBorder = Color(0xFF333B37)
val ColorCharcoalCapsule = Color(0xFF2B322E)

val ColorAlert = Color(0xFFD84315)        // Warm burnt orange / Alert / Listening state
val ColorSuccess = Color(0xFF2E7D32)      // Sage / Transmit / Connected
val ColorWarning = Color(0xFFFFB300)      // Amber warning
val ColorSignalBlue = Color(0xFF0288D1)   // Muted Industrial Signal Blue

// Backwards-compatible aliases for existing tactical themes and screens
val TacticalBackground = ColorCharcoal
val TacticalSurface = ColorCharcoalSurface
val TacticalSurfaceHighlight = ColorCharcoalCapsule
val TacticalBorder = ColorCharcoalBorder

val RadarGreen = ColorSuccess
val RadarGreenDim = Color(0xFF1B5E20)
val SignalBlue = ColorSignalBlue
val AlertAmber = ColorWarning
val DistressRed = ColorAlert

val TextPrimaryDark = Color(0xFFF6F4ED)
val TextSecondaryDark = Color(0xFF8D9993)
val TextTertiaryDark = Color(0xFF5A635E)

val TextPrimaryLight = Color(0xFF1B1F1D)
val TextSecondaryLight = Color(0xFF5A625E)
val TextTertiaryLight = Color(0xFF8D9590)

val TextPrimary = TextPrimaryDark
val TextSecondary = TextSecondaryDark
val TextTertiary = TextTertiaryDark
