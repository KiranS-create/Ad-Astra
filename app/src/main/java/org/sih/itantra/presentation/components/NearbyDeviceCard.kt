package org.sih.itantra.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.discovery.DeviceTrustState
import org.sih.itantra.core.discovery.MeshReachabilityState
import org.sih.itantra.core.discovery.NearbyDevice
import org.sih.itantra.presentation.theme.ColorSignalBlue
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical device card representing a discovered iTantra node.
 * Provides high-density overview with collapsible full telemetry and actionable controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearbyDeviceCard(
    device: NearbyDevice,
    onInspect: (NearbyDevice) -> Unit = {},
    onAddContact: (NearbyDevice) -> Unit = {},
    onOpenChat: (Int) -> Unit = {},
    onTestConnection: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    var isExpanded by remember { mutableStateOf(false) }

    val accessibilityDesc = "Node ${device.formattedNodeId}, Callsign ${device.callsign}, Proximity ${device.proximityState.label}, Reachability ${device.meshReachability.label}"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(radioColors.surface)
            .border(
                1.dp,
                if (isExpanded) radioColors.sage.copy(alpha = 0.8f) else radioColors.border.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .clickable { isExpanded = !isExpanded }
            .padding(14.dp)
            .semantics { contentDescription = accessibilityDesc }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: Node ID, Callsign, Proximity Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (device.nodeId > 0) "NODE ${device.formattedNodeId}" else "UNIDENTIFIED BEACON",
                            color = radioColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = device.callsign,
                        color = radioColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                ProximityBadge(proximityState = device.proximityState)
            }

            // Subtitle: Discovery Method & Signal Strength
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${device.discoverySource.badgeText} · ${device.signalStrength.displayFormatted}",
                    color = ColorSignalBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )

                // Reachability Badge
                val reachabilityColor = when (device.meshReachability) {
                    MeshReachabilityState.DIRECT_NEIGHBOR -> radioColors.sage
                    MeshReachabilityState.MULTI_HOP_RELAY -> ColorSignalBlue
                    MeshReachabilityState.RECENTLY_HEARD -> radioColors.warning
                    MeshReachabilityState.NOT_IN_MESH -> radioColors.textTertiary
                }

                Text(
                    text = device.meshReachability.badge,
                    color = reachabilityColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Capabilities & Languages Tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                device.transportCapabilities.forEach { transport ->
                    TacticalPill(text = transport, textColor = radioColors.textPrimary)
                }

                device.supportedLanguages.take(3).forEach { lang ->
                    TacticalPill(text = lang.nativeName, textColor = radioColors.textSecondary)
                }
            }

            // Collapsible Detailed Telemetry & Action Buttons
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Divider(color = radioColors.border.copy(alpha = 0.5f), thickness = 1.dp)

                    // Telemetry Grid
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TelemetryDetailRow(label = "NODE ID", value = if (device.nodeId > 0) "#${device.nodeId}" else "Pending handshake")
                        TelemetryDetailRow(label = "CALLSIGN", value = device.callsign)
                        TelemetryDetailRow(label = "PROXIMITY BAND", value = device.proximityState.label)
                        TelemetryDetailRow(label = "DISCOVERY METHOD", value = "${device.discoverySource.displayName} (${device.discoverySource.badgeText})")
                        TelemetryDetailRow(label = "TRANSPORTS", value = device.transportCapabilities.joinToString(", "))
                        TelemetryDetailRow(label = "LANGUAGES", value = device.supportedLanguages.joinToString(", ") { it.displayName })
                        TelemetryDetailRow(label = "LAST HEARD", value = device.relativeTimeText)
                        TelemetryDetailRow(label = "MESH STATUS", value = "${device.meshReachability.label} (${device.hopCount ?: 1} hop)")
                        device.batteryPct?.let {
                            TelemetryDetailRow(label = "BATTERY", value = "$it%")
                        }
                        TelemetryDetailRow(
                            label = "TRUST STATE",
                            value = device.trustState.label,
                            valueColor = if (device.trustState.isVerified) radioColors.sage else radioColors.warning
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Tactical Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Add Contact Button
                        Button(
                            onClick = { onAddContact(device) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "ADD CONTACT", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        // Open Chat Button
                        OutlinedButton(
                            onClick = { onOpenChat(device.nodeId) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = radioColors.textPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(radioColors.border)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "OPEN CHAT", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Test Radio Connection Action
                    OutlinedButton(
                        onClick = { onTestConnection(device.nodeId) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ColorSignalBlue
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(ColorSignalBlue.copy(alpha = 0.5f))
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "TEST CONNECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Expand / Collapse Chevron indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                    tint = radioColors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TacticalPill(
    text: String,
    textColor: Color
) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(radioColors.capsule)
            .border(1.dp, radioColors.border.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
private fun TelemetryDetailRow(
    label: String,
    value: String,
    valueColor: Color? = null
) {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = radioColors.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = value,
            color = valueColor ?: radioColors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
