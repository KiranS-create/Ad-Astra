package org.sih.itantra.core.vbr

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.network.AdaptiveNetworkMode
import org.sih.itantra.core.protocol.AdaptiveCompressor
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.protocol.SemanticEmergencyClassifier

/**
 * Feature 16B: Pure deterministic selection policy for Adaptive Two-Pass Semantic VBR.
 *
 * Core Principles:
 * 1. Meaning preservation > bitrate reduction.
 * 2. Never force low-confidence speech into a structured semantic command.
 * 3. Zero post-speech waiting: decisions use pre-computed contextual and network states.
 * 4. Application-layer representation selection; radio protocol framing is unchanged.
 */
object AdaptiveRepresentationPolicy {

    const val SEMANTIC_CONFIDENCE_THRESHOLD = 0.85f

    /**
     * Evaluates text, network condition, and confidence to deterministically select the optimal
     * representation mode and build the corresponding [AdaptiveMessageRepresentation].
     *
     * @param text Transcribed natural language text.
     * @param networkMode Active physical/logical network condition.
     * @param language Source language of the utterance.
     * @param semanticConfidence Confidence score in [0.0, 1.0] from STT Pass 2.
     * @param forceMode Optional manual override for operator preference or testing.
     * @return Selected [AdaptiveMessageRepresentation].
     */
    fun select(
        text: String,
        networkMode: AdaptiveNetworkMode,
        language: IndicLanguage = IndicLanguage.HINDI,
        semanticConfidence: Float = 0.90f,
        forceMode: AdaptiveRepresentationMode? = null,
        useLayeredSemantic: Boolean = false
    ): AdaptiveMessageRepresentation {
        val trimmed = text.trim()
        val semanticCandidate = SemanticEmergencyClassifier.classify(trimmed)

        // 1. Manual / Explicit Override
        if (forceMode != null) {
            return when (forceMode) {
                AdaptiveRepresentationMode.SEMANTIC_BASE -> {
                    val cmd = semanticCandidate ?: SemanticCommand(
                        category = EmergencyCategory.OTHER,
                        severity = EmergencySeverity.CRITICAL
                    )
                    buildSemanticBaseRepresentation(cmd, semanticConfidence, "Forced SEMANTIC_BASE (BASE ONLY) mode")
                }
                AdaptiveRepresentationMode.SEMANTIC_ENHANCED -> {
                    val cmd = semanticCandidate ?: SemanticCommand(
                        category = EmergencyCategory.OTHER,
                        severity = EmergencySeverity.CRITICAL
                    )
                    buildSemanticEnhancedRepresentation(cmd, trimmed, semanticConfidence, "Forced SEMANTIC_ENHANCED (BASE + ENH) mode")
                }
                AdaptiveRepresentationMode.SEMANTIC -> {
                    val cmd = semanticCandidate ?: SemanticCommand(
                        category = EmergencyCategory.OTHER,
                        severity = EmergencySeverity.CRITICAL
                    )
                    buildSemanticRepresentation(cmd, semanticConfidence, "Forced SEMANTIC mode")
                }
                AdaptiveRepresentationMode.COMPACT -> {
                    buildCompactRepresentation(trimmed, language, "Forced COMPACT mode")
                }
                AdaptiveRepresentationMode.FULL -> {
                    buildFullRepresentation(trimmed, "Forced FULL mode")
                }
                AdaptiveRepresentationMode.UNKNOWN -> {
                    buildFullRepresentation(trimmed, "Fallback FULL representation")
                }
            }
        }

        // 2. High-Confidence Semantic Candidate
        val hasHighConfidenceSemantic = semanticCandidate != null &&
                semanticConfidence >= SEMANTIC_CONFIDENCE_THRESHOLD

        if (hasHighConfidenceSemantic) {
            val cmd = semanticCandidate!!
            if (useLayeredSemantic) {
                return when (networkMode) {
                    AdaptiveNetworkMode.DEGRADED,
                    AdaptiveNetworkMode.LIMITED,
                    AdaptiveNetworkMode.CONGESTED,
                    AdaptiveNetworkMode.DTN_STORED,
                    AdaptiveNetworkMode.OFFLINE,
                    AdaptiveNetworkMode.WAITING_FOR_ROUTE -> {
                        buildSemanticBaseRepresentation(
                            cmd = cmd,
                            confidence = semanticConfidence,
                            explanation = "Constrained network (${networkMode.label}): 8-byte survival base payload selected"
                        )
                    }
                    AdaptiveNetworkMode.HEALTHY,
                    AdaptiveNetworkMode.UNKNOWN -> {
                        if (cmd.severity == EmergencySeverity.CRITICAL ||
                            cmd.category in setOf(
                                EmergencyCategory.MEDICAL,
                                EmergencyCategory.FIRE,
                                EmergencyCategory.TRAPPED,
                                EmergencyCategory.RESCUE,
                                EmergencyCategory.EVACUATION
                            )
                        ) {
                            buildSemanticEnhancedRepresentation(
                                cmd = cmd,
                                originalText = trimmed,
                                confidence = semanticConfidence,
                                explanation = "Critical emergency (${cmd.category.label}): prioritized as semantic base + enhancement"
                            )
                        } else {
                            // Non-critical command in healthy network transmits verbatim
                            buildFullRepresentation(
                                trimmed,
                                "Healthy network: natural language verbatim representation preserved"
                            )
                        }
                    }
                }
            } else {
                return when (networkMode) {
                    AdaptiveNetworkMode.DEGRADED,
                    AdaptiveNetworkMode.LIMITED,
                    AdaptiveNetworkMode.CONGESTED,
                    AdaptiveNetworkMode.DTN_STORED,
                    AdaptiveNetworkMode.OFFLINE,
                    AdaptiveNetworkMode.WAITING_FOR_ROUTE -> {
                        buildSemanticRepresentation(
                            cmd = cmd,
                            confidence = semanticConfidence,
                            explanation = "Constrained network (${networkMode.label}): 6-byte survival payload selected"
                        )
                    }
                    AdaptiveNetworkMode.HEALTHY,
                    AdaptiveNetworkMode.UNKNOWN -> {
                        if (cmd.severity == EmergencySeverity.CRITICAL ||
                            cmd.category in setOf(
                                EmergencyCategory.MEDICAL,
                                EmergencyCategory.FIRE,
                                EmergencyCategory.TRAPPED,
                                EmergencyCategory.RESCUE,
                                EmergencyCategory.EVACUATION
                            )
                        ) {
                            buildSemanticRepresentation(
                                cmd = cmd,
                                confidence = semanticConfidence,
                                explanation = "Critical emergency (${cmd.category.label}): prioritized as 6-byte semantic structure"
                            )
                        } else {
                            // Non-critical command in healthy network transmits verbatim
                            buildFullRepresentation(
                                trimmed,
                                "Healthy network: natural language verbatim representation preserved"
                            )
                        }
                    }
                }
            }
        }

        // 3. Fallback for non-semantic text or low-confidence semantic candidates
        // (Meaning preservation: Never guess or force ambiguous speech into a structured command)
        return when (networkMode) {
            AdaptiveNetworkMode.DEGRADED,
            AdaptiveNetworkMode.LIMITED,
            AdaptiveNetworkMode.CONGESTED,
            AdaptiveNetworkMode.DTN_STORED -> {
                buildCompactRepresentation(
                    text = trimmed,
                    language = language,
                    explanation = "Constrained network (${networkMode.label}): tactical shorthand reduces airtime"
                )
            }
            AdaptiveNetworkMode.OFFLINE,
            AdaptiveNetworkMode.WAITING_FOR_ROUTE -> {
                if (trimmed.length > 50) {
                    buildCompactRepresentation(
                        text = trimmed,
                        language = language,
                        explanation = "Offline/store-and-forward: tactical shorthand conserves DTN buffer"
                    )
                } else {
                    buildFullRepresentation(
                        text = trimmed,
                        explanation = "Short utterance: stored verbatim in DTN buffer"
                    )
                }
            }
            AdaptiveNetworkMode.HEALTHY,
            AdaptiveNetworkMode.UNKNOWN -> {
                buildFullRepresentation(
                    text = trimmed,
                    explanation = "Healthy network: verbatim natural language representation"
                )
            }
        }
    }

