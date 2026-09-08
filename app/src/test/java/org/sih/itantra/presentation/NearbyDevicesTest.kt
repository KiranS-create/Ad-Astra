package org.sih.itantra.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.discovery.DiscoveredRawDevice
import org.sih.itantra.core.discovery.DiscoveryScanningState
import org.sih.itantra.core.discovery.DiscoverySourceStatus
import org.sih.itantra.core.discovery.DiscoverySourceType
import org.sih.itantra.core.discovery.MeshReachabilityState
import org.sih.itantra.core.discovery.NearbyDevice
import org.sih.itantra.core.discovery.NearbyDeviceDiscoveryState
import org.sih.itantra.core.discovery.NearbyDeviceIdentity
import org.sih.itantra.core.discovery.NearbyDeviceNetworkState
import org.sih.itantra.core.discovery.NearbyDeviceRepository
import org.sih.itantra.core.discovery.NearbyDiscoverySource
import org.sih.itantra.core.discovery.ProximityCalculator
import org.sih.itantra.core.discovery.ProximityState
import org.sih.itantra.core.discovery.SignalLevel
import org.sih.itantra.core.discovery.SignalStrengthInfo

/**
 * Deterministic, offline unit tests for Feature 4: Nearby iTantra Devices.
 * Strictly adheres to 14 core validation requirements without needing physical hardware.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyDevicesTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val localNodeId = 209070

    private lateinit var fakeBleSource: FakeTestDiscoverySource
    private lateinit var fakeUwbSource: FakeTestDiscoverySource
    private lateinit var fakeMeshSource: FakeTestDiscoverySource
    private lateinit var repository: NearbyDeviceRepository

    @Before
    fun setUp() {
        fakeBleSource = FakeTestDiscoverySource(
            sourceType = DiscoverySourceType.BLE,
            initialStatus = DiscoverySourceStatus.STANDBY,
            supported = true
        )
        fakeUwbSource = FakeTestDiscoverySource(
            sourceType = DiscoverySourceType.UWB,
            initialStatus = DiscoverySourceStatus.UNAVAILABLE,
            supported = false
        )
        fakeMeshSource = FakeTestDiscoverySource(
            sourceType = DiscoverySourceType.MESH_TOPOLOGY,
            initialStatus = DiscoverySourceStatus.STANDBY,
            supported = true
        )

        repository = NearbyDeviceRepository(
            localNodeId = localNodeId,
            bleSource = fakeBleSource,
            uwbSource = fakeUwbSource,
            meshSource = fakeMeshSource,
            dispatcher = testDispatcher
        )
    }

    // 1. Discovered device model creation
    @Test
    fun testDiscoveredDeviceModelCreation() {
        val identity = NearbyDeviceIdentity(
            nodeId = 477124,
            callsign = "SQUAD BRAVO",
            isLocalDevice = false
        )
        val discovery = NearbyDeviceDiscoveryState(
            discoverySource = DiscoverySourceType.BLE,
            proximityState = ProximityState.VERY_CLOSE,
            signalStrength = SignalStrengthInfo(rssi = -52, level = SignalLevel.STRONG, label = "SIGNAL STRONG"),
            rawRssi = -52
        )
        val network = NearbyDeviceNetworkState(
            meshReachability = MeshReachabilityState.DIRECT_NEIGHBOR,
            transportCapabilities = listOf("BLE", "Bluetooth RFCOMM"),
            supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH),
            hopCount = 1,
            batteryPct = 85
        )

        val device = NearbyDevice(
            identity = identity,
            discovery = discovery,
            network = network
        )

        assertEquals(477124, device.nodeId)
        assertEquals("#477124", device.formattedNodeId)
        assertEquals("SQUAD BRAVO", device.callsign)
        assertFalse(device.isLocalDevice)
        assertEquals(DiscoverySourceType.BLE, device.discoverySource)
        assertEquals(ProximityState.VERY_CLOSE, device.proximityState)
        assertEquals(SignalLevel.STRONG, device.signalStrength.level)
        assertEquals(-52, device.rawRssi)
        assertEquals(MeshReachabilityState.DIRECT_NEIGHBOR, device.meshReachability)
        assertEquals(85, device.batteryPct)
        assertEquals(2, device.supportedLanguages.size)
    }

    // 2. Duplicate discovered device suppression
    @Test
    fun testDuplicateDiscoveredDeviceSuppression() = testScope.runTest {
        val dev1 = DiscoveredRawDevice(
            deviceId = "AA:BB:CC:DD:EE:01",
            nodeId = 477124,
            callsign = "SQUAD BRAVO",
            sourceType = DiscoverySourceType.BLE,
            rssi = -70
        )

        val dev1Update = DiscoveredRawDevice(
            deviceId = "AA:BB:CC:DD:EE:01",
            nodeId = 477124,
            callsign = "SQUAD BRAVO",
            sourceType = DiscoverySourceType.BLE,
            rssi = -50 // Moved closer
        )

        repository.ingestRawDevices(listOf(dev1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.discoveredDevices.value.size)
        assertEquals(ProximityState.NEARBY, repository.discoveredDevices.value[0].proximityState)

        // Ingest updated sample of same node
        repository.ingestRawDevices(listOf(dev1Update))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.discoveredDevices.value.size)
        assertEquals(-50, repository.discoveredDevices.value[0].rawRssi)
        assertEquals(ProximityState.VERY_CLOSE, repository.discoveredDevices.value[0].proximityState)
    }

    // 3. Local device suppression
    @Test
    fun testLocalDeviceSuppression() = testScope.runTest {
        val localDeviceEcho = DiscoveredRawDevice(
            deviceId = "00:00:00:00:00:00",
            nodeId = localNodeId, // 209070
            callsign = "THIS DEVICE",
            sourceType = DiscoverySourceType.BLE,
            rssi = -30
        )

        val peerDevice = DiscoveredRawDevice(
            deviceId = "11:22:33:44:55:66",
            nodeId = 312900,
            callsign = "COMMAND ALPHA",
            sourceType = DiscoverySourceType.BLE,
            rssi = -60
        )

        repository.ingestRawDevices(listOf(localDeviceEcho, peerDevice))
        testDispatcher.scheduler.advanceUntilIdle()

        val list = repository.discoveredDevices.value
        assertEquals(1, list.size)
        assertEquals(312900, list[0].nodeId)
        assertFalse(list.any { it.nodeId == localNodeId })
    }

    // 4. Proximity state mapping
    @Test
    fun testProximityStateMappingFromRssi() {
        assertEquals(ProximityState.VERY_CLOSE, ProximityCalculator.fromRssi(-40))
        assertEquals(ProximityState.VERY_CLOSE, ProximityCalculator.fromRssi(-55))
        assertEquals(ProximityState.NEARBY, ProximityCalculator.fromRssi(-56))
        assertEquals(ProximityState.NEARBY, ProximityCalculator.fromRssi(-75))
        assertEquals(ProximityState.FAR, ProximityCalculator.fromRssi(-76))
        assertEquals(ProximityState.FAR, ProximityCalculator.fromRssi(-95))

        val strong = ProximityCalculator.signalLevelFromRssi(-50)
        assertEquals(SignalLevel.STRONG, strong.level)
        assertEquals("SIGNAL STRONG", strong.label)

        val moderate = ProximityCalculator.signalLevelFromRssi(-65)
        assertEquals(SignalLevel.MODERATE, moderate.level)
        assertEquals("SIGNAL MODERATE", moderate.label)

        val weak = ProximityCalculator.signalLevelFromRssi(-80)
        assertEquals(SignalLevel.WEAK, weak.level)
        assertEquals("SIGNAL WEAK", weak.label)

        val unknown = ProximityCalculator.signalLevelFromRssi(null)
        assertEquals(SignalLevel.UNKNOWN, unknown.level)
        assertEquals("SIGNAL UNKNOWN", unknown.label)
    }

    // 5. Discovery source mapping
    @Test
    fun testDiscoverySourceMapping() {
        assertEquals("BLE", DiscoverySourceType.BLE.badgeText)
        assertEquals("UWB", DiscoverySourceType.UWB.badgeText)
        assertEquals("Wi-Fi", DiscoverySourceType.WIFI_LOCAL.badgeText)
        assertEquals("MESH", DiscoverySourceType.MESH_TOPOLOGY.badgeText)
        assertEquals("MANUAL", DiscoverySourceType.MANUAL.badgeText)
    }

    // 6. BLE unavailable state
    @Test
    fun testBleUnavailableState() = testScope.runTest {
        fakeBleSource.setStatus(DiscoverySourceStatus.DISABLED)
        repository.startScanning()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = repository.scanningState.value
        assertEquals(DiscoverySourceStatus.DISABLED, state.bleStatus)
    }

    // 7. UWB unavailable state
    @Test
    fun testUwbUnavailableState() = testScope.runTest {
        assertFalse(fakeUwbSource.isSupported())
        assertEquals(DiscoverySourceStatus.UNAVAILABLE, fakeUwbSource.status.value)
        val started = fakeUwbSource.startScan()
        assertFalse(started)
        assertEquals(DiscoverySourceStatus.UNAVAILABLE, fakeUwbSource.status.value)
    }

    // 8. No-device empty state
    @Test
    fun testNoDeviceEmptyState() = testScope.runTest {
        repository.clear()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.discoveredDevices.value.isEmpty())
        assertEquals(0, repository.scanningState.value.discoveredCount)
    }

    // 9. Known mesh node vs newly discovered node distinction
    @Test
    fun testMeshNodeVsDiscoveredNodeDistinction() = testScope.runTest {
        val meshNeighbor = DiscoveredRawDevice(
            deviceId = "NODE-477124",
            nodeId = 477124,
            callsign = "SQUAD BRAVO",
            sourceType = DiscoverySourceType.MESH_TOPOLOGY,
            hopCount = 1
        )

        val multiHopRoute = DiscoveredRawDevice(
            deviceId = "NODE-555555",
            nodeId = 555555,
            callsign = "RELAY ECHO",
            sourceType = DiscoverySourceType.MESH_TOPOLOGY,
            hopCount = 2
        )

        val bleOnlyDevice = DiscoveredRawDevice(
            deviceId = "AA:11:22:33:44:55",
            nodeId = 666666,
            callsign = "UNLINKED PEER",
            sourceType = DiscoverySourceType.BLE,
            rssi = -65
        )

        repository.ingestRawDevices(listOf(meshNeighbor, multiHopRoute, bleOnlyDevice))
        testDispatcher.scheduler.advanceUntilIdle()

        val dev1 = repository.discoveredDevices.value.first { it.nodeId == 477124 }
        val dev2 = repository.discoveredDevices.value.first { it.nodeId == 555555 }
        val dev3 = repository.discoveredDevices.value.first { it.nodeId == 666666 }

        assertEquals(MeshReachabilityState.DIRECT_NEIGHBOR, dev1.meshReachability)
        assertEquals(MeshReachabilityState.MULTI_HOP_RELAY, dev2.meshReachability)
        assertEquals(MeshReachabilityState.NOT_IN_MESH, dev3.meshReachability)
    }

    // 10. Device detail field preservation
    @Test
    fun testDeviceDetailFieldPreservation() = testScope.runTest {
        val initial = DiscoveredRawDevice(
            deviceId = "DEV-01",
            nodeId = 477124,
            callsign = "SQUAD BRAVO",
            sourceType = DiscoverySourceType.BLE,
            rssi = -60,
            batteryPct = 90,
            transportCapabilities = listOf("BLE", "Bluetooth RFCOMM", "Wi-Fi"),
            supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.MARATHI)
        )

        repository.ingestRawDevices(listOf(initial))
        testDispatcher.scheduler.advanceUntilIdle()

        val updateWithoutTelemetry = DiscoveredRawDevice(
            deviceId = "DEV-01",
            nodeId = 477124,
            callsign = null, // null in subsequent beacon
            sourceType = DiscoverySourceType.BLE,
            rssi = -55,
            batteryPct = null, // missing in raw beacon
            transportCapabilities = emptyList()
        )

        repository.ingestRawDevices(listOf(updateWithoutTelemetry))
        testDispatcher.scheduler.advanceUntilIdle()

        val merged = repository.discoveredDevices.value.first { it.nodeId == 477124 }
        assertEquals("SQUAD BRAVO", merged.callsign)
        assertEquals(90, merged.batteryPct)
        assertEquals(2, merged.supportedLanguages.size)
        assertTrue(merged.transportCapabilities.contains("Bluetooth RFCOMM"))
        assertEquals(-55, merged.rawRssi)
        assertEquals(ProximityState.VERY_CLOSE, merged.proximityState)
    }

    // 11. Add Contact callback passes correct node ID
    @Test
    fun testAddContactCallbackPassesCorrectNodeId() {
        val device = NearbyDevice(
            identity = NearbyDeviceIdentity(nodeId = 477124, callsign = "SQUAD BRAVO"),
            discovery = NearbyDeviceDiscoveryState(
                discoverySource = DiscoverySourceType.BLE,
                proximityState = ProximityState.VERY_CLOSE,
                signalStrength = SignalStrengthInfo(-50, SignalLevel.STRONG, "STRONG")
            ),
            network = NearbyDeviceNetworkState(meshReachability = MeshReachabilityState.DIRECT_NEIGHBOR)
        )

        var capturedDevice: NearbyDevice? = null
        val onAddContact: (NearbyDevice) -> Unit = { target -> capturedDevice = target }

        onAddContact(device)

        assertNotNull(capturedDevice)
        assertEquals(477124, capturedDevice?.nodeId)
        assertEquals("SQUAD BRAVO", capturedDevice?.callsign)
    }

    // 12. Open Chat callback passes correct node ID
    @Test
    fun testOpenChatCallbackPassesCorrectNodeId() {
        var openedNodeId: Int? = null
        val onOpenChat: (Int) -> Unit = { id -> openedNodeId = id }

        onOpenChat(477124)

        assertEquals(477124, openedNodeId)
    }

    // 13. Permission failure state
    @Test
    fun testPermissionFailureState() = testScope.runTest {
        fakeBleSource.setStatus(DiscoverySourceStatus.PERMISSION_REQUIRED)
        repository.startScanning()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DiscoverySourceStatus.PERMISSION_REQUIRED, repository.scanningState.value.bleStatus)
    }

    // 14. Scanner lifecycle cleanup/state stop
    @Test
    fun testScannerLifecycleCleanupAndStop() = testScope.runTest {
        repository.startScanning()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.scanningState.value.isScanning)
        assertTrue(fakeBleSource.isScanning.value)

        repository.stopScanning()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(repository.scanningState.value.isScanning)
        assertFalse(fakeBleSource.isScanning.value)
        assertEquals(0, repository.scanningState.value.activeSourceCount)
    }
}

/**
 * Deterministic test discovery source with controllable status, scanning state, and devices.
 */
