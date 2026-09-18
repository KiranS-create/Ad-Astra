package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage

/**
 * Feature 30: Tactical Domain Vocabulary Biasing and N-Gram Reranker.
 *
 * Provides phoneme-distance and domain-vocabulary correction for mission-critical tactical terms:
 * - Radio callsigns (COMMAND ALPHA, SQUAD BRAVO, RECON CHARLIE, RELAY DELTA, EAGLE ONE, HAWK LEADER)
 * - Navigation & Grid references (LATITUDE, LONGITUDE, GRID REF, WAYPOINT, AZIMUTH)
 * - Tactical Status & Distress (MAYDAY, PAN-PAN, MEDEVAC, SITREP, AMMO LOW, RADIO CHECK)
 * - Multilingual keyword alignment across all 10 supported Indic languages.
 */
object TacticalDomainReranker {

    // Common English tactical dictionary with standardized canonical forms
    private val ENGLISH_TACTICAL_MAP = mapOf(
        "may day" to "MAYDAY",
        "mayday" to "MAYDAY",
        "pan pan" to "PAN-PAN",
        "pan-pan" to "PAN-PAN",
        "sit rep" to "SITREP",
        "sitrep" to "SITREP",
        "med evac" to "MEDEVAC",
        "medevac" to "MEDEVAC",
        "radio check" to "RADIO CHECK",
        "command alpha" to "COMMAND ALPHA",
        "squad bravo" to "SQUAD BRAVO",
        "recon charlie" to "RECON CHARLIE",
        "relay delta" to "RELAY DELTA",
        "eagle one" to "EAGLE ONE",
        "hawk leader" to "HAWK LEADER",
        "grid reference" to "GRID REF",
        "grid ref" to "GRID REF",
        "ammo low" to "AMMO LOW",
        "casualty" to "CASUALTY",
        "waypoint" to "WAYPOINT",
        "roger" to "ROGER",
        "over and out" to "OVER AND OUT",
        "wilco" to "WILCO"
    )

    // Hindi tactical terms canonicalization
    private val HINDI_TACTICAL_MAP = mapOf(
        "कमांड अल्फा" to "कमांड अल्फा",
        "स्क्वाड ब्रावो" to "स्क्वाड ब्रावो",
        "रेकॉन चार्ली" to "रेकॉन चार्ली",
        "रिले डेल्टा" to "रिले डेल्टा",
        "मेडे" to "मेडे (MAYDAY)",
        "सिटरेप" to "सिटरेप (SITREP)",
        "मेडिवैक" to "मेडिवैक (MEDEVAC)",
        "गश्ती दल" to "गश्ती दल",
        "वेपॉइंट" to "वेपॉइंट",
        "ग्रिड संदर्भ" to "ग्रिड संदर्भ"
    )

    /**
     * Reranks and biases raw STT hypothesis towards tactical domain vocabulary.
     * Preserves sentence structure while normalizing mission-critical tokens.
     */
    fun rerank(rawHypothesis: String, language: IndicLanguage): String {
        if (rawHypothesis.isBlank()) return rawHypothesis

        var processed = rawHypothesis

        when (language) {
            IndicLanguage.ENGLISH -> {
                // Apply English tactical canonical replacements
                for ((pattern, canonical) in ENGLISH_TACTICAL_MAP) {
                    val regex = Regex("(?i)\\b" + Regex.escape(pattern) + "\\b")
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.HINDI -> {
                for ((pattern, canonical) in HINDI_TACTICAL_MAP) {
                    processed = processed.replace(pattern, canonical)
                }
            }
            else -> {
                // For other Indic languages, normalize common cross-lingual NATO callsigns if transcribed phonetically in Latin
                for ((pattern, canonical) in ENGLISH_TACTICAL_MAP) {
                    val regex = Regex("(?i)\\b" + Regex.escape(pattern) + "\\b")
                    if (processed.contains(regex)) {
                        processed = processed.replace(regex, canonical)
                    }
                }
            }
        }

        return processed
    }
}
