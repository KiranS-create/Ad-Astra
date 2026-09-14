package org.sih.itantra.core.speech

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.network.AdaptiveNetworkMode
import org.sih.itantra.core.speech.benchmark.ScriptedPass1Processor
import org.sih.itantra.core.speech.benchmark.TargetedRefinementBenchmarkRunner
import org.sih.itantra.core.speech.pipeline.AudioChunk
import org.sih.itantra.core.speech.pipeline.Pass1Hypothesis
import org.sih.itantra.core.speech.refinement.DefaultTargetedPass2Refiner
import org.sih.itantra.core.speech.refinement.RefinementBudget
import org.sih.itantra.core.speech.refinement.RefinementCandidate
import org.sih.itantra.core.speech.refinement.RefinementReason
import org.sih.itantra.core.speech.refinement.TargetedRefinementPolicy
import org.sih.itantra.core.speech.refinement.TargetedTwoPassSpeechEngine
import org.sih.itantra.core.vbr.AdaptiveRepresentationMode
import org.sih.itantra.core.vbr.AdaptiveRepresentationPolicy

class TargetedRefinementTest {

    private val policy = TargetedRefinementPolicy()
    private val refiner = DefaultTargetedPass2Refiner(simulatedRefinementDelayMs = 0L)

    // ==========================================
    // 1. CANDIDATE SELECTION TESTS (10 TESTS)
    // ==========================================

    @Test
    fun testCandidateSelection_EmergencyKeyword_English() {
        val hyp = Pass1Hypothesis(chunkIndex = 1, partialText = "We need medical assistance now", confidence = 0.9f)
        val candidates = policy.evaluateCandidates(hyp, null, IndicLanguage.HINDI)

        assertTrue("Should detect medical emergency keyword", candidates.any { it.tokenText.equals("medical", ignoreCase = true) })
        val med = candidates.first { it.tokenText.equals("medical", ignoreCase = true) }
        assertEquals(RefinementReason.EMERGENCY_KEYWORD, med.reason)
        assertEquals(1.0f, med.priorityScore, 0.001f)
    }

    @Test
    fun testCandidateSelection_EmergencyKeyword_Hindi() {
        val hyp = Pass1Hypothesis(chunkIndex = 1, partialText = "यहाँ तुरंत मदद भेजो घायल ऑपरेटर", confidence = 0.9f)
        val candidates = policy.evaluateCandidates(hyp, null, IndicLanguage.HINDI)

        assertTrue("Should detect मदद", candidates.any { it.tokenText == "मदद" })
        assertTrue("Should detect घायल", candidates.any { it.tokenText == "घायल" })
    }

    @Test
    fun testCandidateSelection_EmergencyKeyword_Tamil() {
        val hyp = Pass1Hypothesis(chunkIndex = 1, partialText = "உடனடி உதவி தேவை", confidence = 0.9f)
        val candidates = policy.evaluateCandidates(hyp, null, IndicLanguage.TAMIL)

        assertTrue("Should detect உதவி", candidates.any { it.tokenText == "உதவி" })
    }

    @Test
    fun testCandidateSelection_TacticalCoordinate_Digits() {
        val hyp = Pass1Hypothesis(chunkIndex = 2, partialText = "Report coordinates 72.45 to base", confidence = 0.9f)
        val candidates = policy.evaluateCandidates(hyp, null, IndicLanguage.HINDI)

        assertTrue("Should detect coordinate 72.45", candidates.any { it.tokenText == "72.45" })
        val coord = candidates.first { it.tokenText == "72.45" }
        assertEquals(RefinementReason.TACTICAL_COORDINATE_OR_NUMBER, coord.reason)
    }

    @Test
    fun testCandidateSelection_TacticalCoordinate_Callsign() {
        val hyp = Pass1Hypothesis(chunkIndex = 2, partialText = "patrol moving to sector 4 checkpoint bravo", confidence = 0.9f)
        val candidates = policy.evaluateCandidates(hyp, null, IndicLanguage.HINDI)

        assertTrue("Should detect sector", candidates.any { it.tokenText.equals("sector", ignoreCase = true) })
        assertTrue("Should detect 4", candidates.any { it.tokenText == "4" })
        assertTrue("Should detect bravo", candidates.any { it.tokenText.equals("bravo", ignoreCase = true) })
    }

