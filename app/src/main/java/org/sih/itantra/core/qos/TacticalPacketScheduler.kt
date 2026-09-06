package org.sih.itantra.core.qos

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import org.sih.itantra.core.protocol.Packet
import java.util.ArrayDeque

/**
 * Congestion state classification based on outbound queue depth.
 */
enum class CongestionState(val label: String) {
    NORMAL("NORMAL"),
    BUSY("BUSY"),
    CONGESTED("CONGESTED")
}

/**
 * Metadata wrapper for a packet buffered in the transmission queue.
 */
data class QueuedPacket(
    val packet: Packet,
    val enqueuedAtMs: Long = System.currentTimeMillis(),
    val completionDeferred: CompletableDeferred<Boolean>? = null,
    val onDelivered: ((Boolean) -> Unit)? = null
)

/**
 * Priority-aware tactical transmission queue and scheduler for iTantra.
 *
 * Implements:
 * 1. Priority hierarchy: DISTRESS > ALERT > IMPORTANT > NORMAL.
 * 2. Bounded multi-queue capacity (max [maxCapacity] packets).
 * 3. Emergency pre-emption: DISTRESS and ALERT packets jump ahead of queued NORMAL packets.
 * 4. Atomic packet boundaries: in-flight physical transmissions run to completion before next dispatch.
 * 5. Bounded starvation protection: if oldest NORMAL packet waits >= [starvationThresholdMs],
 *    it is granted one transmission slot before returning to strict priority.
 * 6. Bounded congestion control: NORMAL (<25), BUSY (25-60), CONGESTED (>60).
 * 7. Overflow policy: emergency traffic evicts oldest NORMAL traffic if queue reaches capacity.
 */
