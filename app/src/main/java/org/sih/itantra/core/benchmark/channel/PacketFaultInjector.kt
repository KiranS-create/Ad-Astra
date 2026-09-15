package org.sih.itantra.core.benchmark.channel

import org.sih.itantra.core.protocol.Packet

/**
 * Controlled packet fault and security corruption injector.
 *
 * Used exclusively for validating that corrupted payloads, invalid HMACs,
 * and CRC failures are safely rejected by the iTantra security stack.
 */
object PacketFaultInjector {

    /**
     * Flips bits in the raw serialized packet bytes to simulate bit-errors or tampering.
     */
    fun corruptBytes(rawBytes: ByteArray, offset: Int = 10, bitMask: Byte = 0x5A): ByteArray {
        val corrupted = rawBytes.copyOf()
        val targetIndex = offset.coerceIn(0, corrupted.size - 1)
        corrupted[targetIndex] = (corrupted[targetIndex].toInt() xor bitMask.toInt()).toByte()
        return corrupted
    }

    /**
     * Corrupts the 4-byte CRC-32 trailer at the end of the packet.
     */
    fun corruptCrc(rawBytes: ByteArray): ByteArray {
        if (rawBytes.size < 4) return rawBytes
        val corrupted = rawBytes.copyOf()
        corrupted[corrupted.size - 1] = (corrupted[corrupted.size - 1].toInt() xor 0xFF).toByte()
        return corrupted
    }

    /**
     * Replaces or corrupts the HMAC authentication tag within the packet structure.
     */
    fun corruptHmac(packet: Packet): Packet {
        val badAuthTag = if (packet.authTag != null) {
            val copy = packet.authTag.copyOf()
            copy[0] = (copy[0].toInt() xor 0xFF).toByte()
            copy
        } else {
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0, 0, 0, 0)
        }
        return packet.copy(
            flags = (packet.flags.toInt() or Packet.FLAG_AUTHENTICATED).toByte(),
            authTag = badAuthTag
        )
    }

    /**
     * Tampers with the packet payload without recomputing authentication or CRC.
     */
    fun tamperPayload(packet: Packet): Packet {
        val tamperedPayload = if (packet.payload.isNotEmpty()) {
            val copy = packet.payload.copyOf()
            copy[0] = (copy[0].toInt() xor 0x01).toByte()
            copy
        } else {
            byteArrayOf(0x01, 0x02, 0x03)
        }
        return packet.copy(payload = tamperedPayload)
    }
}
