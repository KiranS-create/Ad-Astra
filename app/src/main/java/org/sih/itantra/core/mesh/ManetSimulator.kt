package org.sih.itantra.core.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import java.util.concurrent.atomic.AtomicLong

enum class SimEventType {
    HELLO,
    RREQ,
    RREP,
    RERR,
    DATA_FORWARD,
    DATA_DELIVERED,
    FAILURE,
    REPAIR,
    RESET
}

data class SimNode(
    val id: Int,
    val label: String,
    val hardwareLabel: String,
    val isReal: Boolean,
    val isOnline: Boolean,
    val neighborCount: Int = 0,
    val routeCount: Int = 0
)

data class SimLink(
    val fromNodeId: Int,
    val toNodeId: Int,
    val isActive: Boolean,
    val isHighlighted: Boolean = false
)

data class SimEvent(
    val id: Long,
    val timestampMs: Long,
    val title: String,
    val description: String,
    val packet: Packet? = null,
    val eventType: SimEventType
)

data class TopologyState(
    val nodes: List<SimNode> = emptyList(),
    val links: List<SimLink> = emptyList(),
    val activeRoute: List<Int>? = null,
    val events: List<SimEvent> = emptyList(),
    val selectedPacket: Packet? = null,
    val liveHardwareCount: Int = 2,
    val simulatedNodeCount: Int = 2,
    val statusMessage: String = "IDLE · READY TO SIMULATE"
)

/**
 * Deterministic MANET Simulator using the actual production routing components:
 * - NeighborTable
 * - RouteTable
 * - PacketRelayRouter
 * - RreqDupCache
 * - Packet wire layout & PacketSerializer (CRC32)
 *
 * Topology:
 *        [ Node B: Real Phone B / Relay ]
 *       /                                \
 * [ Node A: Real Phone A ]              [ Node C: Simulated Dest ]
 *       \                                /
 *        [ Node D: Simulated Alt Relay  ]
 */
class ManetSimulator {

    companion object {
        const val NODE_A_ID = 101 // Real Phone A
        const val NODE_B_ID = 102 // Real Phone B / Relay
        const val NODE_C_ID = 103 // Simulated Destination
        const val NODE_D_ID = 104 // Simulated Alternate Relay
    }

    private class NodeInstance(
        val id: Int,
        val label: String,
        val hardwareLabel: String,
        val isReal: Boolean,
        var isOnline: Boolean = true
    ) {
        val neighborTable = NeighborTable(maxNeighbors = 16, expiryMs = 60_000L)
        val routeTable = RouteTable(capacity = 32, routeLifetimeMs = 120_000L)
        val relayRouter = PacketRelayRouter(id).apply { setRelayEnabled(true) }
        val dupCache = RreqDupCache(capacity = 32)
        var seqCounter: Short = 1
    }

    private val nodesMap = linkedMapOf(
        NODE_A_ID to NodeInstance(NODE_A_ID, "NODE A", "REAL: Phone A (Galaxy A55)", isReal = true),
        NODE_B_ID to NodeInstance(NODE_B_ID, "NODE B", "REAL: Phone B (Galaxy Note 10 Lite)", isReal = true),
        NODE_C_ID to NodeInstance(NODE_C_ID, "NODE C", "SIMULATED: Destination Node", isReal = false),
        NODE_D_ID to NodeInstance(NODE_D_ID, "NODE D", "SIMULATED: Alternate Relay Node", isReal = false)
    )

    // Ad-hoc links: A-B, B-C, A-D, D-C
    private val staticLinks = listOf(
        Pair(NODE_A_ID, NODE_B_ID),
        Pair(NODE_B_ID, NODE_C_ID),
        Pair(NODE_A_ID, NODE_D_ID),
        Pair(NODE_D_ID, NODE_C_ID)
    )

    private val eventIdCounter = AtomicLong(1L)
    private val _events = mutableListOf<SimEvent>()
    private var _selectedPacket: Packet? = null
    private var _activeRoute: List<Int>? = null
    private var _statusMessage: String = "IDLE · READY TO SIMULATE"

    private val _topologyState = MutableStateFlow(buildTopologyState())
    val topologyState: StateFlow<TopologyState> = _topologyState.asStateFlow()

    init {
        publishState()
    }

    // -------------------------------------------------------------------------
    // Deterministic Actions
    // -------------------------------------------------------------------------

