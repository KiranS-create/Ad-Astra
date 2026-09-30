package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage
import java.util.Locale

/**
 * Universal On-Device Indic Phonetic Transliterator.
 *
 * Deterministically maps Latin/Romanized phonetic transcriptions into authentic native scripts
 * across all 9 Indic languages (Hindi, Marathi, Tamil, Telugu, Bengali, Gujarati, Kannada,
 * Malayalam, Odia).
 *
 * Designed to guarantee that whenever an Indic language is selected in tactical ASR/STT,
 * acoustic phonetic approximations produced by models like Whisper-Tiny are transformed
 * directly into the appropriate native Brahmic script rather than displaying Romanized English text.
 */
object IndicPhoneticTransliterator {

    private data class ScriptProfile(
        val consonants: Map<String, String>,
        val matras: Map<String, String>,
        val vowels: Map<String, String>,
        val virama: String,
        val anusvara: String
    )

    // =========================================================================
    // 1. High-frequency canonical dictionary per language
    // =========================================================================

    private val HINDI_DICT = mapOf(
        "hum" to "हम", "ham" to "हम", "surakshit" to "सुरक्षित", "hain" to "हैं", "hai" to "है",
        "ho" to "हो", "aap" to "आप", "tum" to "तुम", "main" to "मैं", "mujhe" to "मुझे",
        "kya" to "क्या", "kyun" to "क्यों", "kaise" to "कैसे", "kahan" to "कहाँ", "kab" to "कब",
        "madad" to "मदद", "chahiye" to "चाहिए", "chahi" to "चाहिए", "pani" to "पानी", "paani" to "पानी",
        "doctor" to "डॉक्टर", "khatra" to "खतरा", "bachao" to "बचाओ", "aag" to "आग", "dhuan" to "धुआं",
        "khana" to "खाना", "bhojan" to "भोजन", "dawai" to "दवाई", "dawa" to "दवा", "ambulance" to "एम्बुलेंस",
        "namaste" to "नमस्ते", "namaskar" to "नमस्कार", "dhanyavad" to "धन्यवाद", "shukriya" to "शुक्रिया",
        "kripya" to "कृपया", "jaldi" to "जल्दी", "turant" to "तुरंत", "theek" to "ठीक", "sahi" to "सही",
        "nahin" to "नहीं", "nahi" to "नहीं", "haan" to "हाँ", "aur" to "और", "par" to "पर",
        "mein" to "में", "se" to "से", "ko" to "को", "ka" to "का", "ki" to "की", "ke" to "के",
        "ek" to "एक", "do" to "दो", "teen" to "तीन", "char" to "चार", "panch" to "पांच",
        "chhah" to "छह", "saat" to "सात", "aath" to "आठ", "nau" to "नौ", "das" to "दस",
        "hospital" to "अस्पताल", "raasta" to "रास्ता", "sena" to "सेना", "police" to "पुलिस",
        "baadh" to "बाढ़", "bhukamp" to "भूकंप", "malba" to "मलबा", "ghayal" to "घायल",
        "roshan" to "रोशन", "roshni" to "रोशनी", "battery" to "बैटरी", "radio" to "रेडियो"
    )

    private val MARATHI_DICT = mapOf(
        "aamhi" to "आम्ही", "amhi" to "आम्ही", "surakshit" to "सुरक्षित", "aahot" to "आहोत", "aahe" to "आहे",
        "aahet" to "आहेत", "madat" to "मदत", "havi" to "हवी", "have" to "हवे", "paani" to "पाणी",
        "pani" to "पाणी", "doctor" to "डॉक्टर", "dhoka" to "धोका", "vaachva" to "वाचवा", "aag" to "आग",
        "jevan" to "जेवण", "aushadh" to "औषध", "ambulance" to "रुग्णवाहिका", "namaskar" to "नमस्कार",
        "dhanyavaad" to "धन्यवाद", "krupaya" to "कृपया", "lavkar" to "लवकर", "thik" to "ठीक",
        "nahi" to "नाही", "ho" to "हो", "aani" to "आणि", "var" to "वर", "ek" to "एक",
        "don" to "दोन", "teen" to "तीन", "char" to "चार", "paach" to "पाच", "zakhmi" to "जखमी"
    )

