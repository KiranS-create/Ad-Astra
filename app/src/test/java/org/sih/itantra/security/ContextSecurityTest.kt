package org.sih.itantra.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.context.ConflictResolutionResult
import org.sih.itantra.core.context.ContextConfidence
import org.sih.itantra.core.context.SharedContextEntry
import org.sih.itantra.core.context.SharedContextStore
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.vbr.ContextDelta
import org.sih.itantra.core.vbr.SemanticBase
import org.sih.itantra.core.vbr.SemanticEnhancement
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Feature 23: Context-Data Security, Anti-Poisoning & Semantic Validation Suite.
 *
 * Verifies:
 * - SharedContextStore resistance against poisoning attacks (low confidence, stale rollback, conflict).
 * - ContextDelta binary deserialization safety and strict schema validation.
 * - SemanticBase and SemanticEnhancement boundary enforcement and fail-closed parser.
 * - Emergency message integrity and priority protection.
 */
class ContextSecurityTest {

    @Before
    fun setUp() {
        SharedContextStore.clear()
    }

    // =========================================================================
    // SECTION 1: SHARED CONTEXT STORE POISONING RESILIENCE
    // =========================================================================

    @Test
    fun context_lowConfidencePoisoning_rejectedWhenCreatingContext() {
        val entry = SharedContextEntry(
            contextId = 101,
            version = 1,
            category = EmergencyCategory.FIRE,
            subtype = EmergencySubtype.BUILDING,
            severity = EmergencySeverity.CRITICAL,
            count = 10,
            sector = 4,
            confidence = ContextConfidence.LOW_THRESHOLD - 5, // Confidence = 35% (Untrusted noise)
            sourceDeviceId = 123456,
            lastUpdatedAt = System.currentTimeMillis()
        )

        val result = SharedContextStore.put(entry)
        assertEquals("Low confidence update must be rejected when creating new context",
            ConflictResolutionResult.REJECTED_LOW_CONFIDENCE, result)
        assertNull(SharedContextStore.get(101))
    }

    @Test
    fun context_authoritativeEntry_successfullyStored() {
        val entry = SharedContextEntry(
            contextId = 102,
            version = 1,
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.INJURED,
            severity = EmergencySeverity.CRITICAL,
            count = 3,
            sector = 2,
            confidence = ContextConfidence.AUTHORITATIVE_THRESHOLD, // Confidence = 85%
            sourceDeviceId = 123456,
            lastUpdatedAt = System.currentTimeMillis()
        )

        val result = SharedContextStore.put(entry)
        assertEquals(ConflictResolutionResult.APPLIED, result)
        assertNotNull(SharedContextStore.get(102))
    }

    @Test
    fun context_staleVersionRollbackAttack_rejectedDeterministically() {
        val initialEntry = SharedContextEntry(
            contextId = 103,
            version = 3, // Current authoritative version = 3
            category = EmergencyCategory.TRAPPED,
            subtype = EmergencySubtype.COLLAPSE,
            severity = EmergencySeverity.CRITICAL,
            count = 8,
            sector = 5,
            confidence = 90,
            sourceDeviceId = 123456,
            lastUpdatedAt = System.currentTimeMillis()
        )
        assertEquals(ConflictResolutionResult.APPLIED, SharedContextStore.put(initialEntry))

        // Attacker attempts to rollback state to version 1 with fake count 0
        val rollbackAttempt = initialEntry.copy(
            version = 1, // Stale older version!
            count = 0,
            severity = EmergencySeverity.NORMAL
        )

        val rollbackResult = SharedContextStore.put(rollbackAttempt)
        assertEquals("Stale version rollback must be rejected", ConflictResolutionResult.REJECTED_STALE, rollbackResult)

        // Canonical context remains untouched
        val active = SharedContextStore.get(103)
        assertNotNull(active)
        assertEquals(3, active?.version)
        assertEquals(8, active?.count)
        assertEquals(EmergencySeverity.CRITICAL, active?.severity)
    }