    /**
     * Step 1: Broadcast HELLO beacons across all active links and build neighbor tables.
     */
    fun startDiscovery() {
        logEvent(
            eventType = SimEventType.HELLO,
            title = "NEIGHBOR DISCOVERY STARTED",
            description = "Broadcasting periodic HELLO beacons (15s interval) across RFCOMM / Multicast links."
        )

        for ((nodeId, node) in nodesMap) {
            if (!node.isOnline) continue

            val helloPayload = HelloPayload(
                nodeId = nodeId,
                seqNum = node.seqCounter++,
                batteryPct = 90
            )

            val rawBytes = PacketSerializer.serialize(
                Packet(
                    msgType = Packet.TYPE_HELLO,
                    priority = MessagePriority.NORMAL,
                    ttl = 1,
                    sequenceNumber = node.seqCounter,
                    timestamp = System.currentTimeMillis(),
                    sourceDeviceId = nodeId,
                    destinationDeviceId = Packet.BROADCAST_ID,
                    language = IndicLanguage.ENGLISH,
                    payload = helloPayload.serialize()
                )
            )

            val helloPacket = PacketSerializer.deserialize(rawBytes)

            // Deliver to online direct neighbors
            val neighbors = getDirectNeighbors(nodeId)
            for (neighborId in neighbors) {
                val neighborNode = nodesMap[neighborId] ?: continue
                if (!neighborNode.isOnline) continue

                neighborNode.neighborTable.upsert(
                    NeighborEntry(
                        nodeId = nodeId,
                        transport = if (node.isReal && neighborNode.isReal) "BT_RFCOMM" else "VIRTUAL_UDP",
                        lastSeenMs = System.currentTimeMillis(),
                        batteryPct = 90,
                        seqNum = helloPayload.seqNum.toInt()
                    )
                )
            }
        }

        _statusMessage = "NEIGHBORS DISCOVERED · AD-HOC TOPOLOGY SYNCHRONIZED"
        logEvent(
            eventType = SimEventType.HELLO,
            title = "DISCOVERY COMPLETE",
            description = "All online nodes updated neighbor registries. Links active."
        )
        publishState()
    }

    /**
     * Step 2: Send test packet from Node A to Node C.
     * Triggers route discovery if no route exists, establishes A -> B -> C, then delivers DATA.
     */
    fun sendPacketAtoC(testMessage: String = "iTantra Voice Packet (Compressed INT8)") {
        val nodeA = nodesMap[NODE_A_ID] ?: return
        val nodeC = nodesMap[NODE_C_ID] ?: return

        if (!nodeA.isOnline) {
            logEvent(SimEventType.FAILURE, "TRANSMIT FAILED", "Node A is offline.")
            return
        }

        // 1. Route Check on Node A
        val existingRoute = nodeA.routeTable.lookup(NODE_C_ID)
        if (existingRoute == null || existingRoute.state != RouteState.VALID) {
            logEvent(
                eventType = SimEventType.RREQ,
                title = "ROUTE CACHE MISS (A -> C)",
                description = "No valid route from Node A to Node C. Initiating AODV Route Discovery."
            )
            val routeFound = performRouteDiscovery(originId = NODE_A_ID, destId = NODE_C_ID)
            if (!routeFound) {
                _statusMessage = "DISCOVERY FAILED: DESTINATION UNREACHABLE"
                logEvent(
                    eventType = SimEventType.RERR,
                    title = "DISCOVERY FAILED",
                    description = "Destination Node C unreachable. All candidate relay paths are down."
                )
                _activeRoute = null
                publishState()
                return
            }
        }

        // 2. Data Packet Forwarding
        deliverDataPacket(originId = NODE_A_ID, destId = NODE_C_ID, message = testMessage)
    }

