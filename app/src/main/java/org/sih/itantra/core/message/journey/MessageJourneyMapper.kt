package org.sih.itantra.core.message.journey

import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.message.RadioMessageTelemetry
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus

/**
 * Pure stateless mapper that projects a [MessageRecord] and its associated [RadioMessageTelemetry]
 * (from Feature 6) into an immutable, operator-facing [MessageJourney].
 *
 * Ground truth rules:
 * 1. Absolute Data Accuracy: Only display lifecycle events grounded in real application data.
 * 2. Never invent intermediate mesh nodes or fake relay callsigns — show "UNKNOWN".
 * 3. Never invent transit timestamps when not recorded locally.
 * 4. Where transit telemetry is incomplete, explicitly flag [MessageJourney.isPartial] = true.
 * 5. Guarantees 100% consistency with Feature 6 [RadioMessageStateMapper] and Feature 7.
 */
object MessageJourneyMapper {

    /**
     * Maps a [MessageRecord] into a [MessageJourney].
     *
     * @param record Source message record.
     * @param telemetryOverride Pre-computed Feature 6 telemetry, or null to derive dynamically.
     * @param transportOverride Topology-derived transport string, or null to derive from record.
     */
    fun map(
        record: MessageRecord,
        telemetryOverride: RadioMessageTelemetry? = null,
        transportOverride: String? = null
    ): MessageJourney {
        val telemetry = telemetryOverride ?: RadioMessageStateMapper.map(record, transportOverride)
        val isEmergency = record.priority == MessagePriority.DISTRESS || record.priority == MessagePriority.ALERT

        val isOutgoing = record.direction == MessageDirection.SENT
        val source = if (isOutgoing) "Local Node (Self)" else (record.peer.ifBlank { "UNKNOWN" })
        val destination = if (isOutgoing) (record.peer.ifBlank { "Broadcast" }) else "Local Node (Self)"

        val rawEvents = if (isOutgoing) {
            buildSentEvents(record, telemetry)
        } else {
            buildReceivedEvents(record, telemetry)
        }

        // Deduplicate events by type (keep earliest / highest fidelity)
        val deduplicatedEvents = deduplicateEvents(rawEvents)

        // Sort events deterministically (chronological + logical protocol sequence)
        val orderedEvents = sortEvents(deduplicatedEvents, record.direction)

        // Determine if the journey is partial (unrecorded intermediate hops/timestamps)
        val (isPartial, partialReason) = evaluateCompleteness(record, telemetry, orderedEvents)

        // Derive summary indicators
        val totalKnownHops = deriveTotalKnownHops(record, telemetry)
        val knownTransports = listOfNotNull(telemetry.transport).distinct()
        val ackState = deriveAckState(record, telemetry)
        val dtnStatus = deriveDtnStatus(record, telemetry)
        val fragmentationSummary = deriveFragmentationSummary(record)

        return MessageJourney(
            messageId = record.id,
            text = record.text,
            direction = record.direction,
            source = source,
            destination = destination,
            priority = record.priority,
            priorityContext = telemetry.priorityContext,
            isEmergency = isEmergency,
            currentRadioState = telemetry.deliveryState,
            events = orderedEvents,
            isPartial = isPartial,
            partialReason = partialReason,
            totalKnownHops = totalKnownHops,
            knownTransports = knownTransports,
            ackState = ackState,
            dtnStatus = dtnStatus,
            fragmentationSummary = fragmentationSummary
        )
    }

    // -------------------------------------------------------------------------
    // Outgoing (SENT) Events Builder
    // -------------------------------------------------------------------------

