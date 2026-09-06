package org.sih.itantra.ml.tts

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * High-performance, thread-safe preprocessor for Tamil Text-to-Speech synthesis.
 * Tailored specifically for the Meta MMS VITS Tamil model (vits-mms-ta):
 * 1. Normalizes Unicode to NFC canonical form.
 * 2. Transliterates operational military/radio English loanwords and acronyms to Tamil phonetics.
 * 3. Transliterates rogue English Latin letters to Tamil phonetic names.
 * 4. Expands numerals and digits to Tamil words (crucial because digit '8' is absent in MMS tokens.txt).
 * 5. Adapts Tamil Aytham ('ஃ' U+0B83, also absent in tokens.txt) contextually to natural Tamil phonemes.
 * 6. Normalizes punctuation marks to whitespace to prevent unknown-character warnings and serialized multi-pass batching overhead.
 */
object TamilTextPreprocessor {

    // Common operational radio / transceiver terms mapped to natural Tamil phonetics
    private val OPERATIONAL_TERMS = listOf(
        Pattern.compile("(?i)\\bi-?tantra\\b") to "ஐ தந்த்ரா",
        Pattern.compile("(?i)\\bradio\\b") to "ரேடியோ",
        Pattern.compile("(?i)\\bsos\\b") to "எஸ் ஓ எஸ்",
        Pattern.compile("(?i)\\bptt\\b") to "பி டி டி",
        Pattern.compile("(?i)\\bok\\b") to "சரி",
        Pattern.compile("(?i)\\bgps\\b") to "ஜி பி எஸ்",
        Pattern.compile("(?i)\\bbattery\\b") to "பேட்டரி",
        Pattern.compile("(?i)\\bsignal\\b") to "சிக்னல்",
        Pattern.compile("(?i)\\brfcomm\\b") to "ஆர் எப் காம்",
        Pattern.compile("(?i)\\bbluetooth\\b") to "ப்ளூடூத்",
        Pattern.compile("(?i)\\bwi-?fi\\b") to "வை பை",
        Pattern.compile("(?i)\\bchannel\\b") to "சேனல்",
        Pattern.compile("(?i)\\bemergency\\b") to "அவசரம்",
        Pattern.compile("(?i)\\boffline\\b") to "ஆப்லைன்",
        Pattern.compile("(?i)\\bonline\\b") to "ஆன்லைன்",
        Pattern.compile("(?i)\\bmesh\\b") to "மெஷ்",
        Pattern.compile("(?i)\\btest\\b") to "சோதனை"
    )

    // Latin alphabet phonetic pronunciation map for any rogue English letters
    private val LATIN_CHAR_MAP = mapOf(
        'a' to "ஏ", 'A' to "ஏ",
        'b' to "பி", 'B' to "பி",
        'c' to "சி", 'C' to "சி",
        'd' to "டி", 'D' to "டி",
        'e' to "ஈ", 'E' to "ஈ",
        'f' to "எப்", 'F' to "எப்",
        'g' to "ஜி", 'G' to "ஜி",
        'h' to "எச்", 'H' to "எச்",
        'i' to "ஐ", 'I' to "ஐ",
        'j' to "ஜே", 'J' to "ஜே",
        'k' to "கே", 'K' to "கே",
        'l' to "எல்", 'L' to "எல்",
        'm' to "எம்", 'M' to "எம்",
        'n' to "என்", 'N' to "என்",
        'o' to "ஓ", 'O' to "ஓ",
        'p' to "பி", 'P' to "பி",
        'q' to "க்யூ", 'Q' to "க்யூ",
        'r' to "ஆர்", 'R' to "ஆர்",
        's' to "எஸ்", 'S' to "எஸ்",
        't' to "டி", 'T' to "டி",
        'u' to "யூ", 'U' to "யூ",
        'v' to "வி", 'V' to "வி",
        'w' to "டபிள்யூ", 'W' to "டபிள்யூ",
        'x' to "எக்ஸ்", 'X' to "எக்ஸ்",
        'y' to "ஒய்", 'Y' to "ஒய்",
        'z' to "இசட்", 'Z' to "இசட்"
    )

    // Tamil digits 0-9
    private val DIGITS_TAMIL = arrayOf(
        "பூஜ்ஜியம்", "ஒன்று", "இரண்டு", "மூன்று", "நான்கு",
        "ஐந்து", "ஆறு", "ஏழு", "எட்டு", "ஒன்பது"
    )

    // Numbers 1-19
    private val ONES_AND_TEENS = arrayOf(
        "", "ஒன்று", "இரண்டு", "மூன்று", "நான்கு", "ஐந்து",
        "ஆறு", "ஏழு", "எட்டு", "ஒன்பது", "பத்து",
        "பதினொன்று", "பன்னிரண்டு", "பதின்மூன்று", "பதினான்கு", "பதினைந்து",
        "பதினாறு", "பதினேழு", "பதினெட்டு", "பத்தொன்பது"
    )

