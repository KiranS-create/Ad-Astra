package org.sih.itantra.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.mesh.ManetSimulator
import org.sih.itantra.core.mesh.SimEvent
import org.sih.itantra.core.mesh.SimEventType
import org.sih.itantra.core.mesh.SimNode
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ManetDemoScreen(
    viewModel: TransceiverViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val state by viewModel.topologyState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = radioColors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MANET TOPOLOGY DEMO",
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(radioColors.warning.copy(alpha = 0.2f))
                                .border(1.dp, radioColors.warning, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SIMULATION",
                                color = radioColors.warning,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "Deterministic multi-hop routing using production engine",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = radioColors.surface),
                modifier = Modifier.height(32.dp)
            ) {
                Text("CLOSE", color = radioColors.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware vs Sim Summary Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(radioColors.success))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE HARDWARE: 2",
                            color = radioColors.textPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Phone A, Phone B",
                        color = radioColors.textSecondary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(radioColors.warning))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SIMULATED: 2",
                            color = radioColors.warning,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Node C (Dest), Node D (Alt Relay)",
                        color = radioColors.warning.copy(alpha = 0.8f),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Persistent Background Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (radioColors.isDark) Color(0xFF16241C) else Color(0xFFE8F2EC))
                .border(1.dp, radioColors.success.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = radioColors.success,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BACKGROUND NODE: Phone B relays packets while screen is OFF or UI is closed (connectedDevice service).",
                    color = radioColors.textPrimary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Topology Diagram Card
        TopologyDiagramCard(state = state)

        Spacer(modifier = Modifier.height(10.dp))

        // Control Buttons Row
        SimulationControls(
            onDiscovery = { viewModel.simStartDiscovery() },
            onSend = { viewModel.simSendPacketAtoC() },
            onFailB = { viewModel.simFailNodeB() },
            onReroute = { viewModel.simTriggerFailureAndRediscovery() },
            onRepairB = { viewModel.simRepairNodeB() },
            onReset = { viewModel.simResetTopology() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Packet Inspector Card (if packet selected)
        PacketInspectorCard(packet = state.selectedPacket)

        Spacer(modifier = Modifier.height(10.dp))

        // Event Log Feed
        Text(
            text = "EVENT FEED (${state.events.size}) · TAP EVENT TO INSPECT PACKET",
            color = radioColors.textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.events.isEmpty()) {
                Text(
                    text = "Awaiting simulation events. Tap [1. DISCOVERY] or [2. SEND A→C] to begin.",
                    color = radioColors.textTertiary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                state.events.forEach { event ->
                    SimEventItem(
                        event = event,
                        isSelected = state.selectedPacket != null && event.packet == state.selectedPacket,
                        onClick = {
                            if (event.packet != null) {
                                viewModel.simSelectPacket(event.packet)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TopologyDiagramCard(state: org.sih.itantra.core.mesh.TopologyState) {
    val radioColors = LocalRadioColors.current
    val nodeA = state.nodes.firstOrNull { it.id == ManetSimulator.NODE_A_ID }
    val nodeB = state.nodes.firstOrNull { it.id == ManetSimulator.NODE_B_ID }
    val nodeC = state.nodes.firstOrNull { it.id == ManetSimulator.NODE_C_ID }
    val nodeD = state.nodes.firstOrNull { it.id == ManetSimulator.NODE_D_ID }

    val activePathStr = when (state.activeRoute) {
        listOf(ManetSimulator.NODE_A_ID, ManetSimulator.NODE_B_ID, ManetSimulator.NODE_C_ID) ->
            "ACTIVE ROUTE: A ──> B ──> C (2 HOPS · PRIMARY)"
        listOf(ManetSimulator.NODE_A_ID, ManetSimulator.NODE_D_ID, ManetSimulator.NODE_C_ID) ->
            "ACTIVE ROUTE: A ──> D ──> C (2 HOPS · REROUTED VIA D)"
        else -> "ACTIVE ROUTE: NONE (AWAITING DISCOVERY / TX)"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NETWORK TOPOLOGY",
                    color = radioColors.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activePathStr,
                    color = if (state.activeRoute != null) radioColors.success else radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 1: Node B (Top Relay)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (nodeB != null) {
                    NodeBox(node = nodeB, isRelay = true)
                }
            }

            // Cross link indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (nodeB?.isOnline == true) "/          \\" else "X (DOWN)     X",
                    color = if (nodeB?.isOnline == true) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.alert,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Row 2: Node A (Left Origin) and Node C (Right Dest)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (nodeA != null) {
                    NodeBox(node = nodeA, isOrigin = true)
                }

                Text(
                    text = "◀── MULTI-HOP ──▶",
                    color = radioColors.textTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (nodeC != null) {
                    NodeBox(node = nodeC, isDest = true)
                }
            }

            // Cross link indicators bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "\\          /",
                    color = if (nodeD?.isOnline == true) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.textTertiary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Row 3: Node D (Bottom Alternate Relay)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (nodeD != null) {
                    NodeBox(node = nodeD, isAltRelay = true)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = radioColors.border.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "STATUS: ${state.statusMessage}",
                color = radioColors.textPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NodeBox(
    node: SimNode,
    isOrigin: Boolean = false,
    isDest: Boolean = false,
    isRelay: Boolean = false,
    isAltRelay: Boolean = false
) {
    val radioColors = LocalRadioColors.current

    val borderColor = when {
        !node.isOnline -> radioColors.alert
        isOrigin -> (if (radioColors.isDark) radioColors.sage else radioColors.forest)
        isDest -> radioColors.success
        else -> radioColors.border
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (node.isOnline) radioColors.surfaceHighlight else radioColors.alert.copy(alpha = 0.1f))
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (node.isOnline) radioColors.success else radioColors.alert)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = node.label,
                    color = radioColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (node.isReal) radioColors.success.copy(alpha = 0.2f) else radioColors.warning.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (node.isReal) "REAL" else "SIM",
                        color = if (node.isReal) radioColors.success else radioColors.warning,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = when {
                    !node.isOnline -> "OFFLINE"
                    isOrigin -> "Origin (Phone A)"
                    isRelay -> "Relay (Phone B)"
                    isDest -> "Dest (Node C)"
                    isAltRelay -> "Alt Relay (Node D)"
                    else -> ""
                },
                color = if (node.isOnline) radioColors.textSecondary else radioColors.alert,
                fontSize = 9.sp,
                fontFamily = FontFamily.SansSerif
            )

            Text(
                text = "Neigh:${node.neighborCount} | Rts:${node.routeCount}",
                color = radioColors.textTertiary,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SimulationControls(
    onDiscovery: () -> Unit,
    onSend: () -> Unit,
    onFailB: () -> Unit,
    onReroute: () -> Unit,
    onRepairB: () -> Unit,
    onReset: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SimButton(
                text = "1. DISCOVERY",
                onClick = onDiscovery,
                modifier = Modifier.weight(1f),
                containerColor = radioColors.surface
            )
            SimButton(
                text = "2. SEND A→C",
                onClick = onSend,
                modifier = Modifier.weight(1f),
                containerColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                textColor = Color.White
            )
            SimButton(
                text = "3. FAIL NODE B",
                onClick = onFailB,
                modifier = Modifier.weight(1f),
                containerColor = radioColors.alert.copy(alpha = 0.2f),
                textColor = radioColors.alert
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SimButton(
                text = "4. REROUTE (D)",
                onClick = onReroute,
                modifier = Modifier.weight(1f),
                containerColor = radioColors.warning.copy(alpha = 0.2f),
                textColor = radioColors.warning
            )
            SimButton(
                text = "5. REPAIR B",
                onClick = onRepairB,
                modifier = Modifier.weight(1f),
                containerColor = radioColors.surface
            )
            SimButton(
                text = "RESET",
                onClick = onReset,
                modifier = Modifier.weight(0.8f),
                containerColor = radioColors.surface
            )
        }
    }
}

@Composable
private fun SimButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    textColor: Color = Color.Unspecified
) {
    val radioColors = LocalRadioColors.current
    val effectiveTextColor = if (textColor == Color.Unspecified) radioColors.textPrimary else textColor

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = effectiveTextColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun PacketInspectorCard(packet: Packet?) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PACKET INSPECTOR (28-BYTE WIRE PROTOCOL)",
                    color = radioColors.textSecondary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (packet != null) {
                    Text(
                        text = "CRC: 0x${packet.crc32.toString(16).uppercase()}",
                        color = radioColors.success,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (packet != null) {
                val typeStr = when (packet.msgType) {
                    Packet.TYPE_ROUTE_REQUEST -> "RREQ (8)"
                    Packet.TYPE_ROUTE_REPLY   -> "RREP (9)"
                    Packet.TYPE_ROUTE_ERROR   -> "RERR (10)"
                    Packet.TYPE_HELLO         -> "HELLO (1)"
                    Packet.TYPE_TEXT          -> "TEXT / VOICE (3)"
                    else                      -> "TYPE_${packet.msgType}"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InspectorItem(label = "TYPE", value = typeStr)
                    InspectorItem(label = "SEQ", value = "#${packet.sequenceNumber}")
                    InspectorItem(label = "SRC", value = "${packet.sourceDeviceId}")
                    InspectorItem(label = "DEST", value = if (packet.destinationDeviceId == Packet.BROADCAST_ID) "BCAST" else "${packet.destinationDeviceId}")
                    InspectorItem(label = "TTL", value = "${packet.ttl}/${Packet.DEFAULT_TTL}")
                    InspectorItem(label = "PAYLOAD", value = "${packet.payload.size} B")
                }
            } else {
                Text(
                    text = "No packet selected. Perform discovery or transmission to inspect frames.",
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun InspectorItem(label: String, value: String) {
    val radioColors = LocalRadioColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = radioColors.textTertiary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = radioColors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SimEventItem(
    event: SimEvent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val timeStr = timeFormat.format(Date(event.timestampMs))

    val badgeColor = when (event.eventType) {
        SimEventType.HELLO          -> radioColors.textSecondary
        SimEventType.RREQ           -> radioColors.warning
        SimEventType.RREP           -> radioColors.success
        SimEventType.RERR           -> radioColors.alert
        SimEventType.DATA_FORWARD   -> (if (radioColors.isDark) radioColors.sage else radioColors.forest)
        SimEventType.DATA_DELIVERED -> radioColors.success
        SimEventType.FAILURE        -> radioColors.alert
        SimEventType.REPAIR         -> radioColors.success
        SimEventType.RESET          -> radioColors.textTertiary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) radioColors.surfaceHighlight else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) radioColors.success else radioColors.border.copy(alpha = 0.25f),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = event.eventType.name,
                            color = badgeColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = event.title,
                        color = radioColors.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = timeStr,
                    color = radioColors.textTertiary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = event.description,
                color = radioColors.textSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 12.sp
            )
        }
    }
}
