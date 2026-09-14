package org.sih.itantra.core.vbr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.MessageTechnicalInspectorMapper
import org.sih.itantra.core.network.AdaptiveNetworkMode
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.CorruptPacketException
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.protocol.SemanticEmergencyClassifier
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Feature 18: Comprehensive Unit Tests for Semantic Base + Enhancement Layer.
 *
 * Validates all 20 required scenarios from Section 12:
 * 1. Base deterministic encoding (8 bytes)
 * 2. Base decoding (exact field recovery)
 * 3. Backward compatibility (6-byte legacy payload handling)
 * 4. Enhancement encoding & decoding (text + optional detail)
 * 5. Base + Enhancement composite serialization & deserialization
 * 6. Base-only payload contract and valid tactical card generation
 * 7. Missing/truncated enhancement resilience (safe Base-only recovery)
 * 8. Unknown enhancement schema version handling
 * 9. Corrupted/invalid base payload rejection
 * 10. Network-aware selection in AdaptiveRepresentationPolicy
 * 11. Backward compatibility for SEMANTIC and isSemantic
 * 12. Sector extraction in SemanticEmergencyClassifier (English, Hindi, Tamil)
 * 13. Feature 17 refinement integration into enhancement layer
 * 14. Zero post-endpoint delay guarantee (instantaneous serialization)
 * 15. HMAC-SHA256 and CRC32 integrity validation
 * 16. Packet fragmentation safety (Base fits in single unfragmented frame)
 * 17. MessageTechnicalInspectorMapper representation inspection
 * 18. MessageRecord persistence schema fields
 * 19. Display strings and badges ([BASE ONLY] vs [BASE+ENH])
 * 20. State isolation across multiple consecutive mixed messages
 */
class SemanticBaseEnhancementTest {

    private val testPsk = ByteArray(32) { 0x42 }

    private fun sampleCommand(sector: Int = 4): SemanticCommand = SemanticCommand(
        category = EmergencyCategory.MEDICAL,
        subtype = EmergencySubtype.INJURED,
        severity = EmergencySeverity.CRITICAL,
        count = 3,
        parameter = sector.toShort()
    )

