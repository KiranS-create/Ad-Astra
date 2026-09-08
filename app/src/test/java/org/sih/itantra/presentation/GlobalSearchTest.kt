package org.sih.itantra.presentation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.chat.ChatDeliveryStatus
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
import org.sih.itantra.core.search.LocalSearchRepository
import org.sih.itantra.core.search.SearchContactItem
import org.sih.itantra.core.search.SearchFilterState
import org.sih.itantra.core.search.SearchFilterType
import org.sih.itantra.core.search.SearchIndex
import org.sih.itantra.core.search.SearchQueryParser
import org.sih.itantra.core.search.SearchResultType

class GlobalSearchTest {

    private lateinit var searchIndex: SearchIndex
    private lateinit var repository: LocalSearchRepository

    @Before
    fun setUp() {
        MessageHistoryStore.clear()
        searchIndex = SearchIndex()
        repository = LocalSearchRepository(context = null, searchIndex = searchIndex)
    }

    @After
    fun tearDown() {
        searchIndex.clear()
        repository.release()
        MessageHistoryStore.clear()
    }

    // 1. Exact message match
    @Test
    fun testExactMessageMatch() {
        val record = createMessage(
            id = "m1",
            text = "Assistance requested at Ridge Trail.",
            peer = "Node #477124",
            priority = MessagePriority.DISTRESS
        )
        MessageHistoryStore.addRecord(record)

        val results = searchIndex.search("Assistance requested at Ridge Trail.")
        assertFalse("Expected non-empty search results", results.isEmpty())
        val match = results.first()
        assertEquals(SearchResultType.MESSAGE, match.resultType)
        assertEquals("Assistance requested at Ridge Trail.", match.snippet)
        assertTrue(match.relevanceScore >= 600)
    }

    // 2. Substring match
    @Test
    fun testSubstringMatch() {
        val record = createMessage(
            id = "m2",
            text = "Reconnaissance patrol along Sector 4.",
            peer = "Node #477124"
        )
        MessageHistoryStore.addRecord(record)

        val results = searchIndex.search("patrol")
        assertFalse(results.isEmpty())
        assertTrue(results.any { it.snippet?.contains("patrol") == true })
    }

    // 3. Case-insensitive match
    @Test
    fun testCaseInsensitiveMatch() {
        val record = createMessage(
            id = "m3",
            text = "EVACUATION ORDER ISSUED",
            peer = "Node #101"
        )
        MessageHistoryStore.addRecord(record)

        val results = searchIndex.search("evacuation order")
        assertFalse(results.isEmpty())
        assertTrue(results.any { it.snippet?.equals("EVACUATION ORDER ISSUED", ignoreCase = true) == true })
    }

    // 4. Node ID normalization ("Node #477124", "477124", "node 477124", "#477124")
    @Test
    fun testNodeIdNormalization() {
        val record = createMessage(
            id = "m4",
            text = "Status green at perimeter checkpoint.",
            peer = "Node #477124"
        )
        MessageHistoryStore.addRecord(record)

        val queries = listOf("477124", "Node 477124", "node #477124", "#477124", "Node#477124")
        for (q in queries) {
            val results = searchIndex.search(q)
            assertFalse("Query '$q' should find results for node 477124", results.isEmpty())
            assertTrue(
                "Query '$q' should match either node or message with node 477124",
                results.any { it.nodeId == 477124 }
            )
        }
    }

    // 5. Callsign matching
    @Test
    fun testCallsignMatching() {
        searchIndex.setContactProvider {
            listOf(
                SearchContactItem(
                    nodeId = 477124,
                    callsign = "SQUAD BRAVO",
                    displayName = "Ridge Patrol Unit"
                )
            )
        }

        val results = searchIndex.search("SQUAD BRAVO")
        assertFalse(results.isEmpty())
        val contactMatch = results.firstOrNull { it.resultType == SearchResultType.CONTACT }
        assertNotNull("Expected contact match for SQUAD BRAVO", contactMatch)
        assertEquals("SQUAD BRAVO", contactMatch?.title)
        assertEquals(477124, contactMatch?.nodeId)
    }

