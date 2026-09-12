package org.sih.itantra.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.topology.TopologyDisplayMapper
import org.sih.itantra.presentation.components.MeshTopologyCanvas
import org.sih.itantra.presentation.components.TopologyNodeDetails
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

/**
 * ViewModel overload for Live Mesh Topology screen.
 */
@Composable
fun MeshTopologyScreen(
    viewModel: TransceiverViewModel,
    onBack: () -> Unit,
    onOpenChat: (Int) -> Unit = {},
    onOpenContact: (Int) -> Unit = {},
    onInspectNode: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snapshot by viewModel.meshTopologySnapshot.collectAsState()
    val nearbyDevices by viewModel.nearbyDeviceRepository.discoveredDevices.collectAsState()

    MeshTopologyScreen(
        snapshot = snapshot,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onOpenContact = onOpenContact,
        onInspectNode = onInspectNode,
        callsignProvider = { nodeId ->
            viewModel.contactRepository.getContact(nodeId)?.identity?.callsign
        },
        rssiProvider = { nodeId ->
            nearbyDevices.firstOrNull { it.nodeId == nodeId }?.rawRssi
        },
        modifier = modifier
    )
}

/**
 * Pure, reactive Live Mesh Topology Screen.
 * Visualizes existing real network state with zero fabrication.
 */
@Composable
fun MeshTopologyScreen(
    snapshot: MeshTopologySnapshot,
    onBack: () -> Unit,
    onOpenChat: (Int) -> Unit = {},
    onOpenContact: (Int) -> Unit = {},
    onInspectNode: (Int) -> Unit = {},
    callsignProvider: ((Int) -> String?)? = null,
    rssiProvider: ((Int) -> Int?)? = null,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    var selectedNodeId by remember { mutableStateOf<Int?>(null) }

    // Map snapshot deterministically into immutable UI display state
    val displayState = remember(snapshot, selectedNodeId, callsignProvider, rssiProvider) {
        TopologyDisplayMapper.mapSnapshot(
            snapshot = snapshot,
            selectedNodeId = selectedNodeId,
            callsignProvider = callsignProvider,
            rssiProvider = rssiProvider
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Top App Bar & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(32.dp)
                ) {
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
                            text = "LIVE MESH TOPOLOGY",
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
                                    if (snapshot.isSimulation) radioColors.warning.copy(alpha = 0.2f)
                                    else radioColors.success.copy(alpha = 0.2f)
                                )
                                .border(
                                    1.dp,
                                    if (snapshot.isSimulation) radioColors.warning else radioColors.success,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (snapshot.isSimulation) "SIMULATION" else "LIVE HARDWARE",
                                color = if (snapshot.isSimulation) radioColors.warning else radioColors.success,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "Real-time MANET Routing, Multi-hop Nodes & RF Links",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Tactical Statistics Header Bar
        TacticalStatsHeader(displayState = displayState)

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Central Radar Topology Canvas
        MeshTopologyCanvas(
            state = displayState,
            onSelectNode = { selectedNodeId = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Selected Node Telemetry HUD / Prompt
        val activeSelectedNode = displayState.selectedNode
        if (activeSelectedNode != null) {
            TopologyNodeDetails(
                node = activeSelectedNode,
                onOpenChat = onOpenChat,
                onOpenContact = onOpenContact,
                onInspectNode = onInspectNode
            )
        } else {
            // Default prompt if no node currently selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(radioColors.surface.copy(alpha = 0.5f))
                    .border(1.dp, radioColors.border.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TAP ANY NODE ON THE RADAR GRID TO INSPECT",
                        color = radioColors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Zero-fabrication active · Missing metrics display UNKNOWN",
                        color = radioColors.textTertiary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TacticalStatsHeader(
    displayState: org.sih.itantra.core.topology.MeshTopologyDisplayState
) {
    val radioColors = LocalRadioColors.current
    val stats = displayState.stats

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricTag(label = "NODES", value = String.format("%02d", stats.nodeCount))
                    MetricTag(label = "LINKS", value = String.format("%02d", stats.linkCount))
                    MetricTag(label = "ROUTES", value = String.format("%02d", stats.routeCount))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (stats.nodeCount > 1) radioColors.success else radioColors.warning)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stats.activeTransport,
                        color = radioColors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (displayState.emergencyOverlay != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(radioColors.alert.copy(alpha = 0.2f))
                        .border(1.dp, radioColors.alert, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OVERLAY: ${displayState.emergencyOverlay}",
                        color = radioColors.alert,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTag(label: String, value: String) {
    val radioColors = LocalRadioColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            color = radioColors.textTertiary,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp
        )
        Text(
            text = value,
            color = radioColors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