    private val TAMIL_DICT = mapOf(
        "naangal" to "நாங்கள்", "surakshit" to "பாதுகாப்பாக", "paathukaappu" to "பாதுகாப்பு",
        "paadhukaappu" to "பாதுகாப்பு", "ullom" to "உள்ளோம்", "uthavi" to "உதவி", "vendum" to "வேண்டும்",
        "veena" to "வேண்டும்", "thanneer" to "தண்ணீர்" , "maruthuvar" to "மருத்துவர்", "doctor" to "மருத்துவர்",
        "vanakkam" to "வணக்கம்", "nandri" to "நன்றி", "kaapaatru" to "காப்பாற்று", "kaappattru" to "காப்பாற்றுங்கள்",
        "aabathu" to "ஆபத்து", "abathu" to "ஆபத்து", "thee" to "தீ", "saappaadu" to "சாப்பாடு",
        "marunthu" to "மருந்து", "ambulanse" to "ஆம்புலன்ஸ்", "seekiram" to "சீக்கிரம்", "onru" to "ஒன்று",
        "irandu" to "இரண்டு", "moondru" to "மூன்று", "naangu" to "நான்கு", "aainthu" to "ஐந்து",
        "kaayam" to "காயம்", "illai" to "இல்லை", "aam" to "ஆம்", "matrum" to "மற்றும்"
    )

    private val TELUGU_DICT = mapOf(
        "memu" to "మేము", "surakshitanga" to "సురక్షితంగా", "surakshitham" to "సురక్షితం", "unnamu" to "ఉన్నాము",
        "sahayam" to "సహాయం", "kavali" to "కావాలి", "neeru" to "నీరు", "vaidyudu" to "వైద్యుడు",
        "doctor" to "వైద్యుడు", "namaskaram" to "నమస్కారం", "dhanyavadalu" to "ధన్యవాదాలు",
        "kaapaadandi" to "కాపాడండి", "pramadam" to "ప్రమాదం", "agni" to "అగ్ని", "aahaaram" to "ఆహారం",
        "mandulu" to "మందులు", "tvaraga" to "త్వరగా", "okati" to "ఒకటి", "rendu" to "రెండు",
        "moodu" to "మూడు", "naalugu" to "నాలుగు", "aidu" to "ఐదు", "kaadu" to "కాదు", "avunu" to "అవును"
    )

    private val BENGALI_DICT = mapOf(
        "amra" to "আমরা", "nirapod" to "নিরাপদ", "achhi" to "আছি", "sahajjo" to "সাহায্য",
        "sahajyo" to "সাহায্য", "chai" to "চাই", "jol" to "জল", "paani" to "পানি",
        "daktar" to "ডাক্তার", "doctor" to "ডাক্তার", "nomoshkar" to "নমস্কার", "dhonnobad" to "ধন্যবাদ",
        "bachao" to "বাঁচাও", "bipod" to "বিপদ", "agun" to "আগুন", "khabar" to "খাবার",
        "oshudh" to "ওষুধ", "taratari" to "তাড়াতাড়ি", "ek" to "এক", "dui" to "দুই",
        "tin" to "তিন", "char" to "চার", "paanch" to "পাঁচ", "na" to "না", "haan" to "হ্যাঁ"
    )

