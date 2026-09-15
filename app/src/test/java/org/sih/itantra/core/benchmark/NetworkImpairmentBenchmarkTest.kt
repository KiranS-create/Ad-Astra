package org.sih.itantra.core.benchmark

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.benchmark.channel.BandwidthLimiter
import org.sih.itantra.core.benchmark.channel.BernoulliLossModel
import org.sih.itantra.core.benchmark.channel.BurstLossModel
import org.sih.itantra.core.benchmark.channel.ChannelSimulator
import org.sih.itantra.core.benchmark.channel.LatencyJitterInjector
import org.sih.itantra.core.benchmark.channel.NetworkCondition
import org.sih.itantra.core.benchmark.channel.PacketFaultInjector
import org.sih.itantra.core.benchmark.corpus.TacticalCorpus
import org.sih.itantra.core.benchmark.corpus.TacticalEvaluator
import org.sih.itantra.core.benchmark.metrics.DistributionMetrics
import org.sih.itantra.core.benchmark.metrics.ScenarioMetrics
import org.sih.itantra.core.benchmark.runner.NetworkImpairmentRunner
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.network.AdaptiveNetworkMode
import org.sih.itantra.core.protocol.CorruptPacketException
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.qos.TacticalPacketScheduler
import org.sih.itantra.core.vbr.AdaptiveRepresentationMode
import org.sih.itantra.core.vbr.AdaptiveRepresentationPolicy
import org.sih.itantra.core.vbr.ContextDelta
import org.sih.itantra.core.vbr.SemanticBase
import java.util.Random

class NetworkImpairmentBenchmarkTest {

    // 1. Deterministic random seed
    @Test
    fun test_01_deterministicRandomSeed() {
        val seed = 20260915L
        val sim1 = ChannelSimulator(NetworkCondition.LOSS_20, seed = seed)
        val res1 = sim1.runScenario("TEST1", AdaptiveRepresentationMode.SEMANTIC_BASE)

        val sim2 = ChannelSimulator(NetworkCondition.LOSS_20, seed = seed)
        val res2 = sim2.runScenario("TEST1", AdaptiveRepresentationMode.SEMANTIC_BASE)

        assertEquals("Same seed must produce identical delivery success rate", res1.deliverySuccessRate, res2.deliverySuccessRate, 0.0001)
        assertEquals("Same seed must produce identical observed loss rate", res1.observedPacketLossRate, res2.observedPacketLossRate, 0.0001)
        assertEquals("Same seed must produce identical retransmissions", res1.retransmissionCount, res2.retransmissionCount)
        assertEquals("Same seed must produce identical bytes transmitted", res1.bytesTransmitted, res2.bytesTransmitted)
    }

    // 2. Zero-loss behavior
    @Test
    fun test_02_zeroLossBehavior() {
        val sim = ChannelSimulator(NetworkCondition.LOSS_0, seed = 12345L)
        val result = sim.runScenario("ZERO_LOSS", AdaptiveRepresentationMode.SEMANTIC_BASE)

        assertEquals(1.0, result.deliverySuccessRate, 0.001)
        assertEquals(0.0, result.observedPacketLossRate, 0.001)
        assertEquals(0, result.retransmissionCount)
        assertEquals(6, result.successfulMessages)
        assertEquals(1.0, result.tacticalSuccessRate, 0.001)
    }

    // 3. Packet-loss injection
    @Test
    fun test_03_packetLossInjection() {
        val model = BernoulliLossModel(0.30, Random(42L))
        var dropped = 0
        val total = 5000
        for (i in 0 until total) {
            if (model.shouldDrop(i, 100)) dropped++
        }
        val observedRate = dropped.toDouble() / total.toDouble()
        // 30% loss within reasonable statistical tolerance for 5000 trials
        assertEquals(0.30, observedRate, 0.04)
    }

