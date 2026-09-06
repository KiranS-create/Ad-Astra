package org.sih.itantra.core.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer

class PacketRelayRouterTest {

    private val localId = 200002
    private lateinit var router: PacketRelayRouter

    @Before
    fun setup() {
        router = PacketRelayRouter(localDeviceId = localId)
        router.setRelayEnabled(true)
    }

    private fun createSamplePacket(
        sourceId: Int = 100001,
        seq: Short = 1,
        ttl: Byte = Packet.DEFAULT_TTL,
        lang: IndicLanguage = IndicLanguage.HINDI,
        text: String = "रिले परीक्षण संदेश"
    ): Packet {
        val payload = text.toByteArray(Charsets.UTF_8)
        return Packet(
            sourceDeviceId = sourceId,
            sequenceNumber = seq,
            timestamp = System.currentTimeMillis(),
            ttl = ttl,
            language = lang,
            priority = MessagePriority.NORMAL,
            payload = payload
        )
    }

    @Test
    fun testTtlDecrementAndForward() {
        val packet = createSamplePacket(ttl = 3)
        val action = router.evaluatePacket(packet)

        assertTrue("Expected ForwardAndDeliver for fresh packet with TTL=3", action is RelayAction.ForwardAndDeliver)
        val forwarded = (action as RelayAction.ForwardAndDeliver).forwardedPacket

        assertEquals(2.toByte(), forwarded.ttl)
        assertTrue(forwarded.isForwarded)
        assertEquals(packet.sequenceNumber, forwarded.sequenceNumber)
        assertEquals(packet.sourceDeviceId, forwarded.sourceDeviceId)
        assertEquals(packet.language, forwarded.language)
        assertArrayEquals(packet.payload, forwarded.payload)

        // Verify serializing and deserializing forwarded packet preserves CRC
        val serialized = PacketSerializer.serialize(forwarded)
        val deserialized = PacketSerializer.deserialize(serialized)
        assertEquals(forwarded.ttl, deserialized.ttl)
        assertEquals(forwarded.flags, deserialized.flags)
        assertArrayEquals(forwarded.payload, deserialized.payload)
    }

    @Test
    fun testTtlExpiryPreventsForwarding() {
        val packet = createSamplePacket(ttl = 1)
        val action = router.evaluatePacket(packet)

        assertTrue("TTL=1 decrements to 0, packet should be delivered locally only", action is RelayAction.DeliverLocalOnly)
        val stats = router.getStats()
        assertEquals(1L, stats.packetsReceived)
        assertEquals(0L, stats.packetsForwarded)
        assertEquals(1L, stats.ttlExpiredDropped)
    }

    @Test
    fun testDuplicateSuppression() {
        val packet = createSamplePacket(sourceId = 100001, seq = 42)

        val firstAction = router.evaluatePacket(packet)
        assertTrue("First packet should be forwarded", firstAction is RelayAction.ForwardAndDeliver)

        val secondAction = router.evaluatePacket(packet)
        assertTrue("Duplicate packet should be dropped", secondAction is RelayAction.DropDuplicate)

        val stats = router.getStats()
        assertEquals(1L, stats.packetsReceived)
        assertEquals(1L, stats.duplicatesDropped)
        assertEquals(1L, stats.packetsForwarded)
    }

    @Test
    fun testSelfPacketSuppression() {
        val selfPacket = createSamplePacket(sourceId = localId, seq = 5)
        val action = router.evaluatePacket(selfPacket)

        assertTrue("Packet from local device must be dropped as self", action is RelayAction.DropSelf)
        val stats = router.getStats()
        assertEquals(0L, stats.packetsReceived)
        assertEquals(1L, stats.selfPacketsDropped)
    }

    @Test
    fun testRelayDisabledBehavior() {
        router.setRelayEnabled(false)
        assertFalse(router.isRelayEnabled.value)

        val packet = createSamplePacket(ttl = 3)
        val action = router.evaluatePacket(packet)

        assertTrue("When relay disabled, valid packet is delivered local only", action is RelayAction.DeliverLocalOnly)
        val stats = router.getStats()
        assertEquals(1L, stats.packetsReceived)
        assertEquals(0L, stats.packetsForwarded)
    }

    @Test
    fun testMultiHopMeshSimulationABC() {
        // Node A: Source Transmitter (id=101)
        val nodeAId = 101
        // Node B: Intermediate Relay (id=202)
        val nodeB = PacketRelayRouter(localDeviceId = 202)
        nodeB.setRelayEnabled(true)
        // Node C: Final Receiver / Terminal Relay (id=303)
        val nodeC = PacketRelayRouter(localDeviceId = 303)
        nodeC.setRelayEnabled(true)

        // 1. Node A creates original packet P_A
        val packetA = createSamplePacket(sourceId = nodeAId, seq = 100, ttl = 3, text = "Field Recon Unit Alpha")

        // 2. Node B receives P_A from Node A
        val actionB = nodeB.evaluatePacket(packetA)
        assertTrue("Node B must forward P_A", actionB is RelayAction.ForwardAndDeliver)
        val packetB = (actionB as RelayAction.ForwardAndDeliver).forwardedPacket
        assertEquals(2.toByte(), packetB.ttl)
        assertTrue(packetB.isForwarded)

        // 3. Node C receives P_B from Node B
        val actionC = nodeC.evaluatePacket(packetB)
        assertTrue("Node C must forward P_B", actionC is RelayAction.ForwardAndDeliver)
        val packetC = (actionC as RelayAction.ForwardAndDeliver).forwardedPacket
        assertEquals(1.toByte(), packetC.ttl)

        // 4. Node B overhears P_C re-broadcast from Node C
        val actionBOverhear = nodeB.evaluatePacket(packetC)
        assertTrue("Node B must drop duplicate P_C from Node C", actionBOverhear is RelayAction.DropDuplicate)

        // 5. Node A overhears P_B re-broadcast from Node B
        val nodeARouter = PacketRelayRouter(localDeviceId = nodeAId)
        val actionAOverhear = nodeARouter.evaluatePacket(packetB)
        assertTrue("Node A must drop self-originated packet P_B", actionAOverhear is RelayAction.DropSelf)

        // Verify stats on intermediate relay Node B
        val bStats = nodeB.getStats()
        assertEquals(1L, bStats.packetsReceived)
        assertEquals(1L, bStats.packetsForwarded)
        assertEquals(1L, bStats.duplicatesDropped)
        assertEquals(0L, bStats.ttlExpiredDropped)
    }
}