    /**
     * Simulate route discovery from origin to destination.
     * Tries preferred relay B first (if online); falls back to alternate D (if online).
     */
    private fun performRouteDiscovery(originId: Int, destId: Int): Boolean {
        val originNode = nodesMap[originId] ?: return false
        val destNode = nodesMap[destId] ?: return false

        val reqId = originNode.seqCounter++
        val rreq = RreqPayload(
            originNodeId = originId,
            destNodeId = destId,
            originSeqNum = originNode.seqCounter,
            reqId = reqId,
            hopCount = 0,
            ttl = Packet.DEFAULT_TTL
        )

        val rreqPkt = Packet(
            msgType = Packet.TYPE_ROUTE_REQUEST,
            priority = MessagePriority.NORMAL,
            ttl = Packet.DEFAULT_TTL,
            sequenceNumber = reqId,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = originId,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = rreq.serialize()
        )
        val serializedRreq = PacketSerializer.deserialize(PacketSerializer.serialize(rreqPkt))

        logEvent(
            eventType = SimEventType.RREQ,
            title = "RREQ BROADCAST: Node A",
            description = "Origin 101 requests route to 103 (reqId=$reqId, TTL=3)",
            packet = serializedRreq
        )

        // Find which relays are available
        // Priority: Node B (primary physical relay) -> Node D (alternate simulated relay)
        val relayCandidate = when {
            nodesMap[NODE_B_ID]?.isOnline == true -> nodesMap[NODE_B_ID]
            nodesMap[NODE_D_ID]?.isOnline == true -> nodesMap[NODE_D_ID]
            else -> null
        }

        if (relayCandidate == null) {
            return false
        }

        val relayId = relayCandidate.id

        // Relay receives RREQ: records reverse route to origin A
        relayCandidate.routeTable.addOrUpdate(
            RouteEntry(
                destinationNodeId = originId,
                nextHopNodeId = originId,
                hopCount = 1,
                routeSeqNum = rreq.originSeqNum.toInt(),
                expiryMs = System.currentTimeMillis() + 120_000L
            )
        )

        logEvent(
            eventType = SimEventType.RREQ,
            title = "RREQ FORWARDED via ${relayCandidate.label}",
            description = "${relayCandidate.label} recorded reverse route to A. Forwarding RREQ to C (hop=1, TTL=2).",
            packet = serializedRreq.copy(ttl = 2)
        )

        // Dest C receives RREQ: records reverse route to A via relay
        destNode.routeTable.addOrUpdate(
            RouteEntry(
                destinationNodeId = originId,
                nextHopNodeId = relayId,
                hopCount = 2,
                routeSeqNum = rreq.originSeqNum.toInt(),
                expiryMs = System.currentTimeMillis() + 120_000L
            )
        )

        // Dest C generates RREP back to relay
        val rrep = RrepPayload(
            originNodeId = originId,
            destNodeId = destId,
            destSeqNum = destNode.seqCounter++,
            hopCount = 0
        )
        val rrepPkt = Packet(
            msgType = Packet.TYPE_ROUTE_REPLY,
            priority = MessagePriority.NORMAL,
            ttl = Packet.DEFAULT_TTL,
            sequenceNumber = destNode.seqCounter,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = destId,
            destinationDeviceId = relayId,
            language = IndicLanguage.ENGLISH,
            payload = rrep.serialize()
        )
        val serializedRrep = PacketSerializer.deserialize(PacketSerializer.serialize(rrepPkt))

        logEvent(
            eventType = SimEventType.RREP,
            title = "RREP UNICAST: Node C -> ${relayCandidate.label}",
            description = "Destination C replies with RREP (destSeq=${rrep.destSeqNum}, hop=1)",
            packet = serializedRrep
        )

        // Relay receives RREP: records forward route to C
        relayCandidate.routeTable.addOrUpdate(
            RouteEntry(
                destinationNodeId = destId,
                nextHopNodeId = destId,
                hopCount = 1,
                routeSeqNum = rrep.destSeqNum.toInt(),
                expiryMs = System.currentTimeMillis() + 120_000L
            )
        )

        // Relay forwards RREP to Origin A
        logEvent(
            eventType = SimEventType.RREP,
            title = "RREP FORWARDED: ${relayCandidate.label} -> Node A",
            description = "${relayCandidate.label} recorded route to C. Forwarding RREP to origin A.",
            packet = serializedRrep.copy(sourceDeviceId = relayId, destinationDeviceId = originId)
        )

        // Origin A receives RREP: records route to C via relay
        originNode.routeTable.addOrUpdate(
            RouteEntry(
                destinationNodeId = destId,
                nextHopNodeId = relayId,
                hopCount = 2,
                routeSeqNum = rrep.destSeqNum.toInt(),
                expiryMs = System.currentTimeMillis() + 120_000L
            )
        )

        _activeRoute = listOf(originId, relayId, destId)
        logEvent(
            eventType = SimEventType.RREP,
            title = "ROUTE ESTABLISHED: Node A -> ${relayCandidate.label} -> Node C",
            description = "Path confirmed (2 hops). Forward next-hop: ${relayCandidate.label}. Ready for payload transmission."
        )

        return true
    }