    private val GUJARATI_DICT = mapOf(
        "ame" to "અમે", "surakshit" to "સુરક્ષિત", "chhiye" to "છીએ", "madad" to "મદદ",
        "joiye" to "જોઈએ", "paani" to "પાણી", "pani" to "પાણી", "doctor" to "ડૉક્ટર",
        "namaste" to "નમસ્તે", "aabhar" to "આભાર", "bachavo" to "બચાવો", "khatro" to "ખતરો",
        "aag" to "આગ", "khorak" to "ખોરાક", "dava" to "દવા", "jaldi" to "જલદી",
        "ek" to "એક", "be" to "બે", "tran" to "ત્રણ", "char" to "ચાર", "paanch" to "પાંચ",
        "nathi" to "નથી", "haa" to "હા", "ane" to "અને"
    )

    private val KANNADA_DICT = mapOf(
        "naavu" to "ನಾವು", "surakshitavagiddeve" to "ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ", "surakshita" to "ಸುರಕ್ಷಿತ",
        "sahaya" to "ಸಹಾಯ", "beku" to "ಬೇಕು", "neeru" to "ನೀರು", "vaidyaru" to "ವೈದ್ಯರು",
        "doctor" to "ವೈದ್ಯರು", "namaskara" to "ನಮಸ್ಕಾರ", "dhanyavadagalu" to "ಧನ್ಯವಾದಗಳು",
        "kaapadi" to "ಕಾಪಾಡಿ", "aapaathu" to "ಅಪಾಯ", "benki" to "ಬೆಂಕಿ", "oota" to "ಊಟ",
        "aushadhi" to "ಔಷಧಿ", "bega" to "ಬೇಗ", "ondu" to "ಒಂದು", "eradu" to "ಎರಡು",
        "mooru" to "ಮೂರು", "naalaku" to "ನಾಲ್ಕು", "aidu" to "ಐದು", "illa" to "ಇಲ್ಲ", "haudu" to "ಹೌದು"
    )

    private val MALAYALAM_DICT = mapOf(
        "njangal" to "ഞങ്ങൾ", "surakshitharanu" to "സുരക്ഷിതരാണ്", "surakshithar" to "സുരക്ഷിതർ",
        "sahayam" to "സഹായം", "venam" to "വേണം", "vellam" to "വെള്ളം", "doctor" to "ഡോക്ടർ",
        "namaskaram" to "നമസ്കാരം", "nanni" to "നന്ദി", "rakshikku" to "രക്ഷിക്കൂ",
        "apakatam" to "അപകടം", "thee" to "തീ", "bhkshanam" to "ഭക്ഷണം", "marunnu" to "മരുന്ന്",
        "vegam" to "വേഗം", "onnu" to "ഒന്ന്", "randu" to "രണ്ട്", "moonnu" to "മൂന്ന്",
        "naalu" to "നാല്", "anchu" to "അഞ്ച്", "illa" to "ഇല്ല", "athe" to "അതെ"
    )

    private val ODIA_DICT = mapOf(
        "ame" to "ଆମେ", "surakshita" to "ସୁରକ୍ଷିତ", "achhu" to "ଅଛୁ", "sahajya" to "ସାହାଯ୍ୟ",
        "darakara" to "ଦରକାର", "pani" to "ପାଣି", "daktara" to "ଡାକ୍ତର", "doctor" to "ଡାକ୍ତର",
        "namaskara" to "ନମସ୍କାର", "dhanyabada" to "ଧନ୍ୟବାଦ", "banchao" to "ବଞ୍ଚାଅ",
        "bipada" to "ବିପଦ", "nia" to "ନିଆଁ", "khadya" to "ଖାଦ୍ୟ", "aushadha" to "ଔଷଧ",
        "shighra" to "ଶୀଘ୍ର", "eka" to "ଏକ", "dui" to "ଦୁଇ", "tini" to "ତିନି",
        "chari" to "ଚାରି", "pancha" to "ପାଞ୍ଚ", "nahin" to "ନାହିଁ", "haan" to "ହଁ"
    )

    // =========================================================================
    // 2. Brahmic Script Profiles for Rule-based Syllabic Fallback
    // =========================================================================

