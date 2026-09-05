package org.sih.itantra.core.diagnostics

import org.sih.itantra.core.audio.AudioFormatConfig

data class BandwidthMetrics(
    val rawAudioBytes: Long = 0L,
    val transmittedPacketBytes: Long = 0L,
    val characterCount: Int = 0,
    val utf8Bytes: Int = 0,
    val isCompressed: Boolean = false
) {
    val bandwidthReductionPercent: Double
        get() {
            if (rawAudioBytes <= 0L) return 0.0
            val reduction = 1.0 - (transmittedPacketBytes.toDouble() / rawAudioBytes.toDouble())
            return (reduction * 100.0).coerceIn(0.0, 99.9)
        }

    companion object {
        fun fromAudioDuration(durationMs: Long, packetBytes: Int, text: String, compressed: Boolean): BandwidthMetrics {
            val rawBytes = (durationMs * AudioFormatConfig.RAW_AUDIO_BYTES_PER_SEC) / 1000L
            val utf8 = text.toByteArray(Charsets.UTF_8).size
            return BandwidthMetrics(
                rawAudioBytes = maxOf(rawBytes, 1L),
                transmittedPacketBytes = packetBytes.toLong(),
                characterCount = text.length,
                utf8Bytes = utf8,
                isCompressed = compressed
            )
        }
    }
}
