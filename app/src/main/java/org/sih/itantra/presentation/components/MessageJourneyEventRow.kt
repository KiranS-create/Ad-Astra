package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.message.journey.JourneyEvent
import org.sih.itantra.core.message.journey.JourneyEventStatus
import org.sih.itantra.presentation.theme.LocalRadioColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tactical vertical timeline event row.
 *
 * Visualizes a single substantiated lifecycle event with vertical interconnecting line,
 * color-coded tactical node indicator, timestamp, node identity, and technical details.
 */
@Composable
fun MessageJourneyEventRow(
    event: JourneyEvent,
    isFirst: Boolean,
    isLast: Boolean,
    isEmergency: Boolean,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val (statusColor, dotSymbol) = when (event.status) {
        JourneyEventStatus.SUCCESS -> (if (radioColors.isDark) radioColors.sage else radioColors.forest) to "●"
        JourneyEventStatus.PENDING -> radioColors.warning to "◌"
        JourneyEventStatus.WARNING -> radioColors.warning to "▲"
        JourneyEventStatus.FAILED  -> radioColors.alert to "✕"
        JourneyEventStatus.INFO    -> radioColors.textSecondary to "●"
    }

    val effectiveColor = if (isEmergency && (event.status == JourneyEventStatus.FAILED || event.status == JourneyEventStatus.WARNING)) {
        radioColors.alert
    } else statusColor

    val formattedTime = if (event.timestampMs != null && event.timestampMs > 0) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        sdf.format(Date(event.timestampMs))
    } else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Left Column: Tactical Timeline Vertical Line + Node Dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            // Top vertical line segment
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(10.dp)
                    .background(
                        if (isFirst) Color.Transparent else radioColors.border.copy(alpha = 0.5f)
                    )
            )

            // Node Dot Indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(effectiveColor.copy(alpha = 0.15f))
                    .border(1.dp, effectiveColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dotSymbol,
                    color = effectiveColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom vertical line segment
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .weight(1f)
                    .background(
                        if (isLast) Color.Transparent else radioColors.border.copy(alpha = 0.5f)
                    )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Column: Event Content Card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 4.dp else 16.dp)
        ) {
            // Header Row: Event Type + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.type.label,
                    color = effectiveColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )

                if (formattedTime != null) {
                    Text(
                        text = formattedTime,
                        color = radioColors.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Metadata Badges Row: Node · Transport · Hop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Node Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(radioColors.capsule)
                        .border(0.5.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Node: ${event.node}",
                        color = if (event.node == "UNKNOWN") radioColors.textTertiary else radioColors.textSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (event.node == "UNKNOWN") FontWeight.Normal else FontWeight.Medium
                    )
                }

                // Transport Badge
                if (event.transport != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(radioColors.capsule)
                            .border(0.5.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = event.transport,
                            color = radioColors.textSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Hop Badge
                if (event.hop != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(radioColors.capsule)
                            .border(0.5.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (event.hop > 1) "${event.hop} Hops" else "1 Hop",
                            color = radioColors.sage,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Detail Text
            if (!event.detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.detail,
                    color = radioColors.textPrimary.copy(alpha = 0.85f),
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
