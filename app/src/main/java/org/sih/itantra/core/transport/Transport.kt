package org.sih.itantra.core.transport

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.sih.itantra.core.protocol.Packet

enum class TransportState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LISTENING,
    ERROR
}

enum class TransportType(val displayName: String) {
    WIFI("Wi-Fi Direct / Local Mesh"),
    BLUETOOTH("Bluetooth RFCOMM SPP"),
    LOOPBACK("Loopback (Single-Phone Test)"),
    EMBEDDED_RADIO("Embedded Radio / LoRa / SDR"),
    WIFI_DIRECT("Wi-Fi Direct P2P")
}

data class PeerDevice(
    val id: String,
    val name: String,
    val address: String,
    val transportType: TransportType,
    val isConnected: Boolean = false,
    val signalDbm: Int? = null
)

interface Transport {
    val transportType: TransportType
    val state: StateFlow<TransportState>
    val receivedPackets: SharedFlow<Packet>
    val connectedPeers: StateFlow<List<PeerDevice>>

    suspend fun start()
    suspend fun stop()
    suspend fun send(packet: Packet): Boolean
}
