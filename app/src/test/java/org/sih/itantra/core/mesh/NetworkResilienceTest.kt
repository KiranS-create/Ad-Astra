package org.sih.itantra.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.GeoLocation
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer

/**
 * Targeted verification of Network Resilience Phase:
 * 1. DTN Store-and-Forward (bounded, priority ordering, eviction, expiry, dup suppression)
 * 2. Adaptive MANET Route Selection (hops > link quality > battery health tie-breaker)
 * 3. Emergency Location-bearing DISTRESS DTN survival
 */
class NetworkResilienceTest {

    private lateinit var dtnStore: DtnStore

    @Before
    fun setUp() {
        dtnStore = DtnStore(maxPackets = 10, maxBytes = 64 * 1024, expiryMs = 600_000L)
    }

    private fun makePacket(
        destId: Int = 200,
        sourceId: Int = 100,
        seq: Short = 1,
        priority: MessagePriority = MessagePriority.NORMAL,
        msgType: Byte = Packet.TYPE_TEXT,
        location: GeoLocation? = null
    ): Packet {
        return Packet(
            msgType = msgType,
            priority = priority,
            ttl = Packet.DEFAULT_TTL,
            flags = if (location != null) Packet.FLAG_HAS_LOCATION.toByte() else 0.toByte(),
            sequenceNumber = seq,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = sourceId,
            destinationDeviceId = destId,
            language = IndicLanguage.ENGLISH,
            payload = "TEST_PAYLOAD_$seq".toByteArray(Charsets.UTF_8),
            location = location
        )
    }

    // -------------------------------------------------------------------------
    // 1. DTN Priority Ordering
    // -------------------------------------------------------------------------
    @Test
    fun testDtnPriorityOrdering() {
        val dest = 500
        val pNormal = makePacket(destId = dest, seq = 1, priority = MessagePriority.NORMAL)
        val pImportant = makePacket(destId = dest, seq = 2, priority = MessagePriority.IMPORTANT)
        val pDistress = makePacket(destId = dest, seq = 3, priority = MessagePriority.DISTRESS, msgType = Packet.TYPE_DISTRESS)
        val pAlert = makePacket(destId = dest, seq = 4, priority = MessagePriority.ALERT)

        // Store in arbitrary order
        dtnStore.store(pNormal)
        dtnStore.store(pImportant)
        dtnStore.store(pDistress)
        dtnStore.store(pAlert)

        assertEquals("Queue should contain 4 packets", 4, dtnStore.size())

        val drained = dtnStore.drainForDestination(dest)
        assertEquals("Should drain all 4 packets", 4, drained.size)

        // Order must strictly be: DISTRESS > ALERT > IMPORTANT > NORMAL
        assertEquals("1st should be DISTRESS", MessagePriority.DISTRESS, drained[0].priority)
        assertEquals("2nd should be ALERT", MessagePriority.ALERT, drained[1].priority)
        assertEquals("3rd should be IMPORTANT", MessagePriority.IMPORTANT, drained[2].priority)
        assertEquals("4th should be NORMAL", MessagePriority.NORMAL, drained[3].priority)
    }

    // -------------------------------------------------------------------------
    // 2. DTN Bounded Capacity and Low-Priority Eviction
    // -------------------------------------------------------------------------
    @Test
    fun testDtnBoundedCapacityAndEviction() {
        val smallStore = DtnStore(maxPackets = 3, maxBytes = 10_000, expiryMs = 60_000L)
        val p1 = makePacket(destId = 1, seq = 1, priority = MessagePriority.NORMAL)
        val p2 = makePacket(destId = 2, seq = 2, priority = MessagePriority.NORMAL)
        val p3 = makePacket(destId = 3, seq = 3, priority = MessagePriority.NORMAL)

        assertTrue(smallStore.store(p1))
        assertTrue(smallStore.store(p2))
        assertTrue(smallStore.store(p3))
        assertEquals(3, smallStore.size())

        // Add 4th packet with DISTRESS priority - should evict oldest NORMAL packet
        val pDistress = makePacket(destId = 99, seq = 4, priority = MessagePriority.DISTRESS, msgType = Packet.TYPE_DISTRESS)
        assertTrue("DISTRESS should be accepted by evicting low priority", smallStore.store(pDistress))
        assertEquals("Capacity bounded at 3", 3, smallStore.size())

        val all = smallStore.drainAll()
        assertTrue("DISTRESS packet must be present in queue", all.any { it.priority == MessagePriority.DISTRESS })
        assertFalse("Oldest NORMAL packet p1 should have been evicted", all.any { it.sequenceNumber == 1.toShort() })
    }

