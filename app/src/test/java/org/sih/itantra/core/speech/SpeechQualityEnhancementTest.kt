package org.sih.itantra.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.audio.SpeechIntelligibilityFilter
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.stt.SentenceFinalizer
import org.sih.itantra.core.stt.TacticalDomainReranker
import org.sih.itantra.core.tts.IndicTextPreprocessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Verification test suite for STT Low Word Error Rate (WER) and TTS High Legibility/Flow pipelines.
 */
class SpeechQualityEnhancementTest {

    // =========================================================================
    // 1. IndicTextPreprocessor Tests (TTS Legibility, Numbers, Acronyms)
    // =========================================================================

    @Test
    fun testIndicTextPreprocessorNumbersAcrossLanguages() {
        // Hindi: 40% -> चालीस प्रतिशत, 13.1 -> तेरह दशमलव एक
        val hiResult = IndicTextPreprocessor.process("बैटरी 40% है और वेपॉइंट 13.1 है", IndicLanguage.HINDI)
        assertTrue("Expected चालीस प्रतिशत in Hindi: $hiResult", hiResult.contains("चालीस प्रतिशत"))
        assertTrue("Expected तेरह दशमलव एक in Hindi: $hiResult", hiResult.contains("तेरह दशमलव एक"))

        // Marathi: 40% -> चाळीस टक्के
        val mrResult = IndicTextPreprocessor.process("बॅटरी 40% शिल्लक आहे", IndicLanguage.MARATHI)
        assertTrue("Expected चाळीस टक्के in Marathi: $mrResult", mrResult.contains("चाळीस टक्के"))

        // Gujarati: 40% -> ચાલીસ ટકા
        val guResult = IndicTextPreprocessor.process("બેટરી 40% છે", IndicLanguage.GUJARATI)
        assertTrue("Expected ચાલીસ ટકા in Gujarati: $guResult", guResult.contains("ચાલીસ ટકા"))

        // Bengali: 40% -> চল্লিশ শতাংশ
        val bnResult = IndicTextPreprocessor.process("ব্যাটারি 40% আছে", IndicLanguage.BENGALI)
        assertTrue("Expected চল্লিশ শতাংশ in Bengali: $bnResult", bnResult.contains("চল্লিশ শতাংশ"))

        // Telugu: 40% -> నలభై శాతం
        val teResult = IndicTextPreprocessor.process("బ్యాటరీ 40% ఉంది", IndicLanguage.TELUGU)
        assertTrue("Expected నలభై శాతం in Telugu: $teResult", teResult.contains("నలభై శాతం"))

        // Kannada: 40% -> ನಲವತ್ತು ಪ್ರತಿಶತ
        val knResult = IndicTextPreprocessor.process("ಬ್ಯಾಟರಿ 40% ಇದೆ", IndicLanguage.KANNADA)
        assertTrue("Expected ನಲವತ್ತು ಪ್ರತಿಶತ in Kannada: $knResult", knResult.contains("ನಲವತ್ತು ಪ್ರತಿಶತ"))

        // Malayalam: 40% -> നാൽപ്പത് ശതമാനം
        val mlResult = IndicTextPreprocessor.process("ബാറ്ററി 40% ഉണ്ട്", IndicLanguage.MALAYALAM)
        assertTrue("Expected നാൽപ്പത് ശതമാനം in Malayalam: $mlResult", mlResult.contains("നാൽപ്പത് ശതമാനം"))

        // Odia: 40% -> ଚାଳିଶ ପ୍ରତିଶତ
        val orResult = IndicTextPreprocessor.process("ବ୍ୟାଟେରୀ 40% ଅଛି", IndicLanguage.ODIA)
        assertTrue("Expected ଚାଳିଶ ପ୍ରତିଶତ in Odia: $orResult", orResult.contains("ଚାଳିଶ ପ୍ରତିଶତ"))

        // English: 40% -> forty percent
        val enResult = IndicTextPreprocessor.process("Battery is at 40%", IndicLanguage.ENGLISH)
        assertTrue("Expected forty percent in English: $enResult", enResult.contains("forty percent"))
    }