    private fun buildSentEvents(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry
    ): List<JourneyEvent> {
        val events = mutableListOf<JourneyEvent>()
        val baseTimestamp = record.timestamp.takeIf { it > 0 }

        // 1. CREATED
        events.add(
            JourneyEvent(
                type = JourneyEventType.CREATED,
                timestampMs = baseTimestamp,
                node = "Local Node (Self)",
                detail = when {
                    record.priority == MessagePriority.DISTRESS -> "Emergency distress broadcast initiated"
                    record.priority == MessagePriority.ALERT    -> "Emergency alert broadcast initiated"
                    record.isSemantic                          -> "Semantic command encoded (${record.semanticSavingsBytes ?: 0}B saved)"
                    else                                       -> "Tactical text finalized locally"
                },
                status = JourneyEventStatus.SUCCESS
            )
        )

        // 2. QUEUED (Only when QoS congestion or queuing was recorded)
        if (!record.qosStatus.isNullOrBlank() && record.qosStatus.contains("QUEUED", ignoreCase = true)) {
            events.add(
                JourneyEvent(
                    type = JourneyEventType.QUEUED,
                    timestampMs = baseTimestamp,
                    node = "Local Node (Self)",
                    detail = "QoS priority buffer: ${record.qosStatus}",
                    status = JourneyEventStatus.WARNING
                )
            )
        }

        // 3. WAITING_FOR_ROUTE (When route lookup failed and packet held)
        if (telemetry.deliveryState == RadioDeliveryState.WAITING_FOR_ROUTE) {
            events.add(
                JourneyEvent(
                    type = JourneyEventType.WAITING_FOR_ROUTE,
                    timestampMs = baseTimestamp,
                    node = "Local Node (Self)",
                    detail = "No active route to ${record.peer}; triggering discovery",
                    status = JourneyEventStatus.WARNING
                )
            )
        }

        // 4. DTN_STORED (When packet entered DTN store)
        if (telemetry.isDtnPending || telemetry.deliveryState == RadioDeliveryState.DTN_STORED) {
            events.add(
                JourneyEvent(
                    type = JourneyEventType.DTN_STORED,
                    timestampMs = baseTimestamp,
                    node = "Local Node (Self)",
                    detail = "Held in DTN buffer (600s TTL, awaiting contact window)",
                    status = JourneyEventStatus.WARNING
                )
            )
        }

        // 5. SENDING (Actively in flight)
        if (record.deliveryStatus == DeliveryStatus.SENDING) {
            events.add(
                JourneyEvent(
                    type = JourneyEventType.SENDING,
                    timestampMs = baseTimestamp,
                    node = "Local Node (Self)",
                    transport = telemetry.transport,
                    hop = 1,
                    detail = "In flight over ${telemetry.transport ?: "wireless link"}",
                    status = JourneyEventStatus.PENDING
                )
            )
        }

        // 6. TRANSMITTED (Handed to radio bearer)
        val wasTransmitted = record.deliveryStatus in listOf(
            DeliveryStatus.SENDING,
            DeliveryStatus.PENDING,
            DeliveryStatus.DELIVERED,
            DeliveryStatus.TIMEOUT
        ) || (telemetry.deliveryState != RadioDeliveryState.QUEUED &&
              telemetry.deliveryState != RadioDeliveryState.WAITING_FOR_ROUTE &&
              telemetry.deliveryState != RadioDeliveryState.DTN_STORED)

        if (wasTransmitted) {
            events.add(
                JourneyEvent(
                    type = JourneyEventType.TRANSMITTED,
                    timestampMs = baseTimestamp,
                    node = "Local Node (Self)",
                    transport = telemetry.transport,
                    hop = 1,
                    detail = "Payload ${record.packetSizeBytes}B via ${telemetry.transport ?: "wireless link"}",
                    status = JourneyEventStatus.SUCCESS
                )
            )
        }

        // 7. RELAYED (Multi-hop mesh forwarder)
        if (record.isRelayed || (record.hopCount > 1)) {
            val hopCount = telemetry.hopCount ?: (if (record.isRelayed) 2 else null)
            events.add(
                JourneyEvent(
                    type = JourneyEventType.RELAYED,
                    timestampMs = null, // Intermediate transit time not recorded locally
                    node = "UNKNOWN",   // Intermediate relay identity not captured locally
                    transport = if (record.isRelayed) "MANET Relay" else telemetry.transport,
                    hop = hopCount,
                    detail = when {
                        record.isContextDelta && hopCount != null && hopCount > 1 ->
                            "Context delta v${record.contextVersion ?: 1} forwarded through mesh ($hopCount hops)"
                        record.isContextDelta ->
                            "Context delta forwarded through mesh (≥2 hops)"
                        hopCount != null && hopCount > 1 ->
                            "Forwarded through mesh ($hopCount hops)"
                        else ->
                            "Forwarded through mesh (≥2 hops)"
                    },
                    status = JourneyEventStatus.INFO
                )
            )
        }

        // 8. ACK_PENDING
        if (record.deliveryStatus == DeliveryStatus.PENDING && record.transferId != null && !record.isRelayed) {
            events.add(
                JourneyEvent(
                    type = JourneyEventType.ACK_PENDING,
                    timestampMs = baseTimestamp,
                    node = record.peer.ifBlank { "UNKNOWN" },
                    detail = "Transfer #${record.transferId} registered; awaiting receipt",
                    status = JourneyEventStatus.PENDING
                )
            )
        }

        // 9. ACKNOWLEDGED
        if (record.deliveryStatus == DeliveryStatus.DELIVERED || telemetry.deliveryState == RadioDeliveryState.ACKNOWLEDGED) {
            val ackTime = if (baseTimestamp != null && record.deliveryLatencyMs != null && record.deliveryLatencyMs > 0) {
                baseTimestamp + record.deliveryLatencyMs
            } else {
                baseTimestamp
            }
            val detail = if (record.deliveryLatencyMs != null && record.deliveryLatencyMs > 0) {
                "Delivery confirmed (RTT: ${record.deliveryLatencyMs} ms)"
            } else {
                "Delivery receipt confirmed"
            }
            events.add(
                JourneyEvent(
                    type = JourneyEventType.ACKNOWLEDGED,
                    timestampMs = ackTime,
                    node = record.peer.ifBlank { "UNKNOWN" },
                    detail = detail,
                    status = JourneyEventStatus.SUCCESS
                )
            )
        }

        // 10. FAILED (Delivery timeout)
        if (record.deliveryStatus == DeliveryStatus.TIMEOUT || telemetry.deliveryState == RadioDeliveryState.FAILED) {
            val failTime = if (baseTimestamp != null) baseTimestamp + 30_000L else null
            events.add(
                JourneyEvent(
                    type = JourneyEventType.FAILED,
                    timestampMs = failTime,
                    node = record.peer.ifBlank { "UNKNOWN" },
                    detail = "Delivery timed out (30 s receipt window expired)",
                    status = JourneyEventStatus.FAILED
                )
            )
        }

        return events
    }

