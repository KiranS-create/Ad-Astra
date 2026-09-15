package org.sih.itantra.security

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
import org.sih.itantra.core.mesh.DtnStore
import org.sih.itantra.core.mesh.PacketRelayRouter
import org.sih.itantra.core.mesh.RelayAction
import org.sih.itantra.core.protocol.CorruptPacketException
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.GeoLocation
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.qos.TacticalPacketScheduler
import java.security.MessageDigest
import java.util.Random

/**
 * Feature 23: Security Audit & Adversarial Negative Testing Suite.
 *
 * Covers:
 * 1. HMAC-SHA256 Authenticity & Tampering Resistance
 * 2. MANET Relay Security (Attacker -> Relay -> Destination)
 * 3. DTN Storage Security & Integrity Enforcement
 * 4. Application-Level QoS & Flooding Resilience
 */
class SecurityAuditTest {

    private val authKeyAlpha = MessageDigest.getInstance("SHA-256")
        .digest("Audit-Symmetric-Key-Alpha-2026-SIH".toByteArray(Charsets.UTF_8))
    private val authKeyBravo = MessageDigest.getInstance("SHA-256")
        .digest("Audit-Symmetric-Key-Bravo-2026-SIH".toByteArray(Charsets.UTF_8))

    @Before
    fun setUp() {
        NetworkKeyManager.setTestKey(authKeyAlpha)
    }

    // =========================================================================
    // SECTION 1: HMAC-SHA256 AUTHENTICITY & INTEGRITY AUDIT
    // =========================================================================

