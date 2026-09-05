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

        val expectedCodes = listOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")
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
}
