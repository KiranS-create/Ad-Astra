package org.sih.itantra.core.topology

/**
 * Tactical node states visually distinguished on the topology screen.
 */
enum class TopologyNodeDisplayState(val label: String) {
    DIRECT("DIRECT"),
    RELAY("RELAY"),
    DTN("DTN"),
    UNREACHABLE("UNREACHABLE"),
    RECENTLY_HEARD("RECENTLY HEARD"),
    UNKNOWN("UNKNOWN")
}

/**
 * Logical link classification between nodes.
 */
enum class TopologyLinkType(val label: String) {
    DIRECT("DIRECT"),
    RELAY("RELAY"),
    DTN("DTN"),
    UNKNOWN("UNKNOWN")
}

/**
 * Immutable display state for a single node in the tactical topology.
 */
data class TopologyDisplayNode(
    val nodeId: Int,
    val callsign: String?,
    val displayLabel: String,
    val isLocal: Boolean,
    val status: String,
    val state: TopologyNodeDisplayState,
    val routeSummary: String,
    val hopCount: Int?,
    val transport: String?,
    val rssi: Int?,
    val lastSeenFormatted: String,
    val batteryLevel: String = "N/A",
    val linkQuality: String = "N/A",
    val isReachable: Boolean = true,
    val tier: Int = 1,
    val angleRad: Float = 0f,
    val normalizedRadius: Float = 0f,
    val isHighlighted: Boolean = false
) {
    val effectiveCallsign: String
        get() = callsign ?: "UNKNOWN"

    val effectiveRssi: String
        get() = if (rssi != null) "$rssi dBm" else "UNKNOWN"

    val effectiveHops: String
        get() = if (hopCount != null) "$hopCount" else "UNKNOWN"

    val effectiveTransport: String
        get() = transport?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
}

/**
 * Immutable display state for a link between two nodes.
 */
data class TopologyDisplayLink(
    val sourceNodeId: Int,
    val destinationNodeId: Int,
    val linkType: TopologyLinkType,
    val transport: String,
    val isReachable: Boolean,
    val isHighlighted: Boolean = false,
    val isDashed: Boolean = false,
    val hopCost: Int = 1
)

/**
 * Tactical statistics header summary.
 */
data class TopologyStats(
    val nodeCount: Int,
    val linkCount: Int,
    val routeCount: Int,
    val activeTransport: String,
    val congestionState: String = "NORMAL",
    val dtnPendingCount: Int = 0,
    val queueDepthSummary: String = "0/100"
)

/**
 * Complete immutable screen state for MeshTopologyScreen.
 */
data class MeshTopologyDisplayState(
    val stats: TopologyStats,
    val localNode: TopologyDisplayNode?,
    val nodes: List<TopologyDisplayNode> = emptyList(),
    val links: List<TopologyDisplayLink> = emptyList(),
    val selectedNodeId: Int? = null,
    val isEmpty: Boolean = false,
    val emptyMessage: String = "NO PEERS DETECTED",
    val emergencyOverlay: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val selectedNode: TopologyDisplayNode?
        get() = if (selectedNodeId != null) {
            if (localNode?.nodeId == selectedNodeId) localNode
            else nodes.firstOrNull { it.nodeId == selectedNodeId }
        } else null
}
