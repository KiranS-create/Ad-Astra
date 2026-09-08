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

    private val _isAutoFailoverEnabled = MutableStateFlow(true)
    val isAutoFailoverEnabled: StateFlow<Boolean> = _isAutoFailoverEnabled.asStateFlow()

    var preferredTransportType: TransportType = TransportType.BLUETOOTH

    private val _receivedPackets = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 64)
    val receivedPackets: SharedFlow<Packet> = _receivedPackets.asSharedFlow()

    private val seenPacketsLock = Any()
    private val seenPackets = object : java.util.LinkedHashMap<String, Long>(300, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 300
        }
    }

    private fun isDuplicateTransportPacket(packet: Packet): Boolean {
        val now = System.currentTimeMillis()
        val key = "${packet.sourceDeviceId}_${packet.sequenceNumber}_${packet.msgType}_${packet.payload.size}_${packet.crc32}"
        synchronized(seenPacketsLock) {
            val lastSeen = seenPackets[key]
            if (lastSeen != null && (now - lastSeen) < 15_000L) {
                return true
            }
            seenPackets[key] = now
            return false
        }
    }

    init {
        // In auto failover mode, listen to both Wi-Fi and Bluetooth so no incoming packets are missed
        scope.launch {
            wifiTransport.receivedPackets.collect { packet ->
                if ((_isAutoFailoverEnabled.value || _activeTransport.value == wifiTransport) && !isDuplicateTransportPacket(packet)) {
                    _receivedPackets.emit(packet)
                }
            }
        }
        scope.launch {
            bluetoothTransport.receivedPackets.collect { packet ->
                if ((_isAutoFailoverEnabled.value || _activeTransport.value == bluetoothTransport) && !isDuplicateTransportPacket(packet)) {
                    _receivedPackets.emit(packet)
                }
            }
        }
        scope.launch {
            loopbackTransport.receivedPackets.collect { packet ->
                if (_activeTransport.value == loopbackTransport && !isDuplicateTransportPacket(packet)) {
                    _receivedPackets.emit(packet)
                }
            }
        }
        scope.launch {
            embeddedRadioTransport.receivedPackets.collect { packet ->
                if (_activeTransport.value == embeddedRadioTransport && !isDuplicateTransportPacket(packet)) {
                    _receivedPackets.emit(packet)
                }
            }
        }
    }

    fun setAutoFailoverEnabled(enabled: Boolean) {
        _isAutoFailoverEnabled.value = enabled
    }

    suspend fun switchTransport(type: TransportType) {
        _activeTransport.value.stop()

        preferredTransportType = type
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
        if (!_isAutoFailoverEnabled.value) {
            val success = _activeTransport.value.send(packet)
            if (success) {
                val tName = if (_activeTransport.value == bluetoothTransport) "BT" else "WIFI"
                org.sih.itantra.core.diagnostics.DiagnosticsRepository.recordTransportSent(tName)
            }
            return success
        }

        // Automatic Bluetooth <-> Wi-Fi failover
        val (primary, secondary, pName, sName) = if (preferredTransportType == TransportType.BLUETOOTH) {
            listOf(bluetoothTransport, wifiTransport, "BT", "WIFI")
        } else {
            listOf(wifiTransport, bluetoothTransport, "WIFI", "BT")
        }

        val primaryTransport = primary as Transport
        val secondaryTransport = secondary as Transport
        val primaryLabel = pName as String
        val secondaryLabel = sName as String

        var primarySent = false
        try {
            primarySent = primaryTransport.send(packet)
        } catch (_: Exception) {
            primarySent = false
        }

        if (primarySent) {
            org.sih.itantra.core.diagnostics.DiagnosticsRepository.recordTransportSent(primaryLabel)
            return true
        }

        // Primary transport failed or unavailable: Attempt fallback transport
        var secondarySent = false
        try {
            secondarySent = secondaryTransport.send(packet)
        } catch (_: Exception) {
            secondarySent = false
        }

        if (secondarySent) {
            org.sih.itantra.core.diagnostics.DiagnosticsRepository.recordTransportFailover(from = primaryLabel, to = secondaryLabel)
            org.sih.itantra.core.diagnostics.DiagnosticsRepository.recordTransportSent(secondaryLabel)
            return true
        }

        return false
    }

    suspend fun start() {
        _activeTransport.value.start()
    }

    suspend fun stop() {
        _activeTransport.value.stop()
    }

    suspend fun connectBluetooth(targetAddress: String? = null): Boolean {
        return bluetoothTransport.connectToBondedPeer(targetAddress)
    }

    fun getBondedBluetoothDevices(): List<PeerDevice> {
        return bluetoothTransport.getBondedDevices()
    }
}
