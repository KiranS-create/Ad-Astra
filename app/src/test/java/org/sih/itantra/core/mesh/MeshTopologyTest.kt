package org.sih.itantra.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.qos.CongestionState
import org.sih.itantra.core.qos.TacticalPacketScheduler

class MeshTopologyTest {

    private val localNodeId = 101
    private lateinit var neighborTable: NeighborTable
    private lateinit var routeTable: RouteTable
    private lateinit var qosScheduler: TacticalPacketScheduler

    @Before
    fun setUp() {
        neighborTable = NeighborTable(maxNeighbors = 16, expiryMs = 45_000L)
        routeTable = RouteTable(capacity = 32, routeLifetimeMs = 120_000L)
        qosScheduler = TacticalPacketScheduler(autoTransmit = false)
    }

    // =========================================================================
    // Group A: Topology Model
    // =========================================================================

    @Test
    fun testLocalNodePresentInSnapshot() {
        val snapshot = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler,
            batteryPct = 85
        )

        assertNotNull("Local node must exist", snapshot.localNode)
        assertEquals("Local node ID matches", localNodeId, snapshot.localNode!!.nodeId)
        assertTrue("isLocal must be true", snapshot.localNode!!.isLocal)
        assertEquals("Role must be LOCAL", TopologyNodeRole.LOCAL, snapshot.localNode!!.role)
        assertEquals("Hop count must be 0", 0, snapshot.localNode!!.hopCount)
        assertEquals("Battery should reflect 85%", "85%", snapshot.localNode!!.batteryLevel)
    }

    @Test
    fun testDiscoveredNeighborAppears() {
        val neighborId = 102
        neighborTable.upsert(
            NeighborEntry(
                nodeId = neighborId,
                transport = "BT",
                lastSeenMs = System.currentTimeMillis(),
                batteryPct = 92,
                seqNum = 1
            )
        )

        val snapshot = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler
        )

        val neighborNode = snapshot.nodes.find { it.nodeId == neighborId }
        assertNotNull("Discovered neighbor should be present", neighborNode)
        assertFalse("Neighbor is not local", neighborNode!!.isLocal)
        assertEquals("Role should be NEIGHBOR", TopologyNodeRole.NEIGHBOR, neighborNode.role)
        assertEquals("1 hop to direct neighbor", 1, neighborNode.hopCount)
        assertEquals("Transport should be BT", "BT", neighborNode.transport)
        assertEquals("Battery should reflect 92%", "92%", neighborNode.batteryLevel)

        val link = snapshot.links.find { it.destinationNodeId == neighborId }
        assertNotNull("Direct link must exist", link)
        assertEquals(localNodeId, link!!.sourceNodeId)
    }

    @Test
    fun testExpiredNeighborRemovedOrStale() {
        val staleNeighborId = 105
        neighborTable.upsert(
            NeighborEntry(
                nodeId = staleNeighborId,
                transport = "WIFI",
                lastSeenMs = System.currentTimeMillis() - 60_000L, // Expired (> 45s)
                batteryPct = 50,
                seqNum = 1
            )
        )

        val snapshot = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler
        )

        val found = snapshot.nodes.find { it.nodeId == staleNeighborId }
        assertEquals("Stale neighbor should not be in live snapshot nodes", null, found)
    }

    @Test
    fun testRouteSnapshotMapsCorrectly() {
        val relayId = 102
        val destId = 103

        // Neighbor 102
        neighborTable.upsert(
            NeighborEntry(
                nodeId = relayId,
                transport = "BT",
                lastSeenMs = System.currentTimeMillis(),
                batteryPct = 90,
                seqNum = 1
            )
        )

        // Route to 103 via 102 (2 hops)
        routeTable.addOrUpdate(
            RouteEntry(
                destinationNodeId = destId,
                nextHopNodeId = relayId,
                hopCount = 2,
                routeSeqNum = 5,
                expiryMs = System.currentTimeMillis() + 60_000L,
                state = RouteState.VALID,
                linkQuality = 0.95f,
                batteryPct = 88
            )
        )

        val snapshot = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler
        )

        val relayNode = snapshot.nodes.find { it.nodeId == relayId }
        assertNotNull("Relay node exists", relayNode)
        assertEquals("Neighbor acting as next-hop must be designated RELAY", TopologyNodeRole.RELAY, relayNode!!.role)

        val destNode = snapshot.nodes.find { it.nodeId == destId }
        assertNotNull("Destination node exists in snapshot", destNode)
        assertEquals("Role must be DESTINATION", TopologyNodeRole.DESTINATION, destNode!!.role)
        assertEquals("Hop count must be 2", 2, destNode.hopCount)
        assertTrue("Destination is reachable", destNode.isReachable)
    }

    // =========================================================================
    // Group B: Route Visualization
    // =========================================================================

    @Test
    fun testNextHopAndHopCountAccuracy() {
        routeTable.addOrUpdate(
            RouteEntry(
                destinationNodeId = 103,
                nextHopNodeId = 102,
                hopCount = 3,
                routeSeqNum = 12,
                expiryMs = System.currentTimeMillis() + 60_000L,
                state = RouteState.VALID,
                linkQuality = 0.85f,
                batteryPct = 75
            )
        )

        val snapshot = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler
        )

        val route = snapshot.routes.find { it.destinationNodeId == 103 }
        assertNotNull("Route to 103 must be in snapshot routes", route)
        assertEquals("Next hop must be 102", 102, route!!.nextHopNodeId)
        assertEquals("Hop count must be 3", 3, route.hopCount)
        assertEquals("Route sequence should match", 12L, route.routeFreshness)
    }

    @Test
    fun testTransportStateMapping() {
        val neighborId = 104
        neighborTable.upsert(
            NeighborEntry(
                nodeId = neighborId,
                transport = "WIFI",
                lastSeenMs = System.currentTimeMillis(),
                batteryPct = 80,
                seqNum = 1
            )
        )

        val snapshot = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler
        )

        val neighborNode = snapshot.nodes.find { it.nodeId == neighborId }
        assertEquals("WIFI transport must be mapped", "WIFI", neighborNode?.transport)

        val link = snapshot.links.find { it.destinationNodeId == neighborId }
        assertEquals("Link transport must match", "WIFI", link?.transport)
    }

    @Test
    fun testRouteUpdateRefreshesTopology() {
        val snap1 = MeshTopologyProvider.buildLiveSnapshot(localNodeId, neighborTable, routeTable, qosScheduler = qosScheduler)
        assertEquals("Initially 0 routes", 0, snap1.routes.size)

        routeTable.addOrUpdate(
            RouteEntry(
                destinationNodeId = 200,
                nextHopNodeId = 102,
                hopCount = 2,
                routeSeqNum = 1,
                expiryMs = System.currentTimeMillis() + 60_000L,
                state = RouteState.VALID
            )
        )

        val snap2 = MeshTopologyProvider.buildLiveSnapshot(localNodeId, neighborTable, routeTable, qosScheduler = qosScheduler)
        assertEquals("After route discovery, 1 route present", 1, snap2.routes.size)
        assertEquals(200, snap2.routes[0].destinationNodeId)
    }

    // =========================================================================
    // Group C: Transport Failover
    // =========================================================================

    @Test
    fun testTransportFailoverAppearsInTopology() {
        val snapBt = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler,
            activeTransportOverride = "BT"
        )
        assertEquals("BT", snapBt.localNode?.transport)

        val snapWifi = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler,
            activeTransportOverride = "WIFI"
        )
        assertEquals("WIFI", snapWifi.localNode?.transport)
    }

    // =========================================================================
    // Group D: QoS & Congestion
    // =========================================================================

    @Test
    fun testQosCongestionStateReflected() {
        // Normal queue (< 25)
        val snapNormal = MeshTopologyProvider.buildLiveSnapshot(localNodeId, neighborTable, routeTable, qosScheduler = qosScheduler)
        assertEquals("NORMAL", snapNormal.congestionState)

        // Fill queue to BUSY (>= 25)
        for (i in 1..26) {
            qosScheduler.enqueue(createMockPacket(seq = i.toShort(), priority = MessagePriority.NORMAL))
        }
        val snapBusy = MeshTopologyProvider.buildLiveSnapshot(localNodeId, neighborTable, routeTable, qosScheduler = qosScheduler)
        assertEquals("BUSY", snapBusy.congestionState)
    }

    @Test
    fun testQueueBreakdownCountsAccurate() {
        qosScheduler.enqueue(createMockPacket(seq = 1, priority = MessagePriority.DISTRESS))
        qosScheduler.enqueue(createMockPacket(seq = 2, priority = MessagePriority.ALERT))
        qosScheduler.enqueue(createMockPacket(seq = 3, priority = MessagePriority.IMPORTANT))
        qosScheduler.enqueue(createMockPacket(seq = 4, priority = MessagePriority.NORMAL))

        val snap = MeshTopologyProvider.buildLiveSnapshot(localNodeId, neighborTable, routeTable, qosScheduler = qosScheduler)
        assertEquals("4/100", snap.queueDepthSummary)
        assertTrue("Breakdown should contain D 1", snap.queueBreakdown.contains("D 1"))
        assertTrue("Breakdown should contain A 1", snap.queueBreakdown.contains("A 1"))
        assertTrue("Breakdown should contain I 1", snap.queueBreakdown.contains("I 1"))
        assertTrue("Breakdown should contain N 1", snap.queueBreakdown.contains("N 1"))
    }

    // =========================================================================
    // Group E: Emergency Overlay
    // =========================================================================

    @Test
    fun testDistressActiveSurfacesInOverlay() {
        val destId = 300
        val relayId = 102
        routeTable.addOrUpdate(
            RouteEntry(
                destinationNodeId = destId,
                nextHopNodeId = relayId,
                hopCount = 2,
                routeSeqNum = 1,
                expiryMs = System.currentTimeMillis() + 60_000L,
                state = RouteState.VALID
            )
        )

        val snap = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler,
            activeDistressDestinationId = destId
        )

        assertEquals("DISTRESS ACTIVE", snap.emergencyOverlay)
        assertNotNull("Emergency path must be calculated", snap.emergencyPath)
        assertEquals(listOf(localNodeId, relayId, destId), snap.emergencyPath)
    }

    @Test
    fun testDtnPendingStateSurfaces() {
        val snap = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler,
            dtnPendingCount = 4
        )

        assertEquals("DTN PENDING (4)", snap.emergencyOverlay)
        assertEquals(4, snap.dtnPendingCount)
    }

    @Test
    fun testActivityFeedLogsAndSurfaces() {
        val events = listOf(
            TopologyPacketActivity(
                id = 1L,
                timestampMs = System.currentTimeMillis(),
                timeFormatted = "12:00:01",
                type = "TX",
                description = "OUTBOUND PACKET"
            ),
            TopologyPacketActivity(
                id = 2L,
                timestampMs = System.currentTimeMillis(),
                timeFormatted = "12:00:02",
                type = "ACK",
                description = "DELIVERY CONFIRMED"
            )
        )

        val snap = MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = localNodeId,
            neighborTable = neighborTable,
            routeTable = routeTable,
            qosScheduler = qosScheduler,
            activityEvents = events
        )

        assertEquals(2, snap.activityEvents.size)
        assertEquals("TX", snap.activityEvents[0].type)
        assertEquals("ACK", snap.activityEvents[1].type)
    }

    // =========================================================================
    // Group F: Simulation Mode Translation
    // =========================================================================

    @Test
    fun testSimulationModeTranslatesExistingAtoBtoC() {
        val sim = ManetSimulator()
        sim.startDiscovery()
        sim.sendPacketAtoC("Test transmission")

        val snap = MeshTopologyProvider.buildSimulationSnapshot(sim.topologyState.value)
        assertTrue("Must be marked as simulation", snap.isSimulation)
        assertEquals("Should have 4 nodes (A, B, C, D)", 4, snap.nodes.size)
        assertNotNull("Local node is Node A", snap.localNode)
        assertEquals(ManetSimulator.NODE_A_ID, snap.localNode?.nodeId)

        val destNode = snap.nodes.find { it.nodeId == ManetSimulator.NODE_C_ID }
        assertNotNull("Node C destination exists", destNode)
        assertEquals(TopologyNodeRole.DESTINATION, destNode?.role)

        assertEquals("Active route should be A -> B -> C", listOf(101, 102, 103), snap.emergencyPath)
    }

    @Test
    fun testSimulationRouteRepairTranslates() {
        val sim = ManetSimulator()
        sim.startDiscovery()
        sim.sendPacketAtoC("Packet 1")
        sim.failNodeB()
        sim.triggerFailureAndRediscovery()

        val snap = MeshTopologyProvider.buildSimulationSnapshot(sim.topologyState.value)
        assertTrue("Must be marked simulation", snap.isSimulation)

        val nodeB = snap.nodes.find { it.nodeId == ManetSimulator.NODE_B_ID }
        assertNotNull("Node B exists", nodeB)
        assertEquals("Node B should be marked OFFLINE after failure", TopologyNodeState.OFFLINE, nodeB?.state)
        assertFalse("Node B is not reachable", nodeB!!.isReachable)

        // Rerouted path via Node D (104)
        assertEquals("Active route should be rerouted via D: A -> D -> C", listOf(101, 104, 103), snap.emergencyPath)
    }

    private fun createMockPacket(seq: Short, priority: MessagePriority): Packet {
        return Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = priority,
            flags = 0,
            sequenceNumber = seq,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = localNodeId,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = org.sih.itantra.core.common.IndicLanguage.HINDI,
            payload = byteArrayOf(1, 2, 3)
        )
    }
}
