package org.sih.itantra.core.speech.benchmark.multilang

data class LanguageCapabilityInfo(
    val languageCode: String,
    val languageDisplayName: String,
    val nativeScriptSample: String,
    val sttEngine: String,
    val sttModelName: String,
    val sttQuantization: String,
    val sttSizeMb: Double,
    val sttExpectedRtfArm64: Double,
    val ttsEngine: String,
    val ttsVoiceName: String,
    val ttsSampleRateHz: Int,
    val ttsSizeMb: Double,
    val totalFootprintMb: Double,
    val isFullyOffline: Boolean,
    val technicalNotes: String
)

object MultiLangModelCapabilityMatrix {

    private val CAPABILITIES = listOf(
        LanguageCapabilityInfo(
            languageCode = "hi",
            languageDisplayName = "Hindi",
            nativeScriptSample = "नमस्ते",
            sttEngine = "Sherpa-ONNX Dolphin CTC / Whisper INT8",
            sttModelName = "whisper-tiny-int8-indic / dolphin-ctc",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.12,
            ttsEngine = "Sherpa-ONNX VITS Piper",
            ttsVoiceName = "hi_IN-rohan-medium",
            ttsSampleRateHz = 22050,
            ttsSizeMb = 66.5,
            totalFootprintMb = 170.0,
            isFullyOffline = true,
            technicalNotes = "Fully on-device neural STT and VITS synthesis via native C++ Sherpa-ONNX runtime. Zero network egress."
        ),
        LanguageCapabilityInfo(
            languageCode = "gu",
            languageDisplayName = "Gujarati",
            nativeScriptSample = "નમસ્તે",
            sttEngine = "Sherpa-ONNX Whisper INT8 / IndicConformer",
            sttModelName = "whisper-tiny-int8-indic / gujarati-conformer",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.14,
            ttsEngine = "Sherpa-ONNX VITS Mimic3",
            ttsVoiceName = "gu_IN-cmu-indic",
            ttsSampleRateHz = 16000,
            ttsSizeMb = 76.3,
            totalFootprintMb = 179.8,
            isFullyOffline = true,
            technicalNotes = "Native INT8 neural STT with Mimic3 Gujarati VITS engine. Single resident model eviction active."
        ),
        LanguageCapabilityInfo(
            languageCode = "mr",
            languageDisplayName = "Marathi",
            nativeScriptSample = "नमस्कार",
            sttEngine = "Sherpa-ONNX Whisper INT8 / IndicConformer",
            sttModelName = "whisper-tiny-int8-indic / marathi-conformer",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.13,
            ttsEngine = "Sherpa-ONNX VITS Piper",
            ttsVoiceName = "mr_IN-google-medium",
            ttsSampleRateHz = 22050,
            ttsSizeMb = 76.8,
            totalFootprintMb = 180.3,
            isFullyOffline = true,
            technicalNotes = "Native INT8 neural STT with Piper Marathi VITS engine on-device."
        ),
        LanguageCapabilityInfo(
            languageCode = "kn",
            languageDisplayName = "Kannada",
            nativeScriptSample = "ನಮಸ್ಕಾರ",
            sttEngine = "Sherpa-ONNX Whisper INT8 / IndicConformer",
            sttModelName = "whisper-tiny-int8-indic / kannada-conformer",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.15,
            ttsEngine = "Sherpa-ONNX VITS MMS Meta",
            ttsVoiceName = "vits-mms-kan-meta",
            ttsSampleRateHz = 16000,
            ttsSizeMb = 114.0,
            totalFootprintMb = 217.5,
            isFullyOffline = true,
            technicalNotes = "Meta MMS Kannada neural VITS paired with INT8 on-device STT."
        ),
        LanguageCapabilityInfo(
            languageCode = "ml",
            languageDisplayName = "Malayalam",
            nativeScriptSample = "നമസ്കാരം",
            sttEngine = "Sherpa-ONNX Whisper INT8 / IndicConformer",
            sttModelName = "whisper-tiny-int8-indic / malayalam-conformer",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.14,
            ttsEngine = "Sherpa-ONNX VITS Piper",
            ttsVoiceName = "ml_IN-arjun-medium",
            ttsSampleRateHz = 22050,
            ttsSizeMb = 60.0,
            totalFootprintMb = 163.5,
            isFullyOffline = true,
            technicalNotes = "Piper Malayalam VITS neural engine paired with INT8 on-device STT."
        ),
        LanguageCapabilityInfo(
            languageCode = "ta",
            languageDisplayName = "Tamil",
            nativeScriptSample = "வணக்கம்",
            sttEngine = "Sherpa-ONNX Whisper INT8 / IndicConformer",
            sttModelName = "whisper-tiny-int8-indic / tamil-conformer",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.14,
            ttsEngine = "Sherpa-ONNX VITS MMS Meta",
            ttsVoiceName = "vits-mms-tam-meta",
            ttsSampleRateHz = 16000,
            ttsSizeMb = 114.0,
            totalFootprintMb = 217.5,
            isFullyOffline = true,
            technicalNotes = "Meta MMS Tamil neural VITS paired with INT8 on-device STT."
        ),
        LanguageCapabilityInfo(
            languageCode = "te",
            languageDisplayName = "Telugu",
            nativeScriptSample = "నమస్కారం",
            sttEngine = "Sherpa-ONNX Whisper INT8 / IndicConformer",
            sttModelName = "whisper-tiny-int8-indic / telugu-conformer",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.13,
            ttsEngine = "Sherpa-ONNX VITS Piper",
            ttsVoiceName = "te_IN-maya-medium",
            ttsSampleRateHz = 22050,
            ttsSizeMb = 60.0,
            totalFootprintMb = 163.5,
            isFullyOffline = true,
            technicalNotes = "Piper Telugu VITS neural engine paired with INT8 on-device STT."
        ),
        LanguageCapabilityInfo(
            languageCode = "or",
            languageDisplayName = "Odia",
            nativeScriptSample = "ନମସ୍କାର",
            sttEngine = "Android OS SpeechRecognizer Fallback",
            sttModelName = "android-speech-or-in / meta-mms",
            sttQuantization = "OS / INT8",
            sttSizeMb = 0.0,
            sttExpectedRtfArm64 = 0.16,
            ttsEngine = "Sherpa-ONNX VITS MMS Meta",
            ttsVoiceName = "vits-mms-ory-meta",
            ttsSampleRateHz = 16000,
            ttsSizeMb = 114.0,
            totalFootprintMb = 114.0,
            isFullyOffline = true,
            technicalNotes = "Meta MMS Odia neural VITS for offline acoustic synthesis. STT uses Android OS SpeechRecognizer fallback because Odia is not in Whisper 99-language set."
        ),
        LanguageCapabilityInfo(
            languageCode = "bn",
            languageDisplayName = "Bengali",
            nativeScriptSample = "নমস্কার",
            sttEngine = "Sherpa-ONNX Whisper INT8 / IndicConformer",
            sttModelName = "whisper-tiny-int8-indic / bengali-conformer",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.13,
            ttsEngine = "Sherpa-ONNX VITS Piper",
            ttsVoiceName = "bn_IN-google-medium",
            ttsSampleRateHz = 22050,
            ttsSizeMb = 76.8,
            totalFootprintMb = 180.3,
            isFullyOffline = true,
            technicalNotes = "Piper Bengali VITS neural engine paired with INT8 on-device STT."
        ),
        LanguageCapabilityInfo(
            languageCode = "en",
            languageDisplayName = "English",
            nativeScriptSample = "Hello",
            sttEngine = "Sherpa-ONNX Whisper-Tiny INT8",
            sttModelName = "whisper-tiny-en-int8",
            sttQuantization = "INT8 ONNX",
            sttSizeMb = 103.5,
            sttExpectedRtfArm64 = 0.10,
            ttsEngine = "Sherpa-ONNX VITS Piper",
            ttsVoiceName = "en_US-lessac-medium",
            ttsSampleRateHz = 22050,
            ttsSizeMb = 63.2,
            totalFootprintMb = 166.7,
            isFullyOffline = true,
            technicalNotes = "Whisper-Tiny English INT8 paired with Piper Lessac VITS synthesis."
        )
    )

    fun getAllCapabilities(): List<LanguageCapabilityInfo> = CAPABILITIES

    fun getByLanguage(languageCode: String): LanguageCapabilityInfo? =
        CAPABILITIES.find { it.languageCode.equals(languageCode, ignoreCase = true) }

    fun getTotalFootprintMb(): Double =
        CAPABILITIES.sumOf { it.totalFootprintMb }

    fun getSupportedLanguageCodes(): List<String> =
        CAPABILITIES.map { it.languageCode }
}
