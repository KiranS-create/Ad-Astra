package org.sih.itantra.core.mesh

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
import org.sih.itantra.core.context.ConflictResolutionResult
import org.sih.itantra.core.context.SharedContextEntry
import org.sih.itantra.core.context.SharedContextStore
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.message.MessageTechnicalInspectorMapper
import org.sih.itantra.core.message.journey.JourneyEventType
import org.sih.itantra.core.message.journey.MessageJourneyMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.vbr.ContextDelta
import org.sih.itantra.core.vbr.SemanticBase
import java.io.File
import javax.crypto.spec.SecretKeySpec

class ContextAwareRelayTest {

    private val nodeAId = 100001 // Transmitter
    private val nodeBId = 200002 // Intermediate Relay
    private val nodeCId = 300003 // Terminal Destination / Second Relay
    private val nodeDId = 400004 // Final Destination in 4-node chain

    private lateinit var relayRouterB: ContextAwareRelayRouter
    private lateinit var relayRouterC: ContextAwareRelayRouter

    private val testKey = ByteArray(32) { 0x5A.toByte() }

    @Before
    fun setUp() {
        SharedContextStore.clear()
        relayRouterB = ContextAwareRelayRouter(localDeviceId = nodeBId)
        relayRouterC = ContextAwareRelayRouter(localDeviceId = nodeCId)
    }

    private fun createBaseContext(
        sourceDeviceId: Int = nodeAId,
        category: EmergencyCategory = EmergencyCategory.MEDICAL,
        subtype: EmergencySubtype = EmergencySubtype.INJURED,
        severity: EmergencySeverity = EmergencySeverity.ALERT,
        count: Int = 3,
        sector: Short = 4,
        confidence: Int = 85,
        version: Int = 1
    ): SharedContextEntry {
        val ctxId = SharedContextStore.computeContextId(sourceDeviceId, category, sector)
        return SharedContextEntry(
            contextId = ctxId,
            version = version,
            category = category,
            subtype = subtype,
            severity = severity,
            count = count,
            sector = sector,
            confidence = confidence,
            sourceDeviceId = sourceDeviceId,
            createdAt = System.currentTimeMillis(),
            lastUpdatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + SharedContextStore.DEFAULT_TTL_MS
        )
    }

    private fun createDeltaPacket(
        delta: ContextDelta,
        sourceDeviceId: Int = nodeAId,
        destinationDeviceId: Int = nodeCId,
        seq: Short = 10,
        ttl: Byte = Packet.DEFAULT_TTL,
        priority: MessagePriority = MessagePriority.ALERT
    ): Packet {
        val payload = delta.serialize()
        return Packet(
            sourceDeviceId = sourceDeviceId,
            destinationDeviceId = destinationDeviceId,
            sequenceNumber = seq,
            timestamp = System.currentTimeMillis(),
            ttl = ttl,
            priority = priority,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = payload
        )
    }

    // =========================================================================
    // 1. RELAY PAYLOAD PRESERVATION & COMPACTNESS INVARIANTS (Tests 1–4)
    // =========================================================================