    @Test
    fun testCandidateSelection_HypothesisInstability() {
        val prev = Pass1Hypothesis(chunkIndex = 1, partialText = "team reached wayward")
        val curr = Pass1Hypothesis(chunkIndex = 2, partialText = "team reached waypoint")

        val candidates = policy.evaluateCandidates(curr, prev, IndicLanguage.HINDI)
        assertTrue("Should detect instability on waypoint/wayward", candidates.any { it.reason == RefinementReason.HYPOTHESIS_INSTABILITY })
    }

    @Test
    fun testCandidateSelection_LowConfidence() {
        val lowConfPolicy = TargetedRefinementPolicy(confidenceThreshold = 0.85f)
        val hyp = Pass1Hypothesis(chunkIndex = 1, partialText = "patrol active", confidence = 0.65f)

        val candidates = lowConfPolicy.evaluateCandidates(hyp, null, IndicLanguage.HINDI)
        assertTrue("Should flag low confidence tokens", candidates.any { it.reason == RefinementReason.LOW_CONFIDENCE })
    }

    @Test
    fun testCandidateSelection_IndicScriptAmbiguity() {
        val hyp = Pass1Hypothesis(chunkIndex = 1, partialText = "unit sector४ reached", confidence = 0.9f)
        val candidates = policy.evaluateCandidates(hyp, null, IndicLanguage.HINDI)

        assertTrue("Should detect mixed script or tactical token", candidates.isNotEmpty())
    }

    @Test
    fun testCandidateSelection_EmptyHypothesis() {
        val hyp = Pass1Hypothesis(chunkIndex = 0, partialText = "", confidence = 0.9f)
        val candidates = policy.evaluateCandidates(hyp, null, IndicLanguage.HINDI)
        assertTrue("Empty hypothesis must yield empty candidate list", candidates.isEmpty())
    }

    @Test
    fun testCandidateSelection_PriorityOrdering() {
        val hyp = Pass1Hypothesis(chunkIndex = 1, partialText = "sector 4 has medical emergency", confidence = 0.70f)
        val candidates = policy.evaluateCandidates(hyp, null, IndicLanguage.HINDI)

        assertTrue("Candidates should be sorted descending by priority", candidates.size >= 2)
        for (i in 0 until candidates.size - 1) {
            assertTrue(
                "Priority must be non-increasing",
                candidates[i].priorityScore >= candidates[i + 1].priorityScore
            )
        }
    }

    // ==========================================
    // 2. REFINEMENT BUDGET TESTS (6 TESTS)
    // ==========================================

    @Test
    fun testBudget_EnforcesMaxCandidatesPerWindow() {
        val budget = RefinementBudget(maxCandidatesPerSilenceWindow = 2, maxTotalRefinementsPerUtterance = 6)
        val candidates = listOf(
            RefinementCandidate(1, "sos", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 0, 3),
            RefinementCandidate(1, "medical", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 4, 11),
            RefinementCandidate(1, "sector", 0.85f, RefinementReason.TACTICAL_COORDINATE_OR_NUMBER, 12, 18)
        )

        val filtered = budget.filterCandidates(candidates)
        assertEquals("Should only accept 2 candidates per silence window", 2, filtered.size)
        assertEquals(1, budget.getBudgetStats().totalAbandonedDueToBudget)
    }

    @Test
    fun testBudget_EnforcesMaxTotalPerUtterance() {
        val budget = RefinementBudget(maxCandidatesPerSilenceWindow = 2, maxTotalRefinementsPerUtterance = 3)

        // Window 1: 2 candidates applied
        val cand1 = RefinementCandidate(1, "sos", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 0, 3)
        val cand2 = RefinementCandidate(1, "medical", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 4, 11)
        budget.filterCandidates(listOf(cand1, cand2)).forEach { budget.recordRefinementApplied(it) }

        // Window 2: attempts 2 candidates, only 1 should be accepted due to utterance limit of 3
        val cand3 = RefinementCandidate(2, "fire", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 12, 16)
        val cand4 = RefinementCandidate(2, "sector", 0.85f, RefinementReason.TACTICAL_COORDINATE_OR_NUMBER, 17, 23)
        val filteredWindow2 = budget.filterCandidates(listOf(cand3, cand4))

        assertEquals("Should only accept 1 more candidate to reach utterance limit of 3", 1, filteredWindow2.size)
        filteredWindow2.forEach { budget.recordRefinementApplied(it) }

        assertTrue("Utterance budget should now be exhausted", budget.isBudgetExhausted())

        // Window 3: attempts more, all must be rejected
        val cand5 = RefinementCandidate(3, "ambush", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 24, 30)
        val filteredWindow3 = budget.filterCandidates(listOf(cand5))
        assertEquals("Exhausted budget must accept 0 candidates", 0, filteredWindow3.size)
    }

