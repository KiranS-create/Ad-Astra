package org.sih.itantra.core.vbr

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Feature 18: Optional Enhancement Layer for Two-Layer Semantic Representation.
 *
 * Carries non-essential but contextually valuable details such as operator wording,
 * normalized natural language rendering, or tactical notes.
 *
 * Wire Encoding:
 * - Byte 0: Schema Version (Byte = 0x01)
 * - Byte 1: Flags (Byte: bit 0 = HAS_DETAIL)
 * - Byte 2..3: Text Length (Short, Big-Endian)
 * - Byte 4..(4+len-1): UTF-8 Text
 * - [Optional] Next 2 bytes: Detail Length + UTF-8 Detail
 */
data class SemanticEnhancement(
    val schemaVersion: Byte = SCHEMA_VERSION,
    val text: String,
    val detail: String? = null
) {
    fun serialize(): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val detailBytes = detail?.toByteArray(Charsets.UTF_8)
        val hasDetail = detailBytes != null && detailBytes.isNotEmpty()

        val totalSize = 4 + textBytes.size + (if (hasDetail) 2 + detailBytes!!.size else 0)
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        buffer.put(schemaVersion)
        val flags: Byte = if (hasDetail) FLAG_HAS_DETAIL else 0
        buffer.put(flags)
        buffer.putShort(textBytes.size.toShort())
        buffer.put(textBytes)

        if (hasDetail) {
            val dBytes = detailBytes!!
            buffer.putShort(dBytes.size.toShort())
            buffer.put(dBytes)
        }

        return buffer.array()
    }

    companion object {
        const val SCHEMA_VERSION: Byte = 1
        const val FLAG_HAS_DETAIL: Byte = 0x01

        /**
         * Deserializes a [SemanticEnhancement] from a byte array starting at [offset].
         * Safely returns null if the schema version is unknown, data is truncated, or corrupted.
         */
        fun deserialize(bytes: ByteArray, offset: Int = 0): SemanticEnhancement? {
            val remaining = bytes.size - offset
            if (remaining < 4) return null

            val buffer = ByteBuffer.wrap(bytes, offset, remaining).order(ByteOrder.BIG_ENDIAN)
            val version = buffer.get()

            // Safe unknown-version handling: reject unhandled future schemas without crashing
            if (version != SCHEMA_VERSION) {
                return null
            }

            val flags = buffer.get()
            val textLen = buffer.short.toInt() and 0xFFFF
            if (textLen < 0 || buffer.remaining() < textLen) {
                return null
            }

            val textBytes = ByteArray(textLen)
            buffer.get(textBytes)
            val text = String(textBytes, Charsets.UTF_8)

            var detail: String? = null
            val hasDetail = (flags.toInt() and FLAG_HAS_DETAIL.toInt()) != 0
            if (hasDetail && buffer.remaining() >= 2) {
                val detailLen = buffer.short.toInt() and 0xFFFF
                if (detailLen in 1..buffer.remaining()) {
                    val detailBytes = ByteArray(detailLen)
                    buffer.get(detailBytes)
                    detail = String(detailBytes, Charsets.UTF_8)
                }
            }

            return SemanticEnhancement(
                schemaVersion = version,
                text = text,
                detail = detail
            )
        }
    }
}
