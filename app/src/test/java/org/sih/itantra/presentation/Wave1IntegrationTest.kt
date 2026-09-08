package org.sih.itantra.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.chat.ChatRepository
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.ContactIdentity
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.core.discovery.DeviceTrustState
import org.sih.itantra.core.discovery.DiscoveredRawDevice
import org.sih.itantra.core.discovery.DiscoverySourceStatus
import org.sih.itantra.core.discovery.DiscoverySourceType
import org.sih.itantra.core.discovery.NearbyDevice
import org.sih.itantra.core.discovery.NearbyDeviceRepository
import org.sih.itantra.core.discovery.NearbyDiscoverySource
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.mesh.TopologyNodeRole
import org.sih.itantra.core.mesh.TopologyNodeState
import org.sih.itantra.core.mesh.TopologyRoute
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.search.LocalSearchRepository
import org.sih.itantra.core.search.SearchContactItem
import org.sih.itantra.core.search.SearchFilterState
import org.sih.itantra.core.search.SearchFilterType
import org.sih.itantra.core.search.SearchIndex
import org.sih.itantra.core.search.SearchResultType

/**
 * Wave 1 End-to-End Integration Test Suite.
 * Validates cross-feature interactions between:
 * - Feature 1: Tactical Chats Home
 * - Feature 2: Tactical Individual Chat
 * - Feature 3: Tactical Contacts
 * - Feature 4: Nearby iTantra Devices
 * - Feature 5: Offline Global Search
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Wave1IntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val localNodeId = 1001

    private lateinit var contactRepository: ContactRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var searchIndex: SearchIndex
    private lateinit var searchRepository: LocalSearchRepository
    private lateinit var fakeBleSource: FakeIntegrationDiscoverySource
    private lateinit var fakeUwbSource: FakeIntegrationDiscoverySource
    private lateinit var fakeMeshSource: FakeIntegrationDiscoverySource
    private lateinit var nearbyDeviceRepository: NearbyDeviceRepository

    private fun createMessage(
        id: String = "msg-1",
        timestamp: Long = System.currentTimeMillis(),
        direction: MessageDirection = MessageDirection.SENT,
        language: IndicLanguage = IndicLanguage.HINDI,
        priority: MessagePriority = MessagePriority.NORMAL,
        text: String = "Test transmission",
        peer: String = "Broadcast",
        deliveryStatus: DeliveryStatus = DeliveryStatus.NONE,
        isRelayed: Boolean = false,
        hopCount: Int = 0
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
        hopCount = hopCount
    )

    @Before
    fun setUp() {
        MessageHistoryStore.clear()

        // 1. Setup Contacts Repository
        contactRepository = ContactRepository(context = null, scope = testScope)
        contactRepository.clearAll()

        // 2. Setup Chats Repository
        chatRepository = ChatRepository(context = null, scope = testScope)

        // 3. Setup Global Search
        searchIndex = SearchIndex()
        searchRepository = LocalSearchRepository(
            context = null,
            searchIndex = searchIndex,
            scope = testScope
        ).apply {
            setContactProvider {
                contactRepository.contacts.value.map { contact ->
                    SearchContactItem(
                        nodeId = contact.nodeId,
                        callsign = contact.callsign,
                        displayName = contact.displayName,
                        supportedLanguages = contact.supportedLanguages,
                        authStatus = contact.authStatus.name,
                        notes = contact.identity.notes,
                        isOffline = contact.isOffline
                    )
                }
            }
        }

        // 4. Setup Nearby Devices Repository with Fakes
        fakeBleSource = FakeIntegrationDiscoverySource(DiscoverySourceType.BLE, DiscoverySourceStatus.STANDBY, true)
        fakeUwbSource = FakeIntegrationDiscoverySource(DiscoverySourceType.UWB, DiscoverySourceStatus.UNAVAILABLE, false)
        fakeMeshSource = FakeIntegrationDiscoverySource(DiscoverySourceType.MESH_TOPOLOGY, DiscoverySourceStatus.STANDBY, true)

        nearbyDeviceRepository = NearbyDeviceRepository(
            localNodeId = localNodeId,
            bleSource = fakeBleSource,
            uwbSource = fakeUwbSource,
            meshSource = fakeMeshSource,
            dispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        searchIndex.clear()
        searchRepository.release()
        contactRepository.clearAll()
        MessageHistoryStore.clear()
    }

    // =========================================================================
    // 1. Contacts + Mesh Topology Integration
    // =========================================================================

    @Test
    fun contacts_meshTopologyProjection_updatesRouteStateAccurately() = runTest(testDispatcher) {
        val contact1 = ContactIdentity(
            nodeId = 2001,
            callsign = "EAGLE-1",
            displayName = "Captain Miller",
            supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH),
            authStatus = ContactAuthStatus.AUTHENTICATED
        )
        val contact2 = ContactIdentity(
            nodeId = 2002,
            callsign = "EAGLE-2",
            displayName = "Lieutenant Davis",
            supportedLanguages = listOf(IndicLanguage.TAMIL),
            authStatus = ContactAuthStatus.TRUSTED
        )
        contactRepository.addContact(contact1)
        contactRepository.addContact(contact2)

        advanceUntilIdle()

        // Initially both contacts are offline (no topology snapshot provided)
        var retrieved1 = contactRepository.getContact(2001)
        var retrieved2 = contactRepository.getContact(2002)
        assertEquals(ChatRouteState.DISCONNECTED, retrieved1?.networkState?.routeState)
        assertEquals(ChatRouteState.DISCONNECTED, retrieved2?.networkState?.routeState)
        assertTrue(retrieved1?.isOffline == true)
        assertTrue(retrieved2?.isOffline == true)

        // Inject mesh topology: 2001 is direct (hopCount = 1), 2002 is multi-hop (hopCount = 3)
        val topologySnapshot = MeshTopologySnapshot(
            nodes = listOf(
                TopologyNode(
                    nodeId = 2001,
                    displayName = "EAGLE-1",
                    isLocal = false,
                    isReachable = true,
                    lastSeen = "now",
                    lastSeenMs = System.currentTimeMillis(),
                    hopCount = 1,
                    transport = "Wi-Fi",
                    routeState = "DIRECT",
                    role = TopologyNodeRole.NEIGHBOR,
                    state = TopologyNodeState.ONLINE
                ),
                TopologyNode(
                    nodeId = 2002,
                    displayName = "EAGLE-2",
                    isLocal = false,
                    isReachable = true,
                    lastSeen = "now",
                    lastSeenMs = System.currentTimeMillis(),
                    hopCount = 3,
                    transport = "Bluetooth",
                    routeState = "RELAYED",
                    role = TopologyNodeRole.RELAY,
                    state = TopologyNodeState.ONLINE
                )
            ),
            routes = listOf(
                TopologyRoute(2001, 2001, 1, System.currentTimeMillis(), "Wi-Fi", "ACTIVE"),
                TopologyRoute(2002, 2001, 3, System.currentTimeMillis(), "Bluetooth", "ACTIVE")
            )
        )

        contactRepository.updateTopology(topologySnapshot)
        advanceUntilIdle()

        retrieved1 = contactRepository.getContact(2001)
        retrieved2 = contactRepository.getContact(2002)

        assertEquals(ChatRouteState.CONNECTED_DIRECT, retrieved1?.networkState?.routeState)
        assertEquals(1, retrieved1?.networkState?.hopCount)
        assertFalse(retrieved1?.isOffline == true)

        assertEquals(ChatRouteState.CONNECTED_RELAYED, retrieved2?.networkState?.routeState)
        assertEquals(3, retrieved2?.networkState?.hopCount)
        assertFalse(retrieved2?.isOffline == true)
    }

    // =========================================================================
    // 2. Nearby Devices -> Add Contact -> Trust Model Mapping
    // =========================================================================

    @Test
    fun nearbyDevice_addToContacts_mapsTrustStateAndSuppressesLocalNode() = runTest(testDispatcher) {
        nearbyDeviceRepository.startScanning()
        advanceUntilIdle()

        // 1. Ingest local node (should be suppressed by repository)
        val localRaw = DiscoveredRawDevice(
            deviceId = "LOCAL_MAC_ADDR",
            nodeId = localNodeId,
            callsign = "LOCAL NODE",
            sourceType = DiscoverySourceType.BLE,
            rssi = -40
        )
        nearbyDeviceRepository.ingestRawDevices(listOf(localRaw))
        advanceUntilIdle()

        val devicesAfterLocal = nearbyDeviceRepository.discoveredDevices.value
        assertTrue("Local node must be suppressed from discovery", devicesAfterLocal.none { it.nodeId == localNodeId })

        // 2. Ingest external device
        val hmacRaw = DiscoveredRawDevice(
            deviceId = "MAC_BRAVO_01",
            nodeId = 3001,
            callsign = "BRAVO-LEAD",
            name = "Alpha Bravo",
            sourceType = DiscoverySourceType.BLE,
            rssi = -55,
            supportedLanguages = listOf(IndicLanguage.HINDI)
        )

        // 3. Ingest external unverified device
        val unverifiedRaw = DiscoveredRawDevice(
            deviceId = "MAC_UNKNOWN_02",
            nodeId = 3002,
            callsign = "UNKNOWN-SCOUT",
            sourceType = DiscoverySourceType.BLE,
            rssi = -80
        )

        nearbyDeviceRepository.ingestRawDevices(listOf(hmacRaw, unverifiedRaw))
        advanceUntilIdle()

        val discoveredList = nearbyDeviceRepository.discoveredDevices.value
        assertEquals(2, discoveredList.size)

        val dev1 = discoveredList.first { it.nodeId == 3001 }
        val dev2 = discoveredList.first { it.nodeId == 3002 }

        // Convert device to contact identity with UNVERIFIED default trust
        val contact1 = ContactIdentity(
            nodeId = dev1.nodeId,
            callsign = dev1.callsign,
            displayName = if (dev1.callsign.startsWith("NODE #")) null else dev1.callsign,
            supportedLanguages = dev1.supportedLanguages,
            authStatus = when (dev1.trustState) {
                DeviceTrustState.AUTHENTICATED_HMAC -> ContactAuthStatus.AUTHENTICATED
                DeviceTrustState.KNOWN_CONTACT -> ContactAuthStatus.TRUSTED
                else -> ContactAuthStatus.UNVERIFIED
            }
        )
        assertTrue(contactRepository.addContact(contact1))

        val contact2 = ContactIdentity(
            nodeId = dev2.nodeId,
            callsign = dev2.callsign,
            displayName = null,
            supportedLanguages = dev2.supportedLanguages,
            authStatus = when (dev2.trustState) {
                DeviceTrustState.AUTHENTICATED_HMAC -> ContactAuthStatus.AUTHENTICATED
                DeviceTrustState.KNOWN_CONTACT -> ContactAuthStatus.TRUSTED
                else -> ContactAuthStatus.UNVERIFIED
            }
        )
        assertTrue(contactRepository.addContact(contact2))
        advanceUntilIdle()

        val added1 = contactRepository.getContact(3001)
        val added2 = contactRepository.getContact(3002)

        // Unverified default trust verified
        assertEquals(ContactAuthStatus.UNVERIFIED, added1?.authStatus)
        assertEquals(ContactAuthStatus.UNVERIFIED, added2?.authStatus)
    }

    // =========================================================================
    // 3. Offline Global Search Across Messages, Contacts, and Mesh Topology
    // =========================================================================

    @Test
    fun offlineGlobalSearch_indexesAndRetrievesAcrossAllThreeDomains() = runTest(testDispatcher) {
        // Domain A: Contact
        val contact = ContactIdentity(
            nodeId = 4001,
            callsign = "TIGER-1",
            displayName = "Command Post Alpha",
            supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.MARATHI),
            notes = "Coordinates 28.6139N 77.2090E bunker",
            authStatus = ContactAuthStatus.AUTHENTICATED
        )
        contactRepository.addContact(contact)
        searchRepository.searchIndex.refreshContacts()

        // Domain B: Messages
        MessageHistoryStore.addRecord(
            createMessage(
                id = "msg-101",
                timestamp = System.currentTimeMillis() - 5000,
                direction = MessageDirection.RECEIVED,
                language = IndicLanguage.HINDI,
                priority = MessagePriority.NORMAL,
                text = "Perimeter clear at Northern Checkpoint bunker.",
                peer = "Node #4001"
            )
        )
        MessageHistoryStore.addRecord(
            createMessage(
                id = "msg-102",
                timestamp = System.currentTimeMillis(),
                direction = MessageDirection.SENT,
                language = IndicLanguage.ENGLISH,
                priority = MessagePriority.DISTRESS,
                text = "Medevac requested at Ridge Sector 4!",
                peer = "Broadcast"
            )
        )

        // Domain C: Topology Nodes
        val topologySnapshot = MeshTopologySnapshot(
            nodes = listOf(
                TopologyNode(
                    nodeId = 5001,
                    displayName = "RELAY-FOXTROT",
                    isLocal = false,
                    isReachable = true,
                    lastSeen = "now",
                    lastSeenMs = System.currentTimeMillis(),
                    hopCount = 2,
                    transport = "Wi-Fi",
                    routeState = "RELAYED",
                    role = TopologyNodeRole.RELAY,
                    state = TopologyNodeState.ONLINE
                )
            ),
            routes = listOf(
                TopologyRoute(5001, 4001, 2, System.currentTimeMillis(), "Wi-Fi", "ACTIVE")
            )
        )
        searchRepository.updateTopology(topologySnapshot)
        advanceUntilIdle()

        // 1. Search for "bunker": should match both Contact (in notes) and Message (in text)
        val bunkerResults = searchRepository.searchIndex.search("bunker")
        val bunkerTypes = bunkerResults.map { it.resultType }
        assertTrue("Should find message matching 'bunker'", bunkerTypes.contains(SearchResultType.MESSAGE))
        assertTrue("Should find contact matching 'bunker'", bunkerTypes.contains(SearchResultType.CONTACT))

        // 2. Search for "RELAY-FOXTROT": should match Topology Node
        val relayResults = searchRepository.searchIndex.search("RELAY-FOXTROT")
        assertEquals(1, relayResults.size)
        assertEquals(SearchResultType.NODE, relayResults[0].resultType)
        assertEquals(5001, relayResults[0].nodeId)

        // 3. Search with Filter: filter specifically for MESSAGES
        val filteredMessages = searchRepository.searchIndex.search(
            "Medevac",
            filterState = SearchFilterState(filterType = SearchFilterType.MESSAGES)
        )
        assertEquals(1, filteredMessages.size)
        assertEquals("msg_msg-102", filteredMessages[0].resultId)

        // 4. Search with Filter: filter specifically for CONTACTS
        val filteredContacts = searchRepository.searchIndex.search(
            "TIGER",
            filterState = SearchFilterState(filterType = SearchFilterType.CONTACTS)
        )
        assertEquals(1, filteredContacts.size)
        assertEquals(4001, filteredContacts[0].nodeId)
    }

    // =========================================================================
    // 4. Canonical Peer ID Resolution & Individual Chat Routing
    // =========================================================================

    @Test
    fun canonicalPeerIdResolution_alignsContactsNearbyAndIndividualChat() = runTest(testDispatcher) {
        val targetNodeId = 7788
        val canonicalPeerId = "Node #$targetNodeId"

        // 1. Add contact
        contactRepository.addContact(
            ContactIdentity(
                nodeId = targetNodeId,
                callsign = "SENTRY-SEVEN",
                displayName = "Checkpoint Sentry"
            )
        )

        // 2. Verify chatRepository.normalizePeerId normalizes various representations
        assertEquals(canonicalPeerId, chatRepository.normalizePeerId(canonicalPeerId))
        assertEquals(canonicalPeerId, chatRepository.normalizePeerId("7788"))
        assertEquals(canonicalPeerId, chatRepository.normalizePeerId("NODE 7788"))
        assertEquals(canonicalPeerId, chatRepository.normalizePeerId("Node #7788"))

        // 3. Add message addressed to the canonical peer ID
        val msg = createMessage(
            id = "msg-direct-1",
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.NORMAL,
            text = "Status report required.",
            peer = canonicalPeerId,
            deliveryStatus = DeliveryStatus.DELIVERED
        )
        MessageHistoryStore.addRecord(msg)
        advanceUntilIdle()

        // 4. Query conversation summaries
        val summaries = chatRepository.filteredConversations.value
        val summary = summaries.firstOrNull { it.id == canonicalPeerId }
        assertNotNull("Conversation summary must exist for canonical peer ID", summary)
        assertEquals("NODE #$targetNodeId", summary?.displayName)
        assertEquals(targetNodeId, summary?.peerNodeId)

        // 5. Query thread messages
        val threadMessages = chatRepository.getThreadMessages(canonicalPeerId)
        assertEquals(1, threadMessages.size)
        assertEquals("Status report required.", threadMessages[0].text)

        // 6. Verify IndividualChatHeaderState
        val headerState = chatRepository.getHeaderState(canonicalPeerId)
        assertEquals(targetNodeId, headerState.peerNodeId)
        assertEquals("NODE #$targetNodeId", headerState.displayName)
    }

    // =========================================================================
    // 5. Full Lifecycle: Nearby Discovery -> Add Contact -> Chat Message -> Search
    // =========================================================================

    @Test
    fun fullEndToEndLifecycle_discoveryToContactToChatToSearch() = runTest(testDispatcher) {
        // A. Start nearby discovery
        nearbyDeviceRepository.startScanning()
        advanceUntilIdle()

        val rawDiscovery = DiscoveredRawDevice(
            deviceId = "MAC_SCOUT_09",
            nodeId = 9009,
            callsign = "PATHFINDER-9",
            name = "Recon Scout",
            supportedLanguages = listOf(IndicLanguage.HINDI),
            rssi = -60,
            sourceType = DiscoverySourceType.BLE
        )
        nearbyDeviceRepository.ingestRawDevices(listOf(rawDiscovery))
        advanceUntilIdle()

        val discoveredDevice = nearbyDeviceRepository.discoveredDevices.value.first { it.nodeId == 9009 }
        assertNotNull(discoveredDevice)

        // B. Add to Tactical Contacts
        val contactIdentity = ContactIdentity(
            nodeId = discoveredDevice.nodeId,
            callsign = discoveredDevice.callsign,
            displayName = if (discoveredDevice.callsign.startsWith("NODE #")) null else discoveredDevice.callsign,
            supportedLanguages = discoveredDevice.supportedLanguages,
            authStatus = ContactAuthStatus.AUTHENTICATED
        )
        val added = contactRepository.addContact(contactIdentity)
        assertTrue("Should add contact from nearby discovery", added)
        advanceUntilIdle()

        // C. Send chat message to newly added contact
        val peerId = "Node #${discoveredDevice.nodeId}"
        val chatMsg = createMessage(
            id = "msg-life-1",
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.SENT,
            language = IndicLanguage.HINDI,
            priority = MessagePriority.NORMAL,
            text = "आपातकालीन आपूर्ति मार्ग सुरक्षित है।",
            peer = peerId,
            deliveryStatus = DeliveryStatus.DELIVERED
        )
        MessageHistoryStore.addRecord(chatMsg)
        advanceUntilIdle()

        // D. Verify chat thread and conversation summary
        val threadMessages = chatRepository.getThreadMessages(peerId)
        assertEquals(1, threadMessages.size)
        assertEquals("आपातकालीन आपूर्ति मार्ग सुरक्षित है।", threadMessages[0].text)

        // E. Update mesh topology
        val topologySnapshot = MeshTopologySnapshot(
            nodes = listOf(
                TopologyNode(
                    nodeId = 9009,
                    displayName = "PATHFINDER-9",
                    isLocal = false,
                    isReachable = true,
                    lastSeen = "now",
                    lastSeenMs = System.currentTimeMillis(),
                    hopCount = 1,
                    transport = "Wi-Fi",
                    routeState = "DIRECT",
                    role = TopologyNodeRole.NEIGHBOR,
                    state = TopologyNodeState.ONLINE
                )
            ),
            routes = listOf(
                TopologyRoute(9009, 9009, 1, System.currentTimeMillis(), "Wi-Fi", "ACTIVE")
            )
        )
        contactRepository.updateTopology(topologySnapshot)
        chatRepository.updateTopology(topologySnapshot)
        searchRepository.updateTopology(topologySnapshot)
        advanceUntilIdle()

        // Contact routeState should now be CONNECTED_DIRECT
        val updatedContact = contactRepository.getContact(9009)
        assertEquals(ChatRouteState.CONNECTED_DIRECT, updatedContact?.networkState?.routeState)

        // F. Global Search should find both the Contact and the Message
        searchRepository.searchIndex.refreshContacts()
        val searchByCallsign = searchRepository.searchIndex.search("PATHFINDER")
        assertTrue("Search should find PATHFINDER contact", searchByCallsign.any { it.resultType == SearchResultType.CONTACT && it.nodeId == 9009 })

        val searchByText = searchRepository.searchIndex.search("आपूर्ति")
        assertTrue("Search should find Indic text message", searchByText.any { it.resultType == SearchResultType.MESSAGE && it.resultId.endsWith("msg-life-1") })
    }
}

/**
 * Fake discovery source for deterministic integration testing.
 */
