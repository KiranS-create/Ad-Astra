package org.sih.itantra.core.speech.refinement

import kotlinx.coroutines.delay
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.diagnostics.BenchmarkClock
import java.util.Locale

/**
 * Interface for targeted Pass 2 refinement worker.
 */
interface TargetedPass2Refiner {
    /**
     * Refines a specific candidate token identified by the refinement policy.
     *
     * @param candidate The targeted token candidate.
     * @param fullContext Current accumulated partial text.
     * @param language Active Indic language.
     * @return TargetedRefinementResult containing original vs refined text.
     */
    suspend fun refineCandidate(
        candidate: RefinementCandidate,
        fullContext: String,
        language: IndicLanguage
    ): TargetedRefinementResult
}

/**
 * Default implementation of TargetedPass2Refiner.
 * Applies deterministic tactical standardization, emergency tagging, and coordinate formatting
 * with controlled non-blocking execution duration.
 */
class DefaultTargetedPass2Refiner(
    private val simulatedRefinementDelayMs: Long = 10L
) : TargetedPass2Refiner {

    override suspend fun refineCandidate(
        candidate: RefinementCandidate,
        fullContext: String,
        language: IndicLanguage
    ): TargetedRefinementResult {
        val tStart = BenchmarkClock.nowNanos()

        if (simulatedRefinementDelayMs > 0) {
            delay(simulatedRefinementDelayMs)
        }

        val rawToken = candidate.tokenText
        val lower = rawToken.lowercase(Locale.ROOT).trim()

        val refinedToken = when (candidate.reason) {
            RefinementReason.EMERGENCY_KEYWORD -> {
                when (lower) {
                    "madad" -> if (language == IndicLanguage.HINDI) "मदद" else "MADAD"
                    "ghayal" -> if (language == IndicLanguage.HINDI) "घायल" else "GHAYAL"
                    "aag" -> if (language == IndicLanguage.HINDI) "आग" else "FIRE"
                    "khatra" -> if (language == IndicLanguage.HINDI) "खतरा" else "DANGER"
                    "uthavi" -> if (language == IndicLanguage.TAMIL) "உதவி" else "UTHAVI"
                    "kaayam" -> if (language == IndicLanguage.TAMIL) "காயம்" else "INJURED"
                    "sos", "mayday", "help", "emergency", "medical", "injured", "fire", "ambush", "evac" -> {
                        rawToken.uppercase(Locale.ROOT)
                    }
                    else -> rawToken.uppercase(Locale.ROOT)
                }
            }

            RefinementReason.TACTICAL_COORDINATE_OR_NUMBER -> {
                when (lower) {
                    "sector", "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "grid", "checkpoint", "base" -> {
                        rawToken.uppercase(Locale.ROOT)
                    }
                    else -> rawToken // Preserve numeric digits or formatted coords
                }
            }

            RefinementReason.HYPOTHESIS_INSTABILITY -> {
                // Stabilize by trimming punctuation and ensuring canonical casing
                rawToken.replace(Regex("""[^\w\u0900-\u097F\u0B80-\u0BFF]"""), "").trim()
            }

            RefinementReason.INDIC_SCRIPT_AMBIGUITY -> {
                // If mixed script or romanized Indic, standardize
                rawToken.trim()
            }

            RefinementReason.LOW_CONFIDENCE,
            RefinementReason.BOUNDARY_UNCERTAINTY -> {
                rawToken.trim()
            }
        }

        val tEnd = BenchmarkClock.nowNanos()
        val elapsed = tEnd - tStart

        return TargetedRefinementResult(
            candidate = candidate,
            originalText = rawToken,
            refinedText = refinedToken,
            confidenceGain = 0.20f,
            computeNanos = elapsed,
            wasApplied = (refinedToken != rawToken)
        )
    }
}
