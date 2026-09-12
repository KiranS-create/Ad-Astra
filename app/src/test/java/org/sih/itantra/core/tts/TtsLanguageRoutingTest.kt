package org.sih.itantra.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import java.util.UUID

class TtsLanguageRoutingTest {

    private val resolver = TtsVoiceResolver(TtsVoiceRegistry.DEFAULT)

    private fun createRecord(
        language: IndicLanguage,
        priority: MessagePriority = MessagePriority.NORMAL,
        text: String = "Test transmission"
    ): MessageRecord {
        return MessageRecord(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.RECEIVED,
            language = language,
            priority = priority,
            text = text,
            peer = "Node #101",
            packetSizeBytes = 64,
            rawAudioEquivalentBytes = 32000L,
            measuredLatencyMs = 120.0
        )
    }

    @Test
    fun testRoutingDirectFromMessageRecord() {
        val tamilRecord = createRecord(IndicLanguage.TAMIL, text = "வணக்கம்")
        val result = resolver.resolveFromRecord(tamilRecord, activeAppLanguage = IndicLanguage.ENGLISH)

        assertTrue(result is TtsResolutionResult.Resolved)
        val resolved = result as TtsResolutionResult.Resolved
        assertEquals(TtsLanguage.TAMIL, resolved.profile.language)
        assertEquals("ta_mms", resolved.profile.voiceId)
        assertEquals(16000, resolved.profile.sampleRate)
        assertEquals(ResolutionSource.MESSAGE_METADATA, resolved.source)
    }

    @Test
    fun testRoutingBengaliRecord() {
        val bengaliRecord = createRecord(IndicLanguage.BENGALI, text = "আমরা নিরাপদ আছি")
        val result = resolver.resolveFromRecord(bengaliRecord, activeAppLanguage = IndicLanguage.HINDI)

        assertTrue(result is TtsResolutionResult.Resolved)
        val resolved = result as TtsResolutionResult.Resolved
        assertEquals(TtsLanguage.BENGALI, resolved.profile.language)
        assertEquals("bn_google", resolved.profile.voiceId)
        assertEquals(22050, resolved.profile.sampleRate)
    }

    @Test
    fun testEmergencyDistressPrioritizationFlags() {
        val distressRecord = createRecord(
            language = IndicLanguage.HINDI,
            priority = MessagePriority.DISTRESS,
            text = "आपातकालीन सहायता की आवश्यकता है"
        )

        assertTrue("Distress priority must be recognized as emergency", distressRecord.priority.isEmergency)

        val result = resolver.resolveFromRecord(distressRecord)
        assertTrue(result is TtsResolutionResult.Resolved)
        val resolved = result as TtsResolutionResult.Resolved
        assertEquals(TtsLanguage.HINDI, resolved.profile.language)
    }

    @Test
    fun testNoSilentHindiFallbackWhenLanguageIsUnsupported() {
        // If an asset is unavailable, it must NEVER silently substitute Hindi
        val registry = TtsVoiceRegistry { lang ->
            lang == TtsLanguage.HINDI // Only Hindi is available
        }
        val strictResolver = TtsVoiceResolver(registry, allowFallbackToDefault = false)

        val teluguRecord = createRecord(IndicLanguage.TELUGU)
        val result = strictResolver.resolveFromRecord(teluguRecord)

        assertTrue("Must report Unavailable instead of silently playing Hindi", result is TtsResolutionResult.Unavailable)
        val unavail = result as TtsResolutionResult.Unavailable
        assertEquals(TtsLanguage.TELUGU, unavail.language)
    }

    @Test
    fun testAllProfilesRegistryIntegrity() {
        val registry = TtsVoiceRegistry.DEFAULT
        val profiles = registry.getAllProfiles()
        assertEquals(10, profiles.size)

        val supported = registry.getSupportedLanguages()
        assertEquals(10, supported.size)
        assertFalse("UNKNOWN must not be listed as supported language", supported.contains(TtsLanguage.UNKNOWN))
    }
}
