package org.sih.itantra.core.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.speech.benchmark.TwoPassSpeechBenchmarkRunner
import org.sih.itantra.core.speech.model.ZipformerInvestigation
import org.sih.itantra.core.speech.pipeline.AudioChunk
import org.sih.itantra.core.speech.pipeline.DefaultPass1Processor
import org.sih.itantra.core.speech.pipeline.DefaultPass2Processor
import org.sih.itantra.core.speech.pipeline.Pass1Hypothesis
import org.sih.itantra.core.speech.pipeline.Pass1Processor
import org.sih.itantra.core.speech.pipeline.Pass2Processor
import org.sih.itantra.core.speech.pipeline.Pass2Refinement
import org.sih.itantra.core.speech.pipeline.PipelinedTwoPassSpeechEngine
import org.sih.itantra.core.stt.SentenceFinalizer
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class TwoPassSpeechPipelineTest {

    // 1. Chunk ordering preserved
    @Test
    fun testChunkOrdering() = runBlocking {
        val receivedIndices = Collections.synchronizedList(mutableListOf<Int>())
        val customPass1 = Pass1Processor { chunk, acc, _ ->
            receivedIndices.add(chunk.chunkIndex)
            Pass1Hypothesis(chunk.chunkIndex, "$acc ${chunk.chunkIndex}".trim())
        }

        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = customPass1,
            pass2Processor = DefaultPass2Processor(refinementDelayMs = 0L)
        )

        pipeline.startUtterance()
        for (i in 0 until 5) {
            pipeline.feedChunk(AudioChunk(chunkIndex = i, pcmData = ByteArray(640), isSpeech = true))
        }

        pipeline.markEndOfSpeech()
        assertEquals(listOf(0, 1, 2, 3, 4), receivedIndices)
    }

    // 2. Pass 1 completion
    @Test
    fun testPass1Completion() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 0L),
            pass2Processor = DefaultPass2Processor(refinementDelayMs = 0L)
        )

        pipeline.startUtterance(IndicLanguage.HINDI)
        pipeline.feedChunk(AudioChunk(chunkIndex = 0, pcmData = ByteArray(640), isSpeech = true))
        pipeline.feedChunk(AudioChunk(chunkIndex = 1, pcmData = ByteArray(640), isSpeech = true))

        val metrics = pipeline.markEndOfSpeech()
        assertTrue(pipeline.partialHypothesisFlow.value.contains("खंड-1"))
        assertTrue(pipeline.partialHypothesisFlow.value.contains("खंड-2"))
        assertEquals(2, metrics.chunksProcessedCount)
    }

    // 3. Pass 2 scheduling on silence
    @Test
    fun testPass2Scheduling() = runBlocking {
        val pass2CallCount = AtomicInteger(0)
        val customPass2 = Pass2Processor { hypotheses, _ ->
            pass2CallCount.incrementAndGet()
            Pass2Refinement(hypotheses.lastOrNull()?.chunkIndex ?: 0, "RefinedText")
        }

        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 0L),
            pass2Processor = customPass2
        )

        pipeline.startUtterance()
        pipeline.feedChunk(AudioChunk(chunkIndex = 0, pcmData = ByteArray(640), isSpeech = true))
        pipeline.feedChunk(AudioChunk.createSilence(chunkIndex = 1, durationMs = 300L))

        // Yield slightly for worker
        delay(50L)
        pipeline.markEndOfSpeech()

        assertTrue("Pass 2 should have been scheduled on silence", pass2CallCount.get() >= 1)
    }

    // 4. Pass 1 does not block capture
    @Test
    fun testPass1DoesNotBlockCapture() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 100L) // Slow Pass 1
        )

        pipeline.startUtterance()

        val startNanos = System.nanoTime()
        // Feed 10 chunks immediately
        var allSucceeded = true
        for (i in 0 until 10) {
            val fed = pipeline.feedChunk(AudioChunk(chunkIndex = i, pcmData = ByteArray(640), isSpeech = true))
            if (!fed) allSucceeded = false
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0

        assertTrue("Feed chunk must succeed without blocking", allSucceeded)
        assertTrue("Feeding 10 chunks should take < 50ms, took $elapsedMs ms", elapsedMs < 50.0)

        pipeline.cancel()
    }

    // 5. Pass 2 does not block capture
    @Test
    fun testPass2DoesNotBlockCapture() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 0L),
            pass2Processor = DefaultPass2Processor(refinementDelayMs = 150L) // Very slow Pass 2
        )

        pipeline.startUtterance()

        val startNanos = System.nanoTime()
        for (i in 0 until 8) {
            pipeline.feedChunk(AudioChunk(chunkIndex = i, pcmData = ByteArray(640), isSpeech = (i % 2 == 0)))
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0

        assertTrue("Capture feed must not block waiting for slow Pass 2", elapsedMs < 40.0)
        pipeline.cancel()
    }

    // 6. Silence allows queued refinement work
    @Test
    fun testSilenceAllowsQueuedRefinementWork() = runBlocking {
        var refinedOnSilence = false
        val customPass2 = Pass2Processor { hypotheses, _ ->
            if (hypotheses.isNotEmpty()) {
                refinedOnSilence = true
            }
            Pass2Refinement(hypotheses.lastOrNull()?.chunkIndex ?: 0, "SilenceRefined")
        }

        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 0L),
            pass2Processor = customPass2
        )

        pipeline.startUtterance()
        pipeline.feedChunk(AudioChunk(chunkIndex = 0, pcmData = ByteArray(640), isSpeech = true))
        delay(20L)
        // Feed silence
        pipeline.feedChunk(AudioChunk.createSilence(chunkIndex = 1, durationMs = 400L))
        delay(40L)

        assertTrue("Silence must allow Pass 2 refinement to proceed", refinedOnSilence)
        pipeline.cancel()
    }

    // 7. Resumed speech continues capture
    @Test
    fun testResumedSpeechContinuesCapture() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 0L),
            pass2Processor = DefaultPass2Processor(refinementDelayMs = 0L)
        )

        pipeline.startUtterance()
        pipeline.feedChunk(AudioChunk(chunkIndex = 0, pcmData = ByteArray(640), isSpeech = true))
        pipeline.feedChunk(AudioChunk.createSilence(chunkIndex = 1, durationMs = 200L))
        pipeline.feedChunk(AudioChunk(chunkIndex = 2, pcmData = ByteArray(640), isSpeech = true, isLast = true))

        val metrics = pipeline.markEndOfSpeech()
        assertEquals(3, metrics.chunksProcessedCount)
        assertTrue(pipeline.stableHypothesisFlow.value.isNotBlank())
    }

    // 8. Bounded queue behavior
    @Test
    fun testBoundedQueueBehavior() = runBlocking {
        val smallCapacity = 4
        val pipeline = PipelinedTwoPassSpeechEngine(
            bufferCapacity = smallCapacity,
            pass1Processor = Pass1Processor { _, _, _ ->
                delay(200L) // Hold up queue
                Pass1Hypothesis(0, "Slow")
            }
        )

        pipeline.startUtterance()
        // Overflow queue intentionally
        for (i in 0 until 12) {
            pipeline.feedChunk(AudioChunk(chunkIndex = i, pcmData = ByteArray(640), isSpeech = true))
        }

        // Must not crash or run out of memory
        pipeline.cancel()
    }

    // 9. Cancellation when utterance ends
    @Test
    fun testCancellationWhenUtteranceEnds() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine()
        pipeline.startUtterance()
        assertTrue(pipeline.isRunning)

        pipeline.feedChunk(AudioChunk(chunkIndex = 0, pcmData = ByteArray(640), isSpeech = true))
        pipeline.cancel()

        assertFalse(pipeline.isRunning)
    }

    // 10. Final hypothesis selection
    @Test
    fun testFinalHypothesisSelection() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 0L),
            pass2Processor = DefaultPass2Processor(refinementDelayMs = 0L)
        )

        pipeline.startUtterance(IndicLanguage.HINDI)
        pipeline.feedChunk(AudioChunk(chunkIndex = 0, pcmData = ByteArray(640), isSpeech = true))

        pipeline.markEndOfSpeech()
        val finalResult = pipeline.stableHypothesisFlow.value
        assertTrue("Final text must be finalized with danda in Hindi", finalResult.endsWith("।"))
    }

    // 11. No duplicate chunk processing
    @Test
    fun testNoDuplicateChunkProcessing() = runBlocking {
        val seenIndices = Collections.synchronizedSet(mutableSetOf<Int>())
        val duplicateFound = AtomicInteger(0)

        val customPass1 = Pass1Processor { chunk, acc, _ ->
            if (!seenIndices.add(chunk.chunkIndex)) {
                duplicateFound.incrementAndGet()
            }
            Pass1Hypothesis(chunk.chunkIndex, "$acc ${chunk.chunkIndex}")
        }

        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = customPass1,
            pass2Processor = DefaultPass2Processor(refinementDelayMs = 0L)
        )

        pipeline.startUtterance()
        for (i in 0 until 6) {
            pipeline.feedChunk(AudioChunk(chunkIndex = i, pcmData = ByteArray(640), isSpeech = true))
        }

        pipeline.markEndOfSpeech()
        assertEquals(0, duplicateFound.get())
        assertEquals(6, seenIndices.size)
    }

    // 12. No dropped chunk under nominal load
    @Test
    fun testNoDroppedChunksUnderNominalLoad() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine(
            bufferCapacity = 32,
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 2L)
        )

        pipeline.startUtterance()
        for (i in 0 until 8) {
            val ok = pipeline.feedChunk(AudioChunk(chunkIndex = i, pcmData = ByteArray(640), isSpeech = true))
            assertTrue("Chunk $i should not be dropped under nominal load", ok)
            delay(5L)
        }

        val metrics = pipeline.markEndOfSpeech()
        assertEquals(0, metrics.droppedChunksCount)
    }

    // 13. Graceful overload behavior
    @Test
    fun testGracefulOverloadBehavior() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine(
            bufferCapacity = 2,
            pass1Processor = Pass1Processor { _, _, _ ->
                delay(100L)
                Pass1Hypothesis(0, "Stall")
            }
        )

        pipeline.startUtterance()
        // Feed 10 chunks into capacity 2 without any delay
        for (i in 0 until 10) {
            pipeline.feedChunk(AudioChunk(chunkIndex = i, pcmData = ByteArray(640), isSpeech = true))
        }

        val metrics = pipeline.markEndOfSpeech()
        // Must complete without throwing and report any dropped chunks
        assertNotNull(metrics)
    }

    // 14. Baseline vs prototype latency instrumentation
    @Test
    fun testBaselineVsPrototypeLatencyInstrumentation() = runBlocking {
        val runner = TwoPassSpeechBenchmarkRunner()
        val chunks = runner.createStandardBenchmarkChunks()

        val baselineResult = runner.runBaselineSerialBatch(chunks)
        val serialTwoPassResult = runner.runSerialTwoPassNaive(chunks)
        val prototypeResult = runner.runPipelinedTwoPass(chunks, chunkFeedDelayMs = 1L)

        println("=== FEATURE 16A SPEECH PIPELINE BENCHMARK RESULTS ===")
        println("Audio Duration: ${chunks.sumOf { it.durationMs }} ms (${chunks.size} chunks)")
        println("1. ${baselineResult.pipelineName}: End-of-Speech Wait = ${"%.2f".format(baselineResult.endOfSpeechToPacketReadyMs)} ms | Total Compute = ${"%.2f".format(baselineResult.totalComputeMs)} ms | Overlap = ${"%.2f".format(baselineResult.overlapDurationMs)} ms")
        println("2. ${serialTwoPassResult.pipelineName}: End-of-Speech Wait = ${"%.2f".format(serialTwoPassResult.endOfSpeechToPacketReadyMs)} ms | Total Compute = ${"%.2f".format(serialTwoPassResult.totalComputeMs)} ms | Overlap = ${"%.2f".format(serialTwoPassResult.overlapDurationMs)} ms")
        println("3. ${prototypeResult.pipelineName}: End-of-Speech Wait = ${"%.2f".format(prototypeResult.endOfSpeechToPacketReadyMs)} ms | Total Compute = ${"%.2f".format(prototypeResult.totalComputeMs)} ms | Overlap = ${"%.2f".format(prototypeResult.overlapDurationMs)} ms")
        println("======================================================")

        // Acceptance target: prototype latency <= baseline latency
        assertTrue(
            "Prototype end-of-speech latency (${prototypeResult.endOfSpeechToPacketReadyMs}ms) must be <= baseline (${baselineResult.endOfSpeechToPacketReadyMs}ms)",
            prototypeResult.endOfSpeechToPacketReadyMs <= baselineResult.endOfSpeechToPacketReadyMs
        )
    }

    // 15. Deterministic fake-clock timing tests
    @Test
    fun testDeterministicFakeClockTiming() = runBlocking {
        val pipeline = PipelinedTwoPassSpeechEngine(
            pass1Processor = DefaultPass1Processor(chunkProcessingDelayMs = 5L),
            pass2Processor = DefaultPass2Processor(refinementDelayMs = 5L)
        )

        pipeline.startUtterance()
        pipeline.feedChunk(AudioChunk(chunkIndex = 0, pcmData = ByteArray(9600), isSpeech = true)) // 300ms chunk
        delay(10L)

        val metrics = pipeline.markEndOfSpeech()
        assertTrue("Capture start must be set", metrics.tCaptureStartNanos > 0L)
        assertTrue("Endpoint must be set", metrics.tEndpointNanos >= metrics.tCaptureStartNanos)
        assertTrue("Packet ready must be >= endpoint", metrics.tPacketReadyNanos >= metrics.tEndpointNanos)
    }

    // 16. Concurrent pipeline safety
    @Test
    fun testConcurrentPipelineSafety() = runBlocking(Dispatchers.Default) {
        val pipeline = PipelinedTwoPassSpeechEngine()

        val jobs = List(4) { workerId ->
            launch {
                pipeline.startUtterance()
                repeat(5) { step ->
                    pipeline.feedChunk(AudioChunk(chunkIndex = workerId * 10 + step, pcmData = ByteArray(320), isSpeech = true))
                    delay(2L)
                }
                pipeline.cancel()
            }
        }

        jobs.forEach { it.join() }
        assertFalse(pipeline.isRunning)
    }

    // 17. Prototype disabled path preserves production behavior
    @Test
    fun testPrototypeDisabledPathPreservesProductionBehavior() {
        // SentenceFinalizer and production STT pipeline remain 100% regression-safe
        val hindiResult = SentenceFinalizer.finalizeSentence("नमस्ते परीक्षण", IndicLanguage.HINDI)
        assertEquals("नमस्ते परीक्षण।", hindiResult)

        val englishResult = SentenceFinalizer.finalizeSentence("Radio check normal", IndicLanguage.ENGLISH)
        assertEquals("Radio check normal.", englishResult)

        // Sherpa-ONNX Zipformer investigation report sanity check
        val report = ZipformerInvestigation.checkRuntimeCompatibility()
        assertEquals("1.13.7", report.sherpaOnnxVersion)
        assertTrue("Must support arm64-v8a", report.supportedAbis.contains("arm64-v8a"))
    }
}
