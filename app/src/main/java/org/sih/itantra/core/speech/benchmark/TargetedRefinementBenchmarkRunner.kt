package org.sih.itantra.core.speech.benchmark

import kotlinx.coroutines.delay
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.diagnostics.BenchmarkClock
import org.sih.itantra.core.speech.pipeline.AudioChunk
import org.sih.itantra.core.speech.pipeline.DefaultPass1Processor
import org.sih.itantra.core.speech.pipeline.DefaultPass2Processor
import org.sih.itantra.core.speech.pipeline.Pass1Hypothesis
import org.sih.itantra.core.speech.pipeline.Pass1Processor
import org.sih.itantra.core.speech.pipeline.PipelinedSpeechMetrics
import org.sih.itantra.core.speech.pipeline.PipelinedTwoPassSpeechEngine
import org.sih.itantra.core.speech.refinement.DefaultTargetedPass2Refiner
import org.sih.itantra.core.speech.refinement.RefinementBudget
import org.sih.itantra.core.speech.refinement.TargetedRefinementMetrics
import org.sih.itantra.core.speech.refinement.TargetedRefinementPolicy
import org.sih.itantra.core.speech.refinement.TargetedTwoPassSpeechEngine
import org.sih.itantra.core.stt.SentenceFinalizer

/**
 * Scripted Pass 1 processor that emits tokens from a defined test phrase per speech chunk.
 */
class ScriptedPass1Processor(
    private val words: List<String>,
    private val chunkProcessingDelayMs: Long = 10L
) : Pass1Processor {
    override suspend fun process(
        chunk: AudioChunk,
        accumulatedText: String,
        language: IndicLanguage
    ): Pass1Hypothesis {
        val tStart = BenchmarkClock.nowNanos()
        if (chunkProcessingDelayMs > 0) {
            delay(chunkProcessingDelayMs)
        }

        val word = if (chunk.chunkIndex < words.size) words[chunk.chunkIndex] else "tok-${chunk.chunkIndex}"
        val newText = if (accumulatedText.isBlank()) word else "$accumulatedText $word"
        val tEnd = BenchmarkClock.nowNanos()

        return Pass1Hypothesis(
            chunkIndex = chunk.chunkIndex,
            partialText = newText,
            isFinalChunk = chunk.isLast,
            latencyNanos = tEnd - tStart
        )
    }
}

/**
 * Comparative benchmark result container comparing all 4 pipelines for a single test utterance.
 */
data class TargetedComparativeBenchmarkResult(
    val utteranceName: String,
    val utteranceText: String,
    val audioDurationMs: Long,
    val pipelineA_SerialBatch: SpeechPipelineBenchmarkResult,
    val pipelineB_SerialTwoPass: SpeechPipelineBenchmarkResult,
    val pipelineC_Feature16A: SpeechPipelineBenchmarkResult,
    val pipelineD_Feature17Targeted: SpeechPipelineBenchmarkResult,
    val targetedMetrics: TargetedRefinementMetrics,
    val latencyReductionVsBaselinePercent: Double,
    val latencyReductionVs16APercent: Double
)

/**
 * Monotonic comparative benchmark runner for Feature 17 Targeted Refinement.
 * Evaluates:
 * 1. Pipeline A: Baseline Serial Batch
 * 2. Pipeline B: Serial Two-Pass (Naive Sequential)
 * 3. Pipeline C: Pipelined Two-Pass (Feature 16A)
 * 4. Pipeline D: Targeted Refinement (Feature 17 - Opportunistic silence, zero endpoint wait)
 */
class TargetedRefinementBenchmarkRunner {

    private val legacyRunner = TwoPassSpeechBenchmarkRunner()

