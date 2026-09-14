package org.sih.itantra.core.speech.benchmark

import kotlinx.coroutines.delay
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.diagnostics.BenchmarkClock
import org.sih.itantra.core.speech.pipeline.AudioChunk
import org.sih.itantra.core.speech.pipeline.DefaultPass1Processor
import org.sih.itantra.core.speech.pipeline.DefaultPass2Processor
import org.sih.itantra.core.speech.pipeline.PipelinedTwoPassSpeechEngine
import org.sih.itantra.core.speech.pipeline.PipelinedSpeechMetrics
import org.sih.itantra.core.stt.SentenceFinalizer

/**
 * Result data class from comparative speech pipeline benchmark.
 */
data class SpeechPipelineBenchmarkResult(
    val pipelineName: String,
    val audioDurationMs: Long,
    val endOfSpeechToPacketReadyMs: Double,
    val firstPartialLatencyMs: Double,
    val totalComputeMs: Double,
    val overlapDurationMs: Double,
    val realTimeFactor: Double,
    val finalTranscription: String,
    val isRealTimeStreaming: Boolean
)

/**
 * Monotonic comparative benchmark runner for iTantra speech pipelines.
 * Evaluates:
 * 1. Pipeline A: Baseline Serial Batch Pipeline
 * 2. Pipeline B: Serial Two-Pass (Naive Sequential)
 * 3. Pipeline C: Pipelined Two-Pass Overlapped (Feature 16A)
 */
class TwoPassSpeechBenchmarkRunner {

    /**
     * Executes the baseline serial batch pipeline:
     * Chunks are buffered during speech; STT runs in full batch ONLY after speech ends.
     */
    suspend fun runBaselineSerialBatch(
        chunks: List<AudioChunk>,
        language: IndicLanguage = IndicLanguage.HINDI,
        simulatedRtf: Double = 0.15 // e.g. 525ms compute for 3500ms audio on ARM64
    ): SpeechPipelineBenchmarkResult {
        val totalAudioDurationMs = chunks.sumOf { it.durationMs }
        val tCaptureStart = BenchmarkClock.nowNanos()

        // Simulate audio recording duration
        val audioStreamingIntervalMs = (totalAudioDurationMs / 10).coerceAtLeast(10L)
        delay(audioStreamingIntervalMs)

        // End of speech: user stops speaking
        val tEndpoint = BenchmarkClock.nowNanos()

        // Full batch STT runs AFTER speech ends
        val simulatedSttTimeMs = (totalAudioDurationMs * simulatedRtf).toLong().coerceAtLeast(100L)
        val tSttStart = BenchmarkClock.nowNanos()
        delay(simulatedSttTimeMs)
        val rawText = "नमस्ते यह आई-तंत्र बेसलाइन परीक्षण है"
        val tSttEnd = BenchmarkClock.nowNanos()

        // Semantic & Sentence finalization
        val finalized = SentenceFinalizer.finalizeSentence(rawText, language)
        delay(15L) // simulated classification
        delay(10L) // simulated packet prep

        val tPacketReady = BenchmarkClock.nowNanos()

        val endOfSpeechLatencyMs = BenchmarkClock.elapsedMs(tEndpoint, tPacketReady)
        val totalComputeMs = BenchmarkClock.elapsedMs(tSttStart, tPacketReady)

        return SpeechPipelineBenchmarkResult(
            pipelineName = "Pipeline A: Baseline Serial Batch",
            audioDurationMs = totalAudioDurationMs,
            endOfSpeechToPacketReadyMs = endOfSpeechLatencyMs,
            firstPartialLatencyMs = endOfSpeechLatencyMs, // Not available until complete end
            totalComputeMs = totalComputeMs,
            overlapDurationMs = 0.0, // Zero overlap
            realTimeFactor = simulatedRtf,
            finalTranscription = finalized,
            isRealTimeStreaming = false
        )
    }

