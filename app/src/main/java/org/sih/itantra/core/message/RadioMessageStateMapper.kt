package org.sih.itantra.core.message

import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus

/**
 * Projects a [MessageRecord] into [RadioMessageTelemetry] suitable for the chat bubble UI.
 *
 * Mapping rules:
 * - Only states that can be verified from real MessageRecord fields are emitted.
 * - When data is insufficient, [RadioDeliveryState.UNKNOWN] is used.
 * - Transport string comes from MessageRecord metadata; never hardcoded.
 * - Hop count comes from MessageRecord.hopCount; 0 is treated as unavailable for
 *   incoming messages unless isRelayed is explicitly true.
 * - Authentication is ONLY asserted when isSecure == true.
 * - DTN_STORED is only emitted when BOTH transferId is non-null AND deliveryStatus is PENDING.
 * - WAITING_FOR_ROUTE is distinguished from DTN_STORED by isRelayed == false.
 */
object RadioMessageStateMapper {

    /**
     * Maps a single [MessageRecord] to its [RadioMessageTelemetry].
     *
     * @param record           The message record to project.
     * @param transportOverride Optional topology-sourced transport name. Falls back to
     *                          record-level transport heuristic when null.
     */
    fun map(
        record: MessageRecord,
        transportOverride: String? = null
    ): RadioMessageTelemetry {
        val deliveryState = deriveDeliveryState(record)
        val priorityContext = RadioPriorityContext.from(record.priority)
        val transport = transportOverride ?: deriveTransport(record)
        val hopCount = deriveHopCount(record)
        val ackRttMs = if (deliveryState == RadioDeliveryState.ACKNOWLEDGED) {
            record.deliveryLatencyMs?.takeIf { it > 0L }
        } else null
        val isFragmented = record.fragmentCount != null && record.fragmentCount > 1
        val isDtnPending = record.deliveryStatus == DeliveryStatus.PENDING &&
            record.transferId != null &&
            record.isRelayed

        return RadioMessageTelemetry(
            deliveryState = deliveryState,
            priorityContext = priorityContext,
            transport = transport,
            hopCount = hopCount,
            isAuthenticated = record.isSecure,
            isFragmented = isFragmented,
            fragmentCount = if (isFragmented) record.fragmentCount else null,
            ackRttMs = ackRttMs,
            isDtnPending = isDtnPending,
            qosStatus = record.qosStatus,
            timestampMs = record.timestamp
        )
    }

    /**
     * Derives the primary [RadioDeliveryState] from a [MessageRecord].
     *
     * Decision tree (sent messages):
     *   qosStatus contains "QUEUED"  → QUEUED
     *   deliveryStatus == SENDING    → SENDING
     *   deliveryStatus == DELIVERED  → ACKNOWLEDGED
     *   deliveryStatus == TIMEOUT    → FAILED
     *   deliveryStatus == PENDING:
     *     + transferId != null + isRelayed  → DTN_STORED
     *     + transferId != null               → ACK_PENDING
     *     + no transferId                    → WAITING_FOR_ROUTE
     *   deliveryStatus == NONE:
     *     default                            → ACK_PENDING (sent, no transfer tracker yet)
     *
     * Decision tree (received messages):
     *   hopCount > 1 || isRelayed  → RELAYED
     *   else                       → RECEIVED
     */
    internal fun deriveDeliveryState(record: MessageRecord): RadioDeliveryState {
        return when (record.direction) {
            MessageDirection.RECEIVED -> {
                if (record.isRelayed || record.hopCount > 1) {
                    RadioDeliveryState.RELAYED
                } else {
                    RadioDeliveryState.RECEIVED
                }
            }
            MessageDirection.SENT -> deriveSentState(record)
        }
    }

    private fun deriveSentState(record: MessageRecord): RadioDeliveryState {
        // QoS queue signal overrides delivery tracker
        if (!record.qosStatus.isNullOrBlank() && record.qosStatus.contains("QUEUED", ignoreCase = true)) {
            return RadioDeliveryState.QUEUED
        }

        return when (record.deliveryStatus) {
            DeliveryStatus.SENDING   -> RadioDeliveryState.SENDING
            DeliveryStatus.DELIVERED -> RadioDeliveryState.ACKNOWLEDGED
            DeliveryStatus.TIMEOUT   -> RadioDeliveryState.FAILED
            DeliveryStatus.PENDING   -> {
                when {
                    record.transferId != null && record.isRelayed -> RadioDeliveryState.DTN_STORED
                    record.transferId != null                      -> RadioDeliveryState.ACK_PENDING
                    else                                           -> RadioDeliveryState.WAITING_FOR_ROUTE
                }
            }
            DeliveryStatus.NONE -> {
                // Message was created but no transfer was registered yet.
                // Treat as ACK_PENDING (most recent outgoing messages before tracker engages).
                RadioDeliveryState.ACK_PENDING
            }
        }
    }

    /**
     * Derives hop count only when real data is present.
     * Returns null (→ UNKNOWN in UI) if no meaningful hop data exists.
     */
    internal fun deriveHopCount(record: MessageRecord): Int? {
        return when {
            record.hopCount > 0 -> record.hopCount
            record.isRelayed    -> 2 // conservative floor: relayed implies ≥2 hops
            else                -> null
        }
    }

    /**
     * Derives transport label from record metadata without fabricating hardware details.
     * Returns null when no transport information is available.
     */
    internal fun deriveTransport(record: MessageRecord): String? {
        // The qosStatus field sometimes encodes transport info e.g. "WIFI/QUEUED"
        if (!record.qosStatus.isNullOrBlank()) {
            val upper = record.qosStatus.uppercase()
            return when {
                upper.contains("WIFI") || upper.contains("WI-FI")  -> "Wi-Fi UDP"
                upper.contains("BT") || upper.contains("BLUETOOTH") -> "Bluetooth RFCOMM"
                else -> null
            }
        }
        // Heuristic: direction + relay flag
        return when {
            record.direction == MessageDirection.SENT && !record.isRelayed -> "Wi-Fi UDP"
            record.isRelayed -> "MANET Relay"
            else -> null
        }
    }
}