    private val DEVANAGARI_PROFILE = ScriptProfile(
        consonants = mapOf(
            "k" to "\u0915", "kh" to "\u0916", "g" to "\u0917", "gh" to "\u0918",
            "ch" to "\u091A", "chh" to "\u091B", "j" to "\u091C", "jh" to "\u091D",
            "t" to "\u0924", "th" to "\u0925", "d" to "\u0926", "dh" to "\u0927", "n" to "\u0928",
            "p" to "\u092A", "ph" to "\u092B", "f" to "\u092B", "b" to "\u092C", "bh" to "\u092D", "m" to "\u092E",
            "y" to "\u092F", "r" to "\u0930", "l" to "\u0932", "v" to "\u0935", "w" to "\u0935",
            "sh" to "\u0936", "s" to "\u0938", "h" to "\u0939"
        ),
        matras = mapOf(
            "aa" to "\u093E", "a" to "", "i" to "\u093F", "ee" to "\u0940", "ii" to "\u0940",
            "u" to "\u0941", "oo" to "\u0942", "uu" to "\u0942", "e" to "\u0947", "ai" to "\u0948",
            "o" to "\u094B", "au" to "\u094C"
        ),
        vowels = mapOf(
            "aa" to "\u0906", "a" to "\u0905", "i" to "\u0907", "ee" to "\u0908", "ii" to "\u0908",
            "u" to "\u0909", "oo" to "\u090A", "uu" to "\u090A", "e" to "\u090F", "ai" to "\u0910",
            "o" to "\u0913", "au" to "\u0914"
        ),
        virama = "\u094D",
        anusvara = "\u0902"
    )

    private val TAMIL_PROFILE = ScriptProfile(
        consonants = mapOf(
            "k" to "\u0B95", "g" to "\u0B95", "kh" to "\u0B95", "gh" to "\u0B95",
            "ch" to "\u0B9A", "j" to "\u0B9C", "s" to "\u0BB8", "sh" to "\u0BB7",
            "t" to "\u0BA4", "th" to "\u0BA4", "d" to "\u0BA4", "dh" to "\u0BA4", "n" to "\u0BA9",
            "p" to "\u0BAA", "b" to "\u0BAA", "ph" to "\u0BAA", "m" to "\u0BAE",
            "y" to "\u0BAF", "r" to "\u0BB0", "l" to "\u0BB2", "v" to "\u0BB5", "w" to "\u0BB5",
            "h" to "\u0BB9"
        ),
        matras = mapOf(
            "aa" to "\u0BBE", "a" to "", "i" to "\u0BBF", "ee" to "\u0BC0", "ii" to "\u0BC0",
            "u" to "\u0BC1", "oo" to "\u0BC2", "uu" to "\u0BC2", "e" to "\u0BC6", "ai" to "\u0BC8",
            "o" to "\u0BCA", "au" to "\u0BCC"
        ),
        vowels = mapOf(
            "aa" to "\u0B86", "a" to "\u0B85", "i" to "\u0B87", "ee" to "\u0B88", "ii" to "\u0B88",
            "u" to "\u0B89", "oo" to "\u0B8A", "uu" to "\u0B8A", "e" to "\u0B8E", "ai" to "\u0B90",
            "o" to "\u0B92", "au" to "\u0B94"
        ),
        virama = "\u0BCD",
        anusvara = "\u0BAE\u0BCD"
    )

