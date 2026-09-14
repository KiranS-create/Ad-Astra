package org.sih.itantra.presentation.components.health

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
import org.sih.itantra.core.health.OverallHealthStatus
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical Hero Banner displaying top-level communication health status.
 */
@Composable
fun OverallHealthBanner(
    status: OverallHealthStatus,
    badgeLabel: String,
    summary: String,
    localNodeId: Int,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val (statusColor, statusBg) = when (status) {
        OverallHealthStatus.HEALTHY -> Pair(radioColors.success, radioColors.success.copy(alpha = 0.12f))
        OverallHealthStatus.LIMITED,
        OverallHealthStatus.DEGRADED -> Pair(radioColors.warning, radioColors.warning.copy(alpha = 0.14f))
        OverallHealthStatus.OFFLINE  -> Pair(radioColors.alert, radioColors.alert.copy(alpha = 0.15f))
        OverallHealthStatus.UNKNOWN  -> Pair(radioColors.textSecondary, radioColors.surface)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(radioColors.surface)
            .border(1.5.dp, statusColor.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OVERALL COMMUNICATION HEALTH",
                    color = radioColors.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(radioColors.background)
                        .border(1.dp, radioColors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "NODE #$localNodeId",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.label,
                    color = statusColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        color = statusColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = summary,
                color = radioColors.textPrimary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
