package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage

data class LanguageModelStatus(
    val language: IndicLanguage,
    val sttEngine: String,
    val sttStatus: String,
    val ttsEngine: String,
    val ttsStatus: String,
    val isOfflineReady: Boolean,
    val footprintMb: Float
)

object LanguageModelRegistry {

    fun getCapabilities(): List<LanguageModelStatus> {
        return IndicLanguage.entries.map { lang ->
            LanguageModelStatus(
                language = lang,
                sttEngine = "IndicConformer-INT8 / Offline ASR",
                sttStatus = "READY",
                ttsEngine = if (lang == IndicLanguage.HINDI) "MMS-TTS Hindi / Offline TTS" else "VITS-Indic / Offline TTS",
                ttsStatus = "READY",
                isOfflineReady = true,
                footprintMb = when (lang) {
                    IndicLanguage.HINDI -> 18.5f
                    IndicLanguage.ENGLISH -> 14.2f
                    else -> 16.8f
                }
            )
        }
    }
}
