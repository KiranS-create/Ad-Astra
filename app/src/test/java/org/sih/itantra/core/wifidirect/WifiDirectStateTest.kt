package org.sih.itantra.core.wifidirect

import org.junit.Assert.*
import org.junit.Test
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.WifiDirectState

/**
 * Feature 28 — Unit tests for WifiDirectState enum and state transitions.
 */
class WifiDirectStateTest {

    private fun toTransportState(s: WifiDirectState): TransportState = when (s) {
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

    @Test fun testAllWifiDirectStatesPresent() {
        val states = WifiDirectState.values().map { it.name }.toSet()
        val expected = setOf(
            "UNAVAILABLE", "DISABLED", "PERMISSION_REQUIRED",
            "DISCOVERING", "PEERS_FOUND", "CONNECTING",
            "CONNECTED", "FAILED", "DISCONNECTED"
        )
        assertEquals(expected, states)
    }

    @Test fun testConnectedMapsToConnected() {
        assertEquals(TransportState.CONNECTED, toTransportState(WifiDirectState.CONNECTED))
    }

    @Test fun testConnectingMapsToConnecting() {
        assertEquals(TransportState.CONNECTING, toTransportState(WifiDirectState.CONNECTING))
    }

    @Test fun testDiscoveringMapsToListening() {
        assertEquals(TransportState.LISTENING, toTransportState(WifiDirectState.DISCOVERING))
    }

    @Test fun testPeersFoundMapsToListening() {
        assertEquals(TransportState.LISTENING, toTransportState(WifiDirectState.PEERS_FOUND))
    }

    @Test fun testUnavailableMapsToError() {
        assertEquals(TransportState.ERROR, toTransportState(WifiDirectState.UNAVAILABLE))
    }

    @Test fun testDisabledMapsToError() {
        assertEquals(TransportState.ERROR, toTransportState(WifiDirectState.DISABLED))
    }

    @Test fun testPermissionRequiredMapsToError() {
        assertEquals(TransportState.ERROR, toTransportState(WifiDirectState.PERMISSION_REQUIRED))
    }

    @Test fun testFailedMapsToError() {
        assertEquals(TransportState.ERROR, toTransportState(WifiDirectState.FAILED))
    }

    @Test fun testDisconnectedMapsToDisconnected() {
        assertEquals(TransportState.DISCONNECTED, toTransportState(WifiDirectState.DISCONNECTED))
    }

    @Test fun testDiscoveryToConnectedLifecycle() {
        val lifecycle = listOf(
            WifiDirectState.DISCONNECTED,
            WifiDirectState.DISCOVERING,
            WifiDirectState.PEERS_FOUND,
            WifiDirectState.CONNECTING,
            WifiDirectState.CONNECTED
        )
        assertEquals(TransportState.DISCONNECTED, toTransportState(lifecycle[0]))
        assertEquals(TransportState.LISTENING, toTransportState(lifecycle[1]))
        assertEquals(TransportState.LISTENING, toTransportState(lifecycle[2]))
        assertEquals(TransportState.CONNECTING, toTransportState(lifecycle[3]))
        assertEquals(TransportState.CONNECTED, toTransportState(lifecycle[4]))
    }
}
