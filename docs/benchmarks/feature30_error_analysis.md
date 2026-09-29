# Feature 30 — Multilingual STT Real Error Analysis

> **Dataset**: 250 Evaluated Tactical Utterances across 10 Languages

## 1. Error Category Classification per Language

| Language | Substitutions | Deletions | Insertions | Number Errors | Tactical Entity Errors |
|---|---|---|---|---|---|
| **English (en)** | 44 | 23 | 9 | 0 | 18 |
| **Hindi (hi)** | 162 | 58 | 20 | 0 | 25 |
| **Gujarati (gu)** | 25 | 153 | 0 | 0 | 25 |
| **Marathi (mr)** | 138 | 49 | 13 | 0 | 25 |
| **Kannada (kn)** | 38 | 110 | 0 | 0 | 25 |
| **Malayalam (ml)** | 147 | 16 | 28 | 0 | 25 |
| **Tamil (ta)** | 46 | 107 | 1 | 0 | 25 |
| **Telugu (te)** | 148 | 36 | 24 | 0 | 25 |
| **Bengali (bn)** | 124 | 51 | 4 | 0 | 25 |
| **Odia (or)** | 32 | 137 | 0 | 0 | 25 |

## 2. Top Observed Failure Patterns & Recommended Fixes

### English (en)
- Missed token ['nodes', 'channel one', 'contact'] -> Hyp: 'All nodes maintain contac'
- Missed token ['weather', 'visibility normal'] -> Hyp: 'Whether is clear and the '
- Missed token ['standby', 'instructions'] -> Hyp: 'Stand by for next technic'
- Missed token ['sector four', 'three', 'injured'] -> Hyp: 'sector four reports three'
- Missed token ['twelve', 'packets remaining'] -> Hyp: 'We have 12 supply packets'
- Missed token ['twenty five', 'personnel'] -> Hyp: 'total 25 personnel presen'
- Missed token ['battery', 'forty'] -> Hyp: 'Battery level is 40%.'
- Missed token ['latitude 12.9', 'longitude 77.5'] -> Hyp: 'Position ladder to 12.9 L'
- Missed token ['waypoint', '13.1 N', '76.0 E'] -> Hyp: '8.13.1 North 76.0 East'
- Missed token ['77.2', 'coordinates'] -> Hyp: 'Mark Ordnett 77.2 on MAP'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Hindi (hi)
- Missed token ['गश्ती दल', 'वेपॉइंट'] -> Hyp: 'Team Yaagashty Dalve Poin'
- Missed token ['नोड्स', 'चैनल एक', 'संपर्क'] -> Hyp: 'subi notes channel 1 per '
- Missed token ['मौसम', 'दृश्यता सामान्य'] -> Hyp: 'Mostum is a lot of drama '
- Missed token ['सुरक्षित', 'स्थान'] -> Hyp: 'Hans Rakshits Tanbar Bhaj'
- Missed token ['निर्देश', 'प्रतीक्षा'] -> Hyp: 'Agla ner dish milletakta '
- Missed token ['सेक्टर चार', 'तीन', 'घायल'] -> Hyp: 'sector 4-3-3-gile operato'
- Missed token ['बारह', 'पैकेट शेष'] -> Hyp: 'our past barha packet shi'
- Missed token ['पच्चीस', 'जवान'] -> Hyp: 'Group has been the best o'
- Missed token ['बैटरी', 'चालीस'] -> Hyp: 'battery kuster chalees ko'
- Missed token ['पांच', 'काफिले'] -> Hyp: '5.5.5.5.5.5'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Gujarati (gu)
- Missed token ['પેટ્રોલિંગ જૂથ', 'આગળ'] -> Hyp: '[MARAN]'
- Missed token ['નોડ', 'ચેનલ એક', 'સંપર્ક'] -> Hyp: '[silence]'
- Missed token ['વાતાવરણ', 'સામાન્ય'] -> Hyp: '[MARAN]'
- Missed token ['સુરક્ષિત સ્થળે'] -> Hyp: '[MARAN]'
- Missed token ['સૂચના', 'રાહ જુઓ'] -> Hyp: '[MARAN]'
- Missed token ['સેક્ટર ચાર', 'ત્રણ', 'ઈજાગ્રસ્ત'] -> Hyp: '[MARAN]'
- Missed token ['બાર', 'પેકેટ બાકી'] -> Hyp: '[MARAN]'
- Missed token ['પચીસ', 'હાજર'] -> Hyp: '[Buskeh]'
- Missed token ['બેટરી', 'ચાલીસ'] -> Hyp: '[MARAN]'
- Missed token ['પાંચ વાહનો', 'કાફલામાં'] -> Hyp: '[MARAN]'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Marathi (mr)
- Missed token ['गस्ती पथक', 'वेपॉईंट'] -> Hyp: 'Samhigasthi pathuk way po'
- Missed token ['नोड्स', 'चॅनल एक', 'संपर्क'] -> Hyp: 'serv Nos channel 1were sa'
- Missed token ['हवामान', 'सामान्य'] -> Hyp: 'Hava man sua chahe anit p'
- Missed token ['सुरक्षित', 'ठिकाणी'] -> Hyp: 'Amhisura kshitki kani poh'
- Missed token ['आदेश', 'प्रतीक्षा'] -> Hyp: 'Pudhil AD-Shi-Parenta-Ret'
- Missed token ['सेक्टर चार', 'तीन', 'जखमी'] -> Hyp: 'Sektar 4 Madhya 3.1.'
- Missed token ['बारा', 'पॅकेट शिल्लक'] -> Hyp: 'Amcha kadai varapakit sil'
- Missed token ['पंचवीस', 'जवान'] -> Hyp: 'Kirtamadee, ekon Panchwis'
- Missed token ['बॅटरी', 'चाळीस'] -> Hyp: 'Madhli-Chi-Pata Chari-S'
- Missed token ['पाच', 'ताफ्यामध्ये'] -> Hyp: 'ert ert ert ert ert'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Kannada (kn)
- Missed token ['ಗಸ್ತು ಪಡೆ', 'ವೇಪಾಯಿಂಟ್'] -> Hyp: '[SUSCAN]'
- Missed token ['ನೋಡ್\u200cಗಳು', 'ಚಾನೆಲ್ ಒಂದು', 'ಸಂಪರ್ಕ'] -> Hyp: '[Music]'
- Missed token ['ಹವಾಮಾನ', 'ಸಾಮಾನ್ಯ'] -> Hyp: '[Subscribe]'
- Missed token ['ಸುರಕ್ಷಿತ', 'ಸ್ಥಳ'] -> Hyp: '*sad music*'
- Missed token ['ಸೂಚನೆ', 'ಕಾಯಿರಿ'] -> Hyp: '[Music]'
- Missed token ['ಸೆಕ್ಟರ್ ನಾಲ್ಕು', 'ಮೂರು', 'ಗಾಯಗೊಂಡ'] -> Hyp: '*sad music*'
- Missed token ['ಹನ್ನೆರಡು', 'ಪ್ಯಾಕೆಟ್'] -> Hyp: '*sad music*'
- Missed token ['ಇಪ್ಪತ್ತೈದು', 'ಯೋಧರು'] -> Hyp: '*sad music*'
- Missed token ['ಬ್ಯಾಟರಿ', 'ನಲವತ್ತು'] -> Hyp: '[Music]'
- Missed token ['ಐದು ವಾಹನ', 'ಬೆಂಗಾವಲು'] -> Hyp: '[音樂]'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Malayalam (ml)
- Missed token ['പട്രോളിംഗ്', 'വേപോയിന്റ്'] -> Hyp: 'sangham patrolling unit w'
- Missed token ['നോഡുകൾ', 'ചാനൽ ഒന്ന്', 'ബന്ധപ്പെടുക'] -> Hyp: 'illshaanuode galam channe'
- Missed token ['കാലാവസ്ഥ', 'സാധാരണ'] -> Hyp: 'Kaliava Stavectamanah Sta'
- Missed token ['സുരക്ഷിത', 'സ്ഥാനത്ത്'] -> Hyp: 'ndal surrechi da staneta '
- Missed token ['നിർദ്ദേശം', 'കാത്തിരിക്കുക'] -> Hyp: 'oudtana deshamberé kao te'
- Missed token ['സെക്ടർ നാല്', 'മൂന്ന്', 'പരിക്കേറ്റു'] -> Hyp: 'sectornalli mono peka par'
- Missed token ['പന്ത്രണ്ട്', 'പാക്കറ്റുകൾ'] -> Hyp: 'nd nd nd nd nd nd nd nd n'
- Missed token ['ഇരുപത്തിയഞ്ച്', 'അംഗങ്ങൾ'] -> Hyp: 'Vupil A.G. Irivatiyaan Ja'
- Missed token ['ബാറ്ററി', 'നാൽപ്പത്'] -> Hyp: 'Bacterin, Nalpathasathama'
- Missed token ['അഞ്ച് വാഹനം', 'വാഹനവ്യൂഹം'] -> Hyp: '앉a わhanangal わhanabu khat'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Tamil (ta)
- Missed token ['ரோந்து குழு', 'வேபாயிண்ட்'] -> Hyp: 'அம்மமமமமமமமமமமமமமமமமமமமமம'
- Missed token ['முனையங்கள்', 'சேனல் ஒன்று', 'தொடர்பு'] -> Hyp: 'அவர்கள் விடையும் விடையும்'
- Missed token ['வானிலை', 'சாதாரணமாக'] -> Hyp: 'திரும்'
- Missed token ['பாதுகாப்பான', 'இடத்தை'] -> Hyp: 'திரும் திரும் திரும் திரு'
- Missed token ['உத்தரவு', 'காத்திருக்கவும்'] -> Hyp: 'துவில்லையையையையையையையையைய'
- Missed token ['செக்டார் நான்கு', 'மூன்று', 'காயமடைந்துள்ளனர்'] -> Hyp: 'துவில்லையும் துவில்லையும்'
- Missed token ['பன்னிரண்டு', 'பாக்கெட்டுகள்'] -> Hyp: 'துவில்லையும் துவில்லையும்'
- Missed token ['இருபத்தைந்து', 'வீரர்கள்'] -> Hyp: 'துவில்லையும் துவில்லையும்'
- Missed token ['பேட்டரி', 'நாற்பது'] -> Hyp: 'துவில்லையையையையையையையையை'
- Missed token ['ஐந்து வாகனம்', 'அணியில்'] -> Hyp: 'துவில்லையையையையையையையை'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Telugu (te)
- Missed token ['పెట్రోలింగ్', 'వేపాయింట్'] -> Hyp: 'Bundam, 以 Petroling Bunda'
- Missed token ['నోడ్లు', 'ఛానెల్ ఒకటి', 'సంప్రదింపు'] -> Hyp: '按ні node로 channel わけ till'
- Missed token ['వాతావరణం', 'సాధారణం'] -> Hyp: 'わたわанem スパスターンが'
- Missed token ['సురక్షిత', 'ప్రదేశం'] -> Hyp: '名i  surakshita predisanik'
- Missed token ['ఆదేశాలు', 'వేచి ఉండండి'] -> Hyp: 'uper uper uper'
- Missed token ['సెక్టార్ నాలుగు', 'ముగ్గురు', 'గాయపడిన'] -> Hyp: 'Sekta Nalugelu, Mugu Gaip'
- Missed token ['పన్నెండు', 'ప్యాకెట్లు'] -> Hyp: '麻вatha  панnin to packetu'
- Missed token ['ఇరవై ఐదు', 'సభ్యులు'] -> Hyp: 'Samu humlo, mohtum, yerav'
- Missed token ['బ్యాటరీ', 'నలభై'] -> Hyp: 'Bahtari Style alai saatha'
- Missed token ['ఐదు వాహనాలు', 'కాన్వాయ్'] -> Hyp: 'convoylo aidu vahanalu un'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Bengali (bn)
- Missed token ['টহল ইউনিট', 'ওয়েপয়েন্ট'] -> Hyp: 'Dal 8.0 unit, op.dk-acqui'
- Missed token ['নোড', 'চ্যানেল এক', 'যোগাযোগ'] -> Hyp: 'shopnut channel ekid jole'
- Missed token ['আবহাওয়া', 'স্বাভাবিক'] -> Hyp: 'Abha Puriška Ace Abung Pu'
- Missed token ['নিরাপদ', 'স্থানে'] -> Hyp: 'Amaranirappu.httn'
- Missed token ['নির্দেশ', 'অপেক্ষা'] -> Hyp: 'PORBUT INIT DESNAN ASHA P'
- Missed token ['সেক্টর চার', 'তিনজন', 'আহত'] -> Hyp: 'Shector Charlie, Dingen A'
- Missed token ['বারোটি', 'প্যাকেট'] -> Hyp: 'Amadikasi Bharati packet '
- Missed token ['পঁচিশ', 'সদস্য'] -> Hyp: '答e mu t bhochision shadas'
- Missed token ['ব্যাটারি', 'চল্লিশ'] -> Hyp: 'Baderil Matra, Cholish Sh'
- Missed token ['পাঁচটি গাড়ি', 'কনভয়ে'] -> Hyp: '把チキャリこの v12.0.'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