    /**
     * Delivers DATA packet along the established route.
     * Decrements TTL at each hop (3 -> 2 -> 1) and passes through PacketRelayRouter.
     */
    private fun deliverDataPacket(originId: Int, destId: Int, message: String) {
        val originNode = nodesMap[originId] ?: return
        val route = originNode.routeTable.lookup(destId) ?: return
        val nextHopId = route.nextHopNodeId
        val relayNode = nodesMap[nextHopId] ?: return
        val destNode = nodesMap[destId] ?: return

        // 1. Origin sends DATA with TTL=3
        val initialDataPacket = Packet(
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.NORMAL,
            ttl = Packet.DEFAULT_TTL, // 3
            sequenceNumber = originNode.seqCounter++,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = originId,
            destinationDeviceId = destId,
            language = IndicLanguage.HINDI,
            payload = message.toByteArray()
        )
        val dataPktHop0 = PacketSerializer.deserialize(PacketSerializer.serialize(initialDataPacket))

        logEvent(
            eventType = SimEventType.DATA_FORWARD,
            title = "DATA TX: Node A (TTL=3)",
            description = "A transmits payload (${dataPktHop0.payload.size} bytes) via next-hop ${relayNode.label}.",
            packet = dataPktHop0
        )

        // Check if nextHop is online
        if (!relayNode.isOnline) {
            handleRelayFailure(originId, destId, nextHopId)
            return
        }

        // 2. Relay evaluates packet using real PacketRelayRouter
        val relayAction = relayNode.relayRouter.evaluatePacket(dataPktHop0)
        when (relayAction) {
            is RelayAction.ForwardAndDeliver -> {
                val forwardedPkt = relayAction.forwardedPacket // TTL=2, FLAG_FORWARDED
                logEvent(
                    eventType = SimEventType.DATA_FORWARD,
                    title = "DATA RELAYED via ${relayNode.label} (TTL=2)",
                    description = "${relayNode.label} decremented TTL (3 -> 2). Forwarding to destination Node C.",
                    packet = forwardedPkt
                )

                // 3. Destination evaluates packet
                val destAction = destNode.relayRouter.evaluatePacket(forwardedPkt)
                val isFinalDest = (forwardedPkt.destinationDeviceId == destNode.id)
                if (destAction is RelayAction.DeliverLocalOnly || isFinalDest) {
                    _statusMessage = "PACKET DELIVERED (A -> ${relayNode.label} -> C) · HOPS: 2 · TTL: 1"
                    logEvent(
                        eventType = SimEventType.DATA_DELIVERED,
                        title = "DATA RECEIVED: Node C (TTL=1)",
                        description = "Node C received payload successfully. End-to-end multi-hop delivery complete!",
                        packet = forwardedPkt.copy(ttl = 1)
                    )
                }
            }
            else -> {
                logEvent(
                    eventType = SimEventType.FAILURE,
                    title = "RELAY DROPPED PACKET",
                    description = "Relay action was $relayAction"
                )
            }
        }

        publishState()
    }

    /**
     * Simulate Node B failure (taking it offline).
     */
    fun failNodeB() {
        val nodeB = nodesMap[NODE_B_ID] ?: return
        nodeB.isOnline = false
        _statusMessage = "NODE B WENT OFFLINE (SIMULATED LINK FAILURE)"
        logEvent(
            eventType = SimEventType.FAILURE,
            title = "SIMULATED FAILURE: Node B OFFLINE",
            description = "Node B radio disabled. Links A-B and B-C broken. Simulating physical relay disconnection."
        )
        publishState()
    }

    /**
     * Trigger failure detection and RERR propagation when Node B fails.
     * Automatically initiates rediscovery via Node D.
     */
    fun triggerFailureAndRediscovery() {
        val nodeA = nodesMap[NODE_A_ID] ?: return

        // Invalidate previous route through B
        nodeA.routeTable.invalidate(NODE_C_ID)
        _activeRoute = null

        val rerr = RerrPayload(brokenDestNodeId = NODE_C_ID, affectedSrcNodeId = NODE_A_ID)
        val rerrPkt = Packet(
            msgType = Packet.TYPE_ROUTE_ERROR,
            priority = MessagePriority.IMPORTANT,
            ttl = Packet.DEFAULT_TTL,
            sequenceNumber = nodeA.seqCounter++,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = NODE_A_ID,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = rerr.serialize()
        )
        val serializedRerr = PacketSerializer.deserialize(PacketSerializer.serialize(rerrPkt))

        logEvent(
            eventType = SimEventType.RERR,
            title = "RERR GENERATED: Route A->B->C Broken",
            description = "Next-hop B unreachable. Route invalidated in Node A RouteTable. Triggering alternate path discovery.",
            packet = serializedRerr
        )

        // Rediscover via alternate Node D
        val rediscoverySuccess = performRouteDiscovery(originId = NODE_A_ID, destId = NODE_C_ID)
        if (rediscoverySuccess) {
            _statusMessage = "ROUTE RECOVERED: A -> D -> C"
            deliverDataPacket(originId = NODE_A_ID, destId = NODE_C_ID, message = "Rerouted Voice Payload (via Node D)")
        } else {
            _statusMessage = "REDISCOVERY FAILED · ALL RELAYS DOWN"
            publishState()
        }
    }