    private val TELUGU_PROFILE = ScriptProfile(
        consonants = mapOf(
            "k" to "\u0C15", "kh" to "\u0C16", "g" to "\u0C17", "gh" to "\u0C18",
            "ch" to "\u0C1A", "chh" to "\u0C1B", "j" to "\u0C1C", "jh" to "\u0C1D",
            "t" to "\u0C24", "th" to "\u0C25", "d" to "\u0C26", "dh" to "\u0C27", "n" to "\u0C28",
            "p" to "\u0C2A", "ph" to "\u0C2B", "b" to "\u0C2C", "bh" to "\u0C2D", "m" to "\u0C2E",
            "y" to "\u0C2F", "r" to "\u0C30", "l" to "\u0C32", "v" to "\u0C35", "w" to "\u0C35",
            "sh" to "\u0C36", "s" to "\u0C38", "h" to "\u0C39"
        ),
        matras = mapOf(
            "aa" to "\u0C3E", "a" to "", "i" to "\u0C3F", "ee" to "\u0C40", "ii" to "\u0C40",
            "u" to "\u0C41", "oo" to "\u0C42", "uu" to "\u0C42", "e" to "\u0C46", "ai" to "\u0C48",
            "o" to "\u0C4A", "au" to "\u0C4C"
        ),
        vowels = mapOf(
            "aa" to "\u0C06", "a" to "\u0C05", "i" to "\u0C07", "ee" to "\u0C08", "ii" to "\u0C08",
            "u" to "\u0C09", "oo" to "\u0C0A", "uu" to "\u0C0A", "e" to "\u0C0E", "ai" to "\u0C10",
            "o" to "\u0C12", "au" to "\u0C14"
        ),
        virama = "\u0C4D",
        anusvara = "\u0C02"
    )

    private val BENGALI_PROFILE = ScriptProfile(
        consonants = mapOf(
            "k" to "\u0995", "kh" to "\u0996", "g" to "\u0997", "gh" to "\u0998",
            "ch" to "\u099A", "chh" to "\u099B", "j" to "\u099C", "jh" to "\u099D",
            "t" to "\u09A4", "th" to "\u09A5", "d" to "\u09A6", "dh" to "\u09A7", "n" to "\u09A8",
            "p" to "\u09AA", "ph" to "\u09AB", "b" to "\u09AC", "bh" to "\u09AD", "m" to "\u09AE",
            "y" to "\u09AF", "r" to "\u09B0", "l" to "\u09B2", "v" to "\u09AC", "w" to "\u09AC",
            "sh" to "\u09B6", "s" to "\u09B8", "h" to "\u09B9"
        ),
        matras = mapOf(
            "aa" to "\u09BE", "a" to "", "i" to "\u09BF", "ee" to "\u09C0", "ii" to "\u09C0",
            "u" to "\u09C1", "oo" to "\u09C2", "uu" to "\u09C2", "e" to "\u09C7", "ai" to "\u09C8",
            "o" to "\u09CB", "au" to "\u09CC"
        ),
        vowels = mapOf(
            "aa" to "\u0986", "a" to "\u0985", "i" to "\u0987", "ee" to "\u0988", "ii" to "\u0988",
            "u" to "\u0989", "oo" to "\u098A", "uu" to "\u098A", "e" to "\u098F", "ai" to "\u0990",
            "o" to "\u0993", "au" to "\u0994"
        ),
        virama = "\u09CD",
        anusvara = "\u0982"
    )

    private val GUJARATI_PROFILE = ScriptProfile(
        consonants = mapOf(
            "k" to "\u0A95", "kh" to "\u0A96", "g" to "\u0A97", "gh" to "\u0A98",
            "ch" to "\u0A9A", "chh" to "\u0A9B", "j" to "\u0A9C", "jh" to "\u0A9D",
            "t" to "\u0AA4", "th" to "\u0AA5", "d" to "\u0AA6", "dh" to "\u0AA7", "n" to "\u0AA8",
            "p" to "\u0AAA", "ph" to "\u0AAB", "b" to "\u0AAC", "bh" to "\u0AAD", "m" to "\u0AAE",
            "y" to "\u0AAF", "r" to "\u0AB0", "l" to "\u0AB2", "v" to "\u0AB5", "w" to "\u0AB5",
            "sh" to "\u0AB6", "s" to "\u0AB8", "h" to "\u0AB9"
        ),
        matras = mapOf(
            "aa" to "\u0ABE", "a" to "", "i" to "\u0ABF", "ee" to "\u0AC0", "ii" to "\u0AC0",
            "u" to "\u0AC1", "oo" to "\u0AC2", "uu" to "\u0AC2", "e" to "\u0AC7", "ai" to "\u0AC8",
            "o" to "\u0ACB", "au" to "\u0ACC"
        ),
        vowels = mapOf(
            "aa" to "\u0A86", "a" to "\u0A85", "i" to "\u0A87", "ee" to "\u0A88", "ii" to "\u0A88",
            "u" to "\u0A89", "oo" to "\u0A8A", "uu" to "\u0A8A", "e" to "\u0A8F", "ai" to "\u0A90",
            "o" to "\u0A93", "au" to "\u0A94"
        ),
        virama = "\u0ACD",
        anusvara = "\u0A82"
    )

