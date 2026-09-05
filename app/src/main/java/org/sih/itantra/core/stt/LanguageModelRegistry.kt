package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage

data class LanguageModelStatus(
    val language: IndicLanguage,
    val sttEngine: String,
    val sttStatus: String,
    val ttsEngine: String,
    val ttsStatus: String,
    val isOfflineReady: Boolean,
    val footprintMb: Float,
    val verificationNotes: String
)

object LanguageModelRegistry {

    fun getCapabilities(): List<LanguageModelStatus> {
        return IndicLanguage.entries.map { lang ->
            val isCommonOsVoice = lang == IndicLanguage.ENGLISH || lang == IndicLanguage.HINDI
            LanguageModelStatus(
                language = lang,
                sttEngine = "Android System ASR (EXTRA_PREFER_OFFLINE)",
                sttStatus = if (isCommonOsVoice) "SYSTEM READY" else "REQUIRES OS VOICE PACK",
                ttsEngine = "Android TextToSpeech (${lang.isoCode}_IN)",
                ttsStatus = if (isCommonOsVoice) "SYSTEM READY" else "REQUIRES OS VOICE PACK",
                isOfflineReady = isCommonOsVoice,
                footprintMb = 0.0f, // No local ONNX models currently bundled in APK assets
                verificationNotes = if (isCommonOsVoice) {
                    "Supported offline if host OS has pre-downloaded offline speech voice."
                } else {
                    "FALLBACK-ONLY: Unbundled. Requires external ONNX model or OS language pack download."
                }
            )
        }
    }
}
