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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.health.TransportHealthItem
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical Card displaying live status of available wireless transports (Wi-Fi, Bluetooth).
 * Enforces truthful reporting (e.g. Signal is explicitly "NOT MEASURED").
 */
@Composable
fun ActiveTransportsCard(
    transports: List<TransportHealthItem>,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "ACTIVE TRANSPORTS",
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (transports.size <= 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                transports.forEach { item ->
                    TransportSubCard(
                        item = item,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                transports.chunked(2).forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunk.forEach { item ->
                            TransportSubCard(
                                item = item,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (chunk.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportSubCard(
    item: TransportHealthItem,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val dotColor = when (item.state) {
        TransportState.CONNECTED  -> radioColors.success
        TransportState.LISTENING  -> if (radioColors.isDark) radioColors.sage else radioColors.forest
        TransportState.CONNECTING -> radioColors.warning
        TransportState.DISCONNECTED,
        TransportState.ERROR      -> radioColors.alert
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(radioColors.surface)
            .border(
                1.dp,
                if (item.state == TransportState.DISCONNECTED) radioColors.alert.copy(alpha = 0.3f)
                else radioColors.border.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = radioColors.textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "State: ${item.displayState}",
                color = dotColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.details,
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Signal: ${item.signalDbm}",
                color = radioColors.textTertiary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
