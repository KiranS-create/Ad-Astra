package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.diagnostics.BandwidthMetrics
import org.sih.itantra.core.diagnostics.LatencyMetrics

class BenchmarkMetricsTest {

    @Test
    fun testBandwidthSavingsCalculation() {
        // 3 seconds of 16kHz 16-bit PCM audio = 96,000 bytes
        val durationMs = 3000L
        val text = "हम राहत सामग्री के साथ उत्तर दिशा में आगे बढ़ रहे हैं।"
        val packetBytes = 175 // ~31 bytes header + ~144 bytes text

        val metrics = BandwidthMetrics.fromAudioDuration(
            durationMs = durationMs,
            packetBytes = packetBytes,
            text = text,
            compressed = false
        )

        assertEquals(96000L, metrics.rawAudioBytes)
        assertEquals(175L, metrics.transmittedPacketBytes)
        assertTrue("Bandwidth savings must be > 99%", metrics.bandwidthReductionPercent > 99.0)
    }

    @Test
    fun testRealTimeFactor() {
        val latency = LatencyMetrics(
            audioDurationMs = 2000L,
            sttLatencyMs = 350.0
        )
        // RTF = 350ms / 2000ms = 0.175 (well below 1.0 real-time threshold)
        assertEquals(0.175, latency.realTimeFactor, 0.001)
    }
}
