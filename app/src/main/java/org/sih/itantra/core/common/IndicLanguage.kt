package org.sih.itantra.core.common

/**
 * The 10 official languages supported by iTantra for SIH26173 / ISRO.
 * Each language entry includes ISO codes, native script representation, and sample emergency utterance.
 */
enum class IndicLanguage(
    val id: Byte,
    val isoCode: String,
    val displayName: String,
    val nativeName: String,
    val scriptSample: String
) {
    ENGLISH(9, "en", "English", "English", "We are safe."),
    HINDI(0, "hi", "Hindi", "हिंदी", "हम सुरक्षित हैं।"),
    GUJARATI(1, "gu", "Gujarati", "ગુજરાતી", "અમે સુરક્ષિત છીએ."),
    MARATHI(2, "mr", "Marathi", "मराठी", "आम्ही सुरक्षित आहोत."),
    KANNADA(3, "kn", "Kannada", "ಕನ್ನಡ", "ನಾವು ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ."),
    MALAYALAM(4, "ml", "Malayalam", "മലയാളം", "ഞങ്ങൾ സുരക്ഷിതരാണ്."),
    TAMIL(5, "ta", "Tamil", "தமிழ்", "நாங்கள் பாதுகாப்பாக உள்ளோம்."),
    TELUGU(6, "te", "Telugu", "తెలుగు", "మేము సురక్షితంగా ఉన్నాము."),
    ODIA(7, "or", "Odia", "ଓଡ଼ିଆ", "ଆମେ ସୁରକ୍ଷିତ ଅଛୁ।"),
    BENGALI(8, "bn", "Bengali", "বাংলা", "আমরা নিরাপদ আছি।");

    companion object {
        fun fromId(id: Byte): IndicLanguage {
            return entries.firstOrNull { it.id == id } ?: ENGLISH
        }

        fun fromIsoCode(code: String): IndicLanguage {
            return entries.firstOrNull { it.isoCode.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}
