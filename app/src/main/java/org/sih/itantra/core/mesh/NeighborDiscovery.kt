package org.sih.itantra.core.mesh

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.transport.TransportManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages periodic HELLO broadcast and incoming HELLO processing.
 *
 * - Every [helloIntervalMs] milliseconds, broadcasts a HELLO packet to all neighbors.
 * - When a HELLO is received, upserts the sender into [neighborTable].
 * - Exposes [neighborCount] as a live [StateFlow] for UI consumption.
 *
 * Thread-safe. Designed to run on an IO [CoroutineScope].
 */
class NeighborDiscovery(
    private val context: Context,
    private val localNodeId: Int,
    private val neighborTable: NeighborTable,
    private val transportManager: TransportManager,
    private val scope: CoroutineScope,
    val helloIntervalMs: Long = HELLO_INTERVAL_MS,
    private val onHelloTx: () -> Unit = {},
    private val onHelloRx: () -> Unit = {},
    private val onNeighborDiscovered: (NeighborEntry) -> Unit = {}
) {
    companion object {
        private const val TAG = "NeighborDiscovery"
        const val HELLO_INTERVAL_MS = 15_000L
    }

    private val _neighborCount = MutableStateFlow(0)
    val neighborCount: StateFlow<Int> = _neighborCount.asStateFlow()

    private val helloSeq = AtomicInteger(0)
    private var helloJob: Job? = null

    /** Start periodic HELLO broadcasts. Idempotent. */
    fun start() {
        if (helloJob?.isActive == true) return
        helloJob = scope.launch {
            while (isActive) {
                broadcastHello()
                delay(helloIntervalMs)
            }
        }
        Log.i(TAG, "NeighborDiscovery started (nodeId=$localNodeId, interval=${helloIntervalMs}ms)")
    }

    /** Stop periodic HELLO broadcasts. */
    fun stop() {
        helloJob?.cancel()
        helloJob = null
        Log.i(TAG, "NeighborDiscovery stopped")
    }

    /**
     * Called by ManetRouter when a TYPE_HELLO packet arrives.
     * Parses the HELLO payload, upserts neighbor, updates count.
     */
    fun handleHello(packet: Packet, transport: String = "UNKNOWN") {
        val hello = HelloPayload.deserialize(packet.payload) ?: run {
            Log.w(TAG, "Malformed HELLO from sourceId=${packet.sourceDeviceId}, ignoring")
            return
        }

        if (hello.nodeId == localNodeId) {
            Log.d(TAG, "Discarding own HELLO echo (nodeId=$localNodeId)")
            return
        }

        val entry = NeighborEntry(
            nodeId      = hello.nodeId,
            transport   = transport,
            lastSeenMs  = System.currentTimeMillis(),
            batteryPct  = hello.batteryPct.toInt().coerceIn(0, 100),
            seqNum      = hello.seqNum.toInt()
        )

        val isNew = neighborTable.lookup(hello.nodeId) == null
        neighborTable.upsert(entry)
        _neighborCount.value = neighborTable.liveCount()

        if (isNew) {
            Log.i(TAG, "NEIGHBOR DISCOVERED nodeId=${hello.nodeId} transport=$transport battery=${entry.batteryPct}%")
            onNeighborDiscovered(entry)
        } else {
            Log.d(TAG, "NEIGHBOR REFRESHED nodeId=${hello.nodeId} seq=${hello.seqNum}")
        }
        onHelloRx()
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private suspend fun broadcastHello() {
        val seq = helloSeq.getAndIncrement().toShort()
        val battery = readBatteryPct()
        val payload = HelloPayload(
            nodeId     = localNodeId,
            seqNum     = seq,
            batteryPct = battery.toByte()
        ).serialize()

        val packet = Packet(
            msgType          = Packet.TYPE_HELLO,
            priority         = MessagePriority.NORMAL,
            sequenceNumber   = seq,
            timestamp        = System.currentTimeMillis(),
            sourceDeviceId   = localNodeId,
            destinationDeviceId = Packet.BROADCAST_ID,
            language         = IndicLanguage.ENGLISH,   // language field unused for control packets
            payload          = payload
        )

        try {
            transportManager.send(packet)
            _neighborCount.value = neighborTable.liveCount()
            Log.d(TAG, "HELLO TX seq=$seq battery=$battery% neighbors=${_neighborCount.value}")
            onHelloTx()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast HELLO: ${e.message}")
        }
    }

    private fun readBatteryPct(): Int {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 50
        } catch (_: Exception) {
            50
        }
    }
}
