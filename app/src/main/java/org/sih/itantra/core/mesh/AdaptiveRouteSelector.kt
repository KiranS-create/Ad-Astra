package org.sih.itantra.core.mesh

/**
 * Adaptive MANET route selector.
 *
 * Metric evaluation hierarchy:
 * 1. Freshness / Validity: Stale or invalid routes are immediately superseded by valid routes.
 *    Fresher route sequence numbers strictly supersede older ones.
 * 2. Hop Count (Strict Priority): Lower hop count is strictly prioritized over link quality
 *    and battery to minimize latency and network overhead.
 * 3. Link Quality (0.0 to 1.0): If hop counts are identical, link quality (e.g., RSSI / recency)
 *    is compared. A difference > 0.15 indicates a significantly better channel.
 * 4. Battery Health (0 to 100%): Used strictly as a secondary tie-breaker when hop counts are
 *    equal and link quality difference is <= 0.15.
 *
 * All decisions are deterministic and explainable.
 */
object AdaptiveRouteSelector {

    /**
     * Human-readable classification of a link quality score (0.0 to 1.0).
     */
    fun qualityLabel(quality: Float): String = when {
        quality >= 0.8f -> "EXCELLENT"
        quality >= 0.6f -> "GOOD"
        quality >= 0.4f -> "FAIR"
        else -> "POOR"
    }

    /**
     * Determines whether [candidate] should replace [existing] in the routing table.
     *
     * @param existing The current entry in the table, or null if none exists.
     * @param candidate The newly proposed route entry.
     * @return true if candidate is superior and should replace existing; false otherwise.
     */
    fun shouldReplace(existing: RouteEntry?, candidate: RouteEntry): Boolean {
        if (existing == null) return true
        if (candidate.state != RouteState.VALID) return false
        if (existing.state != RouteState.VALID || System.currentTimeMillis() > existing.expiryMs) return true

        // 1. Freshness via sequence number (higher is fresher from destination)
        if (candidate.routeSeqNum > existing.routeSeqNum) return true
        if (candidate.routeSeqNum < existing.routeSeqNum) return false

        // 2. Strict hop count priority
        if (candidate.hopCount < existing.hopCount) return true
        if (candidate.hopCount > existing.hopCount) return false

        // 3. Link quality (diff > 0.15 indicates a meaningful channel difference)
        val qualityDiff = candidate.linkQuality - existing.linkQuality
        if (qualityDiff > 0.15f) return true
        if (qualityDiff < -0.15f) return false

        // 4. Battery health tie-breaker (when hops equal & link quality diff <= 0.15)
        return candidate.batteryPct > existing.batteryPct
    }

    /**
     * Selects the single best route among a collection of candidate routes to the same destination.
     */
    fun selectBestRoute(candidates: Collection<RouteEntry>): RouteEntry? {
        if (candidates.isEmpty()) return null
        var best: RouteEntry? = null
        for (candidate in candidates) {
            if (shouldReplace(best, candidate)) {
                best = candidate
            }
        }
        return best
    }

    /**
     * Formats an explainable route label for UI and telemetry.
     * Example: "A -> B -> D | 2 HOPS | QUALITY GOOD | BATTERY 72%"
     */
    fun formatRouteExplanation(
        entry: RouteEntry,
        pathLabels: List<String>? = null
    ): String {
        val pathStr = if (!pathLabels.isNullOrEmpty()) {
            pathLabels.joinToString(" -> ")
        } else {
            "${entry.nextHopNodeId} -> ${entry.destinationNodeId}"
        }
        val hopsStr = if (entry.hopCount == 1) "1 HOP" else "${entry.hopCount} HOPS"
        val qLabel = qualityLabel(entry.linkQuality)
        return "$pathStr | $hopsStr | QUALITY $qLabel | BATTERY ${entry.batteryPct}%"
    }
}