    @Test
    fun testBudget_SuppressesDuplicateCandidates() {
        val budget = RefinementBudget(maxCandidatesPerSilenceWindow = 3, maxTotalRefinementsPerUtterance = 5)
        val cand = RefinementCandidate(1, "injured", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 0, 7)

        val firstPass = budget.filterCandidates(listOf(cand))
        assertEquals(1, firstPass.size)
        budget.recordRefinementApplied(firstPass[0])

        // Pass exact same token and span in next window
        val secondPass = budget.filterCandidates(listOf(cand))
        assertEquals("Duplicate token span must be suppressed", 0, secondPass.size)
        assertEquals(1, budget.getBudgetStats().totalDuplicatesSuppressed)
    }

    @Test
    fun testBudget_CountsAbandonedCandidates() {
        val budget = RefinementBudget(maxCandidatesPerSilenceWindow = 1, maxTotalRefinementsPerUtterance = 2)
        val cands = listOf(
            RefinementCandidate(1, "c1", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 0, 2),
            RefinementCandidate(1, "c2", 0.9f, RefinementReason.EMERGENCY_KEYWORD, 3, 5),
            RefinementCandidate(1, "c3", 0.8f, RefinementReason.EMERGENCY_KEYWORD, 6, 8)
        )

        budget.filterCandidates(cands)
        assertEquals("2 candidates should be recorded as abandoned", 2, budget.getBudgetStats().totalAbandonedDueToBudget)
    }

    @Test
    fun testBudget_ResetClearsAllCounters() {
        val budget = RefinementBudget()
        val cand = RefinementCandidate(1, "sos", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 0, 3)
        budget.filterCandidates(listOf(cand)).forEach { budget.recordRefinementApplied(it) }

        budget.reset()
        val stats = budget.getBudgetStats()
        assertEquals(0, stats.totalRefinementsExecuted)
        assertEquals(0, stats.totalAbandonedDueToBudget)
        assertEquals(0, stats.totalDuplicatesSuppressed)
        assertFalse(budget.isBudgetExhausted())
    }

    @Test
    fun testBudget_IsExhaustedReturnsTrueWhenFull() {
        val budget = RefinementBudget(maxTotalRefinementsPerUtterance = 1)
        assertFalse(budget.isBudgetExhausted())
        budget.recordRefinementApplied(RefinementCandidate(1, "w", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 0, 1))
        assertTrue(budget.isBudgetExhausted())
    }

    // ==========================================
    // 3. TARGETED REFINER TESTS (3 TESTS)
    // ==========================================

    @Test
    fun testDefaultTargetedRefiner_EmergencyStandardization() = runBlocking {
        val candidate = RefinementCandidate(1, "madad", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 0, 5)
        val result = refiner.refineCandidate(candidate, "madad chahiye", IndicLanguage.HINDI)

        assertEquals("मदद", result.refinedText)
        assertTrue(result.wasApplied)
    }

    @Test
    fun testDefaultTargetedRefiner_TacticalPrefixStandardization() = runBlocking {
        val candidate = RefinementCandidate(1, "sector", 0.85f, RefinementReason.TACTICAL_COORDINATE_OR_NUMBER, 0, 6)
        val result = refiner.refineCandidate(candidate, "sector 4", IndicLanguage.HINDI)

        assertEquals("SECTOR", result.refinedText)
        assertTrue(result.wasApplied)
    }

    @Test
    fun testDefaultTargetedRefiner_CalculatesLatency() = runBlocking {
        val delayedRefiner = DefaultTargetedPass2Refiner(simulatedRefinementDelayMs = 5L)
        val candidate = RefinementCandidate(1, "sos", 1.0f, RefinementReason.EMERGENCY_KEYWORD, 0, 3)
        val result = delayedRefiner.refineCandidate(candidate, "sos", IndicLanguage.HINDI)

        assertTrue("Refinement latency must be positive", result.computeNanos > 0L)
    }

    // ==========================================
    // 4. ENGINE & ZERO ENDPOINT WAIT TESTS (6 TESTS)
    // ==========================================

