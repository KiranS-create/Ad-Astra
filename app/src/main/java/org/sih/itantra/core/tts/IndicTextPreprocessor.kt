package org.sih.itantra.core.tts

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.ml.tts.TamilTextPreprocessor
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Universal, high-performance multilingual text preprocessor for on-device neural Text-to-Speech.
 * Provides complete linguistic conditioning across all 10 supported Indian languages:
 *
 * 1. Canonical Unicode normalization (NFC) and invisible zero-width character sanitization.
 * 2. Transliteration of Indic script numerals (Devanagari, Bengali, Gujarati, Odia, Telugu, Kannada, Malayalam, Tamil) to ASCII digits.
 * 3. Full verbalization of numbers (0..999,999), percentages, and decimal coordinates into natural native words.
 * 4. Transliteration of operational military/radio loanwords and tactical acronyms (SOS, PTT, GPS, MEDEVAC, SITREP, MAYDAY) into native phonetics.
 * 5. Prosodic pause normalization: replaces harsh punctuation with whitespace micro-pauses, preventing token crashes and unhandled exceptions in character-based MMS and Piper models.
 */
object IndicTextPreprocessor {

    // Native numeral characters mapping to standard ASCII digits '0'..'9'
    private val NATIVE_NUMERALS_MAP = mapOf(
        // Devanagari (Hindi, Marathi)
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9',
        // Bengali
        '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
        '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9',
        // Gujarati
        '૦' to '0', '૧' to '1', '૨' to '2', '૩' to '3', '૪' to '4',
        '૫' to '5', '૬' to '6', '૭' to '7', '૮' to '8', '૯' to '9',
        // Odia
        '୦' to '0', '୧' to '1', '୨' to '2', '୩' to '3', '୪' to '4',
        '୫' to '5', '୬' to '6', '୭' to '7', '୮' to '8', '୯' to '9',
        // Telugu
        '౦' to '0', '౧' to '1', '౨' to '2', '౩' to '3', '౪' to '4',
        '౫' to '5', '౬' to '6', '౭' to '7', '౮' to '8', '౯' to '9',
        // Kannada
        '೦' to '0', '೧' to '1', '೨' to '2', '೩' to '3', '೪' to '4',
        '೫' to '5', '೬' to '6', '೭' to '7', '೮' to '8', '೯' to '9',
        // Malayalam
        '൦' to '0', '൧' to '1', '൨' to '2', '൩' to '3', '൪' to '4',
        '൫' to '5', '൬' to '6', '൭' to '7', '൮' to '8', '൯' to '9',
        // Tamil
        '௦' to '0', '௧' to '1', '௨' to '2', '௩' to '3', '௪' to '4',
        '௫' to '5', '௬' to '6', '௭' to '7', '௮' to '8', '௯' to '9'
    )

    // Language-specific digit-to-word tables (0..9)
    private val DIGITS_MAP = mapOf(
        IndicLanguage.ENGLISH to arrayOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"),
        IndicLanguage.HINDI to arrayOf("शून्य", "एक", "दो", "तीन", "चार", "पांच", "छह", "सात", "आठ", "नौ"),
        IndicLanguage.MARATHI to arrayOf("शून्य", "एक", "दोन", "तीन", "चार", "पाच", "सहा", "सात", "आठ", "नऊ"),
        IndicLanguage.GUJARATI to arrayOf("શૂન્ય", "એક", "બે", "ત્રણ", "ચાર", "પાંચ", "છ", "સાત", "આઠ", "નવ"),
        IndicLanguage.BENGALI to arrayOf("শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়"),
        IndicLanguage.TELUGU to arrayOf("సున్నా", "ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది"),
        IndicLanguage.KANNADA to arrayOf("ಶೂನ್ಯ", "ಒಂದು", "ಎರಡು", "ಮೂರು", "ನಾಲ್ಕು", "ಐದು", "ಆರು", "ಏಳು", "ಎಂಟು", "ಒಂಬತ್ತು"),
        IndicLanguage.MALAYALAM to arrayOf("പൂജ്യം", "ഒന്ന്", "രണ്ട്", "മൂന്ന്", "നാല്", "അഞ്ച്", "ആറ്", "ഏഴ്", "എട്ട്", "ഒമ്പത്"),
        IndicLanguage.ODIA to arrayOf("ଶୂନ", "ଏକ", "ଦୁଇ", "ତିନି", "ଚାରି", "ପାଞ୍ଚ", "ଛଅ", "ସାତ", "ଆଠ", "ନଅ"),
        IndicLanguage.TAMIL to arrayOf("பூஜ்ஜியம்", "ஒன்று", "இரண்டு", "மூன்று", "நான்கு", "ஐந்து", "ஆறு", "ஏழு", "எட்டு", "ஒன்பது")
    )

    // Decimal point word in each language
    private val DECIMAL_WORD_MAP = mapOf(
        IndicLanguage.ENGLISH to "point",
        IndicLanguage.HINDI to "दशमलव",
        IndicLanguage.MARATHI to "दशांश",
        IndicLanguage.GUJARATI to "દશાંશ",
        IndicLanguage.BENGALI to "দশমিক",
        IndicLanguage.TELUGU to "పాయింట్",
        IndicLanguage.KANNADA to "ಪಾಯಿಂಟ್",
        IndicLanguage.MALAYALAM to "പോയിന്റ്",
        IndicLanguage.ODIA to "ଦଶମିକ",
        IndicLanguage.TAMIL to "புள்ளி"
    )

    // Percentage word in each language
    private val PERCENT_WORD_MAP = mapOf(
        IndicLanguage.ENGLISH to "percent",
        IndicLanguage.HINDI to "प्रतिशत",
        IndicLanguage.MARATHI to "टक्के",
        IndicLanguage.GUJARATI to "ટકા",
        IndicLanguage.BENGALI to "শতাংশ",
        IndicLanguage.TELUGU to "శాతం",
        IndicLanguage.KANNADA to "ಪ್ರತಿಶತ",
        IndicLanguage.MALAYALAM to "ശതമാനം",
        IndicLanguage.ODIA to "ପ୍ରତିଶତ",
        IndicLanguage.TAMIL to "சதவீதம்"
    )

