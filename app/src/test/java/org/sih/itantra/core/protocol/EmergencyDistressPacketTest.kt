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
import org.sih.itantra.core.mesh.PacketRelayRouter
import org.sih.itantra.core.mesh.RelayAction

class EmergencyDistressPacketTest {

    private val sampleLocation = GeoLocation(
        latitude = 11.016844,
        longitude = 76.955832,
        accuracy = 12.5f,
        timestamp = 1725500000000L,
        altitude = 420.5
    )

    // 1. DISTRESS packet serialization
    @Test
    fun testDistressPacketSerialization() {
        val payload = "EMERGENCY: FLOOD EVACUATION REQUIRED".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            ttl = Packet.DEFAULT_TTL,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 101,
            timestamp = 1725500000000L,
            sourceDeviceId = 888111,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = payload,
            location = sampleLocation
        )

        val bytes = PacketSerializer.serialize(packet)
        val expectedSize = Packet.HEADER_SIZE_BYTES + Packet.LOCATION_SIZE_BYTES + payload.size + Packet.CRC_SIZE_BYTES
        assertEquals("Distress packet with location should match header + location + payload + crc size", expectedSize, bytes.size)
    }

    // 2. DISTRESS packet deserialization
    @Test
    fun testDistressPacketDeserialization() {
        val payload = "அவசர உதவி தேவை!".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 42,
            timestamp = 1725500000000L,
            sourceDeviceId = 777222,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.TAMIL,
            payload = payload,
            location = sampleLocation
        )

        val bytes = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(bytes)

        assertEquals(Packet.TYPE_DISTRESS, deserialized.msgType)
        assertEquals(MessagePriority.DISTRESS, deserialized.priority)
        assertEquals(packet.sequenceNumber, deserialized.sequenceNumber)
        assertEquals(packet.sourceDeviceId, deserialized.sourceDeviceId)
        assertEquals(packet.language, deserialized.language)
        assertArrayEquals(packet.payload, deserialized.payload)
        assertTrue(deserialized.hasLocation)
        assertNotNull(deserialized.location)
    }

    // 3. Location metadata preservation
    @Test
    fun testLocationMetadataPreservation() {
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 1,
            timestamp = 1725500000000L,
            sourceDeviceId = 123456,
            language = IndicLanguage.HINDI,
            payload = "आपातकालीन स्थिति".toByteArray(Charsets.UTF_8),
            location = sampleLocation
        )

        val bytes = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(bytes)
        val loc = deserialized.location

        assertNotNull("Location must not be null after deserialization", loc)
        assertEquals(sampleLocation.latitude, loc!!.latitude, 0.000001)
        assertEquals(sampleLocation.longitude, loc.longitude, 0.000001)
        assertEquals(sampleLocation.accuracy, loc.accuracy, 0.01f)
        assertEquals(sampleLocation.timestamp, loc.timestamp)
        assertNotNull(loc.altitude)
        assertEquals(sampleLocation.altitude!!, loc.altitude!!, 0.01)
    }

    // 4. Normal TEXT has no location
    @Test
    fun testNormalTextHasNoLocation() {
        val payload = "Normal conversational radio text".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.NORMAL,
            flags = 0,
            sequenceNumber = 12,
            timestamp = 1725500000000L,
            sourceDeviceId = 555000,
            language = IndicLanguage.ENGLISH,
            payload = payload,
            location = null
        )

        val bytes = PacketSerializer.serialize(packet)
        // Normal text packet must strictly be 28 + payload.size + 4 bytes
        val expectedSize = Packet.HEADER_SIZE_BYTES + payload.size + Packet.CRC_SIZE_BYTES
        assertEquals("Normal text packet must not include location bytes", expectedSize, bytes.size)

        val deserialized = PacketSerializer.deserialize(bytes)
        assertFalse("Normal packet must not have location flag", deserialized.hasLocation)
        assertNull("Normal packet location must be null", deserialized.location)
    }

    // 5. Location-absent DISTRESS works
    @Test
    fun testLocationAbsentDistressWorks() {
        val payload = "EMERGENCY: GPS DISABLED".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = 0, // No FLAG_HAS_LOCATION
            sequenceNumber = 99,
            timestamp = 1725500000000L,
            sourceDeviceId = 333444,
            language = IndicLanguage.ENGLISH,
            payload = payload,
            location = null
        )

        val bytes = PacketSerializer.serialize(packet)
        val expectedSize = Packet.HEADER_SIZE_BYTES + payload.size + Packet.CRC_SIZE_BYTES
        assertEquals("Location-absent distress packet must not inflate packet size", expectedSize, bytes.size)

        val deserialized = PacketSerializer.deserialize(bytes)
        assertEquals(Packet.TYPE_DISTRESS, deserialized.msgType)
        assertEquals(MessagePriority.DISTRESS, deserialized.priority)
        assertFalse(deserialized.hasLocation)
        assertNull(deserialized.location)
        assertArrayEquals(payload, deserialized.payload)
    }

    // 6. CRC remains valid and detects corruption
    @Test
    fun testCrcValidationAndCorruptionDetection() {
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 55,
            timestamp = 1725500000000L,
            sourceDeviceId = 999111,
            language = IndicLanguage.HINDI,
            payload = "सुरक्षा सहायता".toByteArray(Charsets.UTF_8),
            location = sampleLocation
        )

        val bytes = PacketSerializer.serialize(packet)
        // Valid deserialization
        val deserialized = PacketSerializer.deserialize(bytes)
        assertEquals(packet.sourceDeviceId, deserialized.sourceDeviceId)

        // Corrupt a byte in the location payload region
        val corruptedBytes = bytes.clone()
        val corruptIndex = Packet.HEADER_SIZE_BYTES + 4 // inside latitude field
        corruptedBytes[corruptIndex] = (corruptedBytes[corruptIndex].toInt() xor 0xFF).toByte()

        var caught = false
        try {
            PacketSerializer.deserialize(corruptedBytes)
        } catch (e: CorruptPacketException) {
            caught = true
            assertTrue(e.message!!.contains("CRC-32 checksum mismatch"))
        }
        assertTrue("Corrupt packet must throw CorruptPacketException", caught)
    }

    // 7. TTL still works
    @Test
    fun testTtlDecrementPreservesLocation() {
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            ttl = 3,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 77,
            timestamp = 1725500000000L,
            sourceDeviceId = 123789,
            language = IndicLanguage.TELUGU,
            payload = "సహాయం కావాలి".toByteArray(Charsets.UTF_8),
            location = sampleLocation
        )

        val decremented = packet.copy(ttl = (packet.ttl - 1).toByte())
        assertEquals(2.toByte(), decremented.ttl)
        assertNotNull(decremented.location)
        assertEquals(sampleLocation.latitude, decremented.location!!.latitude, 0.000001)

        val bytes = PacketSerializer.serialize(decremented)
        val deserialized = PacketSerializer.deserialize(bytes)
        assertEquals(2.toByte(), deserialized.ttl)
        assertEquals(sampleLocation.longitude, deserialized.location!!.longitude, 0.000001)
    }

    // 8. Source location is preserved through relay
    @Test
    fun testSourceLocationPreservedThroughRelay() {
        val relayRouter = PacketRelayRouter(localDeviceId = 200002)
        relayRouter.setRelayEnabled(true)

        val originalOriginId = 100001
        val distressPacket = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            ttl = 3,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 10,
            timestamp = 1725500000000L,
            sourceDeviceId = originalOriginId,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.TAMIL,
            payload = "காப்பாற்றுங்கள்!".toByteArray(Charsets.UTF_8),
            location = sampleLocation
        )

        // Evaluate at intermediate relay node 200002
        val action = relayRouter.evaluatePacket(distressPacket)
        assertTrue("Intermediate relay must ForwardAndDeliver fresh distress packet", action is RelayAction.ForwardAndDeliver)

        val forwardedPacket = (action as RelayAction.ForwardAndDeliver).forwardedPacket
        // Relay node MUST NOT overwrite origin source ID or location
        assertEquals("Source device ID must remain original sender", originalOriginId, forwardedPacket.sourceDeviceId)
        assertEquals("TTL must be decremented by 1", 2.toByte(), forwardedPacket.ttl)
        assertTrue("Packet must have FLAG_FORWARDED set", forwardedPacket.isForwarded)
        assertTrue("Packet must retain FLAG_HAS_LOCATION", forwardedPacket.hasLocation)
        assertNotNull("Location metadata must be intact", forwardedPacket.location)
        assertEquals("Origin latitude must be preserved exactly", sampleLocation.latitude, forwardedPacket.location!!.latitude, 0.000001)
        assertEquals("Origin longitude must be preserved exactly", sampleLocation.longitude, forwardedPacket.location!!.longitude, 0.000001)
        assertEquals("Origin accuracy must be preserved exactly", sampleLocation.accuracy, forwardedPacket.location!!.accuracy, 0.01f)

        // Verify forwarded packet serializes and deserializes cleanly with recomputed CRC
        val forwardedBytes = PacketSerializer.serialize(forwardedPacket)
        val receivedAtThirdNode = PacketSerializer.deserialize(forwardedBytes)
        assertEquals(originalOriginId, receivedAtThirdNode.sourceDeviceId)
        assertEquals(2.toByte(), receivedAtThirdNode.ttl)
        assertEquals(sampleLocation.latitude, receivedAtThirdNode.location!!.latitude, 0.000001)
    }
}