    fun buildSemanticBaseRepresentation(
        cmd: SemanticCommand,
        confidence: Float,
        explanation: String
    ): AdaptiveMessageRepresentation {
        val base = SemanticBase.fromCommand(cmd)
        val payload = base.serialize(hasEnhancement = false)
        return AdaptiveMessageRepresentation(
            mode = AdaptiveRepresentationMode.SEMANTIC_BASE,
            text = base.toDisplayString(),
            payloadBytes = payload,
            wirePayloadSizeBytes = payload.size,
            confidence = confidence,
            semanticCommand = cmd,
            semanticBase = base,
            semanticEnhancement = null,
            basePayloadSizeBytes = payload.size,
            enhancementPayloadSizeBytes = 0,
            explanation = explanation
        )
    }

    fun buildSemanticEnhancedRepresentation(
        cmd: SemanticCommand,
        originalText: String,
        confidence: Float,
        explanation: String,
        refinedText: String? = null
    ): AdaptiveMessageRepresentation {
        val base = SemanticBase.fromCommand(cmd)
        val primaryText = refinedText ?: originalText
        val detailText = if (refinedText != null && refinedText != originalText) originalText else null
        val enhancement = SemanticEnhancement(text = primaryText, detail = detailText)
        val combinedPayload = SemanticBase.serializeComposite(base, enhancement)

        return AdaptiveMessageRepresentation(
            mode = AdaptiveRepresentationMode.SEMANTIC_ENHANCED,
            text = base.toDisplayString(enhancementText = primaryText),
            payloadBytes = combinedPayload,
            wirePayloadSizeBytes = combinedPayload.size,
            confidence = confidence,
            semanticCommand = cmd,
            semanticBase = base,
            semanticEnhancement = enhancement,
            basePayloadSizeBytes = SemanticBase.BASE_SIZE_BYTES,
            enhancementPayloadSizeBytes = combinedPayload.size - SemanticBase.BASE_SIZE_BYTES,
            explanation = explanation
        )
    }

