package org.sih.itantra.core.mesh

import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.qos.TacticalPacketScheduler
import org.sih.itantra.core.transport.TransportManager
import org.sih.itantra.core.transport.TransportType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Derives immutable, UI-facing [MeshTopologySnapshot]s from live runtime networking state
 * or from [ManetSimulator.TopologyState] when in simulation mode.
 */
object MeshTopologyProvider {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun formatRelativeTime(timestampMs: Long, now: Long = System.currentTimeMillis()): String {
        val diffSec = (now - timestampMs) / 1000
        return when {
            diffSec < 2 -> "Just now"
            diffSec < 60 -> "${diffSec}s ago"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            else -> "${diffSec / 3600}h ago"
        }
    }

    /**
     * Build live hardware topology snapshot from production routing components.
     */
    fun buildLiveSnapshot(
        localNodeId: Int,
        neighborTable: NeighborTable,
        routeTable: RouteTable,
        transportManager: TransportManager? = null,
        qosScheduler: TacticalPacketScheduler? = null,
        dtnPendingCount: Int = 0,
        activeDistressDestinationId: Int? = null,
        selectedNodeId: Int? = null,
        activityEvents: List<TopologyPacketActivity> = emptyList(),
        batteryPct: Int? = null,
        activeTransportOverride: String? = null
    ): MeshTopologySnapshot {
        val now = System.currentTimeMillis()
        val liveNeighbors = neighborTable.liveNeighbors()
        val neighborMap = liveNeighbors.associateBy { it.nodeId }
        val allRoutes = routeTable.allEntries()

        // 1. Local Node
        val activeTransportName = if (activeTransportOverride != null) {
            activeTransportOverride
        } else if (transportManager != null) {
            val activeTransportObj = transportManager.activeTransport.value
            val isBtActive = activeTransportObj == transportManager.bluetoothTransport
            val isWifiActive = activeTransportObj == transportManager.wifiTransport
            when {
                isBtActive -> "BT"
                isWifiActive -> "WIFI"
                else -> "LOOPBACK"
            }
        } else {
            "BT"
        }

        val queueDepthStr = if (qosScheduler != null) "${qosScheduler.totalQueued()}/${qosScheduler.maxCapacity}" else "0/100"

        val localNode = TopologyNode(
            nodeId = localNodeId,
            displayName = "NODE $localNodeId (LOCAL)",
            isLocal = true,
            isReachable = true,
            lastSeen = "CURRENT",
            lastSeenMs = now,
            hopCount = 0,
            transport = activeTransportName,
            routeState = "LOCAL",
            batteryLevel = if (batteryPct != null && batteryPct > 0) "$batteryPct%" else "N/A",
            queueDepth = queueDepthStr,
            linkQuality = "1.00",
            lastPacketTime = if (activityEvents.isNotEmpty()) activityEvents.first().timeFormatted else "N/A",
            role = TopologyNodeRole.LOCAL,
            state = TopologyNodeState.LOCAL,
            isRealHardware = true
        )

        // 2. Identify relays from multi-hop routes
        val relayNodeIds = allRoutes
            .filter { it.hopCount > 1 && it.state == RouteState.VALID }
            .map { it.nextHopNodeId }
            .toSet()

        val nodesList = mutableListOf<TopologyNode>()
        nodesList.add(localNode)

        val linksList = mutableListOf<TopologyLink>()

        // 3. 1-hop Neighbors
        for (neighbor in liveNeighbors) {
            val isRelay = relayNodeIds.contains(neighbor.nodeId)
            val role = if (isRelay) TopologyNodeRole.RELAY else TopologyNodeRole.NEIGHBOR
            val node = TopologyNode(
                nodeId = neighbor.nodeId,
                displayName = "NODE ${neighbor.nodeId}",
                isLocal = false,
                isReachable = true,
                lastSeen = formatRelativeTime(neighbor.lastSeenMs, now),
                lastSeenMs = neighbor.lastSeenMs,
                hopCount = 1,
                transport = neighbor.transport,
                routeState = "DIRECT",
                batteryLevel = if (neighbor.batteryPct > 0) "${neighbor.batteryPct}%" else "N/A",
                queueDepth = "N/A",
                linkQuality = "0.95",
                lastPacketTime = "N/A",
                role = role,
                state = TopologyNodeState.ONLINE,
                isRealHardware = true
            )
            nodesList.add(node)

            linksList.add(
                TopologyLink(
                    sourceNodeId = localNodeId,
                    destinationNodeId = neighbor.nodeId,
                    transport = neighbor.transport,
                    isReachable = true,
                    lastSeenMs = neighbor.lastSeenMs,
                    hopCost = 1
                )
            )
        }

        // 4. Multi-hop Routes
        val routesList = mutableListOf<TopologyRoute>()
        val processedDestIds = mutableSetOf<Int>()

        for (route in allRoutes) {
            val destId = route.destinationNodeId
            if (destId == localNodeId) continue

            val isExpired = route.state != RouteState.VALID || now > route.expiryMs
            val stateStr = if (isExpired) "EXPIRED" else route.state.name
            val nextHopNeighbor = neighborMap[route.nextHopNodeId]
            val transportStr = nextHopNeighbor?.transport ?: "BT/WIFI"

            routesList.add(
                TopologyRoute(
                    destinationNodeId = destId,
                    nextHopNodeId = route.nextHopNodeId,
                    hopCount = route.hopCount,
                    routeFreshness = route.routeSeqNum.toLong(),
                    transport = transportStr,
                    state = stateStr,
                    linkQuality = route.linkQuality,
                    batteryPct = route.batteryPct
                )
            )

            // Add destination as a node if hopCount > 1 and not already added as direct neighbor
            if (route.hopCount > 1 && !neighborMap.containsKey(destId) && processedDestIds.add(destId)) {
                val destNode = TopologyNode(
                    nodeId = destId,
                    displayName = "NODE $destId",
                    isLocal = false,
                    isReachable = !isExpired,
                    lastSeen = if (!isExpired) "ACTIVE ROUTE" else "STALE",
                    lastSeenMs = route.expiryMs,
                    hopCount = route.hopCount,
                    transport = "MULTI-HOP",
                    routeState = stateStr,
                    batteryLevel = "${route.batteryPct}%",
                    linkQuality = String.format(Locale.US, "%.2f", route.linkQuality),
                    role = TopologyNodeRole.DESTINATION,
                    state = if (!isExpired) TopologyNodeState.ONLINE else TopologyNodeState.OFFLINE,
                    isRealHardware = false
                )
                nodesList.add(destNode)

                // Add link between nextHop and destination
                linksList.add(
                    TopologyLink(
                        sourceNodeId = route.nextHopNodeId,
                        destinationNodeId = destId,
                        transport = "MESH",
                        isReachable = !isExpired,
                        lastSeenMs = now,
                        hopCost = route.hopCount - 1,
                        isDashed = true
                    )
                )
            }
        }

        // 5. Emergency Overlay & Path
        var emergencyOverlay: String? = null
        var emergencyPath: List<Int>? = null

        if (activeDistressDestinationId != null) {
            emergencyOverlay = "DISTRESS ACTIVE"
            val route = allRoutes.firstOrNull { it.destinationNodeId == activeDistressDestinationId && it.state == RouteState.VALID }
            emergencyPath = if (route != null) {
                if (route.hopCount == 1) listOf(localNodeId, route.destinationNodeId)
                else listOf(localNodeId, route.nextHopNodeId, route.destinationNodeId)
            } else {
                listOf(localNodeId, activeDistressDestinationId)
            }
        } else if (dtnPendingCount > 0) {
            emergencyOverlay = "DTN PENDING ($dtnPendingCount)"
        }

        // 6. Highlight links on emergency path
        val finalLinks = if (emergencyPath != null && emergencyPath.size >= 2) {
            linksList.map { link ->
                val onPath = (0 until emergencyPath.size - 1).any { i ->
                    (link.sourceNodeId == emergencyPath[i] && link.destinationNodeId == emergencyPath[i + 1]) ||
                    (link.sourceNodeId == emergencyPath[i + 1] && link.destinationNodeId == emergencyPath[i])
                }
                if (onPath) link.copy(isHighlighted = true) else link
            }
        } else {
            linksList
        }

        val primary = if (transportManager?.preferredTransportType == TransportType.BLUETOOTH || transportManager == null) "Bluetooth" else "Wi-Fi"
        val fallback = if (primary == "Bluetooth") "Wi-Fi" else "Bluetooth"
        val isAuto = transportManager?.isAutoFailoverEnabled?.value ?: true
        val activeSummary = if (isAuto) "$activeTransportName (AUTO)" else activeTransportName

        val qosCongestion = qosScheduler?.getCongestionState()?.label ?: "NORMAL"
        val qosBreakdown = if (qosScheduler != null) "D ${qosScheduler.queuedDistress} | A ${qosScheduler.queuedAlert} | I ${qosScheduler.queuedImportant} | N ${qosScheduler.queuedNormal}" else "D 0 | A 0 | I 0 | N 0"

        return MeshTopologySnapshot(
            isSimulation = false,
            localNode = localNode,
            nodes = nodesList,
            links = finalLinks,
            routes = routesList,
            selectedNodeId = selectedNodeId,
            activityEvents = activityEvents,
            congestionState = qosCongestion,
            queueDepthSummary = queueDepthStr,
            queueBreakdown = qosBreakdown,
            activeTransport = activeSummary,
            primaryTransport = primary,
            fallbackTransport = fallback,
            emergencyOverlay = emergencyOverlay,
            emergencyPath = emergencyPath,
            dtnPendingCount = dtnPendingCount,
            timestamp = now
        )
    }

