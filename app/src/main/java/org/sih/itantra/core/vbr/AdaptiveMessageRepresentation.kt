package org.sih.itantra.core.vbr

import org.sih.itantra.core.protocol.SemanticCommand

/**
 * Feature 16B: Container representing an encoded message under a specific
 * application representation mode (FULL, COMPACT, or SEMANTIC).
 *
 * @property mode The active representation mode.
 * @property text The human-readable text representation (verbatim, shorthand, or structured card).
 * @property payloadBytes The raw binary payload bytes formatted for transmission.
 * @property wirePayloadSizeBytes Byte length of [payloadBytes].
 * @property confidence Confidence score in [0.0, 1.0] of this representation.
 * @property semanticCommand Structured 6-byte semantic command if mode is [AdaptiveRepresentationMode.SEMANTIC].
 * @property explanation Human-readable justification of why this representation was chosen.
 */
data class AdaptiveMessageRepresentation(
    val mode: AdaptiveRepresentationMode,
    val text: String,
    val payloadBytes: ByteArray,
    val wirePayloadSizeBytes: Int,
    val confidence: Float,
    val isCompressed: Boolean = false,
    val semanticCommand: SemanticCommand? = null,
    val semanticBase: SemanticBase? = null,
    val semanticEnhancement: SemanticEnhancement? = null,
    val basePayloadSizeBytes: Int = 0,
    val enhancementPayloadSizeBytes: Int = 0,
    val contextDelta: ContextDelta? = null,
    val contextId: Int? = null,
    val contextVersion: Int? = null,
    val isContextFallback: Boolean = false,
    val explanation: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AdaptiveMessageRepresentation
        return mode == other.mode &&
                text == other.text &&
                payloadBytes.contentEquals(other.payloadBytes) &&
                wirePayloadSizeBytes == other.wirePayloadSizeBytes &&
                confidence == other.confidence &&
                semanticCommand == other.semanticCommand &&
                semanticBase == other.semanticBase &&
                semanticEnhancement == other.semanticEnhancement &&
                basePayloadSizeBytes == other.basePayloadSizeBytes &&
                enhancementPayloadSizeBytes == other.enhancementPayloadSizeBytes &&
                contextDelta == other.contextDelta &&
                contextId == other.contextId &&
                contextVersion == other.contextVersion &&
                isContextFallback == other.isContextFallback &&
                explanation == other.explanation
    }

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + payloadBytes.contentHashCode()
        result = 31 * result + wirePayloadSizeBytes
        result = 31 * result + confidence.hashCode()
        result = 31 * result + (semanticCommand?.hashCode() ?: 0)
        result = 31 * result + (semanticBase?.hashCode() ?: 0)
        result = 31 * result + (semanticEnhancement?.hashCode() ?: 0)
        result = 31 * result + basePayloadSizeBytes
        result = 31 * result + enhancementPayloadSizeBytes
        result = 31 * result + (contextDelta?.hashCode() ?: 0)
        result = 31 * result + (contextId ?: 0)
        result = 31 * result + (contextVersion ?: 0)
        result = 31 * result + isContextFallback.hashCode()
        result = 31 * result + explanation.hashCode()
        return result
    }
}
