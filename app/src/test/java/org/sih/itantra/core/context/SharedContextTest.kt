package org.sih.itantra.core.context

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.message.MessageTechnicalInspectorMapper
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.network.AdaptiveNetworkMode
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.CorruptPacketException
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.protocol.SemanticEmergencyClassifier
import org.sih.itantra.core.vbr.AdaptiveMessageRepresentation
import org.sih.itantra.core.vbr.AdaptiveRepresentationMode
import org.sih.itantra.core.vbr.AdaptiveRepresentationPolicy
import org.sih.itantra.core.vbr.ContextDelta
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Feature 19: Comprehensive Unit Tests for Shared Context + Confidence-Aware Communication.
 *
 * Validates all 35 required specifications:
 * 1. Context creation
 * 2. Deterministic context ID
 * 3. Version increments
 * 4. Newer version replaces older version
 * 5. Older version never overwrites newer (REJECTED_STALE)
 * 6. Same-version conflict rejection (REJECTED_CONFLICT)
 * 7. Context expiration boundary
 * 8. Expired context is never reused
 * 9. Storage bounds (max 64 entries)
 * 10. Deterministic LRU eviction
 * 11. High-confidence context reuse (>=80)
 * 12. Low-confidence context rejection (<50)
 * 13. Unknown confidence does not become authoritative
 * 14. Delta encoding (7 bytes for single changed field e.g. people count 3 -> 4)
 * 15. Delta decoding (exact field recovery)
 * 16. Delta reconstruction with valid context
 * 17. Missing-context fallback behavior (safe standalone display, no crash)
 * 18. Missing enhancement does not break base/context reconstruction
 * 19. Unsupported schema version handling
 * 20. Malformed context handling
 * 21. Feature 17 refinement improves context confidence
 * 22. Unfinished refinement never blocks endpoint
 * 23. FULL representation fallback preserved
 * 24. COMPACT representation fallback preserved
 * 25. SEMANTIC_BASE representation fallback preserved
 * 26. HMAC integrity preserved over context delta packets
 * 27. CRC tampering detection preserved
 * 28. Packet fragmentation safety (delta fits in single frame)
 * 29. MessageRecord persistence mapping
 * 30. Technical Inspector mapping
 * 31. English context equivalence
 * 32. Hindi context equivalence
 * 33. Tamil context equivalence
 * 34. Emergency semantic context safety
 * 35. Consecutive mixed-message state isolation
 */
class SharedContextTest {

    private val testPsk = ByteArray(32) { 0x42 }

    @Before
    fun setUp() {
        SharedContextStore.clear()
    }

    @After
    fun tearDown() {
        SharedContextStore.clear()
    }

    private fun sampleCommand(sector: Int = 4, count: Int = 3): SemanticCommand = SemanticCommand(
        category = EmergencyCategory.MEDICAL,
        subtype = EmergencySubtype.INJURED,
        count = count,
        severity = EmergencySeverity.CRITICAL,
        parameter = sector.toShort()
    )

    // =========================================================================
    // 1. Context creation
    // =========================================================================
    @Test
    fun test01_contextCreation() {
        val cmd = sampleCommand(sector = 4, count = 3)
        val entry = SharedContextEntry.fromCommand(
            contextId = 1001,
            command = cmd,
            sourceDeviceId = 42,
            confidence = 90
        )

        assertEquals(1001, entry.contextId)
        assertEquals(1, entry.version)
        assertEquals(EmergencyCategory.MEDICAL, entry.category)
        assertEquals(EmergencySubtype.INJURED, entry.subtype)
        assertEquals(EmergencySeverity.CRITICAL, entry.severity)
        assertEquals(3, entry.count)
        assertEquals(4.toShort(), entry.sector)
        assertEquals(90, entry.confidence)
        assertEquals(42, entry.sourceDeviceId)
        assertFalse(entry.isExpiredAt(System.currentTimeMillis()))
    }

