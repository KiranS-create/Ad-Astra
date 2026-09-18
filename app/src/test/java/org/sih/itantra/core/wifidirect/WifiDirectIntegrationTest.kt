package org.sih.itantra.core.wifidirect

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import org.sih.itantra.core.health.CommunicationHealthMapper
import org.sih.itantra.core.health.OverallHealthStatus
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.transport.PeerDevice
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType

/**
 * Feature 28 — Integration tests for Wi-Fi Direct:
 * - Telemetry event dispatching
 * - CommunicationHealthMapper mapping
 * - Failover selection
 */
class WifiDirectIntegrationTest {

    @Before
    fun setUp() {
        DiagnosticsRepository.resetForTesting()
    }

    @Test
    fun testTelemetryDispatching() {
        DiagnosticsRepository.recordWifiDirectDiscoveryStarted()
        assertEquals(1L, DiagnosticsRepository.state.value.wifiDirectDiscoveryStarted)
        assertEquals("DISCOVERING", DiagnosticsRepository.state.value.wifiDirectState)

        DiagnosticsRepository.recordWifiDirectPeerFound("Node #209072")
        assertEquals(1L, DiagnosticsRepository.state.value.wifiDirectPeersFound)
        assertEquals("PEERS_FOUND", DiagnosticsRepository.state.value.wifiDirectState)

        DiagnosticsRepository.recordWifiDirectConnecting("Node #209072")
        assertEquals(1L, DiagnosticsRepository.state.value.wifiDirectConnectingCount)
        assertEquals("CONNECTING", DiagnosticsRepository.state.value.wifiDirectState)

        DiagnosticsRepository.recordWifiDirectConnected("192.168.49.1")
        assertEquals(1L, DiagnosticsRepository.state.value.wifiDirectConnectedCount)
        assertEquals("CONNECTED", DiagnosticsRepository.state.value.wifiDirectState)

        DiagnosticsRepository.recordWifiDirectDisconnected("Graceful close")
        assertEquals(1L, DiagnosticsRepository.state.value.wifiDirectDisconnectedCount)
        assertEquals("DISCONNECTED", DiagnosticsRepository.state.value.wifiDirectState)

        DiagnosticsRepository.recordWifiDirectFailed("Timeout")
        assertEquals(1L, DiagnosticsRepository.state.value.wifiDirectFailedCount)
        assertEquals("FAILED", DiagnosticsRepository.state.value.wifiDirectState)

        DiagnosticsRepository.recordTransportSent("WIFI_DIRECT")
        assertEquals(1L, DiagnosticsRepository.state.value.transportWifiDirectSent)
    }

    @Test
    fun testCommunicationHealthMappingWithWifiDirect() {
        val health = CommunicationHealthMapper.map(
            localNodeId = 209071,
            diagnostics = DiagnosticsRepository.state.value,
            topology = MeshTopologySnapshot(),
            messageHistory = emptyList(),
            wifiState = TransportState.DISCONNECTED,
            bluetoothState = TransportState.DISCONNECTED,
            wifiDirectState = TransportState.CONNECTED,
            wifiDirectPeers = listOf(
                PeerDevice(
                    id = "WIFI_DIRECT-209072",
                    name = "NODE #209072",
                    address = "192.168.49.2",
                    transportType = TransportType.WIFI_DIRECT,
                    isConnected = true
                )
            ),
            preferredTransport = TransportType.WIFI_DIRECT
        )

        assertEquals(OverallHealthStatus.HEALTHY, health.overallStatus)
        val wdItem = health.transports.find { it.type == TransportType.WIFI_DIRECT }
        assertNotNull(wdItem)
        assertEquals(TransportState.CONNECTED, wdItem!!.state)
        assertEquals("CONNECTED (1)", wdItem.displayState)
        assertEquals("NOT MEASURED", wdItem.signalDbm)
        assertTrue(wdItem.isPrimary)
    }
}
