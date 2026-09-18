package org.sih.itantra.core.transport

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Required Wi-Fi Direct operational states.
 */
enum class WifiDirectState {
    UNAVAILABLE,
    DISABLED,
    PERMISSION_REQUIRED,
    DISCOVERING,
    PEERS_FOUND,
    CONNECTING,
    CONNECTED,
    FAILED,
    DISCONNECTED
}

/**
 * Metadata for a discovered authentic iTantra Wi-Fi Direct peer.
 */
data class WifiDirectDiscoveredPeer(
    val nodeId: Int,
    val callsign: String,
    val displayName: String,
    val supportedLanguages: List<String>,
    val protocolVersion: String,
    val capabilities: List<String>,
    val deviceAddress: String,
    val deviceName: String,
    val lastSeenMs: Long = System.currentTimeMillis()
)

/**
 * Pure routerless Android Wi-Fi Direct (Wi-Fi P2P) transport for iTantra.
 *
 * Requirements satisfied:
 * 1. Routerless / Off-grid: Direct phone-to-phone Wi-Fi P2P without router, phone hotspot, or internet.
 * 2. Identity: Uses canonical integer `nodeId` as identity. Never uses MAC address as application identity.
 * 3. Native DNS-SD: Advertises and discovers `_itantra._tcp` with TXT records (nodeId, callsign, displayName, etc.).
 * 4. Dedicated TCP Socket: Port 42889 with 4-byte big-endian length-prefixed framing.
 * 5. Lifecycle: Handles discovery, connection, rejection, timeout, disconnect, peer disappearance, GO changes.
 * 6. Truthful Telemetry: Emits WIFI_DIRECT_* events to DiagnosticsRepository.
 */
