package org.sih.itantra.core.mesh

import org.sih.itantra.core.protocol.Packet

enum class TopologyNodeRole(val label: String) {
    LOCAL("LOCAL"),
    NEIGHBOR("NEIGHBOR"),
    RELAY("RELAY"),
    DESTINATION("DESTINATION"),
    UNKNOWN("UNKNOWN")
}

enum class TopologyNodeState(val label: String) {
    LOCAL("LOCAL"),
    ONLINE("ONLINE"),
    STALE("STALE"),
    OFFLINE("OFFLINE"),
    RELAY("RELAY")
}

data class TopologyNode(
    val nodeId: Int,
    val displayName: String,
    val isLocal: Boolean,
    val isReachable: Boolean,
    val lastSeen: String,
    val lastSeenMs: Long,
    val hopCount: Int,
    val transport: String,
    val routeState: String,
    val batteryLevel: String = "N/A",
    val queueDepth: String = "N/A",
    val linkQuality: String = "N/A",
    val lastPacketTime: String = "N/A",
    val role: TopologyNodeRole = TopologyNodeRole.UNKNOWN,
    val state: TopologyNodeState = TopologyNodeState.ONLINE,
    val isRealHardware: Boolean = false
)

data class TopologyLink(
    val sourceNodeId: Int,
    val destinationNodeId: Int,
    val transport: String,
    val isReachable: Boolean,
    val lastSeenMs: Long,
    val hopCost: Int = 1,
    val isHighlighted: Boolean = false,
    val isDashed: Boolean = false
)

data class TopologyRoute(
    val destinationNodeId: Int,
    val nextHopNodeId: Int,
    val hopCount: Int,
    val routeFreshness: Long,
    val transport: String,
    val state: String,
    val linkQuality: Float = 1.0f,
    val batteryPct: Int = 100
)


data class TopologyPacketActivity(
    val id: Long,
    val timestampMs: Long,
    val timeFormatted: String,
    val type: String,
    val description: String,
    val sourceId: Int? = null,
    val destId: Int? = null,
    val priorityLabel: String? = null,
    val rawPacket: Packet? = null
)

data class MeshTopologySnapshot(
    val isSimulation: Boolean = false,
    val localNode: TopologyNode? = null,
    val nodes: List<TopologyNode> = emptyList(),
    val links: List<TopologyLink> = emptyList(),
    val routes: List<TopologyRoute> = emptyList(),
    val selectedNodeId: Int? = null,
    val activityEvents: List<TopologyPacketActivity> = emptyList(),
    val congestionState: String = "NORMAL",
    val queueDepthSummary: String = "0/100",
    val queueBreakdown: String = "D 0 | A 0 | I 0 | N 0",
    val activeTransport: String = "BT / WIFI (AUTO)",
    val primaryTransport: String = "Bluetooth",
    val fallbackTransport: String = "Wi-Fi",
    val emergencyOverlay: String? = null,
    val emergencyPath: List<Int>? = null,
    val dtnPendingCount: Int = 0,
    val deliveryAckPendingCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
