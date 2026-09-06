package org.sih.itantra.core.protocol

import java.util.Locale
import java.util.regex.Pattern

/**
 * Deterministic, rule-based structured emergency command classifier.
 *
 * Implements explicit vocabulary and keyword-pattern matching for high-priority
 * emergency scenarios without any AI or NLP models.
 *
 * Fallback Rule:
 * Only returns a [SemanticCommand] when deterministic classification is confident.
 * For unknown, conversational, or ambiguous sentences, returns null so the system
 * safely falls back to standard UTF-8 text transmission.
 */
object SemanticEmergencyClassifier {

    private val DIGIT_REGEX = Pattern.compile("\\b(\\d{1,3})\\b")

    private val WORD_NUMBERS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "ஒரு" to 1, "ஒன்று" to 1, "இரண்டு" to 2, "மூன்று" to 3, "நான்கு" to 4, "ஐந்து" to 5,
        "ஆறு" to 6, "ஏழு" to 7, "எட்டு" to 8, "ஒன்பது" to 9, "பத்து" to 10
    )

    /**
     * Attempts deterministic classification of [text] into a structured [SemanticCommand].
     *
     * @param text The input natural language utterance from STT or distress trigger.
     * @return [SemanticCommand] if a confident deterministic rule matched; null otherwise.
     */
    fun classify(text: String): SemanticCommand? {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.length > 120) {
            // Empty or overly verbose/narrative utterances must remain free-form text
            return null
        }

        val clean = trimmed.lowercase(Locale.ROOT)

        // Conversational/narrative explanations or conjunctions indicate free-form text rather than a structured command
        if (clean.contains("because") || clean.contains("since") || clean.contains("due to") || clean.contains("as we")) {
            return null
        }

        val count = extractCount(clean)

        // 1. MEDICAL CATEGORY
        if (clean.contains("ambulance") || clean.contains("ஆம்புலன்ஸ்")) {
            return SemanticCommand(
                category = EmergencyCategory.MEDICAL,
                subtype = EmergencySubtype.AMBULANCE,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        if (clean.contains("unconscious") || clean.contains("மயக்கம்") || clean.contains("மயக்கமடைந்து")) {
            return SemanticCommand(
                category = EmergencyCategory.MEDICAL,
                subtype = EmergencySubtype.UNCONSCIOUS,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        if (clean.contains("injured") || clean.contains("injuries") || clean.contains("காயம்") || clean.contains("காயமடைந்த")) {
            return SemanticCommand(
                category = EmergencyCategory.MEDICAL,
                subtype = EmergencySubtype.INJURED,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        if (clean.contains("medical emergency") || clean.contains("hospital needed") || clean.contains("மருத்துவ அவசரம்")) {
            return SemanticCommand(
                category = EmergencyCategory.MEDICAL,
                subtype = EmergencySubtype.NONE,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        // 2. FIRE CATEGORY
        if (clean.contains("fire") || clean.contains("flames") || clean.contains("burning") || clean.contains("தீ விபத்து") || clean.contains("தீப்பிடித்த")) {
            val isBuilding = clean.contains("building") || clean.contains("house") || clean.contains("facility") || clean.contains("கட்டடம்")
            return SemanticCommand(
                category = EmergencyCategory.FIRE,
                subtype = if (isBuilding) EmergencySubtype.BUILDING else EmergencySubtype.NONE,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        // 3. TRAPPED / COLLAPSE CATEGORY
        if (clean.contains("trapped") || clean.contains("debris") || clean.contains("சிக்கியுள்ளனர்") || clean.contains("மாட்டியுள்ளனர்")) {
            return SemanticCommand(
                category = EmergencyCategory.TRAPPED,
                subtype = EmergencySubtype.NONE,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        if (clean.contains("collapse") || clean.contains("collapsed") || clean.contains("இடிந்து")) {
            return SemanticCommand(
                category = EmergencyCategory.TRAPPED,
                subtype = EmergencySubtype.COLLAPSE,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        // 4. RESCUE CATEGORY
        if (clean.contains("rescue team") || clean.contains("மீட்புக் குழு") || (clean.contains("rescue") && clean.contains("team"))) {
            return SemanticCommand(
                category = EmergencyCategory.RESCUE,
                subtype = EmergencySubtype.TEAM,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        if (clean.startsWith("send rescue") || clean.startsWith("rescue needed") || clean.contains("மீட்கவும்") || clean.contains("காப்பாற்றுங்கள்")) {
            return SemanticCommand(
                category = EmergencyCategory.RESCUE,
                subtype = EmergencySubtype.NONE,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        // 5. SUPPLY CATEGORY (WATER & FOOD)
        val isSupplyContext = clean.contains("need") || clean.contains("supply") || clean.contains("send") ||
                clean.contains("require") || clean.contains("shortage") || clean.contains("தேவை")

        if ((clean.contains("water") || clean.contains("தண்ணீர்") || clean.contains("குடிநீர்")) && isSupplyContext) {
            return SemanticCommand(
                category = EmergencyCategory.SUPPLY,
                subtype = EmergencySubtype.WATER,
                count = count,
                severity = EmergencySeverity.ALERT
            )
        }

        if ((clean.contains("food") || clean.contains("ration") || clean.contains("உணவு")) && isSupplyContext) {
            return SemanticCommand(
                category = EmergencyCategory.SUPPLY,
                subtype = EmergencySubtype.FOOD,
                count = count,
                severity = EmergencySeverity.ALERT
            )
        }

        // 6. EVACUATION CATEGORY
        if (clean.contains("evacuate") || clean.contains("evacuation") || clean.contains("வெளியேறவும்")) {
            return SemanticCommand(
                category = EmergencyCategory.EVACUATION,
                subtype = EmergencySubtype.NONE,
                count = count,
                severity = EmergencySeverity.CRITICAL
            )
        }

        // Conservative fallback: no confident rule matched
        return null
    }

    /**
     * Extracts a numeric count from digits or spelled-out number words.
     */
    fun extractCount(cleanText: String): Int {
        // 1. Check for digits
        val matcher = DIGIT_REGEX.matcher(cleanText)
        if (matcher.find()) {
            val num = matcher.group(1)?.toIntOrNull()
            if (num != null && num in 1..255) return num
        }

        // 2. Check for word numbers
        val words = cleanText.split(Regex("[\\s,;.]+"))
        for (w in words) {
            val mapped = WORD_NUMBERS[w]
            if (mapped != null) return mapped
        }

        return 0
    }
}
