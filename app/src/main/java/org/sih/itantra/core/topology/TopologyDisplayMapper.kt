package org.sih.itantra.core.topology

import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.mesh.TopologyNodeRole
import org.sih.itantra.core.mesh.TopologyNodeState
import kotlin.math.PI

/**
 * Pure, stateless mapper that transforms a canonical [MeshTopologySnapshot]
 * into a deterministic, UI-facing [MeshTopologyDisplayState].
 *
 * Guarantees:
 * - Zero fabrication of nodes, links, RSSI, routes, or metrics.
 * - Missing telemetry strictly maps to null / UNKNOWN.
 * - Deterministic polar layout (stable angles and radii across recompositions).
 * - Node and link deduplication.
 */
object TopologyDisplayMapper {

    private const val RADIUS_TIER_LOCAL = 0.0f
    private const val RADIUS_TIER_DIRECT = 0.50f
    private const val RADIUS_TIER_RELAY = 0.78f
    private const val RADIUS_TIER_OUTER = 0.95f

    fun mapSnapshot(
        snapshot: MeshTopologySnapshot,
        selectedNodeId: Int? = null,
        callsignProvider: ((Int) -> String?)? = null,
        rssiProvider: ((Int) -> Int?)? = null
    ): MeshTopologyDisplayState {
        // 1. Resolve Local Node
        val rawLocalNode = snapshot.localNode ?: snapshot.nodes.firstOrNull { it.isLocal }
        val localDisplayNode = rawLocalNode?.let { node ->
            val callsign = callsignProvider?.invoke(node.nodeId) ?: extractCallsign(node.displayName)
            val label = if (callsign != null) "$callsign (HQ)" else "NODE #${node.nodeId} (LOCAL)"
            TopologyDisplayNode(
                nodeId = node.nodeId,
                callsign = callsign,
                displayLabel = label,
                isLocal = true,
                status = "LOCAL",
                state = TopologyNodeDisplayState.DIRECT,
                routeSummary = "LOCAL RADIO (HQ)",
                hopCount = 0,
                transport = node.transport.takeIf { it.isNotBlank() } ?: snapshot.activeTransport,
                rssi = null, // Local node has no RSSI to itself
                lastSeenFormatted = "CURRENT",
                batteryLevel = node.batteryLevel,
                linkQuality = node.linkQuality,
                isReachable = true,
                tier = 0,
                angleRad = 0f,
                normalizedRadius = RADIUS_TIER_LOCAL,
                isHighlighted = selectedNodeId == node.nodeId
            )
        }

        val localNodeId = localDisplayNode?.nodeId

        // 2. Peer Nodes Deduplication & Filtering
        val rawPeers = snapshot.nodes
            .filter { !it.isLocal && (localNodeId == null || it.nodeId != localNodeId) }
            .distinctBy { it.nodeId }
            .sortedBy { it.nodeId }

        // 3. Classify Peers into Tiers and Map Display Nodes
        val mappedPeers = rawPeers.map { node ->
            mapPeerNode(
                node = node,
                snapshot = snapshot,
                selectedNodeId = selectedNodeId,
                callsignProvider = callsignProvider,
                rssiProvider = rssiProvider
            )
        }

        // 4. Assign Deterministic Polar Coordinates per Tier
        val tier1 = mappedPeers.filter { it.tier == 1 }
        val tier2 = mappedPeers.filter { it.tier == 2 }
        val tier3 = mappedPeers.filter { it.tier >= 3 }

        val finalPeers = mutableListOf<TopologyDisplayNode>()

        // Tier 1 (Direct 1-Hop Peers)
        val n1 = tier1.size
        tier1.forEachIndexed { idx, node ->
            val angle = if (n1 == 1) - (PI.toFloat() / 2f)
            else - (PI.toFloat() / 2f) + (idx * 2f * PI.toFloat() / n1)
            finalPeers.add(node.copy(angleRad = angle, normalizedRadius = RADIUS_TIER_DIRECT))
        }

        // Tier 2 (Relays / Multi-hop / DTN)
        val n2 = tier2.size
        tier2.forEachIndexed { idx, node ->
            val offset = if (n1 > 0) (PI.toFloat() / n2.coerceAtLeast(1)) else 0f
            val angle = - (PI.toFloat() / 2f) + offset + (idx * 2f * PI.toFloat() / n2.coerceAtLeast(1))
            finalPeers.add(node.copy(angleRad = angle, normalizedRadius = RADIUS_TIER_RELAY))
        }

        // Tier 3 (Unreachable / Stale / Out-of-contact)
        val n3 = tier3.size
        tier3.forEachIndexed { idx, node ->
            val angle = (PI.toFloat() / 4f) + (idx * 2f * PI.toFloat() / n3.coerceAtLeast(1))
            finalPeers.add(node.copy(angleRad = angle, normalizedRadius = RADIUS_TIER_OUTER))
        }

        // 5. Links Mapping & Deduplication
        val processedLinkPairs = mutableSetOf<Pair<Int, Int>>()
        val displayLinks = mutableListOf<TopologyDisplayLink>()

        for (link in snapshot.links) {
            val s = link.sourceNodeId
            val d = link.destinationNodeId
            val pairKey = if (s <= d) s to d else d to s
            if (!processedLinkPairs.add(pairKey)) continue

            val linkType = when {
                link.isDashed || link.transport.contains("MESH", ignoreCase = true) || link.hopCost > 1 ->
                    TopologyLinkType.RELAY
                link.hopCost == 1 ->
                    TopologyLinkType.DIRECT
                else ->
                    TopologyLinkType.UNKNOWN
            }

            val isOnEmergencyPath = snapshot.emergencyPath?.let { path ->
                (0 until path.size - 1).any { i ->
                    (s == path[i] && d == path[i + 1]) || (d == path[i] && s == path[i + 1])
                }
            } ?: false

            val isSelectedLink = selectedNodeId != null && (s == selectedNodeId || d == selectedNodeId)

            displayLinks.add(
                TopologyDisplayLink(
                    sourceNodeId = s,
                    destinationNodeId = d,
                    linkType = linkType,
                    transport = link.transport.takeIf { it.isNotBlank() } ?: "UNKNOWN",
                    isReachable = link.isReachable,
                    isHighlighted = link.isHighlighted || isOnEmergencyPath || isSelectedLink,
                    isDashed = link.isDashed || linkType == TopologyLinkType.RELAY,
                    hopCost = link.hopCost
                )
            )
        }

        // 6. Aggregate Stats
        val totalNodesCount = (if (localDisplayNode != null) 1 else 0) + finalPeers.size
        val stats = TopologyStats(
            nodeCount = totalNodesCount,
            linkCount = displayLinks.size,
            routeCount = snapshot.routes.size,
            activeTransport = snapshot.activeTransport,
            congestionState = snapshot.congestionState,
            dtnPendingCount = snapshot.dtnPendingCount,
            queueDepthSummary = snapshot.queueDepthSummary
        )

        return MeshTopologyDisplayState(
            stats = stats,
            localNode = localDisplayNode,
            nodes = finalPeers,
            links = displayLinks,
            selectedNodeId = selectedNodeId,
            isEmpty = finalPeers.isEmpty(),
            emptyMessage = if (finalPeers.isEmpty()) "NO PEERS DETECTED" else "",
            emergencyOverlay = snapshot.emergencyOverlay,
            timestamp = snapshot.timestamp
        )
    }

