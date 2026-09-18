package org.sih.itantra.core.health

import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.qos.CongestionState
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType

/**
 * Top-level status representing overall communication health.
 * Derived deterministically from real existing application state.
 */
enum class OverallHealthStatus(val label: String) {
    HEALTHY("HEALTHY"),
    LIMITED("LIMITED"),
    DEGRADED("DEGRADED"),
    OFFLINE("OFFLINE"),
    UNKNOWN("UNKNOWN")
}

/**
 * Health telemetry for a specific physical/logical transport (Wi-Fi, Bluetooth).
 */
data class TransportHealthItem(
    val name: String,
    val type: TransportType,
    val state: TransportState,
    val displayState: String,
    val connectedPeerCount: Int,
    val details: String,
    val signalDbm: String = "NOT MEASURED",
    val isPrimary: Boolean = false
)

/**
 * MANET mesh routing health state.
 */
data class RouteHealthState(
    val activeRoutesCount: Int,
    val reachableDestinationsCount: Int,
    val knownNodesCount: Int,
    val activeNeighborsCount: Int,
    val isRelayEnabled: Boolean,
    val packetsRelayed: Long,
    val preferredRouteSummary: String,
    val routeQualityLabel: String
)

/**
 * Delivery health summary derived from Feature 6 message states.
 */
data class DeliveryHealthSummary(
    val totalMessagesCount: Int,
    val acknowledgedCount: Int,
    val ackPendingCount: Int,
    val relayedCount: Int,
    val dtnStoredCount: Int,
    val waitingRouteCount: Int,
    val failedCount: Int,
    val lastDeliveryAckLatencyMs: Double? = null,
    val deliverySuccessRatePercent: Double? = null
)

/**
 * Delay-Tolerant Networking (DTN) store-and-forward queue health.
 */
data class DtnHealthState(
    val queueSize: Int,
    val maxCapacity: Int = 50,
    val totalStoredPackets: Long,
    val totalForwardedPackets: Long,
    val totalExpiredPackets: Long,
    val totalDroppedPackets: Long,
    val isActivelyUsed: Boolean
)

/**
 * Tactical QoS transmission queue and congestion health.
 */
data class QosHealthState(
    val congestionState: CongestionState,
    val queuedPackets: Int,
    val maxCapacity: Int = 100,
    val queuedDistress: Int,
    val queuedAlert: Int,
    val queuedImportant: Int,
    val queuedNormal: Int,
    val emergencyPreemptions: Long,
    val normalStarvationAvoidance: Long,
    val queueOverflows: Long
)

/**
 * A recent communication event entry for the operator timeline.
 */
data class CommunicationEventItem(
    val id: String,
    val timestampMs: Long,
    val timeFormatted: String,
    val type: String,
    val description: String,
    val deliveryState: RadioDeliveryState,
    val isEmergency: Boolean = false
)

/**
 * Aggregated immutable health snapshot consumed by the Communication Health Panel.
 */
data class CommunicationHealthState(
    val localNodeId: Int,
    val overallStatus: OverallHealthStatus,
    val overallBadge: String,
    val overallSummary: String,
    val transports: List<TransportHealthItem>,
    val routeHealth: RouteHealthState,
    val deliveryHealth: DeliveryHealthSummary,
    val dtnHealth: DtnHealthState,
    val qosHealth: QosHealthState,
    val recentEvents: List<CommunicationEventItem>,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val activeTransports: List<TransportHealthItem>
        get() = transports.filter { it.state == TransportState.CONNECTED || it.state == TransportState.LISTENING }
}
