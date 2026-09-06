package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage

class LanguageRoutingTest {

    @Test
    fun testAllTenLanguagesConfigured() {
        assertEquals(10, IndicLanguage.entries.size)

        // Exact SIH target order: English, Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali
        val expectedCodes = listOf("en", "hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn")
        val actualCodes = IndicLanguage.entries.map { it.isoCode }
        assertEquals(expectedCodes, actualCodes)

        // Verify roundtrip from ID and ISO
        IndicLanguage.entries.forEach { lang ->
            assertEquals(lang, IndicLanguage.fromId(lang.id))
            assertEquals(lang, IndicLanguage.fromIsoCode(lang.isoCode))
            assertNotNull(lang.scriptSample)
            assertTrue("Script sample must not be blank", lang.scriptSample.isNotBlank())
        }
    }

    @Test
    fun testAutoLidHonestState_noSilentHindiFallback() {
        val lid = org.sih.itantra.core.language.OfflineLanguageIdentifier()
        // Must report false honestly
        assertEquals(false, lid.isAvailable())

        val previous = IndicLanguage.TELUGU
        val autoUnavailableState = org.sih.itantra.core.language.LanguageSelectionState.AutoUnavailable(previous)

        // Must not silently substitute Hindi
        assertEquals(previous, autoUnavailableState.previousLanguage)
        assertEquals("AUTO UNAVAILABLE", autoUnavailableState.displayTitle)
    }
}
