package org.sih.itantra.core.network

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.context.SharedContextEntry
import org.sih.itantra.core.context.SharedContextStore
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.mesh.ContextAwareRelayRouter
import org.sih.itantra.core.mesh.ContextRelayDecision
import org.sih.itantra.core.protocol.*
import org.sih.itantra.core.vbr.*

class Feature29MultiHopRelayTest {

    private val nodeAId = 100001
    private val nodeBId = 200002
    private val nodeCId = 300003
    private val nodeDId = 400004

    private lateinit var relayRouterB: ContextAwareRelayRouter
    private lateinit var relayRouterC: ContextAwareRelayRouter
    private lateinit var relayRouterD: ContextAwareRelayRouter

    private val testKey = ByteArray(32) { 0x4A.toByte() }

    @Before
    fun setUp() {
        SharedContextStore.clear()
        relayRouterB = ContextAwareRelayRouter(localDeviceId = nodeBId)
        relayRouterC = ContextAwareRelayRouter(localDeviceId = nodeCId)
        relayRouterD = ContextAwareRelayRouter(localDeviceId = nodeDId)
    }

    private fun createSampleContext(
        sourceDeviceId: Int = nodeAId,
        category: EmergencyCategory = EmergencyCategory.MEDICAL,
        subtype: EmergencySubtype = EmergencySubtype.INJURED,
        severity: EmergencySeverity = EmergencySeverity.ALERT,
        count: Int = 3,
        sector: Short = 4,
        confidence: Int = 90,
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

    @Test
    fun test01_fullForwarding_preservesPayloadAndDecrementsTtl() {
        val text = "CRITICAL FLOODING EVACUATE CAMP IMMEDIATELY"
        val payload = text.toByteArray(Charsets.UTF_8)
        val packetA = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeDId,
            sequenceNumber = 101,
            timestamp = System.currentTimeMillis(),
            ttl = 4,
            priority = MessagePriority.DISTRESS,
            flags = 0,
            language = IndicLanguage.ENGLISH,
            payload = payload
        )

        val evalB = relayRouterB.evaluateRelay(packetA)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, evalB.decision)
        val packetB = evalB.forwardedPacket
        assertNotNull(packetB)
        assertEquals(3.toByte(), packetB!!.ttl)
        assertTrue(packetB.payload.contentEquals(payload))
        assertTrue((packetB.flags.toInt() and Packet.FLAG_FORWARDED) != 0)

