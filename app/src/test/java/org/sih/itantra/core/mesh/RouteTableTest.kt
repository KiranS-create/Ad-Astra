package org.sih.itantra.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RouteTableTest {

    private lateinit var routeTable: RouteTable
    private lateinit var dupCache: RreqDupCache

    @Before
    fun setUp() {
        // Use 100 ms route lifetime for fast expiry tests
        routeTable = RouteTable(capacity = 10, routeLifetimeMs = 100L)
        dupCache = RreqDupCache(capacity = 8)
    }

    private fun fakeRoute(
        dest: Int,
        nextHop: Int,
        hopCount: Int = 1,
        seqNum: Int = 1,
        expiryOffset: Long = 10_000L
    ) = RouteEntry(
        destinationNodeId = dest,
        nextHopNodeId     = nextHop,
        hopCount          = hopCount,
        routeSeqNum       = seqNum,
        expiryMs          = System.currentTimeMillis() + expiryOffset
    )

    // ------------------------------------------------------------------
    // Test 4: Insert a route and look it up
    // ------------------------------------------------------------------
    @Test
    fun insertAndLookup() {
        routeTable.addOrUpdate(fakeRoute(dest = 500, nextHop = 200))
        val entry = routeTable.lookup(500)
        assertNotNull("Route to 500 should be present", entry)
        assertEquals("Next hop should be 200", 200, entry!!.nextHopNodeId)
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

        // Raw entry (without going through lookup which may re-stamp state) should be INVALID
        val rawEntry = routeTable.allEntries().firstOrNull { it.destinationNodeId == destId }
        assertNotNull("Raw entry should still exist (not yet pruned)", rawEntry)
        assertEquals("State should be INVALID", RouteState.INVALID, rawEntry!!.state)

        // lookup() must return null for INVALID routes (same as for EXPIRED)
        val afterInvalidation = routeTable.lookup(destId)
        assertNull("Invalidated route should return null from lookup", afterInvalidation)
    }

    // ------------------------------------------------------------------
    // Test 6: Expired route returns null on lookup
    //
    // RouteTable.addOrUpdate() always refreshes expiryMs on insert (by design —
    // it sets expiryMs = now + routeLifetimeMs). To test expiry, we use a
    // routeLifetimeMs of 1 ms and then wait briefly so the route becomes stale.
    // ------------------------------------------------------------------
    @Test
    fun routeExpiryReturnsNull() {
        // Create a table with a 1 ms route lifetime
        val shortLifetimeTable = RouteTable(capacity = 10, routeLifetimeMs = 1L)
        shortLifetimeTable.addOrUpdate(RouteEntry(
            destinationNodeId = 700,
            nextHopNodeId     = 300,
            hopCount          = 1,
            routeSeqNum       = 1,
            expiryMs          = System.currentTimeMillis() + 1L  // will be overridden to now+1ms
        ))
        // Sleep long enough for the 1 ms lifetime to expire
        Thread.sleep(20)
        val entry = shortLifetimeTable.lookup(700)
        assertNull("Expired route should return null", entry)
    }

    // ------------------------------------------------------------------
    // Test 5: Duplicate RREQ suppression via RreqDupCache
    // ------------------------------------------------------------------
    @Test
    fun duplicateRreqSuppression() {
        val key = RreqKey(originNodeId = 999, reqId = 7)
        val firstSeen   = dupCache.isDuplicate(key)
        val secondSeen  = dupCache.isDuplicate(key)
        val thirdSeen   = dupCache.isDuplicate(key)

        assertEquals("First occurrence should NOT be a duplicate", false, firstSeen)
        assertEquals("Second occurrence SHOULD be a duplicate",   true,  secondSeen)
        assertEquals("Third occurrence SHOULD be a duplicate",    true,  thirdSeen)
    }
}
