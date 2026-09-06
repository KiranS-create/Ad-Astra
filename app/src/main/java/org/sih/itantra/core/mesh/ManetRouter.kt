package org.sih.itantra.core.mesh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.transport.TransportManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// ---------------------------------------------------------------------------
// Diagnostics counters snapshot
// ---------------------------------------------------------------------------

data class ManetStats(
    val helloTx: Long = 0L,
    val helloRx: Long = 0L,
    val neighborsDiscovered: Long = 0L,
    val rreqTx: Long = 0L,
    val rreqRx: Long = 0L,
    val rrepTx: Long = 0L,
    val rrepRx: Long = 0L,
    val routesEstablished: Long = 0L,
    val routesExpired: Long = 0L,
    val rerrCount: Long = 0L,
    val packetsRouted: Long = 0L,
    val routeRediscoveries: Long = 0L
)

// ---------------------------------------------------------------------------
// Pending packet queue entry
// ---------------------------------------------------------------------------

private data class PendingPacket(
    val packet: Packet,
    val enqueuedMs: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------------------
// ManetRouter
// ---------------------------------------------------------------------------

/**
 * Lightweight offline MANET-oriented routing coordinator.
 *
 * Responsibilities:
 * 1. Manage [NeighborDiscovery] (HELLO beacons, neighbor table).
 * 2. Maintain a [RouteTable] of known routes.
 * 3. On [routeAndSend]: unicast via known next hop, or trigger RREQ and queue.
 * 4. Handle incoming control packets (HELLO / RREQ / RREP / RERR).
 * 5. On route failure: mark INVALID, send RERR, trigger rediscovery.
 *
 * DATA and ALERT packets are routed. Control packets (HELLO/RREQ/RREP/RERR)
 * are never queued — they are always broadcast or unicast immediately.
 *
 * Safety nets:
 * - TTL decrement and duplicate suppression remain in [PacketRelayRouter] below this layer.
 * - Pending queue: max [MAX_PENDING_PER_DEST] packets per destination, [PENDING_TTL_MS] timeout.
 */
class ManetRouter(
    private val context: Context,
    val localNodeId: Int,
    private val transportManager: TransportManager,
    val relayRouter: PacketRelayRouter,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "ManetRouter"
        private const val MAX_PENDING_PER_DEST = 4
        private const val PENDING_TTL_MS = 10_000L
        private const val PRUNE_INTERVAL_MS = 60_000L
    }

    // Sub-components
    val neighborTable = NeighborTable()
    val routeTable    = RouteTable()
    val dupCache      = RreqDupCache()

    val neighborDiscovery = NeighborDiscovery(
        context           = context,
        localNodeId       = localNodeId,
        neighborTable     = neighborTable,
        transportManager  = transportManager,
        scope             = scope,
        onHelloTx         = { cHelloTx.incrementAndGet() },
        onHelloRx         = { cHelloRx.incrementAndGet() },
        onNeighborDiscovered = { cNeighborsDiscovered.incrementAndGet() }
    )

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    val neighborCount: StateFlow<Int> = neighborDiscovery.neighborCount

    private val _routeCount = MutableStateFlow(0)
    val routeCount: StateFlow<Int> = _routeCount.asStateFlow()

    // Pending queue: destNodeId → bounded deque of packets awaiting route
    private val pendingQueues = ConcurrentHashMap<Int, LinkedBlockingDeque<PendingPacket>>()

    // RREQ sequence number per (local origin) — monotonically increasing
    private val rreqCounter = AtomicInteger(0)
    private val localSeqNum = AtomicInteger(0)

    // Diagnostics counters
    private val cHelloTx             = AtomicLong(0)
    private val cHelloRx             = AtomicLong(0)
    private val cNeighborsDiscovered  = AtomicLong(0)
    private val cRreqTx              = AtomicLong(0)
    private val cRreqRx              = AtomicLong(0)
    private val cRrepTx              = AtomicLong(0)
    private val cRrepRx              = AtomicLong(0)
    private val cRoutesEstablished   = AtomicLong(0)
    private val cRoutesExpired       = AtomicLong(0)
    private val cRerrCount           = AtomicLong(0)
    private val cPacketsRouted       = AtomicLong(0)
    private val cRouteRediscoveries  = AtomicLong(0)

    private var pruneJob: Job? = null

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (enabled) {
            neighborDiscovery.start()
            startPruneJob()
            Log.i(TAG, "MANET routing ENABLED (nodeId=$localNodeId)")
        } else {
            neighborDiscovery.stop()
            pruneJob?.cancel()
            Log.i(TAG, "MANET routing DISABLED")
        }
    }

    fun stop() {
        neighborDiscovery.stop()
        pruneJob?.cancel()
        pendingQueues.clear()
    }

    // -----------------------------------------------------------------------
    // Data packet routing
    // -----------------------------------------------------------------------

    /**
     * Route a DATA/ALERT packet.
     *
     * If MANET is disabled → fall back to broadcast via existing transport.
     * If a valid route exists → unicast to next hop.
     * Else → trigger RREQ discovery and queue the packet briefly.
     */
    suspend fun routeAndSend(packet: Packet): Boolean {
        if (!_isEnabled.value) {
            // MANET disabled: delegate to broadcast
            return transportManager.send(packet)
        }

        val destId = packet.destinationDeviceId
        if (destId == Packet.BROADCAST_ID) {
            // Broadcast packets bypass routing — send directly
            return transportManager.send(packet)
        }

        val route = routeTable.lookup(destId)
        return if (route != null) {
            sendViaNextHop(packet, route.nextHopNodeId)
        } else {
            Log.i(TAG, "No route to $destId — queueing packet and triggering RREQ")
            enqueuePacket(destId, packet)
            triggerRreq(destId)
            true // packet accepted into queue; delivery asynchronous
        }
    }

    // -----------------------------------------------------------------------
    // Control packet dispatch
    // -----------------------------------------------------------------------

    /**
     * Dispatch an incoming control packet to the appropriate handler.
     * Returns true if this was a control packet (caller should NOT also deliver as data).
     */
    fun handleControlPacket(packet: Packet, transport: String = "UNKNOWN"): Boolean {
        return when (packet.msgType) {
            Packet.TYPE_HELLO         -> { neighborDiscovery.handleHello(packet, transport); true }
            Packet.TYPE_ROUTE_REQUEST -> { handleRreq(packet); true }
            Packet.TYPE_ROUTE_REPLY   -> { handleRrep(packet); true }
            Packet.TYPE_ROUTE_ERROR   -> { handleRerr(packet); true }
            else                      -> false
        }
    }

    // -----------------------------------------------------------------------
    // RREQ handling
    // -----------------------------------------------------------------------

    private fun triggerRreq(destNodeId: Int) {
        val reqId  = rreqCounter.getAndIncrement().toShort()
        val seqNum = localSeqNum.incrementAndGet().toShort()
        val payload = RreqPayload(
            originNodeId = localNodeId,
            destNodeId   = destNodeId,
            originSeqNum = seqNum,
            reqId        = reqId,
            hopCount     = 0,
            ttl          = Packet.DEFAULT_TTL
        ).serialize()

        val rreqPacket = buildControlPacket(Packet.TYPE_ROUTE_REQUEST, payload)
        scope.launch {
            transportManager.send(rreqPacket)
            cRreqTx.incrementAndGet()
            Log.i(TAG, "RREQ TX dest=$destNodeId reqId=$reqId")
        }
    }

    private fun handleRreq(packet: Packet) {
        val rreq = RreqPayload.deserialize(packet.payload) ?: run {
            Log.w(TAG, "Malformed RREQ from ${packet.sourceDeviceId}")
            return
        }
        cRreqRx.incrementAndGet()

        // Dup suppression
        val key = RreqKey(rreq.originNodeId, rreq.reqId)
        if (dupCache.isDuplicate(key)) {
            Log.d(TAG, "RREQ DUP origin=${rreq.originNodeId} reqId=${rreq.reqId} — dropped")
            return
        }

        Log.i(TAG, "RREQ RX origin=${rreq.originNodeId} dest=${rreq.destNodeId} hops=${rreq.hopCount}")

        // Create reverse route toward origin (via the node that sent us this RREQ)
        routeTable.addOrUpdate(RouteEntry(
            destinationNodeId = rreq.originNodeId,
            nextHopNodeId     = packet.sourceDeviceId,
            hopCount          = rreq.hopCount + 1,
            routeSeqNum       = rreq.originSeqNum.toInt(),
            expiryMs          = System.currentTimeMillis() + RouteTable.ROUTE_LIFETIME_MS
        ))
        updateRouteCount()

        if (rreq.destNodeId == localNodeId) {
            // We are the destination — send RREP back toward origin
            sendRrep(originNodeId = rreq.originNodeId, originNextHop = packet.sourceDeviceId)
        } else {
            // Check if we have a fresh route to destination (can answer on destination's behalf)
            val existingRoute = routeTable.lookup(rreq.destNodeId)
            if (existingRoute != null && existingRoute.routeSeqNum > rreq.originSeqNum) {
                // Answer with intermediate RREP
                sendRrepIntermediate(rreq, existingRoute, packet.sourceDeviceId)
            } else {
                // Forward RREQ if TTL allows
                val nextTtl = (rreq.ttl - 1).toByte()
                if (nextTtl > 0) {
                    val forwarded = RreqPayload(
                        originNodeId = rreq.originNodeId,
                        destNodeId   = rreq.destNodeId,
                        originSeqNum = rreq.originSeqNum,
                        reqId        = rreq.reqId,
                        hopCount     = (rreq.hopCount + 1).toByte(),
                        ttl          = nextTtl
                    ).serialize()
                    scope.launch {
                        transportManager.send(buildControlPacket(Packet.TYPE_ROUTE_REQUEST, forwarded))
                        cRreqTx.incrementAndGet()
                        Log.d(TAG, "RREQ FWD origin=${rreq.originNodeId} dest=${rreq.destNodeId} ttl=$nextTtl")
                    }
                } else {
                    Log.d(TAG, "RREQ TTL=0 for dest=${rreq.destNodeId} — not forwarded")
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // RREP handling
    // -----------------------------------------------------------------------

    private fun sendRrep(originNodeId: Int, originNextHop: Int) {
        val destSeqNum = localSeqNum.incrementAndGet().toShort()
        val payload = RrepPayload(
            originNodeId = originNodeId,
            destNodeId   = localNodeId,
            destSeqNum   = destSeqNum,
            hopCount     = 0
        ).serialize()

        val rrepPacket = buildUnicastControlPacket(Packet.TYPE_ROUTE_REPLY, payload, originNextHop)
        scope.launch {
            transportManager.send(rrepPacket)
            cRrepTx.incrementAndGet()
            Log.i(TAG, "RREP TX to origin=$originNodeId via nextHop=$originNextHop")
        }
    }

    private fun sendRrepIntermediate(rreq: RreqPayload, existingRoute: RouteEntry, backToHop: Int) {
        val payload = RrepPayload(
            originNodeId = rreq.originNodeId,
            destNodeId   = rreq.destNodeId,
            destSeqNum   = existingRoute.routeSeqNum.toShort(),
            hopCount     = (existingRoute.hopCount + 1).toByte()
        ).serialize()

        val rrepPacket = buildUnicastControlPacket(Packet.TYPE_ROUTE_REPLY, payload, backToHop)
        scope.launch {
            transportManager.send(rrepPacket)
            cRrepTx.incrementAndGet()
            Log.i(TAG, "RREP INTERMEDIATE dest=${rreq.destNodeId} back to $backToHop")
        }
    }

    private fun handleRrep(packet: Packet) {
        val rrep = RrepPayload.deserialize(packet.payload) ?: run {
            Log.w(TAG, "Malformed RREP from ${packet.sourceDeviceId}")
            return
        }
        cRrepRx.incrementAndGet()

        Log.i(TAG, "RREP RX dest=${rrep.destNodeId} origin=${rrep.originNodeId} hops=${rrep.hopCount}")

        // Install forward route to destination
        routeTable.addOrUpdate(RouteEntry(
            destinationNodeId = rrep.destNodeId,
            nextHopNodeId     = packet.sourceDeviceId,
            hopCount          = rrep.hopCount + 1,
            routeSeqNum       = rrep.destSeqNum.toInt(),
            expiryMs          = System.currentTimeMillis() + RouteTable.ROUTE_LIFETIME_MS
        ))
        cRoutesEstablished.incrementAndGet()
        updateRouteCount()

        Log.i(TAG, "ROUTE ESTABLISHED dest=${rrep.destNodeId} nextHop=${packet.sourceDeviceId} hops=${rrep.hopCount + 1}")

        if (rrep.originNodeId == localNodeId) {
            // We are the origin — flush queued packets to this destination
            flushPendingQueue(rrep.destNodeId, packet.sourceDeviceId)
        } else {
            // Intermediate node — forward RREP toward origin
            val originRoute = routeTable.lookup(rrep.originNodeId)
            if (originRoute != null) {
                val forwarded = RrepPayload(
                    originNodeId = rrep.originNodeId,
                    destNodeId   = rrep.destNodeId,
                    destSeqNum   = rrep.destSeqNum,
                    hopCount     = (rrep.hopCount + 1).toByte()
                ).serialize()
                val forwardPacket = buildUnicastControlPacket(Packet.TYPE_ROUTE_REPLY, forwarded, originRoute.nextHopNodeId)
                scope.launch {
                    transportManager.send(forwardPacket)
                    cRrepTx.incrementAndGet()
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // RERR handling
    // -----------------------------------------------------------------------

    fun handleNextHopFailure(failedNextHopId: Int, affectedDest: Int) {
        Log.w(TAG, "NEXT-HOP FAILURE nextHop=$failedNextHopId affectedDest=$affectedDest")
        routeTable.invalidate(affectedDest)
        cRoutesExpired.incrementAndGet()
        updateRouteCount()
        sendRerr(brokenDest = affectedDest, affectedSrc = localNodeId)
        // Re-queue and trigger fresh RREQ for the affected destination
        scope.launch {
            cRouteRediscoveries.incrementAndGet()
            triggerRreq(affectedDest)
        }
    }

    private fun sendRerr(brokenDest: Int, affectedSrc: Int) {
        val payload = RerrPayload(brokenDest, affectedSrc).serialize()
        val rerr = buildControlPacket(Packet.TYPE_ROUTE_ERROR, payload)
        scope.launch {
            transportManager.send(rerr)
            cRerrCount.incrementAndGet()
            Log.i(TAG, "RERR TX brokenDest=$brokenDest affectedSrc=$affectedSrc")
        }
    }

    private fun handleRerr(packet: Packet) {
        val rerr = RerrPayload.deserialize(packet.payload) ?: return
        Log.w(TAG, "RERR RX brokenDest=${rerr.brokenDestNodeId} affectedSrc=${rerr.affectedSrcNodeId}")
        routeTable.invalidate(rerr.brokenDestNodeId)
        cRoutesExpired.incrementAndGet()
        cRerrCount.incrementAndGet()
        updateRouteCount()
        // If we have packets queued for this destination, trigger rediscovery
        if (pendingQueues.containsKey(rerr.brokenDestNodeId)) {
            cRouteRediscoveries.incrementAndGet()
            triggerRreq(rerr.brokenDestNodeId)
        }
    }

    // -----------------------------------------------------------------------
    // Pending packet queue
    // -----------------------------------------------------------------------

    private fun enqueuePacket(destId: Int, packet: Packet) {
        val queue = pendingQueues.getOrPut(destId) { LinkedBlockingDeque(MAX_PENDING_PER_DEST) }
        val accepted = queue.offerLast(PendingPacket(packet))
        if (!accepted) {
            Log.w(TAG, "Pending queue full for dest=$destId — oldest packet dropped")
            queue.pollFirst()
            queue.offerLast(PendingPacket(packet))
        }
    }

    private fun flushPendingQueue(destId: Int, nextHopId: Int) {
        val queue = pendingQueues.remove(destId) ?: return
        val now = System.currentTimeMillis()
        scope.launch {
            while (true) {
                val pending = queue.pollFirst() ?: break
                if (now - pending.enqueuedMs > PENDING_TTL_MS) {
                    Log.w(TAG, "Pending packet for dest=$destId expired in queue — dropped")
                    continue
                }
                sendViaNextHop(pending.packet, nextHopId)
                cPacketsRouted.incrementAndGet()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private suspend fun sendViaNextHop(packet: Packet, nextHopId: Int): Boolean {
        // Re-address the packet to the next hop node ID as the destination field.
        // The transport layer uses the active transport's own addressing (BT MAC / UDP).
        // At the next hop, ManetRouter reads the original payload's final destination.
        val routed = packet.copy(destinationDeviceId = nextHopId)
        cPacketsRouted.incrementAndGet()
        Log.d(TAG, "DATA ROUTED dest=${packet.destinationDeviceId} via nextHop=$nextHopId")
        return transportManager.send(routed)
    }

    private fun buildControlPacket(msgType: Byte, payload: ByteArray): Packet = Packet(
        msgType             = msgType,
        priority            = MessagePriority.NORMAL,
        sequenceNumber      = rreqCounter.getAndIncrement().toShort(),
        timestamp           = System.currentTimeMillis(),
        sourceDeviceId      = localNodeId,
        destinationDeviceId = Packet.BROADCAST_ID,
        language            = IndicLanguage.ENGLISH,
        payload             = payload
    )

    private fun buildUnicastControlPacket(msgType: Byte, payload: ByteArray, destId: Int): Packet = Packet(
        msgType             = msgType,
        priority            = MessagePriority.NORMAL,
        sequenceNumber      = rreqCounter.getAndIncrement().toShort(),
        timestamp           = System.currentTimeMillis(),
        sourceDeviceId      = localNodeId,
        destinationDeviceId = destId,
        language            = IndicLanguage.ENGLISH,
        payload             = payload
    )

    private fun updateRouteCount() {
        _routeCount.value = routeTable.validCount()
    }

    private fun startPruneJob() {
        pruneJob?.cancel()
        pruneJob = scope.launch {
            while (true) {
                delay(PRUNE_INTERVAL_MS)
                routeTable.pruneExpired()
                updateRouteCount()
                // Expire stale pending queues
                val now = System.currentTimeMillis()
                pendingQueues.entries.removeIf { (destId, queue) ->
                    val sizeBefore = queue.size
                    queue.removeIf { now - it.enqueuedMs > PENDING_TTL_MS }
                    if (queue.isEmpty()) {
                        Log.d(TAG, "Pending queue for dest=$destId fully expired")
                        true
                    } else {
                        if (queue.size < sizeBefore) Log.d(TAG, "Pruned ${sizeBefore - queue.size} expired pending for dest=$destId")
                        false
                    }
                }
            }
        }
    }

    fun getStats(): ManetStats = ManetStats(
        helloTx              = cHelloTx.get(),
        helloRx              = cHelloRx.get(),
        neighborsDiscovered  = cNeighborsDiscovered.get(),
        rreqTx               = cRreqTx.get(),
        rreqRx               = cRreqRx.get(),
        rrepTx               = cRrepTx.get(),
        rrepRx               = cRrepRx.get(),
        routesEstablished    = cRoutesEstablished.get(),
        routesExpired        = cRoutesExpired.get(),
        rerrCount            = cRerrCount.get(),
        packetsRouted        = cPacketsRouted.get(),
        routeRediscoveries   = cRouteRediscoveries.get()
    )
}
