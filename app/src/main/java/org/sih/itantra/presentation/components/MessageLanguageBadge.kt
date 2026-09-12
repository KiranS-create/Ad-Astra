package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.tts.TtsLanguage
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical language badge displaying the message's detected/tagged language
 * and offline TTS synthesis readiness.
 *
 * Examples:
 *   [HI · TTS READY]
 *   [TA · TTS READY]
 *   [ML · TTS UNAVAILABLE]
 *   [?? · LANGUAGE UNKNOWN]
 */
@Composable
fun MessageLanguageBadge(
    language: TtsLanguage,
    isTtsReady: Boolean = true,
    statusLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val (bg, borderColor, textColor, displayText) = when {
        language == TtsLanguage.UNKNOWN -> {
            val label = statusLabel ?: "LANGUAGE UNKNOWN"
            Quad(
                radioColors.capsule,
                radioColors.border.copy(alpha = 0.4f),
                radioColors.textTertiary,
                "?? · $label"
            )
        }
        !isTtsReady -> {
            val label = statusLabel ?: "TTS UNAVAILABLE"
            Quad(
                radioColors.alert.copy(alpha = 0.12f),
                radioColors.alert.copy(alpha = 0.4f),
                radioColors.alert,
                "${language.badgeCode} · $label"
            )
        }
        else -> {
            val label = statusLabel ?: "TTS READY"
            Quad(
                radioColors.capsule,
                radioColors.border.copy(alpha = 0.5f),
                if (radioColors.isDark) radioColors.sage else radioColors.forest,
                "${language.badgeCode} · $label"
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    ) {
        Text(
            text = displayText,
            color = textColor,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.4.sp
        )
    }
}

/**
 * Overload accepting nullable IndicLanguage directly from MessageRecord.
 */
@Composable
fun MessageLanguageBadge(
    indicLanguage: IndicLanguage?,
    isTtsReady: Boolean = true,
    statusLabel: String? = null,
    modifier: Modifier = Modifier
) {
    MessageLanguageBadge(
        language = TtsLanguage.fromIndicLanguage(indicLanguage),
        isTtsReady = isTtsReady,
        statusLabel = statusLabel,
        modifier = modifier
    )
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
