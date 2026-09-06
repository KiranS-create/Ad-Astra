package org.sih.itantra.core.persistence

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
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
    val location: GeoLocation? = null
)