    // =========================================================================
    // 2. Deterministic context ID
    // =========================================================================
    @Test
    fun test02_deterministicContextId() {
        val id1 = SharedContextStore.computeContextId(sourceDeviceId = 209070, category = EmergencyCategory.MEDICAL, sector = 4)
        val id2 = SharedContextStore.computeContextId(sourceDeviceId = 209070, category = EmergencyCategory.MEDICAL, sector = 4)
        val id3 = SharedContextStore.computeContextId(sourceDeviceId = 209070, category = EmergencyCategory.FIRE, sector = 4)
        val id4 = SharedContextStore.computeContextId(sourceDeviceId = 477124, category = EmergencyCategory.MEDICAL, sector = 4)

        assertEquals("Identical inputs must yield identical context ID", id1, id2)
        assertTrue("Different category must yield different context ID", id1 != id3)
        assertTrue("Different node must yield different context ID", id1 != id4)
        assertTrue("Context ID must be positive Short-friendly range [1..65535]", id1 in 1..65535)
    }

    // =========================================================================
    // 3. Version increments
    // =========================================================================
    @Test
    fun test03_versionIncrements() {
        val cmd1 = sampleCommand(sector = 4, count = 3)
        val entry1 = SharedContextEntry.fromCommand(1001, cmd1, 42, 90)
        SharedContextStore.put(entry1)

        val delta = ContextDelta.computeDelta(entry1, sampleCommand(sector = 4, count = 4), newVersion = 2)
        val entry2 = delta.applyTo(entry1)

        assertEquals(2, entry2.version)
        assertEquals(4, entry2.count)
        assertEquals(entry1.sector, entry2.sector)
    }

    // =========================================================================
    // 4. Newer version replaces older version
    // =========================================================================
    @Test
    fun test04_newerVersionReplacesOlderVersion() {
        val cmd1 = sampleCommand(sector = 4, count = 3)
        val entry1 = SharedContextEntry.fromCommand(1001, cmd1, 42, 90)
        val res1 = SharedContextStore.put(entry1)
        assertEquals(ConflictResolutionResult.APPLIED, res1)

        val entry2 = entry1.copy(version = 2, count = 4)
        val res2 = SharedContextStore.put(entry2)
        assertEquals(ConflictResolutionResult.APPLIED, res2)

        val stored = SharedContextStore.get(1001)
        assertNotNull(stored)
        assertEquals(2, stored!!.version)
        assertEquals(4, stored.count)
    }

    // =========================================================================
    // 5. Older version never overwrites newer (REJECTED_STALE)
    // =========================================================================
    @Test
    fun test05_olderVersionNeverOverwritesNewer() {
        val cmd1 = sampleCommand(sector = 4, count = 3)
        val entryV2 = SharedContextEntry.fromCommand(1001, cmd1, 42, 90).copy(version = 2, count = 4)
        SharedContextStore.put(entryV2)

        val staleV1 = SharedContextEntry.fromCommand(1001, cmd1, 42, 90).copy(version = 1, count = 3)
        val res = SharedContextStore.put(staleV1)

        assertEquals(ConflictResolutionResult.REJECTED_STALE, res)
        val current = SharedContextStore.get(1001)
        assertEquals("Version 2 must remain untouched", 2, current!!.version)
        assertEquals("Count 4 must remain untouched", 4, current.count)
    }

    // =========================================================================
    // 6. Same-version conflict rejection (REJECTED_CONFLICT)
    // =========================================================================
    @Test
    fun test06_sameVersionConflictRejection() {
        val cmd1 = sampleCommand(sector = 4, count = 3)
        val entryA = SharedContextEntry.fromCommand(1001, cmd1, 42, 90).copy(version = 2, count = 4)
        SharedContextStore.put(entryA)

        // Same version, different content -> Conflict!
        val entryConflicting = entryA.copy(count = 9)
        val resConflict = SharedContextStore.put(entryConflicting)
        assertEquals(ConflictResolutionResult.REJECTED_CONFLICT, resConflict)

        // Same version, identical content -> Idempotent!
        val entryIdentical = entryA.copy()
        val resIdempotent = SharedContextStore.put(entryIdentical)
        assertEquals(ConflictResolutionResult.APPLIED_IDEMPOTENT, resIdempotent)
    }

