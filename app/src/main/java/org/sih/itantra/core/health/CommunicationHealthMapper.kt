package org.sih.itantra.core.health

import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.diagnostics.DiagnosticsState
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.qos.CongestionState
import org.sih.itantra.core.transport.PeerDevice
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deterministic pure-function mapper translating low-level network,
 * transport, DTN, QoS, and message history state into [CommunicationHealthState].
 *
 * Requirements:
 * 1. Truthful reporting: zero fabricated metrics (Signal is strictly "NOT MEASURED").
 * 2. Deterministic derivation of [OverallHealthStatus].
 * 3. Feature 6 delivery state aggregation.
 */
object CommunicationHealthMapper {

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun map(
        localNodeId: Int,
        diagnostics: DiagnosticsState,
        topology: MeshTopologySnapshot,
        messageHistory: List<MessageRecord>,
        wifiState: TransportState,
        wifiPeers: List<PeerDevice> = emptyList(),
        bluetoothState: TransportState,
        bluetoothPeers: List<PeerDevice> = emptyList(),
        preferredTransport: TransportType = TransportType.WIFI,
        isRelayEnabled: Boolean = true,
        dtnQueueSize: Int = diagnostics.dtnQueueSize,
        qosQueuedPackets: Int = diagnostics.queuedPackets,
        qosCongestion: CongestionState = when (diagnostics.congestionState) {
            "CONGESTED" -> CongestionState.CONGESTED
            "BUSY"      -> CongestionState.BUSY
            else        -> CongestionState.NORMAL
        },
        qosDistress: Int = diagnostics.queuedDistress,
        qosAlert: Int = diagnostics.queuedAlert,
        qosImportant: Int = diagnostics.queuedImportant,
        qosNormal: Int = diagnostics.queuedNormal
    ): CommunicationHealthState {

        // 1. Delivery Health Aggregation (Feature 6 states)
        var acknowledgedCount = 0
        var ackPendingCount = 0
        var relayedCount = 0
        var dtnStoredCount = 0
        var waitingRouteCount = 0
        var failedCount = 0

        for (record in messageHistory) {
            val telemetry = RadioMessageStateMapper.map(record)
            when (telemetry.deliveryState) {
                RadioDeliveryState.ACKNOWLEDGED -> acknowledgedCount++
                RadioDeliveryState.ACK_PENDING   -> ackPendingCount++
                RadioDeliveryState.RELAYED       -> relayedCount++
                RadioDeliveryState.DTN_STORED    -> dtnStoredCount++
                RadioDeliveryState.WAITING_FOR_ROUTE -> waitingRouteCount++
                RadioDeliveryState.FAILED        -> failedCount++
                else -> { /* QUEUED, SENDING, RECEIVED, UNKNOWN */ }
            }
        }

        val totalMsgs = messageHistory.size
        val successRate = if ((acknowledgedCount + failedCount) > 0) {
            (acknowledgedCount.toDouble() / (acknowledgedCount + failedCount)) * 100.0
        } else null

        val deliveryHealth = DeliveryHealthSummary(
            totalMessagesCount = totalMsgs,
            acknowledgedCount = acknowledgedCount,
            ackPendingCount = ackPendingCount,
            relayedCount = relayedCount,
            dtnStoredCount = dtnStoredCount,
            waitingRouteCount = waitingRouteCount,
            failedCount = failedCount,
            lastDeliveryAckLatencyMs = diagnostics.lastDeliveryAckLatencyMs,
            deliverySuccessRatePercent = successRate
        )

        // 2. Transport Health Items
        val isWifiUp = wifiState == TransportState.CONNECTED || wifiState == TransportState.LISTENING
        val isBtUp = bluetoothState == TransportState.CONNECTED || bluetoothState == TransportState.LISTENING

        val wifiDisplayState = when (wifiState) {
            TransportState.CONNECTED   -> "CONNECTED"
            TransportState.LISTENING   -> "LISTENING / BCAST"
            TransportState.CONNECTING  -> "CONNECTING"
            TransportState.DISCONNECTED-> "DISCONNECTED"
            TransportState.ERROR       -> "ERROR"
        }

        val btDisplayState = when (bluetoothState) {
            TransportState.CONNECTED   -> "CONNECTED (${bluetoothPeers.size})"
            TransportState.LISTENING   -> "LISTENING"
            TransportState.CONNECTING  -> "CONNECTING"
            TransportState.DISCONNECTED-> "DISCONNECTED"
            TransportState.ERROR       -> "ERROR"
        }

        val transports = listOf(
            TransportHealthItem(
                name = "Wi-Fi (UDP Bcast)",
                type = TransportType.WIFI,
                state = wifiState,
                displayState = wifiDisplayState,
                connectedPeerCount = wifiPeers.size,
                details = "Port 42888 · Ad-hoc / Hotspot",
                signalDbm = "NOT MEASURED",
                isPrimary = preferredTransport == TransportType.WIFI
            ),
            TransportHealthItem(
                name = "Bluetooth Classic",
                type = TransportType.BLUETOOTH,
                state = bluetoothState,
                displayState = btDisplayState,
                connectedPeerCount = bluetoothPeers.size,
                details = if (bluetoothPeers.isNotEmpty()) "RFCOMM SPP Active" else "RFCOMM Standby",
                signalDbm = "NOT MEASURED",
                isPrimary = preferredTransport == TransportType.BLUETOOTH
            )
        )

        // 3. Route Health
        val reachableNodes = topology.nodes.filter { !it.isLocal && it.isReachable }
        val knownNodes = topology.nodes.filter { !it.isLocal }
        val activeNeighbors = topology.nodes.filter { !it.isLocal && it.hopCount == 1 }

        val preferredRouteSummary = when {
            topology.routes.isNotEmpty() -> {
                val best = topology.routes.first()
                "Route to #${best.destinationNodeId} via #${best.nextHopNodeId} (${best.hopCount} hop${if (best.hopCount > 1) "s" else ""})"
            }
            reachableNodes.isNotEmpty() -> "Direct 1-Hop Neighbors (${reachableNodes.size} reachable)"
            else -> "Broadcast Only (No Routes Established)"
        }

        val routeHealth = RouteHealthState(
            activeRoutesCount = topology.routes.size,
            reachableDestinationsCount = reachableNodes.size,
            knownNodesCount = knownNodes.size,
            activeNeighborsCount = activeNeighbors.size,
            isRelayEnabled = isRelayEnabled,
            packetsRelayed = diagnostics.packetsRelayed,
            preferredRouteSummary = preferredRouteSummary,
            routeQualityLabel = diagnostics.lastRouteQualityLabel
        )

        // 4. DTN Health
        val dtnHealth = DtnHealthState(
            queueSize = dtnQueueSize,
            maxCapacity = 50,
            totalStoredPackets = diagnostics.dtnStored,
            totalForwardedPackets = diagnostics.dtnForwarded,
            totalExpiredPackets = diagnostics.dtnExpired,
            totalDroppedPackets = diagnostics.dtnDropped,
            isActivelyUsed = dtnQueueSize > 0 || diagnostics.dtnStored > 0
        )

        // 5. QoS Health
        val qosHealth = QosHealthState(
            congestionState = qosCongestion,
            queuedPackets = qosQueuedPackets,
            maxCapacity = 100,
            queuedDistress = qosDistress,
            queuedAlert = qosAlert,
            queuedImportant = qosImportant,
            queuedNormal = qosNormal,
            emergencyPreemptions = diagnostics.distressPreemptions,
            normalStarvationAvoidance = diagnostics.normalStarvationAvoidance,
            queueOverflows = diagnostics.queueOverflows
        )

        // 6. Overall Status Determination
        val (overallStatus, overallBadge, overallSummary) = when {
            // Both transports disconnected/error
            !isWifiUp && !isBtUp -> {
                Triple(
                    OverallHealthStatus.OFFLINE,
                    "0 TRANSPORTS UP",
                    "Both Wi-Fi and Bluetooth transports are inactive or disconnected. Radio pipeline is off-grid."
                )
            }
            // Congestion or high DTN buffer
            qosCongestion == CongestionState.CONGESTED || dtnQueueSize >= 25 || (failedCount >= 3 && acknowledgedCount == 0) -> {
                Triple(
                    OverallHealthStatus.DEGRADED,
                    if (qosCongestion == CongestionState.CONGESTED) "CONGESTED" else "ELEVATED DTN / TIMEOUTS",
                    "Network queue backpressure detected. Packets are buffering or experiencing delivery timeouts."
                )
            }
            // Single transport or no reachable peers while listening
            (!isWifiUp || !isBtUp) && reachableNodes.isEmpty() -> {
                Triple(
                    OverallHealthStatus.LIMITED,
                    if (isWifiUp) "WI-FI ONLY (NO PEERS)" else "BT ONLY (NO PEERS)",
                    "Operating in single-transport mode without confirmed direct peers. Listening for broadcasts."
                )
            }
            // Healthy operation
            else -> {
                val transportCount = (if (isWifiUp) 1 else 0) + (if (isBtUp) 1 else 0)
                Triple(
                    OverallHealthStatus.HEALTHY,
                    "$transportCount TRANSPORT${if (transportCount > 1) "S" else ""} READY",
                    if (reachableNodes.isNotEmpty()) {
                        "Mesh routing active with ${reachableNodes.size} reachable peer${if (reachableNodes.size > 1) "s" else ""}. Zero queue congestion."
                    } else {
                        "Radio transports active and listening. Ready for tactical mesh traffic."
                    }
                )
            }
        }

        // 7. Recent Communication Events
        val recentEvents = messageHistory.take(8).map { record ->
            val telemetry = RadioMessageStateMapper.map(record)
            val timeStr = timeFormatter.format(Date(record.timestamp))
            val isEmergency = record.priority == MessagePriority.DISTRESS || record.priority == MessagePriority.ALERT
            val desc = when (record.direction) {
                MessageDirection.SENT -> "TX to ${record.peer}: ${record.text.take(30)}"
                MessageDirection.RECEIVED -> "RX from ${record.peer}: ${record.text.take(30)}"
            }
            CommunicationEventItem(
                id = record.id,
                timestampMs = record.timestamp,
                timeFormatted = timeStr,
                type = if (isEmergency) "DISTRESS" else if (record.direction == MessageDirection.SENT) "OUTBOUND" else "INBOUND",
                description = desc,
                deliveryState = telemetry.deliveryState,
                isEmergency = isEmergency
            )
        }

        return CommunicationHealthState(
            localNodeId = localNodeId,
            overallStatus = overallStatus,
            overallBadge = overallBadge,
            overallSummary = overallSummary,
            transports = transports,
            routeHealth = routeHealth,
            deliveryHealth = deliveryHealth,
            dtnHealth = dtnHealth,
            qosHealth = qosHealth,
            recentEvents = recentEvents
        )
    }
}
