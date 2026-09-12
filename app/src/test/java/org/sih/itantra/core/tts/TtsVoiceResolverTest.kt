package org.sih.itantra.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage

class TtsVoiceResolverTest {

    @Test
    fun testAllTenIndicLanguagesResolveToCorrectProfiles() {
        val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT)

        val testCases = listOf(
            IndicLanguage.HINDI to Pair("hi_rohan", TtsEngineType.PIPER),
            IndicLanguage.GUJARATI to Pair("gu_mimic3", TtsEngineType.MIMIC3),
            IndicLanguage.MARATHI to Pair("mr_google", TtsEngineType.PIPER),
            IndicLanguage.KANNADA to Pair("kn_mms", TtsEngineType.MMS),
            IndicLanguage.MALAYALAM to Pair("ml_arjun", TtsEngineType.PIPER),
            IndicLanguage.TAMIL to Pair("ta_mms", TtsEngineType.MMS),
            IndicLanguage.TELUGU to Pair("te_maya", TtsEngineType.PIPER),
            IndicLanguage.ODIA to Pair("or_mms", TtsEngineType.MMS),
            IndicLanguage.BENGALI to Pair("bn_google", TtsEngineType.PIPER),
            IndicLanguage.ENGLISH to Pair("en_lessac", TtsEngineType.PIPER)
        )

