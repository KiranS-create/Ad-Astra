package org.sih.itantra.presentation.components.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.sih.itantra.core.network.AdaptiveNetworkBannerState
import org.sih.itantra.core.network.AdaptiveNetworkMode
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical banner positioned beneath the chat header communicating the real
 * underlying network transport/reachability state.
 */
@Composable
fun NetworkContextBanner(
    bannerState: AdaptiveNetworkBannerState,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val (accentColor, bgColor, symbol) = when (bannerState.mode) {
        AdaptiveNetworkMode.HEALTHY -> Triple(
            if (radioColors.isDark) Color(0xFF3FB950) else Color(0xFF2E7D32),
            if (radioColors.isDark) Color(0xFF3FB950).copy(alpha = 0.12f) else Color(0xFF2E7D32).copy(alpha = 0.10f),
            "●"
        )
        AdaptiveNetworkMode.LIMITED -> Triple(
            Color(0xFFD29922),
            Color(0xFFD29922).copy(alpha = 0.14f),
            "⌁"
        )
        AdaptiveNetworkMode.DEGRADED -> Triple(
            Color(0xFFDB6D28),
            Color(0xFFDB6D28).copy(alpha = 0.14f),
            "▲"
        )
        AdaptiveNetworkMode.OFFLINE -> Triple(
            radioColors.alert,
            radioColors.alert.copy(alpha = 0.14f),
            "✕"
        )
        AdaptiveNetworkMode.CONGESTED -> Triple(
            radioColors.alert,
            radioColors.alert.copy(alpha = 0.18f),
            "⚡"
        )
        AdaptiveNetworkMode.WAITING_FOR_ROUTE -> Triple(
            Color(0xFF388BFD),
            Color(0xFF388BFD).copy(alpha = 0.14f),
            "⌕"
        )
        AdaptiveNetworkMode.DTN_STORED -> Triple(
            Color(0xFFFF9800),
            Color(0xFFFF9800).copy(alpha = 0.14f),
            "⏸"
        )
        AdaptiveNetworkMode.UNKNOWN -> Triple(
            radioColors.textTertiary,
            radioColors.surface,
            "?"
        )
    }

    AnimatedVisibility(
        visible = bannerState.isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .border(1.dp, accentColor.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = symbol,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = bannerState.bannerText,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = bannerState.badgeLabel,
                    color = accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