    @Test
    fun testRelayPreservesContextDeltaPayloadExactBytes() {
        val base = createBaseContext()
        val delta = ContextDelta(
            contextId = base.contextId,
            version = 2,
            count = 7,
            severity = EmergencySeverity.CRITICAL
        )
        val packet = createDeltaPacket(delta)

        val eval = relayRouterB.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, eval.decision)
        assertNotNull(eval.forwardedPacket)
        assertTrue(eval.isPayloadPreserved)
        assertArrayEquals(packet.payload, eval.forwardedPacket!!.payload)
    }

    @Test
    fun testRelayPreservesSemanticBasePayloadExactBytes() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.FIRE,
            subtype = EmergencySubtype.BUILDING,
            severity = EmergencySeverity.CRITICAL,
            count = 2,
            parameter = 5
        )
        val base = SemanticBase.fromCommand(cmd, sector = 5)
        val payload = base.serialize()

        val packet = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 11,
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            priority = MessagePriority.ALERT,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = payload
        )

        val eval = relayRouterB.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, eval.decision)
        assertTrue(eval.isPayloadPreserved)
        assertArrayEquals(payload, eval.forwardedPacket!!.payload)
    }

    @Test
    fun testRelayNeverExpandsDeltaToFullText() {
        val base = createBaseContext()
        val delta = ContextDelta(
            contextId = base.contextId,
            version = 2,
            count = 12
        )
        val packet = createDeltaPacket(delta)

        val eval = relayRouterB.evaluateRelay(packet)
        val forwarded = eval.forwardedPacket!!

        // Delta payload must remain compact binary (< 16 bytes), NOT expanded to full text
        assertTrue("Forwarded payload must be <= 16 bytes", forwarded.payload.size <= 16)
        assertTrue("Forwarded payload must start with MAGIC_DELTA 0xCD", ContextDelta.isContextDeltaPayload(forwarded.payload))
        val fullTextEquivalent = "Urgent medical evacuation required for 12 injured personnel in Sector 4"
        assertTrue("Forwarded delta must be far smaller than full text", forwarded.payload.size < fullTextEquivalent.toByteArray().size)
    }

    @Test
    fun testRelayNeverExpandsBaseToFullText() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.EVACUATION,
            subtype = EmergencySubtype.COLLAPSE,
            severity = EmergencySeverity.CRITICAL,
            count = 10,
            parameter = 3
        )
        val base = SemanticBase.fromCommand(cmd, sector = 3)
        val packet = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 12,
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = base.serialize()
        )

        val eval = relayRouterB.evaluateRelay(packet)
        val forwarded = eval.forwardedPacket!!
        assertEquals("Semantic base forwarded payload must remain 8 bytes", SemanticBase.BASE_SIZE_BYTES, forwarded.payload.size)
    }

    // =========================================================================
    // 2. IDENTITY & HOP METADATA INVARIANTS (Tests 5–8)
    // =========================================================================

    @Test
    fun testContextIdAndVersionImmutableAcrossHops() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packetA = createDeltaPacket(delta, destinationDeviceId = nodeDId, ttl = 3)

        // Hop 1: A -> B
        val evalB = relayRouterB.evaluateRelay(packetA)
        val packetB = evalB.forwardedPacket!!

        // Hop 2: B -> C
        val evalC = relayRouterC.evaluateRelay(packetB)
        val packetC = evalC.forwardedPacket!!

        val decodedA = ContextDelta.deserialize(packetA.payload)!!.delta
        val decodedB = ContextDelta.deserialize(packetB.payload)!!.delta
        val decodedC = ContextDelta.deserialize(packetC.payload)!!.delta

        assertEquals(base.contextId, decodedA.contextId)
        assertEquals(base.contextId, decodedB.contextId)
        assertEquals(base.contextId, decodedC.contextId)
        assertEquals(2, decodedA.version)
        assertEquals(2, decodedB.version)
        assertEquals(2, decodedC.version)
    }

    @Test
    fun testOriginalSourceDeviceIdPreservedAcrossHops() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packetA = createDeltaPacket(delta, sourceDeviceId = nodeAId, ttl = 3)

        val evalB = relayRouterB.evaluateRelay(packetA)
        val packetB = evalB.forwardedPacket!!

        assertEquals("Source device ID must remain Node A (not replaced by Node B)", nodeAId, packetB.sourceDeviceId)
    }

    @Test
    fun testDestinationDeviceIdPreservedAcrossHops() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packetA = createDeltaPacket(delta, destinationDeviceId = nodeCId, ttl = 3)

        val evalB = relayRouterB.evaluateRelay(packetA)
        val packetB = evalB.forwardedPacket!!

        assertEquals("Destination device ID must remain Node C across hops", nodeCId, packetB.destinationDeviceId)
    }

    @Test
    fun testTtlDecrementedAndFlagForwardedSet() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packet = createDeltaPacket(delta, ttl = 3)

        val eval = relayRouterB.evaluateRelay(packet)
        val forwarded = eval.forwardedPacket!!

        assertEquals(2.toByte(), forwarded.ttl)
        assertTrue(forwarded.isForwarded)
        assertTrue((forwarded.flags.toInt() and Packet.FLAG_FORWARDED) != 0)
    }

    // =========================================================================
    // 3. LOOPBACK & DUPLICATE SUPPRESSION (Tests 9–12)
    // =========================================================================

    @Test
    fun testTtlZeroDropPreventsInfiniteLoop() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packet = createDeltaPacket(delta, ttl = 1) // 1 hop remaining -> next TTL = 0

        val eval = relayRouterB.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.DROP_TTL_EXPIRED, eval.decision)
        assertNull(eval.forwardedPacket)
    }

    @Test
    fun testSelfPacketSuppression() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val selfPacket = createDeltaPacket(delta, sourceDeviceId = nodeBId)

        val eval = relayRouterB.evaluateRelay(selfPacket)
        assertEquals(ContextRelayDecision.DROP_SELF, eval.decision)
        assertNull(eval.forwardedPacket)
    }

    @Test
    fun testDuplicateSuppressionFirstHop() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packet = createDeltaPacket(delta, seq = 42)

        val firstEval = relayRouterB.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, firstEval.decision)

        val secondEval = relayRouterB.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.DROP_DUPLICATE, secondEval.decision)
    }

    @Test
    fun testMultiPathDuplicateSuppression() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packet = createDeltaPacket(delta, destinationDeviceId = nodeDId, seq = 77)

        // Path 1 arrives at Node C
        val evalPath1 = relayRouterC.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, evalPath1.decision)

        // Path 2 (alternate relay path) arrives with same source & sequenceNumber
        val packetPath2 = packet.copy(ttl = 1) // different TTL from longer path
        val evalPath2 = relayRouterC.evaluateRelay(packetPath2)
        assertEquals(ContextRelayDecision.DROP_DUPLICATE, evalPath2.decision)
    }

    // =========================================================================
    // 4. DESTINATION BEHAVIOR CASES A–F (Tests 13–22)
    // =========================================================================

    @Test
    fun testDestinationCaseA_MatchingContextReconstructed() {
        val base = createBaseContext(count = 3, severity = EmergencySeverity.ALERT)
        SharedContextStore.put(base)

        val delta = ContextDelta(
            contextId = base.contextId,
            version = 2,
            count = 8,
            severity = EmergencySeverity.CRITICAL
        )
        val packet = createDeltaPacket(delta)

        val result = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(result)
        assertEquals("RECONSTRUCTED", result!!.status)
        assertEquals("RECONSTRUCTED", result.forwardingAction)
        assertFalse(result.isFallback)
        assertEquals(2, result.contextVersion)
        assertEquals(8, result.updatedContext!!.count)
        assertEquals(EmergencySeverity.CRITICAL, result.updatedContext!!.severity)
    }

    @Test
    fun testDestinationCaseA_UpdatesStoreVersion() {
        val base = createBaseContext(count = 3, version = 1)
        SharedContextStore.put(base)

        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 9)
        val packet = createDeltaPacket(delta)

        relayRouterC.processDestinationDelta(packet, packet.payload)

        val inStore = SharedContextStore.get(base.contextId)
        assertNotNull(inStore)
        assertEquals(2, inStore!!.version)
        assertEquals(9, inStore.count)
    }

    @Test
    fun testDestinationCaseB_MissingContextFallback() {
        // SharedContextStore is empty — no base context established
        val delta = ContextDelta(
            contextId = 9999,
            version = 2,
            count = 5,
            severity = EmergencySeverity.IMPORTANT
        )
        val packet = createDeltaPacket(delta)

        val result = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(result)
        assertEquals("FALLBACK", result!!.status)
        assertEquals("FALLBACK", result.forwardingAction)
        assertTrue(result.isFallback)
        assertTrue(result.displayText.contains("CTX #9999"))
        assertTrue(result.displayText.contains("PEOPLE: 5"))
    }

    @Test
    fun testDestinationCaseB_FallbackPreservesSemanticSummary() {
        val delta = ContextDelta(
            contextId = 7777,
            version = 1,
            sector = 8,
            category = EmergencyCategory.SECURITY
        )
        val packet = createDeltaPacket(delta)

        val result = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(result)
        assertTrue(result!!.deltaSummary.contains("SECTOR: 8"))
        assertTrue(result.deltaSummary.contains("CATEGORY: SECURITY", ignoreCase = true))
    }

    @Test
    fun testDestinationCaseC_ExpiredContextFallback() {
        // Base context expired in the past
        val expiredBase = createBaseContext().copy(expiresAt = System.currentTimeMillis() - 10_000L)
        SharedContextStore.put(expiredBase)

        val delta = ContextDelta(contextId = expiredBase.contextId, version = 2, count = 4)
        val packet = createDeltaPacket(delta)

        val result = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(result)
        assertEquals("FALLBACK", result!!.status)
        assertTrue(result.isFallback)
    }

    @Test
    fun testDestinationCaseD_StaleDeltaRejected() {
        // Store has Version 3
        val currentContext = createBaseContext(version = 3, count = 15)
        SharedContextStore.put(currentContext)

        // Arriving delta is older (Version 2)
        val staleDelta = ContextDelta(contextId = currentContext.contextId, version = 2, count = 7)
        val packet = createDeltaPacket(staleDelta)

        val result = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(result)
        assertEquals("REJECTED STALE", result!!.status)
        assertEquals("REJECTED STALE", result.forwardingAction)
        assertTrue(result.isFallback)
    }

    @Test
    fun testDestinationCaseD_DoesNotOverwriteNewerContext() {
        val currentContext = createBaseContext(version = 3, count = 15)
        SharedContextStore.put(currentContext)

        val staleDelta = ContextDelta(contextId = currentContext.contextId, version = 2, count = 7)
        val packet = createDeltaPacket(staleDelta)

        relayRouterC.processDestinationDelta(packet, packet.payload)

        val inStore = SharedContextStore.get(currentContext.contextId)
        assertNotNull(inStore)
        assertEquals(3, inStore!!.version)
        assertEquals("Store must not be overwritten by stale delta", 15, inStore.count)
    }

    @Test
    fun testDestinationCaseE_DuplicateIdenticalIdempotent() {
        val currentContext = createBaseContext(version = 2, count = 5)
        SharedContextStore.put(currentContext)

        // Arriving delta has identical facts and version
        val identicalDelta = ContextDelta(contextId = currentContext.contextId, version = 2, count = 5)
        val packet = createDeltaPacket(identicalDelta)

        val result = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(result)
        assertEquals("DUPLICATE SUPPRESSED", result!!.status)
        assertEquals("DUPLICATE SUPPRESSED", result.forwardingAction)
        assertFalse(result.isFallback)
        assertEquals("TTS audio should be suppressed for idempotent duplicate", "", result.ttsSpeechText)
    }

    @Test
    fun testDestinationCaseF_ConflictingVersionRejected() {
        // Store has Version 2 with Sector 4
        val currentContext = createBaseContext(version = 2, sector = 4)
        SharedContextStore.put(currentContext)

        // Arriving delta has Version 2 with conflicting Sector 9
        val conflictingDelta = ContextDelta(contextId = currentContext.contextId, version = 2, sector = 9)
        val packet = createDeltaPacket(conflictingDelta)

        val result = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(result)
        assertEquals("REJECTED CONFLICT", result!!.status)
        assertEquals("REJECTED CONFLICT", result.forwardingAction)
        assertTrue(result.isFallback)
    }

    @Test
    fun testDestinationCaseF_StoreRemainsConsistent() {
        val currentContext = createBaseContext(version = 2, sector = 4)
        SharedContextStore.put(currentContext)

        val conflictingDelta = ContextDelta(contextId = currentContext.contextId, version = 2, sector = 9)
        val packet = createDeltaPacket(conflictingDelta)

        relayRouterC.processDestinationDelta(packet, packet.payload)

        val inStore = SharedContextStore.get(currentContext.contextId)
        assertNotNull(inStore)
        assertEquals(4.toShort(), inStore!!.sector)
    }

    // =========================================================================
    // 5. DTN INTEGRATION (Tests 23–26)
    // =========================================================================

    @Test
    fun testDtnStoresContextDeltaWithoutConversion() {
        val dtnStore = org.sih.itantra.core.mesh.DtnStore()
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 6)
        val packet = createDeltaPacket(delta)

        val accepted = dtnStore.store(packet)
        assertTrue(accepted)
        assertEquals(1, dtnStore.size())
    }

    @Test
    fun testDtnDrainsContextDeltaInPriorityOrder() {
        val dtnStore = org.sih.itantra.core.mesh.DtnStore()
        val base = createBaseContext()

        val normalPacket = createDeltaPacket(
            ContextDelta(contextId = base.contextId, version = 1, count = 1),
            seq = 1,
            priority = MessagePriority.NORMAL
        )
        val distressPacket = createDeltaPacket(
            ContextDelta(contextId = base.contextId, version = 2, count = 2),
            seq = 2,
            priority = MessagePriority.DISTRESS
        )
        val alertPacket = createDeltaPacket(
            ContextDelta(contextId = base.contextId, version = 3, count = 3),
            seq = 3,
            priority = MessagePriority.ALERT
        )

        dtnStore.store(normalPacket)
        dtnStore.store(distressPacket)
        dtnStore.store(alertPacket)

        val drained = dtnStore.drainAll()
        assertEquals(3, drained.size)
        assertEquals(MessagePriority.DISTRESS, drained[0].priority)
        assertEquals(MessagePriority.ALERT, drained[1].priority)
        assertEquals(MessagePriority.NORMAL, drained[2].priority)
    }

    @Test
    fun testDtnPrunesExpiredContextDeltas() {
        val dtnStore = org.sih.itantra.core.mesh.DtnStore(expiryMs = 50L)
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packet = createDeltaPacket(delta)

        dtnStore.store(packet)
        assertEquals(1, dtnStore.size())

        Thread.sleep(80L)
        val pruned = dtnStore.pruneExpired()
        assertEquals(1, pruned)
        assertEquals(0, dtnStore.size())
    }

    @Test
    fun testDtnForwardPreservesContextDeltaWirePayload() {
        val dtnStore = org.sih.itantra.core.mesh.DtnStore()
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 14)
        val packet = createDeltaPacket(delta)

        dtnStore.store(packet)
        val drained = dtnStore.drainAll().first()

        assertArrayEquals(packet.payload, drained.payload)
        assertTrue(ContextDelta.isContextDeltaPayload(drained.payload))
        val decoded = ContextDelta.deserialize(drained.payload)!!.delta
        assertEquals(14, decoded.count)
    }

    // =========================================================================
    // 6. QOS, HMAC & CRC HOP INVARIANCE (Tests 27–29)
    // =========================================================================

    @Test
    fun testQosPriorityPreservedAcrossMultiHopRelay() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packetA = createDeltaPacket(delta, destinationDeviceId = nodeDId, priority = MessagePriority.ALERT, ttl = 3)

        val evalB = relayRouterB.evaluateRelay(packetA)
        val packetB = evalB.forwardedPacket!!

        val evalC = relayRouterC.evaluateRelay(packetB)
        val packetC = evalC.forwardedPacket!!

        assertEquals(MessagePriority.ALERT, packetB.priority)
        assertEquals(MessagePriority.ALERT, packetC.priority)
    }

    @Test
    fun testHmacHopInvarianceAcrossRelays() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packetA = createDeltaPacket(delta, ttl = 3)

        // 1. Source signs packet
        val signedPacketA = PacketAuthenticator.sign(packetA, testKey)
        assertTrue(signedPacketA.isAuthenticated)

        // 2. Intermediate Relay B evaluates and forwards
        val evalB = relayRouterB.evaluateRelay(signedPacketA)
        val packetB = evalB.forwardedPacket!!

        // 3. Serialize on wire and deserialize at destination Node C
        val wireBytes = PacketSerializer.serialize(packetB)
        val receivedAtC = PacketSerializer.deserialize(wireBytes)

        // 4. Node C verifies HMAC
        val verifyStatus = PacketAuthenticator.verify(receivedAtC, testKey)
        assertTrue(
            "HMAC must remain valid across relay hops due to canonical flag masking and TTL exclusion",
            verifyStatus.isValid
        )
    }

    @Test
    fun testCrcRecalculationOnForwardedWireFrame() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packetA = createDeltaPacket(delta, ttl = 3)

        val evalB = relayRouterB.evaluateRelay(packetA)
        val packetB = evalB.forwardedPacket!!

        val serialized = PacketSerializer.serialize(packetB)
        val deserialized = PacketSerializer.deserialize(serialized)

        assertEquals(packetB.ttl, deserialized.ttl)
        assertEquals(packetB.flags, deserialized.flags)
        assertArrayEquals(packetB.payload, deserialized.payload)
    }

    // =========================================================================
    // 7. UNICAST VS BROADCAST RELAY FORWARDING DECISION (Tests 30–32)
    // =========================================================================

    @Test
    fun testUnicastPacketRelayDoesNotDeliverToLocalUser() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        // Packet addressed specifically to Node C (Node B is pure intermediate relay)
        val packet = createDeltaPacket(delta, destinationDeviceId = nodeCId)

        val eval = relayRouterB.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, eval.decision)
    }

    @Test
    fun testBroadcastPacketRelayForwardsAndDeliversLocally() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packet = createDeltaPacket(delta, destinationDeviceId = Packet.BROADCAST_ID)

        val eval = relayRouterB.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.FORWARD_AND_DELIVER, eval.decision)
    }

    @Test
    fun testTerminalDestinationDoesNotForwardFurther() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        // Packet reached destination Node C
        val packet = createDeltaPacket(delta, destinationDeviceId = nodeCId)

        val eval = relayRouterC.evaluateRelay(packet)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, eval.decision)
        assertNull(eval.forwardedPacket)
    }

    // =========================================================================
    // 8. END-TO-END MULTI-HOP SIMULATION CHAINS (Tests 33–34)
    // =========================================================================

    @Test
    fun testMultiHopChain_ThreeNodes_A_B_C() {
        // Node A: Source, Node B: Relay, Node C: Destination
        val baseA = createBaseContext(sourceDeviceId = nodeAId, count = 2)
        SharedContextStore.put(baseA) // Shared context established initially

        // 1. Node A creates Delta v2
        val deltaA = ContextDelta(contextId = baseA.contextId, version = 2, count = 8)
        val packetA = createDeltaPacket(deltaA, sourceDeviceId = nodeAId, destinationDeviceId = nodeCId, ttl = 3)

        // 2. Node B relays packet
        val evalB = relayRouterB.evaluateRelay(packetA)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, evalB.decision)
        val packetB = evalB.forwardedPacket!!
        assertEquals(2.toByte(), packetB.ttl)

        // 3. Node C receives packet
        val evalC = relayRouterC.evaluateRelay(packetB)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, evalC.decision)

        // 4. Node C processes delta
        val reconC = relayRouterC.processDestinationDelta(packetB, packetB.payload)
        assertNotNull(reconC)
        assertEquals("RECONSTRUCTED", reconC!!.status)
        assertEquals(8, reconC.updatedContext!!.count)
        assertEquals(2, reconC.contextVersion)
    }

    @Test
    fun testMultiHopChain_FourNodes_A_B_C_D() {
        // 4 nodes: A -> B -> C -> D
        val relayRouterD = ContextAwareRelayRouter(localDeviceId = nodeDId)
        val base = createBaseContext(count = 1)
        SharedContextStore.put(base)

        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 5)
        val packetA = createDeltaPacket(delta, sourceDeviceId = nodeAId, destinationDeviceId = nodeDId, ttl = 3)

        // Hop 1: A -> B (TTL 3 -> 2)
        val evalB = relayRouterB.evaluateRelay(packetA)
        val packetB = evalB.forwardedPacket!!
        assertEquals(2.toByte(), packetB.ttl)

        // Hop 2: B -> C (TTL 2 -> 1)
        val evalC = relayRouterC.evaluateRelay(packetB)
        val packetC = evalC.forwardedPacket!!
        assertEquals(1.toByte(), packetC.ttl)

        // Hop 3: C -> D (Destination reached)
        val evalD = relayRouterD.evaluateRelay(packetC)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, evalD.decision)

        // Terminal reconstruction at Node D
        val recon = relayRouterD.processDestinationDelta(packetC, packetC.payload)
        assertNotNull(recon)
        assertEquals("RECONSTRUCTED", recon!!.status)
        assertEquals(5, recon.updatedContext!!.count)
    }

    // =========================================================================
    // 9. OBSERVABILITY: JOURNEY & TECHNICAL INSPECTOR (Tests 35–36)
    // =========================================================================

    @Test
    fun testMessageJourneyMultiHopContextDeltaLogging() {
        val record = MessageRecord(
            id = "test-journey-delta",
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.RECEIVED,
            language = IndicLanguage.HINDI,
            priority = MessagePriority.ALERT,
            text = "Emergency medical update: 8 injured in Sector 4",
            peer = "Node #100001",
            packetSizeBytes = 42,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 2.5,
            isRelayed = true,
            hopCount = 2,
            isSemantic = true,
            isContextDelta = true,
            contextId = 555,
            contextVersion = 2,
            forwardingAction = "RECONSTRUCTED",
            contextReconstructionStatus = "RECONSTRUCTED"
        )

        val journey = MessageJourneyMapper.map(record)

        val relayedEvent = journey.events.find { it.type == JourneyEventType.RELAYED }
        assertNotNull(relayedEvent)
        assertTrue(relayedEvent?.detail?.contains("Context delta v2 forwarded unchanged (2 hops)") == true)

        val receivedEvent = journey.events.find { it.type == JourneyEventType.RECEIVED }
        assertNotNull(receivedEvent)
        assertTrue(receivedEvent?.detail?.contains("Reconstructed from Context #555 v2") == true)
    }

    @Test
    fun testMessageTechnicalInspectorMultiHopFields() {
        val record = MessageRecord(
            id = "test-inspector-delta",
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.RECEIVED,
            language = IndicLanguage.HINDI,
            priority = MessagePriority.ALERT,
            text = "Medical emergency",
            peer = "Node #100001",
            packetSizeBytes = 42,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 2.5,
            isRelayed = true,
            hopCount = 2,
            isSemantic = true,
            isContextDelta = true,
            contextId = 555,
            contextVersion = 2,
            forwardingAction = "FORWARDED UNCHANGED",
            contextReconstructionStatus = "RECONSTRUCTED",
            relayNodeId = nodeBId
        )

        val inspector = MessageTechnicalInspectorMapper.map(record)

        val routeSection = inspector.sections.find { it.title == "ROUTE" }
        assertNotNull(routeSection)
        val fwdActionField = routeSection!!.fields.find { it.label == "FORWARDING ACTION" }
        assertNotNull(fwdActionField)
        assertEquals("FORWARDED UNCHANGED", fwdActionField!!.value)

        val relayNodeField = routeSection.fields.find { it.label == "RELAY NODE" }
        assertNotNull(relayNodeField)
        assertEquals("Node #$nodeBId", relayNodeField!!.value)

        val messageSection = inspector.sections.find { it.title == "MESSAGE" }
        assertNotNull(messageSection)
        val reconStatusField = messageSection!!.fields.find { it.label == "RECONSTRUCTED STATUS" }
        assertNotNull(reconStatusField)
        assertTrue(reconStatusField!!.value.contains("VALID (Reconstructed from Context #555 v2)"))
    }

    // =========================================================================
    // 10. BANDWIDTH METRICS (Test 37)
    // =========================================================================

    @Test
    fun testBandwidthSavingsMultiHopContextDelta() {
        val base = createBaseContext()
        val delta = ContextDelta(contextId = base.contextId, version = 2, count = 10)
        val deltaPayloadSize = delta.serialize().size // ~8 bytes
        val fullTextPayloadSize = "Urgent: 10 injured casualties requiring medical triage in Sector 4".toByteArray().size // ~66 bytes

        val headerSize = Packet.MIN_PACKET_SIZE // 32 bytes

        val deltaPacketSize = headerSize + deltaPayloadSize
        val fullTextPacketSize = headerSize + fullTextPayloadSize

        // Across 3 hops (A -> B -> C -> D = 3 transmissions)
        val hops = 3
        val totalDeltaMeshBytes = deltaPacketSize * hops
        val totalFullTextMeshBytes = fullTextPacketSize * hops

        val bytesSaved = totalFullTextMeshBytes - totalDeltaMeshBytes
        val percentSaved = (bytesSaved.toDouble() / totalFullTextMeshBytes.toDouble()) * 100.0

        assertTrue("Cumulative delta bytes across mesh must be < full text bytes", totalDeltaMeshBytes < totalFullTextMeshBytes)
        assertTrue("Cumulative mesh bandwidth reduction must exceed 50%", percentSaved > 50.0)
    }
}