    // -------------------------------------------------------------------------
    // 3. DTN Expiry Handling
    // -------------------------------------------------------------------------
    @Test
    fun testDtnExpiryHandling() {
        val fastExpireStore = DtnStore(maxPackets = 10, maxBytes = 10_000, expiryMs = 20L)
        val p = makePacket(destId = 300, seq = 1)
        fastExpireStore.store(p)
        assertEquals(1, fastExpireStore.size())

        Thread.sleep(40) // Wait for expiry

        val pruned = fastExpireStore.pruneExpired()
        assertEquals("Should prune 1 expired packet", 1, pruned)
        assertEquals("Store should now be empty", 0, fastExpireStore.size())
        assertTrue("Drain should yield empty list", fastExpireStore.drainForDestination(300).isEmpty())
    }

    // -------------------------------------------------------------------------
    // 4. DTN Duplicate Suppression
    // -------------------------------------------------------------------------
    @Test
    fun testDtnDuplicateSuppression() {
        val p = makePacket(sourceId = 42, seq = 99)
        val firstStored = dtnStore.store(p)
        val secondStored = dtnStore.store(p)

        assertTrue("First insertion should succeed", firstStored)
        assertFalse("Duplicate insertion should be suppressed", secondStored)
        assertEquals(1, dtnStore.size())
    }

    // -------------------------------------------------------------------------
    // 5. Adaptive Route Selection: Hop Count Strict Priority
    // -------------------------------------------------------------------------
    @Test
    fun testAdaptiveRouteSelectionHopCountPriority() {
        val dest = 700
        val existing = RouteEntry(
            destinationNodeId = dest,
            nextHopNodeId = 10,
            hopCount = 3,
            routeSeqNum = 5,
            expiryMs = System.currentTimeMillis() + 60_000L,
            linkQuality = 1.0f,
            batteryPct = 100
        )

        // Candidate has fewer hops (1 hop vs 3 hops) even with lower quality and battery
        val candidate = RouteEntry(
            destinationNodeId = dest,
            nextHopNodeId = 20,
            hopCount = 1,
            routeSeqNum = 5,
            expiryMs = System.currentTimeMillis() + 60_000L,
            linkQuality = 0.5f,
            batteryPct = 40
        )

        val replaced = AdaptiveRouteSelector.shouldReplace(existing, candidate)
        assertTrue("Lower hop count must strictly supersede higher hop count", replaced)

        // Conversely, higher hop count must NOT supersede lower hop count even with 100% battery
        val worseCandidate = candidate.copy(hopCount = 4, batteryPct = 100, linkQuality = 1.0f)
        val replacedWorse = AdaptiveRouteSelector.shouldReplace(existing, worseCandidate)
        assertFalse("Higher hop count must not supersede fewer hops", replacedWorse)
    }

    // -------------------------------------------------------------------------
    // 6. Adaptive Route Selection: Link Quality Secondary Priority
    // -------------------------------------------------------------------------
    @Test
    fun testAdaptiveRouteSelectionLinkQuality() {
        val dest = 800
        val existing = RouteEntry(
            destinationNodeId = dest,
            nextHopNodeId = 10,
            hopCount = 2,
            routeSeqNum = 5,
            expiryMs = System.currentTimeMillis() + 60_000L,
            linkQuality = 0.50f,
            batteryPct = 80
        )

        // Candidate has same hops (2), but significantly better link quality (0.85 vs 0.50, diff = 0.35 > 0.15)
        val betterCandidate = RouteEntry(
            destinationNodeId = dest,
            nextHopNodeId = 30,
            hopCount = 2,
            routeSeqNum = 5,
            expiryMs = System.currentTimeMillis() + 60_000L,
            linkQuality = 0.85f,
            batteryPct = 50 // even with lower battery
        )

        assertTrue("Better link quality (>0.15 diff) should replace equal-hop route",
            AdaptiveRouteSelector.shouldReplace(existing, betterCandidate))

        // Worse link quality candidate should be rejected
        val worseCandidate = betterCandidate.copy(linkQuality = 0.30f)
        assertFalse("Worse link quality candidate must be rejected",
            AdaptiveRouteSelector.shouldReplace(existing, worseCandidate))
    }