    @Test
    fun testIndicTextPreprocessorNativeNumeralMapping() {
        // Devanagari numerals ०-९ mapped and verbalized
        val devanagariText = "चैनल १ पर संपर्क करें"
        val hiProcessed = IndicTextPreprocessor.process(devanagariText, IndicLanguage.HINDI)
        assertTrue("Expected एक in Hindi: $hiProcessed", hiProcessed.contains("एक"))

        // Bengali numerals mapped
        val bengaliText = "চ্যানেল ১"
        val bnProcessed = IndicTextPreprocessor.process(bengaliText, IndicLanguage.BENGALI)
        assertTrue("Expected এক in Bengali: $bnProcessed", bnProcessed.contains("এক"))

        // Gujarati numerals mapped
        val gujaratiText = "ચેનલ ૧"
        val guProcessed = IndicTextPreprocessor.process(gujaratiText, IndicLanguage.GUJARATI)
        assertTrue("Expected એક in Gujarati: $guProcessed", guProcessed.contains("એક"))
    }

    @Test
    fun testIndicTextPreprocessorTacticalAcronyms() {
        // SOS, PTT, GPS, MEDEVAC across languages
        val hiSos = IndicTextPreprocessor.process("SOS PTT GPS MEDEVAC", IndicLanguage.HINDI)
        assertTrue("Hindi should have एस ओ एस: $hiSos", hiSos.contains("एस ओ एस"))
        assertTrue("Hindi should have पी टी टी: $hiSos", hiSos.contains("पी टी टी"))
        assertTrue("Hindi should have जी पी एस: $hiSos", hiSos.contains("जी पी एस"))
        assertTrue("Hindi should have मेडिवैक: $hiSos", hiSos.contains("मेडिवैक"))

        val teSos = IndicTextPreprocessor.process("SOS GPS", IndicLanguage.TELUGU)
        assertTrue("Telugu should have ఎస్ ఓ ఎస్: $teSos", teSos.contains("ఎస్ ఓ ఎస్"))
        assertTrue("Telugu should have జి పి ఎస్: $teSos", teSos.contains("జి పి ఎస్"))

        val knSos = IndicTextPreprocessor.process("SOS PTT", IndicLanguage.KANNADA)
        assertTrue("Kannada should have ಎಸ್ ಓ ಎಸ್: $knSos", knSos.contains("ಎಸ್ ಓ ಎಸ್"))
        assertTrue("Kannada should have ಪಿ ಟಿ ಟಿ: $knSos", knSos.contains("ಪಿ ಟಿ ಟಿ"))
    }

    @Test
    fun testIndicTextPreprocessorTamilSpecializedDelegation() {
        val taText = "சேனல் 1ல் பேட்டரி 40% உள்ளது"
        val taResult = IndicTextPreprocessor.process(taText, IndicLanguage.TAMIL)
        assertFalse("Tamil result should not be blank", taResult.isBlank())
        assertTrue("Tamil should expand channel", taResult.contains("சேனல்"))
    }

    @Test
    fun testIndicTextPreprocessorPunctuationSanitization() {
        val textWithPunctuation = "कमांड अल्फा! क्या आप वहां हैं? स्थिति: सामान्य; संपर्क जारी रखें।"
        val processed = IndicTextPreprocessor.process(textWithPunctuation, IndicLanguage.HINDI)
        assertFalse("Should not contain exclamation mark", processed.contains("!"))
        assertFalse("Should not contain question mark", processed.contains("?"))
        assertFalse("Should not contain colon", processed.contains(":"))
        assertFalse("Should not contain semicolon", processed.contains(";"))
        assertFalse("Should not contain danda", processed.contains("।"))
    }

    // =========================================================================
    // 2. TacticalDomainReranker Tests (STT Low WER & Tactical Entity Recovery)
    // =========================================================================

