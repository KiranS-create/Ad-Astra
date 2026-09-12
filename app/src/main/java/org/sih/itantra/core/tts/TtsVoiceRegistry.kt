package org.sih.itantra.core.tts

import org.sih.itantra.ml.model.ModelAssetManager

/**
 * Registry mapping supported languages to their exact offline TTS voice models.
 * Grounded strictly in repository model assets.
 */
class TtsVoiceRegistry(
    private val availabilityChecker: ((TtsLanguage) -> Boolean)? = null
) {
    private val staticProfiles: Map<TtsLanguage, TtsVoiceProfile> = mapOf(
        TtsLanguage.HINDI to TtsVoiceProfile(
            language = TtsLanguage.HINDI,
            voiceId = "hi_rohan",
            engineType = TtsEngineType.PIPER,
            modelName = "hi_IN-rohan-medium.onnx",
            sampleRate = 22050,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.GUJARATI to TtsVoiceProfile(
            language = TtsLanguage.GUJARATI,
            voiceId = "gu_mimic3",
            engineType = TtsEngineType.MIMIC3,
            modelName = "gu_IN-cmu-indic_low.onnx",
            sampleRate = 22050,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.MARATHI to TtsVoiceProfile(
            language = TtsLanguage.MARATHI,
            voiceId = "mr_google",
            engineType = TtsEngineType.PIPER,
            modelName = "mr_IN-google-medium.onnx",
            sampleRate = 22050,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.KANNADA to TtsVoiceProfile(
            language = TtsLanguage.KANNADA,
            voiceId = "kn_mms",
            engineType = TtsEngineType.MMS,
            modelName = "model.onnx",
            sampleRate = 16000,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.MALAYALAM to TtsVoiceProfile(
            language = TtsLanguage.MALAYALAM,
            voiceId = "ml_arjun",
            engineType = TtsEngineType.PIPER,
            modelName = "ml_IN-arjun-medium.onnx",
            sampleRate = 22050,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.TAMIL to TtsVoiceProfile(
            language = TtsLanguage.TAMIL,
            voiceId = "ta_mms",
            engineType = TtsEngineType.MMS,
            modelName = "model.onnx",
            sampleRate = 16000,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.TELUGU to TtsVoiceProfile(
            language = TtsLanguage.TELUGU,
            voiceId = "te_maya",
            engineType = TtsEngineType.PIPER,
            modelName = "te_IN-maya-medium.onnx",
            sampleRate = 22050,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.ODIA to TtsVoiceProfile(
            language = TtsLanguage.ODIA,
            voiceId = "or_mms",
            engineType = TtsEngineType.MMS,
            modelName = "model.onnx",
            sampleRate = 16000,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.BENGALI to TtsVoiceProfile(
            language = TtsLanguage.BENGALI,
            voiceId = "bn_google",
            engineType = TtsEngineType.PIPER,
            modelName = "bn_BD-google-medium.onnx",
            sampleRate = 22050,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.ENGLISH to TtsVoiceProfile(
            language = TtsLanguage.ENGLISH,
            voiceId = "en_lessac",
            engineType = TtsEngineType.PIPER,
            modelName = "en_US-lessac-medium.onnx",
            sampleRate = 22050,
            isAvailable = true,
            statusLabel = "TTS READY"
        ),
        TtsLanguage.UNKNOWN to TtsVoiceProfile(
            language = TtsLanguage.UNKNOWN,
            voiceId = "unknown",
            engineType = TtsEngineType.NONE,
            modelName = "none",
            sampleRate = 16000,
            isAvailable = false,
            statusLabel = "LANGUAGE UNKNOWN"
        )
    )

    fun isVoiceAvailable(language: TtsLanguage): Boolean {
        if (language == TtsLanguage.UNKNOWN) return false
        return availabilityChecker?.invoke(language) ?: true
    }

    fun getProfile(language: TtsLanguage): TtsVoiceProfile {
        val base = staticProfiles[language] ?: staticProfiles.getValue(TtsLanguage.UNKNOWN)
        if (language == TtsLanguage.UNKNOWN) {
            return base.copy(isAvailable = false, statusLabel = "LANGUAGE UNKNOWN")
        }
        val available = isVoiceAvailable(language)
        return if (available) {
            base.copy(isAvailable = true, statusLabel = "TTS READY")
        } else {
            base.copy(isAvailable = false, statusLabel = "TTS UNAVAILABLE")
        }
    }

    fun getAllProfiles(): List<TtsVoiceProfile> {
        return TtsLanguage.entries
            .filter { it != TtsLanguage.UNKNOWN }
            .map { getProfile(it) }
    }

    fun getSupportedLanguages(): List<TtsLanguage> {
        return TtsLanguage.entries.filter { it != TtsLanguage.UNKNOWN }
    }

    companion object {
        val DEFAULT = TtsVoiceRegistry()

        fun fromModelAssetManager(manager: ModelAssetManager): TtsVoiceRegistry {
            return TtsVoiceRegistry { lang ->
                when (lang) {
                    TtsLanguage.HINDI -> manager.isHindiTtsReady()
                    TtsLanguage.GUJARATI -> manager.isGujaratiTtsReady()
                    TtsLanguage.MARATHI -> manager.isMarathiTtsReady()
                    TtsLanguage.KANNADA -> manager.isKannadaTtsReady()
                    TtsLanguage.MALAYALAM -> manager.isMalayalamTtsReady()
                    TtsLanguage.TAMIL -> manager.isTamilTtsReady()
                    TtsLanguage.TELUGU -> manager.isTeluguTtsReady()
                    TtsLanguage.ODIA -> manager.isOdiaTtsReady()
                    TtsLanguage.BENGALI -> manager.isBengaliTtsReady()
                    TtsLanguage.ENGLISH -> manager.isEnglishTtsReady()
                    TtsLanguage.UNKNOWN -> false
                }
            }
        }
    }
}
