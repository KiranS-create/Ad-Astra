package org.sih.itantra.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.sih.itantra.core.mesh.ManetRouter
import org.sih.itantra.core.mesh.PacketRelayRouter
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.transport.TransportManager
import org.sih.itantra.presentation.MainActivity

/**
 * Persistent MANET node foreground service.
 *
 * Foreground service type: `dataSync`
 * — Selected because this service relays compact binary packets between
 *   peer devices over Bluetooth RFCOMM and Wi-Fi UDP sockets.
 *   This matches Android's documented dataSync use-case:
 *   "data syncing, file transfers, or any network communication to or from
 *   a remote server or peer device."
 *
 * Lifecycle:
 * - Started by user action (explicit startForegroundService call from MainActivity).
 * - START_STICKY: restarted by OS if killed under memory pressure.
 * - stopWithTask=false: survives MainActivity destruction.
 * - Stopped cleanly when user disables Node Mode.
 *
 * Architecture:
 * - Owns its own [TransportManager] and [ManetRouter] for the relay/routing loop.
 * - UI layer binds via [LocalBinder] and observes [serviceState] StateFlow.
 * - When unbound, the service continues fully independently.
 *
 * Battery / Doze:
 * - HELLO beacons via coroutine delay (15 s) — no AlarmManager, no wake locks.
 * - Foreground services are exempt from most Doze restrictions.
 * - OEM power managers (Samsung, Xiaomi) may impose additional limits.
 *   Users should add iTantra to "Unrestricted" battery mode for reliable
 *   background operation.
 */
class ManetNodeService : LifecycleService() {

    companion object {
        private const val TAG = "ManetNodeService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "manet_node"
        const val ACTION_DISABLE_NODE = "org.sih.itantra.ACTION_DISABLE_NODE"
        const val ACTION_OPEN_APP     = "org.sih.itantra.ACTION_OPEN_APP"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 30_000L
    }

    // -------------------------------------------------------------------------
    // Binder — exposes this service to the ViewModel
    // -------------------------------------------------------------------------

    inner class LocalBinder : Binder() {
        fun getService(): ManetNodeService = this@ManetNodeService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    // -------------------------------------------------------------------------
    // Service state (observed by ViewModel when bound)
    // -------------------------------------------------------------------------

    data class ManetServiceState(
        val isRunning: Boolean = false,
        val localNodeId: Int = 0,
        val neighborCount: Int = 0,
        val routeCount: Int = 0,
        val packetsRelayed: Long = 0L
    )

    private val _serviceState = MutableStateFlow(ManetServiceState())
    val serviceState: StateFlow<ManetServiceState> = _serviceState.asStateFlow()

    // -------------------------------------------------------------------------
    // Core components — owned by the service, NOT the ViewModel
    // -------------------------------------------------------------------------

    lateinit var transportManager: TransportManager
        private set

    lateinit var relayRouter: PacketRelayRouter
        private set

    lateinit var manetRouter: ManetRouter
        private set

    private var localNodeId: Int = 0

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ManetNodeService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_DISABLE_NODE -> {
                Log.i(TAG, "Disable Node action received from notification")
                stopNodeMode()
                return START_NOT_STICKY
            }
            ACTION_OPEN_APP -> {
                // Notification open — nothing to do in service; MainActivity handles it
            }
        }

        if (!::transportManager.isInitialized) {
            initializeNode()
        }

