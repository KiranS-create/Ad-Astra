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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.health.CommunicationEventItem
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical Timeline displaying recent communication events (transmissions, ACKs, relays, DTN stores).
 */
@Composable
fun RecentEventsTimeline(
    events: List<CommunicationEventItem>,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "RECENT COMMUNICATION EVENTS",
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
            if (events.isEmpty()) {
                Text(
                    text = "No recent communication events recorded",
                    color = radioColors.textTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { event ->
                        CommunicationEventRow(event = event)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunicationEventRow(
    event: CommunicationEventItem
) {
    val radioColors = LocalRadioColors.current

    val stateColor = when (event.deliveryState) {
        RadioDeliveryState.ACKNOWLEDGED -> radioColors.success
        RadioDeliveryState.ACK_PENDING   -> radioColors.warning
        RadioDeliveryState.RELAYED       -> if (radioColors.isDark) radioColors.sage else radioColors.forest
        RadioDeliveryState.DTN_STORED    -> radioColors.textPrimary
        RadioDeliveryState.WAITING_FOR_ROUTE -> radioColors.warning
        RadioDeliveryState.FAILED        -> radioColors.alert
        else                             -> radioColors.textSecondary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = event.timeFormatted,
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = event.deliveryState.icon,
                color = stateColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = event.description,
                color = if (event.isEmergency) radioColors.alert else radioColors.textPrimary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(stateColor.copy(alpha = 0.15f))
                .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.5.dp)
        ) {
            Text(
                text = event.deliveryState.label.uppercase(),
                color = stateColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
