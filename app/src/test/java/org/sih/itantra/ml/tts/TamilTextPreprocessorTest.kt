package org.sih.itantra.ml.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TamilTextPreprocessorTest {

    @Test
    fun testBaselineSentencePreprocessing() {
        val input = "வணக்கம், இது ஐ-தந்த்ரா ஆஃப்லைன் நியூரல் பேச்சு தொகுப்பு சோதனை."
        val processed = TamilTextPreprocessor.process(input)

        // Aytham 'ஃ' should be adapted to 'ப்' in ஆஃப்லைன் -> ஆப்லைன்
        assertTrue("Aytham should be adapted", processed.contains("ஆப்லைன்"))
        // Punctuation should be normalized to whitespace
        assertTrue("Commas should be removed", !processed.contains(","))
        assertTrue("Periods should be removed", !processed.contains("."))
        // Hyphens should be normalized
        assertTrue("Hyphens should be removed", !processed.contains("-"))
        // Should not have multiple spaces
        assertTrue("No multiple consecutive spaces", !processed.contains("  "))
    }

    @Test
    fun testMissingDigit8AndSuffixExpansion() {
        val input = "அவசர எச்சரிக்கை! பகுதி 8ல் உடனடியாக உதவி தேவை."
        val processed = TamilTextPreprocessor.process(input)

        // 8 is absent in MMS tokens.txt, must be expanded to Tamil words with suffix: எட்டில்
        assertEquals("அவசர எச்சரிக்கை பகுதி எட்டில் உடனடியாக உதவி தேவை", processed)
    }

    @Test
    fun testYearAndCompoundNumbers() {
        val input = "2026 ஆம் ஆண்டு"
        val processed = TamilTextPreprocessor.process(input)

        assertEquals("இரண்டாயிரத்து இருபத்து ஆறு ஆம் ஆண்டு", processed)
    }

    @Test
    fun testOperationalEnglishWordsAndAcronyms() {
        val input = "iTantra Radio PTT SOS OK GPS"
        val processed = TamilTextPreprocessor.process(input)

        assertTrue(processed.contains("ஐ தந்த்ரா"))
        assertTrue(processed.contains("ரேடியோ"))
        assertTrue(processed.contains("பி டி டி"))
        assertTrue(processed.contains("எஸ் ஓ எஸ்"))
        assertTrue(processed.contains("சரி"))
        assertTrue(processed.contains("ஜி பி எஸ்"))
    }

    @Test
    fun testTamilNumerals() {
        val input = "பகுதி ௮"
        val processed = TamilTextPreprocessor.process(input)

        assertEquals("பகுதி எட்டு", processed)
    }

    @Test
    fun testNumbersToWords() {
        assertEquals("பூஜ்ஜியம்", TamilTextPreprocessor.numberToTamilWords(0))
        assertEquals("ஒன்று", TamilTextPreprocessor.numberToTamilWords(1))
        assertEquals("எட்டு", TamilTextPreprocessor.numberToTamilWords(8))
        assertEquals("பத்து", TamilTextPreprocessor.numberToTamilWords(10))
        assertEquals("பதினெட்டு", TamilTextPreprocessor.numberToTamilWords(18))
        assertEquals("இருபது", TamilTextPreprocessor.numberToTamilWords(20))
        assertEquals("இருபத்து எட்டு", TamilTextPreprocessor.numberToTamilWords(28))
        assertEquals("நூறு", TamilTextPreprocessor.numberToTamilWords(100))
        assertEquals("நூற்று எட்டு", TamilTextPreprocessor.numberToTamilWords(108))
        assertEquals("ஆயிரம்", TamilTextPreprocessor.numberToTamilWords(1000))
    }

    @Test
    fun testEmptyAndBlank() {
        assertEquals("", TamilTextPreprocessor.process(""))
        assertEquals("", TamilTextPreprocessor.process("   "))
    }
}
