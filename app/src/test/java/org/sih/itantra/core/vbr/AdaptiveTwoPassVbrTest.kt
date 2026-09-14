package org.sih.itantra.core.vbr

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
import org.sih.itantra.core.protocol.AdaptiveCompressor
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.SemanticCommand

/**
 * Feature 16B: Comprehensive Unit Tests for Adaptive Two-Pass Semantic VBR Communication.
 *
 * Validates:
 * 1. Deterministic tactical shorthand generation (CompactTextGenerator).
 * 2. Adaptive representation mode selection policy (AdaptiveRepresentationPolicy).
 * 3. Semantic confidence gating and meaning preservation rules.
 * 4. Binary wire encoding and backward compatibility (Packet.FLAG_COMPACT).
 * 5. MessageRecord and MessageTechnicalInspector projections.
 */
class AdaptiveTwoPassVbrTest {

    // =========================================================================
    // 1. CompactTextGenerator Tests
    // =========================================================================

    @Test
    fun testEnglishFillerRemovalAndUppercase() {
        val input = "Please could you kindly report your status to HQ right now"
        val compacted = CompactTextGenerator.compact(input, IndicLanguage.ENGLISH)
        assertTrue(compacted.contains("REPORT"))
        assertTrue(compacted.contains("STATUS"))
        assertTrue(compacted.contains("HQ"))
        assertFalse(compacted.contains("PLEASE", ignoreCase = true))
        assertFalse(compacted.contains("KINDLY", ignoreCase = true))
        assertFalse(compacted.contains("RIGHT NOW", ignoreCase = true))
    }

    @Test
    fun testPreservesCriticalNumbersAndImperatives() {
        val input = "We have 3 people trapped in building, send 2 ambulances immediately"
        val compacted = CompactTextGenerator.compact(input, IndicLanguage.ENGLISH)
        assertTrue(compacted.contains("3"))
        assertTrue(compacted.contains("PEOPLE"))
        assertTrue(compacted.contains("TRAPPED"))
        assertTrue(compacted.contains("BUILDING"))
        assertTrue(compacted.contains("SEND"))
        assertTrue(compacted.contains("2"))
        assertTrue(compacted.contains("AMBULANCES"))
    }

    @Test
    fun testPreservesCallsignsAndUnits() {
        val input = "Alpha 1 to Bravo 2, advance to Sector 4 and hold position"
        val compacted = CompactTextGenerator.compact(input, IndicLanguage.ENGLISH)
        assertTrue(compacted.contains("ALPHA 1"))
        assertTrue(compacted.contains("BRAVO 2"))
        assertTrue(compacted.contains("ADVANCE"))
        assertTrue(compacted.contains("SECTOR 4"))
        assertTrue(compacted.contains("HOLD POSITION"))
    }

    @Test
    fun testTamilCompactionAndUnicodePreservation() {
        val input = "தயவுசெய்து இங்கே 2 ஆம்புலன்ஸ் அனுப்பவும்"
        val compacted = CompactTextGenerator.compact(input, IndicLanguage.TAMIL)
        assertTrue(compacted.contains("2"))
        assertTrue(compacted.contains("ஆம்புலன்ஸ்"))
        assertTrue(compacted.contains("அனுப்பவும்"))
        assertFalse(compacted.contains("தயவுசெய்து"))
        assertFalse(compacted.contains("இங்கே"))
    }

    @Test
    fun testHindiCompactionAndUnicodePreservation() {
        val input = "कृपया यहाँ पर तुरंत सहायता भेजें"
        val compacted = CompactTextGenerator.compact(input, IndicLanguage.HINDI)
        assertTrue(compacted.contains("तुरंत"))
        assertTrue(compacted.contains("सहायता"))
        assertTrue(compacted.contains("भेजें"))
        assertFalse(compacted.contains("कृपया"))
        assertFalse(compacted.contains("यहाँ पर"))
    }

    @Test
    fun testEmptyOrWhitespaceInputHandling() {
        assertEquals("", CompactTextGenerator.compact("", IndicLanguage.ENGLISH))
        assertEquals("", CompactTextGenerator.compact("   ", IndicLanguage.HINDI))
    }

    @Test
    fun testAllFillersFallbackPreservesOriginal() {
        val input = "Please kindly"
        val compacted = CompactTextGenerator.compact(input, IndicLanguage.ENGLISH)
        // If all words are stripped, falls back to original trimmed uppercase rather than empty
        assertTrue(compacted.isNotBlank())
    }

    // =========================================================================
    // 2. AdaptiveRepresentationPolicy Tests
    // =========================================================================

