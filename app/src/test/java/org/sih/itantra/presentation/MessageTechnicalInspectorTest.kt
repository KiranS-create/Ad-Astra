package org.sih.itantra.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.MessageTechnicalInspector
import org.sih.itantra.core.message.MessageTechnicalInspectorMapper
import org.sih.itantra.core.message.RadioDeliveryState
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.message.TechnicalFieldStyle
import org.sih.itantra.core.message.TechnicalInspectorField
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.protocol.GeoLocation

/**
 * Deterministic unit tests for Feature 7: Message Technical Inspector.
 *
 * Covers all 23 required test specifications:
 * 1. Message ID
 * 2. Source mapping
 * 3. Destination mapping
 * 4. Timestamp
 * 5. Language
 * 6. Priority
 * 7. Delivery state
 * 8. ACK status
 * 9. Route state
 * 10. Hop count
 * 11. Transport
 * 12. DTN state
 * 13. Fragmentation
 * 14. Reassembly
 * 15. HMAC-SHA256 status
 * 16. CRC/checksum / wire integrity status where available
 * 17. Emergency/distress priority
 * 18. UNKNOWN metadata
 * 19. Incomplete metadata
 * 20. Conflicting metadata
 * 21. No fabricated telemetry
 * 22. Feature 6 state consistency
 * 23. Inspector read-only behavior
 */
class MessageTechnicalInspectorTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun MessageTechnicalInspector.field(sectionTitle: String, label: String): TechnicalInspectorField? {
        val section = sections.firstOrNull { it.title.equals(sectionTitle, ignoreCase = true) } ?: return null
        return section.fields.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }

    private fun sentRecord(
        id: String = "test-msg-101",
        timestamp: Long = 1726000000000L,
        priority: MessagePriority = MessagePriority.NORMAL,
        peer: String = "Node #209071",
        deliveryStatus: DeliveryStatus = DeliveryStatus.NONE,
        isRelayed: Boolean = false,
        hopCount: Int = 0,
        transferId: Short? = null,
        qosStatus: String? = null,
        isSecure: Boolean = false,
        authStatus: String? = null,
        fragmentCount: Int? = null,
        fragmentIndex: Int? = null,
        deliveryLatencyMs: Long? = null,
        measuredLatencyMs: Double = 0.0,
        packetSizeBytes: Int = 128,
        rawAudioEquivalentBytes: Long = 0L,
        location: GeoLocation? = null,
        isSemantic: Boolean = false,
        semanticSavingsBytes: Int? = null
    ) = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = MessageDirection.SENT,
        language = IndicLanguage.ENGLISH,
        priority = priority,
        text = "Tactical transmission payload",
        peer = peer,
        packetSizeBytes = packetSizeBytes,
        rawAudioEquivalentBytes = rawAudioEquivalentBytes,
        measuredLatencyMs = measuredLatencyMs,
        isRelayed = isRelayed,
        hopCount = hopCount,
        location = location,
        isSemantic = isSemantic,
        semanticSavingsBytes = semanticSavingsBytes,
        deliveryStatus = deliveryStatus,
        transferId = transferId,
        fragmentCount = fragmentCount,
        fragmentIndex = fragmentIndex,
        deliveryLatencyMs = deliveryLatencyMs,
        isSecure = isSecure,
        authStatus = authStatus,
        qosStatus = qosStatus
    )

    private fun receivedRecord(
        id: String = "rx-msg-202",
        timestamp: Long = 1726000050000L,
        priority: MessagePriority = MessagePriority.NORMAL,
        peer: String = "Node #883192",
        isRelayed: Boolean = false,
        hopCount: Int = 0,
        isSecure: Boolean = true,
        authStatus: String? = "AUTH ✓",
        packetSizeBytes: Int = 96,
        location: GeoLocation? = null
    ) = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = MessageDirection.RECEIVED,
        language = IndicLanguage.HINDI,
        priority = priority,
        text = "Incoming command payload",
        peer = peer,
        packetSizeBytes = packetSizeBytes,
        rawAudioEquivalentBytes = 0L,
        measuredLatencyMs = 15.0,
        isRelayed = isRelayed,
        hopCount = hopCount,
        location = location,
        isSecure = isSecure,
        authStatus = authStatus
    )

    // -------------------------------------------------------------------------
    // 1. Message ID
    // -------------------------------------------------------------------------
    @Test
    fun testMessageId() {
        val record = sentRecord(id = "msg-alpha-992")
        val inspector = MessageTechnicalInspectorMapper.map(record)
        assertEquals("msg-alpha-992", inspector.messageId)
        val field = inspector.field("MESSAGE", "MESSAGE ID")
        assertNotNull(field)
        assertEquals("msg-alpha-992", field?.value)
    }

    // -------------------------------------------------------------------------
    // 2. Source Mapping
    // -------------------------------------------------------------------------
    @Test
    fun testSourceMappingSent() {
        val record = sentRecord(peer = "Node #441")
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val sourceField = inspector.field("MESSAGE", "SOURCE")
        assertEquals("Local Node (Self)", sourceField?.value)
    }

    @Test
    fun testSourceMappingReceived() {
        val record = receivedRecord(peer = "Node #883192")
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val sourceField = inspector.field("MESSAGE", "SOURCE")
        assertEquals("Node #883192", sourceField?.value)
    }

    // -------------------------------------------------------------------------
    // 3. Destination Mapping
    // -------------------------------------------------------------------------
    @Test
    fun testDestinationMappingSent() {
        val record = sentRecord(peer = "Node #209071")
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val destField = inspector.field("MESSAGE", "DESTINATION")
        assertEquals("Node #209071", destField?.value)
    }

    @Test
    fun testDestinationMappingReceived() {
        val record = receivedRecord(peer = "Node #883192")
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val destField = inspector.field("MESSAGE", "DESTINATION")
        assertEquals("Local Node (Self)", destField?.value)
    }

    // -------------------------------------------------------------------------
    // 4. Timestamp
    // -------------------------------------------------------------------------
    @Test
    fun testTimestampFormatted() {
        val record = sentRecord(timestamp = 1726000000000L)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val tsField = inspector.field("MESSAGE", "TIMESTAMP")
        assertNotNull(tsField)
        assertTrue(tsField?.value?.contains("2024") == true || tsField?.value?.contains("202") == true)
    }

    @Test
    fun testTimestampUnknownWhenNonPositive() {
        val record = sentRecord(timestamp = 0L)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val tsField = inspector.field("MESSAGE", "TIMESTAMP")
        assertEquals("UNKNOWN", tsField?.value)
    }

    // -------------------------------------------------------------------------
    // 5. Language
    // -------------------------------------------------------------------------
    @Test
    fun testLanguageDisplay() {
        val record = sentRecord() // IndicLanguage.ENGLISH
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val langField = inspector.field("MESSAGE", "LANGUAGE")
        assertEquals("English (en)", langField?.value)
    }

    // -------------------------------------------------------------------------
    // 6. Priority
    // -------------------------------------------------------------------------
    @Test
    fun testNormalPriority() {
        val record = sentRecord(priority = MessagePriority.NORMAL)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val prioField = inspector.field("MESSAGE", "PRIORITY")
        assertEquals("NORMAL (P0)", prioField?.value)
        assertFalse(inspector.isEmergency)
    }

    // -------------------------------------------------------------------------
    // 7. Delivery State
    // -------------------------------------------------------------------------
    @Test
    fun testDeliveryStateAcknowledged() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.DELIVERED, deliveryLatencyMs = 45L)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val delField = inspector.field("DELIVERY", "DELIVERY STATE")
        assertNotNull(delField)
        assertTrue(delField?.value?.contains("ACKNOWLEDGED") == true)
    }

    // -------------------------------------------------------------------------
    // 8. ACK Status
    // -------------------------------------------------------------------------
    @Test
    fun testAckStatusValidReceipt() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.DELIVERED, deliveryLatencyMs = 38L)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val ackField = inspector.field("DELIVERY", "ACK STATUS")
        assertEquals("VALID RECEIPT ✓", ackField?.value)
        assertEquals(TechnicalFieldStyle.SUCCESS, ackField?.style)
        val rttField = inspector.field("DELIVERY", "ACK RTT")
        assertEquals("38 ms", rttField?.value)
    }

    @Test
    fun testAckStatusPending() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.PENDING, transferId = 0x0012)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val ackField = inspector.field("DELIVERY", "ACK STATUS")
        assertEquals("AWAITING RECEIPT", ackField?.value)
    }

    @Test
    fun testAckStatusFailed() {
        val record = sentRecord(deliveryStatus = DeliveryStatus.TIMEOUT)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val ackField = inspector.field("DELIVERY", "ACK STATUS")
        assertEquals("TIMEOUT (30s) ✕", ackField?.value)
        assertEquals(TechnicalFieldStyle.ALERT, ackField?.style)
    }

    // -------------------------------------------------------------------------
    // 9. Route State
    // -------------------------------------------------------------------------
    @Test
    fun testRouteStateDirect() {
        val record = sentRecord(hopCount = 1, isRelayed = false)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val routeField = inspector.field("ROUTE", "ROUTE STATE")
        assertEquals("DIRECT (1 HOP)", routeField?.value)
    }

    @Test
    fun testRouteStateRelayed() {
        val record = receivedRecord(hopCount = 3, isRelayed = true)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val routeField = inspector.field("ROUTE", "ROUTE STATE")
        assertEquals("RELAYED (3 HOPS)", routeField?.value)
    }

    // -------------------------------------------------------------------------
    // 10. Hop Count
    // -------------------------------------------------------------------------
    @Test
    fun testHopCountKnown() {
        val record = sentRecord(hopCount = 2, isRelayed = true)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val hopField = inspector.field("ROUTE", "HOP COUNT")
        assertEquals("2", hopField?.value)
    }

    @Test
    fun testHopCountConservativeRelayedFloor() {
        val record = receivedRecord(isRelayed = true, hopCount = 0)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val hopField = inspector.field("ROUTE", "HOP COUNT")
        assertEquals("2", hopField?.value)
    }

    // -------------------------------------------------------------------------
    // 11. Transport
    // -------------------------------------------------------------------------
    @Test
    fun testTransportFromQosWifi() {
        val record = sentRecord(qosStatus = "WIFI/ACTIVE")
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val transportField = inspector.field("ROUTE", "TRANSPORT")
        assertEquals("Wi-Fi UDP", transportField?.value)
    }

    @Test
    fun testTransportFromTopologyOverride() {
        val record = sentRecord()
        val inspector = MessageTechnicalInspectorMapper.map(record, transportOverride = "Bluetooth RFCOMM")
        val transportField = inspector.field("ROUTE", "TRANSPORT")
        assertEquals("Bluetooth RFCOMM", transportField?.value)
    }

    // -------------------------------------------------------------------------
    // 12. DTN State
    // -------------------------------------------------------------------------
    @Test
    fun testDtnSectionPresentWhenStored() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.PENDING,
            transferId = 0x0077,
            isRelayed = true
        )
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val dtnStorage = inspector.field("DTN", "DTN STORAGE")
        assertNotNull(dtnStorage)
        assertEquals("STORED IN DTN BUFFER", dtnStorage?.value)
    }

    @Test
    fun testDtnSectionOmittedWhenNotDtn() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.DELIVERED,
            isRelayed = false
        )
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val dtnSection = inspector.sections.firstOrNull { it.title == "DTN" }
        assertNull(dtnSection)
    }

    // -------------------------------------------------------------------------
    // 13. Fragmentation
    // -------------------------------------------------------------------------
    @Test
    fun testFragmentationPresent() {
        val record = sentRecord(fragmentCount = 4, fragmentIndex = 1)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val statusField = inspector.field("FRAGMENTATION", "STATUS")
        assertEquals("FRAGMENTED (4 PACKETS)", statusField?.value)
        val totalField = inspector.field("FRAGMENTATION", "TOTAL FRAGMENTS")
        assertEquals("4", totalField?.value)
        val indexField = inspector.field("FRAGMENTATION", "FRAGMENT INDEX")
        assertEquals("2 of 4", indexField?.value)
    }

    // -------------------------------------------------------------------------
    // 14. Reassembly
    // -------------------------------------------------------------------------
    @Test
    fun testReassemblyComplete() {
        val record = sentRecord(fragmentCount = 3)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val reassemblyField = inspector.field("FRAGMENTATION", "REASSEMBLY")
        assertEquals("COMPLETE ✓", reassemblyField?.value)
        assertEquals(TechnicalFieldStyle.SUCCESS, reassemblyField?.style)
    }

    @Test
    fun testUnfragmentedSinglePacket() {
        val record = sentRecord(fragmentCount = 1)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val statusField = inspector.field("FRAGMENTATION", "STATUS")
        assertEquals("SINGLE PACKET (1/1)", statusField?.value)
        val reassemblyField = inspector.field("FRAGMENTATION", "REASSEMBLY")
        assertNull(reassemblyField)
    }

    // -------------------------------------------------------------------------
    // 15. HMAC-SHA256 Status (Never claims encryption)
    // -------------------------------------------------------------------------
    @Test
    fun testHmacSha256Valid() {
        val record = sentRecord(isSecure = true, authStatus = "AUTH ✓")
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val hmacField = inspector.field("SECURITY & INTEGRITY", "HMAC-SHA256")
        assertEquals("VALID ✓ (Authenticated)", hmacField?.value)
        assertEquals(TechnicalFieldStyle.SUCCESS, hmacField?.style)
        // Ensure no encryption is claimed
        assertNull(inspector.field("SECURITY & INTEGRITY", "ENCRYPTION"))
        assertNull(inspector.field("SECURITY & INTEGRITY", "ENCRYPTED"))
    }

    @Test
    fun testHmacSha256Unverified() {
        val record = sentRecord(isSecure = false, authStatus = null)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val hmacField = inspector.field("SECURITY & INTEGRITY", "HMAC-SHA256")
        assertEquals("UNVERIFIED", hmacField?.value)
        assertEquals(TechnicalFieldStyle.WARNING, hmacField?.style)
    }

    // -------------------------------------------------------------------------
    // 16. CRC / Wire Integrity status
    // -------------------------------------------------------------------------
    @Test
    fun testWirePayloadSize() {
        val record = sentRecord(packetSizeBytes = 240)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val wireField = inspector.field("SECURITY & INTEGRITY", "WIRE PAYLOAD")
        assertEquals("240 Bytes", wireField?.value)
    }

    @Test
    fun testSemanticSavingsField() {
        val record = sentRecord(isSemantic = true, semanticSavingsBytes = 180)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val savingsField = inspector.field("SECURITY & INTEGRITY", "SEMANTIC SAVINGS")
        assertEquals("-180 B (-82%)", savingsField?.value)
    }

    // -------------------------------------------------------------------------
    // 17. Emergency / Distress Priority
    // -------------------------------------------------------------------------
    @Test
    fun testEmergencyDistressPriority() {
        val record = sentRecord(priority = MessagePriority.DISTRESS)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        assertTrue(inspector.isEmergency)
        val typeField = inspector.field("MESSAGE", "TYPE")
        assertEquals("EMERGENCY DISTRESS", typeField?.value)
        assertEquals(TechnicalFieldStyle.ALERT, typeField?.style)
        val prioField = inspector.field("MESSAGE", "PRIORITY")
        assertEquals("DISTRESS (P3)", prioField?.value)
    }

    @Test
    fun testEmergencyAlertPriority() {
        val record = sentRecord(priority = MessagePriority.ALERT)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        assertTrue(inspector.isEmergency)
        val typeField = inspector.field("MESSAGE", "TYPE")
        assertEquals("EMERGENCY ALERT", typeField?.value)
        assertEquals(TechnicalFieldStyle.WARNING, typeField?.style)
    }

    // -------------------------------------------------------------------------
    // 18. UNKNOWN Metadata
    // -------------------------------------------------------------------------
    @Test
    fun testUnknownMetadataHandling() {
        // Historical incoming message with minimal metadata
        val historicalRecord = MessageRecord(
            id = "historical-001",
            timestamp = 0L,
            direction = MessageDirection.RECEIVED,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.NORMAL,
            text = "Legacy message",
            peer = "",
            packetSizeBytes = 64,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 0.0,
            isRelayed = false,
            hopCount = 0
        )
        val inspector = MessageTechnicalInspectorMapper.map(historicalRecord)
        assertEquals("UNKNOWN", inspector.field("MESSAGE", "TIMESTAMP")?.value)
        assertEquals("UNKNOWN", inspector.field("MESSAGE", "SOURCE")?.value)
        assertEquals("UNKNOWN", inspector.field("ROUTE", "ROUTE STATE")?.value)
        assertEquals("UNKNOWN", inspector.field("ROUTE", "HOP COUNT")?.value)
    }

    // -------------------------------------------------------------------------
    // 19. Incomplete Metadata (Graceful fallback)
    // -------------------------------------------------------------------------
    @Test
    fun testIncompleteMetadataDoesNotCrash() {
        val incompleteRecord = sentRecord(
            transferId = null,
            qosStatus = null,
            deliveryLatencyMs = null,
            fragmentCount = null,
            authStatus = null,
            location = null
        )
        val inspector = MessageTechnicalInspectorMapper.map(incompleteRecord)
        assertNotNull(inspector)
        assertEquals(5, inspector.sections.size) // MESSAGE, DELIVERY, ROUTE, FRAGMENTATION, SECURITY
    }

    // -------------------------------------------------------------------------
    // 20. Conflicting Metadata
    // -------------------------------------------------------------------------
    @Test
    fun testConflictingHopCountAndRelayedFlag() {
        // isRelayed is false, but hopCount is 3 -> Hop count takes precedence in route state
        val record = sentRecord(isRelayed = false, hopCount = 3)
        val inspector = MessageTechnicalInspectorMapper.map(record)
        val routeField = inspector.field("ROUTE", "ROUTE STATE")
        assertEquals("RELAYED (3 HOPS)", routeField?.value)
    }

    // -------------------------------------------------------------------------
    // 21. No Fabricated Telemetry
    // -------------------------------------------------------------------------
    @Test
    fun testNoFabricatedFields() {
        val record = sentRecord(
            isSecure = false,
            location = null,
            rawAudioEquivalentBytes = 0L,
            semanticSavingsBytes = null
        )
        val inspector = MessageTechnicalInspectorMapper.map(record)
        // Ensure optional fields that don't exist are NOT fabricated
        assertNull(inspector.field("SECURITY & INTEGRITY", "GEO LOCATION"))
        assertNull(inspector.field("SECURITY & INTEGRITY", "RAW AUDIO EQUIV"))
        assertNull(inspector.field("SECURITY & INTEGRITY", "SEMANTIC SAVINGS"))
        assertNull(inspector.field("DELIVERY", "ACK RTT"))
    }

    // -------------------------------------------------------------------------
    // 22. Feature 6 State Consistency
    // -------------------------------------------------------------------------
    @Test
    fun testConsistencyWithFeature6Telemetry() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 64L,
            isRelayed = true,
            hopCount = 2,
            isSecure = true
        )
        val feature6Telemetry = RadioMessageStateMapper.map(record)
        val inspector = MessageTechnicalInspectorMapper.map(record, feature6Telemetry)

        assertEquals(
            "${feature6Telemetry.deliveryState.icon} ${feature6Telemetry.deliveryState.label.uppercase()}",
            inspector.field("DELIVERY", "DELIVERY STATE")?.value
        )
        assertEquals("${feature6Telemetry.ackRttMs} ms", inspector.field("DELIVERY", "ACK RTT")?.value)
        assertEquals(feature6Telemetry.priorityContext, inspector.priorityContext)
    }

    // -------------------------------------------------------------------------
    // 23. Inspector Read-Only Behavior (Idempotence & No Side Effects)
    // -------------------------------------------------------------------------
    @Test
    fun testInspectorIsPureAndIdempotent() {
        val record = sentRecord(
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 50L,
            isSecure = true,
            priority = MessagePriority.DISTRESS
        )

        val inspector1 = MessageTechnicalInspectorMapper.map(record)
        val inspector2 = MessageTechnicalInspectorMapper.map(record)

        assertEquals(inspector1.messageId, inspector2.messageId)
        assertEquals(inspector1.isEmergency, inspector2.isEmergency)
        assertEquals(inspector1.sections.size, inspector2.sections.size)

        for (i in inspector1.sections.indices) {
            val sec1 = inspector1.sections[i]
            val sec2 = inspector2.sections[i]
            assertEquals(sec1.title, sec2.title)
            assertEquals(sec1.fields.size, sec2.fields.size)
            for (j in sec1.fields.indices) {
                assertEquals(sec1.fields[j], sec2.fields[j])
            }
        }
    }
}