class TacticalPacketScheduler(
    val maxCapacity: Int = MAX_OUTBOUND_QUEUE,
    val starvationThresholdMs: Long = DEFAULT_STARVATION_THRESHOLD_MS,
    val autoTransmit: Boolean = true,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val transmitter: suspend (Packet) -> Boolean = { true }
) {
    companion object {
        const val TAG = "TacticalQos"
        const val MAX_OUTBOUND_QUEUE = 100
        const val DEFAULT_STARVATION_THRESHOLD_MS = 5000L // 5 seconds

        const val CONGESTION_THRESHOLD_BUSY = 25
        const val CONGESTION_THRESHOLD_CONGESTED = 60
    }

    private val lock = Any()

    // 4 internal priority deques (FIFO within same priority)
    private val distressQueue = ArrayDeque<QueuedPacket>()
    private val alertQueue = ArrayDeque<QueuedPacket>()
    private val importantQueue = ArrayDeque<QueuedPacket>()
    private val normalQueue = ArrayDeque<QueuedPacket>()

    private var isWorkerRunning = false

    val totalQueuedPackets: Int
        get() = synchronized(lock) { totalSizeInternal() }

    val totalQueued: Int
        get() = totalQueuedPackets

    fun totalQueued(): Int = totalQueuedPackets

    val queuedDistress: Int
        get() = synchronized(lock) { distressQueue.size }

    val queuedAlert: Int
        get() = synchronized(lock) { alertQueue.size }

    val queuedImportant: Int
        get() = synchronized(lock) { importantQueue.size }

    val queuedNormal: Int
        get() = synchronized(lock) { normalQueue.size }

    /**
     * Enqueue a single packet for transmission.
     * Returns true if accepted, false if rejected due to queue capacity overflow.
     */
    fun enqueue(
        packet: Packet,
        enqueuedAtMs: Long = System.currentTimeMillis(),
        onDelivered: ((Boolean) -> Unit)? = null
    ): Boolean {
        val accepted = synchronized(lock) {
            enqueueInternal(QueuedPacket(packet, enqueuedAtMs, null, onDelivered), enqueuedAtMs)
        }
        if (accepted && autoTransmit) {
            triggerWorker()
        }
        return accepted
    }

    /**
     * Enqueue a batch of packets (e.g. fragments of a single message) atomically.
     * Preserves fragment order within the same priority level.
     */
    fun enqueueBatch(
        packets: List<Packet>,
        enqueuedAtMs: Long = System.currentTimeMillis(),
        onDelivered: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (packets.isEmpty()) return true
        var allAccepted = true
        synchronized(lock) {
            for (pkt in packets) {
                val ok = enqueueInternal(QueuedPacket(pkt, enqueuedAtMs, null, onDelivered), enqueuedAtMs)
                allAccepted = allAccepted && ok
            }
        }
        if (autoTransmit) {
            triggerWorker()
        }
        return allAccepted
    }

    /**
     * Suspendable send for a single packet.
     * Enqueues the packet and suspends until physical transmission completes.
     */
    suspend fun send(packet: Packet): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val enqueuedAtMs = System.currentTimeMillis()
        val accepted = synchronized(lock) {
            enqueueInternal(QueuedPacket(packet, enqueuedAtMs, deferred, null), enqueuedAtMs)
        }
        if (!accepted) {
            return false
        }
        triggerWorker()
        return deferred.await()
    }

    /**
     * Suspendable send for a batch of packets (e.g. fragments).
     * Enqueues all packets at once and suspends until all fragments complete transmission.
     */
    suspend fun sendBatch(packets: List<Packet>): Boolean {
        if (packets.isEmpty()) return true
        val deferreds = ArrayList<CompletableDeferred<Boolean>>(packets.size)
        val enqueuedAtMs = System.currentTimeMillis()

        synchronized(lock) {
            for (pkt in packets) {
                val def = CompletableDeferred<Boolean>()
                deferreds.add(def)
                val accepted = enqueueInternal(QueuedPacket(pkt, enqueuedAtMs, def, null), enqueuedAtMs)
                if (!accepted) {
                    def.complete(false)
                }
            }
        }

        triggerWorker()

        var allOk = true
        for (def in deferreds) {
            val res = def.await()
            allOk = allOk && res
        }
        return allOk
    }

    /**
     * Poll the next highest-priority packet to transmit according to tactical QoS rules.
     * Visible for testing and used internally by the transmitter worker.
     */
    fun pollNextPacket(nowMs: Long = System.currentTimeMillis()): Packet? = synchronized(lock) {
        val queued = pollNextPacketInternal(nowMs) ?: return null
        queued.packet
    }

    /**
     * Determine current congestion state based on total queue depth.
     */
    fun getCongestionState(): CongestionState = synchronized(lock) {
        val size = totalSizeInternal()
        when {
            size >= CONGESTION_THRESHOLD_CONGESTED -> CongestionState.CONGESTED
            size >= CONGESTION_THRESHOLD_BUSY -> CongestionState.BUSY
            else -> CongestionState.NORMAL
        }
    }

    /**
     * Returns the age in ms of the oldest queued packet across all priorities.
     */
    fun getOldestQueuedAgeMs(nowMs: Long = System.currentTimeMillis()): Long = synchronized(lock) {
        var oldestTime = Long.MAX_VALUE
        distressQueue.peekFirst()?.let { oldestTime = minOf(oldestTime, it.enqueuedAtMs) }
        alertQueue.peekFirst()?.let { oldestTime = minOf(oldestTime, it.enqueuedAtMs) }
        importantQueue.peekFirst()?.let { oldestTime = minOf(oldestTime, it.enqueuedAtMs) }
        normalQueue.peekFirst()?.let { oldestTime = minOf(oldestTime, it.enqueuedAtMs) }
        if (oldestTime == Long.MAX_VALUE) 0L else maxOf(0L, nowMs - oldestTime)
    }

    fun getQueueCounts(): Map<MessagePriority, Int> = synchronized(lock) {
        mapOf(
            MessagePriority.DISTRESS to distressQueue.size,
            MessagePriority.ALERT to alertQueue.size,
            MessagePriority.IMPORTANT to importantQueue.size,
            MessagePriority.NORMAL to normalQueue.size
        )
    }

    fun clear() = synchronized(lock) {
        distressQueue.clear()
        alertQueue.clear()
        importantQueue.clear()
        normalQueue.clear()
        syncDiagnosticsInternal(System.currentTimeMillis())
    }

    // -----------------------------------------------------------------------
    // Internal scheduling & queue management
    // -----------------------------------------------------------------------

    private fun totalSizeInternal(): Int {
        return distressQueue.size + alertQueue.size + importantQueue.size + normalQueue.size
    }

    private fun enqueueInternal(queued: QueuedPacket, nowMs: Long): Boolean {
        val currentSize = totalSizeInternal()

        // Check if queue has capacity
        if (currentSize < maxCapacity) {
            insertByPriority(queued)
            checkPreemption(queued.packet.priority)
            syncDiagnosticsInternal(nowMs)
            return true
        }

        // Queue is at capacity: execute overflow / eviction policy
        val priority = queued.packet.priority
        if (priority == MessagePriority.DISTRESS || priority == MessagePriority.ALERT) {
            // Emergency traffic: evict oldest NORMAL first, then IMPORTANT
            val evicted = when {
                normalQueue.isNotEmpty() -> normalQueue.pollFirst()
                importantQueue.isNotEmpty() -> importantQueue.pollFirst()
                priority == MessagePriority.DISTRESS && alertQueue.isNotEmpty() -> alertQueue.pollFirst()
                else -> null
            }

            if (evicted != null) {
                evicted.completionDeferred?.complete(false)
                evicted.onDelivered?.invoke(false)
                DiagnosticsRepository.recordQueueOverflow(
                    reason = "EVICTED_FOR_EMERGENCY",
                    evictedPriority = evicted.packet.priority
                )
                insertByPriority(queued)
                checkPreemption(priority)
                syncDiagnosticsInternal(nowMs)
                Log.w(TAG, "Queue capacity reached: evicted ${evicted.packet.priority} for incoming $priority")
                return true
            }

            // Cannot evict because entire queue is filled with equal or higher emergency traffic
            DiagnosticsRepository.recordQueueOverflow(
                reason = "EMERGENCY_QUEUE_SATURATED",
                evictedPriority = null
            )
            Log.e(TAG, "Queue full with emergency traffic: unable to accept $priority")
            return false
        } else if (priority == MessagePriority.IMPORTANT && normalQueue.isNotEmpty()) {
            val evicted = normalQueue.pollFirst()
            evicted?.completionDeferred?.complete(false)
            evicted?.onDelivered?.invoke(false)
            DiagnosticsRepository.recordQueueOverflow(
                reason = "EVICTED_FOR_IMPORTANT",
                evictedPriority = MessagePriority.NORMAL
            )
            insertByPriority(queued)
            syncDiagnosticsInternal(nowMs)
            return true
        } else {
            // NORMAL traffic dropped when queue is full
            DiagnosticsRepository.recordQueueOverflow(
                reason = "NORMAL_QUEUE_OVERFLOW_DROP",
                evictedPriority = priority
            )
            Log.w(TAG, "Queue full ($currentSize/$maxCapacity): dropped non-emergency packet $priority")
            return false
        }
    }

    private fun insertByPriority(queued: QueuedPacket) {
        when (queued.packet.priority) {
            MessagePriority.DISTRESS -> distressQueue.addLast(queued)
            MessagePriority.ALERT -> alertQueue.addLast(queued)
            MessagePriority.IMPORTANT -> importantQueue.addLast(queued)
            MessagePriority.NORMAL -> normalQueue.addLast(queued)
        }
    }

    private fun checkPreemption(enqueuedPriority: MessagePriority) {
        if (enqueuedPriority == MessagePriority.DISTRESS) {
            if (normalQueue.isNotEmpty() || importantQueue.isNotEmpty()) {
                DiagnosticsRepository.recordDistressPreemption()
            }
        }
    }

    private fun pollNextPacketInternal(nowMs: Long): QueuedPacket? {
        if (totalSizeInternal() == 0) return null

        // 1. Check Starvation Protection:
        // If oldest NORMAL packet has waited >= starvationThresholdMs and there is higher priority traffic,
        // allow this one packet through to preserve bounded fairness.
        val oldestNormal = normalQueue.peekFirst()
        if (oldestNormal != null) {
            val normalAge = nowMs - oldestNormal.enqueuedAtMs
            val hasHigherTraffic = distressQueue.isNotEmpty() || alertQueue.isNotEmpty() || importantQueue.isNotEmpty()
            if (normalAge >= starvationThresholdMs && hasHigherTraffic) {
                val rescued = normalQueue.pollFirst()
                DiagnosticsRepository.recordStarvationAvoidance()
                Log.i(TAG, "STARVATION RESCUE: Normal packet waited ${normalAge}ms (>= ${starvationThresholdMs}ms) — dispatched")
                syncDiagnosticsInternal(nowMs)
                return rescued
            }
        }

        // 2. Strict Priority Dispatch: DISTRESS > ALERT > IMPORTANT > NORMAL
        val selected = when {
            distressQueue.isNotEmpty() -> {
                if (normalQueue.isNotEmpty()) DiagnosticsRepository.recordNormalDeferral()
                distressQueue.pollFirst()
            }
            alertQueue.isNotEmpty() -> {
                if (normalQueue.isNotEmpty()) DiagnosticsRepository.recordNormalDeferral()
                alertQueue.pollFirst()
            }
            importantQueue.isNotEmpty() -> {
                importantQueue.pollFirst()
            }
            normalQueue.isNotEmpty() -> {
                normalQueue.pollFirst()
            }
            else -> null
        }

        syncDiagnosticsInternal(nowMs)
        return selected
    }

    private fun syncDiagnosticsInternal(nowMs: Long) {
        val total = totalSizeInternal()
        val cState = when {
            total >= CONGESTION_THRESHOLD_CONGESTED -> CongestionState.CONGESTED
            total >= CONGESTION_THRESHOLD_BUSY -> CongestionState.BUSY
            else -> CongestionState.NORMAL
        }

        var oldestTime = Long.MAX_VALUE
        distressQueue.peekFirst()?.let { oldestTime = minOf(oldestTime, it.enqueuedAtMs) }
        alertQueue.peekFirst()?.let { oldestTime = minOf(oldestTime, it.enqueuedAtMs) }
        importantQueue.peekFirst()?.let { oldestTime = minOf(oldestTime, it.enqueuedAtMs) }
        normalQueue.peekFirst()?.let { oldestTime = minOf(oldestTime, it.enqueuedAtMs) }
        val oldestAge = if (oldestTime == Long.MAX_VALUE) 0L else maxOf(0L, nowMs - oldestTime)

        DiagnosticsRepository.updateQosQueueState(
            queuedTotal = total,
            qDistress = distressQueue.size,
            qAlert = alertQueue.size,
            qImportant = importantQueue.size,
            qNormal = normalQueue.size,
            congestionState = cState.label,
            oldestAgeMs = oldestAge
        )
    }

    private fun triggerWorker() {
        synchronized(lock) {
            if (isWorkerRunning) return
            isWorkerRunning = true
        }

        scope.launch {
            try {
                while (true) {
                    val next = synchronized(lock) {
                        pollNextPacketInternal(System.currentTimeMillis())
                    } ?: break

                    var success = false
                    try {
                        // Atomic physical packet transmission boundary
                        success = transmitter(next.packet)
                    } catch (e: Exception) {
                        Log.e(TAG, "Transmission exception for ${next.packet.priority}: ${e.message}", e)
                        success = false
                    }

                    next.completionDeferred?.complete(success)
                    next.onDelivered?.invoke(success)
                }
            } finally {
                synchronized(lock) {
                    isWorkerRunning = false
                    // If items were enqueued while exiting, re-trigger
                    if (totalSizeInternal() > 0) {
                        triggerWorker()
                    }
                }
            }
        }
    }
}