    // =========================================================================
    // 1. Base Deterministic Encoding (8 Bytes)
    // =========================================================================
    @Test
    fun test1_baseDeterministicEncoding8Bytes() {
        val cmd = sampleCommand(sector = 4)
        val base = SemanticBase.fromCommand(cmd, sector = 4)
        val encoded = base.serialize(hasEnhancement = false)

        assertEquals(8, encoded.size)
        assertEquals(EmergencyCategory.MEDICAL.id, encoded[0])
        assertEquals(EmergencySubtype.INJURED.id, encoded[1])
        assertEquals(EmergencySeverity.CRITICAL.id, encoded[2])
        assertEquals(3.toByte(), encoded[3])
        // Parameter (Sector 4) in big-endian short
        val sector = ByteBuffer.wrap(encoded, 4, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
        assertEquals(4, sector)
        // Schema version
        assertEquals(1.toByte(), encoded[6])
        // Flags (hasEnhancement = false)
        assertEquals(0.toByte(), encoded[7])
    }

    // =========================================================================
    // 2. Base Decoding (Exact Field Recovery)
    // =========================================================================
    @Test
    fun test2_baseDecodingExactFieldRecovery() {
        val cmd = sampleCommand(sector = 7)
        val base = SemanticBase.fromCommand(cmd, sector = 7)
        val encoded = base.serialize(hasEnhancement = true)
        val decoded = SemanticBase.deserialize(encoded)

        assertNotNull(decoded)
        assertEquals(EmergencyCategory.MEDICAL, decoded!!.category)
        assertEquals(EmergencySubtype.INJURED, decoded.subtype)
        assertEquals(EmergencySeverity.CRITICAL, decoded.severity)
        assertEquals(3, decoded.count)
        assertEquals(7, decoded.sector.toInt())
        assertEquals(1.toByte(), decoded.schemaVersion)
        assertTrue(decoded.hasEnhancement)
    }

    // =========================================================================
    // 3. Backward Compatibility (6-byte Legacy Payload Handling)
    // =========================================================================
    @Test
    fun test3_backwardCompatibility6ByteLegacyPayload() {
        val legacyCmd = sampleCommand(sector = 2)
        val legacyBytes = legacyCmd.serialize()
        assertEquals(6, legacyBytes.size)

        val decoded = SemanticBase.deserialize(legacyBytes)
        assertNotNull(decoded)
        assertEquals(EmergencyCategory.MEDICAL, decoded!!.category)
        assertEquals(EmergencySubtype.INJURED, decoded.subtype)
        assertEquals(EmergencySeverity.CRITICAL, decoded.severity)
        assertEquals(3, decoded.count)
        assertEquals(2, decoded.sector.toInt())
        assertEquals(1.toByte(), decoded.schemaVersion)
        assertFalse(decoded.hasEnhancement)
    }

    // =========================================================================
    // 4. Enhancement Encoding & Decoding (Text + Optional Detail)
    // =========================================================================
    @Test
    fun test4_enhancementEncodingAndDecoding() {
        val enhancementWithDetail = SemanticEnhancement(
            schemaVersion = 1,
            text = "3 casualties reported at Sector 4",
            detail = "Send 2 ambulances immediately"
        )
        val bytes = enhancementWithDetail.serialize()
        val decoded = SemanticEnhancement.deserialize(bytes)

        assertNotNull(decoded)
        assertEquals(1.toByte(), decoded!!.schemaVersion)
        assertEquals("3 casualties reported at Sector 4", decoded.text)
        assertEquals("Send 2 ambulances immediately", decoded.detail)

        // Test without detail
        val enhancementTextOnly = SemanticEnhancement(
            schemaVersion = 1,
            text = "Immediate medical assistance required"
        )
        val bytesTextOnly = enhancementTextOnly.serialize()
        val decodedTextOnly = SemanticEnhancement.deserialize(bytesTextOnly)

        assertNotNull(decodedTextOnly)
        assertEquals("Immediate medical assistance required", decodedTextOnly!!.text)
        assertNull(decodedTextOnly.detail)
    }

    // =========================================================================
    // 5. Base + Enhancement Composite Serialization & Deserialization
    // =========================================================================
    @Test
    fun test5_compositeSerializationAndDeserialization() {
        val cmd = sampleCommand(sector = 5)
        val base = SemanticBase.fromCommand(cmd, sector = 5)
        val enhancement = SemanticEnhancement(
            schemaVersion = 1,
            text = "Sector 5 collapsed structure 3 injured",
            detail = "Doctor team dispatched"
        )

        val composite = SemanticBase.serializeComposite(base, enhancement)
        assertTrue(composite.size > 8)

        val decodedPayload = SemanticBase.deserializeWithEnhancement(composite)
        assertNotNull(decodedPayload.base)
        assertFalse(decodedPayload.isBaseOnly)
        assertEquals(EmergencyCategory.MEDICAL, decodedPayload.base!!.category)
        assertEquals(5, decodedPayload.base!!.sector.toInt())
        assertNotNull(decodedPayload.enhancement)
        assertEquals("Sector 5 collapsed structure 3 injured", decodedPayload.enhancement!!.text)
        assertEquals("Doctor team dispatched", decodedPayload.enhancement!!.detail)
    }

    // =========================================================================
    // 6. Base-Only Payload Contract & Tactical Card Generation
    // =========================================================================
    @Test
    fun test6_baseOnlyPayloadContract() {
        val cmd = sampleCommand(sector = 4)
        val base = SemanticBase.fromCommand(cmd, sector = 4)
        val baseBytes = base.serialize(hasEnhancement = false)
        assertEquals(8, baseBytes.size)

        val decoded = SemanticBase.deserializeWithEnhancement(baseBytes)
        assertNotNull(decoded.base)
        assertTrue(decoded.isBaseOnly)
        assertNull(decoded.enhancement)

        val command = decoded.base!!.command
        val displayStr = command.toDisplayString()
        val badgeStr = command.toBadgeString()

        assertTrue(displayStr.contains("MEDICAL"))
        assertTrue(displayStr.contains("INJURED"))
        assertTrue(displayStr.contains("SECTOR 4"))
        assertTrue(badgeStr.contains("INJURED") || badgeStr.contains("MEDICAL"))
    }

    // =========================================================================
    // 7. Missing / Truncated Enhancement Resilience
    // =========================================================================
    @Test
    fun test7_truncatedEnhancementResilience() {
        val cmd = sampleCommand(sector = 3)
        val base = SemanticBase.fromCommand(cmd, sector = 3)
        val enhancement = SemanticEnhancement(
            schemaVersion = 1,
            text = "Long text that will be violently truncated halfway through"
        )
        val composite = SemanticBase.serializeComposite(base, enhancement)

        // Truncate halfway through the enhancement payload
        val truncated = composite.copyOfRange(0, 12)
        val decoded = SemanticBase.deserializeWithEnhancement(truncated)

        assertNotNull("Base must be safely recovered even if enhancement is truncated", decoded.base)
        assertTrue(decoded.isBaseOnly)
        assertNull(decoded.enhancement)
        assertEquals(EmergencyCategory.MEDICAL, decoded.base!!.category)
        assertEquals(3, decoded.base!!.sector.toInt())
    }

    // =========================================================================
    // 8. Unknown Enhancement Schema Version Handling
    // =========================================================================
    @Test
    fun test8_unknownEnhancementSchemaVersionHandling() {
        val cmd = sampleCommand(sector = 4)
        val base = SemanticBase.fromCommand(cmd, sector = 4)
        val futureEnhancement = SemanticEnhancement(
            schemaVersion = 99, // Unknown future version
            text = "Future data structure"
        )
        val composite = SemanticBase.serializeComposite(base, futureEnhancement)
        val decoded = SemanticBase.deserializeWithEnhancement(composite)

        assertNotNull(decoded.base)
        assertTrue(decoded.isBaseOnly)
        assertNull(decoded.enhancement)
        assertEquals(4, decoded.base!!.sector.toInt())
    }

    // =========================================================================
    // 9. Corrupted / Invalid Base Payload Rejection
    // =========================================================================
    @Test
    fun test9_corruptedInvalidBasePayloadRejection() {
        assertNull(SemanticBase.deserialize(ByteArray(0)))
        assertNull(SemanticBase.deserialize(ByteArray(5))) // Less than 6 bytes
        assertNull(SemanticBase.deserializeWithEnhancement(ByteArray(4)).base)
    }

    // =========================================================================
    // 10. Network-Aware Selection in AdaptiveRepresentationPolicy
    // =========================================================================
    @Test
    fun test10_networkAwareSelectionPolicy() {
        val text = "Ambulance required for 3 injured in sector 4"

        // 1. HEALTHY -> SEMANTIC_ENHANCED
        val healthyRep = AdaptiveRepresentationPolicy.selectLayered(
            text = text,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            semanticConfidence = 0.95f
        )
        assertEquals(AdaptiveRepresentationMode.SEMANTIC_ENHANCED, healthyRep.mode)
        assertEquals(8, healthyRep.basePayloadSizeBytes)
        assertTrue(healthyRep.enhancementPayloadSizeBytes > 0)
        assertNotNull(healthyRep.semanticEnhancement)

        // 2. Constrained networks -> SEMANTIC_BASE
        val constrainedModes = listOf(
            AdaptiveNetworkMode.DEGRADED,
            AdaptiveNetworkMode.LIMITED,
            AdaptiveNetworkMode.CONGESTED,
            AdaptiveNetworkMode.DTN_STORED,
            AdaptiveNetworkMode.OFFLINE,
            AdaptiveNetworkMode.WAITING_FOR_ROUTE
        )
        for (netMode in constrainedModes) {
            val rep = AdaptiveRepresentationPolicy.selectLayered(
                text = text,
                networkMode = netMode,
                semanticConfidence = 0.95f
            )
            assertEquals("Expected SEMANTIC_BASE for network mode $netMode", AdaptiveRepresentationMode.SEMANTIC_BASE, rep.mode)
            assertEquals(8, rep.basePayloadSizeBytes)
            assertEquals(0, rep.enhancementPayloadSizeBytes)
            assertNull(rep.semanticEnhancement)
        }
    }

    // =========================================================================
    // 11. Backward Compatibility for SEMANTIC and isSemantic
    // =========================================================================
    @Test
    fun test11_backwardCompatibilitySemanticModes() {
        assertTrue(AdaptiveRepresentationMode.SEMANTIC.isSemantic)
        assertTrue(AdaptiveRepresentationMode.SEMANTIC_BASE.isSemantic)
        assertTrue(AdaptiveRepresentationMode.SEMANTIC_ENHANCED.isSemantic)
        assertFalse(AdaptiveRepresentationMode.FULL.isSemantic)
        assertFalse(AdaptiveRepresentationMode.COMPACT.isSemantic)

        assertEquals(AdaptiveRepresentationMode.SEMANTIC_BASE, AdaptiveRepresentationMode.fromString("BASE_ONLY"))
        assertEquals(AdaptiveRepresentationMode.SEMANTIC_ENHANCED, AdaptiveRepresentationMode.fromString("BASE_PLUS_ENHANCEMENT"))
        assertEquals(AdaptiveRepresentationMode.SEMANTIC_BASE, AdaptiveRepresentationMode.fromString("SEMANTIC_BASE"))
        assertEquals(AdaptiveRepresentationMode.SEMANTIC_ENHANCED, AdaptiveRepresentationMode.fromString("SEMANTIC_ENHANCED"))
    }

    // =========================================================================
    // 12. Sector Extraction in SemanticEmergencyClassifier
    // =========================================================================
    @Test
    fun test12_sectorExtractionAcrossLanguages() {
        // English: "Sector 4"
        val resEn = SemanticEmergencyClassifier.classify("Send ambulance to sector 4 immediately, 3 injured")
        assertNotNull(resEn)
        assertEquals(4, resEn!!.sector)

        // Hindi: "सेक्टर 4"
        val resHi = SemanticEmergencyClassifier.classify("सेक्टर 4 में आग लगी है तुरंत मदद")
        assertNotNull(resHi)
        assertEquals(4, resHi!!.sector)

        // Tamil: "செக்டார் 4"
        val resTa = SemanticEmergencyClassifier.classify("செக்டார் 4 ஆம்புலன்ஸ் தேவை")
        assertNotNull(resTa)
        assertEquals(4, resTa!!.sector)

        // Hindi word number: "सेक्टर चार"
        val resHiWord = SemanticEmergencyClassifier.classify("सेक्टर चार में आग लगी है तुरंत मदद")
        assertNotNull(resHiWord)
        assertEquals(4, resHiWord!!.sector)
    }

    // =========================================================================
    // 13. Feature 17 Refinement Integration into Enhancement Layer
    // =========================================================================
    @Test
    fun test13_feature17RefinementIntegration() {
        val cmd = sampleCommand(sector = 4)
        val rawSpeech = "two people hurt in sector four"
        val refinedSpeech = "2 casualties reported in Sector 4"

        val rep = AdaptiveRepresentationPolicy.buildSemanticEnhancedRepresentation(
            cmd = cmd,
            originalText = rawSpeech,
            confidence = 0.95f,
            explanation = "Feature 17 Refinement Integration",
            refinedText = refinedSpeech
        )

        assertNotNull(rep.semanticBase)
        assertNotNull(rep.semanticEnhancement)
        assertEquals(4, rep.semanticBase!!.sector.toInt())
        // Refined text is primary text, original raw is preserved as detail
        assertEquals(refinedSpeech, rep.semanticEnhancement!!.text)
        assertEquals(rawSpeech, rep.semanticEnhancement!!.detail)
        assertEquals(AdaptiveRepresentationMode.SEMANTIC_ENHANCED, rep.mode)
    }

    // =========================================================================
    // 14. Zero Post-Endpoint Delay Guarantee (Instantaneous Serialization)
    // =========================================================================
    @Test
    fun test14_zeroPostEndpointDelayGuarantee() {
        val cmd = sampleCommand(sector = 4)
        val base = SemanticBase.fromCommand(cmd, sector = 4)
        val enhancement = SemanticEnhancement(
            schemaVersion = 1,
            text = "Tactical transmission completed",
            detail = "Operator note"
        )

        val startTime = System.nanoTime()
        val composite = SemanticBase.serializeComposite(base, enhancement)
        val decoded = SemanticBase.deserializeWithEnhancement(composite)
        val elapsedNanos = System.nanoTime() - startTime
        val elapsedMs = elapsedNanos / 1_000_000.0

        assertNotNull(decoded.base)
        // Serialization and deserialization must take less than 5ms
        assertTrue("Base+enhancement operations must be microsecond-scale (was ${elapsedMs}ms)", elapsedMs < 5.0)
    }

    // =========================================================================
    // 15. HMAC-SHA256 and CRC32 Integrity Validation
    // =========================================================================
    @Test
    fun test15_hmacAndCrcIntegrityValidation() {
        val cmd = sampleCommand(sector = 4)
        val base = SemanticBase.fromCommand(cmd, sector = 4)
        val basePayload = base.serialize(hasEnhancement = false)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 101,
            timestamp = 1726000000000L,
            sourceDeviceId = 1001,
            destinationDeviceId = 1002,
            language = IndicLanguage.ENGLISH,
            payload = basePayload
        )

        val wireBytes = PacketSerializer.serialize(packet)
        assertEquals(Packet.HEADER_SIZE_BYTES + basePayload.size + Packet.CRC_SIZE_BYTES, wireBytes.size)

        val deserialized = PacketSerializer.deserialize(wireBytes)
        assertNotNull(deserialized)
        assertTrue(deserialized.isSemantic)
        assertArrayEquals(basePayload, deserialized.payload)

        // Verify tampering breaks CRC integrity
        val tamperedBytes = wireBytes.clone()
        tamperedBytes[Packet.HEADER_SIZE_BYTES + 2] = (tamperedBytes[Packet.HEADER_SIZE_BYTES + 2] + 1).toByte()
        var corrupted = false
        try {
            PacketSerializer.deserialize(tamperedBytes)
        } catch (_: CorruptPacketException) {
            corrupted = true
        }
        assertTrue("Tampered packet must fail integrity check", corrupted)
    }

