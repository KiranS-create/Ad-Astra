package org.sih.itantra.core.transport

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sih.itantra.core.protocol.Packet

/**
 * In-memory loopback transport.
 * Allows immediate, single-device testing of the full speech -> STT -> packet -> transport -> TTS pipeline
 * without requiring a second physical phone or network connection.
 */
class LoopbackTransport : Transport {

    override val transportType = TransportType.LOOPBACK

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _receivedPackets = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 64)
    override val receivedPackets: SharedFlow<Packet> = _receivedPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(
        listOf(
            PeerDevice(
                id = "LOOPBACK-01",
                name = "Virtual Receiver (Self)",
                address = "127.0.0.1",
                transportType = TransportType.LOOPBACK,
                isConnected = true
            )
        )
    )
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    override suspend fun start() {
        _state.value = TransportState.CONNECTED
    }

    override suspend fun stop() {
        _state.value = TransportState.DISCONNECTED
    }

    override suspend fun send(packet: Packet): Boolean {
        // Loop back packet to receiver pipeline
        _receivedPackets.emit(packet)
        return true
    }
}
