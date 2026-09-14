package org.sih.itantra.core.speech.pipeline

import org.sih.itantra.core.audio.AudioFormatConfig

/**
 * Represents an incremental audio frame or chunk in the pipelined speech architecture.
 *
 * @property chunkIndex Zero-based monotonically increasing chunk index within the utterance.
 * @property pcmData Raw 16-bit Mono PCM audio bytes (16 kHz, little-endian).
 * @property timestampNanos Monotonic timestamp (from BenchmarkClock or System.nanoTime) when chunk was captured.
 * @property isSpeech True if Voice Activity Detection (VAD) flagged this chunk as speech, false if pause/silence.
 * @property isLast True if this is the final chunk of the utterance (e.g. PTT released or speech endpoint).
 */
data class AudioChunk(
    val chunkIndex: Int,
    val pcmData: ByteArray,
    val timestampNanos: Long = System.nanoTime(),
    val isSpeech: Boolean = true,
    val isLast: Boolean = false
) {
    /**
     * Duration of this audio chunk in milliseconds based on standard 16 kHz 16-bit mono PCM.
     */
    val durationMs: Long
        get() = if (pcmData.isNotEmpty()) {
            (pcmData.size / AudioFormatConfig.BYTES_PER_SAMPLE * 1000L) / AudioFormatConfig.SAMPLE_RATE_HZ
        } else 0L

    val sampleCount: Int
        get() = pcmData.size / AudioFormatConfig.BYTES_PER_SAMPLE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioChunk
        if (chunkIndex != other.chunkIndex) return false
        if (!pcmData.contentEquals(other.pcmData)) return false
        if (timestampNanos != other.timestampNanos) return false
        if (isSpeech != other.isSpeech) return false
        if (isLast != other.isLast) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chunkIndex
        result = 31 * result + pcmData.contentHashCode()
        result = 31 * result + timestampNanos.hashCode()
        result = 31 * result + isSpeech.hashCode()
        result = 31 * result + isLast.hashCode()
        return result
    }

    companion object {
        /**
         * Convenience factory for creating a silent chunk of specified duration.
         */
        fun createSilence(chunkIndex: Int, durationMs: Long, isLast: Boolean = false): AudioChunk {
            val numSamples = (AudioFormatConfig.SAMPLE_RATE_HZ * durationMs) / 1000L
            val byteCount = (numSamples * AudioFormatConfig.BYTES_PER_SAMPLE).toInt()
            return AudioChunk(
                chunkIndex = chunkIndex,
                pcmData = ByteArray(byteCount),
                isSpeech = false,
                isLast = isLast
            )
        }
    }
}
