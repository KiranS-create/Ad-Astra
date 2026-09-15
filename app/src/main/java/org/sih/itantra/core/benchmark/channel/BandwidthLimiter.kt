package org.sih.itantra.core.benchmark.channel

/**
 * Deterministic bandwidth constraint and transmission delay calculator.
 *
 * Models physical serialization delay for radio frames given wire byte size
 * and effective channel bitrate in bits per second (bps).
 */
class BandwidthLimiter(
    val bandwidthBps: Long = NetworkCondition.UNLIMITED_BANDWIDTH
) {
    val isUnlimited: Boolean get() = bandwidthBps <= 0L || bandwidthBps == NetworkCondition.UNLIMITED_BANDWIDTH

    /**
     * Calculates the transmission airtime serialization delay in milliseconds.
     *
     * Formula: (wireBytes * 8 bits/byte * 1000 ms/sec) / bandwidthBps
     */
    fun calculateTransmissionDelayMs(wireBytes: Int): Long {
        if (isUnlimited || wireBytes <= 0) return 0L
        val bits = wireBytes.toLong() * 8L
        // Round up to integer millisecond
        return (bits * 1000L + bandwidthBps - 1L) / bandwidthBps
    }

    /**
     * Calculates transmission delay in fractional milliseconds for precise profiling.
     */
    fun calculateTransmissionDelayMsDouble(wireBytes: Int): Double {
        if (isUnlimited || wireBytes <= 0) return 0.0
        val bits = wireBytes.toDouble() * 8.0
        return (bits * 1000.0) / bandwidthBps.toDouble()
    }

    /**
     * Estimates maximum throughput in bytes per second.
     */
    val maxBytesPerSecond: Double
        get() = if (isUnlimited) Double.POSITIVE_INFINITY else bandwidthBps / 8.0
}
