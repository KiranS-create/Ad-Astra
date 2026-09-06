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

/**
 * Targeted unit tests for Emergency Semantic Compression / Structured Emergency Commands:
 * A. Classification & Rule Engine
 * B. Fallback to normal text
 * C. Binary Serialization & Corruption Safety
 * D. Mesh Routing & TTL Compatibility
 * E. Distress & Location Compatibility
 * F. DTN Store-and-Forward Compatibility
 * G. Deterministic Compression Benchmark
 */
class SemanticEmergencyTest {

    private val sampleLocation = GeoLocation(
        latitude = 11.016844,
        longitude = 76.955832,
        accuracy = 10.0f,
        timestamp = 1725500000000L,
        altitude = 420.0
    )

    // =========================================================================
    // A. CLASSIFICATION & RULE ENGINE TESTS
    // =========================================================================

    @Test
    fun testClassifyMedicalAmbulance() {
        val cmd = SemanticEmergencyClassifier.classify("Need ambulance")
        assertNotNull("Should classify 'Need ambulance'", cmd)
        assertEquals(EmergencyCategory.MEDICAL, cmd!!.category)
        assertEquals(EmergencySubtype.AMBULANCE, cmd.subtype)
        assertEquals(0, cmd.count)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
    }

    @Test
    fun testClassifyMedicalUnconscious() {
        val cmd = SemanticEmergencyClassifier.classify("Help, person unconscious")
        assertNotNull(cmd)
        assertEquals(EmergencyCategory.MEDICAL, cmd!!.category)
        assertEquals(EmergencySubtype.UNCONSCIOUS, cmd.subtype)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
    }

    @Test
    fun testClassifyMedicalInjuredWithCount() {
        val cmd = SemanticEmergencyClassifier.classify("Medical emergency, 3 people injured")
        assertNotNull(cmd)
        assertEquals(EmergencyCategory.MEDICAL, cmd!!.category)
        assertEquals(EmergencySubtype.INJURED, cmd.subtype)
        assertEquals(3, cmd.count)
    }

    @Test
    fun testClassifyFireBuilding() {
        val cmd = SemanticEmergencyClassifier.classify("Fire at building")
        assertNotNull(cmd)
        assertEquals(EmergencyCategory.FIRE, cmd!!.category)
        assertEquals(EmergencySubtype.BUILDING, cmd.subtype)
    }

    @Test
    fun testClassifyTrappedWithWordCount() {
        val cmd = SemanticEmergencyClassifier.classify("Three people trapped")
        assertNotNull(cmd)
        assertEquals(EmergencyCategory.TRAPPED, cmd!!.category)
        assertEquals(3, cmd.count)
    }

    @Test
    fun testClassifyRescueTeam() {
        val cmd = SemanticEmergencyClassifier.classify("Send rescue team")
        assertNotNull(cmd)
        assertEquals(EmergencyCategory.RESCUE, cmd!!.category)
        assertEquals(EmergencySubtype.TEAM, cmd.subtype)
    }

    @Test
    fun testClassifySupplyFoodAndWater() {
        val waterCmd = SemanticEmergencyClassifier.classify("Need water")
        assertNotNull(waterCmd)
        assertEquals(EmergencyCategory.SUPPLY, waterCmd!!.category)
        assertEquals(EmergencySubtype.WATER, waterCmd.subtype)

        val foodCmd = SemanticEmergencyClassifier.classify("Need food")
        assertNotNull(foodCmd)
        assertEquals(EmergencyCategory.SUPPLY, foodCmd!!.category)
        assertEquals(EmergencySubtype.FOOD, foodCmd.subtype)
    }

    @Test
    fun testClassifyEvacuation() {
        val cmd = SemanticEmergencyClassifier.classify("Evacuate immediately")
        assertNotNull(cmd)
        assertEquals(EmergencyCategory.EVACUATION, cmd!!.category)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
    }

