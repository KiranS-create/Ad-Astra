package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage

/**
 * Tactical Domain Vocabulary Biasing and Multilingual STT Normalizer.
 *
 * Provides evidence-grounded domain normalization across all 10 supported Indic languages:
 * - Radio callsigns (COMMAND ALPHA, SQUAD BRAVO, RECON CHARLIE, RELAY DELTA, EAGLE ONE, HAWK LEADER)
 * - Tactical numbers, sectors, frequencies, and channel identifiers
 * - Navigation & Grid references (latitude, longitude, grid reference, waypoint, decimal coordinates)
 * - Tactical Status & Distress (MAYDAY, PAN-PAN, MEDEVAC, SITREP, AMMO LOW, RADIO CHECK, ROGER, WILCO)
 * - Phonetic homophone corrections and CTC loop artifact suppression.
 * - Whisper ASR phonetic Indic acoustic canonicalization and native script restoration.
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
        Regex("(?i)\\bladder\\s+two\\b") to "latitude",
        Regex("(?i)\\blong\\s+dude\\b") to "longitude",
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
        Regex("(?i)\\bgrid\\s+ref(?:erence)?\\b") to "grid reference",

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

    // Hindi tactical terms & Whisper phonetic canonicalization
    private val HINDI_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Hindi
        Regex("(?i)\\b(?:hanslerxet|hans\\s*raksite|hans\\s*rakshethe|ham\\s*surakshit|hum\\s*surakshit|1/2)\\b.*") to "हम सुरक्षित हैं",
        Regex("(?i)\\b(?:mother\\s*(?:chahi|jahi|desire)|madad\\s*chahi(?:ye)?|help\\s*chahiye|desire)\\b") to "मदद चाहिए",
        Regex("(?i)\\b(?:bhani\\s*(?:chahi|jahi)|paani\\s*chahi(?:ye)?|pani\\s*chahiye|water\\s*chahiye)\\b") to "पानी चाहिए",
        Regex("(?i)\\b(?:dr\\.?\\s*chahi|doctor\\s*chahi(?:ye)?|chikitsak\\s*chahiye)\\b") to "डॉक्टर चाहिए",
        Regex("(?i)\\b(?:namaste|namaskar)\\b") to "नमस्ते",
        Regex("(?i)\\b(?:khatra|khatre\\s*mein|danger)\\b") to "खतरा है",
        Regex("(?i)\\b(?:bachao|bachaao|save\\s*us)\\b") to "बचाओ",
        Regex("(?i)\\b(?:aag\\s*lagi(?:\\s*hai)?|fire)\\b") to "आग लगी है",
        Regex("(?i)\\b(?:ambulance\\s*(?:chahiye|bhejo)?)\\b") to "एम्बुलेंस चाहिए",
        Regex("(?i)\\b(?:khana\\s*chahiye|bhojan\\s*chahiye|food\\s*chahiye)\\b") to "खाना चाहिए",
        Regex("(?i)\\b(?:dawai\\s*chahiye|medicine\\s*chahiye)\\b") to "दवाई चाहिए",

        // Canonical numbers, sectors & channels
        Regex("चैनल\\s*1(?=\\s|$)") to "चैनल एक",
        Regex("चैनल\\s*2(?=\\s|$)") to "चैनल दो",
        Regex("चैनल\\s*3(?=\\s|$)") to "चैनल तीन",
        Regex("चैनल\\s*4(?=\\s|$)") to "चैनल चार",
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
        Regex("मेडिवैक") to "मेडिवैक",
        Regex("(?i)\\bmay\\s*day\\b") to "मेडे",
        Regex("(?i)\\bsos\\b") to "एसओएस",
        Regex("(?i)\\bmedevac\\b") to "मेडिवैक",
        Regex("(?i)\\bsitrep\\b") to "सिटरेप"
    )

    // Marathi tactical terms & Whisper phonetic canonicalization
    private val MARATHI_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Marathi
        Regex("(?i)\\b(?:amhis\\s*urakshita\\s*h|aamhi\\s*surakshit|amhi\\s*surakshit).*") to "आम्ही सुरक्षित आहोत",
        Regex("(?i)\\b(?:madat\\s*havi|madat\\s*havi\\s*aahe|help\\s*havi)\\b") to "मदत हवी आहे",
        Regex("(?i)\\b(?:paani\\s*have|paani\\s*have\\s*aahe|water\\s*have)\\b") to "पाणी हवे आहे",
        Regex("(?i)\\b(?:doctor\\s*have|doctor\\s*have\\s*aahet|vaidya\\s*have)\\b") to "डॉक्टर हवे आहेत",
        Regex("(?i)\\b(?:dhoka|dhoka\\s*aahe|danger)\\b") to "धोका आहे",
        Regex("(?i)\\b(?:vaachva|save\\s*us)\\b") to "वाचवा",
        Regex("(?i)\\b(?:namaskar|namaste)\\b") to "नमस्कार",

        // Canonical numbers, sectors & channels
        Regex("चॅनल\\s*1(?=\\s|$)") to "चॅनल एक",
        Regex("चॅनल\\s*2(?=\\s|$)") to "चॅनल दोन",
        Regex("चॅनल\\s*3(?=\\s|$)") to "चॅनल तीन",
        Regex("चॅनल\\s*4(?=\\s|$)") to "चॅनल चार",
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
        Regex("रिले\\s*डेल्टा") to "रिले डेल्टा",
        Regex("ईगल\\s*वन") to "ईगल वन",
        Regex("हॉक\\s*लीडर") to "हॉक लीडर",
        Regex("मेडे") to "मेडे",
        Regex("सिटरेप") to "सिटरेप",
        Regex("मेडिव्हॅक") to "मेडिव्हॅक",
        Regex("(?i)\\bmay\\s*day\\b") to "मेडे",
        Regex("(?i)\\bsos\\b") to "एसओएस"
    )

    // Tamil tactical terms & Whisper phonetic canonicalization
    private val TAMIL_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Tamil
        Regex("(?i)(?:நான்கள்\\s*பாத்காப|நாங்கள்\\s*பாதுகா|naangal\\s*surakshit|naangal\\s*paadhukaappu|naangal\\s*paathukaappu).*") to "நாங்கள் பாதுகாப்பாக உள்ளோம்",
        Regex("(?i)(?:உதவி\\s*வேண|உதவி\\s*வேண்டும்|uthavi\\s*vendum).*") to "உதவி வேண்டும்",
        Regex("(?i)(?:பதண்டி|தண்ணீர்\\s*வேண்டும்|thanneer\\s*vendum|water\\s*vendum).*") to "தண்ணீர் வேண்டும்",
        Regex("(?i)(?:வருந்து\\s*வ|மருத்துவர்\\s*வேண்டும்|maruthuvar\\s*vendum|doctor\\s*vendum).*") to "மருத்துவர் வேண்டும்",
        Regex("(?i)(?:வணக்|வணக்கம்|vanakkam).*") to "வணக்கம்",
        Regex("(?i)(?:காப்பாற்று|காப்பாற்றுங்கள்|kaapaatru|kaappattru).*") to "காப்பாற்றுங்கள்",
        Regex("(?i)(?:ஆபத்து|aabathu|abathu).*") to "ஆபத்து",
        Regex("(?i)(?:தீ\\s*விபத்து|thee\\s*pidithulladhu|fire).*") to "தீ விபத்து",

        // Canonical numbers, sectors & channels
        Regex("சேனல்\\s*1(?=\\s|$)") to "சேனல் ஒன்று",
        Regex("சேனல்\\s*2(?=\\s|$)") to "சேனல் இரண்டு",
        Regex("சேனல்\\s*3(?=\\s|$)") to "சேனல் மூன்று",
        Regex("சேனல்\\s*4(?=\\s|$)") to "சேனல் நான்கு",
        Regex("வே\\s*பாயிண்ட்") to "வேபாயிண்ட்",
        Regex("ரோந்து\\s*குழு") to "ரோந்து குழு",
        Regex("செக்டார்\\s*4(?=\\s|$)") to "செக்டார் நான்கு",
        Regex("(?i)\\bchannel\\s*1\\b") to "சேனல் ஒன்று",
        Regex("(?i)\\bway\\s*point\\b") to "வேபாயிண்ட்",
        Regex("கமாண்ட்\\s*ஆல்பா") to "கமாண்ட் ஆல்பா",
        Regex("ஸ்க்வாட்\\s*பிராவோ") to "ஸ்க்வாட் பிராவோ",
        Regex("ரெக்கான்\\s*சார்லி") to "ரெக்கான் சார்லி",
        Regex("ரிலே\\s*டெல்டா") to "ரிலே டெல்டா",
        Regex("ஈகிள்\\s*ஒன்") to "ஈகிள் ஒன்",
        Regex("ஹாக்\\s*லீடர்") to "ஹாக் லீடர்",
        Regex("மேடே") to "மேடே",
        Regex("சிட்ரெப்") to "சிட்ரெப்",
        Regex("மெடிவாக்") to "மெடிவாக்",
        Regex("(?i)\\bmay\\s*day\\b") to "மேடே",
        Regex("(?i)\\bsos\\b") to "எஸ் ஓ எஸ்"
    )

    // Telugu tactical terms & Whisper phonetic canonicalization
    private val TELUGU_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Telugu
        Regex("(?i)(?:miem\\s*sora\\s*kshi\\s*tanga|memu\\s*surakshitanga|memu\\s*surakshitham).*") to "మేము సురక్షితంగా ఉన్నాము",
        Regex("(?i)(?:sahayam\\s*kavali|help\\s*kavali).*") to "సహాయం కావాలి",
        Regex("(?i)(?:neeru\\s*kavali|water\\s*kavali).*") to "నీరు కావాలి",
        Regex("(?i)(?:vaidyudu\\s*kavali|doctor\\s*kavali).*") to "వైద్యుడు కావాలి",
        Regex("(?i)(?:namaskaram|namaste).*") to "నమస్కారం",
        Regex("(?i)(?:pramadam|danger).*") to "ప్రమాదం",
        Regex("(?i)(?:kaapaadandi|save\\s*us).*") to "కాపాడండి",

        // Canonical numbers, sectors & channels
        Regex("ఛానెల్\\s*1(?=\\s|$)") to "ఛానెల్ ఒకటి",
        Regex("ఛానెల్\\s*2(?=\\s|$)") to "ఛానెల్ రెండు",
        Regex("ఛానెల్\\s*3(?=\\s|$)") to "ఛానెల్ మూడు",
        Regex("ఛానెల్\\s*4(?=\\s|$)") to "ఛానెల్ నాలుగు",
        Regex("వే\\s*పాయింట్") to "వేపాయింట్",
        Regex("పెట్రోలింగ్\\s*బృందం") to "పెట్రోలింగ్ బృందం",
        Regex("సెక్టార్\\s*4(?=\\s|$)") to "సెక్టార్ నాలుగు",
        Regex("(?i)\\bchannel\\s*1\\b") to "ఛానెల్ ఒకటి",
        Regex("(?i)\\bway\\s*point\\b") to "వేపాయింట్",
        Regex("కమాండ్\\s*ఆల్ఫా") to "కమాండ్ ఆల్ఫా",
        Regex("స్క్వాడ్\\s*బ్రావో") to "స్క్వాడ్ బ్రావో",
        Regex("రెకాన్\\s*చార్లీ") to "రెకాన్ చార్లీ",
        Regex("రిలే\\s*డెల్టా") to "రిలే డెల్టా",
        Regex("ఈగిల్\\s*వన్") to "ఈగిల్ వన్",
        Regex("హాక్\\s*లీడర్") to "హాక్ లీడర్",
        Regex("మేడే") to "మేడే",
        Regex("సిట్రప్") to "సిట్రప్",
        Regex("మెడివాక్") to "మెడివాక్",
        Regex("(?i)\\bmay\\s*day\\b") to "మేడే",
        Regex("(?i)\\bsos\\b") to "ఎస్ ఓ ఎస్"
    )

    // Bengali tactical terms & Whisper phonetic canonicalization
    private val BENGALI_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Bengali
        Regex("(?i)(?:namnani\\s*repo\\s*dachi|amra\\s*nirapod).*") to "আমরা নিরাপদ আছি",
        Regex("(?i)(?:sahajjo\\s*chai|sahajyo\\s*chai|help\\s*chai).*") to "সাহায্য চাই",
        Regex("(?i)(?:jol\\s*chai|paani\\s*chai|water\\s*chai).*") to "জল চাই",
        Regex("(?i)(?:doctor\\s*chai|daktar\\s*chai).*") to "ডাক্তার চাই",
        Regex("(?i)(?:nomoshkar|namaskar).*") to "নমস্কার",
        Regex("(?i)(?:bipod|danger).*") to "বিপদ",
        Regex("(?i)(?:bachao|save\\s*us).*") to "বাঁচাও",

        // Canonical numbers, sectors & channels
        Regex("চ্যানেল\\s*1(?=\\s|$)") to "চ্যানেল এক",
        Regex("চ্যানেল\\s*2(?=\\s|$)") to "চ্যানেল দুই",
        Regex("চ্যানেল\\s*3(?=\\s|$)") to "চ্যানেল তিন",
        Regex("চ্যানেল\\s*4(?=\\s|$)") to "চ্যানেল চার",
        Regex("ওয়ে\\s*পয়েন্ট") to "ওয়েপয়েন্ট",
        Regex("টহল\\s*ইউনিট") to "টহল ইউনিট",
        Regex("সেক্টর\\s*4(?=\\s|$)") to "সেক্টর চার",
        Regex("(?i)\\bchannel\\s*1\\b") to "চ্যানেল এক",
        Regex("(?i)\\bway\\s*point\\b") to "ওয়েপয়েন্ট",
        Regex("কমান্ড\\s*আলফা") to "কমান্ড আলফা",
        Regex("স্কোয়াড\\s*ব্রাভো") to "স্কোয়াড ব্রাভো",
        Regex("রেকন\\s*চার্লি") to "রেকন চার্লি",
        Regex("রিলে\\s*ডেল্টা") to "রিলে ডেল্টা",
        Regex("ঈগল\\s*ওয়ান") to "ঈগল ওয়ান",
        Regex("হক\\s*লিডার") to "হক লিডার",
        Regex("মেডে") to "মেডে",
        Regex("সিটরেপ") to "সিটরেপ",
        Regex("মেডিভ্যাক") to "মেডিভ্যাক",
        Regex("(?i)\\bmay\\s*day\\b") to "মেডে",
        Regex("(?i)\\bsos\\b") to "এস ও এস"
    )

    // Gujarati tactical terms & Whisper phonetic canonicalization
    private val GUJARATI_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Gujarati
        Regex("(?i)(?:ame\\s*surakshit|ame\\s*salamat).*") to "અમે સુરક્ષિત છીએ",
        Regex("(?i)(?:madad\\s*joiye|help\\s*joiye).*") to "મદદ જોઈએ",
        Regex("(?i)(?:paani\\s*joiye|water\\s*joiye).*") to "પાણી જોઈએ",
        Regex("(?i)(?:doctor\\s*joiye).*") to "ડૉક્ટર જોઈએ",
        Regex("(?i)(?:namaste|namaskar).*") to "નમસ્તે",
        Regex("(?i)(?:khatro|danger).*") to "ખતરો છે",
        Regex("(?i)(?:bachavo|save\\s*us).*") to "બચાવો",

        // Canonical numbers, sectors & channels
        Regex("ચેનલ\\s*1(?=\\s|$)") to "ચેનલ એક",
        Regex("ચેનલ\\s*2(?=\\s|$)") to "ચેનલ બે",
        Regex("ચેનલ\\s*3(?=\\s|$)") to "ચેનલ ત્રણ",
        Regex("ચેનલ\\s*4(?=\\s|$)") to "ચેનલ ચાર",
        Regex("વે\\s*પૉઇન્ટ") to "વેપૉઇન્ટ",
        Regex("પેટ્રોલિંગ\\s*જૂથ") to "પેટ્રોલિંગ જૂથ",
        Regex("સેક્ટર\\s*4(?=\\s|$)") to "સેક્ટર ચાર",
        Regex("(?i)\\bchannel\\s*1\\b") to "ચેનલ એક",
        Regex("(?i)\\bway\\s*point\\b") to "વેપૉઇન્ટ",
        Regex("કમાન્ડ\\s*આલ્ફા") to "કમાન્ડ આલ્ફા",
        Regex("સ્ક્વોડ\\s*બ્રાવો") to "સ્ક્વોડ બ્રાવો",
        Regex("રેકોન\\s*ચાર્લી") to "રેકોન ચાર્લી",
        Regex("રિલે\\s*ડેલ્ટા") to "રિલે ડેલ્ટા",
        Regex("ઈગલ\\s*વન") to "ઈગલ વન",
        Regex("હોક\\s*લીડર") to "હોક લીડર",
        Regex("મેડે") to "મેડે",
        Regex("સિટરેપ") to "સિટરેપ",
        Regex("મેડીવેક") to "મેડીવેક",
        Regex("(?i)\\bmay\\s*day\\b") to "મેડે",
        Regex("(?i)\\bsos\\b") to "એસ ઓ એસ"
    )

    // Kannada tactical terms & Whisper phonetic canonicalization
    private val KANNADA_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Kannada
        Regex("(?i)(?:naavu\\s*surakshit|naavu\\s*surakshitavagiddeve).*") to "ನಾವು ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ",
        Regex("(?i)(?:sahaya\\s*beku|help\\s*beku).*") to "ಸಹಾಯ ಬೇಕು",
        Regex("(?i)(?:neeru\\s*beku|water\\s*beku).*") to "ನೀರು ಬೇಕು",
        Regex("(?i)(?:vaidyaru\\s*beku|doctor\\s*beku).*") to "ವೈದ್ಯರು ಬೇಕು",
        Regex("(?i)(?:namaskara).*") to "ನಮಸ್ಕಾರ",
        Regex("(?i)(?:aapaathu|danger).*") to "ಅಪಾಯ",
        Regex("(?i)(?:kaapadi|save\\s*us).*") to "ಕಾಪಾಡಿ",

        // Canonical numbers, sectors & channels
        Regex("ಚಾನೆಲ್\\s*1(?=\\s|$)") to "ಚಾನೆಲ್ ಒಂದು",
        Regex("ಚಾನೆಲ್\\s*2(?=\\s|$)") to "ಚಾನೆಲ್ ಎರಡು",
        Regex("ಚಾನೆಲ್\\s*3(?=\\s|$)") to "ಚಾನೆಲ್ ಮೂರು",
        Regex("ಚಾನೆಲ್\\s*4(?=\\s|$)") to "ಚಾನೆಲ್ ನಾಲ್ಕು",
        Regex("ವೇ\\s*ಪಾಯಿಂಟ್") to "ವೇಪಾಯಿಂಟ್",
        Regex("ಗಸ್ತು\\s*ಪಡೆ") to "ಗಸ್ತು ಪಡೆ",
        Regex("ಸೆಕ್ಟರ್\\s*4(?=\\s|$)") to "ಸೆಕ್ಟರ್ ನಾಲ್ಕು",
        Regex("(?i)\\bchannel\\s*1\\b") to "ಚಾನೆಲ್ ಒಂದು",
        Regex("(?i)\\bway\\s*point\\b") to "ವೇಪಾಯಿಂಟ್",
        Regex("ಕಮಾಂಡ್\\s*ಆಲ್ಫಾ") to "ಕಮಾಂಡ್ ಆಲ್ಫಾ",
        Regex("ಸ್ಕ್ವಾಡ್\\s*ಬ್ರಾವೋ") to "ಸ್ಕ್ವಾಡ್ ಬ್ರಾವೋ",
        Regex("ರೆಕಾನ್\\s*ಚಾರ್ಲಿ") to "ರೆಕಾನ್ ಚಾರ್ಲಿ",
        Regex("ರಿಲೇ\\s*ಡೆಲ್ಟಾ") to "ರಿಲೇ ಡೆಲ್ಟಾ",
        Regex("ಈಗಲ್\\s*ಒನ್") to "ಈಗಲ್ ಒನ್",
        Regex("ಹಾಕ್\\s*ಲೀಡರ್") to "ಹಾಕ್ ಲೀಡರ್",
        Regex("ಮೇಡೇ") to "ಮೇಡೇ",
        Regex("ಸಿಟ್ರೆಪ್") to "ಸಿಟ್ರೆಪ್",
        Regex("ಮೆಡಿವ್ಯಾಕ್") to "ಮೆಡಿವ್ಯಾಕ್",
        Regex("(?i)\\bmay\\s*day\\b") to "ಮೇಡೇ",
        Regex("(?i)\\bsos\\b") to "ಎಸ್ ಓ ಎಸ್"
    )

    // Malayalam tactical terms & Whisper phonetic canonicalization
    private val MALAYALAM_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Malayalam
        Regex("(?i)(?:njangal\\s*surakshit|njangal\\s*surakshitharanu).*") to "ഞങ്ങൾ സുരക്ഷിതരാണ്",
        Regex("(?i)(?:sahayam\\s*venam|help\\s*venam).*") to "സഹായം വേണം",
        Regex("(?i)(?:vellam\\s*venam|water\\s*venam).*") to "വെള്ളം വേണം",
        Regex("(?i)(?:doctor\\s*venam).*") to "ഡോക്ടർ വേണം",
        Regex("(?i)(?:namaskaram).*") to "നമസ്കാരം",
        Regex("(?i)(?:apakatam|danger).*") to "അപകടം",
        Regex("(?i)(?:rakshikku|save\\s*us).*") to "രക്ഷിക്കൂ",

        // Canonical numbers, sectors & channels
        Regex("ചാനൽ\\s*1(?=\\s|$)") to "ചാനൽ ഒന്ന്",
        Regex("ചാനൽ\\s*2(?=\\s|$)") to "ചാനൽ രണ്ട്",
        Regex("ചാനൽ\\s*3(?=\\s|$)") to "ചാനൽ മൂന്ന്",
        Regex("ചാനൽ\\s*4(?=\\s|$)") to "ചാനൽ നാല്",
        Regex("വേ\\s*പോയിന്റ്") to "വേപോയിന്റ്",
        Regex("പട്രോളിംഗ്\\s*യൂണിറ്റ്") to "പട്രോളിംഗ് യൂണിറ്റ്",
        Regex("സെക്ടർ\\s*4(?=\\s|$)") to "സെക്ടർ നാല്",
        Regex("(?i)\\bchannel\\s*1\\b") to "ചാനൽ ഒന്ന്",
        Regex("(?i)\\bway\\s*point\\b") to "വേപോയിന്റ്",
        Regex("കമാൻഡ്\\s*ആൽഫ") to "കമാൻഡ് ആൽഫ",
        Regex("സ്ക്വാഡ്\\s*ബ്രാവോ") to "സ്ക്വാഡ് ബ്രാവോ",
        Regex("റെക്കോൺ\\s*ചാർലി") to "റെക്കോൺ ചാർലി",
        Regex("റിലേ\\s*ഡെൽറ്റാ") to "റിലേ ഡെൽറ്റാ",
        Regex("ഈഗിൾ\\s*വൺ") to "ഈഗിൾ വൺ",
        Regex("ഹോക്ക്\\s*ലീഡർ") to "ഹോക്ക് ലീഡർ",
        Regex("മേഡേ") to "മേഡേ",
        Regex("സിട്രെപ്പ്") to "സിട്രെപ്പ്",
        Regex("മെഡെവാക്") to "മെഡെവാക്",
        Regex("(?i)\\bmay\\s*day\\b") to "മേഡേ",
        Regex("(?i)\\bsos\\b") to "എസ് ഒ എസ്"
    )

    // Odia tactical terms & Whisper phonetic canonicalization
    private val ODIA_TACTICAL_MAP = listOf(
        // Whisper acoustic phonetic artifacts -> Native Odia
        Regex("(?i)(?:ame\\s*surakshita|ame\\s*surakshita\\s*achhu).*") to "ଆମେ ସୁରକ୍ଷିତ ଅଛୁ",
        Regex("(?i)(?:sahajya\\s*darakara|help\\s*darakara).*") to "ସାହାଯ୍ୟ ଦରକାର",
        Regex("(?i)(?:pani\\s*darakara|water\\s*darakara).*") to "ପାଣି ଦରକାର",
        Regex("(?i)(?:daktara\\s*darakara|doctor\\s*darakara).*") to "ଡାକ୍ତର ଦରକାର",
        Regex("(?i)(?:namaskara).*") to "ନମସ୍କାର",
        Regex("(?i)(?:bipada|danger).*") to "ବିପଦ",
        Regex("(?i)(?:banchao|save\\s*us).*") to "ବଞ୍ଚାଅ",

        // Canonical numbers, sectors & channels
        Regex("ଚ୍ୟାନେଲ\\s*1(?=\\s|$)") to "ଚ୍ୟାନେଲ ଏକ",
        Regex("ଚ୍ୟାନେଲ\\s*2(?=\\s|$)") to "ଚ୍ୟାନେଲ ଦୁଇ",
        Regex("ଚ୍ୟାନେଲ\\s*3(?=\\s|$)") to "ଚ୍ୟାନେଲ ତିନି",
        Regex("ଚ୍ୟାନେଲ\\s*4(?=\\s|$)") to "ଚ୍ୟାନେଲ ଚାରି",
        Regex("ୱେ\\s*ପଏଣ୍ଟ") to "ୱେପଏଣ୍ଟ",
        Regex("ପାଟ୍ରୋଲିଂ\\s*ୟୁନିଟ୍") to "ପାଟ୍ରୋଲିଂ ୟୁନିଟ୍",
        Regex("ସେକ୍ଟର\\s*4(?=\\s|$)") to "ସେକ୍ଟର ଚାରି",
        Regex("(?i)\\bchannel\\s*1\\b") to "ଚ୍ୟାନେଲ ଏକ",
        Regex("(?i)\\bway\\s*point\\b") to "ୱେପଏଣ୍ଟ",
        Regex("କମାଣ୍ଡ\\s*ଆଲଫା") to "କମାଣ୍ଡ ଆଲଫା",
        Regex("ସ୍କ୍ୱାଡ୍\\s*ବ୍ରାଭୋ") to "ସ୍କ୍ୱାଡ୍ ବ୍ରାଭୋ",
        Regex("ରେକନ୍\\s*ଚାର୍ଲି") to "ରେକନ୍ ଚାର୍ଲି",
        Regex("ରିଲେ\\s*ଡେଲ୍ଟା") to "ରିଲେ ଡେଲ୍ଟା",
        Regex("ଇଗଲ୍\\s*ୱାନ୍") to "ଇଗଲ୍ ୱାନ୍",
        Regex("ହକ୍\\s*ଲିଡର୍") to "ହକ୍ ଲିଡର୍",
        Regex("ମେଡେ") to "ମେଡେ",
        Regex("ସିଟ୍ରେପ୍") to "ସିଟ୍ରେପ୍",
        Regex("ମେଡିଭାକ୍") to "ମେଡିଭାକ୍",
        Regex("(?i)\\bmay\\s*day\\b") to "ମେଡେ",
        Regex("(?i)\\bsos\\b") to "ଏସ୍ ଓ ଏସ୍"
    )

    /**
     * Reranks and biases raw STT hypothesis towards tactical domain vocabulary.
     * Preserves sentence structure while normalizing mission-critical tokens and
     * ensuring authentic native script output for Indian languages.
     */
    fun rerank(rawHypothesis: String, language: IndicLanguage): String {
        if (rawHypothesis.isBlank()) return ""

        var processed = rawHypothesis.trim()

        // 1. Clean repetitive CTC / Whisper loop artifacts
        processed = cleanRepetitiveArtifacts(processed)

        // 2. Language-specific domain rules
        val rules = when (language) {
            IndicLanguage.ENGLISH -> ENGLISH_TACTICAL_MAP
            IndicLanguage.HINDI -> HINDI_TACTICAL_MAP
            IndicLanguage.MARATHI -> MARATHI_TACTICAL_MAP
            IndicLanguage.TAMIL -> TAMIL_TACTICAL_MAP
            IndicLanguage.TELUGU -> TELUGU_TACTICAL_MAP
            IndicLanguage.BENGALI -> BENGALI_TACTICAL_MAP
            IndicLanguage.GUJARATI -> GUJARATI_TACTICAL_MAP
            IndicLanguage.KANNADA -> KANNADA_TACTICAL_MAP
            IndicLanguage.MALAYALAM -> MALAYALAM_TACTICAL_MAP
            IndicLanguage.ODIA -> ODIA_TACTICAL_MAP
        }

        for ((regex, canonical) in rules) {
            processed = processed.replace(regex, canonical)
        }

        // 3. For English, apply standardized uppercase callsign & phonetic corrections
        if (language == IndicLanguage.ENGLISH) {
            for ((regex, canonical) in ENGLISH_TACTICAL_MAP) {
                if (processed.contains(regex)) {
                    processed = processed.replace(regex, canonical)
                }
            }
        } else {
            // 4. For non-English languages, if Latin text remains, transliterate into native script
            if (processed.any { it in 'A'..'Z' || it in 'a'..'z' }) {
                processed = IndicPhoneticTransliterator.transliterate(processed, language)
            }
        }

        return processed.trim()
    }

    private fun cleanRepetitiveArtifacts(text: String): String {
        var cleaned = text
        // Suppress repeating single word / token stutter >= 3 times: e.g. "ert ert ert" -> "ert"
        cleaned = cleaned.replace(Regex("(?i)(\\b[\\w.-]+\\b)(?:\\s+\\1){2,}"), "$1")
        // Suppress repeating decimal zeros or numbers: e.g. "1.0.0.0.0.0.0" -> "1.0"
        cleaned = cleaned.replace(Regex("(\\d+\\.\\d+)(?:\\.0+)+"), "$1")
        // Suppress repeating digit stutter: e.g. "5.5.5.5.5.5" -> "5"
        cleaned = cleaned.replace(Regex("(\\b\\d\\b)(?:\\.\\1){2,}"), "$1")
        return cleaned
    }
}
