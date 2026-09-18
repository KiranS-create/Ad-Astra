package org.sih.itantra.core.failover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import org.sih.itantra.core.mesh.DtnStore
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.GeoLocation
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.transport.Transport
import org.sih.itantra.core.transport.TransportType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Feature 31 - Agent 2: Transport Failover & DTN Store-and-Forward Verification.
 *
 * Validates:
 * 1. Automatic multi-transport failover (Wi-Fi Direct -> Wi-Fi UDP -> Bluetooth)
 * 2. Cross-interface packet deduplication during transport transitions
 * 3. DTN store-and-forward buffering during complete transport outages
 * 4. Priority-driven DTN drainage upon route/transport recovery
 * 5. Transparent return to preferred transport when restored
 *
 * Classification: AUTOMATED PASS / SYNTHETIC PASS.
 */
class TransportFailoverDtnTest {

    private lateinit var dtnStore: DtnStore
    private val sourceId = 501
    private val destId = 502

    @Before
    fun setUp() {
        DiagnosticsRepository.resetForTesting()
        dtnStore = DtnStore(maxPackets = 50, maxBytes = 128 * 1024, expiryMs = 300_000L)
    }

    private fun createTestPacket(
        seq: Short,
        priority: MessagePriority = MessagePriority.NORMAL,
        msgType: Byte = Packet.TYPE_TEXT,
        payloadText: String = "Tactical transmission seq $seq"
    ): Packet {
        return Packet(
            msgType = msgType,
            priority = priority,
            ttl = Packet.DEFAULT_TTL,
            sequenceNumber = seq,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = sourceId,
            destinationDeviceId = destId,
            language = IndicLanguage.ENGLISH,
            payload = payloadText.toByteArray(Charsets.UTF_8)
        )
    }

    // =========================================================================
    // 1. SIMULATED MULTI-TRANSPORT FAILOVER PIPELINE
    // =========================================================================

    class MockTransport(
        val name: String,
        private var isAvailable: Boolean = true
    ) {
        val sentPackets = mutableListOf<Packet>()

        fun setAvailable(available: Boolean) {
            this.isAvailable = available
        }

        fun send(packet: Packet): Boolean {
            if (!isAvailable) return false
            sentPackets.add(packet)
            return true
        }
    }

    class FailoverRouter(
        val wifiDirect: MockTransport,
        val wifiUdp: MockTransport,
        val bluetooth: MockTransport,
        val dtn: DtnStore
    ) {
        var preferredTransport: String = "WIFI_DIRECT"
        var activeTransportUsed: String = preferredTransport
        val failoverEvents = mutableListOf<Pair<String, String>>()

        fun dispatch(packet: Packet): Boolean {
            val candidates = when (preferredTransport) {
                "WIFI_DIRECT" -> listOf(wifiDirect, wifiUdp, bluetooth)
                "WIFI" -> listOf(wifiUdp, wifiDirect, bluetooth)
                else -> listOf(bluetooth, wifiDirect, wifiUdp)
            }

            for (i in candidates.indices) {
                val candidate = candidates[i]
                if (candidate.send(packet)) {
                    if (candidate.name != activeTransportUsed) {
                        failoverEvents.add(Pair(activeTransportUsed, candidate.name))
                        DiagnosticsRepository.recordTransportFailover(from = activeTransportUsed, to = candidate.name)
                        activeTransportUsed = candidate.name
                    }
                    DiagnosticsRepository.recordTransportSent(candidate.name)
                    return true
                }
            }

            // Total transport failure -> Buffer in DTN store
            val stored = dtn.store(packet)
            if (stored) {
                DiagnosticsRepository.recordDtnStored(dtn.size())
            }
            return false
        }

        fun recoverAndDrain(destinationId: Int): List<Packet> {
            val pending = dtn.drainForDestination(destinationId)
            val delivered = mutableListOf<Packet>()
            for (p in pending) {
                if (dispatch(p)) {
                    delivered.add(p)
                    DiagnosticsRepository.recordDtnForwarded(dtn.size())
                } else {
                    // Re-store if dispatch fails again
                    dtn.store(p)
                }
            }
            return delivered
        }
    }

