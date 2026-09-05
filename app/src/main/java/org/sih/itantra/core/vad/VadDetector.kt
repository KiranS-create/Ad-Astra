package org.sih.itantra.core.vad

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sih.itantra.core.audio.AudioFormatConfig
import org.sih.itantra.core.audio.AudioMetrics
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Real-time, zero-RAM Voice Activity Detector (VAD).
 * Combines adaptive energy thresholding, zero-crossing rate filtering, and pause hangover timing.
 * Specifically tuned for low/mid-range mobile phones and tactical walkie-talkie communication.
 */
class VadDetector(
    private var pauseThresholdMs: Long = 700L,
    private val speechOnsetFramesThreshold: Int = 2,
    private val initialNoiseFloorRms: Double = 250.0
) {
    private val _vadState = MutableStateFlow(VadState.IDLE)
    val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private var noiseFloorRms: Double = initialNoiseFloorRms
    private var consecutiveSpeechFrames = 0
    private var silenceDurationMs = 0L
    private val frameDurationMs: Long = (AudioFormatConfig.FRAME_SIZE_SAMPLES * 1000L) / AudioFormatConfig.SAMPLE_RATE_HZ

    private val speechBuffer = ByteArrayOutputStream()

    var onSpeechSegmentFinalized: ((audioSegment: ByteArray, durationMs: Long) -> Unit)? = null
    var onSpeechStateChanged: ((VadState) -> Unit)? = null

    /**
     * Process an incoming 16-bit PCM frame (typically 1024 bytes / 32ms).
     */
    fun processFrame(pcmFrame: ByteArray) {
        val frameRms = AudioMetrics.calculateRms(pcmFrame)
        val zcr = AudioMetrics.calculateZeroCrossingRate(pcmFrame)

        // Dynamic threshold: 3x noise floor or minimum 600 RMS
        val dynamicThreshold = max(600.0, noiseFloorRms * 2.8)
        val isVoiceLikely = frameRms > dynamicThreshold && zcr in 0.02..0.55

        when (_vadState.value) {
            VadState.IDLE -> {
                // Adapt ambient noise floor during silence
                noiseFloorRms = (noiseFloorRms * 0.95) + (frameRms * 0.05)

                if (isVoiceLikely) {
                    consecutiveSpeechFrames++
                    if (consecutiveSpeechFrames >= speechOnsetFramesThreshold) {
                        transitionTo(VadState.SPEECH_START)
                        speechBuffer.reset()
                        speechBuffer.write(pcmFrame)
                        silenceDurationMs = 0L
                        transitionTo(VadState.SPEECH_ACTIVE)
                    }
                } else {
                    consecutiveSpeechFrames = 0
                }
            }

            VadState.SPEECH_START, VadState.SPEECH_ACTIVE -> {
                speechBuffer.write(pcmFrame)

                if (isVoiceLikely) {
                    silenceDurationMs = 0L
                } else {
                    silenceDurationMs += frameDurationMs
                    if (silenceDurationMs >= pauseThresholdMs) {
                        // Pause threshold reached: speech has completed
                        finalizeUtterance()
                    }
                }
            }

            VadState.SPEECH_END -> {
                // Reset to idle
                transitionTo(VadState.IDLE)
                consecutiveSpeechFrames = 0
                silenceDurationMs = 0L
            }
        }
    }

    /**
     * Force-finalizes the current active utterance (e.g. when PTT button is released).
     */
    fun forceFinalize() {
        if (_vadState.value == VadState.SPEECH_ACTIVE || _vadState.value == VadState.SPEECH_START) {
            finalizeUtterance()
        } else {
            reset()
        }
    }

    private fun finalizeUtterance() {
        transitionTo(VadState.SPEECH_END)
        val segment = speechBuffer.toByteArray()
        val durationMs = if (segment.isNotEmpty()) {
            (segment.size / AudioFormatConfig.BYTES_PER_SAMPLE * 1000L) / AudioFormatConfig.SAMPLE_RATE_HZ
        } else 0L

        speechBuffer.reset()
        if (segment.isNotEmpty() && durationMs >= 250L) { // Filter micro-clicks (<250ms)
            onSpeechSegmentFinalized?.invoke(segment, durationMs)
        }
        transitionTo(VadState.IDLE)
    }

    fun reset() {
        speechBuffer.reset()
        consecutiveSpeechFrames = 0
        silenceDurationMs = 0L
        transitionTo(VadState.IDLE)
    }

    private fun transitionTo(newState: VadState) {
        if (_vadState.value != newState) {
            _vadState.value = newState
            onSpeechStateChanged?.invoke(newState)
        }
    }

    fun setPauseThreshold(thresholdMs: Long) {
        pauseThresholdMs = thresholdMs.coerceIn(300L, 2000L)
    }
}
