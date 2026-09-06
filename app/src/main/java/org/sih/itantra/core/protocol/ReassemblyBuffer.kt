package org.sih.itantra.core.protocol

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Composite key identifying a multi-fragment transfer session.
 */
data class TransferKey(
    val sourceDeviceId: Int,
    val transferId: Short
)

/**
 * Result of a successfully completed reassembly.
 */
data class ReassemblyResult(
    val sourceDeviceId: Int,
    val transferId: Short,
    val originalMsgType: Byte,
    val originalFlags: Byte,
    val payload: ByteArray,
    val fragmentCount: Int,
    val reassemblyTimeMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ReassemblyResult
        if (sourceDeviceId != other.sourceDeviceId) return false
        if (transferId != other.transferId) return false
        if (originalMsgType != other.originalMsgType) return false
        if (originalFlags != other.originalFlags) return false
        if (fragmentCount != other.fragmentCount) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sourceDeviceId
        result = 31 * result + transferId.toInt()
        result = 31 * result + originalMsgType.toInt()
        result = 31 * result + originalFlags.toInt()
        result = 31 * result + fragmentCount
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Thread-safe, bounded reassembly buffer for reconstructed multi-fragment messages.
 *
 * Enforces:
 * 1. Out-of-order reassembly
 * 2. Duplicate fragment deduplication
 * 3. 30-second bounded timeout eviction
 * 4. 16 KB hard ceiling on reassembled payload
 * 5. Bounded session capacity (max 50 concurrent transfers)
 */
class ReassemblyBuffer(
    private val maxCapacity: Int = MAX_CAPACITY,
    private val timeoutMs: Long = Packet.REASSEMBLY_TIMEOUT_MS,
    private val maxReassembledBytes: Int = Packet.MAX_REASSEMBLED_BYTES
) {
    companion object {
        private const val TAG = "ReassemblyBuffer"
        const val MAX_CAPACITY = 50
    }

    private data class TransferEntry(
        val sourceDeviceId: Int,
        val transferId: Short,
        val fragmentCount: Int,
        val originalMsgType: Byte,
        val originalFlags: Byte,
        val firstReceivedMs: Long,
        var lastUpdatedMs: Long,
        val fragments: MutableMap<Int, ByteArray> = HashMap()
    )

    private val entries = HashMap<TransferKey, TransferEntry>()
    private val lock = Any()

    /**
     * Ingests a validated [PacketFragment] from an incoming [Packet].
     *
     * Returns [ReassemblyResult] when all fragments have been received and reconstructed,
     * or `null` if the transfer is still incomplete or if the fragment was rejected.
     */
    fun addFragment(sourceDeviceId: Int, fragment: PacketFragment, nowMs: Long = System.currentTimeMillis()): ReassemblyResult? = synchronized(lock) {
        pruneExpired(nowMs)

        val meta = fragment.metadata
        val totalCount = meta.fragmentCount.toInt() and 0xFF
        val index = meta.fragmentIndex.toInt() and 0xFF

        // Validate count and bounds
        if (totalCount < 2 || index >= totalCount) {
            Log.w(TAG, "Rejected fragment with invalid bounds: index=$index, count=$totalCount")
            return null
        }

        // Hard boundary: projected max size cannot exceed 16 KB
        if (totalCount * Packet.MAX_FRAGMENT_PAYLOAD > maxReassembledBytes) {
            Log.w(TAG, "Rejected transfer: projected size exceeds hard limit of $maxReassembledBytes bytes")
            return null
        }

        val key = TransferKey(sourceDeviceId, meta.transferId)
        var entry = entries[key]

        if (entry == null) {
            if (entries.size >= maxCapacity) {
                // Evict oldest incomplete transfer
                val oldestKey = entries.minByOrNull { it.value.firstReceivedMs }?.key
                if (oldestKey != null) {
                    entries.remove(oldestKey)
                    Log.w(TAG, "Reassembly capacity reached ($maxCapacity). Evicted oldest transfer $oldestKey")
                }
            }

            entry = TransferEntry(
                sourceDeviceId = sourceDeviceId,
                transferId = meta.transferId,
                fragmentCount = totalCount,
                originalMsgType = meta.originalMsgType,
                originalFlags = meta.originalFlags,
                firstReceivedMs = nowMs,
                lastUpdatedMs = nowMs
            )
            entries[key] = entry
        } else {
            // Guard against metadata mismatch within the same transfer ID
            if (entry.fragmentCount != totalCount || entry.originalMsgType != meta.originalMsgType) {
                Log.w(TAG, "Metadata mismatch on transfer ${meta.transferId}: count ${entry.fragmentCount} vs $totalCount")
                return null
            }
            entry.lastUpdatedMs = nowMs
        }

        // Duplicate fragment safety: ignore duplicate fragment index
        if (!entry.fragments.containsKey(index)) {
            entry.fragments[index] = fragment.data
        }

        // Check if all expected fragments are now present
        if (entry.fragments.size == entry.fragmentCount) {
            var totalBytes = 0
            for (i in 0 until entry.fragmentCount) {
                val piece = entry.fragments[i]
                if (piece == null) {
                    // Gap detected, incomplete
                    return null
                }
                totalBytes += piece.size
            }

            // Verify strict 16 KB payload ceiling
            if (totalBytes > maxReassembledBytes) {
                Log.e(TAG, "Reassembled size ($totalBytes B) exceeds max ($maxReassembledBytes B). Discarding transfer.")
                entries.remove(key)
                return null
            }

            val assembled = ByteArray(totalBytes)
            var offset = 0
            for (i in 0 until entry.fragmentCount) {
                val piece = entry.fragments[i]!!
                System.arraycopy(piece, 0, assembled, offset, piece.size)
                offset += piece.size
            }

            val reassemblyLatencyMs = (nowMs - entry.firstReceivedMs).coerceAtLeast(0L)
            entries.remove(key)

            Log.i(TAG, "Reassembly complete for transfer 0x${Integer.toHexString(meta.transferId.toInt() and 0xFFFF)} " +
                    "from node $sourceDeviceId: ${assembled.size}B across ${entry.fragmentCount} fragments in ${reassemblyLatencyMs}ms")

            return ReassemblyResult(
                sourceDeviceId = sourceDeviceId,
                transferId = meta.transferId,
                originalMsgType = entry.originalMsgType,
                originalFlags = entry.originalFlags,
                payload = assembled,
                fragmentCount = entry.fragmentCount,
                reassemblyTimeMs = reassemblyLatencyMs
            )
        }

        return null
    }

    /**
     * Prunes expired incomplete transfers exceeding [timeoutMs].
     * Returns the count of purged transfers.
     */
    fun pruneExpired(nowMs: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val expiredKeys = entries.filter { (nowMs - it.value.firstReceivedMs) > timeoutMs }.keys.toList()
        for (k in expiredKeys) {
            val entry = entries.remove(k)
            if (entry != null) {
                Log.d(TAG, "Pruned expired transfer 0x${Integer.toHexString(entry.transferId.toInt() and 0xFFFF)} " +
                        "(${entry.fragments.size}/${entry.fragmentCount} received)")
            }
        }
        return expiredKeys.size
    }

    /**
     * Returns the number of currently incomplete transfers.
     */
    fun pendingCount(): Int = synchronized(lock) {
        entries.size
    }

    /**
     * Clears all reassembly buffers.
     */
    fun clear(): Unit = synchronized(lock) {
        entries.clear()
    }
}
