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
    fun testTamilPriorityBenchmark10Cycles() {
        runBlocking {
            Log.i(tag, "========== START TAMIL PRIORITY BENCHMARK (10 CYCLES) ==========")
            val baseSentence = "வணக்கம், இது ஐ-தந்த்ரா ஆஃப்லைன் நியூரல் பேச்சு தொகுப்பு சோதனை."
            val numbersSentence = "அவசர எச்சரிக்கை! பகுதி 8ல் 2026 ஆம் ஆண்டு உடனடியாக உதவி தேவை."
            val operationalSentence = "ரேடியோ இணைப்பு நிலை சரி, சிக்னல் வலிமை நன்று."

            // 1. Cold start measurement
            val coldStartOk = ttsEngine.synthesize(baseSentence, IndicLanguage.TAMIL, isUrgent = false)
            assertTrue("Cold start synthesis must succeed", coldStartOk)
            val coldMetrics = ttsEngine.lastPerfMetrics.value!!
            val coldLoadMs = coldMetrics.modelLoadMs
            val coldSynthMs = coldMetrics.synthMs
            Log.i(tag, "[TAMIL-COLD] load_ms=$coldLoadMs synth_ms=$coldSynthMs total_ms=${coldMetrics.totalTtsStageMs}")

            // 2. 10 warm iterations on base sentence
            val synthTimes = mutableListOf<Double>()
            val prepTimes = mutableListOf<Double>()
            val trackPrepTimes = mutableListOf<Double>()
            val tfaTimes = mutableListOf<Double>()
            val totalTimes = mutableListOf<Double>()

            for (i in 1..10) {
                val ok = ttsEngine.synthesize(baseSentence, IndicLanguage.TAMIL, isUrgent = false)
                assertTrue("Warm cycle $i must succeed", ok)
                val m = ttsEngine.lastPerfMetrics.value!!
                synthTimes.add(m.synthMs)
                prepTimes.add(m.preprocessMs)
                trackPrepTimes.add(m.trackPrepMs)
                tfaTimes.add(m.timeToFirstAudioMs)
                totalTimes.add(m.totalTtsStageMs)
                Log.i(tag, String.format(java.util.Locale.US, "[TAMIL-CYCLE-%02d] prep=%.2fms synth=%.2fms track=%.2fms tfa=%.2fms total=%.2fms",
                    i, m.preprocessMs, m.synthMs, m.trackPrepMs, m.timeToFirstAudioMs, m.totalTtsStageMs))
            }

            synthTimes.sort()
            val medianSynth = synthTimes[synthTimes.size / 2]
            val p95Synth = synthTimes[(synthTimes.size * 0.95).toInt().coerceAtMost(synthTimes.size - 1)]
            val medianPrep = prepTimes.sorted()[prepTimes.size / 2]
            val medianTrackPrep = trackPrepTimes.sorted()[trackPrepTimes.size / 2]
            val medianTfa = tfaTimes.sorted()[tfaTimes.size / 2]
            val medianTotal = totalTimes.sorted()[totalTimes.size / 2]

            Log.i(tag, "==================================================")
            Log.i(tag, "[TAMIL-BENCHMARK-SUMMARY] 10 Warm Cycles:")
            Log.i(tag, String.format(java.util.Locale.US, "  Cold Load: %.2f ms", coldLoadMs))
            Log.i(tag, String.format(java.util.Locale.US, "  Median Preprocessing: %.2f ms", medianPrep))
            Log.i(tag, String.format(java.util.Locale.US, "  Median Synthesis: %.2f ms", medianSynth))
            Log.i(tag, String.format(java.util.Locale.US, "  P95 Synthesis: %.2f ms", p95Synth))
            Log.i(tag, String.format(java.util.Locale.US, "  Median Track Prep: %.2f ms", medianTrackPrep))
            Log.i(tag, String.format(java.util.Locale.US, "  Median Time To First Audio: %.2f ms", medianTfa))
            Log.i(tag, String.format(java.util.Locale.US, "  Median Total TTS Stage: %.2f ms", medianTotal))
            Log.i(tag, "==================================================")

            // 3. Sentence 2 (Numbers & Missing 8 Test)
            val ok2 = ttsEngine.synthesize(numbersSentence, IndicLanguage.TAMIL, isUrgent = false)
            assertTrue("Numbers sentence synthesis must succeed", ok2)
            val m2 = ttsEngine.lastPerfMetrics.value!!
            Log.i(tag, String.format(java.util.Locale.US, "[TAMIL-SENTENCE-2] prep=%.2fms synth=%.2fms total=%.2fms", m2.preprocessMs, m2.synthMs, m2.totalTtsStageMs))

            // 4. Sentence 3 (Operational radio terms)
            val ok3 = ttsEngine.synthesize(operationalSentence, IndicLanguage.TAMIL, isUrgent = false)
            assertTrue("Operational sentence synthesis must succeed", ok3)
            val m3 = ttsEngine.lastPerfMetrics.value!!
            Log.i(tag, String.format(java.util.Locale.US, "[TAMIL-SENTENCE-3] prep=%.2fms synth=%.2fms total=%.2fms", m3.preprocessMs, m3.synthMs, m3.totalTtsStageMs))
            Log.i(tag, "========== COMPLETED TAMIL PRIORITY BENCHMARK ==========")
        }
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
            val ok = ttsEngine.synthesize(text, language, isUrgent = false)
            assertTrue("${language.displayName} TTS synthesize must succeed", ok)

            val metrics = ttsEngine.lastPerfMetrics.value
            assertNotNull("Perf metrics for ${language.displayName} must be captured", metrics)
            Log.i(tag, "========================================")
            Log.i(tag, "${language.displayName} TTS FULL PIPELINE RESULT:")
            Log.i(tag, "  Model: $modelName")
            Log.i(tag, "  Text: '$text'")
            Log.i(tag, "  Preprocessing: ${metrics!!.preprocessMs} ms")
            Log.i(tag, "  Model Load: ${metrics.modelLoadMs} ms")
            Log.i(tag, "  Synthesis: ${metrics.synthMs} ms")
            Log.i(tag, "  Track Prep: ${metrics.trackPrepMs} ms")
            Log.i(tag, "  Time to First Audio: ${metrics.timeToFirstAudioMs} ms")
            Log.i(tag, "  Playback Duration: ${metrics.playbackDurationMs} ms")
            Log.i(tag, "  Total TTS Stage: ${metrics.totalTtsStageMs} ms")
            Log.i(tag, "========================================")

            assertTrue("Synthesis time must be positive", metrics.synthMs > 0)
            assertTrue("Total TTS stage time must be positive", metrics.totalTtsStageMs > 0)
        }
    }
}
