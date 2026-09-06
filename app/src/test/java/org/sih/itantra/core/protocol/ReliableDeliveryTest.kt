package org.sih.itantra.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.mesh.DtnStore
import org.sih.itantra.core.mesh.PacketRelayRouter
import org.sih.itantra.core.mesh.RelayAction

class ReliableDeliveryTest {

    // =======================================================================
    // GROUP A — FRAGMENTATION THRESHOLDS
    // =======================================================================

    @Test
    fun test_A1_smallMessageRemainsUnfragmented() {
        val payload = "Short text message".toByteArray(Charsets.UTF_8)
        assertFalse("Payload <= 128 bytes should not need fragmentation", PacketFragmenter.needsFragmentation(payload.size))
        val fragmenter = PacketFragmenter(Packet.MAX_FRAGMENT_PAYLOAD)
        val fragments = fragmenter.fragment(payload, transferId = 1)
        assertTrue("Fragments list should be empty for unfragmented payload", fragments.isEmpty())
    }

    @Test
    fun test_A2_largeMessageCreatesCorrectFragmentCount() {
        // 420 bytes payload with 128-byte fragment size -> 4 fragments (128 + 128 + 128 + 36)
        val payload = ByteArray(420) { (it % 100).toByte() }
        assertTrue(PacketFragmenter.needsFragmentation(payload.size))
        val fragmenter = PacketFragmenter(Packet.MAX_FRAGMENT_PAYLOAD)
        val fragments = fragmenter.fragment(payload, transferId = 0x1234.toShort())

        assertEquals(4, fragments.size)
        assertEquals(128, fragments[0].data.size)
        assertEquals(128, fragments[1].data.size)
        assertEquals(128, fragments[2].data.size)
        assertEquals(36, fragments[3].data.size)

        for (i in 0 until 4) {
            assertEquals(0x1234.toShort(), fragments[i].metadata.transferId)
            assertEquals(i.toByte(), fragments[i].metadata.fragmentIndex)
            assertEquals(4.toByte(), fragments[i].metadata.fragmentCount)
        }
    }

    @Test
    fun test_A3_exactThresholdCaseProducesNoFragments() {
        val payload = ByteArray(128) { 0x42 }
        assertFalse("Exact 128 bytes should not be fragmented", PacketFragmenter.needsFragmentation(payload.size))
        val fragmenter = PacketFragmenter(128)
        val fragments = fragmenter.fragment(payload, transferId = 2)
        assertTrue(fragments.isEmpty())
    }

    @Test
    fun test_A4_thresholdPlusOneCreatesAdditionalFragment() {
        val payload = ByteArray(129) { 0x42 }
        assertTrue("129 bytes must be fragmented", PacketFragmenter.needsFragmentation(payload.size))
        val fragmenter = PacketFragmenter(128)
        val fragments = fragmenter.fragment(payload, transferId = 3)
        assertEquals(2, fragments.size)
        assertEquals(128, fragments[0].data.size)
        assertEquals(1, fragments[1].data.size)
    }

    // =======================================================================
    // GROUP B — MULTILINGUAL UTF-8 INTEGRITY
    // =======================================================================

    @Test
    fun test_B1_tamilFragmentedMessagePreservesUtf8Integrity() {
        val tamilText = "வெள்ளப்பெருக்கு அவசர உதவி தேவை! மக்கள் அனைவரும் உடனடியாக மேடான பகுதிக்கு செல்லவும். மீட்பு குழுவினர் விரைந்து வருகின்றனர்."
        val rawBytes = tamilText.toByteArray(Charsets.UTF_8)
        assertTrue("Tamil text must exceed 128 bytes to test fragmentation", rawBytes.size > 128)

        val fragmenter = PacketFragmenter(64) // Force more fragments for rigorous testing
        val fragments = fragmenter.fragment(rawBytes, transferId = 101)
        val buffer = ReassemblyBuffer()

        // Feed out of order: 2, 0, 1, 3, ...
        val shuffled = fragments.shuffled()
        var result: ReassemblyResult? = null
        for (f in shuffled) {
            result = buffer.addFragment(sourceDeviceId = 5001, fragment = f)
        }

        assertNotNull("Reassembly should complete", result)
        assertArrayEquals(rawBytes, result!!.payload)
        val decodedText = String(result.payload, Charsets.UTF_8)
        assertEquals(tamilText, decodedText)
    }

