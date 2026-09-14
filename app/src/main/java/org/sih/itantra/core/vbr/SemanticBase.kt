package org.sih.itantra.core.vbr

import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.SemanticCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Feature 18: Decoded container holding the result of parsing a multi-layer semantic payload.
 */
data class DecodedSemanticPayload(
    val base: SemanticBase?,
    val enhancement: SemanticEnhancement?,
    val isBaseOnly: Boolean
)

/**
 * Feature 18: Compact Deterministic Semantic Base Layer.
 *
 * Encodes the essential tactical intent into an 8-byte bounded structure.
 * Guaranteed to produce a complete, meaningful tactical message even if the
 * optional Enhancement Layer is missing, corrupted, or delayed.
 *
 * Wire Encoding (8 Bytes):
 * - Bytes 0..5: SemanticCommand binary payload (Category 1B, Subtype 1B, Severity 1B, Count 1B, Parameter/Sector 2B)
 * - Byte 6: Schema Version (Byte = 0x01)
 * - Byte 7: Flags (Byte: Bit 0 = FLAG_HAS_ENHANCEMENT 0x01)
 */
data class SemanticBase(
    val schemaVersion: Byte = SCHEMA_VERSION,
    val command: SemanticCommand,
    val sector: Short = command.parameter,
    val hasEnhancement: Boolean = false
) {
    val category: EmergencyCategory get() = command.category
    val subtype: EmergencySubtype get() = command.subtype
    val severity: EmergencySeverity get() = command.severity
    val count: Int get() = command.count

    /**
     * Serializes this [SemanticBase] into an 8-byte bounded binary payload.
     * Bytes 0..5 match [SemanticCommand] exactly for 100% backward compatibility.
     */
    fun serialize(hasEnhancement: Boolean = this.hasEnhancement): ByteArray {
        val buffer = ByteBuffer.allocate(BASE_SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        val cmdBytes = command.serialize()
        buffer.put(cmdBytes)
        buffer.put(schemaVersion)
        val flags: Byte = if (hasEnhancement) FLAG_HAS_ENHANCEMENT else 0
        buffer.put(flags)
        return buffer.array()
    }

    /**
     * Renders a human-readable tactical card.
     * If [enhancementText] is provided, formats progressive detail below the card.
     */
    fun toDisplayString(enhancementText: String? = null): String {
        val baseDisplay = command.toDisplayString()
        return if (!enhancementText.isNullOrBlank()) {
            "$baseDisplay\n📝 $enhancementText"
        } else {
            baseDisplay
        }
    }

    companion object {
        const val SCHEMA_VERSION: Byte = 1
        const val BASE_SIZE_BYTES: Int = 8
        const val LEGACY_BASE_SIZE_BYTES: Int = 6
        const val FLAG_HAS_ENHANCEMENT: Byte = 0x01

        fun fromCommand(
            command: SemanticCommand,
            sector: Short = command.parameter,
            schemaVersion: Byte = SCHEMA_VERSION
        ): SemanticBase {
            val cmd = if (command.parameter != sector) command.copy(parameter = sector) else command
            return SemanticBase(
                schemaVersion = schemaVersion,
                command = cmd,
                sector = sector,
                hasEnhancement = false
            )
        }

        /**
         * Deserializes a [SemanticBase] from raw bytes.
         * Accepts both new 8-byte structures and legacy 6-byte SemanticCommand payloads.
         * Safely returns null if the buffer is too small (< 6 bytes).
         */
        fun deserialize(bytes: ByteArray): SemanticBase? {
            if (bytes.size < LEGACY_BASE_SIZE_BYTES) return null

            val cmd = SemanticCommand.deserialize(bytes) ?: return null

            return if (bytes.size >= BASE_SIZE_BYTES) {
                val version = bytes[6]
                // Validates schema version (safely falls back to current version if within bounds)
                val safeVersion = if (version in 1..10) version else SCHEMA_VERSION
                val flags = bytes[7]
                val hasEnh = (flags.toInt() and FLAG_HAS_ENHANCEMENT.toInt()) != 0
                SemanticBase(
                    schemaVersion = safeVersion,
                    command = cmd,
                    sector = cmd.parameter,
                    hasEnhancement = hasEnh
                )
            } else {
                // Legacy 6-byte base
                SemanticBase(
                    schemaVersion = SCHEMA_VERSION,
                    command = cmd,
                    sector = cmd.parameter,
                    hasEnhancement = false
                )
            }
        }

        /**
         * Deserializes a multi-layer payload containing a base and an optional enhancement layer.
         *
         * Non-negotiable safety property:
         * If the enhancement layer is absent, corrupt, or uses an unknown future schema,
         * the base is ALWAYS preserved and returned as [isBaseOnly = true].
         */
        fun deserializeWithEnhancement(bytes: ByteArray): DecodedSemanticPayload {
            val base = deserialize(bytes) ?: return DecodedSemanticPayload(null, null, false)

            if (bytes.size > BASE_SIZE_BYTES && base.hasEnhancement) {
                val enhancement = SemanticEnhancement.deserialize(bytes, offset = BASE_SIZE_BYTES)
                return if (enhancement != null) {
                    DecodedSemanticPayload(
                        base = base,
                        enhancement = enhancement,
                        isBaseOnly = false
                    )
                } else {
                    // Unknown or corrupt enhancement safely ignored — base is preserved!
                    DecodedSemanticPayload(
                        base = base,
                        enhancement = null,
                        isBaseOnly = true
                    )
                }
            }

            return DecodedSemanticPayload(
                base = base,
                enhancement = null,
                isBaseOnly = true
            )
        }

        /**
         * Serializes a [SemanticBase] and [SemanticEnhancement] into a composite wire payload.
         */
        fun serializeComposite(base: SemanticBase, enhancement: SemanticEnhancement): ByteArray {
            val baseBytes = base.serialize(hasEnhancement = true)
            val enhBytes = enhancement.serialize()
            val result = ByteArray(baseBytes.size + enhBytes.size)
            System.arraycopy(baseBytes, 0, result, 0, baseBytes.size)
            System.arraycopy(enhBytes, 0, result, baseBytes.size, enhBytes.size)
            return result
        }
    }
}
