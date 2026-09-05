package org.sih.itantra.core.protocol

import java.util.concurrent.ConcurrentHashMap

data class PacketFragment(
    val originalSeqNum: Short,
    val fragmentIndex: Int,
    val totalFragments: Int,
    val data: ByteArray
)

/**
 * Handles fragmentation and reassembly for transmission channels with strict MTU limits.
 */
class PacketFragmenter(
    private val maxMtuBytes: Int = 512
) {
    private val reassemblyBuffers = ConcurrentHashMap<Short, MutableMap<Int, ByteArray>>()

    fun fragment(packetBytes: ByteArray, seqNum: Short): List<ByteArray> {
        if (packetBytes.size <= maxMtuBytes) {
            return listOf(packetBytes)
        }

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < packetBytes.size) {
            val length = minOf(maxMtuBytes, packetBytes.size - offset)
            val chunk = packetBytes.copyOfRange(offset, offset + length)
            chunks.add(chunk)
            offset += length
        }
        return chunks
    }

    fun addFragment(seqNum: Short, index: Int, total: Int, data: ByteArray): ByteArray? {
        val map = reassemblyBuffers.computeIfAbsent(seqNum) { ConcurrentHashMap() }
        map[index] = data

        if (map.size == total) {
            // All fragments received: reassemble in order
            val totalSize = (0 until total).sumOf { map[it]?.size ?: 0 }
            val assembled = ByteArray(totalSize)
            var offset = 0
            for (i in 0 until total) {
                val piece = map[i] ?: return null
                System.arraycopy(piece, 0, assembled, offset, piece.size)
                offset += piece.size
            }
            reassemblyBuffers.remove(seqNum)
            return assembled
        }
        return null
    }
}
