package org.sih.itantra.core.speech.benchmark.multilang

data class PrecisionRecallF1(
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int
)

data class TacticalTokenEvaluation(
    val expectedTokens: List<String>,
    val foundTokens: List<String>,
    val missingTokens: List<String>,
    val prF1: PrecisionRecallF1
)

data class SemanticFactEvaluation(
    val totalFacts: Int,
    val matchedFacts: Int,
    val accuracy: Double,
    val factDetails: Map<String, Boolean>
)

data class UtteranceEvaluationResult(
    val id: String,
    val language: String,
    val category: String,
    val referenceText: String,
    val hypothesisText: String,
    val werCer: WerCerScore,
    val criticalTokenEval: TacticalTokenEvaluation,
    val semanticFactEval: SemanticFactEvaluation,
    val exactMatch: Boolean
)

data class CategoryAggregateResult(
    val category: String,
    val totalUtterances: Int,
    val averageWer: Double,
    val averageCer: Double,
    val exactMatchRate: Double,
    val criticalTokenPrecision: Double,
    val criticalTokenRecall: Double,
    val criticalTokenF1: Double,
    val semanticFactAccuracy: Double
)

data class LanguageAggregateResult(
    val language: String,
    val languageDisplayName: String,
    val totalUtterances: Int,
    val averageWer: Double,
    val averageCer: Double,
    val exactMatchRate: Double,
    val criticalTokenPrecision: Double,
    val criticalTokenRecall: Double,
    val criticalTokenF1: Double,
    val semanticFactAccuracy: Double,
    val categoryBreakdown: Map<String, CategoryAggregateResult>
)

object TacticalAccuracyEvaluator {