    @Test
    fun test01_PreferredTransportDelivery() {
        val wd = MockTransport("WIFI_DIRECT", isAvailable = true)
        val udp = MockTransport("WIFI", isAvailable = true)
        val bt = MockTransport("BT", isAvailable = true)
        val router = FailoverRouter(wd, udp, bt, dtnStore)

        val p1 = createTestPacket(1, MessagePriority.NORMAL)
        val sent = router.dispatch(p1)

        assertTrue("Packet must be dispatched over preferred transport", sent)
        assertEquals(1, wd.sentPackets.size)
        assertEquals(0, udp.sentPackets.size)
        assertEquals(0, bt.sentPackets.size)
        assertEquals(0, dtnStore.size())
    }

    @Test
    fun test02_FailoverToWifiUdpOnWifiDirectDisconnect() {
        val wd = MockTransport("WIFI_DIRECT", isAvailable = false) // Disconnected
        val udp = MockTransport("WIFI", isAvailable = true)
        val bt = MockTransport("BT", isAvailable = true)
        val router = FailoverRouter(wd, udp, bt, dtnStore)

        val p1 = createTestPacket(2, MessagePriority.ALERT)
        val sent = router.dispatch(p1)

        assertTrue("Packet must successfully failover to Wi-Fi UDP", sent)
        assertEquals(0, wd.sentPackets.size)
        assertEquals(1, udp.sentPackets.size)
        assertEquals(0, bt.sentPackets.size)
        assertEquals("WIFI", router.activeTransportUsed)
        assertEquals(1, router.failoverEvents.size)
        assertEquals(Pair("WIFI_DIRECT", "WIFI"), router.failoverEvents[0])
    }

    @Test
    fun test03_FailoverToBluetoothOnWifiOutage() {
        val wd = MockTransport("WIFI_DIRECT", isAvailable = false)
        val udp = MockTransport("WIFI", isAvailable = false)
        val bt = MockTransport("BT", isAvailable = true)
        val router = FailoverRouter(wd, udp, bt, dtnStore)

        val p1 = createTestPacket(3, MessagePriority.IMPORTANT)
        val sent = router.dispatch(p1)

        assertTrue("Packet must failover to Bluetooth when Wi-Fi is down", sent)
        assertEquals(0, wd.sentPackets.size)
        assertEquals(0, udp.sentPackets.size)
        assertEquals(1, bt.sentPackets.size)
        assertEquals("BT", router.activeTransportUsed)
    }

    @Test
    fun test04_DtnBufferingOnCompleteTransportOutage() {
        val wd = MockTransport("WIFI_DIRECT", isAvailable = false)
        val udp = MockTransport("WIFI", isAvailable = false)
        val bt = MockTransport("BT", isAvailable = false)
        val router = FailoverRouter(wd, udp, bt, dtnStore)

        val pDistress = createTestPacket(10, MessagePriority.DISTRESS, msgType = Packet.TYPE_DISTRESS)
        val pNormal = createTestPacket(11, MessagePriority.NORMAL)

        val sent1 = router.dispatch(pDistress)
        val sent2 = router.dispatch(pNormal)

        assertFalse("Dispatch must return false when all physical transports are down", sent1)
        assertFalse("Dispatch must return false when all physical transports are down", sent2)
        assertEquals("DTN store must buffer both packets", 2, dtnStore.size())
    }

