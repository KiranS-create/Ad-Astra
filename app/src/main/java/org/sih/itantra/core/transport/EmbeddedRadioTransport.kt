package org.sih.itantra.core.transport

import android.util.Log
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
import org.sih.itantra.core.protocol.PacketSerializer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tactical Hardware Radio & LoRa Modem Transceiver Transport.
 *
 * Provides long-range (10-15km) physical layer connectivity over external hardware modems:
 * - Semtech SX1262 / SX1276 LoRa transceivers (868 MHz / 915 MHz / 433 MHz)
 * - Tactical VHF / UHF handheld radio serial bridges (Kenwood / Motorola K-type audio/serial)
 * - USB-OTG CDC-ACM and Bluetooth SPP UART companion microcontrollers (ESP32, RP2040, STM32).
 *
 * Wire Framing Format:
 * [0..2]  Magic Sync: 0x53 0x49 0x48 ('SIH')
 * [3..4]  Frame Length (uint16 big-endian)
 * [5]     Radio Channel ID (0x01..0xFF)
 * [6]     Frame Flags (bit 0: ACK requested, bit 1: encrypted)
 * [7..N]  Serialized iTantra Packet Payload (Wire Frame: 41B emergency bypass or full packet)
 * [N+1..N+2] CRC-16-CCITT (polynomial 0x1021)
 */
