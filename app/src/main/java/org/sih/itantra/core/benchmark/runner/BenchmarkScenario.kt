package org.sih.itantra.core.benchmark.runner

import org.sih.itantra.core.benchmark.channel.NetworkCondition
import org.sih.itantra.core.vbr.AdaptiveRepresentationMode

/**
 * Descriptor of a benchmark scenario to execute.
 */
data class BenchmarkScenario(
    val id: String,
    val category: String,
    val description: String,
    val condition: NetworkCondition,
    val representation: AdaptiveRepresentationMode
)
