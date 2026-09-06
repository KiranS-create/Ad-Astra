package org.sih.itantra.core.protocol

import org.sih.itantra.core.common.IndicLanguage

/**
 * Deterministic command phrase dictionaries for offline tactical voice control.
 *
 * Maps VoiceCommand enums to explicit keyword phrases in the project's 10 target languages.
 * No machine translation — only verified field-tested phrases for genuine radio operations.
 *
 * The phrases are exact matches — no fuzzy matching, partial words, or AI understanding.
 * Each phrase is 1-3 words for easy detection and minimal false positives.
 */
public object CommandPhraseDictionary {

    /**
     * Returns the phrase list for a specific command in a specific language.
     * Returns null if no phrase exists for that language+command combination.
     */
    fun getPhrases(command: VoiceCommand, language: IndicLanguage): Set<String>? {
        return when (language) {
            IndicLanguage.ENGLISH -> ENGLISH_PHRASE_MAP[command]
            IndicLanguage.HINDI -> HINDI_PHRASE_MAP[command]
            IndicLanguage.GUJARATI -> GUJARATI_PHRASE_MAP[command]
            IndicLanguage.MARATHI -> MARATHI_PHRASE_MAP[command]
            IndicLanguage.KANNADA -> KANNADA_PHRASE_MAP[command]
            IndicLanguage.MALAYALAM -> MALAYALAM_PHRASE_MAP[command]
            IndicLanguage.TAMIL -> TAMIL_PHRASE_MAP[command]
            IndicLanguage.TELUGU -> TELUGU_PHRASE_MAP[command]
            IndicLanguage.ODIA -> ODIA_PHRASE_MAP[command]
            IndicLanguage.BENGALI -> BENGALI_PHRASE_MAP[command]
        }
    }

    /**
     * Returns the set of all supported languages that have phrases for at least one command.
     */
    val supportedLanguages: Set<IndicLanguage>
        get() = setOf(
            IndicLanguage.ENGLISH,
            IndicLanguage.HINDI,
            IndicLanguage.GUJARATI,
            IndicLanguage.MARATHI,
            IndicLanguage.KANNADA,
            IndicLanguage.MALAYALAM,
            IndicLanguage.TAMIL,
            IndicLanguage.TELUGU,
            IndicLanguage.ODIA,
            IndicLanguage.BENGALI
        )

    private val ENGLISH_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("send", "transmit", "broadcast"),
        VoiceCommand.CANCEL to setOf("cancel", "stop transmission", "abort"),
        VoiceCommand.DISTRESS to setOf("distress", "emergency", "urgent"),
        VoiceCommand.ALERT to setOf("alert", "warning", "attention"),
        VoiceCommand.STATUS to setOf("status", "report", "condition"),
        VoiceCommand.PTT_MODE to setOf("ptt mode", "ptt", "push to talk"),
        VoiceCommand.CONTINUOUS_MODE to setOf("continuous mode", "continuous", "continuous listening"),
        VoiceCommand.STOP to setOf("stop", "halt", "freeze"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("switch language", "change language", "language"),
        VoiceCommand.REPEAT to setOf("repeat", "repeat last", "replay"),
        VoiceCommand.MUTE to setOf("mute", "silence", "quiet"),
        VoiceCommand.UNMUTE to setOf("unmute", "enable", "activate"),
        VoiceCommand.TOPOLOGY to setOf("topology", "map", "topology view"),
        VoiceCommand.DIAGNOSTICS to setOf("diagnostics", "status", "metrics")
    )