    @Test
    fun testEngine_ZeroPostSpeechWait() = runBlocking {
        val words = listOf("SOS", "patrol", "moving", "to", "sector", "4")
        val engine = TargetedTwoPassSpeechEngine(
            pass1Processor = ScriptedPass1Processor(words, chunkProcessingDelayMs = 0L),
            targetedRefiner = DefaultTargetedPass2Refiner(simulatedRefinementDelayMs = 0L)
        )

        engine.startUtterance(IndicLanguage.HINDI)

        // Feed speech chunks
        engine.feedChunk(AudioChunk(0, ByteArray(9600), isSpeech = true))
        engine.feedChunk(AudioChunk(1, ByteArray(9600), isSpeech = true))

        // Feed silence chunk (opportunistic refinement)
        engine.feedChunk(AudioChunk.createSilence(2, durationMs = 200L))
        kotlinx.coroutines.delay(20L) // Allow background silence worker to execute

        // Feed more speech chunks
        engine.feedChunk(AudioChunk(3, ByteArray(9600), isSpeech = true))
        engine.feedChunk(AudioChunk(4, ByteArray(9600), isSpeech = true, isLast = true))

        // Trigger endpoint
        val metrics = engine.markEndOfSpeech()
        val targetedMetrics = engine.getTargetedMetrics()

        assertNotNull("Targeted metrics should be non-null", targetedMetrics)
        assertEquals("Post-endpoint refinements count must be strictly 0", 0, targetedMetrics!!.postEndpointRefinementsCount)
        assertEquals("Endpoint waiting nanos must be strictly 0", 0L, targetedMetrics.endpointWaitingNanos)
    }

    @Test
    fun testEngine_EndpointLatencyUnder25Ms() = runBlocking {
        val words = listOf("Team", "at", "waypoint")
        val engine = TargetedTwoPassSpeechEngine(
            pass1Processor = ScriptedPass1Processor(words, chunkProcessingDelayMs = 0L),
            targetedRefiner = DefaultTargetedPass2Refiner(simulatedRefinementDelayMs = 0L)
        )

        engine.startUtterance(IndicLanguage.HINDI)
        engine.feedChunk(AudioChunk(0, ByteArray(9600), isSpeech = true, isLast = true))

        engine.markEndOfSpeech()
        val metrics = engine.getTargetedMetrics()!!

        assertTrue(
            "Endpoint to transcript latency must be near-zero (<25ms), was ${metrics.endpointToFinalResultLatencyMs} ms",
            metrics.endpointToFinalResultLatencyMs < 25.0
        )
    }

    @Test
    fun testEngine_AppliesRefinementsInFinalTranscript() = runBlocking {
        val words = listOf("madad", "chahiye", "sector", "4")
        val engine = TargetedTwoPassSpeechEngine(
            pass1Processor = ScriptedPass1Processor(words, chunkProcessingDelayMs = 0L),
            targetedRefiner = DefaultTargetedPass2Refiner(simulatedRefinementDelayMs = 0L)
        )

        engine.startUtterance(IndicLanguage.HINDI)

        // Chunk 0: madad
        engine.feedChunk(AudioChunk(0, ByteArray(9600), isSpeech = true))

        // Silence window: triggers refinement of madad -> मदद
        engine.feedChunk(AudioChunk.createSilence(1, durationMs = 250L))
        kotlinx.coroutines.delay(30L)

        // Chunk 2: sector 4
        engine.feedChunk(AudioChunk(2, ByteArray(9600), isSpeech = true, isLast = true))

        engine.markEndOfSpeech()
        val finalTranscript = engine.stableHypothesisFlow.value

        assertTrue(
            "Final transcript must contain refined Hindi token मदद: $finalTranscript",
            finalTranscript.contains("मदद")
        )
    }

    @Test
    fun testEngine_FeedChunkNonBlocking() {
        val engine = TargetedTwoPassSpeechEngine(bufferCapacity = 4)
        engine.startUtterance(IndicLanguage.HINDI)

        // Feed chunks without blocking
        val fed1 = engine.feedChunk(AudioChunk(0, ByteArray(9600), isSpeech = true))
        val fed2 = engine.feedChunk(AudioChunk(1, ByteArray(9600), isSpeech = true))

        assertTrue(fed1)
        assertTrue(fed2)
        engine.cancel()
    }

    @Test
    fun testEngine_CancellationStopsWorkers() {
        val engine = TargetedTwoPassSpeechEngine()
        engine.startUtterance(IndicLanguage.HINDI)
        assertTrue(engine.isRunning)

        engine.cancel()
        assertFalse(engine.isRunning)
    }