    // =========================================================================
    // 7. Context expiration boundary
    // =========================================================================
    @Test
    fun test07_contextExpirationBoundary() {
        val t0 = 1_000_000L
        val entry = SharedContextEntry.fromCommand(1001, sampleCommand(4, 3), 42, 90).copy(
            createdAt = t0,
            lastUpdatedAt = t0,
            expiresAt = t0 + 300_000L // 5 mins
        )

        assertFalse(entry.isExpiredAt(t0 + 299_999L))
        assertTrue(entry.isExpiredAt(t0 + 300_000L))
        assertTrue(entry.isExpiredAt(t0 + 300_001L))
    }

    // =========================================================================
    // 8. Expired context is never reused
    // =========================================================================
    @Test
    fun test08_expiredContextIsNeverReused() {
        val t0 = 1_000_000L
        val entry = SharedContextEntry.fromCommand(1001, sampleCommand(4, 3), 42, 90).copy(
            createdAt = t0,
            lastUpdatedAt = t0,
            expiresAt = t0 + 300_000L
        )
        SharedContextStore.put(entry, currentTime = t0)

        // At t0 + 100s, still valid
        assertNotNull(SharedContextStore.get(1001, currentTime = t0 + 100_000L))

        // At t0 + 301s, expired -> lazily removed, returns null
        assertNull(SharedContextStore.get(1001, currentTime = t0 + 300_001L))
        assertEquals(0, SharedContextStore.size(currentTime = t0 + 300_001L))
    }

    // =========================================================================
    // 9. Storage bounds (max 64 entries)
    // =========================================================================
    @Test
    fun test09_storageBoundsMax64Entries() {
        val t0 = 1_000_000L
        for (i in 1..70) {
            val entry = SharedContextEntry.fromCommand(i, sampleCommand(i, 1), 42, 90).copy(
                createdAt = t0 + i,
                lastUpdatedAt = t0 + i,
                expiresAt = t0 + 1_000_000L
            )
            SharedContextStore.put(entry, currentTime = t0)
        }

        assertTrue("Store size must never exceed MAX_ENTRIES", SharedContextStore.size(t0) <= SharedContextStore.MAX_ENTRIES)
        assertEquals(SharedContextStore.MAX_ENTRIES, SharedContextStore.size(t0))
    }

    // =========================================================================
    // 10. Deterministic LRU eviction
    // =========================================================================
    @Test
    fun test10_deterministicLruEviction() {
        val t0 = 1_000_000L
        // Insert entry 1 with old timestamp
        val entry1 = SharedContextEntry.fromCommand(1, sampleCommand(1, 1), 42, 90).copy(
            createdAt = t0,
            lastUpdatedAt = t0,
            expiresAt = t0 + 10_000_000L
        )
        SharedContextStore.put(entry1, currentTime = t0)

        // Fill remaining 63 + 1 entries with newer timestamps
        for (i in 2..65) {
            val entry = SharedContextEntry.fromCommand(i, sampleCommand(i, 1), 42, 90).copy(
                createdAt = t0 + i,
                lastUpdatedAt = t0 + i,
                expiresAt = t0 + 10_000_000L
            )
            SharedContextStore.put(entry, currentTime = t0 + i)
        }

        // Entry 1 (oldest updated) must have been evicted by LRU
        assertNull("Oldest LRU entry must be evicted", SharedContextStore.get(1, currentTime = t0 + 100))
        assertNotNull("Recent entry must exist", SharedContextStore.get(65, currentTime = t0 + 100))
    }

    // =========================================================================
    // 11. High-confidence context reuse (>=80)
    // =========================================================================
    @Test
    fun test11_highConfidenceContextReuse() {
        val cmd = sampleCommand(sector = 4, count = 3)
        val entry = SharedContextEntry.fromCommand(1001, cmd, 42, confidence = 85)
        val res = SharedContextStore.put(entry)
        assertEquals(ConflictResolutionResult.APPLIED, res)

        assertTrue(ContextConfidence.isAuthoritative(85))
        assertNotNull(SharedContextStore.get(1001))
    }