    @Test
    fun test_B2_malayalamFragmentedMessagePreservesUtf8Integrity() {
        val malayalamText = "തീപിടുത്തം അടിയന്തര രക്ഷാപ്രവർത്തനം ആവശ്യമാണ് ഉടൻ സഹായം എത്തിക്കുക. അഗ്നിശമന സേനാംഗങ്ങൾ വഴിയിലാണ്."
        val rawBytes = malayalamText.toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(64)
        val fragments = fragmenter.fragment(rawBytes, transferId = 102)
        val buffer = ReassemblyBuffer()

        var result: ReassemblyResult? = null
        for (f in fragments.reversed()) {
            result = buffer.addFragment(sourceDeviceId = 5002, fragment = f)
        }

        assertNotNull(result)
        val decodedText = String(result!!.payload, Charsets.UTF_8)
        assertEquals(malayalamText, decodedText)
    }

    @Test
    fun test_B3_hindiFragmentedMessagePreservesUtf8Integrity() {
        val hindiText = "गंभीर बाढ़ की स्थिति तत्काल चिकित्सा सहायता और बचाव दल की आवश्यकता है। सभी नागरिक सुरक्षित स्थानों पर रहें।"
        val rawBytes = hindiText.toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(64)
        val fragments = fragmenter.fragment(rawBytes, transferId = 103)
        val buffer = ReassemblyBuffer()

        var result: ReassemblyResult? = null
        for (f in fragments) {
            result = buffer.addFragment(sourceDeviceId = 5003, fragment = f)
        }

        assertNotNull(result)
        val decodedText = String(result!!.payload, Charsets.UTF_8)
        assertEquals(hindiText, decodedText)
    }

    @Test
    fun test_B4_mixedLanguageMessagePreservesUtf8Integrity() {
        val mixedText = "EMERGENCY: உதவி தேவை (Tamil) | मदद चाहिए (Hindi) | സഹായം വേണം (Malayalam) | ALL UNITS RESPOND IMMEDIATELY TO SECTOR 7"
        val rawBytes = mixedText.toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(128)
        val fragments = fragmenter.fragment(rawBytes, transferId = 104)
        val buffer = ReassemblyBuffer()

        var result: ReassemblyResult? = null
        for (f in fragments) {
            result = buffer.addFragment(sourceDeviceId = 5004, fragment = f)
        }

        assertNotNull(result)
        val decodedText = String(result!!.payload, Charsets.UTF_8)
        assertEquals(mixedText, decodedText)
    }

    // =======================================================================
    // GROUP C — REASSEMBLY ENGINE & OUT-OF-ORDER / DUPLICATE HANDLING
    // =======================================================================

    @Test
    fun test_C1_orderedFragmentsReassembleSuccessfully() {
        val data = "Chunk-0---Chunk-1---Chunk-2---End".toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(10)
        val fragments = fragmenter.fragment(data, transferId = 201)
        val buffer = ReassemblyBuffer()

        for (i in 0 until fragments.size - 1) {
            assertNull(buffer.addFragment(100, fragments[i]))
        }
        val complete = buffer.addFragment(100, fragments.last())
        assertNotNull(complete)
        assertArrayEquals(data, complete!!.payload)
    }

    @Test
    fun test_C2_outOfOrderFragmentsReassembleSuccessfully() {
        val data = "Out of order packet reconstruction test over radio channel".toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(12)
        val fragments = fragmenter.fragment(data, transferId = 202)
        assertEquals(5, fragments.size)

        val buffer = ReassemblyBuffer()
        // Feed: 3, 1, 4, 0, 2
        assertNull(buffer.addFragment(100, fragments[3]))
        assertNull(buffer.addFragment(100, fragments[1]))
        assertNull(buffer.addFragment(100, fragments[4]))
        assertNull(buffer.addFragment(100, fragments[0]))

        val complete = buffer.addFragment(100, fragments[2])
        assertNotNull(complete)
        assertArrayEquals(data, complete!!.payload)
    }

