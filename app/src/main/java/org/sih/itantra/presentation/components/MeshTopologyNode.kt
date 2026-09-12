package org.sih.itantra.presentation.components

import androidx.compose.ui.graphics.Color
import org.sih.itantra.core.topology.TopologyNodeDisplayState
import org.sih.itantra.presentation.theme.RadioColors

/**
 * Styling and color definitions for tactical topology nodes.
 */
object MeshTopologyNodeStyle {

    fun nodeColor(state: TopologyNodeDisplayState, isLocal: Boolean, colors: RadioColors): Color {
        return when {
            isLocal -> if (colors.isDark) Color(0xFF68D391) else Color(0xFF276749)
            state == TopologyNodeDisplayState.DIRECT -> colors.success
            state == TopologyNodeDisplayState.RELAY -> colors.warning
            state == TopologyNodeDisplayState.DTN -> Color(0xFF38B2AC)
            state == TopologyNodeDisplayState.UNREACHABLE -> colors.alert
            state == TopologyNodeDisplayState.RECENTLY_HEARD -> colors.textSecondary
            else -> colors.textTertiary
        }
    }

    fun nodeHaloColor(state: TopologyNodeDisplayState, isLocal: Boolean, colors: RadioColors): Color {
        return nodeColor(state, isLocal, colors).copy(alpha = 0.22f)
    }

    fun stateBadgeText(state: TopologyNodeDisplayState, isLocal: Boolean): String {
        return when {
            isLocal -> "HQ (LOCAL)"
            state == TopologyNodeDisplayState.DIRECT -> "DIRECT"
            state == TopologyNodeDisplayState.RELAY -> "RELAY"
            state == TopologyNodeDisplayState.DTN -> "DTN"
            state == TopologyNodeDisplayState.UNREACHABLE -> "OFFLINE"
            state == TopologyNodeDisplayState.RECENTLY_HEARD -> "STALE"
            else -> "UNKNOWN"
        }
    }
}