    // Tens prefixes for compound numbers (e.g. 21 -> இருபத்து ஒன்று)
    private val TENS_PREFIX = arrayOf(
        "", "", "இருபத்து", "முப்பத்து", "நாற்பத்து",
        "ஐம்பத்து", "அறுபத்து", "எழுபத்து", "எண்பத்து", "தொண்ணூற்று"
    )

    // Exact tens (e.g. 20 -> இருபது)
    private val TENS_EXACT = arrayOf(
        "", "", "இருபது", "முப்பது", "நாற்பது",
        "ஐம்பது", "அறுபது", "எழுபது", "எண்பது", "தொண்ணூறு"
    )

    // Hundreds
    private val HUNDREDS_EXACT = arrayOf(
        "", "நூறு", "இருநூறு", "முந்நூறு", "நானூறு",
        "ஐந்நூறு", "அறுநூறு", "எழுநூறு", "எண்ணூறு", "தொள்ளாயிரம்"
    )
    private val HUNDREDS_PREFIX = arrayOf(
        "", "நூற்று", "இருநூற்று", "முந்நூற்று", "நானூற்று",
        "ஐந்நூற்று", "அறுநூற்று", "எழுநூற்று", "எண்ணூற்று", "தொள்ளாயிரத்து"
    )

    // Thousands exact (1000 - 9000)
    private val THOUSANDS_EXACT = arrayOf(
        "", "ஆயிரம்", "இரண்டாயிரம்", "மூன்றாயிரம்", "நான்காயிரம்",
        "ஐந்தாயிரம்", "ஆறாயிரம்", "ஏழாயிரம்", "எண்ணாயிரம்", "ஒன்பதாயிரம்"
    )

    // Thousands prefix for compound numbers (1001 - 9999)
    private val THOUSANDS_PREFIX = arrayOf(
        "", "ஆயிரத்து", "இரண்டாயிரத்து", "மூன்றாயிரத்து", "நான்காயிரத்து",
        "ஐந்தாயிரத்து", "ஆறாயிரத்து", "ஏழாயிரத்து", "எண்ணாயிரத்து", "ஒன்பதாயிரத்து"
    )

    // Match numbers with optional Tamil case suffixes (like 8ல், 2026ல், 8வது)
    private val NUMBER_WITH_SUFFIX_REGEX = Pattern.compile("(\\d+)(ல்|வது|ஆம்|க்கு)?")

    // Tamil numeral characters mapping
    private val TAMIL_NUMERALS_MAP = mapOf(
        '௦' to '0', '௧' to '1', '௨' to '2', '௩' to '3', '௪' to '4',
        '௫' to '5', '௬' to '6', '௭' to '7', '௮' to '8', '௯' to '9'
    )

