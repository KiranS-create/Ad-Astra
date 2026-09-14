package org.sih.itantra.core.speech.refinement

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.speech.pipeline.PipelinedSpeechMetrics

/**
 * High-precision monotonic metrics and diagnostics for Feature 17 Targeted Refinement.
 *
 * All timestamps are captured via monotonic clock (BenchmarkClock.nowNanos()).
 *
 * Core Guarantee:
 * [postEndpointRefinementsCount] is strictly 0 and [endpointWaitingNanos] is strictly 0,
 * because all refinement computation occurs strictly before endpoint during silence intervals.
 */
data class TargetedRefinementMetrics(
    // Monotonic timestamps
    val tCaptureStartNanos: Long = 0L,
    val tFirstSpeechNanos: Long = 0L,
    val tFirstPartialNanos: Long = 0L,
    val tSilenceStartNanos: Long = 0L,
    val tRefinementStartNanos: Long = 0L,
    val tRefinementEndNanos: Long = 0L,
    val tEndpointNanos: Long = 0L,
    val tFinalTranscriptNanos: Long = 0L,
    val tPacketReadyNanos: Long = 0L,

    // Audio & compute durations
    val totalAudioDurationMs: Long = 0L,
    val pass1ComputeDurationMs: Double = 0.0,
    val targetedRefinementComputeDurationMs: Double = 0.0,
    val silenceComputeDurationMs: Double = 0.0,
    val overlapDurationMs: Double = 0.0,

    // Candidate and budget counters
    val preEndpointRefinementsCount: Int = 0,
    val postEndpointRefinementsCount: Int = 0, // Must remain 0
    val abandonedDueToBudgetCount: Int = 0,
    val duplicatesSuppressedCount: Int = 0,
    val candidatesEvaluatedCount: Int = 0,
    val endpointWaitingNanos: Long = 0L, // Must remain 0

    val maxQueueDepth: Int = 0,
    val droppedChunksCount: Int = 0,
    val chunksProcessedCount: Int = 0,
    val language: IndicLanguage = IndicLanguage.HINDI
) {
    /**
     * Latency from speech endpoint (operator stopped speaking / PTT released)
     * to final refined transcript availability.
     * In Feature 17, this is near-zero (<5ms) because no post-speech Pass 2 is triggered.
     */
    val endpointToFinalResultLatencyMs: Double
        get() = if (tEndpointNanos > 0L && tFinalTranscriptNanos >= tEndpointNanos) {
            (tFinalTranscriptNanos - tEndpointNanos) / 1_000_000.0
        } else 0.0

    /**
     * Latency from speech endpoint to complete packet assembly and signing.
     */
    val endpointToPacketReadyMs: Double
        get() = if (tEndpointNanos > 0L && tPacketReadyNanos >= tEndpointNanos) {
            (tPacketReadyNanos - tEndpointNanos) / 1_000_000.0
        } else 0.0

    /**
     * Latency from audio capture start to first partial transcription result.
     */
    val firstPartialLatencyMs: Double
        get() = if (tCaptureStartNanos > 0L && tFirstPartialNanos >= tCaptureStartNanos) {
            (tFirstPartialNanos - tCaptureStartNanos) / 1_000_000.0
        } else 0.0

    /**
     * Total end-to-end elapsed time from capture start to packet ready.
     */
    val totalEndToEndMs: Double
        get() = if (tCaptureStartNanos > 0L && tPacketReadyNanos >= tCaptureStartNanos) {
            (tPacketReadyNanos - tCaptureStartNanos) / 1_000_000.0
        } else 0.0

    /**
     * Real-time factor (RTF).
     */
    val realTimeFactor: Double
        get() = if (totalAudioDurationMs > 0L) {
            (pass1ComputeDurationMs + targetedRefinementComputeDurationMs) / totalAudioDurationMs.toDouble()
        } else 0.0

    /**
     * Converts to standard PipelinedSpeechMetrics for seamless interoperability with
     * Feature 16A/16B consumers.
     */
    fun toPipelinedSpeechMetrics(): PipelinedSpeechMetrics {
        return PipelinedSpeechMetrics(
            tCaptureStartNanos = tCaptureStartNanos,
            tFirstSpeechNanos = tFirstSpeechNanos,
            tFirstPartialNanos = tFirstPartialNanos,
            tEndpointNanos = tEndpointNanos,
            tSttEndNanos = tFinalTranscriptNanos,
            tSemanticEndNanos = tFinalTranscriptNanos,
            tPacketReadyNanos = tPacketReadyNanos,
            totalAudioDurationMs = totalAudioDurationMs,
            pass1ComputeDurationMs = pass1ComputeDurationMs,
            pass2ComputeDurationMs = targetedRefinementComputeDurationMs,
            overlapDurationMs = overlapDurationMs,
            silenceComputeDurationMs = silenceComputeDurationMs,
            maxQueueDepth = maxQueueDepth,
            droppedChunksCount = droppedChunksCount,
            chunksProcessedCount = chunksProcessedCount,
            language = language
        )
    }
}
