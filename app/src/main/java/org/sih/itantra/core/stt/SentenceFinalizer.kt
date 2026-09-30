package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage

/**
 * Sentence segmentation, multilingual script boundary normalizer, and utterance boundary detector.
 * Handles Indian scripts (danda '।', double danda '॥'), English punctuation, and pause rules.
 */
object SentenceFinalizer {

    private val TERMINATORS = setOf('.', '?', '!', '।', '॥', '\n')

    // Purge CJK (Chinese, Japanese, Korean) characters that never belong in Indic or English tactical speech
    private val CJK_REGEX = Regex("[\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff\\u3040-\\u309f\\u30a0-\\u30ff\\uac00-\\ud7af\\u2e80-\\u2eff\\u3000-\\u303f\\uff00-\\uffef]")

    // Phantom hallucination phrases commonly emitted by Whisper on silence or ambient background noise
    private val PHANTOM_HALLUCINATIONS = listOf(
        Regex("(?i)^\\s*(?:thank(?:s|\\s+you)(?:\\s+very\\s+much)?(?:\\s+for\\s+watching)?|please\\s+subscribe|subtitles\\s+by.*|amara\\.org|mbc|copyright.*|all\\s+rights\\s+reserved|bye(?:\\s+bye)?)\\s*[.?!।॥]*\\s*$"),
        Regex("(?i)^\\s*(?:you|i|the|a|it|so)\\s*[.?!।॥]*\\s*$")
    )

    /**
     * Cleans and finalizes recognized speech into a clean, radio-transmittable sentence in the active language.
     */
    fun finalizeSentence(rawText: String, language: IndicLanguage): String {
        var trimmed = rawText.trim().replace("\\s+".toRegex(), " ")
        if (trimmed.isEmpty()) return ""

        // 1. Purge Chinese/East Asian characters permanently
        if (trimmed.contains(CJK_REGEX)) {
            trimmed = trimmed.replace(CJK_REGEX, "").trim().replace("\\s+".toRegex(), " ")
            trimmed = trimmed.replace(Regex("^[,;:?\\s]+"), "").trim()
            if (trimmed.isEmpty()) return ""
        }

        // 2. Reject Whisper phantom silence/ambient noise hallucinations
        for (pattern in PHANTOM_HALLUCINATIONS) {
            if (pattern.matches(trimmed)) {
                return ""
            }
        }

        // 3. Suppress repetitive loop artifacts common in CTC/Whisper decoding under low confidence
        trimmed = cleanRepetitiveLoops(trimmed)
        if (trimmed.isEmpty()) return ""

        // 4. Feature 30: Tactical domain vocabulary biasing, acoustic homophone normalization, and script restoration
        trimmed = TacticalDomainReranker.rerank(trimmed, language)
        if (trimmed.isEmpty()) return ""

        // 5. Ultimate Indic script guarantee: no Latin alphabet leaks if an Indic language is selected
        if (language != IndicLanguage.ENGLISH && trimmed.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            trimmed = IndicPhoneticTransliterator.transliterate(trimmed, language)
        }
        if (trimmed.isEmpty()) return ""

        val lastChar = trimmed.last()
        val hasTerminator = lastChar in TERMINATORS

        val defaultTerminator = when (language) {
            IndicLanguage.HINDI, IndicLanguage.ODIA, IndicLanguage.BENGALI -> "।"
            else -> "."
        }

        return if (hasTerminator) {
            if ((language == IndicLanguage.HINDI || language == IndicLanguage.ODIA || language == IndicLanguage.BENGALI) && lastChar == '.') {
                trimmed.dropLast(1) + "।"
            } else {
                trimmed
            }
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
