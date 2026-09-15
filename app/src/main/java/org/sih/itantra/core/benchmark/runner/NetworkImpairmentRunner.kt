package org.sih.itantra.core.benchmark.runner

import org.sih.itantra.core.benchmark.channel.ChannelSimulator
import org.sih.itantra.core.benchmark.channel.NetworkCondition
import org.sih.itantra.core.benchmark.corpus.TacticalCorpus
import org.sih.itantra.core.benchmark.metrics.ScenarioMetrics
import org.sih.itantra.core.vbr.AdaptiveRepresentationMode
import java.io.File

/**
 * Deterministic benchmark execution harness orchestrating all Feature 22 impairment experiments.
 */
class NetworkImpairmentRunner(
    val masterSeed: Long = 20260915L
) {

    /**
     * Builds the complete matrix of scenarios to evaluate across all experimental dimensions.
     */
    fun buildAllScenarios(): List<BenchmarkScenario> {
        val scenarios = mutableListOf<BenchmarkScenario>()
        val reps = listOf(
            AdaptiveRepresentationMode.FULL,
            AdaptiveRepresentationMode.COMPACT,
            AdaptiveRepresentationMode.SEMANTIC_BASE,
            AdaptiveRepresentationMode.SEMANTIC_ENHANCED,
            AdaptiveRepresentationMode.CONTEXT_DELTA
        )

        // 1. Bandwidth Experiments (Section 9)
        val bandwidthConditions = listOf(
            NetworkCondition.BW_100K,
            NetworkCondition.BW_50K,
            NetworkCondition.BW_20K,
            NetworkCondition.BW_10K,
            NetworkCondition.BW_5K
        )
        for (bw in bandwidthConditions) {
            for (rep in reps) {
                scenarios.add(
                    BenchmarkScenario(
                        id = "EXP-BW-${bw.name}-${rep.name}",
                        category = "BANDWIDTH",
                        description = "Bandwidth ${bw.bandwidthBps / 1000} kbps with ${rep.name}",
                        condition = bw,
                        representation = rep
                    )
                )
            }
        }

        // 2. Packet Loss Experiments (Section 10)
        val lossConditions = listOf(
            NetworkCondition.LOSS_0,
            NetworkCondition.LOSS_5,
            NetworkCondition.LOSS_10,
            NetworkCondition.LOSS_20,
            NetworkCondition.LOSS_30,
            NetworkCondition.LOSS_50
        )
        for (loss in lossConditions) {
            for (rep in reps) {
                scenarios.add(
                    BenchmarkScenario(
                        id = "EXP-LOSS-${loss.name}-${rep.name}",
                        category = "PACKET_LOSS",
                        description = "Packet Loss ${(loss.packetLossRate * 100).toInt()}% with ${rep.name}",
                        condition = loss,
                        representation = rep
                    )
                )
            }
        }

        // 3. Latency & Jitter Experiments (Section 11)
        val latJitterConditions = listOf(
            NetworkCondition.LATENCY_0_JITTER_0,
            NetworkCondition.LATENCY_50_JITTER_10,
            NetworkCondition.LATENCY_100_JITTER_25,
            NetworkCondition.LATENCY_250_JITTER_50,
            NetworkCondition.LATENCY_500_JITTER_50
        )
        for (lj in latJitterConditions) {
            for (rep in reps) {
                scenarios.add(
                    BenchmarkScenario(
                        id = "EXP-LAT-${lj.name}-${rep.name}",
                        category = "LATENCY_JITTER",
                        description = "Latency ${lj.oneWayLatencyMs}ms / Jitter ${lj.jitterMs}ms with ${rep.name}",
                        condition = lj,
                        representation = rep
                    )
                )
            }
        }

        // 4. Burst Loss Experiments (Section 12)
        val burstConditions = listOf(
            NetworkCondition.BURST_3,
            NetworkCondition.BURST_5,
            NetworkCondition.BURST_10
        )
        for (burst in burstConditions) {
            for (rep in reps) {
                scenarios.add(
                    BenchmarkScenario(
                        id = "EXP-BURST-${burst.name}-${rep.name}",
                        category = "BURST_LOSS",
                        description = "Burst loss of ${burst.burstLossCount} consecutive packets with ${rep.name}",
                        condition = burst,
                        representation = rep
                    )
                )
            }
        }

        // 5. DTN Outage Window Experiments (Section 14)
        val dtnConditions = listOf(
            NetworkCondition.OUTAGE_10S,
            NetworkCondition.OUTAGE_30S,
            NetworkCondition.OUTAGE_60S,
            NetworkCondition.OUTAGE_300S
        )
        for (dtn in dtnConditions) {
            for (rep in reps) {
                scenarios.add(
                    BenchmarkScenario(
                        id = "EXP-DTN-${dtn.name}-${rep.name}",
                        category = "DTN_OUTAGE",
                        description = "Outage window ${dtn.outageDurationMs / 1000}s with ${rep.name}",
                        condition = dtn,
                        representation = rep
                    )
                )
            }
        }

        return scenarios
    }

    /**
     * Runs all scenarios deterministically and returns the aggregated metrics.
     */
    fun runAll(): List<ScenarioMetrics> {
        val scenarios = buildAllScenarios()
        val results = mutableListOf<ScenarioMetrics>()

        scenarios.forEachIndexed { index, scenario ->
            val scenarioSeed = masterSeed + index
            val simulator = ChannelSimulator(
                condition = scenario.condition,
                seed = scenarioSeed
            )
            val metric = simulator.runScenario(
                scenarioId = scenario.id,
                representationMode = scenario.representation,
                messages = TacticalCorpus.ALL_MESSAGES
            )
            results.add(metric)
        }

        return results
    }

    /**
     * Formats the list of metrics into standard CSV output.
     */
    fun exportToCsv(results: List<ScenarioMetrics>): String {
        val sb = StringBuilder()
        sb.appendLine(ScenarioMetrics.CSV_HEADER)
        for (r in results) {
            sb.appendLine(r.toCsvRow())
        }
        return sb.toString()
    }

    /**
     * Formats the list of metrics into clean, structured JSON.
     */
    fun exportToJson(results: List<ScenarioMetrics>): String {
        val sb = StringBuilder()
        sb.appendLine("[")
        results.forEachIndexed { index, r ->
            sb.appendLine("  {")
            sb.appendLine("    \"scenarioId\": \"${r.scenarioId}\",")
            sb.appendLine("    \"representation\": \"${r.representation}\",")
            sb.appendLine("    \"conditionName\": \"${r.conditionName}\",")
            sb.appendLine("    \"bandwidthBps\": ${r.bandwidthBps},")
            sb.appendLine("    \"latencyMs\": ${r.latencyMs},")
            sb.appendLine("    \"jitterMs\": ${r.jitterMs},")
            sb.appendLine("    \"configuredLossRate\": ${r.configuredLossRate},")
            sb.appendLine("    \"burstLossLength\": ${r.burstLossLength},")
            sb.appendLine("    \"totalMessages\": ${r.totalMessages},")
            sb.appendLine("    \"successfulMessages\": ${r.successfulMessages},")
            sb.appendLine("    \"deliverySuccessRate\": ${r.deliverySuccessRate},")
            sb.appendLine("    \"observedPacketLossRate\": ${r.observedPacketLossRate},")
            sb.appendLine("    \"latencyMedianMs\": ${r.endToEndLatencyMetrics.median},")
            sb.appendLine("    \"latencyP95Ms\": ${r.endToEndLatencyMetrics.p95},")
            sb.appendLine("    \"latencyMeanMs\": ${r.endToEndLatencyMetrics.mean},")
            sb.appendLine("    \"ackMedianMs\": ${r.ackLatencyMetrics.median},")
            sb.appendLine("    \"ackP95Ms\": ${r.ackLatencyMetrics.p95},")
            sb.appendLine("    \"retransmissionCount\": ${r.retransmissionCount},")
            sb.appendLine("    \"fragmentCount\": ${r.fragmentCount},")
            sb.appendLine("    \"reassemblySuccessCount\": ${r.reassemblySuccessCount},")
            sb.appendLine("    \"dtnStorageCount\": ${r.dtnStorageCount},")
            sb.appendLine("    \"dtnExpiryCount\": ${r.dtnExpiryCount},")
            sb.appendLine("    \"duplicateSuppressionCount\": ${r.duplicateSuppressionCount},")
            sb.appendLine("    \"bytesTransmitted\": ${r.bytesTransmitted},")
            sb.appendLine("    \"bytesDelivered\": ${r.bytesDelivered},")
            sb.appendLine("    \"goodputBps\": ${r.goodputBps},")
            sb.appendLine("    \"overheadRatio\": ${r.overheadRatio},")
            sb.appendLine("    \"representationExpansionRatio\": ${r.representationExpansionRatio},")
            sb.appendLine("    \"contextReconstructionSuccessRate\": ${r.contextReconstructionSuccessRate},")
            sb.appendLine("    \"semanticCorrectnessRate\": ${r.semanticCorrectnessRate},")
            sb.appendLine("    \"tacticalSuccessRate\": ${r.tacticalSuccessRate},")
            sb.appendLine("    \"semanticFieldAccuracy\": ${r.semanticFieldAccuracy},")
            sb.appendLine("    \"criticalFieldRecall\": ${r.criticalFieldRecall},")
            sb.appendLine("    \"seed\": ${r.seed},")
            sb.appendLine("    \"device\": \"${r.device}\"")
            sb.append("  }")
            if (index < results.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("]")
        return sb.toString()
    }

    /**
     * Executes the benchmark and writes output to the specified target directory.
     */
    fun runAndSave(outputDir: File): List<ScenarioMetrics> {
        if (!outputDir.exists()) outputDir.mkdirs()
        val results = runAll()

        val csvFile = File(outputDir, "feature22_results.csv")
        csvFile.writeText(exportToCsv(results), Charsets.UTF_8)

        val jsonFile = File(outputDir, "feature22_results.json")
        jsonFile.writeText(exportToJson(results), Charsets.UTF_8)

        return results
    }
}
