package org.sih.itantra.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Tactical Acoustic Front-End (AFE) DSP Noise Filter.
 *
 * Engineered specifically for mission-critical disaster and tactical operations:
 * - Flood rescue (turbulent water rush, engine thrum)
 * - Aerial evacuation (helicopter rotor wash, blade noise)
 * - Industrial / rubbled disaster zones (heavy equipment drone, sirens, wind gusts)
 *
 * Processing Pipeline:
 * 1. 200 Hz High-Pass Filter (IIR): Rejects sub-vocal rumble, wind buffeting, and handling clicks.
 * 2. 3600 Hz Low-Pass Filter (IIR): Attenuates high-frequency hiss and radio carrier squeal outside human voice band.
 * 3. Asymmetric Noise Floor Tracker: Fast downward tracking, slow upward decay to avoid speech biasing.
 * 4. Adaptive Soft-Knee Downward Expander: Smoothly attenuates stationary background drone during pauses
 *    without abrupt gating flutter or consonant clipping.
 */
enum class AcousticFilterMode {
    BYPASS,
    MODERATE,
    TACTICAL_AGGRESSIVE
}

class AcousticFrontEnd(
    var mode: AcousticFilterMode = AcousticFilterMode.BYPASS,
    val sampleRate: Int = AudioFormatConfig.SAMPLE_RATE_HZ
) {
    // 1st order HPF: 200 Hz for TACTICAL_AGGRESSIVE (high noise rejection), 60 Hz for MODERATE (vocal fundamental preservation)
    private val fcHpf: Double
        get() = if (mode == AcousticFilterMode.TACTICAL_AGGRESSIVE) 200.0 else 60.0
    private val dt = 1.0 / sampleRate
    private val rcHpf: Double
        get() = 1.0 / (2.0 * PI * fcHpf)
    private val alphaHpf: Float
        get() = (rcHpf / (rcHpf + dt)).toFloat()

    // 1st order LPF: 3600 Hz for TACTICAL_AGGRESSIVE (carrier hiss rejection), 7500 Hz for MODERATE (full-bandwidth ASR)
    private val fcLpf: Double
        get() = if (mode == AcousticFilterMode.TACTICAL_AGGRESSIVE) 3600.0 else 7500.0
    private val rcLpf: Double
        get() = 1.0 / (2.0 * PI * fcLpf)
    private val alphaLpf: Float
        get() = (dt / (rcLpf + dt)).toFloat()

    // Filter states
    private var hpfPrevIn = 0f
    private var hpfPrevOut = 0f
    private var lpfPrevOut = 0f

    // Noise floor and envelope follower
    private var noiseFloorRms = 250.0
    private var smoothedGain = 1.0f

    val currentNoiseFloor: Double
        get() = noiseFloorRms

    val currentGain: Float
        get() = smoothedGain

    fun reset() {
        hpfPrevIn = 0f
        hpfPrevOut = 0f
        lpfPrevOut = 0f
        noiseFloorRms = 250.0
        smoothedGain = 1.0f
    }

    /**
     * Cleans an incoming 16-bit little-endian PCM frame.
     * Returns a new ByteArray containing the filtered audio.
     */
    fun process(pcmFrame: ByteArray): ByteArray {
        if (pcmFrame.size < 4 || mode == AcousticFilterMode.BYPASS) {
            return pcmFrame
        }

        val numSamples = pcmFrame.size / 2
        val inputBuffer = ByteBuffer.wrap(pcmFrame).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val outputBytes = ByteArray(pcmFrame.size)
        val outputBuffer = ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        // 1. Calculate Frame RMS for noise tracking
        var sumSquares = 0.0
        for (i in 0 until numSamples) {
            val sample = inputBuffer.get(i).toDouble()
            sumSquares += sample * sample
        }
        val frameRms = sqrt(sumSquares / numSamples)

        // 2. Asymmetric Noise Floor Tracking
        // Quickly adapt downwards if ambient drops; slowly track upwards to prevent speech adaptation
        if (frameRms < noiseFloorRms) {
            noiseFloorRms = (noiseFloorRms * 0.90) + (frameRms * 0.10)
        } else {
            noiseFloorRms = (noiseFloorRms * 0.995) + (frameRms * 0.005)
        }
        noiseFloorRms = noiseFloorRms.coerceIn(50.0, 4000.0)

        // 3. Compute Expander Target Gain based on SNR above noise floor
        val (thresholdRms, minGain, kneeWidth) = when (mode) {
            AcousticFilterMode.TACTICAL_AGGRESSIVE -> Triple(max(300.0, noiseFloorRms * 1.8), 0.15f, 1.5)
            AcousticFilterMode.MODERATE -> Triple(max(200.0, noiseFloorRms * 1.3), 0.35f, 1.2)
            AcousticFilterMode.BYPASS -> Triple(0.0, 1.0f, 1.0)
        }

        val upperThreshold = thresholdRms * kneeWidth
        val lowerThreshold = thresholdRms * 0.7

        val targetGain = when {
            frameRms >= upperThreshold -> 1.0f
            frameRms <= lowerThreshold -> minGain
            else -> {
                // Soft-knee interpolation
                val ratio = ((frameRms - lowerThreshold) / (upperThreshold - lowerThreshold)).toFloat()
                minGain + ratio * (1.0f - minGain)
            }
        }

        // 4. Sample-by-sample filtering and smoothing
        // Attack coefficient (~5ms) vs Release coefficient (~60ms)
        val attackAlpha = 0.05f
        val releaseAlpha = 0.008f

        for (i in 0 until numSamples) {
            val raw = inputBuffer.get(i) / 32768.0f

            // Stage 1: High-Pass Filter (removes rumble < 200 Hz)
            val hpfOut = alphaHpf * (hpfPrevOut + raw - hpfPrevIn)
            hpfPrevIn = raw
            hpfPrevOut = hpfOut

            // Stage 2: Low-Pass Filter (removes hiss > 3600 Hz)
            val lpfOut = lpfPrevOut + alphaLpf * (hpfOut - lpfPrevOut)
            lpfPrevOut = lpfOut

            // Stage 3: Smooth Expander Gain Follower
            val alphaGain = if (targetGain > smoothedGain) attackAlpha else releaseAlpha
            smoothedGain += alphaGain * (targetGain - smoothedGain)

            // Stage 4: Apply Gain & Clamp
            val processedSample = (lpfOut * smoothedGain * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            outputBuffer.put(processedSample)
        }

        return outputBytes
    }
}