    /**
     * Preprocess input text for Tamil MMS VITS synthesis.
     */
    fun process(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. Canonical Unicode normalization
        var text = Normalizer.normalize(rawText, Normalizer.Form.NFC)

        // 2. Map Tamil numeral characters (௦-௯) to standard ASCII digits
        if (text.any { it in TAMIL_NUMERALS_MAP }) {
            val sb = StringBuilder(text.length)
            for (c in text) {
                sb.append(TAMIL_NUMERALS_MAP[c] ?: c)
            }
            text = sb.toString()
        }

        // 3. Operational radio & military English word transliteration
        for ((pattern, replacement) in OPERATIONAL_TERMS) {
            text = pattern.matcher(text).replaceAll(replacement)
        }

        // 4. Adapt Aytham ('ஃ' U+0B83)
        // MMS tokens.txt lacks 'ஃ'. In spoken Tamil loanwords (e.g. ஆஃப்லைன் -> ஆப்லைன்),
        // or classical Tamil (அஃது -> அக்து), replace with natural plosives.
        text = text.replace("ஆஃப்", "ஆப்")
        text = text.replace("ஃ", "க்")

        // 5. Expand numbers to Tamil words (with suffix handling e.g. 8ல் -> எட்டில்)
        text = expandNumbers(text)

        // 6. Transliterate any remaining rogue Latin letters to Tamil phonetics
        if (text.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            val sb = StringBuilder(text.length * 2)
            for (c in text) {
                val tamilPhonetic = LATIN_CHAR_MAP[c]
                if (tamilPhonetic != null) {
                    sb.append(' ').append(tamilPhonetic).append(' ')
                } else {
                    sb.append(c)
                }
            }
            text = sb.toString()
        }

        // 7. Punctuation normalization:
        // Punctuation characters (,.!?:;-"'()[]{}<>/\\|@#$%^&*+=~`) are not in MMS tokens.txt
        // and trigger unknown-character errors and multi-pass sentence splits.
        // Replace with space to provide natural pauses without triggering multi-batch inference.
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                ',', ';', ':', '-', '—', '–', '\"', '(', ')', '[', ']', '{', '}',
                '<', '>', '/', '\\', '|', '@', '#', '$', '%', '^', '&', '*', '+', '=',
                '~', '`', '!', '?', '.', '_' -> sb.append(' ')
                else -> sb.append(c)
            }
        }

        // 8. Collapse whitespace and trim
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Expands number sequences to Tamil words, handling Tamil locative/ordinal suffixes.
     */
    private fun expandNumbers(text: String): String {
        val matcher = NUMBER_WITH_SUFFIX_REGEX.matcher(text)
        val sb = StringBuffer(text.length * 2)

        while (matcher.find()) {
            val numStr = matcher.group(1) ?: ""
            val suffix = matcher.group(2) ?: ""

            val numVal = numStr.toLongOrNull()
            val expandedWords = if (numVal != null && numVal in 0..999999) {
                numberToTamilWords(numVal.toInt())
            } else {
                // Large number / code: spell out digit-by-digit
                numStr.map { DIGITS_TAMIL[it - '0'] }.joinToString(" ")
            }

            val finalWithSuffix = when (suffix) {
                "ல்" -> {
                    // Suffix adaptation: எட்டு + ல் -> எட்டில், ஒன்று + ல் -> ஒன்றில், ஆறு + ல் -> ஆறில்
                    when {
                        expandedWords.endsWith("எட்டு") -> expandedWords.removeSuffix("எட்டு") + "எட்டில்"
                        expandedWords.endsWith("ஒன்று") -> expandedWords.removeSuffix("ஒன்று") + "ஒன்றில்"
                        expandedWords.endsWith("ஆறு") -> expandedWords.removeSuffix("ஆறு") + "ஆறில்"
                        expandedWords.endsWith("ஐந்து") -> expandedWords.removeSuffix("ஐந்து") + "ஐந்தில்"
                        expandedWords.endsWith("இரண்டு") -> expandedWords.removeSuffix("இரண்டு") + "இரண்டில்"
                        expandedWords.endsWith("மூன்று") -> expandedWords.removeSuffix("மூன்று") + "மூன்றில்"
                        expandedWords.endsWith("நான்கு") -> expandedWords.removeSuffix("நான்கு") + "நான்கில்"
                        expandedWords.endsWith("ஏழு") -> expandedWords.removeSuffix("ஏழு") + "ஏழில்"
                        expandedWords.endsWith("ஒன்பது") -> expandedWords.removeSuffix("ஒன்பது") + "ஒன்பதில்"
                        expandedWords.endsWith("பத்து") -> expandedWords.removeSuffix("பத்து") + "பத்தில்"
                        expandedWords.endsWith("நூறு") -> expandedWords.removeSuffix("நூறு") + "நூற்றில்"
                        else -> "$expandedWords இல்"
                    }
                }
                "வது" -> "$expandedWords ஆவது"
                "ஆம்" -> "$expandedWords ஆம்"
                "க்கு" -> "$expandedWords க்கு"
                else -> expandedWords
            }

            matcher.appendReplacement(sb, MatcherQuoteReplacement(finalWithSuffix))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun MatcherQuoteReplacement(s: String): String =
        java.util.regex.Matcher.quoteReplacement(s)

    /**
     * Converts an integer in 0..999,999 to spoken Tamil words.
     */
    fun numberToTamilWords(n: Int): String {
        if (n == 0) return DIGITS_TAMIL[0]
        if (n < 0) return "கழித்தல் " + numberToTamilWords(-n)

        val parts = mutableListOf<String>()

        var rem = n

        // Thousands (1000 - 999999)
        val thousands = rem / 1000
        if (thousands > 0) {
            val remAfterThousand = rem % 1000
            if (thousands in 1..9) {
                if (remAfterThousand == 0) {
                    parts.add(THOUSANDS_EXACT[thousands])
                } else {
                    parts.add(THOUSANDS_PREFIX[thousands])
                }
            } else {
                val thWords = convertUnder1000(thousands)
                if (remAfterThousand == 0) {
                    parts.add("$thWords ஆயிரம்")
                } else {
                    parts.add("$thWords ஆயிரத்து")
                }
            }
            rem %= 1000
        }

        // Hundreds and below
        if (rem > 0) {
            parts.add(convertUnder1000(rem))
        }

        return parts.joinToString(" ").trim()
    }

    private fun convertUnder1000(n: Int): String {
        var rem = n
        val parts = mutableListOf<String>()

        val hundreds = rem / 100
        if (hundreds > 0) {
            rem %= 100
            if (rem == 0) {
                parts.add(HUNDREDS_EXACT[hundreds])
            } else {
                parts.add(HUNDREDS_PREFIX[hundreds])
            }
        }

        if (rem > 0) {
            if (rem < 20) {
                parts.add(ONES_AND_TEENS[rem])
            } else {
                val tens = rem / 10
                val ones = rem % 10
                if (ones == 0) {
                    parts.add(TENS_EXACT[tens])
                } else {
                    parts.add(TENS_PREFIX[tens] + " " + ONES_AND_TEENS[ones])
                }
            }
        }

        return parts.joinToString(" ").trim()
    }
}