    private val HINDI_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("भेजें", "प्रसारित करें", "प्रसारण"),
        VoiceCommand.CANCEL to setOf("रद्द करें", "रुकें", "बंद करें"),
        VoiceCommand.DISTRESS to setOf("आपातकालीन", "आपात स्थिति", "आपात"),
        VoiceCommand.ALERT to setOf("चेतावनी", "ध्यान दें", "सावधान"),
        VoiceCommand.STATUS to setOf("स्थिति", "रिपोर्ट", "स्थिति बताएं"),
        VoiceCommand.PTT_MODE to setOf("पीटीटी मोड", "पीटीटी", "पुश टू टॉक"),
        VoiceCommand.CONTINUOUS_MODE to setOf("निरंतर मोड", "निरंतर", "निरंतर सुनना"),
        VoiceCommand.STOP to setOf("रुकें", "रुकावट", "बंद करें"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("भाषा बदलें", "भाषा परिवर्तित करें", "भाषा"),
        VoiceCommand.REPEAT to setOf("दोहराएं", "दोहराना", "दोहराना"),
        VoiceCommand.MUTE to setOf("म्यूट करें", "शांति", "बंद करें"),
        VoiceCommand.UNMUTE to setOf("अनम्यूट करें", "चालू करें", "सक्रिय करें"),
        VoiceCommand.TOPOLOGY to setOf("टोपोलॉजी", "मानचित्र", "टोपोलॉजी देखें"),
        VoiceCommand.DIAGNOSTICS to setOf("निदान", "स्थिति", "माप" )
    )

    private val GUJARATI_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("મોકલીશુ", "પ્રસાર", "પ્રસારિત"),
        VoiceCommand.CANCEL to setOf("રદ", "રોકો", "બંદ"),
        VoiceCommand.DISTRESS to setOf("આપત્ત", "ઇમરજન્સી", "આપત્ત"),
        VoiceCommand.ALERT to setOf("ચેતવણી", "ધ્યાન", "સાવધાન"),
        VoiceCommand.STATUS to setOf("સ્થિતિ", "રિપોર્ટ", "સ્થિતિ કહો"),
        VoiceCommand.PTT_MODE to setOf("પીટીટી મોડ", "પીટીટી", "પુશ ટુ ટૉક"),
        VoiceCommand.CONTINUOUS_MODE to setOf("સતત મોડ", "સતત", "સતત સાંભળો"),
        VoiceCommand.STOP to setOf("રોકો", "વિરામ", "બંદ"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("ભાષા બદલો", "ભાષા પરિવર્તિત", "ભાષા"),
        VoiceCommand.REPEAT to setOf("પુનરાવર્તિ", "પુનરાવર્તિ", "પુનરાવર્તિ"),
        VoiceCommand.MUTE to setOf("મ્યુટ કરો", "શાંત", "બંદ"),
        VoiceCommand.UNMUTE to setOf("અનમ્યુટ કરો", "ચાલુ કરો", "સક્રિય કરો"),
        VoiceCommand.TOPOLOGY to setOf("ટોપોલૉજી", "મૅનચિત્ર", "ટોપોલૉજી જુઓ"),
        VoiceCommand.DIAGNOSTICS to setOf("ડાયગ્નોસ્ટિક્સ", "સ્થિતિ", "માપ" )
    )

    private val MARATHI_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("पाठवा", "प्रसारित करा", "प्रसारित"),
        VoiceCommand.CANCEL to setOf("रद्द करा", "रोको", "बंद करा"),
        VoiceCommand.DISTRESS to setOf("आपत्कालीन", "इमरजेंसी", "आपत"),
        VoiceCommand.ALERT to setOf("चेतावणी", "ध्यान द्या", "सावधान"),
        VoiceCommand.STATUS to setOf("स्थिती", "रिपोर्ट", "स्थिती कळा"),
        VoiceCommand.PTT_MODE to setOf("पीटीटी मोड", "पीटीटी", "पुश टू टॉक"),
        VoiceCommand.CONTINUOUS_MODE to setOf("निरंतर मोड", "निरंतर", "निरंतर ऐका"),
        VoiceCommand.STOP to setOf("रोको", "थांब", "बंद"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("भाषा बदला", "भाषा परिवर्तित", "भाषा"),
        VoiceCommand.REPEAT to setOf("पुनरावृत्ती", "पुनरावृत्ती", "पुनरावृत्ती"),
        VoiceCommand.MUTE to setOf("म्यूट करा", "शांती", "बंद"),
        VoiceCommand.UNMUTE to setOf("अनम्यूट करा", "सक्रिय करा", "चालू करा"),
        VoiceCommand.TOPOLOGY to setOf("टोपोलॉजी", "मानचित्र", "टोपोलॉजी पहा"),
        VoiceCommand.DIAGNOSTICS to setOf("डायग्नॉस्टिक्स", "स्थिती", "माप" )
    )

