package org.sih.itantra.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.ContactIdentity
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.core.contact.ContactValidator
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.mesh.TopologyRoute
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import java.util.UUID

/**
 * Targeted unit tests for Feature 3: Tactical Contacts.
 * Verifies identity separation, offline persistence, topology projection, validation, and search.
 */
class ContactsTest {

    private lateinit var repository: ContactRepository

    @Before
    fun setup() {
        MessageHistoryStore.clear()
        repository = ContactRepository(context = null)
        repository.clearAll()
    }

    @Test
    fun contactCreation_persistsIdentityFieldsCorrectly() {
        val identity = ContactIdentity(
            nodeId = 998811,
            callsign = "SQUAD DELTA",
            displayName = "Recon Alpha Lead",
            supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.TAMIL),
            notes = "Forward observation post",
            authStatus = ContactAuthStatus.AUTHENTICATED
        )

        val added = repository.addContact(identity)
        assertTrue("Contact should be added successfully", added)

        val retrieved = repository.getContact(998811)
        assertNotNull("Retrieved contact should not be null", retrieved)
        assertEquals(998811, retrieved!!.nodeId)
        assertEquals("SQUAD DELTA", retrieved.callsign)
        assertEquals("Recon Alpha Lead", retrieved.displayName)
        assertEquals(listOf(IndicLanguage.HINDI, IndicLanguage.TAMIL), retrieved.supportedLanguages)
        assertEquals(ContactAuthStatus.AUTHENTICATED, retrieved.authStatus)
        assertEquals("Forward observation post", retrieved.identity.notes)
    }

    @Test
    fun contactCreation_rejectsDuplicateNodeId() {
        val contact1 = ContactIdentity(nodeId = 554433, callsign = "OUTPOST ONE")
        val contact2 = ContactIdentity(nodeId = 554433, callsign = "OUTPOST TWO")

        assertTrue("First contact should be added", repository.addContact(contact1))
        assertFalse("Duplicate nodeId must be rejected", repository.addContact(contact2))

        val retrieved = repository.getContact(554433)
        assertEquals("OUTPOST ONE", retrieved!!.callsign)
    }

    @Test
    fun contactValidation_validatesNodeIdCorrectly() {
        // Valid node IDs
        assertTrue(ContactValidator.validateNodeId("209070").first)
        assertTrue(ContactValidator.validateNodeId("#209070").first)
        assertTrue(ContactValidator.validateNodeId(" 1234 ").first)

        // Invalid node IDs
        assertFalse(ContactValidator.validateNodeId("").first)
        assertFalse(ContactValidator.validateNodeId("   ").first)
        assertFalse(ContactValidator.validateNodeId("abc").first)
        assertFalse(ContactValidator.validateNodeId("-15").first)
        assertFalse(ContactValidator.validateNodeId("0").first)
        assertFalse(ContactValidator.validateNodeId("999999999").first)
    }

    @Test
    fun contactValidation_validatesCallsignCorrectly() {
        // Valid callsigns
        assertTrue(ContactValidator.validateCallsign("SQUAD ALPHA").first)
        assertTrue(ContactValidator.validateCallsign("HQ").first)
        assertTrue(ContactValidator.validateCallsign("RECON-1").first)

        // Invalid callsigns
        assertFalse(ContactValidator.validateCallsign("").first)
        assertFalse(ContactValidator.validateCallsign("   ").first)
        assertFalse(ContactValidator.validateCallsign("A").first) // Min 2 chars
        assertFalse(ContactValidator.validateCallsign("A".repeat(35)).first) // Max 32 chars
    }

    @Test
    fun contactLookup_byNodeIdFindsExactMatch() {
        repository.addContact(ContactIdentity(nodeId = 1001, callsign = "NODE 1"))
        repository.addContact(ContactIdentity(nodeId = 1002, callsign = "NODE 2"))

        assertNotNull(repository.getContact(1001))
        assertEquals("NODE 1", repository.getContact(1001)!!.callsign)
        assertNotNull(repository.getContact(1002))
        assertEquals("NODE 2", repository.getContact(1002)!!.callsign)
        assertNull(repository.getContact(9999))
    }

    @Test
    fun contactLookup_byCallsignFindsMatchCaseInsensitive() {
        repository.addContact(ContactIdentity(nodeId = 2001, callsign = "COMMAND BASE"))

        val matchUpper = repository.getContactByCallsign("COMMAND BASE")
        assertNotNull(matchUpper)
        assertEquals(2001, matchUpper!!.nodeId)

        val matchLower = repository.getContactByCallsign("command base")
        assertNotNull(matchLower)
        assertEquals(2001, matchLower!!.nodeId)

        val matchTrim = repository.getContactByCallsign("  COMMAND BASE  ")
        assertNotNull(matchTrim)
        assertEquals(2001, matchTrim!!.nodeId)

        assertNull(repository.getContactByCallsign("NON_EXISTENT"))
    }

    @Test
    fun topologyProjection_mapsDirectConnectedRoute() {
        val peerId = 3001
        repository.addContact(ContactIdentity(nodeId = peerId, callsign = "DIRECT PEER"))

        val snapshot = MeshTopologySnapshot(
            nodes = listOf(
                TopologyNode(
                    nodeId = peerId,
                    displayName = "DIRECT PEER",
                    isLocal = false,
                    isReachable = true,
                    lastSeen = "Just now",
                    lastSeenMs = System.currentTimeMillis(),
                    hopCount = 1,
                    transport = "Wi-Fi Direct",
                    routeState = "DIRECT"
                )
            )
        )

        repository.updateTopology(snapshot)

        val contact = repository.getContact(peerId)
        assertNotNull(contact)
        assertEquals(ChatRouteState.CONNECTED_DIRECT, contact!!.networkState.routeState)
        assertEquals(1, contact.networkState.hopCount)
        assertEquals("Wi-Fi Direct", contact.networkState.transport)
        assertTrue(contact.networkState.isReachable)
        assertFalse(contact.isOffline)
    }

    @Test
    fun topologyProjection_mapsRelayedMultiHopRoute() {
        val peerId = 4001
        val relayId = 209070
        repository.addContact(ContactIdentity(nodeId = peerId, callsign = "RELAYED PEER"))

        val snapshot = MeshTopologySnapshot(
            nodes = listOf(
                TopologyNode(
                    nodeId = peerId,
                    displayName = "RELAYED PEER",
                    isLocal = false,
                    isReachable = true,
                    lastSeen = "10s ago",
                    lastSeenMs = System.currentTimeMillis() - 10_000,
                    hopCount = 2,
                    transport = "BLE Mesh",
                    routeState = "RELAYED"
                )
            ),
            routes = listOf(
                TopologyRoute(
                    destinationNodeId = peerId,
                    nextHopNodeId = relayId,
                    hopCount = 2,
                    routeFreshness = System.currentTimeMillis(),
                    transport = "BLE Mesh",
                    state = "VALID"
                )
            )
        )

        repository.updateTopology(snapshot)

        val contact = repository.getContact(peerId)
        assertNotNull(contact)
        assertEquals(ChatRouteState.CONNECTED_RELAYED, contact!!.networkState.routeState)
        assertEquals(2, contact.networkState.hopCount)
        assertEquals("BLE Mesh", contact.networkState.transport)
        assertEquals(relayId, contact.networkState.relayNodeId)
        assertTrue(contact.networkState.isReachable)
    }

    @Test
    fun topologyProjection_mapsRecentlyHeardState() {
        val peerId = 5001
        repository.addContact(ContactIdentity(nodeId = peerId, callsign = "RECENT PEER"))

        // Add message received 3 minutes ago
        MessageHistoryStore.addRecord(
            MessageRecord(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis() - 180_000, // 3 minutes ago
                direction = MessageDirection.RECEIVED,
                language = IndicLanguage.HINDI,
                priority = MessagePriority.NORMAL,
                text = "Recent tactical ping",
                peer = "Node #5001",
                packetSizeBytes = 64,
                rawAudioEquivalentBytes = 0,
                measuredLatencyMs = 24.0,
                hopCount = 1
            )
        )

        repository.refresh()

        val contact = repository.getContact(peerId)
        assertNotNull(contact)
        assertEquals(ChatRouteState.RECENTLY_HEARD, contact!!.networkState.routeState)
    }

    @Test
    fun offlineContact_persistsWhenTopologyClears() {
        val peerId = 6001
        repository.addContact(ContactIdentity(nodeId = peerId, callsign = "OFFLINE PEER"))

        // Active topology with peer
        repository.updateTopology(
            MeshTopologySnapshot(
                nodes = listOf(
                    TopologyNode(
                        nodeId = peerId,
                        displayName = "OFFLINE PEER",
                        isLocal = false,
                        isReachable = true,
                        lastSeen = "Now",
                        lastSeenMs = System.currentTimeMillis(),
                        hopCount = 1,
                        transport = "Wi-Fi",
                        routeState = "DIRECT"
                    )
                )
            )
        )

        var contact = repository.getContact(peerId)
        assertEquals(ChatRouteState.CONNECTED_DIRECT, contact!!.networkState.routeState)

        // Topology clears / peer disconnects
        repository.updateTopology(MeshTopologySnapshot(nodes = emptyList()))

        contact = repository.getContact(peerId)
        assertNotNull("Contact identity must still exist when offline", contact)
        assertEquals("OFFLINE PEER", contact!!.callsign)
        assertEquals(ChatRouteState.DISCONNECTED, contact.networkState.routeState)
        assertTrue(contact.isOffline)
    }

    @Test
    fun languageMetadata_preservesSelectedIndicLanguages() {
        val langs = listOf(IndicLanguage.KANNADA, IndicLanguage.MALAYALAM, IndicLanguage.TELUGU)
        repository.addContact(
            ContactIdentity(
                nodeId = 7001,
                callsign = "SOUTH COMMAND",
                supportedLanguages = langs
            )
        )

        val contact = repository.getContact(7001)
        assertNotNull(contact)
        assertEquals(3, contact!!.supportedLanguages.size)
        assertTrue(contact.supportedLanguages.contains(IndicLanguage.KANNADA))
        assertTrue(contact.supportedLanguages.contains(IndicLanguage.MALAYALAM))
        assertTrue(contact.supportedLanguages.contains(IndicLanguage.TELUGU))
    }

    @Test
    fun searchFilter_filtersByCallsignAndLanguage() {
        repository.addContact(
            ContactIdentity(
                nodeId = 8001,
                callsign = "ALPHA SCOUT",
                displayName = "Northern Perimeter",
                supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH)
            )
        )
        repository.addContact(
            ContactIdentity(
                nodeId = 8002,
                callsign = "BRAVO RECON",
                displayName = "Southern Relay",
                supportedLanguages = listOf(IndicLanguage.GUJARATI, IndicLanguage.HINDI)
            )
        )
        repository.addContact(
            ContactIdentity(
                nodeId = 8003,
                callsign = "CHARLIE HQ",
                displayName = "Main Command",
                supportedLanguages = listOf(IndicLanguage.ENGLISH)
            )
        )

        // Search by query
        val alphaResults = repository.searchContacts(query = "alpha")
        assertEquals(1, alphaResults.size)
        assertEquals("ALPHA SCOUT", alphaResults[0].callsign)

        // Search by node ID
        val idResults = repository.searchContacts(query = "8002")
        assertEquals(1, idResults.size)
        assertEquals("BRAVO RECON", idResults[0].callsign)

        // Filter by language
        val gujaratiResults = repository.searchContacts(languageFilter = IndicLanguage.GUJARATI)
        assertEquals(1, gujaratiResults.size)
        assertEquals("BRAVO RECON", gujaratiResults[0].callsign)

        // Filter by Hindi
        val hindiResults = repository.searchContacts(languageFilter = IndicLanguage.HINDI)
        assertEquals(2, hindiResults.size)

        // Combined query + language
        val combined = repository.searchContacts(query = "scout", languageFilter = IndicLanguage.HINDI)
        assertEquals(1, combined.size)
        assertEquals("ALPHA SCOUT", combined[0].callsign)

        val combinedNoMatch = repository.searchContacts(query = "scout", languageFilter = IndicLanguage.GUJARATI)
        assertTrue(combinedNoMatch.isEmpty())
    }

    @Test
    fun serialization_roundTripPreservesAllFields() {
        val original = ContactIdentity(
            nodeId = 123456,
            callsign = "TACTICAL RELAY",
            displayName = "Sierra Unit",
            supportedLanguages = listOf(IndicLanguage.MARATHI, IndicLanguage.BENGALI),
            notes = "Hilltop mast antenna",
            authStatus = ContactAuthStatus.TRUSTED,
            addedTimestamp = 1788901234567L
        )

        val serialized = ContactRepository.serializeIdentity(original)
        val deserialized = ContactRepository.deserializeIdentity(serialized)

        assertNotNull(deserialized)
        assertEquals(original.nodeId, deserialized!!.nodeId)
        assertEquals(original.callsign, deserialized.callsign)
        assertEquals(original.displayName, deserialized.displayName)
        assertEquals(original.supportedLanguages, deserialized.supportedLanguages)
        assertEquals(original.notes, deserialized.notes)
        assertEquals(original.authStatus, deserialized.authStatus)
        assertEquals(original.addedTimestamp, deserialized.addedTimestamp)
    }

    @Test
    fun contactDeletion_removesContactCompletely() {
        repository.addContact(ContactIdentity(nodeId = 9991, callsign = "TEMP NODE"))
        assertNotNull(repository.getContact(9991))

        val deleted = repository.deleteContact(9991)
        assertTrue("Contact deletion should return true", deleted)
        assertNull("Contact should be completely removed", repository.getContact(9991))

        // Deleting non-existent contact
        assertFalse("Deleting non-existent contact should return false", repository.deleteContact(9999))
    }
}
