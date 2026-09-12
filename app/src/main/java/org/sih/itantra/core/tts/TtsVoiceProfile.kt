package org.sih.itantra.core.tts

/**
 * Supported underlying TTS engine types in iTantra offline architecture.
 */
enum class TtsEngineType {
    PIPER,
    MMS,
    MIMIC3,
    SYSTEM_FALLBACK,
    NONE
}

/**
 * Immutable profile describing the offline voice model associated with a language.
 */
data class TtsVoiceProfile(
    val language: TtsLanguage,
    val voiceId: String,
    val engineType: TtsEngineType,
    val modelName: String,
    val sampleRate: Int = 22050,
    val isAvailable: Boolean = true,
    val statusLabel: String = "TTS READY"
)
