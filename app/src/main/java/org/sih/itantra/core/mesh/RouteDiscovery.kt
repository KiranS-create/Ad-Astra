package org.sih.itantra.core.mesh

import java.nio.ByteBuffer
import java.util.Collections

// ---------------------------------------------------------------------------
// AODV-style control packet payloads (fixed-size, no compression)
// ---------------------------------------------------------------------------

/**
 * Route REQuest broadcast.
 * Wire layout (14 bytes):
 *   [0-3]  originNodeId  (Int)
 *   [4-7]  destNodeId    (Int)
 *   [8-9]  originSeqNum  (Short)
 *   [10-11] reqId        (Short)  — per-origin monotonically increasing
 *   [12]   hopCount      (Byte)
 *   [13]   ttl           (Byte)
 */
data class RreqPayload(
    val originNodeId: Int,
    val destNodeId: Int,
    val originSeqNum: Short,
    val reqId: Short,
    val hopCount: Byte,
    val ttl: Byte
) {
    companion object {
        const val SIZE = 14
        fun deserialize(bytes: ByteArray): RreqPayload? {
            if (bytes.size < SIZE) return null
            val buf = ByteBuffer.wrap(bytes)
            return RreqPayload(
                originNodeId = buf.int,
                destNodeId   = buf.int,
                originSeqNum = buf.short,
                reqId        = buf.short,
                hopCount     = buf.get(),
                ttl          = buf.get()
            )
        }
    }

    fun serialize(): ByteArray = ByteBuffer.allocate(SIZE).apply {
        putInt(originNodeId)
        putInt(destNodeId)
        putShort(originSeqNum)
        putShort(reqId)
        put(hopCount)
        put(ttl)
    }.array()
}

/**
 * Route REPly unicast.
 * Wire layout (11 bytes):
 *   [0-3]  originNodeId  (Int)
 *   [4-7]  destNodeId    (Int)
 *   [8-9]  destSeqNum    (Short)
 *   [10]   hopCount      (Byte)
 */
data class RrepPayload(
    val originNodeId: Int,
    val destNodeId: Int,
    val destSeqNum: Short,
    val hopCount: Byte
) {
    companion object {
        const val SIZE = 11
        fun deserialize(bytes: ByteArray): RrepPayload? {
            if (bytes.size < SIZE) return null
            val buf = ByteBuffer.wrap(bytes)
            return RrepPayload(
                originNodeId = buf.int,
                destNodeId   = buf.int,
                destSeqNum   = buf.short,
                hopCount     = buf.get()
            )
        }
    }

    fun serialize(): ByteArray = ByteBuffer.allocate(SIZE).apply {
        putInt(originNodeId)
        putInt(destNodeId)
        putShort(destSeqNum)
        put(hopCount)
    }.array()
}

/**
 * Route ERRor unicast (sent toward origin when next-hop is unreachable).
 * Wire layout (8 bytes):
 *   [0-3]  brokenDestNodeId   (Int)
 *   [4-7]  affectedSrcNodeId  (Int)
 */
data class RerrPayload(
    val brokenDestNodeId: Int,
    val affectedSrcNodeId: Int
) {
    companion object {
        const val SIZE = 8
        fun deserialize(bytes: ByteArray): RerrPayload? {
            if (bytes.size < SIZE) return null
            val buf = ByteBuffer.wrap(bytes)
            return RerrPayload(buf.int, buf.int)
        }
    }

    fun serialize(): ByteArray = ByteBuffer.allocate(SIZE).apply {
        putInt(brokenDestNodeId)
        putInt(affectedSrcNodeId)
    }.array()
}

// ---------------------------------------------------------------------------
// HELLO payload
// ---------------------------------------------------------------------------

/**
 * HELLO beacon payload.
 * Wire layout (12 bytes):
 *   [0-3]  nodeId      (Int)
 *   [4-5]  seqNum      (Short)
 *   [6]    batteryPct  (Byte)   0–100
 *   [7-11] reserved    (5 bytes, zeros)
 */
data class HelloPayload(
    val nodeId: Int,
    val seqNum: Short,
    val batteryPct: Byte
) {
    companion object {
        const val SIZE = 12
        fun deserialize(bytes: ByteArray): HelloPayload? {
            if (bytes.size < SIZE) return null
            val buf = ByteBuffer.wrap(bytes)
            return HelloPayload(
                nodeId     = buf.int,
                seqNum     = buf.short,
                batteryPct = buf.get()
            )
        }
    }

    fun serialize(): ByteArray = ByteBuffer.allocate(SIZE).apply {
        putInt(nodeId)
        putShort(seqNum)
        put(batteryPct)
        put(ByteArray(5))       // reserved
    }.array()
}

// ---------------------------------------------------------------------------
// RREQ duplicate-suppression cache
// ---------------------------------------------------------------------------

/**
 * Key for RREQ de-duplication: one RREQ from a given (origin, reqId) pair
 * must only be processed once per node.
 */
data class RreqKey(val originNodeId: Int, val reqId: Short)

/**
 * Bounded LRU cache that tracks recently seen RREQ keys.
 * Max [capacity] entries; eldest evicted automatically.
 */
class RreqDupCache(private val capacity: Int = 512) {
    private val cache: MutableSet<RreqKey> = Collections.newSetFromMap(
        Collections.synchronizedMap(
            object : LinkedHashMap<RreqKey, Boolean>(capacity, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RreqKey, Boolean>?): Boolean {
                    return size > capacity
                }
            }
        )
    )

    /** Returns true if this key is a duplicate (already seen). Inserts if new. */
    fun isDuplicate(key: RreqKey): Boolean {
        return !cache.add(key)
    }

    fun clear() = cache.clear()
    fun size() = cache.size
}
