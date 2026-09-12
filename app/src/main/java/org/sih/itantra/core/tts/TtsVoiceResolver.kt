package org.sih.itantra.core.tts

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.persistence.MessageRecord
import java.util.concurrent.ConcurrentHashMap

enum class ResolutionSource {
    MESSAGE_METADATA,
    PEER_METADATA,
    APP_ACTIVE_FALLBACK,
    SYSTEM_DEFAULT
}

sealed class TtsResolutionResult {
    data class Resolved(
        val profile: TtsVoiceProfile,
        val source: ResolutionSource
    ) : TtsResolutionResult()

    data class Unavailable(
        val language: TtsLanguage,
        val reason: String = "TTS UNAVAILABLE"
    ) : TtsResolutionResult()

    data class UnknownLanguage(
        val fallbackProfile: TtsVoiceProfile? = null,
        val reason: String = "LANGUAGE UNKNOWN"
    ) : TtsResolutionResult()
}

/**
 * Deterministic voice resolver implementing the language resolution hierarchy:
 * 1. Explicit message metadata
 * 2. Peer/conversation metadata
 * 3. Active application fallback language
 * 4. Safe fallback or UNKNOWN
 */
class TtsVoiceResolver(
    private val registry: TtsVoiceRegistry = TtsVoiceRegistry.DEFAULT,
    private val allowFallbackToDefault: Boolean = true
) {
    private val cache = ConcurrentHashMap<String, TtsResolutionResult>()

    fun resolveTtsLanguage(
        messageLanguage: TtsLanguage,
        peerLanguage: TtsLanguage = TtsLanguage.UNKNOWN,
        activeAppLanguage: TtsLanguage = TtsLanguage.ENGLISH
    ): TtsResolutionResult {
        val cacheKey = "${messageLanguage.name}:${peerLanguage.name}:${activeAppLanguage.name}:$allowFallbackToDefault"
        cache[cacheKey]?.let { return it }

        val result = resolveInternal(messageLanguage, peerLanguage, activeAppLanguage)
        cache[cacheKey] = result
        return result
    }

    private fun resolveInternal(
        messageLanguage: TtsLanguage,
        peerLanguage: TtsLanguage,
        activeAppLanguage: TtsLanguage
    ): TtsResolutionResult {
        // 1. Explicit Message Metadata
        if (messageLanguage != TtsLanguage.UNKNOWN) {
            val profile = registry.getProfile(messageLanguage)
            return if (profile.isAvailable) {
                TtsResolutionResult.Resolved(profile, ResolutionSource.MESSAGE_METADATA)
            } else {
                TtsResolutionResult.Unavailable(messageLanguage, "TTS UNAVAILABLE")
            }
        }

        // 2. Peer / Conversation Metadata
        if (peerLanguage != TtsLanguage.UNKNOWN) {
            val profile = registry.getProfile(peerLanguage)
            return if (profile.isAvailable) {
                TtsResolutionResult.Resolved(profile, ResolutionSource.PEER_METADATA)
            } else {
                TtsResolutionResult.Unavailable(peerLanguage, "TTS UNAVAILABLE")
            }
        }

        // 3. Active Application Fallback Language
        if (activeAppLanguage != TtsLanguage.UNKNOWN) {
            val profile = registry.getProfile(activeAppLanguage)
            return if (profile.isAvailable) {
                TtsResolutionResult.Resolved(profile, ResolutionSource.APP_ACTIVE_FALLBACK)
            } else {
                TtsResolutionResult.Unavailable(activeAppLanguage, "TTS UNAVAILABLE")
            }
        }

        // 4. Safe Fallback or UNKNOWN
        val defaultProfile = if (allowFallbackToDefault) {
            val enProfile = registry.getProfile(TtsLanguage.ENGLISH)
            if (enProfile.isAvailable) enProfile else null
        } else null

        return TtsResolutionResult.UnknownLanguage(
            fallbackProfile = defaultProfile,
            reason = "LANGUAGE UNKNOWN"
        )
    }

    fun resolve(
        messageLanguage: IndicLanguage?,
        peerLanguage: IndicLanguage? = null,
        activeAppLanguage: IndicLanguage? = IndicLanguage.ENGLISH
    ): TtsResolutionResult {
        return resolveTtsLanguage(
            messageLanguage = TtsLanguage.fromIndicLanguage(messageLanguage),
            peerLanguage = TtsLanguage.fromIndicLanguage(peerLanguage),
            activeAppLanguage = TtsLanguage.fromIndicLanguage(activeAppLanguage)
        )
    }

    fun resolveFromRecord(
        record: MessageRecord,
        activeAppLanguage: IndicLanguage = IndicLanguage.ENGLISH
    ): TtsResolutionResult {
        return resolve(
            messageLanguage = record.language,
            peerLanguage = null,
            activeAppLanguage = activeAppLanguage
        )
    }

    fun clearCache() {
        cache.clear()
    }
}
