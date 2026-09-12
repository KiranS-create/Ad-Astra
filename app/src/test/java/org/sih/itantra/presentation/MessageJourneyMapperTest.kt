package org.sih.itantra.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.MessageTechnicalInspectorMapper
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.message.RadioPriorityContext
import org.sih.itantra.core.message.journey.JourneyEventStatus
import org.sih.itantra.core.message.journey.JourneyEventType
import org.sih.itantra.core.message.journey.MessageJourneyMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus

/**
 * Rigorous unit tests for Feature 9: Message Journey Mapper.
 *
 * Verifies all 18 required test specifications:
 * 1. created event
 * 2. queued event
 * 3. transmitted event
 * 4. relay event
 * 5. ACK pending
 * 6. ACK acknowledged
 * 7. received
 * 8. DTN stored
 * 9. failed
 * 10. reassembly
 * 11. partial journey
 * 12. unknown metadata
 * 13. event ordering
 * 14. duplicate event suppression
 * 15. missing timestamps
 * 16. emergency priority
 * 17. consistency with Feature 6
 * 18. consistency with Feature 7
 */
class MessageJourneyMapperTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun sentRecord(
        id: String = "test-sent-101",
        timestamp: Long = 1726000000000L,
        priority: MessagePriority = MessagePriority.NORMAL,
        peer: String = "Node #209070",
        deliveryStatus: DeliveryStatus = DeliveryStatus.NONE,
        transferId: Short? = null,
        deliveryLatencyMs: Long? = null,
        hopCount: Int = 0,
        isRelayed: Boolean = false,
        qosStatus: String? = null,
        fragmentCount: Int? = null,
        isSecure: Boolean = false
    ) = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = MessageDirection.SENT,
        language = IndicLanguage.ENGLISH,
        priority = priority,
        text = "Tactical message payload",
        peer = peer,
        packetSizeBytes = 256,
        rawAudioEquivalentBytes = 0L,
        measuredLatencyMs = 4.0,
        isRelayed = isRelayed,
        hopCount = hopCount,
        deliveryStatus = deliveryStatus,
        transferId = transferId,
        deliveryLatencyMs = deliveryLatencyMs,
        fragmentCount = fragmentCount,
        qosStatus = qosStatus,
        isSecure = isSecure
    )

    private fun receivedRecord(
        id: String = "test-rx-202",
        timestamp: Long = 1726000005000L,
        priority: MessagePriority = MessagePriority.NORMAL,
        peer: String = "Node #477124",
        hopCount: Int = 0,
        isRelayed: Boolean = false,
        fragmentCount: Int? = null,
        isSecure: Boolean = false
    ) = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = MessageDirection.RECEIVED,
        language = IndicLanguage.HINDI,
        priority = priority,
        text = "संदेश प्राप्त हुआ",
        peer = peer,
        packetSizeBytes = 320,
        rawAudioEquivalentBytes = 0L,
        measuredLatencyMs = 8.0,
        isRelayed = isRelayed,
        hopCount = hopCount,
        fragmentCount = fragmentCount,
        isSecure = isSecure
    )

    // -------------------------------------------------------------------------
    // 1. Created Event
    // -------------------------------------------------------------------------
    @Test
    fun testCreatedEvent() {
        val record = sentRecord(timestamp = 1726000100000L)
        val journey = MessageJourneyMapper.map(record)

        val created = journey.events.firstOrNull { it.type == JourneyEventType.CREATED }
        assertNotNull("CREATED event must exist for outgoing record", created)
        assertEquals(1726000100000L, created!!.timestampMs)
        assertEquals("Local Node (Self)", created.node)
        assertEquals(JourneyEventStatus.SUCCESS, created.status)
    }

    // -------------------------------------------------------------------------
    // 2. Queued Event
    // -------------------------------------------------------------------------
    @Test
    fun testQueuedEvent() {
        // With QoS QUEUED status
        val queuedRecord = sentRecord(qosStatus = "WIFI/QUEUED")
        val queuedJourney = MessageJourneyMapper.map(queuedRecord)
        val queuedEvent = queuedJourney.events.firstOrNull { it.type == JourneyEventType.QUEUED }
        assertNotNull("QUEUED event must exist when qosStatus contains QUEUED", queuedEvent)
        assertEquals(JourneyEventStatus.WARNING, queuedEvent!!.status)

        // Without QoS QUEUED status
        val normalRecord = sentRecord(qosStatus = null)
        val normalJourney = MessageJourneyMapper.map(normalRecord)
        val noQueuedEvent = normalJourney.events.firstOrNull { it.type == JourneyEventType.QUEUED }
        assertNull("QUEUED event must NOT be emitted when QoS was not queued", noQueuedEvent)
    }

    // -------------------------------------------------------------------------
    // 3. Transmitted Event
    // -------------------------------------------------------------------------
    @Test
    fun testTransmittedEvent() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.DELIVERED)
        val journey = MessageJourneyMapper.map(record, transportOverride = "Wi-Fi UDP")

        val txEvent = journey.events.firstOrNull { it.type == JourneyEventType.TRANSMITTED }
        assertNotNull("TRANSMITTED event must exist for sent packet", txEvent)
        assertEquals("Local Node (Self)", txEvent!!.node)
        assertEquals("Wi-Fi UDP", txEvent.transport)
        assertEquals(1, txEvent.hop)
        assertEquals(JourneyEventStatus.SUCCESS, txEvent.status)
        assertTrue(txEvent.detail!!.contains("256B"))
    }

    // -------------------------------------------------------------------------
    // 4. Relay Event
    // -------------------------------------------------------------------------
    @Test
    fun testRelayEvent() {
        val record = sentRecord(isRelayed = true, hopCount = 3)
        val journey = MessageJourneyMapper.map(record)

        val relayEvent = journey.events.firstOrNull { it.type == JourneyEventType.RELAYED }
        assertNotNull("RELAYED event must exist when isRelayed is true", relayEvent)
        // CRITICAL: node must be UNKNOWN — never fabricated!
        assertEquals("UNKNOWN", relayEvent!!.node)
        assertEquals(3, relayEvent.hop)
        assertNull("Intermediate relay timestamp must be null (not recorded locally)", relayEvent.timestampMs)
        assertTrue(relayEvent.detail!!.contains("3 hops"))
    }

    // -------------------------------------------------------------------------
    // 5. ACK Pending
    // -------------------------------------------------------------------------
    @Test
    fun testAckPendingEvent() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.PENDING, transferId = 55.toShort(), isRelayed = false)
        val journey = MessageJourneyMapper.map(record)

        val ackPending = journey.events.firstOrNull { it.type == JourneyEventType.ACK_PENDING }
        assertNotNull("ACK_PENDING event must exist when awaiting receipt", ackPending)
        assertEquals("Node #209070", ackPending!!.node)
        assertEquals(JourneyEventStatus.PENDING, ackPending.status)
        assertTrue(ackPending.detail!!.contains("#55"))
    }

    // -------------------------------------------------------------------------
    // 6. ACK Acknowledged
    // -------------------------------------------------------------------------
    @Test
    fun testAckAcknowledgedEvent() {
        val record = sentRecord(
            timestamp = 1726000000000L,
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 42L
        )
        val journey = MessageJourneyMapper.map(record)

        val ackEvent = journey.events.firstOrNull { it.type == JourneyEventType.ACKNOWLEDGED }
        assertNotNull("ACKNOWLEDGED event must exist for DELIVERED status", ackEvent)
        assertEquals(1726000000042L, ackEvent!!.timestampMs) // timestamp + latency
        assertEquals("Node #209070", ackEvent.node)
        assertEquals(JourneyEventStatus.SUCCESS, ackEvent.status)
        assertTrue(ackEvent.detail!!.contains("42 ms"))
    }

    // -------------------------------------------------------------------------
    // 7. Received Event
    // -------------------------------------------------------------------------
    @Test
    fun testReceivedEvent() {
        val record = receivedRecord(timestamp = 1726000005000L, peer = "Node #477124")
        val journey = MessageJourneyMapper.map(record)

        val rxEvent = journey.events.firstOrNull { it.type == JourneyEventType.RECEIVED }
        assertNotNull("RECEIVED event must exist for incoming record", rxEvent)
        assertEquals(1726000005000L, rxEvent!!.timestampMs)
        assertEquals("Local Node (Self)", rxEvent.node)
        assertEquals(JourneyEventStatus.SUCCESS, rxEvent.status)
    }

    // -------------------------------------------------------------------------
    // 8. DTN Stored Event
    // -------------------------------------------------------------------------
    @Test
    fun testDtnStoredEvent() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.PENDING,
            transferId = 12.toShort(),
            isRelayed = true
        )
        val journey = MessageJourneyMapper.map(record)

        val dtnEvent = journey.events.firstOrNull { it.type == JourneyEventType.DTN_STORED }
        assertNotNull("DTN_STORED event must exist when held for DTN", dtnEvent)
        assertEquals("Local Node (Self)", dtnEvent!!.node)
        assertEquals(JourneyEventStatus.WARNING, dtnEvent.status)
        assertEquals(RadioDeliveryState.DTN_STORED, journey.currentRadioState)
        assertTrue(journey.dtnStatus!!.contains("DTN Buffer"))
    }

    // -------------------------------------------------------------------------
    // 9. Failed Event
    // -------------------------------------------------------------------------
    @Test
    fun testFailedEvent() {
        val record = sentRecord(
            timestamp = 1726000000000L,
            deliveryStatus = DeliveryStatus.TIMEOUT
        )
        val journey = MessageJourneyMapper.map(record)

        val failEvent = journey.events.firstOrNull { it.type == JourneyEventType.FAILED }
        assertNotNull("FAILED event must exist on timeout", failEvent)
        assertEquals(1726000030000L, failEvent!!.timestampMs) // timestamp + 30s
        assertEquals("Node #209070", failEvent.node)
        assertEquals(JourneyEventStatus.FAILED, failEvent.status)
        assertEquals(RadioDeliveryState.FAILED, journey.currentRadioState)
    }

    // -------------------------------------------------------------------------
    // 10. Reassembly Event
    // -------------------------------------------------------------------------
    @Test
    fun testReassemblyEvent() {
        val fragmentedRx = receivedRecord(fragmentCount = 4)
        val journey = MessageJourneyMapper.map(fragmentedRx)

        val reassemblyEvent = journey.events.firstOrNull { it.type == JourneyEventType.REASSEMBLED }
        assertNotNull("REASSEMBLED event must exist when fragmentCount > 1", reassemblyEvent)
        assertEquals("Local Node (Self)", reassemblyEvent!!.node)
        assertTrue(reassemblyEvent.detail!!.contains("4 packets"))
        assertTrue(journey.fragmentationSummary!!.contains("4 Fragments"))
    }

    // -------------------------------------------------------------------------
    // 11. Partial Journey Detection
    // -------------------------------------------------------------------------
    @Test
    fun testPartialJourney() {
        // Relayed message -> MUST be flagged as partial
        val relayedRecord = sentRecord(isRelayed = true)
        val relayedJourney = MessageJourneyMapper.map(relayedRecord)
        assertTrue("Relayed message must be marked partial", relayedJourney.isPartial)
        assertNotNull("Partial reason must be populated", relayedJourney.partialReason)

        // Historical unrecorded message -> MUST be flagged as partial
        val historicalRecord = sentRecord(deliveryStatus = DeliveryStatus.NONE)
        val historicalJourney = MessageJourneyMapper.map(historicalRecord)
        assertTrue("Historical message without transit tracking must be partial", historicalJourney.isPartial)

        // Direct single-hop completed message -> NOT partial
        val directDelivered = sentRecord(
            isRelayed = false,
            hopCount = 1,
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 25L
        )
        val directJourney = MessageJourneyMapper.map(directDelivered)
        assertFalse("Fully recorded direct delivery must not be partial", directJourney.isPartial)
        assertNull(directJourney.partialReason)
    }

    // -------------------------------------------------------------------------
    // 12. Unknown Metadata Handling (No Fabrication)
    // -------------------------------------------------------------------------
    @Test
    fun testUnknownMetadata() {
        val blankPeerRecord = sentRecord(peer = "", hopCount = 0, isRelayed = false)
        val journey = MessageJourneyMapper.map(blankPeerRecord)

        assertEquals("Broadcast", journey.destination)

        val relayedBlank = sentRecord(isRelayed = true, hopCount = 0)
        val relayedJourney = MessageJourneyMapper.map(relayedBlank)
        val relayEvent = relayedJourney.events.first { it.type == JourneyEventType.RELAYED }
        assertEquals("UNKNOWN", relayEvent.node) // Never fabricated node
    }

    // -------------------------------------------------------------------------
    // 13. Event Ordering
    // -------------------------------------------------------------------------
    @Test
    fun testEventOrdering() {
        val record = sentRecord(
            qosStatus = "WIFI/QUEUED",
            deliveryStatus = DeliveryStatus.DELIVERED,
            isRelayed = true,
            hopCount = 2,
            deliveryLatencyMs = 50L
        )
        val journey = MessageJourneyMapper.map(record)

        val types = journey.events.map { it.type }
        val createdIdx = types.indexOf(JourneyEventType.CREATED)
        val queuedIdx = types.indexOf(JourneyEventType.QUEUED)
        val txIdx = types.indexOf(JourneyEventType.TRANSMITTED)
        val relayIdx = types.indexOf(JourneyEventType.RELAYED)
        val ackIdx = types.indexOf(JourneyEventType.ACKNOWLEDGED)

        assertTrue(createdIdx != -1)
        assertTrue(queuedIdx != -1)
        assertTrue(txIdx != -1)
        assertTrue(relayIdx != -1)
        assertTrue(ackIdx != -1)

        assertTrue("CREATED must precede QUEUED", createdIdx < queuedIdx)
        assertTrue("QUEUED must precede TRANSMITTED", queuedIdx < txIdx)
        assertTrue("TRANSMITTED must precede RELAYED", txIdx < relayIdx)
        assertTrue("RELAYED must precede ACKNOWLEDGED", relayIdx < ackIdx)
    }

    // -------------------------------------------------------------------------
    // 14. Duplicate Event Suppression
    // -------------------------------------------------------------------------
    @Test
    fun testDuplicateEventSuppression() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.DELIVERED)
        val journey = MessageJourneyMapper.map(record)

        val types = journey.events.map { it.type }
        val distinctTypes = types.distinct()
        assertEquals("Each event type must appear at most once in timeline", distinctTypes.size, types.size)
    }

    // -------------------------------------------------------------------------
    // 15. Missing Timestamps
    // -------------------------------------------------------------------------
    @Test
    fun testMissingTimestamps() {
        val record = sentRecord(timestamp = 0L)
        val journey = MessageJourneyMapper.map(record)

        val created = journey.events.first { it.type == JourneyEventType.CREATED }
        assertNull("Missing or zero timestamp should result in null timestampMs", created.timestampMs)
        assertTrue(journey.isPartial)
    }

    // -------------------------------------------------------------------------
    // 16. Emergency Priority
    // -------------------------------------------------------------------------
    @Test
    fun testEmergencyPriority() {
        val distressRecord = sentRecord(priority = MessagePriority.DISTRESS)
        val distressJourney = MessageJourneyMapper.map(distressRecord)
        assertTrue(distressJourney.isEmergency)
        assertEquals(RadioPriorityContext.DISTRESS, distressJourney.priorityContext)

        val alertRecord = sentRecord(priority = MessagePriority.ALERT)
        val alertJourney = MessageJourneyMapper.map(alertRecord)
        assertTrue(alertJourney.isEmergency)
        assertEquals(RadioPriorityContext.ALERT, alertJourney.priorityContext)

        val normalRecord = sentRecord(priority = MessagePriority.NORMAL)
        val normalJourney = MessageJourneyMapper.map(normalRecord)
        assertFalse(normalJourney.isEmergency)
    }

    // -------------------------------------------------------------------------
    // 17. Consistency With Feature 6 (RadioMessageState)
    // -------------------------------------------------------------------------
    @Test
    fun testConsistencyWithFeature6() {
        val testStatuses = listOf(
            DeliveryStatus.SENDING,
            DeliveryStatus.PENDING,
            DeliveryStatus.DELIVERED,
            DeliveryStatus.TIMEOUT,
            DeliveryStatus.NONE
        )

        for (status in testStatuses) {
            val record = sentRecord(deliveryStatus = status, transferId = 10.toShort())
            val f6Telemetry = RadioMessageStateMapper.map(record)
            val f9Journey = MessageJourneyMapper.map(record)

            assertEquals(
                "Feature 9 currentRadioState must match Feature 6 deliveryState for status $status",
                f6Telemetry.deliveryState,
                f9Journey.currentRadioState
            )
            assertEquals(
                "PriorityContext must match Feature 6",
                f6Telemetry.priorityContext,
                f9Journey.priorityContext
            )
        }
    }

    // -------------------------------------------------------------------------
    // 18. Consistency With Feature 7 (MessageTechnicalInspector)
    // -------------------------------------------------------------------------
    @Test
    fun testConsistencyWithFeature7() {
        val record = sentRecord(
            id = "correlated-msg-99",
            priority = MessagePriority.ALERT,
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 33L
        )

        val inspector = MessageTechnicalInspectorMapper.map(record)
        val journey = MessageJourneyMapper.map(record)

        assertEquals("Message ID must match Feature 7", inspector.messageId, journey.messageId)
        assertEquals("Emergency status must match Feature 7", inspector.isEmergency, journey.isEmergency)
        assertEquals("Priority context must match Feature 7", inspector.priorityContext, journey.priorityContext)
    }
}
