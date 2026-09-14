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
import org.sih.itantra.core.health.DeliveryHealthSummary
import org.sih.itantra.presentation.theme.LocalRadioColors
import java.util.Locale

/**
 * Tactical Card summarizing Feature 6 delivery states across message history.
 */
@Composable
fun DeliveryHealthCard(
    deliveryHealth: DeliveryHealthSummary,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "DELIVERY HEALTH (FEATURE 6)",
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                // 3x2 Grid of Delivery State Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DeliveryChip(
                        count = deliveryHealth.acknowledgedCount,
                        label = "ACKNOWLEDGED",
                        color = radioColors.success,
                        modifier = Modifier.weight(1f)
                    )
                    DeliveryChip(
                        count = deliveryHealth.ackPendingCount,
                        label = "ACK PENDING",
                        color = radioColors.warning,
                        modifier = Modifier.weight(1f)
                    )
                    DeliveryChip(
                        count = deliveryHealth.relayedCount,
                        label = "RELAYED",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DeliveryChip(
                        count = deliveryHealth.dtnStoredCount,
                        label = "DTN STORED",
                        color = radioColors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    DeliveryChip(
                        count = deliveryHealth.failedCount,
                        label = "FAILED / T.O.",
                        color = if (deliveryHealth.failedCount > 0) radioColors.alert else radioColors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    DeliveryChip(
                        count = deliveryHealth.totalMessagesCount,
                        label = "TOTAL MSGS",
                        color = radioColors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                HealthFieldRow(
                    label = "Last Delivery Receipt RTT",
                    value = deliveryHealth.lastDeliveryAckLatencyMs?.let {
                        String.format(Locale.US, "%.1f ms", it)
                    } ?: "NOT MEASURED",
                    valueColor = if (deliveryHealth.lastDeliveryAckLatencyMs != null) radioColors.success else radioColors.textTertiary
                )

                deliveryHealth.deliverySuccessRatePercent?.let { rate ->
                    HealthFieldRow(
                        label = "Historical Delivery Success",
                        value = String.format(Locale.US, "%.1f%%", rate),
                        valueColor = if (rate >= 80.0) radioColors.success else radioColors.warning
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryChip(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.background)
            .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$count",
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label,
                color = radioColors.textSecondary,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
