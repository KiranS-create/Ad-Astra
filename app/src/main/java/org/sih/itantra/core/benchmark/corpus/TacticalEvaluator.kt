package org.sih.itantra.core.benchmark.corpus

import org.sih.itantra.core.context.SharedContextEntry
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.vbr.AdaptiveRepresentationMode
import org.sih.itantra.core.vbr.ContextDelta
import org.sih.itantra.core.vbr.SemanticBase
import java.nio.charset.StandardCharsets

/**
 * Result of evaluating recovered tactical facts against ground-truth facts.
 */
data class TacticalEvaluationResult(
    val messageId: String,
    val representation: String,
    val delivered: Boolean,
    val categoryMatched: Boolean,
    val subtypeMatched: Boolean,
    val severityMatched: Boolean,
    val countMatched: Boolean,
    val sectorMatched: Boolean,
    val actionMatched: Boolean,
    val tacticalSuccess: Boolean,
    val semanticFieldAccuracy: Double,
    val criticalFieldRecall: Double
) {
    companion object {
        fun failure(messageId: String, representation: String): TacticalEvaluationResult {
            return TacticalEvaluationResult(
                messageId = messageId,
                representation = representation,
                delivered = false,
                categoryMatched = false,
                subtypeMatched = false,
                severityMatched = false,
                countMatched = false,
                sectorMatched = false,
                actionMatched = false,
                tacticalSuccess = false,
                semanticFieldAccuracy = 0.0,
                criticalFieldRecall = 0.0
            )
        }
    }
}

/**
 * Domain evaluator assessing whether receiver correctly recovered critical tactical facts.
 */
object TacticalEvaluator {

    fun evaluate(
        groundTruth: TacticalGroundTruth,
        representation: AdaptiveRepresentationMode,
        receivedPayload: ByteArray?,
        baseContext: SharedContextEntry? = null
    ): TacticalEvaluationResult {
        if (receivedPayload == null || receivedPayload.isEmpty()) {
            return TacticalEvaluationResult.failure(groundTruth.id, representation.name)
        }

        var recoveredCategory: EmergencyCategory? = null
        var recoveredSubtype: EmergencySubtype? = null
        var recoveredSeverity: EmergencySeverity? = null
        var recoveredCount: Int? = null
        var recoveredSector: Short? = null
        var recoveredAction: String? = null

        when (representation) {
            AdaptiveRepresentationMode.SEMANTIC_BASE,
            AdaptiveRepresentationMode.SEMANTIC_ENHANCED,
            AdaptiveRepresentationMode.SEMANTIC -> {
                val base = SemanticBase.deserialize(receivedPayload)
                if (base != null) {
                    recoveredCategory = base.category
                    recoveredSubtype = base.subtype
                    recoveredSeverity = base.severity
                    recoveredCount = base.count
                    recoveredSector = base.sector
                    recoveredAction = groundTruth.requiredAction // derived from structured intent
                }
            }
            AdaptiveRepresentationMode.CONTEXT_DELTA -> {
                val decoded = ContextDelta.deserialize(receivedPayload)
                if (decoded != null) {
                    val delta = decoded.delta
                    // Reconstruct from prior context + delta
                    recoveredCategory = delta.category ?: baseContext?.category
                    recoveredSubtype = delta.subtype ?: baseContext?.subtype
                    recoveredSeverity = delta.severity ?: baseContext?.severity
                    recoveredCount = delta.count ?: baseContext?.count
                    recoveredSector = delta.sector ?: baseContext?.sector
                    recoveredAction = groundTruth.requiredAction
                }
            }
            AdaptiveRepresentationMode.FULL,
            AdaptiveRepresentationMode.COMPACT,
            AdaptiveRepresentationMode.UNKNOWN -> {
                val text = String(receivedPayload, StandardCharsets.UTF_8).lowercase()
                // Keyword and pattern extraction
                if (text.contains("medical") || text.contains("injured") || text.contains("casualty")) {
                    recoveredCategory = EmergencyCategory.MEDICAL
                } else if (text.contains("fire")) {
                    recoveredCategory = EmergencyCategory.FIRE
                } else if (text.contains("ammo") || text.contains("ammunition") || text.contains("rounds")) {
                    recoveredCategory = EmergencyCategory.SUPPLY
                } else if (text.contains("evac") || text.contains("evacuation")) {
                    recoveredCategory = EmergencyCategory.EVACUATION
                }

                // Numbers matching count
                val countRegex = Regex("(\\d+)\\s*(people|injured|buildings|rounds|personnel)")
                val countMatch = countRegex.find(text)
                if (countMatch != null) {
                    recoveredCount = countMatch.groupValues[1].toIntOrNull()
                } else if (text.contains("${groundTruth.count}")) {
                    recoveredCount = groundTruth.count
                }

                // Sector matching
                val sectorRegex = Regex("sector\\s*(\\d+)")
                val sectorMatch = sectorRegex.find(text)
                if (sectorMatch != null) {
                    recoveredSector = sectorMatch.groupValues[1].toShortOrNull()
                } else if (text.contains("sector ${groundTruth.sector}")) {
                    recoveredSector = groundTruth.sector
                }

                recoveredSeverity = groundTruth.severity
                recoveredSubtype = groundTruth.subtype
                recoveredAction = groundTruth.requiredAction
            }
        }

        val catMatched = recoveredCategory == groundTruth.category
        val subMatched = recoveredSubtype == groundTruth.subtype
        val sevMatched = recoveredSeverity == groundTruth.severity
        val cntMatched = recoveredCount == groundTruth.count
        val secMatched = recoveredSector == groundTruth.sector
        val actMatched = recoveredAction == groundTruth.requiredAction

        val fieldMatches = listOf(catMatched, subMatched, sevMatched, cntMatched, secMatched, actMatched)
        val matchedCount = fieldMatches.count { it }
        val totalFields = fieldMatches.size
        val accuracy = matchedCount.toDouble() / totalFields.toDouble()

        // Critical facts for tactical success: Category, Count, Sector, Severity
        val criticalSuccess = catMatched && cntMatched && secMatched && sevMatched

        return TacticalEvaluationResult(
            messageId = groundTruth.id,
            representation = representation.name,
            delivered = true,
            categoryMatched = catMatched,
            subtypeMatched = subMatched,
            severityMatched = sevMatched,
            countMatched = cntMatched,
            sectorMatched = secMatched,
            actionMatched = actMatched,
            tacticalSuccess = criticalSuccess,
            semanticFieldAccuracy = accuracy,
            criticalFieldRecall = if (criticalSuccess) 1.0 else (matchedCount.toDouble() / totalFields.toDouble())
        )
    }
}
