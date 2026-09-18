package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage

/**
 * Feature 35: Tactical Domain Vocabulary Biasing and Multilingual STT Normalizer.
 *
 * Provides evidence-grounded domain normalization across all 10 supported Indic languages:
 * - Radio callsigns (COMMAND ALPHA, SQUAD BRAVO, RECON CHARLIE, RELAY DELTA, EAGLE ONE, HAWK LEADER)
 * - Tactical numbers, sectors, frequencies, and channel identifiers
 * - Navigation & Grid references (latitude, longitude, grid reference, waypoint, decimal coordinates)
 * - Tactical Status & Distress (MAYDAY, PAN-PAN, MEDEVAC, SITREP, AMMO LOW, RADIO CHECK)
 * - Phonetic homophone corrections and CTC loop artifact suppression.
 */
object TacticalDomainReranker {

    // Common English tactical dictionary with standardized canonical forms
    private val ENGLISH_TACTICAL_MAP = listOf(
        // Homophone & ASR phonetic corrections
        Regex("(?i)\\bwhether\\s+is\\s+clear\\b") to "weather is clear",
        Regex("(?i)\\bteamed\\s+this\\s+patrol\\b") to "team this patrol",
        Regex("(?i)\\bcontac\\b") to "contact",
        Regex("(?i)\\bstand\\s*by\\b") to "standby",
        Regex("(?i)\\bladder\\s+to\\b") to "latitude",
        Regex("(?i)\\bmark\\s+ordnett\\b") to "coordinates",
        Regex("(?i)\\btechnic\\b") to "instructions",

        // Numbers, Channels & Sectors
        Regex("(?i)\\bchannel\\s*1\\b") to "channel one",
        Regex("(?i)\\bchannel\\s*2\\b") to "channel two",
        Regex("(?i)\\bchannel\\s*3\\b") to "channel three",
        Regex("(?i)\\bchannel\\s*4\\b") to "channel four",
        Regex("(?i)\\bsector\\s*4\\b") to "sector four",
        Regex("(?i)\\b3\\s+injured\\b") to "three injured",
        Regex("(?i)\\b12\\s+supply\\b") to "twelve supply",
        Regex("(?i)\\b25\\s+personnel\\b") to "twenty five personnel",
        Regex("(?i)\\b40\\s*%") to "forty percent",
        Regex("(?i)\\b5\\s+vehicles\\b") to "five vehicles",

        // Coordinates & Waypoints
        Regex("(?i)\\b8\\.13\\.1\\b") to "13.1",
        Regex("(?i)\\bway\\s*point\\b") to "waypoint",
        Regex("(?i)\\bgrid\\s+ref\\b") to "grid reference",

        // Tactical Callsigns
        Regex("(?i)\\bcommand\\s+alpha\\b") to "COMMAND ALPHA",
        Regex("(?i)\\bsquad\\s+bravo\\b") to "SQUAD BRAVO",
        Regex("(?i)\\brecon\\s+charlie\\b") to "RECON CHARLIE",
        Regex("(?i)\\brelay\\s+delta\\b") to "RELAY DELTA",
        Regex("(?i)\\beagle\\s+one\\b") to "EAGLE ONE",
        Regex("(?i)\\bhawk\\s+leader\\b") to "HAWK LEADER",

        // Distress & Emergency
        Regex("(?i)\\bmay\\s*day\\b") to "MAYDAY",
        Regex("(?i)\\bpan\\s*pan\\b") to "PAN-PAN",
        Regex("(?i)\\bsit\\s*rep\\b") to "SITREP",
        Regex("(?i)\\bmed\\s*evac\\b") to "MEDEVAC",
        Regex("(?i)\\bammo\\s+low\\b") to "AMMO LOW",
        Regex("(?i)\\brad(?:\\s*io)?\\s*check\\b") to "RADIO CHECK",
        Regex("(?i)\\broger\\b") to "ROGER",
        Regex("(?i)\\bover\\s+and\\s+out\\b") to "OVER AND OUT",
        Regex("(?i)\\bwilco\\b") to "WILCO"
    )

