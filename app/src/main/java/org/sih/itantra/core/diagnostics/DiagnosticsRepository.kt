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
    val lastDistressStatus: String? = null,
    // DTN Store-and-Forward counters
    val dtnStored: Long = 0L,
    val dtnForwarded: Long = 0L,
    val dtnExpired: Long = 0L,
    val dtnDropped: Long = 0L,
    val dtnQueueSize: Int = 0,
    // Transport Failover counters
    val transportBtSent: Long = 0L,
    val transportWifiSent: Long = 0L,
    val failoverBtToWifi: Long = 0L,
    val failoverWifiToBt: Long = 0L,
    val activeTransportName: String = "BT / WIFI (AUTO)",
    // Adaptive Routing metrics
    val lastRouteQualityLabel: String = "GOOD",
    // Fragmentation & Reassembly counters
    val fragmentsSent: Long = 0L,
    val fragmentsReceived: Long = 0L,
    val messagesReassembled: Long = 0L,
    val reassemblyTimeouts: Long = 0L,
    // Delivery Receipt counters
    val deliveryAcksSent: Long = 0L,
    val deliveryAcksReceived: Long = 0L,
    val lastTransferId: Short? = null,
    val lastFragmentCount: Int? = null,
    val lastReassemblyLatencyMs: Double? = null,
    val lastDeliveryAckLatencyMs: Double? = null,
    val lastFragmentPayloadBytes: Int? = null,
    val lastTotalWireBytes: Int? = null,
    // Security / Authentication counters
    val authenticatedPacketsSent: Long = 0L,
    val authenticatedPacketsReceived: Long = 0L,
    val authenticationFailures: Long = 0L,
    val replayDrops: Long = 0L,
    val unknownKeyDrops: Long = 0L,
    val lastAuthGenMicros: Double? = null,
    val lastAuthVerifyMicros: Double? = null,
    val authTagSizeBytes: Int = org.sih.itantra.core.protocol.Packet.AUTH_TAG_SIZE_BYTES
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

    fun recordDtnStored(queueSize: Int) {
        val current = _state.value
        _state.value = current.copy(
            dtnStored = current.dtnStored + 1,
            dtnQueueSize = queueSize
        )
    }

    fun recordDtnForwarded(queueSize: Int) {
        val current = _state.value
        _state.value = current.copy(
            dtnForwarded = current.dtnForwarded + 1,
            dtnQueueSize = queueSize
        )
    }

    fun recordDtnExpired(queueSize: Int) {
        val current = _state.value
        _state.value = current.copy(
            dtnExpired = current.dtnExpired + 1,
            dtnQueueSize = queueSize
        )
    }

    fun recordDtnDropped() {
        val current = _state.value
        _state.value = current.copy(
            dtnDropped = current.dtnDropped + 1
        )
    }

    fun recordTransportSent(type: String) {
        val current = _state.value
        _state.value = if (type.contains("BT", ignoreCase = true)) {
            current.copy(transportBtSent = current.transportBtSent + 1)
        } else {
            current.copy(transportWifiSent = current.transportWifiSent + 1)
        }
    }

    fun recordTransportFailover(from: String, to: String) {
        val current = _state.value
        _state.value = if (from.contains("BT", ignoreCase = true) && to.contains("WIFI", ignoreCase = true)) {
            current.copy(failoverBtToWifi = current.failoverBtToWifi + 1)
        } else {
            current.copy(failoverWifiToBt = current.failoverWifiToBt + 1)
        }
    }

    fun recordRouteSelection(quality: String) {
        val current = _state.value
        _state.value = current.copy(lastRouteQualityLabel = quality)
    }

    fun setLastRouteQuality(quality: String) = recordRouteSelection(quality)

    fun recordFragmentsSent(count: Int, payloadBytes: Int, wireBytes: Int, transferId: Short) {
        val current = _state.value
        _state.value = current.copy(
            fragmentsSent = current.fragmentsSent + count,
            lastTransferId = transferId,
            lastFragmentCount = count,
            lastFragmentPayloadBytes = payloadBytes,
            lastTotalWireBytes = wireBytes
        )
    }

    fun recordFragmentReceived() {
        val current = _state.value
        _state.value = current.copy(fragmentsReceived = current.fragmentsReceived + 1)
    }

    fun recordMessageReassembled(fragmentCount: Int, latencyMs: Double, transferId: Short) {
        val current = _state.value
        _state.value = current.copy(
            messagesReassembled = current.messagesReassembled + 1,
            lastTransferId = transferId,
            lastFragmentCount = fragmentCount,
            lastReassemblyLatencyMs = latencyMs
        )
    }

    fun recordReassemblyTimeout() {
        val current = _state.value
        _state.value = current.copy(reassemblyTimeouts = current.reassemblyTimeouts + 1)
    }

    fun recordDeliveryAckSent() {
        val current = _state.value
        _state.value = current.copy(deliveryAcksSent = current.deliveryAcksSent + 1)
    }

    fun recordDeliveryAckReceived(transferId: Short, rttMs: Long) {
        val current = _state.value
        _state.value = current.copy(
            deliveryAcksReceived = current.deliveryAcksReceived + 1,
            lastDeliveryAckLatencyMs = rttMs.toDouble(),
            lastTransferId = transferId
        )
    }

    fun recordAuthPacketSent(genMicros: Double) {
        val current = _state.value
        _state.value = current.copy(
            authenticatedPacketsSent = current.authenticatedPacketsSent + 1,
            lastAuthGenMicros = genMicros
        )
    }

    fun recordAuthPacketReceived(verifyMicros: Double) {
        val current = _state.value
        _state.value = current.copy(
            authenticatedPacketsReceived = current.authenticatedPacketsReceived + 1,
            lastAuthVerifyMicros = verifyMicros
        )
    }

    fun recordAuthFailure() {
        val current = _state.value
        _state.value = current.copy(
            authenticationFailures = current.authenticationFailures + 1
        )
    }

    fun recordReplayDrop() {
        val current = _state.value
        _state.value = current.copy(
            replayDrops = current.replayDrops + 1
        )
    }

    fun recordUnknownKeyDrop() {
        val current = _state.value
        _state.value = current.copy(
            unknownKeyDrops = current.unknownKeyDrops + 1
        )
    }
}
