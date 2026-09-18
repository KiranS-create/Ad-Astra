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
        var trimmed = rawText.trim().replace("\\s+".toRegex(), " ")
        if (trimmed.isEmpty()) return ""

        // Suppress repetitive loop artifacts common in CTC decoding under noisy conditions
        trimmed = cleanRepetitiveLoops(trimmed)

        // Feature 30: Tactical domain vocabulary biasing and reranking
        trimmed = TacticalDomainReranker.rerank(trimmed, language)

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

    private fun cleanRepetitiveLoops(text: String): String {
        val words = text.split(" ")
        if (words.size < 6) return text
        val half = words.size / 2
        if (words.subList(0, half) == words.subList(half, 2 * half)) {
            return words.subList(0, half).joinToString(" ")
        }
        return text
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
