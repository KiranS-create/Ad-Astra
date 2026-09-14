package org.sih.itantra.core.speech.refinement

/**
 * Tactical or linguistic reason triggering targeted Pass 2 refinement.
 */
enum class RefinementReason(val defaultPriority: Float) {
    /** High-priority emergency / distress keyword (e.g. SOS, medical, injured, fire, madad, ghayal). */
    EMERGENCY_KEYWORD(1.0f),

    /** Critical numerical digit, grid coordinate, or tactical callsign (e.g. sector 4, grid 72). */
    TACTICAL_COORDINATE_OR_NUMBER(0.85f),

    /** Token mutated or fluctuated across consecutive Pass 1 incremental hypothesis emissions. */
    HYPOTHESIS_INSTABILITY(0.75f),

    /** Confidence score reported by Pass 1 fell below the confidence threshold. */
    LOW_CONFIDENCE(0.60f),

    /** Mixed script or phonetically ambiguous Indic token (e.g. Devanagari/Latin mix). */
    INDIC_SCRIPT_AMBIGUITY(0.50f),

    /** Word boundary or punctuation attachment ambiguity. */
    BOUNDARY_UNCERTAINTY(0.40f)
}

/**
 * A specific token or character span targeted for opportunistic Pass 2 refinement during silence.
 *
 * @property chunkIndex Index of the audio chunk that generated or finalized this candidate.
 * @property tokenText The raw token text identified for refinement.
 * @property priorityScore Deterministic priority score in [0.0, 1.0] (higher is processed first).
 * @property reason Primary reason for targeting this token.
 * @property startIndex Character start index within the accumulated partial text.
 * @property endIndex Character end index within the accumulated partial text.
 * @property contextBefore Surrounding preceding text context (for n-gram or language model disambiguation).
 * @property contextAfter Surrounding succeeding text context.
 */
data class RefinementCandidate(
    val chunkIndex: Int,
    val tokenText: String,
    val priorityScore: Float,
    val reason: RefinementReason,
    val startIndex: Int = 0,
    val endIndex: Int = 0,
    val contextBefore: String = "",
    val contextAfter: String = ""
) {
    /**
     * Unique key identifying this token span to avoid duplicate re-refinement.
     */
    val spanKey: String
        get() = "$startIndex:$endIndex:${tokenText.trim().lowercase()}"
}

/**
 * Result of an opportunistic targeted refinement performed by Pass 2.
 *
 * @property candidate The candidate that was refined.
 * @property originalText Original token text before refinement.
 * @property refinedText Corrected, normalized, or tactical standardized text.
 * @property confidenceGain Estimated confidence increase after Pass 2 refinement.
 * @property computeNanos Time taken to compute this specific refinement.
 * @property wasApplied True if the refinement resulted in a substitution or confirmation.
 */
data class TargetedRefinementResult(
    val candidate: RefinementCandidate,
    val originalText: String,
    val refinedText: String,
    val confidenceGain: Float = 0.15f,
    val computeNanos: Long = 0L,
    val wasApplied: Boolean = true
)