class WifiDirectTransport(
    private val context: Context,
    var localNodeId: Int = 0,
    var localCallsign: String = "RADIO-NODE",
    var localDisplayName: String = "iTantra Node",
    var localLanguages: List<String> = listOf("hi", "en"),
    private val port: Int = DEFAULT_PORT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Transport {

    private val tag = "WifiDirectTransport"
    override val transportType = TransportType.WIFI_DIRECT

    // Mapped TransportState for generic Transport consumers
    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    // Fine-grained Wi-Fi Direct state
    private val _p2pState = MutableStateFlow(WifiDirectState.DISCONNECTED)
    val p2pState: StateFlow<WifiDirectState> = _p2pState.asStateFlow()

    private val _receivedPackets = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 64)
    override val receivedPackets: SharedFlow<Packet> = _receivedPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    // Discovered authentic iTantra peers via DNS-SD
    private val _discoveredPeers = MutableStateFlow<List<WifiDirectDiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<WifiDirectDiscoveredPeer>> = _discoveredPeers.asStateFlow()

    // P2P Manager and Channel
    private val p2pManager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var p2pChannel: WifiP2pManager.Channel? = null

    // Node ID <-> Device mapping
    private val nodeIdToDeviceMap = ConcurrentHashMap<Int, WifiP2pDevice>()
    private val deviceAddressToNodeIdMap = ConcurrentHashMap<String, Int>()
    private val discoveredPeersMap = ConcurrentHashMap<Int, WifiDirectDiscoveredPeer>()

    // Socket connections
    private class ActiveConnection(
        val socket: Socket,
        val inStream: DataInputStream,
        val outStream: DataOutputStream,
        val remoteAddress: String,
        var peerNodeId: Int? = null
    )
    private val activeConnections = ConcurrentHashMap<String, ActiveConnection>()
    private var serverSocket: ServerSocket? = null

    // Jobs & scope
    private val scope = CoroutineScope(dispatcher)
    private var listenJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var peerCleanupJob: Job? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null
    private var localServiceInfo: WifiP2pDnsSdServiceInfo? = null

    private val isRunning = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private var isReceiverRegistered = false
    private var currentP2pInfo: WifiP2pInfo? = null
    private var connectingTargetNodeId: Int? = null

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val p2pEnabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.i(tag, "WIFI_P2P_STATE_CHANGED: enabled=$p2pEnabled")
                    if (!p2pEnabled) {
                        updateP2pState(WifiDirectState.DISABLED)
                    } else if (_p2pState.value == WifiDirectState.DISABLED) {
                        if (isRunning.get()) {
                            startDiscovery()
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d(tag, "WIFI_P2P_PEERS_CHANGED")
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    Log.i(tag, "WIFI_P2P_CONNECTION_CHANGED: isConnected=${networkInfo?.isConnected}")
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    } else {
                        handleP2pDisconnected("P2P NetworkInfo disconnected")
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val thisDevice = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    Log.d(tag, "WIFI_P2P_THIS_DEVICE_CHANGED: ${thisDevice?.deviceName} (${thisDevice?.deviceAddress})")
                }
                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val discoveryState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                    val isDiscovering = discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                    Log.d(tag, "WIFI_P2P_DISCOVERY_CHANGED: started=$isDiscovering")
                }
            }
        }
    }

    override suspend fun start() = withContext(dispatcher) {
        if (isRunning.get()) return@withContext
        Log.i(tag, "Starting WifiDirectTransport (localNodeId=$localNodeId)...")

        // 1. Hardware capability check
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) || p2pManager == null) {
            Log.w(tag, "Wi-Fi Direct hardware/service UNAVAILABLE on this device")
            updateP2pState(WifiDirectState.UNAVAILABLE)
            return@withContext
        }

        // 2. Permission check
        if (!hasRequiredPermissions()) {
            Log.w(tag, "Wi-Fi Direct PERMISSION_REQUIRED")
            updateP2pState(WifiDirectState.PERMISSION_REQUIRED)
            return@withContext
        }

        try {
            p2pChannel = p2pManager.initialize(context, Looper.getMainLooper(), null)
            if (p2pChannel == null) {
                Log.e(tag, "Failed to initialize Wi-Fi P2P Channel")
                updateP2pState(WifiDirectState.FAILED)
                return@withContext
            }

            registerReceiver()
            isRunning.set(true)

            // 3. Register DNS-SD Service (which triggers startDiscovery upon completion)
            setupDnsSdListeners()
            registerDnsSdService()

            // 4. Start periodic peer cleanup (TTL)
            startPeerCleanup()
        } catch (e: Exception) {
            Log.e(tag, "Error starting WifiDirectTransport", e)
            updateP2pState(WifiDirectState.FAILED)
        }
    }

    override suspend fun stop() = withContext(dispatcher) {
        if (!isRunning.get()) return@withContext
        Log.i(tag, "Stopping WifiDirectTransport...")
        isRunning.set(false)
        isConnecting.set(false)

        connectionTimeoutJob?.cancel()
        peerCleanupJob?.cancel()
        listenJob?.cancel()

        unregisterReceiver()
        stopDiscovery()
        unregisterDnsSdService()
        closeAllSockets()

        // Disconnect P2P group if active
        disconnectP2pGroup()

        updateP2pState(WifiDirectState.DISCONNECTED)
        _connectedPeers.value = emptyList()
        Log.i(tag, "WifiDirectTransport stopped")
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(p2pReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(p2pReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(p2pReceiver)
        } catch (e: Exception) {
            Log.w(tag, "Error unregistering P2P receiver: ${e.message}")
        }
        isReceiverRegistered = false
    }

    // -------------------------------------------------------------------------
    // Native DNS-SD Service Discovery
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun registerDnsSdService() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        val txtRecord = mutableMapOf(
            "nodeId" to localNodeId.toString(),
            "callsign" to localCallsign,
            "displayName" to localDisplayName,
            "supportedLanguages" to localLanguages.joinToString(","),
            "protocolVersion" to "1",
            "capabilities" to "WIFI_DIRECT,WIFI_UDP,BT_SPP"
        )

        val serviceName = "iTantra-$localNodeId"
        localServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(serviceName, SERVICE_TYPE, txtRecord)

        manager.clearLocalServices(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.addLocalService(channel, localServiceInfo, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(tag, "DNS-SD local service registered: $serviceName ($SERVICE_TYPE)")
                        startDiscovery()
                    }
                    override fun onFailure(reason: Int) {
                        Log.w(tag, "Failed to register DNS-SD local service: reason $reason")
                        startDiscovery()
                    }
                })
            }
            override fun onFailure(reason: Int) {
                Log.w(tag, "Failed to clear local services: reason $reason")
                manager.addLocalService(channel, localServiceInfo, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(tag, "DNS-SD local service registered after clear failure: $serviceName ($SERVICE_TYPE)")
                        startDiscovery()
                    }
                    override fun onFailure(reason: Int) {
                        Log.w(tag, "Failed to add local service: reason $reason")
                        startDiscovery()
                    }
                })
            }
        })
    }

    private fun unregisterDnsSdService() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        manager.clearLocalServices(channel, null)
        localServiceInfo = null
    }

    @SuppressLint("MissingPermission")
    private fun setupDnsSdListeners() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        val serviceResponseListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, srcDevice ->
            Log.d(tag, "DNS-SD Service Response: instance=$instanceName, reg=$registrationType, src=${srcDevice.deviceAddress}")
        }

        val txtRecordListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomainName, record, srcDevice ->
            Log.d(tag, "DNS-SD TXT Record from ${srcDevice.deviceAddress}: $record")
            if (fullDomainName.contains(SERVICE_TYPE_BASE, ignoreCase = true) || record.containsKey("nodeId")) {
                val peerNodeId = record["nodeId"]?.toIntOrNull()
                if (peerNodeId != null && peerNodeId > 0 && peerNodeId != localNodeId) {
                    val callsign = record["callsign"] ?: "NODE #$peerNodeId"
                    val displayName = record["displayName"] ?: callsign
                    val languages = record["supportedLanguages"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    val protocolVersion = record["protocolVersion"] ?: "1"
                    val capabilities = record["capabilities"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("WIFI_DIRECT")

                    val discovered = WifiDirectDiscoveredPeer(
                        nodeId = peerNodeId,
                        callsign = callsign,
                        displayName = displayName,
                        supportedLanguages = languages,
                        protocolVersion = protocolVersion,
                        capabilities = capabilities,
                        deviceAddress = srcDevice.deviceAddress,
                        deviceName = srcDevice.deviceName ?: srcDevice.deviceAddress,
                        lastSeenMs = System.currentTimeMillis()
                    )
                    onPeerDiscovered(discovered, srcDevice)
                }
            }
        }

        manager.setDnsSdResponseListeners(channel, serviceResponseListener, txtRecordListener)
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        updateP2pState(WifiDirectState.DISCOVERING)
        DiagnosticsRepository.recordWifiDirectDiscoveryStarted()
        Log.i(tag, "WIFI_DIRECT_DISCOVERY_STARTED: initiating DNS-SD service discovery")

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(tag, "Wi-Fi Direct discoverServices started successfully")
                    }
                    override fun onFailure(reason: Int) {
                        Log.w(tag, "discoverServices failed: reason $reason")
                        if (_p2pState.value == WifiDirectState.DISCOVERING) {
                            updateP2pState(WifiDirectState.FAILED)
                            DiagnosticsRepository.recordWifiDirectFailed("discoverServices failed: $reason")
                        }
                    }
                })
            }
            override fun onFailure(reason: Int) {
                Log.w(tag, "addServiceRequest failed: reason $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        manager.clearServiceRequests(channel, null)
        manager.stopPeerDiscovery(channel, null)
    }

    private fun onPeerDiscovered(peer: WifiDirectDiscoveredPeer, device: WifiP2pDevice) {
        nodeIdToDeviceMap[peer.nodeId] = device
        deviceAddressToNodeIdMap[device.deviceAddress] = peer.nodeId
        discoveredPeersMap[peer.nodeId] = peer

        _discoveredPeers.value = discoveredPeersMap.values.toList()

        if (_p2pState.value == WifiDirectState.DISCOVERING || _p2pState.value == WifiDirectState.DISCONNECTED) {
            updateP2pState(WifiDirectState.PEERS_FOUND)
        }
        DiagnosticsRepository.recordWifiDirectPeerFound("Node #${peer.nodeId} (${peer.callsign})")
        Log.i(tag, "WIFI_DIRECT_PEER_FOUND: Node #${peer.nodeId} callsign='${peer.callsign}' at ${device.deviceAddress}")
    }

    private fun startPeerCleanup() {
        peerCleanupJob?.cancel()
        peerCleanupJob = scope.launch {
            while (isActive && isRunning.get()) {
                delay(15_000L)
                val now = System.currentTimeMillis()
                val expiredIds = discoveredPeersMap.filterValues { now - it.lastSeenMs > 45_000L }.keys
                for (id in expiredIds) {
                    val removed = discoveredPeersMap.remove(id)
                    if (removed != null) {
                        nodeIdToDeviceMap.remove(id)
                        deviceAddressToNodeIdMap.remove(removed.deviceAddress)
                        Log.d(tag, "Peer Node #$id expired from discovery table")
                    }
                }
                if (expiredIds.isNotEmpty()) {
                    _discoveredPeers.value = discoveredPeersMap.values.toList()
                    if (_discoveredPeers.value.isEmpty() && _p2pState.value == WifiDirectState.PEERS_FOUND) {
                        updateP2pState(WifiDirectState.DISCOVERING)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connection Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Connect to a specific iTantra node by its canonical integer [targetNodeId].
     * Includes duplicate connection prevention and timeout handling.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(targetNodeId: Int): Boolean = withContext(dispatcher) {
        val manager = p2pManager ?: return@withContext false
        val channel = p2pChannel ?: return@withContext false

        // Duplicate connection prevention
        if (_p2pState.value == WifiDirectState.CONNECTED && activeConnections.values.any { it.peerNodeId == targetNodeId }) {
            Log.w(tag, "Already CONNECTED to Node #$targetNodeId. Ignoring duplicate connect request.")
            return@withContext true
        }
        if (isConnecting.get()) {
            Log.w(tag, "Connection attempt already in progress to Node #$connectingTargetNodeId. Ignoring duplicate.")
            return@withContext false
        }

        val device = nodeIdToDeviceMap[targetNodeId]
        if (device == null) {
            Log.w(tag, "Target Node #$targetNodeId not found in discovered peers table")
            return@withContext false
        }

        isConnecting.set(true)
        connectingTargetNodeId = targetNodeId
        updateP2pState(WifiDirectState.CONNECTING)
        DiagnosticsRepository.recordWifiDirectConnecting("Node #$targetNodeId (${device.deviceAddress})")
        Log.i(tag, "WIFI_DIRECT_CONNECTING: Initiating connection to Node #$targetNodeId at ${device.deviceAddress}")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }

        // Connection timeout watchdog (25 seconds)
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(25_000L)
            if (isConnecting.get() && _p2pState.value == WifiDirectState.CONNECTING) {
                Log.w(tag, "Connection timeout (25s) to Node #$targetNodeId")
                cancelConnect()
                updateP2pState(WifiDirectState.FAILED)
                DiagnosticsRepository.recordWifiDirectFailed("Timeout connecting to Node #$targetNodeId")
                isConnecting.set(false)
                connectingTargetNodeId = null
            }
        }

        var connectInitiated = false
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(tag, "Wi-Fi Direct connect command accepted for ${device.deviceAddress}")
                connectInitiated = true
            }
            override fun onFailure(reason: Int) {
                Log.e(tag, "Wi-Fi Direct connect command failed: reason $reason")
                connectionTimeoutJob?.cancel()
                isConnecting.set(false)
                connectingTargetNodeId = null
                updateP2pState(WifiDirectState.FAILED)
                DiagnosticsRepository.recordWifiDirectFailed("Connect rejected or failed: reason $reason")
            }
        })

        return@withContext true
    }

    @SuppressLint("MissingPermission")
    fun cancelConnect() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(tag, "cancelConnect succeeded")
            }
            override fun onFailure(reason: Int) {
                Log.d(tag, "cancelConnect failed: reason $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        manager.requestConnectionInfo(channel) { info ->
            Log.i(tag, "P2P Connection Info: groupFormed=${info?.groupFormed}, isGO=${info?.isGroupOwner}, goAddress=${info?.groupOwnerAddress?.hostAddress}")
            if (info != null && info.groupFormed) {
                currentP2pInfo = info
                connectionTimeoutJob?.cancel()
                isConnecting.set(false)

                scope.launch {
                    setupSocketTransport(info)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectP2pGroup() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(tag, "Wi-Fi Direct group removed successfully")
            }
            override fun onFailure(reason: Int) {
                Log.d(tag, "removeGroup failed: reason $reason")
            }
        })
    }

    private fun handleP2pDisconnected(reason: String) {
        Log.i(tag, "WIFI_DIRECT_DISCONNECTED: $reason")
        connectionTimeoutJob?.cancel()
        isConnecting.set(false)
        connectingTargetNodeId = null
        currentP2pInfo = null

        closeAllSockets()
        _connectedPeers.value = emptyList()

        updateP2pState(if (discoveredPeersMap.isNotEmpty()) WifiDirectState.PEERS_FOUND else WifiDirectState.DISCONNECTED)
        DiagnosticsRepository.recordWifiDirectDisconnected(reason)
    }

    // -------------------------------------------------------------------------
    // Socket Transport (Port 42889 + Length-Prefixed Framing)
    // -------------------------------------------------------------------------

    private suspend fun setupSocketTransport(info: WifiP2pInfo) = withContext(dispatcher) {
        closeAllSockets()

        if (info.isGroupOwner) {
            // Group Owner: Bind ServerSocket and accept client socket(s)
            Log.i(tag, "Acting as Group Owner: Binding TCP ServerSocket on port $port")
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                listenJob?.cancel()
                listenJob = scope.launch {
                    while (isActive && isRunning.get()) {
                        try {
                            val clientSocket = serverSocket?.accept() ?: break
                            val remoteIp = clientSocket.inetAddress?.hostAddress ?: "unknown"
                            Log.i(tag, "GO accepted incoming TCP socket from $remoteIp")
                            handleNewSocket(clientSocket, remoteIp)
                        } catch (e: Exception) {
                            if (isRunning.get()) {
                                Log.w(tag, "ServerSocket accept exception: ${e.message}")
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to bind Group Owner ServerSocket: ${e.message}", e)
                updateP2pState(WifiDirectState.FAILED)
                DiagnosticsRepository.recordWifiDirectFailed("GO ServerSocket bind error: ${e.message}")
            }
        } else {
            // Client: Connect to Group Owner address
            val goAddress = info.groupOwnerAddress ?: run {
                Log.e(tag, "Group Owner address is null in Client mode")
                updateP2pState(WifiDirectState.FAILED)
                return@withContext
            }
            Log.i(tag, "Acting as Client: Connecting to GO at ${goAddress.hostAddress}:$port...")

            scope.launch {
                var connected = false
                var attempts = 0
                while (isActive && isRunning.get() && attempts < 8 && !connected) {
                    attempts++
                    try {
                        delay(600L) // Allow GO server socket to bind
                        val socket = Socket(goAddress, port)
                        Log.i(tag, "Client successfully connected to GO at ${goAddress.hostAddress}:$port")
                        handleNewSocket(socket, goAddress.hostAddress ?: "GO")
                        connected = true
                    } catch (e: Exception) {
                        Log.d(tag, "Client connect attempt $attempts failed (${e.message}), retrying...")
                    }
                }
                if (!connected && isRunning.get()) {
                    Log.e(tag, "Client failed to connect to GO after $attempts attempts")
                    updateP2pState(WifiDirectState.FAILED)
                    DiagnosticsRepository.recordWifiDirectFailed("Client could not reach GO TCP socket")
                }
            }
        }
    }

    private fun handleNewSocket(socket: Socket, remoteAddress: String) {
        val inStream = DataInputStream(socket.getInputStream())
        val outStream = DataOutputStream(socket.getOutputStream())

        val connection = ActiveConnection(
            socket = socket,
            inStream = inStream,
            outStream = outStream,
            remoteAddress = remoteAddress,
            peerNodeId = connectingTargetNodeId
        )
        activeConnections[remoteAddress] = connection

        updateP2pState(WifiDirectState.CONNECTED)
        DiagnosticsRepository.recordWifiDirectConnected("Remote: $remoteAddress, Node: #${connectingTargetNodeId ?: "unknown"}")
        Log.i(tag, "WIFI_DIRECT_CONNECTED: Socket link established with $remoteAddress")

        updateConnectedPeers()

        // Framed reading loop
        scope.launch {
            try {
                while (isActive && isRunning.get() && !socket.isClosed) {
                    val length = inStream.readInt()
                    if (length <= 0 || length > Packet.MAX_REASSEMBLED_BYTES) {
                        Log.w(tag, "Invalid frame length: $length from $remoteAddress")
                        break
                    }

                    val buffer = ByteArray(length)
                    inStream.readFully(buffer)

                    try {
                        val packet = PacketSerializer.deserialize(buffer)
                        connection.peerNodeId = packet.sourceDeviceId
                        updateConnectedPeers()

                        Log.d(tag, "WIFI_DIRECT_RECEIVE: seq=${packet.sequenceNumber} type=${packet.msgType} from Node #${packet.sourceDeviceId}")
                        _receivedPackets.emit(packet)
                    } catch (e: Exception) {
                        Log.w(tag, "Error deserializing packet from $remoteAddress: ${e.message}")
                    }
                }
            } catch (e: EOFException) {
                Log.i(tag, "Socket EOF reached for $remoteAddress")
            } catch (e: IOException) {
                if (isRunning.get()) {
                    Log.w(tag, "Socket IO exception for $remoteAddress: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error in read loop for $remoteAddress", e)
            } finally {
                closeConnection(remoteAddress)
            }
        }
    }

    override suspend fun send(packet: Packet): Boolean = withContext(dispatcher) {
        if (!isRunning.get() || _p2pState.value != WifiDirectState.CONNECTED) {
            return@withContext false
        }

        val connections = activeConnections.values.toList()
        if (connections.isEmpty()) {
            Log.w(tag, "Cannot send: No active Wi-Fi Direct TCP connections")
            return@withContext false
        }

        return@withContext try {
            val bytes = PacketSerializer.serialize(packet)
            var anySent = false

            for (conn in connections) {
                try {
                    synchronized(conn.outStream) {
                        conn.outStream.writeInt(bytes.size)
                        conn.outStream.write(bytes)
                        conn.outStream.flush()
                    }
                    anySent = true
                } catch (e: Exception) {
                    Log.w(tag, "Error sending packet to ${conn.remoteAddress}: ${e.message}")
                }
            }

            if (anySent) {
                Log.d(tag, "WIFI_DIRECT_SEND: seq=${packet.sequenceNumber}, type=${packet.msgType}, bytes=${bytes.size}")
            }
            anySent
        } catch (e: Exception) {
            Log.e(tag, "Error serializing/sending packet over Wi-Fi Direct", e)
            false
        }
    }

    private fun updateConnectedPeers() {
        val peers = activeConnections.values.map { conn ->
            val nodeId = conn.peerNodeId ?: 0
            val peerName = discoveredPeersMap[nodeId]?.callsign ?: (if (nodeId > 0) "Node #$nodeId" else "Wi-Fi Direct Peer")
            PeerDevice(
                id = "WIFI_DIRECT-$nodeId",
                name = peerName,
                address = conn.remoteAddress,
                transportType = TransportType.WIFI_DIRECT,
                isConnected = true
            )
        }
        _connectedPeers.value = peers
    }

    private fun closeConnection(remoteAddress: String) {
        val conn = activeConnections.remove(remoteAddress)
        try {
            conn?.inStream?.close()
            conn?.outStream?.close()
            conn?.socket?.close()
        } catch (_: Exception) {}

        updateConnectedPeers()
        if (activeConnections.isEmpty()) {
            handleP2pDisconnected("All active connections closed")
        }
    }

    private fun closeAllSockets() {
        for ((addr, conn) in activeConnections) {
            try {
                conn.inStream.close()
                conn.outStream.close()
                conn.socket.close()
            } catch (_: Exception) {}
        }
        activeConnections.clear()

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun updateP2pState(newState: WifiDirectState) {
        _p2pState.value = newState
        _state.value = when (newState) {
            WifiDirectState.CONNECTED           -> TransportState.CONNECTED
            WifiDirectState.CONNECTING          -> TransportState.CONNECTING
            WifiDirectState.DISCOVERING,
            WifiDirectState.PEERS_FOUND         -> TransportState.LISTENING
            WifiDirectState.UNAVAILABLE,
            WifiDirectState.DISABLED,
            WifiDirectState.PERMISSION_REQUIRED,
            WifiDirectState.FAILED              -> TransportState.ERROR
            WifiDirectState.DISCONNECTED        -> TransportState.DISCONNECTED
        }
    }

    companion object {
        const val DEFAULT_PORT = 42889
        const val SERVICE_TYPE_BASE = "_itantra"
        const val SERVICE_TYPE = "_itantra._tcp"
    }
}
