package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.tts.MessagePlaybackState
import org.sih.itantra.core.tts.TtsPlaybackStatus
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical playback status indicator rendered when a message is actively loading,
 * synthesizing, or playing through offline TTS.
 *
 * Example visual presentation:
 *   ─────────────────────────────────
 *   ▶ PLAYING
 *   TTS: TAMIL (MMS VITS)
 */
@Composable
fun TtsPlaybackIndicator(
    playbackState: MessagePlaybackState,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val (actionLabel, detailLabel, tintColor) = when (playbackState.status) {
        TtsPlaybackStatus.LOADING_VOICE -> Triple(
            "⏳ LOADING VOICE MODEL...",
            "TTS: ${playbackState.language.displayName.uppercase()}",
            radioColors.warning
        )
        TtsPlaybackStatus.SYNTHESIZING -> Triple(
            "⚡ SYNTHESIZING OFFLINE SPEECH...",
            "TTS: ${playbackState.language.displayName.uppercase()}",
            if (radioColors.isDark) radioColors.sage else radioColors.forest
        )
        TtsPlaybackStatus.PLAYING -> Triple(
            "▶ PLAYING AUDIO",
            "TTS: ${playbackState.language.displayName.uppercase()}",
            if (radioColors.isDark) radioColors.sage else radioColors.forest
        )
        TtsPlaybackStatus.UNAVAILABLE -> Triple(
            "⚠ TTS UNAVAILABLE",
            playbackState.error ?: "MODEL ASSET NOT READY",
            radioColors.alert
        )
        TtsPlaybackStatus.FAILED -> Triple(
            "✕ SYNTHESIS FAILED",
            playbackState.error ?: "AUDIO PIPELINE ERROR",
            radioColors.alert
        )
        TtsPlaybackStatus.COMPLETED,
        TtsPlaybackStatus.IDLE -> return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        HorizontalDivider(
            color = radioColors.border.copy(alpha = 0.4f),
            thickness = 1.dp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(tintColor.copy(alpha = 0.10f))
                .border(1.dp, tintColor.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = actionLabel,
                color = tintColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            Text(
                text = detailLabel,
                color = radioColors.textSecondary,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
