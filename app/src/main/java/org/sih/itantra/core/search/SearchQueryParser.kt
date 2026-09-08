package org.sih.itantra.core.search

import java.util.Locale

/**
 * Parsed representation of an offline user search query.
 */
data class ParsedQuery(
    val raw: String,
    val normalized: String,
    val tokens: List<String>,
    val candidateNodeId: Int? = null,
    val typeFilterHint: SearchFilterType? = null
) {
    val isBlank: Boolean
        get() = raw.isBlank()
}

/**
 * Offline deterministic search query normalizer and tokenizer.
 *
 * Rules:
 * - Case-insensitive matching across Latin text.
 * - Respects Indic Unicode scripts (Hindi, Tamil, Marathi, Bengali, Telugu, Gujarati, Kannada, Odia, Malayalam).
 * - Normalizes node ID formats (e.g. "Node #477124", "477124", "#477124", "node 477124").
 * - Extracts tokens for prefix and phrase matching.
 */
object SearchQueryParser {

    private val NODE_ID_STRICT_REGEX = Regex("""^(?:node\s*#?|#)?(\d+)$""", RegexOption.IGNORE_CASE)
    private val NODE_ID_EMBEDDED_REGEX = Regex("""(?:node\s*#?|#)(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedQuery {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ParsedQuery(
                raw = raw,
                normalized = "",
                tokens = emptyList(),
                candidateNodeId = null,
                typeFilterHint = null
            )
        }

        // Check for quick inline filter prefix (e.g., "type:msg", "type:node")
        var working = trimmed
        var filterHint: SearchFilterType? = null

        when {
            working.startsWith("msg:", ignoreCase = true) -> {
                filterHint = SearchFilterType.MESSAGES
                working = working.substring(4).trim()
            }
            working.startsWith("node:", ignoreCase = true) -> {
                filterHint = SearchFilterType.NODES
                working = working.substring(5).trim()
            }
            working.startsWith("contact:", ignoreCase = true) -> {
                filterHint = SearchFilterType.CONTACTS
                working = working.substring(8).trim()
            }
            working.startsWith("chat:", ignoreCase = true) -> {
                filterHint = SearchFilterType.CONVERSATIONS
                working = working.substring(5).trim()
            }
        }

        val normalized = normalizeText(working)

        // Extract potential Node ID
        val candidateNodeId = extractNodeId(working)

        // Tokenize by whitespace and standard ASCII punctuation, preserving Indic characters
        val tokens = tokenize(normalized)

        return ParsedQuery(
            raw = raw,
            normalized = normalized,
            tokens = tokens,
            candidateNodeId = candidateNodeId,
            typeFilterHint = filterHint
        )
    }

    /**
     * Normalizes text for search matching:
     * - Trims excess whitespace
     * - Replaces multiple spaces with single space
     * - Converts Latin characters to lowercase (Locale.ROOT)
     * - Preserves Indic script characters, matras, and conjuncts as-is
     */
    fun normalizeText(input: String): String {
        return input.trim()
            .replace(Regex("""\s+"""), " ")
            .lowercase(Locale.ROOT)
    }

    /**
     * Extracts an integer node ID from various formats like:
     * "477124", "Node 477124", "Node #477124", "#477124", "node:477124"
     */
    fun extractNodeId(input: String): Int? {
        val clean = input.trim()
        val strictMatch = NODE_ID_STRICT_REGEX.matchEntire(clean)
        if (strictMatch != null) {
            return strictMatch.groupValues[1].toIntOrNull()
        }

        val embeddedMatch = NODE_ID_EMBEDDED_REGEX.find(clean)
        if (embeddedMatch != null) {
            return embeddedMatch.groupValues[1].toIntOrNull()
        }

        // Check if the query itself is purely numeric
        if (clean.all { it.isDigit() }) {
            return clean.toIntOrNull()
        }

        return null
    }

    /**
     * Splits normalized query into distinct search tokens.
     */
    fun tokenize(normalized: String): List<String> {
        if (normalized.isBlank()) return emptyList()
        return normalized.split(Regex("""[\s\p{Punct}]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
