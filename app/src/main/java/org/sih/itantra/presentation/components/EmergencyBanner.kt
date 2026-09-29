package org.sih.itantra.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.emergency.EmergencyBannerType
import org.sih.itantra.core.emergency.EmergencyChannelContext
import org.sih.itantra.presentation.theme.LocalRadioColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dynamic emergency banner indicating whether the channel currently contains
 * an active distress broadcast or historical distress communications.
 */
@Composable
fun EmergencyBanner(
    context: EmergencyChannelContext,
    onLocatePeer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    AnimatedVisibility(
        visible = context.bannerType != EmergencyBannerType.NONE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        when (context.bannerType) {
            EmergencyBannerType.ACTIVE -> {
                val timeStr = context.latestEmergencyRecord?.let {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestamp))
                } ?: ""

                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(radioColors.alert.copy(alpha = 0.12f))
                        .border(1.dp, radioColors.alert)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Active Distress Signal",
                                tint = radioColors.alert,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (timeStr.isNotBlank()) "⚠ EMERGENCY ACTIVE ($timeStr)" else "⚠ EMERGENCY ACTIVE",
                                color = radioColors.alert,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = context.latestEmergencyRecord?.semanticSummary ?: "PRIORITY 1",
                            color = radioColors.alert,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Integrated 4-State Truthful Emergency Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. EMERGENCY DELIVERY STATE
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text("EMERGENCY", color = radioColors.textTertiary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            val delivery = context.latestEmergencyRecord?.deliveryStatus?.name ?: "ACTIVE"
                            Text(delivery, color = radioColors.alert, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        // 2. LOCATION STATE (FIXED / LAST KNOWN / UNAVAILABLE)
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text("LOCATION", color = radioColors.textTertiary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                context.locationState,
                                color = if (context.locationState == "UNAVAILABLE") radioColors.warning else radioColors.sage,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // 3. PROXIMITY STATE
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (onLocatePeer != null) {
                                        Modifier.clickable { onLocatePeer() }
                                    } else Modifier
                                )
                        ) {
                            Text("PROXIMITY", color = radioColors.textTertiary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            val proxText = if (onLocatePeer != null && context.proximityState != "UNKNOWN") {
                                "${context.proximityState} 🎯"
                            } else {
                                context.proximityState
                            }
                            Text(
                                proxText,
                                color = if (context.proximityState == "UNKNOWN") radioColors.textTertiary else radioColors.sage,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // 4. NETWORK STATE
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text("NETWORK", color = radioColors.textTertiary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text(context.networkState, color = radioColors.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            EmergencyBannerType.HISTORICAL -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(radioColors.warning.copy(alpha = 0.08f))
                        .border(1.dp, radioColors.warning.copy(alpha = 0.4f))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Historical Distress Records",
                            tint = radioColors.warning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PRIOR DISTRESS (${context.totalEmergencyCount}) · RESOLVED / HISTORICAL",
                            color = radioColors.warning,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "INACTIVE",
                        color = radioColors.textTertiary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            EmergencyBannerType.NONE -> {
                // Render nothing
            }
        }
    }
}
