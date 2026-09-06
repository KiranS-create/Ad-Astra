package org.sih.itantra.core.protocol

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class CorruptPacketException(message: String) : Exception(message)

/**
 * Ultra-compact binary serializer and deserializer for iTantra radio packets.
 * Framing overhead is strictly 31 bytes (27 bytes header + 4 bytes CRC-32).
 */
object PacketSerializer {

    fun serialize(packet: Packet): ByteArray {
        val hasLocation = (packet.flags.toInt() and Packet.FLAG_HAS_LOCATION) != 0 && packet.location != null
        val locBytes = if (hasLocation) Packet.LOCATION_SIZE_BYTES else 0
        val totalLength = Packet.HEADER_SIZE_BYTES + locBytes + packet.payload.size + Packet.CRC_SIZE_BYTES
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)

        buffer.putShort(packet.magic)
        buffer.put(packet.version)
        buffer.put(packet.msgType)
        buffer.put(packet.priority.id)
        buffer.put(packet.ttl)
        buffer.put(packet.flags)
        buffer.putShort(packet.sequenceNumber)
        buffer.putLong(packet.timestamp)
        buffer.putInt(packet.sourceDeviceId)
        buffer.putInt(packet.destinationDeviceId)
        buffer.put(packet.language.id)
        buffer.putShort(packet.payload.size.toShort())

        if (hasLocation) {
            val loc = packet.location!!
            buffer.putDouble(loc.latitude)
            buffer.putDouble(loc.longitude)
            buffer.putFloat(loc.accuracy)
            buffer.putLong(loc.timestamp)
            buffer.putFloat(loc.altitude?.toFloat() ?: Float.NaN)
        }

        buffer.put(packet.payload)

        // Calculate CRC32 over all bytes preceding CRC (header + optional location + payload)
        val crc = CRC32()
        crc.update(buffer.array(), 0, Packet.HEADER_SIZE_BYTES + locBytes + packet.payload.size)
        val calculatedCrc = crc.value

        buffer.putInt(calculatedCrc.toInt())
        return buffer.array()
    }

    fun deserialize(rawBytes: ByteArray): Packet {
        if (rawBytes.size < Packet.MIN_PACKET_SIZE) {
            throw CorruptPacketException("Packet undersized: ${rawBytes.size} bytes (min ${Packet.MIN_PACKET_SIZE})")
        }

        val buffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.short
        if (magic != Packet.MAGIC) {
            throw CorruptPacketException("Invalid magic bytes: 0x${Integer.toHexString(magic.toInt() and 0xFFFF)}")
        }

        val version = buffer.get()
        if (version != Packet.PROTOCOL_VERSION) {
            throw CorruptPacketException("Unsupported protocol version: $version")
        }

        val msgType = buffer.get()
        val priorityId = buffer.get()
        val ttl = buffer.get()
        val flags = buffer.get()
        val seqNum = buffer.short
        val timestamp = buffer.long
        val sourceId = buffer.int
        val destId = buffer.int
        val languageId = buffer.get()
        val payloadLen = buffer.short.toInt() and 0xFFFF

        val hasLocation = (flags.toInt() and Packet.FLAG_HAS_LOCATION) != 0
        val locBytes = if (hasLocation) Packet.LOCATION_SIZE_BYTES else 0

        if (rawBytes.size < Packet.HEADER_SIZE_BYTES + locBytes + payloadLen + Packet.CRC_SIZE_BYTES) {
            throw CorruptPacketException("Declared payload length ($payloadLen) + location ($locBytes) exceeds buffer size (${rawBytes.size})")
        }

        val location = if (hasLocation) {
            val lat = buffer.double
            val lon = buffer.double
            val acc = buffer.float
            val time = buffer.long
            val altRaw = buffer.float
            val alt = if (altRaw.isNaN()) null else altRaw.toDouble()
            GeoLocation(
                latitude = lat,
                longitude = lon,
                accuracy = acc,
                timestamp = time,
                altitude = alt
            )
        } else null

        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val receivedCrc = buffer.int.toLong() and 0xFFFFFFFFL

        val crc = CRC32()
        crc.update(rawBytes, 0, Packet.HEADER_SIZE_BYTES + locBytes + payloadLen)
        val computedCrc = crc.value

        if (receivedCrc != computedCrc) {
            throw CorruptPacketException("CRC-32 checksum mismatch: received $receivedCrc, computed $computedCrc")
        }

        return Packet(
            magic = magic,
            version = version,
            msgType = msgType,
            priority = MessagePriority.fromId(priorityId),
            ttl = ttl,
            flags = flags,
            sequenceNumber = seqNum,
            timestamp = timestamp,
            sourceDeviceId = sourceId,
            destinationDeviceId = destId,
            language = IndicLanguage.fromId(languageId),
            payload = payload,
            location = location,
            crc32 = receivedCrc
        )
    }
}