    @Test
    fun test_C3_duplicateFragmentHandledSafely() {
        val data = "Duplicate fragment test payload bytes".toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(10)
        val fragments = fragmenter.fragment(data, transferId = 203)
        val buffer = ReassemblyBuffer()

        // Feed fragment 0 twice
        assertNull(buffer.addFragment(100, fragments[0]))
        assertNull(buffer.addFragment(100, fragments[0])) // duplicate, ignored

        for (i in 1 until fragments.size - 1) {
            assertNull(buffer.addFragment(100, fragments[i]))
            assertNull(buffer.addFragment(100, fragments[i])) // duplicate, ignored
        }

        val complete = buffer.addFragment(100, fragments.last())
        assertNotNull(complete)
        assertArrayEquals(data, complete!!.payload)
    }

    @Test
    fun test_C4_missingFragmentDoesNotDeliver() {
        val data = "Missing fragment test payload".toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(10)
        val fragments = fragmenter.fragment(data, transferId = 204)
        val buffer = ReassemblyBuffer()

        // Skip fragment 1: feed 0, 2
        assertNull(buffer.addFragment(100, fragments[0]))
        assertNull(buffer.addFragment(100, fragments[2]))
        assertEquals(1, buffer.pendingCount())
    }

    @Test
    fun test_C5_timeoutEvictionDiscardsIncompleteTransfer() {
        val data = "Timeout eviction test payload".toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(10)
        val fragments = fragmenter.fragment(data, transferId = 205)
        val buffer = ReassemblyBuffer(timeoutMs = 30_000L)

        val t0 = 1_000_000L
        buffer.addFragment(100, fragments[0], nowMs = t0)
        assertEquals(1, buffer.pendingCount())

        // At t0 + 10s: still present
        val pruned10s = buffer.pruneExpired(nowMs = t0 + 10_000L)
        assertEquals(0, pruned10s)
        assertEquals(1, buffer.pendingCount())

        // At t0 + 31s: timed out and evicted
        val pruned31s = buffer.pruneExpired(nowMs = t0 + 31_000L)
        assertEquals(1, pruned31s)
        assertEquals(0, buffer.pendingCount())
    }

    @Test
    fun test_C6_malformedFragmentMetadataSafelyRejected() {
        val buffer = ReassemblyBuffer()
        // fragmentCount = 1 (invalid, must be >= 2 for fragmented transfer)
        val badMeta1 = FragmentMetadata(1, 0, 1, Packet.TYPE_TEXT, 0)
        assertNull(buffer.addFragment(100, PacketFragment(badMeta1, byteArrayOf(1, 2))))

        // fragmentIndex >= fragmentCount
        val badMeta2 = FragmentMetadata(1, 5, 3, Packet.TYPE_TEXT, 0)
        assertNull(buffer.addFragment(100, PacketFragment(badMeta2, byteArrayOf(1, 2))))
    }

    @Test
    fun test_C7_oversizedTransferRejected() {
        // Hard limit is 16 KB (16,384 bytes).
        // 130 fragments * 128 bytes = 16,640 bytes (exceeds 16 KB)
        val buffer = ReassemblyBuffer(maxReassembledBytes = 16 * 1024)
        val badMeta = FragmentMetadata(transferId = 1, fragmentIndex = 0, fragmentCount = 128.toByte(), originalMsgType = Packet.TYPE_TEXT, originalFlags = 0)
        // Note: 128 fragments * 128 B = 16,384 B (at threshold)
        // But if projected exceeds hard limit:
        val strictBuffer = ReassemblyBuffer(maxReassembledBytes = 100)
        val fragMeta = FragmentMetadata(1, 0, 2, Packet.TYPE_TEXT, 0)
        val frag = PacketFragment(fragMeta, ByteArray(60))
        assertNull(strictBuffer.addFragment(100, frag)) // 2 * 128 = 256 > 100 -> rejected!
    }

    // =======================================================================
    // GROUP D — CRC32 INTEGRITY ON FRAGMENTS
    // =======================================================================

    @Test
    fun test_D1_corruptFragmentFailsCrcAndIsRejected() {
        val originalText = "CRC-32 protection test across radio fragments"
        val payload = originalText.toByteArray(Charsets.UTF_8)
        val fragmenter = PacketFragmenter(16)
        val fragments = fragmenter.fragment(payload, transferId = 301)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.NORMAL,
            flags = Packet.FLAG_FRAGMENTED.toByte(),
            sequenceNumber = 501,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 100,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = fragments[0].toPayload()
        )