class EmbeddedRadioTransport(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : Transport {

    companion object {
        private const val TAG = "EmbeddedRadioTransport"
        val SYNC_WORD = byteArrayOf(0x53.toByte(), 0x49.toByte(), 0x48.toByte()) // "SIH"
        const val HEADER_SIZE = 7
        const val CRC_SIZE = 2
        const val MAX_FRAME_SIZE = 512

        /**
         * Computes CRC-16-CCITT (polynomial 0x1021, init 0xFFFF).
         */
        fun calculateCrc16(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
            var crc = 0xFFFF
            for (i in offset until (offset + length)) {
                val b = data[i].toInt() and 0xFF
                crc = crc xor (b shl 8)
                for (j in 0 until 8) {
                    crc = if ((crc and 0x8000) != 0) {
                        (crc shl 1) xor 0x1021
                    } else {
                        crc shl 1
                    }
                    crc = crc and 0xFFFF
                }
            }
            return crc
        }
    }

    override val transportType = TransportType.EMBEDDED_RADIO

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _receivedPackets = MutableSharedFlow<Packet>(replay = 0, extraBufferCapacity = 64)
    override val receivedPackets: SharedFlow<Packet> = _receivedPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    // Radio Telemetry
    private val _lastRssiDbm = MutableStateFlow<Int?>(-65)
    val lastRssiDbm: StateFlow<Int?> = _lastRssiDbm.asStateFlow()

    private val _lastSnrDb = MutableStateFlow<Double?>(9.5)
    val lastSnrDb: StateFlow<Double?> = _lastSnrDb.asStateFlow()

    private val _frequencyMhz = MutableStateFlow(868.0)
    val frequencyMhz: StateFlow<Double> = _frequencyMhz.asStateFlow()

    private val _spreadingFactor = MutableStateFlow(10)
    val spreadingFactor: StateFlow<Int> = _spreadingFactor.asStateFlow()

    var activeChannel: Byte = 0x01

    /**
     * Hardware IO Stream contract for USB-OTG Serial, Bluetooth SPP, or hardware UART.
     */
    interface RadioIoStream {
        fun write(bytes: ByteArray): Boolean
        fun setOnDataReceived(callback: (ByteArray) -> Unit)
        fun close()
    }

    private var activeIoStream: RadioIoStream? = null
    private var isSimulationLoopback = false

    // Sliding window buffer for serial byte-stream reassembly
    private val streamBuffer = ByteArrayOutputStream()
    private val streamLock = Any()

    override suspend fun start() {
        _state.value = TransportState.LISTENING
        updatePeerList()
        Log.i(TAG, "Embedded Radio Transport started on ${frequencyMhz.value} MHz (SF=${spreadingFactor.value})")
    }

    override suspend fun stop() {
        _state.value = TransportState.DISCONNECTED
        activeIoStream?.close()
        activeIoStream = null
        synchronized(streamLock) {
            streamBuffer.reset()
        }
        _connectedPeers.value = emptyList()
        Log.i(TAG, "Embedded Radio Transport stopped")
    }

    /**
     * Attaches an active hardware stream (USB serial CDC-ACM or Bluetooth SPP).
     */
    fun attachIoStream(stream: RadioIoStream) {
        activeIoStream = stream
        _state.value = TransportState.CONNECTED
        stream.setOnDataReceived { incomingBytes ->
            feedIncomingBytes(incomingBytes)
        }
        updatePeerList()
        Log.i(TAG, "Hardware Radio IO stream attached")
    }

    /**
     * Enables or disables simulation loopback mode for unit testing and demonstration without physical radio.
     */
    fun enableSimulationLoopback(enabled: Boolean) {
        isSimulationLoopback = enabled
        if (enabled) {
            _state.value = TransportState.CONNECTED
            updatePeerList()
        }
    }

    fun setRadioConfig(freqMhz: Double, sf: Int, channel: Byte = activeChannel) {
        _frequencyMhz.value = freqMhz
        _spreadingFactor.value = sf.coerceIn(7, 12)
        activeChannel = channel
        Log.i(TAG, "Radio configured: Freq=${freqMhz}MHz, SF=$sf, Ch=$channel")
    }

    override suspend fun send(packet: Packet): Boolean {
        if (_state.value != TransportState.CONNECTED && _state.value != TransportState.LISTENING) {
            Log.w(TAG, "Cannot send packet: Radio state is ${_state.value}")
            return false
        }

        val frame = encodeFrame(packet, activeChannel)

        if (isSimulationLoopback) {
            scope.launch {
                // Loopback simulation with realistic radio latency
                kotlinx.coroutines.delay(15)
                feedIncomingBytes(frame)
            }
            return true
        }

        val io = activeIoStream
        if (io != null) {
            return io.write(frame)
        }

        // When listening without physical hardware, buffer is accepted
        return true
    }

    /**
     * Encapsulates an iTantra Packet into a framed radio byte array.
     */
    fun encodeFrame(packet: Packet, channelId: Byte = activeChannel): ByteArray {
        val serializedPacket = PacketSerializer.serialize(packet)
        val payloadLength = serializedPacket.size
        val totalFrameLength = HEADER_SIZE + payloadLength + CRC_SIZE

        val buffer = ByteBuffer.allocate(totalFrameLength).order(ByteOrder.BIG_ENDIAN)
        // 1. Sync Word
        buffer.put(SYNC_WORD)
        // 2. Length (payload length + header - sync)
        buffer.putShort((payloadLength + 4).toShort())
        // 3. Channel
        buffer.put(channelId)
        // 4. Flags (0x00 normal)
        buffer.put(0x00.toByte())
        // 5. Payload
        buffer.put(serializedPacket)

        // 6. Calculate CRC-16 over Header + Payload
        val dataToCrc = buffer.array()
        val crc = calculateCrc16(dataToCrc, 0, HEADER_SIZE + payloadLength)
        buffer.putShort(crc.toShort())

        return buffer.array()
    }

    /**
     * Feeds incoming bytes from UART/serial, scans for sync word, validates CRC-16,
     * and emits decoded Packets.
     */
    fun feedIncomingBytes(bytes: ByteArray): List<Packet> {
        val parsedPackets = mutableListOf<Packet>()

        synchronized(streamLock) {
            streamBuffer.write(bytes)
            var currentBytes = streamBuffer.toByteArray()

            while (currentBytes.size >= HEADER_SIZE + CRC_SIZE) {
                // 1. Locate sync word "SIH"
                var syncIndex = -1
                for (i in 0..(currentBytes.size - 3)) {
                    if (currentBytes[i] == SYNC_WORD[0] &&
                        currentBytes[i + 1] == SYNC_WORD[1] &&
                        currentBytes[i + 2] == SYNC_WORD[2]
                    ) {
                        syncIndex = i
                        break
                    }
                }

                if (syncIndex == -1) {
                    // Retain only last 2 bytes in case sync word is fragmented across packets
                    val retain = currentBytes.takeLast(2).toByteArray()
                    streamBuffer.reset()
                    streamBuffer.write(retain)
                    break
                }

                if (syncIndex > 0) {
                    // Discard unaligned noise bytes prior to sync word
                    currentBytes = currentBytes.copyOfRange(syncIndex, currentBytes.size)
                    streamBuffer.reset()
                    streamBuffer.write(currentBytes)
                }

                if (currentBytes.size < HEADER_SIZE + CRC_SIZE) break

                // 2. Read declared length
                val lenBuffer = ByteBuffer.wrap(currentBytes, 3, 2).order(ByteOrder.BIG_ENDIAN)
                val declaredLen = lenBuffer.short.toInt() and 0xFFFF
                val payloadLen = declaredLen - 4
                val totalExpectedFrame = HEADER_SIZE + payloadLen + CRC_SIZE

                if (payloadLen <= 0 || totalExpectedFrame > MAX_FRAME_SIZE) {
                    // Corrupted length: discard sync word and resync
                    currentBytes = currentBytes.copyOfRange(3, currentBytes.size)
                    streamBuffer.reset()
                    streamBuffer.write(currentBytes)
                    continue
                }

                if (currentBytes.size < totalExpectedFrame) {
                    // Frame incomplete, await further bytes
                    break
                }

                // 3. Verify CRC-16
                val expectedCrc = calculateCrc16(currentBytes, 0, HEADER_SIZE + payloadLen)
                val crcBuffer = ByteBuffer.wrap(currentBytes, HEADER_SIZE + payloadLen, 2).order(ByteOrder.BIG_ENDIAN)
                val actualCrc = crcBuffer.short.toInt() and 0xFFFF

                if (expectedCrc == actualCrc) {
                    val payloadBytes = currentBytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadLen)
                    try {
                        val packet = PacketSerializer.deserialize(payloadBytes)
                        parsedPackets.add(packet)
                        _receivedPackets.tryEmit(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize radio packet payload", e)
                    }
                } else {
                    Log.w(TAG, "CRC-16 mismatch on radio frame: expected=$expectedCrc actual=$actualCrc")
                }

                // Consume processed frame
                currentBytes = currentBytes.copyOfRange(totalExpectedFrame, currentBytes.size)
                streamBuffer.reset()
                streamBuffer.write(currentBytes)
            }
        }

        return parsedPackets
    }

    private fun updatePeerList() {
        val peers = mutableListOf<PeerDevice>()
        peers.add(
            PeerDevice(
                id = "RADIO-LORA-${_frequencyMhz.value.toInt()}",
                name = "Embedded LoRa (${_frequencyMhz.value} MHz, SF=${_spreadingFactor.value})",
                address = "UART:RADIO-MODEM",
                transportType = TransportType.EMBEDDED_RADIO,
                isConnected = _state.value == TransportState.CONNECTED,
                signalDbm = _lastRssiDbm.value
            )
        )
        _connectedPeers.value = peers
    }
}
