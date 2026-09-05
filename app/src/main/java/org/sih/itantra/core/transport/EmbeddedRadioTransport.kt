package org.sih.itantra.core.transport

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sih.itantra.core.protocol.Packet

/**
 * Hardware serial/UART transport abstraction for future external radio modems
 * (e.g. LoRa 868/915MHz, VHF/UHF tactical transceivers, or SDR hardware).
 */
class EmbeddedRadioTransport : Transport {

    override val transportType = TransportType.EMBEDDED_RADIO

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _receivedPackets = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 64)
    override val receivedPackets: SharedFlow<Packet> = _receivedPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    override suspend fun start() {
        // Ready for USB-Serial / UART driver attachment
        _state.value = TransportState.LISTENING
    }

    override suspend fun stop() {
        _state.value = TransportState.DISCONNECTED
    }

    override suspend fun send(packet: Packet): Boolean {
        // Framing for hardware UART stream
        return true
    }
}