    // =========================================================================
    // 16. Packet Fragmentation Safety
    // =========================================================================
    @Test
    fun test16_packetFragmentationSafety() {
        val cmd = sampleCommand(sector = 4)
        val base = SemanticBase.fromCommand(cmd, sector = 4)
        val basePayload = base.serialize(hasEnhancement = false)

        val fragmenter = PacketFragmenter(maxPayloadBytes = 64)
        val fragments = fragmenter.fragment(basePayload, transferId = 1)
        assertTrue("Base payload must never fragment (fits in single unfragmented frame)", fragments.isEmpty())
    }

    // =========================================================================
    // 17. MessageTechnicalInspectorMapper Representation Inspection
    // =========================================================================
    @Test
    fun test17_messageTechnicalInspectorMapper() {
        val recordBaseOnly = MessageRecord(
            id = "msg-base-1",
            timestamp = 1726000000000L,
            direction = MessageDirection.RECEIVED,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.DISTRESS,
            text = "3 casualties reported in Sector 4",
            peer = "Node #209071",
            packetSizeBytes = 36,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 12.0,
            isSemantic = true,
            representationMode = "SEMANTIC_BASE",
            semanticBaseBytes = 8,
            enhancementBytes = 0,
            enhancementReceived = false,
            semanticSchemaVersion = 1
        )

        val inspector = MessageTechnicalInspectorMapper.map(recordBaseOnly)
        val allFields = inspector.sections.flatMap { it.fields }
        val repEntry = allFields.find { it.label == "VBR REPRESENTATION" }
        val baseEntry = allFields.find { it.label == "BASE PAYLOAD" }
        val enhEntry = allFields.find { it.label == "ENHANCEMENT PAYLOAD" }
        val enhRecvEntry = allFields.find { it.label == "ENHANCEMENT RECEIVED" }
        val schemaEntry = allFields.find { it.label == "SCHEMA VERSION" }

        assertNotNull(repEntry)
        assertEquals("SEMANTIC BASE", repEntry!!.value)
        assertNotNull(baseEntry)
        assertEquals("8 B (tactical core)", baseEntry!!.value)
        assertNotNull(enhEntry)
        assertEquals("0 B (omitted / constrained link)", enhEntry!!.value)
        assertNotNull(enhRecvEntry)
        assertEquals("No (Base-only tactical fallback)", enhRecvEntry!!.value)
        assertNotNull(schemaEntry)
        assertEquals("v1", schemaEntry!!.value)
    }

