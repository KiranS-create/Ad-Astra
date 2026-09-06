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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
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
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.presentation.theme.LocalRadioColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact, industrial radio transmission log row.
 * Displays directional badge (TX/RX), language, timestamp, payload,
 * packet byte count, and measured round-trip/processing latency.
 */
@Composable
fun RadioTranscriptRow(
    record: MessageRecord,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val isSent = record.direction == MessageDirection.SENT
    val isEmergency = record.priority == MessagePriority.DISTRESS || record.priority == MessagePriority.ALERT

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(record.timestamp))

    val borderColor = when {
        isEmergency -> radioColors.alert
        isSent -> if (radioColors.isDark) radioColors.sage.copy(alpha = 0.5f) else radioColors.forest.copy(alpha = 0.35f)
        else -> Color(0xFF0288D1).copy(alpha = 0.45f)
    }

    val cardBackground = if (isEmergency) {
        radioColors.alert.copy(alpha = 0.08f)
    } else {
        radioColors.surface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBackground)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: TX/RX badge + Peer + Language + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // TX / RX Capsule Badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSent) radioColors.sage.copy(alpha = 0.18f) else Color(0xFF0288D1).copy(alpha = 0.18f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSent) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = if (isSent) radioColors.sage else Color(0xFF0288D1),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isSent) "TX" else "RX",
                                color = if (isSent) radioColors.sage else Color(0xFF0288D1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = record.peer.ifEmpty { if (isSent) "Broadcast" else "Peer" },
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Language tag
                    Text(
                        text = "•  ${record.language.displayName}",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEmergency) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Distress",
                            tint = radioColors.alert,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = formattedTime,
                        color = radioColors.textTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Body: Transcribed / Synthesized Speech Content
            Text(
                text = record.text,
                color = radioColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Telemetry Footer: Packet size + Latency + Compression
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val savingsStr = if (record.rawAudioEquivalentBytes > 0) {
                    val ratio = 100.0 * (1.0 - (record.packetSizeBytes.toDouble() / record.rawAudioEquivalentBytes.toDouble()))
                    "  (${String.format(Locale.US, "%.1f%% saved", ratio.coerceIn(0.0, 99.9))})"
                } else {
                    ""
                }

                Text(
                    text = "${record.packetSizeBytes} B$savingsStr",
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (record.measuredLatencyMs > 0) {
                    Text(
                        text = "${record.measuredLatencyMs.toInt()} ms",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
