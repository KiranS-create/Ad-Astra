package org.sih.itantra.core.network

import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.chat.IndividualChatHeaderState
import org.sih.itantra.core.health.CommunicationHealthState
import org.sih.itantra.core.health.OverallHealthStatus
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.qos.CongestionState

/**
 * Feature 14: Pure deterministic mapper projecting real network telemetry
 * and conversation state into [AdaptiveNetworkUiState].
 *
 * Requirements:
 * 1. Strict truthfulness: Never imply a message was sent when offline, queued, or stored in DTN.
 * 2. Distinguish WAITING_FOR_ROUTE from FAILED.
 * 3. Distinguish DTN_STORED from ACKNOWLEDGED.
 * 4. Emergency DISTRESS remains discoverable and prioritized in all conditions.
 * 5. Provide canonical delivery labels according to Feature 14 specification.
 */
object AdaptiveNetworkUiMapper {

    /**
     * Resolves the canonical presentation label for each [RadioDeliveryState].
     */
    fun resolveAdaptiveDeliveryLabel(deliveryState: RadioDeliveryState): String {
        return when (deliveryState) {
            RadioDeliveryState.QUEUED            -> "SCHEDULED"
            RadioDeliveryState.SENDING           -> "TRANSMITTING"
            RadioDeliveryState.ACK_PENDING       -> "AWAITING ACK"
            RadioDeliveryState.ACKNOWLEDGED      -> "DELIVERED / ACK"
            RadioDeliveryState.RELAYED           -> "RELAYED"
            RadioDeliveryState.DTN_STORED        -> "STORED FOR FORWARDING"
            RadioDeliveryState.WAITING_FOR_ROUTE -> "WAITING FOR ROUTE"
            RadioDeliveryState.FAILED            -> "FAILED"
            RadioDeliveryState.RECEIVED          -> "RECEIVED"
            RadioDeliveryState.UNKNOWN           -> "STATUS UNKNOWN"
        }
    }