    @Test
    fun testClassifyTamilEmergencyPhrases() {
        val ambulance = SemanticEmergencyClassifier.classify("ஆம்புலன்ஸ் தேவை")
        assertNotNull(ambulance)
        assertEquals(EmergencyCategory.MEDICAL, ambulance!!.category)
        assertEquals(EmergencySubtype.AMBULANCE, ambulance.subtype)

        val water = SemanticEmergencyClassifier.classify("குடிநீர் தேவை")
        assertNotNull(water)
        assertEquals(EmergencyCategory.SUPPLY, water!!.category)
        assertEquals(EmergencySubtype.WATER, water.subtype)

        val fire = SemanticEmergencyClassifier.classify("தீ விபத்து ஏற்பட்டுள்ளது")
        assertNotNull(fire)
        assertEquals(EmergencyCategory.FIRE, fire!!.category)
    }

    // =========================================================================
    // B. CONSERVATIVE FALLBACK TESTS
    // =========================================================================

    @Test
    fun testFallbackForArbitraryConversationalText() {
        assertNull("Conversational greeting must return null",
            SemanticEmergencyClassifier.classify("Hello team, hope everyone is doing well today."))

        assertNull("Logistical status must return null",
            SemanticEmergencyClassifier.classify("We reached waypoint delta and are taking a 10 minute rest."))

        assertNull("Overly verbose narrative must return null",
            SemanticEmergencyClassifier.classify("Please send some water because the nearby supply has run out and we have been waiting for hours."))

        assertNull("Blank or whitespace input must return null",
            SemanticEmergencyClassifier.classify("    "))
    }

    // =========================================================================
    // C. BINARY SERIALIZATION & CORRUPTION DETECTION
    // =========================================================================

