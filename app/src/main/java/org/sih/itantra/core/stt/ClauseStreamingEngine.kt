package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.diagnostics.BenchmarkClock

/**
 * Data structure representing an individual speech clause in a streaming transmission pipeline.
 */
data class StreamClause(
    val clauseIndex: Int,
    val text: String,
    val language: IndicLanguage,
    val isFinal: Boolean,
    val isDistressOrCommand: Boolean = false,
    val timestampNanos: Long = BenchmarkClock.nowNanos()
)

/**
 * Tactical Sub-500ms Phrase-Streaming Speech Pipeline Engine.
 *
 * Designed to eliminate the dead-air latency of multi-second push-to-talk utterances.
 * Instead of waiting for an entire compound sentence to finish speaking and decoding,
 * the engine segments natural speech into sequential syntactic clauses at:
 * 1. Indic dandas ('।', '॥') and standard sentence terminators ('.', '?', '!')
 * 2. Intermediate breath punctuation (',', ';', ':', '—')
 * 3. Indic and English tactical coordinating conjunctions ('और', 'மற்றும்', 'మరియు', 'and', etc.)
 *
 * This allows the receiving radio node to synthesize and vocalize Clause 1 over the speaker
 * while Clause 2 is still being articulated over the air, cutting perceived end-to-end
 * acoustic delivery latency to under 500ms.
 */
object ClauseStreamingEngine {

    private val TERMINATORS = setOf('.', '?', '!', '।', '॥', '\n')
    private val CLAUSE_PUNCTUATION = setOf(',', ';', ':', '—', '-')

    // Tactical coordinating conjunctions mapped per language
    private val CONJUNCTIONS: Map<IndicLanguage, List<String>> = mapOf(
        IndicLanguage.HINDI to listOf(" और ", " तथा ", " लेकिन ", " परन्तु ", " इसलिए ", " ताकि "),
        IndicLanguage.TAMIL to listOf(" மற்றும் ", " ஆனால் ", " அதனால் ", " எனவே "),
        IndicLanguage.TELUGU to listOf(" మరియు ", " కానీ ", " కాబట్టి ", " అందువలన "),
        IndicLanguage.BENGALI to listOf(" এবং ", " কিন্তু ", " সুতরাং "),
        IndicLanguage.MARATHI to listOf(" आणि ", " पण ", " परंतु ", " म्हणून "),
        IndicLanguage.GUJARATI to listOf(" અને ", " પરંતુ ", " તેથી "),
        IndicLanguage.KANNADA to listOf(" ಮತ್ತು ", " ಆದರೆ ", " ಆದ್ದರಿಂದ "),
        IndicLanguage.MALAYALAM to listOf(" ഒപ്പം ", " എന്നാൽ ", " അതിനാൽ "),
        IndicLanguage.ODIA to listOf(" ଏବଂ ", " କିନ୍ତୁ ", " ତେଣୁ "),
        IndicLanguage.ENGLISH to listOf(" and ", " but ", " however ", " therefore ", " then ")
    )

    private const val MIN_CLAUSE_CHARS = 10
    private const val MAX_CLAUSE_CHARS = 100

    /**
     * Segments complete or multi-clause text into an ordered list of [StreamClause].
     */
    fun segment(fullText: String, language: IndicLanguage): List<StreamClause> {
        val trimmed = fullText.trim().replace("\\s+".toRegex(), " ")
        if (trimmed.isEmpty()) return emptyList()

        val rawClauses = mutableListOf<String>()
        var currentBuffer = StringBuilder()

        val words = trimmed.split(" ")
        val langConjunctions = CONJUNCTIONS[language] ?: emptyList()

        for (word in words) {
            if (currentBuffer.isNotEmpty()) {
                currentBuffer.append(" ")
            }
            currentBuffer.append(word)

            val currentStr = currentBuffer.toString()
            val hasTerminator = word.any { it in TERMINATORS }
            val hasClausePunct = word.any { it in CLAUSE_PUNCTUATION }

            val endsWithConjunction = langConjunctions.any { conj ->
                currentStr.endsWith(conj.trimEnd())
            }

            val isLongEnough = currentStr.length >= MIN_CLAUSE_CHARS
            val isTooLong = currentStr.length >= MAX_CLAUSE_CHARS

            if ((hasTerminator || hasClausePunct || isTooLong || (endsWithConjunction && isLongEnough)) && isLongEnough) {
                rawClauses.add(currentStr.trim())
                currentBuffer = StringBuilder()
            }
        }

        if (currentBuffer.isNotBlank()) {
            val remaining = currentBuffer.toString().trim()
            if (rawClauses.isNotEmpty() && remaining.length < MIN_CLAUSE_CHARS) {
                // Merge short dangling trailing fragment into previous clause
                val lastIdx = rawClauses.size - 1
                rawClauses[lastIdx] = "${rawClauses[lastIdx]} $remaining"
            } else {
                rawClauses.add(remaining)
            }
        }

        if (rawClauses.isEmpty()) {
            rawClauses.add(trimmed)
        }

        return rawClauses.mapIndexed { index, clauseText ->
            val isFinal = index == rawClauses.size - 1
            StreamClause(
                clauseIndex = index,
                text = clauseText,
                language = language,
                isFinal = isFinal,
                isDistressOrCommand = containsUrgentKeywords(clauseText)
            )
        }
    }

    private fun containsUrgentKeywords(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("help") || lower.contains("sos") ||
                lower.contains("बचाओ") || lower.contains("सहायता") ||
                lower.contains("காப்பாற்று") || lower.contains("సహాయం") ||
                lower.contains("साहाय्य") || lower.contains("সাহায্য") ||
                lower.contains("મદદ") || lower.contains("സഹായം") ||
                lower.contains("ସାହାଯ୍ୟ") || lower.contains("ಸಹಾಯ")
    }

    /**
     * Stateful streaming clause accumulator for incremental STT decoders.
     */
    class StreamingAccumulator(
        val language: IndicLanguage
    ) {
        private val buffer = StringBuilder()
        private var emittedCount = 0

        @Synchronized
        fun feed(textChunk: String): List<StreamClause> {
            buffer.append(textChunk)
            val fullCurrent = buffer.toString()
            val candidateClauses = segment(fullCurrent, language)

            if (candidateClauses.size <= 1) {
                // Not enough segmented boundaries accumulated yet
                return emptyList()
            }

            // Emit all except the last unresolved clause
            val toEmit = candidateClauses.dropLast(1)
            val emittedClauses = mutableListOf<StreamClause>()

            for (c in toEmit) {
                emittedClauses.add(
                    c.copy(
                        clauseIndex = emittedCount++,
                        isFinal = false
                    )
                )
            }

            // Keep the unresolved tail in buffer
            val lastCandidate = candidateClauses.last().text
            buffer.clear()
            buffer.append(lastCandidate)

            return emittedClauses
        }

        @Synchronized
        fun finalizeUtterance(): List<StreamClause> {
            val remaining = buffer.toString().trim()
            buffer.clear()
            if (remaining.isEmpty()) return emptyList()

            val finalClause = StreamClause(
                clauseIndex = emittedCount++,
                text = remaining,
                language = language,
                isFinal = true,
                isDistressOrCommand = containsUrgentKeywords(remaining)
            )
            return listOf(finalClause)
        }

        @Synchronized
        fun reset() {
            buffer.clear()
            emittedCount = 0
        }
    }
}
