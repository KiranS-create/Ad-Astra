package org.sih.itantra.core.speech.benchmark

import org.junit.Assert.*
import org.junit.Test
import org.sih.itantra.core.speech.benchmark.multilang.WerCerCalculator

class WerCerCalculatorTest {

    @Test
    fun testExactMatchZeroError() {
        val ref = "सेक्टर चार में तत्काल बैकअप भेजें"
        val hyp = "सेक्टर चार में तत्काल बैकअप भेजें"

        val score = WerCerCalculator.evaluate(ref, hyp)

        assertTrue(score.exactMatch)
        assertEquals(0.0, score.wer.errorRate, 0.001)
        assertEquals(0.0, score.cer.errorRate, 0.001)
        assertEquals(6, score.wer.hits)
        assertEquals(0, score.wer.substitutions)
        assertEquals(0, score.wer.deletions)
        assertEquals(0, score.wer.insertions)
    }

    @Test
    fun testWordSubstitutionAndDeletion() {
        val ref = "Alpha 1 report status immediately"
        val hyp = "Alpha 1 report status"

        val score = WerCerCalculator.evaluate(ref, hyp)

        assertFalse(score.exactMatch)
        assertEquals(5, score.wer.totalReference)
        assertEquals(4, score.wer.hits)
        assertEquals(1, score.wer.deletions)
        assertEquals(0.20, score.wer.errorRate, 0.001)
    }

    @Test
    fun testIndicScriptNormalizationPunctuation() {
        val ref = "हॉक 1, स्थिति क्या है?"
        val hyp = "हॉक 1 स्थिति क्या है"

        val score = WerCerCalculator.evaluate(ref, hyp)

        // With punctuation stripped during normalization, this is an exact match
        assertTrue(score.exactMatch)
        assertEquals(0.0, score.wer.errorRate, 0.001)
    }

    @Test
    fun testTamilCerCalculation() {
        val ref = "பிரிவு 4 உடனடி உதவி தேவை"
        val hyp = "பிரிவு 4 உதவி தேவை" // Missing "உடனடி"

        val score = WerCerCalculator.evaluate(ref, hyp)

        assertFalse(score.exactMatch)
        assertTrue(score.wer.deletions >= 1)
        assertTrue(score.cer.deletions > 0)
        assertTrue(score.cer.errorRate > 0.0 && score.cer.errorRate < 1.0)
    }

    @Test
    fun testKannadaGraphemeTokenization() {
        val text = "ಸೆಕ್ಟರ್ 4"
        val tokens = WerCerCalculator.tokenizeChars(text)
        assertTrue(tokens.isNotEmpty())
        assertEquals("ಸೆಕ್ಟರ್4", tokens.joinToString(""))
    }

    @Test
    fun testAggregateScores() {
        val score1 = WerCerCalculator.evaluate("Alpha 1", "Alpha 1")
        val score2 = WerCerCalculator.evaluate("Sector 4", "Sector 5")

        val agg = WerCerCalculator.aggregate(listOf(score1, score2))

        assertEquals(2, agg.totalUtterances)
        assertEquals(1, agg.exactMatchCount)
        assertEquals(0.5, agg.exactMatchRate, 0.001)
        assertEquals(4, agg.totalReferenceWords)
        assertEquals(1, agg.totalSubstitutions)
        assertEquals(0.25, agg.averageWer, 0.001)
    }
}