    // 4. Burst-loss injection
    @Test
    fun test_04_burstLossInjection() {
        val burstModel = BurstLossModel(burstLength = 3, triggerInterval = 5, random = null)
        val drops = (0 until 15).map { burstModel.shouldDrop(it, 100) }

        // First 5 packets succeed, then 3 consecutive packets are dropped
        assertFalse(drops[0])
        assertFalse(drops[1])
        assertFalse(drops[2])
        assertFalse(drops[3])
        assertFalse(drops[4])
        assertTrue("Burst packet 1 must drop", drops[5])
        assertTrue("Burst packet 2 must drop", drops[6])
        assertTrue("Burst packet 3 must drop", drops[7])
        assertFalse("Packet after burst must succeed", drops[8])
    }

    // 5. Bandwidth calculation
    @Test
    fun test_05_bandwidthCalculation() {
        val limiter50k = BandwidthLimiter(50_000L) // 50 kbps
        // 250 bytes * 8 bits = 2000 bits. 2000 / 50,000 = 0.04s = 40 ms
        val delay = limiter50k.calculateTransmissionDelayMs(250)
        assertEquals(40L, delay)

        val limiterUnlimited = BandwidthLimiter(NetworkCondition.UNLIMITED_BANDWIDTH)
        assertEquals(0L, limiterUnlimited.calculateTransmissionDelayMs(1024))
    }

    // 6. Latency injection
    @Test
    fun test_06_latencyInjection() {
        val injector = LatencyJitterInjector(oneWayLatencyMs = 100L, maxJitterMs = 0L)
        assertEquals(100L, injector.calculateDelayMs())
    }

    // 7. Jitter injection
    @Test
    fun test_07_jitterInjection() {
        val injector = LatencyJitterInjector(oneWayLatencyMs = 100L, maxJitterMs = 25L, random = Random(99L))
        val delays = (0 until 50).map { injector.calculateDelayMs() }
        for (d in delays) {
            assertTrue("Delay must be >= 75ms (100 - 25)", d >= 75L)
            assertTrue("Delay must be <= 125ms (100 + 25)", d <= 125L)
        }
        val min = delays.minOrNull()!!
        val max = delays.maxOrNull()!!
        assertTrue("Jitter must introduce variation", max > min)
    }

    // 8. Fragmentation accounting
    @Test
    fun test_08_fragmentationAccounting() {
        val sim = ChannelSimulator(NetworkCondition.BW_UNLIMITED, seed = 12345L)
        // FULL representation on long messages triggers fragmentation (> 128B)
        val longMsg = TacticalCorpus.MSG_MED_INITIAL.copy(
            text = "Long tactical medical evacuation request with extensive situational notes and detailed instructions for triage teams operating in severe disaster zone. Additional situational notes: Sector 4 building collapse has trapped 12 individuals across multiple basement levels. High concentration of dust, limited visibility, and compromised structural integrity. Triage unit requires immediate stabilization supplies, surgical packs, blood expanders, and powered hydraulic cutters."
        )
        val result = sim.runScenario(
            scenarioId = "FRAG_TEST",
            representationMode = AdaptiveRepresentationMode.FULL,
            messages = listOf(longMsg)
        )
        assertTrue("Fragments must be generated for long message", result.fragmentCount >= 2)
        assertEquals(1, result.reassemblySuccessCount)
    }

    // 9. Retransmission accounting
    @Test
    fun test_09_retransmissionAccounting() {
        val sim = ChannelSimulator(NetworkCondition.LOSS_30, seed = 54321L, maxRetries = 3)
        val result = sim.runScenario("RETX_TEST", AdaptiveRepresentationMode.SEMANTIC_BASE, requireAck = true)
        assertTrue("Retransmissions must occur under 30% loss with ACKs", result.retransmissionCount > 0)
    }

