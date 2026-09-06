package org.sih.itantra.core.mesh

import java.util.Collections

/**
 * State of a MANET route entry.
 */
enum class RouteState {
    VALID,
    EXPIRED,
    INVALID
}

/**
 * A single entry in the MANET routing table.
 *
 * @param destinationNodeId  Final destination node.
 * @param nextHopNodeId      Next hop toward destination (may equal destination for 1-hop neighbors).
 * @param hopCount           Number of hops to destination.
 * @param routeSeqNum        Sequence number of the route (from destination's RREP). Higher = fresher.
 * @param expiryMs           Wall-clock time (ms) after which this entry is considered EXPIRED.
 * @param state              VALID | EXPIRED | INVALID.
 * @param linkQuality        Channel quality score (0.0 to 1.0; 1.0 = best).
 * @param batteryPct         Battery percentage of next hop / path (0 to 100).
 */
data class RouteEntry(
    val destinationNodeId: Int,
    val nextHopNodeId: Int,
    val hopCount: Int,
    val routeSeqNum: Int,
    val expiryMs: Long,
    val state: RouteState = RouteState.VALID,
    val linkQuality: Float = 1.0f,
    val batteryPct: Int = 100
)

/**
 * Bounded route table for MANET routing.
 *
 * - Max [capacity] entries; oldest added is evicted when full (FIFO by insertion order).
 * - Expiry is checked lazily on every [lookup].
 * - Thread-safe: all mutations are synchronized.
 */
class RouteTable(
    private val capacity: Int = 256,
    private val routeLifetimeMs: Long = ROUTE_LIFETIME_MS
) {
    companion object {
        const val ROUTE_LIFETIME_MS = 120_000L  // 2 minutes
    }

    // LinkedHashMap in insertion order → evict eldest when over capacity
    private val table: MutableMap<Int, RouteEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, RouteEntry>(capacity, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, RouteEntry>?): Boolean {
                return size > capacity
            }
        }
    )

    /**
     * Add or update a route using [AdaptiveRouteSelector]. Accepts the route only if:
     * - No existing route, OR
     * - Fresher sequence number, OR
     * - Fewer hops (strictly prioritized), OR
     * - Same hops with superior link quality (> 0.15 diff) or battery health tie-breaker.
     */
    fun addOrUpdate(entry: RouteEntry) {
        synchronized(table) {
            val existing = table[entry.destinationNodeId]
            if (AdaptiveRouteSelector.shouldReplace(existing, entry)) {
                table[entry.destinationNodeId] = entry.copy(
                    expiryMs = System.currentTimeMillis() + routeLifetimeMs,
                    state = RouteState.VALID
                )
            }
        }
    }

    /**
     * Look up the best valid route to [destinationNodeId].
     * Expired entries are pruned lazily. Returns null when no valid route exists.
     */
    fun lookup(destinationNodeId: Int): RouteEntry? {
        synchronized(table) {
            val entry = table[destinationNodeId] ?: return null
            return if (entry.isExpired()) {
                table[destinationNodeId] = entry.copy(state = RouteState.EXPIRED)
                null
            } else {
                entry
            }
        }
    }

    /**
     * Mark a route as INVALID (e.g., after a RERR).
     * Does not remove it immediately — keeps the entry so callers can detect the invalidity.
     */
    fun invalidate(destinationNodeId: Int) {
        synchronized(table) {
            table[destinationNodeId]?.let {
                table[destinationNodeId] = it.copy(state = RouteState.INVALID)
            }
        }
    }

    /**
     * Remove all EXPIRED and INVALID entries. Call periodically or on memory pressure.
     */
    fun pruneExpired() {
        synchronized(table) {
            val iter = table.entries.iterator()
            while (iter.hasNext()) {
                val e = iter.next().value
                if (e.state != RouteState.VALID || e.isExpired()) iter.remove()
            }
        }
    }

    /** Snapshot of all current entries (for diagnostics). */
    fun allEntries(): List<RouteEntry> = synchronized(table) { table.values.toList() }

    /** Number of routes currently stored (including expired/invalid). */
    fun size(): Int = synchronized(table) { table.size }

    /** Valid route count (after lazy expiry check). */
    fun validCount(): Int = synchronized(table) {
        val now = System.currentTimeMillis()
        table.values.count { it.state == RouteState.VALID && it.expiryMs > now }
    }

    private fun RouteEntry.isExpired(): Boolean =
        state != RouteState.VALID || System.currentTimeMillis() > expiryMs
}