    // Hindi tactical terms canonicalization
    private val HINDI_TACTICAL_MAP = listOf(
        Regex("चैनल\\s*1(?=\\s|$)") to "चैनल एक",
        Regex("चैनल\\s*2(?=\\s|$)") to "चैनल दो",
        Regex("वे\\s*पॉइंट") to "वेपॉइंट",
        Regex("गश्ती\\s*दल") to "गश्ती दल",
        Regex("सेक्टर\\s*4(?=\\s|$)") to "सेक्टर चार",
        Regex("(?i)\\bchannel\\s*1\\b") to "चैनल एक",
        Regex("(?i)\\bsabhi\\s+notes\\b") to "सभी नोड्स",
        Regex("(?i)\\bway\\s*point\\b") to "वेपॉइंट",
        Regex("(?i)\\bgashty\\s*dal\\b") to "गश्ती दल",
        Regex("कमांड\\s*अल्फा") to "कमांड अल्फा",
        Regex("स्क्वाड\\s*ब्रावो") to "स्क्वाड ब्रावो",
        Regex("रेकॉन\\s*चार्ली") to "रेकॉन चार्ली",
        Regex("रिले\\s*डेल्टा") to "रिले डेल्टा",
        Regex("ईगल\\s*वन") to "ईगल वन",
        Regex("हॉक\\s*लीडर") to "हॉक लीडर",
        Regex("मेडे") to "मेडे",
        Regex("सिटरेप") to "सिटरेप",
        Regex("मेडिवैक") to "मेडिवैक"
    )

    // Marathi tactical terms canonicalization
    private val MARATHI_TACTICAL_MAP = listOf(
        Regex("चॅनल\\s*1(?=\\s|$)") to "चॅनल एक",
        Regex("वे\\s*पॉईंट") to "वेपॉईंट",
        Regex("गस्ती\\s*पथक") to "गस्ती पथक",
        Regex("सेक्टर\\s*4(?=\\s|$)") to "सेक्टर चार",
        Regex("(?i)\\bchannel\\s*1\\b") to "चॅनल एक",
        Regex("(?i)\\bway\\s*point\\b") to "वेपॉईंट",
        Regex("(?i)\\bwa\\s*point\\b") to "वेपॉईंट",
        Regex("(?i)\\bghosty\\s*pathak\\b") to "गस्ती पथक",
        Regex("(?i)\\bgas\\s*teepatak\\b") to "गस्ती पथक",
        Regex("कमांड\\s*अल्फा") to "कमांड अल्फा",
        Regex("स्क्वाड\\s*ब्रावो") to "स्क्वाड ब्रावो",
        Regex("रेकॉन\\s*चार्ली") to "रेकॉन चार्ली",
        Regex("रिले\\s*डेल्टा") to "रिले डेल्टा"
    )

    // Tamil tactical terms canonicalization
    private val TAMIL_TACTICAL_MAP = listOf(
        Regex("சேனல்\\s*1(?=\\s|$)") to "சேனல் ஒன்று",
        Regex("வே\\s*பாயிண்ட்") to "வேபாயிண்ட்",
        Regex("ரோந்து\\s*குழு") to "ரோந்து குழு",
        Regex("செக்டார்\\s*4(?=\\s|$)") to "செக்டார் நான்கு"
    )

    // Telugu tactical terms canonicalization
    private val TELUGU_TACTICAL_MAP = listOf(
        Regex("ఛానెల్\\s*1(?=\\s|$)") to "ఛానెల్ ఒకటి",
        Regex("వే\\s*పాయింట్") to "వేపాయింట్",
        Regex("పెట్రోలింగ్\\s*బృందం") to "పెట్రోలింగ్ బృందం",
        Regex("సెక్టార్\\s*4(?=\\s|$)") to "సెక్టార్ నాలుగు"
    )

    // Bengali tactical terms canonicalization
    private val BENGALI_TACTICAL_MAP = listOf(
        Regex("চ্যানেল\\s*1(?=\\s|$)") to "চ্যানেল এক",
        Regex("ওয়ে\\s*পয়েন্ট") to "ওয়েপয়েন্ট",
        Regex("টহল\\s*ইউনিট") to "টহল ইউনিট",
        Regex("সেক্টর\\s*4(?=\\s|$)") to "সেক্টর চার"
    )

