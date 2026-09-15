package org.sih.itantra.core.benchmark.channel

/**
 * Controlled network condition parameters for deterministic channel simulation.
 *
 * NOTE: These conditions represent controlled synthetic communication impairment scenarios
 * rather than physical RF field measurements.
 */
data class NetworkCondition(
    val name: String,
    val bandwidthBps: Long = UNLIMITED_BANDWIDTH,
    val oneWayLatencyMs: Long = 0L,
    val jitterMs: Long = 0L,
    val packetLossRate: Double = 0.0,
    val burstLossCount: Int = 0,
    val outageDurationMs: Long = 0L
) {
    val isUnlimitedBandwidth: Boolean get() = bandwidthBps <= 0L || bandwidthBps == UNLIMITED_BANDWIDTH
    val hasLoss: Boolean get() = packetLossRate > 0.0 || burstLossCount > 0
    val hasLatencyOrJitter: Boolean get() = oneWayLatencyMs > 0L || jitterMs > 0L
    val hasOutage: Boolean get() = outageDurationMs > 0L

    companion object {
        const val UNLIMITED_BANDWIDTH = -1L

        // Standard Bandwidth levels
        val BW_UNLIMITED = NetworkCondition("BW_UNLIMITED", bandwidthBps = UNLIMITED_BANDWIDTH)
        val BW_100K = NetworkCondition("BW_100K", bandwidthBps = 100_000L)
        val BW_50K = NetworkCondition("BW_50K", bandwidthBps = 50_000L)
        val BW_20K = NetworkCondition("BW_20K", bandwidthBps = 20_000L)
        val BW_10K = NetworkCondition("BW_10K", bandwidthBps = 10_000L)
        val BW_5K = NetworkCondition("BW_5K", bandwidthBps = 5_000L)

        // Standard Loss levels
        val LOSS_0 = NetworkCondition("LOSS_0%", packetLossRate = 0.0)
        val LOSS_5 = NetworkCondition("LOSS_5%", packetLossRate = 0.05)
        val LOSS_10 = NetworkCondition("LOSS_10%", packetLossRate = 0.10)
        val LOSS_20 = NetworkCondition("LOSS_20%", packetLossRate = 0.20)
        val LOSS_30 = NetworkCondition("LOSS_30%", packetLossRate = 0.30)
        val LOSS_50 = NetworkCondition("LOSS_50%", packetLossRate = 0.50)

        // Latency and Jitter levels
        val LATENCY_0_JITTER_0 = NetworkCondition("L0_J0", oneWayLatencyMs = 0L, jitterMs = 0L)
        val LATENCY_50_JITTER_10 = NetworkCondition("L50_J10", oneWayLatencyMs = 50L, jitterMs = 10L)
        val LATENCY_100_JITTER_25 = NetworkCondition("L100_J25", oneWayLatencyMs = 100L, jitterMs = 25L)
        val LATENCY_250_JITTER_50 = NetworkCondition("L250_J50", oneWayLatencyMs = 250L, jitterMs = 50L)
        val LATENCY_500_JITTER_50 = NetworkCondition("L500_J50", oneWayLatencyMs = 500L, jitterMs = 50L)

        // Burst loss levels
        val BURST_3 = NetworkCondition("BURST_3", burstLossCount = 3)
        val BURST_5 = NetworkCondition("BURST_5", burstLossCount = 5)
        val BURST_10 = NetworkCondition("BURST_10", burstLossCount = 10)

        // DTN Outage durations
        val OUTAGE_10S = NetworkCondition("OUTAGE_10S", outageDurationMs = 10_000L)
        val OUTAGE_30S = NetworkCondition("OUTAGE_30S", outageDurationMs = 30_000L)
        val OUTAGE_60S = NetworkCondition("OUTAGE_60S", outageDurationMs = 60_000L)
        val OUTAGE_300S = NetworkCondition("OUTAGE_300S", outageDurationMs = 300_000L)
    }
}
