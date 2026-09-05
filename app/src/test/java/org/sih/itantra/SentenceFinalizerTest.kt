package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.stt.SentenceFinalizer

class SentenceFinalizerTest {

    @Test
    fun testHindiDandaFinalization() {
        val raw = "हम सुरक्षित हैं"
        val finalized = SentenceFinalizer.finalizeSentence(raw, IndicLanguage.HINDI)
        assertEquals("हम सुरक्षित हैं।", finalized)
    }

    @Test
    fun testExistingTerminatorPreserved() {
        val rawWithQuestion = "क्या आप वहां हैं?"
        val finalized = SentenceFinalizer.finalizeSentence(rawWithQuestion, IndicLanguage.HINDI)
        assertEquals("क्या आप वहां हैं?", finalized)
    }

    @Test
    fun testEnglishFullStopFinalization() {
        val raw = "Search and rescue team proceeding north"
        val finalized = SentenceFinalizer.finalizeSentence(raw, IndicLanguage.ENGLISH)
        assertEquals("Search and rescue team proceeding north.", finalized)
    }

    @Test
    fun testUtteranceSegmentation() {
        val multiSentence = "टीम तैयार है। हम आगे बढ़ रहे हैं। सब ठीक है।"
        val segments = SentenceFinalizer.segmentUtterance(multiSentence)
        assertEquals(3, segments.size)
        assertEquals("टीम तैयार है।", segments[0])
        assertEquals("हम आगे बढ़ रहे हैं।", segments[1])
        assertEquals("सब ठीक है।", segments[2])
    }
}
