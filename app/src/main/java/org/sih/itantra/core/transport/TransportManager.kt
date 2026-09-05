package org.sih.itantra.core.transport

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sih.itantra.core.protocol.Packet

/**
 * Coordinates and switches between Wi-Fi, Bluetooth, Loopback, and Embedded Radio transports.
 */
class TransportManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val wifiTransport by lazy { WifiTransport(context) }
    val bluetoothTransport by lazy { BluetoothTransport() }
    val loopbackTransport by lazy { LoopbackTransport() }
    val embeddedRadioTransport by lazy { EmbeddedRadioTransport() }

    private val _activeTransport = MutableStateFlow<Transport>(wifiTransport)
    val activeTransport: StateFlow<Transport> = _activeTransport.asStateFlow()

    private val _receivedPackets = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 64)
    val receivedPackets: SharedFlow<Packet> = _receivedPackets.asSharedFlow()

    init {
        // Forward packets from whichever transport is active
        scope.launch {
            wifiTransport.receivedPackets.collect { if (_activeTransport.value == wifiTransport) _receivedPackets.emit(it) }
        }
        scope.launch {
            bluetoothTransport.receivedPackets.collect { if (_activeTransport.value == bluetoothTransport) _receivedPackets.emit(it) }
        }
        scope.launch {
            loopbackTransport.receivedPackets.collect { if (_activeTransport.value == loopbackTransport) _receivedPackets.emit(it) }
        }
        scope.launch {
            embeddedRadioTransport.receivedPackets.collect { if (_activeTransport.value == embeddedRadioTransport) _receivedPackets.emit(it) }
        }
    }

    suspend fun switchTransport(type: TransportType) {
        _activeTransport.value.stop()

        val next = when (type) {
            TransportType.WIFI -> wifiTransport
            TransportType.BLUETOOTH -> bluetoothTransport
            TransportType.LOOPBACK -> loopbackTransport
            TransportType.EMBEDDED_RADIO -> embeddedRadioTransport
        }

        _activeTransport.value = next
        next.start()
    }

    suspend fun send(packet: Packet): Boolean {
        return _activeTransport.value.send(packet)
    }

    suspend fun start() {
        _activeTransport.value.start()
    }

    suspend fun stop() {
        _activeTransport.value.stop()
    }
}
