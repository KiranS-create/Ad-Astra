package org.sih.itantra.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NeighborTableTest {

    private lateinit var table: NeighborTable

    @Before
    fun setUp() {
        // Use 100 ms expiry for fast test verification
        table = NeighborTable(maxNeighbors = 4, expiryMs = 100L)
    }

    private fun fakeEntry(nodeId: Int, seqNum: Int = 1, offsetMs: Long = 0L) = NeighborEntry(
        nodeId     = nodeId,
        transport  = "TEST",
        lastSeenMs = System.currentTimeMillis() + offsetMs,
        batteryPct = 80,
        seqNum     = seqNum
    )

    // ------------------------------------------------------------------
    // Test 1: Add and immediately look up a neighbor
    // ------------------------------------------------------------------
    @Test
    fun addAndLookup() {
        table.upsert(fakeEntry(1001))
        val result = table.lookup(1001)
        assertNotNull("Neighbor 1001 should be present", result)
        assertEquals(1001, result!!.nodeId)
    }

    // ------------------------------------------------------------------
    // Test 2: Stale entry (past expiry) is pruned on lookup
    // ------------------------------------------------------------------
    @Test
    fun expiresStaleEntry() {
        // Insert with a lastSeen timestamp already 200 ms in the past
        table.upsert(fakeEntry(nodeId = 2001, offsetMs = -200L))
        // Wait a tiny bit to ensure expiry threshold is crossed
        Thread.sleep(10)
        val result = table.lookup(2001)
        assertNull("Stale neighbor 2001 should be null after expiry", result)
    }

    // ------------------------------------------------------------------
    // Test 3: LRU eviction — table evicts oldest when over capacity
    // ------------------------------------------------------------------
    @Test
    fun lruEviction() {
        // Capacity = 4; insert 5 neighbors
        for (id in 1..5) {
            table.upsert(fakeEntry(id * 1000))
        }
        val live = table.liveNeighbors()
        // Only 4 should remain (oldest evicted by LinkedHashMap)
        assertEquals("Should have max 4 neighbors", 4, live.size)
        // 1000 (first inserted) should have been evicted
        assertNull("First inserted neighbor should be evicted", table.lookup(1000))
        // Most recently inserted should still be present
        assertNotNull("Last inserted neighbor should be present", table.lookup(5000))
    }
}