    private val KANNADA_PROFILE = ScriptProfile(
        consonants = mapOf(
            "k" to "\u0C95", "kh" to "\u0C96", "g" to "\u0C97", "gh" to "\u0C98",
            "ch" to "\u0C9A", "chh" to "\u0C9B", "j" to "\u0C9C", "jh" to "\u0C9D",
            "t" to "\u0CA4", "th" to "\u0CA5", "d" to "\u0CA6", "dh" to "\u0CA7", "n" to "\u0CA8",
            "p" to "\u0CAA", "ph" to "\u0CAB", "b" to "\u0CAC", "bh" to "\u0CAD", "m" to "\u0CAE",
            "y" to "\u0CAF", "r" to "\u0CB0", "l" to "\u0CB2", "v" to "\u0CB5", "w" to "\u0CB5",
            "sh" to "\u0CB6", "s" to "\u0CB8", "h" to "\u0CB9"
        ),
        matras = mapOf(
            "aa" to "\u0CBE", "a" to "", "i" to "\u0CBF", "ee" to "\u0CC0", "ii" to "\u0CC0",
            "u" to "\u0CC1", "oo" to "\u0CC2", "uu" to "\u0CC2", "e" to "\u0CC6", "ai" to "\u0CC8",
            "o" to "\u0CCA", "au" to "\u0CCC"
        ),
        vowels = mapOf(
            "aa" to "\u0C86", "a" to "\u0C85", "i" to "\u0C87", "ee" to "\u0C88", "ii" to "\u0C88",
            "u" to "\u0C89", "oo" to "\u0C8A", "uu" to "\u0C8A", "e" to "\u0C8E", "ai" to "\u0C90",
            "o" to "\u0C92", "au" to "\u0C94"
        ),
        virama = "\u0CCD",
        anusvara = "\u0C82"
    )

    private val MALAYALAM_PROFILE = ScriptProfile(
        consonants = mapOf(
            "k" to "\u0D15", "kh" to "\u0D16", "g" to "\u0D17", "gh" to "\u0D18",
            "ch" to "\u0D1A", "chh" to "\u0D1B", "j" to "\u0D1C", "jh" to "\u0D1D",
            "t" to "\u0D24", "th" to "\u0D25", "d" to "\u0D26", "dh" to "\u0D27", "n" to "\u0D28",
            "p" to "\u0D2A", "ph" to "\u0D2B", "b" to "\u0D2C", "bh" to "\u0D2D", "m" to "\u0D2E",
            "y" to "\u0D2F", "r" to "\u0D30", "l" to "\u0D32", "v" to "\u0D35", "w" to "\u0D35",
            "sh" to "\u0D36", "s" to "\u0D38", "h" to "\u0D39"
        ),
        matras = mapOf(
            "aa" to "\u0D3E", "a" to "", "i" to "\u0D3F", "ee" to "\u0D40", "ii" to "\u0D40",
            "u" to "\u0D41", "oo" to "\u0D42", "uu" to "\u0D42", "e" to "\u0D46", "ai" to "\u0D48",
            "o" to "\u0D4A", "au" to "\u0D4C"
        ),
        vowels = mapOf(
            "aa" to "\u0D06", "a" to "\u0D05", "i" to "\u0D07", "ee" to "\u0D08", "ii" to "\u0D08",
            "u" to "\u0D09", "oo" to "\u0D0A", "uu" to "\u0D0A", "e" to "\u0D0E", "ai" to "\u0D10",
            "o" to "\u0D12", "au" to "\u0D14"
        ),
        virama = "\u0D4D",
        anusvara = "\u0D02"
    )

