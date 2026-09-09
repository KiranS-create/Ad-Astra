package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import org.sih.itantra.core.message.RadioMessageTelemetry
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Expanded radio telemetry panel shown inside a message bubble when the operator
 * taps to reveal the Technical Packet Inspector.
 *
 * This panel extends the EXISTING packet inspector in [IndividualChatScreen] with
 * Feature 6 radio state fields, rendered BELOW the existing inspector rows.
 *
 * Only non-null fields are displayed — no fabricated values.
 *
 * Fields shown:
 * - RADIO STATE
 * - TRANSPORT          (if available)
 * - HOP COUNT          (if available)
 * - ACK RTT LATENCY    (if ACKNOWLEDGED with RTT)
 * - DTN PENDING        (if DTN_STORED)
 * - FRAGMENTED         (if fragmentCount > 1)
 * - AUTH STATUS        (HMAC-SHA256 or UNVERIFIED)
 * - QOS STATUS         (if present)
 */
@Composable
fun MessageRadioTelemetry(
    telemetry: RadioMessageTelemetry,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val relayBlue = Color(0xFF0288D1)
    val dtnAmber = Color(0xFFFF9800)

    val stateColor = when {
        telemetry.deliveryState.name == "ACKNOWLEDGED" || telemetry.deliveryState.name == "RECEIVED" ->
            radioColors.sage
        telemetry.deliveryState.name == "RELAYED" -> relayBlue
        telemetry.deliveryState.name == "DTN_STORED" -> dtnAmber
        telemetry.deliveryState.name == "FAILED" -> radioColors.alert
        telemetry.deliveryState.name == "WAITING_FOR_ROUTE" -> radioColors.warning
        telemetry.deliveryState.name == "ACK_PENDING" -> radioColors.warning
        else -> radioColors.textSecondary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF081008))
            .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "RADIO STATE",
            color = stateColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Primary state row
        TelemetryRow(
            label = "DELIVERY STATE",
            value = "${telemetry.deliveryState.icon}  ${telemetry.deliveryState.label.uppercase()}",
            valueColor = stateColor
        )

        // Priority context (always shown — never fabricated)
        TelemetryRow(
            label = "PRIORITY",
            value = telemetry.priorityContext.label,
            valueColor = if (telemetry.priorityContext.isEmergency) radioColors.alert else radioColors.textPrimary
        )

        // Transport (only if available)
        if (!telemetry.transport.isNullOrBlank()) {
            TelemetryRow(
                label = "TRANSPORT",
                value = telemetry.transport,
                valueColor = radioColors.textPrimary
            )
        }

        // Hop count (only if available)
        if (telemetry.hopCount != null) {
            TelemetryRow(
                label = "HOP COUNT",
                value = if (telemetry.hopCount <= 1) "Direct (1 hop)" else "${telemetry.hopCount} hops (Relayed)",
                valueColor = if (telemetry.hopCount > 1) relayBlue else radioColors.sage
            )
        }

        // ACK round-trip time (only when acknowledged with measured RTT)
        if (telemetry.ackRttMs != null && telemetry.ackRttMs > 0) {
            TelemetryRow(
                label = "ACK RTT",
                value = "${telemetry.ackRttMs} ms",
                valueColor = radioColors.sage
            )
        }

        // DTN pending flag
        if (telemetry.isDtnPending) {
            TelemetryRow(
                label = "DTN STATUS",
                value = "Awaiting relay forward",
                valueColor = dtnAmber
            )
        }

        // Fragmentation info
        if (telemetry.isFragmented && telemetry.fragmentCount != null) {
            TelemetryRow(
                label = "FRAGMENTED",
                value = "${telemetry.fragmentCount} fragments",
                valueColor = radioColors.textSecondary
            )
        }

        // Authentication status (HMAC-SHA256)
        TelemetryRow(
            label = "INTEGRITY AUTH",
            value = if (telemetry.isAuthenticated) "HMAC-SHA256 Valid ✓" else "UNVERIFIED",
            valueColor = if (telemetry.isAuthenticated) radioColors.sage else radioColors.warning
        )

        // Raw QoS status if present and not redundant
        if (!telemetry.qosStatus.isNullOrBlank()) {
            TelemetryRow(
                label = "QOS STATUS",
                value = telemetry.qosStatus,
                valueColor = radioColors.textSecondary
            )
        }
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    valueColor: Color
) {
    val radioColors = LocalRadioColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = radioColors.textTertiary,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
