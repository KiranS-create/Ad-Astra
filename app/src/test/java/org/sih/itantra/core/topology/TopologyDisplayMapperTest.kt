package org.sih.itantra.core.topology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyLink
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.mesh.TopologyNodeRole
import org.sih.itantra.core.mesh.TopologyNodeState
import org.sih.itantra.core.mesh.TopologyRoute

/**
 * Deterministic unit tests for Feature 8: Live Mesh Topology Screen.
 *
 * Covers all 17 required test specifications:
 * 1. local node mapping
 * 2. peer mapping
 * 3. node deduplication
 * 4. direct link mapping
 * 5. multi-hop mapping
 * 6. DTN mapping
 * 7. unknown telemetry
 * 8. missing callsign
 * 9. missing RSSI
 * 10. missing hop count
 * 11. route-state mapping
 * 12. deterministic layout
 * 13. node selection
 * 14. empty topology
 * 15. topology updates
 * 16. malformed/incomplete topology
 * 17. no fabricated nodes
 */
class TopologyDisplayMapperTest {

    private fun createLocalNode(nodeId: Int = 209070): TopologyNode {
        return TopologyNode(
            nodeId = nodeId,
            displayName = "NODE $nodeId (LOCAL)",
            isLocal = true,
            isReachable = true,
            lastSeen = "CURRENT",
            lastSeenMs = System.currentTimeMillis(),
            hopCount = 0,
            transport = "BT",
            routeState = "LOCAL",
            role = TopologyNodeRole.LOCAL,
            state = TopologyNodeState.LOCAL,
            isRealHardware = true
        )
    }

    private fun createDirectPeer(nodeId: Int = 101, transport: String = "BT"): TopologyNode {
        return TopologyNode(
            nodeId = nodeId,
            displayName = "NODE #$nodeId",
            isLocal = false,
            isReachable = true,
            lastSeen = "12s ago",
            lastSeenMs = System.currentTimeMillis() - 12000L,
            hopCount = 1,
            transport = transport,
            routeState = "DIRECT",
            batteryLevel = "85%",
            role = TopologyNodeRole.NEIGHBOR,
            state = TopologyNodeState.ONLINE,
            isRealHardware = true
        )
    }

    private fun createRelayPeer(nodeId: Int = 305): TopologyNode {
        return TopologyNode(
            nodeId = nodeId,
            displayName = "NODE #$nodeId",
            isLocal = false,
            isReachable = true,
            lastSeen = "ACTIVE ROUTE",
            lastSeenMs = System.currentTimeMillis(),
            hopCount = 2,
            transport = "MULTI-HOP",
            routeState = "VALID",
            batteryLevel = "70%",
            role = TopologyNodeRole.DESTINATION,
            state = TopologyNodeState.ONLINE,
            isRealHardware = false
        )
    }

