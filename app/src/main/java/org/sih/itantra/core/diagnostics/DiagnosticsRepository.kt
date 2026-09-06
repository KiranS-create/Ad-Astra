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
    // Mesh relay counters
    val packetsRelayed: Long = 0L,
    val relayDuplicatesDropped: Long = 0L,
    val relayTtlExpired: Long = 0L,
    // MANET routing counters
    val manetHelloTx: Long = 0L,
    val manetHelloRx: Long = 0L,
    val manetNeighborsDiscovered: Long = 0L,
    val manetRreqTx: Long = 0L,
    val manetRreqRx: Long = 0L,
    val manetRrepTx: Long = 0L,
    val manetRrepRx: Long = 0L,
    val manetRoutesEstablished: Long = 0L,
    val manetRoutesExpired: Long = 0L,
    val manetRerrCount: Long = 0L,
    val manetPacketsRouted: Long = 0L,
    val manetRouteRediscoveries: Long = 0L,
    // Emergency & Distress counters
    val distressSent: Long = 0L,
    val distressReceived: Long = 0L,
    val distressLocationAttached: Long = 0L,
    val distressLocationUnavailable: Long = 0L,
    val lastDistressSource: Int? = null,
    val lastDistressSeq: Short? = null,
    val lastDistressHops: Int? = null,
    val lastDistressStatus: String? = null
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

    /** Snapshot current MANET stats from ManetRouter into diagnostics state. */
    fun recordManetStats(stats: org.sih.itantra.core.mesh.ManetStats) {
        val current = _state.value
        _state.value = current.copy(
            manetHelloTx            = stats.helloTx,
            manetHelloRx            = stats.helloRx,
            manetNeighborsDiscovered = stats.neighborsDiscovered,
            manetRreqTx             = stats.rreqTx,
            manetRreqRx             = stats.rreqRx,
            manetRrepTx             = stats.rrepTx,
            manetRrepRx             = stats.rrepRx,
            manetRoutesEstablished  = stats.routesEstablished,
            manetRoutesExpired      = stats.routesExpired,
            manetRerrCount          = stats.rerrCount,
            manetPacketsRouted      = stats.packetsRouted,
            manetRouteRediscoveries = stats.routeRediscoveries
        )
    }

    fun recordDistressSent(hasLocation: Boolean, seq: Short = 0) {
        val current = _state.value
        _state.value = current.copy(
            distressSent = current.distressSent + 1,
            distressLocationAttached = if (hasLocation) current.distressLocationAttached + 1 else current.distressLocationAttached,
            distressLocationUnavailable = if (!hasLocation) current.distressLocationUnavailable + 1 else current.distressLocationUnavailable,
            lastDistressSeq = seq,
            lastDistressStatus = if (hasLocation) "SENT · LOCATION ATTACHED" else "SENT · LOCATION NOT ATTACHED"
        )
    }

    fun recordDistressReceived(hasLocation: Boolean, source: Int, hops: Int, seq: Short = 0) {
        val current = _state.value
        _state.value = current.copy(
            distressReceived = current.distressReceived + 1,
            distressLocationAttached = if (hasLocation) current.distressLocationAttached + 1 else current.distressLocationAttached,
            distressLocationUnavailable = if (!hasLocation) current.distressLocationUnavailable + 1 else current.distressLocationUnavailable,
            lastDistressSource = source,
            lastDistressSeq = seq,
            lastDistressHops = hops,
            lastDistressStatus = if (hasLocation) "RECEIVED · LOCATION ATTACHED" else "RECEIVED · NO LOCATION"
        )
    }
}
