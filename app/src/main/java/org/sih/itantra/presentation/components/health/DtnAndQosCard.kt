package org.sih.itantra.presentation.components.health

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.health.DtnHealthState
import org.sih.itantra.core.health.QosHealthState
import org.sih.itantra.core.qos.CongestionState
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical Card displaying live telemetry for the QoS transmission queue and DTN store-and-forward buffer.
 */
@Composable
fun DtnAndQosCard(
    qosHealth: QosHealthState,
    dtnHealth: DtnHealthState,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val qosColor = when (qosHealth.congestionState) {
        CongestionState.NORMAL    -> radioColors.success
        CongestionState.BUSY      -> radioColors.warning
        CongestionState.CONGESTED -> radioColors.alert
    }

    val dtnColor = when {
        dtnHealth.queueSize >= 35 -> radioColors.alert
        dtnHealth.queueSize > 0   -> radioColors.warning
        else                      -> radioColors.success
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "QOS TRANSMISSION & DTN BUFFER",
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
                // QoS Telemetry
                HealthFieldRow(
                    label = "QoS Congestion State",
                    value = "${qosHealth.congestionState.name} (${qosHealth.queuedPackets}/${qosHealth.maxCapacity} pkts)",
                    valueColor = qosColor
                )
                HealthFieldRow(
                    label = "Priority Breakdown",
                    value = "D: ${qosHealth.queuedDistress} | A: ${qosHealth.queuedAlert} | I: ${qosHealth.queuedImportant} | N: ${qosHealth.queuedNormal}",
                    valueColor = radioColors.textPrimary
                )
                HealthFieldRow(
                    label = "Emergency Pre-emptions",
                    value = "${qosHealth.emergencyPreemptions} Executed",
                    valueColor = if (qosHealth.emergencyPreemptions > 0) radioColors.success else radioColors.textSecondary
                )
                HealthFieldRow(
                    label = "Starvation Avoidance Rescues",
                    value = "${qosHealth.normalStarvationAvoidance} Normal pkts",
                    valueColor = radioColors.textSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // DTN Store-and-Forward Telemetry
                HealthFieldRow(
                    label = "DTN Stored Queue",
                    value = "${dtnHealth.queueSize} / ${dtnHealth.maxCapacity} Packets",
                    valueColor = dtnColor
                )
                HealthFieldRow(
                    label = "DTN Packets Forwarded",
                    value = "${dtnHealth.totalForwardedPackets} Forwarded",
                    valueColor = if (dtnHealth.totalForwardedPackets > 0) radioColors.success else radioColors.textSecondary
                )
                HealthFieldRow(
                    label = "DTN Expired (10m TTL)",
                    value = "${dtnHealth.totalExpiredPackets} Expired",
                    valueColor = if (dtnHealth.totalExpiredPackets > 0) radioColors.warning else radioColors.textSecondary
                )
                HealthFieldRow(
                    label = "DTN Evicted / Dropped",
                    value = "${dtnHealth.totalDroppedPackets} Dropped",
                    valueColor = if (dtnHealth.totalDroppedPackets > 0) radioColors.alert else radioColors.textSecondary
                )
            }
        }
    }
}