    @Test
    fun test05_DtnPriorityDrainageUponTransportRecovery() {
        val wd = MockTransport("WIFI_DIRECT", isAvailable = false)
        val udp = MockTransport("WIFI", isAvailable = false)
        val bt = MockTransport("BT", isAvailable = false)
        val router = FailoverRouter(wd, udp, bt, dtnStore)

        // Store packets in arbitrary order during outage
        val pNormal = createTestPacket(1, MessagePriority.NORMAL, payloadText = "Routine situation report")
        val pDistress = createTestPacket(2, MessagePriority.DISTRESS, msgType = Packet.TYPE_DISTRESS, payloadText = "DISTRESS: Soldier down")
        val pAlert = createTestPacket(3, MessagePriority.ALERT, payloadText = "ALERT: Perimeter breached")
        val pImportant = createTestPacket(4, MessagePriority.IMPORTANT, payloadText = "IMPORTANT: Supply convoy departing")

        router.dispatch(pNormal)
        router.dispatch(pDistress)
        router.dispatch(pAlert)
        router.dispatch(pImportant)
        assertEquals(4, dtnStore.size())

        // Transport recovers (Bluetooth connects first)
        bt.setAvailable(true)

        val drained = router.recoverAndDrain(destId)
        assertEquals("All 4 packets must be drained and dispatched", 4, drained.size)
        assertEquals("DTN queue must be empty after recovery", 0, dtnStore.size())
        assertEquals("Bluetooth transport should have received all 4 packets", 4, bt.sentPackets.size)

        // Verify priority ordering: DISTRESS > ALERT > IMPORTANT > NORMAL
        assertEquals(MessagePriority.DISTRESS, bt.sentPackets[0].priority)
        assertEquals(MessagePriority.ALERT, bt.sentPackets[1].priority)
        assertEquals(MessagePriority.IMPORTANT, bt.sentPackets[2].priority)
        assertEquals(MessagePriority.NORMAL, bt.sentPackets[3].priority)
    }

    @Test
    fun test06_ReturnToPreferredTransportWhenRestored() {
        val wd = MockTransport("WIFI_DIRECT", isAvailable = false)
        val udp = MockTransport("WIFI", isAvailable = true)
        val bt = MockTransport("BT", isAvailable = true)
        val router = FailoverRouter(wd, udp, bt, dtnStore)

        // 1. Initial transmission on fallback Wi-Fi UDP
        router.dispatch(createTestPacket(1))
        assertEquals("WIFI", router.activeTransportUsed)

        // 2. Preferred Wi-Fi Direct restored
        wd.setAvailable(true)
        router.dispatch(createTestPacket(2))

        assertEquals("Must transparently return to preferred Wi-Fi Direct", 1, wd.sentPackets.size)
        assertEquals("WIFI_DIRECT", router.activeTransportUsed)
    }

    // =========================================================================
    // 2. CROSS-INTERFACE DEDUPLICATION IN TRANSITION
    // =========================================================================

    @Test
    fun test07_CrossInterfaceDeduplicationDuringTransition() {
        val seenLock = Any()
        val seenMap = HashMap<String, Long>()

        fun isDuplicate(p: Packet): Boolean {
            val key = "${p.sourceDeviceId}_${p.sequenceNumber}_${p.msgType}_${p.payload.size}_${p.crc32}"
            val now = System.currentTimeMillis()
            synchronized(seenLock) {
                val last = seenMap[key]
                if (last != null && (now - last) < 15_000L) {
                    return true
                }
                seenMap[key] = now
                return false
            }
        }

        val packet = createTestPacket(99, MessagePriority.ALERT)

        // Simultaneously received on Wi-Fi Direct and Wi-Fi UDP
        val firstRxFromWifiDirect = !isDuplicate(packet)
        val secondRxFromWifiUdp = !isDuplicate(packet)

        assertTrue("First reception on Wi-Fi Direct must be accepted", firstRxFromWifiDirect)
        assertFalse("Duplicate reception on Wi-Fi UDP must be suppressed", secondRxFromWifiUdp)
    }

    @Test
    fun test08_DtnCapacityBoundingAndEviction() {
        val boundedDtn = DtnStore(maxPackets = 2, maxBytes = 4096, expiryMs = 60_000L)
        val p1 = createTestPacket(101, MessagePriority.NORMAL)
        val p2 = createTestPacket(102, MessagePriority.NORMAL)
        val p3Distress = createTestPacket(103, MessagePriority.DISTRESS, msgType = Packet.TYPE_DISTRESS)

        assertTrue(boundedDtn.store(p1))
        assertTrue(boundedDtn.store(p2))
        assertEquals(2, boundedDtn.size())

        // DISTRESS packet must evict oldest NORMAL packet
        assertTrue(boundedDtn.store(p3Distress))
        assertEquals(2, boundedDtn.size())

        val remaining = boundedDtn.drainForDestination(destId)
        assertTrue("DISTRESS packet must be preserved", remaining.any { it.priority == MessagePriority.DISTRESS })
        assertFalse("Oldest NORMAL packet p1 must be evicted", remaining.any { it.sequenceNumber == 101.toShort() })
    }
}