    // =========================================================================
    // 1. Local Node Mapping
    // =========================================================================
    @Test
    fun testLocalNodeMapping() {
        val local = createLocalNode(209070)
        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local),
            activeTransport = "BT / WIFI (AUTO)"
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        assertNotNull(result.localNode)
        val mappedLocal = result.localNode!!
        assertEquals(209070, mappedLocal.nodeId)
        assertTrue(mappedLocal.isLocal)
        assertEquals(0, mappedLocal.tier)
        assertEquals(0f, mappedLocal.normalizedRadius, 0.001f)
        assertEquals(0f, mappedLocal.angleRad, 0.001f)
        assertEquals(0, mappedLocal.hopCount)
        assertEquals("0", mappedLocal.effectiveHops)
        assertEquals("LOCAL", mappedLocal.status)
        assertEquals("LOCAL RADIO (HQ)", mappedLocal.routeSummary)
        assertNull(mappedLocal.rssi)
        assertEquals("UNKNOWN", mappedLocal.effectiveRssi)
    }

    // =========================================================================
    // 2. Peer Mapping
    // =========================================================================
    @Test
    fun testPeerMapping() {
        val local = createLocalNode(209070)
        val peer = createDirectPeer(101, transport = "BT")
        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, peer),
            links = listOf(
                TopologyLink(sourceNodeId = 209070, destinationNodeId = 101, transport = "BT", isReachable = true, lastSeenMs = 0L, hopCost = 1)
            )
        )

        val result = TopologyDisplayMapper.mapSnapshot(
            snapshot = snapshot,
            callsignProvider = { if (it == 101) "SQUAD BRAVO" else null },
            rssiProvider = { if (it == 101) -64 else null }
        )

        assertEquals(1, result.nodes.size)
        val mappedPeer = result.nodes[0]
        assertEquals(101, mappedPeer.nodeId)
        assertEquals("SQUAD BRAVO", mappedPeer.callsign)
        assertEquals("SQUAD BRAVO (#101)", mappedPeer.displayLabel)
        assertEquals(1, mappedPeer.hopCount)
        assertEquals("DIRECT (1 HOP)", mappedPeer.routeSummary)
        assertEquals(1, mappedPeer.tier)
        assertEquals("BT", mappedPeer.effectiveTransport)
        assertEquals(-64, mappedPeer.rssi)
        assertEquals("-64 dBm", mappedPeer.effectiveRssi)
        assertEquals(TopologyNodeDisplayState.DIRECT, mappedPeer.state)
    }

    // =========================================================================
    // 3. Node Deduplication
    // =========================================================================
    @Test
    fun testNodeDeduplication() {
        val local = createLocalNode(209070)
        val peer1 = createDirectPeer(101)
        val peer1Duplicate = createDirectPeer(101).copy(displayName = "NODE #101 DUP")
        val peer2 = createDirectPeer(102)

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, peer1, peer1Duplicate, peer2)
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        // Must deduplicate node 101 so total peer count is 2
        assertEquals(2, result.nodes.size)
        val nodeIds = result.nodes.map { it.nodeId }
        assertEquals(listOf(101, 102), nodeIds)
        assertEquals(3, result.stats.nodeCount) // 1 local + 2 peers
    }

    // =========================================================================
    // 4. Direct Link Mapping
    // =========================================================================
    @Test
    fun testDirectLinkMapping() {
        val local = createLocalNode(209070)
        val peer = createDirectPeer(101)
        val link = TopologyLink(
            sourceNodeId = 209070,
            destinationNodeId = 101,
            transport = "BT",
            isReachable = true,
            lastSeenMs = 0L,
            hopCost = 1,
            isDashed = false
        )

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, peer),
            links = listOf(link)
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        assertEquals(1, result.links.size)
        val mappedLink = result.links[0]
        assertEquals(209070, mappedLink.sourceNodeId)
        assertEquals(101, mappedLink.destinationNodeId)
        assertEquals(TopologyLinkType.DIRECT, mappedLink.linkType)
        assertFalse(mappedLink.isDashed)
        assertEquals(1, mappedLink.hopCost)
        assertEquals("BT", mappedLink.transport)
    }

    // =========================================================================
    // 5. Multi-Hop Mapping
    // =========================================================================
    @Test
    fun testMultiHopMapping() {
        val local = createLocalNode(209070)
        val relay = createDirectPeer(101)
        val dest = createRelayPeer(305)

        val route = TopologyRoute(
            destinationNodeId = 305,
            nextHopNodeId = 101,
            hopCount = 2,
            routeFreshness = 1L,
            transport = "MESH",
            state = "VALID"
        )

        val linkDirect = TopologyLink(sourceNodeId = 209070, destinationNodeId = 101, transport = "BT", isReachable = true, lastSeenMs = 0L, hopCost = 1)
        val linkRelay = TopologyLink(sourceNodeId = 101, destinationNodeId = 305, transport = "MESH", isReachable = true, lastSeenMs = 0L, hopCost = 1, isDashed = true)

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, relay, dest),
            links = listOf(linkDirect, linkRelay),
            routes = listOf(route)
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        val destNode = result.nodes.first { it.nodeId == 305 }
        assertEquals(2, destNode.hopCount)
        assertEquals("VIA #101 (2 HOPS)", destNode.routeSummary)
        assertEquals(TopologyNodeDisplayState.RELAY, destNode.state)
        assertEquals(2, destNode.tier) // Multi-hop nodes live in Tier 2

        val relayLink = result.links.first { it.destinationNodeId == 305 }
        assertEquals(TopologyLinkType.RELAY, relayLink.linkType)
        assertTrue(relayLink.isDashed)
    }

    // =========================================================================
    // 6. DTN Mapping
    // =========================================================================
    @Test
    fun testDtnMapping() {
        val local = createLocalNode(209070)
        val dtnDest = TopologyNode(
            nodeId = 402,
            displayName = "NODE #402",
            isLocal = false,
            isReachable = true,
            lastSeen = "DTN PENDING",
            lastSeenMs = 0L,
            hopCount = 0,
            transport = "UNKNOWN",
            routeState = "DTN_STORED",
            role = TopologyNodeRole.DESTINATION,
            state = TopologyNodeState.ONLINE
        )

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, dtnDest),
            dtnPendingCount = 3
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        val mappedDtn = result.nodes.first { it.nodeId == 402 }
        assertEquals(TopologyNodeDisplayState.DTN, mappedDtn.state)
        assertEquals(2, mappedDtn.tier)
        assertEquals("DTN STORED", mappedDtn.routeSummary)
        assertEquals(3, result.stats.dtnPendingCount)
    }

    // =========================================================================
    // 7. Unknown Telemetry
    // =========================================================================
    @Test
    fun testUnknownTelemetry() {
        val local = createLocalNode(209070)
        val blankPeer = TopologyNode(
            nodeId = 999,
            displayName = "NODE 999",
            isLocal = false,
            isReachable = true,
            lastSeen = "",
            lastSeenMs = 0L,
            hopCount = 0,
            transport = "",
            routeState = "",
            batteryLevel = "N/A",
            role = TopologyNodeRole.UNKNOWN,
            state = TopologyNodeState.ONLINE
        )

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, blankPeer)
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        val mapped = result.nodes.first { it.nodeId == 999 }
        assertNull(mapped.callsign)
        assertEquals("UNKNOWN", mapped.effectiveCallsign)
        assertNull(mapped.rssi)
        assertEquals("UNKNOWN", mapped.effectiveRssi)
        assertNull(mapped.hopCount)
        assertEquals("UNKNOWN", mapped.effectiveHops)
        assertNull(mapped.transport)
        assertEquals("UNKNOWN", mapped.effectiveTransport)
        assertEquals("UNKNOWN", mapped.lastSeenFormatted)
    }

    // =========================================================================
    // 8. Missing Callsign
    // =========================================================================
    @Test
    fun testMissingCallsign() {
        val local = createLocalNode(209070)
        val peer = createDirectPeer(105).copy(displayName = "NODE #105")

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, peer)
        )

        // No callsign provider
        val result = TopologyDisplayMapper.mapSnapshot(snapshot, callsignProvider = null)

        val mapped = result.nodes.first { it.nodeId == 105 }
        assertNull(mapped.callsign)
        assertEquals("UNKNOWN", mapped.effectiveCallsign)
        assertEquals("NODE #105", mapped.displayLabel)
    }

    // =========================================================================
    // 9. Missing RSSI
    // =========================================================================
    @Test
    fun testMissingRssi() {
        val local = createLocalNode(209070)
        val peer = createDirectPeer(101)

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, peer)
        )

        // rssiProvider returns null
        val result = TopologyDisplayMapper.mapSnapshot(snapshot, rssiProvider = { null })

        val mapped = result.nodes.first { it.nodeId == 101 }
        assertNull(mapped.rssi)
        assertEquals("UNKNOWN", mapped.effectiveRssi)
    }

    // =========================================================================
    // 10. Missing Hop Count
    // =========================================================================
    @Test
    fun testMissingHopCount() {
        val local = createLocalNode(209070)
        val peer = createDirectPeer(101).copy(hopCount = 0, role = TopologyNodeRole.UNKNOWN)

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, peer),
            routes = emptyList()
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        val mapped = result.nodes.first { it.nodeId == 101 }
        assertNull(mapped.hopCount)
        assertEquals("UNKNOWN", mapped.effectiveHops)
    }

    // =========================================================================
    // 11. Route-State Mapping
    // =========================================================================
    @Test
    fun testRouteStateMapping() {
        val local = createLocalNode(209070)
        val directNode = createDirectPeer(101)
        val relayNode = createRelayPeer(102)
        val unreachableNode = createDirectPeer(103).copy(isReachable = false)
        val staleNode = createDirectPeer(104).copy(state = TopologyNodeState.STALE)

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, directNode, relayNode, unreachableNode, staleNode)
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        assertEquals(TopologyNodeDisplayState.DIRECT, result.nodes.first { it.nodeId == 101 }.state)
        assertEquals(TopologyNodeDisplayState.RELAY, result.nodes.first { it.nodeId == 102 }.state)
        assertEquals(TopologyNodeDisplayState.UNREACHABLE, result.nodes.first { it.nodeId == 103 }.state)
        assertEquals(TopologyNodeDisplayState.RECENTLY_HEARD, result.nodes.first { it.nodeId == 104 }.state)
    }

    // =========================================================================
    // 12. Deterministic Layout
    // =========================================================================
    @Test
    fun testDeterministicLayout() {
        val local = createLocalNode(209070)
        val p1 = createDirectPeer(101)
        val p2 = createDirectPeer(102)
        val p3 = createRelayPeer(301)

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, p1, p2, p3)
        )

        val run1 = TopologyDisplayMapper.mapSnapshot(snapshot)
        val run2 = TopologyDisplayMapper.mapSnapshot(snapshot)

        assertEquals(run1.nodes.size, run2.nodes.size)
        for (i in run1.nodes.indices) {
            val n1 = run1.nodes[i]
            val n2 = run2.nodes[i]
            assertEquals(n1.nodeId, n2.nodeId)
            assertEquals(n1.angleRad, n2.angleRad, 0.0001f)
            assertEquals(n1.normalizedRadius, n2.normalizedRadius, 0.0001f)
            assertEquals(n1.tier, n2.tier)
        }
    }

    // =========================================================================
    // 13. Node Selection
    // =========================================================================
    @Test
    fun testNodeSelection() {
        val local = createLocalNode(209070)
        val peer1 = createDirectPeer(101)
        val peer2 = createDirectPeer(102)
        val link = TopologyLink(sourceNodeId = 209070, destinationNodeId = 101, transport = "BT", isReachable = true, lastSeenMs = 0L)

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, peer1, peer2),
            links = listOf(link)
        )

        val resultSelected = TopologyDisplayMapper.mapSnapshot(snapshot, selectedNodeId = 101)

        assertEquals(101, resultSelected.selectedNodeId)
        assertNotNull(resultSelected.selectedNode)
        assertEquals(101, resultSelected.selectedNode!!.nodeId)
        assertTrue(resultSelected.nodes.first { it.nodeId == 101 }.isHighlighted)
        assertFalse(resultSelected.nodes.first { it.nodeId == 102 }.isHighlighted)
        assertTrue(resultSelected.links.first { it.destinationNodeId == 101 }.isHighlighted)
    }

    // =========================================================================
    // 14. Empty Topology
    // =========================================================================
    @Test
    fun testEmptyTopology() {
        val local = createLocalNode(209070)
        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local),
            links = emptyList()
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        assertTrue(result.isEmpty)
        assertEquals("NO PEERS DETECTED", result.emptyMessage)
        assertEquals(0, result.nodes.size)
        assertEquals(1, result.stats.nodeCount) // Only local node
        assertEquals(0, result.stats.linkCount)
    }

    // =========================================================================
    // 15. Topology Updates
    // =========================================================================
    @Test
    fun testTopologyUpdates() {
        val local = createLocalNode(209070)
        val snapInitial = MeshTopologySnapshot(localNode = local, nodes = listOf(local))

        val resultInitial = TopologyDisplayMapper.mapSnapshot(snapInitial)
        assertTrue(resultInitial.isEmpty)
        assertEquals(0, resultInitial.nodes.size)

        // Peer discovered
        val newPeer = createDirectPeer(101)
        val snapUpdated = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, newPeer),
            links = listOf(TopologyLink(sourceNodeId = 209070, destinationNodeId = 101, transport = "BT", isReachable = true, lastSeenMs = 0L))
        )

        val resultUpdated = TopologyDisplayMapper.mapSnapshot(snapUpdated)
        assertFalse(resultUpdated.isEmpty)
        assertEquals(1, resultUpdated.nodes.size)
        assertEquals(101, resultUpdated.nodes[0].nodeId)
        assertEquals(2, resultUpdated.stats.nodeCount)
        assertEquals(1, resultUpdated.stats.linkCount)
    }

    // =========================================================================
    // 16. Malformed / Incomplete Topology
    // =========================================================================
    @Test
    fun testMalformedIncompleteTopology() {
        // Snapshot with null local node and orphan links
        val orphanLink = TopologyLink(sourceNodeId = 999, destinationNodeId = 888, transport = "UNKNOWN", isReachable = false, lastSeenMs = 0L)
        val malformedSnapshot = MeshTopologySnapshot(
            localNode = null,
            nodes = emptyList(),
            links = listOf(orphanLink),
            routes = emptyList()
        )

        val result = TopologyDisplayMapper.mapSnapshot(malformedSnapshot)

        assertNull(result.localNode)
        assertTrue(result.isEmpty)
        assertEquals(1, result.links.size)
        assertEquals(0, result.stats.nodeCount)
    }

    // =========================================================================
    // 17. No Fabricated Nodes
    // =========================================================================
    @Test
    fun testNoFabricatedNodes() {
        val local = createLocalNode(209070)
        val realPeer1 = createDirectPeer(101)
        val realPeer2 = createDirectPeer(102)

        val snapshot = MeshTopologySnapshot(
            localNode = local,
            nodes = listOf(local, realPeer1, realPeer2)
        )

        val result = TopologyDisplayMapper.mapSnapshot(snapshot)

        // Strict equality: must not contain any made up nodes (e.g. ALPHA-1, BRAVO-2, etc.)
        val mappedNodeIds = result.nodes.map { it.nodeId }.toSet()
        assertEquals(setOf(101, 102), mappedNodeIds)
        assertEquals(3, result.stats.nodeCount)
    }

    // =========================================================================
    // 18. Callsign Extraction Utilities
    // =========================================================================
    @Test
    fun testExtractCallsign() {
        assertEquals("ALPHA ONE", TopologyDisplayMapper.extractCallsign("ALPHA ONE"))
        assertEquals("SQUAD CHARLIE", TopologyDisplayMapper.extractCallsign("NODE 101 (SQUAD CHARLIE)"))
        assertNull(TopologyDisplayMapper.extractCallsign("NODE 101"))
        assertNull(TopologyDisplayMapper.extractCallsign("NODE #101"))
        assertNull(TopologyDisplayMapper.extractCallsign("NODE 209070 (LOCAL)"))
        assertNull(TopologyDisplayMapper.extractCallsign(""))
    }
}