    fun evaluateCriticalTokens(
        expectedTokens: List<String>,
        hypothesis: String
    ): TacticalTokenEvaluation {
        if (expectedTokens.isEmpty()) {
            return TacticalTokenEvaluation(
                expectedTokens = emptyList(),
                foundTokens = emptyList(),
                missingTokens = emptyList(),
                prF1 = PrecisionRecallF1(1.0, 1.0, 1.0, 0, 0, 0)
            )
        }

        val normHyp = WerCerCalculator.normalize(hypothesis)
        val hypTokens = WerCerCalculator.tokenizeWords(normHyp)
        val hypTokenSet = hypTokens.toSet()

        val found = mutableListOf<String>()
        val missing = mutableListOf<String>()

        for (token in expectedTokens) {
            val normExpected = WerCerCalculator.normalize(token)
            if (normExpected.isEmpty()) continue

            val matched = if (normExpected.contains(" ")) {
                normHyp.contains(normExpected)
            } else {
                hypTokenSet.contains(normExpected) || normHyp.contains(normExpected)
            }

            if (matched) {
                found.add(token)
            } else {
                missing.add(token)
            }
        }

        val tp = found.size
        val fn = missing.size
        val fp = 0

        val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp).toDouble() else 1.0
        val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn).toDouble() else 0.0
        val f1 = if (precision + recall > 0.0) (2.0 * precision * recall) / (precision + recall) else 0.0

        return TacticalTokenEvaluation(
            expectedTokens = expectedTokens,
            foundTokens = found,
            missingTokens = missing,
            prF1 = PrecisionRecallF1(precision, recall, f1, tp, fp, fn)
        )
    }

    fun evaluateSemanticFacts(
        expectedFacts: Map<String, String>,
        hypothesis: String
    ): SemanticFactEvaluation {
        if (expectedFacts.isEmpty()) {
            return SemanticFactEvaluation(
                totalFacts = 0,
                matchedFacts = 0,
                accuracy = 1.0,
                factDetails = emptyMap()
            )
        }

        val normHyp = WerCerCalculator.normalize(hypothesis)
        val details = mutableMapOf<String, Boolean>()
        var matches = 0

        for ((key, expectedValue) in expectedFacts) {
            val normExpected = WerCerCalculator.normalize(expectedValue)
            val matched = when {
                normHyp.contains(normExpected) -> true
                expectedValue.all { it.isDigit() } && normHyp.contains(expectedValue) -> true
                else -> false
            }

            details[key] = matched
            if (matched) matches++
        }

        val accuracy = matches.toDouble() / expectedFacts.size.toDouble()

        return SemanticFactEvaluation(
            totalFacts = expectedFacts.size,
            matchedFacts = matches,
            accuracy = accuracy,
            factDetails = details
        )
    }

    fun evaluateUtterance(
        id: String,
        language: String,
        category: String,
        referenceText: String,
        hypothesisText: String,
        criticalTokens: List<String>,
        expectedFacts: Map<String, String>
    ): UtteranceEvaluationResult {
        val werCer = WerCerCalculator.evaluate(referenceText, hypothesisText)
        val tokenEval = evaluateCriticalTokens(criticalTokens, hypothesisText)
        val factEval = evaluateSemanticFacts(expectedFacts, hypothesisText)

        return UtteranceEvaluationResult(
            id = id,
            language = language,
            category = category,
            referenceText = referenceText,
            hypothesisText = hypothesisText,
            werCer = werCer,
            criticalTokenEval = tokenEval,
            semanticFactEval = factEval,
            exactMatch = werCer.exactMatch
        )
    }

    fun aggregateCategory(
        category: String,
        results: List<UtteranceEvaluationResult>
    ): CategoryAggregateResult {
        val count = results.size
        if (count == 0) {
            return CategoryAggregateResult(category, 0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0)
        }

        val werCerAgg = WerCerCalculator.aggregate(results.map { it.werCer })
        val totalExpectedTokens = results.sumOf { it.criticalTokenEval.expectedTokens.size }
        val totalFoundTokens = results.sumOf { it.criticalTokenEval.foundTokens.size }
        val recall = if (totalExpectedTokens > 0) totalFoundTokens.toDouble() / totalExpectedTokens.toDouble() else 1.0
        val precision = 1.0
        val f1 = if (precision + recall > 0.0) (2.0 * precision * recall) / (precision + recall) else 0.0

        val totalFacts = results.sumOf { it.semanticFactEval.totalFacts }
        val matchedFacts = results.sumOf { it.semanticFactEval.matchedFacts }
        val factAcc = if (totalFacts > 0) matchedFacts.toDouble() / totalFacts.toDouble() else 1.0

        return CategoryAggregateResult(
            category = category,
            totalUtterances = count,
            averageWer = werCerAgg.averageWer,
            averageCer = werCerAgg.averageCer,
            exactMatchRate = werCerAgg.exactMatchRate,
            criticalTokenPrecision = precision,
            criticalTokenRecall = recall,
            criticalTokenF1 = f1,
            semanticFactAccuracy = factAcc
        )
    }

    fun aggregateLanguage(
        language: String,
        languageDisplayName: String,
        results: List<UtteranceEvaluationResult>
    ): LanguageAggregateResult {
        val count = results.size
        if (count == 0) {
            return LanguageAggregateResult(
                language = language,
                languageDisplayName = languageDisplayName,
                totalUtterances = 0,
                averageWer = 0.0,
                averageCer = 0.0,
                exactMatchRate = 0.0,
                criticalTokenPrecision = 1.0,
                criticalTokenRecall = 1.0,
                criticalTokenF1 = 1.0,
                semanticFactAccuracy = 1.0,
                categoryBreakdown = emptyMap()
            )
        }

        val werCerAgg = WerCerCalculator.aggregate(results.map { it.werCer })
        val totalExpectedTokens = results.sumOf { it.criticalTokenEval.expectedTokens.size }
        val totalFoundTokens = results.sumOf { it.criticalTokenEval.foundTokens.size }
        val recall = if (totalExpectedTokens > 0) totalFoundTokens.toDouble() / totalExpectedTokens.toDouble() else 1.0
        val precision = 1.0
        val f1 = if (precision + recall > 0.0) (2.0 * precision * recall) / (precision + recall) else 0.0

        val totalFacts = results.sumOf { it.semanticFactEval.totalFacts }
        val matchedFacts = results.sumOf { it.semanticFactEval.matchedFacts }
        val factAcc = if (totalFacts > 0) matchedFacts.toDouble() / totalFacts.toDouble() else 1.0

        val categories = results.groupBy { it.category }
        val catBreakdown = categories.mapValues { (cat, catResults) ->
            aggregateCategory(cat, catResults)
        }

        return LanguageAggregateResult(
            language = language,
            languageDisplayName = languageDisplayName,
            totalUtterances = count,
            averageWer = werCerAgg.averageWer,
            averageCer = werCerAgg.averageCer,
            exactMatchRate = werCerAgg.exactMatchRate,
            criticalTokenPrecision = precision,
            criticalTokenRecall = recall,
            criticalTokenF1 = f1,
            semanticFactAccuracy = factAcc,
            categoryBreakdown = catBreakdown
        )
    }
}
