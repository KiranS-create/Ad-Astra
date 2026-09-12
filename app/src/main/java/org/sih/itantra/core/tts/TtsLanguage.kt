package org.sih.itantra.core.tts

import org.sih.itantra.core.common.IndicLanguage

/**
 * Supported TTS languages for iTantra SIH26173 language-aware playback.
 * Extends the 10 Indic languages with an UNKNOWN representation for unparseable,
 * missing, or corrupted language metadata.
 */
enum class TtsLanguage(
    val id: Byte?,
    val isoCode: String,
    val badgeCode: String,
    val displayName: String,
    val nativeName: String,
    val scriptSample: String
) {
    ENGLISH(9, "en", "EN", "English", "English", "We are safe."),
    HINDI(0, "hi", "HI", "Hindi", "हिंदी", "हम सुरक्षित हैं।"),
    GUJARATI(1, "gu", "GU", "Gujarati", "ગુજરાતી", "અમે સુરક્ષિત છીએ."),
    MARATHI(2, "mr", "MR", "Marathi", "मराठी", "आम्ही सुरक्षित आहोत."),
    KANNADA(3, "kn", "KN", "Kannada", "ಕನ್ನಡ", "ನಾವು ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ."),
    MALAYALAM(4, "ml", "ML", "Malayalam", "മലയാളം", "ഞങ്ങൾ സുரಕ್ಷితരാണ്."),
    TAMIL(5, "ta", "TA", "Tamil", "தமிழ்", "நாங்கள் பாதுகாப்பாக உள்ளோம்."),
    TELUGU(6, "te", "TE", "Telugu", "తెలుగు", "మేము సురక్షితంగా ఉన్నాము."),
    ODIA(7, "or", "OR", "Odia", "ଓଡ଼ିଆ", "ଆମେ ସୁରକ୍ଷିତ ଅଛୁ।"),
    BENGALI(8, "bn", "BN", "Bengali", "বাংলা", "আমরা নিরাপদ আছি।"),
    UNKNOWN(null, "??", "??", "Unknown", "Unknown", "");

    fun toIndicLanguageOrNull(): IndicLanguage? {
        return when (this) {
            ENGLISH -> IndicLanguage.ENGLISH
            HINDI -> IndicLanguage.HINDI
            GUJARATI -> IndicLanguage.GUJARATI
            MARATHI -> IndicLanguage.MARATHI
            KANNADA -> IndicLanguage.KANNADA
            MALAYALAM -> IndicLanguage.MALAYALAM
            TAMIL -> IndicLanguage.TAMIL
            TELUGU -> IndicLanguage.TELUGU
            ODIA -> IndicLanguage.ODIA
            BENGALI -> IndicLanguage.BENGALI
            UNKNOWN -> null
        }
    }

    companion object {
        fun fromIndicLanguage(lang: IndicLanguage?): TtsLanguage {
            if (lang == null) return UNKNOWN
            return when (lang) {
                IndicLanguage.ENGLISH -> ENGLISH
                IndicLanguage.HINDI -> HINDI
                IndicLanguage.GUJARATI -> GUJARATI
                IndicLanguage.MARATHI -> MARATHI
                IndicLanguage.KANNADA -> KANNADA
                IndicLanguage.MALAYALAM -> MALAYALAM
                IndicLanguage.TAMIL -> TAMIL
                IndicLanguage.TELUGU -> TELUGU
                IndicLanguage.ODIA -> ODIA
                IndicLanguage.BENGALI -> BENGALI
            }
        }

        fun fromIsoCode(code: String?): TtsLanguage {
            if (code.isNullOrBlank()) return UNKNOWN
            val trimmed = code.trim().lowercase()
            return entries.firstOrNull { it != UNKNOWN && it.isoCode.equals(trimmed, ignoreCase = true) }
                ?: UNKNOWN
        }

        fun fromId(id: Byte?): TtsLanguage {
            if (id == null) return UNKNOWN
            return entries.firstOrNull { it.id == id } ?: UNKNOWN
        }
    }
}
