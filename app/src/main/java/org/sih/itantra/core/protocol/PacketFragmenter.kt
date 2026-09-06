package org.sih.itantra.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compact 6-byte binary metadata prepended to each fragment payload.
 *
 * Wire format:
 * - transferId: Short (2 bytes, big-endian)
 * - fragmentIndex: Byte (1 byte, 0-indexed: 0 <= index < fragmentCount)
 * - fragmentCount: Byte (1 byte, total fragments: 2 <= count <= 128)
 * - originalMsgType: Byte (1 byte, e.g. TYPE_TEXT, TYPE_ALERT, TYPE_DISTRESS)
 * - originalFlags: Byte (1 byte, original packet flags e.g. FLAG_COMPRESSED, FLAG_SEMANTIC)
 */
data class FragmentMetadata(
    val transferId: Short,
    val fragmentIndex: Byte,
    val fragmentCount: Byte,
    val originalMsgType: Byte,
    val originalFlags: Byte
) {
    companion object {
        const val HEADER_SIZE_BYTES = 6

        fun serialize(meta: FragmentMetadata): ByteArray {
            val buf = ByteBuffer.allocate(HEADER_SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
            buf.putShort(meta.transferId)
            buf.put(meta.fragmentIndex)
            buf.put(meta.fragmentCount)
            buf.put(meta.originalMsgType)
            buf.put(meta.originalFlags)
            return buf.array()
        }

        fun deserialize(bytes: ByteArray): FragmentMetadata? {
            if (bytes.size < HEADER_SIZE_BYTES) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val transferId = buf.short
            val fragmentIndex = buf.get()
            val fragmentCount = buf.get()
            val originalMsgType = buf.get()
            val originalFlags = buf.get()

            // Strict safety validation on network-controlled fields
            val countInt = fragmentCount.toInt() and 0xFF
            val indexInt = fragmentIndex.toInt() and 0xFF
            if (countInt < 2 || countInt > 128) return null
            if (indexInt >= countInt) return null

            return FragmentMetadata(transferId, fragmentIndex, fragmentCount, originalMsgType, originalFlags)
        }
    }

    fun serialize(): ByteArray = serialize(this)
}

/**
 * Represents an individual fragment containing its 6-byte header and data payload slice.
 */
data class PacketFragment(
    val metadata: FragmentMetadata,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PacketFragment
        if (metadata != other.metadata) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    fun toPayload(): ByteArray {
        val metaBytes = metadata.serialize()
        val result = ByteArray(metaBytes.size + data.size)
        System.arraycopy(metaBytes, 0, result, 0, metaBytes.size)
        System.arraycopy(data, 0, result, metaBytes.size, data.size)
        return result
    }

    companion object {
        fun fromPayload(payload: ByteArray): PacketFragment? {
            val meta = FragmentMetadata.deserialize(payload) ?: return null
            val dataLen = payload.size - FragmentMetadata.HEADER_SIZE_BYTES
            if (dataLen < 0) return null
            val data = ByteArray(dataLen)
            System.arraycopy(payload, FragmentMetadata.HEADER_SIZE_BYTES, data, 0, dataLen)
            return PacketFragment(meta, data)
        }
    }
}

/**
 * Handles application-level packet fragmentation for low-bandwidth radio channels.
 */
class PacketFragmenter(
    val maxPayloadBytes: Int = Packet.MAX_FRAGMENT_PAYLOAD
) {
    /**
     * Slices an application-layer payload into structured [PacketFragment] objects.
     * Returns empty list if payload fits in a single unfragmented packet.
     */
    fun fragment(
        payload: ByteArray,
        transferId: Short,
        originalMsgType: Byte = Packet.TYPE_TEXT,
        originalFlags: Byte = 0
    ): List<PacketFragment> {
        if (payload.size <= maxPayloadBytes) {
            return emptyList()
        }

        val count = ((payload.size + maxPayloadBytes - 1) / maxPayloadBytes)
        if (count > 128) {
            throw IllegalArgumentException("Payload size ${payload.size} exceeds maximum fragment count of 128 (max 16 KB)")
        }

        val fragments = ArrayList<PacketFragment>(count)
        var offset = 0
        var index: Byte = 0
        while (offset < payload.size) {
            val length = minOf(maxPayloadBytes, payload.size - offset)
            val chunk = payload.copyOfRange(offset, offset + length)
            val meta = FragmentMetadata(
                transferId = transferId,
                fragmentIndex = index,
                fragmentCount = count.toByte(),
                originalMsgType = originalMsgType,
                originalFlags = originalFlags
            )
            fragments.add(PacketFragment(meta, chunk))
            offset += length
            index++
        }
        return fragments
    }

    companion object {
        const val MAX_FRAGMENT_PAYLOAD = Packet.MAX_FRAGMENT_PAYLOAD

        fun needsFragmentation(payloadSize: Int): Boolean {
            return payloadSize > MAX_FRAGMENT_PAYLOAD
        }
    }
}
