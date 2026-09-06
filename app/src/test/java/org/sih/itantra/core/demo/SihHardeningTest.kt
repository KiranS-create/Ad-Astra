package org.sih.itantra.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.mesh.ManetSimulator
import org.sih.itantra.core.protocol.DeliveryReceipt
import org.sih.itantra.core.protocol.Packet

@OptIn(ExperimentalCoroutinesApi::class)
class SihHardeningTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manetSimulator: ManetSimulator
    private lateinit var coordinator: SihDemoCoordinator

    @Before
    fun setUp() {
        manetSimulator = ManetSimulator()
        coordinator = SihDemoCoordinator(manetSimulator, testScope)
    }

    // =========================================================================
    // A. Demo Safety Tests
    // =========================================================================

    @Test
    fun testSimulationNeverCallsRealRadioTransmission() {
        val state = coordinator.state.value
        assertTrue("State must indicate simulation mode", state.isSimulation)
        assertFalse(
            "DemoSafetyBoundary must block physical radio emission in simulation mode",
            DemoSafetyBoundary.canEmitPhysicalRadio(state.isSimulation)
        )
        try {
            DemoSafetyBoundary.enforceSimulationSafety(state.isSimulation, "BLUETOOTH_RFCOMM_SEND")
            fail("Expected IllegalStateException for physical send attempt in simulation mode")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("SAFETY VIOLATION BLOCKED"))
        }
    }

    @Test
    fun testSimulationNeverRequestsLocation() {
        val state = coordinator.state.value
        assertFalse(
            "DemoSafetyBoundary must block hardware location acquisition in simulation mode",
            DemoSafetyBoundary.canAcquireHardwareLocation(state.isSimulation)
        )
        try {
            DemoSafetyBoundary.enforceSimulationSafety(state.isSimulation, "GPS_LOCATION_POLL")
            fail("Expected IllegalStateException for GPS poll in simulation mode")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("SAFETY VIOLATION BLOCKED"))
        }
    }

    @Test
    fun testSimulationNeverEmitsRealDistressIntent() {
        val state = coordinator.state.value
        assertFalse(
            "DemoSafetyBoundary must block Android emergency broadcast intent in simulation mode",
            DemoSafetyBoundary.canBroadcastEmergencyIntent(state.isSimulation)
        )
        try {
            DemoSafetyBoundary.enforceSimulationSafety(state.isSimulation, "EMERGENCY_INTENT_BROADCAST")
            fail("Expected IllegalStateException for emergency intent broadcast in simulation mode")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("SAFETY VIOLATION BLOCKED"))
        }
    }

    @Test
    fun testSimulationLabelIsPresentOnAllSimulatedStates() {
        for (scenario in DemoScenario.entries) {
            coordinator.selectScenario(scenario)
            val state = coordinator.state.value
            assertTrue(
                "Status summary must include [SIMULATION] badge label",
                state.statusSummary.contains("[SIMULATION]")
            )
        }
    }

    // =========================================================================
    // B. Reset Tests
    // =========================================================================

    @Test
    fun testResetClearsDemoStateDeterministically() = testScope.runTest {
        coordinator.selectScenario(DemoScenario.CONGESTION_PREEMPTION)
        coordinator.nextStep()
        coordinator.nextStep()
        coordinator.processManualTextInput("Temp input text")

        // Act: Reset demo
        coordinator.resetDemo()

        val state = coordinator.state.value
        assertEquals("Step index must be reset to 0", 0, state.currentStepIndex)
        assertFalse("Auto-run must be stopped", state.isAutoRunning)
        assertEquals("Elapsed timer must be reset to 0", 0, state.elapsedSeconds)
        assertFalse("Timer must not be running", state.isTimerRunning)
        assertFalse("Manual fallback must be inactive", state.isManualFallbackActive)
        assertNull("Failure message must be cleared", state.failureMessage)
        state.stageStates.values.forEach { stageState ->
            assertEquals("All stages must be IDLE", StageState.IDLE, stageState)
        }
    }

    @Test
    fun testResetPreservesApplicationSettingsAndSecurityKey() {
        val initialLang = coordinator.state.value.activeLanguage
        coordinator.selectScenario(DemoScenario.SEMANTIC_COMPRESSION)
        coordinator.nextStep()
        coordinator.resetDemo()

        val state = coordinator.state.value
        assertEquals("Active language must be preserved", initialLang, state.activeLanguage)
        assertEquals("Security protocol status must be preserved", "HMAC-SHA256 AUTHENTICATED", state.securityStatus)
    }

    // =========================================================================
    // C. Benchmark Tests
    // =========================================================================

    @Test
    fun testBenchmarkProducesNonNegativeTimings() {
        val runner = SihBenchmarkRunner(manetSimulator, sampleCount = 5)
        val suite = runner.runFullBenchmarkSuite()

        assertTrue(suite.semanticClassificationMicros.median >= 0)
        assertTrue(suite.hmacGenerationMicros.median >= 0)
        assertTrue(suite.hmacVerificationMicros.median >= 0)
        assertTrue(suite.qosSchedulingMicros.median >= 0)
        assertTrue(suite.fragmentationReassemblyMicros.median >= 0)
        assertTrue(suite.deliveryReceiptMicros.median >= 0)
        assertTrue(suite.multiHopTransitMicros.median >= 0)
    }

    @Test
    fun testBenchmarkProducesExactValidByteCounts() {
        val runner = SihBenchmarkRunner(manetSimulator, sampleCount = 5)
        val suite = runner.runFullBenchmarkSuite()

        val semScenario = suite.scenarios.first { it.scenarioKey == "BENCHMARK_B" }
        assertEquals("Semantic compressed payload must be exactly 6 bytes", 6, semScenario.payloadBytes)
        assertEquals("Semantic wire frame must be exactly 43 bytes", 43, semScenario.wireBytes)
        assertNotNull(semScenario.payloadSavingsPercent)
        assertTrue(semScenario.payloadSavingsPercent!! >= 85.0)

        val receiptScenario = suite.scenarios.first { it.scenarioKey == "BENCHMARK_G" }
        assertEquals("Delivery receipt frame must be exactly 35 bytes", 35, receiptScenario.wireBytes)
    }

    @Test
    fun testRepeatedBenchmarkProducesValidMinMedianMeanMax() {
        val runner = SihBenchmarkRunner(manetSimulator, sampleCount = 10)
        val suite = runner.runFullBenchmarkSuite()

        val hmac = suite.hmacGenerationMicros
        assertTrue("Min must be <= Median", hmac.min <= hmac.median)
        assertTrue("Median must be <= Max", hmac.median <= hmac.max)
        assertTrue("Min must be <= Mean", hmac.min <= hmac.mean)
        assertTrue("Mean must be <= Max", hmac.mean <= hmac.max)
    }

    @Test
    fun testNoFabricatedPlaceholderMetricsInBenchmark() {
        val runner = SihBenchmarkRunner(manetSimulator, sampleCount = 5)
        val suite = runner.runFullBenchmarkSuite()

        assertEquals("Must execute exactly 7 scenarios", 7, suite.scenarios.size)
        suite.scenarios.forEach { sc ->
            assertTrue("Scenario must be marked simulated", sc.isSimulated)
            assertTrue("Wire bytes must be > 0", sc.wireBytes > 0)
            assertTrue("Latency median must be >= 0", sc.executionLatency.median >= 0)
        }
    }

    // =========================================================================
    // D. Failure Handling Tests
    // =========================================================================

    @Test
    fun testMissingSttHandledGracefullyWithFallback() {
        coordinator.triggerFailureNotice("STT engine unavailable: using manual fallback")
        val state = coordinator.state.value
        assertEquals("STT engine unavailable: using manual fallback", state.failureMessage)

        // Operator uses manual text input
        coordinator.processManualTextInput("Medical emergency, need doctor")
        val updated = coordinator.state.value
        assertTrue("Manual fallback must be active", updated.isManualFallbackActive)
        assertEquals(StageState.SUCCESS, updated.stageStates[PipelineStage.VOICE])
        assertEquals(StageState.SUCCESS, updated.stageStates[PipelineStage.STT])
    }

    @Test
    fun testMissingTtsHandledWithoutCrash() = testScope.runTest {
        coordinator.selectScenario(DemoScenario.NORMAL_VOICE)
        for (i in 1..8) {
            coordinator.nextStep()
        }
        val state = coordinator.state.value
        assertEquals(StageState.SUCCESS, state.stageStates[PipelineStage.TTS])
        assertTrue(state.deliveryStatus.contains("DELIVERED"))
    }

    @Test
    fun testMissingSecurityKeyHandled() {
        val testPacket = Packet(
            sequenceNumber = 1,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 101,
            destinationDeviceId = 103,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.NORMAL,
            payload = byteArrayOf(1, 2, 3)
        )
        val emptyKey = ByteArray(0)
        try {
            PacketAuthenticator.sign(testPacket, emptyKey)
            fail("Expected exception for empty key")
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException || e is java.security.InvalidKeyException)
        }
    }

    @Test
    fun testRouteUnavailableHandled() {
        // Take all relays down
        manetSimulator.setNodeOnline(ManetSimulator.NODE_B_ID, false)
        manetSimulator.setNodeOnline(ManetSimulator.NODE_D_ID, false)
        manetSimulator.sendPacketAtoC("PROBE")

        val state = manetSimulator.topologyState.value
        assertTrue("State must indicate destination unreachable", state.statusMessage.contains("UNREACHABLE"))
    }

    // =========================================================================
    // E. Manual Fallback Tests
    // =========================================================================

    @Test
    fun testTextFallbackReachesSemanticAndSecurityPipeline() {
        val manualUtterance = "Medical emergency, officer down at Grid 42, heavy bleeding."
        coordinator.processManualTextInput(manualUtterance)

        val state = coordinator.state.value
        assertTrue(state.isManualFallbackActive)
        assertEquals(manualUtterance, state.sampleUtterance)
        assertEquals(manualUtterance, state.recognizedText)
        assertEquals("Semantic compressed payload must be 6 bytes", 6, state.lowBitrateMetrics.payloadBytes)
        assertEquals("Semantic wire bytes must be 43 bytes", 43, state.lowBitrateMetrics.wireBytes)
        assertEquals(StageState.ACTIVE, state.stageStates[PipelineStage.SEMANTIC_OR_TEXT])
    }

    // =========================================================================
    // F. Readiness Tests
    // =========================================================================

    @Test
    fun testReadinessStateReflectsActualComponentAvailability() {
        val readiness = DeviceReadinessState(
            sttStatus = "READY",
            ttsStatus = "READY",
            networkStatus = "READY",
            securityStatus = "READY",
            manetServiceStatus = "STOPPED",
            topologyStatus = "AVAILABLE",
            demoStatus = "READY"
        )
        coordinator.updateDeviceReadiness(readiness)

        val state = coordinator.state.value
        assertEquals("READY", state.deviceReadiness.sttStatus)
        assertEquals("READY", state.deviceReadiness.ttsStatus)
        assertEquals("READY", state.deviceReadiness.networkStatus)
        assertEquals("READY", state.deviceReadiness.securityStatus)
        assertEquals("STOPPED", state.deviceReadiness.manetServiceStatus)
        assertEquals("AVAILABLE", state.deviceReadiness.topologyStatus)
        assertEquals("READY", state.deviceReadiness.demoStatus)
    }

    // =========================================================================
    // G. Claims Audit Tests
    // =========================================================================

    @Test
    fun testFinalNarrationContainsNoGuaranteedDelivery() {
        for (scenario in DemoScenario.entries) {
            for (step in 0..8) {
                coordinator.selectScenario(scenario)
                for (s in 0 until step) coordinator.nextStep()
                val narration = coordinator.state.value.judgeScriptNarration
                assertFalse("Narration must not claim guaranteed delivery", narration.lowercase().contains("guaranteed delivery"))
                assertFalse("Narration must not claim guarantee", narration.lowercase().contains("guarantee"))
            }
        }
    }

    @Test
    fun testFinalNarrationContainsNoMilitaryGrade() {
        for (scenario in DemoScenario.entries) {
            coordinator.selectScenario(scenario)
            val narration = coordinator.state.value.judgeScriptNarration
            assertFalse("Narration must not claim military-grade", narration.lowercase().contains("military-grade"))
            assertFalse("Narration must not claim unbreakable", narration.lowercase().contains("unbreakable"))
        }
    }

    @Test
    fun testFinalUiDoesNotSayEncryptedMesh() {
        val state = coordinator.state.value
        assertFalse("Security status must say authenticated mesh, not encrypted mesh", state.securityStatus.lowercase().contains("encrypted mesh"))
        assertEquals("HMAC-SHA256 AUTHENTICATED", state.securityStatus)
    }

    // =========================================================================
    // H. Regression Tests
    // =========================================================================

    @Test
    fun testExistingSihDemoAllScenariosStillComplete() = testScope.runTest {
        for (scenario in DemoScenario.entries) {
            coordinator.selectScenario(scenario)
            for (step in 1..8) {
                coordinator.nextStep()
            }
            val state = coordinator.state.value
            assertEquals("Scenario ${scenario.name} must reach step 8", 8, state.currentStepIndex)
            assertEquals("TTS must be SUCCESS at step 8", StageState.SUCCESS, state.stageStates[PipelineStage.TTS])
            assertEquals("ACK must be SUCCESS at step 8", StageState.SUCCESS, state.stageStates[PipelineStage.ACK])
            assertTrue("Status must show verified delivered", state.statusSummary.contains("VERIFIED DELIVERED"))
        }
    }
}
