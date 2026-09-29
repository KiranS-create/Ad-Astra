package org.sih.itantra.core.protocol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.crypto.AuthStatus
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.discovery.DiscoveredRawDevice
import org.sih.itantra.core.discovery.DiscoverySourceType
import org.sih.itantra.core.discovery.LocateModeEngine
import org.sih.itantra.core.discovery.LocateProximityState
import org.sih.itantra.core.discovery.LocateRssiTrend
import org.sih.itantra.core.discovery.NearbyDeviceRepository
import org.sih.itantra.core.emergency.EmergencyAction
import org.sih.itantra.core.message.MessageTechnicalInspectorMapper
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord

/**
 * Comprehensive verification of the Emergency Rescue Communication Stack:
 * 1. 1-byte emergency bypass serialization & deserialization
 * 2. Backward compatibility with standard 6-byte semantic commands
 * 3. Exact wire frame size verification (41B unauthenticated/authenticated bounds, 73B with GPS)
 * 4. Real byte-saving telemetry & inspector presentation
 * 5. BLE RSSI-based Locate Mode Engine (EMA, trend, proximity bands, filtering, staleness)
 * 6. Emergency GPS + proximity integrated workflow
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyRescueCommunicationStackTest {

    private val sampleLocation = GeoLocation(
        latitude = 11.016844,
        longitude = 76.955832,
        accuracy = 8.5f,
        timestamp = 1725500000000L,
        altitude = 420.0
    )

    // =========================================================================
    // 1. 1-BYTE EMERGENCY BYPASS PROTOCOL
    // =========================================================================

    @Test
    fun testAllBypassCodesSerializeToExactlyOneByte() {
        for (bypass in EmergencyBypassCode.entries) {
            val cmd = SemanticCommand(
                category = bypass.category,
                subtype = bypass.subtype,
                count = 0,
                severity = EmergencySeverity.CRITICAL,
                parameter = 0,
                _bypassCode = bypass
            )
            assertTrue("Command must recognize bypass status", cmd.is1ByteBypass)
            assertEquals("Bypass code must match", bypass, cmd.bypassCode)

            val serialized = cmd.serialize1Byte()
            assertEquals("serialize1Byte must return exactly 1 byte", 1, serialized.size)
            assertEquals("Byte value must match bypass code", bypass.code, serialized[0])

            // Deserialization round trip
            val deserialized = SemanticCommand.deserialize(serialized)
            assertNotNull("1-byte payload must deserialize cleanly", deserialized)
            assertTrue(deserialized!!.is1ByteBypass)
            assertEquals(bypass.category, deserialized.category)
            assertEquals(bypass.subtype, deserialized.subtype)
            assertEquals(EmergencySeverity.CRITICAL, deserialized.severity)
            assertEquals(0, deserialized.count)
            assertEquals(0.toShort(), deserialized.parameter)
        }
    }

    @Test
    fun testBackwardCompatibilityWithLegacy6ByteSemanticCommand() {
        val legacyCmd = SemanticCommand(
            category = EmergencyCategory.FIRE,
            subtype = EmergencySubtype.BUILDING,
            count = 5,
            severity = EmergencySeverity.CRITICAL,
            parameter = 104
        )
        val bytes = legacyCmd.serialize()
        assertEquals(6, bytes.size)

        val deserialized = SemanticCommand.deserialize(bytes)
        assertNotNull(deserialized)
        assertFalse("Legacy 6-byte command should not report is1ByteBypass", deserialized!!.is1ByteBypass)
        assertEquals(EmergencyCategory.FIRE, deserialized.category)
        assertEquals(EmergencySubtype.BUILDING, deserialized.subtype)
        assertEquals(5, deserialized.count)
        assertEquals(104.toShort(), deserialized.parameter)
    }

    @Test
    fun testCorruptPayloadRejection() {
        // Empty bytes
        assertNull(SemanticCommand.deserialize(ByteArray(0)))
        // 2-5 bytes (neither 1-byte bypass nor 6-byte semantic)
        assertNull(SemanticCommand.deserialize(ByteArray(2)))
        assertNull(SemanticCommand.deserialize(ByteArray(3)))
        assertNull(SemanticCommand.deserialize(ByteArray(4)))
        assertNull(SemanticCommand.deserialize(ByteArray(5)))
        // Invalid bypass byte code
        assertNull(SemanticCommand.deserialize(byteArrayOf(0x7F)))
    }

    @Test
    fun testEmergencyActionToSemanticCommandBypassBinding() {
        for (action in EmergencyAction.entries) {
            val cmd = action.toSemanticCommand()
            assertTrue("EmergencyAction must produce bypass-aware SemanticCommand", cmd.is1ByteBypass)
            assertEquals(action.bypassCode, cmd.bypassCode)
            assertEquals(1, cmd.serialize1Byte().size)
        }
    }

    // =========================================================================
    // 2. WIRE FRAME SIZING & PACKET SERIALIZATION
    // =========================================================================

    @Test
    fun test1ByteBypassWireFrameSizeWithoutLocation() {
        val bypass = EmergencyBypassCode.MEDICAL_INJURED
        val payload = byteArrayOf(bypass.code)
        val cmd = SemanticCommand(bypass.category, bypass.subtype, 0, EmergencySeverity.CRITICAL, 0, bypass)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 1,
            timestamp = 1725500000000L,
            sourceDeviceId = 101,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = payload,
            location = null,
            semanticCommand = cmd
        )

        val wire = PacketSerializer.serialize(packet)
        // 28B Header + 1B Payload + 4B CRC = 33B (unauthenticated)
        // With 8B AuthTag (if authenticated): 41B
        val expectedWireSize = Packet.HEADER_SIZE_BYTES + 1 + Packet.CRC_SIZE_BYTES
        assertEquals(expectedWireSize, wire.size)
        assertEquals(33, wire.size)

        // Authenticated variant
        val authPacket = packet.copy(
            flags = (packet.flags.toInt() or Packet.FLAG_AUTHENTICATED).toByte(),
            authTag = ByteArray(8) { 0x01 }
        )
        val authWire = PacketSerializer.serialize(authPacket)
        assertEquals(41, authWire.size)

        // Verify deserialization succeeds and preserves 1-byte semantic payload
        val decoded = PacketSerializer.deserialize(authWire)
        assertTrue(decoded.isSemantic)
        assertNotNull(decoded.semanticCommand)
        assertEquals(EmergencyCategory.MEDICAL, decoded.semanticCommand!!.category)
        assertEquals(EmergencySubtype.INJURED, decoded.semanticCommand!!.subtype)
        assertEquals(1, decoded.payload.size)
    }

    @Test
    fun test1ByteBypassWireFrameSizeWithLocation() {
        val bypass = EmergencyBypassCode.TRAPPED
        val payload = byteArrayOf(bypass.code)
        val cmd = SemanticCommand(bypass.category, bypass.subtype, 0, EmergencySeverity.CRITICAL, 0, bypass)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = (Packet.FLAG_SEMANTIC or Packet.FLAG_HAS_LOCATION or Packet.FLAG_AUTHENTICATED).toByte(),
            sequenceNumber = 2,
            timestamp = 1725500000000L,
            sourceDeviceId = 102,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = payload,
            location = sampleLocation,
            semanticCommand = cmd,
            authTag = ByteArray(8) { 0x02 }
        )

        val wire = PacketSerializer.serialize(packet)
        // 28B Header + 32B Location + 1B Payload + 8B Auth + 4B CRC = 73 Bytes!
        assertEquals(73, wire.size)

        val decoded = PacketSerializer.deserialize(wire)
        assertTrue(decoded.hasLocation)
        assertNotNull(decoded.location)
        assertEquals(sampleLocation.latitude, decoded.location!!.latitude, 0.0001)
        assertEquals(1, decoded.payload.size)
        assertEquals(EmergencyCategory.TRAPPED, decoded.semanticCommand!!.category)
    }

    // =========================================================================
    // 3. REAL BYTE-SAVING TELEMETRY & INSPECTOR
    // =========================================================================

    @Test
    fun testTechnicalInspectorTelemetryFor1ByteBypass() {
        val record = MessageRecord(
            id = "msg-bypass-1",
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.DISTRESS,
            text = "🚨 MEDICAL EMERGENCY\nINJURIES REPORTED",
            peer = "Broadcast",
            packetSizeBytes = 41,
            rawAudioEquivalentBytes = 0L,
            measuredLatencyMs = 0.0,
            location = null,
            isSemantic = true,
            semanticSummary = "SEMANTIC • MEDICAL • INJURED",
            semanticSavingsBytes = 38,
            isSecure = true,
            authStatus = "AUTH ✓",
            representationMode = "EMERGENCY_1BYTE",
            payloadSizeBytes = 1,
            wireFrameBytes = 41,
            savingsPercentage = 97.4
        )

        val radioTelemetry = RadioMessageStateMapper.map(record)
        val inspector = MessageTechnicalInspectorMapper.map(record, radioTelemetry)

        // Find representation mode field
        val vbrField = inspector.sections.flatMap { it.fields }.firstOrNull { it.label == "VBR REPRESENTATION" }
        assertNotNull(vbrField)
        assertEquals("1-BYTE EMERGENCY BYPASS", vbrField!!.value)

        // Find payload field
        val payloadField = inspector.sections.flatMap { it.fields }.firstOrNull { it.label == "APP PAYLOAD" }
        assertNotNull(payloadField)
        assertEquals("1 Byte (1-Byte Bypass)", payloadField!!.value)

        // Find wire frame field
        val wireField = inspector.sections.flatMap { it.fields }.firstOrNull { it.label == "WIRE FRAME" }
        assertNotNull(wireField)
        assertEquals("41 Bytes", wireField!!.value)

        // Find semantic savings field
        val savingsField = inspector.sections.flatMap { it.fields }.firstOrNull { it.label == "SEMANTIC SAVINGS" }
        assertNotNull(savingsField)
        assertTrue(savingsField!!.value.contains("-38 B"))
        assertTrue(savingsField.value.contains("-97%"))
    }

    // =========================================================================
    // 4. BLE RSSI-BASED LOCATE MODE ENGINE
    // =========================================================================

    @Test
    fun testLocateModeProximityBands() {
        assertEquals(LocateProximityState.VERY_NEAR, LocateModeEngine.categorizeProximity(-45.0))
        assertEquals(LocateProximityState.VERY_NEAR, LocateModeEngine.categorizeProximity(-55.0))
        assertEquals(LocateProximityState.NEAR, LocateModeEngine.categorizeProximity(-55.1))
        assertEquals(LocateProximityState.NEAR, LocateModeEngine.categorizeProximity(-65.0))
        assertEquals(LocateProximityState.CLOSER, LocateModeEngine.categorizeProximity(-65.1))
        assertEquals(LocateProximityState.CLOSER, LocateModeEngine.categorizeProximity(-75.0))
        assertEquals(LocateProximityState.FAR, LocateModeEngine.categorizeProximity(-75.1))
        assertEquals(LocateProximityState.FAR, LocateModeEngine.categorizeProximity(-90.0))
        assertEquals(LocateProximityState.UNKNOWN, LocateModeEngine.categorizeProximity(null))
    }

    @Test
    fun testLocateModeEmaSmoothing() {
        val discoveryRepo = NearbyDeviceRepository(localNodeId = 1)
        val engine = LocateModeEngine(discoveryRepository = discoveryRepo)

        // Emit sample 1: -80 dBm
        engine.processRssiSample(-80)
        var state = engine.sessionState.value
        assertEquals("First reading initializes EMA directly", -80.0, state.smoothedRssi!!, 0.01)
        assertEquals(LocateProximityState.FAR, state.proximityState)

        // Emit sample 2: -80 dBm (baseline establishing)
        engine.processRssiSample(-80)

        // Emit sample 3: -50 dBm (moving closer, delta >= 3dBm, sampleCount >= 3)
        // EMA = 0.35 * -50 + 0.65 * -80 = -17.5 - 52.0 = -69.5 dBm
        engine.processRssiSample(-50)
        state = engine.sessionState.value
        assertEquals(-69.5, state.smoothedRssi!!, 0.1)
        assertEquals(LocateProximityState.CLOSER, state.proximityState)
        assertEquals(LocateRssiTrend.CLOSER, state.trend)
    }

    @Test
    fun testLocateModeTargetFiltering() = runTest {
        val discoveryRepo = NearbyDeviceRepository(localNodeId = 1, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        val engine = LocateModeEngine(discoveryRepository = discoveryRepo, dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)

        try {
            engine.startLocating(targetNodeId = 555, enableWatchdog = false)

            // Emit sample for DIFFERENT node 999
            discoveryRepo.ingestRawDevices(listOf(createTestRawDevice(999, -40)))
            var state = engine.sessionState.value
            assertEquals("Different node must not trigger readings", 0, state.sampleCount)
            assertNull(state.smoothedRssi)

            // Emit sample for TARGET node 555
            discoveryRepo.ingestRawDevices(listOf(createTestRawDevice(555, -45)))
            state = engine.sessionState.value
            assertEquals("Target node must trigger reading", 1, state.sampleCount)
            assertEquals(-45.0, state.smoothedRssi!!, 0.01)
            assertEquals(LocateProximityState.VERY_NEAR, state.proximityState)
        } finally {
            engine.stopLocating()
        }
    }

    @Test
    fun test1ByteBypassHmacSha256AuthenticationAndTamperDetection() {
        val dummyKey = ByteArray(32) { 0x42 }
        val bypass = EmergencyBypassCode.FIRE
        val cmd = SemanticCommand(bypass.category, bypass.subtype, 0, EmergencySeverity.CRITICAL, 0.toShort(), bypass)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 42.toShort(),
            timestamp = 1725500000000L,
            sourceDeviceId = 101,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.HINDI,
            payload = byteArrayOf(bypass.code),
            location = null,
            semanticCommand = cmd
        )

        // Sign packet
        val signedPacket = PacketAuthenticator.sign(packet, dummyKey)
        assertNotNull(signedPacket.authTag)
        assertEquals(Packet.AUTH_TAG_SIZE_BYTES, signedPacket.authTag!!.size)
        assertTrue(signedPacket.isAuthenticated)

        // Verify valid signature
        val validResult = PacketAuthenticator.verify(signedPacket, dummyKey)
        assertTrue("Signature on 1-byte bypass packet must be valid", validResult.isValid)
        assertEquals(AuthStatus.VALID, validResult.status)

        // Tamper with payload byte (modify bypass code from 0x01 to 0x02)
        val tamperedPayload = byteArrayOf(EmergencyBypassCode.TRAPPED.code)
        val tamperedPacket = signedPacket.copy(payload = tamperedPayload)
        val invalidResult = PacketAuthenticator.verify(tamperedPacket, dummyKey)
        assertFalse("Tampered 1-byte payload must fail verification", invalidResult.isValid)
        assertEquals(AuthStatus.INVALID_TAG, invalidResult.status)
    }

    @Test
    fun testLocateModeTrendDetectionFullCycle() {
        var currentTime = 1000000L
        val discoveryRepo = NearbyDeviceRepository(localNodeId = 1)
        val engine = LocateModeEngine(
            discoveryRepository = discoveryRepo,
            timeProvider = { currentTime }
        )

        // Step 1: Initial readings establishing baseline at -70 dBm
        engine.processRssiSample(-70)
        engine.processRssiSample(-70)
        engine.processRssiSample(-70)
        assertEquals("3 steady samples produce STABLE trend", LocateRssiTrend.STABLE, engine.sessionState.value.trend)

        // Step 2: Signal strengthens rapidly: -50 dBm
        // delta > +3 dBm -> CLOSER
        engine.processRssiSample(-50)
        assertEquals("Signal strengthening produces CLOSER trend", LocateRssiTrend.CLOSER, engine.sessionState.value.trend)

        // Step 3: Let EMA converge at -50 dBm
        repeat(5) {
            engine.processRssiSample(-50)
        }
        // Advance 6 seconds so previous samples fall out of window
        currentTime += 6000L
        engine.processRssiSample(-50)
        engine.processRssiSample(-50)
        engine.processRssiSample(-50)
        assertEquals("Steady converged samples produce STABLE trend", LocateRssiTrend.STABLE, engine.sessionState.value.trend)

        // Step 4: Signal drops rapidly to -80 dBm
        // delta < -3 dBm -> FARTHER
        engine.processRssiSample(-80)
        assertEquals("Signal weakening produces FARTHER trend", LocateRssiTrend.FARTHER, engine.sessionState.value.trend)
    }

    @Test
    fun testLocateModeStalenessWatchdog() {
        var currentTime = 1000000L
        val discoveryRepo = NearbyDeviceRepository(localNodeId = 1)
        val engine = LocateModeEngine(
            discoveryRepository = discoveryRepo,
            timeProvider = { currentTime }
        )

        engine.startLocating(targetNodeId = 300, enableWatchdog = false)

        // Emit a sample
        engine.processRssiSample(-60)
        assertFalse(engine.sessionState.value.isStale)
        assertEquals(LocateProximityState.NEAR, engine.sessionState.value.proximityState)

        // Advance time past STALE_TIMEOUT_MS (15,000 ms)
        currentTime += 16000L
        engine.checkStale(currentTime)

        val staleState = engine.sessionState.value
        assertTrue("Session should mark stale after 15s without readings", staleState.isStale)
        assertEquals(LocateProximityState.UNKNOWN, staleState.proximityState)
        assertEquals(LocateRssiTrend.UNKNOWN, staleState.trend)
        assertTrue(staleState.statusMessage.contains("SIGNAL LOST"))

        engine.stopLocating()
    }

    private fun createTestRawDevice(nodeId: Int, rssi: Int): DiscoveredRawDevice {
        return DiscoveredRawDevice(
            deviceId = "node-$nodeId",
            nodeId = nodeId,
            callsign = "NODE $nodeId",
            sourceType = DiscoverySourceType.BLE,
            rssi = rssi
        )
    }
}