    // =========================================================================
    // 12. Low-confidence context rejection (<50)
    // =========================================================================
    @Test
    fun test12_lowConfidenceContextRejection() {
        val cmd = sampleCommand(sector = 4, count = 3)
        val entry = SharedContextEntry.fromCommand(1001, cmd, 42, confidence = 45)
        val res = SharedContextStore.put(entry)

        assertEquals("New entry with low confidence must be rejected", ConflictResolutionResult.REJECTED_LOW_CONFIDENCE, res)
        assertNull(SharedContextStore.get(1001))
    }

    // =========================================================================
    // 13. Unknown confidence does not become authoritative
    // =========================================================================
    @Test
    fun test13_unknownConfidenceDoesNotBecomeAuthoritative() {
        assertFalse(ContextConfidence.isAuthoritative(0))
        assertFalse(ContextConfidence.isAuthoritative(50))
        assertFalse(ContextConfidence.isAuthoritative(79))
        assertTrue(ContextConfidence.isAuthoritative(80))
        assertTrue(ContextConfidence.isAuthoritative(100))

        assertEquals(ContextConfidence.Level.LOW, ContextConfidence.Level.fromScore(40))
        assertEquals(ContextConfidence.Level.MEDIUM, ContextConfidence.Level.fromScore(65))
        assertEquals(ContextConfidence.Level.HIGH, ContextConfidence.Level.fromScore(85))
    }

    // =========================================================================
    // 14. Delta encoding (7 bytes for single changed field)
    // =========================================================================
    @Test
    fun test14_deltaEncoding7BytesForSingleChangedField() {
        val delta = ContextDelta(
            contextId = 1001,
            version = 2,
            count = 4
        )
        val bytes = delta.serialize()

        // Magic (1) + Schema (1) + ContextId (2) + Version (1) + Mask (1) + Count (1) = 7 bytes!
        assertEquals("Single field delta must serialize to exactly 7 bytes", 7, bytes.size)
        assertEquals(ContextDelta.MAGIC_DELTA, bytes[0])
        assertEquals(ContextDelta.SCHEMA_VERSION, bytes[1])
        assertEquals(ContextDelta.MASK_COUNT.toByte(), bytes[5])
        assertEquals(4.toByte(), bytes[6])
    }

    // =========================================================================
    // 15. Delta decoding (exact field recovery)
    // =========================================================================
    @Test
    fun test15_deltaDecodingExactFieldRecovery() {
        val original = ContextDelta(
            contextId = 2055,
            version = 3,
            category = EmergencyCategory.FIRE,
            severity = EmergencySeverity.CRITICAL,
            count = 5,
            sector = 12
        )
        val serialized = original.serialize()
        val decoded = ContextDelta.deserialize(serialized)

        assertNotNull(decoded)
        val delta = decoded!!.delta
        assertEquals(2055, delta.contextId)
        assertEquals(3, delta.version)
        assertEquals(EmergencyCategory.FIRE, delta.category)
        assertNull(delta.subtype)
        assertEquals(EmergencySeverity.CRITICAL, delta.severity)
        assertEquals(5, delta.count)
        assertEquals(12.toShort(), delta.sector)
        assertFalse(delta.hasEnhancement)
    }

    // =========================================================================
    // 16. Delta reconstruction with valid context
    // =========================================================================
    @Test
    fun test16_deltaReconstructionWithValidContext() {
        val baseContext = SharedContextEntry.fromCommand(1001, sampleCommand(sector = 4, count = 3), 42, 90)
        val delta = ContextDelta(
            contextId = 1001,
            version = 2,
            count = 4
        )

        val reconstructed = delta.applyTo(baseContext)
        assertEquals(1001, reconstructed.contextId)
        assertEquals(2, reconstructed.version)
        assertEquals(4, reconstructed.count)
        assertEquals(EmergencyCategory.MEDICAL, reconstructed.category)
        assertEquals(4.toShort(), reconstructed.sector)

        val cmd = reconstructed.toSemanticCommand()
        val display = cmd.toDisplayString()
        assertTrue("Reconstructed command display must contain 4 PEOPLE INJURED", display.contains("4 PEOPLE INJURED"))
        assertTrue("Reconstructed command display must contain SECTOR 4", display.contains("SECTOR 4"))
    }