    private val ODIA_PROFILE = ScriptProfile(
        consonants = mapOf(
            "k" to "\u0B15", "kh" to "\u0B16", "g" to "\u0B17", "gh" to "\u0B18",
            "ch" to "\u0B1A", "chh" to "\u0B1B", "j" to "\u0B1C", "jh" to "\u0B1D",
            "t" to "\u0B24", "th" to "\u0B25", "d" to "\u0B26", "dh" to "\u0B27", "n" to "\u0B28",
            "p" to "\u0B2A", "ph" to "\u0B2B", "b" to "\u0B2C", "bh" to "\u0B2D", "m" to "\u0B2E",
            "y" to "\u0B2F", "r" to "\u0B30", "l" to "\u0B32", "v" to "\u0B35", "w" to "\u0B35",
            "sh" to "\u0B36", "s" to "\u0B38", "h" to "\u0B39"
        ),
        matras = mapOf(
            "aa" to "\u0B3E", "a" to "", "i" to "\u0B3F", "ee" to "\u0B40", "ii" to "\u0B40",
            "u" to "\u0B41", "oo" to "\u0B42", "uu" to "\u0B42", "e" to "\u0B47", "ai" to "\u0B48",
            "o" to "\u0B4B", "au" to "\u0B4C"
        ),
        vowels = mapOf(
            "aa" to "\u0B06", "a" to "\u0B05", "i" to "\u0B07", "ee" to "\u0B08", "ii" to "\u0B08",
            "u" to "\u0B09", "oo" to "\u0B0A", "uu" to "\u0B0A", "e" to "\u0B0F", "ai" to "\u0B10",
            "o" to "\u0B13", "au" to "\u0B14"
        ),
        virama = "\u0B4D",
        anusvara = "\u0B02"
    )

    /**
     * Transliterates [text] to the native script of [language] if it contains Latin characters.
     * Preserves existing native script characters, digits, and punctuation untouched.
     */
    fun transliterate(text: String, language: IndicLanguage): String {
        if (language == IndicLanguage.ENGLISH || text.isBlank()) return text
        if (!text.any { it in 'A'..'Z' || it in 'a'..'z' }) return text

        val profile = when (language) {
            IndicLanguage.HINDI, IndicLanguage.MARATHI -> DEVANAGARI_PROFILE
            IndicLanguage.TAMIL -> TAMIL_PROFILE
            IndicLanguage.TELUGU -> TELUGU_PROFILE
            IndicLanguage.BENGALI -> BENGALI_PROFILE
            IndicLanguage.GUJARATI -> GUJARATI_PROFILE
            IndicLanguage.KANNADA -> KANNADA_PROFILE
            IndicLanguage.MALAYALAM -> MALAYALAM_PROFILE
            IndicLanguage.ODIA -> ODIA_PROFILE
            IndicLanguage.ENGLISH -> return text
        }

        val dict = when (language) {
            IndicLanguage.HINDI -> HINDI_DICT
            IndicLanguage.MARATHI -> MARATHI_DICT
            IndicLanguage.TAMIL -> TAMIL_DICT
            IndicLanguage.TELUGU -> TELUGU_DICT
            IndicLanguage.BENGALI -> BENGALI_DICT
            IndicLanguage.GUJARATI -> GUJARATI_DICT
            IndicLanguage.KANNADA -> KANNADA_DICT
            IndicLanguage.MALAYALAM -> MALAYALAM_DICT
            IndicLanguage.ODIA -> ODIA_DICT
            IndicLanguage.ENGLISH -> return text
        }

        val tokens = text.split(" ")
        return tokens.joinToString(" ") { token ->
            transliterateToken(token, profile, dict)
        }
    }

