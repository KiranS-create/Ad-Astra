package org.sih.itantra.core.mesh

import android.util.Log
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import java.io.File
import java.util.PriorityQueue

/**
 * Thread-safe, bounded, priority-ordered Delay-Tolerant Network (DTN) packet store.
 *
 * Requirements:
 * - Bounded queue (max 50 packets)
 * - Bounded total bytes (max 512 KB)
 * - Expiry threshold (10 minutes)
 * - Priority order: DISTRESS > ALERT > IMPORTANT > NORMAL
 * - Duplicate suppression based on (sourceDeviceId, sequenceNumber)
 * - Operates purely on serialized compact binary packets (zero audio / PCM)
 */
class DtnStore(
    private val storageDir: File? = null,
    private val maxPackets: Int = MAX_PACKETS,
    private val maxBytes: Int = MAX_BYTES,
    private val expiryMs: Long = EXPIRY_MS
) {
    companion object {
        const val TAG = "DtnStore"
        const val MAX_PACKETS = 50
        const val MAX_BYTES = 512 * 1024 // 512 KB
        const val EXPIRY_MS = 600_000L    // 10 minutes
    }

    data class DtnEntry(
        val key: String, // "src_seq"
        val destinationId: Int,
        val priority: MessagePriority,
        val enqueuedMs: Long,
        val packetBytes: ByteArray
    ) : Comparable<DtnEntry> {
        // PriorityQueue orders by natural comparison.
        // Higher priority first; for same priority, oldest first (FIFO).
        override fun compareTo(other: DtnEntry): Int {
            val priorityDiff = other.priority.id.compareTo(this.priority.id) // descending
            if (priorityDiff != 0) return priorityDiff
            return this.enqueuedMs.compareTo(other.enqueuedMs) // ascending
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DtnEntry) return false
            return key == other.key
        }

        override fun hashCode(): Int = key.hashCode()
    }

    private val entries = mutableListOf<DtnEntry>()
    private val lock = Any()

    init {
        storageDir?.let { dir ->
            if (!dir.exists()) dir.mkdirs()
            loadFromDisk(dir)
        }
    }

    /**
     * Store a packet in DTN storage.
     * Returns true if accepted, false if dropped (duplicate, queue full of higher priority, etc.).
     */
    fun store(packet: Packet): Boolean = synchronized(lock) {
        drainExpiredInternal()

        val key = "${packet.sourceDeviceId}_${packet.sequenceNumber}"
        if (entries.any { it.key == key }) {
            Log.d(TAG, "DTN DROP DUP: src=${packet.sourceDeviceId} seq=${packet.sequenceNumber}")
            return false
        }

        val serialized = try {
            PacketSerializer.serialize(packet)
        } catch (e: Exception) {
            Log.e(TAG, "DTN serialize error: ${e.message}")
            return false
        }

        var currentTotalBytes = entries.sumOf { it.packetBytes.size }

        // Eviction loop if capacity is reached
        while (entries.size >= maxPackets || (currentTotalBytes + serialized.size) > maxBytes) {
            // Find lowest priority entry to evict
            val lowest = entries.minByOrNull { it.priority.id } ?: break

            // If the lowest is higher priority than this incoming packet, we cannot evict
            if (lowest.priority.id > packet.priority.id) {
                Log.w(TAG, "DTN DROP: Queue full with higher priority traffic")
                DiagnosticsRepository.recordDtnDropped()
                return false
            }

            // Evict lowest
            entries.remove(lowest)
            deleteFromDisk(lowest.key)
            currentTotalBytes -= lowest.packetBytes.size
            Log.i(TAG, "DTN EVICT: Dropped ${lowest.key} (priority=${lowest.priority}) to make room")
            DiagnosticsRepository.recordDtnDropped()
        }

        val entry = DtnEntry(
            key = key,
            destinationId = packet.destinationDeviceId,
            priority = packet.priority,
            enqueuedMs = System.currentTimeMillis(),
            packetBytes = serialized
        )

        entries.add(entry)
        saveToDisk(entry)
        DiagnosticsRepository.recordDtnStored(entries.size)
        Log.i(TAG, "DTN STORED: dest=${packet.destinationDeviceId} priority=${packet.priority} size=${serialized.size}B (queue=${entries.size})")
        return true
    }

    /**
     * Drain stored packets for a specific destination, returned in priority order.
     */
    fun drainForDestination(destinationId: Int): List<Packet> = synchronized(lock) {
        drainExpiredInternal()

        // Match packets for destination or broadcast
        val matches = entries.filter { it.destinationId == destinationId || it.destinationId == Packet.BROADCAST_ID }
            .sorted() // orders by priority descending, enqueuedMs ascending

        val result = mutableListOf<Packet>()
        for (entry in matches) {
            entries.remove(entry)
            deleteFromDisk(entry.key)
            try {
                val deserialized = PacketSerializer.deserialize(entry.packetBytes)
                result.add(deserialized)
                DiagnosticsRepository.recordDtnForwarded(entries.size)
                Log.i(TAG, "DTN DRAIN FORWARD: dest=$destinationId key=${entry.key} (remaining=${entries.size})")
            } catch (e: Exception) {
                Log.e(TAG, "DTN deserialize error for ${entry.key}: ${e.message}")
            }
        }
        result
    }

    /**
     * Drain all expired packets.
     */
    fun drainExpired(): List<Packet> = synchronized(lock) {
        drainExpiredInternal()
    }

    /**
     * Prune expired packets and return the count pruned.
     */
    fun pruneExpired(): Int = synchronized(lock) {
        drainExpiredInternal().size
    }

    /**
     * Drain all stored packets regardless of destination, returned in priority order.
     */
    fun drainAll(): List<Packet> = synchronized(lock) {
        drainExpiredInternal()
        val all = entries.sorted()
        val result = mutableListOf<Packet>()
        for (entry in all) {
            deleteFromDisk(entry.key)
            try {
                result.add(PacketSerializer.deserialize(entry.packetBytes))
            } catch (_: Exception) {}
        }
        entries.clear()
        result
    }

    private fun drainExpiredInternal(): List<Packet> {
        val now = System.currentTimeMillis()
        val expired = entries.filter { (now - it.enqueuedMs) > expiryMs }
        val expiredPackets = mutableListOf<Packet>()

        for (e in expired) {
            entries.remove(e)
            deleteFromDisk(e.key)
            DiagnosticsRepository.recordDtnExpired(entries.size)
            Log.w(TAG, "DTN EXPIRED: key=${e.key} dest=${e.destinationId}")
            try {
                expiredPackets.add(PacketSerializer.deserialize(e.packetBytes))
            } catch (_: Exception) {}
        }
        return expiredPackets
    }

    fun remove(transferOrSeqId: Short): Boolean = synchronized(lock) {
        val toRemove = entries.filter { entry ->
            if (entry.key.endsWith("_$transferOrSeqId")) {
                true
            } else {
                try {
                    val packet = PacketSerializer.deserialize(entry.packetBytes)
                    if (packet.sequenceNumber == transferOrSeqId) {
                        true
                    } else if (packet.isFragmented) {
                        org.sih.itantra.core.protocol.FragmentMetadata.deserialize(packet.payload)?.transferId == transferOrSeqId
                    } else {
                        false
                    }
                } catch (_: Exception) {
                    false
                }
            }
        }
        if (toRemove.isNotEmpty()) {
            for (e in toRemove) {
                entries.remove(e)
                deleteFromDisk(e.key)
            }
            Log.i(TAG, "DTN REMOVED ACKED: id=$transferOrSeqId count=${toRemove.size}")
            return true
        }
        return false
    }

    fun size(): Int = synchronized(lock) { entries.size }
    fun count(): Int = size()

    fun totalByteCount(): Int = synchronized(lock) { entries.sumOf { it.packetBytes.size } }

    fun clear() = synchronized(lock) {
        entries.clear()
        storageDir?.listFiles()?.forEach { it.delete() }
    }

    private fun saveToDisk(entry: DtnEntry) {
        if (storageDir == null) return
        try {
            val file = File(storageDir, "${entry.key}.dtn")
            file.writeBytes(entry.packetBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist DTN entry to disk: ${e.message}")
        }
    }

    private fun deleteFromDisk(key: String) {
        if (storageDir == null) return
        try {
            val file = File(storageDir, "$key.dtn")
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    private fun loadFromDisk(dir: File) {
        val files = dir.listFiles { _, name -> name.endsWith(".dtn") } ?: return
        val now = System.currentTimeMillis()
        for (f in files) {
            try {
                val bytes = f.readBytes()
                val packet = PacketSerializer.deserialize(bytes)
                val key = "${packet.sourceDeviceId}_${packet.sequenceNumber}"
                val age = now - packet.timestamp
                if (age > expiryMs) {
                    f.delete()
                    continue
                }
                entries.add(
                    DtnEntry(
                        key = key,
                        destinationId = packet.destinationDeviceId,
                        priority = packet.priority,
                        enqueuedMs = packet.timestamp,
                        packetBytes = bytes
                    )
                )
            } catch (e: Exception) {
                f.delete()
            }
        }
        Log.i(TAG, "Loaded ${entries.size} persistent DTN packets from disk.")
    }
}