    fun selectLayered(
        text: String,
        networkMode: AdaptiveNetworkMode,
        language: IndicLanguage = IndicLanguage.HINDI,
        semanticConfidence: Float = 0.90f,
        forceMode: AdaptiveRepresentationMode? = null
    ): AdaptiveMessageRepresentation = select(
        text = text,
        networkMode = networkMode,
        language = language,
        semanticConfidence = semanticConfidence,
        forceMode = forceMode,
        useLayeredSemantic = true
    )

    fun buildSemanticRepresentation(
        cmd: SemanticCommand,
        confidence: Float,
        explanation: String
    ): AdaptiveMessageRepresentation {
        val payload = cmd.serialize()
        return AdaptiveMessageRepresentation(
            mode = AdaptiveRepresentationMode.SEMANTIC,
            text = cmd.toDisplayString(),
            payloadBytes = payload,
            wirePayloadSizeBytes = payload.size,
            confidence = confidence,
            semanticCommand = cmd,
            semanticBase = null,
            semanticEnhancement = null,
            basePayloadSizeBytes = 0,
            enhancementPayloadSizeBytes = 0,
            explanation = explanation
        )
    }

    private fun buildCompactRepresentation(
        text: String,
        language: IndicLanguage,
        explanation: String
    ): AdaptiveMessageRepresentation {
        val compactText = CompactTextGenerator.compact(text, language)
        val rawBytes = compactText.toByteArray(Charsets.UTF_8)
        val compression = AdaptiveCompressor.compress(rawBytes)
        return AdaptiveMessageRepresentation(
            mode = AdaptiveRepresentationMode.COMPACT,
            text = compactText,
            payloadBytes = compression.bytes,
            wirePayloadSizeBytes = compression.bytes.size,
            confidence = 1.0f,
            isCompressed = compression.isCompressed,
            semanticCommand = null,
            explanation = explanation
        )
    }

    private fun buildFullRepresentation(
        text: String,
        explanation: String
    ): AdaptiveMessageRepresentation {
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        val compression = AdaptiveCompressor.compress(rawBytes)
        return AdaptiveMessageRepresentation(
            mode = AdaptiveRepresentationMode.FULL,
            text = text,
            payloadBytes = compression.bytes,
            wirePayloadSizeBytes = compression.bytes.size,
            confidence = 1.0f,
            isCompressed = compression.isCompressed,
            semanticCommand = null,
            explanation = explanation
        )
    }
}