    @Test
    fun testSemanticCommandBinaryRoundTrip() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.AMBULANCE,
            count = 4,
            severity = EmergencySeverity.CRITICAL,
            parameter = 0x1234.toShort()
        )

        val bytes = cmd.serialize()
        assertEquals("Semantic command must be strictly 6 bytes", SemanticCommand.SIZE_BYTES, bytes.size)

        val deserialized = SemanticCommand.deserialize(bytes)
        assertNotNull(deserialized)
        assertEquals(cmd.category, deserialized!!.category)
        assertEquals(cmd.subtype, deserialized.subtype)
        assertEquals(cmd.count, deserialized.count)
        assertEquals(cmd.severity, deserialized.severity)
        assertEquals(cmd.parameter, deserialized.parameter)
    }

    @Test
    fun testSemanticPacketWireRoundTripWithCrc() {
        val cmd = SemanticCommand(
            category = EmergencyCategory.TRAPPED,
            subtype = EmergencySubtype.COLLAPSE,
            count = 2,
            severity = EmergencySeverity.CRITICAL
        )

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 105,
            timestamp = 1725500000000L,
            sourceDeviceId = 888999,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = cmd.serialize(),
            semanticCommand = cmd
        )

        val wireBytes = PacketSerializer.serialize(packet)
        // 28B header + 6B payload + 4B CRC = 38B
        assertEquals(Packet.HEADER_SIZE_BYTES + SemanticCommand.SIZE_BYTES + Packet.CRC_SIZE_BYTES, wireBytes.size)

        val decoded = PacketSerializer.deserialize(wireBytes)
        assertTrue(decoded.isSemantic)
        assertNotNull(decoded.semanticCommand)
        assertEquals(EmergencyCategory.TRAPPED, decoded.semanticCommand!!.category)
        assertEquals(EmergencySubtype.COLLAPSE, decoded.semanticCommand!!.subtype)
        assertEquals(2, decoded.semanticCommand!!.count)
        assertEquals("SEMANTIC • TRAPPED • 2 • COLLAPSE", decoded.semanticCommand!!.toBadgeString())
    }

    @Test(expected = CorruptPacketException::class)
    fun testMalformedSemanticPayloadThrowsException() {
        val badBytes = ByteArray(Packet.HEADER_SIZE_BYTES + 3 + Packet.CRC_SIZE_BYTES) // Only 3 bytes payload instead of 6
        badBytes[0] = 0x49 // 'I'
        badBytes[1] = 0x54 // 'T'
        badBytes[2] = 1    // version
        badBytes[3] = Packet.TYPE_DISTRESS
        badBytes[4] = MessagePriority.DISTRESS.id
        badBytes[5] = 3    // TTL
        badBytes[6] = Packet.FLAG_SEMANTIC.toByte() // Declares semantic flag!
        badBytes[7] = 0    // seq MSB
        badBytes[8] = 1    // seq LSB
        // payload length = 3
        badBytes[26] = 0
        badBytes[27] = 3

        // Calculate CRC over header + payload
        val crc = java.util.zip.CRC32()
        crc.update(badBytes, 0, Packet.HEADER_SIZE_BYTES + 3)
        val crcVal = crc.value.toInt()
        badBytes[badBytes.size - 4] = (crcVal ushr 24).toByte()
        badBytes[badBytes.size - 3] = (crcVal ushr 16).toByte()
        badBytes[badBytes.size - 2] = (crcVal ushr 8).toByte()
        badBytes[badBytes.size - 1] = crcVal.toByte()

        PacketSerializer.deserialize(badBytes)
    }

    // =========================================================================
    // D. MESH ROUTING & TTL COMPATIBILITY
    // =========================================================================

    @Test
    fun testSemanticPacketSurvivesMeshRelayAndDecrementsTtl() {
        val router = PacketRelayRouter(localDeviceId = 9999)
        router.setRelayEnabled(true)
        val cmd = SemanticCommand(category = EmergencyCategory.FIRE, subtype = EmergencySubtype.BUILDING)
        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            ttl = 3.toByte(),
            sequenceNumber = 200,
            timestamp = 1725500000000L,
            sourceDeviceId = 1111,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = cmd.serialize(),
            semanticCommand = cmd
        )

        val action = router.evaluatePacket(packet)
        assertTrue("Router should forward valid remote semantic packet", action is RelayAction.ForwardAndDeliver)
        val forwarded = (action as RelayAction.ForwardAndDeliver).forwardedPacket
        assertEquals("TTL must decrement by 1", 2.toByte(), forwarded.ttl)
        assertTrue("FLAG_FORWARDED must be set", forwarded.isForwarded)
        assertTrue("FLAG_SEMANTIC must remain intact", forwarded.isSemantic)
        assertEquals(cmd, forwarded.semanticCommand)

        // Duplicate arrival should be suppressed
        val dupAction = router.evaluatePacket(packet)
        assertTrue("Duplicate semantic packet must be dropped", dupAction is RelayAction.DropDuplicate)
    }

    // =========================================================================
    // E. DISTRESS + LOCATION COMPATIBILITY
    // =========================================================================

    @Test
    fun testDistressSemanticWithAndWithoutLocation() {
        val cmd = SemanticCommand(EmergencyCategory.MEDICAL, EmergencySubtype.AMBULANCE, count = 3)

        // 1. With Location
        val withLocPacket = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = (Packet.FLAG_SEMANTIC or Packet.FLAG_HAS_LOCATION).toByte(),
            sequenceNumber = 301,
            timestamp = 1725500000000L,
            sourceDeviceId = 500,
            language = IndicLanguage.ENGLISH,
            payload = cmd.serialize(),
            location = sampleLocation,
            semanticCommand = cmd
        )
        val withLocBytes = PacketSerializer.serialize(withLocPacket)
        // 28B header + 32B location + 6B semantic payload + 4B CRC = 70B
        assertEquals(28 + 32 + 6 + 4, withLocBytes.size)

        val deserializedWithLoc = PacketSerializer.deserialize(withLocBytes)
        assertTrue(deserializedWithLoc.hasLocation)
        assertTrue(deserializedWithLoc.isSemantic)
        assertEquals(11.016844, deserializedWithLoc.location!!.latitude, 0.0001)
        assertEquals(EmergencyCategory.MEDICAL, deserializedWithLoc.semanticCommand!!.category)
        assertEquals(3, deserializedWithLoc.semanticCommand!!.count)

        // 2. Without Location
        val withoutLocPacket = withLocPacket.copy(
            flags = Packet.FLAG_SEMANTIC.toByte(),
            location = null
        )
        val withoutLocBytes = PacketSerializer.serialize(withoutLocPacket)
        assertEquals(28 + 0 + 6 + 4, withoutLocBytes.size)

        val deserializedWithoutLoc = PacketSerializer.deserialize(withoutLocBytes)
        assertFalse(deserializedWithoutLoc.hasLocation)
        assertTrue(deserializedWithoutLoc.isSemantic)
        assertNull(deserializedWithoutLoc.location)
    }

    // =========================================================================
    // F. DTN COMPATIBILITY
    // =========================================================================

    @Test
    fun testSemanticPacketSurvivesDtnStoreAndForward() {
        val dtn = DtnStore(maxPackets = 10, maxBytes = 10_000, expiryMs = 60_000L)
        val cmd = SemanticCommand(EmergencyCategory.SUPPLY, EmergencySubtype.WATER)
        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_ALERT,
            priority = MessagePriority.ALERT,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 401,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 777,
            destinationDeviceId = 888,
            language = IndicLanguage.ENGLISH,
            payload = cmd.serialize(),
            semanticCommand = cmd
        )

        assertTrue(dtn.store(packet))
        assertEquals(1, dtn.size())

        val drained = dtn.drainForDestination(888)
        assertEquals(1, drained.size)
        val retrieved = drained[0]
        assertTrue(retrieved.isSemantic)
        assertNotNull(retrieved.semanticCommand)
        assertEquals(EmergencyCategory.SUPPLY, retrieved.semanticCommand!!.category)
        assertEquals(EmergencySubtype.WATER, retrieved.semanticCommand!!.subtype)
    }

    // =========================================================================
    // G. DETERMINISTIC COMPRESSION BENCHMARK
    // =========================================================================

    data class BenchmarkSample(
        val sentence: String,
        val originalBytes: Int,
        val semanticBytes: Int,
        val savedBytes: Int,
        val savingsPct: Double,
        val fullPacketBytesBefore: Int,
        val fullPacketBytesAfter: Int
    )

    @Test
    fun testDeterministicCompressionBenchmark() {
        val sentences = listOf(
            "Medical emergency, 3 people injured and we need an ambulance",
            "Fire at building",
            "Three people trapped",
            "Need water",
            "Evacuate immediately"
        )

        val results = mutableListOf<BenchmarkSample>()

        for (text in sentences) {
            val originalPayload = text.toByteArray(Charsets.UTF_8)
            val cmd = SemanticEmergencyClassifier.classify(text)
            assertNotNull("Classifier must recognize benchmark sentence: '$text'", cmd)

            val semanticPayload = cmd!!.serialize()
            val originalLen = originalPayload.size
            val semanticLen = semanticPayload.size
            val saved = originalLen - semanticLen
            val savingsPct = (saved.toDouble() / originalLen.toDouble()) * 100.0

            val fullPacketBefore = Packet.HEADER_SIZE_BYTES + originalLen + Packet.CRC_SIZE_BYTES
            val fullPacketAfter = Packet.HEADER_SIZE_BYTES + semanticLen + Packet.CRC_SIZE_BYTES

            results.add(
                BenchmarkSample(
                    sentence = text,
                    originalBytes = originalLen,
                    semanticBytes = semanticLen,
                    savedBytes = saved,
                    savingsPct = savingsPct,
                    fullPacketBytesBefore = fullPacketBefore,
                    fullPacketBytesAfter = fullPacketAfter
                )
            )

            assertTrue("Savings must be strictly positive", saved > 0)
            assertTrue("Compression percentage must be >= 40%", savingsPct >= 40.0)
        }

        // Print benchmark table to stdout
        println("=== iTantra Emergency Semantic Compression Benchmark ===")
        println(String.format("%-45s | %-8s | %-8s | %-8s | %-8s | %-12s",
            "Sentence", "Orig (B)", "Sem (B)", "Saved(B)", "Saved %", "Pkt Before/After"))
        println("-".repeat(105))
        for (r in results) {
            println(String.format("%-45s | %-8d | %-8d | %-8d | %-7.1f%% | %d B -> %d B",
                if (r.sentence.length > 45) r.sentence.take(42) + "..." else r.sentence,
                r.originalBytes,
                r.semanticBytes,
                r.savedBytes,
                r.savingsPct,
                r.fullPacketBytesBefore,
                r.fullPacketBytesAfter
            ))
        }
        println("==========================================================")
    }
}
