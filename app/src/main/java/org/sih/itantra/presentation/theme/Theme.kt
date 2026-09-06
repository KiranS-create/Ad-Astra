package org.sih.itantra.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class RadioColors(
    val background: Color,
    val surface: Color,
    val surfaceHighlight: Color,
    val border: Color,
    val capsule: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val forest: Color,
    val sage: Color,
    val alert: Color,
    val warning: Color,
    val success: Color,
    val isDark: Boolean
)

val LocalRadioColors = staticCompositionLocalOf {
    DarkRadioColors
}

val LightRadioColors = RadioColors(
    background = ColorSand,
    surface = ColorSandSurface,
    surfaceHighlight = Color(0xFFDFDACB),
    border = ColorSandBorder,
    capsule = ColorSandCapsule,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    forest = ColorForest,
    sage = ColorSage,
    alert = ColorAlert,
    warning = ColorWarning,
    success = ColorSuccess,
    isDark = false
)

val DarkRadioColors = RadioColors(
    background = ColorCharcoal,
    surface = ColorCharcoalSurface,
    surfaceHighlight = Color(0xFF2C3430),
    border = ColorCharcoalBorder,
    capsule = ColorCharcoalCapsule,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    forest = Color(0xFF143D30),
    sage = ColorSage,
    alert = ColorAlert,
    warning = ColorWarning,
    success = ColorSuccess,
    isDark = true
)

private val LightColorScheme = lightColorScheme(
    primary = ColorForest,
    onPrimary = ColorSand,
    secondary = ColorSage,
    onSecondary = ColorSand,
    background = ColorSand,
    surface = ColorSandSurface,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    error = ColorAlert,
    onError = ColorSand
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorSage,
    onPrimary = ColorCharcoal,
    secondary = ColorForest,
    onSecondary = ColorSand,
    background = ColorCharcoal,
    surface = ColorCharcoalSurface,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    error = ColorAlert,
    onError = TextPrimaryDark
)

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun ITantraTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val radioColors = if (darkTheme) DarkRadioColors else LightRadioColors

    CompositionLocalProvider(LocalRadioColors provides radioColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