    // -------------------------------------------------------------------------
    // 7. Adaptive Route Selection: Battery Health Tie-Breaker
    // -------------------------------------------------------------------------
    @Test
    fun testAdaptiveRouteSelectionBatteryTieBreaker() {
        val dest = 900
        // Equal hops (2) and link qualities within 0.15 (0.75 vs 0.80, diff = 0.05)
        val existing = RouteEntry(
            destinationNodeId = dest,
            nextHopNodeId = 10,
            hopCount = 2,
            routeSeqNum = 5,
            expiryMs = System.currentTimeMillis() + 60_000L,
            linkQuality = 0.75f,
            batteryPct = 35
        )

        val higherBatteryCandidate = RouteEntry(
            destinationNodeId = dest,
            nextHopNodeId = 40,
            hopCount = 2,
            routeSeqNum = 5,
            expiryMs = System.currentTimeMillis() + 60_000L,
            linkQuality = 0.80f,
            batteryPct = 88
        )

        assertTrue("Higher battery candidate should win tie-break when link quality is comparable",
            AdaptiveRouteSelector.shouldReplace(existing, higherBatteryCandidate))

        val lowerBatteryCandidate = higherBatteryCandidate.copy(batteryPct = 20)
        assertFalse("Lower battery candidate should lose tie-break",
            AdaptiveRouteSelector.shouldReplace(existing, lowerBatteryCandidate))
    }

    // -------------------------------------------------------------------------
    // 8. Explainable Route Explanation Label
    // -------------------------------------------------------------------------
    @Test
    fun testRouteExplanationFormatting() {
        val entry = RouteEntry(
            destinationNodeId = 300,
            nextHopNodeId = 200,
            hopCount = 2,
            routeSeqNum = 1,
            expiryMs = System.currentTimeMillis() + 60_000L,
            linkQuality = 0.75f,
            batteryPct = 72
        )

        val explanation = AdaptiveRouteSelector.formatRouteExplanation(entry, listOf("A", "B", "D"))
        assertEquals("A -> B -> D | 2 HOPS | QUALITY GOOD | BATTERY 72%", explanation)

        val directEntry = entry.copy(hopCount = 1, linkQuality = 0.95f)
        val directExplanation = AdaptiveRouteSelector.formatRouteExplanation(directEntry)
        assertEquals("200 -> 300 | 1 HOP | QUALITY EXCELLENT | BATTERY 72%", directExplanation)
    }

    // -------------------------------------------------------------------------
    // 9. Location-bearing DISTRESS DTN Integration Round-Trip
    // -------------------------------------------------------------------------
    @Test
    fun testEmergencyDistressDtnSurvival() {
        val location = GeoLocation(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 8.5f,
            timestamp = 1725500000000L,
            altitude = 920.0
        )
        val distressPacket = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 77,
            timestamp = 1725500000000L,
            sourceDeviceId = 1001,
            destinationDeviceId = 2002,
            language = IndicLanguage.TAMIL,
            payload = "வெள்ள அபாயம்! உடனடியாக மீட்கவும்".toByteArray(Charsets.UTF_8),
            location = location
        )

        // Store in DTN
        assertTrue("DTN store should accept DISTRESS packet", dtnStore.store(distressPacket))

        // Drain for destination
        val drained = dtnStore.drainForDestination(2002)
        assertEquals(1, drained.size)
        val retrieved = drained[0]

        // Verify content before wire serialization
        assertEquals(Packet.TYPE_DISTRESS, retrieved.msgType)
        assertEquals(MessagePriority.DISTRESS, retrieved.priority)
        assertNotNull(retrieved.location)
        assertEquals(12.9716, retrieved.location!!.latitude, 0.0001)
        assertEquals(77.5946, retrieved.location!!.longitude, 0.0001)

        // Wire serialization & deserialization check (simulating next-hop transmission)
        val wireBytes = PacketSerializer.serialize(retrieved)
        val deserialized = PacketSerializer.deserialize(wireBytes)
        assertNotNull("Wire deserialization must succeed", deserialized)
        assertEquals(1001, deserialized!!.sourceDeviceId)
        assertEquals(2002, deserialized.destinationDeviceId)
        assertNotNull("Location must survive wire transit", deserialized.location)
        assertEquals(12.9716, deserialized.location!!.latitude, 0.0001)
        assertEquals(77.5946, deserialized.location!!.longitude, 0.0001)
        assertEquals("வெள்ள அபாயம்! உடனடியாக மீட்கவும்", String(deserialized.payload, Charsets.UTF_8))
    }
}
