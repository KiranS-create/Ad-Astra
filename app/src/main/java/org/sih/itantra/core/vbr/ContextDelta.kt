package org.sih.itantra.core.vbr

import org.sih.itantra.core.context.SharedContextEntry
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.SemanticCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result of deserializing a context delta wire payload.
 */
data class DecodedDeltaPayload(
    val delta: ContextDelta,
    val enhancement: SemanticEnhancement?
)

/**
 * Feature 19: Bounded, typed application-layer tactical context delta.
 *
 * Encodes only fields that changed relative to an established [SharedContextEntry],
 * achieving maximum compression without sacrificing semantic correctness.
 *
 * Wire Layout:
 * - Byte 0: Magic Discriminator: 0xCD (never collides with EmergencyCategory IDs 0..10)
 * - Byte 1: Schema Version: 0x01
 * - Bytes 2..3: Context ID: Short (Big-Endian)
 * - Byte 4: Monotonic Version: Byte (1..255)
 * - Byte 5: Delta Field Mask: Byte
 *     Bit 0 (0x01): Category changed (1B)
 *     Bit 1 (0x02): Subtype changed (1B)
 *     Bit 2 (0x04): Severity changed (1B)
 *     Bit 3 (0x08): Count changed (1B)
 *     Bit 4 (0x10): Sector changed (2B Short)
 *     Bit 7 (0x80): Has Enhancement layer
 * - Dynamic payload: Only changed field values packed in order of mask bits.
 * - [Optional] Trailing Enhancement Layer if Bit 7 is set.
 */
