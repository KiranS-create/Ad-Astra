package org.sih.itantra.core.persistence

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.protocol.GeoLocation

enum class MessageDirection {
    SENT,
    RECEIVED
}

data class MessageRecord(
    val id: String,
    val timestamp: Long,
    val direction: MessageDirection,
    val language: IndicLanguage,
    val priority: MessagePriority,
    val text: String,
    val peer: String,
    val packetSizeBytes: Int,
    val rawAudioEquivalentBytes: Long,
    val measuredLatencyMs: Double,
    val isRelayed: Boolean = false,
    val hopCount: Int = 0,
    val location: GeoLocation? = null,
    val isSemantic: Boolean = false,
    val semanticSummary: String? = null,
    val semanticSavingsBytes: Int? = null,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.NONE,
    val transferId: Short? = null,
    val fragmentCount: Int? = null,
    val fragmentIndex: Int? = null,
    val deliveryLatencyMs: Long? = null,
    val isSecure: Boolean = false,
    val authStatus: String? = null,
    val qosStatus: String? = null,
    val representationMode: String? = null, // "FULL", "COMPACT", "SEMANTIC", "SEMANTIC_BASE", or "SEMANTIC_ENHANCED"
    val semanticBaseBytes: Int? = null,
    val enhancementBytes: Int? = null,
    val enhancementReceived: Boolean? = null,
    val semanticSchemaVersion: Int? = null,
    val contextId: Int? = null,
    val contextVersion: Int? = null,
    val isContextDelta: Boolean = false,
    val contextConfidence: Int? = null,
    val contextFallback: Boolean = false,
    val deltaSummary: String? = null
)