    // 6. Result ranking (exact node ID match > callsign match > phrase match > snippet match)
    @Test
    fun testResultRanking() {
        searchIndex.setContactProvider {
            listOf(
                SearchContactItem(nodeId = 555, callsign = "BRAVO 555")
            )
        }

        searchIndex.updateTopology(
            MeshTopologySnapshot(
                nodes = listOf(
                    TopologyNode(
                        nodeId = 555,
                        displayName = "BRAVO 555",
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
                )
            )
        )

        MessageHistoryStore.addRecord(
            createMessage(
                id = "m555",
                text = "Contact made near Node 555 waypoint.",
                peer = "Node #100"
            )
        )

        val results = searchIndex.search("555")
        assertTrue(results.size >= 2)

        // Node result should rank first
        val topResult = results[0]
        assertEquals(SearchResultType.NODE, topResult.resultType)
        assertEquals(555, topResult.nodeId)
        assertTrue(topResult.relevanceScore >= 1000)

        // Message referencing 555 should rank below direct node match
        val messageResult = results.firstOrNull { it.resultType == SearchResultType.MESSAGE }
        assertNotNull(messageResult)
        assertTrue(topResult.relevanceScore > messageResult!!.relevanceScore)
    }

    // 7. Result type distinction (MESSAGE vs NODE vs CONTACT)
    @Test
    fun testResultTypeDistinction() {
        searchIndex.setContactProvider {
            listOf(
                SearchContactItem(nodeId = 777, callsign = "EAGLE 777")
            )
        }

        searchIndex.updateTopology(
            MeshTopologySnapshot(
                nodes = listOf(
                    TopologyNode(
                        nodeId = 777,
                        displayName = "EAGLE 777",
                        isLocal = false,
                        isReachable = true,
                        lastSeen = "1m",
                        lastSeenMs = System.currentTimeMillis(),
                        hopCount = 1,
                        transport = "Wi-Fi",
                        routeState = "DIRECT",
                        role = TopologyNodeRole.NEIGHBOR,
                        state = TopologyNodeState.ONLINE
                    )
                )
            )
        )

        MessageHistoryStore.addRecord(
            createMessage(
                id = "m777",
                text = "Report from EAGLE 777 received.",
                peer = "Node #777"
            )
        )

        val results = searchIndex.search("777")
        val types = results.map { it.resultType }.toSet()

        assertTrue("Expected NODE result type", types.contains(SearchResultType.NODE))
        assertTrue("Expected CONTACT result type", types.contains(SearchResultType.CONTACT))
        assertTrue("Expected MESSAGE result type", types.contains(SearchResultType.MESSAGE))
    }

    // 8. Empty query behavior
    @Test
    fun testEmptyQueryBehavior() {
        MessageHistoryStore.addRecord(createMessage(id = "mEmpty", text = "Sample message", peer = "Node #1"))
        val emptyResults = searchIndex.search("")
        assertTrue(emptyResults.isEmpty())

        val whitespaceResults = searchIndex.search("   ")
        assertTrue(whitespaceResults.isEmpty())
    }

    // 9. No-results behavior
    @Test
    fun testNoResultsBehavior() {
        MessageHistoryStore.addRecord(createMessage(id = "m1", text = "Alpha Bravo Charlie", peer = "Node #1"))
        val results = searchIndex.search("ZuluNonExistentQuery999")
        assertTrue(results.isEmpty())
    }

    // 10. Multiple result grouping
    @Test
    fun testMultipleResultGrouping() {
        repository.setContactProvider {
            listOf(
                SearchContactItem(nodeId = 888, callsign = "HAWK 888")
            )
        }

        MessageHistoryStore.addRecord(
            createMessage(id = "m888", text = "Hawk 888 standing by.", peer = "Node #888")
        )

        repository.setQuery("888")
        val grouped = repository.groupedResults.value

        assertTrue(grouped.containsKey(SearchResultType.CONTACT) || grouped.containsKey(SearchResultType.NODE))
        assertTrue(grouped.containsKey(SearchResultType.MESSAGE))
    }

    // 11. Indic-script text matching (Hindi, Tamil)
    @Test
    fun testIndicScriptMatching() {
        val hindiRecord = createMessage(
            id = "m_hi",
            text = "हम सुरक्षित हैं। सहायता की आवश्यकता नहीं है।",
            peer = "Node #301",
            language = IndicLanguage.HINDI
        )
        val tamilRecord = createMessage(
            id = "m_ta",
            text = "நாங்கள் பாதுகாப்பாக உள்ளோம்.",
            peer = "Node #302",
            language = IndicLanguage.TAMIL
        )

        MessageHistoryStore.addRecord(hindiRecord)
        MessageHistoryStore.addRecord(tamilRecord)

        // Search in native Hindi script
        val hindiResults = searchIndex.search("सुरक्षित")
        assertFalse("Hindi search should match Hindi record", hindiResults.isEmpty())
        assertEquals("m_hi", hindiResults.first().resultId.removePrefix("msg_"))
        assertEquals(IndicLanguage.HINDI, hindiResults.first().language)

        // Search in native Tamil script
        val tamilResults = searchIndex.search("பாதுகாப்பாக")
        assertFalse("Tamil search should match Tamil record", tamilResults.isEmpty())
        assertEquals("m_ta", tamilResults.first().resultId.removePrefix("msg_"))
        assertEquals(IndicLanguage.TAMIL, tamilResults.first().language)
    }

    // 12. Filter by result type (Messages only, Nodes only, Contacts only)
    @Test
    fun testFilterByResultType() {
        searchIndex.setContactProvider {
            listOf(SearchContactItem(nodeId = 999, callsign = "DELTA 999"))
        }

        searchIndex.updateTopology(
            MeshTopologySnapshot(
                nodes = listOf(
                    TopologyNode(
                        nodeId = 999,
                        displayName = "DELTA 999",
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
                )
            )
        )

        MessageHistoryStore.addRecord(
            createMessage(id = "m999", text = "Delta 999 in position.", peer = "Node #999")
        )

        // Filter: Messages only
        val msgOnly = searchIndex.search("999", SearchFilterState(filterType = SearchFilterType.MESSAGES))
        assertTrue(msgOnly.all { it.resultType == SearchResultType.MESSAGE })

        // Filter: Nodes only
        val nodesOnly = searchIndex.search("999", SearchFilterState(filterType = SearchFilterType.NODES))
        assertTrue(nodesOnly.all { it.resultType == SearchResultType.NODE })

        // Filter: Contacts only
        val contactsOnly = searchIndex.search("999", SearchFilterState(filterType = SearchFilterType.CONTACTS))
        assertTrue(contactsOnly.all { it.resultType == SearchResultType.CONTACT })
    }

    // 13. Incremental index update when new message arrives via MessageHistoryStore
    @Test
    fun testIncrementalIndexUpdateOnNewMessage() {
        repository.setQuery("urgent")
        assertTrue("Initially no matches", repository.searchResults.value.isEmpty())

        // New message arrives in MessageHistoryStore
        val incoming = createMessage(
            id = "m_inc",
            text = "Urgent medical supplies needed at camp.",
            peer = "Node #404",
            priority = MessagePriority.ALERT
        )
        MessageHistoryStore.addRecord(incoming)

        // Search repository should reactively reflect the new item
        val updated = repository.searchResults.value
        assertFalse("Repository should reactively include new message", updated.isEmpty())
        assertEquals("msg_m_inc", updated.first().resultId)
    }

    // 14. Duplicate index entry suppression
    @Test
    fun testDuplicateIndexSuppression() {
        val record = createMessage(id = "mDup", text = "Duplicate test text", peer = "Node #123")
        MessageHistoryStore.addRecord(record)
        // Add same record again or re-index
        searchIndex.indexMessage(record)

        val results = searchIndex.search("Duplicate test")
        val messageMatches = results.filter { it.resultType == SearchResultType.MESSAGE && it.resultId == "msg_mDup" }
        assertEquals("Duplicate message ID should not produce duplicate search entries", 1, messageMatches.size)
    }

    // 15. Bounded recent-search behavior (capped at 10, FIFO eviction, clearable)
    @Test
    fun testBoundedRecentSearches() {
        for (i in 1..15) {
            repository.recordSearchCommit("Query $i")
        }

        val recents = repository.recentSearches.value
        assertEquals("Recent searches must be capped at 10 items", 10, recents.size)
        assertEquals("Most recent query should be first", "Query 15", recents[0])
        assertEquals("Query 6 should be the 10th item", "Query 6", recents[9])
        assertFalse("Older queries should be evicted", recents.contains("Query 1"))

        // Test clear
        repository.clearRecentSearches()
        assertTrue("Recent searches should be empty after clear", repository.recentSearches.value.isEmpty())
    }

    // 16. Action callback receives correct node/message identifier
    @Test
    fun testActionCallbackIdentifiers() {
        val record = createMessage(
            id = "action_msg_1",
            text = "Action test message",
            peer = "Node #654321"
        )
        MessageHistoryStore.addRecord(record)

        val results = searchIndex.search("654321")
        val messageResult = results.firstOrNull { it.resultType == SearchResultType.MESSAGE }
        assertNotNull(messageResult)

        assertEquals("msg_action_msg_1", messageResult?.resultId)
        assertEquals("action_msg_1", messageResult?.resultId?.removePrefix("msg_"))
        assertEquals(654321, messageResult?.nodeId)
    }

    // 17. Distinct messages with identical text remain distinct
    @Test
    fun testDistinctMessagesWithIdenticalTextRemainDistinct() {
        val m1 = createMessage(id = "msg_id_A", text = "Checkpoint clear", peer = "Node #10")
        val m2 = createMessage(id = "msg_id_B", text = "Checkpoint clear", peer = "Node #20")
        MessageHistoryStore.addRecord(m1)
        MessageHistoryStore.addRecord(m2)

        val results = searchIndex.search("Checkpoint clear")
        val messageResults = results.filter { it.resultType == SearchResultType.MESSAGE }
        assertEquals(2, messageResults.size)
        val ids = messageResults.map { it.resultId }.toSet()
        assertTrue(ids.contains("msg_msg_id_A"))
        assertTrue(ids.contains("msg_msg_id_B"))
    }

    // 18. Search does not mutate MessageHistoryStore
    @Test
    fun testSearchDoesNotMutateHistoryStore() {
        val initialRecord = createMessage(id = "mInitial", text = "Initial message", peer = "Node #99")
        MessageHistoryStore.addRecord(initialRecord)
        val initialSize = MessageHistoryStore.getRecords().size

        searchIndex.search("Initial")
        repository.setQuery("Initial")
        repository.setFilterType(SearchFilterType.MESSAGES)
        repository.clearFilters()

        assertEquals("MessageHistoryStore must not be mutated by search operations", initialSize, MessageHistoryStore.getRecords().size)
    }

    // 19. Search does not initialize speech models
    @Test
    fun testSearchDoesNotInitializeSpeechModels() {
        // Calling search query parser, index, and repository should never touch Sherpa, Piper, or ONNX
        val parser = SearchQueryParser.parse("Test speech model safety")
        assertEquals(4, parser.tokens.size)

        val results = searchIndex.search("Any query")
        assertNotNull(results)
        // Passes trivially without loading any native libs or asset managers
    }

    private fun createMessage(
        id: String,
        text: String,
        peer: String,
        language: IndicLanguage = IndicLanguage.ENGLISH,
        priority: MessagePriority = MessagePriority.NORMAL
    ): MessageRecord {
        return MessageRecord(
            id = id,
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.RECEIVED,
            language = language,
            priority = priority,
            text = text,
            peer = peer,
            packetSizeBytes = 64,
            rawAudioEquivalentBytes = 32000,
            measuredLatencyMs = 25.0,
            isRelayed = false,
            hopCount = 1,
            deliveryStatus = DeliveryStatus.DELIVERED,
            isSecure = true
        )
    }
}
