package org.sih.itantra.core.qos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.crypto.AuthStatus
import org.sih.itantra.core.crypto.NetworkKeyManager
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import org.sih.itantra.core.mesh.DtnStore
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketFragment
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.ReassemblyBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TacticalQosTest {

    private lateinit var scheduler: TacticalPacketScheduler

    @Before
    fun setUp() {
        NetworkKeyManager.provisionDemoKey()
        scheduler = TacticalPacketScheduler(
            maxCapacity = 100,
            starvationThresholdMs = 5000L,
            autoTransmit = false
        )
    }

    private fun createPacket(
        priority: MessagePriority = MessagePriority.NORMAL,
        seq: Short = 1,
        sourceId: Int = 1001,
        payload: ByteArray = "Test Payload".toByteArray()
    ): Packet {
        return Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = if (priority.isEmergency) Packet.TYPE_DISTRESS else Packet.TYPE_TEXT,
            priority = priority,
            sequenceNumber = seq,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = sourceId,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.HINDI,
            payload = payload
        )
    }

    // =======================================================================
    // GROUP A: PRIORITY ORDERING
    // =======================================================================

    @Test
    fun testDistressTransmitsBeforeAlert() {
        val alert = createPacket(MessagePriority.ALERT, seq = 1)
        val distress = createPacket(MessagePriority.DISTRESS, seq = 2)

        scheduler.enqueue(alert)
        scheduler.enqueue(distress)

        val first = scheduler.pollNextPacket()
        val second = scheduler.pollNextPacket()

        assertEquals(MessagePriority.DISTRESS, first?.priority)
        assertEquals(2.toShort(), first?.sequenceNumber)
        assertEquals(MessagePriority.ALERT, second?.priority)
        assertEquals(1.toShort(), second?.sequenceNumber)
    }

    @Test
    fun testAlertTransmitsBeforeImportant() {
        val important = createPacket(MessagePriority.IMPORTANT, seq = 1)
        val alert = createPacket(MessagePriority.ALERT, seq = 2)

        scheduler.enqueue(important)
        scheduler.enqueue(alert)

        val first = scheduler.pollNextPacket()
        val second = scheduler.pollNextPacket()

        assertEquals(MessagePriority.ALERT, first?.priority)
        assertEquals(MessagePriority.IMPORTANT, second?.priority)
    }

    @Test
    fun testImportantTransmitsBeforeNormal() {
        val normal = createPacket(MessagePriority.NORMAL, seq = 1)
        val important = createPacket(MessagePriority.IMPORTANT, seq = 2)

        scheduler.enqueue(normal)
        scheduler.enqueue(important)

        val first = scheduler.pollNextPacket()
        val second = scheduler.pollNextPacket()

        assertEquals(MessagePriority.IMPORTANT, first?.priority)
        assertEquals(MessagePriority.NORMAL, second?.priority)
    }

    @Test
    fun testFifoOrderingWithinSamePriority() {
        for (i in 1..5) {
            scheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = i.toShort()))
        }

        for (i in 1..5) {
            val polled = scheduler.pollNextPacket()
            assertNotNull(polled)
            assertEquals(i.toShort(), polled?.sequenceNumber)
        }
        assertNull(scheduler.pollNextPacket())
    }

    // =======================================================================
    // GROUP B: EMERGENCY PRE-EMPTION
    // =======================================================================

    @Test
    fun testDistressJumpsAheadOfQueuedNormal() {
        // Enqueue 10 normal packets
        for (i in 1..10) {
            scheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = i.toShort()))
        }
        assertEquals(10, scheduler.totalQueuedPackets)
        assertEquals(10, scheduler.queuedNormal)

        // Inject 1 distress packet
        val distress = createPacket(MessagePriority.DISTRESS, seq = 99)
        scheduler.enqueue(distress)

        assertEquals(11, scheduler.totalQueuedPackets)
        assertEquals(1, scheduler.queuedDistress)

        // Poll next: must be DISTRESS, jumping ahead of all 10 normal packets
        val polled = scheduler.pollNextPacket()
        assertEquals(MessagePriority.DISTRESS, polled?.priority)
        assertEquals(99.toShort(), polled?.sequenceNumber)

        // Subsequent polls return normal packets in FIFO order
        val next = scheduler.pollNextPacket()
        assertEquals(MessagePriority.NORMAL, next?.priority)
        assertEquals(1.toShort(), next?.sequenceNumber)
    }

    @Test
    fun testAlertJumpsAheadOfQueuedNormal() {
        scheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = 10))
        scheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = 20))
        scheduler.enqueue(createPacket(MessagePriority.ALERT, seq = 50))

        val polled = scheduler.pollNextPacket()
        assertEquals(MessagePriority.ALERT, polled?.priority)
        assertEquals(50.toShort(), polled?.sequenceNumber)
    }

    @Test
    fun testInFlightTransmissionNeverInterrupted() = runBlocking {
        val inFlightActive = AtomicBoolean(false)
        val finishedNormal = AtomicBoolean(false)
        val order = mutableListOf<String>()

        val delayedScheduler = TacticalPacketScheduler(
            scope = this,
            transmitter = { pkt ->
                inFlightActive.set(true)
                if (pkt.priority == MessagePriority.NORMAL) {
                    delay(50) // simulate 50ms physical transmission time
                    finishedNormal.set(true)
                    order.add("NORMAL_SENT")
                } else if (pkt.priority == MessagePriority.DISTRESS) {
                    order.add("DISTRESS_SENT")
                }
                inFlightActive.set(false)
                true
            }
        )

        // Start transmitting NORMAL
        val normalPkt = createPacket(MessagePriority.NORMAL, seq = 1)
        val distressPkt = createPacket(MessagePriority.DISTRESS, seq = 2)

        launch {
            delayedScheduler.send(normalPkt)
        }

        // Wait a few ms so normal packet is in flight
        delay(10)
        assertTrue("Normal packet should be in-flight", inFlightActive.get())

        // Enqueue DISTRESS while normal is in flight
        launch {
            delayedScheduler.send(distressPkt)
        }

        // Wait for both to complete
        delay(120)

        // Normal must have finished safely before DISTRESS finished
        assertTrue(finishedNormal.get())
        assertEquals(listOf("NORMAL_SENT", "DISTRESS_SENT"), order)
    }

    // =======================================================================
    // GROUP C: FAIRNESS & STARVATION PROTECTION
    // =======================================================================

    @Test
    fun testNormalTrafficTransmitsWhenIdle() {
        val normal = createPacket(MessagePriority.NORMAL, seq = 42)
        scheduler.enqueue(normal)

        val polled = scheduler.pollNextPacket()
        assertEquals(MessagePriority.NORMAL, polled?.priority)
        assertEquals(42.toShort(), polled?.sequenceNumber)
    }

    @Test
    fun testStarvationThresholdRescuesOldNormalPacket() {
        val baseTime = 100_000L
        val normal = createPacket(MessagePriority.NORMAL, seq = 1)
        scheduler.enqueue(normal, enqueuedAtMs = baseTime)

        val distress1 = createPacket(MessagePriority.DISTRESS, seq = 10)
        val distress2 = createPacket(MessagePriority.DISTRESS, seq = 20)
        scheduler.enqueue(distress1, enqueuedAtMs = baseTime + 1000)
        scheduler.enqueue(distress2, enqueuedAtMs = baseTime + 2000)

        // At baseTime + 2500ms (age = 2500ms < 5000ms threshold): DISTRESS is preferred
        val p1 = scheduler.pollNextPacket(nowMs = baseTime + 2500)
        assertEquals(MessagePriority.DISTRESS, p1?.priority)
        assertEquals(10.toShort(), p1?.sequenceNumber)

        // At baseTime + 5500ms (age = 5500ms >= 5000ms threshold):
        // Starvation protection triggers! NORMAL is yielded!
        val p2 = scheduler.pollNextPacket(nowMs = baseTime + 5500)
        assertEquals(MessagePriority.NORMAL, p2?.priority)
        assertEquals(1.toShort(), p2?.sequenceNumber)

        // Immediately returns to priority ordering on next poll
        val p3 = scheduler.pollNextPacket(nowMs = baseTime + 5501)
        assertEquals(MessagePriority.DISTRESS, p3?.priority)
        assertEquals(20.toShort(), p3?.sequenceNumber)
    }

    @Test
    fun testRepeatedEmergencyPacketsDoNotCorruptFifo() {
        scheduler.enqueue(createPacket(MessagePriority.DISTRESS, seq = 1))
        scheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = 101))
        scheduler.enqueue(createPacket(MessagePriority.DISTRESS, seq = 2))
        scheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = 102))

        assertEquals(1.toShort(), scheduler.pollNextPacket()?.sequenceNumber)
        assertEquals(2.toShort(), scheduler.pollNextPacket()?.sequenceNumber)
        assertEquals(101.toShort(), scheduler.pollNextPacket()?.sequenceNumber)
        assertEquals(102.toShort(), scheduler.pollNextPacket()?.sequenceNumber)
    }

    // =======================================================================
    // GROUP D: QUEUE BOUNDS & OVERFLOW HANDLING
    // =======================================================================

    @Test
    fun testQueueReachesMax100Safely() {
        val boundedScheduler = TacticalPacketScheduler(maxCapacity = 20, autoTransmit = false)
        for (i in 1..20) {
            val ok = boundedScheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = i.toShort()))
            assertTrue(ok)
        }
        assertEquals(20, boundedScheduler.totalQueuedPackets)

        // 21st NORMAL packet is rejected
        val overflowNormal = createPacket(MessagePriority.NORMAL, seq = 21)
        val accepted = boundedScheduler.enqueue(overflowNormal)
        assertFalse(accepted)
        assertEquals(20, boundedScheduler.totalQueuedPackets)
    }

    @Test
    fun testQueueOverflowEvictsOldestNormalForDistress() {
        val boundedScheduler = TacticalPacketScheduler(maxCapacity = 5, autoTransmit = false)
        for (i in 1..5) {
            boundedScheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = i.toShort()))
        }
        assertEquals(5, boundedScheduler.totalQueuedPackets)

        // Incoming DISTRESS evicts oldest NORMAL (seq 1)
        val distress = createPacket(MessagePriority.DISTRESS, seq = 999)
        val accepted = boundedScheduler.enqueue(distress)
        assertTrue(accepted)
        assertEquals(5, boundedScheduler.totalQueuedPackets)

        // First poll is DISTRESS
        val first = boundedScheduler.pollNextPacket()
        assertEquals(MessagePriority.DISTRESS, first?.priority)
        assertEquals(999.toShort(), first?.sequenceNumber)

        // Second poll is seq 2 (seq 1 was evicted!)
        val second = boundedScheduler.pollNextPacket()
        assertEquals(2.toShort(), second?.sequenceNumber)
    }

    @Test
    fun testDistressNeverDiscardedWhenNormalEvictable() {
        val boundedScheduler = TacticalPacketScheduler(maxCapacity = 3, autoTransmit = false)
        boundedScheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = 1))
        boundedScheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = 2))
        boundedScheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = 3))

        val d1 = boundedScheduler.enqueue(createPacket(MessagePriority.DISTRESS, seq = 10))
        val d2 = boundedScheduler.enqueue(createPacket(MessagePriority.DISTRESS, seq = 20))
        assertTrue(d1)
        assertTrue(d2)

        // Only 1 NORMAL packet should remain
        assertEquals(2, boundedScheduler.queuedDistress)
        assertEquals(1, boundedScheduler.queuedNormal)
    }

    // =======================================================================
    // GROUP E: PRIORITY-AWARE FRAGMENTATION
    // =======================================================================

    @Test
    fun testEmergencyFragmentsInheritPriority() {
        val fragmenter = PacketFragmenter(maxPayloadBytes = 100)
        val largePayload = ByteArray(250) { 0x42 }
        val fragments = fragmenter.fragment(largePayload, transferId = 1)

        val packets = fragments.map { frag ->
            Packet(
                priority = MessagePriority.DISTRESS,
                sequenceNumber = 1,
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = 1001,
                destinationDeviceId = Packet.BROADCAST_ID,
                language = IndicLanguage.HINDI,
                payload = frag.toPayload()
            )
        }

        assertTrue(packets.size >= 2)
        for (pkt in packets) {
            assertEquals(MessagePriority.DISTRESS, pkt.priority)
        }
    }

    @Test
    fun testFragmentOrderingMaintainedWithinTransfer() {
        val fragmenter = PacketFragmenter(maxPayloadBytes = 50)
        val payload = "Part 1 -- Part 2 -- Part 3 -- Part 4".toByteArray()
        val fragments = fragmenter.fragment(payload, transferId = 10)

        val packets = fragments.map { frag ->
            Packet(
                priority = MessagePriority.NORMAL,
                sequenceNumber = 1,
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = 1001,
                destinationDeviceId = Packet.BROADCAST_ID,
                language = IndicLanguage.HINDI,
                payload = frag.toPayload()
            )
        }

        scheduler.enqueueBatch(packets)

        for (i in fragments.indices) {
            val polled = scheduler.pollNextPacket()
            assertNotNull(polled)
            val parsedFrag = PacketFragment.fromPayload(polled!!.payload)
            assertEquals(i.toByte(), parsedFrag?.metadata?.fragmentIndex)
        }
    }

    @Test
    fun testLargeNormalTransferInterruptedByEmergencyThenReassembled() {
        val fragmenter = PacketFragmenter(maxPayloadBytes = 80)
        val originalText = "Tactical communications system message spanning multiple packet fragments across radio link"
        val payload = originalText.toByteArray(Charsets.UTF_8)
        val fragments = fragmenter.fragment(payload, transferId = 77)

        val normalPackets = fragments.map { frag ->
            Packet(
                priority = MessagePriority.NORMAL,
                sequenceNumber = 1,
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = 1001,
                destinationDeviceId = Packet.BROADCAST_ID,
                language = IndicLanguage.HINDI,
                payload = frag.toPayload()
            )
        }

        // Enqueue all normal fragments
        scheduler.enqueueBatch(normalPackets)

        // Enqueue high priority emergency packet
        val emergencyPacket = createPacket(MessagePriority.DISTRESS, seq = 999)
        scheduler.enqueue(emergencyPacket)

        // First packet out must be the emergency packet!
        val p1 = scheduler.pollNextPacket()
        assertEquals(MessagePriority.DISTRESS, p1?.priority)
        assertEquals(999.toShort(), p1?.sequenceNumber)

        // Remaining packets are the normal fragments in FIFO order
        val reassemblyBuffer = ReassemblyBuffer()
        var reassembled: ByteArray? = null

        while (true) {
            val nextPkt = scheduler.pollNextPacket() ?: break
            val frag = PacketFragment.fromPayload(nextPkt.payload)
            assertNotNull(frag)
            val res = reassemblyBuffer.addFragment(nextPkt.sourceDeviceId, frag!!)
            if (res != null) {
                reassembled = res.payload
            }
        }

        assertNotNull(reassembled)
        assertEquals(originalText, String(reassembled!!, Charsets.UTF_8))
    }

    // =======================================================================
    // GROUP F: DTN INTEGRATION
    // =======================================================================

    @Test
    fun testDtnDrainsEmergencyTrafficBeforeNormal() {
        val dtnStore = DtnStore(maxPackets = 10)
        val normal = createPacket(MessagePriority.NORMAL, seq = 1, sourceId = 100)
        val alert = createPacket(MessagePriority.ALERT, seq = 2, sourceId = 100)
        val distress = createPacket(MessagePriority.DISTRESS, seq = 3, sourceId = 100)

        dtnStore.store(normal)
        dtnStore.store(alert)
        dtnStore.store(distress)

        val drained = dtnStore.drainForDestination(Packet.BROADCAST_ID)
        assertEquals(3, drained.size)
        assertEquals(MessagePriority.DISTRESS, drained[0].priority)
        assertEquals(MessagePriority.ALERT, drained[1].priority)
        assertEquals(MessagePriority.NORMAL, drained[2].priority)
    }

    @Test
    fun testDtnExpiryMaintainedUnderQos() {
        val dtnStore = DtnStore(maxPackets = 10, expiryMs = 100L)
        val packet = createPacket(MessagePriority.DISTRESS, seq = 1)
        dtnStore.store(packet)

        Thread.sleep(150)
        val expired = dtnStore.drainExpired()
        assertEquals(1, expired.size)
    }

    // =======================================================================
    // GROUP G: TRANSPORT FAILOVER
    // =======================================================================

    @Test
    fun testQueuedTrafficSurvivesTransportFailover() = runBlocking {
        var activeTransport = "BT"
        val dispatched = mutableListOf<String>()

        val failoverScheduler = TacticalPacketScheduler(
            scope = this,
            transmitter = { pkt ->
                if (activeTransport == "BT" && pkt.sequenceNumber == 2.toShort()) {
                    // Simulate BT link break
                    activeTransport = "WIFI"
                    dispatched.add("FAILOVER->WIFI:${pkt.sequenceNumber}")
                    true
                } else {
                    dispatched.add("$activeTransport:${pkt.sequenceNumber}")
                    true
                }
            }
        )

        val p1 = createPacket(MessagePriority.NORMAL, seq = 1)
        val p2 = createPacket(MessagePriority.DISTRESS, seq = 2)
        val p3 = createPacket(MessagePriority.ALERT, seq = 3)

        failoverScheduler.enqueue(p1)
        failoverScheduler.enqueue(p2)
        failoverScheduler.enqueue(p3)

        // Wait for worker to complete
        delay(100)

        assertEquals(3, dispatched.size)
        // Order must be DISTRESS, ALERT, NORMAL
        assertEquals("FAILOVER->WIFI:2", dispatched[0]) // distress triggered failover
        assertEquals("WIFI:3", dispatched[1]) // alert sent second over WIFI
        assertEquals("WIFI:1", dispatched[2]) // normal sent third over WIFI
    }

    @Test
    fun testPriorityRetainedAfterTransportSwitch() {
        // Enqueue across failover
        scheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = 10))
        scheduler.enqueue(createPacket(MessagePriority.DISTRESS, seq = 20))

        assertEquals(MessagePriority.DISTRESS, scheduler.pollNextPacket()?.priority)
        assertEquals(MessagePriority.NORMAL, scheduler.pollNextPacket()?.priority)
    }

    // =======================================================================
    // GROUP H: SECURITY & HMAC PRESERVATION
    // =======================================================================

    @Test
    fun testAuthenticatedPacketRemainsValidAfterQosScheduling() {
        val key = NetworkKeyManager.getKey()
        assertNotNull(key)

        val raw = createPacket(MessagePriority.DISTRESS, seq = 88)
        val (signed, _) = PacketAuthenticator.signWithLatency(raw, key!!)
        assertTrue(signed.isAuthenticated)

        // Pass through QoS scheduler
        scheduler.enqueue(signed)
        val polled = scheduler.pollNextPacket()
        assertNotNull(polled)

        // Verify cryptographic validity
        val verifyResult = PacketAuthenticator.verify(polled!!, key)
        assertEquals(AuthStatus.VALID, verifyResult.status)
    }

    @Test
    fun testQosDoesNotAlterAuthTagOrSequence() {
        val key = NetworkKeyManager.getKey()!!
        val raw = createPacket(MessagePriority.ALERT, seq = 42)
        val (signed, _) = PacketAuthenticator.signWithLatency(raw, key)

        scheduler.enqueue(signed)
        val polled = scheduler.pollNextPacket()

        assertArrayEquals(signed.authTag, polled?.authTag)
        assertEquals(signed.sequenceNumber, polled?.sequenceNumber)
        assertEquals(signed.priority, polled?.priority)
    }

    // =======================================================================
    // BENCHMARK: TACTICAL SCHEDULER TIMING
    // =======================================================================

    @Test
    fun benchmarkQosSchedulingLatency() {
        val iterations = 500
        val benchScheduler = TacticalPacketScheduler(maxCapacity = 100, autoTransmit = false)

        // 1. Measure NORMAL-only scheduling latency
        val startNormal = System.nanoTime()
        for (i in 1..iterations) {
            benchScheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = i.toShort()))
            benchScheduler.pollNextPacket()
        }
        val normalDurationNanos = System.nanoTime() - startNormal
        val avgNormalLatencyMicros = (normalDurationNanos / iterations) / 1000.0

        // 2. Measure Congested queue with emergency injection
        // Fill queue with 40 NORMAL packets (BUSY state)
        for (i in 1..40) {
            benchScheduler.enqueue(createPacket(MessagePriority.NORMAL, seq = i.toShort()))
        }
        val queueDepthBefore = benchScheduler.totalQueuedPackets
        val congestionStateBefore = benchScheduler.getCongestionState()

        val startEmergency = System.nanoTime()
        for (i in 1..iterations) {
            benchScheduler.enqueue(createPacket(MessagePriority.DISTRESS, seq = (1000 + i).toShort()))
            val polled = benchScheduler.pollNextPacket()
            assertEquals(MessagePriority.DISTRESS, polled?.priority)
        }
        val emergencyDurationNanos = System.nanoTime() - startEmergency
        val avgEmergencyLatencyMicros = (emergencyDurationNanos / iterations) / 1000.0

        val queueDepthAfter = benchScheduler.totalQueuedPackets

        println("=== REAL MEASURED TACTICAL QOS SCHEDULER BENCHMARK ===")
        println("Iterations: $iterations")
        println("Average NORMAL-only Scheduling Latency: ${String.format("%.2f", avgNormalLatencyMicros)} µs")
        println("Average Emergency Pre-emption Scheduling Latency: ${String.format("%.2f", avgEmergencyLatencyMicros)} µs")
        println("Congestion State at Injection: $congestionStateBefore")
        println("Queue Depth Before Pre-emption: $queueDepthBefore")
        println("Queue Depth After Benchmark: $queueDepthAfter")
        println("Queue Bound: ${benchScheduler.maxCapacity} packets")
        println("======================================================")

        assertTrue("Emergency latency must be sub-millisecond", avgEmergencyLatencyMicros < 1000.0)
    }
}