    private fun handleRelayFailure(originId: Int, destId: Int, failedRelayId: Int) {
        val originNode = nodesMap[originId] ?: return
        originNode.routeTable.invalidate(destId)
        _activeRoute = null

        val failedLabel = nodesMap[failedRelayId]?.label ?: "Relay"
        logEvent(
            eventType = SimEventType.RERR,
            title = "NEXT HOP UNREACHABLE ($failedLabel)",
            description = "Socket transfer to $failedLabel failed. Route to $destId invalidated. Initiating alternate path rediscovery."
        )
        triggerFailureAndRediscovery()
    }

    /**
     * Repair Node B (bringing it back online).
     */
    fun repairNodeB() {
        val nodeB = nodesMap[NODE_B_ID] ?: return
        nodeB.isOnline = true
        _statusMessage = "NODE B REPAIRED · REJOINED MESH"
        logEvent(
            eventType = SimEventType.REPAIR,
            title = "NODE B RESTORED",
            description = "Node B radio re-enabled. Ready to participate in neighbor discovery and relay."
        )
        publishState()
    }

    /**
     * Set specific node online or offline state.
     */
    fun setNodeOnline(nodeId: Int, online: Boolean) {
        val node = nodesMap[nodeId] ?: return
        node.isOnline = online
        if (!online) {
            _activeRoute = null
        }
        publishState()
    }

    /**
     * Reset topology to clean initial state.
     */
    fun resetTopology() {
        for ((_, node) in nodesMap) {
            node.isOnline = true
            node.neighborTable.clear()
            node.routeTable.allEntries().forEach { node.routeTable.invalidate(it.destinationNodeId) }
        }
        _events.clear()
        _selectedPacket = null
        _activeRoute = null
        _statusMessage = "TOPOLOGY RESET · INITIAL STATE"
        logEvent(
            eventType = SimEventType.RESET,
            title = "TOPOLOGY RESET",
            description = "All routing and neighbor tables cleared. All 4 nodes initialized online."
        )
        publishState()
    }

    fun selectPacket(packet: Packet?) {
        _selectedPacket = packet
        publishState()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun getDirectNeighbors(nodeId: Int): List<Int> {
        return staticLinks.mapNotNull { (from, to) ->
            when (nodeId) {
                from -> to
                to -> from
                else -> null
            }
        }
    }

    private fun logEvent(
        eventType: SimEventType,
        title: String,
        description: String,
        packet: Packet? = null
    ) {
        val event = SimEvent(
            id = eventIdCounter.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            title = title,
            description = description,
            packet = packet,
            eventType = eventType
        )
        _events.add(0, event) // latest first
        if (packet != null && _selectedPacket == null) {
            _selectedPacket = packet
        }
    }

    private fun publishState() {
        _topologyState.value = buildTopologyState()
    }

    private fun buildTopologyState(): TopologyState {
        val simNodes = nodesMap.values.map { node ->
            SimNode(
                id = node.id,
                label = node.label,
                hardwareLabel = node.hardwareLabel,
                isReal = node.isReal,
                isOnline = node.isOnline,
                neighborCount = node.neighborTable.liveCount(),
                routeCount = node.routeTable.allEntries().count { it.state == RouteState.VALID }
            )
        }

        val simLinks = staticLinks.map { (fromId, toId) ->
            val fromOnline = nodesMap[fromId]?.isOnline == true
            val toOnline = nodesMap[toId]?.isOnline == true
            val active = fromOnline && toOnline

            val isHighlighted = if (_activeRoute != null) {
                val r = _activeRoute!!
                (r.indexOf(fromId) != -1 && r.indexOf(toId) != -1 &&
                        Math.abs(r.indexOf(fromId) - r.indexOf(toId)) == 1)
            } else false

            SimLink(
                fromNodeId = fromId,
                toNodeId = toId,
                isActive = active,
                isHighlighted = isHighlighted
            )
        }

        return TopologyState(
            nodes = simNodes,
            links = simLinks,
            activeRoute = _activeRoute,
            events = _events.toList(),
            selectedPacket = _selectedPacket,
            liveHardwareCount = 2,
            simulatedNodeCount = 2,
            statusMessage = _statusMessage
        )
    }
}