    @Test
    fun testEngine_ProducesValidMetricsFlow() = runBlocking {
        val engine = TargetedTwoPassSpeechEngine()
        engine.startUtterance(IndicLanguage.HINDI)
        engine.feedChunk(AudioChunk(0, ByteArray(9600), isSpeech = true, isLast = true))

        val metrics = engine.markEndOfSpeech()
        assertNotNull(engine.pipelineMetricsFlow.value)
        assertEquals(metrics, engine.pipelineMetricsFlow.value)
    }

    // ==========================================
    // 5. BENCHMARK RUNNER & VBR INTEGRATION (4 TESTS)
    // ==========================================

    @Test
    fun testBenchmarkRunner_TargetedPipelineExecutesSuccessfully() = runBlocking {
        val runner = TargetedRefinementBenchmarkRunner()
        val chunks = runner.createUtteranceChunks(phrase1ChunkCount = 2, silenceDurationMs = 200L, phrase2ChunkCount = 2)

        val (res, metrics) = runner.runTargetedRefinement(
            chunks = chunks,
            words = listOf("SOS", "medical", "at", "base"),
            language = IndicLanguage.HINDI,
            chunkFeedDelayMs = 0L,
            pass1DelayMs = 2L,
            refinementDelayMs = 2L
        )

        assertNotNull(res)
        assertEquals("Pipeline D: Feature 17 Targeted Refinement", res.pipelineName)
        assertTrue(res.isRealTimeStreaming)
        assertEquals(0, metrics.postEndpointRefinementsCount)
    }

    @Test
    fun testBenchmarkRunner_TargetedBeatsBaselineLatency() = runBlocking {
        val runner = TargetedRefinementBenchmarkRunner()
        val chunks = runner.createUtteranceChunks(phrase1ChunkCount = 2, silenceDurationMs = 200L, phrase2ChunkCount = 2)
        val words = listOf("Report", "sector", "4", "verified")

        val result = runner.runComparativeBenchmark(
            utteranceName = "Test Numbers",
            utteranceText = "Report sector 4 verified",
            chunks = chunks,
            words = words,
            language = IndicLanguage.HINDI
        )

        assertTrue(
            "Targeted endpoint latency (${result.pipelineD_Feature17Targeted.endOfSpeechToPacketReadyMs}ms) must be lower than Baseline (${result.pipelineA_SerialBatch.endOfSpeechToPacketReadyMs}ms)",
            result.pipelineD_Feature17Targeted.endOfSpeechToPacketReadyMs < result.pipelineA_SerialBatch.endOfSpeechToPacketReadyMs
        )
        assertTrue("Latency reduction must be > 50%", result.latencyReductionVsBaselinePercent > 50.0)
    }

    @Test
    fun testBenchmarkRunner_StandardSuiteExecutesAll5Utterances() = runBlocking {
        val runner = TargetedRefinementBenchmarkRunner()
        val results = runner.runStandardSuite()

        assertEquals("Must execute exactly 5 utterances in standard benchmark suite", 5, results.size)
        for (res in results) {
            assertEquals(0, res.targetedMetrics.postEndpointRefinementsCount)
            assertTrue("Targeted refinement must reduce latency vs baseline", res.latencyReductionVsBaselinePercent > 0.0)
        }
    }

    @Test
    fun testIntegration_TargetedTextIntegratesWithAdaptiveRepresentationPolicy() {
        // 1. Emergency refined text with high confidence -> SEMANTIC under DEGRADED
        val emergencyRefinedText = "Ambulance required for 3 injured"
        val decEmergency = AdaptiveRepresentationPolicy.select(
            text = emergencyRefinedText,
            networkMode = AdaptiveNetworkMode.DEGRADED,
            semanticConfidence = 0.95f
        )
        assertEquals(AdaptiveRepresentationMode.SEMANTIC, decEmergency.mode)
        assertNotNull(decEmergency.semanticCommand)

        // 2. Tactical numbers -> COMPACT under LIMITED
        val tacticalRefinedText = "Could you please confirm if convoy has reached checkpoint 3"
        val decTactical = AdaptiveRepresentationPolicy.select(
            text = tacticalRefinedText,
            networkMode = AdaptiveNetworkMode.LIMITED,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.COMPACT, decTactical.mode)

        // 3. Normal conversational -> FULL under HEALTHY
        val normalText = "Team this is patrol base moving to waypoint"
        val decNormal = AdaptiveRepresentationPolicy.select(
            text = normalText,
            networkMode = AdaptiveNetworkMode.HEALTHY,
            language = IndicLanguage.ENGLISH
        )
        assertEquals(AdaptiveRepresentationMode.FULL, decNormal.mode)
        assertEquals(normalText, decNormal.text)
    }
}
