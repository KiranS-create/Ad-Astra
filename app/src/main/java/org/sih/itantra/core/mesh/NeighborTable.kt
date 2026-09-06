package org.sih.itantra.core.mesh

import java.util.Collections

/**
 * A discovered MANET neighbor.
 *
 * @param nodeId      Unique device ID of the neighbor.
 * @param transport   Which physical transport this neighbor was seen on ("BT", "WIFI").
 * @param lastSeenMs  Wall-clock time (ms) of the last received HELLO.
 * @param batteryPct  Battery level reported by that node (0–100).
 * @param seqNum      Sequence number from the last HELLO (for freshness).
 */
data class NeighborEntry(
    val nodeId: Int,
    val transport: String,
    val lastSeenMs: Long,
    val batteryPct: Int,
    val seqNum: Int
)

/**
 * Thread-safe, bounded neighbor table with wall-clock expiry.
 *
 * Capacity: [maxNeighbors] entries; eldest is evicted when full.
 * A neighbor is considered stale when [System.currentTimeMillis()] > lastSeenMs + [expiryMs].
 */
class NeighborTable(
    private val maxNeighbors: Int = 64,
    val expiryMs: Long = NEIGHBOR_EXPIRY_MS
) {
    companion object {
        /** 3× HELLO interval (15 s × 3 = 45 s). */
        const val NEIGHBOR_EXPIRY_MS = 45_000L
    }

    private val table: MutableMap<Int, NeighborEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, NeighborEntry>(maxNeighbors, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, NeighborEntry>?): Boolean {
                return size > maxNeighbors
            }
        }
    )

    /**
     * Insert or refresh a neighbor.
     * Always updates if the incoming seqNum >= existing (ties refresh lastSeen).
     */
    fun upsert(entry: NeighborEntry) {
        synchronized(table) {
            val existing = table[entry.nodeId]
            if (existing == null || entry.seqNum >= existing.seqNum) {
                table[entry.nodeId] = entry
            }
        }
    }

    /**
     * Return a neighbor only if present and NOT stale.
     * Stale entries are pruned lazily from the map.
     */
    fun lookup(nodeId: Int): NeighborEntry? {
        synchronized(table) {
            val e = table[nodeId] ?: return null
            return if (isStale(e)) {
                table.remove(nodeId)
                null
            } else e
        }
    }

    /**
     * All currently live (non-stale) neighbors.
     */
    fun liveNeighbors(): List<NeighborEntry> {
        synchronized(table) {
            val now = System.currentTimeMillis()
            val stale = table.values.filter { now - it.lastSeenMs > expiryMs }.map { it.nodeId }
            stale.forEach { table.remove(it) }
            return table.values.toList()
        }
    }

    /** Count of non-stale neighbors. */
    fun liveCount(): Int = liveNeighbors().size

    /** Remove all entries. */
    fun clear() = synchronized(table) { table.clear() }

    private fun isStale(e: NeighborEntry): Boolean =
        System.currentTimeMillis() - e.lastSeenMs > expiryMs
}
