package org.sih.itantra.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
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
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Point-to-point Bluetooth Classic RFCOMM SPP socket transport.
 * Allows direct phone-to-phone data exchange without any Wi-Fi infrastructure.
 */
class BluetoothTransport(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Transport {

    private val tag = "BluetoothTransport"
    override val transportType = TransportType.BLUETOOTH

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _receivedPackets = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 64)
    override val receivedPackets: SharedFlow<Packet> = _receivedPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private var acceptJob: Job? = null
    private var readJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        withContext(dispatcher) {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.w(tag, "Bluetooth adapter not available or disabled")
                _state.value = TransportState.ERROR
                return@withContext
            }

            try {
                _state.value = TransportState.CONNECTING
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("iTantraRadio", SPP_UUID)
                isRunning.set(true)
                _state.value = TransportState.LISTENING
                Log.i(tag, "Bluetooth RFCOMM server listening on SPP_UUID $SPP_UUID ('iTantraRadio')")

                acceptJob?.cancel()
                acceptJob = CoroutineScope(dispatcher).launch {
                    while (isActive && isRunning.get()) {
                        try {
                            val socket = serverSocket?.accept() ?: break
                            val remoteAddr = try { socket.remoteDevice?.address } catch (e: SecurityException) { "unknown" }
                            Log.i(tag, "Incoming RFCOMM connection accepted from $remoteAddr")
                            handleConnectedSocket(socket)
                        } catch (e: Exception) {
                            if (isRunning.get()) {
                                Log.w(tag, "Accept loop interrupted: ${e.message}")
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to start Bluetooth server: ${e.message}", e)
                _state.value = TransportState.ERROR
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<PeerDevice> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(tag, "Bluetooth adapter not available or disabled when querying bonded devices")
            return emptyList()
        }
        return try {
            bluetoothAdapter.bondedDevices?.map { device ->
                val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
                val isConnected = (activeSocket?.remoteDevice?.address == device.address && activeSocket?.isConnected == true)
                PeerDevice(
                    id = device.address,
                    name = name,
                    address = device.address,
                    transportType = TransportType.BLUETOOTH,
                    isConnected = isConnected
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error querying bonded devices: ${e.message}", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(targetAddress: String): Boolean = withContext(dispatcher) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(tag, "Connect attempt failed: Bluetooth adapter unavailable or disabled")
            _state.value = TransportState.ERROR
            return@withContext false
        }

        Log.i(tag, "Connecting to target device: $targetAddress...")
        _state.value = TransportState.CONNECTING

        try {
            bluetoothAdapter.cancelDiscovery()
        } catch (e: Exception) {
            Log.w(tag, "cancelDiscovery failed: ${e.message}")
        }

        try {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(targetAddress)
            val devName = try { device.name ?: targetAddress } catch (e: SecurityException) { targetAddress }
            Log.i(tag, "Connect attempt to '$devName' ($targetAddress) using SPP_UUID $SPP_UUID")

            try {
                activeSocket?.close()
                outputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // ignore
            }

            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            Log.i(tag, "RFCOMM socket established successfully with '$devName' ($targetAddress)")
            handleConnectedSocket(socket)
            return@withContext true
        } catch (e: Exception) {
            Log.e(tag, "Connect failed to device $targetAddress: ${e.message}", e)
            _state.value = if (serverSocket != null) TransportState.LISTENING else TransportState.ERROR
            return@withContext false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToBondedPeer(targetAddress: String? = null): Boolean = withContext(dispatcher) {
        val bonded = getBondedDevices()
        if (bonded.isEmpty()) {
            Log.w(tag, "No bonded peer found! Please pair Phone A and Phone B in Android Settings first.")
            return@withContext false
        }

        val target = if (!targetAddress.isNullOrBlank()) {
            bonded.firstOrNull { it.address.equals(targetAddress, ignoreCase = true) || it.name.contains(targetAddress, ignoreCase = true) }
        } else {
            bonded.firstOrNull()
        }

        if (target == null) {
            Log.w(tag, "Target device '$targetAddress' not found in bonded devices: ${bonded.map { "${it.name} (${it.address})" }}")
            return@withContext false
        }

        return@withContext connect(target.address)
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectedSocket(socket: BluetoothSocket) {
        activeSocket = socket
        outputStream = socket.outputStream
        inputStream = socket.inputStream
        _state.value = TransportState.CONNECTED

        val device = socket.remoteDevice
        val devName = try { device.name ?: "BT-Device" } catch (e: SecurityException) { "BT-Device" }
        Log.i(tag, "BT_CONNECTED: transport=BLUETOOTH, device='$devName', address='${device.address}'")

        val peer = PeerDevice(
            id = device.address,
            name = devName,
            address = device.address,
            transportType = TransportType.BLUETOOTH,
            isConnected = true
        )
        _connectedPeers.value = listOf(peer)

        readJob?.cancel()
        readJob = CoroutineScope(dispatcher).launch {
            val buffer = ByteArray(4096)
            val streamBuffer = java.io.ByteArrayOutputStream()
            while (isActive && isRunning.get()) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        streamBuffer.write(buffer, 0, bytesRead)

                        var buf = streamBuffer.toByteArray()
                        var offset = 0
                        while (buf.size - offset >= Packet.MIN_PACKET_SIZE) {
                            val magic = ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
                            if (magic != (Packet.MAGIC.toInt() and 0xFFFF)) {
                                offset++
                                continue
                            }

                            if (buf.size - offset < Packet.HEADER_SIZE_BYTES) {
                                break
                            }

                            val flags = buf[offset + 6].toInt()
                            val hasLocation = (flags and Packet.FLAG_HAS_LOCATION) != 0
                            val locBytes = if (hasLocation) Packet.LOCATION_SIZE_BYTES else 0
                            val hasAuth = (flags and Packet.FLAG_AUTHENTICATED) != 0
                            val authBytes = if (hasAuth) Packet.AUTH_TAG_SIZE_BYTES else 0
                            val payloadLen = ((buf[offset + 26].toInt() and 0xFF) shl 8) or (buf[offset + 27].toInt() and 0xFF)
                            val totalExpected = Packet.HEADER_SIZE_BYTES + locBytes + payloadLen + authBytes + Packet.CRC_SIZE_BYTES

                            if (buf.size - offset < totalExpected) {
                                break
                            }

                            val packetBytes = buf.copyOfRange(offset, offset + totalExpected)
                            offset += totalExpected

                            try {
                                val packet = PacketSerializer.deserialize(packetBytes)
                                Log.i(tag, "BT_RECEIVE: transport=BLUETOOTH, seq=${packet.sequenceNumber}, type=${packet.msgType}, bytes=${packetBytes.size}, from=${device.address}")
                                _receivedPackets.emit(packet)
                            } catch (e: Exception) {
                                Log.w(tag, "BT_ERROR: Malformed packet in stream from ${device.address}: ${e.message}")
                            }
                        }

                        val remaining = buf.size - offset
                        streamBuffer.reset()
                        if (remaining > 0) {
                            streamBuffer.write(buf, offset, remaining)
                        }
                    } else if (bytesRead < 0) {
                        Log.w(tag, "BT_DISCONNECTED: Bluetooth stream reached EOF from ${device.address}")
                        break
                    }
                } catch (e: java.io.IOException) {
                    if (isRunning.get()) {
                        Log.w(tag, "BT_DISCONNECTED: Bluetooth socket IO exception from ${device.address}: ${e.message}")
                    }
                    break
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(tag, "BT_ERROR: Unexpected error in read loop: ${e.message}", e)
                    }
                }
            }
            Log.i(tag, "BT_DISCONNECTED: Bluetooth session ended for ${device.address}")
            activeSocket = null
            outputStream = null
            inputStream = null
            _state.value = if (serverSocket != null) TransportState.LISTENING else TransportState.DISCONNECTED
            _connectedPeers.value = emptyList()
        }
    }

    override suspend fun stop() {
        withContext(dispatcher) {
            isRunning.set(false)
            acceptJob?.cancel()
            readJob?.cancel()
            try {
                serverSocket?.close()
                activeSocket?.close()
                outputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {
                Log.e(tag, "BT_ERROR: Error closing Bluetooth sockets: ${e.message}", e)
            } finally {
                serverSocket = null
                activeSocket = null
                outputStream = null
                inputStream = null
                _state.value = TransportState.DISCONNECTED
                _connectedPeers.value = emptyList()
                Log.i(tag, "BT_DISCONNECTED: BluetoothTransport stopped")
            }
        }
    }

    override suspend fun send(packet: Packet): Boolean = withContext(dispatcher) {
        val out = outputStream
        if (out == null) {
            Log.w(tag, "BT_ERROR: Bluetooth send failed: outputStream is null (no connected RFCOMM socket)")
            return@withContext false
        }
        return@withContext try {
            val bytes = PacketSerializer.serialize(packet)
            out.write(bytes)
            out.flush()
            val remoteAddr = try { activeSocket?.remoteDevice?.address ?: "unknown" } catch (e: SecurityException) { "unknown" }
            Log.i(tag, "BT_SEND: transport=BLUETOOTH, seq=${packet.sequenceNumber}, type=${packet.msgType}, bytes=${bytes.size}, to=$remoteAddr")
            true
        } catch (e: Exception) {
            Log.e(tag, "BT_ERROR: Bluetooth send failed: ${e.message}", e)
            false
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
