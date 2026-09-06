package org.sih.itantra.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.mesh.ManetSimulator
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.protocol.SemanticEmergencyClassifier
import java.util.Locale

/**
 * Unit test suite for SIH Tactical Demo Mode:
 * Covers deterministic initialization, scenario selection, simulation safety barriers,
 * dynamic semantic measurements, QoS pre-emption state, routing resilience, and step progression.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SihDemoTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var manetSimulator: ManetSimulator
    private lateinit var coordinator: SihDemoCoordinator

    @Before
    fun setUp() {
        manetSimulator = ManetSimulator()
        coordinator = SihDemoCoordinator(manetSimulator, testScope)
    }

    // -------------------------------------------------------------------------
    // A. Demo State & Initialization (3 tests)
    // -------------------------------------------------------------------------

    @Test
    fun `test01 Initial demo state is deterministic and clean`() {
        val state = coordinator.state.value
        assertEquals(DemoScenario.NORMAL_VOICE, state.selectedScenario)
        assertEquals(0, state.currentStepIndex)
        assertEquals(8, state.totalSteps)
        assertFalse(state.isAutoRunning)
        assertTrue("All stages must start IDLE", state.stageStates.values.all { it == StageState.IDLE })
        assertEquals("READY", state.deliveryStatus)
    }

    @Test
    fun `test02 Scenario selection updates parameters correctly`() {
        coordinator.selectScenario(DemoScenario.SEMANTIC_COMPRESSION)
        val s2 = coordinator.state.value
        assertEquals(DemoScenario.SEMANTIC_COMPRESSION, s2.selectedScenario)
        assertEquals(SihDemoCoordinator.SCENARIO_2_UTTERANCE, s2.sampleUtterance)
        assertEquals(0, s2.currentStepIndex)

        coordinator.selectScenario(DemoScenario.CONGESTION_PREEMPTION)
        val s4 = coordinator.state.value
        assertEquals(DemoScenario.CONGESTION_PREEMPTION, s4.selectedScenario)
        assertEquals("BUSY", s4.congestionState)
        assertEquals(35, s4.queueDepth)
    }

    @Test
    fun `test03 Reset demo restores pristine step index and idle stages`() {
        coordinator.selectScenario(DemoScenario.MULTI_HOP_DISTRESS)
        coordinator.nextStep()
        coordinator.nextStep()
        assertEquals(2, coordinator.state.value.currentStepIndex)

        coordinator.resetDemo()
        val resetState = coordinator.state.value
        assertEquals(0, resetState.currentStepIndex)
        assertTrue("Stages must reset to IDLE", resetState.stageStates.values.all { it == StageState.IDLE })
        assertEquals(DemoScenario.MULTI_HOP_DISTRESS, resetState.selectedScenario)
    }

    // -------------------------------------------------------------------------
    // B. Simulation Safety (2 tests)
    // -------------------------------------------------------------------------

    @Test
    fun `test04 Simulation safety flag is unconditionally true`() {
        assertTrue("isSimulation must always be true for demo coordinator", coordinator.state.value.isSimulation)
        DemoScenario.entries.forEach { scenario ->
            coordinator.selectScenario(scenario)
            assertTrue("isSimulation must remain true across all scenarios", coordinator.state.value.isSimulation)
        }
    }

    @Test
    fun `test05 Status summary is always labeled with SIMULATION`() {
        DemoScenario.entries.forEach { scenario ->
            coordinator.selectScenario(scenario)
            assertTrue("Status must include [SIMULATION]", coordinator.state.value.statusSummary.contains("[SIMULATION]"))
            coordinator.nextStep()
            assertTrue("Step status must include [SIMULATION]", coordinator.state.value.statusSummary.contains("[SIMULATION]"))
        }
    }

    // -------------------------------------------------------------------------
    // C. Pipeline & Dynamic Measurements (3 tests)
    // -------------------------------------------------------------------------

    @Test
    fun `test06 Normal voice pipeline advances through 8 stages to DELIVERED`() {
        coordinator.selectScenario(DemoScenario.NORMAL_VOICE)
        for (i in 1..8) {
            coordinator.nextStep()
            assertEquals(i, coordinator.state.value.currentStepIndex)
        }
        val finalState = coordinator.state.value
        assertEquals(8, finalState.currentStepIndex)
        assertTrue(finalState.deliveryStatus.contains("DELIVERED ✓"))
        assertEquals(StageState.SUCCESS, finalState.stageStates[PipelineStage.TTS])
        assertEquals(StageState.SUCCESS, finalState.stageStates[PipelineStage.ACK])
    }

    @Test
    fun `test07 Semantic scenario dynamically calculates 6-byte compression and savings`() {
        coordinator.selectScenario(DemoScenario.SEMANTIC_COMPRESSION)
        val metrics = coordinator.state.value.lowBitrateMetrics

        assertEquals(6, metrics.semanticBytes)
        assertEquals(6, metrics.payloadBytes)
        assertTrue("Original text bytes must be > 50", metrics.originalTextBytes > 50)
        assertTrue("Savings must exceed 80%", metrics.payloadSavingsPercent > 80.0)

        // Verify underlying production classifier gives matching structured output
        val classification = SemanticEmergencyClassifier.classify(SihDemoCoordinator.SCENARIO_2_UTTERANCE.lowercase(Locale.ROOT))
        assertNotNull("Must classify medical emergency utterance", classification)
        assertEquals(6, classification!!.serialize().size)
    }

    @Test
    fun `test08 Wire frame breakdown accurately reflects 25B header, 8B auth, 4B CRC`() {
        coordinator.selectScenario(DemoScenario.NORMAL_VOICE)
        val m1 = coordinator.state.value.lowBitrateMetrics
        assertEquals(25 + m1.payloadBytes + 8 + 4, m1.wireBytes)
        assertEquals(8, m1.authTagBytes)
        assertEquals(4, m1.crcBytes)
        assertEquals(25, m1.headerBytes)

        coordinator.selectScenario(DemoScenario.SEMANTIC_COMPRESSION)
        val m2 = coordinator.state.value.lowBitrateMetrics
        assertEquals(25 + 6 + 8 + 4, m2.wireBytes) // 43 bytes total wire
        assertEquals(43, m2.wireBytes)
    }

    // -------------------------------------------------------------------------
    // D. Multi-hop & MANET Routing (2 tests)
    // -------------------------------------------------------------------------

    @Test
    fun `test09 Scenario 3 multi-hop distress path connects Node A to Node C via Node B`() {
        coordinator.selectScenario(DemoScenario.MULTI_HOP_DISTRESS)
        val path = coordinator.state.value.networkPath
        assertEquals(3, path.size)
        assertEquals(101, path[0].nodeId)
        assertEquals(102, path[1].nodeId)
        assertEquals(103, path[2].nodeId)
        assertEquals(2, coordinator.state.value.activeRouteHops)
    }

    @Test
    fun `test10 Scenario 5 failure resilience marks Node B offline with alternate Node D`() {
        coordinator.selectScenario(DemoScenario.FAILURE_RESILIENCE)
        val path = coordinator.state.value.networkPath
        assertEquals(4, path.size)
        val nodeB = path.first { it.nodeId == 102 }
        assertFalse("Node B must be marked offline", nodeB.isOnline)
        val nodeD = path.first { it.nodeId == 104 }
        assertTrue("Node D must be online alternate", nodeD.isOnline)
    }

    // -------------------------------------------------------------------------
    // E. Tactical QoS & Congestion (2 tests)
    // -------------------------------------------------------------------------

    @Test
    fun `test11 Scenario 4 configures queue depth and BUSY congestion state`() {
        coordinator.selectScenario(DemoScenario.CONGESTION_PREEMPTION)
        val state = coordinator.state.value
        assertEquals(35, state.queueDepth)
        assertEquals("BUSY", state.congestionState)
        assertEquals(100, state.maxQueueCapacity)
    }

    @Test
    fun `test12 Scenario 4 records emergency pre-emption counter`() {
        coordinator.selectScenario(DemoScenario.CONGESTION_PREEMPTION)
        assertEquals(1L, coordinator.state.value.distressPreemptions)
    }

    // -------------------------------------------------------------------------
    // F. Security & Anti-Replay (2 tests)
    // -------------------------------------------------------------------------

    @Test
    fun `test13 Security status specifies HMAC-SHA256 authenticated`() {
        val sec = coordinator.state.value.securityStatus
        assertTrue(sec.contains("HMAC-SHA256"))
        assertFalse("Must not claim encryption", sec.contains("ENCRYPTED", ignoreCase = true))
    }

    @Test
    fun `test14 Replay window status specifies 64-packet bitmask`() {
        val replay = coordinator.state.value.replayWindowStatus
        assertTrue(replay.contains("64-PACKET"))
    }

    // -------------------------------------------------------------------------
    // G. Benchmarks & Narration (2 tests)
    // -------------------------------------------------------------------------

    @Test
    fun `test15 Benchmark metrics contain realistic measured non-zero timings`() {
        coordinator.selectScenario(DemoScenario.SEMANTIC_COMPRESSION)
        val b = coordinator.state.value.benchmarkMetrics
        assertTrue("STT latency must be > 0", b.sttLatencyMs > 0)
        assertTrue("HMAC gen must be > 0", b.hmacGenMicros > 0)
        assertTrue("Semantic classification must be > 0", b.semanticClassificationMicros > 0)
        assertTrue("ACK RTT must be > 0", b.ackRttMs > 0)
    }

    @Test
    fun `test16 Judge script narration updates synchronously with demo step`() {
        coordinator.selectScenario(DemoScenario.NORMAL_VOICE)
        val n0 = coordinator.state.value.judgeScriptNarration
        assertTrue(n0.contains("1. Ready"))

        coordinator.nextStep() // step 1 (MIC)
        val n1 = coordinator.state.value.judgeScriptNarration
        assertTrue(n1.contains("2. [MIC]"))

        coordinator.nextStep() // step 2 (STT)
        val n2 = coordinator.state.value.judgeScriptNarration
        assertTrue(n2.contains("3. [STT]"))
    }
}
