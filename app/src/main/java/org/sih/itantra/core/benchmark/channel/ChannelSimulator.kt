package org.sih.itantra.core.benchmark.channel

import org.sih.itantra.core.benchmark.corpus.TacticalCorpus
import org.sih.itantra.core.benchmark.corpus.TacticalEvaluator
import org.sih.itantra.core.benchmark.corpus.TacticalGroundTruth
import org.sih.itantra.core.benchmark.metrics.DistributionMetrics
import org.sih.itantra.core.benchmark.metrics.ScenarioMetrics
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.context.SharedContextEntry
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.mesh.DtnStore
import org.sih.itantra.core.network.AdaptiveNetworkMode
import org.sih.itantra.core.protocol.DeliveryReceipt
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.ReassemblyBuffer
import org.sih.itantra.core.vbr.AdaptiveMessageRepresentation
import org.sih.itantra.core.vbr.AdaptiveRepresentationMode
import org.sih.itantra.core.vbr.AdaptiveRepresentationPolicy
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic end-to-end communication channel simulator for controlled benchmarks.
 */
class ChannelSimulator(
    val condition: NetworkCondition,
    val seed: Long = 20260915L,
    val maxRetries: Int = 3
) {
    private val random = Random(seed)
    private val bandwidthLimiter = BandwidthLimiter(condition.bandwidthBps)
    private val latencyJitter = LatencyJitterInjector(condition.oneWayLatencyMs, condition.jitterMs, random)
    private val lossModel: LossModel = if (condition.burstLossCount > 0) {
        BurstLossModel(burstLength = condition.burstLossCount, triggerInterval = 5, random = random)
    } else {
        BernoulliLossModel(condition.packetLossRate, random)
    }

    private val fragmenter = PacketFragmenter(Packet.MAX_FRAGMENT_PAYLOAD)
    private val seqGenerator = AtomicInteger(100)

    /**
     * Executes a benchmark run across a set of tactical messages using the specified representation.
     */
    fun runScenario(
        scenarioId: String,
        representationMode: AdaptiveRepresentationMode,
        messages: List<TacticalGroundTruth> = TacticalCorpus.ALL_MESSAGES,
        networkMode: AdaptiveNetworkMode = AdaptiveNetworkMode.HEALTHY,
        requireAck: Boolean = true
    ): ScenarioMetrics {
        var totalPacketsTransmitted = 0
        var totalPacketsLost = 0
        var totalRetransmissions = 0
        var totalFragmentsGenerated = 0
        var totalReassemblySuccess = 0
        var totalDtnStored = 0
        var totalDtnExpired = 0
        var totalDuplicateSuppressed = 0
        var totalBytesTransmitted = 0L
        var totalBytesDelivered = 0L

        val endToEndLatencies = mutableListOf<Long>()
        val ackLatencies = mutableListOf<Long>()
        val tacticalSuccesses = mutableListOf<Boolean>()
        val fieldAccuracies = mutableListOf<Double>()
        val criticalRecalls = mutableListOf<Double>()
        val semanticCorrectness = mutableListOf<Boolean>()
        val contextReconstruction = mutableListOf<Boolean>()

        var successfulMessages = 0
        var activeContext: SharedContextEntry? = null
        val seenPacketKeys = mutableSetOf<String>()

        val dtnStore = if (condition.hasOutage) {
            DtnStore(maxPackets = 50, maxBytes = 512 * 1024, expiryMs = 600_000L)
        } else null

        val reassemblyBuffer = ReassemblyBuffer()

        for (gt in messages) {
            val seq = (seqGenerator.incrementAndGet() and 0x7FFF).toShort()
            val srcId = 1001
            val destId = 2002

            // 1. Build Representation
            val rep = buildRepresentation(gt, representationMode, networkMode, srcId, activeContext)
            val rawPayload = rep.payloadBytes
            val flags = determineFlags(rep.mode, requireAck, rep.isCompressed)

            // 2. Fragmentation Check
            val fragments = if (PacketFragmenter.needsFragmentation(rawPayload.size)) {
                fragmenter.fragment(rawPayload, transferId = seq)
            } else {
                emptyList()
            }

            val packetsToSend = if (fragments.isNotEmpty()) {
                totalFragmentsGenerated += fragments.size
                fragments.mapIndexed { idx, frag ->
                    Packet(
                        sequenceNumber = (seq + idx).toShort(),
                        timestamp = 1000L,
                        sourceDeviceId = srcId,
                        destinationDeviceId = destId,
                        language = IndicLanguage.ENGLISH,
                        flags = (flags.toInt() or Packet.FLAG_FRAGMENTED).toByte(),
                        priority = when (gt.severity) {
                            org.sih.itantra.core.protocol.EmergencySeverity.CRITICAL -> MessagePriority.ALERT
                            org.sih.itantra.core.protocol.EmergencySeverity.ALERT -> MessagePriority.ALERT
                            org.sih.itantra.core.protocol.EmergencySeverity.IMPORTANT -> MessagePriority.IMPORTANT
                            else -> MessagePriority.NORMAL
                        },
                        payload = frag.toPayload()
                    )
                }
            } else {
                listOf(
                    Packet(
                        sequenceNumber = seq,
                        timestamp = 1000L,
                        sourceDeviceId = srcId,
                        destinationDeviceId = destId,
                        language = IndicLanguage.ENGLISH,
                        flags = flags,
                        priority = when (gt.severity) {
                            org.sih.itantra.core.protocol.EmergencySeverity.CRITICAL -> MessagePriority.ALERT
                            org.sih.itantra.core.protocol.EmergencySeverity.ALERT -> MessagePriority.ALERT
                            org.sih.itantra.core.protocol.EmergencySeverity.IMPORTANT -> MessagePriority.IMPORTANT
                            else -> MessagePriority.NORMAL
                        },
                        payload = rawPayload
                    )
                )
            }

            var messageDelivered = false
            var messageLatency = 0L
            var messageAckLatency = 0L
            var deliveredPayload: ByteArray? = null

            // 3. Outage Simulation (DTN Store and Forward)
            if (condition.hasOutage && dtnStore != null) {
                totalDtnStored++
                for (pkt in packetsToSend) {
                    dtnStore.store(pkt)
                }

                if (condition.outageDurationMs >= DtnStore.EXPIRY_MS) {
                    // Outage exceeded TTL: message expires in DTN buffer
                    totalDtnExpired++
                    dtnStore.pruneExpired()
                    tacticalSuccesses.add(false)
                    fieldAccuracies.add(0.0)
                    criticalRecalls.add(0.0)
                    semanticCorrectness.add(false)
                    contextReconstruction.add(false)
                    continue
                } else {
                    // Recover after outage
                    messageLatency += condition.outageDurationMs
                }
            }

            // 4. Packet Transmission Simulation with Retries
            var allPacketsDelivered = true
            for (pkt in packetsToSend) {
                val wireBytes = PacketSerializer.serialize(pkt)
                totalBytesTransmitted += wireBytes.size

                val duplicateKey = "${pkt.sourceDeviceId}_${pkt.sequenceNumber}"
                if (seenPacketKeys.contains(duplicateKey)) {
                    totalDuplicateSuppressed++
                } else {
                    seenPacketKeys.add(duplicateKey)
                }

                var packetDelivered = false
                var attempts = 0
                val maxAttempts = if (requireAck) (1 + maxRetries) else 1

                var attemptLatency = 0L
                while (attempts < maxAttempts && !packetDelivered) {
                    attempts++
                    totalPacketsTransmitted++

                    val txDelay = bandwidthLimiter.calculateTransmissionDelayMs(wireBytes.size)
                    val flightDelay = latencyJitter.calculateDelayMs()
                    attemptLatency += (txDelay + flightDelay)

                    val isDropped = lossModel.shouldDrop(totalPacketsTransmitted, wireBytes.size)
                    if (isDropped) {
                        totalPacketsLost++
                        if (attempts > 1) totalRetransmissions++
                        // Wait timeout before next attempt
                        attemptLatency += 200L
                    } else {
                        packetDelivered = true
                        totalBytesDelivered += wireBytes.size
                        if (requireAck) {
                            // Reverse path for ACK (3-byte payload + 31-byte frame = 34 bytes)
                            val ackWireBytes = 34
                            val ackTxDelay = bandwidthLimiter.calculateTransmissionDelayMs(ackWireBytes)
                            val ackFlightDelay = latencyJitter.calculateDelayMs()
                            val ackDropped = lossModel.shouldDrop(totalPacketsTransmitted + 999, ackWireBytes)
                            if (ackDropped) {
                                // ACK lost causes retransmission even though receiver got it
                                packetDelivered = false
                                totalRetransmissions++
                                attemptLatency += (ackTxDelay + ackFlightDelay + 200L)
                            } else {
                                messageAckLatency = (attemptLatency + ackTxDelay + ackFlightDelay)
                            }
                        }
                    }
                }

                if (!packetDelivered) {
                    allPacketsDelivered = false
                    break
                } else {
                    messageLatency += attemptLatency
                    // Receiver reassembly handling
                    if (pkt.isFragmented) {
                        val fragment = org.sih.itantra.core.protocol.PacketFragment.fromPayload(pkt.payload)
                        if (fragment != null) {
                            val res = reassemblyBuffer.addFragment(pkt.sourceDeviceId, fragment)
                            if (res != null) {
                                totalReassemblySuccess++
                                deliveredPayload = res.payload
                            }
                        }
                    } else {
                        deliveredPayload = pkt.payload
                    }
                }
            }

            messageDelivered = allPacketsDelivered && (if (fragments.isNotEmpty()) deliveredPayload != null else true)

            if (messageDelivered) {
                successfulMessages++
                endToEndLatencies.add(messageLatency)
                if (requireAck) ackLatencies.add(messageAckLatency)

                // 5. Tactical Evaluation
                val eval = TacticalEvaluator.evaluate(gt, rep.mode, deliveredPayload, activeContext)
                tacticalSuccesses.add(eval.tacticalSuccess)
                fieldAccuracies.add(eval.semanticFieldAccuracy)
                criticalRecalls.add(eval.criticalFieldRecall)
                semanticCorrectness.add(eval.categoryMatched && eval.countMatched && eval.sectorMatched)

                if (rep.mode == AdaptiveRepresentationMode.CONTEXT_DELTA) {
                    contextReconstruction.add(eval.tacticalSuccess)
                } else {
                    contextReconstruction.add(true)
                }

                // Update shared context for subsequent delta messages
                if (!gt.isDeltaUpdate) {
                    activeContext = SharedContextEntry(
                        contextId = 42,
                        version = 1,
                        category = gt.category,
                        subtype = gt.subtype,
                        severity = gt.severity,
                        count = gt.count,
                        sector = gt.sector.toShort(),
                        confidence = 90,
                        sourceDeviceId = srcId,
                        createdAt = 1000L,
                        lastUpdatedAt = 1000L,
                        expiresAt = 1000L + org.sih.itantra.core.context.SharedContextStore.DEFAULT_TTL_MS
                    )
                } else if (activeContext != null) {
                    activeContext = activeContext.copy(
                        version = activeContext.version + 1,
                        count = gt.count,
                        lastUpdatedAt = 1000L
                    )
                }
            } else {
                tacticalSuccesses.add(false)
                fieldAccuracies.add(0.0)
                criticalRecalls.add(0.0)
                semanticCorrectness.add(false)
                contextReconstruction.add(false)
            }
        }

        val totalMsgs = messages.size
        val deliverySuccessRate = if (totalMsgs > 0) successfulMessages.toDouble() / totalMsgs.toDouble() else 0.0
        val observedLossRate = if (totalPacketsTransmitted > 0) totalPacketsLost.toDouble() / totalPacketsTransmitted.toDouble() else 0.0

        val latencyDist = DistributionMetrics.fromLongs(endToEndLatencies)
        val ackDist = DistributionMetrics.fromLongs(ackLatencies)

        val totalTimeSec = (latencyDist.mean * totalMsgs.coerceAtLeast(1)) / 1000.0
        val goodputBps = if (totalTimeSec > 0.0) (totalBytesDelivered * 8.0) / totalTimeSec else 0.0

        val overheadRatio = if (totalBytesTransmitted > 0) {
            val rawPayloadSum = messages.sumOf { it.text.toByteArray(Charsets.UTF_8).size }
            ((totalBytesTransmitted - rawPayloadSum).toDouble() / totalBytesTransmitted.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0

        val repExpansion = if (representationMode == AdaptiveRepresentationMode.FULL) 1.0 else {
            val baseSize = TacticalCorpus.MSG_MED_INITIAL.text.toByteArray(Charsets.UTF_8).size
            val actualAvg = if (totalMsgs > 0) (totalBytesTransmitted.toDouble() / totalMsgs.toDouble()) else baseSize.toDouble()
            actualAvg / baseSize.toDouble()
        }

        return ScenarioMetrics(
            scenarioId = scenarioId,
            representation = representationMode.name,
            conditionName = condition.name,
            bandwidthBps = condition.bandwidthBps,
            latencyMs = condition.oneWayLatencyMs,
            jitterMs = condition.jitterMs,
            configuredLossRate = condition.packetLossRate,
            burstLossLength = condition.burstLossCount,
            totalMessages = totalMsgs,
            successfulMessages = successfulMessages,
            deliverySuccessRate = deliverySuccessRate,
            observedPacketLossRate = observedLossRate,
            endToEndLatencyMetrics = latencyDist,
            ackLatencyMetrics = ackDist,
            retransmissionCount = totalRetransmissions,
            fragmentCount = totalFragmentsGenerated,
            reassemblySuccessCount = totalReassemblySuccess,
            dtnStorageCount = totalDtnStored,
            dtnExpiryCount = totalDtnExpired,
            duplicateSuppressionCount = totalDuplicateSuppressed,
            bytesTransmitted = totalBytesTransmitted,
            bytesDelivered = totalBytesDelivered,
            goodputBps = goodputBps,
            overheadRatio = overheadRatio,
            representationExpansionRatio = repExpansion,
            contextReconstructionSuccessRate = if (contextReconstruction.isNotEmpty()) contextReconstruction.count { it }.toDouble() / contextReconstruction.size else 1.0,
            semanticCorrectnessRate = if (semanticCorrectness.isNotEmpty()) semanticCorrectness.count { it }.toDouble() / semanticCorrectness.size else 0.0,
            tacticalSuccessRate = if (tacticalSuccesses.isNotEmpty()) tacticalSuccesses.count { it }.toDouble() / tacticalSuccesses.size else 0.0,
            semanticFieldAccuracy = if (fieldAccuracies.isNotEmpty()) fieldAccuracies.average() else 0.0,
            criticalFieldRecall = if (criticalRecalls.isNotEmpty()) criticalRecalls.average() else 0.0,
            seed = seed,
            device = "CONTROLLED_SIMULATION"
        )
    }

    private fun buildRepresentation(
        gt: TacticalGroundTruth,
        mode: AdaptiveRepresentationMode,
        networkMode: AdaptiveNetworkMode,
        sourceDeviceId: Int,
        activeContext: SharedContextEntry?
    ): AdaptiveMessageRepresentation {
        val cmd = gt.toSemanticCommand()
        return when (mode) {
            AdaptiveRepresentationMode.FULL -> {
                AdaptiveRepresentationPolicy.select(
                    text = gt.text,
                    networkMode = networkMode,
                    forceMode = AdaptiveRepresentationMode.FULL
                )
            }
            AdaptiveRepresentationMode.COMPACT -> {
                AdaptiveRepresentationPolicy.select(
                    text = gt.text,
                    networkMode = networkMode,
                    forceMode = AdaptiveRepresentationMode.COMPACT
                )
            }
            AdaptiveRepresentationMode.SEMANTIC_BASE -> {
                AdaptiveRepresentationPolicy.buildSemanticBaseRepresentation(
                    cmd = cmd,
                    confidence = 0.95f,
                    explanation = "Benchmark SEMANTIC_BASE"
                )
            }
            AdaptiveRepresentationMode.SEMANTIC_ENHANCED -> {
                AdaptiveRepresentationPolicy.buildSemanticEnhancedRepresentation(
                    cmd = cmd,
                    originalText = gt.text,
                    confidence = 0.95f,
                    explanation = "Benchmark SEMANTIC_ENHANCED"
                )
            }
            AdaptiveRepresentationMode.CONTEXT_DELTA -> {
                if (activeContext != null && gt.isDeltaUpdate) {
                    val delta = org.sih.itantra.core.vbr.ContextDelta.computeDelta(
                        baseContext = activeContext,
                        newCommand = cmd,
                        newVersion = activeContext.version + 1,
                        hasEnhancement = false
                    )
                    AdaptiveRepresentationPolicy.buildContextDeltaRepresentation(
                        delta = delta,
                        baseContext = activeContext,
                        command = cmd,
                        confidence = 0.95f,
                        enhancement = null,
                        explanation = "Benchmark CONTEXT_DELTA update"
                    )
                } else {
                    // Initial or fallback delta
                    AdaptiveRepresentationPolicy.buildContextDeltaRepresentation(
                        cmd = cmd,
                        sourceDeviceId = sourceDeviceId,
                        confidence = 0.95f,
                        originalText = gt.text
                    )
                }
            }
            else -> {
                AdaptiveRepresentationPolicy.select(
                    text = gt.text,
                    networkMode = networkMode,
                    forceMode = AdaptiveRepresentationMode.FULL
                )
            }
        }
    }

    private fun determineFlags(
        mode: AdaptiveRepresentationMode,
        requireAck: Boolean,
        isCompressed: Boolean
    ): Byte {
        var flags = 0
        if (requireAck) flags = flags or Packet.FLAG_REQUIRES_ACK
        if (isCompressed) flags = flags or Packet.FLAG_COMPRESSED
        if (mode == AdaptiveRepresentationMode.COMPACT) flags = flags or Packet.FLAG_COMPACT
        if (mode == AdaptiveRepresentationMode.SEMANTIC_BASE ||
            mode == AdaptiveRepresentationMode.SEMANTIC_ENHANCED ||
            mode == AdaptiveRepresentationMode.SEMANTIC ||
            mode == AdaptiveRepresentationMode.CONTEXT_DELTA) {
            flags = flags or Packet.FLAG_SEMANTIC
        }
        return flags.toByte()
    }
}