        val wireBytes = PacketSerializer.serialize(packet)
        // Corrupt single byte in payload
        wireBytes[Packet.HEADER_SIZE_BYTES + 2] = (wireBytes[Packet.HEADER_SIZE_BYTES + 2] + 1).toByte()

        var caughtCorrupt = false
        try {
            PacketSerializer.deserialize(wireBytes)
        } catch (e: CorruptPacketException) {
            caughtCorrupt = true
        }
        assertTrue("Corrupted fragment byte must trigger CorruptPacketException", caughtCorrupt)
    }

    // =======================================================================
    // GROUP E — MANET RELAY COMPATIBILITY
    // =======================================================================

    @Test
    fun test_E1_fragmentedPacketsSurviveRelayWithUniqueSequences() {
        val relayRouter = PacketRelayRouter(localDeviceId = 999)
        relayRouter.setRelayEnabled(true)

        val frag1 = Packet(
            sequenceNumber = 1001,
            sourceDeviceId = 100,
            destinationDeviceId = Packet.BROADCAST_ID,
            ttl = 3,
            flags = Packet.FLAG_FRAGMENTED.toByte(),
            timestamp = System.currentTimeMillis(),
            language = IndicLanguage.TAMIL,
            payload = byteArrayOf(1, 2, 3)
        )

        val frag2 = Packet(
            sequenceNumber = 1002, // Unique sequence for fragment 2
            sourceDeviceId = 100,
            destinationDeviceId = Packet.BROADCAST_ID,
            ttl = 3,
            flags = Packet.FLAG_FRAGMENTED.toByte(),
            timestamp = System.currentTimeMillis(),
            language = IndicLanguage.TAMIL,
            payload = byteArrayOf(4, 5, 6)
        )

        val action1 = relayRouter.evaluatePacket(frag1)
        assertTrue("Fragment 1 should be forwarded", action1 is RelayAction.ForwardAndDeliver)
        assertEquals(2.toByte(), (action1 as RelayAction.ForwardAndDeliver).forwardedPacket.ttl)

        val action2 = relayRouter.evaluatePacket(frag2)
        assertTrue("Fragment 2 with unique seq should also be forwarded (no duplicate collision)", action2 is RelayAction.ForwardAndDeliver)

        // Duplicate of fragment 1
        val actionDup = relayRouter.evaluatePacket(frag1)
        assertTrue("Duplicate fragment 1 must be dropped", actionDup is RelayAction.DropDuplicate)
    }

    // =======================================================================
    // GROUP F — DTN STORE-AND-FORWARD COMPATIBILITY
    // =======================================================================

    @Test
    fun test_F1_dtnStoresAndPrioritizesFragments() {
        val dtnStore = DtnStore()
        val fragP1 = Packet(
            sequenceNumber = 2001,
            sourceDeviceId = 100,
            destinationDeviceId = 200,
            priority = MessagePriority.NORMAL,
            flags = Packet.FLAG_FRAGMENTED.toByte(),
            timestamp = System.currentTimeMillis(),
            language = IndicLanguage.HINDI,
            payload = byteArrayOf(1, 2, 3)
        )

        val fragDistress = Packet(
            sequenceNumber = 2002,
            sourceDeviceId = 100,
            destinationDeviceId = 200,
            priority = MessagePriority.DISTRESS, // Emergency fragment
            flags = Packet.FLAG_FRAGMENTED.toByte(),
            timestamp = System.currentTimeMillis(),
            language = IndicLanguage.HINDI,
            payload = byteArrayOf(4, 5, 6)
        )

        assertTrue(dtnStore.store(fragP1))
        assertTrue(dtnStore.store(fragDistress))
        assertEquals(2, dtnStore.size())

        // Drain for destination 200: DISTRESS fragment must come out first!
        val drained = dtnStore.drainForDestination(200)
        assertEquals(2, drained.size)
        assertEquals(MessagePriority.DISTRESS, drained[0].priority)
        assertEquals(MessagePriority.NORMAL, drained[1].priority)
    }

    // =======================================================================
    // GROUP G — DELIVERY RECEIPTS & CORRELATION
    // =======================================================================

    @Test
    fun test_G1_successfulReassemblyGeneratesMatchingDeliveryReceipt() {
        val receipt = DeliveryReceipt(transferId = 0x5678.toShort(), status = DeliveryReceipt.STATUS_DELIVERED)
        val wireBytes = receipt.serialize()

        assertEquals("Delivery receipt wire format must be strictly 3 bytes", 3, wireBytes.size)

        val parsed = DeliveryReceipt.deserialize(wireBytes)
        assertNotNull(parsed)
        assertEquals(0x5678.toShort(), parsed!!.transferId)
        assertEquals(DeliveryReceipt.STATUS_DELIVERED, parsed.status)
        assertTrue(parsed.isDelivered)
    }

    @Test
    fun test_G2_senderTracksDeliveryReceiptAndHandlesDuplicatesAndTimeout() {
        val tracker = PendingTransferTracker(timeoutMs = 30_000L)
        val transferId: Short = 0x4321.toShort()
        val t0 = 500_000L

        // Register outgoing transfer
        tracker.registerTransfer(
            transferId = transferId,
            messageId = "msg-1",
            destinationDeviceId = 200,
            fragmentCount = 3,
            payloadBytes = 350,
            totalWireBytes = 450,
            nowMs = t0
        )

        // Incoming delivery receipt at t0 + 45ms
        val receipt = DeliveryReceipt(transferId = transferId, status = DeliveryReceipt.STATUS_DELIVERED)
        val acknowledged = tracker.onReceiptReceived(receipt, nowMs = t0 + 45L)
        assertNotNull("Receipt should match pending transfer", acknowledged)
        assertEquals(DeliveryStatus.DELIVERED, acknowledged!!.status)
        assertEquals(45L, acknowledged.deliveryRttMs)

        // Duplicate receipt arrives at t0 + 60ms
        val dupAcknowledged = tracker.onReceiptReceived(receipt, nowMs = t0 + 60L)
        assertNull("Duplicate receipt must be safely ignored", dupAcknowledged)

        // New transfer times out
        val transferId2: Short = 0x9999.toShort()
        tracker.registerTransfer(
            transferId = transferId2,
            messageId = "msg-2",
            destinationDeviceId = 200,
            fragmentCount = 2,
            payloadBytes = 200,
            totalWireBytes = 280,
            nowMs = t0
        )
        val timedOut = tracker.pruneExpired(nowMs = t0 + 31_000L)
        assertEquals(1, timedOut.size)
        assertEquals(transferId2, timedOut[0].transferId)
        assertEquals(DeliveryStatus.TIMEOUT, timedOut[0].status)
    }

    // =======================================================================
    // GROUP H — SEMANTIC COMPATIBILITY
    // =======================================================================

    @Test
    fun test_H1_semanticEmergencyPacketRemainsUnfragmented() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.RESCUE,
            subtype = EmergencySubtype.BUILDING,
            count = 5,
            severity = EmergencySeverity.CRITICAL
        )
        val serializedCmd = cmd.serialize()
        assertEquals(6, serializedCmd.size)
        assertFalse("6-byte semantic command must not be fragmented", PacketFragmenter.needsFragmentation(serializedCmd.size))

        val packet = Packet(
            sequenceNumber = 1,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 100,
            language = IndicLanguage.TAMIL,
            payload = serializedCmd,
            flags = Packet.FLAG_SEMANTIC.toByte()
        )
        val wire = PacketSerializer.serialize(packet)
        // 28B header + 6B payload + 4B CRC = 38 bytes
        assertEquals(38, wire.size)
    }

    @Test
    fun test_H2_semanticWithLocationRemainsUnfragmented() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.INJURED,
            count = 1,
            severity = EmergencySeverity.ALERT
        )
        val location = GeoLocation(13.0827, 80.2707, 5.0f, System.currentTimeMillis())
        val packet = Packet(
            sequenceNumber = 2,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 100,
            language = IndicLanguage.TAMIL,
            payload = cmd.serialize(),
            location = location,
            flags = (Packet.FLAG_SEMANTIC or Packet.FLAG_HAS_LOCATION).toByte()
        )

        assertFalse("Semantic + Location payload (6B) must not be fragmented", PacketFragmenter.needsFragmentation(packet.payload.size))
        val wire = PacketSerializer.serialize(packet)
        // 28B header + 32B location + 6B payload + 4B CRC = 70 bytes
        assertEquals(70, wire.size)
    }

    @Test
    fun test_Benchmark_empiricalMeasurement() {
        val originalText = "வெள்ளப்பெருக்கு அவசர உதவி தேவை! மக்கள் அனைவரும் உடனடியாக மேடான பகுதிக்கு செல்லவும். மருத்துவக் குழுக்கள் தயார் நிலையில் உள்ளன. குடிநீர் மற்றும் உணவுப் பொட்டலங்கள் விநியோகிக்கப்பட்டு வருகின்றன. அவசர உதவிக்கு இந்த அலைவரிசையில் தொடர்பு கொள்ளவும்."
        val payload = originalText.toByteArray(Charsets.UTF_8)
        val transferId: Short = 0x4242.toShort()
        val fragmenter = PacketFragmenter(Packet.MAX_FRAGMENT_PAYLOAD)

        // 1. Fragmentation
        val tFragStart = System.nanoTime()
        val fragments = fragmenter.fragment(payload, transferId)
        val tFragEnd = System.nanoTime()

        var totalWireBytes = 0
        val packets = fragments.mapIndexed { idx, frag ->
            val p = Packet(
                version = Packet.PROTOCOL_VERSION,
                msgType = Packet.TYPE_TEXT,
                priority = MessagePriority.NORMAL,
                flags = Packet.FLAG_FRAGMENTED.toByte(),
                sequenceNumber = (1000 + idx).toShort(),
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = 101,
                destinationDeviceId = 202,
                language = IndicLanguage.TAMIL,
                payload = frag.toPayload()
            )
            val wire = PacketSerializer.serialize(p)
            totalWireBytes += wire.size
            p
        }

        // 2. Reassembly
        val buffer = ReassemblyBuffer()
        val tReassemblyStart = System.nanoTime()
        var result: ReassemblyResult? = null
        for (p in packets) {
            val frag = PacketFragment.fromPayload(p.payload)!!
            result = buffer.addFragment(p.sourceDeviceId, frag)
        }
        val tReassemblyEnd = System.nanoTime()
        val reassemblyMs = (tReassemblyEnd - tReassemblyStart) / 1_000_000.0

        assertNotNull(result)
        assertArrayEquals(payload, result!!.payload)

        // 3. Delivery Receipt
        val tracker = PendingTransferTracker()
        tracker.registerTransfer(
            transferId = transferId,
            messageId = "bench-1",
            destinationDeviceId = 202,
            fragmentCount = fragments.size,
            payloadBytes = payload.size,
            totalWireBytes = totalWireBytes
        )
        val tAckStart = System.nanoTime()
        val ackPayload = DeliveryReceipt(result.transferId, DeliveryReceipt.STATUS_DELIVERED).serialize()
        val ackPacket = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_ACK,
            priority = MessagePriority.NORMAL,
            flags = 0,
            sequenceNumber = 2001,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 202,
            destinationDeviceId = 101,
            language = IndicLanguage.TAMIL,
            payload = ackPayload
        )
        val ackReceipt = DeliveryReceipt.deserialize(ackPacket.payload)!!
        val tracked = tracker.onReceiptReceived(ackReceipt)
        val tAckEnd = System.nanoTime()
        val ackRttMs = (tAckEnd - tAckStart) / 1_000_000.0

        assertNotNull(tracked)
        assertEquals(DeliveryStatus.DELIVERED, tracked!!.status)

        println("=== EMPIRICAL FRAGMENTATION & DELIVERY BENCHMARK ===")
        println("PAYLOAD      ${payload.size} B")
        println("FRAGMENTS    ${fragments.size}")
        println("PER-FRAGMENT ${fragments[0].data.size} B (max ${Packet.MAX_FRAGMENT_PAYLOAD} B)")
        println("TOTAL WIRE   ${totalWireBytes} B (${totalWireBytes / fragments.size} B/packet)")
        println("REASSEMBLY   ${String.format(java.util.Locale.US, "%.3f ms", reassemblyMs)}")
        println("DELIVERY ACK ${String.format(java.util.Locale.US, "%.3f ms", ackRttMs)}")
        println("RECEIPT WIRE ${PacketSerializer.serialize(ackPacket).size} B")
        println("===================================================")
    }
}
