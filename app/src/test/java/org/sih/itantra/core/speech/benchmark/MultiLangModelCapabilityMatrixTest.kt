package org.sih.itantra.core.speech.benchmark

import org.junit.Assert.*
import org.junit.Test
import org.sih.itantra.core.speech.benchmark.multilang.MultiLangModelCapabilityMatrix

class MultiLangModelCapabilityMatrixTest {

    @Test
    fun testAllTenLanguagesPresent() {
        val caps = MultiLangModelCapabilityMatrix.getAllCapabilities()
        assertEquals(10, caps.size)

        val codes = MultiLangModelCapabilityMatrix.getSupportedLanguageCodes()
        val expectedCodes = listOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")
        assertEquals(expectedCodes.sorted(), codes.sorted())
    }

    @Test
    fun testLanguageDetailsNonEmpty() {
        for (lang in MultiLangModelCapabilityMatrix.getSupportedLanguageCodes()) {
            val cap = MultiLangModelCapabilityMatrix.getByLanguage(lang)
            assertNotNull("Capability for $lang must not be null", cap)
            assertTrue("Display name must not be blank for $lang", cap!!.languageDisplayName.isNotBlank())
            assertTrue("STT engine must not be blank for $lang", cap.sttEngine.isNotBlank())
            assertTrue("TTS engine must not be blank for $lang", cap.ttsEngine.isNotBlank())
            assertTrue("TTS voice name must not be blank for $lang", cap.ttsVoiceName.isNotBlank())
            assertTrue("Expected RTF must be positive for $lang", cap.sttExpectedRtfArm64 > 0.0)
            assertTrue("Sample rate must be valid for $lang", cap.ttsSampleRateHz in listOf(16000, 22050))
        }
    }

    @Test
    fun testTotalSystemFootprint() {
        val totalMb = MultiLangModelCapabilityMatrix.getTotalFootprintMb()
        assertTrue("Total footprint should be greater than 1000 MB across all 10 languages", totalMb > 1000.0)
    }

    @Test
    fun testOdiaFallbackDocumented() {
        val odia = MultiLangModelCapabilityMatrix.getByLanguage("or")
        assertNotNull(odia)
        assertTrue(odia!!.technicalNotes.contains("Whisper 99-language set", ignoreCase = true) ||
                   odia.sttEngine.contains("Fallback", ignoreCase = true))
    }
}
