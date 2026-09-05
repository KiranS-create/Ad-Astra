package org.sih.itantra.core.diagnostics

import org.sih.itantra.core.common.IndicLanguage

data class LatencyMetrics(
    val audioDurationMs: Long = 0L,
    val vadLagMs: Double = 0.0,
    val sttLatencyMs: Double = 0.0,
    val encodingLatencyMs: Double = 0.0,
    val transportLatencyMs: Double = 0.0,
    val ttsLatencyMs: Double = 0.0,
    val playbackStartupMs: Double = 0.0,
    val totalEndToEndMs: Double = 0.0,
    val language: IndicLanguage = IndicLanguage.HINDI
) {
    val realTimeFactor: Double
        get() = if (audioDurationMs > 0) sttLatencyMs / audioDurationMs.toDouble() else 0.0
}