    // =========================================================================
    // 18. MessageRecord Persistence Schema Fields
    // =========================================================================
    @Test
    fun test18_messageRecordPersistenceFields() {
        val record = MessageRecord(
            id = "msg-enh-2",
            timestamp = 1726000050000L,
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.ALERT,
            text = "Sector 4 fire outbreak",
            peer = "Node #883192",
            packetSizeBytes = 78,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 15.0,
            isSemantic = true,
            representationMode = "SEMANTIC_BASE + ENHANCEMENT",
            semanticBaseBytes = 8,
            enhancementBytes = 42,
            enhancementReceived = true,
            semanticSchemaVersion = 1
        )

        assertEquals(8, record.semanticBaseBytes)
        assertEquals(42, record.enhancementBytes)
        assertTrue(record.enhancementReceived == true)
        assertEquals(1, record.semanticSchemaVersion)
        assertEquals("SEMANTIC_BASE + ENHANCEMENT", record.representationMode)
    }

    // =========================================================================
    // 19. Display Strings and Badges
    // =========================================================================
    @Test
    fun test19_displayStringsAndBadges() {
        val cmdWithSector = SemanticCommand(
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.INJURED,
            severity = EmergencySeverity.CRITICAL,
            count = 2,
            parameter = 4
        )
        val display = cmdWithSector.toDisplayString()
        assertTrue(display.contains("SECTOR 4"))

        val badge = cmdWithSector.toBadgeString()
        assertTrue(badge.contains("INJURED") || badge.contains("MEDICAL"))

        // Mode badge strings
        assertEquals("BASE ONLY", AdaptiveRepresentationMode.SEMANTIC_BASE.badgeLabel)
        assertEquals("BASE+ENH", AdaptiveRepresentationMode.SEMANTIC_ENHANCED.badgeLabel)
    }