    // Common operational terms and acronyms mapped to native phonetic words
    private val TACTICAL_TERMS_MAP = mapOf(
        IndicLanguage.HINDI to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "आई तन्त्रा",
            Pattern.compile("(?i)\\bsos\\b") to "एस ओ एस",
            Pattern.compile("(?i)\\bptt\\b") to "पी टी टी",
            Pattern.compile("(?i)\\bgps\\b") to "जी पी एस",
            Pattern.compile("(?i)\\bmedevac\\b") to "मेडिवैक",
            Pattern.compile("(?i)\\bmayday\\b") to "मेडे",
            Pattern.compile("(?i)\\bsitrep\\b") to "सिटरेप",
            Pattern.compile("(?i)\\bradio\\b") to "रेडियो",
            Pattern.compile("(?i)\\bbattery\\b") to "बैटरी",
            Pattern.compile("(?i)\\bsignal\\b") to "सिग्नल",
            Pattern.compile("(?i)\\bchannel\\b") to "चैनल",
            Pattern.compile("(?i)\\bemergency\\b") to "आपातकाल",
            Pattern.compile("(?i)\\bcommand\\s+alpha\\b") to "कमांड अल्फा",
            Pattern.compile("(?i)\\bsquad\\s+bravo\\b") to "स्क्वाड ब्रावो",
            Pattern.compile("(?i)\\brecon\\s+charlie\\b") to "रेकॉन चार्ली",
            Pattern.compile("(?i)\\brelay\\s+delta\\b") to "रिले डेल्टा",
            Pattern.compile("(?i)\\beagle\\s+one\\b") to "ईगल वन",
            Pattern.compile("(?i)\\bhawk\\s+leader\\b") to "हॉक लीडर",
            Pattern.compile("(?i)\\bway\\s*point\\b") to "वेपॉइंट",
            Pattern.compile("(?i)\\bsector\\b") to "सेक्टर"
        ),
        IndicLanguage.MARATHI to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "आई तन्त्रा",
            Pattern.compile("(?i)\\bsos\\b") to "एस ओ एस",
            Pattern.compile("(?i)\\bptt\\b") to "पी टी टी",
            Pattern.compile("(?i)\\bgps\\b") to "जी पी एस",
            Pattern.compile("(?i)\\bmedevac\\b") to "मेडिव्हॅक",
            Pattern.compile("(?i)\\bmayday\\b") to "मेडे",
            Pattern.compile("(?i)\\bsitrep\\b") to "सिटरेप",
            Pattern.compile("(?i)\\bradio\\b") to "रेडिओ",
            Pattern.compile("(?i)\\bbattery\\b") to "बॅटरी",
            Pattern.compile("(?i)\\bsignal\\b") to "सिग्नल",
            Pattern.compile("(?i)\\bchannel\\b") to "चॅनेल",
            Pattern.compile("(?i)\\bcommand\\s+alpha\\b") to "कमांड अल्फा",
            Pattern.compile("(?i)\\bsquad\\s+bravo\\b") to "स्क्वाड ब्रावो",
            Pattern.compile("(?i)\\brecon\\s+charlie\\b") to "रेकॉन चार्ली",
            Pattern.compile("(?i)\\brelay\\s+delta\\b") to "रिले डेल्टा",
            Pattern.compile("(?i)\\bway\\s*point\\b") to "वेपॉईंट",
            Pattern.compile("(?i)\\bsector\\b") to "सेक्टर"
        ),
        IndicLanguage.GUJARATI to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "આઈ તન્ત્રા",
            Pattern.compile("(?i)\\bsos\\b") to "એસ ઓ એસ",
            Pattern.compile("(?i)\\bptt\\b") to "પી ટી ટી",
            Pattern.compile("(?i)\\bgps\\b") to "જી પી એસ",
            Pattern.compile("(?i)\\bmedevac\\b") to "મેડીવેક",
            Pattern.compile("(?i)\\bmayday\\b") to "મેડે",
            Pattern.compile("(?i)\\bsitrep\\b") to "સિટરેપ",
            Pattern.compile("(?i)\\bradio\\b") to "રેડિયો",
            Pattern.compile("(?i)\\bbattery\\b") to "બેટરી",
            Pattern.compile("(?i)\\bsignal\\b") to "સિગ્નલ",
            Pattern.compile("(?i)\\bchannel\\b") to "ચેનલ",
            Pattern.compile("(?i)\\bcommand\\s+alpha\\b") to "કમાન્ડ આલ્ફા",
            Pattern.compile("(?i)\\bsquad\\s+bravo\\b") to "સ્ક્વોડ બ્રાવો",
            Pattern.compile("(?i)\\bway\\s*point\\b") to "વેપૉઇન્ટ",
            Pattern.compile("(?i)\\bsector\\b") to "સેક્ટર"
        ),
        IndicLanguage.BENGALI to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "আই তন্ত্র",
            Pattern.compile("(?i)\\bsos\\b") to "এস ও এস",
            Pattern.compile("(?i)\\bptt\\b") to "পি টি টি",
            Pattern.compile("(?i)\\bgps\\b") to "জি পি এস",
            Pattern.compile("(?i)\\bmedevac\\b") to "মেডিভ্যাক",
            Pattern.compile("(?i)\\bmayday\\b") to "মেডে",
            Pattern.compile("(?i)\\bsitrep\\b") to "সিটরেপ",
            Pattern.compile("(?i)\\bradio\\b") to "রেডিও",
            Pattern.compile("(?i)\\bbattery\\b") to "ব্যাটারি",
            Pattern.compile("(?i)\\bsignal\\b") to "সিগন্যাল",
            Pattern.compile("(?i)\\bchannel\\b") to "চ্যানেল",
            Pattern.compile("(?i)\\bcommand\\s+alpha\\b") to "কমান্ড আলফা",
            Pattern.compile("(?i)\\bsquad\\s+bravo\\b") to "স্কোয়াড ব্রাভো",
            Pattern.compile("(?i)\\bway\\s*point\\b") to "ওয়েপয়েন্ট",
            Pattern.compile("(?i)\\bsector\\b") to "সেক্টর"
        ),
        IndicLanguage.TELUGU to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "ఐ తంత్ర",
            Pattern.compile("(?i)\\bsos\\b") to "ఎస్ ఓ ఎస్",
            Pattern.compile("(?i)\\bptt\\b") to "పి టి టి",
            Pattern.compile("(?i)\\bgps\\b") to "జి పి ఎస్",
            Pattern.compile("(?i)\\bmedevac\\b") to "మెడివాక్",
            Pattern.compile("(?i)\\bmayday\\b") to "మేడే",
            Pattern.compile("(?i)\\bsitrep\\b") to "సిట్రప్",
            Pattern.compile("(?i)\\bradio\\b") to "రేడియో",
            Pattern.compile("(?i)\\bbattery\\b") to "బ్యాటరీ",
            Pattern.compile("(?i)\\bsignal\\b") to "సిగ్నల్",
            Pattern.compile("(?i)\\bchannel\\b") to "ఛానెల్",
            Pattern.compile("(?i)\\bcommand\\s+alpha\\b") to "కమాండ్ ఆల్ఫా",
            Pattern.compile("(?i)\\bsquad\\s+bravo\\b") to "స్క్వాడ్ బ్రావో",
            Pattern.compile("(?i)\\bway\\s*point\\b") to "వేపాయింట్",
            Pattern.compile("(?i)\\bsector\\b") to "సెక్టార్"
        ),
        IndicLanguage.KANNADA to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "ಐ ತಂತ್ರ",
            Pattern.compile("(?i)\\bsos\\b") to "ಎಸ್ ಓ ಎಸ್",
            Pattern.compile("(?i)\\bptt\\b") to "ಪಿ ಟಿ ಟಿ",
            Pattern.compile("(?i)\\bgps\\b") to "ಜಿ ಪಿ ಎಸ್",
            Pattern.compile("(?i)\\bmedevac\\b") to "ಮೆಡಿವ್ಯಾಕ್",
            Pattern.compile("(?i)\\bmayday\\b") to "ಮೇಡೇ",
            Pattern.compile("(?i)\\bsitrep\\b") to "ಸಿಟ್ರೆಪ್",
            Pattern.compile("(?i)\\bradio\\b") to "ರೇಡಿಯೋ",
            Pattern.compile("(?i)\\bbattery\\b") to "ಬ್ಯಾಟರಿ",
            Pattern.compile("(?i)\\bsignal\\b") to "ಸಿಗ್ನಲ್",
            Pattern.compile("(?i)\\bchannel\\b") to "ಚಾನೆಲ್",
            Pattern.compile("(?i)\\bcommand\\s+alpha\\b") to "ಕಮಾಂಡ್ ಆಲ್ಫಾ",
            Pattern.compile("(?i)\\bsquad\\s+bravo\\b") to "ಸ್ಕ್ವಾಡ್ ಬ್ರಾವೋ",
            Pattern.compile("(?i)\\bway\\s*point\\b") to "ವೇಪಾಯಿಂಟ್",
            Pattern.compile("(?i)\\bsector\\b") to "ಸೆಕ್ಟರ್"
        ),
        IndicLanguage.MALAYALAM to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "ഐ തന്ത്ര",
            Pattern.compile("(?i)\\bsos\\b") to "എസ് ഒ എസ്",
            Pattern.compile("(?i)\\bptt\\b") to "പി ടി ടി",
            Pattern.compile("(?i)\\bgps\\b") to "ജി പി എസ്",
            Pattern.compile("(?i)\\bmedevac\\b") to "മെഡെവാക്",
            Pattern.compile("(?i)\\bmayday\\b") to "മേഡേ",
            Pattern.compile("(?i)\\bsitrep\\b") to "സിട്രെപ്പ്",
            Pattern.compile("(?i)\\bradio\\b") to "റേഡിയോ",
            Pattern.compile("(?i)\\bbattery\\b") to "ബാറ്ററി",
            Pattern.compile("(?i)\\bsignal\\b") to "സിഗ്നൽ",
            Pattern.compile("(?i)\\bchannel\\b") to "ചാനൽ",
            Pattern.compile("(?i)\\bcommand\\s+alpha\\b") to "കമാൻഡ് ആൽഫ",
            Pattern.compile("(?i)\\bsquad\\s+bravo\\b") to "സ്ക്വാഡ് ബ്രാവോ",
            Pattern.compile("(?i)\\bway\\s*point\\b") to "വേപോയിന്റ്",
            Pattern.compile("(?i)\\bsector\\b") to "സെക്ടർ"
        ),
        IndicLanguage.ODIA to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "ଆଇ ତନ୍ତ୍ର",
            Pattern.compile("(?i)\\bsos\\b") to "ଏସ୍ ଓ ଏସ୍",
            Pattern.compile("(?i)\\bptt\\b") to "ପି ଟି ଟି",
            Pattern.compile("(?i)\\bgps\\b") to "ଜି ପି ଏସ୍",
            Pattern.compile("(?i)\\bmedevac\\b") to "ମେଡିଭାକ୍",
            Pattern.compile("(?i)\\bmayday\\b") to "ମେଡେ",
            Pattern.compile("(?i)\\bsitrep\\b") to "ସିଟ୍ରେପ୍",
            Pattern.compile("(?i)\\bradio\\b") to "ରେଡିଓ",
            Pattern.compile("(?i)\\bbattery\\b") to "ବ୍ୟାଟେରୀ",
            Pattern.compile("(?i)\\bsignal\\b") to "ସିଗ୍ନାଲ୍",
            Pattern.compile("(?i)\\bchannel\\b") to "ଚ୍ୟାନେଲ୍",
            Pattern.compile("(?i)\\bcommand\\s+alpha\\b") to "କମାଣ୍ଡ ଆଲଫା",
            Pattern.compile("(?i)\\bsquad\\s+bravo\\b") to "ସ୍କ୍ୱାଡ୍ ବ୍ରାଭୋ",
            Pattern.compile("(?i)\\bway\\s*point\\b") to "ୱେପଏଣ୍ଟ",
            Pattern.compile("(?i)\\bsector\\b") to "ସେକ୍ଟର"
        ),
        IndicLanguage.ENGLISH to listOf(
            Pattern.compile("(?i)\\bi-?tantra\\b") to "eye tantra",
            Pattern.compile("(?i)\\bsos\\b") to "S O S",
            Pattern.compile("(?i)\\bptt\\b") to "P T T",
            Pattern.compile("(?i)\\bgps\\b") to "G P S",
            Pattern.compile("(?i)\\bmedevac\\b") to "medevac",
            Pattern.compile("(?i)\\bmayday\\b") to "MAYDAY",
            Pattern.compile("(?i)\\bsitrep\\b") to "sitrep"
        )
    )

    /**
     * Process input text for neural TTS synthesis across any supported language.
     */
    fun process(rawText: String, language: IndicLanguage): String {
        if (rawText.isBlank()) return ""

        // For Tamil, invoke the specialized TamilTextPreprocessor (handles Aytham, Tamil suffixes, etc.)
        if (language == IndicLanguage.TAMIL) {
            return TamilTextPreprocessor.process(rawText)
        }

        // 1. Canonical Unicode NFC Normalization
        var text = Normalizer.normalize(rawText, Normalizer.Form.NFC)

        // 2. Strip Zero-Width non-joiners & joiners that break eSpeak phonemizers
        text = text.replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "")

        // 3. Map any native script numeral characters to standard ASCII digits
        if (text.any { it in NATIVE_NUMERALS_MAP }) {
            val sb = StringBuilder(text.length)
            for (c in text) {
                sb.append(NATIVE_NUMERALS_MAP[c] ?: c)
            }
            text = sb.toString()
        }

        // 4. Operational & Tactical term transliteration
        val tacticalRules = TACTICAL_TERMS_MAP[language] ?: TACTICAL_TERMS_MAP[IndicLanguage.HINDI] ?: emptyList()
        for ((pattern, replacement) in tacticalRules) {
            text = pattern.matcher(text).replaceAll(replacement)
        }

        // 5. Expand percentages: e.g. "40%" -> "40 percent"
        val percentWord = PERCENT_WORD_MAP[language] ?: "percent"
        text = text.replace(Regex("(\\d+)\\s*%"), "$1 $percentWord")

        // 6. Expand decimals: e.g. "13.1" -> "13 point 1"
        val decimalWord = DECIMAL_WORD_MAP[language] ?: "point"
        val decimalPattern = Pattern.compile("(\\d+)\\.(\\d+)")
        val decMatcher = decimalPattern.matcher(text)
        val decSb = StringBuffer()
        while (decMatcher.find()) {
            val whole = decMatcher.group(1) ?: ""
            val frac = decMatcher.group(2) ?: ""
            val wholeWords = expandNumber(whole, language)
            val fracWords = frac.map { getDigitWord(it, language) }.joinToString(" ")
            decMatcher.appendReplacement(decSb, "$wholeWords $decimalWord $fracWords")
        }
        decMatcher.appendTail(decSb)
        text = decSb.toString()

        // 7. Expand whole numbers
        val numPattern = Pattern.compile("\\b(\\d+)\\b")
        val numMatcher = numPattern.matcher(text)
        val numSb = StringBuffer()
        while (numMatcher.find()) {
            val numStr = numMatcher.group(1) ?: ""
            val expanded = expandNumber(numStr, language)
            numMatcher.appendReplacement(numSb, expanded)
        }
        numMatcher.appendTail(numSb)
        text = numSb.toString()

        // 8. Punctuation normalization: replace punctuation with whitespace for micro-pauses
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                ',', ';', ':', '-', '—', '–', '\"', '(', ')', '[', ']', '{', '}',
                '<', '>', '/', '\\', '|', '@', '#', '$', '^', '&', '*', '+', '=',
                '~', '`', '!', '?', '_', '।' -> sb.append(' ')
                '.' -> {
                    // Retain period at end of sentence, otherwise space
                    sb.append(' ')
                }
                else -> sb.append(c)
            }
        }

        // 9. Collapse multiple whitespace and trim
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getDigitWord(digitChar: Char, language: IndicLanguage): String {
        val idx = digitChar - '0'
        val table = DIGITS_MAP[language] ?: DIGITS_MAP[IndicLanguage.ENGLISH]!!
        return if (idx in 0..9) table[idx] else digitChar.toString()
    }

    private fun expandNumber(numStr: String, language: IndicLanguage): String {
        val numVal = numStr.toLongOrNull()
        if (numVal == null || numVal !in 0..99) {
            // For numbers >= 100 or phone/code numbers, spell out digit by digit
            return numStr.map { getDigitWord(it, language) }.joinToString(" ")
        }

        val n = numVal.toInt()
        return when (language) {
            IndicLanguage.HINDI -> HINDI_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            IndicLanguage.MARATHI -> MARATHI_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            IndicLanguage.GUJARATI -> GUJARATI_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            IndicLanguage.BENGALI -> BENGALI_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            IndicLanguage.TELUGU -> TELUGU_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            IndicLanguage.KANNADA -> KANNADA_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            IndicLanguage.MALAYALAM -> MALAYALAM_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            IndicLanguage.ODIA -> ODIA_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            IndicLanguage.ENGLISH -> ENGLISH_0_TO_99.getOrNull(n) ?: numStr.map { getDigitWord(it, language) }.joinToString(" ")
            else -> numStr.map { getDigitWord(it, language) }.joinToString(" ")
        }
    }

    // Number word tables 0..99
    private val ENGLISH_0_TO_99 = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
        "twenty", "twenty one", "twenty two", "twenty three", "twenty four", "twenty five", "twenty six", "twenty seven", "twenty eight", "twenty nine",
        "thirty", "thirty one", "thirty two", "thirty three", "thirty four", "thirty five", "thirty six", "thirty seven", "thirty eight", "thirty nine",
        "forty", "forty one", "forty two", "forty three", "forty four", "forty five", "forty six", "forty seven", "forty eight", "forty nine",
        "fifty", "fifty one", "fifty two", "fifty three", "fifty four", "fifty five", "fifty six", "fifty seven", "fifty eight", "fifty nine",
        "sixty", "sixty one", "sixty two", "sixty three", "sixty four", "sixty five", "sixty six", "sixty seven", "sixty eight", "sixty nine",
        "seventy", "seventy one", "seventy two", "seventy three", "seventy four", "seventy five", "seventy six", "seventy seven", "seventy eight", "seventy nine",
        "eighty", "eighty one", "eighty two", "eighty three", "eighty four", "eighty five", "eighty six", "eighty seven", "eighty eight", "eighty nine",
        "ninety", "ninety one", "ninety two", "ninety three", "ninety four", "ninety five", "ninety six", "ninety seven", "ninety eight", "ninety nine"
    )

    private val HINDI_0_TO_99 = arrayOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पांच", "छह", "सात", "आठ", "नौ",
        "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
        "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस",
        "तीस", "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस",
        "चालीस", "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास",
        "पचास", "इक्यावन", "बावन", "तिरेपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
        "साठ", "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सरसठ", "अड़सठ", "उनहत्तर",
        "सत्तर", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्नासी",
        "अस्सी", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी",
        "नब्बे", "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पंचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे"
    )

    private val MARATHI_0_TO_99 = arrayOf(
        "शून्य", "एक", "दोन", "तीन", "चार", "पाच", "सहा", "सात", "आठ", "नऊ",
        "दहा", "अकरा", "बारा", "तेरा", "चौदा", "पंधरा", "सोळा", "सतरा", "अठरा", "एकोणीस",
        "वीस", "एकवीस", "बावीस", "तेवीस", "चोवीस", "पंचवीस", "सव्वीस", "सत्तावीस", "अठ्ठावीस", "एकोणतीस",
        "तीस", "एकतीस", "बत्तीस", "तेहेतीस", "चौतीस", "पस्तीस", "छत्तीस", "सदतीस", "अडतीस", "एकोणचाळीस",
        "चाळीस", "एक्केचाळीस", "बेचाळीस", "त्रेचाळीस", "चव्वेचाळीस", "पंचेचाळीस", "शेहेचाळीस", "सत्तेचाळीस", "अठ्ठेचाळीस", "एकोणपन्नास",
        "पन्नास", "एक्कावन्न", "बावन्न", "त्रेपन्न", "चोपन्न", "पंचावन्न", "छप्पन्न", "सत्तावन्न", "अठ्ठावन्न", "एकोणसाठ",
        "साठ", "एकसष्ठ", "बासष्ठ", "त्रेसष्ठ", "चौसष्ठ", "पासष्ठ", "सहासष्ठ", "सदुसष्ठ", "अडुसष्ठ", "एकोणसत्तर",
        "सत्तर", "एकाहत्तर", "बाहत्तर", "त्र्याहत्तर", "चौर्‍याहत्तर", "पंच्याहत्तर", "शहात्तर", "सत्त्याहत्तर", "अठ्ठ्याहत्तर", "एकोणऐंशी",
        "ऐंशी", "एक्याऐंशी", "ब्याऐंशी", "त्र्याऐंशी", "चौऱ्याऐंशी", "पंच्याऐंशी", "शहाऐंशी", "सत्त्याऐंशी", "अठ्ठ्याऐंशी", "एकोणनव्वद",
        "नव्वद", "एक्याण्णव", "ब्याण्णव", "त्र्याण्णव", "चौऱ्याण्णव", "पंच्याण्णव", "शहाण्णव", "सत्त्याण्णव", "अठ्ठ्याण्णव", "नव्याण्णव"
    )

    private val GUJARATI_0_TO_99 = arrayOf(
        "શૂન્ય", "એક", "બે", "ત્રણ", "ચાર", "પાંચ", "છ", "સાત", "આઠ", "નવ",
        "દસ", "અગિયાર", "બાર", "તેર", "ચૌદ", "પંદર", "સોળ", "સત્તર", "અઢાર", "ઓગણીસ",
        "વીસ", "એકવીસ", "બાવીસ", "તેવીસ", "ચોવીસ", "પચ્ચીસ", "છવીસ", "સત્તાવીસ", "અઠ્ઠાવીસ", "ઓગણત્રીસ",
        "ત્રીસ", "એકત્રીસ", "બત્રીસ", "તેત્રીસ", "ચોત્રીસ", "પાંત્રીસ", "છત્રીસ", "સાડત્રીસ", "આડત્રીસ", "ઓગણચાલીસ",
        "ચાલીસ", "એકતાલીસ", "બેતાલીસ", "તેતાલીસ", "ચુંમાલીસ", "પિસ્તાલીસ", "છેતાલીસ", "સુડતાલીસ", "અડતાલીસ", "ઓગણપચાસ",
        "પચાસ", "એકાવન", "બાવન", "ત્રેપન", "ચોપન", "પંચાવન", "છપ્પન", "સત્તાવન", "અઠ્ઠાવન", "ઓગણસાઠ",
        "સાઠ", "એકસઠ", "બાસઠ", "ત્રેસઠ", "ચોસઠ", "પાંસઠ", "છાસઠ", "સડસઠ", "અડસઠ", "અગણોસિત્તેર",
        "સિત્તેર", "એકોતેર", "બોતેર", "તોતેર", "ચોતેર", "પંચોતેર", "છોતેર", "સિત્યોતેર", "ઇઠ્યોતેર", "ઓગણએંસી",
        "એંસી", "એક્યાસી", "બ્યાસી", "ત્યાંસી", "ચોર્યાસી", "પંચાસી", "છ્યાસી", "સિત્યાસી", "ઇઠ્યાસી", "નેવ્યાસી",
        "નેવું", "એકાણું", "બાણું", "ત્રાણું", "ચોરાણું", "પંચાણું", "છન્નું", "સત્તાણું", "અઠ્ઠાણું", "નવ્વાણું"
    )

    private val BENGALI_0_TO_99 = arrayOf(
        "শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়",
        "দশ", "এগারো", "বারো", "তেরো", "চৌদ্দ", "পনেরো", "ষোলো", "সতেরো", "আঠারো", "ঊনিশ",
        "কুড়ি", "একুশ", "বাইশ", "তেইশ", "চব্বিশ", "পঁচিশ", "ছাব্বিশ", "সাতাশ", "আটাশ", "ঊনত্রিশ",
        "ত্রিশ", "একত্রিশ", "বত্রিশ", "তেত্রিশ", "চৌত্রিশ", "পঁয়ত্রিশ", "ছত্রিশ", "সাঁইত্রিশ", "আটত্রিশ", "ঊনচল্লিশ",
        "চল্লিশ", "একচল্লিশ", "বিয়াল্লিশ", "তেতাল্লিশ", "চুয়াল্লিশ", "পঁয়তাল্লিশ", "ছেচল্লিশ", "সাতচল্লিশ", "আটচল্লিশ", "ঊনপঞ্চাশ",
        "পঞ্চাশ", "একান্ন", "বায়ান্ন", "তিপ্পান্ন", "চুয়ান্ন", "পঞ্চান্ন", "ছাপ্পান্ন", "সাতান্ন", "আটান্ন", "ঊনষাট",
        "ষাট", "একষট্টি", "বাষট্টি", "তেষট্টি", "চৌষট্টি", "পঁয়ষট্টি", "ছেষট্টি", "সাতষট্টি", "আটষট্টি", "ঊনসত্তর",
        "সত্তর", "একাত্তর", "বাহাত্তর", "তিয়াত্তর", "চুয়াত্তর", "পঁচাত্তর", "ছিয়াত্তর", "সাতাত্তর", "আটাত্তর", "ঊনআশি",
        "আশি", "একাশি", "বিরাশি", "তিরাশি", "চুরাশি", "পঁচাশি", "ছিয়াশি", "সাতাশি", "অষ্টআশি", "ঊননব্বই",
        "নব্বই", "একানব্বই", "বিরানব্বই", "তিরানব্বই", "চুরানব্বই", "পঁচানব্বই", "ছিয়ানব্বই", "সাতানব্বই", "আটানব্বই", "নিরানব্বই"
    )

    private val TELUGU_0_TO_99 = arrayOf(
        "సున్నా", "ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది",
        "పది", "పదకొండు", "పన్నెండు", "పదమూడు", "పద్నాలుగు", "పదిహేను", "పదహారు", "పదిహేడు", "పద్దెనిమిది", "పంతొమ్మిది",
        "ఇరవై", "ఇరవై ఒకటి", "ఇరవై రెండు", "ఇరవై మూడు", "ఇరవై నాలుగు", "ఇరవై ఐదు", "ఇరవై ఆరు", "ఇరవై ఏడు", "ఇరవై ఎనిమిది", "ఇరవై తొమ్మిది",
        "ముప్పై", "ముప్పై ఒకటి", "ముప్పై రెండు", "ముప్పై మూడు", "ముప్పై నాలుగు", "ముప్పై ఐదు", "ముప్పై ఆరు", "ముప్పై ఏడు", "ముప్పై ఎనిమిది", "ముప్పై తొమ్మిది",
        "నలభై", "నలభై ఒకటి", "నలభై రెండు", "నలభై మూడు", "నలభై నాలుగు", "నలభై ఐదు", "నలభై ఆరు", "నలభై ఏడు", "నలభై ఎనిమిది", "నలభై తొమ్మిది",
        "యాభై", "యాభై ఒకటి", "యాభై రెండు", "యాభై మూడు", "యాభై నాలుగు", "యాభై ఐదు", "యాభై ఆరు", "యాభై ఏడు", "యాభై ఎనిమిది", "యాభై తొమ్మిది",
        "అరవై", "అరవై ఒకటి", "అరవై రెండు", "అరవై మూడు", "అరవై నాలుగు", "అరవై ఐదు", "అరవై ఆరు", "అరవై ఏడు", "అరవై ఎనిమిది", "అరవై తొమ్మిది",
        "డెబ్బై", "డెబ్బై ఒకటి", "డెబ్బై రెండు", "డెబ్బై మూడు", "డెబ్బై నాలుగు", "డెబ్బై ఐదు", "డెబ్బై ఆరు", "డెబ్బై ఏడు", "డెబ్బై ఎనిమిది", "డెబ్బై తొమ్మిది",
        "ఎనభై", "ఎనభై ఒకటి", "ఎనభై రెండు", "ఎనభై మూడు", "ఎనభై నాలుగు", "ఎనభై ఐదు", "ఎనభై ఆరు", "ఎనభై ఏడు", "ఎనభై ఎనిమిది", "ఎనభై తొమ్మిది",
        "తొంభై", "తొంభై ఒకటి", "తొంభై రెండు", "తొంభై మూడు", "తొంభై నాలుగు", "తొంభై ఐదు", "తొంభై ఆరు", "తొంభై ఏడు", "తొంభై ఎనిమిది", "తొంభై తొమ్మిది"
    )

    private val KANNADA_0_TO_99 = arrayOf(
        "ಶೂನ್ಯ", "ಒಂದು", "ಎರಡು", "ಮೂರು", "ನಾಲ್ಕು", "ಐದು", "ಆರು", "ಏಳು", "ಎಂಟು", "ಒಂಬತ್ತು",
        "ಹತ್ತು", "ಹನ್ನೊಂದು", "ಹನ್ನೆರಡು", "ಹದಿಮೂರು", "ಹದಿನಾಲ್ಕು", "ಹದಿನೈದು", "ಹದಿನಾರು", "ಹದಿನೇಳು", "ಹದಿನೆಂಟು", "ಹತ್ತೊಂಬತ್ತು",
        "ಇಪ್ಪತ್ತು", "ಇಪ್ಪತ್ತೊಂದು", "ಇಪ್ಪತ್ತೆರಡು", "ಇಪ್ಪತ್ಮೂರು", "ಇಪ್ಪತ್ನಾಲ್ಕು", "ಇಪ್ಪತ್ತೈದು", "ಇಪ್ಪತ್ತಾರು", "ಇಪ್ಪತ್ತೇಳು", "ಇಪ್ಪತ್ತೆಂಟು", "ಇಪ್ಪತ್ತೊಂಬತ್ತು",
        "ಮೂವತ್ತು", "ಮೂವತ್ತೊಂದು", "ಮೂವತ್ತೆರಡು", "ಮೂವತ್ಮೂರು", "ಮೂವತ್ನಾಲ್ಕು", "ಮೂವತ್ತೈದು", "ಮೂವತ್ತಾರು", "ಮೂವತ್ತೇಳು", "ಮೂವತ್ತೆಂಟು", "ಮೂವತ್ತೊಂಬತ್ತು",
        "ನಲವತ್ತು", "ನಲವತ್ತೊಂದು", "ನಲವತ್ತೆರಡು", "ನಲವತ್ಮೂರು", "ನಲವತ್ನಾಲ್ಕು", "ನಲವತ್ತೈದು", "ನಲವತ್ತಾರು", "ನಲವತ್ತೇಳು", "ನಲವತ್ತೆಂಟು", "ನಲವತ್ತೊಂಬತ್ತು",
        "ಐವತ್ತು", "ಐವತ್ತೊಂದು", "ಐವತ್ತೆರಡು", "ಐವತ್ಮೂರು", "ಐವತ್ನಾಲ್ಕು", "ಐವತ್ತೈದು", "ಐವತ್ತಾರು", "ಐವತ್ತೇಳು", "ಐವತ್ತೆಂಟು", "ಐವತ್ತೊಂಬತ್ತು",
        "ಅರವತ್ತು", "ಅರವತ್ತೊಂದು", "ಅರವತ್ತೆರಡು", "ಅರವತ್ಮೂರು", "ಅರವತ್ನಾಲ್ಕು", "ಅರವತ್ತೈದು", "ಅರವತ್ತಾರು", "ಅರವತ್ತೇಳು", "ಅರವತ್ತೆಂಟು", "ಅರವತ್ತೊಂಬತ್ತು",
        "ಎಪ್ಪತ್ತು", "ಎಪ್ಪತ್ತೊಂದು", "ಎಪ್ಪತ್ತೆರಡು", "ಎಪ್ಪತ್ಮೂರು", "ಎಪ್ಪತ್ನಾಲ್ಕು", "ಎಪ್ಪತ್ತೈದು", "ಎಪ್ಪತ್ತಾರು", "ಎಪ್ಪತ್ತೇಳು", "ಎಪ್ಪತ್ತೆಂಟು", "ಎಪ್ಪತ್ತೊಂಬತ್ತು",
        "ಎಂಬತ್ತು", "ಎಂಬತ್ತೊಂದು", "ಎಂಬತ್ತೆರಡು", "ಎಂಬತ್ಮೂರು", "ಎಂಬತ್ನಾಲ್ಕು", "ಎಂಬತ್ತೈದು", "ಎಂಬತ್ತಾರು", "ಎಂಬತ್ತೇಳು", "ಎಂಬತ್ತೆಂಟು", "ಎಂಬತ್ತೊಂಬತ್ತು",
        "ತೊಂಬತ್ತು", "ತೊಂಬತ್ತೊಂದು", "ತೊಂಬತ್ತೆರಡು", "ತೊಂಬತ್ಮೂರು", "ತೊಂಬತ್ನಾಲ್ಕು", "ತೊಂಬತ್ತೈದು", "ತೊಂಬತ್ತಾರು", "ತೊಂಬತ್ತೇಳು", "ತೊಂಬತ್ತೆಂಟು", "ತೊಂಬತ್ತೊಂಬತ್ತು"
    )

    private val MALAYALAM_0_TO_99 = arrayOf(
        "പൂജ്യം", "ഒന്ന്", "രണ്ട്", "മൂന്ന്", "നാല്", "അഞ്ച്", "ആറ്", "ഏഴ്", "എട്ട്", "ഒമ്പത്",
        "പത്ത്", "പതിനൊന്ന്", "പന്ത്രണ്ട്", "പതിമൂന്ന്", "പതിനാല്", "പതിനഞ്ച്", "പതിനാറ്", "പതിനേഴ്", "പതിനെട്ട്", "പത്തൊമ്പത്",
        "ഇരുപത്", "ഇരുപത്തൊന്ന്", "ഇരുപത്തിരണ്ട്", "ഇരുപത്തിമൂന്ന്", "ഇരുപത്തിനാല്", "ഇരുപത്തിയഞ്ച്", "ഇരുപത്തിയാറ്", "ഇരുപത്തിയേഴ്", "ഇരുപത്തിയെട്ട്", "ഇരുപത്തൊമ്പത്",
        "മുപ്പത്", "മുപ്പത്തൊന്ന്", "മുപ്പത്തിരണ്ട്", "മുപ്പത്തിമൂന്ന്", "മുപ്പത്തിനാല്", "മുപ്പത്തിയഞ്ച്", "മുപ്പത്തിയാറ്", "മുപ്പത്തിയേഴ്", "മുപ്പത്തിയെട്ട്", "മുപ്പത്തൊമ്പത്",
        "നാൽപ്പത്", "നാൽപ്പത്തൊന്ന്", "നാൽപ്പത്തിരണ്ട്", "നാൽപ്പത്തിമൂന്ന്", "നാൽപ്പത്തിനാല്", "നാൽപ്പത്തിയഞ്ച്", "നാൽപ്പത്തിയാറ്", "നാൽപ്പത്തിയേഴ്", "നാൽപ്പത്തിയെട്ട്", "നാൽപ്പത്തൊമ്പത്",
        "അമ്പത്", "അമ്പത്തൊന്ന്", "അമ്പത്തിരണ്ട്", "അമ്പത്തിമൂന്ന്", "അമ്പത്തിനാല്", "അമ്പത്തിയഞ്ച്", "അമ്പത്തിയാറ്", "അമ്പത്തിയേഴ്", "അമ്പത്തിയെട്ട്", "അമ്പത്തൊമ്പത്",
        "അറുപത്", "അറുപത്തൊന്ന്", "അറുപത്തിരണ്ട്", "അറുപത്തിമൂന്ന്", "അറുപത്തിനാല്", "അറുപത്തിയഞ്ച്", "അറുപത്തിയാറ്", "അറുപത്തിയേഴ്", "അറുപത്തിയെട്ട്", "അറുപത്തൊമ്പത്",
        "എഴുപത്", "എഴുപത്തൊന്ന്", "എഴുപത്തിരണ്ട്", "എഴുപത്തിമൂന്ന്", "എഴുപത്തിനാല്", "എഴുപത്തിയഞ്ച്", "എഴുപത്തിയാറ്", "എഴുപത്തിയേഴ്", "എഴുപത്തിയെട്ട്", "എഴുപത്തൊമ്പത്",
        "എൺപത്", "എൺപത്തൊന്ന്", "എൺപത്തിരണ്ട്", "എൺപത്തിമൂന്ന്", "എൺപത്തിനാല്", "എൺപത്തിയഞ്ച്", "എൺപത്തിയാറ്", "എൺപത്തിയേഴ്", "എൺപത്തിയെട്ട്", "എൺപത്തൊമ്പത്",
        "തൊണ്ണൂറ്", "തൊണ്ണൂറ്റൊന്ന്", "തൊണ്ണൂറ്റിരണ്ട്", "തൊണ്ണൂറ്റിമൂന്ന്", "തൊണ്ണൂറ്റിനാല്", "തൊണ്ണൂറ്റിയഞ്ച്", "തൊണ്ണൂറ്റിയാറ്", "തൊണ്ണൂറ്റിയേഴ്", "തൊണ്ണൂറ്റിയെട്ട്", "തൊണ്ണൂറ്റിയൊമ്പത്"
    )

    private val ODIA_0_TO_99 = arrayOf(
        "ଶୂନ", "ଏକ", "ଦୁଇ", "ତିନି", "ଚାରି", "ପାଞ୍ଚ", "ଛଅ", "ସାତ", "ଆଠ", "ନଅ",
        "ଦଶ", "ଏଗାର", "ବାର", "ତେର", "ଚଉଦ", "ପନ୍ଦର", "ଷୋହଳ", "ସତର", "ଅଠର", "ଉଣାଇଶ",
        "କୋଡ଼ିଏ", "ଏକୋଇଶ", "ବାଇଶ", "ତେଇଶ", "ଚବିଶ", "ପଚିଶ", "ଛବିଶ", "ସତାଇଶ", "ଅଠାଇଶ", "ଅଣତିରିଶ",
        "ତିରିଶ", "ଏକତିରିଶ", "ବତିଶ", "ତେତିଶ", "ଚୌତିଶ", "ପଞ୍ଚତିରିଶ", "ଛତିଶ", "ସଂଇତିରିଶ", "ଅଠତିରିଶ", "ଅଣଚାଳିଶ",
        "ଚାଳିଶ", "ଏକଚାଳିଶ", "ବୟାଳିଶ", "ତେୟାଳିଶ", "ଚଉରାଳିଶ", "ପଞ୍ଚଚାଳିଶ", "ଛୟାଳିଶ", "ସତଚାଳିଶ", "ଅଠଚାଳିଶ", "ଅଣଚାଶ",
        "ପଚାଶ", "ଏକାବନ", "ବାଉନ", "ତେପନ", "ଚଉବନ", "ପଞ୍ଚାବନ", "ଛପନ", "ସତାବନ", "ଅଠାବନ", "ଅଣଷଠି",
        "ଷଠି", "ଏକଷଠି", "ବାଷଠି", "ତେଷଠି", "ଚଉଷଠି", "ପଞ୍ଚଷଠି", "ଛଅଷଠି", "ସତଷଠି", "ଅଠଷଠି", "ଅଣସତୁରି",
        "ସତୁରି", "ଏକସ୍ତରୀ", "ବାସ୍ତରୀ", "ତେସ୍ତରୀ", "ଚଉସ୍ତରୀ", "ପଞ୍ଚସ୍ତରୀ", "ଛଅସ୍ତରୀ", "ସତସ୍ତରୀ", "ଅଠସ୍ତରୀ", "ଅଣଅଶୀ",
        "ଅଶୀ", "ଏକାଅଶୀ", "ବୟାଅଶୀ", "ତେୟାଅଶୀ", "ଚଉରାଅଶୀ", "ପଞ୍ଚାଅଶୀ", "ଛୟାଅଶୀ", "ସତାଅଶୀ", "ଅଠାଅଶୀ", "ଅଣନବେ",
        "ନବେ", "ଏକାନବେ", "ବୟାନବେ", "ତେୟାନବେ", "ଚଉରାନବେ", "ପଞ୍ଚାନବେ", "ଛୟାନବେ", "ସତାନବେ", "ଅଠାନବେ", "ଅନେଶତ"
    )
}
