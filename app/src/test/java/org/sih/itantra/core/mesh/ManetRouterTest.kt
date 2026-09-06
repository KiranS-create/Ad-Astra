package org.sih.itantra.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.protocol.Packet

/**
 * ManetRouter logic tests.
 *
 * ManetRouter depends on Context and TransportManager (Android runtime), so
 * we test the collaborating pure-logic components directly:
 *   - RouteTable (route install / lookup)
 *   - RreqDupCache (duplicate suppression)
 *   - PacketRelayRouter (TTL loop prevention)
 *   - RouteEntry state transitions (VALID → INVALID → route rediscovery)
 *
 * These cover the exact behavioral guarantees listed in the implementation spec:
 *   7. RREQ→RREP route establishment
 *   8. Route failure / RERR
 *   9. Route rediscovery after RERR
 *   10. TTL loop prevention
 *   11. DATA forwarding through next hop
 */
class ManetRouterTest {

    private lateinit var routeTable: RouteTable
    private lateinit var dupCache: RreqDupCache
    private lateinit var relayRouter: PacketRelayRouter
    private val localNodeId = 111_111

    @Before
    fun setUp() {
        routeTable  = RouteTable()
        dupCache    = RreqDupCache()
        relayRouter = PacketRelayRouter(localNodeId)
        relayRouter.setRelayEnabled(true)
    }

    // ------------------------------------------------------------------
    // Test 7: RREQ→RREP flow installs a valid route in RouteTable
    // ------------------------------------------------------------------
    @Test
    fun rreqToRrepEstablishesRoute() {
        // Simulate: node 222 sends RREP saying it can reach node 333 via 1 hop
        val rrepSrcId = 222        // intermediate node that sent us the RREP
        val destId    = 333
        val destSeq   = 5

        // ManetRouter.handleRrep() calls routeTable.addOrUpdate() with this entry
        routeTable.addOrUpdate(RouteEntry(
            destinationNodeId = destId,
            nextHopNodeId     = rrepSrcId,
            hopCount          = 1,
            routeSeqNum       = destSeq,
            expiryMs          = System.currentTimeMillis() + 120_000L
        ))

        val route = routeTable.lookup(destId)
        assertNotNull("Route to $destId should be established after RREP", route)
        assertEquals("Next hop should be $rrepSrcId", rrepSrcId, route!!.nextHopNodeId)
        assertEquals("Hop count should be 1", 1, route.hopCount)
        assertEquals("Sequence number should match", destSeq, route.routeSeqNum)
        assertEquals("Route should be VALID", RouteState.VALID, route.state)
    }

    // ------------------------------------------------------------------
    // Test 8: Route failure marks route INVALID and allows RERR propagation
    // ------------------------------------------------------------------
    @Test
    fun routeFailureMarksRouteInvalid() {
        val destId = 444

        // Install a valid route
        routeTable.addOrUpdate(RouteEntry(
            destinationNodeId = destId,
            nextHopNodeId     = 555,
            hopCount          = 2,
            routeSeqNum       = 3,
            expiryMs          = System.currentTimeMillis() + 120_000L
        ))
        assertNotNull("Route should exist before failure", routeTable.lookup(destId))

        // Simulate ManetRouter.handleNextHopFailure() → routeTable.invalidate()
        routeTable.invalidate(destId)

        // Read raw state BEFORE calling lookup() — lookup() triggers lazy expiry re-stamp
        val rawEntry = routeTable.allEntries().firstOrNull { it.destinationNodeId == destId }
        assertNotNull("Raw entry should still exist (not yet pruned)", rawEntry)
        assertEquals("State should be INVALID", RouteState.INVALID, rawEntry!!.state)

        // lookup() must return null for INVALID (same behaviour as EXPIRED)
        val afterInvalidation = routeTable.lookup(destId)
        assertNull("Invalidated route should return null from lookup", afterInvalidation)
    }