    /**
     * Executes the naive serial two-pass pipeline:
     * User stops speaking, then Pass 1 runs over all audio, then Pass 2 runs sequentially.
     */
    suspend fun runSerialTwoPassNaive(
        chunks: List<AudioChunk>,
        language: IndicLanguage = IndicLanguage.HINDI
    ): SpeechPipelineBenchmarkResult {
        val totalAudioDurationMs = chunks.sumOf { it.durationMs }
        val tCaptureStart = BenchmarkClock.nowNanos()

        // Simulate audio streaming
        delay((totalAudioDurationMs / 10).coerceAtLeast(10L))

        // End of speech
        val tEndpoint = BenchmarkClock.nowNanos()

        // Pass 1 sequentially on all chunks
        val tPass1Start = BenchmarkClock.nowNanos()
        val speechChunks = chunks.filter { it.isSpeech }
        delay((speechChunks.size * 20L).coerceAtLeast(120L))
        val tPass1End = BenchmarkClock.nowNanos()

        // Pass 2 sequentially on all hypotheses
        val tPass2Start = BenchmarkClock.nowNanos()
        delay(80L)
        val tPass2End = BenchmarkClock.nowNanos()

        // Finalization & packet
        delay(20L)
        val tPacketReady = BenchmarkClock.nowNanos()

        val endOfSpeechLatencyMs = BenchmarkClock.elapsedMs(tEndpoint, tPacketReady)
        val totalComputeMs = BenchmarkClock.elapsedMs(tPass1Start, tPacketReady)

        return SpeechPipelineBenchmarkResult(
            pipelineName = "Pipeline B: Serial Two-Pass (Naive)",
            audioDurationMs = totalAudioDurationMs,
            endOfSpeechToPacketReadyMs = endOfSpeechLatencyMs,
            firstPartialLatencyMs = endOfSpeechLatencyMs,
            totalComputeMs = totalComputeMs,
            overlapDurationMs = 0.0,
            realTimeFactor = totalComputeMs / totalAudioDurationMs.toDouble(),
            finalTranscription = SentenceFinalizer.finalizeSentence("सीरियल टू-पास परीक्षण", language),
            isRealTimeStreaming = false
        )
    }

    /**
     * Executes the Pipelined Two-Pass Overlapped Architecture (Feature 16A):
     * Chunks stream in real-time, Pass 1 computes concurrently, Pass 2 seizes silence windows.
     */
    suspend fun runPipelinedTwoPass(
        chunks: List<AudioChunk>,
        language: IndicLanguage = IndicLanguage.HINDI,
        chunkFeedDelayMs: Long = 10L
    ): SpeechPipelineBenchmarkResult {
        val totalAudioDurationMs = chunks.sumOf { it.durationMs }
        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 12L),
            pass2Processor = DefaultPass2Processor(refinementDelayMs = 18L)
        )

        pipeline.startUtterance(language)

        var firstPartialTimeNanos = 0L
        for (chunk in chunks) {
            pipeline.feedChunk(chunk)
            if (firstPartialTimeNanos == 0L && pipeline.partialHypothesisFlow.value.isNotBlank()) {
                firstPartialTimeNanos = BenchmarkClock.nowNanos()
            }
            if (chunkFeedDelayMs > 0) {
                delay(chunkFeedDelayMs)
            }
        }

        // Speech ends: trigger instant finalization
        val metrics: PipelinedSpeechMetrics = pipeline.markEndOfSpeech()

        return SpeechPipelineBenchmarkResult(
            pipelineName = "Pipeline C: Pipelined Two-Pass (Feature 16A)",
            audioDurationMs = totalAudioDurationMs,
            endOfSpeechToPacketReadyMs = metrics.endOfSpeechToPacketReadyMs,
            firstPartialLatencyMs = metrics.firstPartialLatencyMs,
            totalComputeMs = metrics.pass1ComputeDurationMs + metrics.pass2ComputeDurationMs,
            overlapDurationMs = metrics.overlapDurationMs,
            realTimeFactor = metrics.realTimeFactor,
            finalTranscription = pipeline.stableHypothesisFlow.value,
            isRealTimeStreaming = true
        )
    }

    /**
     * Generates a standard benchmark utterance comprising speech, silence, and speech:
     * e.g. 5 chunks speech (1,500ms) + 1 chunk silence (300ms) + 5 chunks speech (1,500ms) + 1 final chunk (300ms) = 3,600ms.
     */
    fun createStandardBenchmarkChunks(): List<AudioChunk> {
        val list = mutableListOf<AudioChunk>()
        var idx = 0

        // Phrase 1: 5 speech chunks (300ms each = 9600 bytes at 16kHz 16-bit)
        repeat(5) {
            list.add(AudioChunk(chunkIndex = idx++, pcmData = ByteArray(9600), isSpeech = true))
        }

        // Natural Silence Pause: 1 silence chunk (300ms)
        list.add(AudioChunk.createSilence(chunkIndex = idx++, durationMs = 300L))

        // Phrase 2: 5 speech chunks (300ms each)
        repeat(5) {
            list.add(AudioChunk(chunkIndex = idx++, pcmData = ByteArray(9600), isSpeech = true))
        }

        // Final closing chunk
        list.add(AudioChunk(chunkIndex = idx++, pcmData = ByteArray(9600), isSpeech = true, isLast = true))

        return list
    }
}
