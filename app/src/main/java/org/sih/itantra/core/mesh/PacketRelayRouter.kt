package org.sih.itantra.core.mesh

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sih.itantra.core.protocol.Packet
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

data class PacketKey(
    val sourceDeviceId: Int,
    val sequenceNumber: Short
)

sealed interface RelayAction {
    /**
     * Packet is valid, non-duplicate, and has remaining TTL > 0.
     * Intermediate node must re-broadcast the forwardedPacket and deliver payload locally.
     */
    data class ForwardAndDeliver(val forwardedPacket: Packet) : RelayAction

    /**
     * Packet is delivered to local receiver/TTS, but not forwarded further
     * (e.g. relay disabled, or TTL reached zero).
     */
    object DeliverLocalOnly : RelayAction

    /**
     * Packet has already been processed or forwarded; discard immediately.
     */
    object DropDuplicate : RelayAction

    /**
     * Packet originated from this device (echo/loopback); discard immediately.
     */
    object DropSelf : RelayAction
}

data class RelayStats(
    val packetsReceived: Long = 0L,
    val packetsForwarded: Long = 0L,
    val duplicatesDropped: Long = 0L,
    val ttlExpiredDropped: Long = 0L,
    val selfPacketsDropped: Long = 0L
)

/**
 * Autonomous, transport-agnostic packet relay router.
 *
 * Enforces:
 * 1. Self-packet suppression (prevents loopback to transmitter)
 * 2. Duplicate suppression via bounded LRU cache (prevents mesh flooding)
 * 3. Hop-limit control via decremented TTL (prevents infinite packet bounce)
 * 4. Transparent payload preservation without requiring intermediate voice reconstruction
 */
class PacketRelayRouter(
    val localDeviceId: Int,
    private val cacheCapacity: Int = 1000
) {
    companion object {
        private const val TAG = "PacketRelayRouter"
    }

    private val _isRelayEnabled = MutableStateFlow(false)
    val isRelayEnabled: StateFlow<Boolean> = _isRelayEnabled.asStateFlow()

    private val countRx = AtomicLong(0)
    private val countFwd = AtomicLong(0)
    private val countDup = AtomicLong(0)
    private val countTtlExpired = AtomicLong(0)
    private val countSelf = AtomicLong(0)

    // Bounded thread-safe LRU cache
    private val seenCache: MutableMap<PacketKey, Long> = Collections.synchronizedMap(
        object : LinkedHashMap<PacketKey, Long>(cacheCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PacketKey, Long>?): Boolean {
                return size > cacheCapacity
            }
        }
    )

    fun setRelayEnabled(enabled: Boolean) {
        _isRelayEnabled.value = enabled
        Log.i(TAG, "Mesh relay routing set to: $enabled (nodeId=$localDeviceId)")
    }

    /**
     * Evaluates an incoming packet against mesh routing policies.
     */
    fun evaluatePacket(packet: Packet): RelayAction {
        // 1. Drop packets originated from this device
        if (packet.sourceDeviceId == localDeviceId) {
            countSelf.incrementAndGet()
            Log.d(TAG, "RELAY DROP SELF source=${packet.sourceDeviceId} seq=${packet.sequenceNumber}")
            return RelayAction.DropSelf
        }

        // 2. Duplicate suppression check
        val key = PacketKey(packet.sourceDeviceId, packet.sequenceNumber)
        val isDuplicate = synchronized(seenCache) {
            if (seenCache.containsKey(key)) {
                true
            } else {
                seenCache[key] = System.currentTimeMillis()
                false
            }
        }

        if (isDuplicate) {
            countDup.incrementAndGet()
            Log.i(TAG, "RELAY DROP DUPLICATE source=${packet.sourceDeviceId} seq=${packet.sequenceNumber}")
            return RelayAction.DropDuplicate
        }

        // 3. Valid fresh reception
        countRx.incrementAndGet()
        Log.i(TAG, "RELAY RX source=${packet.sourceDeviceId} seq=${packet.sequenceNumber} ttl=${packet.ttl} lang=${packet.language}")

        // 4. Relay forwarding decision
        if (!_isRelayEnabled.value) {
            return RelayAction.DeliverLocalOnly
        }

        val nextTtl = (packet.ttl - 1).toByte()
        return if (nextTtl > 0) {
            val forwardedFlags = (packet.flags.toInt() or Packet.FLAG_FORWARDED).toByte()
            val forwardedPacket = packet.copy(
                ttl = nextTtl,
                flags = forwardedFlags
            )
            countFwd.incrementAndGet()
            Log.i(TAG, "RELAY FORWARD nextHop=BROADCAST seq=${packet.sequenceNumber} ttl=$nextTtl")
            RelayAction.ForwardAndDeliver(forwardedPacket)
        } else {
            countTtlExpired.incrementAndGet()
            Log.i(TAG, "RELAY DROP TTL=0 source=${packet.sourceDeviceId} seq=${packet.sequenceNumber}")
            RelayAction.DeliverLocalOnly
        }
    }

    fun getStats(): RelayStats = RelayStats(
        packetsReceived = countRx.get(),
        packetsForwarded = countFwd.get(),
        duplicatesDropped = countDup.get(),
        ttlExpiredDropped = countTtlExpired.get(),
        selfPacketsDropped = countSelf.get()
    )

    fun clearCache() {
        synchronized(seenCache) {
            seenCache.clear()
        }
    }

    fun resetStats() {
        countRx.set(0)
        countFwd.set(0)
        countDup.set(0)
        countTtlExpired.set(0)
        countSelf.set(0)
    }
}