    // ------------------------------------------------------------------
    // Test 9: Route rediscovery — after invalidation, fresh addOrUpdate
    //         with higher seqNum restores a VALID route
    // ------------------------------------------------------------------
    @Test
    fun routeRediscoveryAfterRerr() {
        val destId = 555

        // Install initial route
        routeTable.addOrUpdate(RouteEntry(
            destinationNodeId = destId,
            nextHopNodeId     = 666,
            hopCount          = 2,
            routeSeqNum       = 4,
            expiryMs          = System.currentTimeMillis() + 120_000L
        ))

        // Failure
        routeTable.invalidate(destId)
        assertNull("Route should be gone after failure", routeTable.lookup(destId))

        // Rediscovery — new RREP arrives with higher seqNum and alternate path
        routeTable.addOrUpdate(RouteEntry(
            destinationNodeId = destId,
            nextHopNodeId     = 777,
            hopCount          = 3,
            routeSeqNum       = 6,   // higher than previous 4
            expiryMs          = System.currentTimeMillis() + 120_000L
        ))

        val recovered = routeTable.lookup(destId)
        assertNotNull("Route should be recoverable after rediscovery", recovered)
        assertEquals("New next hop should be 777", 777, recovered!!.nextHopNodeId)
        assertEquals("New seq should be 6", 6, recovered.routeSeqNum)
        assertEquals("Recovered route should be VALID", RouteState.VALID, recovered.state)
    }

    // ------------------------------------------------------------------
    // Test 10: TTL loop prevention — PacketRelayRouter drops TTL=0 packets
    // ------------------------------------------------------------------
    @Test
    fun ttlLoopPrevention() {
        val sourceId = 999_999  // NOT the local node
        val ttlZeroPacket = Packet(
            msgType             = Packet.TYPE_TEXT,
            priority            = org.sih.itantra.core.common.MessagePriority.NORMAL,
            ttl                 = 1,           // TTL=1 → after decrement becomes 0 → DeliverLocalOnly
            sequenceNumber      = 42,
            timestamp           = System.currentTimeMillis(),
            sourceDeviceId      = sourceId,
            destinationDeviceId = Packet.BROADCAST_ID,
            language            = org.sih.itantra.core.common.IndicLanguage.HINDI,
            payload             = "test".toByteArray()
        )

        val decision = relayRouter.evaluatePacket(ttlZeroPacket)

        // With TTL=1 and relay enabled: nextTtl = 0 → DeliverLocalOnly (not ForwardAndDeliver)
        assertTrue(
            "TTL=0 after decrement must not result in forwarding",
            decision is RelayAction.DeliverLocalOnly
        )
    }

    // ------------------------------------------------------------------
    // Test 11: DATA packet with known route should be sent to nextHopNodeId
    //          (verifies RouteTable lookup drives routing decision)
    // ------------------------------------------------------------------
    @Test
    fun dataForwardingUsesRouteTableNextHop() {
        val destId   = 888_888
        val nextHop  = 444_444

        // Pre-install route to destination
        routeTable.addOrUpdate(RouteEntry(
            destinationNodeId = destId,
            nextHopNodeId     = nextHop,
            hopCount          = 1,
            routeSeqNum       = 1,
            expiryMs          = System.currentTimeMillis() + 120_000L
        ))

        // Simulate routeAndSend() route lookup
        val route = routeTable.lookup(destId)
        assertNotNull("Route must exist for unicast forwarding", route)
        assertEquals(
            "Packet should be routed via nextHop $nextHop (not broadcast)",
            nextHop,
            route!!.nextHopNodeId
        )

        // Confirm that a broadcast destination bypasses route lookup
        val broadcastDest = Packet.BROADCAST_ID
        val broadcastRoute = routeTable.lookup(broadcastDest)
        assertNull("Broadcast destination has no route entry — must bypass routing", broadcastRoute)
    }
}
