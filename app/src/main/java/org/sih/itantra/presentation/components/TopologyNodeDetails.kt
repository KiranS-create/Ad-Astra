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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import org.sih.itantra.core.topology.TopologyDisplayNode
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical HUD card displaying genuine telemetry for a selected node.
 * Strictly presents UNKNOWN for missing metrics without fabrication.
 */
@Composable
fun TopologyNodeDetails(
    node: TopologyDisplayNode,
    onOpenChat: (Int) -> Unit,
    onOpenContact: (Int) -> Unit,
    onInspectNode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val accentColor = MeshTopologyNodeStyle.nodeColor(node.state, node.isLocal, radioColors)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (radioColors.isDark) Color(0xFF08120C) else radioColors.surface)
            .border(1.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (node.isLocal) "LOCAL NODE: #${node.nodeId}" else "NODE TELEMETRY: #${node.nodeId}",
                        color = radioColors.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = MeshTopologyNodeStyle.stateBadgeText(node.state, node.isLocal),
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = accentColor.copy(alpha = 0.3f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // 2. Telemetry Grid (2 Columns)
            Row(modifier = Modifier.fillMaxWidth()) {
                // Column 1
                Column(modifier = Modifier.weight(1f)) {
                    TelemetryField(label = "CALLSIGN", value = node.effectiveCallsign)
                    Spacer(modifier = Modifier.height(6.dp))
                    TelemetryField(label = "STATUS", value = node.status)
                    Spacer(modifier = Modifier.height(6.dp))
                    TelemetryField(label = "ROUTE", value = node.routeSummary)
                    Spacer(modifier = Modifier.height(6.dp))
                    TelemetryField(label = "BATTERY", value = node.batteryLevel)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Column 2
                Column(modifier = Modifier.weight(1f)) {
                    TelemetryField(label = "TRANSPORT", value = node.effectiveTransport)
                    Spacer(modifier = Modifier.height(6.dp))
                    TelemetryField(label = "HOPS", value = node.effectiveHops)
                    Spacer(modifier = Modifier.height(6.dp))
                    TelemetryField(label = "RSSI", value = node.effectiveRssi)
                    Spacer(modifier = Modifier.height(6.dp))
                    TelemetryField(label = "LAST HEARD", value = node.lastSeenFormatted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!node.isLocal) {
                    OutlinedButton(
                        onClick = { onOpenChat(node.nodeId) },
                        modifier = Modifier.weight(1f).height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = radioColors.textPrimary)
                    ) {
                        Text(
                            text = "[ CHAT ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { onOpenContact(node.nodeId) },
                        modifier = Modifier.weight(1f).height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = radioColors.textPrimary)
                    ) {
                        Text(
                            text = "[ CONTACT ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { onInspectNode(node.nodeId) },
                        modifier = Modifier.weight(1f).height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.25f), contentColor = accentColor)
                    ) {
                        Text(
                            text = "[ INSPECT ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = { onInspectNode(node.nodeId) },
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.25f), contentColor = accentColor)
                    ) {
                        Text(
                            text = "[ LOCAL DIAGNOSTICS & TELEMETRY ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryField(label: String, value: String) {
    val radioColors = LocalRadioColors.current
    val isUnknown = value.equals("UNKNOWN", ignoreCase = true) || value.equals("N/A", ignoreCase = true)

    Column {
        Text(
            text = label,
            color = radioColors.textTertiary,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = if (isUnknown) radioColors.textTertiary else radioColors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
