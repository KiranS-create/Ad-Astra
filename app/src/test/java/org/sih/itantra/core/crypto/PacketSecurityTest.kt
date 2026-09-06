package org.sih.itantra.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.mesh.PacketRelayRouter
import org.sih.itantra.core.mesh.RelayAction
import org.sih.itantra.core.protocol.DeliveryReceipt
import org.sih.itantra.core.protocol.GeoLocation
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketFragment
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.ReassemblyBuffer
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.EmergencySeverity
import java.security.MessageDigest

/**
 * Comprehensive test suite for Secure Tactical Messaging:
 * - HMAC-SHA256 Authenticated Packet Integrity
 * - Bounded Anti-Replay Sliding Window
 * - MANET Multi-Hop Relay Invariance
 * - Semantic Emergency & Location Integrity
 * - Bounded Fragmentation & Delivery Receipt Security
 */
class PacketSecurityTest {

    private val testKeyA = MessageDigest.getInstance("SHA-256")
        .digest("Unit-Test-Symmetric-Key-Alpha-2026".toByteArray(Charsets.UTF_8))
    private val testKeyB = MessageDigest.getInstance("SHA-256")
        .digest("Unit-Test-Symmetric-Key-Beta-2026".toByteArray(Charsets.UTF_8))

    @Before
    fun setUp() {
        NetworkKeyManager.setTestKey(testKeyA)
    }

    // =========================================================================
    // GROUP A: Authentication
    // =========================================================================

    @Test
    fun testValidPacketComputesCorrectTagAndVerifies() {
        val payload = "Tactical reconnaissance update: Sector 4 clear.".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            sequenceNumber = 101,
            timestamp = 1772000000000L,
            sourceDeviceId = 123456,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = payload
        )

        val signed = PacketAuthenticator.sign(packet, testKeyA)

        assertTrue("FLAG_AUTHENTICATED must be set", (signed.flags.toInt() and Packet.FLAG_AUTHENTICATED) != 0)
        assertNotNull("authTag must be present", signed.authTag)
        assertEquals("Auth tag size must be strictly 8 bytes", 8, signed.authTag!!.size)
        assertTrue(signed.isAuthenticated)

