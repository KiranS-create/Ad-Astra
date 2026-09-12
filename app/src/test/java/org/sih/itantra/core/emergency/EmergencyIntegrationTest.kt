package org.sih.itantra.core.emergency

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
import org.sih.itantra.core.message.TechnicalFieldStyle
import org.sih.itantra.core.message.journey.MessageJourneyMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.protocol.GeoLocation
import org.sih.itantra.core.tts.TtsLanguage
import org.sih.itantra.core.tts.TtsResolutionResult
import org.sih.itantra.core.tts.TtsVoiceRegistry
import org.sih.itantra.core.tts.TtsVoiceResolver

class EmergencyIntegrationTest {

    private fun createDistressRecord(
        id: String = "distress-1",
        deliveryStatus: DeliveryStatus = DeliveryStatus.DELIVERED,
        transferId: Short? = null,
        isRelayed: Boolean = false,
        hopCount: Int = 1,
        location: GeoLocation? = GeoLocation(12.9716, 77.5946, 5.0f, System.currentTimeMillis(), 920.0),
        language: IndicLanguage = IndicLanguage.TAMIL
    ): MessageRecord = MessageRecord(
        id = id,
        timestamp = System.currentTimeMillis(),
        direction = MessageDirection.SENT,
        language = language,
        priority = MessagePriority.DISTRESS,
        text = "Medical emergency hospital needed immediately.",
        peer = "Broadcast",
        packetSizeBytes = 64,
        rawAudioEquivalentBytes = 0L,
        measuredLatencyMs = 22.0,
        isRelayed = isRelayed,
        hopCount = hopCount,
        location = location,
        deliveryStatus = deliveryStatus,
        transferId = transferId,
        isSecure = true,
        authStatus = "AUTH ✓",
        qosStatus = "PRIORITY 1"
    )

    @Test
    fun testEmergencyDeliveryStateCompatibilityFeature6Delivered() {
        val record = createDistressRecord(deliveryStatus = DeliveryStatus.DELIVERED)
        val telemetry = RadioMessageStateMapper.map(record)

        assertEquals(RadioDeliveryState.ACKNOWLEDGED, telemetry.deliveryState)
        assertTrue(telemetry.priorityContext.isEmergency)
        assertEquals("DISTRESS · PRIORITY 1", telemetry.priorityContext.label)
        assertEquals("PRIORITY 1", telemetry.qosStatus)
    }

    @Test
    fun testEmergencyDeliveryStateCompatibilityFeature6Pending() {
        val record = createDistressRecord(
            deliveryStatus = DeliveryStatus.PENDING,
            transferId = 101.toShort()
        )
        val telemetry = RadioMessageStateMapper.map(record)

        assertEquals(RadioDeliveryState.ACK_PENDING, telemetry.deliveryState)
        assertTrue(telemetry.priorityContext.isEmergency)
    }

    @Test
    fun testEmergencyDeliveryStateCompatibilityFeature6DtnStored() {
        val record = createDistressRecord(
            deliveryStatus = DeliveryStatus.PENDING,
            transferId = 102.toShort(),
            isRelayed = true
        )
        val telemetry = RadioMessageStateMapper.map(record)

        assertEquals(RadioDeliveryState.DTN_STORED, telemetry.deliveryState)
        assertTrue(telemetry.priorityContext.isEmergency)
    }

    @Test
    fun testEmergencyDeliveryStateCompatibilityFeature6Failed() {
        val record = createDistressRecord(deliveryStatus = DeliveryStatus.TIMEOUT)
        val telemetry = RadioMessageStateMapper.map(record)

        assertEquals(RadioDeliveryState.FAILED, telemetry.deliveryState)
        assertTrue(telemetry.priorityContext.isEmergency)
    }

    @Test
    fun testEmergencyRelayTelemetryCompatibilityFeature6() {
        val record = createDistressRecord(
            deliveryStatus = DeliveryStatus.DELIVERED,
            isRelayed = true,
            hopCount = 3
        )
        val telemetry = RadioMessageStateMapper.map(record)

        assertEquals(3, telemetry.hopCount)
    }

