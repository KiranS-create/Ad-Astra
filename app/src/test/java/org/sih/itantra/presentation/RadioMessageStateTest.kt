package org.sih.itantra.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.message.RadioPriorityContext
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus

/**
 * Unit tests for Feature 6: Radio-Aware Message States.
 *
 * All tests are deterministic — no networking, device, or hardware required.
 * Covers all 15 required test cases from the Feature 6 spec.
 */
class RadioMessageStateTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun sentRecord(
        deliveryStatus: DeliveryStatus = DeliveryStatus.NONE,
        isRelayed: Boolean = false,
        hopCount: Int = 0,
        transferId: Short? = null,
        qosStatus: String? = null,
        priority: MessagePriority = MessagePriority.NORMAL,
        isSecure: Boolean = false,
        fragmentCount: Int? = null,
        deliveryLatencyMs: Long? = null
    ) = MessageRecord(
        id = "test-${System.nanoTime()}",
        timestamp = System.currentTimeMillis(),
        direction = MessageDirection.SENT,
        language = IndicLanguage.ENGLISH,
        priority = priority,
        text = "Test message",
        peer = "Node #12345",
        packetSizeBytes = 256,
        rawAudioEquivalentBytes = 0L,
        measuredLatencyMs = 0.0,
        isRelayed = isRelayed,
        hopCount = hopCount,
        deliveryStatus = deliveryStatus,
        transferId = transferId,
        fragmentCount = fragmentCount,
        deliveryLatencyMs = deliveryLatencyMs,
        isSecure = isSecure,
        qosStatus = qosStatus
    )

    private fun receivedRecord(
        isRelayed: Boolean = false,
        hopCount: Int = 0,
        priority: MessagePriority = MessagePriority.NORMAL,
        isSecure: Boolean = true
    ) = MessageRecord(
        id = "rx-${System.nanoTime()}",
        timestamp = System.currentTimeMillis(),
        direction = MessageDirection.RECEIVED,
        language = IndicLanguage.HINDI,
        priority = priority,
        text = "Incoming message",
        peer = "Node #67890",
        packetSizeBytes = 128,
        rawAudioEquivalentBytes = 0L,
        measuredLatencyMs = 0.0,
        isRelayed = isRelayed,
        hopCount = hopCount,
        isSecure = isSecure
    )

    // -------------------------------------------------------------------------
    // 1. Queued state
    // -------------------------------------------------------------------------

    @Test
    fun testQueuedState() {
        val record = sentRecord(qosStatus = "WIFI/QUEUED")
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.QUEUED, telemetry.deliveryState)
    }

    @Test
    fun testQueuedStateCaseInsensitive() {
        val record = sentRecord(qosStatus = "queued/wifi")
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.QUEUED, telemetry.deliveryState)
    }

    // -------------------------------------------------------------------------
    // 2. Sent / ACK-pending state (NONE delivery status = just sent, no tracker yet)
    // -------------------------------------------------------------------------

    @Test
    fun testSentStateBeforeTracker() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.NONE, transferId = null)
        val telemetry = RadioMessageStateMapper.map(record)
        // NONE without transfer tracker → ACK_PENDING (optimistic — sent, awaiting system pickup)
        assertEquals(RadioDeliveryState.ACK_PENDING, telemetry.deliveryState)
    }

    // -------------------------------------------------------------------------
    // 3. ACK pending state
    // -------------------------------------------------------------------------

    @Test
    fun testAckPendingState() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.PENDING,
            transferId = 0x0042
        )
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.ACK_PENDING, telemetry.deliveryState)
    }

    // -------------------------------------------------------------------------
    // 4. Acknowledged state
    // -------------------------------------------------------------------------

    @Test
    fun testAcknowledgedState() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 42L
        )
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.ACKNOWLEDGED, telemetry.deliveryState)
        assertEquals(42L, telemetry.ackRttMs)
    }

    @Test
    fun testAcknowledgedNoRtt() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = null
        )
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.ACKNOWLEDGED, telemetry.deliveryState)
        assertNull(telemetry.ackRttMs)
    }

    // -------------------------------------------------------------------------
    // 5. Relayed state (incoming relayed message)
    // -------------------------------------------------------------------------

    @Test
    fun testRelayedIncomingByFlag() {
        val record = receivedRecord(isRelayed = true, hopCount = 2)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.RELAYED, telemetry.deliveryState)
        assertEquals(2, telemetry.hopCount)
    }

    @Test
    fun testRelayedIncomingByHopCount() {
        // hopCount > 1 even without isRelayed flag
        val record = receivedRecord(isRelayed = false, hopCount = 3)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.RELAYED, telemetry.deliveryState)
        assertEquals(3, telemetry.hopCount)
    }

    // -------------------------------------------------------------------------
    // 6. DTN stored state
    // -------------------------------------------------------------------------

    @Test
    fun testDtnStoredState() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.PENDING,
            transferId = 0x00AB,
            isRelayed = true  // pending + relayed = DTN stored
        )
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.DTN_STORED, telemetry.deliveryState)
        assertTrue(telemetry.isDtnPending)
    }

    // -------------------------------------------------------------------------
    // 7. Waiting for route
    // -------------------------------------------------------------------------

    @Test
    fun testWaitingForRouteState() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.PENDING,
            transferId = null,  // no transfer registered = no route
            isRelayed = false
        )
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.WAITING_FOR_ROUTE, telemetry.deliveryState)
    }

    // -------------------------------------------------------------------------
    // 8. Failed state
    // -------------------------------------------------------------------------

    @Test
    fun testFailedState() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.TIMEOUT)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioDeliveryState.FAILED, telemetry.deliveryState)
    }

    // -------------------------------------------------------------------------
    // 9. Emergency priority context
    // -------------------------------------------------------------------------

    @Test
    fun testDistressPriorityContext() {
        val record = sentRecord(priority = MessagePriority.DISTRESS)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioPriorityContext.DISTRESS, telemetry.priorityContext)
        assertTrue(telemetry.priorityContext.isEmergency)
    }

    @Test
    fun testAlertPriorityContext() {
        val record = sentRecord(priority = MessagePriority.ALERT)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioPriorityContext.ALERT, telemetry.priorityContext)
        assertTrue(telemetry.priorityContext.isEmergency)
    }

    @Test
    fun testNormalPriorityNotEmergency() {
        val record = sentRecord(priority = MessagePriority.NORMAL)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioPriorityContext.NORMAL, telemetry.priorityContext)
        assertFalse(telemetry.priorityContext.isEmergency)
    }

    // -------------------------------------------------------------------------
    // 10. Historical message (UNKNOWN transport + no special flags)
    // -------------------------------------------------------------------------

    @Test
    fun testHistoricalMessageUnknownHopCount() {
        // Historical received message with no relay info
        val record = receivedRecord(isRelayed = false, hopCount = 0)
        val telemetry = RadioMessageStateMapper.map(record)
        // Received, not relayed → RECEIVED
        assertEquals(RadioDeliveryState.RECEIVED, telemetry.deliveryState)
        // hopCount = 0 and isRelayed = false → null (UNKNOWN in UI)
        assertNull(telemetry.hopCount)
    }

    // -------------------------------------------------------------------------
    // 11. Transport mapping
    // -------------------------------------------------------------------------

    @Test
    fun testTransportMappingWifi() {
        val record = sentRecord(qosStatus = "WIFI/ACTIVE")
        val telemetry = RadioMessageStateMapper.map(record)
        // qosStatus contains WIFI → "Wi-Fi UDP"
        // But qosStatus = "WIFI/ACTIVE" does NOT contain "QUEUED" so state != QUEUED
        // Transport should be derived from qosStatus: "Wi-Fi UDP"
        assertEquals("Wi-Fi UDP", telemetry.transport)
    }

    @Test
    fun testTransportMappingBluetooth() {
        val record = sentRecord(qosStatus = "BT/ACTIVE")
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals("Bluetooth RFCOMM", telemetry.transport)
    }

    @Test
    fun testTransportMappingHeuristicSentDirect() {
        // No qosStatus, not relayed → Wi-Fi UDP heuristic
        val record = sentRecord(qosStatus = null, isRelayed = false)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals("Wi-Fi UDP", telemetry.transport)
    }

    @Test
    fun testTransportMappingHeuristicRelayed() {
        val record = receivedRecord(isRelayed = true, hopCount = 2)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals("MANET Relay", telemetry.transport)
    }

    // -------------------------------------------------------------------------
    // 12. Hop count mapping
    // -------------------------------------------------------------------------

    @Test
    fun testHopCountFromRecord() {
        val record = sentRecord(hopCount = 3, isRelayed = true, deliveryStatus = DeliveryStatus.DELIVERED)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(3, telemetry.hopCount)
    }

    @Test
    fun testHopCountConservativeFloorWhenRelayedButZero() {
        // isRelayed = true but hopCount = 0 → conservative floor: 2
        val record = receivedRecord(isRelayed = true, hopCount = 0)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(2, telemetry.hopCount)
    }

    @Test
    fun testHopCountNullWhenNoData() {
        val record = receivedRecord(isRelayed = false, hopCount = 0)
        val telemetry = RadioMessageStateMapper.map(record)
        assertNull(telemetry.hopCount)
    }

    // -------------------------------------------------------------------------
    // 13. Invalid / incomplete telemetry (graceful UNKNOWN / null handling)
    // -------------------------------------------------------------------------

    @Test
    fun testNoTransportAvailable() {
        // Received, not relayed, no qosStatus
        val record = receivedRecord(isRelayed = false, hopCount = 0)
        val telemetry = RadioMessageStateMapper.map(record)
        // Should not crash; transport should be null (heuristic can't determine for incoming direct)
        // receivedRecord direction = RECEIVED, isRelayed = false, qosStatus = null
        // deriveTransport: qosStatus null, not isRelayed, not SENT → null
        assertNull(telemetry.transport)
    }

    @Test
    fun testFragmentInfoPresentWhenAvailable() {
        val record = sentRecord(fragmentCount = 4, deliveryStatus = DeliveryStatus.DELIVERED)
        val telemetry = RadioMessageStateMapper.map(record)
        assertTrue(telemetry.isFragmented)
        assertEquals(4, telemetry.fragmentCount)
    }

    @Test
    fun testNoFragmentInfoWhenAbsent() {
        val record = sentRecord(fragmentCount = null)
        val telemetry = RadioMessageStateMapper.map(record)
        assertFalse(telemetry.isFragmented)
        assertNull(telemetry.fragmentCount)
    }

    @Test
    fun testSingleFragmentNotConsideredFragmented() {
        // fragmentCount = 1 → not actually fragmented
        val record = sentRecord(fragmentCount = 1)
        val telemetry = RadioMessageStateMapper.map(record)
        assertFalse(telemetry.isFragmented)
    }

    // -------------------------------------------------------------------------
    // 14. State transition ordering (logic consistency check)
    // -------------------------------------------------------------------------

    @Test
    fun testStateTransitionQueuedToPending() {
        // QoS queued → maps to QUEUED
        val queued = sentRecord(qosStatus = "QUEUED", deliveryStatus = DeliveryStatus.PENDING, transferId = 0x0001)
        assertEquals(RadioDeliveryState.QUEUED, RadioMessageStateMapper.deriveDeliveryState(queued))

        // After leaving queue → PENDING+transferId → ACK_PENDING
        val pending = sentRecord(qosStatus = null, deliveryStatus = DeliveryStatus.PENDING, transferId = 0x0001)
        assertEquals(RadioDeliveryState.ACK_PENDING, RadioMessageStateMapper.deriveDeliveryState(pending))

        // After ACK → ACKNOWLEDGED
        val acked = sentRecord(qosStatus = null, deliveryStatus = DeliveryStatus.DELIVERED)
        assertEquals(RadioDeliveryState.ACKNOWLEDGED, RadioMessageStateMapper.deriveDeliveryState(acked))
    }

    @Test
    fun testStateTransitionSendingToFailed() {
        val sending = sentRecord(deliveryStatus = DeliveryStatus.SENDING)
        assertEquals(RadioDeliveryState.SENDING, RadioMessageStateMapper.deriveDeliveryState(sending))

        val failed = sentRecord(deliveryStatus = DeliveryStatus.TIMEOUT)
        assertEquals(RadioDeliveryState.FAILED, RadioMessageStateMapper.deriveDeliveryState(failed))
    }

    // -------------------------------------------------------------------------
    // 15. Duplicate event handling (idempotence of mapper)
    // -------------------------------------------------------------------------

    @Test
    fun testMapperIsIdempotent() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 55L,
            isSecure = true
        )
        val t1 = RadioMessageStateMapper.map(record)
        val t2 = RadioMessageStateMapper.map(record)

        // Two calls on the same record must yield identical results
        assertEquals(t1.deliveryState, t2.deliveryState)
        assertEquals(t1.ackRttMs, t2.ackRttMs)
        assertEquals(t1.isAuthenticated, t2.isAuthenticated)
        assertEquals(t1.priorityContext, t2.priorityContext)
    }

    // -------------------------------------------------------------------------
    // Authentication status
    // -------------------------------------------------------------------------

    @Test
    fun testAuthenticatedWhenSecure() {
        val record = sentRecord(isSecure = true, deliveryStatus = DeliveryStatus.DELIVERED)
        val telemetry = RadioMessageStateMapper.map(record)
        assertTrue(telemetry.isAuthenticated)
    }

    @Test
    fun testNotAuthenticatedWhenInsecure() {
        val record = sentRecord(isSecure = false)
        val telemetry = RadioMessageStateMapper.map(record)
        assertFalse(telemetry.isAuthenticated)
    }

    // -------------------------------------------------------------------------
    // Priority context round-trip
    // -------------------------------------------------------------------------

    @Test
    fun testImportantPriority() {
        val record = sentRecord(priority = MessagePriority.IMPORTANT)
        val telemetry = RadioMessageStateMapper.map(record)
        assertEquals(RadioPriorityContext.IMPORTANT, telemetry.priorityContext)
        assertFalse(telemetry.priorityContext.isEmergency)
    }

    // -------------------------------------------------------------------------
    // Transport override
    // -------------------------------------------------------------------------

    @Test
    fun testTransportOverrideFromTopology() {
        val record = sentRecord()
        val telemetry = RadioMessageStateMapper.map(record, transportOverride = "Bluetooth RFCOMM")
        assertEquals("Bluetooth RFCOMM", telemetry.transport)
    }
}
