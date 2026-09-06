package org.sih.itantra.core.language

import org.sih.itantra.core.common.IndicLanguage

/**
 * User selection intent: explicit manual choice or automatic detection request.
 */
sealed class LanguageSelectionMode {
    data class Manual(val language: IndicLanguage) : LanguageSelectionMode()
    data object Auto : LanguageSelectionMode()
}

/**
 * Explicit operational state of language selection.
 * Honest: never silently routes AUTO to Hindi when offline LID is unavailable.
 */
sealed class LanguageSelectionState {
    data class Manual(val language: IndicLanguage) : LanguageSelectionState()
    data object Auto : LanguageSelectionState()
    data object Detecting : LanguageSelectionState()
    data class Detected(val language: IndicLanguage, val confidence: Float) : LanguageSelectionState()
    data class AutoUnavailable(val previousLanguage: IndicLanguage? = null) : LanguageSelectionState()
    data object Undetermined : LanguageSelectionState()

    val displayTitle: String
        get() = when (this) {
            is Manual -> "${language.displayName} (${language.nativeName})"
            is Auto -> "AUTO"
            is Detecting -> "DETECTING..."
            is Detected -> "${language.displayName} (${(confidence * 100).toInt()}%)"
            is AutoUnavailable -> "AUTO UNAVAILABLE"
            is Undetermined -> "UNDETERMINED"
        }

    val operatingLanguageOrNull: IndicLanguage?
        get() = when (this) {
            is Manual -> language
            is Detected -> language
            is AutoUnavailable -> previousLanguage
            is Auto, is Detecting, is Undetermined -> null
        }
}