    private val KANNADA_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("ಕಳುಹಿಸಿ", "ಪ್ರಸಾರ", "ಪ್ರಸಾರಿಸು"),
        VoiceCommand.CANCEL to setOf("ರದ್ದುಗೊಳಿಸಿ", "ನಿಲ್ಲಿಸಿ", "ಸ್ಥಗಿತ"),
        VoiceCommand.DISTRESS to setOf("ತುರ್ತು", "ಇಮರ್ಜೆನ್ಸಿ", "ತುರ್ತು"),
        VoiceCommand.ALERT to setOf("ಎಚ್ಚರಿಕೆ", "ಗಮನ", "ಜಾಗೃತ"),
        VoiceCommand.STATUS to setOf("ಸ್ಥಿತಿ", "ವರದಿ", "ಸ್ಥಿತಿ ತಿಳಿಸು"),
        VoiceCommand.PTT_MODE to setOf("ಪಿಟಿಟಿ ಮೋಡ್", "ಪಿಟಿಟಿ", "ಪುಶ್ ಟು ಟಾಕ್"),
        VoiceCommand.CONTINUOUS_MODE to setOf("ನಿರಂತರ ಮೋಡ್", "ನಿರಂತರ", "ನಿರಂತರ ಆಲಿಸು"),
        VoiceCommand.STOP to setOf("ನಿಲ್ಲಿಸು", "ಸ್ಥಗಿತ", "ಬಂದ"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("ಭಾಷೆ ಬದಲಾಯಿಸಿ", "ಭಾಷೆ ಬದಲಾಯಿಸಿ", "ಭಾಷೆ"),
        VoiceCommand.REPEAT to setOf("ಪುನರಾವರ್ತನೆ", "ಪುನರಾವರ್ತನೆ", "ಪುನರಾವರ್ತನೆ"),
        VoiceCommand.MUTE to setOf("ಮ್ಯೂಟ್ ಮಾಡಿ", "ಶಾಂತಿ", "ಸ್ಥಗಿತ"),
        VoiceCommand.UNMUTE to setOf("ಅನ್ಮ್ಯೂಟ್ ಮಾಡಿ", "ಸಕ್ರಿಯಗೊಳಿಸಿ", "ಚಾಲನೆಗೊಳಿಸಿ"),
        VoiceCommand.TOPOLOGY to setOf("ಟೋಪೊಲಾಜಿ", "ಮ್ಯಾಪ್", "ಟೋಪೊಲಾಜಿ ನೋಡಿ"),
        VoiceCommand.DIAGNOSTICS to setOf("ಡಯಾಗ್ನಾಸ್ಟಿಕ್ಸ್", "ಸ್ಥಿತಿ", "ಮಾಪ" )
    )

    private val MALAYALAM_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("അയയ്ക്കുക", "പ്രചരിപ്പിക്കുക", "പ്രചരണ"),
        VoiceCommand.CANCEL to setOf("റദ്ദാക്കുക", "നിർത്തുക", "അടയ്ക്കുക"),
        VoiceCommand.DISTRESS to setOf("അടിയന്തര", "ഇമർജൻസി", "അടിയന്തരം"),
        VoiceCommand.ALERT to setOf("അലേർട്ട്", "ശ്രദ്ധിക്കുക", "ജാഗ്രത"),
        VoiceCommand.STATUS to setOf("നില", "റിപ്പോർട്ട്", "നില പറയുക"),
        VoiceCommand.PTT_MODE to setOf("പിടിടി മോഡ്", "പിടിടി", "പുഷ് ടു ടോക്ക്"),
        VoiceCommand.CONTINUOUS_MODE to setOf("നിരന്തര മോഡ്", "നിരന്തരം", "നിരന്തരം കേൾക്കുക"),
        VoiceCommand.STOP to setOf("നിർത്തുക", "നിർത്തുക", "അടയ്ക്കുക"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("ഭാഷ മാറ്റുക", "ഭാഷ മാറ്റുക", "ഭാഷ"),
        VoiceCommand.REPEAT to setOf("ആവർത്തികം", "ആവർത്തികം", "ആവർത്തികം"),
        VoiceCommand.MUTE to setOf("മ്യൂട്ട് ചെയ്യുക", "ശാംതി", "അടയ്ക്കുക"),
        VoiceCommand.UNMUTE to setOf("അന്മ്യൂട്ട് ചെയ്യുക", "സജീവമാക്കുക", "സജീവമാക്കുക"),
        VoiceCommand.TOPOLOGY to setOf("ടോപ്പോളജി", "മാപ്പ്", "ടോപ്പോളജി നോക്കുക"),
        VoiceCommand.DIAGNOSTICS to setOf("ഡയഗ്നോസ്ടിക്സ്", "നില", "മാപ" )
    )