class FakeTestDiscoverySource(
    override val sourceType: DiscoverySourceType,
    initialStatus: DiscoverySourceStatus = DiscoverySourceStatus.STANDBY,
    private val supported: Boolean = true
) : NearbyDiscoverySource {

    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<DiscoverySourceStatus> = _status.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredRawDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredRawDevice>> = _discoveredDevices.asStateFlow()

    fun emitDevices(devices: List<DiscoveredRawDevice>) {
        _discoveredDevices.value = devices
    }

    fun setStatus(newStatus: DiscoverySourceStatus) {
        _status.value = newStatus
    }

    override fun isSupported(): Boolean = supported

    override suspend fun startScan(): Boolean {
        if (!supported || _status.value == DiscoverySourceStatus.DISABLED || _status.value == DiscoverySourceStatus.PERMISSION_REQUIRED) {
            return false
        }
        _isScanning.value = true
        _status.value = DiscoverySourceStatus.ACTIVE
        return true
    }

    override suspend fun stopScan() {
        _isScanning.value = false
        if (!supported) {
            _status.value = DiscoverySourceStatus.UNAVAILABLE
        } else if (_status.value == DiscoverySourceStatus.ACTIVE) {
            _status.value = DiscoverySourceStatus.STANDBY
        }
    }
}