    // 10. ACK accounting
    @Test
    fun test_10_ackAccounting() {
        val sim = ChannelSimulator(NetworkCondition.LATENCY_50_JITTER_10, seed = 123L)
        val result = sim.runScenario("ACK_TEST", AdaptiveRepresentationMode.SEMANTIC_BASE, requireAck = true)
        assertTrue("ACK median latency must be > 0ms", result.ackLatencyMetrics.median > 0.0)
        assertTrue("ACK latency should include round trip", result.ackLatencyMetrics.median >= 50.0)
    }

    // 11. DTN outage simulation
    @Test
    fun test_11_dtnOutageSimulation() {
        // Outage of 30 seconds (well within 600s TTL)
        val simRecoverable = ChannelSimulator(NetworkCondition.OUTAGE_30S, seed = 100L)
        val resRecoverable = simRecoverable.runScenario("DTN_REC", AdaptiveRepresentationMode.SEMANTIC_BASE)
        assertEquals(6, resRecoverable.dtnStorageCount)
        assertEquals(0, resRecoverable.dtnExpiryCount)
        assertEquals(1.0, resRecoverable.deliverySuccessRate, 0.001)

        // Outage of 650 seconds (exceeds 600s TTL: messages expire)
        val expiredCondition = NetworkCondition("OUTAGE_650S", outageDurationMs = 650_000L)
        val simExpired = ChannelSimulator(expiredCondition, seed = 100L)
        val resExpired = simExpired.runScenario("DTN_EXP", AdaptiveRepresentationMode.SEMANTIC_BASE)
        assertEquals(6, resExpired.dtnStorageCount)
        assertEquals(6, resExpired.dtnExpiryCount)
        assertEquals(0.0, resExpired.deliverySuccessRate, 0.001)
    }

    // 12. Representation comparison
    @Test
    fun test_12_representationComparison() {
        val sim = ChannelSimulator(NetworkCondition.BW_50K, seed = 42L)
        val resFull = sim.runScenario("COMP_FULL", AdaptiveRepresentationMode.FULL)
        val resBase = sim.runScenario("COMP_BASE", AdaptiveRepresentationMode.SEMANTIC_BASE)
        val resDelta = sim.runScenario("COMP_DELTA", AdaptiveRepresentationMode.CONTEXT_DELTA)

        assertTrue("SEMANTIC_BASE must transmit fewer bytes than FULL", resBase.bytesTransmitted < resFull.bytesTransmitted)
        assertTrue("CONTEXT_DELTA must transmit fewer bytes than FULL", resDelta.bytesTransmitted < resFull.bytesTransmitted)
    }

    // 13. Tactical success evaluation
    @Test
    fun test_13_tacticalSuccessEvaluation() {
        val gt = TacticalCorpus.MSG_MED_INITIAL
        val cmd = gt.toSemanticCommand()
        val base = SemanticBase.fromCommand(cmd)
        val payload = base.serialize()

        val eval = TacticalEvaluator.evaluate(gt, AdaptiveRepresentationMode.SEMANTIC_BASE, payload)
        assertTrue("Tactical success must be true for matching semantic base", eval.tacticalSuccess)
        assertEquals(1.0, eval.semanticFieldAccuracy, 0.001)
        assertEquals(1.0, eval.criticalFieldRecall, 0.001)
    }

    // 14. Semantic field evaluation
    @Test
    fun test_14_semanticFieldEvaluation() {
        val gt = TacticalCorpus.MSG_MED_INITIAL
        val wrongCmd = gt.toSemanticCommand().copy(count = 99) // mismatched count
        val base = SemanticBase.fromCommand(wrongCmd)
        val payload = base.serialize()

        val eval = TacticalEvaluator.evaluate(gt, AdaptiveRepresentationMode.SEMANTIC_BASE, payload)
        assertFalse("Count mismatch should fail tactical success", eval.tacticalSuccess)
        assertFalse(eval.countMatched)
        assertTrue("Category should still match", eval.categoryMatched)
    }

