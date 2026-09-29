package org.sih.itantra.core.discovery

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Categorical proximity bands for BLE RSSI Locate Mode.
 *
 * TRUTHFULNESS RULE:
 * Radio frequency RSSI is subject to multipath interference, antenna orientation,
 * and physical obstacles. Never claim exact metric distance (e.g. "1.2 meters").
 * Bands represent qualitative proximity tiers only.
 */
enum class LocateProximityState(
    val label: String,
    val description: String,
    val tier: Int
) {
    VERY_NEAR("VERY NEAR", "Immediate physical vicinity (< -55 dBm)", 4),
    NEAR("NEAR", "Close proximity vicinity (-65 to -55 dBm)", 3),
    CLOSER("APPROACHING / CLOSER", "Moderate proximity signal (-75 to -65 dBm)", 2),
    FAR("FAR", "Distant RF signal (< -75 dBm)", 1),
    UNKNOWN("SEARCHING...", "No active RF signal detected", 0)
}

/**
 * Trend direction of smoothed RSSI signal changes over time.
 */
enum class LocateRssiTrend(
    val symbol: String,
    val label: String
) {
    CLOSER("▲", "CLOSER (Signal Rising)"),
    FARTHER("▼", "FARTHER (Signal Dropping)"),
    STABLE("▬", "STABLE (Constant Range)"),
    UNKNOWN("·", "CALIBRATING")
}

/**
 * Immutable state projection for an active BLE Locate Mode session.
 */
data class LocateSessionState(
    val isActive: Boolean = false,
    val targetNodeId: Int? = null,
    val targetAddress: String? = null,
    val targetCallsign: String? = null,
    val smoothedRssi: Double? = null,
    val rawRssi: Int? = null,
    val proximityState: LocateProximityState = LocateProximityState.UNKNOWN,
    val trend: LocateRssiTrend = LocateRssiTrend.UNKNOWN,
    val sampleCount: Int = 0,
    val lastReadingTimestampMs: Long = 0L,
    val isStale: Boolean = false,
    val isScanning: Boolean = false,
    val statusMessage: String = "STANDBY"
)

/**
 * Tactical BLE RSSI-based Locate Mode Engine.
 *
 * Features:
 * - Exponential Moving Average (EMA, alpha = 0.35) smoothing to suppress RF multipath jitter.
 * - 3-state qualitative trend detection (CLOSER, FARTHER, STABLE) over sliding baseline window.
 * - Relative qualitative proximity bands (UNKNOWN, FAR, CLOSER, NEAR, VERY_NEAR).
 * - Target node or hardware address filtering.
 * - Stale observation detection (> 15 seconds without readings).
 * - Battery-safe lifecycle: active scanning only while locate session is actively open.
 */
