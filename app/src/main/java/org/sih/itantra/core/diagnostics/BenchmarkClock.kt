package org.sih.itantra.core.diagnostics

/**
 * Nanosecond-precision monotonic clock for non-fabricated latency profiling.
 */
object BenchmarkClock {
    fun nowNanos(): Long = System.nanoTime()
    fun nowMillis(): Long = System.currentTimeMillis()

    fun elapsedMs(startNanos: Long, endNanos: Long = nowNanos()): Double {
        return (endNanos - startNanos) / 1_000_000.0
    }
}