        val evalC = relayRouterC.evaluateRelay(packetB)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, evalC.decision)
        val packetC = evalC.forwardedPacket
        assertNotNull(packetC)
        assertEquals(2.toByte(), packetC!!.ttl)
        assertTrue(packetC.payload.contentEquals(payload))

        val evalD = relayRouterD.evaluateRelay(packetC)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, evalD.decision)
        assertNull(evalD.forwardedPacket)
        assertEquals(text, String(packetC.payload, Charsets.UTF_8))
    }

    @Test
    fun test02_compactForwarding_preservesCompressedBytesAcrossAllHops() {
        val originalText = "MEDICAL EMERGENCY WATER RISING SECTOR FOUR REQUIRING IMMEDIATE AIRLIFT AND EVACUATION PLAN"
        val rawBytes = originalText.toByteArray(Charsets.UTF_8)
        val compression = AdaptiveCompressor.compress(rawBytes)
        val compressedBytes = compression.bytes
        val packetA = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeDId,
            sequenceNumber = 102,
            timestamp = System.currentTimeMillis(),
            ttl = 5,
            priority = MessagePriority.ALERT,
            flags = Packet.FLAG_COMPRESSED.toByte(),
            language = IndicLanguage.ENGLISH,
            payload = compressedBytes
        )

        val packetB = relayRouterB.evaluateRelay(packetA).forwardedPacket!!
        assertEquals(4.toByte(), packetB.ttl)
        assertArrayEquals(compressedBytes, packetB.payload)

        val packetC = relayRouterC.evaluateRelay(packetB).forwardedPacket!!
        assertEquals(3.toByte(), packetC.ttl)
        assertArrayEquals(compressedBytes, packetC.payload)

        val evalD = relayRouterD.evaluateRelay(packetC)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, evalD.decision)
        val decompressedBytes = AdaptiveCompressor.decompress(packetC.payload, isCompressed = true)
        val decompressedText = String(decompressedBytes, Charsets.UTF_8)
        assertEquals(originalText, decompressedText)
    }

    @Test
    fun test03_semanticBaseForwarding_preservesEightBytesWithoutExpansion() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.FIRE,
            subtype = EmergencySubtype.BUILDING,
            severity = EmergencySeverity.CRITICAL,
            count = 12,
            parameter = 9
        )
        val base = SemanticBase.fromCommand(cmd, sector = 9)
        val payload = base.serialize()
        assertEquals(8, payload.size)

        val packetA = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeDId,
            sequenceNumber = 103,
            timestamp = System.currentTimeMillis(),
            ttl = 4,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = payload
        )

        val evalB = relayRouterB.evaluateRelay(packetA)
        assertTrue(evalB.isPayloadPreserved)
        assertEquals(8, evalB.wirePayloadBytes)
        val packetB = evalB.forwardedPacket!!

        val evalC = relayRouterC.evaluateRelay(packetB)
        assertTrue(evalC.isPayloadPreserved)
        assertEquals(8, evalC.wirePayloadBytes)
        val packetC = evalC.forwardedPacket!!

        val evalD = relayRouterD.evaluateRelay(packetC)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, evalD.decision)

        val decodedD = SemanticBase.deserialize(packetC.payload)
        assertNotNull(decodedD)
        assertEquals(EmergencyCategory.FIRE, decodedD!!.category)
        assertEquals(EmergencySubtype.BUILDING, decodedD.subtype)
        assertEquals(EmergencySeverity.CRITICAL, decodedD.severity)
        assertEquals(12, decodedD.count)
        assertEquals(9.toShort(), decodedD.sector)
    }

    @Test
    fun test04_semanticEnhancedForwarding_preservesBaseAndEnhancementLayers() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.RESCUE,
            subtype = EmergencySubtype.COLLAPSE,
            severity = EmergencySeverity.CRITICAL,
            count = 5,
            parameter = 3
        )
        val base = SemanticBase.fromCommand(cmd, sector = 3)
        val enh = SemanticEnhancement(text = "EAST WING ROOFTOP COLLAPSE")
        val payload = SemanticBase.serializeComposite(base, enh)

        val packetA = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeDId,
            sequenceNumber = 104,
            timestamp = System.currentTimeMillis(),
            ttl = 4,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.ENGLISH,
            payload = payload
        )

        val packetB = relayRouterB.evaluateRelay(packetA).forwardedPacket!!
        val packetC = relayRouterC.evaluateRelay(packetB).forwardedPacket!!
        val evalD = relayRouterD.evaluateRelay(packetC)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, evalD.decision)

        val decoded = SemanticBase.deserializeWithEnhancement(packetC.payload)
        assertNotNull(decoded.base)
        assertEquals(EmergencyCategory.RESCUE, decoded.base!!.category)
        assertEquals(EmergencySubtype.COLLAPSE, decoded.base!!.subtype)
        assertEquals(5, decoded.base!!.count)
        assertNotNull(decoded.enhancement)
        assertEquals("EAST WING ROOFTOP COLLAPSE", decoded.enhancement!!.text)
    }

    @Test
    fun test05_contextDeltaForwarding_preservesBitForBitAndUpdatesDestination() {
        val baseCtx = createSampleContext(
            sourceDeviceId = nodeAId,
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.INJURED,
            severity = EmergencySeverity.ALERT,
            count = 2,
            sector = 4,
            version = 1
        )
        SharedContextStore.put(baseCtx)

        val delta = ContextDelta(
            contextId = baseCtx.contextId,
            version = 2,
            count = 5,
            severity = EmergencySeverity.CRITICAL,
            subtype = EmergencySubtype.UNCONSCIOUS
        )
        val deltaPayload = delta.serialize()

        val packetA = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeDId,
            sequenceNumber = 105,
            timestamp = System.currentTimeMillis(),
            ttl = 4,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = deltaPayload
        )

        val evalB = relayRouterB.evaluateRelay(packetA)
        assertTrue(evalB.isPayloadPreserved)
        val packetB = evalB.forwardedPacket!!

        val evalC = relayRouterC.evaluateRelay(packetB)
        assertTrue(evalC.isPayloadPreserved)
        val packetC = evalC.forwardedPacket!!

        val evalD = relayRouterD.evaluateRelay(packetC)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, evalD.decision)

        val destResult = relayRouterD.processDestinationDelta(packetC, packetC.payload)
        assertNotNull(destResult)
        assertEquals("RECONSTRUCTED", destResult!!.status)
        assertFalse(destResult.isFallback)
        assertEquals(2, destResult.contextVersion)
        assertEquals(5, destResult.updatedContext?.count)
        assertEquals(EmergencySeverity.CRITICAL, destResult.updatedContext?.severity)
        assertEquals(EmergencySubtype.UNCONSCIOUS, destResult.updatedContext?.subtype)
    }

    @Test
    fun test06_ttlExpiry_dropsWhenTtlReachesZero() {
        val packetA = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 106,
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            priority = MessagePriority.NORMAL,
            flags = 0,
            language = IndicLanguage.ENGLISH,
            payload = "EXPIRE TEST".toByteArray()
        )

        val evalB = relayRouterB.evaluateRelay(packetA)
        assertEquals(ContextRelayDecision.DROP_TTL_EXPIRED, evalB.decision)
        assertNull(evalB.forwardedPacket)
        assertEquals("Hop limit reached (TTL decremented to 0)", evalB.dropReason)
        assertEquals(1L, relayRouterB.getStats()["ttlExpired"])
    }

    @Test
    fun test07_duplicatePacketSuppression_dropsDuplicateSequenceNumbers() {
        val packetA = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 107,
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            priority = MessagePriority.NORMAL,
            flags = 0,
            language = IndicLanguage.ENGLISH,
            payload = "DUP TEST".toByteArray()
        )

        val eval1 = relayRouterB.evaluateRelay(packetA)
        assertEquals(ContextRelayDecision.FORWARD_ONLY, eval1.decision)
        assertNotNull(eval1.forwardedPacket)

        val eval2 = relayRouterB.evaluateRelay(packetA)
        assertEquals(ContextRelayDecision.DROP_DUPLICATE, eval2.decision)
        assertNull(eval2.forwardedPacket)
        assertEquals(1L, relayRouterB.getStats()["duplicatesDropped"])
    }

    @Test
    fun test08_relayLoopPrevention_dropsPacketOriginatedBySelf() {
        val echoPacket = Packet(
            sourceDeviceId = nodeBId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 108,
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            priority = MessagePriority.ALERT,
            flags = Packet.FLAG_FORWARDED.toByte(),
            language = IndicLanguage.ENGLISH,
            payload = "ECHO TEST".toByteArray()
        )

        val eval = relayRouterB.evaluateRelay(echoPacket)
        assertEquals(ContextRelayDecision.DROP_SELF, eval.decision)
        assertNull(eval.forwardedPacket)
        assertEquals(1L, relayRouterB.getStats()["selfDropped"])
    }

    @Test
    fun test09_staleContext_rejectedGracefullyAtDestination() {
        val activeContext = createSampleContext(
            sourceDeviceId = nodeAId,
            version = 3,
            count = 10
        )
        SharedContextStore.put(activeContext)

        val staleDelta = ContextDelta(
            contextId = activeContext.contextId,
            version = 2,
            count = 8
        )
        val packet = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 109,
            timestamp = System.currentTimeMillis(),
            ttl = 2,
            priority = MessagePriority.ALERT,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = staleDelta.serialize()
        )

        val destResult = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(destResult)
        assertEquals("REJECTED STALE", destResult!!.status)
        assertTrue(destResult.isFallback)
        assertEquals(activeContext.version, destResult.updatedContext?.version)
        assertEquals(10, destResult.updatedContext?.count)
    }

    @Test
    fun test10_missingContext_gracefulFallbackWithoutCrash() {
        val testCtxId = 9009
        val delta = ContextDelta(
            contextId = testCtxId,
            version = 2,
            count = 4,
            severity = EmergencySeverity.CRITICAL
        )
        val packet = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 110,
            timestamp = System.currentTimeMillis(),
            ttl = 2,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = delta.serialize()
        )

        val destResult = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(destResult)
        assertEquals("FALLBACK", destResult!!.status)
        assertTrue(destResult.isFallback)
        assertNull(destResult.updatedContext)
        assertTrue(destResult.displayText.contains("CTX #$testCtxId"))
    }

    @Test
    fun test11_conflictingContext_rejectedAndRetainsActiveContext() {
        val baseCtx = createSampleContext(
            sourceDeviceId = nodeAId,
            category = EmergencyCategory.MEDICAL,
            version = 1
        )
        SharedContextStore.put(baseCtx)

        val conflictingDelta = ContextDelta(
            contextId = baseCtx.contextId,
            version = 1,
            severity = EmergencySeverity.CRITICAL,
            count = 100
        )
        val packet = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 111,
            timestamp = System.currentTimeMillis(),
            ttl = 2,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = conflictingDelta.serialize()
        )

        val destResult = relayRouterC.processDestinationDelta(packet, packet.payload)
        assertNotNull(destResult)
        assertEquals("REJECTED CONFLICT", destResult!!.status)
        assertTrue(destResult.isFallback)
        assertEquals(baseCtx.severity, destResult.updatedContext?.severity)
    }

    @Test
    fun test12_invalidHmac_detectsTamperedPayloadOrKeyAcrossRelay() {
        val originalPacket = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 112,
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            priority = MessagePriority.DISTRESS,
            flags = 0,
            language = IndicLanguage.ENGLISH,
            payload = "LEGITIMATE AUTHENTICATED PAYLOAD".toByteArray()
        )

        val signedPacket = PacketAuthenticator.sign(originalPacket, testKey)
        val result1 = PacketAuthenticator.verify(signedPacket, testKey)
        assertTrue(result1.isValid)

        val forwarded = relayRouterB.evaluateRelay(signedPacket).forwardedPacket!!

        val resultForwarded = PacketAuthenticator.verify(forwarded, testKey)
        assertTrue(resultForwarded.isValid)

        val tamperedPayload = "MALICIOUS TAMPERED PAYLOAD".toByteArray()
        val tamperedPacket = forwarded.copy(payload = tamperedPayload)

        val resultTampered = PacketAuthenticator.verify(tamperedPacket, testKey)
        assertFalse(resultTampered.isValid)

        val wrongKey = ByteArray(32) { 0x99.toByte() }
        val resultWrongKey = PacketAuthenticator.verify(forwarded, wrongKey)
        assertFalse(resultWrongKey.isValid)
    }

    @Test
    fun test13_corruptedCrc_detectsBitErrorsInWireFraming() {
        val packet = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeCId,
            sequenceNumber = 113,
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            priority = MessagePriority.ALERT,
            flags = 0,
            language = IndicLanguage.ENGLISH,
            payload = "CRC INTEGRITY TEST".toByteArray()
        )

        val wireBytes = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(wireBytes)
        assertEquals(packet.sequenceNumber, deserialized.sequenceNumber)

        val corruptedBytes = wireBytes.clone()
        corruptedBytes[Packet.HEADER_SIZE_BYTES + 2] = (corruptedBytes[Packet.HEADER_SIZE_BYTES + 2].toInt() xor 0xFF).toByte()

        try {
            PacketSerializer.deserialize(corruptedBytes)
            fail("Expected CorruptPacketException due to CRC mismatch")
        } catch (e: CorruptPacketException) {
            assertTrue(e.message?.contains("CRC") == true)
        }
    }

    @Test
    fun test14_representationDowngrade_onlyWhenNecessary() {
        val text = "THREE INJURED PEOPLE SECTOR FOUR MEDICAL ASSISTANCE"

        // High confidence (0.95 >= 0.85): selects SEMANTIC_ENHANCED
        val repHigh = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            semanticConfidence = 0.95f,
            useLayeredSemantic = true
        )
        assertTrue(
            repHigh.mode == AdaptiveRepresentationMode.SEMANTIC_ENHANCED ||
            repHigh.mode == AdaptiveRepresentationMode.SEMANTIC_BASE
        )

        // With shared context: selects CONTEXT_DELTA
        val baseContext = createSampleContext(
            sourceDeviceId = nodeAId,
            category = EmergencyCategory.MEDICAL,
            sector = 4,
            version = 1
        )
        SharedContextStore.put(baseContext)

        val repDelta = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            semanticConfidence = 0.95f,
            sourceDeviceId = nodeAId,
            useSharedContext = true
        )
        assertEquals(AdaptiveRepresentationMode.CONTEXT_DELTA, repDelta.mode)

        // Low confidence (0.70 < 0.85): downgrades to COMPACT under constrained network
        val repLow = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.DEGRADED,
            language = IndicLanguage.ENGLISH,
            semanticConfidence = 0.70f,
            sourceDeviceId = nodeAId,
            useSharedContext = true
        )
        assertEquals(AdaptiveRepresentationMode.COMPACT, repLow.mode)
    }

    @Test
    fun test15_semanticMeaning_remainsUnchangedAcrossFourHops() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.RESCUE,
            subtype = EmergencySubtype.COLLAPSE,
            severity = EmergencySeverity.CRITICAL,
            count = 7,
            parameter = 12
        )
        val base = SemanticBase.fromCommand(cmd, sector = 12)
        val enh = SemanticEnhancement(text = "COLLAPSED COMMERCIAL BUILDING BASEMENT")
        val wirePayload = SemanticBase.serializeComposite(base, enh)

        val originalPacket = Packet(
            sourceDeviceId = nodeAId,
            destinationDeviceId = nodeDId,
            sequenceNumber = 115,
            timestamp = 1718000000000L,
            ttl = 4,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.HINDI,
            payload = wirePayload
        )

        val pB = relayRouterB.evaluateRelay(originalPacket).forwardedPacket!!
        val pC = relayRouterC.evaluateRelay(pB).forwardedPacket!!
        val evalD = relayRouterD.evaluateRelay(pC)
        assertEquals(ContextRelayDecision.DELIVER_LOCAL_ONLY, evalD.decision)

        val finalPayload = pC.payload
        val decoded = SemanticBase.deserializeWithEnhancement(finalPayload)
        assertNotNull(decoded.base)

        assertEquals(EmergencyCategory.RESCUE, decoded.base!!.category)
        assertEquals(EmergencySubtype.COLLAPSE, decoded.base!!.subtype)
        assertEquals(EmergencySeverity.CRITICAL, decoded.base!!.severity)
        assertEquals(7, decoded.base!!.count)
        assertEquals(12.toShort(), decoded.base!!.sector)
        assertEquals("COLLAPSED COMMERCIAL BUILDING BASEMENT", decoded.enhancement?.text)
    }
}