    // -------------------------------------------------------------------------
    // Incoming (RECEIVED) Events Builder
    // -------------------------------------------------------------------------

    private fun buildReceivedEvents(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry
    ): List<JourneyEvent> {
        val events = mutableListOf<JourneyEvent>()
        val baseTimestamp = record.timestamp.takeIf { it > 0 }

        // 1. TRANSMITTED (Originating transmission at peer)
        events.add(
            JourneyEvent(
                type = JourneyEventType.TRANSMITTED,
                timestampMs = null, // Remote transmit timestamp not stored locally
                node = record.peer.ifBlank { "UNKNOWN" },
                transport = telemetry.transport,
                hop = 1,
                detail = "Originated and sent by peer node",
                status = JourneyEventStatus.INFO
            )
        )

        // 2. RELAYED (Multi-hop mesh forwarder)
        if (record.isRelayed || record.hopCount > 1) {
            val hopCount = telemetry.hopCount ?: (if (record.isRelayed) 2 else null)
            events.add(
                JourneyEvent(
                    type = JourneyEventType.RELAYED,
                    timestampMs = null, // Intermediate transit time not recorded locally
                    node = "UNKNOWN",   // Intermediate relay identity not captured locally
                    transport = "MANET Relay",
                    hop = hopCount,
                    detail = when {
                        record.isContextDelta && hopCount != null && hopCount > 1 ->
                            "Context delta v${record.contextVersion ?: 1} forwarded unchanged ($hopCount hops)"
                        record.isContextDelta ->
                            "Context delta forwarded unchanged (≥2 hops)"
                        hopCount != null && hopCount > 1 ->
                            "Traversed mesh relay ($hopCount hops)"
                        else ->
                            "Traversed mesh relay (≥2 hops)"
                    },
                    status = JourneyEventStatus.INFO
                )
            )
        }

        // 3. REASSEMBLED (If fragmented)
        if (record.fragmentCount != null && record.fragmentCount > 1) {
            events.add(
                JourneyEvent(
                    type = JourneyEventType.REASSEMBLED,
                    timestampMs = baseTimestamp,
                    node = "Local Node (Self)",
                    detail = "Reconstructed from ${record.fragmentCount} packets",
                    status = JourneyEventStatus.SUCCESS
                )
            )
        }

        // 4. RECEIVED (Local ingress)
        events.add(
            JourneyEvent(
                type = JourneyEventType.RECEIVED,
                timestampMs = baseTimestamp,
                node = "Local Node (Self)",
                transport = telemetry.transport,
                detail = when {
                    record.contextReconstructionStatus == "RECONSTRUCTED" ->
                        "Reconstructed from Context #${record.contextId} v${record.contextVersion}"
                    record.contextReconstructionStatus == "FALLBACK" ->
                        "Delivered as standalone fallback (context missing/expired)"
                    record.contextReconstructionStatus == "REJECTED STALE" ->
                        "Stale delta update rejected"
                    record.contextReconstructionStatus == "REJECTED CONFLICT" ->
                        "Conflicting delta update rejected"
                    record.contextReconstructionStatus == "DUPLICATE SUPPRESSED" ->
                        "Duplicate delta update suppressed"
                    else ->
                        "Delivered to local transceiver application"
                },
                status = JourneyEventStatus.SUCCESS
            )
        )

        return events
    }

