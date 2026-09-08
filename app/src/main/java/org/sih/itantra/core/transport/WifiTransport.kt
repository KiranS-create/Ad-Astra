package org.sih.itantra.core.transport

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local offline Wi-Fi transport.
 * Operates over UDP broadcast on port 42888 across local ad-hoc networks or mobile hotspots.
 * Zero dependency on internet access, external routers, or cloud servers.
 */
class WifiTransport(
    private val context: Context,
    private val port: Int = DEFAULT_PORT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Transport {

    private val tag = "WifiTransport"
    override val transportType = TransportType.WIFI

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _receivedPackets = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 64)
    override val receivedPackets: SharedFlow<Packet> = _receivedPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private var socket: DatagramSocket? = null
    private var listenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val isRunning = AtomicBoolean(false)

    override suspend fun start() {
        withContext(dispatcher) {
            if (isRunning.get()) return@withContext

            try {
                _state.value = TransportState.CONNECTING
                acquireMulticastLock()

                socket = DatagramSocket(port).apply {
                    broadcast = true
                    reuseAddress = true
                }

                isRunning.set(true)
                _state.value = TransportState.LISTENING
                Log.i(tag, "Wi-Fi UDP socket listening on port $port")

                listenJob = CoroutineScope(dispatcher).launch {
                    val buffer = ByteArray(4096)
                    while (isActive && isRunning.get()) {
                        try {
                            val datagram = DatagramPacket(buffer, buffer.size)
                            socket?.receive(datagram)

                            val rawBytes = buffer.copyOf(datagram.length)
                            val packet = PacketSerializer.deserialize(rawBytes)

                            // Update discovered peers
                            val senderIp = datagram.address.hostAddress ?: "Unknown"
                            updatePeer(senderIp, packet.sourceDeviceId)

                            _receivedPackets.emit(packet)
                        } catch (e: Exception) {
                            if (isRunning.get()) {
                                Log.w(tag, "Error receiving UDP datagram", e)
                            }
                        }
                    }
                }
                _state.value = TransportState.CONNECTED
            } catch (e: Exception) {
                Log.e(tag, "Failed to start Wi-Fi transport", e)
                _state.value = TransportState.ERROR
            }
        }
    }

    override suspend fun stop() {
        withContext(dispatcher) {
            isRunning.set(false)
            listenJob?.cancel()
            listenJob = null

            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e(tag, "Error closing UDP socket", e)
            } finally {
                socket = null
                releaseMulticastLock()
                _state.value = TransportState.DISCONNECTED
                Log.i(tag, "Wi-Fi transport stopped")
            }
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val broadcastList = mutableListOf<InetAddress>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        broadcastList.add(broadcast)
                    }
                }
            }
            if (broadcastList.isEmpty()) {
                broadcastList.add(InetAddress.getByName("255.255.255.255"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error determining broadcast addresses", e)
            if (broadcastList.isEmpty()) {
                try {
                    broadcastList.add(InetAddress.getByName("255.255.255.255"))
                } catch (_: Exception) {}
            }
        }
        return broadcastList.distinct()
    }

    override suspend fun send(packet: Packet): Boolean = withContext(dispatcher) {
        if (!isRunning.get() || socket == null) {
            Log.w(tag, "Cannot send: Wi-Fi transport is not running")
            return@withContext false
        }

        return@withContext try {
            val bytes = PacketSerializer.serialize(packet)
            val targets = getBroadcastAddresses()
            for (target in targets) {
                try {
                    val datagram = DatagramPacket(bytes, bytes.size, target, port)
                    socket?.send(datagram)
                } catch (e: Exception) {
                    Log.w(tag, "Failed to send to $target: ${e.message}")
                }
            }
            Log.d(tag, "Transmitted ${bytes.size} bytes over Wi-Fi broadcast to ${targets.size} targets")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to send packet over Wi-Fi", e)
            false
        }
    }

    private fun updatePeer(ip: String, deviceId: Int) {
        val current = _connectedPeers.value.toMutableList()
        val peerId = "WIFI-$deviceId"
        if (current.none { it.id == peerId }) {
            current.add(
                PeerDevice(
                    id = peerId,
                    name = "Radio Node #$deviceId",
                    address = ip,
                    transportType = TransportType.WIFI,
                    isConnected = true
                )
            )
            _connectedPeers.value = current
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("iTantraMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error releasing multicast lock", e)
        } finally {
            multicastLock = null
        }
    }

    companion object {
        const val DEFAULT_PORT = 42888
    }
}
