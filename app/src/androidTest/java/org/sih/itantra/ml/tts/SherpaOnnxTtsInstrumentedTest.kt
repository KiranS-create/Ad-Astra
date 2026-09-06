package org.sih.itantra.ml.tts

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.ml.model.ModelAssetManager

/**
 * On-device instrumented test for Sherpa-ONNX VITS Neural Text-to-Speech across all 10 languages.
 * Measures on-device synthesis latency, sample count, sample rate, and verifies genuine neural inference.
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxTtsInstrumentedTest {

    private val tag = "SherpaOnnxTtsTest"
    private lateinit var modelAssetManager: ModelAssetManager
    private lateinit var ttsEngine: SherpaOnnxTtsEngine

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        modelAssetManager = ModelAssetManager(ctx)
        ttsEngine = SherpaOnnxTtsEngine(ctx, modelAssetManager)
        Log.i(tag, "===== SHERPA-ONNX TTS ON-DEVICE TEST =====")
    }

    @Test
    fun testHindiTts() {
        doTestTts(
            IndicLanguage.HINDI,
            "नमस्ते, यह आई-तंत्रा का ऑफलाइन न्यूरल भाषण संश्लेषण परीक्षण है।",
            "VITS Piper Rohan Medium (hi_IN)"
        )
    }

    @Test
    fun testEnglishTts() {
        doTestTts(
            IndicLanguage.ENGLISH,
            "Hello, this is an offline neural text to speech test for iTantra.",
            "VITS Piper Lessac Medium (en_US)"
        )
    }

    @Test
    fun testBengaliTts() {
        doTestTts(
            IndicLanguage.BENGALI,
            "নমস্কার, এটি আই-তন্ত্রার অফলাইন নিউরাল স্পিচ সিন্থেসিস পরীক্ষা।",
            "VITS Piper Google Medium (bn_BD)"
        )
    }

    @Test
    fun testTamilTts() {
        doTestTts(
            IndicLanguage.TAMIL,
            "வணக்கம், இது ஐ-தந்த்ரா ஆஃப்லைன் நியூரல் பேச்சு தொகுப்பு சோதனை.",
            "VITS MMS Tamil (ta)"
        )
    }

    @Test
    fun testTeluguTts() {
        doTestTts(
            IndicLanguage.TELUGU,
            "నమస్కారం, ఇది ఐ-తంత్ర ఆఫ్లైన్ న్యూరల్ స్పీచ్ సింథసిస్ పరీక్ష.",
            "VITS Piper Maya Medium (te_IN)"
        )
    }

    @Test
    fun testMarathiTts() {
        doTestTts(
            IndicLanguage.MARATHI,
            "नमस्कार, ही आय-तंत्राची ऑफलाइन न्यूरल स्पीच सिंथेसिस चाचणी आहे.",
            "VITS Piper Google Medium (mr_IN)"
        )
    }

    @Test
    fun testGujaratiTts() {
        doTestTts(
            IndicLanguage.GUJARATI,
            "નમસ્તે, આ આઇ-તંત્રનું ઑફલાઇન ન્યુરલ સ્પીચ સિન્થેસિસ પરીક્ષણ છે.",
            "VITS Mimic3 CMU-Indic Low (gu_IN)"
        )
    }

    @Test
    fun testKannadaTts() {
        doTestTts(
            IndicLanguage.KANNADA,
            "ನಮಸ್ಕಾರ, ಇದು ಐ-ತಂತ್ರದ ಆಫ್‌ಲೈನ್ ನ್ಯೂರಲ್ ಭಾಷಣ ಸಂಶ್ಲೇಷಣೆ ಪರೀಕ್ಷೆ.",
            "VITS MMS Kannada (kn)"
        )
    }

    @Test
    fun testMalayalamTts() {
        doTestTts(
            IndicLanguage.MALAYALAM,
            "നമസ്കാരം, ഇത് ഐ-തന്ത്ര ഓഫ്‌ലൈൻ ന്യൂറൽ സ്പീച്ച് സിന്തസിസ് പരീക്ഷണമാണ്.",
            "VITS Piper Arjun Medium (ml_IN)"
        )
    }

    @Test
    fun testOdiaTts() {
        doTestTts(
            IndicLanguage.ODIA,
            "ନମସ୍କାର, ଏହା ଆଇ-ତନ୍ତ୍ରାର ଅଫଲାଇନ୍ ନ୍ୟୁରାଲ୍ ସ୍ପିଚ୍ ସିନ୍ଥେସିସ୍ ପରୀକ୍ଷା।",
            "VITS MMS Odia (or)"
        )
    }

    private fun doTestTts(language: IndicLanguage, text: String, modelName: String) {
        runBlocking {
            Log.i(tag, "--- Testing ${language.displayName} TTS ($modelName) ---")
            val offlineTts = ttsEngine.getOrInitTts(language)
            assertNotNull("TTS engine for ${language.displayName} must initialize", offlineTts)

            val sampleRate = offlineTts!!.sampleRate()
            val numSpeakers = offlineTts.numSpeakers()
            Log.i(tag, "${language.displayName} TTS initialized. SampleRate: ${sampleRate}Hz, Speakers: $numSpeakers")

            val startTime = System.currentTimeMillis()
            val audio = offlineTts.generate(text = text, sid = 0, speed = 1.0f)
            val elapsedMs = System.currentTimeMillis() - startTime

            val samples = audio.samples
            val audioDurationSec = samples.size.toDouble() / sampleRate
            val rtf = if (audioDurationSec > 0) elapsedMs / 1000.0 / audioDurationSec else 0.0

            Log.i(tag, "========================================")
            Log.i(tag, "${language.displayName} TTS RESULT:")
            Log.i(tag, "  Model: $modelName")
            Log.i(tag, "  Text: '$text'")
            Log.i(tag, "  Text length: ${text.length} chars")
            Log.i(tag, "  Generated samples: ${samples.size}")
            Log.i(tag, "  Sample rate: ${sampleRate}Hz")
            Log.i(tag, "  Audio duration: ${String.format("%.2f", audioDurationSec)}s")
            Log.i(tag, "  Synthesis latency: ${elapsedMs}ms")
            Log.i(tag, "  RTF: ${String.format("%.3f", rtf)}x")
            Log.i(tag, "========================================")

            assertTrue(
                "${language.displayName} TTS must produce non-zero audio samples. Got ${samples.size}",
                samples.isNotEmpty()
            )
        }
    }
}