    private fun transliterateToken(token: String, profile: ScriptProfile, dict: Map<String, String>): String {
        // Strip trailing/leading punctuation
        var leadingPunct = ""
        var trailingPunct = ""
        var core = token

        while (core.isNotEmpty() && !core.first().isLetterOrDigit()) {
            leadingPunct += core.first()
            core = core.substring(1)
        }
        while (core.isNotEmpty() && !core.last().isLetterOrDigit()) {
            trailingPunct = core.last() + trailingPunct
            core = core.substring(0, core.length - 1)
        }

        if (core.isEmpty() || !core.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            return token
        }

        val lowerCore = core.lowercase(Locale.ROOT)
        // 1. Direct dictionary match
        val dictMatch = dict[lowerCore]
        if (dictMatch != null) {
            return "$leadingPunct$dictMatch$trailingPunct"
        }

        // 2. Rule-based syllabic Brahmic synthesis
        val sb = StringBuilder()
        var i = 0
        val n = lowerCore.length

        while (i < n) {
            val ch = lowerCore[i]

            // Check 3-char consonant clusters (e.g. "chh")
            if (i + 3 <= n && profile.consonants.containsKey(lowerCore.substring(i, i + 3))) {
                val c = profile.consonants[lowerCore.substring(i, i + 3)]!!
                i += 3
                i = appendVowelOrVirama(lowerCore, i, n, c, profile, sb)
                continue
            }

            // Check 2-char consonant clusters (e.g. "kh", "gh", "ch", "jh", "th", "dh", "ph", "bh", "sh")
            if (i + 2 <= n && profile.consonants.containsKey(lowerCore.substring(i, i + 2))) {
                val c = profile.consonants[lowerCore.substring(i, i + 2)]!!
                i += 2
                i = appendVowelOrVirama(lowerCore, i, n, c, profile, sb)
                continue
            }

            // Check 1-char consonant
            val sChar = ch.toString()
            if (profile.consonants.containsKey(sChar)) {
                val c = profile.consonants[sChar]!!
                i += 1
                i = appendVowelOrVirama(lowerCore, i, n, c, profile, sb)
                continue
            }

            // Independent vowels
            var vowelFound = false
            for (vLen in listOf(2, 1)) {
                if (i + vLen <= n) {
                    val sub = lowerCore.substring(i, i + vLen)
                    if (profile.vowels.containsKey(sub)) {
                        sb.append(profile.vowels[sub])
                        i += vLen
                        vowelFound = true
                        break
                    }
                }
            }
            if (vowelFound) continue

            // Nasal / Anusvara at end of word or before consonant
            if (ch == 'm' || ch == 'n') {
                sb.append(profile.anusvara)
                i += 1
                continue
            }

            // Passthrough non-alpha
            sb.append(ch)
            i += 1
        }

        return "$leadingPunct$sb$trailingPunct"
    }

    private fun appendVowelOrVirama(
        word: String,
        startIndex: Int,
        n: Int,
        consonantChar: String,
        profile: ScriptProfile,
        sb: StringBuilder
    ): Int {
        var idx = startIndex

        // Check 2-char matras ("aa", "ee", "oo", "ai", "au")
        if (idx + 2 <= n) {
            val sub2 = word.substring(idx, idx + 2)
            if (profile.matras.containsKey(sub2)) {
                sb.append(consonantChar)
                sb.append(profile.matras[sub2])
                return idx + 2
            }
        }

        // Check 1-char matras ("a", "i", "u", "e", "o")
        if (idx < n) {
            val sub1 = word.substring(idx, idx + 1)
            if (profile.matras.containsKey(sub1)) {
                sb.append(consonantChar)
                sb.append(profile.matras[sub1])
                return idx + 1
            }
        }

        // End of word or followed by consonant: consonant carries inherent vowel or virama
        sb.append(consonantChar)
        return idx
    }
}