### Odia (or)
- Missed token ['ପାଟ୍ରୋଲିଂ', 'ୱେପଏଣ୍ଟ'] -> Hyp: '(sad music)'
- Missed token ['ନୋଡ', 'ଚ୍ୟାନେଲ୍ ଏକ', 'ଯୋଗାଯୋଗ'] -> Hyp: '(music)'
- Missed token ['ପାଣିପାଗ', 'ସାଧାରଣ'] -> Hyp: '[Music]'
- Missed token ['ସୁରକ୍ଷିତ', 'ସ୍ଥାନ'] -> Hyp: '(music)'
- Missed token ['ନିର୍ଦ୍ଦେଶ', 'ଅପେକ୍ଷା'] -> Hyp: '[Music]'
- Missed token ['ସେକ୍ଟର ଚାରି', 'ତିନି', 'ଆହତ'] -> Hyp: '(music)'
- Missed token ['ବାରଟି', 'ପ୍ୟାକେଟ୍'] -> Hyp: '(music)'
- Missed token ['ପଚିଶ', 'ଯବାନ'] -> Hyp: '(music)'
- Missed token ['ବ୍ୟାଟେରୀ', 'ଚାଳିଶ'] -> Hyp: '(music)'
- Missed token ['ପାଞ୍ଚଟି ଗାଡ଼ି', 'କନଭୟ'] -> Hyp: '(sad music)'
**Recommendation**: Expand regex normalizer for code-switched military numbers and NATO phonetic alphabet.

