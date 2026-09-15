package org.sih.itantra.core.benchmark.metrics

import kotlin.math.roundToLong

/**
 * Summary distribution metrics containing mean, median, P95, min, max, and count.
 */
data class DistributionMetrics(
    val mean: Double,
    val median: Double,
    val p95: Double,
    val min: Double,
    val max: Double,
    val count: Int
) {
    companion object {
        val ZERO = DistributionMetrics(
            mean = 0.0,
            median = 0.0,
            p95 = 0.0,
            min = 0.0,
            max = 0.0,
            count = 0
        )

        fun fromLongs(values: List<Long>): DistributionMetrics {
            return fromDoubles(values.map { it.toDouble() })
        }

        fun fromDoubles(values: List<Double>): DistributionMetrics {
            if (values.isEmpty()) return ZERO
            val sorted = values.sorted()
            val count = sorted.size
            val sum = sorted.sum()
            val mean = sum / count
            val min = sorted.first()
            val max = sorted.last()

            val median = if (count % 2 == 1) {
                sorted[count / 2]
            } else {
                (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0
            }

            // P95 nearest rank calculation
            val p95Index = ((count * 0.95).roundToLong().toInt() - 1).coerceIn(0, count - 1)
            val p95 = sorted[p95Index]

            return DistributionMetrics(
                mean = mean,
                median = median,
                p95 = p95,
                min = min,
                max = max,
                count = count
            )
        }
    }

    fun toFormattedString(): String {
        return "med=%.1f ms, p95=%.1f ms, mean=%.1f ms (min=%.1f, max=%.1f, n=%d)".format(
            median, p95, mean, min, max, count
        )
    }
}
