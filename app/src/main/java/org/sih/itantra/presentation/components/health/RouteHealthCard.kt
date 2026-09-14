package org.sih.itantra.presentation.components.health

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.health.RouteHealthState
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical Card displaying MANET multi-hop routing health, neighbor reachability, and relay capability.
 */
@Composable
fun RouteHealthCard(
    routeHealth: RouteHealthState,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "MANET ROUTE HEALTH",
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                HealthFieldRow(
                    label = "Active Mesh Nodes",
                    value = "${routeHealth.knownNodesCount} Known (${routeHealth.activeNeighborsCount} direct)",
                    valueColor = if (routeHealth.knownNodesCount > 0) radioColors.success else radioColors.warning
                )
                HealthFieldRow(
                    label = "Reachable Destinations",
                    value = "${routeHealth.reachableDestinationsCount} Reachable",
                    valueColor = if (routeHealth.reachableDestinationsCount > 0) radioColors.success else radioColors.warning
                )
                HealthFieldRow(
                    label = "Multi-Hop Routes",
                    value = "${routeHealth.activeRoutesCount} Established",
                    valueColor = radioColors.textPrimary
                )
                HealthFieldRow(
                    label = "Preferred Route",
                    value = routeHealth.preferredRouteSummary,
                    valueColor = if (routeHealth.reachableDestinationsCount > 0) radioColors.textPrimary else radioColors.textSecondary
                )
                HealthFieldRow(
                    label = "Node Relay Routing",
                    value = if (routeHealth.isRelayEnabled) "ENABLED (Relayed: ${routeHealth.packetsRelayed})" else "DISABLED",
                    valueColor = if (routeHealth.isRelayEnabled) radioColors.success else radioColors.alert
                )
                HealthFieldRow(
                    label = "Route Selection Quality",
                    value = routeHealth.routeQualityLabel,
                    valueColor = if (routeHealth.routeQualityLabel == "GOOD") radioColors.success else radioColors.warning
                )
            }
        }
    }
}

@Composable
internal fun HealthFieldRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
