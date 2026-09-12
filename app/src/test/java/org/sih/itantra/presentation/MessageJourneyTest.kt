package org.sih.itantra.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioPriorityContext
import org.sih.itantra.core.message.journey.JourneyEvent
import org.sih.itantra.core.message.journey.JourneyEventStatus
import org.sih.itantra.core.message.journey.JourneyEventType
import org.sih.itantra.core.message.journey.MessageJourney
import org.sih.itantra.core.message.journey.MessageJourneyMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus

/**
 * Domain and scenario tests for Feature 9: Message Journey.
 *
 * Verifies model immutability, pure statelessness, complex end-to-end tactical scenarios,
 * and edge-case boundary conditions.
 */
class MessageJourneyTest {

    @Test
    fun testJourneyEventDataModel() {
        val event = JourneyEvent(
            type = JourneyEventType.TRANSMITTED,
            timestampMs = 1726000000000L,
            node = "Local Node (Self)",
            transport = "Wi-Fi UDP",
            hop = 1,
            status = JourneyEventStatus.SUCCESS,
            detail = "256 B wire packet"
        )

        assertEquals(JourneyEventType.TRANSMITTED, event.type)
        assertEquals(1726000000000L, event.timestampMs)
        assertEquals("Local Node (Self)", event.node)
        assertEquals("Wi-Fi UDP", event.transport)
        assertEquals(1, event.hop)
        assertEquals(JourneyEventStatus.SUCCESS, event.status)
        assertEquals("256 B wire packet", event.detail)
    }

    @Test
    fun testMessageJourneyDataModel() {
        val journey = MessageJourney(
            messageId = "test-msg-01",
            text = "Check perimeter fence",
            direction = MessageDirection.SENT,
            source = "Local Node (Self)",
            destination = "Node #9901",
            priority = MessagePriority.NORMAL,
            priorityContext = RadioPriorityContext.NORMAL,
            isEmergency = false,
            currentRadioState = RadioDeliveryState.ACKNOWLEDGED,
            events = emptyList(),
            isPartial = false,
            partialReason = null,
            totalKnownHops = 1,
            knownTransports = listOf("Wi-Fi UDP"),
            ackState = "Confirmed (15 ms RTT)",
            dtnStatus = "Bypassed (Route Active)",
            fragmentationSummary = "Single Packet (1/1)"
        )

        assertEquals("test-msg-01", journey.messageId)
        assertEquals("Check perimeter fence", journey.text)
        assertEquals(MessageDirection.SENT, journey.direction)
        assertEquals("Local Node (Self)", journey.source)
        assertEquals("Node #9901", journey.destination)
        assertFalse(journey.isEmergency)
        assertEquals(RadioDeliveryState.ACKNOWLEDGED, journey.currentRadioState)
        assertEquals(1, journey.totalKnownHops)
        assertEquals(listOf("Wi-Fi UDP"), journey.knownTransports)
    }

    @Test
    fun testPureStatelessness_idempotent() {
        val record = MessageRecord(
            id = "stateless-check-01",
            timestamp = 1726000000000L,
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.NORMAL,
            text = "Testing stateless mapper",
            peer = "Node #209070",
            packetSizeBytes = 200,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 2.0,
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 18L
        )

        val journey1 = MessageJourneyMapper.map(record)
        val journey2 = MessageJourneyMapper.map(record)

        assertEquals(journey1, journey2)
        assertEquals(journey1.events.size, journey2.events.size)
        assertEquals(journey1.events, journey2.events)
    }