    /**
     * Translates [TopologyState] from [ManetSimulator] into unified [MeshTopologySnapshot].
     */
    fun buildSimulationSnapshot(
        simState: TopologyState,
        selectedNodeId: Int? = null
    ): MeshTopologySnapshot {
        val now = System.currentTimeMillis()
        val nodesList = simState.nodes.map { simNode ->
            val role = when (simNode.id) {
                ManetSimulator.NODE_A_ID -> TopologyNodeRole.LOCAL
                ManetSimulator.NODE_B_ID, ManetSimulator.NODE_D_ID -> TopologyNodeRole.RELAY
                ManetSimulator.NODE_C_ID -> TopologyNodeRole.DESTINATION
                else -> TopologyNodeRole.UNKNOWN
            }
            val state = when {
                !simNode.isOnline -> TopologyNodeState.OFFLINE
                simNode.id == ManetSimulator.NODE_A_ID -> TopologyNodeState.LOCAL
                role == TopologyNodeRole.RELAY -> TopologyNodeState.RELAY
                else -> TopologyNodeState.ONLINE
            }
            val hop = when (simNode.id) {
                ManetSimulator.NODE_A_ID -> 0
                ManetSimulator.NODE_B_ID, ManetSimulator.NODE_D_ID -> 1
                ManetSimulator.NODE_C_ID -> 2
                else -> 1
            }
            TopologyNode(
                nodeId = simNode.id,
                displayName = "${simNode.label} (${if (simNode.isReal) "REAL" else "SIM"})",
                isLocal = simNode.id == ManetSimulator.NODE_A_ID,
                isReachable = simNode.isOnline,
                lastSeen = if (simNode.isOnline) "ONLINE" else "OFFLINE / FAILED",
                lastSeenMs = now,
                hopCount = hop,
                transport = if (simNode.isReal) "BT RFCOMM" else "MULTICAST",
                routeState = if (simNode.isOnline) "VALID" else "INVALID",
                batteryLevel = if (simNode.isOnline) "90%" else "0%",
                queueDepth = if (simNode.id == ManetSimulator.NODE_A_ID) "1/100" else "0/100",
                linkQuality = if (simNode.isOnline) "0.98" else "0.00",
                role = role,
                state = state,
                isRealHardware = simNode.isReal
            )
        }

        val localNode = nodesList.firstOrNull { it.nodeId == ManetSimulator.NODE_A_ID }

        val linksList = simState.links.map { simLink ->
            TopologyLink(
                sourceNodeId = simLink.fromNodeId,
                destinationNodeId = simLink.toNodeId,
                transport = "RFCOMM / UDP",
                isReachable = simLink.isActive,
                lastSeenMs = now,
                hopCost = 1,
                isHighlighted = simLink.isHighlighted
            )
        }

        val routesList = mutableListOf<TopologyRoute>()
        if (simState.activeRoute != null && simState.activeRoute.size >= 2) {
            routesList.add(
                TopologyRoute(
                    destinationNodeId = simState.activeRoute.last(),
                    nextHopNodeId = simState.activeRoute[1],
                    hopCount = simState.activeRoute.size - 1,
                    routeFreshness = 1L,
                    transport = "BT / WIFI",
                    state = "ACTIVE",
                    linkQuality = 0.98f,
                    batteryPct = 90
                )
            )
        }

        val activityEvents = simState.events.map { event ->
            val typeStr = when (event.eventType) {
                SimEventType.HELLO -> "HELLO"
                SimEventType.RREQ -> "RREQ"
                SimEventType.RREP -> "RREP"
                SimEventType.RERR -> "RERR"
                SimEventType.DATA_FORWARD -> "RELAY"
                SimEventType.DATA_DELIVERED -> "ACK"
                SimEventType.FAILURE -> "FAIL"
                SimEventType.REPAIR -> "REPAIR"
                SimEventType.RESET -> "RESET"
            }
            TopologyPacketActivity(
                id = event.id,
                timestampMs = event.timestampMs,
                timeFormatted = timeFormat.format(Date(event.timestampMs)),
                type = typeStr,
                description = "${event.title}: ${event.description}",
                sourceId = event.packet?.sourceDeviceId,
                destId = event.packet?.destinationDeviceId,
                priorityLabel = event.packet?.priority?.name,
                rawPacket = event.packet
            )
        }

        return MeshTopologySnapshot(
            isSimulation = true,
            localNode = localNode,
            nodes = nodesList,
            links = linksList,
            routes = routesList,
            selectedNodeId = selectedNodeId,
            activityEvents = activityEvents,
            congestionState = "NORMAL",
            queueDepthSummary = "0/100",
            queueBreakdown = "D 0 | A 0 | I 0 | N 0",
            activeTransport = "SIMULATED RFCOMM/UDP",
            primaryTransport = "RFCOMM (Phone A-B)",
            fallbackTransport = "UDP Multicast (Alt Path)",
            emergencyOverlay = if (simState.statusMessage.contains("REROUTE") || simState.statusMessage.contains("FAILED")) simState.statusMessage else null,
            emergencyPath = simState.activeRoute,
            timestamp = now
        )
    }
}