    @Test
    fun context_sameVersionConflictingUpdate_rejectedAsConflict() {
        val authoritativeEntry = SharedContextEntry(
            contextId = 104,
            version = 2,
            category = EmergencyCategory.FIRE,
            subtype = EmergencySubtype.BUILDING,
            severity = EmergencySeverity.CRITICAL,
            count = 4,
            sector = 1,
            confidence = 85,
            sourceDeviceId = 123456,
            lastUpdatedAt = System.currentTimeMillis()
        )
        assertEquals(ConflictResolutionResult.APPLIED, SharedContextStore.put(authoritativeEntry))

        // Conflicting update with identical version 2 but contradictory sector and casualty count
        val conflictingUpdate = authoritativeEntry.copy(
            count = 20, // Disputed count
            sector = 9  // Disputed sector
        )

        val conflictResult = SharedContextStore.put(conflictingUpdate)
        assertEquals("Conflicting same-version update must be rejected", ConflictResolutionResult.REJECTED_CONFLICT, conflictResult)

        // Authoritative context is preserved
        val active = SharedContextStore.get(104)
        assertEquals(4, active?.count)
        assertEquals(1.toShort(), active?.sector)
    }

    @Test
    fun context_sameVersionIdenticalUpdate_acceptedIdempotently() {
        val entry = SharedContextEntry(
            contextId = 105,
            version = 1,
            category = EmergencyCategory.RESCUE,
            subtype = EmergencySubtype.TEAM,
            severity = EmergencySeverity.IMPORTANT,
            count = 2,
            sector = 7,
            confidence = 80,
            sourceDeviceId = 123456,
            lastUpdatedAt = System.currentTimeMillis()
        )
        assertEquals(ConflictResolutionResult.APPLIED, SharedContextStore.put(entry))

        // Identical update retransmitted
        val duplicateResult = SharedContextStore.put(entry.copy())
        assertEquals(ConflictResolutionResult.APPLIED_IDEMPOTENT, duplicateResult)
    }

    @Test
    fun context_lowConfidenceDegradationAttack_rejectedDeterministically() {
        val highConfidenceEntry = SharedContextEntry(
            contextId = 106,
            version = 1,
            category = EmergencyCategory.MEDICAL,
            subtype = EmergencySubtype.AMBULANCE,
            severity = EmergencySeverity.CRITICAL,
            count = 2,
            sector = 3,
            confidence = ContextConfidence.HIGH_THRESHOLD + 10, // 80%
            sourceDeviceId = 123456,
            lastUpdatedAt = System.currentTimeMillis()
        )
        assertEquals(ConflictResolutionResult.APPLIED, SharedContextStore.put(highConfidenceEntry))

        // Attacker attempts to update to version 2 with extremely low confidence (< 40%)
        val degradedAttempt = highConfidenceEntry.copy(
            version = 2,
            confidence = ContextConfidence.LOW_THRESHOLD - 10, // 30%
            severity = EmergencySeverity.NORMAL
        )

        val result = SharedContextStore.put(degradedAttempt)
        assertEquals("Low confidence degradation of authoritative context must be rejected",
            ConflictResolutionResult.REJECTED_LOW_CONFIDENCE, result)

        val current = SharedContextStore.get(106)
        assertEquals(1, current?.version)
        assertEquals(EmergencySeverity.CRITICAL, current?.severity)
    }

    @Test
    fun context_expiredContext_rejectedAndLazilyEvicted() {
        val now = System.currentTimeMillis()
        val expiredEntry = SharedContextEntry(
            contextId = 107,
            version = 1,
            category = EmergencyCategory.HAZARD,
            subtype = EmergencySubtype.NONE,
            severity = EmergencySeverity.ALERT,
            count = 0,
            sector = 2,
            confidence = 85,
            sourceDeviceId = 123456,
            lastUpdatedAt = now - 500_000L,
            expiresAt = now - 10_000L // Already expired 10s ago
        )

        val result = SharedContextStore.put(expiredEntry, currentTime = now)
        assertEquals(ConflictResolutionResult.REJECTED_EXPIRED, result)
        assertNull(SharedContextStore.get(107, currentTime = now))
    }

    // =========================================================================
    // SECTION 2: CONTEXT DELTA BINARY PARSING SECURITY
    // =========================================================================

    @Test
    fun delta_undersizedPayload_returnsNullSafely() {
        for (len in 0 until ContextDelta.HEADER_SIZE_BYTES) {
            val bytes = ByteArray(len)
            val decoded = ContextDelta.deserialize(bytes)
            assertNull("Undersized delta ($len bytes) must return null without crashing", decoded)
        }
    }