        val result = PacketAuthenticator.verify(signed, testKeyA)
        assertEquals(AuthStatus.VALID, result.status)
        assertTrue(result.isValid)
        assertTrue("Elapsed nanoseconds must be recorded", result.elapsedNanos >= 0)
    }

    @Test
    fun testTamperedPayloadFailsVerification() {
        val payload = "Original tactical instructions".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            sequenceNumber = 102,
            timestamp = 1772000000000L,
            sourceDeviceId = 123456,
            language = IndicLanguage.ENGLISH,
            payload = payload
        )
        val signed = PacketAuthenticator.sign(packet, testKeyA)

        // Tamper with payload
        val tamperedPayload = payload.clone()
        tamperedPayload[0] = (tamperedPayload[0] + 1).toByte()
        val tamperedPacket = signed.copy(payload = tamperedPayload)

        val result = PacketAuthenticator.verify(tamperedPacket, testKeyA)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
        assertFalse(result.isValid)
    }

    @Test
    fun testTamperedSecurityHeaderFieldsFailVerification() {
        val packet = Packet(
            sequenceNumber = 103,
            timestamp = 1772000000000L,
            sourceDeviceId = 123456,
            destinationDeviceId = 654321,
            language = IndicLanguage.HINDI,
            payload = "नमस्ते".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, testKeyA)

        // 1. Tamper with sequence number
        val tamperedSeq = signed.copy(sequenceNumber = 104)
        assertEquals(AuthStatus.INVALID_TAG, PacketAuthenticator.verify(tamperedSeq, testKeyA).status)

        // 2. Tamper with sourceDeviceId
        val tamperedSource = signed.copy(sourceDeviceId = 999999)
        assertEquals(AuthStatus.INVALID_TAG, PacketAuthenticator.verify(tamperedSource, testKeyA).status)

        // 3. Tamper with destinationDeviceId
        val tamperedDest = signed.copy(destinationDeviceId = Packet.BROADCAST_ID)
        assertEquals(AuthStatus.INVALID_TAG, PacketAuthenticator.verify(tamperedDest, testKeyA).status)

        // 4. Tamper with msgType
        val tamperedType = signed.copy(msgType = Packet.TYPE_ALERT)
        assertEquals(AuthStatus.INVALID_TAG, PacketAuthenticator.verify(tamperedType, testKeyA).status)

        // 5. Tamper with priority
        val tamperedPrio = signed.copy(priority = MessagePriority.ALERT)
        assertEquals(AuthStatus.INVALID_TAG, PacketAuthenticator.verify(tamperedPrio, testKeyA).status)

        // 6. Tamper with timestamp
        val tamperedTime = signed.copy(timestamp = 1772000001000L)
        assertEquals(AuthStatus.INVALID_TAG, PacketAuthenticator.verify(tamperedTime, testKeyA).status)
    }

    @Test
    fun testWrongSymmetricKeyFailsVerification() {
        val packet = Packet(
            sequenceNumber = 105,
            timestamp = 1772000000000L,
            sourceDeviceId = 123456,
            language = IndicLanguage.ENGLISH,
            payload = "Secret mission coordinates".toByteArray(Charsets.UTF_8)
        )
        val signedWithKeyA = PacketAuthenticator.sign(packet, testKeyA)

        // Attempt verify with different key B
        val result = PacketAuthenticator.verify(signedWithKeyA, testKeyB)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
        assertFalse(result.isValid)
    }

    @Test
    fun testMissingOrMalformedAuthTagFails() {
        val packet = Packet(
            sequenceNumber = 106,
            timestamp = 1772000000000L,
            sourceDeviceId = 123456,
            language = IndicLanguage.ENGLISH,
            payload = "Unauthenticated message".toByteArray(Charsets.UTF_8),
            authTag = null
        )

        // Missing tag
        val resultMissing = PacketAuthenticator.verify(packet, testKeyA)
        assertEquals(AuthStatus.MISSING_TAG, resultMissing.status)

        // Malformed tag (e.g. 7 bytes instead of 8 bytes)
        val malformedPacket = packet.copy(authTag = ByteArray(7))
        val resultMalformed = PacketAuthenticator.verify(malformedPacket, testKeyA)
        assertEquals(AuthStatus.MALFORMED_TAG, resultMalformed.status)

        // Unknown / unprovisioned key
        val signed = PacketAuthenticator.sign(packet, testKeyA)
        val resultUnknown = PacketAuthenticator.verify(signed, null)
        assertEquals(AuthStatus.UNKNOWN_KEY, resultUnknown.status)
    }

    // =========================================================================
    // GROUP B: Anti-Replay Sliding Window
    // =========================================================================

    @Test
    fun testStrictlyNewerSequenceAdvancesWindow() {
        val filter = AntiReplayFilter()
        val source = 2001

        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 1).status)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 2).status)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 3).status)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 10).status)
    }

    @Test
    fun testExactDuplicateSequenceRejected() {
        val filter = AntiReplayFilter()
        val source = 2002

        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 5).status)
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(source, 5).status)
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(source, 5).status)
    }

    @Test
    fun testOutOfOrderWithinWindowAcceptedOnceThenDuplicate() {
        val filter = AntiReplayFilter()
        val source = 2003

        // Advance window to 20
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 20).status)

        // Arrive out of order: 18 (2 packets behind leading edge)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 18).status)
        // Arrive again: must be rejected as duplicate
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(source, 18).status)

        // Arrive out of order: 15 (5 packets behind)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 15).status)
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(source, 15).status)
    }

    @Test
    fun testStalePacketOutside64WindowRejected() {
        val filter = AntiReplayFilter()
        val source = 2004

        // Advance window to 100
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 100).status)

        // 100 - 36 = 64 (at/beyond boundary) -> STALE
        assertEquals(ReplayStatus.STALE, filter.checkAndRecord(source, 36).status)
        assertEquals(ReplayStatus.STALE, filter.checkAndRecord(source, 10).status)
        assertEquals(ReplayStatus.STALE, filter.checkAndRecord(source, 1).status)

        // 100 - 63 = 37 (within boundary 0..63) -> ACCEPTED
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 37).status)
        // Second arrival of 37 -> DUPLICATE
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(source, 37).status)
    }

    @Test
    fun testSequenceRolloverHandledGracefully() {
        val filter = AntiReplayFilter()
        val source = 2005

        // Start near 16-bit max (Short.MAX_VALUE = 32767)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, 32767.toShort()).status)
        // Rollover into negative Short representation (-32768)
        val rolloverSeq = (-32768).toShort()
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(source, rolloverSeq).status)
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(source, rolloverSeq).status)
    }

    @Test
    fun testBoundedMemoryLimitsMaxNodes() {
        val maxNodes = 10
        val filter = AntiReplayFilter(maxTrackedNodes = maxNodes)

        // Insert 15 nodes
        for (i in 1..15) {
            filter.checkAndRecord(sourceDeviceId = i, sequenceNumber = 1)
        }

        assertTrue("Tracked nodes must not exceed ceiling", filter.getTrackedNodeCount() <= maxNodes)
    }

    @Test
    fun testInactivityPruning() {
        val filter = AntiReplayFilter(pruneInactivityMs = 100L)
        filter.checkAndRecord(sourceDeviceId = 3001, sequenceNumber = 1)
        assertEquals(1, filter.getTrackedNodeCount())

        // Sleep briefly past 100ms
        Thread.sleep(120L)
        val evicted = filter.pruneInactive(maxAgeMs = 100L)
        assertEquals(1, evicted)
        assertEquals(0, filter.getTrackedNodeCount())
    }

    // =========================================================================
    // GROUP C: Semantic Emergency Integrity
    // =========================================================================

    @Test
    fun testSemanticEmergencyPacketAuthenticates() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.AMBULANCE,
            count = 3,
            severity = EmergencySeverity.CRITICAL
        )
        val packet = Packet(
            sequenceNumber = 201,
            timestamp = 1772000000000L,
            sourceDeviceId = 555555,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.TAMIL,
            payload = cmd.serialize(),
            semanticCommand = cmd
        )

        val signed = PacketAuthenticator.sign(packet, testKeyA)
        assertTrue(signed.isAuthenticated)
        assertEquals(6, signed.payload.size) // Semantic payload remains strictly 6 bytes!

        val result = PacketAuthenticator.verify(signed, testKeyA)
        assertEquals(AuthStatus.VALID, result.status)
    }

    @Test
    fun testTamperedSemanticCommandRejected() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.AMBULANCE,
            count = 1,
            severity = EmergencySeverity.ALERT
        )
        val packet = Packet(
            sequenceNumber = 202,
            timestamp = 1772000000000L,
            sourceDeviceId = 555555,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.ENGLISH,
            payload = cmd.serialize(),
            semanticCommand = cmd
        )
        val signed = PacketAuthenticator.sign(packet, testKeyA)

        // Attacker alters victimCount from 1 to 99 in payload
        val tamperedPayload = signed.payload.clone()
        tamperedPayload[3] = 99.toByte()
        val tamperedPacket = signed.copy(payload = tamperedPayload, semanticCommand = null)

        val result = PacketAuthenticator.verify(tamperedPacket, testKeyA)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    // =========================================================================
    // GROUP D: Location Integrity
    // =========================================================================

    @Test
    fun testDistressWithLocationAuthenticates() {
        val loc = GeoLocation(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 4.5f,
            timestamp = 1772000000000L,
            altitude = 920.0
        )
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 301,
            timestamp = 1772000000000L,
            sourceDeviceId = 777777,
            language = IndicLanguage.KANNADA,
            payload = "ತುರ್ತು ಪರಿಸ್ಥಿತಿ".toByteArray(Charsets.UTF_8),
            location = loc
        )

        val signed = PacketAuthenticator.sign(packet, testKeyA)
        assertTrue(signed.isAuthenticated)

        val result = PacketAuthenticator.verify(signed, testKeyA)
        assertEquals(AuthStatus.VALID, result.status)
    }

    @Test
    fun testTamperedLocationCoordinatesRejected() {
        val loc = GeoLocation(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 4.5f,
            timestamp = 1772000000000L
        )
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 302,
            timestamp = 1772000000000L,
            sourceDeviceId = 777777,
            language = IndicLanguage.ENGLISH,
            payload = "Distress call".toByteArray(Charsets.UTF_8),
            location = loc
        )
        val signed = PacketAuthenticator.sign(packet, testKeyA)

        // Spoof coordinates (GPS tampering)
        val spoofedLoc = loc.copy(latitude = 28.6139, longitude = 77.2090)
        val spoofedPacket = signed.copy(location = spoofedLoc)

        val result = PacketAuthenticator.verify(spoofedPacket, testKeyA)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    // =========================================================================
    // GROUP E: Fragmentation Compatibility
    // =========================================================================

    @Test
    fun testIndividualFragmentsIndependentlyAuthenticated() {
        val fragmenter = PacketFragmenter(maxPayloadBytes = 128)
        val largePayload = ByteArray(250) { it.toByte() }
        val transferId: Short = 401
        val fragments = fragmenter.fragment(
            payload = largePayload,
            transferId = transferId,
            originalMsgType = Packet.TYPE_TEXT,
            originalFlags = 0
        )

        assertEquals(2, fragments.size)

        // Sign each fragment independently
        val signedFrags = fragments.mapIndexed { idx, frag ->
            val p = Packet(
                msgType = Packet.TYPE_TEXT,
                flags = Packet.FLAG_FRAGMENTED.toByte(),
                sequenceNumber = (1000 + idx).toShort(),
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = 888888,
                language = IndicLanguage.ENGLISH,
                payload = frag.toPayload()
            )
            PacketAuthenticator.sign(p, testKeyA)
        }

        // Verify each fragment individually
        for (signedFrag in signedFrags) {
            assertTrue(signedFrag.isAuthenticated)
            val res = PacketAuthenticator.verify(signedFrag, testKeyA)
            assertEquals(AuthStatus.VALID, res.status)
        }
    }

    @Test
    fun testTamperedFragmentRejectedBeforeReassembly() {
        val fragmenter = PacketFragmenter(maxPayloadBytes = 128)
        val payload = ByteArray(200) { 0x42.toByte() }
        val frags = fragmenter.fragment(payload, 402, Packet.TYPE_TEXT, 0)
        val frag0Packet = Packet(
            flags = Packet.FLAG_FRAGMENTED.toByte(),
            sequenceNumber = 1010,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 888888,
            language = IndicLanguage.ENGLISH,
            payload = frags[0].toPayload()
        )
        val signedFrag = PacketAuthenticator.sign(frag0Packet, testKeyA)

        // Corrupt fragment payload
        val corruptedBytes = signedFrag.payload.clone()
        corruptedBytes[10] = (corruptedBytes[10] + 1).toByte()
        val corruptedPacket = signedFrag.copy(payload = corruptedBytes)

        assertEquals(AuthStatus.INVALID_TAG, PacketAuthenticator.verify(corruptedPacket, testKeyA).status)
    }

    @Test
    fun testOutOfOrderAuthenticatedFragmentsReassemble() {
        val fragmenter = PacketFragmenter(maxPayloadBytes = 100)
        val originalText = "A".repeat(250)
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val frags = fragmenter.fragment(originalBytes, 403, Packet.TYPE_TEXT, 0)
        assertEquals(3, frags.size)

        val signedPackets = frags.mapIndexed { i, f ->
            val p = Packet(
                flags = Packet.FLAG_FRAGMENTED.toByte(),
                sequenceNumber = (2000 + i).toShort(),
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = 999111,
                language = IndicLanguage.ENGLISH,
                payload = f.toPayload()
            )
            PacketAuthenticator.sign(p, testKeyA)
        }

        // Feed to reassembly in reverse order (2, 1, 0) after verifying each
        val buffer = ReassemblyBuffer()
        val sourceId = 999111

        // Verify 2
        assertEquals(AuthStatus.VALID, PacketAuthenticator.verify(signedPackets[2], testKeyA).status)
        val frag2 = PacketFragment.fromPayload(signedPackets[2].payload)!!
        assertNull(buffer.addFragment(sourceId, frag2))

        // Verify 1
        assertEquals(AuthStatus.VALID, PacketAuthenticator.verify(signedPackets[1], testKeyA).status)
        val frag1 = PacketFragment.fromPayload(signedPackets[1].payload)!!
        assertNull(buffer.addFragment(sourceId, frag1))

        // Verify 0 -> triggers complete reassembly
        assertEquals(AuthStatus.VALID, PacketAuthenticator.verify(signedPackets[0], testKeyA).status)
        val frag0 = PacketFragment.fromPayload(signedPackets[0].payload)!!
        val result = buffer.addFragment(sourceId, frag0)

        assertNotNull(result)
        assertEquals(originalText, String(result!!.payload, Charsets.UTF_8))
    }

    // =========================================================================
    // GROUP F: Delivery Receipts
    // =========================================================================

    @Test
    fun testDeliveryReceiptAuthenticates() {
        val receiptPayload = DeliveryReceipt(501.toShort(), DeliveryReceipt.STATUS_DELIVERED).serialize()
        assertEquals(3, receiptPayload.size) // Receipt payload remains strictly 3 bytes!

        val ackPacket = Packet(
            msgType = Packet.TYPE_ACK,
            priority = MessagePriority.NORMAL,
            sequenceNumber = 5001,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 222333,
            destinationDeviceId = 111222,
            language = IndicLanguage.ENGLISH,
            payload = receiptPayload
        )

        val signedAck = PacketAuthenticator.sign(ackPacket, testKeyA)
        assertTrue(signedAck.isAuthenticated)

        val verifyResult = PacketAuthenticator.verify(signedAck, testKeyA)
        assertEquals(AuthStatus.VALID, verifyResult.status)
    }

    @Test
    fun testForgedDeliveryReceiptWithoutKeyRejected() {
        val forgedPayload = DeliveryReceipt(502.toShort(), DeliveryReceipt.STATUS_DELIVERED).serialize()
        val forgedAck = Packet(
            msgType = Packet.TYPE_ACK,
            priority = MessagePriority.NORMAL,
            sequenceNumber = 5002,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 666666, // Attacker node
            destinationDeviceId = 111222,
            language = IndicLanguage.ENGLISH,
            payload = forgedPayload
        )

        // Attacker attempts to sign with their own key B
        val signedWithAttackerKey = PacketAuthenticator.sign(forgedAck, testKeyB)

        // Legitimate network node with Key A rejects forged ACK
        val result = PacketAuthenticator.verify(signedWithAttackerKey, testKeyA)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    // =========================================================================
    // GROUP G: MANET Relay Hop Invariance
    // =========================================================================

    @Test
    fun testManetRelayPreservesEndToEndAuthenticationWithoutResigning() {
        // Node 1 creates and signs packet
        val originalPacket = Packet(
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.NORMAL,
            ttl = Packet.DEFAULT_TTL, // ttl = 3
            sequenceNumber = 601,
            timestamp = 1772000000000L,
            sourceDeviceId = 10001,
            destinationDeviceId = 10003,
            language = IndicLanguage.MALAYALAM,
            payload = "റിലേ സന്ദേശം".toByteArray(Charsets.UTF_8)
        )
        val signedAtOrigin = PacketAuthenticator.sign(originalPacket, testKeyA)
        val wireAtOrigin = PacketSerializer.serialize(signedAtOrigin)

        // Relay Node 2 receives wire packet, verifies CRC, deserializes
        val receivedAtRelay = PacketSerializer.deserialize(wireAtOrigin)
        assertEquals(AuthStatus.VALID, PacketAuthenticator.verify(receivedAtRelay, testKeyA).status)

        // Relay evaluates and forwards packet: decrements TTL (3 -> 2), sets FLAG_FORWARDED
        val relayRouter = PacketRelayRouter(localDeviceId = 10002)
        relayRouter.setRelayEnabled(true)
        val decision = relayRouter.evaluatePacket(receivedAtRelay)
        assertTrue(decision is RelayAction.ForwardAndDeliver)
        val forwardedPacket = (decision as RelayAction.ForwardAndDeliver).forwardedPacket

        assertEquals(2.toByte(), forwardedPacket.ttl)
        assertTrue(forwardedPacket.isForwarded)
        // Relay serializes forwarded packet with new CRC32 over decremented TTL
        val wireFromRelay = PacketSerializer.serialize(forwardedPacket)

        // Destination Node 3 receives wire packet from relay
        val receivedAtDest = PacketSerializer.deserialize(wireFromRelay)

        // CRITICAL CHECK: End-to-end authentication must verify cleanly WITHOUT relay re-signing!
        val destAuthResult = PacketAuthenticator.verify(receivedAtDest, testKeyA)
        assertEquals("Relayed packet must verify end-to-end despite TTL decrement and FLAG_FORWARDED",
            AuthStatus.VALID, destAuthResult.status)
        assertTrue(destAuthResult.isValid)
    }

    // =========================================================================
    // GROUP H: Compatibility & Wire Framing Overhead
    // =========================================================================

    @Test
    fun testAuthenticationWireOverheadIsStrictly8Bytes() {
        val payload = "Testing wire overhead bytes".toByteArray(Charsets.UTF_8)
        val unauthenticated = Packet(
            sequenceNumber = 701,
            timestamp = 1772000000000L,
            sourceDeviceId = 1234,
            language = IndicLanguage.ENGLISH,
            payload = payload
        )
        val unauthWire = PacketSerializer.serialize(unauthenticated)

        val authenticated = PacketAuthenticator.sign(unauthenticated, testKeyA)
        val authWire = PacketSerializer.serialize(authenticated)

        assertEquals("Wire overhead for authenticated packet must be strictly 8 bytes",
            unauthWire.size + Packet.AUTH_TAG_SIZE_BYTES, authWire.size)
    }

    @Test
    fun testUnauthenticatedPacketPreservesLegacyCompatibility() {
        val payload = "Legacy unauthenticated packet".toByteArray(Charsets.UTF_8)
        val legacy = Packet(
            sequenceNumber = 702,
            timestamp = 1772000000000L,
            sourceDeviceId = 1234,
            language = IndicLanguage.ENGLISH,
            payload = payload,
            authTag = null
        )
        val wire = PacketSerializer.serialize(legacy)
        val deserialized = PacketSerializer.deserialize(wire)

        assertFalse(deserialized.isAuthenticated)
        assertNull(deserialized.authTag)
        assertEquals(legacy.sequenceNumber, deserialized.sequenceNumber)
        assertTrue(legacy.payload.contentEquals(deserialized.payload))
    }

    @Test
    fun benchmarkAuthenticationTiming() {
        val payload = "Tactical emergency reconnaissance coordinates and status report".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            sequenceNumber = 999,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 444555,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.HINDI,
            payload = payload
        )

        // Warmup JIT
        for (i in 0 until 500) {
            val signed = PacketAuthenticator.sign(packet, testKeyA)
            PacketAuthenticator.verify(signed, testKeyA)
        }

        // Benchmark 1000 iterations
        val iterations = 1000
        var totalGenNanos = 0L
        var totalVerifyNanos = 0L

        for (i in 0 until iterations) {
            val (signed, genNanos) = PacketAuthenticator.signWithLatency(packet, testKeyA)
            totalGenNanos += genNanos
            val verifyResult = PacketAuthenticator.verify(signed, testKeyA)
            totalVerifyNanos += verifyResult.elapsedNanos
        }

        val avgGenMicros = (totalGenNanos.toDouble() / iterations) / 1000.0
        val avgVerifyMicros = (totalVerifyNanos.toDouble() / iterations) / 1000.0

        println("=== REAL MEASURED AUTHENTICATION BENCHMARKS ===")
        println("Iterations: $iterations")
        println("Average HMAC-SHA256 Generation Time: " + String.format(java.util.Locale.US, "%.2f µs", avgGenMicros))
        println("Average HMAC-SHA256 Verification Time: " + String.format(java.util.Locale.US, "%.2f µs", avgVerifyMicros))
        println("Per-Packet Wire Overhead: ${Packet.AUTH_TAG_SIZE_BYTES} bytes")
        println("================================================")

        assertTrue("HMAC generation must be sub-millisecond", avgGenMicros < 1000.0)
        assertTrue("HMAC verification must be sub-millisecond", avgVerifyMicros < 1000.0)
    }
}
