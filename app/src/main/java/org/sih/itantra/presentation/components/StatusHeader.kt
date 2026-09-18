package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.SignalBlue
import org.sih.itantra.presentation.theme.TacticalBorder
import org.sih.itantra.presentation.theme.TacticalSurface
import org.sih.itantra.presentation.theme.TextPrimary
import org.sih.itantra.presentation.theme.TextSecondary

@Composable
fun StatusHeader(
    activeTransport: TransportType,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(TacticalSurface, RoundedCornerShape(8.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // App Identity & System Code
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "iTANTRA",
                color = RadarGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = " // SIH26173",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 100% OFFLINE Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0x2200FF66), RoundedCornerShape(4.dp))
                .border(1.dp, RadarGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(RadarGreen, CircleShape)
            )
            Text(
                text = " OFFLINE AI",
                color = RadarGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Active Transport indicator
        Text(
            text = when (activeTransport) {
                TransportType.WIFI -> "WI-FI"
                TransportType.BLUETOOTH -> "BT SPP"
                TransportType.LOOPBACK -> "LOOPBACK"
                TransportType.EMBEDDED_RADIO -> "RADIO"
                TransportType.WIFI_DIRECT -> "WI-FI P2P"
            },
            color = SignalBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
