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
        val hasSemantic = ((packet.flags.toInt() and Packet.FLAG_SEMANTIC) != 0) || packet.semanticCommand != null
        val payload = if (hasSemantic && packet.payload.isEmpty() && packet.semanticCommand != null) {
            packet.semanticCommand.serialize()
        } else {
            packet.payload
        }
        val flags = if (hasSemantic) (packet.flags.toInt() or Packet.FLAG_SEMANTIC).toByte() else packet.flags

        val hasLocation = (flags.toInt() and Packet.FLAG_HAS_LOCATION) != 0 && packet.location != null
        val locBytes = if (hasLocation) Packet.LOCATION_SIZE_BYTES else 0
        val hasAuth = (flags.toInt() and Packet.FLAG_AUTHENTICATED) != 0 && packet.authTag != null
        val authBytes = if (hasAuth) Packet.AUTH_TAG_SIZE_BYTES else 0
        val totalLength = Packet.HEADER_SIZE_BYTES + locBytes + payload.size + authBytes + Packet.CRC_SIZE_BYTES
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)

        buffer.putShort(packet.magic)
        buffer.put(packet.version)
        buffer.put(packet.msgType)
        buffer.put(packet.priority.id)
        buffer.put(packet.ttl)
        buffer.put(flags)
        buffer.putShort(packet.sequenceNumber)
        buffer.putLong(packet.timestamp)
        buffer.putInt(packet.sourceDeviceId)
        buffer.putInt(packet.destinationDeviceId)
        buffer.put(packet.language.id)
        buffer.putShort(payload.size.toShort())

        if (hasLocation) {
            val loc = packet.location!!
            buffer.putDouble(loc.latitude)
            buffer.putDouble(loc.longitude)
            buffer.putFloat(loc.accuracy)
            buffer.putLong(loc.timestamp)
            buffer.putFloat(loc.altitude?.toFloat() ?: Float.NaN)
        }

        buffer.put(payload)

        if (hasAuth) {
            buffer.put(packet.authTag!!)
        }

        // Calculate CRC32 over all bytes preceding CRC (header + optional location + payload + optional authTag)
        val crc = CRC32()
        crc.update(buffer.array(), 0, Packet.HEADER_SIZE_BYTES + locBytes + payload.size + authBytes)
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

        val hasAuth = (flags.toInt() and Packet.FLAG_AUTHENTICATED) != 0
        val authBytes = if (hasAuth) Packet.AUTH_TAG_SIZE_BYTES else 0

        if (rawBytes.size < Packet.HEADER_SIZE_BYTES + locBytes + payloadLen + authBytes + Packet.CRC_SIZE_BYTES) {
            throw CorruptPacketException("Declared payload length ($payloadLen) + location ($locBytes) + auth ($authBytes) exceeds buffer size (${rawBytes.size})")
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

        val authTag = if (hasAuth) {
            val tag = ByteArray(Packet.AUTH_TAG_SIZE_BYTES)
            buffer.get(tag)
            tag
        } else null

        val receivedCrc = buffer.int.toLong() and 0xFFFFFFFFL

        val crc = CRC32()
        crc.update(rawBytes, 0, Packet.HEADER_SIZE_BYTES + locBytes + payloadLen + authBytes)
        val computedCrc = crc.value

        if (receivedCrc != computedCrc) {
            throw CorruptPacketException("CRC-32 checksum mismatch: received $receivedCrc, computed $computedCrc")
        }

        val hasSemantic = (flags.toInt() and Packet.FLAG_SEMANTIC) != 0
        val semanticCommand = if (hasSemantic) {
            SemanticCommand.deserialize(payload)
                ?: throw CorruptPacketException("Malformed semantic command payload: size ${payload.size} (min ${SemanticCommand.SIZE_BYTES})")
        } else null

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
            semanticCommand = semanticCommand,
            authTag = authTag,
            crc32 = receivedCrc
        )
    }
}
