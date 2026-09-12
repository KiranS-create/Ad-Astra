package org.sih.itantra.core.message.journey

import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioPriorityContext
import org.sih.itantra.core.persistence.MessageDirection

/**
 * Categorized lifecycle event types occurring in a message's network path.
 * Every type is supported by real underlying application data.
 */
enum class JourneyEventType(
    val label: String,
    val description: String
) {
    /** Message was created and recorded locally or received at source node. */
    CREATED("CREATED", "Message created and recorded"),

    /** Message placed in QoS queue due to channel congestion or priority scheduling. */
    QUEUED("QUEUED", "Held in QoS priority queue"),

    /** No active route exists to destination; message held while route is sought. */
    WAITING_FOR_ROUTE("WAITING FOR ROUTE", "Awaiting route discovery"),

    /** Actively in flight over the wireless transport medium. */
    SENDING("SENDING", "Transmitting over wireless medium"),

    /** Initial packet transmission onto the physical/wireless link. */
    TRANSMITTED("TRANSMITTED", "Transmitted via radio bearer"),

    /** Forwarded by intermediate mesh node(s). */
    RELAYED("RELAYED", "Forwarded through multi-hop mesh"),

    /** Bounded storage in DTN buffer awaiting contact window or route. */
    DTN_STORED("DTN STORED", "Stored in DTN buffer"),

    /** Initial transmission complete; awaiting application-layer ACK (30s timeout). */
    ACK_PENDING("ACK PENDING", "Awaiting delivery receipt"),

    /** Delivery receipt received from destination node. */
    ACKNOWLEDGED("ACKNOWLEDGED", "Delivery receipt confirmed"),

    /** Ingress of message at this node from a peer. */
    RECEIVED("RECEIVED", "Received by local node"),

    /** Multi-fragment packet reconstruction complete. */
    REASSEMBLED("REASSEMBLED", "Fragments reassembled"),

    /** Delivery receipt timed out (30s) without an ACK. */
    FAILED("FAILED", "Delivery timed out / failed")
}

/**
 * Visual styling status for a journey timeline event.
 */
enum class JourneyEventStatus {
    SUCCESS,
    PENDING,
    WARNING,
    FAILED,
    INFO
}

/**
 * An immutable, verified event in the journey of an individual message.
 *
 * Ground truth rules:
 * - Unavailable fields must be null or "UNKNOWN".
 * - Never fabricate intermediate nodes or transit timestamps.
 *
 * @param type Categorized lifecycle event type.
 * @param timestampMs Epoch timestamp in ms, or null if unrecorded.
 * @param node Name or callsign of the node involved ("Local Node (Self)", "Node #X", or "UNKNOWN").
 * @param transport Wireless bearer label (e.g. "Wi-Fi UDP", "Bluetooth RFCOMM"), or null.
 * @param hop Hop index (e.g. 1, 2), or null if not applicable / unknown.
 * @param status Visual highlight status.
 * @param detail Factual explanation supported by recorded state.
 */
data class JourneyEvent(
    val type: JourneyEventType,
    val timestampMs: Long? = null,
    val node: String = "UNKNOWN",
    val transport: String? = null,
    val hop: Int? = null,
    val status: JourneyEventStatus = JourneyEventStatus.INFO,
    val detail: String? = null
)

/**
 * Immutable UI projection model representing the complete substantiated journey of a message.
 *
 * The journey explains "WHAT HAPPENED" chronologically, in contrast to Feature 7
 * which explains "WHAT THIS PACKET IS" statically.
 *
 * @param messageId Unique message identifier.
 * @param text Message textual content.
 * @param direction SENT or RECEIVED.
 * @param source Originating node display name.
 * @param destination Target node display name.
 * @param priority Underlying message priority.
 * @param priorityContext Priority context derived from Feature 6.
 * @param isEmergency True if priority is DISTRESS or ALERT.
 * @param currentRadioState Current Feature 6 delivery state.
 * @param events Chronologically ordered, deduplicated journey events.
 * @param isPartial True when intermediate transit events or timestamps were not recorded locally.
 * @param partialReason Explanation of what telemetry is missing.
 * @param totalKnownHops Total known hops traversed, or null if unknown.
 * @param knownTransports List of transports used during the journey.
 * @param ackState Formatted description of ACK status.
 * @param dtnStatus Formatted description of DTN involvement.
 * @param fragmentationSummary Formatted description of fragmentation/reassembly.
 */
data class MessageJourney(
    val messageId: String,
    val text: String,
    val direction: MessageDirection,
    val source: String,
    val destination: String,
    val priority: MessagePriority,
    val priorityContext: RadioPriorityContext,
    val isEmergency: Boolean,
    val currentRadioState: RadioDeliveryState,
    val events: List<JourneyEvent>,
    val isPartial: Boolean,
    val partialReason: String? = null,
    val totalKnownHops: Int? = null,
    val knownTransports: List<String> = emptyList(),
    val ackState: String? = null,
    val dtnStatus: String? = null,
    val fragmentationSummary: String? = null
)
