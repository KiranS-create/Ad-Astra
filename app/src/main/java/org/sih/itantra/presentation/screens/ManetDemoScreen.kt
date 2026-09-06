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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.mesh.ManetSimulator
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.SimEvent
import org.sih.itantra.core.mesh.SimEventType
import org.sih.itantra.core.mesh.SimNode
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.mesh.TopologyNodeRole
import org.sih.itantra.core.mesh.TopologyNodeState
import org.sih.itantra.core.mesh.TopologyPacketActivity
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
    val snapshot by viewModel.meshTopologySnapshot.collectAsState()
    val isSimulation by viewModel.isSimulationMode.collectAsState()
    val simState by viewModel.topologyState.collectAsState()
    val selectedNodeId by viewModel.selectedNodeId.collectAsState()

    var inspectorPacket by remember { mutableStateOf<Packet?>(null) }

    // Keep inspector packet updated if simulation selection changes
    LaunchedEffect(simState.selectedPacket) {
        if (isSimulation && simState.selectedPacket != null) {
            inspectorPacket = simState.selectedPacket
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Top Header
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
                            text = if (isSimulation) "MANET SIMULATION" else "LIVE TACTICAL MESH MAP",
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSimulation) radioColors.warning.copy(alpha = 0.2f)
                                    else radioColors.success.copy(alpha = 0.2f)
                                )
                                .border(
                                    1.dp,
                                    if (isSimulation) radioColors.warning else radioColors.success,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isSimulation) "SIMULATION" else "LIVE HARDWARE",
                                color = if (isSimulation) radioColors.warning else radioColors.success,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = if (isSimulation) "Deterministic 4-node production routing simulation" else "Real-time node topology, multi-hop routes & link health",
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

        // 2. Mode Selector (LIVE HARDWARE vs SIMULATION)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (!isSimulation) (if (radioColors.isDark) Color(0xFF162E20) else Color(0xFFD8EEDF))
                        else Color.Transparent
                    )
                    .clickable { viewModel.setSimulationMode(false) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (!isSimulation) radioColors.success else radioColors.textTertiary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE HARDWARE",
                        color = if (!isSimulation) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSimulation) (if (radioColors.isDark) Color(0xFF332412) else Color(0xFFFFECCC))
                        else Color.Transparent
                    )
                    .clickable { viewModel.setSimulationMode(true) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isSimulation) radioColors.warning else radioColors.textTertiary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SIMULATION",
                        color = if (isSimulation) radioColors.warning else radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Operational Telemetry Banner
        if (!isSimulation) {
            LiveOperationalBanner(snapshot = snapshot)
        } else {
            SimulationOperationalBanner(state = simState)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Tactical Network Graph Canvas
        if (!isSimulation) {
            LiveTopologyCanvas(
                snapshot = snapshot,
                selectedNodeId = selectedNodeId,
                onSelectNode = { viewModel.selectNode(it) }
            )
        } else {
            TopologyDiagramCard(
                state = simState,
                selectedNodeId = selectedNodeId,
                onSelectNode = { viewModel.selectNode(it) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Selected Node Details Panel
        val activeSelectedNode = if (!isSimulation) {
            snapshot.nodes.firstOrNull { it.nodeId == selectedNodeId } ?: snapshot.localNode
        } else {
            snapshot.nodes.firstOrNull { it.nodeId == selectedNodeId } ?: snapshot.localNode
        }
        if (activeSelectedNode != null) {
            NodeDetailsPanel(
                node = activeSelectedNode,
                snapshot = snapshot,
                route = snapshot.routes.firstOrNull { it.destinationNodeId == activeSelectedNode.nodeId }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 6. Tactical QoS & Emergency Overlay Card
        TacticalQosEmergencyCard(snapshot = snapshot)

        Spacer(modifier = Modifier.height(10.dp))

        // 7. Simulation Controls (only shown in Simulation mode)
        if (isSimulation) {
            SimulationControls(
                onDiscovery = { viewModel.simStartDiscovery() },
                onSend = { viewModel.simSendPacketAtoC() },
                onFailB = { viewModel.simFailNodeB() },
                onReroute = { viewModel.simTriggerFailureAndRediscovery() },
                onRepairB = { viewModel.simRepairNodeB() },
                onReset = { viewModel.simResetTopology() }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 8. Packet Inspector Card (when packet selected or tapped)
        if (inspectorPacket != null) {
            PacketInspectorCard(
                packet = inspectorPacket,
                onDismiss = { inspectorPacket = null }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 9. Packet Activity Feed
        val events = snapshot.activityEvents
        Text(
            text = "LIVE PACKET ACTIVITY (${events.size}/50) · TAP TO INSPECT FRAME",
            color = radioColors.textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSimulation) "No simulation events yet. Tap '1. DISCOVERY' to start." else "Awaiting live packet activity across Bluetooth / Wi-Fi mesh.",
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                events.take(20).forEach { event ->
                    ActivityEventRow(
                        activity = event,
                        onInspect = {
                            if (event.rawPacket != null) {
                                inspectorPacket = event.rawPacket
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// -----------------------------------------------------------------------------
// UI Sub-components
// -----------------------------------------------------------------------------

@Composable
private fun LiveOperationalBanner(snapshot: MeshTopologySnapshot) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(radioColors.success))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TRANSPORT: ${snapshot.activeTransport}",
                        color = radioColors.textPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "PRI: ${snapshot.primaryTransport} | ALT: ${snapshot.fallbackTransport}",
                    color = radioColors.textSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISCOVERED NODES: ${snapshot.nodes.size} (${snapshot.nodes.count { it.role == TopologyNodeRole.NEIGHBOR }} 1-hop peers)",
                    color = radioColors.textSecondary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "ACTIVE ROUTES: ${snapshot.routes.size}",
                    color = if (snapshot.routes.isNotEmpty()) radioColors.success else radioColors.textTertiary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SimulationOperationalBanner(state: org.sih.itantra.core.mesh.TopologyState) {
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
                    text = "Phone A, Phone B (Note 10 Lite)",
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
}

@Composable
private fun LiveTopologyCanvas(
    snapshot: MeshTopologySnapshot,
    selectedNodeId: Int?,
    onSelectNode: (Int?) -> Unit
) {
    val radioColors = LocalRadioColors.current
    val localNode = snapshot.localNode
    val neighbors = snapshot.nodes.filter { !it.isLocal }

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
                    text = "TACTICAL TOPOLOGY GRAPH",
                    color = radioColors.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (neighbors.isEmpty()) "SCANNING FOR PEERS..." else "${neighbors.size} PEER(S) LINKED",
                    color = if (neighbors.isEmpty()) radioColors.warning else radioColors.success,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Center: Local Node
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (localNode != null) {
                    LiveNodeBadge(
                        node = localNode,
                        isSelected = selectedNodeId == localNode.nodeId || selectedNodeId == null,
                        onClick = { onSelectNode(localNode.nodeId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (neighbors.isEmpty()) {
                // Scanning indicator
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "· · ·  BEACON SEARCH (BT RFCOMM / UDP)  · · ·",
                        color = radioColors.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Peers will appear automatically when in RFCOMM / Wi-Fi range",
                        color = radioColors.textSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else {
                // Link lines
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "|          |",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Neighbors grid/row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    neighbors.forEach { neighbor ->
                        LiveNodeBadge(
                            node = neighbor,
                            isSelected = selectedNodeId == neighbor.nodeId,
                            onClick = { onSelectNode(neighbor.nodeId) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = radioColors.border.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "TAP NODE TO INSPECT TELEMETRY & ROUTE METRICS",
                color = radioColors.textTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun LiveNodeBadge(
    node: TopologyNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    val borderColor = when {
        isSelected -> if (radioColors.isDark) radioColors.sage else radioColors.forest
        !node.isReachable -> radioColors.alert
        node.isLocal -> radioColors.border
        else -> radioColors.border.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) (if (radioColors.isDark) Color(0xFF1E3326) else Color(0xFFE2F0E7))
                else radioColors.surfaceHighlight
            )
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (node.isReachable) radioColors.success else radioColors.alert)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Node #${node.nodeId}",
                    color = radioColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when (node.role) {
                                TopologyNodeRole.LOCAL -> radioColors.border.copy(alpha = 0.3f)
                                TopologyNodeRole.RELAY -> radioColors.warning.copy(alpha = 0.2f)
                                TopologyNodeRole.DESTINATION -> radioColors.success.copy(alpha = 0.2f)
                                else -> radioColors.surface.copy(alpha = 0.5f)
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = node.role.label,
                        color = when (node.role) {
                            TopologyNodeRole.LOCAL -> radioColors.textSecondary
                            TopologyNodeRole.RELAY -> radioColors.warning
                            TopologyNodeRole.DESTINATION -> radioColors.success
                            else -> radioColors.textSecondary
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${node.transport} · ${node.lastSeen}",
                color = radioColors.textSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TopologyDiagramCard(
    state: org.sih.itantra.core.mesh.TopologyState,
    selectedNodeId: Int?,
    onSelectNode: (Int?) -> Unit
) {
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
                    text = "NETWORK TOPOLOGY (SIMULATION)",
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
                    SimNodeBox(
                        node = nodeB,
                        isRelay = true,
                        isSelected = selectedNodeId == nodeB.id,
                        onClick = { onSelectNode(nodeB.id) }
                    )
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
                    SimNodeBox(
                        node = nodeA,
                        isOrigin = true,
                        isSelected = selectedNodeId == nodeA.id || selectedNodeId == null,
                        onClick = { onSelectNode(nodeA.id) }
                    )
                }
                if (nodeC != null) {
                    SimNodeBox(
                        node = nodeC,
                        isDest = true,
                        isSelected = selectedNodeId == nodeC.id,
                        onClick = { onSelectNode(nodeC.id) }
                    )
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
                    SimNodeBox(
                        node = nodeD,
                        isAltRelay = true,
                        isSelected = selectedNodeId == nodeD.id,
                        onClick = { onSelectNode(nodeD.id) }
                    )
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
private fun SimNodeBox(
    node: SimNode,
    isOrigin: Boolean = false,
    isDest: Boolean = false,
    isRelay: Boolean = false,
    isAltRelay: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val radioColors = LocalRadioColors.current

    val borderColor = when {
        isSelected -> if (radioColors.isDark) radioColors.sage else radioColors.forest
        !node.isOnline -> radioColors.alert
        isOrigin -> (if (radioColors.isDark) radioColors.sage else radioColors.forest)
        isDest -> radioColors.success
        else -> radioColors.border
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) (if (radioColors.isDark) Color(0xFF1E3326) else Color(0xFFE2F0E7))
                else if (node.isOnline) radioColors.surfaceHighlight else radioColors.alert.copy(alpha = 0.1f)
            )
            .border(if (isSelected) 2.dp else 1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
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
private fun NodeDetailsPanel(
    node: TopologyNode,
    snapshot: MeshTopologySnapshot,
    route: org.sih.itantra.core.mesh.TopologyRoute?
) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.surface)
            .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NODE DETAILS: ${node.displayName}",
                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ROLE: ${node.role.label}",
                    color = radioColors.textSecondary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    DetailLine(label = "Status", value = node.state.label)
                    DetailLine(label = "Hops", value = "${node.hopCount}")
                    DetailLine(label = "Transport", value = node.transport)
                    DetailLine(label = "Route State", value = node.routeState)
                }
                Column {
                    DetailLine(label = "Last Seen", value = node.lastSeen)
                    DetailLine(label = "Link Quality", value = node.linkQuality)
                    DetailLine(label = "Battery", value = node.batteryLevel)
                    DetailLine(label = "Queue Depth", value = node.queueDepth)
                }
            }

            if (route != null) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = radioColors.border.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ACTIVE ROUTE -> Next Hop: Node #${route.nextHopNodeId} | Hops: ${route.hopCount} | Seq: #${route.routeFreshness}",
                    color = radioColors.success,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val radioColors = LocalRadioColors.current
    Row {
        Text(text = "$label: ", color = radioColors.textTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = radioColors.textPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TacticalQosEmergencyCard(snapshot: MeshTopologySnapshot) {
    val radioColors = LocalRadioColors.current

    val qosColor = when (snapshot.congestionState) {
        "CONGESTED" -> radioColors.alert
        "BUSY" -> radioColors.warning
        else -> radioColors.success
    }

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
                    text = "TACTICAL QOS & EMERGENCY OVERLAY",
                    color = radioColors.textSecondary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(qosColor.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "QOS: ${snapshot.congestionState}",
                        color = qosColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Outbound Queue: ${snapshot.queueDepthSummary} (${snapshot.queueBreakdown})",
                    color = radioColors.textPrimary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "DTN: ${snapshot.dtnPendingCount} pend",
                    color = if (snapshot.dtnPendingCount > 0) radioColors.warning else radioColors.textTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (snapshot.emergencyOverlay != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "OVERLAY: ${snapshot.emergencyOverlay}",
                    color = radioColors.alert,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
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
private fun PacketInspectorCard(
    packet: Packet?,
    onDismiss: () -> Unit = {}
) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.surface)
            .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.8f), RoundedCornerShape(8.dp))
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
                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
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
                    Packet.TYPE_ACK           -> "ACK (4)"
                    Packet.TYPE_DISTRESS      -> "DISTRESS (5)"
                    Packet.TYPE_ALERT         -> "ALERT (6)"
                    else                      -> "TYPE_${packet.msgType}"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InspectorItem(label = "TYPE", value = typeStr)
                    InspectorItem(label = "PRIORITY", value = packet.priority.name)
                    InspectorItem(label = "SRC", value = "${packet.sourceDeviceId}")
                    InspectorItem(label = "DEST", value = if (packet.destinationDeviceId == Packet.BROADCAST_ID) "BCAST" else "${packet.destinationDeviceId}")
                    InspectorItem(label = "TTL", value = "${packet.ttl}/${Packet.DEFAULT_TTL}")
                    InspectorItem(label = "PAYLOAD", value = "${packet.payload.size} B")
                }
            }
        }
    }
}

@Composable
private fun InspectorItem(label: String, value: String) {
    val radioColors = LocalRadioColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = radioColors.textTertiary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = radioColors.textPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ActivityEventRow(
    activity: TopologyPacketActivity,
    onInspect: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    val badgeColor = when (activity.type) {
        "DISTRESS", "FAIL" -> radioColors.alert
        "TX", "RREQ" -> if (radioColors.isDark) radioColors.sage else radioColors.forest
        "ACK", "REPAIR", "HELLO" -> radioColors.success
        "RELAY", "RREP" -> radioColors.warning
        "FRAG" -> Color(0xFF64B5F6)
        else -> radioColors.border
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable { onInspect() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(badgeColor.copy(alpha = 0.2f))
                    .border(1.dp, badgeColor, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = activity.type,
                    color = badgeColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = activity.description,
                color = radioColors.textPrimary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = activity.timeFormatted,
            color = radioColors.textTertiary,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