    @Test
    fun testComplexScenario_relayedWithQosAndDeliveryReceipt() {
        val record = MessageRecord(
            id = "scenario-complex-01",
            timestamp = 1726000000000L,
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.IMPORTANT,
            text = "Coordinate link with checkpoint Bravo",
            peer = "Node #554433",
            packetSizeBytes = 280,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 5.2,
            isRelayed = true,
            hopCount = 3,
            deliveryStatus = DeliveryStatus.DELIVERED,
            transferId = 77.toShort(),
            deliveryLatencyMs = 65L,
            qosStatus = "WIFI/QUEUED",
            isSecure = true,
            authStatus = "AUTH ✓"
        )

        val journey = MessageJourneyMapper.map(record)

        // Must have CREATED, QUEUED, TRANSMITTED, RELAYED, ACKNOWLEDGED
        val eventTypes = journey.events.map { it.type }
        assertTrue(eventTypes.contains(JourneyEventType.CREATED))
        assertTrue(eventTypes.contains(JourneyEventType.QUEUED))
        assertTrue(eventTypes.contains(JourneyEventType.TRANSMITTED))
        assertTrue(eventTypes.contains(JourneyEventType.RELAYED))
        assertTrue(eventTypes.contains(JourneyEventType.ACKNOWLEDGED))

        // Since it is relayed, must be flagged as partial
        assertTrue(journey.isPartial)
        assertNotNull(journey.partialReason)

        // Hops must be 3
        assertEquals(3, journey.totalKnownHops)
        // Per Feature 6, active QUEUED QoS status overrides delivery tracker in radio state
        assertEquals(RadioDeliveryState.QUEUED, journey.currentRadioState)
    }

    @Test
    fun testComplexScenario_relayedAcknowledged() {
        val record = MessageRecord(
            id = "scenario-relayed-ack",
            timestamp = 1726000000000L,
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.IMPORTANT,
            text = "Coordinate link with checkpoint Bravo",
            peer = "Node #554433",
            packetSizeBytes = 280,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 5.2,
            isRelayed = true,
            hopCount = 3,
            deliveryStatus = DeliveryStatus.DELIVERED,
            transferId = 77.toShort(),
            deliveryLatencyMs = 65L,
            qosStatus = null,
            isSecure = true,
            authStatus = "AUTH ✓"
        )

        val journey = MessageJourneyMapper.map(record)
        assertEquals(RadioDeliveryState.ACKNOWLEDGED, journey.currentRadioState)
        assertEquals(3, journey.totalKnownHops)
        assertTrue(journey.isPartial)
    }

    @Test
    fun testComplexScenario_incomingMultiFragmentMeshMessage() {
        val record = MessageRecord(
            id = "scenario-incoming-frag",
            timestamp = 1726000010000L,
            direction = MessageDirection.RECEIVED,
            language = IndicLanguage.MARATHI,
            priority = MessagePriority.NORMAL,
            text = "लांब संदेश पॅकेट रीअसेम्बल केले",
            peer = "Node #1002",
            packetSizeBytes = 1024,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 12.0,
            isRelayed = true,
            hopCount = 2,
            fragmentCount = 3,
            isSecure = true,
            authStatus = "AUTH ✓"
        )

        val journey = MessageJourneyMapper.map(record)

        assertEquals("Node #1002", journey.source)
        assertEquals("Local Node (Self)", journey.destination)

        val eventTypes = journey.events.map { it.type }
        assertTrue(eventTypes.contains(JourneyEventType.TRANSMITTED))
        assertTrue(eventTypes.contains(JourneyEventType.RELAYED))
        assertTrue(eventTypes.contains(JourneyEventType.REASSEMBLED))
        assertTrue(eventTypes.contains(JourneyEventType.RECEIVED))

        val reassembly = journey.events.first { it.type == JourneyEventType.REASSEMBLED }
        assertTrue(reassembly.detail!!.contains("3 packets"))
        assertEquals("Local Node (Self)", reassembly.node)
    }

    @Test
    fun testEmergencyDistressBroadcastJourney() {
        val distressRecord = MessageRecord(
            id = "distress-911",
            timestamp = 1726000020000L,
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.DISTRESS,
            text = "MAYDAY IMMEDIATE ASSISTANCE REQUIRED",
            peer = "Emergency Broadcast",
            packetSizeBytes = 120,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 1.2,
            isRelayed = false,
            hopCount = 1,
            deliveryStatus = DeliveryStatus.DELIVERED,
            isSecure = true
        )

        val journey = MessageJourneyMapper.map(distressRecord)

        assertTrue(journey.isEmergency)
        assertEquals(RadioPriorityContext.DISTRESS, journey.priorityContext)
        assertEquals("Emergency Broadcast", journey.destination)

        val created = journey.events.first { it.type == JourneyEventType.CREATED }
        assertTrue(created.detail!!.contains("Emergency distress"))
    }
}