    @Test
    fun testTacticalDomainRerankerEnglishCorrections() {
        val raw1 = "whether is clear and team this patrol"
        val out1 = TacticalDomainReranker.rerank(raw1, IndicLanguage.ENGLISH)
        assertEquals("weather is clear and team this patrol", out1)

        val raw2 = "All nodes maintain contac"
        val out2 = TacticalDomainReranker.rerank(raw2, IndicLanguage.ENGLISH)
        assertEquals("All nodes maintain contact", out2)

        val raw3 = "Position ladder to 12.9"
        val out3 = TacticalDomainReranker.rerank(raw3, IndicLanguage.ENGLISH)
        assertEquals("Position latitude 12.9", out3)

        val raw4 = "Mark Ordnett 77.2 on MAP"
        val out4 = TacticalDomainReranker.rerank(raw4, IndicLanguage.ENGLISH)
        assertEquals("coordinates 77.2 on MAP", out4)

        val raw5 = "command alpha this is squad bravo mayday"
        val out5 = TacticalDomainReranker.rerank(raw5, IndicLanguage.ENGLISH)
        assertEquals("COMMAND ALPHA this is SQUAD BRAVO MAYDAY", out5)

        val raw6 = "way point at grid ref 8.13.1"
        val out6 = TacticalDomainReranker.rerank(raw6, IndicLanguage.ENGLISH)
        assertEquals("waypoint at grid reference 13.1", out6)
    }

    @Test
    fun testTacticalDomainRerankerMultilingualCoverage() {
        // Hindi
        val hiRaw = "कमांड अल्फा चैनल 1 पर संपर्क करें"
        val hiOut = TacticalDomainReranker.rerank(hiRaw, IndicLanguage.HINDI)
        assertEquals("कमांड अल्फा चैनल एक पर संपर्क करें", hiOut)

        // Marathi
        val mrRaw = "चॅनल 1 वर संपर्क करा आणि वे पॉईंट कडे जा"
        val mrOut = TacticalDomainReranker.rerank(mrRaw, IndicLanguage.MARATHI)
        assertTrue(mrOut.contains("चॅनल एक"))
        assertTrue(mrOut.contains("वेपॉईंट"))

        // Tamil
        val taRaw = "சேனல் 1 ரோந்து குழு"
        val taOut = TacticalDomainReranker.rerank(taRaw, IndicLanguage.TAMIL)
        assertEquals("சேனல் ஒன்று ரோந்து குழு", taOut)

        // Telugu
        val teRaw = "ఛానెల్ 1 పెట్రోలింగ్ బృందం"
        val teOut = TacticalDomainReranker.rerank(teRaw, IndicLanguage.TELUGU)
        assertEquals("ఛానెల్ ఒకటి పెట్రోలింగ్ బృందం", teOut)

        // Bengali
        val bnRaw = "চ্যানেল 1 টহল ইউনিট"
        val bnOut = TacticalDomainReranker.rerank(bnRaw, IndicLanguage.BENGALI)
        assertEquals("চ্যানেল এক টহল ইউনিট", bnOut)

        // Gujarati
        val guRaw = "ચેનલ 1 પેટ્રોલિંગ જૂથ"
        val guOut = TacticalDomainReranker.rerank(guRaw, IndicLanguage.GUJARATI)
        assertEquals("ચેનલ એક પેટ્રોલિંગ જૂથ", guOut)

        // Kannada
        val knRaw = "ಚಾನೆಲ್ 1 ಗಸ್ತು ಪಡೆ"
        val knOut = TacticalDomainReranker.rerank(knRaw, IndicLanguage.KANNADA)
        assertEquals("ಚಾನೆಲ್ ಒಂದು ಗಸ್ತು ಪಡೆ", knOut)

        // Malayalam
        val mlRaw = "ചാനൽ 1 പട്രോളിംഗ് യൂണിറ്റ്"
        val mlOut = TacticalDomainReranker.rerank(mlRaw, IndicLanguage.MALAYALAM)
        assertEquals("ചാനൽ ഒന്ന് പട്രോളിംഗ് യൂണിറ്റ്", mlOut)

        // Odia
        val orRaw = "ଚ୍ୟାନେଲ 1 ପାଟ୍ରୋଲିଂ ୟୁନିଟ୍"
        val orOut = TacticalDomainReranker.rerank(orRaw, IndicLanguage.ODIA)
        assertEquals("ଚ୍ୟାନେଲ ଏକ ପାଟ୍ରୋଲିଂ ୟୁନିଟ୍", orOut)
    }

