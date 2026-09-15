package org.sih.itantra.core.context

/**
 * Feature 19: Deterministic confidence scoring and classification for tactical shared context.
 *
 * Rules:
 * 1. Strictly bounded numeric score in [0, 100].
 * 2. No neural or probabilistic hallucinations — derived purely from deterministic evidence:
 *    - Base STT / semantic classifier confidence score.
 *    - Feature 17 targeted silence refinement completion status.
 *    - Structural semantic completeness (presence of casualty count, sector).
 *    - Ambiguity indicators (penalizes to LOW).
 * 3. Categorization into explicit operational levels:
 *    - HIGH (>= 80): Safe for context reuse and delta generation.
 *    - MEDIUM (50..79): Usable for contextual awareness but must not be authoritative.
 *    - LOW (< 50): Strictly rejected from shared context.
 */
object ContextConfidence {

    const val HIGH_THRESHOLD = 80
    const val MEDIUM_THRESHOLD = 50
    const val AUTHORITATIVE_THRESHOLD = HIGH_THRESHOLD
    const val LOW_THRESHOLD = 40
    const val DEFAULT_HIGH_CONFIDENCE = 90
    const val DEFAULT_BASELINE_CONFIDENCE = 85

    enum class Level(val label: String, val minScore: Int) {
        HIGH("HIGH", HIGH_THRESHOLD),
        MEDIUM("MEDIUM", MEDIUM_THRESHOLD),
        LOW("LOW", 0);

        companion object {
            fun fromScore(score: Int): Level = when {
                score >= HIGH_THRESHOLD -> HIGH
                score >= MEDIUM_THRESHOLD -> MEDIUM
                else -> LOW
            }
        }
    }

    /**
     * Evaluates available deterministic signals to compute a bounded confidence score in [0, 100].
     *
     * @param classifierConfidence Normalized confidence [0.0, 1.0] from semantic classification.
     * @param hasTargetedRefinement True if Feature 17 silence refinement successfully normalized tokens.
     * @param hasExplicitSector True if tactical sector or grid coordinate was explicitly identified.
     * @param hasCount True if casualty count was explicitly verified (> 0).
     * @param isAmbiguous True if conflicting or ambiguous keywords were detected.
     */
    fun computeConfidence(
        classifierConfidence: Float,
        hasTargetedRefinement: Boolean = false,
        hasExplicitSector: Boolean = false,
        hasCount: Boolean = false,
        isAmbiguous: Boolean = false
    ): Int {
        if (isAmbiguous) {
            return 30 // Low confidence: never authoritative
        }

        var score = (classifierConfidence.coerceIn(0.0f, 1.0f) * 100).toInt()

        // Feature 17 silence refinement boost (+5)
        if (hasTargetedRefinement) {
            score = (score + 5).coerceAtMost(100)
        }

        // Structural fact bonuses
        if (hasExplicitSector) {
            score = (score + 3).coerceAtMost(100)
        }
        if (hasCount) {
            score = (score + 2).coerceAtMost(100)
        }

        return score.coerceIn(0, 100)
    }

    /**
     * Determines whether a confidence score qualifies an entry to become authoritative shared context.
     */
    fun isAuthoritative(score: Int): Boolean = score >= HIGH_THRESHOLD
}