        for ((indicLang, expected) in testCases) {
            val (expectedVoiceId, expectedEngineType) = expected
            val res = resolver.resolve(messageLanguage = indicLang)
            assertTrue("Expected Resolved for ${indicLang.name}, got $res", res is TtsResolutionResult.Resolved)
            val resolved = res as TtsResolutionResult.Resolved
            assertEquals(expectedVoiceId, resolved.profile.voiceId)
            assertEquals(expectedEngineType, resolved.profile.engineType)
            assertEquals(ResolutionSource.MESSAGE_METADATA, resolved.source)
            assertTrue("Voice should be marked available", resolved.profile.isAvailable)
            assertEquals("TTS READY", resolved.profile.statusLabel)
        }
    }

    @Test
    fun testSampleRateAllocation() {
        val registry = TtsVoiceRegistry.DEFAULT
        // MMS models (Tamil, Kannada, Odia) operate at 16000 Hz
        assertEquals(16000, registry.getProfile(TtsLanguage.TAMIL).sampleRate)
        assertEquals(16000, registry.getProfile(TtsLanguage.KANNADA).sampleRate)
        assertEquals(16000, registry.getProfile(TtsLanguage.ODIA).sampleRate)

        // Piper and Mimic3 models operate at 22050 Hz
        assertEquals(22050, registry.getProfile(TtsLanguage.HINDI).sampleRate)
        assertEquals(22050, registry.getProfile(TtsLanguage.GUJARATI).sampleRate)
        assertEquals(22050, registry.getProfile(TtsLanguage.MARATHI).sampleRate)
        assertEquals(22050, registry.getProfile(TtsLanguage.MALAYALAM).sampleRate)
        assertEquals(22050, registry.getProfile(TtsLanguage.TELUGU).sampleRate)
        assertEquals(22050, registry.getProfile(TtsLanguage.BENGALI).sampleRate)
        assertEquals(22050, registry.getProfile(TtsLanguage.ENGLISH).sampleRate)
    }

    @Test
    fun testExplicitMessageMetadataPriorityOverPeerAndActiveApp() {
        val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT)

        val result = resolver.resolve(
            messageLanguage = IndicLanguage.TAMIL,
            peerLanguage = IndicLanguage.HINDI,
            activeAppLanguage = IndicLanguage.ENGLISH
        )

        assertTrue(result is TtsResolutionResult.Resolved)
        val resolved = result as TtsResolutionResult.Resolved
        assertEquals(TtsLanguage.TAMIL, resolved.profile.language)
        assertEquals(ResolutionSource.MESSAGE_METADATA, resolved.source)
    }

    @Test
    fun testPeerMetadataFallbackWhenMessageMetadataIsUnknown() {
        val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT)

        val result = resolver.resolve(
            messageLanguage = null, // Unknown / missing
            peerLanguage = IndicLanguage.GUJARATI,
            activeAppLanguage = IndicLanguage.ENGLISH
        )

        assertTrue(result is TtsResolutionResult.Resolved)
        val resolved = result as TtsResolutionResult.Resolved
        assertEquals(TtsLanguage.GUJARATI, resolved.profile.language)
        assertEquals(ResolutionSource.PEER_METADATA, resolved.source)
    }

    @Test
    fun testAppActiveFallbackWhenMessageAndPeerAreUnknown() {
        val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT)

        val result = resolver.resolve(
            messageLanguage = null,
            peerLanguage = null,
            activeAppLanguage = IndicLanguage.MARATHI
        )

        assertTrue(result is TtsResolutionResult.Resolved)
        val resolved = result as TtsResolutionResult.Resolved
        assertEquals(TtsLanguage.MARATHI, resolved.profile.language)
        assertEquals(ResolutionSource.APP_ACTIVE_FALLBACK, resolved.source)
    }

    @Test
    fun testAllUnknownReturnsUnknownLanguageWithEnglishFallback() {
        val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT, allowFallbackToDefault = true)

        val result = resolver.resolveTtsLanguage(
            messageLanguage = TtsLanguage.UNKNOWN,
            peerLanguage = TtsLanguage.UNKNOWN,
            activeAppLanguage = TtsLanguage.UNKNOWN
        )

        assertTrue(result is TtsResolutionResult.UnknownLanguage)
        val unknown = result as TtsResolutionResult.UnknownLanguage
        assertNotNull(unknown.fallbackProfile)
        assertEquals(TtsLanguage.ENGLISH, unknown.fallbackProfile?.language)
        assertEquals("LANGUAGE UNKNOWN", unknown.reason)
    }

    @Test
    fun testAllUnknownWithNoFallbackReturnsNullProfile() {
        val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT, allowFallbackToDefault = false)

        val result = resolver.resolveTtsLanguage(
            messageLanguage = TtsLanguage.UNKNOWN,
            peerLanguage = TtsLanguage.UNKNOWN,
            activeAppLanguage = TtsLanguage.UNKNOWN
        )

        assertTrue(result is TtsResolutionResult.UnknownLanguage)
        val unknown = result as TtsResolutionResult.UnknownLanguage
        assertNull(unknown.fallbackProfile)
    }

    @Test
    fun testUnavailableModelReportsUnavailableResult() {
        // Mock registry where Malayalam model is missing / not extracted
        val registry = TtsVoiceRegistry { lang ->
            lang != TtsLanguage.MALAYALAM
        }
        val resolver = TtsVoiceResolver(registry)

        val result = resolver.resolve(messageLanguage = IndicLanguage.MALAYALAM)
        assertTrue(result is TtsResolutionResult.Unavailable)
        val unavail = result as TtsResolutionResult.Unavailable
        assertEquals(TtsLanguage.MALAYALAM, unavail.language)
        assertEquals("TTS UNAVAILABLE", unavail.reason)

        val profile = registry.getProfile(TtsLanguage.MALAYALAM)
        assertFalse(profile.isAvailable)
        assertEquals("TTS UNAVAILABLE", profile.statusLabel)
    }

    @Test
    fun testTtsLanguageConversionHelpers() {
        // IndicLanguage mapping
        IndicLanguage.entries.forEach { indic ->
            val ttsLang = TtsLanguage.fromIndicLanguage(indic)
            assertEquals(indic, ttsLang.toIndicLanguageOrNull())
            assertEquals(indic.id, ttsLang.id)
            assertEquals(indic.isoCode, ttsLang.isoCode)
            assertEquals(indic.isoCode.uppercase(), ttsLang.badgeCode)
        }

        // Null IndicLanguage mapping
        assertEquals(TtsLanguage.UNKNOWN, TtsLanguage.fromIndicLanguage(null))
        assertNull(TtsLanguage.UNKNOWN.toIndicLanguageOrNull())

        // ISO code mapping (case insensitive, trim)
        assertEquals(TtsLanguage.HINDI, TtsLanguage.fromIsoCode("hi"))
        assertEquals(TtsLanguage.HINDI, TtsLanguage.fromIsoCode("HI"))
        assertEquals(TtsLanguage.HINDI, TtsLanguage.fromIsoCode("  hi  "))
        assertEquals(TtsLanguage.TAMIL, TtsLanguage.fromIsoCode("ta"))
        assertEquals(TtsLanguage.UNKNOWN, TtsLanguage.fromIsoCode("xx"))
        assertEquals(TtsLanguage.UNKNOWN, TtsLanguage.fromIsoCode(""))
        assertEquals(TtsLanguage.UNKNOWN, TtsLanguage.fromIsoCode(null))

        // Byte ID mapping
        assertEquals(TtsLanguage.HINDI, TtsLanguage.fromId(0))
        assertEquals(TtsLanguage.ENGLISH, TtsLanguage.fromId(9))
        assertEquals(TtsLanguage.UNKNOWN, TtsLanguage.fromId(100.toByte()))
        assertEquals(TtsLanguage.UNKNOWN, TtsLanguage.fromId(null))
    }

    @Test
    fun testResolverCacheIdempotency() {
        val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT)

        val res1 = resolver.resolve(IndicLanguage.ODIA)
        val res2 = resolver.resolve(IndicLanguage.ODIA)
        assertEquals(res1, res2)

        resolver.clearCache()
        val res3 = resolver.resolve(IndicLanguage.ODIA)
        assertEquals(res1, res3)
    }
}