    @Test
    fun testTacticalDomainRerankerStutterAndLoopSuppression() {
        val stutterText = "ert ert ert ert ert"
        val cleaned = TacticalDomainReranker.rerank(stutterText, IndicLanguage.ENGLISH)
        assertEquals("ert", cleaned)

        val digitStutter = "5.5.5.5.5.5"
        val cleanedDigits = TacticalDomainReranker.rerank(digitStutter, IndicLanguage.ENGLISH)
        assertEquals("5", cleanedDigits)

        val decimalStutter = "1.0.0.0.0.0.0"
        val cleanedDecimal = TacticalDomainReranker.rerank(decimalStutter, IndicLanguage.ENGLISH)
        assertEquals("1.0", cleanedDecimal)
    }

    // =========================================================================
    // 3. SpeechIntelligibilityFilter Tests (DSP Audio Conditioning)
    // =========================================================================

    @Test
    fun testSpeechIntelligibilityFilterSilenceProducesSilence() {
        val sampleRate = 16000
        val numSamples = 320 // 20ms
        val silenceBytes = ByteArray(numSamples * 2)

        val filtered = SpeechIntelligibilityFilter.process(silenceBytes, sampleRate)
        assertEquals(silenceBytes.size, filtered.size)

        val buf = ByteBuffer.wrap(filtered).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numSamples) {
            assertEquals(0.toShort(), buf.get(i))
        }
    }

    @Test
    fun testSpeechIntelligibilityFilterPreventsClipping() {
        val sampleRate = 16000
        val numSamples = 500
        val pcmBytes = ByteArray(numSamples * 2)
        val bufIn = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        // Write maximum possible amplitude values (+32767 and -32768)
        for (i in 0 until numSamples) {
            val s = if (i % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE
            bufIn.put(s)
        }

        val filtered = SpeechIntelligibilityFilter.process(pcmBytes, sampleRate)
        assertEquals(pcmBytes.size, filtered.size)

        val bufOut = ByteBuffer.wrap(filtered).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numSamples) {
            val sample = bufOut.get(i)
            assertTrue("Sample must be within 16-bit range", sample in Short.MIN_VALUE..Short.MAX_VALUE)
        }
    }

    @Test
    fun testSpeechIntelligibilityFilterAttenuatesDcOffset() {
        val sampleRate = 16000
        val numSamples = 1600 // 100ms
        val pcmBytes = ByteArray(numSamples * 2)
        val bufIn = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        // Constant DC offset of 10000
        for (i in 0 until numSamples) {
            bufIn.put(10000.toShort())
        }

        val filtered = SpeechIntelligibilityFilter.process(pcmBytes, sampleRate)
        val bufOut = ByteBuffer.wrap(filtered).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        // Due to the 100Hz high-pass filter, the DC offset must decay close to 0 over 100ms
        val endSample = bufOut.get(numSamples - 1).toInt()
        assertTrue("End sample DC should be substantially attenuated, was $endSample", Math.abs(endSample) < 1000)
    }

    // =========================================================================
    // 4. SentenceFinalizer Integration
    // =========================================================================

    @Test
    fun testSentenceFinalizerRerankingIntegration() {
        val rawEnglish = "command alpha weather is clear"
        val finalizedEnglish = SentenceFinalizer.finalizeSentence(rawEnglish, IndicLanguage.ENGLISH)
        assertEquals("COMMAND ALPHA weather is clear.", finalizedEnglish)

        val rawHindi = "कमांड अल्फा चैनल 1 पर संपर्क करें"
        val finalizedHindi = SentenceFinalizer.finalizeSentence(rawHindi, IndicLanguage.HINDI)
        assertEquals("कमांड अल्फा चैनल एक पर संपर्क करें।", finalizedHindi)
    }
}