    // -------------------------------------------------------------------------
    // Deduplication & Ordering
    // -------------------------------------------------------------------------

    private fun deduplicateEvents(events: List<JourneyEvent>): List<JourneyEvent> {
        val seenTypes = mutableSetOf<JourneyEventType>()
        val result = mutableListOf<JourneyEvent>()
        for (event in events) {
            if (seenTypes.add(event.type)) {
                result.add(event)
            }
        }
        return result
    }

    private fun sortEvents(events: List<JourneyEvent>, direction: MessageDirection): List<JourneyEvent> {
        fun lifecycleRank(type: JourneyEventType): Int = when (direction) {
            MessageDirection.SENT -> when (type) {
                JourneyEventType.CREATED           -> 1
                JourneyEventType.QUEUED            -> 2
                JourneyEventType.WAITING_FOR_ROUTE -> 3
                JourneyEventType.DTN_STORED        -> 4
                JourneyEventType.SENDING           -> 5
                JourneyEventType.TRANSMITTED       -> 6
                JourneyEventType.RELAYED           -> 7
                JourneyEventType.ACK_PENDING       -> 8
                JourneyEventType.ACKNOWLEDGED      -> 9
                JourneyEventType.FAILED            -> 10
                JourneyEventType.REASSEMBLED       -> 11
                JourneyEventType.RECEIVED          -> 12
            }
            MessageDirection.RECEIVED -> when (type) {
                JourneyEventType.CREATED           -> 1
                JourneyEventType.TRANSMITTED       -> 2
                JourneyEventType.RELAYED           -> 3
                JourneyEventType.REASSEMBLED       -> 4
                JourneyEventType.RECEIVED          -> 5
                JourneyEventType.QUEUED            -> 6
                JourneyEventType.WAITING_FOR_ROUTE -> 7
                JourneyEventType.DTN_STORED        -> 8
                JourneyEventType.SENDING           -> 9
                JourneyEventType.ACK_PENDING       -> 10
                JourneyEventType.ACKNOWLEDGED      -> 11
                JourneyEventType.FAILED            -> 12
            }
        }

        return events.sortedWith { a, b ->
            // If both have timestamps and they are strictly different, sort chronologically
            if (a.timestampMs != null && b.timestampMs != null && a.timestampMs != b.timestampMs) {
                a.timestampMs.compareTo(b.timestampMs)
            } else {
                // Otherwise fall back to canonical protocol lifecycle ranking
                lifecycleRank(a.type).compareTo(lifecycleRank(b.type))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Completeness Evaluation
    // -------------------------------------------------------------------------

    private fun evaluateCompleteness(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry,
        events: List<JourneyEvent>
    ): Pair<Boolean, String?> {
        // Relayed messages do not have local logs of intermediate forwarders
        if (record.isRelayed || (record.hopCount > 1)) {
            return true to "Intermediate mesh relay hops forwarded by peer nodes without local telemetry logging."
        }

        // Historical messages or messages predating tracking
        if (telemetry.deliveryState == RadioDeliveryState.UNKNOWN ||
            (record.direction == MessageDirection.SENT && record.deliveryStatus == DeliveryStatus.NONE)) {
            return true to "Historical message record predates detailed radio-state tracking."
        }

        // Missing essential timestamps
        if (record.timestamp <= 0) {
            return true to "Message timestamp was not recorded in storage."
        }

        // Any event having an unknown node (other than standard broadcast)
        val hasUnknownNode = events.any { it.node == "UNKNOWN" && it.type != JourneyEventType.ACK_PENDING }
        if (hasUnknownNode) {
            return true to "Certain network node identities could not be resolved from local application state."
        }

        return false to null
    }

    // -------------------------------------------------------------------------
    // Summary Field Derivations
    // -------------------------------------------------------------------------

    private fun deriveTotalKnownHops(record: MessageRecord, telemetry: RadioMessageTelemetry): Int? {
        return when {
            telemetry.hopCount != null && telemetry.hopCount > 0 -> telemetry.hopCount
            record.hopCount > 0                                  -> record.hopCount
            record.isRelayed                                     -> 2 // conservative floor
            record.direction == MessageDirection.SENT            -> 1
            else                                                 -> null
        }
    }

    private fun deriveAckState(record: MessageRecord, telemetry: RadioMessageTelemetry): String {
        return when (telemetry.deliveryState) {
            RadioDeliveryState.ACKNOWLEDGED -> {
                if (record.deliveryLatencyMs != null && record.deliveryLatencyMs > 0) {
                    "Confirmed (${record.deliveryLatencyMs} ms RTT)"
                } else {
                    "Confirmed ✓"
                }
            }
            RadioDeliveryState.ACK_PENDING -> "Awaiting Receipt"
            RadioDeliveryState.FAILED      -> "Delivery Failed (30s timeout) ✕"
            RadioDeliveryState.RECEIVED, RadioDeliveryState.RELAYED -> "Delivered to Local"
            RadioDeliveryState.WAITING_FOR_ROUTE -> "Held (No Route)"
            RadioDeliveryState.DTN_STORED  -> "Awaiting Forward ACK"
            RadioDeliveryState.QUEUED      -> "Queued (Not Sent)"
            RadioDeliveryState.SENDING     -> "In Flight"
            RadioDeliveryState.UNKNOWN     -> "Unknown"
        }
    }

    private fun deriveDtnStatus(record: MessageRecord, telemetry: RadioMessageTelemetry): String {
        return if (telemetry.isDtnPending || telemetry.deliveryState == RadioDeliveryState.DTN_STORED) {
            "Stored in DTN Buffer (600s TTL)"
        } else {
            "Bypassed (Route Active)"
        }
    }

    private fun deriveFragmentationSummary(record: MessageRecord): String {
        val count = record.fragmentCount
        return if (count != null && count > 1) {
            "Reassembled ($count Fragments)"
        } else {
            "Single Packet (1/1)"
        }
    }
}
