package org.sih.itantra.core.chat

import org.sih.itantra.core.persistence.MessageRecord

/**
 * Tactical Message Retention Policy.
 *
 * Messages are eligible for local removal strictly 10 minutes (600,000 ms)
 * after their creation timestamp.
 *
 * TRUTHFULNESS GUARANTEES:
 * - Strictly local lifecycle: applies only to local device memory.
 * - Does NOT recall or erase transmitted radio packets.
 * - Does NOT command or affect peer nodes or relays.
 * - Does NOT alter underlying transport ACK records or DTN bundle store.
 * - Zero background battery drain: lazy evaluation on access without polling daemons.
 */
object MessageRetentionPolicy {

    /** Strict 10-minute retention boundary in milliseconds */
    const val RETENTION_PERIOD_MS: Long = 600_000L

    /** Tactical disclaimer banner copy */
    const val RETENTION_DISCLAIMER: String = "LOCAL MESSAGE RETENTION · 10 MIN (APPLIES TO THIS DEVICE ONLY)"

    /** Tactical badge copy */
    const val RETENTION_SHORT_BADGE: String = "RETENTION: 10M LOCAL"

    /**
     * Time provider interface for deterministic testability and time-mocking.
     */
    interface TimeProvider {
        fun currentTimeMillis(): Long
    }

    /**
     * Default system time provider.
     */
    object SystemTimeProvider : TimeProvider {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }

    /**
     * Active time provider. Configurable for deterministic testing.
     */
    var timeProvider: TimeProvider = SystemTimeProvider

    /** Returns the current time in milliseconds from the active time provider. */
    fun currentTime(): Long = timeProvider.currentTimeMillis()

    /** Resets the time provider back to system clock. */
    fun resetTimeProvider() {
        timeProvider = SystemTimeProvider
    }

    /**
     * Determines whether a message timestamp has reached or exceeded the 10-minute retention limit.
     * Boundary rule: currentTime >= timestamp + 600,000L
     */
    fun isExpired(timestamp: Long, currentTimeMs: Long = currentTime()): Boolean {
        return currentTimeMs >= timestamp + RETENTION_PERIOD_MS
    }

    /**
     * Determines whether a MessageRecord is expired based on its creation timestamp.
     */
    fun isExpired(record: MessageRecord, currentTimeMs: Long = currentTime()): Boolean {
        return isExpired(record.timestamp, currentTimeMs)
    }

    /**
     * Returns remaining time-to-live in milliseconds before local pruning, clamped at 0.
     */
    fun getRemainingTtlMs(timestamp: Long, currentTimeMs: Long = currentTime()): Long {
        val expiryTime = timestamp + RETENTION_PERIOD_MS
        return (expiryTime - currentTimeMs).coerceAtLeast(0L)
    }

    /**
     * Filters a list of MessageRecords to include only active (unexpired) messages.
     */
    fun filterActive(records: List<MessageRecord>, currentTimeMs: Long = currentTime()): List<MessageRecord> {
        return records.filter { !isExpired(it, currentTimeMs) }
    }

    /**
     * Formats remaining TTL into a concise tactical badge label.
     * Examples: "TTL: 8m", "EXPIRING: 42s", "<1m", "EXPIRED"
     */
    fun formatTtl(remainingMs: Long): String {
        return when {
            remainingMs <= 0L -> "EXPIRED"
            remainingMs < 60_000L -> "<1m"
            else -> "${remainingMs / 60_000L}m"
        }
    }
}
