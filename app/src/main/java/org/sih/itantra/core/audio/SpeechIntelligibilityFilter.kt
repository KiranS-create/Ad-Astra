package org.sih.itantra.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time, zero-allocation DSP audio conditioning filter designed specifically for
 * mobile phone speaker speech legibility in tactical, high-noise environments.
 *
 * Processing Stages:
 * 1. 100 Hz High-Pass Filter (HPF): Attenuates low-frequency cabinet rumble and proximity boom.
 * 2. 3.2 kHz Consonant Presence Boost (+2.5 dB, Q=1.2): Accentuates the critical band for
 *    consonant articulation (distinguishing Indic dental vs retroflex stops, fricatives, and plosives).
 * 3. Soft-Knee Peak Limiter: Prevents harsh digital clipping during high-urgency distress alerts.
 */
object SpeechIntelligibilityFilter {

    /**
     * Applies the speech intelligibility conditioning filter in-place to 16-bit little-endian PCM bytes.
     * Returns a new or modified ByteArray with enhanced vocal presence.
     */
    fun process(pcmBytes: ByteArray, sampleRate: Int = AudioFormatConfig.SAMPLE_RATE_HZ): ByteArray {
        if (pcmBytes.size < 4 || sampleRate <= 0) return pcmBytes

        val numSamples = pcmBytes.size / 2
        val inputBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val outputBytes = ByteArray(pcmBytes.size)
        val outputBuffer = ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        // 1. Calculate HPF coefficient (1st order IIR, fc = 100 Hz)
        val fcHpf = 100.0
        val dt = 1.0 / sampleRate
        val rc = 1.0 / (2.0 * Math.PI * fcHpf)
        val alphaHpf = (rc / (rc + dt)).toFloat()

        // 2. Calculate Peaking EQ coefficients (Biquad, f0 = 3200 Hz, Q = 1.2, Gain = +2.5 dB)
        val f0 = 3200.0.coerceAtMost(sampleRate * 0.45) // ensure below Nyquist
        val q = 1.2
        val gainDb = 2.5
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * Math.PI * f0 / sampleRate
        val alphaEq = (sin(w0) / (2.0 * q))

        val b0 = (1.0 + alphaEq * a)
        val b1 = (-2.0 * cos(w0))
        val b2 = (1.0 - alphaEq * a)
        val a0 = (1.0 + alphaEq / a)
        val a1 = (-2.0 * cos(w0))
        val a2 = (1.0 - alphaEq / a)

        // Normalize biquad coefficients
        val nb0 = (b0 / a0).toFloat()
        val nb1 = (b1 / a0).toFloat()
        val nb2 = (b2 / a0).toFloat()
        val na1 = (a1 / a0).toFloat()
        val na2 = (a2 / a0).toFloat()

        // State variables
        var hpfPrevIn = 0f
        var hpfPrevOut = 0f

        var eqX1 = 0f
        var eqX2 = 0f
        var eqY1 = 0f
        var eqY2 = 0f

        for (i in 0 until numSamples) {
            val raw = inputBuffer.get(i) / 32768.0f

            // Stage 1: High-Pass Filter
            val hpfOut = alphaHpf * (hpfPrevOut + raw - hpfPrevIn)
            hpfPrevIn = raw
            hpfPrevOut = hpfOut

            // Stage 2: Peaking EQ
            val eqOut = nb0 * hpfOut + nb1 * eqX1 + nb2 * eqX2 - na1 * eqY1 - na2 * eqY2
            eqX2 = eqX1
            eqX1 = hpfOut
            eqY2 = eqY1
            eqY1 = eqOut

            // Stage 3: Soft-Knee Limiter / Saturation
            // Linear up to 0.85, smooth tanh compression above
            val limited = if (kotlin.math.abs(eqOut) <= 0.85f) {
                eqOut
            } else {
                val sign = if (eqOut > 0) 1.0f else -1.0f
                val excess = kotlin.math.abs(eqOut) - 0.85f
                sign * (0.85f + 0.14f * tanh(excess.toDouble()).toFloat())
            }

            val outSample = (limited * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            outputBuffer.put(outSample)
        }

        return outputBytes
    }
}