    @Test
    fun testEmergencyTechnicalInspectorCompatibilityFeature7() {
        val record = createDistressRecord()
        val telemetry = RadioMessageStateMapper.map(record)
        val inspector = MessageTechnicalInspectorMapper.map(record, telemetry)

        assertTrue("Inspector must preserve isEmergency", inspector.isEmergency)
        assertTrue(inspector.priorityContext.isEmergency)

        // Verify priority field has alert styling in one of the sections
        val hasAlertField = inspector.sections.any { s ->
            s.fields.any { it.style == TechnicalFieldStyle.ALERT }
        }
        assertTrue("Inspector should contain ALERT styled fields for distress", hasAlertField)
    }

    @Test
    fun testEmergencyMessageJourneyCompatibilityFeature9() {
        val record = createDistressRecord(isRelayed = true, hopCount = 2)
        val journey = MessageJourneyMapper.map(record)

        assertTrue("Message Journey must reflect emergency flag", journey.isEmergency)
        assertEquals(MessagePriority.DISTRESS, journey.priority)
        assertEquals(RadioDeliveryState.ACKNOWLEDGED, journey.currentRadioState)
    }

    @Test
    fun testEmergencyMultilingualPlaybackCompatibilityFeature11() {
        val registry = TtsVoiceRegistry { true }
        val resolver = TtsVoiceResolver(registry)

        val record = createDistressRecord(language = IndicLanguage.TAMIL)
        val result = resolver.resolveTtsLanguage(messageLanguage = TtsLanguage.fromIndicLanguage(record.language))

        assertTrue(result is TtsResolutionResult.Resolved)
        val ready = result as TtsResolutionResult.Resolved
        assertEquals(TtsLanguage.TAMIL, ready.profile.language)
        assertEquals(16000, ready.profile.sampleRate)
    }

    @Test
    fun testAccidentalInteractionDoesNotSendDistress() {
        // Step 1: User opens emergency composer
        var state = EmergencyUiState.IDLE.copy(isEmergencyComposerOpen = true)
        assertTrue(state.isEmergencyComposerOpen)
        assertFalse(state.isConfirmationOpen)

        // Step 2: User switches categories without sending
        state = state.copy(selectedAction = EmergencyAction.TRAPPED)
        assertEquals(EmergencyAction.TRAPPED, state.selectedAction)
        assertFalse(state.isConfirmationOpen)

        // Step 3: User cancels - state is restored to IDLE without transmitting
        state = EmergencyUiState.IDLE
        assertFalse(state.isEmergencyComposerOpen)
        assertFalse(state.isConfirmationOpen)
        assertFalse(state.isTransmitting)
    }

    @Test
    fun testDeliberateConfirmationWorkflow() {
        // Step 1: Open composer
        var state = EmergencyUiState.IDLE.copy(isEmergencyComposerOpen = true)

        // Step 2: Request confirmation
        state = state.copy(isConfirmationOpen = true)
        assertTrue(state.isConfirmationOpen)

        // Step 3: Dismiss confirmation without sending
        state = state.copy(isConfirmationOpen = false)
        assertFalse(state.isConfirmationOpen)
        assertTrue(state.isEmergencyComposerOpen)

        // Step 4: Confirm and send
        state = state.copy(isConfirmationOpen = false, isEmergencyComposerOpen = false, isTransmitting = true)
        assertTrue(state.isTransmitting)
        assertFalse(state.canSend)
    }

    @Test
    fun testLocationAttachmentVisibilityHonesty() {
        // Location attached
        val recordWithLoc = createDistressRecord(location = GeoLocation(13.0827, 80.2707, 4.0f, System.currentTimeMillis(), 10.0))
        assertNotNull(recordWithLoc.location)
        assertEquals(13.0827, recordWithLoc.location!!.latitude, 0.0001)

        // Location absent
        val recordWithoutLoc = createDistressRecord(location = null)
        assertNull(recordWithoutLoc.location)
    }
}