data class ContextDelta(
    val contextId: Int,
    val version: Int,
    val category: EmergencyCategory? = null,
    val subtype: EmergencySubtype? = null,
    val severity: EmergencySeverity? = null,
    val count: Int? = null,
    val sector: Short? = null,
    val hasEnhancement: Boolean = false,
    val schemaVersion: Byte = SCHEMA_VERSION
) {
    val deltaMask: Byte
        get() {
            var mask = 0
            if (category != null) mask = mask or MASK_CATEGORY
            if (subtype != null) mask = mask or MASK_SUBTYPE
            if (severity != null) mask = mask or MASK_SEVERITY
            if (count != null) mask = mask or MASK_COUNT
            if (sector != null) mask = mask or MASK_SECTOR
            if (hasEnhancement) mask = mask or MASK_HAS_ENHANCEMENT
            return mask.toByte()
        }

    val changedFieldCount: Int
        get() {
            var c = 0
            if (category != null) c++
            if (subtype != null) c++
            if (severity != null) c++
            if (count != null) c++
            if (sector != null) c++
            return c
        }

    /**
     * Serializes this delta into a compact binary wire payload.
     */
    fun serialize(enhancement: SemanticEnhancement? = null): ByteArray {
        val hasEnh = enhancement != null
        val mask = (deltaMask.toInt() or (if (hasEnh) MASK_HAS_ENHANCEMENT else 0)).toByte()

        // Calculate dynamic payload size
        var dynamicSize = 0
        if (category != null) dynamicSize += 1
        if (subtype != null) dynamicSize += 1
        if (severity != null) dynamicSize += 1
        if (count != null) dynamicSize += 1
        if (sector != null) dynamicSize += 2

        val enhBytes = enhancement?.serialize()
        val totalSize = HEADER_SIZE_BYTES + dynamicSize + (enhBytes?.size ?: 0)

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC_DELTA)
        buffer.put(schemaVersion)
        buffer.putShort((contextId and 0xFFFF).toShort())
        buffer.put((version and 0xFF).toByte())
        buffer.put(mask)

        if (category != null) buffer.put(category.id)
        if (subtype != null) buffer.put(subtype.id)
        if (severity != null) buffer.put(severity.id)
        if (count != null) buffer.put((count and 0xFF).toByte())
        if (sector != null) buffer.putShort(sector)

        if (enhBytes != null) {
            buffer.put(enhBytes)
        }

        return buffer.array()
    }

    /**
     * Reconstructs a complete updated [SharedContextEntry] by applying this delta to [baseContext].
     */
    fun applyTo(
        baseContext: SharedContextEntry,
        newConfidence: Int = baseContext.confidence,
        currentTime: Long = System.currentTimeMillis(),
        ttlMs: Long = SharedContextEntry.DEFAULT_TTL_MS
    ): SharedContextEntry {
        return baseContext.copy(
            version = version,
            category = category ?: baseContext.category,
            subtype = subtype ?: baseContext.subtype,
            severity = severity ?: baseContext.severity,
            count = count ?: baseContext.count,
            sector = sector ?: baseContext.sector,
            confidence = newConfidence.coerceIn(0, 100),
            lastUpdatedAt = currentTime,
            expiresAt = currentTime + ttlMs
        )
    }

    /**
     * Generates a concise human-readable summary of changed fields.
     */
    fun toSummaryString(baseContext: SharedContextEntry? = null): String {
        val changes = mutableListOf<String>()
        if (count != null) {
            changes.add(if (baseContext != null) "PEOPLE: ${baseContext.count} → $count" else "PEOPLE: $count")
        }
        if (sector != null) {
            changes.add(if (baseContext != null) "SECTOR: ${baseContext.sector} → $sector" else "SECTOR: $sector")
        }
        if (severity != null) {
            changes.add(if (baseContext != null) "SEVERITY: ${baseContext.severity.label} → ${severity.label}" else "SEVERITY: ${severity.label}")
        }
        if (subtype != null) {
            changes.add(if (baseContext != null) "SUBTYPE: ${baseContext.subtype.label} → ${subtype.label}" else "SUBTYPE: ${subtype.label}")
        }
        if (category != null) {
            changes.add(if (baseContext != null) "CATEGORY: ${baseContext.category.label} → ${category.label}" else "CATEGORY: ${category.label}")
        }
        return if (changes.isNotEmpty()) changes.joinToString(", ") else "NO CHANGE (KEEP-ALIVE)"
    }

    companion object {
        const val MAGIC_DELTA: Byte = 0xCD.toByte()
        const val SCHEMA_VERSION: Byte = 1
        const val HEADER_SIZE_BYTES: Int = 6

        const val MASK_CATEGORY: Int = 1 shl 0
        const val MASK_SUBTYPE: Int = 1 shl 1
        const val MASK_SEVERITY: Int = 1 shl 2
        const val MASK_COUNT: Int = 1 shl 3
        const val MASK_SECTOR: Int = 1 shl 4
        const val MASK_HAS_ENHANCEMENT: Int = 1 shl 7

        /**
         * Fast check if a binary payload represents a context delta.
         */
        fun isContextDeltaPayload(bytes: ByteArray): Boolean {
            return bytes.size >= HEADER_SIZE_BYTES && bytes[0] == MAGIC_DELTA
        }

        /**
         * Computes the delta between an existing [baseContext] and a new [newCommand].
         */
        fun computeDelta(
            baseContext: SharedContextEntry,
            newCommand: SemanticCommand,
            newVersion: Int,
            hasEnhancement: Boolean = false
        ): ContextDelta {
            val cat = if (newCommand.category != baseContext.category) newCommand.category else null
            val sub = if (newCommand.subtype != baseContext.subtype) newCommand.subtype else null
            val sev = if (newCommand.severity != baseContext.severity) newCommand.severity else null
            val cnt = if (newCommand.count != baseContext.count) newCommand.count else null
            val sec = if (newCommand.parameter != baseContext.sector) newCommand.parameter else null

            return ContextDelta(
                contextId = baseContext.contextId,
                version = newVersion,
                category = cat,
                subtype = sub,
                severity = sev,
                count = cnt,
                sector = sec,
                hasEnhancement = hasEnhancement,
                schemaVersion = SCHEMA_VERSION
            )
        }

        /**
         * Deserializes a [ContextDelta] and optional enhancement layer from bytes.
         * Returns null if the data is corrupt, under-sized, or uses an unknown schema.
         */
        fun deserialize(bytes: ByteArray, offset: Int = 0): DecodedDeltaPayload? {
            val remaining = bytes.size - offset
            if (remaining < HEADER_SIZE_BYTES) return null

            val buffer = ByteBuffer.wrap(bytes, offset, remaining).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.get()
            if (magic != MAGIC_DELTA) return null

            val version = buffer.get()
            if (version != SCHEMA_VERSION) return null // Strict schema check

            val contextId = buffer.short.toInt() and 0xFFFF
            val contextVer = buffer.get().toInt() and 0xFF
            val mask = buffer.get().toInt() and 0xFF

            var cat: EmergencyCategory? = null
            var sub: EmergencySubtype? = null
            var sev: EmergencySeverity? = null
            var cnt: Int? = null
            var sec: Short? = null

            if ((mask and MASK_CATEGORY) != 0) {
                if (buffer.remaining() < 1) return null
                cat = EmergencyCategory.fromId(buffer.get()) ?: return null
            }
            if ((mask and MASK_SUBTYPE) != 0) {
                if (buffer.remaining() < 1) return null
                sub = EmergencySubtype.fromId(buffer.get()) ?: return null
            }
            if ((mask and MASK_SEVERITY) != 0) {
                if (buffer.remaining() < 1) return null
                sev = EmergencySeverity.fromId(buffer.get()) ?: return null
            }
            if ((mask and MASK_COUNT) != 0) {
                if (buffer.remaining() < 1) return null
                cnt = buffer.get().toInt() and 0xFF
            }
            if ((mask and MASK_SECTOR) != 0) {
                if (buffer.remaining() < 2) return null
                sec = buffer.short
            }

            val hasEnh = (mask and MASK_HAS_ENHANCEMENT) != 0
            var enhancement: SemanticEnhancement? = null
            if (hasEnh && buffer.remaining() >= 4) {
                val enhOffset = offset + (bytes.size - offset - buffer.remaining())
                enhancement = SemanticEnhancement.deserialize(bytes, offset = enhOffset)
            }

            val delta = ContextDelta(
                contextId = contextId,
                version = contextVer,
                category = cat,
                subtype = sub,
                severity = sev,
                count = cnt,
                sector = sec,
                hasEnhancement = hasEnh && enhancement != null,
                schemaVersion = version
            )

            return DecodedDeltaPayload(delta, enhancement)
        }
    }
}