private class FakeIntegrationDiscoverySource(
    override val sourceType: DiscoverySourceType,
    initialStatus: DiscoverySourceStatus = DiscoverySourceStatus.STANDBY,
    private val isSourceSupported: Boolean = true
) : NearbyDiscoverySource {

    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<DiscoverySourceStatus> = _status.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredRawDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredRawDevice>> = _discoveredDevices.asStateFlow()

    override fun isSupported(): Boolean = isSourceSupported

    override suspend fun startScan(): Boolean {
        if (!isSourceSupported || _status.value == DiscoverySourceStatus.DISABLED || _status.value == DiscoverySourceStatus.PERMISSION_REQUIRED) {
            return false
        }
        _isScanning.value = true
        _status.value = DiscoverySourceStatus.ACTIVE
        return true
    }

    override suspend fun stopScan() {
        _isScanning.value = false
        if (!isSourceSupported) {
            _status.value = DiscoverySourceStatus.UNAVAILABLE
        } else if (_status.value == DiscoverySourceStatus.ACTIVE) {
            _status.value = DiscoverySourceStatus.STANDBY
        }
    }

    fun emitDevices(devices: List<DiscoveredRawDevice>) {
        _discoveredDevices.value = devices
    }

    fun setStatus(newStatus: DiscoverySourceStatus) {
        _status.value = newStatus
    }
}
