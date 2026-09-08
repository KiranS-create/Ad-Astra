package org.sih.itantra.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.chat.ChatDeliveryStatus
import org.sih.itantra.core.chat.ChatRepository
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.mesh.TopologyNodeRole
import org.sih.itantra.core.mesh.TopologyNodeState
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsHomeTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MessageHistoryStore.clear()
    }

    @After
    fun tearDown() {
        MessageHistoryStore.clear()
    }

    private fun createRecord(
        id: String = "msg-1",
        timestamp: Long = System.currentTimeMillis(),
        direction: MessageDirection = MessageDirection.SENT,
        language: IndicLanguage = IndicLanguage.HINDI,
        priority: MessagePriority = MessagePriority.NORMAL,
        text: String = "Test transmission",
        peer: String = "Broadcast",
        deliveryStatus: DeliveryStatus = DeliveryStatus.NONE,
        isRelayed: Boolean = false,
        hopCount: Int = 0,
        qosStatus: String? = null,
        transferId: Short? = null
    ) = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = direction,
        language = language,
        priority = priority,
        text = text,
        peer = peer,
        packetSizeBytes = 42,
        rawAudioEquivalentBytes = 96000L,
        measuredLatencyMs = 12.0,
        deliveryStatus = deliveryStatus,
        isRelayed = isRelayed,
        hopCount = hopCount,
        qosStatus = qosStatus,
        transferId = transferId
    )

    @Test
    fun testConversationAggregationFromRecords() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        val t1 = System.currentTimeMillis() - 5000L
        val t2 = System.currentTimeMillis() - 2000L
        val t3 = System.currentTimeMillis()

        // 2 messages from Node #477124
        MessageHistoryStore.addRecord(
            createRecord(id = "1", timestamp = t1, peer = "Node #477124", text = "First message from 477124", direction = MessageDirection.RECEIVED)
        )
        MessageHistoryStore.addRecord(
            createRecord(id = "2", timestamp = t2, peer = "Node #477124", text = "Second message from 477124", direction = MessageDirection.RECEIVED)
        )
        // 1 message to Broadcast
        MessageHistoryStore.addRecord(
            createRecord(id = "3", timestamp = t3, peer = "Broadcast", text = "Outbound broadcast", direction = MessageDirection.SENT)
        )

        advanceUntilIdle()

        val summaries = repo.conversations.value
        assertEquals(2, summaries.size)

        // Latest conversation should be Broadcast (t3)
        assertEquals("TACTICAL BROADCAST", summaries[0].displayName)
        assertEquals("Outbound broadcast", summaries[0].lastMessageText)
        assertEquals(t3, summaries[0].lastTimestamp)

        // Second conversation should be Node #477124 with latest text from t2
        assertEquals("NODE #477124", summaries[1].displayName)
        assertEquals("Second message from 477124", summaries[1].lastMessageText)
        assertEquals(477124, summaries[1].peerNodeId)
    }

    @Test
    fun testUnreadCountIncrementsAndClears() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        val t1 = System.currentTimeMillis() - 10000L
        val t2 = System.currentTimeMillis() - 5000L

        MessageHistoryStore.addRecord(
            createRecord(id = "1", timestamp = t1, peer = "Node #209070", text = "Inbound 1", direction = MessageDirection.RECEIVED)
        )
        MessageHistoryStore.addRecord(
            createRecord(id = "2", timestamp = t2, peer = "Node #209070", text = "Inbound 2", direction = MessageDirection.RECEIVED)
        )
        // Outbound sent message should not increment unread
        MessageHistoryStore.addRecord(
            createRecord(id = "3", timestamp = t2 + 1000L, peer = "Node #209070", text = "Outbound ACK", direction = MessageDirection.SENT)
        )

        advanceUntilIdle()

        var conv = repo.conversations.value.first { it.peerNodeId == 209070 }
        assertEquals("Unread count should be 2 for 2 received messages", 2, conv.unreadCount)

        // Mark as read
        repo.markConversationAsRead("Node #209070")
        advanceUntilIdle()

        conv = repo.conversations.value.first { it.peerNodeId == 209070 }
        assertEquals("Unread count should clear to 0 after markConversationAsRead", 0, conv.unreadCount)
    }

    @Test
    fun testRadioAwareDeliveryStatusMapping() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        // 1. Sent with Delivered ACK
        MessageHistoryStore.addRecord(
            createRecord(id = "1", peer = "Node #101", direction = MessageDirection.SENT, deliveryStatus = DeliveryStatus.DELIVERED)
        )
        advanceUntilIdle()
        assertEquals(ChatDeliveryStatus.ACK, repo.conversations.value.first { it.peerNodeId == 101 }.deliveryStatus)

        // 2. Sent pending transfer
        MessageHistoryStore.addRecord(
            createRecord(id = "2", peer = "Node #102", direction = MessageDirection.SENT, deliveryStatus = DeliveryStatus.PENDING)
        )
        advanceUntilIdle()
        assertEquals(ChatDeliveryStatus.TRANSMITTING, repo.conversations.value.first { it.peerNodeId == 102 }.deliveryStatus)

        // 3. Sent timeout failed
        MessageHistoryStore.addRecord(
            createRecord(id = "3", peer = "Node #103", direction = MessageDirection.SENT, deliveryStatus = DeliveryStatus.TIMEOUT)
        )
        advanceUntilIdle()
        assertEquals(ChatDeliveryStatus.FAILED, repo.conversations.value.first { it.peerNodeId == 103 }.deliveryStatus)

        // 4. Sent with Congestion Queued status
        MessageHistoryStore.addRecord(
            createRecord(id = "4", peer = "Node #104", direction = MessageDirection.SENT, qosStatus = "QUEUED")
        )
        advanceUntilIdle()
        assertEquals(ChatDeliveryStatus.QUEUED, repo.conversations.value.first { it.peerNodeId == 104 }.deliveryStatus)

        // 5. Received standard
        MessageHistoryStore.addRecord(
            createRecord(id = "5", peer = "Node #105", direction = MessageDirection.RECEIVED)
        )
        advanceUntilIdle()
        assertEquals(ChatDeliveryStatus.RECEIVED, repo.conversations.value.first { it.peerNodeId == 105 }.deliveryStatus)

        // 6. Received via multi-hop relay
        MessageHistoryStore.addRecord(
            createRecord(id = "6", peer = "Node #106", direction = MessageDirection.RECEIVED, isRelayed = true, hopCount = 2)
        )
        advanceUntilIdle()
        assertEquals(ChatDeliveryStatus.RELAYED, repo.conversations.value.first { it.peerNodeId == 106 }.deliveryStatus)
    }

    @Test
    fun testRouteStateResolutionWithTopology() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        val activeDirectNode = TopologyNode(
            nodeId = 201,
            displayName = "Node #201",
            isLocal = false,
            isReachable = true,
            lastSeen = "Just now",
            lastSeenMs = System.currentTimeMillis(),
            hopCount = 1,
            transport = "Wi-Fi",
            routeState = "DIRECT",
            role = TopologyNodeRole.NEIGHBOR,
            state = TopologyNodeState.ONLINE
        )

        val activeRelayedNode = TopologyNode(
            nodeId = 202,
            displayName = "Node #202",
            isLocal = false,
            isReachable = true,
            lastSeen = "Just now",
            lastSeenMs = System.currentTimeMillis(),
            hopCount = 3,
            transport = "Mesh Relay",
            routeState = "RELAY",
            role = TopologyNodeRole.DESTINATION,
            state = TopologyNodeState.RELAY
        )

        repo.updateTopology(
            MeshTopologySnapshot(
                nodes = listOf(activeDirectNode, activeRelayedNode)
            )
        )

        MessageHistoryStore.addRecord(
            createRecord(id = "1", peer = "Node #201", text = "Direct ping")
        )
        MessageHistoryStore.addRecord(
            createRecord(id = "2", peer = "Node #202", text = "Relayed ping")
        )
        MessageHistoryStore.addRecord(
            createRecord(id = "3", peer = "Node #203", timestamp = System.currentTimeMillis() - 700_000L, text = "Old stale ping")
        )

        advanceUntilIdle()

        val c201 = repo.conversations.value.first { it.peerNodeId == 201 }
        assertEquals(ChatRouteState.CONNECTED_DIRECT, c201.routeState)
        assertEquals(1, c201.hopCount)

        val c202 = repo.conversations.value.first { it.peerNodeId == 202 }
        assertEquals(ChatRouteState.CONNECTED_RELAYED, c202.routeState)
        assertEquals(3, c202.hopCount)

        val c203 = repo.conversations.value.first { it.peerNodeId == 203 }
        assertEquals(ChatRouteState.DISCONNECTED, c203.routeState)
    }

    @Test
    fun testSearchFilterByNodeNameAndMessageSnippet() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        MessageHistoryStore.addRecord(
            createRecord(id = "1", peer = "Node #5555", text = "Alpha perimeter is all clear.")
        )
        MessageHistoryStore.addRecord(
            createRecord(id = "2", peer = "Node #7777", text = "Squad Bravo holding checkpoint Charlie.")
        )

        advanceUntilIdle()
        assertEquals(2, repo.filteredConversations.value.size)

        // Filter by node ID
        repo.setSearchQuery("5555")
        advanceUntilIdle()
        val filteredNode = repo.filteredConversations.value
        assertEquals(1, filteredNode.size)
        assertEquals(5555, filteredNode[0].peerNodeId)

        // Filter by message substring
        repo.setSearchQuery("Bravo")
        advanceUntilIdle()
        val filteredMsg = repo.filteredConversations.value
        assertEquals(1, filteredMsg.size)
        assertEquals(7777, filteredMsg[0].peerNodeId)

        // Filter with no match
        repo.setSearchQuery("nonexistent")
        advanceUntilIdle()
        assertTrue(repo.filteredConversations.value.isEmpty())

        // Clear filter
        repo.setSearchQuery("")
        advanceUntilIdle()
        assertEquals(2, repo.filteredConversations.value.size)
    }

    @Test
    fun testEmergencyDistressFlagging() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        MessageHistoryStore.addRecord(
            createRecord(
                id = "1",
                peer = "Node #999",
                text = "MEDEVAC NEEDED AT SECTOR 4",
                priority = MessagePriority.DISTRESS,
                timestamp = System.currentTimeMillis()
            )
        )

        advanceUntilIdle()

        val conv = repo.conversations.value.first()
        assertTrue("Conversation must be flagged as emergency", conv.isEmergency)
        assertEquals(MessagePriority.DISTRESS, conv.priority)
    }

    @Test
    fun testNavigationIntegrityInMainActivityAndBottomNavBar() {
        val mainFile = File("src/main/java/org/sih/itantra/presentation/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", mainFile.exists())
        val mainText = mainFile.readText()

        assertTrue("MainActivity must route RadioNavTab.CHATS", mainText.contains("RadioNavTab.CHATS -> ChatsHomeScreen"))
        assertTrue("MainActivity must route RadioNavTab.RADIO", mainText.contains("RadioNavTab.RADIO -> MainTransceiverScreen"))
        assertTrue("MainActivity must route RadioNavTab.DIAGNOSTICS", mainText.contains("RadioNavTab.DIAGNOSTICS -> DiagnosticsScreen"))
        assertTrue("MainActivity must route RadioNavTab.SETTINGS", mainText.contains("RadioNavTab.SETTINGS -> SettingsScreen"))

        val navFile = File("src/main/java/org/sih/itantra/presentation/components/BottomNavBar.kt")
        assertTrue("BottomNavBar.kt must exist", navFile.exists())
        val navText = navFile.readText()

        assertTrue("BottomNavBar must declare RadioNavTab.CHATS", navText.contains("CHATS(\"Chats\""))
        assertTrue("BottomNavBar visible tabs must include CHATS", navText.contains("RadioNavTab.CHATS"))
    }
}
