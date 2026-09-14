package org.sih.itantra.core.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.diagnostics.DiagnosticsState
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.mesh.TopologyNodeRole
import org.sih.itantra.core.mesh.TopologyNodeState
import org.sih.itantra.core.mesh.TopologyRoute
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.qos.CongestionState
import org.sih.itantra.core.transport.PeerDevice
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType

class CommunicationHealthMapperTest {

    private fun createMessage(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
        direction: MessageDirection = MessageDirection.SENT,
        priority: MessagePriority = MessagePriority.NORMAL,
        deliveryStatus: DeliveryStatus = DeliveryStatus.DELIVERED,
        isRelayed: Boolean = false,
        transferId: Short? = null,
        peer: String = "Node #209071"
    ): MessageRecord = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = direction,
        language = IndicLanguage.ENGLISH,
        priority = priority,
        text = "Test packet message $id",
        peer = peer,
        packetSizeBytes = 128,
        rawAudioEquivalentBytes = 0L,
        measuredLatencyMs = 22.0,
        isRelayed = isRelayed,
        hopCount = if (isRelayed) 2 else 1,
        deliveryStatus = deliveryStatus,
        transferId = transferId
    )

    private fun createNode(
        nodeId: Int,
        displayName: String,
        isLocal: Boolean = false,
        isReachable: Boolean = true,
        hopCount: Int = 1,
        role: TopologyNodeRole = TopologyNodeRole.NEIGHBOR
    ): TopologyNode = TopologyNode(
        nodeId = nodeId,
        displayName = displayName,
        isLocal = isLocal,
        isReachable = isReachable,
        lastSeen = "12:00:00",
        lastSeenMs = System.currentTimeMillis(),
        hopCount = hopCount,
        transport = "Wi-Fi UDP",
        routeState = "ACTIVE",
        role = role,
        state = if (isReachable) TopologyNodeState.ONLINE else TopologyNodeState.OFFLINE
    )

    private fun createRoute(
        destNodeId: Int,
        nextHopId: Int,
        hopCount: Int = 1
    ): TopologyRoute = TopologyRoute(
        destinationNodeId = destNodeId,
        nextHopNodeId = nextHopId,
        hopCount = hopCount,
        routeFreshness = System.currentTimeMillis(),
        transport = "Wi-Fi UDP",
        state = "ACTIVE"
    )

    private fun createTopology(
        nodes: List<TopologyNode> = emptyList(),
        routes: List<TopologyRoute> = emptyList()
    ): MeshTopologySnapshot = MeshTopologySnapshot(
        timestamp = System.currentTimeMillis(),
        nodes = nodes,
        routes = routes,
        activeTransport = "Wi-Fi UDP",
        congestionState = "NORMAL",
        dtnPendingCount = 0
    )

    @Test
    fun testHealthyState_bothTransportsUpAndRoutesAvailable() {
        val nodes = listOf(
            createNode(1001, "ALPHA", isLocal = true, hopCount = 0, role = TopologyNodeRole.LOCAL),
            createNode(1002, "BRAVO", isLocal = false, hopCount = 1, role = TopologyNodeRole.NEIGHBOR),
            createNode(1003, "CHARLIE", isLocal = false, hopCount = 2, role = TopologyNodeRole.DESTINATION)
        )
        val routes = listOf(
            createRoute(1003, 1002, 2)
        )
        val topology = createTopology(nodes, routes)
        val diagnostics = DiagnosticsState()

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = diagnostics,
            topology = topology,
            messageHistory = emptyList(),
            wifiState = TransportState.CONNECTED,
            wifiPeers = listOf(PeerDevice("1002", "BRAVO", "192.168.49.2", TransportType.WIFI, true)),
            bluetoothState = TransportState.CONNECTED,
            bluetoothPeers = listOf(PeerDevice("1002", "BRAVO-BT", "AA:BB:CC:DD:EE:01", TransportType.BLUETOOTH, true))
        )

        assertEquals(OverallHealthStatus.HEALTHY, health.overallStatus)
        assertEquals("2 TRANSPORTS READY", health.overallBadge)
        assertTrue(health.overallSummary.contains("Mesh routing active") || health.overallSummary.contains("reachable peer"))
        assertEquals(1001, health.localNodeId)

        // Verify truthful metrics
        val wifiItem = health.transports.first { it.type == TransportType.WIFI }
        val btItem = health.transports.first { it.type == TransportType.BLUETOOTH }
        assertEquals("NOT MEASURED", wifiItem.signalDbm)
        assertEquals("NOT MEASURED", btItem.signalDbm)
        assertEquals("CONNECTED", wifiItem.displayState)
        assertTrue(btItem.displayState.startsWith("CONNECTED"))
    }

    @Test
    fun testLimitedState_singleTransportActiveAndNoPeers() {
        val topology = createTopology()
        val diagnostics = DiagnosticsState()

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = diagnostics,
            topology = topology,
            messageHistory = emptyList(),
            wifiState = TransportState.CONNECTED,
            wifiPeers = emptyList(),
            bluetoothState = TransportState.DISCONNECTED
        )

        assertEquals(OverallHealthStatus.LIMITED, health.overallStatus)
        assertEquals("WI-FI ONLY (NO PEERS)", health.overallBadge)
        assertTrue(health.overallSummary.contains("single-transport mode") || health.overallSummary.contains("direct peers"))
    }

    @Test
    fun testDegradedState_qosCongestion() {
        val nodes = listOf(
            createNode(1001, "ALPHA", isLocal = true, hopCount = 0, role = TopologyNodeRole.LOCAL),
            createNode(1002, "BRAVO", isLocal = false, hopCount = 1, role = TopologyNodeRole.NEIGHBOR)
        )
        val topology = createTopology(nodes)
        val diagnostics = DiagnosticsState(congestionState = "CONGESTED", queuedPackets = 42)

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = diagnostics,
            topology = topology,
            messageHistory = emptyList(),
            wifiState = TransportState.CONNECTED,
            bluetoothState = TransportState.CONNECTED,
            qosCongestion = CongestionState.CONGESTED,
            qosQueuedPackets = 42
        )

        assertEquals(OverallHealthStatus.DEGRADED, health.overallStatus)
        assertEquals("CONGESTED", health.overallBadge)
        assertTrue(health.overallSummary.contains("backpressure", ignoreCase = true) || health.overallSummary.contains("buffering", ignoreCase = true))
        assertEquals(CongestionState.CONGESTED, health.qosHealth.congestionState)
        assertEquals(42, health.qosHealth.queuedPackets)
    }

    @Test
    fun testDegradedState_dtnQueueBackpressure() {
        val nodes = listOf(
            createNode(1001, "ALPHA", isLocal = true, hopCount = 0, role = TopologyNodeRole.LOCAL),
            createNode(1002, "BRAVO", isLocal = false, hopCount = 1, role = TopologyNodeRole.NEIGHBOR)
        )
        val topology = createTopology(nodes)
        val diagnostics = DiagnosticsState(dtnQueueSize = 30)

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = diagnostics,
            topology = topology,
            messageHistory = emptyList(),
            wifiState = TransportState.CONNECTED,
            bluetoothState = TransportState.CONNECTED,
            dtnQueueSize = 30
        )

        assertEquals(OverallHealthStatus.DEGRADED, health.overallStatus)
        assertEquals("ELEVATED DTN / TIMEOUTS", health.overallBadge)
        assertEquals(30, health.dtnHealth.queueSize)
    }

    @Test
    fun testDegradedState_highDeliveryFailures() {
        val nodes = listOf(
            createNode(1001, "ALPHA", isLocal = true, hopCount = 0, role = TopologyNodeRole.LOCAL),
            createNode(1002, "BRAVO", isLocal = false, hopCount = 1, role = TopologyNodeRole.NEIGHBOR)
        )
        val topology = createTopology(nodes)
        val diagnostics = DiagnosticsState()

        val messages = listOf(
            createMessage("1", deliveryStatus = DeliveryStatus.TIMEOUT),
            createMessage("2", deliveryStatus = DeliveryStatus.TIMEOUT),
            createMessage("3", deliveryStatus = DeliveryStatus.TIMEOUT)
        )

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = diagnostics,
            topology = topology,
            messageHistory = messages,
            wifiState = TransportState.CONNECTED,
            bluetoothState = TransportState.CONNECTED
        )

        assertEquals(OverallHealthStatus.DEGRADED, health.overallStatus)
        assertEquals(3, health.deliveryHealth.failedCount)
        assertEquals(0, health.deliveryHealth.acknowledgedCount)
    }

    @Test
    fun testOfflineState_bothTransportsDisconnected() {
        val topology = createTopology()
        val diagnostics = DiagnosticsState()

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = diagnostics,
            topology = topology,
            messageHistory = emptyList(),
            wifiState = TransportState.DISCONNECTED,
            bluetoothState = TransportState.DISCONNECTED
        )

        assertEquals(OverallHealthStatus.OFFLINE, health.overallStatus)
        assertEquals("0 TRANSPORTS UP", health.overallBadge)
        assertTrue(health.overallSummary.contains("offline", ignoreCase = true) || health.overallSummary.contains("disconnected", ignoreCase = true))

        assertEquals(TransportState.DISCONNECTED, health.transports.first { it.type == TransportType.WIFI }.state)
        assertEquals(TransportState.DISCONNECTED, health.transports.first { it.type == TransportType.BLUETOOTH }.state)
    }

    @Test
    fun testTruthfulRfSignalMetrics_neverFabricated() {
        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = DiagnosticsState(),
            topology = createTopology(),
            messageHistory = emptyList(),
            wifiState = TransportState.CONNECTED,
            bluetoothState = TransportState.CONNECTED
        )

        for (t in health.transports) {
            assertEquals("NOT MEASURED", t.signalDbm)
            assertFalse(t.signalDbm.contains("-"))
            assertFalse(t.signalDbm.contains("dBm"))
        }
    }

    @Test
    fun testDeliveryHealthAggregation_feature6States() {
        val messages = listOf(
            // ACKNOWLEDGED (delivered)
            createMessage("msg1", deliveryStatus = DeliveryStatus.DELIVERED),
            // ACK_PENDING (sent, in transit)
            createMessage("msg2", deliveryStatus = DeliveryStatus.PENDING, transferId = 101.toShort()),
            // RELAYED (received with hopCount > 1)
            createMessage("msg3", direction = MessageDirection.RECEIVED, isRelayed = true),
            // DTN_STORED (pending with transferId and isRelayed = true)
            createMessage("msg4", deliveryStatus = DeliveryStatus.PENDING, transferId = 102.toShort(), isRelayed = true),
            // FAILED (timeout)
            createMessage("msg5", deliveryStatus = DeliveryStatus.TIMEOUT)
        )

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = DiagnosticsState(lastDeliveryAckLatencyMs = 45.0),
            topology = createTopology(),
            messageHistory = messages,
            wifiState = TransportState.CONNECTED,
            bluetoothState = TransportState.CONNECTED
        )

        val delivery = health.deliveryHealth
        assertEquals(5, delivery.totalMessagesCount)
        assertEquals(1, delivery.acknowledgedCount) // msg1 is DELIVERED
        assertEquals(1, delivery.ackPendingCount)   // msg2 is PENDING with transferId
        assertEquals(1, delivery.relayedCount)      // msg3 is RECEIVED + isRelayed
        assertEquals(1, delivery.dtnStoredCount)    // msg4 is DTN_STORED
        assertEquals(1, delivery.failedCount)       // msg5 is TIMEOUT -> FAILED
        assertEquals(45.0, delivery.lastDeliveryAckLatencyMs ?: 0.0, 0.001)

        // Success rate: 1 ACK / (1 ACK + 1 FAILED) = 50.0%
        assertNotNull(delivery.deliverySuccessRatePercent)
        assertEquals(50.0, delivery.deliverySuccessRatePercent!!, 0.5)
    }

    @Test
    fun testRouteHealthMapping() {
        val nodes = listOf(
            createNode(1001, "ALPHA", isLocal = true, hopCount = 0, role = TopologyNodeRole.LOCAL),
            createNode(1002, "BRAVO", isLocal = false, hopCount = 1, role = TopologyNodeRole.NEIGHBOR),
            createNode(1003, "CHARLIE", isLocal = false, hopCount = 2, role = TopologyNodeRole.DESTINATION),
            createNode(1004, "DELTA", isLocal = false, isReachable = false, hopCount = 3, role = TopologyNodeRole.DESTINATION)
        )
        val routes = listOf(
            createRoute(1003, 1002, 2),
            createRoute(1002, 1002, 1)
        )
        val topology = createTopology(nodes, routes)

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = DiagnosticsState(packetsRelayed = 14L),
            topology = topology,
            messageHistory = emptyList(),
            wifiState = TransportState.CONNECTED,
            bluetoothState = TransportState.CONNECTED,
            isRelayEnabled = true
        )

        val routeHealth = health.routeHealth
        assertEquals(2, routeHealth.activeRoutesCount)
        assertEquals(2, routeHealth.reachableDestinationsCount) // BRAVO and CHARLIE are reachable
        assertEquals(3, routeHealth.knownNodesCount) // BRAVO, CHARLIE, DELTA
        assertEquals(1, routeHealth.activeNeighborsCount) // BRAVO (hopCount == 1)
        assertTrue(routeHealth.isRelayEnabled)
        assertEquals(14L, routeHealth.packetsRelayed)
    }

    @Test
    fun testDtnAndQosTelemetryMapping() {
        val diagnostics = DiagnosticsState(
            queuedPackets = 18,
            queuedDistress = 2,
            queuedAlert = 4,
            queuedImportant = 6,
            queuedNormal = 6,
            congestionState = "BUSY",
            dtnQueueSize = 12,
            dtnStored = 40L,
            dtnForwarded = 25L,
            dtnExpired = 3L,
            dtnDropped = 1L
        )

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = diagnostics,
            topology = createTopology(),
            messageHistory = emptyList(),
            wifiState = TransportState.CONNECTED,
            bluetoothState = TransportState.CONNECTED,
            qosQueuedPackets = 18,
            qosDistress = 2,
            qosAlert = 4,
            qosImportant = 6,
            qosNormal = 6,
            dtnQueueSize = 12
        )

        // QoS
        assertEquals(18, health.qosHealth.queuedPackets)
        assertEquals(2, health.qosHealth.queuedDistress)
        assertEquals(4, health.qosHealth.queuedAlert)
        assertEquals(6, health.qosHealth.queuedImportant)
        assertEquals(6, health.qosHealth.queuedNormal)
        assertEquals(CongestionState.BUSY, health.qosHealth.congestionState)

        // DTN
        assertEquals(12, health.dtnHealth.queueSize)
        assertEquals(50, health.dtnHealth.maxCapacity)
        assertEquals(40L, health.dtnHealth.totalStoredPackets)
        assertEquals(25L, health.dtnHealth.totalForwardedPackets)
        assertEquals(3L, health.dtnHealth.totalExpiredPackets)
        assertEquals(1L, health.dtnHealth.totalDroppedPackets)
        assertTrue(health.dtnHealth.isActivelyUsed)
    }

    @Test
    fun testRecentEventsTimeline_formattingAndSorting() {
        val now = System.currentTimeMillis()
        val messages = listOf(
            createMessage("1", timestamp = now - 5000, priority = MessagePriority.NORMAL, deliveryStatus = DeliveryStatus.DELIVERED, peer = "Node #1002"),
            createMessage("2", timestamp = now - 2000, priority = MessagePriority.DISTRESS, deliveryStatus = DeliveryStatus.PENDING, transferId = 10.toShort(), isRelayed = true, peer = "Node #1003"),
            createMessage("3", timestamp = now - 1000, priority = MessagePriority.ALERT, deliveryStatus = DeliveryStatus.TIMEOUT, peer = "Node #1004")
        )

        val health = CommunicationHealthMapper.map(
            localNodeId = 1001,
            diagnostics = DiagnosticsState(),
            topology = createTopology(),
            messageHistory = messages,
            wifiState = TransportState.CONNECTED,
            bluetoothState = TransportState.CONNECTED
        )

        val events = health.recentEvents
        assertEquals(3, events.size)

        // Message 1
        assertEquals("1", events[0].id)
        assertEquals("OUTBOUND", events[0].type)
        assertEquals(RadioDeliveryState.ACKNOWLEDGED, events[0].deliveryState)
        assertFalse(events[0].isEmergency)

        // Message 2
        assertEquals("2", events[1].id)
        assertEquals("DISTRESS", events[1].type)
        assertEquals(RadioDeliveryState.DTN_STORED, events[1].deliveryState)
        assertTrue(events[1].isEmergency)

        // Message 3
        assertEquals("3", events[2].id)
        assertEquals("DISTRESS", events[2].type)
        assertEquals(RadioDeliveryState.FAILED, events[2].deliveryState)
        assertTrue(events[2].isEmergency)
    }
}
