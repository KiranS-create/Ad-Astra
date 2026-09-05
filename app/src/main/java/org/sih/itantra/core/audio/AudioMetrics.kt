package org.sih.itantra.core.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Real-time audio waveform and energy calculations for VAD and UI oscilloscope visualizers.
 */
object AudioMetrics {
    /**
     * Calculates the Root Mean Square (RMS) amplitude of 16-bit little-endian PCM samples.
     */
    fun calculateRms(pcmBytes: ByteArray, length: Int = pcmBytes.size): Double {
        if (length < 2) return 0.0
        val sampleCount = length / 2
        var sumSquares = 0.0

        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += sample.toDouble() * sample.toDouble()
        }

        return sqrt(sumSquares / sampleCount)
    }

    /**
     * Converts RMS amplitude to decibels relative to full scale (dBFS).
     * Full scale for 16-bit audio is 32767.
     */
    fun calculateDbfs(rms: Double): Double {
        if (rms <= 0.0) return -96.0
        val dbfs = 20.0 * log10(rms / 32767.0)
        return max(-96.0, dbfs)
    }

    /**
     * Returns normalized amplitude in range [0.0, 1.0] for UI visualizer bars.
     */
    fun calculateNormalizedAmplitude(pcmBytes: ByteArray, length: Int = pcmBytes.size): Float {
        val rms = calculateRms(pcmBytes, length)
        val normalized = (rms / 8000.0).toFloat()
        return normalized.coerceIn(0.0f, 1.0f)
    }

    /**
     * Computes the Zero-Crossing Rate (ZCR) of PCM frames to distinguish voiced speech from unvoiced noise.
     */
    fun calculateZeroCrossingRate(pcmBytes: ByteArray, length: Int = pcmBytes.size): Double {
        if (length < 4) return 0.0
        val sampleCount = length / 2
        var crossings = 0
        var prevSign = 0

        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            val currentSign = if (sample >= 0) 1 else -1

            if (i > 0 && currentSign != prevSign) {
                crossings++
            }
            prevSign = currentSign
        }

        return crossings.toDouble() / sampleCount
    }
}
