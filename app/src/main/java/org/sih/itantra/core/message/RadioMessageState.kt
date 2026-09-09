package org.sih.itantra.core.message

import org.sih.itantra.core.common.MessagePriority

/**
 * UI-facing projection of a message's radio/network delivery state.
 *
 * Every value here must be derivable from real application data
 * (MessageRecord, DeliveryStatus, MeshTopologySnapshot).
 * Never invent values — show UNKNOWN when data is unavailable.
 */
enum class RadioDeliveryState(
    val icon: String,
    val label: String
) {
    /** Message is in the QoS queue, not yet sent to the transport layer. */
    QUEUED("◌", "Queued"),

    /** Message is actively being transmitted by the transport layer. */
    SENDING("↑", "Sending"),

    /** Transmission complete; waiting for application-layer ACK (30s timeout). */
    ACK_PENDING("◌", "ACK pending"),

    /** Delivery receipt received from the destination node. */
    ACKNOWLEDGED("✓", "Acknowledged"),

    /** Received by this node from a peer. */
    RECEIVED("↓", "Received"),

    /**
     * Received via multi-hop mesh relay.
     * hop count is available in [RadioMessageTelemetry.hopCount].
     */
    RELAYED("↗", "Relayed"),

    /**
     * Sent to DTN store; will be forwarded when route becomes available.
     * No ACK is expected until the DTN gateway delivers it.
     */
    DTN_STORED("⏸", "Stored for DTN"),

    /** No route exists to destination; message is held locally. */
    WAITING_FOR_ROUTE("⌁", "Waiting for route"),

    /** Delivery receipt timed out (30 s) without an ACK. */
    FAILED("✕", "Delivery failed"),

    /** Historical message predating radio-state tracking; metadata unavailable. */
    UNKNOWN("?", "Unknown")
}

/**
 * Priority context for a message bubble.
 * Derived directly from [MessagePriority] — never applied speculatively.
 */
enum class RadioPriorityContext(
    val label: String,
    val isEmergency: Boolean
) {
    NORMAL("NORMAL", false),
    IMPORTANT("IMPORTANT", false),
    ALERT("ALERT · PRIORITY 2", true),
    DISTRESS("DISTRESS · PRIORITY 1", true);

    companion object {
        fun from(priority: MessagePriority): RadioPriorityContext = when (priority) {
            MessagePriority.NORMAL    -> NORMAL
            MessagePriority.IMPORTANT -> IMPORTANT
            MessagePriority.ALERT     -> ALERT
            MessagePriority.DISTRESS  -> DISTRESS
        }
    }
}

/**
 * Telemetry metadata projected alongside a message bubble.
 *
 * All fields are nullable — set to null if the underlying data is unavailable.
 * The UI must show "UNKNOWN" for null fields rather than fabricating values.
 *
 * @param deliveryState      Primary radio/network delivery state.
 * @param priorityContext    Priority/emergency context derived from MessagePriority.
 * @param transport          Transport medium string from MessageRecord or topology.
 * @param hopCount           Actual hop count from MessageRecord; null if unavailable.
 * @param isAuthenticated    True only when MessageRecord.isSecure && authStatus validated.
 * @param isFragmented       True only when MessageRecord.fragmentCount != null.
 * @param fragmentCount      Fragment count if fragmented; null otherwise.
 * @param ackRttMs           Round-trip time in ms if ACKNOWLEDGED; null otherwise.
 * @param isDtnPending       True if the message is currently in the DTN store.
 * @param qosStatus          Raw QoS status string from MessageRecord; null if absent.
 * @param timestampMs        Message creation timestamp from MessageRecord.
 */
data class RadioMessageTelemetry(
    val deliveryState: RadioDeliveryState,
    val priorityContext: RadioPriorityContext,
    val transport: String?,
    val hopCount: Int?,
    val isAuthenticated: Boolean,
    val isFragmented: Boolean,
    val fragmentCount: Int?,
    val ackRttMs: Long?,
    val isDtnPending: Boolean,
    val qosStatus: String?,
    val timestampMs: Long
)
