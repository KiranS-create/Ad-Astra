package org.sih.itantra.core.crypto

/**
 * Result of checking a packet's sequence number against the anti-replay sliding window.
 */
enum class ReplayStatus {
    ACCEPTED,
    DUPLICATE,
    STALE,
    INVALID_SOURCE
}

data class ReplayCheckResult(
    val status: ReplayStatus,
    val sourceDeviceId: Int,
    val sequenceNumber: Short
) {
    val isAccepted: Boolean get() = status == ReplayStatus.ACCEPTED
}

/**
 * High-performance, bounded Anti-Replay sliding window filter for tactical mesh packets.
 *
 * Design:
 * - 64-packet sliding window per [sourceDeviceId] using a 64-bit bitmask ([Long]).
 * - Supports out-of-order packet arrival within the 64-packet window.
 * - Handles 16-bit sequence number rollover gracefully via signed short modular arithmetic.
 * - Thread-safe bounded LRU storage with a hard ceiling of 100 active peer nodes.
 * - Stale entries pruned on inactivity to prevent unbounded memory growth.
 */
class AntiReplayFilter(
    private val maxTrackedNodes: Int = DEFAULT_MAX_NODES,
    private val pruneInactivityMs: Long = DEFAULT_INACTIVITY_PRUNE_MS
) {

    private class NodeWindow(
        var highestSeq: Short,
        var bitmap: Long = 1L,
        var lastUpdatedMs: Long = System.currentTimeMillis()
    )

    // Access-order LinkedHashMap bounded to maxTrackedNodes
    private val nodeWindows = object : LinkedHashMap<Int, NodeWindow>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, NodeWindow>?): Boolean {
            return size > maxTrackedNodes
        }
    }

    /**
     * Evaluates a packet's sequence number from [sourceDeviceId].
     *
     * If the packet is fresh: records the sequence, advances the window if necessary, and returns [ReplayStatus.ACCEPTED].
     * If duplicate or stale: returns [ReplayStatus.DUPLICATE] or [ReplayStatus.STALE].
     */
    fun checkAndRecord(sourceDeviceId: Int, sequenceNumber: Short): ReplayCheckResult {
        val now = System.currentTimeMillis()

        synchronized(nodeWindows) {
            val window = nodeWindows[sourceDeviceId]

            if (window == null) {
                // First packet seen from this source node: initialize window
                nodeWindows[sourceDeviceId] = NodeWindow(
                    highestSeq = sequenceNumber,
                    bitmap = 1L,
                    lastUpdatedMs = now
                )
                return ReplayCheckResult(ReplayStatus.ACCEPTED, sourceDeviceId, sequenceNumber)
            }

            window.lastUpdatedMs = now

            // Calculate signed 16-bit difference with rollover support: (seq - highestSeq)
            val delta = (sequenceNumber - window.highestSeq).toShort().toInt()

            return when {
                delta > 0 -> {
                    // Strictly newer packet: advance the sliding window
                    if (delta >= WINDOW_SIZE) {
                        window.bitmap = 1L
                    } else {
                        window.bitmap = (window.bitmap shl delta) or 1L
                    }
                    window.highestSeq = sequenceNumber
                    ReplayCheckResult(ReplayStatus.ACCEPTED, sourceDeviceId, sequenceNumber)
                }
                delta == 0 -> {
                    // Exact match to highest seen sequence number
                    ReplayCheckResult(ReplayStatus.DUPLICATE, sourceDeviceId, sequenceNumber)
                }
                else -> {
                    // Packet sequence is older than highestSeq: delta < 0
                    val distance = -delta
                    if (distance >= WINDOW_SIZE) {
                        // Beyond 64-packet sliding window: reject as stale
                        ReplayCheckResult(ReplayStatus.STALE, sourceDeviceId, sequenceNumber)
                    } else {
                        val mask = 1L shl distance
                        if ((window.bitmap and mask) != 0L) {
                            // Bit already set: packet was previously received
                            ReplayCheckResult(ReplayStatus.DUPLICATE, sourceDeviceId, sequenceNumber)
                        } else {
                            // Legitimate out-of-order packet within the window: accept and mark bit
                            window.bitmap = window.bitmap or mask
                            ReplayCheckResult(ReplayStatus.ACCEPTED, sourceDeviceId, sequenceNumber)
                        }
                    }
                }
            }
        }
    }

    /**
     * Prunes inactive nodes older than [maxAgeMs].
     * @return Number of nodes evicted.
     */
    fun pruneInactive(maxAgeMs: Long = pruneInactivityMs): Int {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        var evicted = 0
        synchronized(nodeWindows) {
            val it = nodeWindows.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.value.lastUpdatedMs < cutoff) {
                    it.remove()
                    evicted++
                }
            }
        }
        return evicted
    }

    /**
     * Returns the count of currently tracked nodes.
     */
    fun getTrackedNodeCount(): Int {
        synchronized(nodeWindows) {
            return nodeWindows.size
        }
    }

    /**
     * Clears all replay windows.
     */
    fun clear() {
        synchronized(nodeWindows) {
            nodeWindows.clear()
        }
    }

    companion object {
        const val WINDOW_SIZE = 64
        const val DEFAULT_MAX_NODES = 100
        const val DEFAULT_INACTIVITY_PRUNE_MS = 60 * 60 * 1000L // 1 hour
    }
}
