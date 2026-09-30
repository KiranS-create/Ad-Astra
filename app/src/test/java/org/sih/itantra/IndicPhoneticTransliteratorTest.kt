package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.stt.IndicPhoneticTransliterator

class IndicPhoneticTransliteratorTest {

    @Test
    fun testEnglishPassthrough() {
        val eng = "COMMAND ALPHA this is SQUAD BRAVO we are safe."
        val out = IndicPhoneticTransliterator.transliterate(eng, IndicLanguage.ENGLISH)
        assertEquals(eng, out)
    }

    @Test
    fun testHindiDictionaryAndPhonetics() {
        val raw = "hum surakshit hain"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.HINDI)
        assertEquals("हम सुरक्षित हैं", out)

        val rawHelp = "madad chahiye"
        val outHelp = IndicPhoneticTransliterator.transliterate(rawHelp, IndicLanguage.HINDI)
        assertEquals("मदद चाहिए", outHelp)

        val rawWater = "paani chahiye"
        val outWater = IndicPhoneticTransliterator.transliterate(rawWater, IndicLanguage.HINDI)
        assertEquals("पानी चाहिए", outWater)

        val rawGreeting = "namaste"
        val outGreeting = IndicPhoneticTransliterator.transliterate(rawGreeting, IndicLanguage.HINDI)
        assertEquals("नमस्ते", outGreeting)

        // Ensure no Latin letters remain
        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testTamilDictionaryAndPhonetics() {
        val raw = "naangal paathukaappu"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.TAMIL)
        assertEquals("நாங்கள் பாதுகாப்பு", out)

        val rawHelp = "uthavi vendum"
        val outHelp = IndicPhoneticTransliterator.transliterate(rawHelp, IndicLanguage.TAMIL)
        assertEquals("உதவி வேண்டும்", outHelp)

        val rawGreeting = "vanakkam"
        val outGreeting = IndicPhoneticTransliterator.transliterate(rawGreeting, IndicLanguage.TAMIL)
        assertEquals("வணக்கம்", outGreeting)

        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testTeluguDictionaryAndPhonetics() {
        val raw = "memu surakshitanga unnamu"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.TELUGU)
        assertEquals("మేము సురక్షితంగా ఉన్నాము", out)

        val rawHelp = "sahayam kavali"
        val outHelp = IndicPhoneticTransliterator.transliterate(rawHelp, IndicLanguage.TELUGU)
        assertEquals("సహాయం కావాలి", outHelp)

        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testBengaliDictionaryAndPhonetics() {
        val raw = "amra nirapod achhi"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.BENGALI)
        assertEquals("আমরা নিরাপদ আছি", out)

        val rawHelp = "sahajjo chai"
        val outHelp = IndicPhoneticTransliterator.transliterate(rawHelp, IndicLanguage.BENGALI)
        assertEquals("সাহায্য চাই", outHelp)

        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testGujaratiDictionaryAndPhonetics() {
        val raw = "ame surakshit chhiye"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.GUJARATI)
        assertEquals("અમે સુરક્ષિત છીએ", out)

        val rawHelp = "madad joiye"
        val outHelp = IndicPhoneticTransliterator.transliterate(rawHelp, IndicLanguage.GUJARATI)
        assertEquals("મદદ જોઈએ", outHelp)

        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testMarathiDictionaryAndPhonetics() {
        val raw = "aamhi surakshit aahot"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.MARATHI)
        assertEquals("आम्ही सुरक्षित आहोत", out)

        val rawHelp = "madat havi aahe"
        val outHelp = IndicPhoneticTransliterator.transliterate(rawHelp, IndicLanguage.MARATHI)
        assertEquals("मदत हवी आहे", outHelp)

        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testKannadaDictionaryAndPhonetics() {
        val raw = "naavu surakshitavagiddeve"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.KANNADA)
        assertEquals("ನಾವು ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ", out)

        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testMalayalamDictionaryAndPhonetics() {
        val raw = "njangal surakshitharanu"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.MALAYALAM)
        assertEquals("ഞങ്ങൾ സുരക്ഷിതരാണ്", out)

        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testOdiaDictionaryAndPhonetics() {
        val raw = "ame surakshita achhu"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.ODIA)
        assertEquals("ଆମେ ସୁରକ୍ଷିତ ଅଛୁ", out)

        assertFalse(out.any { it in 'A'..'Z' || it in 'a'..'z' })
    }

    @Test
    fun testPunctuationAndNumericIntegrityPreserved() {
        val raw = "12 madad chahiye!"
        val out = IndicPhoneticTransliterator.transliterate(raw, IndicLanguage.HINDI)
        assertTrue(out.startsWith("12"))
        assertTrue(out.endsWith("!"))
        assertTrue(out.contains("मदद चाहिए"))
    }
}
