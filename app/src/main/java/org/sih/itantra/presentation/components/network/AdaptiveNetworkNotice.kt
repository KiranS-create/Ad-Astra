package org.sih.itantra.presentation.components.network

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import org.sih.itantra.core.network.AdaptiveComposerState
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Feature 14: Compact tactical notice embedded directly above the composer
 * providing strict truthfulness regarding transmission expectations.
 */
@Composable
fun AdaptiveNetworkNotice(
    composerState: AdaptiveComposerState,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val pillColor = when {
        composerState.isOffline     -> radioColors.alert
        composerState.isCongested   -> radioColors.alert
        composerState.isWaitingRoute -> Color(0xFF388BFD)
        composerState.isDtnStored   -> Color(0xFFFF9800)
        !composerState.canTransmitImmediately -> radioColors.warning
        else                        -> if (radioColors.isDark) radioColors.sage else radioColors.forest
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.background.copy(alpha = 0.7f))
            .border(1.dp, radioColors.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Action Pill
            composerState.actionPillText?.let { pill ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(pillColor.copy(alpha = 0.15f))
                        .border(1.dp, pillColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = pill,
                        color = pillColor,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Expectation Text
            Text(
                text = composerState.deliveryExpectationText,
                color = radioColors.textSecondary,
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }

        // Emergency Priority Bypass Callout (Feature 14 requirement for Congested / Offline)
        composerState.emergencyPriorityNotice?.let { emergencyNotice ->
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(radioColors.alert.copy(alpha = 0.12f))
                    .border(1.dp, radioColors.alert.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠",
                    color = radioColors.alert,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = emergencyNotice,
                    color = radioColors.alert,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}
