package org.sih.itantra.core.language

import org.sih.itantra.core.common.IndicLanguage

sealed class LanguageIdentificationResult {
    data class Identified(val language: IndicLanguage, val confidence: Float) : LanguageIdentificationResult()
    data object Unavailable : LanguageIdentificationResult()
    data object Undetermined : LanguageIdentificationResult()
}

interface LanguageIdentifier {
    fun isAvailable(): Boolean
    suspend fun identify(pcmBytes: ByteArray): LanguageIdentificationResult
}

/**
 * Offline Language Identification implementation.
 * Exposes offline availability status honestly without fake guessing.
 */
class OfflineLanguageIdentifier : LanguageIdentifier {
    override fun isAvailable(): Boolean = false

    override suspend fun identify(pcmBytes: ByteArray): LanguageIdentificationResult {
        return LanguageIdentificationResult.Unavailable
    }
}
