package org.sih.itantra.core.benchmark.channel

import java.util.Random

/**
 * Deterministic propagation latency and jitter injector.
 *
 * Models RF flight time, propagation delay, and jitter due to channel contention.
 */
class LatencyJitterInjector(
    val oneWayLatencyMs: Long = 0L,
    val maxJitterMs: Long = 0L,
    private val random: Random? = null
) {
    /**
     * Calculates the total propagation delay for an individual packet.
     *
     * @return oneWayLatencyMs + jitter (bounded to >= 0L)
     */
    fun calculateDelayMs(): Long {
        if (oneWayLatencyMs <= 0L && maxJitterMs <= 0L) return 0L

        val jitterOffset = if (maxJitterMs > 0L && random != null) {
            // Uniform distribution in range [-maxJitterMs, +maxJitterMs]
            val span = (2 * maxJitterMs + 1).toInt()
            (random.nextInt(span) - maxJitterMs)
        } else {
            0L
        }

        return (oneWayLatencyMs + jitterOffset).coerceAtLeast(0L)
    }
}
