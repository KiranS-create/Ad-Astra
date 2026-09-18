package org.sih.itantra.core.discovery

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.mesh.NeighborEntry
import org.sih.itantra.core.mesh.TopologyRoute
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe repository managing off-grid device discovery.
 *
 * Responsibilities:
 * - Aggregates raw observations from BLE, UWB, and MANET Mesh sources.
 * - Deduplicates incoming devices by integer Node ID or hardware address.
 * - Suppresses local node from being displayed as a remote peer.
 * - Merges and preserves identity, routing, and proximity telemetry.
 * - Sorts peers by physical proximity hierarchy.
 */
class NearbyDeviceRepository(
    var localNodeId: Int,
    val bleSource: NearbyDiscoverySource? = null,
    val uwbSource: NearbyDiscoverySource? = null,
    val meshSource: NearbyDiscoverySource? = null,
    val wifiDirectSource: NearbyDiscoverySource? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val scope = CoroutineScope(dispatcher)

    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    private val _scanningState = MutableStateFlow(
        DiscoveryScanningState(
            isScanning = false,
            statusMessage = "IDLE",
            bleStatus = bleSource?.status?.value ?: DiscoverySourceStatus.STANDBY,
            uwbStatus = uwbSource?.status?.value ?: DiscoverySourceStatus.UNAVAILABLE,
            meshStatus = meshSource?.status?.value ?: DiscoverySourceStatus.STANDBY,
            discoveredCount = 0,
            activeSourceCount = 0
        )
    )
    val scanningState: StateFlow<DiscoveryScanningState> = _scanningState.asStateFlow()

    private val devicesMap = ConcurrentHashMap<Int, NearbyDevice>()
    private val unassignedDevicesMap = ConcurrentHashMap<String, NearbyDevice>()

    private var observationJob: Job? = null

    init {
        observeSources()
    }

    private fun observeSources() {
        observationJob?.cancel()
        observationJob = scope.launch {
            val flows = listOfNotNull(
                bleSource?.discoveredDevices,
                meshSource?.discoveredDevices,
                wifiDirectSource?.discoveredDevices
            )

            if (flows.isEmpty()) return@launch

            // Combine discovery streams
            combine(flows) { deviceLists ->
                deviceLists.flatMap { it }
            }.collect { rawList ->
                ingestRawDevices(rawList)
            }
        }
    }

    /**
     * Ingests discovered Wi-Fi Direct P2P peers into the repository.
     */
    fun ingestWifiDirectPeers(peers: List<org.sih.itantra.core.transport.WifiDirectDiscoveredPeer>) {
        val rawList = peers.map { peer ->
            DiscoveredRawDevice(
                deviceId = "WIFI_DIRECT-${peer.nodeId}",
                nodeId = peer.nodeId,
                name = peer.displayName,
                callsign = peer.callsign,
                sourceType = DiscoverySourceType.WIFI_DIRECT,
                rssi = null, // Signal explicitly NOT MEASURED
                timestampMs = peer.lastSeenMs,
                transportCapabilities = peer.capabilities.ifEmpty { listOf("Wi-Fi Direct P2P") },
                supportedLanguages = peer.supportedLanguages.map { IndicLanguage.fromIsoCode(it) },
                hopCount = 1
            )
        }
        ingestRawDevices(rawList)
    }

    /**
     * Ingests a list of raw discovered devices, deduplicating and merging metadata.
     */
    fun ingestRawDevices(rawList: List<DiscoveredRawDevice>) {
        val now = System.currentTimeMillis()

        for (raw in rawList) {
            val rawNodeId = raw.nodeId
            val isLocal = (rawNodeId != null && rawNodeId == localNodeId)

            // Calculate proximity from RSSI if available
            val proximity = when {
                raw.rssi != null -> ProximityCalculator.fromRssi(raw.rssi)
                raw.hopCount == 1 -> ProximityState.NEARBY
                raw.hopCount > 1 -> ProximityState.APPROXIMATE
                else -> ProximityState.UNKNOWN
            }

            val signalInfo = ProximityCalculator.signalLevelFromRssi(raw.rssi)

            val reachability = when {
                isLocal -> MeshReachabilityState.DIRECT_NEIGHBOR
                raw.sourceType == DiscoverySourceType.MESH_TOPOLOGY && raw.hopCount == 1 -> MeshReachabilityState.DIRECT_NEIGHBOR
                raw.sourceType == DiscoverySourceType.MESH_TOPOLOGY && raw.hopCount > 1 -> MeshReachabilityState.MULTI_HOP_RELAY
                raw.sourceType == DiscoverySourceType.WIFI_LOCAL -> MeshReachabilityState.DIRECT_NEIGHBOR
                raw.sourceType == DiscoverySourceType.WIFI_DIRECT -> MeshReachabilityState.DIRECT_NEIGHBOR
                raw.sourceType == DiscoverySourceType.BLE && rawNodeId != null && devicesMap.containsKey(rawNodeId) -> {
                    devicesMap[rawNodeId]?.network?.meshReachability ?: MeshReachabilityState.NOT_IN_MESH
                }
                else -> MeshReachabilityState.NOT_IN_MESH
            }

            val callsign = raw.callsign ?: raw.name ?: (if (rawNodeId != null) "NODE #$rawNodeId" else "DISCOVERED NODE")

            if (rawNodeId != null) {
                // Merge with existing node record if present
                val existing = devicesMap[rawNodeId]

                val mergedIdentity = NearbyDeviceIdentity(
                    nodeId = rawNodeId,
                    callsign = if (existing != null && existing.callsign.isNotBlank() && !existing.callsign.startsWith("NODE #")) existing.callsign else callsign,
                    isLocalDevice = isLocal
                )

                val mergedDiscovery = NearbyDeviceDiscoveryState(
                    discoverySource = raw.sourceType,
                    proximityState = if (raw.rssi != null) proximity else (existing?.discovery?.proximityState ?: proximity),
                    signalStrength = if (raw.rssi != null) signalInfo else (existing?.discovery?.signalStrength ?: signalInfo),
                    rawRssi = raw.rssi ?: existing?.discovery?.rawRssi,
                    firstDiscoveredMs = existing?.discovery?.firstDiscoveredMs ?: now,
                    lastSeenMs = now,
                    relativeTimeText = formatRelativeTime(now, now)
                )

                val mergedTransports = (raw.transportCapabilities + (existing?.network?.transportCapabilities ?: emptyList())).distinct()
                val mergedLanguages = if (raw.supportedLanguages.isNotEmpty()) raw.supportedLanguages else (existing?.network?.supportedLanguages ?: listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH))

                val mergedNetwork = NearbyDeviceNetworkState(
                    meshReachability = if (reachability != MeshReachabilityState.NOT_IN_MESH) reachability else (existing?.network?.meshReachability ?: reachability),
                    transportCapabilities = if (mergedTransports.isNotEmpty()) mergedTransports else listOf("Wi-Fi UDP", "BT RFCOMM"),
                    supportedLanguages = mergedLanguages,
                    hopCount = raw.hopCount,
                    batteryPct = raw.batteryPct ?: existing?.network?.batteryPct
                )

                val trust = if (isLocal) DeviceTrustState.LOCAL_DEVICE else (existing?.trustState ?: DeviceTrustState.UNVERIFIED)

                devicesMap[rawNodeId] = NearbyDevice(
                    identity = mergedIdentity,
                    discovery = mergedDiscovery,
                    network = mergedNetwork,
                    trustState = trust
                )
            } else {
                // Device without known nodeId (raw BLE beacon / peripheral address)
                val deviceKey = raw.deviceId
                val existing = unassignedDevicesMap[deviceKey]

                val identity = NearbyDeviceIdentity(
                    nodeId = 0,
                    callsign = callsign,
                    isLocalDevice = false
                )

                val discovery = NearbyDeviceDiscoveryState(
                    discoverySource = raw.sourceType,
                    proximityState = proximity,
                    signalStrength = signalInfo,
                    rawRssi = raw.rssi,
                    firstDiscoveredMs = existing?.discovery?.firstDiscoveredMs ?: now,
                    lastSeenMs = now,
                    relativeTimeText = "Just now"
                )

                val network = NearbyDeviceNetworkState(
                    meshReachability = MeshReachabilityState.NOT_IN_MESH,
                    transportCapabilities = listOf("BLE"),
                    supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH)
                )

                unassignedDevicesMap[deviceKey] = NearbyDevice(
                    identity = identity,
                    discovery = discovery,
                    network = network,
                    trustState = DeviceTrustState.UNVERIFIED
                )
            }
        }

        publishDevices()
    }

    /**
     * Publishes sorted devices list with local node filtered out.
     */
    private fun publishDevices() {
        val allValidNodes = devicesMap.values.filter { !it.isLocalDevice && it.nodeId != localNodeId && it.nodeId > 0 }
        val unassigned = unassignedDevicesMap.values.filter { !it.isLocalDevice }

        // Sort priority: Proximity level (VERY_CLOSE > NEARBY > FAR) -> RSSI -> freshness
        val sortedList = (allValidNodes + unassigned).sortedWith(
            compareByDescending<NearbyDevice> { it.proximityState.level }
                .thenByDescending { it.rawRssi ?: -120 }
                .thenByDescending { it.lastSeenMs }
        )

        _discoveredDevices.value = sortedList
        updateScanningState(discoveredCount = sortedList.size)
    }

    /**
     * Start discovery on all configured offline sources.
     */
    suspend fun startScanning(): Boolean = withContext(dispatcher) {
        var anyActive = false

        _scanningState.value = _scanningState.value.copy(
            isScanning = true,
            statusMessage = "SCANNING FOR NEARBY NODES..."
        )

        bleSource?.let {
            val started = it.startScan()
            if (started) anyActive = true
        }

        meshSource?.let {
            val started = it.startScan()
            if (started) anyActive = true
        }

        // Truthfully record UWB state
        val uwbStatus = uwbSource?.status?.value ?: DiscoverySourceStatus.UNAVAILABLE

        _scanningState.value = _scanningState.value.copy(
            isScanning = anyActive,
            statusMessage = if (anyActive) "SCANNING FOR NEARBY NODES..." else "DISCOVERY IDLE",
            bleStatus = bleSource?.status?.value ?: DiscoverySourceStatus.DISABLED,
            uwbStatus = uwbStatus,
            meshStatus = meshSource?.status?.value ?: DiscoverySourceStatus.STANDBY,
            activeSourceCount = (if (bleSource?.isScanning?.value == true) 1 else 0) + (if (meshSource?.isScanning?.value == true) 1 else 0)
        )

        return@withContext anyActive
    }

    /**
     * Stop scanning on all discovery sources.
     */
    suspend fun stopScanning() = withContext(dispatcher) {
        bleSource?.stopScan()
        meshSource?.stopScan()

        _scanningState.value = _scanningState.value.copy(
            isScanning = false,
            statusMessage = "DISCOVERY PAUSED",
            bleStatus = bleSource?.status?.value ?: DiscoverySourceStatus.STANDBY,
            meshStatus = meshSource?.status?.value ?: DiscoverySourceStatus.STANDBY,
            activeSourceCount = 0
        )
    }

    private fun updateScanningState(discoveredCount: Int) {
        _scanningState.value = _scanningState.value.copy(
            discoveredCount = discoveredCount,
            bleStatus = bleSource?.status?.value ?: _scanningState.value.bleStatus,
            uwbStatus = uwbSource?.status?.value ?: _scanningState.value.uwbStatus,
            meshStatus = meshSource?.status?.value ?: _scanningState.value.meshStatus
        )
    }

    fun clear() {
        devicesMap.clear()
        unassignedDevicesMap.clear()
        _discoveredDevices.value = emptyList()
        updateScanningState(0)
    }

    private fun formatRelativeTime(timestampMs: Long, now: Long = System.currentTimeMillis()): String {
        val diffSec = (now - timestampMs) / 1000
        return when {
            diffSec < 2 -> "Just now"
            diffSec < 60 -> "${diffSec}s ago"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            else -> "${diffSec / 3600}h ago"
        }
    }
}
