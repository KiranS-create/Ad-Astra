package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.presentation.theme.DistressRed
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.SignalBlue
import org.sih.itantra.presentation.theme.TacticalBorder
import org.sih.itantra.presentation.theme.TacticalSurface
import org.sih.itantra.presentation.theme.TextPrimary
import org.sih.itantra.presentation.theme.TextSecondary

@Composable
fun TranscriptBubble(
    record: MessageRecord,
    modifier: Modifier = Modifier
) {
    val isSent = record.direction == MessageDirection.SENT
    val borderColor = when {
        record.priority == MessagePriority.DISTRESS -> DistressRed
        record.priority == MessagePriority.ALERT -> Color(0xFFFFB300)
        isSent -> RadarGreen.copy(alpha = 0.6f)
        else -> SignalBlue.copy(alpha = 0.6f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TacticalSurface, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isSent) "TX → ${record.peer}" else "RX ← ${record.peer}",
                color = if (isSent) RadarGreen else SignalBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${record.language.displayName.uppercase()} [${record.priority.label}]",
                color = if (record.priority.isEmergency) DistressRed else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Text(
            text = record.text,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Packet: ${record.packetSizeBytes}B (Audio saved: ${(record.rawAudioEquivalentBytes / 1024)}KB)",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${record.measuredLatencyMs.toInt()}ms",
                color = RadarGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