    /**
     * Projects system telemetry into [AdaptiveNetworkUiState].
     */
    fun map(
        healthState: CommunicationHealthState,
        headerState: IndividualChatHeaderState,
        recentMessages: List<MessageRecord> = emptyList(),
        dtnQueueSize: Int = healthState.dtnHealth.queueSize
    ): AdaptiveNetworkUiState {
        val overallStatus = healthState.overallStatus
        val qosState = healthState.qosHealth.congestionState
        val isQosCongested = qosState == CongestionState.CONGESTED || healthState.qosHealth.queuedPackets >= 20

        // Check recent message delivery states for peer-specific DTN or route holds
        var hasRecentDtn = false
        var hasRecentWaitingRoute = false
        for (record in recentMessages.takeLast(5)) {
            val telemetry = RadioMessageStateMapper.map(record)
            if (telemetry.deliveryState == RadioDeliveryState.DTN_STORED) {
                hasRecentDtn = true
            } else if (telemetry.deliveryState == RadioDeliveryState.WAITING_FOR_ROUTE) {
                hasRecentWaitingRoute = true
            }
        }

        // 1. Determine AdaptiveNetworkMode
        val mode = when {
            // Both transports inactive
            overallStatus == OverallHealthStatus.OFFLINE -> AdaptiveNetworkMode.OFFLINE

            // QoS queue backpressure
            isQosCongested -> AdaptiveNetworkMode.CONGESTED

            // Stored in DTN store-and-forward buffer
            headerState.routeState == ChatRouteState.DTN_STORED || (hasRecentDtn && dtnQueueSize > 0) -> AdaptiveNetworkMode.DTN_STORED

            // Route absent while transports are active
            headerState.routeState == ChatRouteState.DISCONNECTED || hasRecentWaitingRoute -> AdaptiveNetworkMode.WAITING_FOR_ROUTE

            // General link degraded
            overallStatus == OverallHealthStatus.DEGRADED -> AdaptiveNetworkMode.DEGRADED

            // General link limited / broadcast only
            overallStatus == OverallHealthStatus.LIMITED || headerState.routeState == ChatRouteState.RECENTLY_HEARD -> AdaptiveNetworkMode.LIMITED

            // Healthy
            overallStatus == OverallHealthStatus.HEALTHY -> AdaptiveNetworkMode.HEALTHY

            else -> AdaptiveNetworkMode.UNKNOWN
        }

        // 2. Build Banner State
        val activeTransportsCount = healthState.transports.count {
            it.state == org.sih.itantra.core.transport.TransportState.CONNECTED ||
            it.state == org.sih.itantra.core.transport.TransportState.LISTENING
        }

        val banner = when (mode) {
            AdaptiveNetworkMode.HEALTHY -> AdaptiveNetworkBannerState(
                bannerText = "LINK HEALTHY · $activeTransportsCount TRANSPORT${if (activeTransportsCount > 1) "S" else ""} ACTIVE",
                badgeLabel = "NOMINAL",
                mode = mode,
                isVisible = true
            )
            AdaptiveNetworkMode.LIMITED -> AdaptiveNetworkBannerState(
                bannerText = "LIMITED LINK · BROADCAST / FEW REACHABLE PEERS",
                badgeLabel = "LIMITED",
                mode = mode,
                isVisible = true
            )
            AdaptiveNetworkMode.DEGRADED -> AdaptiveNetworkBannerState(
                bannerText = "LINK DEGRADED · BACKPRESSURE / RETRIES",
                badgeLabel = "DEGRADED",
                mode = mode,
                isVisible = true
            )
            AdaptiveNetworkMode.OFFLINE -> AdaptiveNetworkBannerState(
                bannerText = "OFFLINE · MESSAGES WILL BE QUEUED LOCALLY",
                badgeLabel = "OFFLINE",
                mode = mode,
                isVisible = true
            )
            AdaptiveNetworkMode.CONGESTED -> AdaptiveNetworkBannerState(
                bannerText = "NETWORK CONGESTED · ${healthState.qosHealth.queuedPackets} PKTS QUEUED",
                badgeLabel = "CONGESTED",
                mode = mode,
                isVisible = true
            )
            AdaptiveNetworkMode.WAITING_FOR_ROUTE -> AdaptiveNetworkBannerState(
                bannerText = "WAITING FOR ROUTE · RETAINING MESSAGE",
                badgeLabel = "DISCOVERY",
                mode = mode,
                isVisible = true
            )
            AdaptiveNetworkMode.DTN_STORED -> AdaptiveNetworkBannerState(
                bannerText = "DTN STORED · WILL FORWARD WHEN ROUTE RETURNS",
                badgeLabel = "STORED",
                mode = mode,
                isVisible = true
            )
            AdaptiveNetworkMode.UNKNOWN -> AdaptiveNetworkBannerState(
                bannerText = "NETWORK STATUS UNKNOWN",
                badgeLabel = "UNKNOWN",
                mode = mode,
                isVisible = true
            )
        }

        // 3. Build Adaptive Composer State
        val composer = when (mode) {
            AdaptiveNetworkMode.HEALTHY -> AdaptiveComposerState(
                pttButtonLabel = "HOLD TO TALK",
                pttButtonSubtext = "Real-time transmission",
                actionPillText = "DIRECT",
                canTransmitImmediately = true,
                deliveryExpectationText = "Direct link confirmed. Transmissions deliver in real-time."
            )
            AdaptiveNetworkMode.LIMITED -> AdaptiveComposerState(
                pttButtonLabel = "HOLD TO TALK",
                pttButtonSubtext = "Broadcast reachability",
                actionPillText = "BROADCAST",
                canTransmitImmediately = true,
                deliveryExpectationText = "Reduced reachability: Sending via broadcast. Delivery unconfirmed until ACK received."
            )
            AdaptiveNetworkMode.DEGRADED -> AdaptiveComposerState(
                pttButtonLabel = "HOLD TO TALK",
                pttButtonSubtext = "Retries active",
                actionPillText = "RETRY",
                canTransmitImmediately = true,
                deliveryExpectationText = "Degraded link: Delivery retries active. Unconfirmed until ACK received."
            )
            AdaptiveNetworkMode.OFFLINE -> AdaptiveComposerState(
                pttButtonLabel = "QUEUE FOR DELIVERY",
                pttButtonSubtext = "Local DTN storage (Offline)",
                actionPillText = "LOCAL QUEUE",
                canTransmitImmediately = false,
                isOffline = true,
                emergencyPriorityNotice = "Distress will queue at Priority 1 and burst on reconnection",
                deliveryExpectationText = "Radio offline: Message will be held in local DTN storage until a transport connects."
            )
            AdaptiveNetworkMode.CONGESTED -> AdaptiveComposerState(
                pttButtonLabel = "HOLD TO TALK (QUEUED)",
                pttButtonSubtext = "QoS queue backpressure",
                actionPillText = "BUFFERED",
                canTransmitImmediately = false,
                isCongested = true,
                emergencyPriorityNotice = "EMERGENCY DISTRESS PRE-EMPTS CONGESTION QUEUE (PRIORITY 1)",
                deliveryExpectationText = "QoS backpressure: Normal messages delayed. Emergency alerts pre-empt immediately."
            )
            AdaptiveNetworkMode.WAITING_FOR_ROUTE -> AdaptiveComposerState(
                pttButtonLabel = "RETAIN UNTIL ROUTE",
                pttButtonSubtext = "Awaiting route discovery",
                actionPillText = "WAIT ROUTE",
                canTransmitImmediately = false,
                isWaitingRoute = true,
                deliveryExpectationText = "Destination unreachable: Message retained locally pending MANET route resolution."
            )
            AdaptiveNetworkMode.DTN_STORED -> AdaptiveComposerState(
                pttButtonLabel = "STORE & FORWARD (DTN)",
                pttButtonSubtext = "Buffered with 10m TTL",
                actionPillText = "DTN STORE",
                canTransmitImmediately = false,
                isDtnStored = true,
                deliveryExpectationText = "DTN store active: Bundle stored locally. Will auto-forward on next encounter."
            )
            AdaptiveNetworkMode.UNKNOWN -> AdaptiveComposerState(
                pttButtonLabel = "HOLD TO TALK",
                canTransmitImmediately = true,
                deliveryExpectationText = "Network status unconfirmed."
            )
        }

        val routeSummary = when (headerState.routeState) {
            ChatRouteState.CONNECTED_DIRECT -> "Direct Line-of-Sight · 1 Hop"
            ChatRouteState.CONNECTED_RELAYED -> "Relayed via Node #${headerState.relayNodeId ?: "?"} · ${headerState.hopCount} Hops"
            ChatRouteState.RECENTLY_HEARD -> "Recently Heard · No Active Route"
            ChatRouteState.DTN_STORED -> "Stored in Local DTN Buffer"
            ChatRouteState.DISCONNECTED -> "No Confirmed Route · Offline / Broadcast"
        }

        return AdaptiveNetworkUiState(
            mode = mode,
            banner = banner,
            composer = composer,
            peerId = headerState.peerId,
            routeSummary = routeSummary,
            overallHealthStatus = overallStatus
        )
    }
}
