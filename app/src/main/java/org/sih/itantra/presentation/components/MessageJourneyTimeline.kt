package org.sih.itantra.presentation.components

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
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.journey.MessageJourney
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Complete Message Journey Timeline view.
 *
 * Renders the full substantiated lifecycle journey of an individual message,
 * including header metadata, partial-history disclosure banner, vertical dot-and-line
 * tactical timeline, current radio state, and summary metrics.
 */
@Composable
fun MessageJourneyTimeline(
    journey: MessageJourney,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val borderColor = if (journey.isEmergency) {
        radioColors.alert.copy(alpha = 0.8f)
    } else {
        radioColors.sage.copy(alpha = 0.4f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF08120C))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. MESSAGE HEADER CARD
        MessageHeaderCard(journey = journey)

        // 2. PARTIAL HISTORY DISCLOSURE BANNER
        if (journey.isPartial) {
            PartialHistoryBanner(reason = journey.partialReason)
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = borderColor.copy(alpha = 0.3f)
        )

        // 3. TIMELINE SECTION
        Text(
            text = "LIFECYCLE TIMELINE",
            color = if (journey.isEmergency) radioColors.alert else radioColors.sage,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            journey.events.forEachIndexed { index, event ->
                MessageJourneyEventRow(
                    event = event,
                    isFirst = index == 0,
                    isLast = index == journey.events.lastIndex,
                    isEmergency = journey.isEmergency
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = borderColor.copy(alpha = 0.3f)
        )

        // 4. CURRENT DELIVERY STATE CARD
        CurrentStateCard(journey = journey)

        // 5. SUMMARY METRICS CARD
        SummaryMetricsCard(journey = journey)
    }
}

@Composable
private fun MessageHeaderCard(journey: MessageJourney) {
    val radioColors = LocalRadioColors.current
    val isOutgoing = journey.direction == MessageDirection.SENT

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top row: ID + Direction & Priority Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ID: ${journey.messageId.take(18)}",
                color = radioColors.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isOutgoing) radioColors.sage.copy(alpha = 0.2f) else radioColors.forest.copy(alpha = 0.3f))
                        .border(0.5.dp, if (isOutgoing) radioColors.sage else radioColors.forest, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (isOutgoing) "OUTGOING (TX)" else "INCOMING (RX)",
                        color = if (isOutgoing) radioColors.sage else radioColors.textPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Priority Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (journey.isEmergency) radioColors.alert.copy(alpha = 0.2f) else radioColors.capsule)
                        .border(0.5.dp, if (journey.isEmergency) radioColors.alert else radioColors.border, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = journey.priorityContext.label,
                        color = if (journey.isEmergency) radioColors.alert else radioColors.textSecondary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Source -> Destination
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = journey.source,
                color = radioColors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "→",
                color = radioColors.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = journey.destination,
                color = radioColors.sage,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Message text excerpt
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(radioColors.capsule.copy(alpha = 0.6f))
                .padding(8.dp)
        ) {
            Text(
                text = "\"${journey.text}\"",
                color = radioColors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun PartialHistoryBanner(reason: String?) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(radioColors.warning.copy(alpha = 0.1f))
            .border(1.dp, radioColors.warning.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "▲",
                    color = radioColors.warning,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "JOURNEY HISTORY PARTIAL",
                    color = radioColors.warning,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = reason ?: "Certain intermediate relay hops were forwarded by peer nodes without local telemetry logging.",
                color = radioColors.textSecondary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun CurrentStateCard(journey: MessageJourney) {
    val radioColors = LocalRadioColors.current
    val state = journey.currentRadioState

    val (stateColor, stateDesc) = when (state) {
        RadioDeliveryState.ACKNOWLEDGED -> (if (radioColors.isDark) radioColors.sage else radioColors.forest) to "DELIVERY CONFIRMED"
        RadioDeliveryState.RECEIVED     -> (if (radioColors.isDark) radioColors.sage else radioColors.forest) to "RECEIVED LOCALLY"
        RadioDeliveryState.RELAYED      -> radioColors.sage to "RELAYED VIA MESH"
        RadioDeliveryState.SENDING      -> radioColors.warning to "TRANSMITTING"
        RadioDeliveryState.ACK_PENDING  -> radioColors.warning to "AWAITING RECEIPT"
        RadioDeliveryState.DTN_STORED   -> radioColors.warning to "HELD IN DTN STORE"
        RadioDeliveryState.WAITING_FOR_ROUTE -> radioColors.warning to "WAITING FOR ROUTE"
        RadioDeliveryState.QUEUED       -> radioColors.warning to "QUEUED LOCALLY"
        RadioDeliveryState.FAILED       -> radioColors.alert to "DELIVERY TIMEOUT"
        RadioDeliveryState.UNKNOWN      -> radioColors.textTertiary to "METADATA UNKNOWN"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(stateColor.copy(alpha = 0.08f))
            .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CURRENT STATE",
                color = radioColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${state.icon} $stateDesc",
                color = stateColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SummaryMetricsCard(journey: MessageJourney) {
    val radioColors = LocalRadioColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "JOURNEY SUMMARY",
            color = radioColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        val hopsDisplay = when {
            journey.totalKnownHops != null && journey.totalKnownHops > 1 -> "${journey.totalKnownHops} Hops (Relayed)"
            journey.totalKnownHops == 1 -> "1 Hop (Direct)"
            else -> "UNKNOWN"
        }

        val transportDisplay = if (journey.knownTransports.isNotEmpty()) {
            journey.knownTransports.joinToString(", ")
        } else {
            "UNKNOWN"
        }

        SummaryRow(label = "TOTAL KNOWN HOPS", value = hopsDisplay)
        SummaryRow(label = "TRANSPORTS USED", value = transportDisplay)
        SummaryRow(label = "ACK STATUS", value = journey.ackState ?: "UNKNOWN")
        SummaryRow(label = "DTN STATUS", value = journey.dtnStatus ?: "UNKNOWN")
        SummaryRow(label = "FRAGMENTATION", value = journey.fragmentationSummary ?: "UNKNOWN")
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = radioColors.textTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = radioColors.textPrimary,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
