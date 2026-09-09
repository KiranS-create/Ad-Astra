package org.sih.itantra.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioMessageTelemetry
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Compact single-row radio state indicator shown beneath each message bubble.
 *
 * Appearance:
 *   [icon]  [state label]  [· detail]         [timestamp]
 *
 * - The icon and label communicate the primary delivery state.
 * - The optional detail shows RTT, hop count, or DTN flag as appropriate.
 * - The timestamp is shown at the end for all messages.
 *
 * This composable is intentionally lightweight — no animations, no complex
 * layouts. It is called per-item in a LazyColumn.
 */
@Composable
fun MessageRadioStateIndicator(
    telemetry: RadioMessageTelemetry,
    formattedTime: String,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val state = telemetry.deliveryState

    val (stateColor, detail) = resolveColorAndDetail(state, telemetry, radioColors)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Icon
        Text(
            text = state.icon,
            color = stateColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        // State label
        Text(
            text = state.label,
            color = stateColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        // Optional detail suffix
        if (detail != null) {
            Text(
                text = "· $detail",
                color = stateColor.copy(alpha = 0.75f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Timestamp (always shown)
        Text(
            text = formattedTime,
            color = radioColors.textSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Resolves the display color and optional detail suffix for a [RadioDeliveryState].
 *
 * Colors:
 * - ACKNOWLEDGED / RECEIVED  → sage green (confirmed delivery)
 * - RELAYED                  → tactical blue (multi-hop)
 * - DTN_STORED               → amber (deferred delivery)
 * - WAITING_FOR_ROUTE        → warning amber
 * - FAILED                   → alert red
 * - QUEUED / SENDING         → secondary text (in-progress, neutral)
 * - ACK_PENDING              → warning amber (waiting)
 * - UNKNOWN                  → tertiary (dim)
 */
@Composable
private fun resolveColorAndDetail(
    state: RadioDeliveryState,
    telemetry: RadioMessageTelemetry,
    radioColors: org.sih.itantra.presentation.theme.RadioColors
): Pair<Color, String?> {
    val relayBlue = Color(0xFF0288D1)
    val dtnAmber = Color(0xFFFF9800)

    return when (state) {
        RadioDeliveryState.ACKNOWLEDGED -> {
            val detail = telemetry.ackRttMs?.let { "${it}ms RTT" }
            radioColors.sage to detail
        }
        RadioDeliveryState.RECEIVED -> {
            radioColors.sage to null
        }
        RadioDeliveryState.RELAYED -> {
            val hops = telemetry.hopCount
            val detail = if (hops != null && hops > 0) "$hops hops" else null
            relayBlue to detail
        }
        RadioDeliveryState.DTN_STORED -> {
            dtnAmber to null
        }
        RadioDeliveryState.WAITING_FOR_ROUTE -> {
            radioColors.warning to null
        }
        RadioDeliveryState.FAILED -> {
            radioColors.alert to null
        }
        RadioDeliveryState.ACK_PENDING -> {
            radioColors.warning to null
        }
        RadioDeliveryState.SENDING -> {
            radioColors.textSecondary to null
        }
        RadioDeliveryState.QUEUED -> {
            radioColors.textSecondary to null
        }
        RadioDeliveryState.UNKNOWN -> {
            radioColors.textTertiary to null
        }
    }
}