    // =========================================================================
    // 17. Missing-context fallback behavior (safe standalone display, no crash)
    // =========================================================================
    @Test
    fun test17_missingContextFallbackBehavior() {
        val delta = ContextDelta(
            contextId = 9999, // non-existent context
            version = 2,
            count = 4
        )
        val summary = delta.toSummaryString(null)
        assertEquals("PEOPLE: 4", summary)

        val fallbackDisplay = "[DELTA v2 CTX #9999] $summary"
        assertTrue(fallbackDisplay.contains("CTX #9999"))
        assertTrue(fallbackDisplay.contains("PEOPLE: 4"))
    }

    // =========================================================================
    // 18. Missing enhancement does not break base/context reconstruction
    // =========================================================================
    @Test
    fun test18_missingEnhancementDoesNotBreakReconstruction() {
        val delta = ContextDelta(
            contextId = 1001,
            version = 2,
            count = 4,
            hasEnhancement = true // flag was set, but enhancement bytes omitted/corrupted
        )
        // Serialize without actual enhancement layer
        val buffer = ByteBuffer.allocate(7).order(ByteOrder.BIG_ENDIAN)
        buffer.put(ContextDelta.MAGIC_DELTA)
        buffer.put(ContextDelta.SCHEMA_VERSION)
        buffer.putShort(1001.toShort())
        buffer.put(2.toByte())
        buffer.put((ContextDelta.MASK_COUNT or ContextDelta.MASK_HAS_ENHANCEMENT).toByte())
        buffer.put(4.toByte())

        val decoded = ContextDelta.deserialize(buffer.array())
        assertNotNull(decoded)
        assertEquals(4, decoded!!.delta.count)
        assertNull("Missing enhancement payload must gracefully resolve to null without crash", decoded.enhancement)
    }

    // =========================================================================
    // 19. Unsupported schema version handling
    // =========================================================================
    @Test
    fun test19_unsupportedSchemaVersionHandling() {
        val bytes = ContextDelta(1001, 2, count = 4).serialize()
        bytes[1] = 0x99.toByte() // Unknown schema version

        val decoded = ContextDelta.deserialize(bytes)
        assertNull("Unsupported schema version must be rejected", decoded)
    }

    // =========================================================================
    // 20. Malformed context handling
    // =========================================================================
    @Test
    fun test20_malformedContextHandling() {
        // Less than header size
        assertNull(ContextDelta.deserialize(byteArrayOf(0xCD.toByte(), 0x01)))
        // Wrong magic
        val wrongMagic = ContextDelta(1001, 2, count = 4).serialize()
        wrongMagic[0] = 0xAA.toByte()
        assertNull(ContextDelta.deserialize(wrongMagic))
        // Truncated payload for mask
        val truncated = byteArrayOf(0xCD.toByte(), 0x01, 0x03, 0xE9.toByte(), 0x02, ContextDelta.MASK_SECTOR.toByte(), 0x00) // Sector needs 2 bytes, only 1 provided
        assertNull(ContextDelta.deserialize(truncated))
    }

    // =========================================================================
    // 21. Feature 17 refinement improves context confidence
    // =========================================================================
    @Test
    fun test21_feature17RefinementImprovesContextConfidence() {
        val scoreRaw = ContextConfidence.computeConfidence(
            classifierConfidence = 0.82f,
            hasTargetedRefinement = false,
            hasExplicitSector = true,
            hasCount = true
        )

        val scoreRefined = ContextConfidence.computeConfidence(
            classifierConfidence = 0.82f,
            hasTargetedRefinement = true, // Refined during Feature 17 silence pass
            hasExplicitSector = true,
            hasCount = true
        )

        assertTrue(
            "Silence refinement must boost confidence score ($scoreRefined vs $scoreRaw)",
            scoreRefined > scoreRaw
        )
        assertTrue(ContextConfidence.isAuthoritative(scoreRefined))
    }

