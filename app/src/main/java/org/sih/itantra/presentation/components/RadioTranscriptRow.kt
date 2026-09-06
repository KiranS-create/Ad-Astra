package org.sih.itantra.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
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

            // Emergency Distress Banner
            if (record.priority == MessagePriority.DISTRESS) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(radioColors.alert.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = radioColors.alert,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "⚠ DISTRESS ${if (record.location != null) "· LOCATION ATTACHED" else "· LOCATION UNAVAILABLE"}",
                            color = radioColors.alert,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Semantic Emergency Compression Badge
            if (record.isSemantic && record.semanticSummary != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background((if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.15f))
                        .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = record.semanticSummary,
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        record.semanticSavingsBytes?.let { savings ->
                            Text(
                                text = "SAVED: ${savings}B",
                                color = radioColors.success,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Body: Transcribed / Synthesized Speech Content
            Text(
                text = record.text,
                color = radioColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp
            )

            // Location Metadata Box with Open Map Action
            if (record.location != null) {
                val loc = record.location
                val context = LocalContext.current
                val latStr = String.format(Locale.US, "%.6f° %s", Math.abs(loc.latitude), if (loc.latitude >= 0) "N" else "S")
                val lonStr = String.format(Locale.US, "%.6f° %s", Math.abs(loc.longitude), if (loc.longitude >= 0) "E" else "W")

                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(radioColors.surfaceHighlight)
                        .border(1.dp, radioColors.alert.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LOCATION: $latStr, $lonStr",
                                color = radioColors.alert,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(radioColors.alert.copy(alpha = 0.2f))
                                    .border(1.dp, radioColors.alert.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        try {
                                            val uri = "geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(Distress+Node)"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            android.widget.Toast.makeText(context, "No map application installed", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "OPEN MAP",
                                    color = radioColors.alert,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ACCURACY: ±${loc.accuracy.toInt()}m${if (loc.altitude != null) " · ALT: ${loc.altitude.toInt()}m" else ""}",
                                color = radioColors.textTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (record.hopCount > 0) {
                                Text(
                                    text = "HOPS: ${record.hopCount}",
                                    color = radioColors.textTertiary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Reliable Delivery & Fragmentation Status Indicators
            if ((record.fragmentCount != null && record.fragmentCount > 1) || record.deliveryStatus != DeliveryStatus.NONE) {
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Fragment badge
                    if (record.fragmentCount != null && record.fragmentCount > 1) {
                        val fragLabel = if (isSent) {
                            "${record.fragmentCount} FRAGMENTS"
                        } else {
                            "REASSEMBLED (${record.fragmentCount} FRAGS)"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(radioColors.textTertiary.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = fragLabel,
                                color = radioColors.textSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Delivery Status badge
                    when (record.deliveryStatus) {
                        DeliveryStatus.DELIVERED -> {
                            val ackStr = if (record.deliveryLatencyMs != null) "DELIVERED ✓ (${record.deliveryLatencyMs}ms)" else "DELIVERED ✓"
                            Text(
                                text = ackStr,
                                color = radioColors.success,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        DeliveryStatus.PENDING -> {
                            Text(
                                text = "DELIVERY PENDING",
                                color = radioColors.warning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        DeliveryStatus.TIMEOUT -> {
                            Text(
                                text = "DELIVERY TIMEOUT",
                                color = radioColors.alert,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        DeliveryStatus.SENDING -> {
                            val count = record.fragmentCount ?: 1
                            Text(
                                text = "SENDING ($count fragments)",
                                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        DeliveryStatus.NONE -> {}
                    }
                }
            }

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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${record.packetSizeBytes} B$savingsStr",
                        color = radioColors.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (record.isSecure) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background((if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.15f))
                                .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = record.authStatus ?: "AUTH ✓",
                                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (record.authStatus == "UNVERIFIED") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(radioColors.warning.copy(alpha = 0.15f))
                                .border(1.dp, radioColors.warning.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "UNVERIFIED",
                                color = radioColors.warning,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

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
