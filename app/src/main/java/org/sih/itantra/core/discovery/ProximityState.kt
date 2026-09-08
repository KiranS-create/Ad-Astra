package org.sih.itantra.core.discovery

/**
 * Categorical proximity bands representing physical radio/beacon proximity.
 *
 * IMPORTANT DESIGN RULE:
 * BLE RSSI is an approximate proximity indicator, NOT an exact distance measurement.
 * Never claim exact centimeter readings (e.g. "10 cm") from RF signals.
 * Proximity is expressed truthfully via calibrated qualitative bands.
 */
enum class ProximityState(val label: String, val level: Int, val description: String) {
    VERY_CLOSE("VERY CLOSE", 4, "Immediate physical vicinity (high signal strength)"),
    NEARBY("NEARBY", 3, "Nearby proximity signal (moderate signal strength)"),
    FAR("PROXIMITY SIGNAL (FAR)", 2, "Distant proximity signal (low signal strength)"),
    APPROXIMATE("PROXIMITY SIGNAL", 1, "Uncalibrated / coarse proximity signal"),
    UNKNOWN("UNKNOWN", 0, "No proximity telemetry available")
}

/**
 * Qualitative signal strength level corresponding to RF field intensity.
 */
enum class SignalLevel(val label: String) {
    STRONG("SIGNAL STRONG"),
    MODERATE("SIGNAL MODERATE"),
    WEAK("SIGNAL WEAK"),
    UNKNOWN("SIGNAL UNKNOWN")
}

/**
 * Structured container for RF signal quality telemetry.
 */
data class SignalStrengthInfo(
    val rssi: Int?,
    val level: SignalLevel,
    val label: String
) {
    val displayFormatted: String
        get() = if (rssi != null) "$label ($rssi dBm)" else label
}

/**
 * Proximity and RF Signal calibration engine.
 */
object ProximityCalculator {

    /** RSSI threshold for immediate physical proximity (e.g. within same desk/room). */
    const val RSSI_THRESHOLD_VERY_CLOSE = -55

    /** RSSI threshold for moderate nearby vicinity. */
    const val RSSI_THRESHOLD_NEARBY = -75

    /** RSSI threshold for strong RF signal. */
    const val RSSI_THRESHOLD_SIGNAL_STRONG = -60

    /** RSSI threshold for moderate RF signal. */
    const val RSSI_THRESHOLD_SIGNAL_MODERATE = -75

    /**
     * Maps an observed RSSI reading (dBm) into a qualitative proximity state.
     * Avoids artificial precision.
     */
    fun fromRssi(rssi: Int): ProximityState {
        return when {
            rssi >= RSSI_THRESHOLD_VERY_CLOSE -> ProximityState.VERY_CLOSE
            rssi >= RSSI_THRESHOLD_NEARBY -> ProximityState.NEARBY
            else -> ProximityState.FAR
        }
    }

    /**
     * Maps an optional RSSI reading into a qualitative signal strength container.
     */
    fun signalLevelFromRssi(rssi: Int?): SignalStrengthInfo {
        if (rssi == null) {
            return SignalStrengthInfo(
                rssi = null,
                level = SignalLevel.UNKNOWN,
                label = SignalLevel.UNKNOWN.label
            )
        }

        val level = when {
            rssi >= RSSI_THRESHOLD_SIGNAL_STRONG -> SignalLevel.STRONG
            rssi >= RSSI_THRESHOLD_SIGNAL_MODERATE -> SignalLevel.MODERATE
            else -> SignalLevel.WEAK
        }

        return SignalStrengthInfo(
            rssi = rssi,
            level = level,
            label = level.label
        )
    }
}