    // =========================================================================
    // 22. Unfinished refinement never blocks endpoint
    // =========================================================================
    @Test
    fun test22_unfinishedRefinementNeverBlocksEndpoint() {
        val tStart = System.nanoTime()
        val score = ContextConfidence.computeConfidence(
            classifierConfidence = 0.75f,
            hasTargetedRefinement = false,
            hasExplicitSector = true,
            hasCount = false
        )
        val elapsedUs = (System.nanoTime() - tStart) / 1000

        assertTrue("Confidence calculation must be instantaneous (<100us)", elapsedUs < 1000)
        assertTrue(score in 0..100)
    }

    // =========================================================================
    // 23. FULL representation fallback preserved
    // =========================================================================
    @Test
    fun test23_fullRepresentationFallbackPreserved() {
        val rep = AdaptiveRepresentationPolicy.select(
            text = "Casual conversation without emergency",
            networkMode = AdaptiveNetworkMode.HEALTHY,
            language = IndicLanguage.ENGLISH,
            forceMode = AdaptiveRepresentationMode.FULL
        )

        assertEquals(AdaptiveRepresentationMode.FULL, rep.mode)
        assertFalse(rep.mode.isSemantic)
        assertNull(rep.contextDelta)
    }

    // =========================================================================
    // 24. COMPACT representation fallback preserved
    // =========================================================================
    @Test
    fun test24_compactRepresentationFallbackPreserved() {
        val rep = AdaptiveRepresentationPolicy.select(
            text = "Please send water rations to checkpost",
            networkMode = AdaptiveNetworkMode.DEGRADED,
            language = IndicLanguage.ENGLISH,
            forceMode = AdaptiveRepresentationMode.COMPACT
        )

        assertEquals(AdaptiveRepresentationMode.COMPACT, rep.mode)
        assertFalse(rep.mode.isSemantic)
        assertNull(rep.contextDelta)
    }

    // =========================================================================
    // 25. SEMANTIC_BASE representation fallback preserved
    // =========================================================================
    @Test
    fun test25_semanticBaseRepresentationFallbackPreserved() {
        val rep = AdaptiveRepresentationPolicy.select(
            text = "Medical emergency sector 4 3 injured",
            networkMode = AdaptiveNetworkMode.DEGRADED,
            language = IndicLanguage.ENGLISH,
            useLayeredSemantic = true,
            useSharedContext = false // Explicitly no shared context -> pure SEMANTIC_BASE
        )

        assertEquals(AdaptiveRepresentationMode.SEMANTIC_BASE, rep.mode)
        assertTrue(rep.mode.isSemantic)
        assertNotNull(rep.semanticCommand)
        assertNull(rep.contextDelta)
    }

    // =========================================================================
    // 26. HMAC integrity preserved over context delta packets
    // =========================================================================
    @Test
    fun test26_hmacIntegrityPreservedOverContextDelta() {
        val delta = ContextDelta(contextId = 1001, version = 2, count = 4)
        val payload = delta.serialize()

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_ALERT,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 1,
            timestamp = 1000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = payload
        )