    /**
     * Executes Pipeline D: Feature 17 Targeted Refinement.
     */
    suspend fun runTargetedRefinement(
        chunks: List<AudioChunk>,
        words: List<String>? = null,
        language: IndicLanguage = IndicLanguage.HINDI,
        chunkFeedDelayMs: Long = 10L,
        pass1DelayMs: Long = 10L,
        refinementDelayMs: Long = 10L
    ): Pair<SpeechPipelineBenchmarkResult, TargetedRefinementMetrics> {
        val totalAudioDurationMs = chunks.sumOf { it.durationMs }

        val pass1Processor: Pass1Processor = if (words != null) {
            ScriptedPass1Processor(words, chunkProcessingDelayMs = pass1DelayMs)
        } else {
            DefaultPass1Processor(chunkProcessingDelayMs = pass1DelayMs)
        }

        val engine = TargetedTwoPassSpeechEngine(
            pass1Processor = pass1Processor,
            targetedRefiner = DefaultTargetedPass2Refiner(simulatedRefinementDelayMs = refinementDelayMs),
            refinementPolicy = TargetedRefinementPolicy(),
            refinementBudget = RefinementBudget()
        )

        engine.startUtterance(language)

        var firstPartialTimeNanos = 0L
        for (chunk in chunks) {
            engine.feedChunk(chunk)
            if (firstPartialTimeNanos == 0L && engine.partialHypothesisFlow.value.isNotBlank()) {
                firstPartialTimeNanos = BenchmarkClock.nowNanos()
            }
            if (chunkFeedDelayMs > 0) {
                delay(chunkFeedDelayMs)
            }
        }

        // Endpoint triggered: zero post-speech join!
        val standardMetrics: PipelinedSpeechMetrics = engine.markEndOfSpeech()
        val targetedMetrics = engine.getTargetedMetrics() ?: TargetedRefinementMetrics()

        val benchmarkResult = SpeechPipelineBenchmarkResult(
            pipelineName = "Pipeline D: Feature 17 Targeted Refinement",
            audioDurationMs = totalAudioDurationMs,
            endOfSpeechToPacketReadyMs = targetedMetrics.endpointToPacketReadyMs,
            firstPartialLatencyMs = targetedMetrics.firstPartialLatencyMs,
            totalComputeMs = targetedMetrics.pass1ComputeDurationMs + targetedMetrics.targetedRefinementComputeDurationMs,
            overlapDurationMs = targetedMetrics.overlapDurationMs,
            realTimeFactor = targetedMetrics.realTimeFactor,
            finalTranscription = engine.stableHypothesisFlow.value,
            isRealTimeStreaming = true
        )

        return Pair(benchmarkResult, targetedMetrics)
    }

    /**
     * Executes the full comparative benchmark across all 4 pipelines for a given utterance.
     */
    suspend fun runComparativeBenchmark(
        utteranceName: String,
        utteranceText: String,
        chunks: List<AudioChunk>,
        words: List<String>,
        language: IndicLanguage = IndicLanguage.HINDI
    ): TargetedComparativeBenchmarkResult {
        val audioDurationMs = chunks.sumOf { it.durationMs }

        // 1. Pipeline A: Baseline Serial Batch
        val resA = legacyRunner.runBaselineSerialBatch(chunks, language)

        // 2. Pipeline B: Serial Two-Pass (Naive)
        val resB = legacyRunner.runSerialTwoPassNaive(chunks, language)

        // 3. Pipeline C: Feature 16A Pipelined Two-Pass
        val resC = legacyRunner.runPipelinedTwoPass(chunks, language)

        // 4. Pipeline D: Feature 17 Targeted Refinement
        val (resD, targetedMetrics) = runTargetedRefinement(chunks, words, language)

        val reductionVsBaseline = if (resA.endOfSpeechToPacketReadyMs > 0) {
            ((resA.endOfSpeechToPacketReadyMs - resD.endOfSpeechToPacketReadyMs) / resA.endOfSpeechToPacketReadyMs) * 100.0
        } else 0.0

        val reductionVs16A = if (resC.endOfSpeechToPacketReadyMs > 0) {
            ((resC.endOfSpeechToPacketReadyMs - resD.endOfSpeechToPacketReadyMs) / resC.endOfSpeechToPacketReadyMs) * 100.0
        } else 0.0

        return TargetedComparativeBenchmarkResult(
            utteranceName = utteranceName,
            utteranceText = utteranceText,
            audioDurationMs = audioDurationMs,
            pipelineA_SerialBatch = resA,
            pipelineB_SerialTwoPass = resB,
            pipelineC_Feature16A = resC,
            pipelineD_Feature17Targeted = resD,
            targetedMetrics = targetedMetrics,
            latencyReductionVsBaselinePercent = reductionVsBaseline,
            latencyReductionVs16APercent = reductionVs16A
        )
    }

