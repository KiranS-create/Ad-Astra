package org.sih.itantra.core.protocol

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val altitude: Double? = null
)

data class Packet(
    val magic: Short = MAGIC,
    val version: Byte = PROTOCOL_VERSION,
    val msgType: Byte = TYPE_TEXT,
    val priority: MessagePriority = MessagePriority.NORMAL,
    val ttl: Byte = DEFAULT_TTL,
    val flags: Byte = 0,
    val sequenceNumber: Short,
    val timestamp: Long,
    val sourceDeviceId: Int,
    val destinationDeviceId: Int = BROADCAST_ID,
    val language: IndicLanguage,
    val payload: ByteArray,
    val location: GeoLocation? = null,
    val semanticCommand: SemanticCommand? = null,
    val authTag: ByteArray? = null,
    val crc32: Long = 0L
) {
    val isCompressed: Boolean
        get() = (flags.toInt() and FLAG_COMPRESSED) != 0

    val requiresAck: Boolean
        get() = (flags.toInt() and FLAG_REQUIRES_ACK) != 0

    val isFragmented: Boolean
        get() = (flags.toInt() and FLAG_FRAGMENTED) != 0

    val isForwarded: Boolean
        get() = (flags.toInt() and FLAG_FORWARDED) != 0 || ttl < DEFAULT_TTL

    val hasLocation: Boolean
        get() = (flags.toInt() and FLAG_HAS_LOCATION) != 0 && location != null

    val isSemantic: Boolean
        get() = (flags.toInt() and FLAG_SEMANTIC) != 0 || semanticCommand != null

    val isAuthenticated: Boolean
        get() = (flags.toInt() and FLAG_AUTHENTICATED) != 0 && authTag != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Packet
        if (sequenceNumber != other.sequenceNumber) return false
        if (sourceDeviceId != other.sourceDeviceId) return false
        if (timestamp != other.timestamp) return false
        if (location != other.location) return false
        if (semanticCommand != other.semanticCommand) return false
        if (!payload.contentEquals(other.payload)) return false
        if (authTag != null) {
            if (other.authTag == null) return false
            if (!authTag.contentEquals(other.authTag)) return false
        } else if (other.authTag != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.toInt()
        result = 31 * result + sourceDeviceId
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + (semanticCommand?.hashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (authTag?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val MAGIC: Short = 0x4954 // "IT" in ASCII
        const val PROTOCOL_VERSION: Byte = 0x01

        const val TYPE_HELLO: Byte = 1
        const val TYPE_SESSION: Byte = 2
        const val TYPE_TEXT: Byte = 3
        const val TYPE_ACK: Byte = 4
        const val TYPE_ALERT: Byte = 5
        const val TYPE_PING: Byte = 6
        const val TYPE_PONG: Byte = 7

        // MANET routing control packets (transport-agnostic)
        const val TYPE_ROUTE_REQUEST: Byte = 8
        const val TYPE_ROUTE_REPLY: Byte   = 9
        const val TYPE_ROUTE_ERROR: Byte   = 10

        // Emergency Distress
        const val TYPE_DISTRESS: Byte      = 11

        const val FLAG_COMPRESSED: Int = 1 shl 0
        const val FLAG_FRAGMENTED: Int = 1 shl 1
        const val FLAG_REQUIRES_ACK: Int = 1 shl 2
        const val FLAG_FORWARDED: Int = 1 shl 3
        const val FLAG_HAS_LOCATION: Int = 1 shl 4
        const val FLAG_SEMANTIC: Int = 1 shl 5
        const val FLAG_AUTHENTICATED: Int = 1 shl 6

        const val DEFAULT_TTL: Byte = 3

        const val BROADCAST_ID: Int = -1 // 0xFFFFFFFF
        const val HEADER_SIZE_BYTES = 28
        const val LOCATION_SIZE_BYTES = 32 // 8B lat + 8B lon + 4B acc + 8B time + 4B alt
        const val AUTH_TAG_SIZE_BYTES = 8 // 8B truncated HMAC-SHA256
        const val CRC_SIZE_BYTES = 4
        const val MIN_PACKET_SIZE = HEADER_SIZE_BYTES + CRC_SIZE_BYTES // 32 bytes

        // Fragmentation and Reliable Delivery limits
        const val MAX_FRAGMENT_PAYLOAD: Int = 128
        const val MAX_REASSEMBLED_BYTES: Int = 16 * 1024 // 16 KB hard ceiling
        const val REASSEMBLY_TIMEOUT_MS: Long = 30_000L // 30 seconds
        const val DELIVERY_RECEIPT_TIMEOUT_MS: Long = 30_000L // 30 seconds
    }
}