    private fun mapPeerNode(
        node: TopologyNode,
        snapshot: MeshTopologySnapshot,
        selectedNodeId: Int?,
        callsignProvider: ((Int) -> String?)?,
        rssiProvider: ((Int) -> Int?)?
    ): TopologyDisplayNode {
        val nodeId = node.nodeId
        val callsign = callsignProvider?.invoke(nodeId) ?: extractCallsign(node.displayName)
        val displayLabel = if (callsign != null) "$callsign (#$nodeId)" else "NODE #$nodeId"

        // Route lookup for this destination
        val route = snapshot.routes.firstOrNull { it.destinationNodeId == nodeId }

        // Determine hop count
        val hopCount = when {
            node.hopCount > 0 -> node.hopCount
            route != null && route.hopCount > 0 -> route.hopCount
            node.role == TopologyNodeRole.NEIGHBOR -> 1
            else -> null
        }

        // Determine route summary
        val routeSummary = when {
            hopCount == 1 || node.routeState.equals("DIRECT", ignoreCase = true) -> "DIRECT (1 HOP)"
            route != null && route.hopCount > 1 -> "VIA #${route.nextHopNodeId} (${route.hopCount} HOPS)"
            hopCount != null && hopCount > 1 -> "MULTI-HOP ($hopCount HOPS)"
            node.role == TopologyNodeRole.RELAY -> "RELAY NODE"
            snapshot.dtnPendingCount > 0 && node.role == TopologyNodeRole.DESTINATION -> "DTN STORED"
            !node.isReachable -> "UNREACHABLE"
            else -> "UNKNOWN"
        }

        // Determine node display state
        val state = when {
            !node.isReachable || node.state == TopologyNodeState.OFFLINE ->
                TopologyNodeDisplayState.UNREACHABLE
            node.state == TopologyNodeState.STALE ->
                TopologyNodeDisplayState.RECENTLY_HEARD
            node.role == TopologyNodeRole.RELAY || (hopCount != null && hopCount > 1) ->
                TopologyNodeDisplayState.RELAY
            hopCount == 1 || node.routeState.equals("DIRECT", ignoreCase = true) ->
                TopologyNodeDisplayState.DIRECT
            snapshot.dtnPendingCount > 0 && node.role == TopologyNodeRole.DESTINATION ->
                TopologyNodeDisplayState.DTN
            else ->
                TopologyNodeDisplayState.UNKNOWN
        }

        // Determine placement tier
        val tier = when (state) {
            TopologyNodeDisplayState.DIRECT -> 1
            TopologyNodeDisplayState.RELAY, TopologyNodeDisplayState.DTN -> 2
            TopologyNodeDisplayState.UNREACHABLE, TopologyNodeDisplayState.RECENTLY_HEARD, TopologyNodeDisplayState.UNKNOWN -> 3
        }

        // Resolve transport
        val transport = node.transport.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", ignoreCase = true) }
            ?: route?.transport?.takeIf { it.isNotBlank() }

        // Resolve RSSI strictly from genuine provider
        val rssi = rssiProvider?.invoke(nodeId)

        // Last seen formatted
        val lastSeen = node.lastSeen.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: "UNKNOWN"

        return TopologyDisplayNode(
            nodeId = nodeId,
            callsign = callsign,
            displayLabel = displayLabel,
            isLocal = false,
            status = node.state.label,
            state = state,
            routeSummary = routeSummary,
            hopCount = hopCount,
            transport = transport,
            rssi = rssi,
            lastSeenFormatted = lastSeen,
            batteryLevel = node.batteryLevel,
            linkQuality = node.linkQuality,
            isReachable = node.isReachable,
            tier = tier,
            isHighlighted = selectedNodeId == nodeId
        )
    }

    /**
     * Extracts callsign from displayName only if it contains genuine identity info
     * and is not merely "NODE 123" or "NODE #123".
     */
    fun extractCallsign(displayName: String): String? {
        val trimmed = displayName.trim()
        if (trimmed.isBlank()) return null

        val nodePattern = Regex("^NODE\\s*#?\\d+$", RegexOption.IGNORE_CASE)
        if (nodePattern.matches(trimmed)) return null

        val parenMatch = Regex("^NODE\\s*#?\\d+\\s*\\((.+)\\)$", RegexOption.IGNORE_CASE).find(trimmed)
        if (parenMatch != null) {
            val candidate = parenMatch.groupValues[1].trim()
            if (candidate.isNotBlank() && !candidate.equals("LOCAL", ignoreCase = true)) {
                return candidate
            }
            return null
        }

        return trimmed
    }
}