    private val TAMIL_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("அனுப்பு", "ஒலிபரப்பு", "பிரசாரம்"),
        VoiceCommand.CANCEL to setOf("ரத்து செய்", "நிறுத்து", "தடுக்க"),
        VoiceCommand.DISTRESS to setOf("அவசர", "வெளிப்பாடு", "பேரிடர"),
        VoiceCommand.ALERT to setOf("எச்சரிக்கை", "கவனி", "பலிசெய்"),
        VoiceCommand.STATUS to setOf("நிலை", "புகாரளி", "நிலையை தெரி"),
        VoiceCommand.PTT_MODE to setOf("பிபிடி முறைமை", "பிபிடி", "பீப் தொடர்பு"),
        VoiceCommand.CONTINUOUS_MODE to setOf("தொடர்ச்சியான முறைமை", "தொடர்ச்சியான", "தொடர்ச்சியான கேட்பது"),
        VoiceCommand.STOP to setOf("நிறுத்து", "போடு", "இறங்கு"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("மொழியை மாற்று", "மொழியை மாற்ற", "மொழி"),
        VoiceCommand.REPEAT to setOf("மீண்டும்", "மீண்டும்", "மீண்டும்"),
        VoiceCommand.MUTE to setOf("முடக்கி", "மௌனம்", "மௌனப்படுத்த"),
        VoiceCommand.UNMUTE to setOf("முடக்கத்தை நீக்கு", "செயல்படுத்து", "செயல்படுத்த"),
        VoiceCommand.TOPOLOGY to setOf("டோப்பாலஜி", "வரைபடம்", "டோப்பாலஜி காண்"),
        VoiceCommand.DIAGNOSTICS to setOf("அறிகையை", "நிலை", "அடையாளமிட" )
    )

    private val TELUGU_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("పంపు", "పంపడం", "ప్రసారం"),
        VoiceCommand.CANCEL to setOf("రద్దు", "ఆపు", "ఆపివేయ"),
        VoiceCommand.DISTRESS to setOf("అత్యవసర", "ముగింప", "పరిస్థితి"),
        VoiceCommand.ALERT to setOf("హెచ్చరిక", "శ్రద్ధతో", "జాగ్రత్త"),
        VoiceCommand.STATUS to setOf("స్థితి", "గుర్తించు", "స్థితిని చెప్పు"),
        VoiceCommand.PTT_MODE to setOf("పిటిటి నమూన", "పిటిటి", "పుష్ టు టాక్"),
        VoiceCommand.CONTINUOUS_MODE to setOf("నిరంతర నమూన", "నిరంతరం", "నిరంతరం వినండి"),
        VoiceCommand.STOP to setOf("ఆపు", "విరామ", "ఆగు"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("భాష మార్చు", "భాష మార్చు", "భాష"),
        VoiceCommand.REPEAT to setOf("పునరావృత", "పునరావృత", "పునరావృత"),
        VoiceCommand.MUTE to setOf("మ్యూట్ చెయ్యండి", "శాంతం", "మూసిపో"),
        VoiceCommand.UNMUTE to setOf("అన్మ్యూట్ చేయండి", "క్రియాశీలం", "క్రియాశీలం చేయండి"),
        VoiceCommand.TOPOLOGY to setOf("టోపోలాజీ", "కవర్", "టోపోలాజీ చూడండి"),
        VoiceCommand.DIAGNOSTICS to setOf("నిర్ధారణ", "స్థితి", "గుర్తించు" )
    )

