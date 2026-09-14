package org.sih.itantra.presentation.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.presentation.components.health.ActiveTransportsCard
import org.sih.itantra.presentation.components.health.DeliveryHealthCard
import org.sih.itantra.presentation.components.health.DtnAndQosCard
import org.sih.itantra.presentation.components.health.OverallHealthBanner
import org.sih.itantra.presentation.components.health.RecentEventsTimeline
import org.sih.itantra.presentation.components.health.RouteHealthCard
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

/**
 * Feature 13: Truthful Communication Health Panel.
 *
 * Provides real-time tactical observability into:
 * 1. Overall communication health (Healthy, Limited, Degraded, Offline)
 * 2. Active physical transports (Wi-Fi, Bluetooth) with strict truthful metrics
 * 3. MANET mesh route reachability and multi-hop relay capability
 * 4. Feature 6 message delivery state aggregation
 * 5. QoS transmission queue depth and DTN store-and-forward buffer
 * 6. Recent communication events timeline
 */
@Composable
fun CommunicationHealthScreen(
    viewModel: TransceiverViewModel,
    onBack: () -> Unit,
    onOpenMeshTopology: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val healthState by viewModel.communicationHealthState.collectAsState()

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Diagnostics",
                        tint = if (radioColors.isDark) radioColors.sage else radioColors.forest
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "COMMUNICATION HEALTH",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Field radio observability & status",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "LIVE TELEMETRY",
                    color = radioColors.success,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Overall Health Banner (Hero)
        OverallHealthBanner(
            status = healthState.overallStatus,
            badgeLabel = healthState.overallBadge,
            summary = healthState.overallSummary,
            localNodeId = healthState.localNodeId
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Active Physical Transports (Wi-Fi, Bluetooth)
        ActiveTransportsCard(
            transports = healthState.transports
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. MANET Mesh Route Health
        RouteHealthCard(
            routeHealth = healthState.routeHealth
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Feature 6 Delivery Health
        DeliveryHealthCard(
            deliveryHealth = healthState.deliveryHealth
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 6. DTN & QoS Telemetry
        DtnAndQosCard(
            qosHealth = healthState.qosHealth,
            dtnHealth = healthState.dtnHealth
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 7. Recent Communication Events Timeline
        RecentEventsTimeline(
            events = healthState.recentEvents
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 8. Operator Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Open Mesh Topology
            Row(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(radioColors.surface)
                    .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .clickable { onOpenMeshTopology() }
                    .padding(vertical = 11.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MESH TOPOLOGY",
                    color = radioColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Test Neural Loopback
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { viewModel.testNeuralLoopback() }
                    .padding(vertical = 11.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = radioColors.success,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "TEST PACKET",
                    color = radioColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
