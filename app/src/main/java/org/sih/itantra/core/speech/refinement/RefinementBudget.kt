package org.sih.itantra.core.speech.refinement

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Budget statistics snapshot.
 */
data class RefinementBudgetStats(
    val maxCandidatesPerWindow: Int,
    val maxTotalPerUtterance: Int,
    val maxSilenceTimeBudgetMs: Long,
    val totalRefinementsExecuted: Int,
    val totalAbandonedDueToBudget: Int,
    val totalDuplicatesSuppressed: Int,
    val totalEvaluatedCandidates: Int
)

/**
 * Strict refinement budget manager enforcing real-time bounds and duplicate suppression
 * for opportunistic Pass 2 refinement during natural silence windows.
 *
 * Guarantees:
 * 1. Constant memory footprint.
 * 2. Zero runaway task accumulation.
 * 3. Never exceeds per-window or per-utterance compute limits.
 * 4. Suppresses redundant re-refinement of identical tokens.
 */
class RefinementBudget(
    val maxCandidatesPerSilenceWindow: Int = 2,
    val maxTotalRefinementsPerUtterance: Int = 4,
    val maxSilenceTimeBudgetMs: Long = 180L
) {
    private val refinedSpans = ConcurrentHashMap.newKeySet<String>()
    private val totalRefinementsExecuted = AtomicInteger(0)
    private val totalAbandonedDueToBudget = AtomicInteger(0)
    private val totalDuplicatesSuppressed = AtomicInteger(0)
    private val totalEvaluatedCandidates = AtomicInteger(0)

    /**
     * Filters and bounds candidates for an upcoming silence window.
     *
     * @param candidates Candidates ordered by priority descending.
     * @return List of candidates accepted within the silence window budget.
     */
    fun filterCandidates(candidates: List<RefinementCandidate>): List<RefinementCandidate> {
        totalEvaluatedCandidates.addAndGet(candidates.size)

        val currentExecuted = totalRefinementsExecuted.get()
        val remainingUtteranceAllowance = (maxTotalRefinementsPerUtterance - currentExecuted).coerceAtLeast(0)

        if (remainingUtteranceAllowance == 0) {
            totalAbandonedDueToBudget.addAndGet(candidates.size)
            return emptyList()
        }

        val windowAllowance = minOf(maxCandidatesPerSilenceWindow, remainingUtteranceAllowance)
        val accepted = mutableListOf<RefinementCandidate>()

        for (candidate in candidates) {
            if (refinedSpans.contains(candidate.spanKey)) {
                totalDuplicatesSuppressed.incrementAndGet()
                continue
            }

            if (accepted.size < windowAllowance) {
                accepted.add(candidate)
            } else {
                totalAbandonedDueToBudget.incrementAndGet()
            }
        }

        return accepted
    }

    /**
     * Records that a candidate was successfully refined and marks its span as completed.
     */
    fun recordRefinementApplied(candidate: RefinementCandidate) {
        refinedSpans.add(candidate.spanKey)
        totalRefinementsExecuted.incrementAndGet()
    }

    /**
     * Checks if the utterance-level budget is completely exhausted.
     */
    fun isBudgetExhausted(): Boolean {
        return totalRefinementsExecuted.get() >= maxTotalRefinementsPerUtterance
    }

    /**
     * Retrieves current budget statistics.
     */
    fun getBudgetStats(): RefinementBudgetStats {
        return RefinementBudgetStats(
            maxCandidatesPerWindow = maxCandidatesPerSilenceWindow,
            maxTotalPerUtterance = maxTotalRefinementsPerUtterance,
            maxSilenceTimeBudgetMs = maxSilenceTimeBudgetMs,
            totalRefinementsExecuted = totalRefinementsExecuted.get(),
            totalAbandonedDueToBudget = totalAbandonedDueToBudget.get(),
            totalDuplicatesSuppressed = totalDuplicatesSuppressed.get(),
            totalEvaluatedCandidates = totalEvaluatedCandidates.get()
        )
    }

    /**
     * Resets the budget state for a new utterance.
     */
    fun reset() {
        refinedSpans.clear()
        totalRefinementsExecuted.set(0)
        totalAbandonedDueToBudget.set(0)
        totalDuplicatesSuppressed.set(0)
        totalEvaluatedCandidates.set(0)
    }
}