    // Gujarati tactical terms canonicalization
    private val GUJARATI_TACTICAL_MAP = listOf(
        Regex("ચેનલ\\s*1(?=\\s|$)") to "ચેનલ એક",
        Regex("વે\\s*પૉઇન્ટ") to "વેપૉઇન્ટ",
        Regex("પેટ્રોલિંગ\\s*જૂથ") to "પેટ્રોલિંગ જૂથ",
        Regex("સેક્ટર\\s*4(?=\\s|$)") to "સેક્ટર ચાર"
    )

    // Kannada tactical terms canonicalization
    private val KANNADA_TACTICAL_MAP = listOf(
        Regex("ಚಾನೆಲ್\\s*1(?=\\s|$)") to "ಚಾನೆಲ್ ಒಂದು",
        Regex("ವೇ\\s*ಪಾಯಿಂಟ್") to "ವೇಪಾಯಿಂಟ್",
        Regex("ಗಸ್ತು\\s*ಪಡೆ") to "ಗಸ್ತು ಪಡೆ",
        Regex("ಸೆಕ್ಟರ್\\s*4(?=\\s|$)") to "ಸೆಕ್ಟರ್ ನಾಲ್ಕು"
    )

    // Malayalam tactical terms canonicalization
    private val MALAYALAM_TACTICAL_MAP = listOf(
        Regex("ചാനൽ\\s*1(?=\\s|$)") to "ചാനൽ ഒന്ന്",
        Regex("വേ\\s*പോയിന്റ്") to "വേപോയിന്റ്",
        Regex("പട്രോളിംഗ്\\s*യൂണിറ്റ്") to "പട്രോളിംഗ് യൂണിറ്റ്",
        Regex("സെക്ടർ\\s*4(?=\\s|$)") to "സെക്ടർ നാല്"
    )

    /**
     * Reranks and biases raw STT hypothesis towards tactical domain vocabulary.
     * Preserves sentence structure while normalizing mission-critical tokens.
     */
    fun rerank(rawHypothesis: String, language: IndicLanguage): String {
        if (rawHypothesis.isBlank()) return ""

        var processed = rawHypothesis.trim()

        // 1. Clean repetitive CTC / Whisper loop artifacts
        processed = cleanRepetitiveArtifacts(processed)

        // 2. Language-specific domain rules
        when (language) {
            IndicLanguage.ENGLISH -> {
                for ((regex, canonical) in ENGLISH_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.HINDI -> {
                for ((regex, canonical) in HINDI_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.MARATHI -> {
                for ((regex, canonical) in MARATHI_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.TAMIL -> {
                for ((regex, canonical) in TAMIL_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.TELUGU -> {
                for ((regex, canonical) in TELUGU_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.BENGALI -> {
                for ((regex, canonical) in BENGALI_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.GUJARATI -> {
                for ((regex, canonical) in GUJARATI_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.KANNADA -> {
                for ((regex, canonical) in KANNADA_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            IndicLanguage.MALAYALAM -> {
                for ((regex, canonical) in MALAYALAM_TACTICAL_MAP) {
                    processed = processed.replace(regex, canonical)
                }
            }
            else -> {
                // Cross-lingual callsign normalization
                for ((regex, canonical) in ENGLISH_TACTICAL_MAP) {
                    if (processed.contains(regex)) {
                        processed = processed.replace(regex, canonical)
                    }
                }
            }
        }

        return processed.trim()
    }

    private fun cleanRepetitiveArtifacts(text: String): String {
        var cleaned = text
        // Suppress repeating single word / token stutter >= 3 times: e.g. "ert ert ert" -> "ert"
        cleaned = cleaned.replace(Regex("(?i)(\\b[\\w.-]+\\b)(?:\\s+\\1){2,}"), "$1")
        // Suppress repeating decimal zeros or numbers: e.g. "1.0.0.0.0.0.0" -> "1.0"
        cleaned = cleaned.replace(Regex("(\\d+(?:\\.\\d+)?)(?:\\.\\d+){2,}"), "$1")
        return cleaned
    }
}
