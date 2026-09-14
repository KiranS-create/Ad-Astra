package org.sih.itantra.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.chat.IndividualChatHeaderState
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.health.CommunicationHealthState
import org.sih.itantra.core.health.DeliveryHealthSummary
import org.sih.itantra.core.health.DtnHealthState
import org.sih.itantra.core.health.OverallHealthStatus
import org.sih.itantra.core.health.QosHealthState
import org.sih.itantra.core.health.RouteHealthState
import org.sih.itantra.core.health.TransportHealthItem
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.qos.CongestionState
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType

/**
 * Feature 14: Comprehensive unit tests for [AdaptiveNetworkUiMapper].
 *
 * Verifies:
 * 1. Canonical presentation delivery labels (SCHEDULED, TRANSMITTING, AWAITING ACK, DELIVERED / ACK, etc.)
 * 2. Deterministic mapping across all 7 network modes (HEALTHY, LIMITED, DEGRADED, OFFLINE, CONGESTED, WAITING_FOR_ROUTE, DTN_STORED)
 * 3. Strict truthfulness: Never claim sent/delivered when queued or offline
 * 4. Distinct states: WAITING_FOR_ROUTE vs FAILED; DTN_STORED vs ACKNOWLEDGED
 * 5. Emergency priority discoverability and bypass under degraded/congested/offline conditions
 */
class AdaptiveNetworkUiMapperTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createHealthState(
        status: OverallHealthStatus = OverallHealthStatus.HEALTHY,
        congestionState: CongestionState = CongestionState.NORMAL,
        queuedPackets: Int = 0,
        dtnQueueSize: Int = 0,
        transports: List<TransportHealthItem> = listOf(
            TransportHealthItem(
                name = "Wi-Fi Direct",
                type = TransportType.WIFI,
                state = TransportState.CONNECTED,
                displayState = "CONNECTED",
                connectedPeerCount = 2,
                details = "Channel 6",
                isPrimary = true
            )
        )
    ): CommunicationHealthState {
        return CommunicationHealthState(
            localNodeId = 1001,
            overallStatus = status,
            overallBadge = status.label,
            overallSummary = "System operational",
            transports = transports,
            routeHealth = RouteHealthState(
                activeRoutesCount = 2,
                reachableDestinationsCount = 2,
                knownNodesCount = 3,
                activeNeighborsCount = 2,
                isRelayEnabled = true,
                packetsRelayed = 5,
                preferredRouteSummary = "Direct",
                routeQualityLabel = "STRONG"
            ),
            deliveryHealth = DeliveryHealthSummary(
                totalMessagesCount = 10,
                acknowledgedCount = 8,
                ackPendingCount = 1,
                relayedCount = 1,
                dtnStoredCount = dtnQueueSize,
                waitingRouteCount = 0,
                failedCount = 0
            ),
            dtnHealth = DtnHealthState(
                queueSize = dtnQueueSize,
                totalStoredPackets = dtnQueueSize.toLong(),
                totalForwardedPackets = 0,
                totalExpiredPackets = 0,
                totalDroppedPackets = 0,
                isActivelyUsed = dtnQueueSize > 0
            ),
            qosHealth = QosHealthState(
                congestionState = congestionState,
                queuedPackets = queuedPackets,
                queuedDistress = 0,
                queuedAlert = 0,
                queuedImportant = 0,
                queuedNormal = queuedPackets,
                emergencyPreemptions = 0,
                normalStarvationAvoidance = 0,
                queueOverflows = 0
            ),
            recentEvents = emptyList()
        )
    }

    private fun createHeaderState(
        peerId: String = "peer-2002",
        routeState: ChatRouteState = ChatRouteState.CONNECTED_DIRECT,
        hopCount: Int = 1,
        relayNodeId: Int? = null
    ): IndividualChatHeaderState {
        return IndividualChatHeaderState(
            peerId = peerId,
            displayName = "BRAVO",
            peerNodeId = 2002,
            routeState = routeState,
            hopCount = hopCount,
            transport = "Wi-Fi UDP",
            relayNodeId = relayNodeId,
            isEmergency = false
        )
    }

    private fun createMessageRecord(
        deliveryStatus: DeliveryStatus = DeliveryStatus.DELIVERED,
        isRelayed: Boolean = false,
        transferId: Short? = 0x0001
    ): MessageRecord {
        return MessageRecord(
            id = "msg-${System.nanoTime()}",
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.NORMAL,
            text = "Test packet",
            peer = "BRAVO",
            packetSizeBytes = 128,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 15.0,
            isRelayed = isRelayed,
            hopCount = if (isRelayed) 2 else 1,
            deliveryStatus = deliveryStatus,
            transferId = transferId
        )
    }

    // -------------------------------------------------------------------------
    // 1. Canonical Delivery Labels
    // -------------------------------------------------------------------------

    @Test
    fun testResolveAdaptiveDeliveryLabel_allCanonicalStates() {
        assertEquals("SCHEDULED", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.QUEUED))
        assertEquals("TRANSMITTING", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.SENDING))
        assertEquals("AWAITING ACK", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.ACK_PENDING))
        assertEquals("DELIVERED / ACK", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.ACKNOWLEDGED))
        assertEquals("RELAYED", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.RELAYED))
        assertEquals("STORED FOR FORWARDING", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.DTN_STORED))
        assertEquals("WAITING FOR ROUTE", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.WAITING_FOR_ROUTE))
        assertEquals("FAILED", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.FAILED))
        assertEquals("RECEIVED", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.RECEIVED))
        assertEquals("STATUS UNKNOWN", AdaptiveNetworkUiMapper.resolveAdaptiveDeliveryLabel(RadioDeliveryState.UNKNOWN))
    }

    // -------------------------------------------------------------------------
    // 2. HEALTHY Mode
    // -------------------------------------------------------------------------

    @Test
    fun testHealthyMode_directTransmission() {
        val health = createHealthState(status = OverallHealthStatus.HEALTHY)
        val header = createHeaderState(routeState = ChatRouteState.CONNECTED_DIRECT)

        val uiState = AdaptiveNetworkUiMapper.map(health, header)

        assertEquals(AdaptiveNetworkMode.HEALTHY, uiState.mode)
        assertTrue(uiState.banner.bannerText.contains("LINK HEALTHY"))
        assertEquals("NOMINAL", uiState.banner.badgeLabel)
        assertEquals("HOLD TO TALK", uiState.composer.pttButtonLabel)
        assertEquals("DIRECT", uiState.composer.actionPillText)
        assertTrue(uiState.composer.canTransmitImmediately)
        assertFalse(uiState.composer.isOffline)
        assertFalse(uiState.composer.isCongested)
        assertTrue(uiState.composer.deliveryExpectationText.contains("real-time"))
    }

    // -------------------------------------------------------------------------
    // 3. LIMITED Mode
    // -------------------------------------------------------------------------

    @Test
    fun testLimitedMode_broadcastReachability() {
        val health = createHealthState(status = OverallHealthStatus.LIMITED)
        val header = createHeaderState(routeState = ChatRouteState.RECENTLY_HEARD)

        val uiState = AdaptiveNetworkUiMapper.map(health, header)

        assertEquals(AdaptiveNetworkMode.LIMITED, uiState.mode)
        assertTrue(uiState.banner.bannerText.contains("LIMITED LINK"))
        assertEquals("LIMITED", uiState.banner.badgeLabel)
        assertEquals("BROADCAST", uiState.composer.actionPillText)
        assertTrue(uiState.composer.canTransmitImmediately)
        assertTrue(uiState.composer.deliveryExpectationText.contains("Unconfirmed until ACK", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // 4. DEGRADED Mode
    // -------------------------------------------------------------------------

    @Test
    fun testDegradedMode_retriesActive() {
        val health = createHealthState(status = OverallHealthStatus.DEGRADED)
        val header = createHeaderState(routeState = ChatRouteState.CONNECTED_DIRECT)

        val uiState = AdaptiveNetworkUiMapper.map(health, header)

        assertEquals(AdaptiveNetworkMode.DEGRADED, uiState.mode)
        assertTrue(uiState.banner.bannerText.contains("LINK DEGRADED"))
        assertEquals("DEGRADED", uiState.banner.badgeLabel)
        assertEquals("RETRY", uiState.composer.actionPillText)
        assertTrue(uiState.composer.deliveryExpectationText.contains("retries active", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // 5. OFFLINE Mode (Truthfulness & Emergency Priority)
    // -------------------------------------------------------------------------

    @Test
    fun testOfflineMode_strictTruthfulnessAndEmergencyPriority() {
        val health = createHealthState(
            status = OverallHealthStatus.OFFLINE,
            transports = listOf(
                TransportHealthItem(
                    name = "Wi-Fi Direct",
                    type = TransportType.WIFI,
                    state = TransportState.DISCONNECTED,
                    displayState = "OFFLINE",
                    connectedPeerCount = 0,
                    details = "Radio disabled"
                )
            )
        )
        val header = createHeaderState(routeState = ChatRouteState.DISCONNECTED)

        val uiState = AdaptiveNetworkUiMapper.map(health, header)

        assertEquals(AdaptiveNetworkMode.OFFLINE, uiState.mode)
        assertTrue(uiState.banner.bannerText.contains("OFFLINE · MESSAGES WILL BE QUEUED LOCALLY"))
        assertEquals("OFFLINE", uiState.banner.badgeLabel)
        assertEquals("QUEUE FOR DELIVERY", uiState.composer.pttButtonLabel)
        assertEquals("LOCAL QUEUE", uiState.composer.actionPillText)
        assertFalse(uiState.composer.canTransmitImmediately)
        assertTrue(uiState.composer.isOffline)

        // Truthfulness checks: must not claim sent or delivered
        assertFalse(uiState.composer.deliveryExpectationText.contains("sent", ignoreCase = true))
        assertFalse(uiState.composer.deliveryExpectationText.contains("delivered", ignoreCase = true))
        assertTrue(uiState.composer.deliveryExpectationText.contains("held in local DTN storage", ignoreCase = true))

        // Emergency Priority Bypass affordance
        assertNotNull(uiState.composer.emergencyPriorityNotice)
        assertTrue(uiState.composer.emergencyPriorityNotice!!.contains("Priority 1", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // 6. CONGESTED Mode (Queue Backpressure & Emergency Preemption)
    // -------------------------------------------------------------------------

    @Test
    fun testCongestedMode_queueBackpressureAndPreemption() {
        val health = createHealthState(
            status = OverallHealthStatus.HEALTHY,
            congestionState = CongestionState.CONGESTED,
            queuedPackets = 25
        )
        val header = createHeaderState(routeState = ChatRouteState.CONNECTED_DIRECT)

        val uiState = AdaptiveNetworkUiMapper.map(health, header)

        assertEquals(AdaptiveNetworkMode.CONGESTED, uiState.mode)
        assertTrue(uiState.banner.bannerText.contains("NETWORK CONGESTED · 25 PKTS QUEUED"))
        assertEquals("CONGESTED", uiState.banner.badgeLabel)
        assertEquals("HOLD TO TALK (QUEUED)", uiState.composer.pttButtonLabel)
        assertEquals("BUFFERED", uiState.composer.actionPillText)
        assertFalse(uiState.composer.canTransmitImmediately)
        assertTrue(uiState.composer.isCongested)

        // Emergency Pre-emption Callout
        assertNotNull(uiState.composer.emergencyPriorityNotice)
        assertTrue(uiState.composer.emergencyPriorityNotice!!.contains("PRE-EMPTS CONGESTION QUEUE", ignoreCase = true))
        assertTrue(uiState.composer.deliveryExpectationText.contains("Emergency alerts pre-empt immediately", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // 7. WAITING_FOR_ROUTE Mode (Distinguished from FAILED)
    // -------------------------------------------------------------------------

    @Test
    fun testWaitingForRouteMode_distinguishedFromFailed() {
        val health = createHealthState(status = OverallHealthStatus.HEALTHY)
        // Transports are up, but peer route is disconnected
        val header = createHeaderState(routeState = ChatRouteState.DISCONNECTED)

        val uiState = AdaptiveNetworkUiMapper.map(health, header)

        assertEquals(AdaptiveNetworkMode.WAITING_FOR_ROUTE, uiState.mode)
        assertEquals("WAITING FOR ROUTE · RETAINING MESSAGE", uiState.banner.bannerText)
        assertEquals("DISCOVERY", uiState.banner.badgeLabel)
        assertEquals("RETAIN UNTIL ROUTE", uiState.composer.pttButtonLabel)
        assertEquals("WAIT ROUTE", uiState.composer.actionPillText)
        assertFalse(uiState.composer.canTransmitImmediately)
        assertTrue(uiState.composer.isWaitingRoute)

        // Truthfulness: distinguished from FAILED — message retained pending route resolution
        assertFalse(uiState.composer.deliveryExpectationText.contains("failed", ignoreCase = true))
        assertTrue(uiState.composer.deliveryExpectationText.contains("Message retained locally pending MANET route resolution", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // 8. DTN_STORED Mode (Distinguished from ACKNOWLEDGED)
    // -------------------------------------------------------------------------

    @Test
    fun testDtnStoredMode_distinguishedFromAcknowledged() {
        val health = createHealthState(
            status = OverallHealthStatus.HEALTHY,
            dtnQueueSize = 3
        )
        val header = createHeaderState(routeState = ChatRouteState.DTN_STORED)

        val uiState = AdaptiveNetworkUiMapper.map(health, header)

        assertEquals(AdaptiveNetworkMode.DTN_STORED, uiState.mode)
        assertEquals("DTN STORED · WILL FORWARD WHEN ROUTE RETURNS", uiState.banner.bannerText)
        assertEquals("STORED", uiState.banner.badgeLabel)
        assertEquals("STORE & FORWARD (DTN)", uiState.composer.pttButtonLabel)
        assertEquals("DTN STORE", uiState.composer.actionPillText)
        assertFalse(uiState.composer.canTransmitImmediately)
        assertTrue(uiState.composer.isDtnStored)

        // Truthfulness: distinguished from ACKNOWLEDGED — bundle is buffered, not delivered
        assertFalse(uiState.composer.deliveryExpectationText.contains("delivered", ignoreCase = true))
        assertTrue(uiState.composer.deliveryExpectationText.contains("Bundle stored locally. Will auto-forward on next encounter.", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // 9. Route Summary
    // -------------------------------------------------------------------------

    @Test
    fun testRouteSummary_allRouteStates() {
        val health = createHealthState()

        val directState = AdaptiveNetworkUiMapper.map(health, createHeaderState(routeState = ChatRouteState.CONNECTED_DIRECT))
        assertEquals("Direct Line-of-Sight · 1 Hop", directState.routeSummary)

        val relayedState = AdaptiveNetworkUiMapper.map(
            health,
            createHeaderState(routeState = ChatRouteState.CONNECTED_RELAYED, hopCount = 3, relayNodeId = 1005)
        )
        assertEquals("Relayed via Node #1005 · 3 Hops", relayedState.routeSummary)

        val recentState = AdaptiveNetworkUiMapper.map(health, createHeaderState(routeState = ChatRouteState.RECENTLY_HEARD))
        assertEquals("Recently Heard · No Active Route", recentState.routeSummary)

        val dtnState = AdaptiveNetworkUiMapper.map(health, createHeaderState(routeState = ChatRouteState.DTN_STORED))
        assertEquals("Stored in Local DTN Buffer", dtnState.routeSummary)

        val discState = AdaptiveNetworkUiMapper.map(health, createHeaderState(routeState = ChatRouteState.DISCONNECTED))
        assertEquals("No Confirmed Route · Offline / Broadcast", discState.routeSummary)
    }

    // -------------------------------------------------------------------------
    // 10. Message History Triggering DTN / Route Waiting
    // -------------------------------------------------------------------------

    @Test
    fun testRecentMessageDtnTriggersDtnMode() {
        val health = createHealthState(status = OverallHealthStatus.HEALTHY, dtnQueueSize = 2)
        val header = createHeaderState(routeState = ChatRouteState.CONNECTED_DIRECT)
        // Message with pending + relayed maps to DTN_STORED in RadioMessageStateMapper
        val dtnMsg = createMessageRecord(
            deliveryStatus = DeliveryStatus.PENDING,
            isRelayed = true,
            transferId = 0x00A1
        )

        val uiState = AdaptiveNetworkUiMapper.map(
            healthState = health,
            headerState = header,
            recentMessages = listOf(dtnMsg)
        )

        assertEquals(AdaptiveNetworkMode.DTN_STORED, uiState.mode)
        assertEquals("STORED", uiState.banner.badgeLabel)
    }
}
