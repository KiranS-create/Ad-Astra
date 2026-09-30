package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.stt.ClauseStreamingEngine

class ClauseStreamingEngineTest {

    @Test
    fun testSegmentHindiWithDandaAndConjunction() {
        val input = "बाढ़ का पानी तेजी से बढ़ रहा है और हमें तुरंत नाव की आवश्यकता है।"
        val clauses = ClauseStreamingEngine.segment(input, IndicLanguage.HINDI)

        assertTrue("Expected at least 2 clauses, got ${clauses.size}", clauses.size >= 2)
        assertEquals(0, clauses[0].clauseIndex)
        assertFalse(clauses[0].isFinal)
        assertTrue(clauses.last().isFinal)
    }

    @Test
    fun testSegmentTamilWithConjunction() {
        val input = "வெள்ளம் சூழ்ந்துள்ளது மற்றும் உடனடியாக மீட்புக் குழு தேவைப்படுகிறது."
        val clauses = ClauseStreamingEngine.segment(input, IndicLanguage.TAMIL)

        assertTrue("Expected at least 2 clauses, got ${clauses.size}", clauses.size >= 2)
        assertEquals(0, clauses[0].clauseIndex)
        assertTrue(clauses.last().isFinal)
    }

    @Test
    fun testSegmentEnglishCompoundSentence() {
        val input = "The bridge is washed out and medical supplies are urgently required immediately."
        val clauses = ClauseStreamingEngine.segment(input, IndicLanguage.ENGLISH)

        assertTrue("Expected at least 2 clauses, got ${clauses.size}", clauses.size >= 2)
        assertEquals(0, clauses[0].clauseIndex)
        assertFalse(clauses[0].isFinal)
        assertTrue(clauses.last().isFinal)
    }

    @Test
    fun testUrgentDistressKeywordFlagging() {
        val sosInput = "SOS help needed here बचाओ"
        val clauses = ClauseStreamingEngine.segment(sosInput, IndicLanguage.HINDI)

        assertTrue("Clauses containing emergency keywords should flag isDistressOrCommand", clauses.any { it.isDistressOrCommand })
    }

    @Test
    fun testStreamingAccumulatorProgressiveFeed() {
        val accumulator = ClauseStreamingEngine.StreamingAccumulator(IndicLanguage.HINDI)

        // Feed first phrase with comma
        val chunk1 = "सेक्टर चार में स्थिति गंभीर है, "
        val emitted1 = accumulator.feed(chunk1)
        // May or may not emit until enough boundary accumulation
        val chunk2 = "सभी लोग राहत शिविर की ओर बढ़ें।"
        val emitted2 = accumulator.feed(chunk2)

        val finalClauses = accumulator.finalizeUtterance()
        val allEmitted = emitted1 + emitted2 + finalClauses

        assertTrue("Utterance should produce at least 1 or 2 clauses total", allEmitted.isNotEmpty())
        assertTrue("Last clause must have isFinal = true", allEmitted.last().isFinal)
    }

    @Test
    fun testEmptyAndBlankInput() {
        val clauses = ClauseStreamingEngine.segment("   ", IndicLanguage.ENGLISH)
        assertTrue("Blank input produces empty clause list", clauses.isEmpty())
    }
}
