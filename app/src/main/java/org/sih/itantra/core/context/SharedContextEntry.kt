package org.sih.itantra.core.context

import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.vbr.SemanticBase

/**
 * Feature 19: Immutable, language-neutral tactical shared context entry.
 *
 * Stores structured FACTS (not UI strings) that can be referenced by subsequent messages
 * to communicate deltas without retransmitting unchanged data.
 *
 * @property contextId Deterministic logical identifier computed from (sourceDeviceId, category, sector).
 * @property version Monotonically incrementing semantic version (1, 2, 3...).
 * @property category Primary emergency domain (Medical, Fire, Evacuation, Security, etc.).
 * @property subtype Specific tactical emergency subtype (Injured, Trapped, Critical, etc.).
 * @property severity Tactical operational severity (Normal, Important, Alert, Critical).
 * @property count Verified casualty or operational unit count.
 * @property sector Tactical grid sector identifier.
 * @property confidence Bounded confidence score in [0, 100].
 * @property sourceDeviceId Originating mesh node ID.
 * @property createdAt Epoch timestamp when this context was initially established.
 * @property lastUpdatedAt Epoch timestamp of the most recent version update.
 * @property expiresAt Epoch timestamp after which this context is considered stale and invalid.
 * @property schemaVersion Protocol schema evolution version.
 */
data class SharedContextEntry(
    val contextId: Int,
    val version: Int,
    val category: EmergencyCategory,
    val subtype: EmergencySubtype,
    val severity: EmergencySeverity,
    val count: Int,
    val sector: Short,
    val confidence: Int,
    val sourceDeviceId: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = createdAt,
    val expiresAt: Long = createdAt + DEFAULT_TTL_MS,
    val schemaVersion: Byte = SCHEMA_VERSION
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt

    fun isExpiredAt(currentTime: Long): Boolean = currentTime >= expiresAt

    /**
     * Projects this context entry into an equivalent self-contained [SemanticCommand].
     */
    fun toSemanticCommand(): SemanticCommand = SemanticCommand(
        category = category,
        subtype = subtype,
        severity = severity,
        count = count,
        parameter = sector
    )

    /**
     * Projects this context entry into an equivalent 8-byte [SemanticBase].
     */
    fun toSemanticBase(): SemanticBase = SemanticBase.fromCommand(
        command = toSemanticCommand(),
        sector = sector,
        schemaVersion = schemaVersion
    )

    /**
     * Renders a human-readable tactical summary from this context's facts.
     */
    fun toDisplayString(): String = toSemanticCommand().toDisplayString()

    companion object {
        const val SCHEMA_VERSION: Byte = 1
        const val DEFAULT_TTL_MS: Long = 300_000L // 5 minutes

        /**
         * Creates an initial Version 1 context entry from a [SemanticCommand].
         */
        fun fromCommand(
            contextId: Int,
            command: SemanticCommand,
            sourceDeviceId: Int,
            confidence: Int,
            currentTime: Long = System.currentTimeMillis(),
            ttlMs: Long = DEFAULT_TTL_MS
        ): SharedContextEntry = SharedContextEntry(
            contextId = contextId,
            version = 1,
            category = command.category,
            subtype = command.subtype,
            severity = command.severity,
            count = command.count,
            sector = command.parameter,
            confidence = confidence.coerceIn(0, 100),
            sourceDeviceId = sourceDeviceId,
            createdAt = currentTime,
            lastUpdatedAt = currentTime,
            expiresAt = currentTime + ttlMs,
            schemaVersion = SCHEMA_VERSION
        )
    }
}
