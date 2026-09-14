package org.sih.itantra.core.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.search.SearchIndex

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRetentionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var chatRepository: ChatRepository

    @Before
    fun setup() {
        MessageHistoryStore.clear()
        MessageRetentionPolicy.resetTimeProvider()
        chatRepository = ChatRepository(context = null, scope = kotlinx.coroutines.CoroutineScope(testDispatcher))
    }

    @After
    fun tearDown() {
        MessageHistoryStore.clear()
        MessageRetentionPolicy.resetTimeProvider()
    }

    private fun createRecord(
        id: String,
        timestamp: Long,
        peer: String = "Node #101",
        direction: MessageDirection = MessageDirection.SENT,
        priority: MessagePriority = MessagePriority.NORMAL,
        text: String = "Tactical transmission payload",
        transferId: Short? = null,
        deliveryStatus: DeliveryStatus = DeliveryStatus.NONE
    ): MessageRecord = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = direction,
        language = IndicLanguage.HINDI,
        priority = priority,
        text = text,
        peer = peer,
        packetSizeBytes = 64,
        rawAudioEquivalentBytes = 12000L,
        measuredLatencyMs = 120.0,
        deliveryStatus = deliveryStatus,
        transferId = transferId
    )

    // -------------------------------------------------------------------------
    // Test 1: Exact 10-Minute Boundary Calculations (600,000 ms)
    // -------------------------------------------------------------------------
    @Test
    fun testExactTenMinuteBoundaryCalculations() {
        val now = 1_000_000_000L
        val exactBoundary = now - MessageRetentionPolicy.RETENTION_PERIOD_MS
        val oneMsBeforeBoundary = exactBoundary + 1L
        val oneMsAfterBoundary = exactBoundary - 1L

        // Exact boundary: currentTime >= timestamp + 600,000 => true
        assertTrue("Exact 10-minute boundary must be expired", MessageRetentionPolicy.isExpired(exactBoundary, now))
        assertFalse("1ms before boundary must remain active", MessageRetentionPolicy.isExpired(oneMsBeforeBoundary, now))
        assertTrue("1ms past boundary must be expired", MessageRetentionPolicy.isExpired(oneMsAfterBoundary, now))
    }

    // -------------------------------------------------------------------------
    // Test 2: FilterActive Retains Younger Messages and Drops Expired
    // -------------------------------------------------------------------------
    @Test
    fun testFilterActiveRetainsYoungerMessagesAndDropsExpired() {
        val now = 2_000_000_000L
        val msgYoung1 = createRecord("msg-young1", timestamp = now - 100_000L) // 1m40s old
        val msgYoung2 = createRecord("msg-young2", timestamp = now - 599_999L) // 9m59.999s old
        val msgExpired1 = createRecord("msg-exp1", timestamp = now - 600_000L) // 10m00s old
        val msgExpired2 = createRecord("msg-exp2", timestamp = now - 900_000L) // 15m old

        val records = listOf(msgYoung1, msgYoung2, msgExpired1, msgExpired2)
        val active = MessageRetentionPolicy.filterActive(records, now)

        assertEquals(2, active.size)
        assertEquals("msg-young1", active[0].id)
        assertEquals("msg-young2", active[1].id)
    }

    // -------------------------------------------------------------------------
    // Test 3: GetRemainingTtlMs Boundary and Clamping
    // -------------------------------------------------------------------------
    @Test
    fun testGetRemainingTtlMsBoundaryAndClamping() {
        val now = 1_000_000_000L
        val timestamp = now - 240_000L // 4m old => 6m remaining (360_000 ms)

        assertEquals(360_000L, MessageRetentionPolicy.getRemainingTtlMs(timestamp, now))

        val exactExpired = now - 600_000L
        assertEquals(0L, MessageRetentionPolicy.getRemainingTtlMs(exactExpired, now))

        val farExpired = now - 1_200_000L
        assertEquals(0L, MessageRetentionPolicy.getRemainingTtlMs(farExpired, now))
    }

    // -------------------------------------------------------------------------
    // Test 4: FormatTtl Formatting
    // -------------------------------------------------------------------------
    @Test
    fun testFormatTtlFormatting() {
        assertEquals("6m", MessageRetentionPolicy.formatTtl(360_000L))
        assertEquals("1m", MessageRetentionPolicy.formatTtl(60_000L))
        assertEquals("<1m", MessageRetentionPolicy.formatTtl(59_999L))
        assertEquals("<1m", MessageRetentionPolicy.formatTtl(1_000L))
        assertEquals("EXPIRED", MessageRetentionPolicy.formatTtl(0L))
        assertEquals("EXPIRED", MessageRetentionPolicy.formatTtl(-5_000L))
    }

    // -------------------------------------------------------------------------
    // Test 5: MessageHistoryStore PruneExpired Removes Expired Records
    // -------------------------------------------------------------------------
    @Test
    fun testMessageHistoryStorePruneExpiredRemovesExpiredRecords() {
        val now = 1_000_000_000L
        MessageHistoryStore.addRecord(createRecord("m1", timestamp = now - 700_000L)) // expired at `now`
        MessageHistoryStore.addRecord(createRecord("m2", timestamp = now - 650_000L)) // expired at `now`
        MessageHistoryStore.addRecord(createRecord("m3", timestamp = now - 100_000L)) // active

        val pruned = MessageHistoryStore.pruneExpired(now)
        assertEquals(2, pruned)

        val remaining = MessageHistoryStore.getRecords()
        assertEquals(1, remaining.size)
        assertEquals("m3", remaining[0].id)
    }

    // -------------------------------------------------------------------------
    // Test 6: MessageHistoryStore PruneExpired Notifies Registered Listeners
    // -------------------------------------------------------------------------
    @Test
    fun testMessageHistoryStorePruneExpiredNotifiesRegisteredListeners() {
        val now = 1_000_000_000L
        MessageHistoryStore.addRecord(createRecord("m1", timestamp = now - 650_000L))

        var notified = false
        val listener = { notified = true }
        MessageHistoryStore.addListener(listener)

        MessageHistoryStore.pruneExpired(now)
        assertTrue("Listener must be notified when records are pruned", notified)

        MessageHistoryStore.removeListener(listener)
    }

    // -------------------------------------------------------------------------
    // Test 7: MessageHistoryStore PruneExpired Does Not Notify When No Records Expired
    // -------------------------------------------------------------------------
    @Test
    fun testMessageHistoryStorePruneExpiredDoesNotNotifyWhenNoRecordsExpired() {
        val now = 1_000_000_000L
        MessageHistoryStore.addRecord(createRecord("m1", timestamp = now - 100_000L))

        var callCount = 0
        val listener: () -> Unit = { callCount++ }
        MessageHistoryStore.addListener(listener)

        val pruned = MessageHistoryStore.pruneExpired(now)
        assertEquals(0, pruned)
        assertEquals(0, callCount)

        MessageHistoryStore.removeListener(listener)
    }

    // -------------------------------------------------------------------------
    // Test 8: MessageHistoryStore AddRecord and Pruning
    // -------------------------------------------------------------------------
    @Test
    fun testMessageHistoryStoreAddRecordAndPruning() {
        var mockTime = 1_000_000_000L
        MessageRetentionPolicy.timeProvider = object : MessageRetentionPolicy.TimeProvider {
            override fun currentTimeMillis(): Long = mockTime
        }
        MessageHistoryStore.addRecord(createRecord("old", timestamp = mockTime))
        assertEquals(1, MessageHistoryStore.getRecords().size)

        // Advance mockTime by 11 minutes (660,000 ms)
        mockTime += 660_000L

        // Pruning removes expired record
        val pruned = MessageHistoryStore.pruneExpired(mockTime)
        assertEquals(1, pruned)

        MessageHistoryStore.addRecord(createRecord("new", timestamp = mockTime))
        val current = MessageHistoryStore.getRecords()
        assertEquals(1, current.size)
        assertEquals("new", current[0].id)
    }

    // -------------------------------------------------------------------------
    // Test 9: MessageHistoryStore PruneExpired Cleans Store
    // -------------------------------------------------------------------------
    @Test
    fun testMessageHistoryStorePruneExpiredCleansStore() {
        var mockTime = 1_000_000_000L
        MessageRetentionPolicy.timeProvider = object : MessageRetentionPolicy.TimeProvider {
            override fun currentTimeMillis(): Long = mockTime
        }
        MessageHistoryStore.addRecord(createRecord("m1", timestamp = mockTime))

        // At mockTime + 5m -> still present
        mockTime += 300_000L
        MessageHistoryStore.pruneExpired(mockTime)
        assertEquals(1, MessageHistoryStore.getRecords().size)

        // At mockTime + 10m from original t0 -> pruned
        mockTime += 300_000L // total +600k
        MessageHistoryStore.pruneExpired(mockTime)
        val afterExpiry = MessageHistoryStore.getRecords()
        assertTrue(afterExpiry.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Test 10: Incremental Pruning as Simulated Time Advances
    // -------------------------------------------------------------------------
    @Test
    fun testIncrementalPruningAsSimulatedTimeAdvances() {
        var mockTime = 1_000_000_000L
        MessageRetentionPolicy.timeProvider = object : MessageRetentionPolicy.TimeProvider {
            override fun currentTimeMillis(): Long = mockTime
        }
        val t0 = mockTime
        MessageHistoryStore.addRecord(createRecord("m1", timestamp = t0))
        MessageHistoryStore.addRecord(createRecord("m2", timestamp = t0 + 120_000L)) // t0 + 2m
        MessageHistoryStore.addRecord(createRecord("m3", timestamp = t0 + 300_000L)) // t0 + 5m

        // At t0 + 8m: all 3 active
        mockTime = t0 + 480_000L
        MessageHistoryStore.pruneExpired(mockTime)
        assertEquals(3, MessageHistoryStore.getRecords().size)

        // At t0 + 10m: m1 expires (m2, m3 remain)
        mockTime = t0 + 600_000L
        MessageHistoryStore.pruneExpired(mockTime)
        val at10m = MessageHistoryStore.getRecords()
        assertEquals(2, at10m.size)
        assertFalse(at10m.any { it.id == "m1" })

        // At t0 + 12m: m2 expires (m3 remains)
        mockTime = t0 + 720_000L
        MessageHistoryStore.pruneExpired(mockTime)
        val at12m = MessageHistoryStore.getRecords()
        assertEquals(1, at12m.size)
        assertEquals("m3", at12m[0].id)

        // At t0 + 15m: m3 expires (none remain)
        mockTime = t0 + 900_000L
        MessageHistoryStore.pruneExpired(mockTime)
        val at15m = MessageHistoryStore.getRecords()
        assertTrue(at15m.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Test 11: Mixed SENT and RECEIVED Messages Both Prune Cleanly
    // -------------------------------------------------------------------------
    @Test
    fun testMixedSentAndReceivedMessagesBothPruneCleanly() {
        val now = 1_000_000_000L
        MessageHistoryStore.addRecord(createRecord("sent-old", timestamp = now - 650_000L, direction = MessageDirection.SENT))
        MessageHistoryStore.addRecord(createRecord("recv-old", timestamp = now - 620_000L, direction = MessageDirection.RECEIVED))
        MessageHistoryStore.addRecord(createRecord("sent-new", timestamp = now - 100_000L, direction = MessageDirection.SENT))
        MessageHistoryStore.addRecord(createRecord("recv-new", timestamp = now - 50_000L, direction = MessageDirection.RECEIVED))

        MessageHistoryStore.pruneExpired(now)
        val records = MessageHistoryStore.getRecords()
        assertEquals(2, records.size)
        assertTrue(records.any { it.id == "sent-new" })
        assertTrue(records.any { it.id == "recv-new" })
    }

    // -------------------------------------------------------------------------
    // Test 12: Emergency Distress Message Prunes Local Display Safely
    // -------------------------------------------------------------------------
    @Test
    fun testEmergencyDistressMessagePrunesLocalDisplaySafely() {
        val now = 1_000_000_000L
        MessageHistoryStore.addRecord(
            createRecord(
                id = "distress-1",
                timestamp = now - 600_000L,
                priority = MessagePriority.DISTRESS,
                text = "MAYDAY SOS NEED EXTRACTION"
            )
        )

        // Expired distress message should prune from local UI memory cleanly
        MessageHistoryStore.pruneExpired(now)
        val records = MessageHistoryStore.getRecords()
        assertTrue("Expired distress message must be pruned locally", records.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Test 13: UpdateRecordDelivery Safely Handles Pruned Record
    // -------------------------------------------------------------------------
    @Test
    fun testUpdateRecordDeliverySafelyHandlesPrunedRecord() {
        val now = 1_000_000_000L
        val transferId: Short = 42
        MessageHistoryStore.addRecord(
            createRecord("out-1", timestamp = now - 650_000L, transferId = transferId, direction = MessageDirection.SENT)
        )

        MessageHistoryStore.pruneExpired(now)
        assertEquals(0, MessageHistoryStore.getRecords().size)

        try {
            MessageHistoryStore.updateRecordDelivery(transferId, DeliveryStatus.DELIVERED, 150L)
        } catch (e: Exception) {
            fail("Late delivery receipt on pruned record should not throw: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Test 14: ChatRepository GetThreadMessages Returns Only Unexpired
    // -------------------------------------------------------------------------
    @Test
    fun testChatRepositoryGetThreadMessagesReturnsOnlyUnexpired() {
        val now = 1_000_000_000L
        MessageHistoryStore.addRecord(createRecord("n1-old", timestamp = now - 700_000L, peer = "Node #500"))
        MessageHistoryStore.addRecord(createRecord("n1-active", timestamp = now - 100_000L, peer = "Node #500"))
        MessageHistoryStore.addRecord(createRecord("n2-active", timestamp = now - 50_000L, peer = "Node #600"))

        MessageHistoryStore.pruneExpired(now)
        val thread500 = chatRepository.getThreadMessages("Node #500")
        assertEquals(1, thread500.size)
        assertEquals("n1-active", thread500[0].id)
    }

    // -------------------------------------------------------------------------
    // Test 15: ChatRepository AggregateConversations Updates LastMessage When Latest Expires
    // -------------------------------------------------------------------------
    @Test
    fun testChatRepositoryAggregateConversationsUpdatesLastMessageWhenLatestExpires() {
        var mockTime = 1_000_000_000L
        MessageRetentionPolicy.timeProvider = object : MessageRetentionPolicy.TimeProvider {
            override fun currentTimeMillis(): Long = mockTime
        }
        val t0 = mockTime
        MessageHistoryStore.addRecord(createRecord("older", timestamp = t0, peer = "Node #700", text = "Older transmission"))
        MessageHistoryStore.addRecord(createRecord("newer", timestamp = t0 + 400_000L, peer = "Node #700", text = "Newer transmission"))

        // At t0 + 500_000L: both are active, lastMessageText is "Newer transmission"
        mockTime = t0 + 500_000L
        MessageHistoryStore.pruneExpired(mockTime)
        chatRepository.refresh()
        var conv = chatRepository.conversations.value.firstOrNull { it.id == "Node #700" }
        assertNotNull(conv)
        assertEquals("Newer transmission", conv!!.lastMessageText)

        // At t0 + 650_000L: "older" (t0) is expired, "newer" (t0 + 400k) is still active
        mockTime = t0 + 650_000L
        MessageHistoryStore.pruneExpired(mockTime)
        chatRepository.refresh()
        conv = chatRepository.conversations.value.firstOrNull { it.id == "Node #700" }
        assertNotNull(conv)
        assertEquals("Newer transmission", conv!!.lastMessageText)
    }

    // -------------------------------------------------------------------------
    // Test 16: ChatRepository AggregateConversations Cleans Up When All Messages Expire
    // -------------------------------------------------------------------------
    @Test
    fun testChatRepositoryAggregateConversationsCleansUpWhenAllMessagesExpire() {
        var mockTime = 1_000_000_000L
        MessageRetentionPolicy.timeProvider = object : MessageRetentionPolicy.TimeProvider {
            override fun currentTimeMillis(): Long = mockTime
        }
        MessageHistoryStore.addRecord(createRecord("m-old", timestamp = mockTime, peer = "Node #800"))
        chatRepository.refresh()
        assertNotNull(chatRepository.conversations.value.firstOrNull { it.id == "Node #800" })

        // Advance 10m+1s
        mockTime += 601_000L
        MessageHistoryStore.pruneExpired(mockTime)
        chatRepository.refresh()
        val conv = chatRepository.conversations.value.firstOrNull { it.id == "Node #800" }
        assertNull("Conversation with all expired messages should not appear in active conversation summaries", conv)
    }

    // -------------------------------------------------------------------------
    // Test 17: SearchIndex Automatically Evicts Expired Messages
    // -------------------------------------------------------------------------
    @Test
    fun testSearchIndexAutomaticallyEvictsExpiredMessages() {
        var mockTime = 1_000_000_000L
        MessageRetentionPolicy.timeProvider = object : MessageRetentionPolicy.TimeProvider {
            override fun currentTimeMillis(): Long = mockTime
        }
        val searchIndex = SearchIndex()
        val uniqueKeyword = "OperationKavach2026"
        MessageHistoryStore.addRecord(
            createRecord("search-exp", timestamp = mockTime, peer = "Broadcast", text = "Classified text $uniqueKeyword")
        )

        // Before pruning, searchIndex contains the item
        searchIndex.syncWithHistory()
        val initialMessageHits = searchIndex.search(uniqueKeyword).filter { it.resultType == org.sih.itantra.core.search.SearchResultType.MESSAGE }
        assertEquals(1, initialMessageHits.size)

        // Advance past 10 minutes and prune
        mockTime += 650_000L
        MessageHistoryStore.pruneExpired(mockTime)
        val afterPruneResults = searchIndex.search(uniqueKeyword)
        assertTrue("SearchIndex must evict pruned message and associated conversation", afterPruneResults.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Test 18: SearchIndex Query Returns Zero Hits for Pruned Messages
    // -------------------------------------------------------------------------
    @Test
    fun testSearchIndexQueryReturnsZeroHitsForPrunedMessages() {
        var mockTime = 1_000_000_000L
        MessageRetentionPolicy.timeProvider = object : MessageRetentionPolicy.TimeProvider {
            override fun currentTimeMillis(): Long = mockTime
        }
        val searchIndex = SearchIndex()
        MessageHistoryStore.addRecord(createRecord("s1", timestamp = mockTime, peer = "Broadcast", text = "TargetRendezvousAlpha"))
        MessageHistoryStore.addRecord(createRecord("s2", timestamp = mockTime + 400_000L, peer = "Broadcast", text = "TargetRendezvousBeta"))
        searchIndex.syncWithHistory()

        // Advance mockTime so s1 expires, but s2 remains active
        mockTime += 650_000L
        MessageHistoryStore.pruneExpired(mockTime)
        val resultsAlpha = searchIndex.search("TargetRendezvousAlpha")
        val resultsBeta = searchIndex.search("TargetRendezvousBeta")

        assertEquals(0, resultsAlpha.size)
        val betaMessages = resultsBeta.filter { it.resultType == org.sih.itantra.core.search.SearchResultType.MESSAGE }
        assertEquals(1, betaMessages.size)
    }

    // -------------------------------------------------------------------------
    // Test 19: PruneExpired Idempotency
    // -------------------------------------------------------------------------
    @Test
    fun testPruneExpiredIdempotency() {
        val now = 1_000_000_000L
        MessageHistoryStore.addRecord(createRecord("i1", timestamp = now - 650_000L))
        MessageHistoryStore.addRecord(createRecord("i2", timestamp = now - 100_000L))

        val firstCall = MessageHistoryStore.pruneExpired(now)
        assertEquals(1, firstCall)

        val secondCall = MessageHistoryStore.pruneExpired(now)
        assertEquals(0, secondCall)

        val thirdCall = MessageHistoryStore.pruneExpired(now)
        assertEquals(0, thirdCall)

        assertEquals(1, MessageHistoryStore.getRecords().size)
    }

    // -------------------------------------------------------------------------
    // Test 20: Custom Mock TimeProvider Determinism
    // -------------------------------------------------------------------------
    @Test
    fun testCustomTimeProviderDeterminism() {
        class MockTimeProvider(var mockTime: Long) : MessageRetentionPolicy.TimeProvider {
            override fun currentTimeMillis(): Long = mockTime
        }

        val clock = MockTimeProvider(1_000_000L)
        val recordTime = clock.mockTime
        val record = createRecord("determ-1", timestamp = recordTime)

        // At T+0
        assertFalse(MessageRetentionPolicy.isExpired(record, clock.currentTimeMillis()))
        assertEquals(600_000L, MessageRetentionPolicy.getRemainingTtlMs(record.timestamp, clock.currentTimeMillis()))

        // Advance 5 minutes
        clock.mockTime += 300_000L
        assertFalse(MessageRetentionPolicy.isExpired(record, clock.currentTimeMillis()))
        assertEquals(300_000L, MessageRetentionPolicy.getRemainingTtlMs(record.timestamp, clock.currentTimeMillis()))

        // Advance 4 minutes (total 9 minutes)
        clock.mockTime += 240_000L
        assertFalse(MessageRetentionPolicy.isExpired(record, clock.currentTimeMillis()))
        assertEquals(60_000L, MessageRetentionPolicy.getRemainingTtlMs(record.timestamp, clock.currentTimeMillis()))
        assertEquals("1m", MessageRetentionPolicy.formatTtl(MessageRetentionPolicy.getRemainingTtlMs(record.timestamp, clock.currentTimeMillis())))

        // Advance 1 minute (total 10 minutes)
        clock.mockTime += 60_000L
        assertTrue(MessageRetentionPolicy.isExpired(record, clock.currentTimeMillis()))
        assertEquals(0L, MessageRetentionPolicy.getRemainingTtlMs(record.timestamp, clock.currentTimeMillis()))
        assertEquals("EXPIRED", MessageRetentionPolicy.formatTtl(MessageRetentionPolicy.getRemainingTtlMs(record.timestamp, clock.currentTimeMillis())))
    }
}
