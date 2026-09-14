package org.sih.itantra.core.vbr

import org.sih.itantra.core.common.IndicLanguage
import java.util.Locale

/**
 * Feature 16B: Pure deterministic tactical shorthand generator.
 *
 * Transforms natural language utterances into terse, radio-operator shorthand.
 *
 * Rules:
 * 1. Preserves:
 *    - All digits and numeric words ("1", "2", "two", "three", "10", etc.)
 *    - Action imperatives ("EVAC", "HOLD", "ADVANCE", "SEND", "DEPLOY", "REPORT", "CONFIRM", "MOVE", "SECURE", "DISPATCH")
 *    - Emergency/tactical tokens ("AMBULANCE", "INJURED", "CASUALTY", "FIRE", "TRAPPED", "WATER", "FOOD", "MEDIC", "COLLAPSE", "CRITICAL")
 *    - Callsigns and grid coordinates ("ALPHA", "BRAVO", "HQ", "BASE", "SECTOR", "GRID", "NODE", "FOB", "COP")
 *    - Proper nouns and key tactical entities
 * 2. Strips:
 *    - Conversational fillers ("please", "could you", "would you mind", "basically", "actually", "kindly", "just", "hey", "hello", "hi", "um", "uh")
 *    - Auxiliary fluff ("we have a", "we have an", "there is a", "there are", "is currently", "are currently", "need to", "have to", "at this time", "right now")
 *    - Loose articles ("a", "an", "the", unless attached to a token)
 * 3. Formats English/Latin into uppercase tactical teleprinter style.
 * 4. Preserves Indic Unicode text intact, stripping language-specific pleasantries.
 * 5. Deterministic fallback: if compaction produces an empty string, falls back to original trimmed text.
 */
object CompactTextGenerator {

    private val ENGLISH_MULTI_WORD_FILLERS = listOf(
        Regex("(?i)\\bwould\\s+you\\s+mind\\b"),
        Regex("(?i)\\bcould\\s+you\\s+please\\b"),
        Regex("(?i)\\bcould\\s+you\\b"),
        Regex("(?i)\\bplease\\b"),
        Regex("(?i)\\bkindly\\b"),
        Regex("(?i)\\bbasically\\b"),
        Regex("(?i)\\bactually\\b"),
        Regex("(?i)\\bat\\s+this\\s+time\\b"),
        Regex("(?i)\\bright\\s+now\\b"),
        Regex("(?i)\\bat\\s+the\\s+moment\\b"),
        Regex("(?i)\\bwe\\s+have\\s+a\\b"),
        Regex("(?i)\\bwe\\s+have\\s+an\\b"),
        Regex("(?i)\\bwe\\s+have\\b"),
        Regex("(?i)\\bthere\\s+is\\s+a\\b"),
        Regex("(?i)\\bthere\\s+is\\s+an\\b"),
        Regex("(?i)\\bthere\\s+is\\b"),
        Regex("(?i)\\bthere\\s+are\\b"),
        Regex("(?i)\\bis\\s+currently\\b"),
        Regex("(?i)\\bare\\s+currently\\b"),
        Regex("(?i)\\bis\\s+reporting\\b"),
        Regex("(?i)\\bare\\s+reporting\\b"),
        Regex("(?i)\\bwe\\s+need\\s+to\\b"),
        Regex("(?i)\\bneed\\s+to\\b"),
        Regex("(?i)\\bhave\\s+to\\b"),
        Regex("(?i)\\bi\\s+think\\b"),
        Regex("(?i)\\bas\\s+soon\\s+as\\s+possible\\b")
    )

    private val ENGLISH_SINGLE_WORD_FILLERS = setOf(
        "just", "um", "uh", "hey", "hello", "hi", "well", "like", "okay", "ok"
    )

    private val ENGLISH_ARTICLES = setOf("a", "an", "the")

    private val TAMIL_FILLERS = listOf(
        "தயவுசெய்து", "வணக்கம்", "இருக்கிறது", "உள்ளது", "இங்கே", "அங்கே"
    )

    private val HINDI_FILLERS = listOf(
        "कृपया", "नमस्ते", "यहाँ पर", "यहाँ", "वहाँ", "हैं", "है", "था", "थे"
    )

    /**
     * Compacts [rawText] into concise tactical shorthand.
     *
     * @param rawText Input natural language transcript.
     * @param language Source language.
     * @return Deterministic tactical shorthand string.
     */
    fun compact(rawText: String, language: IndicLanguage = IndicLanguage.HINDI): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return trimmed

        if (hasTamilCharacters(trimmed)) {
            return compactIndic(trimmed, TAMIL_FILLERS)
        }
        if (hasDevanagariCharacters(trimmed)) {
            return compactIndic(trimmed, HINDI_FILLERS)
        }
        if (isMostlyAscii(trimmed)) {
            return compactEnglish(trimmed)
        }

        return when (language) {
            IndicLanguage.TAMIL -> compactIndic(trimmed, TAMIL_FILLERS)
            IndicLanguage.HINDI -> compactIndic(trimmed, HINDI_FILLERS)
            else -> compactEnglish(trimmed)
        }
    }

    private fun hasTamilCharacters(str: String): Boolean =
        str.any { it.code in 0x0B80..0x0BFF }

    private fun hasDevanagariCharacters(str: String): Boolean =
        str.any { it.code in 0x0900..0x097F }

    private fun compactEnglish(text: String): String {
        var processed = text

        // 1. Remove multi-word phrases
        for (pattern in ENGLISH_MULTI_WORD_FILLERS) {
            processed = pattern.replace(processed, " ")
        }

        // 2. Tokenize and filter single-word fillers and articles
        val tokens = processed.split(Regex("[\\s,;.]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val retained = mutableListOf<String>()
        for (token in tokens) {
            val lower = token.lowercase(Locale.ROOT)
            if (lower in ENGLISH_SINGLE_WORD_FILLERS) continue
            if (lower in ENGLISH_ARTICLES) continue
            retained.add(token.uppercase(Locale.ROOT))
        }

        val result = retained.joinToString(" ").trim()
        return if (result.isNotBlank()) result else text.trim().uppercase(Locale.ROOT)
    }

    private fun compactIndic(text: String, fillers: List<String>): String {
        var processed = text
        for (filler in fillers) {
            processed = processed.replace(filler, " ")
        }

        val tokens = processed.split(Regex("[\\s,;.]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = tokens.joinToString(" ").trim()
        return if (result.isNotBlank()) result else text.trim()
    }

    private fun isMostlyAscii(str: String): Boolean {
        var asciiCount = 0
        for (c in str) {
            if (c.code < 128) asciiCount++
        }
        return (asciiCount.toDouble() / str.length.toDouble()) > 0.7
    }
}