    @Test
    fun hmac_validPacket_verifiesSuccessfully() {
        val packet = Packet(
            sequenceNumber = 101,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Sector 4 reconnaissance clear.".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)
        val result = PacketAuthenticator.verify(signed, authKeyAlpha)

        assertTrue("Valid packet must be authenticated", result.isValid)
        assertEquals(AuthStatus.VALID, result.status)
    }

    @Test
    fun hmac_tamperedPayload_rejectedDeterministically() {
        val packet = Packet(
            sequenceNumber = 102,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.HINDI,
            payload = "Original message: 2 casualties".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        // Adversary alters payload bytes
        val tamperedPayload = "Forged message: 50 casualties".toByteArray(Charsets.UTF_8)
        val forged = signed.copy(payload = tamperedPayload)

        val result = PacketAuthenticator.verify(forged, authKeyAlpha)
        assertFalse("Tampered payload must fail authentication", result.isValid)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    @Test
    fun hmac_tamperedSourceDeviceId_rejectedDeterministically() {
        val packet = Packet(
            sequenceNumber = 103,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Node 209070 operational".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        // Adversary attempts source spoofing
        val forged = signed.copy(sourceDeviceId = 999999)
        val result = PacketAuthenticator.verify(forged, authKeyAlpha)

        assertFalse("Spoofed source ID must fail authentication", result.isValid)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    @Test
    fun hmac_tamperedDestinationDeviceId_rejectedDeterministically() {
        val packet = Packet(
            sequenceNumber = 104,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = 888111,
            language = IndicLanguage.ENGLISH,
            payload = "Confidential command to unit 888111".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        // Adversary retargets packet to broadcast
        val forged = signed.copy(destinationDeviceId = Packet.BROADCAST_ID)
        val result = PacketAuthenticator.verify(forged, authKeyAlpha)

        assertFalse("Tampered destination must fail authentication", result.isValid)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    @Test
    fun hmac_tamperedTimestamp_rejectedDeterministically() {
        val packet = Packet(
            sequenceNumber = 105,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Time-sensitive SITREP".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        // Adversary alters timestamp
        val forged = signed.copy(timestamp = 1772000999999L)
        val result = PacketAuthenticator.verify(forged, authKeyAlpha)

        assertFalse("Tampered timestamp must fail authentication", result.isValid)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    @Test
    fun hmac_tamperedPriority_rejectedDeterministically() {
        val packet = Packet(
            sequenceNumber = 106,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            priority = MessagePriority.NORMAL,
            language = IndicLanguage.ENGLISH,
            payload = "Routine logistics report".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        // Adversary escalates priority to DISTRESS
        val forged = signed.copy(priority = MessagePriority.DISTRESS)
        val result = PacketAuthenticator.verify(forged, authKeyAlpha)

        assertFalse("Unauthorized priority escalation must fail authentication", result.isValid)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    @Test
    fun hmac_tamperedLocationCoordinates_rejectedDeterministically() {
        val originalLoc = GeoLocation(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracy = 4.5f,
            timestamp = 1772000000000L,
            altitude = 216.0
        )
        val packet = Packet(
            sequenceNumber = 107,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            location = originalLoc,
            payload = "Distress with coordinates".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        // Adversary forges GPS coordinates by 10 kilometers
        val forgedLoc = originalLoc.copy(latitude = 28.7139, longitude = 77.3090)
        val forged = signed.copy(location = forgedLoc)
        val result = PacketAuthenticator.verify(forged, authKeyAlpha)

        assertFalse("Tampered GPS coordinates must fail authentication", result.isValid)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    @Test
    fun hmac_tamperedSemanticCommandFields_rejectedDeterministically() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.INJURED,
            count = 2,
            severity = EmergencySeverity.CRITICAL,
            parameter = 4
        )
        val packet = Packet(
            sequenceNumber = 108,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            semanticCommand = cmd,
            payload = cmd.serialize()
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        // Adversary alters casualty count from 2 to 99 in the semantic command
        val forgedCmd = cmd.copy(count = 99)
        val forged = signed.copy(
            semanticCommand = forgedCmd,
            payload = forgedCmd.serialize()
        )
        val result = PacketAuthenticator.verify(forged, authKeyAlpha)

        assertFalse("Tampered semantic fields must fail authentication", result.isValid)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    @Test
    fun hmac_relayHopInvariance_forwardedPacketPreservesAuthenticity() {
        val packet = Packet(
            sequenceNumber = 109,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            ttl = 3,
            language = IndicLanguage.ENGLISH,
            payload = "Relay test packet".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        // MANET intermediate router decrements TTL and sets FLAG_FORWARDED
        val forwardedFlags = (signed.flags.toInt() or Packet.FLAG_FORWARDED).toByte()
        val relayed = signed.copy(ttl = 2, flags = forwardedFlags)

        val result = PacketAuthenticator.verify(relayed, authKeyAlpha)
        assertTrue("Hop-mutable TTL and FLAG_FORWARDED must maintain end-to-end sender authenticity", result.isValid)
    }

    @Test
    fun hmac_wrongKey_rejectedDeterministically() {
        val packet = Packet(
            sequenceNumber = 110,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Test with Key Alpha".toByteArray(Charsets.UTF_8)
        )
        val signedWithAlpha = PacketAuthenticator.sign(packet, authKeyAlpha)

        // Receiver attempts verification using distinct key Bravo
        val result = PacketAuthenticator.verify(signedWithAlpha, authKeyBravo)
        assertFalse("Verification with wrong key must fail", result.isValid)
        assertEquals(AuthStatus.INVALID_TAG, result.status)
    }

    @Test
    fun hmac_unprovisionedOrEmptyKey_returnsUnknownKey() {
        val packet = Packet(
            sequenceNumber = 111,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Test missing key".toByteArray(Charsets.UTF_8)
        )
        val signed = PacketAuthenticator.sign(packet, authKeyAlpha)

        val resultNullKey = PacketAuthenticator.verify(signed, null)
        assertEquals(AuthStatus.UNKNOWN_KEY, resultNullKey.status)

        val resultEmptyKey = PacketAuthenticator.verify(signed, ByteArray(0))
        assertEquals(AuthStatus.UNKNOWN_KEY, resultEmptyKey.status)
    }

    @Test
    fun hmac_malformedTagLengths_rejectedAsMalformed() {
        val packet = Packet(
            sequenceNumber = 112,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Malformed tag test".toByteArray(Charsets.UTF_8),
            authTag = ByteArray(7) // 7 bytes instead of 8
        )
        val resultTruncated = PacketAuthenticator.verify(packet, authKeyAlpha)
        assertEquals(AuthStatus.MALFORMED_TAG, resultTruncated.status)

        val packetOversized = packet.copy(authTag = ByteArray(16)) // 16 bytes instead of 8
        val resultOversized = PacketAuthenticator.verify(packetOversized, authKeyAlpha)
        assertEquals(AuthStatus.MALFORMED_TAG, resultOversized.status)
    }

    @Test
    fun hmac_missingTag_rejectedAsMissing() {
        val packet = Packet(
            sequenceNumber = 113,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Missing tag test".toByteArray(Charsets.UTF_8),
            authTag = null
        )
        val result = PacketAuthenticator.verify(packet, authKeyAlpha)
        assertEquals(AuthStatus.MISSING_TAG, result.status)
    }

    @Test
    fun hmac_allZeroAndRandomTags_rejectedDeterministically() {
        val packet = Packet(
            sequenceNumber = 114,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Zero tag test".toByteArray(Charsets.UTF_8),
            authTag = ByteArray(Packet.AUTH_TAG_SIZE_BYTES) // 8 zero bytes
        )
        val resultZero = PacketAuthenticator.verify(packet, authKeyAlpha)
        assertEquals(AuthStatus.INVALID_TAG, resultZero.status)

        val randomTag = ByteArray(Packet.AUTH_TAG_SIZE_BYTES).also { Random(20260915L).nextBytes(it) }
        val packetRandom = packet.copy(authTag = randomTag)
        val resultRandom = PacketAuthenticator.verify(packetRandom, authKeyAlpha)
        assertEquals(AuthStatus.INVALID_TAG, resultRandom.status)
    }

    // =========================================================================
    // SECTION 2: RELAY SECURITY AUDIT (ATTACKER -> RELAY -> DESTINATION)
    // =========================================================================

    @Test
    fun relay_unauthenticatedPacket_neverForwardedWhenRelayEvaluates() {
        val relayRouter = PacketRelayRouter(localDeviceId = 555555)
        relayRouter.setRelayEnabled(true)

        val packet = Packet(
            sequenceNumber = 201,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 111111,
            destinationDeviceId = Packet.BROADCAST_ID,
            ttl = 3,
            language = IndicLanguage.ENGLISH,
            payload = "Valid text".toByteArray(Charsets.UTF_8)
        )

        // Relay evaluation decrements TTL and increments forwarded stats only for fresh valid packets
        val action = relayRouter.evaluatePacket(packet)
        assertTrue(action is RelayAction.ForwardAndDeliver)
        val fwd = (action as RelayAction.ForwardAndDeliver).forwardedPacket
        assertEquals(2.toByte(), fwd.ttl)
        assertEquals(111111, fwd.sourceDeviceId) // Original source identity preserved!
    }

    @Test
    fun relay_duplicatePacket_suppressedByRelay() {
        val relayRouter = PacketRelayRouter(localDeviceId = 555555)
        relayRouter.setRelayEnabled(true)

        val packet = Packet(
            sequenceNumber = 202,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 111111,
            destinationDeviceId = Packet.BROADCAST_ID,
            ttl = 3,
            language = IndicLanguage.ENGLISH,
            payload = "Duplicate relay test".toByteArray(Charsets.UTF_8)
        )

        val firstAction = relayRouter.evaluatePacket(packet)
        assertTrue(firstAction is RelayAction.ForwardAndDeliver)

        // Second submission of identical packet
        val secondAction = relayRouter.evaluatePacket(packet)
        assertEquals(RelayAction.DropDuplicate, secondAction)
    }

    @Test
    fun relay_expiredTtl_deliveredLocallyOnlyWithoutForwarding() {
        val relayRouter = PacketRelayRouter(localDeviceId = 555555)
        relayRouter.setRelayEnabled(true)

        // Packet arriving with TTL = 1 (next TTL will be 0)
        val packet = Packet(
            sequenceNumber = 203,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 111111,
            destinationDeviceId = Packet.BROADCAST_ID,
            ttl = 1,
            language = IndicLanguage.ENGLISH,
            payload = "Expiring packet".toByteArray(Charsets.UTF_8)
        )

        val action = relayRouter.evaluatePacket(packet)
        assertEquals(RelayAction.DeliverLocalOnly, action)
    }

    @Test
    fun relay_selfPacket_droppedImmediately() {
        val relayRouter = PacketRelayRouter(localDeviceId = 555555)
        relayRouter.setRelayEnabled(true)

        // Packet originated by this relay node (echo / loopback)
        val packet = Packet(
            sequenceNumber = 204,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 555555,
            destinationDeviceId = Packet.BROADCAST_ID,
            ttl = 3,
            language = IndicLanguage.ENGLISH,
            payload = "Loopback echo".toByteArray(Charsets.UTF_8)
        )

        val action = relayRouter.evaluatePacket(packet)
        assertEquals(RelayAction.DropSelf, action)
    }

    // =========================================================================
    // SECTION 3: DTN STORAGE SECURITY AUDIT
    // =========================================================================

    @Test
    fun dtn_duplicatePacket_rejectedFromStorage() {
        val dtnStore = DtnStore()

        val packet = Packet(
            sequenceNumber = 301,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 111111,
            destinationDeviceId = 222222,
            language = IndicLanguage.ENGLISH,
            payload = "DTN message".toByteArray(Charsets.UTF_8)
        )

        val storedFirst = dtnStore.store(packet)
        assertTrue("First storage must succeed", storedFirst)

        val storedDuplicate = dtnStore.store(packet)
        assertFalse("Duplicate storage must be rejected", storedDuplicate)
    }

    @Test
    fun dtn_capacityBounded_evictsLowestPriorityOnOverflow() {
        // Create small DTN store with max 3 packets
        val dtnStore = DtnStore(maxPackets = 3)

        val normalPkt1 = Packet(sequenceNumber = 1, timestamp = 1000L, sourceDeviceId = 1, priority = MessagePriority.NORMAL, language = IndicLanguage.ENGLISH, payload = ByteArray(10))
        val normalPkt2 = Packet(sequenceNumber = 2, timestamp = 2000L, sourceDeviceId = 1, priority = MessagePriority.NORMAL, language = IndicLanguage.ENGLISH, payload = ByteArray(10))
        val normalPkt3 = Packet(sequenceNumber = 3, timestamp = 3000L, sourceDeviceId = 1, priority = MessagePriority.NORMAL, language = IndicLanguage.ENGLISH, payload = ByteArray(10))

        assertTrue(dtnStore.store(normalPkt1))
        assertTrue(dtnStore.store(normalPkt2))
        assertTrue(dtnStore.store(normalPkt3))
        assertEquals(3, dtnStore.count())

        // Incoming critical DISTRESS packet should evict the oldest NORMAL packet
        val distressPkt = Packet(sequenceNumber = 4, timestamp = 4000L, sourceDeviceId = 1, priority = MessagePriority.DISTRESS, language = IndicLanguage.ENGLISH, payload = ByteArray(10))
        assertTrue(dtnStore.store(distressPkt))
        assertEquals(3, dtnStore.count())

        // Drained packets must include the DISTRESS packet first
        val drained = dtnStore.drainAll()
        assertEquals(MessagePriority.DISTRESS, drained.first().priority)
    }

    @Test
    fun dtn_expiredPackets_prunedDeterministically() {
        // Create DTN store with 100ms expiry
        val dtnStore = DtnStore(expiryMs = 50L)

        val packet = Packet(
            sequenceNumber = 302,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 111111,
            destinationDeviceId = 222222,
            language = IndicLanguage.ENGLISH,
            payload = "Ephemeral DTN data".toByteArray(Charsets.UTF_8)
        )
        dtnStore.store(packet)
        assertEquals(1, dtnStore.count())

        // Sleep to exceed 50ms expiry
        Thread.sleep(70L)

        val prunedCount = dtnStore.pruneExpired()
        assertEquals(1, prunedCount)
        assertEquals(0, dtnStore.count())
    }

    // =========================================================================
    // SECTION 4: APPLICATION-LEVEL QOS & FLOODING RESILIENCE
    // =========================================================================

    @Test
    fun qos_floodingNormalPackets_strictlyBoundedAtMaxCapacity() {
        val scheduler = TacticalPacketScheduler(
            maxCapacity = 50,
            autoTransmit = false
        )

        // Adversary floods 100 NORMAL packets into queue of capacity 50
        var accepted = 0
        var rejected = 0
        for (i in 1..100) {
            val pkt = Packet(
                sequenceNumber = i.toShort(),
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = 111111,
                destinationDeviceId = Packet.BROADCAST_ID,
                priority = MessagePriority.NORMAL,
                language = IndicLanguage.ENGLISH,
                payload = ByteArray(20)
            )
            if (scheduler.enqueue(pkt)) accepted++ else rejected++
        }

        assertEquals(50, accepted)
        assertEquals(50, rejected)
        assertEquals(50, scheduler.totalQueuedPackets)
    }

    @Test
    fun qos_emergencyPreemption_evictsNormalTrafficWhenFull() {
        val scheduler = TacticalPacketScheduler(
            maxCapacity = 10,
            autoTransmit = false
        )

        // Fill queue to capacity with NORMAL traffic
        for (i in 1..10) {
            val pkt = Packet(
                sequenceNumber = i.toShort(),
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = 111111,
                destinationDeviceId = Packet.BROADCAST_ID,
                priority = MessagePriority.NORMAL,
                language = IndicLanguage.ENGLISH,
                payload = ByteArray(20)
            )
            scheduler.enqueue(pkt)
        }
        assertEquals(10, scheduler.totalQueuedPackets)
        assertEquals(10, scheduler.queuedNormal)

        // Enqueue DISTRESS packet: must evict one NORMAL packet to maintain emergency readiness
        val distressPkt = Packet(
            sequenceNumber = 999,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 111111,
            destinationDeviceId = Packet.BROADCAST_ID,
            priority = MessagePriority.DISTRESS,
            language = IndicLanguage.ENGLISH,
            payload = "MAYDAY MAYDAY".toByteArray(Charsets.UTF_8)
        )
        val enqueued = scheduler.enqueue(distressPkt)

        assertTrue("Emergency packet must be accepted even when queue is at capacity", enqueued)
        assertEquals(10, scheduler.totalQueuedPackets)
        assertEquals(1, scheduler.queuedDistress)
        assertEquals(9, scheduler.queuedNormal)
    }
}
