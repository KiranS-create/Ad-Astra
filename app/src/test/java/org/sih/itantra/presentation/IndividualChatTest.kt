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
import org.sih.itantra.core.protocol.GeoLocation
import org.sih.itantra.core.tts.TtsLanguage
import org.sih.itantra.core.tts.TtsResolutionResult
import org.sih.itantra.core.tts.TtsVoiceRegistry
import org.sih.itantra.core.tts.TtsVoiceResolver
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class IndividualChatTest {

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
        transferId: Short? = null,
        location: GeoLocation? = null,
        isSecure: Boolean = true,
        deliveryLatencyMs: Long? = null
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
        transferId = transferId,
        location = location,
        isSecure = isSecure,
        deliveryLatencyMs = deliveryLatencyMs
    )

    @Test
    fun testThreadGroupingByPeer() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        // Add records across different peers
        MessageHistoryStore.addRecord(createRecord(id = "1", peer = "Node #477124", text = "Msg 1 for 477124"))
        MessageHistoryStore.addRecord(createRecord(id = "2", peer = "Node #888888", text = "Msg for 888888"))
        MessageHistoryStore.addRecord(createRecord(id = "3", peer = "Node #477124", text = "Msg 2 for 477124"))
        MessageHistoryStore.addRecord(createRecord(id = "4", peer = "Broadcast", text = "Broadcast msg"))

        advanceUntilIdle()

        val thread477 = repo.getThreadMessages("Node #477124")
        assertEquals("Thread for Node #477124 must contain exactly 2 messages", 2, thread477.size)
        assertTrue(thread477.all { repo.normalizePeerId(it.peer) == "Node #477124" })

        val thread888 = repo.getThreadMessages("Node #888888")
        assertEquals("Thread for Node #888888 must contain exactly 1 message", 1, thread888.size)

        val threadBcast = repo.getThreadMessages("Broadcast")
        assertEquals("Thread for Broadcast must contain exactly 1 message", 1, threadBcast.size)
    }

    @Test
    fun testChronologicalOrdering() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        val baseTime = 1700000000000L
        val t1 = baseTime + 1000L
        val t2 = baseTime + 5000L
        val t3 = baseTime + 9000L

        // Added in arbitrary order
        MessageHistoryStore.addRecord(createRecord(id = "2", timestamp = t2, peer = "Node #123456", text = "Middle"))
        MessageHistoryStore.addRecord(createRecord(id = "3", timestamp = t3, peer = "Node #123456", text = "Latest"))
        MessageHistoryStore.addRecord(createRecord(id = "1", timestamp = t1, peer = "Node #123456", text = "Oldest"))

        advanceUntilIdle()

        val thread = repo.getThreadMessages("Node #123456")
        assertEquals(3, thread.size)
        assertEquals("Oldest", thread[0].text)
        assertEquals(t1, thread[0].timestamp)
        assertEquals("Middle", thread[1].text)
        assertEquals(t2, thread[1].timestamp)
        assertEquals("Latest", thread[2].text)
        assertEquals(t3, thread[2].timestamp)
    }

    @Test
    fun testIncomingOutgoingDirectionClassification() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        val t1 = 1700000000000L
        val t2 = 1700000001000L

        MessageHistoryStore.addRecord(
            createRecord(id = "1", timestamp = t1, peer = "Node #5050", direction = MessageDirection.RECEIVED, text = "Hello operator")
        )
        MessageHistoryStore.addRecord(
            createRecord(id = "2", timestamp = t2, peer = "Node #5050", direction = MessageDirection.SENT, text = "Roger, loud and clear")
        )

        advanceUntilIdle()

        val thread = repo.getThreadMessages("Node #5050")
        assertEquals(2, thread.size)
        assertEquals(MessageDirection.RECEIVED, thread[0].direction)
        assertEquals(MessageDirection.SENT, thread[1].direction)
    }

    @Test
    fun testOpeningConversationClearsUnread() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        val now = System.currentTimeMillis()
        MessageHistoryStore.addRecord(
            createRecord(id = "1", peer = "Node #6060", direction = MessageDirection.RECEIVED, timestamp = now - 2000L, text = "Unread 1")
        )
        MessageHistoryStore.addRecord(
            createRecord(id = "2", peer = "Node #6060", direction = MessageDirection.RECEIVED, timestamp = now - 1000L, text = "Unread 2")
        )

        advanceUntilIdle()

        val convBefore = repo.conversations.value.first { it.peerNodeId == 6060 }
        assertEquals(2, convBefore.unreadCount)

        // Opening conversation executes markConversationAsRead
        repo.markConversationAsRead("Node #6060")
        advanceUntilIdle()

        val convAfter = repo.conversations.value.first { it.peerNodeId == 6060 }
        assertEquals(0, convAfter.unreadCount)
    }

    @Test
    fun testDeliveryStatusRenderingAcrossStates() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        // SENT with ACK
        MessageHistoryStore.addRecord(
            createRecord(id = "1", peer = "Node #7001", direction = MessageDirection.SENT, deliveryStatus = DeliveryStatus.DELIVERED)
        )
        // SENT Transmitting
        MessageHistoryStore.addRecord(
            createRecord(id = "2", peer = "Node #7002", direction = MessageDirection.SENT, deliveryStatus = DeliveryStatus.PENDING)
        )
        // SENT Queued
        MessageHistoryStore.addRecord(
            createRecord(id = "3", peer = "Node #7003", direction = MessageDirection.SENT, qosStatus = "QUEUED")
        )
        // SENT Timeout / Failed
        MessageHistoryStore.addRecord(
            createRecord(id = "4", peer = "Node #7004", direction = MessageDirection.SENT, deliveryStatus = DeliveryStatus.TIMEOUT)
        )
        // RECEIVED Direct
        MessageHistoryStore.addRecord(
            createRecord(id = "5", peer = "Node #7005", direction = MessageDirection.RECEIVED, hopCount = 1)
        )
        // RECEIVED Relayed
        MessageHistoryStore.addRecord(
            createRecord(id = "6", peer = "Node #7006", direction = MessageDirection.RECEIVED, isRelayed = true, hopCount = 2)
        )

        advanceUntilIdle()

        assertEquals(ChatDeliveryStatus.ACK, repo.conversations.value.first { it.peerNodeId == 7001 }.deliveryStatus)
        assertEquals(ChatDeliveryStatus.TRANSMITTING, repo.conversations.value.first { it.peerNodeId == 7002 }.deliveryStatus)
        assertEquals(ChatDeliveryStatus.QUEUED, repo.conversations.value.first { it.peerNodeId == 7003 }.deliveryStatus)
        assertEquals(ChatDeliveryStatus.FAILED, repo.conversations.value.first { it.peerNodeId == 7004 }.deliveryStatus)
        assertEquals(ChatDeliveryStatus.RECEIVED, repo.conversations.value.first { it.peerNodeId == 7005 }.deliveryStatus)
        assertEquals(ChatDeliveryStatus.RELAYED, repo.conversations.value.first { it.peerNodeId == 7006 }.deliveryStatus)
    }

    @Test
    fun testRouteStateResolutionForOneToOnePeer() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        val directPeer = TopologyNode(
            nodeId = 8001,
            displayName = "Node #8001",
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

        val relayedPeer = TopologyNode(
            nodeId = 8002,
            displayName = "Node #8002",
            isLocal = false,
            isReachable = true,
            lastSeen = "Just now",
            lastSeenMs = System.currentTimeMillis(),
            hopCount = 2,
            transport = "Wi-Fi",
            routeState = "RELAY",
            role = TopologyNodeRole.DESTINATION,
            state = TopologyNodeState.RELAY
        )

        repo.updateTopology(MeshTopologySnapshot(nodes = listOf(directPeer, relayedPeer)))

        val headerDirect = repo.getHeaderState("Node #8001")
        assertEquals(ChatRouteState.CONNECTED_DIRECT, headerDirect.routeState)
        assertEquals(1, headerDirect.hopCount)

        val headerRelayed = repo.getHeaderState("Node #8002")
        assertEquals(ChatRouteState.CONNECTED_RELAYED, headerRelayed.routeState)
        assertEquals(2, headerRelayed.hopCount)

        val headerOffline = repo.getHeaderState("Node #8003")
        assertEquals(ChatRouteState.DISCONNECTED, headerOffline.routeState)
    }

    @Test
    fun testEmergencyDistressFlagAndLocationPresence() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        val loc = GeoLocation(latitude = 28.6139, longitude = 77.2090, accuracy = 5.0f, timestamp = System.currentTimeMillis(), altitude = 216.0)
        MessageHistoryStore.addRecord(
            createRecord(
                id = "em-1",
                peer = "Node #911",
                priority = MessagePriority.DISTRESS,
                text = "AMBULANCE REQUIRED AT SECTOR 7",
                location = loc,
                isSecure = true
            )
        )

        advanceUntilIdle()

        val thread = repo.getThreadMessages("Node #911")
        assertEquals(1, thread.size)
        val msg = thread[0]
        assertEquals(MessagePriority.DISTRESS, msg.priority)
        assertNotNull(msg.location)
        assertEquals(28.6139, msg.location!!.latitude, 0.0001)
        assertEquals(77.2090, msg.location!!.longitude, 0.0001)

        val header = repo.getHeaderState("Node #911")
        assertTrue("Thread header must report active emergency", header.isEmergency)
    }

    @Test
    fun testDtnStoredRepresentationDoesNotClaimDelivered() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        // Outbound DTN stored packet (pending delivery, relayed store-and-forward)
        MessageHistoryStore.addRecord(
            createRecord(
                id = "dtn-1",
                peer = "Node #4444",
                direction = MessageDirection.SENT,
                deliveryStatus = DeliveryStatus.PENDING,
                isRelayed = true,
                transferId = 0x42.toShort()
            )
        )

        advanceUntilIdle()

        val conv = repo.conversations.value.first { it.peerNodeId == 4444 }
        assertEquals(ChatDeliveryStatus.DTN_STORED, conv.deliveryStatus)
        assertNotEquals("DTN stored message must NEVER be marked as ACK/Delivered", ChatDeliveryStatus.ACK, conv.deliveryStatus)
    }

    @Test
    fun testDistinctMessagesAreNotCollapsed() = runTest(testDispatcher) {
        val repo = ChatRepository(context = null, scope = backgroundScope)

        // Multiple distinct messages with identical text at different times
        val t1 = System.currentTimeMillis() - 4000L
        val t2 = System.currentTimeMillis() - 2000L
        val t3 = System.currentTimeMillis()

        MessageHistoryStore.addRecord(createRecord(id = "m1", timestamp = t1, peer = "Node #3333", text = "Roger"))
        MessageHistoryStore.addRecord(createRecord(id = "m2", timestamp = t2, peer = "Node #3333", text = "Roger"))
        MessageHistoryStore.addRecord(createRecord(id = "m3", timestamp = t3, peer = "Node #3333", text = "Roger"))

        advanceUntilIdle()

        val thread = repo.getThreadMessages("Node #3333")
        assertEquals("Distinct messages must not be collapsed", 3, thread.size)
        assertEquals("m1", thread[0].id)
        assertEquals("m2", thread[1].id)
        assertEquals("m3", thread[2].id)
    }

    @Test
    fun testNavigationAndBottomNavTabOrdering() {
        val mainFile = File("src/main/java/org/sih/itantra/presentation/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", mainFile.exists())
        val mainText = mainFile.readText()

        // Verify IndividualChatScreen integration and back handling
        assertTrue("MainActivity must declare activeChatPeerId", mainText.contains("activeChatPeerId"))
        assertTrue("MainActivity must route IndividualChatScreen", mainText.contains("IndividualChatScreen("))
        assertTrue("MainActivity must wire BackHandler", mainText.contains("BackHandler("))

        // Verify BottomNavBar tab ordering: Radio first, Chats second
        val navFile = File("src/main/java/org/sih/itantra/presentation/components/BottomNavBar.kt")
        assertTrue("BottomNavBar.kt must exist", navFile.exists())
        val navText = navFile.readText()

        val visibleTabsIndex = navText.indexOf("val visibleTabs = listOf(")
        assertTrue("visibleTabs definition found", visibleTabsIndex != -1)
        val visibleTabsSub = navText.substring(visibleTabsIndex, visibleTabsIndex + 160)

        val radioPos = visibleTabsSub.indexOf("RadioNavTab.RADIO")
        val chatsPos = visibleTabsSub.indexOf("RadioNavTab.CHATS")
        val diagPos = visibleTabsSub.indexOf("RadioNavTab.DIAGNOSTICS")
        val setPos = visibleTabsSub.indexOf("RadioNavTab.SETTINGS")

        assertTrue("Radio tab must be present", radioPos != -1)
        assertTrue("Chats tab must be present", chatsPos != -1)
        assertTrue("Radio must precede Chats (Radio first on left)", radioPos < chatsPos)
        assertTrue("Chats must precede Diagnostics", chatsPos < diagPos)
        assertTrue("Diagnostics must precede Settings", diagPos < setPos)
    }

    @Test
    fun testMultilingualMessageBadgeAndTtsResolution() {
        val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT)

        val hindiRecord = createRecord(language = IndicLanguage.HINDI, text = "नमस्ते")
        val tamilRecord = createRecord(language = IndicLanguage.TAMIL, text = "வணக்கம்")
        val englishRecord = createRecord(language = IndicLanguage.ENGLISH, text = "Hello")

        val hindiRes = resolver.resolveFromRecord(hindiRecord)
        val tamilRes = resolver.resolveFromRecord(tamilRecord)
        val englishRes = resolver.resolveFromRecord(englishRecord)

        assertTrue(hindiRes is TtsResolutionResult.Resolved)
        assertTrue(tamilRes is TtsResolutionResult.Resolved)
        assertTrue(englishRes is TtsResolutionResult.Resolved)

        assertEquals("HI", (hindiRes as TtsResolutionResult.Resolved).profile.language.badgeCode)
        assertEquals("TA", (tamilRes as TtsResolutionResult.Resolved).profile.language.badgeCode)
        assertEquals("EN", (englishRes as TtsResolutionResult.Resolved).profile.language.badgeCode)
    }

    @Test
    fun testIndividualChatScreenIntegratesMultilingualPlaybackComponents() {
        val chatScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/IndividualChatScreen.kt")
        assertTrue("IndividualChatScreen.kt must exist", chatScreenFile.exists())
        val code = chatScreenFile.readText()

        // Verify Feature 11 components are integrated
        assertTrue("Must reference MessageLanguageBadge", code.contains("MessageLanguageBadge("))
        assertTrue("Must reference TtsPlaybackIndicator", code.contains("TtsPlaybackIndicator("))
        assertTrue("Must wire playbackState", code.contains("playbackState"))
        assertTrue("Must wire playMessageVoice", code.contains("playMessageVoice("))
        assertTrue("Must wire stopVoicePlayback", code.contains("stopVoicePlayback("))
    }
}