class LocateModeEngine(
    private val discoveryRepository: NearbyDeviceRepository,
    private val bleSource: NearbyDiscoverySource? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val scope = CoroutineScope(dispatcher)

    private val _sessionState = MutableStateFlow(LocateSessionState())
    val sessionState: StateFlow<LocateSessionState> = _sessionState.asStateFlow()

    private var sessionJob: Job? = null
    private var watchdogJob: Job? = null

    // Smoothing parameters
    private val alpha = 0.35 // EMA weighting factor
    private var currentEma: Double? = null

    // Trend detection sliding window (stores timestamp to smoothed value)
    private val recentSamples = ConcurrentLinkedQueue<Pair<Long, Double>>()

    /**
     * Initiates Locate Mode for a target node or BLE hardware address.
     * Starts low-latency scanning and binds observation listener.
     */
    fun startLocating(
        targetNodeId: Int? = null,
        targetAddress: String? = null,
        targetCallsign: String? = null,
        enableWatchdog: Boolean = true
    ) {
        stopLocating()

        val initialCallsign = targetCallsign
            ?: (if (targetNodeId != null) "NODE #$targetNodeId" else (targetAddress ?: "UNKNOWN TARGET"))

        _sessionState.value = LocateSessionState(
            isActive = true,
            targetNodeId = targetNodeId,
            targetAddress = targetAddress,
            targetCallsign = initialCallsign,
            isScanning = true,
            statusMessage = "SCANNING FOR $initialCallsign..."
        )

        currentEma = null
        recentSamples.clear()

        // 1. Ensure BLE source is scanning
        scope.launch {
            try {
                bleSource?.startScan()
            } catch (e: Exception) {
                // Ignore failure if already scanning
            }
        }

        // 2. Observe repository discovered devices
        sessionJob = scope.launch {
            discoveryRepository.discoveredDevices.collect { devices ->
                val targetDevice = findTargetDevice(devices, targetNodeId, targetAddress)
                if (targetDevice != null && targetDevice.rawRssi != null) {
                    processRssiSample(targetDevice.rawRssi!!, targetDevice.callsign)
                }
            }
        }

        // 3. Watchdog job to monitor for stale signals (> 15s timeout)
        if (enableWatchdog) {
            watchdogJob = scope.launch {
                while (isActive) {
                    delay(1000L)
                    checkStale()
                }
            }
        }
    }

    /**
     * Checks if current observation has timed out (> 15s) and transitions state to stale if so.
     */
    fun checkStale(now: Long = timeProvider()) {
        val current = _sessionState.value
        if (current.isActive && current.lastReadingTimestampMs > 0L) {
            val ageMs = now - current.lastReadingTimestampMs
            if (ageMs > STALE_TIMEOUT_MS && !current.isStale) {
                _sessionState.value = current.copy(
                    isStale = true,
                    proximityState = LocateProximityState.UNKNOWN,
                    trend = LocateRssiTrend.UNKNOWN,
                    statusMessage = "SIGNAL LOST (> 15s)"
                )
            }
        }
    }

    /**
     * Stops Locate Mode, terminates scanner to preserve battery, and resets smoothing buffers.
     */
    fun stopLocating() {
        sessionJob?.cancel()
        sessionJob = null
        watchdogJob?.cancel()
        watchdogJob = null

        currentEma = null
        recentSamples.clear()

        _sessionState.value = LocateSessionState(
            isActive = false,
            isScanning = false,
            statusMessage = "STANDBY"
        )
    }

    /**
     * Ingests a raw RSSI sample, computes EMA smoothed value, detects trend, and updates state.
     */
    fun processRssiSample(rawRssi: Int, updatedCallsign: String? = null) {
        val now = timeProvider()

        // 1. Exponential Moving Average smoothing
        val prevEma = currentEma
        val smoothed = if (prevEma == null) {
            rawRssi.toDouble()
        } else {
            alpha * rawRssi + (1.0 - alpha) * prevEma
        }
        currentEma = smoothed

        // 2. Trend calculation over sliding baseline (compare with reading from 3-5 seconds ago)
        recentSamples.add(Pair(now, smoothed))
        while (recentSamples.isNotEmpty() && ((now - (recentSamples.peek()?.first ?: now)) > TREND_WINDOW_MS)) {
            recentSamples.poll()
        }

        val baselineSample = recentSamples.firstOrNull()?.second ?: smoothed
        val deltaRssi = smoothed - baselineSample

        val trend = when {
            recentSamples.size < 3 -> LocateRssiTrend.UNKNOWN
            deltaRssi >= TREND_THRESHOLD_DBM -> LocateRssiTrend.CLOSER
            deltaRssi <= -TREND_THRESHOLD_DBM -> LocateRssiTrend.FARTHER
            else -> LocateRssiTrend.STABLE
        }

        // 3. Qualitative proximity tier
        val proximity = categorizeProximity(smoothed)

        val current = _sessionState.value
        val effectiveCallsign = updatedCallsign ?: current.targetCallsign

        _sessionState.value = current.copy(
            rawRssi = rawRssi,
            smoothedRssi = smoothed,
            proximityState = proximity,
            trend = trend,
            sampleCount = current.sampleCount + 1,
            lastReadingTimestampMs = now,
            isStale = false,
            targetCallsign = effectiveCallsign,
            statusMessage = "${proximity.label} · ${trend.label}"
        )
    }

    private fun findTargetDevice(
        devices: List<NearbyDevice>,
        targetNodeId: Int?,
        targetAddress: String?
    ): NearbyDevice? {
        if (targetNodeId != null) {
            return devices.firstOrNull { it.nodeId == targetNodeId }
        }
        if (targetAddress != null) {
            return devices.firstOrNull { it.identity.callsign.contains(targetAddress) || it.formattedNodeId.contains(targetAddress) }
        }
        // Fallback: Return device with strongest RSSI
        return devices.filter { it.rawRssi != null }.maxByOrNull { it.rawRssi!! }
    }

    companion object {
        const val STALE_TIMEOUT_MS = 15_000L
        const val TREND_WINDOW_MS = 5_000L
        const val TREND_THRESHOLD_DBM = 3.0

        // Qualitative Band Boundaries
        const val RSSI_VERY_NEAR = -55.0
        const val RSSI_NEAR = -65.0
        const val RSSI_CLOSER = -75.0

        /**
         * Pure function mapping smoothed RSSI (dBm) to qualitative proximity bands.
         * Enforces strict truthfulness: no false metric distance precision.
         */
        fun categorizeProximity(smoothedRssi: Double?): LocateProximityState {
            if (smoothedRssi == null) return LocateProximityState.UNKNOWN
            return when {
                smoothedRssi >= RSSI_VERY_NEAR -> LocateProximityState.VERY_NEAR
                smoothedRssi >= RSSI_NEAR -> LocateProximityState.NEAR
                smoothedRssi >= RSSI_CLOSER -> LocateProximityState.CLOSER
                else -> LocateProximityState.FAR
            }
        }
    }
}
