package org.sih.itantra.core.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticsState(
    val packetsSent: Long = 0L,
    val packetsReceived: Long = 0L,
    val totalBytesTransmitted: Long = 0L,
    val totalRawAudioBytesSaved: Long = 0L,
    val lastLatency: LatencyMetrics = LatencyMetrics(),
    val lastBandwidth: BandwidthMetrics = BandwidthMetrics(),
    val overallSavingsPercent: Double? = null,
    val activeSoC: String = "ARM64-v8a",
    val ramUsageMb: Float = ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024f * 1024f)),
    val packetsRelayed: Long = 0L,
    val relayDuplicatesDropped: Long = 0L,
    val relayTtlExpired: Long = 0L
)

object DiagnosticsRepository {
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    fun recordTransmission(packetBytes: Int, rawAudioBytes: Long, latency: LatencyMetrics, bandwidth: BandwidthMetrics) {
        val current = _state.value
        val newSent = current.packetsSent + 1
        val newTransmittedBytes = current.totalBytesTransmitted + packetBytes
        val newSavedBytes = current.totalRawAudioBytesSaved + maxOf(0L, rawAudioBytes - packetBytes)
        val overallPercent = if (newSavedBytes + newTransmittedBytes > 0 && newSavedBytes > 0) {
            (newSavedBytes.toDouble() / (newSavedBytes + newTransmittedBytes).toDouble()) * 100.0
        } else null

        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)

        _state.value = current.copy(
            packetsSent = newSent,
            totalBytesTransmitted = newTransmittedBytes,
            totalRawAudioBytesSaved = newSavedBytes,
            lastLatency = latency,
            lastBandwidth = bandwidth,
            overallSavingsPercent = overallPercent?.coerceIn(0.0, 99.9),
            ramUsageMb = usedRamMb
        )
    }

    fun recordReception(packetBytes: Int) {
        val current = _state.value
        _state.value = current.copy(
            packetsReceived = current.packetsReceived + 1
        )
    }

    fun recordRelayForward() {
        val current = _state.value
        _state.value = current.copy(
            packetsRelayed = current.packetsRelayed + 1
        )
    }

    fun recordRelayDuplicateDrop() {
        val current = _state.value
        _state.value = current.copy(
            relayDuplicatesDropped = current.relayDuplicatesDropped + 1
        )
    }

    fun recordRelayTtlExpired() {
        val current = _state.value
        _state.value = current.copy(
            relayTtlExpired = current.relayTtlExpired + 1
        )
    }
}