    // 15. Context delta reconstruction
    @Test
    fun test_15_contextDeltaReconstruction() {
        val baseGt = TacticalCorpus.MSG_MED_INITIAL
        val baseCmd = baseGt.toSemanticCommand()
        val context = org.sih.itantra.core.context.SharedContextEntry(
            contextId = 42,
            version = 1,
            category = baseCmd.category,
            subtype = baseCmd.subtype,
            severity = baseCmd.severity,
            count = baseCmd.count,
            sector = baseCmd.parameter,
            confidence = 90,
            sourceDeviceId = 1001,
            createdAt = 1000L,
            lastUpdatedAt = 1000L,
            expiresAt = 1000L + 600_000L
        )

        val updateGt = TacticalCorpus.MSG_MED_UPDATE_1 // count = 4
        val updateCmd = updateGt.toSemanticCommand()
        val delta = ContextDelta.computeDelta(baseContext = context, newCommand = updateCmd, newVersion = 2)
        val deltaPayload = delta.serialize()

        val eval = TacticalEvaluator.evaluate(
            groundTruth = updateGt,
            representation = AdaptiveRepresentationMode.CONTEXT_DELTA,
            receivedPayload = deltaPayload,
            baseContext = context
        )

        assertTrue("Context delta must successfully reconstruct", eval.tacticalSuccess)
        assertEquals(4, updateGt.count)
        assertTrue(eval.countMatched)
    }

    // 16. Context delta under loss
    @Test
    fun test_16_contextDeltaUnderLoss() {
        val sim = ChannelSimulator(NetworkCondition.LOSS_10, seed = 888L)
        val result = sim.runScenario("DELTA_LOSS", AdaptiveRepresentationMode.CONTEXT_DELTA)
        assertTrue("Delta should succeed under mild loss with ACKs", result.deliverySuccessRate >= 0.8)
    }

    // 17. HMAC rejection
    @Test
    fun test_17_hmacRejection() {
        val pkt = Packet(
            sequenceNumber = 1,
            timestamp = 1000L,
            sourceDeviceId = 1001,
            language = IndicLanguage.ENGLISH,
            payload = "Sensitive tactical dispatch".toByteArray(Charsets.UTF_8)
        )
        val testKey = ByteArray(32) { 0x42.toByte() }
        val authenticatedPkt = PacketAuthenticator.sign(pkt, testKey)
        val verifyGood = PacketAuthenticator.verify(authenticatedPkt, testKey)
        assertTrue("Unmodified packet HMAC must be valid", verifyGood.isValid)

        val corruptedPkt = PacketFaultInjector.corruptHmac(authenticatedPkt)
        val verifyBad = PacketAuthenticator.verify(corruptedPkt, testKey)
        assertFalse("Corrupted HMAC must be rejected", verifyBad.isValid)
    }

    // 18. CRC rejection
    @Test(expected = CorruptPacketException::class)
    fun test_18_crcRejection() {
        val pkt = Packet(
            sequenceNumber = 2,
            timestamp = 1000L,
            sourceDeviceId = 1001,
            language = IndicLanguage.ENGLISH,
            payload = "CRC validation payload".toByteArray(Charsets.UTF_8)
        )
        val serialized = PacketSerializer.serialize(pkt)
        val corruptedBytes = PacketFaultInjector.corruptCrc(serialized)

        // Must throw CorruptPacketException
        PacketSerializer.deserialize(corruptedBytes)
    }

