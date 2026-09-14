package org.sih.itantra.core.speech.refinement

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.speech.pipeline.Pass1Hypothesis
import java.util.Locale

/**
 * Deterministic policy engine for identifying and prioritizing tokens that warrant
 * Pass 2 targeted refinement during opportunistic silence windows.
 *
 * Evaluation Rules:
 * 1. EMERGENCY_KEYWORD (Priority 1.0): Life-critical tactical/distress tokens.
 * 2. TACTICAL_COORDINATE_OR_NUMBER (Priority 0.85): Coordinate digits, grids, sector names.
 * 3. HYPOTHESIS_INSTABILITY (Priority 0.75): Tokens that mutated between consecutive Pass 1 updates.
 * 4. LOW_CONFIDENCE (Priority 0.60): Pass 1 confidence below confidenceThreshold.
 * 5. INDIC_SCRIPT_AMBIGUITY (Priority 0.50): Mixed scripts or phonetically ambiguous transliterations.
 */
class TargetedRefinementPolicy(
    private val confidenceThreshold: Float = 0.80f
) {

    companion object {
        // Emergency and distress keywords (English, Hindi transliterated/Devanagari, Tamil transliterated/Tamil script)
        private val EMERGENCY_TERMS = setOf(
            // English
            "sos", "mayday", "help", "emergency", "medical", "injured", "casualty",
            "fire", "ambush", "evac", "evacuation", "danger", "hostile", "attack",
            "critical", "urgent", "trapped", "sniper", "ied", "blast",
            // Hindi / Devanagari
            "मदद", "घायल", "आग", "हमला", "खतरा", "इलाज", "डॉक्टर", "बचाव",
            "आपातकालीन", "फंसे", "विस्फोट",
            // Hindi transliterated
            "madad", "ghayal", "aag", "hamla", "khatra", "ilaaj", "doctor", "bachav",
            "apatkalin", "phanse", "visphot",
            // Tamil script
            "உதவி", "காயம்", "தீ", "தாக்குதல்", "ஆபத்து", "மருத்துவம்",
            // Tamil transliterated
            "uthavi", "kaayam", "thee", "thaakkuthal", "aabathu", "maruthuvam"
        )

        // Tactical callsigns, units, and coordinate designators
        private val TACTICAL_PREFIXES = setOf(
            "sector", "alpha", "bravo", "charlie", "delta", "echo", "foxtrot",
            "grid", "coordinate", "checkpoint", "base", "platoon", "unit",
            "सेक्टर", "अल्फा", "ब्रावो", "ग्रिड", "செக்டர்", "பிரிவு"
        )

        // Regex for numbers, coordinates, or mixed tactical tokens
        private val NUMBER_REGEX = Regex("""\b(\d+(\.\d+)?)\b""")
        private val COORDINATE_REGEX = Regex("""\b\d{1,3}\.\d+\b""")
        private val MIXED_SCRIPT_REGEX = Regex("""([a-zA-Z]+[\u0900-\u097F\u0B80-\u0BFF]+|[\u0900-\u097F\u0B80-\u0BFF]+[a-zA-Z]+)""")
    }

    /**
     * Extracts and prioritizes candidates from the current Pass 1 hypothesis.
     *
     * @param current The latest incremental hypothesis from Pass 1.
     * @param previous The immediately preceding hypothesis from Pass 1 (used for instability detection).
     * @param language Active Indic language of the transmission.
     * @return Sorted list of candidates, descending by priority score.
     */
    fun evaluateCandidates(
        current: Pass1Hypothesis,
        previous: Pass1Hypothesis?,
        language: IndicLanguage
    ): List<RefinementCandidate> {
        val candidates = mutableListOf<RefinementCandidate>()
        val text = current.partialText
        if (text.isBlank()) return emptyList()

        // 1. Detect Hypothesis Instability across consecutive Pass 1 updates
        if (previous != null && previous.partialText.isNotBlank()) {
            val prevTokens = previous.partialText.trim().split(Regex("""\s+"""))
            val currTokens = text.trim().split(Regex("""\s+"""))

            val commonLength = minOf(prevTokens.size, currTokens.size)
            var charOffset = 0
            for (i in 0 until commonLength) {
                val prevTok = prevTokens[i]
                val currTok = currTokens[i]
                val startIdx = text.indexOf(currTok, charOffset).coerceAtLeast(charOffset)
                val endIdx = startIdx + currTok.length

                if (!prevTok.equals(currTok, ignoreCase = true)) {
                    candidates.add(
                        RefinementCandidate(
                            chunkIndex = current.chunkIndex,
                            tokenText = currTok,
                            priorityScore = RefinementReason.HYPOTHESIS_INSTABILITY.defaultPriority,
                            reason = RefinementReason.HYPOTHESIS_INSTABILITY,
                            startIndex = startIdx,
                            endIndex = endIdx,
                            contextBefore = prevTok,
                            contextAfter = if (i + 1 < currTokens.size) currTokens[i + 1] else ""
                        )
                    )
                }
                charOffset = endIdx
            }
        }

        // 2. Token-level scan for Emergency keywords, Tactical Coordinates, Numbers, and Ambiguities
        val words = text.split(Regex("""\s+"""))
        var searchIndex = 0

        for (i in words.indices) {
            val rawWord = words[i]
            val cleanedWord = rawWord.trim().lowercase(Locale.ROOT).replace(Regex("""[^\w\u0900-\u097F\u0B80-\u0BFF]"""), "")
            if (cleanedWord.isBlank()) continue

            val wordStart = text.indexOf(rawWord, searchIndex).coerceAtLeast(searchIndex)
            val wordEnd = wordStart + rawWord.length
            searchIndex = wordEnd

            val contextBefore = if (i > 0) words[i - 1] else ""
            val contextAfter = if (i + 1 < words.size) words[i + 1] else ""

            // Rule A: Emergency Keywords
            if (EMERGENCY_TERMS.contains(cleanedWord)) {
                candidates.add(
                    RefinementCandidate(
                        chunkIndex = current.chunkIndex,
                        tokenText = rawWord,
                        priorityScore = RefinementReason.EMERGENCY_KEYWORD.defaultPriority,
                        reason = RefinementReason.EMERGENCY_KEYWORD,
                        startIndex = wordStart,
                        endIndex = wordEnd,
                        contextBefore = contextBefore,
                        contextAfter = contextAfter
                    )
                )
                continue
            }

            // Rule B: Tactical coordinates & prefixes (e.g. "sector", "alpha")
            if (TACTICAL_PREFIXES.contains(cleanedWord)) {
                candidates.add(
                    RefinementCandidate(
                        chunkIndex = current.chunkIndex,
                        tokenText = rawWord,
                        priorityScore = RefinementReason.TACTICAL_COORDINATE_OR_NUMBER.defaultPriority,
                        reason = RefinementReason.TACTICAL_COORDINATE_OR_NUMBER,
                        startIndex = wordStart,
                        endIndex = wordEnd,
                        contextBefore = contextBefore,
                        contextAfter = contextAfter
                    )
                )
                continue
            }

            // Rule C: Numbers and Coordinates
            if (NUMBER_REGEX.containsMatchIn(rawWord) || COORDINATE_REGEX.containsMatchIn(rawWord)) {
                candidates.add(
                    RefinementCandidate(
                        chunkIndex = current.chunkIndex,
                        tokenText = rawWord,
                        priorityScore = RefinementReason.TACTICAL_COORDINATE_OR_NUMBER.defaultPriority,
                        reason = RefinementReason.TACTICAL_COORDINATE_OR_NUMBER,
                        startIndex = wordStart,
                        endIndex = wordEnd,
                        contextBefore = contextBefore,
                        contextAfter = contextAfter
                    )
                )
                continue
            }

            // Rule D: Indic Script Ambiguity (mixed ASCII + Indic script in same token)
            if (MIXED_SCRIPT_REGEX.containsMatchIn(rawWord)) {
                candidates.add(
                    RefinementCandidate(
                        chunkIndex = current.chunkIndex,
                        tokenText = rawWord,
                        priorityScore = RefinementReason.INDIC_SCRIPT_AMBIGUITY.defaultPriority,
                        reason = RefinementReason.INDIC_SCRIPT_AMBIGUITY,
                        startIndex = wordStart,
                        endIndex = wordEnd,
                        contextBefore = contextBefore,
                        contextAfter = contextAfter
                    )
                )
                continue
            }

            // Rule E: Low Pass 1 confidence
            if (current.confidence < confidenceThreshold) {
                candidates.add(
                    RefinementCandidate(
                        chunkIndex = current.chunkIndex,
                        tokenText = rawWord,
                        priorityScore = RefinementReason.LOW_CONFIDENCE.defaultPriority,
                        reason = RefinementReason.LOW_CONFIDENCE,
                        startIndex = wordStart,
                        endIndex = wordEnd,
                        contextBefore = contextBefore,
                        contextAfter = contextAfter
                    )
                )
            }
        }

        // De-duplicate candidates on the same token span, keeping the one with higher priority
        return candidates
            .groupBy { it.spanKey }
            .values
            .map { list -> list.maxByOrNull { it.priorityScore }!! }
            .sortedByDescending { it.priorityScore }
    }
}