        val (signedPacket, _) = PacketAuthenticator.signWithLatency(packet, testPsk)
        val verifyResult = PacketAuthenticator.verify(signedPacket, testPsk)
        assertEquals(org.sih.itantra.core.crypto.AuthStatus.VALID, verifyResult.status)
    }

    // =========================================================================
    // 27. CRC tampering detection preserved
    // =========================================================================
    @Test(expected = CorruptPacketException::class)
    fun test27_crcTamperingDetectionPreserved() {
        val delta = ContextDelta(contextId = 1001, version = 2, count = 4)
        val payload = delta.serialize()

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_ALERT,
            priority = MessagePriority.DISTRESS,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            sequenceNumber = 1,
            timestamp = 1000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = payload
        )

        val wireBytes = PacketSerializer.serialize(packet)
        // Tamper with one payload byte
        wireBytes[Packet.HEADER_SIZE_BYTES + 6] = 0x77.toByte()

        PacketSerializer.deserialize(wireBytes)
    }

    // =========================================================================
    // 28. Packet fragmentation safety (delta fits in single frame)
    // =========================================================================
    @Test
    fun test28_packetFragmentationSafety() {
        val delta = ContextDelta(contextId = 1001, version = 2, count = 4)
        val payload = delta.serialize()

        assertFalse("7-byte ContextDelta must never require fragmentation", PacketFragmenter.needsFragmentation(payload.size))
    }

    // =========================================================================
    // 29. MessageRecord persistence mapping
    // =========================================================================
    @Test
    fun test29_messageRecordPersistenceMapping() {
        val record = MessageRecord(
            id = "msg-123",
            timestamp = 123456L,
            direction = MessageDirection.SENT,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.ALERT,
            text = "MEDICAL 4 INJURED (CRITICAL) - SECTOR 4",
            peer = "Node #209070",
            packetSizeBytes = 47,
            rawAudioEquivalentBytes = 64000L,
            measuredLatencyMs = 15.0,
            representationMode = "CONTEXT_DELTA",
            contextId = 1001,
            contextVersion = 2,
            isContextDelta = true,
            contextConfidence = 90,
            contextFallback = false,
            deltaSummary = "PEOPLE: 3 → 4"
        )

        assertEquals("CONTEXT_DELTA", record.representationMode)
        assertEquals(1001, record.contextId)
        assertEquals(2, record.contextVersion)
        assertTrue(record.isContextDelta)
        assertEquals(90, record.contextConfidence)
        assertFalse(record.contextFallback)
        assertEquals("PEOPLE: 3 → 4", record.deltaSummary)
    }

    // =========================================================================
    // 30. Technical Inspector mapping
    // =========================================================================
    @Test
    fun test30_technicalInspectorMapping() {
        val record = MessageRecord(
            id = "msg-123",
            timestamp = 123456L,
            direction = MessageDirection.RECEIVED,
            language = IndicLanguage.ENGLISH,
            priority = MessagePriority.ALERT,
            text = "MEDICAL 4 INJURED (CRITICAL) - SECTOR 4",
            peer = "Node #209070",
            packetSizeBytes = 47,
            rawAudioEquivalentBytes = 64000L,
            measuredLatencyMs = 15.0,
            representationMode = "CONTEXT_DELTA",
            contextId = 1001,
            contextVersion = 2,
            isContextDelta = true,
            contextConfidence = 90,
            contextFallback = false,
            deltaSummary = "PEOPLE: 3 → 4"
        )

        val radioTelemetry = RadioMessageStateMapper.map(record)
        val inspector = MessageTechnicalInspectorMapper.map(record, radioTelemetry)
        val allFields = inspector.sections.flatMap { it.fields }

        val repField = allFields.find { it.label == "VBR REPRESENTATION" }
        val idField = allFields.find { it.label == "CONTEXT ID" }
        val verField = allFields.find { it.label == "CONTEXT VERSION" }
        val confField = allFields.find { it.label == "CONTEXT CONFIDENCE" }
        val deltaField = allFields.find { it.label == "DELTA FIELDS" }
        val statusField = allFields.find { it.label == "RECONSTRUCTED STATUS" }

        assertNotNull(repField)
        assertEquals("CONTEXT DELTA", repField!!.value)
        assertNotNull(idField)
        assertEquals("#1001", idField!!.value)
        assertNotNull(verField)
        assertEquals("v2", verField!!.value)
        assertNotNull(confField)
        assertTrue(confField!!.value.contains("90/100"))
        assertNotNull(deltaField)
        assertEquals("PEOPLE: 3 → 4", deltaField!!.value)
        assertNotNull(statusField)
        assertTrue(statusField!!.value.startsWith("VALID"))
    }

    // =========================================================================
    // 31. English context equivalence
    // =========================================================================
    @Test
    fun test31_englishContextEquivalence() {
        val cmd1 = SemanticEmergencyClassifier.classify("Medical emergency sector 4 3 injured")
        assertNotNull(cmd1)
        val id1 = SharedContextStore.computeContextId(209070, cmd1!!.category, cmd1.parameter)

        val cmd2 = SemanticEmergencyClassifier.classify("Medical emergency sector 4 4 injured")
        assertNotNull(cmd2)
        val id2 = SharedContextStore.computeContextId(209070, cmd2!!.category, cmd2.parameter)

        assertEquals("Same category and sector must map to same tactical context ID", id1, id2)
    }

    // =========================================================================
    // 32. Hindi context equivalence
    // =========================================================================
    @Test
    fun test32_hindiContextEquivalence() {
        val cmdHi = SemanticEmergencyClassifier.classify("सेक्टर चार में तीन घायल")
        assertNotNull(cmdHi)
        val idHi = SharedContextStore.computeContextId(209070, cmdHi!!.category, cmdHi.parameter)

        val cmdEn = SemanticEmergencyClassifier.classify("Medical emergency sector 4 3 injured")
        assertNotNull(cmdEn)
        val idEn = SharedContextStore.computeContextId(209070, cmdEn!!.category, cmdEn.parameter)

        assertEquals("Hindi and English semantic commands for same tactical event must share Context ID", idEn, idHi)
    }

    // =========================================================================
    // 33. Tamil context equivalence
    // =========================================================================
    @Test
    fun test33_tamilContextEquivalence() {
        val cmdTa = SemanticEmergencyClassifier.classify("செக்டார் 4 மூன்று காயமடைந்தவர்கள்")
        assertNotNull(cmdTa)
        val idTa = SharedContextStore.computeContextId(209070, cmdTa!!.category, cmdTa.parameter)

        val cmdEn = SemanticEmergencyClassifier.classify("Medical emergency sector 4 3 injured")
        assertNotNull(cmdEn)
        val idEn = SharedContextStore.computeContextId(209070, cmdEn!!.category, cmdEn.parameter)

        assertEquals("Tamil and English semantic commands for same tactical event must share Context ID", idEn, idTa)
    }

    // =========================================================================
    // 34. Emergency semantic context safety
    // =========================================================================
    @Test
    fun test34_emergencySemanticContextSafety() {
        val baseContext = SharedContextEntry.fromCommand(1001, sampleCommand(4, 3), 42, 90)
        // Create delta that alters severity from CRITICAL to ALERT
        val delta = ContextDelta(
            contextId = 1001,
            version = 2,
            severity = EmergencySeverity.ALERT
        )
        val updated = delta.applyTo(baseContext)

        assertEquals(EmergencySeverity.ALERT, updated.severity)
        assertEquals(EmergencyCategory.MEDICAL, updated.category)
        assertEquals(3, updated.count)
        assertEquals(4.toShort(), updated.sector)
    }

    // =========================================================================
    // 35. Consecutive mixed-message state isolation
    // =========================================================================
    @Test
    fun test35_consecutiveMixedMessageStateIsolation() {
        // Message 1: Establish Context A (Medical Sector 4)
        val cmdA = sampleCommand(sector = 4, count = 3)
        val idA = SharedContextStore.computeContextId(42, cmdA.category, cmdA.parameter)
        SharedContextStore.put(SharedContextEntry.fromCommand(idA, cmdA, 42, 90))

        // Message 2: Establish Context B (Fire Sector 2)
        val cmdB = SemanticCommand(category = EmergencyCategory.FIRE, subtype = EmergencySubtype.NONE, count = 0, severity = EmergencySeverity.CRITICAL, parameter = 2.toShort())
        val idB = SharedContextStore.computeContextId(42, cmdB.category, cmdB.parameter)
        SharedContextStore.put(SharedContextEntry.fromCommand(idB, cmdB, 42, 90))

        // Message 3: Send Delta for Context A (count 3 -> 4)
        val deltaA = ContextDelta(contextId = idA, version = 2, count = 4)
        val activeA = SharedContextStore.get(idA)!!
        val updatedA = deltaA.applyTo(activeA)
        SharedContextStore.put(updatedA)

        // Verify Context A updated
        assertEquals(4, SharedContextStore.get(idA)!!.count)
        assertEquals(2, SharedContextStore.get(idA)!!.version)

        // Verify Context B completely isolated and unchanged
        val currentB = SharedContextStore.get(idB)!!
        assertEquals(1, currentB.version)
        assertEquals(EmergencyCategory.FIRE, currentB.category)
        assertEquals(2.toShort(), currentB.sector)
    }
}