    // 19. QoS priority behavior
    @Test
    fun test_19_qosPriorityBehavior() {
        val scheduler = TacticalPacketScheduler(autoTransmit = false)
        val normalPkt = Packet(
            sequenceNumber = 10,
            timestamp = 1000L,
            sourceDeviceId = 1001,
            priority = MessagePriority.NORMAL,
            language = IndicLanguage.ENGLISH,
            payload = byteArrayOf(1)
        )
        val distressPkt = Packet(
            sequenceNumber = 11,
            timestamp = 1001L,
            sourceDeviceId = 1001,
            priority = MessagePriority.DISTRESS,
            language = IndicLanguage.ENGLISH,
            payload = byteArrayOf(2)
        )

        scheduler.enqueue(normalPkt)
        scheduler.enqueue(distressPkt)

        val firstOut = scheduler.pollNextPacket()
        assertNotNull(firstOut)
        assertEquals("DISTRESS packet must preempt NORMAL packet in transmission queue", MessagePriority.DISTRESS, firstOut!!.priority)
    }

    // 20. Result aggregation
    @Test
    fun test_20_resultAggregation() {
        val runner = NetworkImpairmentRunner(masterSeed = 20260915L)
        val scenarios = runner.buildAllScenarios()
        assertTrue("Scenarios must cover bandwidth, loss, latency, burst, and DTN", scenarios.size >= 100)

        val targetDir = if (java.io.File("gradlew.bat").exists()) java.io.File(".") else java.io.File("..")
        val results = runner.runAndSave(targetDir)
        assertEquals(scenarios.size, results.size)
        assertTrue(java.io.File(targetDir, "feature22_results.csv").exists())
        assertTrue(java.io.File(targetDir, "feature22_results.json").exists())
    }

    // 21. Percentile calculation
    @Test
    fun test_21_percentileCalculation() {
        val latencies = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0)
        val metrics = DistributionMetrics.fromDoubles(latencies)

        assertEquals(55.0, metrics.mean, 0.001)
        assertEquals(55.0, metrics.median, 0.001)
        assertEquals(100.0, metrics.p95, 0.001)
        assertEquals(10.0, metrics.min, 0.001)
        assertEquals(100.0, metrics.max, 0.001)
        assertEquals(10, metrics.count)
    }

    // 22. CSV serialization
    @Test
    fun test_22_csvSerialization() {
        val sim = ChannelSimulator(NetworkCondition.BW_50K, seed = 777L)
        val metrics = sim.runScenario("CSV_TEST", AdaptiveRepresentationMode.SEMANTIC_BASE)
        val row = metrics.toCsvRow()

        assertTrue(row.startsWith("CSV_TEST,SEMANTIC_BASE,BW_50K,50000"))
        assertEquals(ScenarioMetrics.CSV_HEADER.split(",").size, row.split(",").size)
    }

    // 23. JSON serialization
    @Test
    fun test_23_jsonSerialization() {
        val sim = ChannelSimulator(NetworkCondition.BW_50K, seed = 777L)
        val metrics = sim.runScenario("JSON_TEST", AdaptiveRepresentationMode.SEMANTIC_BASE)
        val runner = NetworkImpairmentRunner()
        val json = runner.exportToJson(listOf(metrics))

        assertTrue(json.contains("\"scenarioId\": \"JSON_TEST\""))
        assertTrue(json.contains("\"representation\": \"SEMANTIC_BASE\""))
        assertTrue(json.contains("\"deliverySuccessRate\":"))
    }

    // 24. Deterministic replay of identical scenario
    @Test
    fun test_24_deterministicReplay() {
        val seed = 999999L
        val runner1 = NetworkImpairmentRunner(masterSeed = seed)
        val res1 = runner1.runAll().take(5)

        val runner2 = NetworkImpairmentRunner(masterSeed = seed)
        val res2 = runner2.runAll().take(5)

        for (i in 0 until 5) {
            assertEquals("Scenario ${i} delivery success must match on replay", res1[i].deliverySuccessRate, res2[i].deliverySuccessRate, 0.0001)
            assertEquals("Scenario ${i} bytes transmitted must match on replay", res1[i].bytesTransmitted, res2[i].bytesTransmitted)
            assertEquals("Scenario ${i} median latency must match on replay", res1[i].endToEndLatencyMetrics.median, res2[i].endToEndLatencyMetrics.median, 0.0001)
        }
    }
}
