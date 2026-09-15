package org.sih.itantra.core.speech.benchmark

import org.junit.Assert.*
import org.junit.Test
import org.sih.itantra.core.speech.benchmark.multilang.TacticalAccuracyEvaluator

class TacticalAccuracyEvaluatorTest {

    @Test
    fun testCriticalTokenEvaluationAllPresent() {
        val expected = listOf("हॉक 1", "सेक्टर 4", "तत्काल")
        val hypothesis = "हॉक 1 सेक्टर 4 में तत्काल सहायता चाहिए"

        val result = TacticalAccuracyEvaluator.evaluateCriticalTokens(expected, hypothesis)

        assertEquals(3, result.foundTokens.size)
        assertEquals(0, result.missingTokens.size)
        assertEquals(1.0, result.prF1.precision, 0.001)
        assertEquals(1.0, result.prF1.recall, 0.001)
        assertEquals(1.0, result.prF1.f1, 0.001)
    }

    @Test
    fun testCriticalTokenEvaluationPartial() {
        val expected = listOf("गरुड़ 3", "28.6139", "77.2090")
        val hypothesis = "गरुड़ 3 स्थान 28.6139 पर पहुंचा" // Missing 77.2090

        val result = TacticalAccuracyEvaluator.evaluateCriticalTokens(expected, hypothesis)

        assertEquals(2, result.foundTokens.size)
        assertEquals(1, result.missingTokens.size)
        assertEquals(listOf("77.2090"), result.missingTokens)
        assertEquals(1.0, result.prF1.precision, 0.001)
        assertEquals(2.0 / 3.0, result.prF1.recall, 0.001)
        assertTrue(result.prF1.f1 > 0.79 && result.prF1.f1 < 0.81)
    }

    @Test
    fun testSemanticFactsEvaluation() {
        val expectedFacts = mapOf(
            "action" to "advance",
            "sector" to "4",
            "urgency" to "immediate"
        )
        val hypothesis = "advance to sector 4 with immediate priority"

        val result = TacticalAccuracyEvaluator.evaluateSemanticFacts(expectedFacts, hypothesis)

        assertEquals(3, result.totalFacts)
        assertEquals(3, result.matchedFacts)
        assertEquals(1.0, result.accuracy, 0.001)
        assertTrue(result.factDetails["action"] == true)
        assertTrue(result.factDetails["sector"] == true)
        assertTrue(result.factDetails["urgency"] == true)
    }

    @Test
    fun testUtteranceAndCategoryAggregation() {
        val u1 = TacticalAccuracyEvaluator.evaluateUtterance(
            id = "utt_01",
            language = "hi",
            category = "COORDINATES",
            referenceText = "28.6139 77.2090",
            hypothesisText = "28.6139 77.2090",
            criticalTokens = listOf("28.6139", "77.2090"),
            expectedFacts = mapOf("lat" to "28.6139", "lon" to "77.2090")
        )

        val u2 = TacticalAccuracyEvaluator.evaluateUtterance(
            id = "utt_02",
            language = "hi",
            category = "COORDINATES",
            referenceText = "19.0760 72.8777",
            hypothesisText = "19.0760", // Missing lon
            criticalTokens = listOf("19.0760", "72.8777"),
            expectedFacts = mapOf("lat" to "19.0760", "lon" to "72.8777")
        )

        val catAgg = TacticalAccuracyEvaluator.aggregateCategory("COORDINATES", listOf(u1, u2))

        assertEquals(2, catAgg.totalUtterances)
        assertEquals(0.5, catAgg.exactMatchRate, 0.001)
        assertEquals(0.75, catAgg.criticalTokenRecall, 0.001)
        assertEquals(0.75, catAgg.semanticFactAccuracy, 0.001)
    }
}
