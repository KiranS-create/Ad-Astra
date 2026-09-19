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
import org.sih.itantra.presentation.theme.TacticalShapeTokens
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
 * Tactical radio transmission message card.
 *
 * Implements high-visibility message hierarchy:
 * - RX messages left-aligned (Signal Blue tone)
 * - TX messages right-aligned (Sage/Forest Green tone)
 * - Emergency messages prominent (Alert Red tone)
 * - Message text is the dominant, highest-contrast element
 * - Compact secondary metadata footer for AUTH, delivery receipts, QoS, and latency
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
        isSent -> if (radioColors.isDark) radioColors.sage.copy(alpha = 0.55f) else radioColors.forest.copy(alpha = 0.40f)
        else -> Color(0xFF0288D1).copy(alpha = 0.50f)
    }

    val cardBackground = when {
        isEmergency -> radioColors.alert.copy(alpha = 0.10f)
        isSent -> if (radioColors.isDark) radioColors.sage.copy(alpha = 0.08f) else radioColors.forest.copy(alpha = 0.06f)
        else -> if (radioColors.isDark) Color(0xFF0288D1).copy(alpha = 0.08f) else Color(0xFFE1F5FE)
    }

    // Outer alignment container: RX left-aligned, TX right-aligned
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(TacticalShapeTokens.Card)
                .background(cardBackground)
                .border(1.dp, borderColor, TacticalShapeTokens.Card)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Header: Direction Badge + Peer + Language + Emergency Badge + Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Direction Capsule: [TX ➔] or [RX 🠔]
                        Box(
                            modifier = Modifier
                                .clip(TacticalShapeTokens.Tag)
                                .background(
                                    if (isSent) radioColors.sage.copy(alpha = 0.22f) else Color(0xFF0288D1).copy(alpha = 0.22f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSent) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isSent) "Transmitted" else "Received",
                                    tint = if (isSent) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else Color(0xFF0288D1),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isSent) "TX" else "RX",
                                    color = if (isSent) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else Color(0xFF0288D1),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        Text(
                            text = record.peer.ifEmpty { if (isSent) "Broadcast" else "Peer" },
                            color = radioColors.textSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Language tag
                        Text(
                            text = "• ${record.language.displayName}",
                            color = radioColors.textTertiary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isEmergency) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Distress Alert",
                                tint = radioColors.alert,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (record.priority == MessagePriority.DISTRESS) "DISTRESS" else "ALERT",
                                color = radioColors.alert,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
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

                // 2. Emergency Distress Banner (if DISTRESS priority)
                if (record.priority == MessagePriority.DISTRESS) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TacticalShapeTokens.Tag)
                            .background(radioColors.alert.copy(alpha = 0.15f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = radioColors.alert,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🚨 DISTRESS • P3 ${if (record.location != null) "(LOCATION ATTACHED)" else "(NO GPS)"}",
                                color = radioColors.alert,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }

                // 3. Semantic Emergency Compression Line
                if (record.isSemantic && record.semanticSummary != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TacticalShapeTokens.Tag)
                            .background((if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.15f))
                            .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.4f), TacticalShapeTokens.Tag)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEMANTIC: ${record.semanticSummary}",
                                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            record.semanticSavingsBytes?.let { savings ->
                                Text(
                                    text = "SAVED: ${savings}B",
                                    color = radioColors.success,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }

                // 4. Primary Message Content — Highest Visual Prominence
                Text(
                    text = record.text,
                    color = radioColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                )

                // 5. Location Metadata Box with Open Map Action (if attached)
                if (record.location != null) {
                    val loc = record.location
                    val context = LocalContext.current
                    val latStr = String.format(Locale.US, "%.6f° %s", Math.abs(loc.latitude), if (loc.latitude >= 0) "N" else "S")
                    val lonStr = String.format(Locale.US, "%.6f° %s", Math.abs(loc.longitude), if (loc.longitude >= 0) "E" else "W")

                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(radioColors.surfaceHighlight)
                            .border(1.dp, radioColors.alert.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
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
                                    fontSize = 11.5.sp,
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

                Spacer(modifier = Modifier.height(6.dp))

                // 6. Secondary Metadata Footer: Security, Delivery Receipts, Priority, Packet Size, Latency
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left metadata: Auth + Delivery status + Fragments
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Auth status badge
                        if (record.isSecure) {
                            Box(
                                modifier = Modifier
                                    .clip(TacticalShapeTokens.Tag)
                                    .background((if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.15f))
                                    .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.4f), TacticalShapeTokens.Tag)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = record.authStatus ?: "AUTH ✓",
                                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else if (record.authStatus == "UNVERIFIED") {
                            Box(
                                modifier = Modifier
                                    .clip(TacticalShapeTokens.Tag)
                                    .background(radioColors.warning.copy(alpha = 0.15f))
                                    .border(1.dp, radioColors.warning.copy(alpha = 0.4f), TacticalShapeTokens.Tag)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "UNVERIFIED",
                                    color = radioColors.warning,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Delivery Receipt status
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
                                    text = "PENDING",
                                    color = radioColors.warning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            DeliveryStatus.TIMEOUT -> {
                                Text(
                                    text = "TIMEOUT",
                                    color = radioColors.alert,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            DeliveryStatus.SENDING -> {
                                val count = record.fragmentCount ?: 1
                                Text(
                                    text = "SENDING ($count frags)",
                                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            DeliveryStatus.NONE -> {}
                        }

                        // Fragments indicator
                        if (record.fragmentCount != null && record.fragmentCount > 1) {
                            val fragLabel = if (isSent) "${record.fragmentCount} FRAGS" else "REASSEMBLED (${record.fragmentCount})"
                            Text(
                                text = fragLabel,
                                color = radioColors.textTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Right metadata: QoS Priority + Packet size + Latency
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Feature 16B, 18 & 19: VBR Representation Mode Badge
                        record.representationMode?.let { mode ->
                            val isContextDelta = mode == "CONTEXT_DELTA" || record.isContextDelta
                            val isStandalone = mode == "STANDALONE" || record.contextFallback
                            val isEnhanced = mode == "SEMANTIC_ENHANCED" || mode == "BASE_PLUS_ENHANCEMENT"
                            val isBaseOnly = mode == "SEMANTIC_BASE" || mode == "BASE_ONLY"
                            val isSemantic = mode == "SEMANTIC"
                            val isCompact = mode == "COMPACT"

                            val modeColor = when {
                                isContextDelta -> if (radioColors.isDark) radioColors.sage else radioColors.forest
                                isStandalone -> radioColors.warning
                                isEnhanced || isBaseOnly || isSemantic -> if (radioColors.isDark) radioColors.sage else radioColors.forest
                                isCompact -> radioColors.warning
                                else -> radioColors.textTertiary
                            }
                            val modeBg = when {
                                isContextDelta -> (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.2f)
                                isStandalone -> radioColors.warning.copy(alpha = 0.2f)
                                isEnhanced || isBaseOnly || isSemantic -> (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.15f)
                                isCompact -> radioColors.warning.copy(alpha = 0.15f)
                                else -> radioColors.surfaceHighlight
                            }
                            val label = when {
                                isContextDelta -> "CTX DELTA"
                                isStandalone -> "STANDALONE"
                                isEnhanced -> "BASE+ENH"
                                isBaseOnly -> "BASE ONLY"
                                isSemantic -> "SEMANTIC"
                                isCompact -> "COMPACT"
                                else -> mode
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(modeBg)
                                    .border(1.dp, modeColor.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = modeColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Tactical QoS Priority Badge
                        val (pColor, pText) = when (record.priority) {
                            MessagePriority.DISTRESS -> Pair(radioColors.alert, "P3")
                            MessagePriority.ALERT -> Pair(radioColors.warning, "P2")
                            MessagePriority.IMPORTANT -> Pair(Color(0xFF00E5FF), "P1")
                            MessagePriority.NORMAL -> Pair(radioColors.textTertiary, "P0")
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(pColor.copy(alpha = 0.12f))
                                .border(1.dp, pColor.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = pText,
                                color = pColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Packet Size
                        Text(
                            text = "${record.packetSizeBytes}B",
                            color = radioColors.textTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        // Measured Latency
                        if (record.measuredLatencyMs > 0) {
                            Text(
                                text = "${record.measuredLatencyMs.toInt()}ms",
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
    }
}
