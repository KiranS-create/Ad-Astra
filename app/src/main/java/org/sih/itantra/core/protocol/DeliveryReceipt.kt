package org.sih.itantra.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compact binary delivery receipt for application-layer message acknowledgement.
 * Payload is strictly 3 bytes:
 * - transferId: Short (2 bytes, big-endian)
 * - status: Byte (1 byte)
 */
data class DeliveryReceipt(
    val transferId: Short,
    val status: Byte = STATUS_DELIVERED
) {
    companion object {
        const val SIZE_BYTES = 3
        const val STATUS_DELIVERED: Byte = 1
        const val STATUS_FAILED: Byte = 2
        const val STATUS_EXPIRED: Byte = 3

        fun serialize(receipt: DeliveryReceipt): ByteArray {
            val buf = ByteBuffer.allocate(SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
            buf.putShort(receipt.transferId)
            buf.put(receipt.status)
            return buf.array()
        }

        fun deserialize(bytes: ByteArray): DeliveryReceipt? {
            if (bytes.size < SIZE_BYTES) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val transferId = buf.short
            val status = buf.get()
            return DeliveryReceipt(transferId, status)
        }
    }

    fun serialize(): ByteArray = serialize(this)

    val isDelivered: Boolean
        get() = status == STATUS_DELIVERED

    val isExpired: Boolean
        get() = status == STATUS_EXPIRED
}