    /**
     * Creates standard utterance chunk sequence containing speech chunks separated by natural VAD silence.
     */
    fun createUtteranceChunks(
        phrase1ChunkCount: Int = 4,
        silenceDurationMs: Long = 300L,
        phrase2ChunkCount: Int = 4,
        chunkDurationMs: Long = 300L
    ): List<AudioChunk> {
        val list = mutableListOf<AudioChunk>()
        var idx = 0

        // Phrase 1 speech chunks
        val byteCount = (chunkDurationMs * 32).toInt() // 16kHz * 2 bytes/sample * ms / 1000
        repeat(phrase1ChunkCount) {
            list.add(AudioChunk(chunkIndex = idx++, pcmData = ByteArray(byteCount), isSpeech = true))
        }

        // Natural silence pause (VAD silence interval)
        list.add(AudioChunk.createSilence(chunkIndex = idx++, durationMs = silenceDurationMs))

        // Phrase 2 speech chunks
        repeat(phrase2ChunkCount - 1) {
            list.add(AudioChunk(chunkIndex = idx++, pcmData = ByteArray(byteCount), isSpeech = true))
        }

        // Final speech chunk
        list.add(AudioChunk(chunkIndex = idx++, pcmData = ByteArray(byteCount), isSpeech = true, isLast = true))

        return list
    }

    /**
     * Executes the standard 5-utterance comparative benchmark suite:
     * 1. Normal Conversational Sentence
     * 2. Sentence with Numbers/Coordinates
     * 3. Tactical Message
     * 4. Emergency Distress Phrase
     * 5. Multilingual (Hindi / Indic) Test
     */
    suspend fun runStandardSuite(
        onProgress: ((Int, Int, TargetedComparativeBenchmarkResult) -> Unit)? = null
    ): List<TargetedComparativeBenchmarkResult> {
        val results = mutableListOf<TargetedComparativeBenchmarkResult>()

        val testCases = listOf(
            TestCase(
                name = "Utterance 1: Normal Conversational",
                text = "Team this is patrol base moving to waypoint",
                words = listOf("Team", "this", "is", "patrol", "base", "moving", "to", "waypoint"),
                language = IndicLanguage.HINDI
            ),
            TestCase(
                name = "Utterance 2: Numbers & Coordinates",
                text = "Report sector 4 grid 72.5 coordinates verified",
                words = listOf("Report", "sector", "4", "grid", "72.5", "coordinates", "verified"),
                language = IndicLanguage.HINDI
            ),
            TestCase(
                name = "Utterance 3: Tactical Callsign",
                text = "Alpha unit check in at checkpoint bravo",
                words = listOf("Alpha", "unit", "check", "in", "at", "checkpoint", "bravo"),
                language = IndicLanguage.HINDI
            ),
            TestCase(
                name = "Utterance 4: Emergency Distress",
                text = "SOS medical assistance required injured operator at sector 9",
                words = listOf("SOS", "medical", "assistance", "required", "injured", "operator", "at", "sector", "9"),
                language = IndicLanguage.HINDI
            ),
            TestCase(
                name = "Utterance 5: Multilingual (Hindi/Indic)",
                text = "मदद चाहिए घायल ऑपरेटर सेक्टर चार पर है",
                words = listOf("मदद", "चाहिए", "घायल", "ऑपरेटर", "सेक्टर", "चार", "पर", "है"),
                language = IndicLanguage.HINDI
            )
        )

        for ((index, testCase) in testCases.withIndex()) {
            val chunks = createUtteranceChunks(
                phrase1ChunkCount = testCase.words.size / 2,
                silenceDurationMs = 300L,
                phrase2ChunkCount = testCase.words.size - (testCase.words.size / 2)
            )

            val result = runComparativeBenchmark(
                utteranceName = testCase.name,
                utteranceText = testCase.text,
                chunks = chunks,
                words = testCase.words,
                language = testCase.language
            )

            results.add(result)
            onProgress?.invoke(index + 1, testCases.size, result)
        }

        return results
    }

    private data class TestCase(
        val name: String,
        val text: String,
        val words: List<String>,
        val language: IndicLanguage
    )
}
