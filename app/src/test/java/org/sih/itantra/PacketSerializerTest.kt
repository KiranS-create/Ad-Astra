package org.sih.itantra

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.CorruptPacketException
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer

class PacketSerializerTest {

    @Test
    fun testSerializationRoundTrip() {
        val payloadText = "हम सुरक्षित हैं और आगे बढ़ रहे हैं।"
        val payloadBytes = payloadText.toByteArray(Charsets.UTF_8)

        val packet = Packet(
            version = 1,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.IMPORTANT,
            flags = 0,
            sequenceNumber = 42,
            timestamp = 1725500000000L,
            sourceDeviceId = 123456,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.HINDI,
            payload = payloadBytes
        )

        val serialized = PacketSerializer.serialize(packet)
        assertEquals(Packet.HEADER_SIZE_BYTES + payloadBytes.size + Packet.CRC_SIZE_BYTES, serialized.size)

        val deserialized = PacketSerializer.deserialize(serialized)
        assertEquals(packet.magic, deserialized.magic)
        assertEquals(packet.version, deserialized.version)
        assertEquals(packet.msgType, deserialized.msgType)
        assertEquals(packet.priority, deserialized.priority)
        assertEquals(packet.sequenceNumber, deserialized.sequenceNumber)
        assertEquals(packet.timestamp, deserialized.timestamp)
        assertEquals(packet.sourceDeviceId, deserialized.sourceDeviceId)
        assertEquals(packet.destinationDeviceId, deserialized.destinationDeviceId)
        assertEquals(packet.language, deserialized.language)
        assertArrayEquals(packet.payload, deserialized.payload)
    }

    @Test(expected = CorruptPacketException::class)
    fun testCorruptedCrcThrowsException() {
        val packet = Packet(
            sequenceNumber = 1,
            timestamp = 1000L,
            sourceDeviceId = 10,
            language = IndicLanguage.TAMIL,
            payload = "வணக்கம்".toByteArray(Charsets.UTF_8)
        )

        val serialized = PacketSerializer.serialize(packet)
        // Corrupt the last byte of CRC
        serialized[serialized.size - 1] = (serialized[serialized.size - 1].toInt() xor 0xFF).toByte()

        PacketSerializer.deserialize(serialized)
    }

    @Test(expected = CorruptPacketException::class)
    fun testCorruptedMagicThrowsException() {
        val packet = Packet(
            sequenceNumber = 2,
            timestamp = 2000L,
            sourceDeviceId = 20,
            language = IndicLanguage.ENGLISH,
            payload = "Test payload".toByteArray(Charsets.UTF_8)
        )

        val serialized = PacketSerializer.serialize(packet)
        // Corrupt magic byte
        serialized[0] = 0x00

        PacketSerializer.deserialize(serialized)
    }

    @Test(expected = CorruptPacketException::class)
    fun testUndersizedPacketThrowsException() {
        val tooShort = ByteArray(20)
        PacketSerializer.deserialize(tooShort)
    }
}
