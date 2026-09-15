package org.sih.itantra.core.benchmark.metrics

/**
 * Comprehensive benchmark telemetry metrics collected for a specific experimental scenario.
 */
data class ScenarioMetrics(
    val scenarioId: String,
    val representation: String,
    val conditionName: String,
    val bandwidthBps: Long,
    val latencyMs: Long,
    val jitterMs: Long,
    val configuredLossRate: Double,
    val burstLossLength: Int,
    val totalMessages: Int,
    val successfulMessages: Int,
    val deliverySuccessRate: Double,
    val observedPacketLossRate: Double,
    val endToEndLatencyMetrics: DistributionMetrics,
    val ackLatencyMetrics: DistributionMetrics,
    val retransmissionCount: Int,
    val fragmentCount: Int,
    val reassemblySuccessCount: Int,
    val dtnStorageCount: Int,
    val dtnExpiryCount: Int,
    val duplicateSuppressionCount: Int,
    val bytesTransmitted: Long,
    val bytesDelivered: Long,
    val goodputBps: Double,
    val overheadRatio: Double,
    val representationExpansionRatio: Double,
    val contextReconstructionSuccessRate: Double,
    val semanticCorrectnessRate: Double,
    val tacticalSuccessRate: Double,
    val semanticFieldAccuracy: Double,
    val criticalFieldRecall: Double,
    val seed: Long,
    val device: String = "SIMULATION"
) {
    /**
     * Serializes this scenario result into standard CSV columns.
     */
    fun toCsvRow(): String {
        return listOf(
            scenarioId,
            representation,
            conditionName,
            bandwidthBps.toString(),
            latencyMs.toString(),
            jitterMs.toString(),
            "%.4f".format(configuredLossRate),
            burstLossLength.toString(),
            totalMessages.toString(),
            successfulMessages.toString(),
            "%.4f".format(deliverySuccessRate),
            "%.4f".format(observedPacketLossRate),
            "%.2f".format(endToEndLatencyMetrics.median),
            "%.2f".format(endToEndLatencyMetrics.p95),
            "%.2f".format(endToEndLatencyMetrics.mean),
            "%.2f".format(ackLatencyMetrics.median),
            "%.2f".format(ackLatencyMetrics.p95),
            retransmissionCount.toString(),
            fragmentCount.toString(),
            reassemblySuccessCount.toString(),
            dtnStorageCount.toString(),
            dtnExpiryCount.toString(),
            duplicateSuppressionCount.toString(),
            bytesTransmitted.toString(),
            bytesDelivered.toString(),
            "%.2f".format(goodputBps),
            "%.4f".format(overheadRatio),
            "%.4f".format(representationExpansionRatio),
            "%.4f".format(contextReconstructionSuccessRate),
            "%.4f".format(semanticCorrectnessRate),
            "%.4f".format(tacticalSuccessRate),
            "%.4f".format(semanticFieldAccuracy),
            "%.4f".format(criticalFieldRecall),
            seed.toString(),
            device
        ).joinToString(",")
    }

    companion object {
        const val CSV_HEADER = "scenarioId,representation,conditionName,bandwidthBps,latencyMs,jitterMs," +
                "configuredLossRate,burstLossLength,totalMessages,successfulMessages,deliverySuccessRate," +
                "observedPacketLossRate,latencyMedianMs,latencyP95Ms,latencyMeanMs,ackMedianMs,ackP95Ms," +
                "retransmissionCount,fragmentCount,reassemblySuccessCount,dtnStorageCount,dtnExpiryCount," +
                "duplicateSuppressionCount,bytesTransmitted,bytesDelivered,goodputBps,overheadRatio," +
                "representationExpansionRatio,contextReconstructionSuccessRate,semanticCorrectnessRate," +
                "tacticalSuccessRate,semanticFieldAccuracy,criticalFieldRecall,seed,device"
    }
}