    @Test
    fun delta_invalidMagic_returnsNullSafely() {
        val buf = ByteBuffer.allocate(ContextDelta.HEADER_SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        buf.put(0xAA.toByte()) // Invalid magic (expected 0xCD)
        buf.put(ContextDelta.SCHEMA_VERSION)
        buf.putShort(101.toShort())
        buf.put(1.toByte())
        buf.put(0.toByte()) // Mask

        val decoded = ContextDelta.deserialize(buf.array())
        assertNull("Invalid magic byte must return null", decoded)
    }

    @Test
    fun delta_unsupportedSchemaVersion_returnsNullSafely() {
        val buf = ByteBuffer.allocate(ContextDelta.HEADER_SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        buf.put(ContextDelta.MAGIC_DELTA)
        buf.put(99.toByte()) // Unsupported schema version!
        buf.putShort(101.toShort())
        buf.put(1.toByte())
        buf.put(0.toByte())

        val decoded = ContextDelta.deserialize(buf.array())
        assertNull("Unsupported schema version must return null", decoded)
    }

    @Test
    fun delta_truncatedDynamicPayload_returnsNullSafely() {
        // Delta declares category (1B) and sector (2B) in mask, but dynamic bytes are missing
        val mask = (ContextDelta.MASK_CATEGORY or ContextDelta.MASK_SECTOR).toByte()
        val buf = ByteBuffer.allocate(ContextDelta.HEADER_SIZE_BYTES + 1).order(ByteOrder.BIG_ENDIAN)
        buf.put(ContextDelta.MAGIC_DELTA)
        buf.put(ContextDelta.SCHEMA_VERSION)
        buf.putShort(101.toShort())
        buf.put(2.toByte())
        buf.put(mask)
        buf.put(EmergencyCategory.FIRE.id) // Only 1 byte provided, sector (2B) is missing!

        val decoded = ContextDelta.deserialize(buf.array())
        assertNull("Truncated dynamic fields must return null without throwing BufferUnderflowException", decoded)
    }

    @Test
    fun delta_propertyTest_validDeltaRoundTripPreservesAllFields() {
        val originalDelta = ContextDelta(
            contextId = 5432,
            version = 4,
            category = EmergencyCategory.SECURITY,
            subtype = EmergencySubtype.ROAD,
            severity = EmergencySeverity.CRITICAL,
            count = 15,
            sector = 12.toShort(),
            hasEnhancement = false
        )

        val wireBytes = originalDelta.serialize()
        val decoded = ContextDelta.deserialize(wireBytes)

        assertNotNull(decoded)
        val delta = decoded!!.delta
        assertEquals(originalDelta.contextId, delta.contextId)
        assertEquals(originalDelta.version, delta.version)
        assertEquals(originalDelta.category, delta.category)
        assertEquals(originalDelta.subtype, delta.subtype)
        assertEquals(originalDelta.severity, delta.severity)
        assertEquals(originalDelta.count, delta.count)
        assertEquals(originalDelta.sector, delta.sector)
        assertFalse(delta.hasEnhancement)
    }

    // =========================================================================
    // SECTION 3: SEMANTIC BASE & ENHANCEMENT VALIDATION
    // =========================================================================

    @Test
    fun semantic_truncatedEnhancementText_returnsNullSafely() {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buf.put(SemanticEnhancement.SCHEMA_VERSION)
        buf.put(0.toByte()) // flags
        buf.putShort(100.toShort()) // Declared text length = 100 bytes, but buffer has 0 text bytes!

        val decoded = SemanticEnhancement.deserialize(buf.array())
        assertNull("Truncated enhancement text length must safely return null", decoded)
    }

    @Test
    fun semantic_corruptedEnhancementPreservesBaseCommand() {
        val base = SemanticBase(
            schemaVersion = 1,
            command = SemanticCommand(
                category = EmergencyCategory.EVACUATION,
                subtype = EmergencySubtype.NONE,
                count = 100,
                severity = EmergencySeverity.CRITICAL,
                parameter = 8
            ),
            sector = 8,
            hasEnhancement = true
        )

        // Serialize base (8 bytes) + append 4 bytes of corrupted garbage enhancement
        val baseBytes = base.serialize(hasEnhancement = true)
        val corruptedComposite = ByteArray(baseBytes.size + 4)
        System.arraycopy(baseBytes, 0, corruptedComposite, 0, baseBytes.size)
        corruptedComposite[baseBytes.size] = 99.toByte() // Invalid enhancement schema version

        val decoded = SemanticBase.deserializeWithEnhancement(corruptedComposite)
        assertNotNull("Base command must be preserved even if enhancement is corrupt", decoded.base)
        assertEquals(EmergencyCategory.EVACUATION, decoded.base?.category)
        assertEquals(100, decoded.base?.count)
        assertNull("Corrupt enhancement must be safely dropped", decoded.enhancement)
        assertTrue(decoded.isBaseOnly)
    }
}
