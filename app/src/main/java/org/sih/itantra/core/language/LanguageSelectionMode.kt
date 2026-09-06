package org.sih.itantra.core.language

import org.sih.itantra.core.common.IndicLanguage

/**
 * Represents the active language selection mode for the transceiver.
 */
sealed class LanguageSelectionMode {
    data class Manual(val language: IndicLanguage) : LanguageSelectionMode()
    data object Auto : LanguageSelectionMode()

    val displayTitle: String
        get() = when (this) {
            is Manual -> language.displayName
            is Auto -> "AUTO"
        }

    val activeLanguage: IndicLanguage
        get() = when (this) {
            is Manual -> language
            is Auto -> IndicLanguage.HINDI // Default baseline for offline mode
        }
}
