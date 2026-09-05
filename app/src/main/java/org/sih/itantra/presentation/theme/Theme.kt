package org.sih.itantra.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = RadarGreen,
    onPrimary = TacticalBackground,
    secondary = SignalBlue,
    onSecondary = TacticalBackground,
    background = TacticalBackground,
    surface = TacticalSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = DistressRed,
    onError = TextPrimary
)

@Composable
fun ITantraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
