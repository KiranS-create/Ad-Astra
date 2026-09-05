package org.sih.itantra.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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

                acceptJob = CoroutineScope(dispatcher).launch {
                    while (isActive && isRunning.get()) {
                        try {
                            val socket = serverSocket?.accept() ?: break
                            handleConnectedSocket(socket)
                        } catch (e: Exception) {
                            if (isRunning.get()) {
                                Log.w(tag, "Accept loop interrupted", e)
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to start Bluetooth server", e)
                _state.value = TransportState.ERROR
            }
        }
    }

    private fun handleConnectedSocket(socket: BluetoothSocket) {
        activeSocket = socket
        outputStream = socket.outputStream
        inputStream = socket.inputStream
        _state.value = TransportState.CONNECTED

        val device = socket.remoteDevice
        val peer = PeerDevice(
            id = device.address,
            name = device.name ?: "BT-Device",
            address = device.address,
            transportType = TransportType.BLUETOOTH,
            isConnected = true
        )
        _connectedPeers.value = listOf(peer)

        readJob = CoroutineScope(dispatcher).launch {
            val buffer = ByteArray(2048)
            while (isActive && isRunning.get()) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        val raw = buffer.copyOf(bytesRead)
                        val packet = PacketSerializer.deserialize(raw)
                        _receivedPackets.emit(packet)
                    } else if (bytesRead < 0) {
                        break
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Bluetooth read error", e)
                    break
                }
            }
            _state.value = TransportState.LISTENING
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
                Log.e(tag, "Error closing Bluetooth sockets", e)
            } finally {
                serverSocket = null
                activeSocket = null
                outputStream = null
                inputStream = null
                _state.value = TransportState.DISCONNECTED
                _connectedPeers.value = emptyList()
            }
        }
    }

    override suspend fun send(packet: Packet): Boolean = withContext(dispatcher) {
        val out = outputStream ?: return@withContext false
        return@withContext try {
            val bytes = PacketSerializer.serialize(packet)
            out.write(bytes)
            out.flush()
            true
        } catch (e: Exception) {
            Log.e(tag, "Bluetooth send failed", e)
            false
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
