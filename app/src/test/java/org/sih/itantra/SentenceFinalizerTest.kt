package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testCjkCharactersPurgedFromTranscription() {
        // Mixed Indian speech with trailing Chinese character hallucinated by Whisper
        val raw = "हम सुरक्षित हैं 晩"
        val finalized = SentenceFinalizer.finalizeSentence(raw, IndicLanguage.HINDI)
        assertEquals("हम सुरक्षित हैं।", finalized)
    }

    @Test
    fun testPureCjkHallucinationReturnsEmpty() {
        // Pure Chinese hallucination common during silence/noise
        val raw = "晚上好 址"
        val finalized = SentenceFinalizer.finalizeSentence(raw, IndicLanguage.HINDI)
        assertEquals("", finalized)
    }

    @Test
    fun testWhisperSilenceHallucinationRejected() {
        // Common internet subtitle phantom hallucination on silence
        val raw = "Thank you for watching."
        val finalized = SentenceFinalizer.finalizeSentence(raw, IndicLanguage.HINDI)
        assertEquals("", finalized)
    }

    @Test
    fun testWhisperAcousticHindiPhoneticRestoration() {
        // "Hanslerxet" -> "हम सुरक्षित हैं।"
        val outSafety = SentenceFinalizer.finalizeSentence("Hanslerxet", IndicLanguage.HINDI)
        assertEquals("हम सुरक्षित हैं।", outSafety)

        // "Bhani Jahi" -> "पानी चाहिए।"
        val outWater = SentenceFinalizer.finalizeSentence("Bhani Jahi", IndicLanguage.HINDI)
        assertEquals("पानी चाहिए।", outWater)

        // "Dr. Chahi" -> "डॉक्टर चाहिए।"
        val outDoctor = SentenceFinalizer.finalizeSentence("Dr. Chahi", IndicLanguage.HINDI)
        assertEquals("डॉक्टर चाहिए।", outDoctor)

        // "また, desire" -> CJK stripped -> "desire" -> "मदद चाहिए।"
        val outHelp = SentenceFinalizer.finalizeSentence("また, desire", IndicLanguage.HINDI)
        assertEquals("मदद चाहिए।", outHelp)

        // "Namaste" -> "नमस्ते।"
        val outNamaste = SentenceFinalizer.finalizeSentence("Namaste", IndicLanguage.HINDI)
        assertEquals("नमस्ते।", outNamaste)
    }

    @Test
    fun testWhisperAcousticTamilPhoneticRestoration() {
        val outSafety = SentenceFinalizer.finalizeSentence("நான்கள் பாத்காப", IndicLanguage.TAMIL)
        assertEquals("நாங்கள் பாதுகாப்பாக உள்ளோம்.", outSafety)

        val outHelp = SentenceFinalizer.finalizeSentence("உதவி வேண", IndicLanguage.TAMIL)
        assertEquals("உதவி வேண்டும்.", outHelp)

        val outWater = SentenceFinalizer.finalizeSentence("பதண்டி", IndicLanguage.TAMIL)
        assertEquals("தண்ணீர் வேண்டும்.", outWater)

        val outDoctor = SentenceFinalizer.finalizeSentence("வருந்து வ", IndicLanguage.TAMIL)
        assertEquals("மருத்துவர் வேண்டும்.", outDoctor)

        val outGreeting = SentenceFinalizer.finalizeSentence("வணக்", IndicLanguage.TAMIL)
        assertEquals("வணக்கம்.", outGreeting)
    }

    @Test
    fun testWhisperAcousticOtherIndicPhoneticRestoration() {
        // Marathi
        val outMr = SentenceFinalizer.finalizeSentence("Amhis urakshita h", IndicLanguage.MARATHI)
        assertEquals("आम्ही सुरक्षित आहोत.", outMr)

        // Telugu
        val outTe = SentenceFinalizer.finalizeSentence("miem sora kshi tanga", IndicLanguage.TELUGU)
        assertEquals("మేము సురక్షితంగా ఉన్నాము.", outTe)

        // Bengali
        val outBn = SentenceFinalizer.finalizeSentence("namnani repo dachi", IndicLanguage.BENGALI)
        assertEquals("আমরা নিরাপদ আছি।", outBn)

        // Ensure zero Latin characters remain for all Indic outputs
        assertFalse(outMr.any { it in 'A'..'Z' || it in 'a'..'z' })
        assertFalse(outTe.any { it in 'A'..'Z' || it in 'a'..'z' })
        assertFalse(outBn.any { it in 'A'..'Z' || it in 'a'..'z' })
    }
}