        // START_STICKY: OS will restart this service if it is killed
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "ManetNodeService destroyed — cleaning up")
        if (::manetRouter.isInitialized) {
            manetRouter.stop()
        }
        if (::transportManager.isInitialized) {
            lifecycleScope.launch { transportManager.stop() }
        }
        ManetNodePreference.setNodeModeEnabled(this, false)
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Node initialization
    // -------------------------------------------------------------------------

    private fun initializeNode() {
        // Stable node ID: reuse persisted ID or generate a new one
        localNodeId = ManetNodePreference.lastNodeId(this).let { stored ->
            if (stored != 0) stored
            else {
                val generated = (Math.random() * 900_000 + 100_000).toInt()
                ManetNodePreference.setLastNodeId(this, generated)
                generated
            }
        }

        transportManager = TransportManager(applicationContext)
        relayRouter      = PacketRelayRouter(localNodeId)
        relayRouter.setRelayEnabled(true)

        manetRouter = ManetRouter(
            context          = applicationContext,
            localNodeId      = localNodeId,
            transportManager = transportManager,
            relayRouter      = relayRouter,
            scope            = lifecycleScope
        )
        manetRouter.setEnabled(true)

        // Start listening on active transport
        lifecycleScope.launch {
            transportManager.start()
        }

        // Receive and dispatch packets
        lifecycleScope.launch {
            transportManager.receivedPackets.collect { packet ->
                dispatchIncomingPacket(packet)
            }
        }

        // Persist node mode enabled
        ManetNodePreference.setNodeModeEnabled(this, true)

        // Publish initial notification (before any async work — required by Android)
        startForeground(NOTIFICATION_ID, buildNotification(0, 0, 0L))

        // Start live state updates for notification and binder observers
        observeAndPublishState()

        _serviceState.value = ManetServiceState(
            isRunning    = true,
            localNodeId  = localNodeId,
            neighborCount = 0,
            routeCount    = 0,
            packetsRelayed = 0L
        )

        Log.i(TAG, "MANET Node initialized (nodeId=$localNodeId)")
    }

    // -------------------------------------------------------------------------
    // Packet dispatch
    // -------------------------------------------------------------------------

    /**
     * Classify and handle incoming packets.
     *
     * Control packets (HELLO/RREQ/RREP/RERR) are routed to ManetRouter.
     * Data packets (TEXT/ALERT/DISTRESS) pass through the relay router —
     * they are forwarded without TTS re-synthesis.
     *
     * NOTE: This service does NOT perform TTS or STT. It only relays compact
     * binary payloads. Voice synthesis is the UI transceiver's responsibility.
     */
    private suspend fun dispatchIncomingPacket(packet: Packet) {
        // 1. Let ManetRouter handle all MANET control packets first
        val isControl = manetRouter.handleControlPacket(packet)
        if (isControl) return

        // 2. DATA / ALERT / DISTRESS — run through relay router (TTL + dup suppression)
        val relayAction = relayRouter.evaluatePacket(packet)
        when (relayAction) {
            is org.sih.itantra.core.mesh.RelayAction.DropSelf,
            is org.sih.itantra.core.mesh.RelayAction.DropDuplicate -> return

            is org.sih.itantra.core.mesh.RelayAction.ForwardAndDeliver -> {
                // Forward to next hop via routing layer
                lifecycleScope.launch {
                    manetRouter.routeAndSend(relayAction.forwardedPacket)
                }
            }

            is org.sih.itantra.core.mesh.RelayAction.DeliverLocalOnly -> {
                // Packet reached this node as final hop — no TTS in service
                Log.d(TAG, "DATA delivered to node (no TTS in service) src=${packet.sourceDeviceId}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Clean stop
    // -------------------------------------------------------------------------

    fun stopNodeMode() {
        Log.i(TAG, "stopNodeMode() called")
        ManetNodePreference.setNodeModeEnabled(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // -------------------------------------------------------------------------
    // State observation → notification updates
    // -------------------------------------------------------------------------

    private fun observeAndPublishState() {
        lifecycleScope.launch {
            combine(
                manetRouter.neighborCount,
                manetRouter.routeCount
            ) { neighbors, routes -> Pair(neighbors, routes) }
                .collectLatest { (neighbors, routes) ->
                    val relayed = relayRouter.getStats().packetsForwarded
                    _serviceState.value = _serviceState.value.copy(
                        neighborCount  = neighbors,
                        routeCount     = routes,
                        packetsRelayed = relayed
                    )
                }
        }

        // Update notification on a slow timer — avoids excessive system calls
        lifecycleScope.launch {
            while (isActive) {
                delay(NOTIFICATION_UPDATE_INTERVAL_MS)
                val s = _serviceState.value
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(s.neighborCount, s.routeCount, s.packetsRelayed))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MANET Node",
            NotificationManager.IMPORTANCE_LOW   // Silent — no sound/vibration
        ).apply {
            description = "iTantra MANET relay node operating in background"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(
        neighbors: Int,
        routes: Int,
        relayed: Long
    ): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_APP
                flags  = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disableIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ManetNodeService::class.java).apply { action = ACTION_DISABLE_NODE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)  // replaced by app icon in production
            .setContentTitle("iTantra MANET NODE")
            .setContentText("ONLINE · Neighbors: $neighbors · Routes: $routes · Relayed: $relayed")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("ONLINE\nNeighbors: $neighbors · Routes: $routes · Relayed: $relayed")
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Disable Node",
                disableIntent
            )
            .addAction(
                android.R.drawable.ic_menu_share,
                "Open iTantra",
                openIntent
            )
            .build()
    }
}
