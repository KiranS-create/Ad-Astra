package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage

/**
 * Sentence segmentation and utterance boundary detector.
 * Handles Indian scripts (danda '।', double danda '॥'), English punctuation, and pause rules.
 */
object SentenceFinalizer {

    private val TERMINATORS = setOf('.', '?', '!', '।', '॥', '\n')

    /**
     * Cleans and finalizes recognized speech into a clean, radio-transmittable sentence.
     */
    fun finalizeSentence(rawText: String, language: IndicLanguage): String {
        val trimmed = rawText.trim().replace("\\s+".toRegex(), " ")
        if (trimmed.isEmpty()) return ""

        val lastChar = trimmed.last()
        val hasTerminator = lastChar in TERMINATORS

        val defaultTerminator = when (language) {
            IndicLanguage.HINDI, IndicLanguage.ODIA, IndicLanguage.BENGALI -> "।"
            else -> "."
        }

        return if (hasTerminator) {
            trimmed
        } else {
            "$trimmed$defaultTerminator"
        }
    }

    /**
     * Splits multi-sentence transcription buffers into individual transmission packets.
     */
    fun segmentUtterance(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        // Split on danda, full stop, question mark, or exclamation mark followed by whitespace
        val regex = Regex("(?<=[.?!।॥])\\s+")
        return text.split(regex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