    @Test
    fun testHealthyNetworkSelectsFullForGeneralText() {
        val text = "Sector 4 reconnaissance patrol reports all quiet."
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.FULL, rep.mode)
        assertEquals(text, rep.text)
        assertNull(rep.semanticCommand)
    }

    @Test
    fun testDegradedNetworkSelectsCompactForGeneralText() {
        val text = "Please note that we have unit Alpha holding position at base"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.DEGRADED,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.COMPACT, rep.mode)
        assertTrue(rep.text.contains("ALPHA"))
        assertTrue(rep.text.contains("HOLDING POSITION"))
        assertNull(rep.semanticCommand)
    }

    @Test
    fun testLimitedNetworkSelectsCompact() {
        val text = "Could you please confirm if convoy has reached checkpoint 3"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.LIMITED,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.COMPACT, rep.mode)
        assertTrue(rep.text.contains("CONFIRM"))
        assertTrue(rep.text.contains("CHECKPOINT 3"))
    }

    @Test
    fun testCongestedNetworkSelectsCompact() {
        val text = "We have received the transmission and will stand by"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.CONGESTED,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.COMPACT, rep.mode)
    }

    @Test
    fun testDtnStoredSelectsCompact() {
        val text = "Please note basically that patrol team is returning to FOB"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.DTN_STORED,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.COMPACT, rep.mode)
    }

    @Test
    fun testOfflineLongTextSelectsCompact() {
        val longText = "We are currently observing movement along the ridge line and request backup team at coordinate Bravo 4"
        val rep = AdaptiveRepresentationPolicy.select(
            text = longText,
            networkMode = AdaptiveNetworkMode.OFFLINE,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.COMPACT, rep.mode)
    }

    @Test
    fun testOfflineShortTextSelectsFull() {
        val shortText = "Team OK"
        val rep = AdaptiveRepresentationPolicy.select(
            text = shortText,
            networkMode = AdaptiveNetworkMode.OFFLINE,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.FULL, rep.mode)
        assertEquals(shortText, rep.text)
    }

    @Test
    fun testHighConfidenceSemanticSelectedInDegraded() {
        val text = "Ambulance required for 3 injured"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.DEGRADED,
            semanticConfidence = 0.95f
        )
        assertEquals(AdaptiveRepresentationMode.SEMANTIC, rep.mode)
        assertNotNull(rep.semanticCommand)
        assertEquals(EmergencyCategory.MEDICAL, rep.semanticCommand!!.category)
        assertEquals(EmergencySubtype.AMBULANCE, rep.semanticCommand!!.subtype)
        assertEquals(3, rep.semanticCommand!!.count)
        assertEquals(6, rep.wirePayloadSizeBytes)
    }

    @Test
    fun testHighConfidenceSemanticSelectedInHealthyForCritical() {
        val text = "3 people trapped building collapse"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            semanticConfidence = 0.92f
        )
        assertEquals(AdaptiveRepresentationMode.SEMANTIC, rep.mode)
        assertNotNull(rep.semanticCommand)
        assertEquals(EmergencyCategory.TRAPPED, rep.semanticCommand!!.category)
        assertEquals(6, rep.wirePayloadSizeBytes)
    }

    @Test
    fun testLowConfidenceSemanticDoesNotForceSemantic() {
        // Semantic candidate exists, but confidence is below the 0.85 threshold
        val text = "Ambulance needed"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.DEGRADED,
            semanticConfidence = 0.60f // Below 0.85 threshold!
        )
        // Rule: Meaning preservation > bitrate reduction. Never guess uncertain speech.
        assertEquals(AdaptiveRepresentationMode.COMPACT, rep.mode)
        assertNull(rep.semanticCommand)
    }

    @Test
    fun testForcedSemanticOverride() {
        val text = "General situation report"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            forceMode = AdaptiveRepresentationMode.SEMANTIC
        )
        assertEquals(AdaptiveRepresentationMode.SEMANTIC, rep.mode)
        assertNotNull(rep.semanticCommand)
        assertEquals(6, rep.wirePayloadSizeBytes)
    }

    @Test
    fun testForcedCompactOverride() {
        val text = "Please report status right now"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            forceMode = AdaptiveRepresentationMode.COMPACT
        )
        assertEquals(AdaptiveRepresentationMode.COMPACT, rep.mode)
        assertFalse(rep.text.contains("PLEASE", ignoreCase = true))
    }

    @Test
    fun testForcedFullOverride() {
        val text = "Please report status right now"
        val rep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.DEGRADED,
            forceMode = AdaptiveRepresentationMode.FULL
        )
        assertEquals(AdaptiveRepresentationMode.FULL, rep.mode)
        assertEquals(text, rep.text)
    }

    @Test
    fun testWireSizeOrdering() {
        val text = "Please send 3 ambulances to Sector 4 immediately because we have casualties"
        val semRep = AdaptiveRepresentationPolicy.select(
            text = "Ambulance required for 3 injured",
            networkMode = AdaptiveNetworkMode.DEGRADED,
            semanticConfidence = 0.95f
        )
        val compactRep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.DEGRADED,
            semanticConfidence = 0.50f
        )
        val fullRep = AdaptiveRepresentationPolicy.select(
            text = text,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            semanticConfidence = 0.50f
        )

        // SEMANTIC wire size is exactly 6 bytes
        assertEquals(6, semRep.wirePayloadSizeBytes)
        // COMPACT text is shorter than FULL text
        assertTrue(compactRep.text.length < fullRep.text.length)
        assertTrue(semRep.wirePayloadSizeBytes < compactRep.wirePayloadSizeBytes)
    }

    // =========================================================================
    // 3. Wire Encoding and Flag Compatibility Tests
    // =========================================================================

    @Test
    fun testFlagCompactBitAllocation() {
        // Bit 7: 1 shl 7 = 0x80 (128)
        assertEquals(0x80, Packet.FLAG_COMPACT)
        // Ensure FLAG_COMPACT does not collide with existing flags
        val allFlags = listOf(
            Packet.FLAG_COMPRESSED,
            Packet.FLAG_FRAGMENTED,
            Packet.FLAG_REQUIRES_ACK,
            Packet.FLAG_FORWARDED,
            Packet.FLAG_HAS_LOCATION,
            Packet.FLAG_SEMANTIC,
            Packet.FLAG_AUTHENTICATED
        )
        for (flag in allFlags) {
            assertEquals(0, Packet.FLAG_COMPACT and flag)
        }
    }

    @Test
    fun testPacketSerializerPreservesFlagCompact() {
        val payload = "TEST COMPACT SHORTHAND".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            flags = Packet.FLAG_COMPACT.toByte(),
            sequenceNumber = 42,
            timestamp = 1700000000L,
            sourceDeviceId = 101,
            destinationDeviceId = 202,
            language = IndicLanguage.ENGLISH,
            payload = payload
        )
        assertTrue(packet.isCompact)
        assertFalse(packet.isSemantic)

        val bytes = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(bytes)
        assertTrue(deserialized.isCompact)
        assertFalse(deserialized.isSemantic)
        assertEquals(42.toShort(), deserialized.sequenceNumber)
        assertEquals(101, deserialized.sourceDeviceId)
        assertEquals("TEST COMPACT SHORTHAND", String(deserialized.payload, Charsets.UTF_8))
    }

    @Test
    fun testPacketSerializerPreservesFlagSemantic() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.AMBULANCE,
            count = 2,
            severity = EmergencySeverity.CRITICAL
        )
        val packet = Packet(
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 77,
            timestamp = 1700000000L,
            sourceDeviceId = 101,
            destinationDeviceId = 202,
            language = IndicLanguage.ENGLISH,
            payload = cmd.serialize(),
            semanticCommand = cmd
        )
        assertTrue(packet.isSemantic)
        assertFalse(packet.isCompact)

        val bytes = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(bytes)
        assertTrue(deserialized.isSemantic)
        assertFalse(deserialized.isCompact)
        assertNotNull(deserialized.semanticCommand)
        assertEquals(EmergencyCategory.MEDICAL, deserialized.semanticCommand!!.category)
        assertEquals(2, deserialized.semanticCommand!!.count)
    }

    @Test
    fun testPacketHeaderSizeIsExactly28Bytes() {
        assertEquals(28, Packet.HEADER_SIZE_BYTES)
        assertEquals(32, Packet.MIN_PACKET_SIZE)
    }

    @Test
    fun testBinaryRoundtripForSemanticPayload() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.FIRE,
            subtype = EmergencySubtype.BUILDING,
            count = 5,
            severity = EmergencySeverity.CRITICAL
        )
        val cmdBytes = cmd.serialize()
        assertEquals(6, cmdBytes.size)

        val packet = Packet(
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 12,
            timestamp = 1700000000L,
            sourceDeviceId = 1,
            language = IndicLanguage.HINDI,
            payload = cmdBytes,
            semanticCommand = cmd
        )

        val serialized = PacketSerializer.serialize(packet)
        // 28B header + 6B payload + 4B CRC = 38B
        assertEquals(38, serialized.size)

        val recovered = PacketSerializer.deserialize(serialized)
        assertEquals(cmd, recovered.semanticCommand)
    }

    @Test
    fun testBinaryRoundtripForCompactPayload() {
        val compactText = "HOLD POSITION GRID 7"
        val rawBytes = compactText.toByteArray(Charsets.UTF_8)
        val compression = AdaptiveCompressor.compress(rawBytes)
        val flags = (Packet.FLAG_COMPACT or (if (compression.isCompressed) Packet.FLAG_COMPRESSED else 0)).toByte()

        val packet = Packet(
            flags = flags,
            sequenceNumber = 99,
            timestamp = 1700000000L,
            sourceDeviceId = 2,
            language = IndicLanguage.ENGLISH,
            payload = compression.bytes
        )

        val serialized = PacketSerializer.serialize(packet)
        val recovered = PacketSerializer.deserialize(serialized)
        assertTrue(recovered.isCompact)
        val decompressed = AdaptiveCompressor.decompress(recovered.payload, recovered.isCompressed)
        assertEquals(compactText, String(decompressed, Charsets.UTF_8))
    }

    @Test
    fun testBackwardCompatibilityWithoutCompactFlag() {
        val rawText = "Normal text message"
        val packet = Packet(
            flags = 0,
            sequenceNumber = 10,
            timestamp = 1700000000L,
            sourceDeviceId = 1,
            language = IndicLanguage.ENGLISH,
            payload = rawText.toByteArray(Charsets.UTF_8)
        )
        val bytes = PacketSerializer.serialize(packet)
        val recovered = PacketSerializer.deserialize(bytes)
        assertFalse(recovered.isCompact)
        assertFalse(recovered.isSemantic)
    }

    // =========================================================================
    // 4. MessageRecord and Inspector Tests
    // =========================================================================

    @Test
    fun testMessageRecordDefaultRepresentationModeIsNull() {
        val record = MessageRecord(
            id = "msg-1",
            timestamp = 1000L,
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.NORMAL,
            text = "Hello",
            peer = "Node #2",
            packetSizeBytes = 36,
            rawAudioEquivalentBytes = 32000L,
            measuredLatencyMs = 15.0
        )
        assertNull(record.representationMode)
    }

    @Test
    fun testMessageRecordStoresRepresentationMode() {
        val record = MessageRecord(
            id = "msg-2",
            timestamp = 1000L,
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.NORMAL,
            text = "SHORTHAND",
            peer = "Node #2",
            packetSizeBytes = 40,
            rawAudioEquivalentBytes = 32000L,
            measuredLatencyMs = 15.0,
            representationMode = "COMPACT"
        )
        assertEquals("COMPACT", record.representationMode)
    }

    @Test
    fun testTechnicalInspectorDisplaysVbrRepresentation() {
        val record = MessageRecord(
            id = "msg-3",
            timestamp = 1000L,
            direction = MessageDirection.RECEIVED,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.ALERT,
            text = "🚨 MEDICAL EMERGENCY",
            peer = "Node #1",
            packetSizeBytes = 38,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 20.0,
            isSemantic = true,
            representationMode = "SEMANTIC"
        )

        val inspector = MessageTechnicalInspectorMapper.map(record)
        val msgSection = inspector.sections.firstOrNull { it.title == "MESSAGE" }
        assertNotNull(msgSection)
        val vbrField = msgSection!!.fields.firstOrNull { it.label == "VBR REPRESENTATION" }
        assertNotNull(vbrField)
        assertEquals("SEMANTIC", vbrField!!.value)
    }

    @Test
    fun testFragmentationCompatibilityWithVbr() {
        val fragmenter = PacketFragmenter()
        // Generate a 300-byte compact payload that requires fragmentation (> 128 bytes)
        val largeCompactText = (1..20).joinToString(" ") { "PATROL UNIT $it HOLD" }
        val payloadBytes = largeCompactText.toByteArray(Charsets.UTF_8)
        assertTrue(PacketFragmenter.needsFragmentation(payloadBytes.size))

        val flags = Packet.FLAG_COMPACT.toByte()
        val fragments = fragmenter.fragment(
            payload = payloadBytes,
            transferId = 0x0A0B.toShort(),
            originalMsgType = Packet.TYPE_TEXT,
            originalFlags = flags
        )
        assertTrue(fragments.size > 1)
        for (frag in fragments) {
            assertEquals(0x0A0B.toShort(), frag.metadata.transferId)
            assertEquals(flags, frag.metadata.originalFlags)
        }
    }
}