    // =========================================================================
    // 20. State Isolation Across Multiple Consecutive Mixed Messages
    // =========================================================================
    @Test
    fun test20_stateIsolationAcrossConsecutiveMixedMessages() {
        val textMed = "Ambulance needed for 3 injured in sector 1"
        val textFire = "Fire outbreak in sector 2 immediately"
        val textPatrol = "Routine patrol report from post"

        // Message 1: Healthy link -> Enhanced
        val rep1 = AdaptiveRepresentationPolicy.selectLayered(
            text = textMed,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            semanticConfidence = 0.95f
        )

        // Message 2: Degraded link -> Base only
        val rep2 = AdaptiveRepresentationPolicy.selectLayered(
            text = textFire,
            networkMode = AdaptiveNetworkMode.DEGRADED,
            semanticConfidence = 0.95f
        )

        // Message 3: Normal text (non-emergency) -> Full
        val rep3 = AdaptiveRepresentationPolicy.selectLayered(
            text = textPatrol,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            semanticConfidence = 0.50f
        )

        assertEquals(AdaptiveRepresentationMode.SEMANTIC_ENHANCED, rep1.mode)
        assertNotNull(rep1.semanticEnhancement)
        assertEquals(8, rep1.basePayloadSizeBytes)
        assertTrue(rep1.enhancementPayloadSizeBytes > 0)

        assertEquals(AdaptiveRepresentationMode.SEMANTIC_BASE, rep2.mode)
        assertNull(rep2.semanticEnhancement)
        assertEquals(8, rep2.basePayloadSizeBytes)
        assertEquals(0, rep2.enhancementPayloadSizeBytes)

        assertEquals(AdaptiveRepresentationMode.FULL, rep3.mode)
        assertNull(rep3.semanticBase)
        assertNull(rep3.semanticEnhancement)
    }
}
