package org.sih.itantra.core.context

import org.sih.itantra.core.protocol.EmergencyCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of attempting to insert or update an entry in [SharedContextStore].
 */
enum class ConflictResolutionResult {
    /** Update accepted and applied to the store. */
    APPLIED,

    /** Duplicate packet with identical version and content; accepted idempotently without modification. */
    APPLIED_IDEMPOTENT,

    /** Rejected because an equal or newer version already exists in the store. */
    REJECTED_STALE,

    /** Rejected because an entry with the same version has conflicting content. */
    REJECTED_CONFLICT,

    /** Rejected because confidence is too low to create or overwrite authoritative context. */
    REJECTED_LOW_CONFIDENCE,

    /** Rejected because the context entry is already expired. */
    REJECTED_EXPIRED
}

/**
 * Feature 19: Bounded, thread-safe, local tactical shared context store.
 *
 * Rules:
 * 1. Bounded memory footprint (max 64 entries, LRU eviction on overflow).
 * 2. Deterministic expiration with lazy cleanup; zero background polling daemons.
 * 3. Strict conflict resolution: monotonic versions only, reject stale and conflicting updates.
 * 4. Confidence-aware: low-confidence updates never overwrite high-confidence context.
 */
object SharedContextStore {

    const val MAX_ENTRIES = 64
    const val DEFAULT_TTL_MS = 300_000L // 5 minutes

    private val store = ConcurrentHashMap<Int, SharedContextEntry>()

    /**
     * Deterministically computes a logical context ID from stable semantic fields.
     */
    fun computeContextId(sourceDeviceId: Int, category: EmergencyCategory, sector: Short): Int {
        var hash = 17
        hash = 31 * hash + (sourceDeviceId and 0xFFFF)
        hash = 31 * hash + (category.id.toInt() and 0xFF)
        hash = 31 * hash + (sector.toInt() and 0xFFFF)
        return (hash and 0x7FFFFFFF) % 65535 + 1 // Bounded positive Short-friendly range [1..65535]
    }

    /**
     * Stores or updates a [SharedContextEntry] enforcing versioning and conflict rules.
     */
    fun put(
        entry: SharedContextEntry,
        currentTime: Long = System.currentTimeMillis()
    ): ConflictResolutionResult {
        if (entry.isExpiredAt(currentTime)) {
            return ConflictResolutionResult.REJECTED_EXPIRED
        }

        synchronized(this) {
            val existing = store[entry.contextId]

            if (existing == null || existing.isExpiredAt(currentTime)) {
                // New entry or overwriting an expired one: must meet high confidence threshold
                if (!ContextConfidence.isAuthoritative(entry.confidence)) {
                    return ConflictResolutionResult.REJECTED_LOW_CONFIDENCE
                }
                ensureCapacity(currentTime)
                store[entry.contextId] = entry
                return ConflictResolutionResult.APPLIED
            }

            // Existing active entry present: evaluate conflict rules
            return when {
                entry.version > existing.version -> {
                    // Newer version: allow if confidence does not violently degrade authoritative context
                    if (entry.confidence < ContextConfidence.MEDIUM_THRESHOLD &&
                        existing.confidence >= ContextConfidence.HIGH_THRESHOLD
                    ) {
                        ConflictResolutionResult.REJECTED_LOW_CONFIDENCE
                    } else {
                        store[entry.contextId] = entry
                        ConflictResolutionResult.APPLIED
                    }
                }
                entry.version < existing.version -> {
                    // Stale update: older versions must NEVER overwrite newer versions
                    ConflictResolutionResult.REJECTED_STALE
                }
                else -> {
                    // Same version: check for identical content vs conflict
                    val isIdentical = entry.category == existing.category &&
                            entry.subtype == existing.subtype &&
                            entry.severity == existing.severity &&
                            entry.count == existing.count &&
                            entry.sector == existing.sector

                    if (isIdentical) {
                        ConflictResolutionResult.APPLIED_IDEMPOTENT
                    } else {
                        ConflictResolutionResult.REJECTED_CONFLICT
                    }
                }
            }
        }
    }

    /**
     * Retrieves an active [SharedContextEntry] by [contextId].
     * Lazily evicts and returns null if the entry has expired.
     */
    fun get(contextId: Int, currentTime: Long = System.currentTimeMillis()): SharedContextEntry? {
        val entry = store[contextId] ?: return null
        if (entry.isExpiredAt(currentTime)) {
            store.remove(contextId)
            return null
        }
        return entry
    }

    /**
     * Looks up an active context matching the given tactical parameters.
     */
    fun findActiveContext(
        sourceDeviceId: Int,
        category: EmergencyCategory,
        sector: Short,
        currentTime: Long = System.currentTimeMillis()
    ): SharedContextEntry? {
        val id = computeContextId(sourceDeviceId, category, sector)
        return get(id, currentTime)
    }

    /**
     * Performs lazy cleanup of all expired entries.
     */
    fun cleanupExpired(currentTime: Long = System.currentTimeMillis()): Int {
        var removed = 0
        val iterator = store.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.isExpiredAt(currentTime)) {
                iterator.remove()
                removed++
            }
        }
        return removed
    }

    /**
     * Enforces storage capacity bounds by evicting expired entries first,
     * then evicting the least recently updated entry.
     */
    private fun ensureCapacity(currentTime: Long) {
        if (store.size < MAX_ENTRIES) return

        // 1. Evict expired entries
        cleanupExpired(currentTime)
        if (store.size < MAX_ENTRIES) return

        // 2. Evict oldest updated entry (LRU)
        val oldestEntry = store.entries.minByOrNull { it.value.lastUpdatedAt }
        if (oldestEntry != null) {
            store.remove(oldestEntry.key)
        }
    }

    /**
     * Returns the count of active, unexpired entries.
     */
    fun size(currentTime: Long = System.currentTimeMillis()): Int {
        cleanupExpired(currentTime)
        return store.size
    }

    /**
     * Clears all entries in the store (for testing or hard reset).
     */
    fun clear() {
        store.clear()
    }
}