    private val ODIA_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("ପାଂଚା କର଻ଢ଼ଣ୍", "ପ્ରବାହ୍", "ପ୍ରସାର"),
        VoiceCommand.CANCEL to setOf("ରଦ୍ଦ୍ତ୍", "ରୋକ୍", "ବଣ୍ଦ୍"),
        VoiceCommand.DISTRESS to setOf("ଐପୋଟ୍", "ଇମେର୍ଜ୍ନ୍ସ୍", "ଐପୋଟ୍"),
        VoiceCommand.ALERT to setOf("ଚେତାଵୋନ୍ସ", "ଗମ୍ନ୍", "ସୌଭାଗ୍ବ୍କ୍"),
        VoiceCommand.STATUS to setOf("ସ୍ଥଟୁଜ", "ବାଙ୍କୋ", "ସ୍ଥଟୁଜ କୁହୋ"),
        VoiceCommand.PTT_MODE to setOf("ପୀଟତଇ ମୋଡ", "ପୀଟତଇ", "ପୁଶ୍ ଟୂ ଟୋକ୍"),
        VoiceCommand.CONTINUOUS_MODE to setOf("ନଖଵ୊ଇୁଝୁ ମୋଡ", "ନଖଵ୊ଇୁଝୁ", "ନଖଵ୊ଇୁଝୁ ସୋମ୍୍ପୋଇଷ୍ କୁଣ୍ଜ୍"),
        VoiceCommand.STOP to setOf("ରୋକ୍", "ତ୍୍ରୋଔ", "ବଣ୍ଦ୍"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("ଭାଁଁହୋ ବଦଳପେଛୌ", "ଭାଁଁହୋ ପରିବର୍ତ୍ତ୍ନ୍", "ଭାଁଁହୋ"),
        VoiceCommand.REPEAT to setOf("ପୁନରଆଵୃତ୍ଣ୍", "ପୁନରଆଵୃତ୍ଣ୍", "ପୁନରଆଵୃତ୍ଣ୍"),
        VoiceCommand.MUTE to setOf("ମ୍ୟୂଟେ କରୋ", "ଶାଂତ୍", "ବଣ୍ଦ୍"),
        VoiceCommand.UNMUTE to setOf("ଅନ୍ ମ୍ୟୂଟେ କରୋ", "ସ୕କ୍ର୍ଯ୍ତ୍", "ସ୕କ୍ର୍ୟ୍ କରୋ"),
        VoiceCommand.TOPOLOGY to setOf("ଟୋପୋଲୁଜୁ", "ମୋପା", "ଟୋପୋଲୁଜୁ କୁଣ୍ଜ୍"),
        VoiceCommand.DIAGNOSTICS to setOf("ଡ୍ୟାଗୋନ୍ସ୍ଟିକ୍ସ", "ସ୍ଥଟୁଜ", "ମୋପ" )
    )

    private val BENGALI_PHRASE_MAP = mapOf(
        VoiceCommand.SEND to setOf("পাঠাও", "প্রসার", "প্রচার"),
        VoiceCommand.CANCEL to setOf("বাতিল", "থামাও", "স্টপ"),
        VoiceCommand.DISTRESS to setOf("জরুরি", "মুহূর্ত", "আপদ"),
        VoiceCommand.ALERT to setOf("সতর্কীকরণ", "বিজ্ঞপ্তি", "মনোযোগ"),
        VoiceCommand.STATUS to setOf("অবস্থা", "রিপোর্ট", "অবস্থাটি বল"),
        VoiceCommand.PTT_MODE to setOf("পিটিটি মোড", "পিটিটি", "পুশ টু টক"),
        VoiceCommand.CONTINUOUS_MODE to setOf("নিরন্তর মোড", "নিরন্তর", "নিরন্তর শুনুন"),
        VoiceCommand.STOP to setOf("স্টপ", "বন্ধ", "রুকুন"),
        VoiceCommand.SWITCH_LANGUAGE to setOf("ভাষা পরিবর্তন", "ভাষা পরিবর্তন", "ভাষা"),
        VoiceCommand.REPEAT to setOf("পুনরাবৃত্তি", "পুনরাবৃত্তি", "পুনরাবৃত্তি"),
        VoiceCommand.MUTE to setOf("নীরব", "শান্ত", "নীরব থাকুন"),
        VoiceCommand.UNMUTE to setOf("সক্রিয়", "চালু করুন", "সক্রিয় করুন"),
        VoiceCommand.TOPOLOGY to setOf("টপোলজি", "মানচিত্র", "টপোলজি দেখুন"),
        VoiceCommand.DIAGNOSTICS to setOf("ডায়াগনস্টিক্স", "অবস্থা", "পরিমাপ" )
    )
}