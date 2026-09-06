package org.sih.itantra.ml.stt

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
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device instrumented test for Dolphin CTC & Whisper STT inference.
 *
 * Prerequisites:
 *   adb push <wav> /data/local/tmp/test_hindi.wav   (etc. for each language)
 *   Dolphin model at: files/models/stt/dolphin/
 *   Whisper model at: files/models/stt/whisper-tiny/
 */
@RunWith(AndroidJUnit4::class)
class DolphinSttInstrumentedTest {

    private val tag = "DolphinSttTest"
    private lateinit var modelAssetManager: ModelAssetManager
    private lateinit var recognizer: SherpaOnnxSpeechRecognizer

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        modelAssetManager = ModelAssetManager(ctx)
        recognizer = SherpaOnnxSpeechRecognizer(ctx, modelAssetManager)
        Log.i(tag, "===== DOLPHIN STT ON-DEVICE TEST =====")
        Log.i(tag, "isDolphinSttReady: ${modelAssetManager.isDolphinSttReady()}")
        Log.i(tag, "isWhisperSttReady: ${modelAssetManager.isWhisperSttReady()}")
    }

    @Test
    fun testDolphinModelInitialization() {
        assertTrue("Dolphin model file must exist", modelAssetManager.dolphinModelFile.exists())
        assertTrue("Dolphin tokens file must exist", modelAssetManager.dolphinTokensFile.exists())
        assertTrue("isDolphinSttReady must be true", modelAssetManager.isDolphinSttReady())
        val ok = recognizer.initEngine(IndicLanguage.HINDI)
        Log.i(tag, "initEngine(HINDI): $ok")
        assertTrue("Dolphin CTC must init for Hindi", ok)
    }

    @Test fun testHindiStt() { doTestLang(IndicLanguage.HINDI, "/data/local/tmp/test_hindi.wav", "IndicConformer") }
    @Test fun testBengaliStt() { doTestLang(IndicLanguage.BENGALI, "/data/local/tmp/test_bn_in.wav", "IndicConformer") }
    @Test fun testTamilStt() { doTestLang(IndicLanguage.TAMIL, "/data/local/tmp/test_ta_in.wav", "IndicConformer") }
    @Test fun testTeluguStt() { doTestLang(IndicLanguage.TELUGU, "/data/local/tmp/test_te_in.wav", "IndicConformer") }
    @Test fun testMarathiStt() { doTestLang(IndicLanguage.MARATHI, "/data/local/tmp/test_mr_in.wav", "IndicConformer") }
    @Test fun testGujaratiStt() { doTestLang(IndicLanguage.GUJARATI, "/data/local/tmp/test_gu_in.wav", "IndicConformer") }
    @Test fun testOdiaStt() { doTestLang(IndicLanguage.ODIA, "/data/local/tmp/test_or_in.wav", "Dolphin") }
    @Test fun testMalayalamStt() { doTestLang(IndicLanguage.MALAYALAM, "/data/local/tmp/test_ml_in.wav", "IndicConformer") }
    @Test fun testKannadaStt() { doTestLang(IndicLanguage.KANNADA, "/data/local/tmp/test_kn_in.wav", "IndicConformer") }
    @Test fun testEnglishStt() { doTestLang(IndicLanguage.ENGLISH, "/data/local/tmp/test_en_us.wav", "Whisper") }

    @Test
    fun testSilenceReturnsEmpty() {
        runBlocking {
            Log.i(tag, "--- Silence test ---")
            val pcm = ByteArray(16000 * 2 * 2) // 2s zeros
            val r = recognizer.processAudioSegment(pcm, IndicLanguage.HINDI)
            Log.i(tag, "Silence => '${r.text}'")
        }
    }

    private fun doTestLang(lang: IndicLanguage, wavPath: String, engine: String) {
        runBlocking {
            Log.i(tag, "--- ${lang.displayName} STT ($engine) ---")
            val f = File(wavPath)
            if (!f.exists()) {
                Log.w(tag, "SKIP: $wavPath not found")
                return@runBlocking
            }
            val pcm = readWavPcm16k(f)
            val dur = pcm.size / 2.0 / 16000.0
            Log.i(tag, "PCM: ${pcm.size} bytes, ${String.format("%.2f", dur)}s")

            // Check audio RMS
            val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            var maxA: Short = 0; var sq = 0.0
            for (i in 0 until buf.limit()) {
                val s = buf.get(i)
                if (kotlin.math.abs(s.toInt()) > kotlin.math.abs(maxA.toInt())) maxA = s
                sq += s.toDouble() * s.toDouble()
            }
            Log.i(tag, "maxAmp=$maxA RMS=${String.format("%.0f", kotlin.math.sqrt(sq / buf.limit()))}")

            val t0 = System.currentTimeMillis()
            val result = recognizer.processAudioSegment(pcm, lang)
            val ms = System.currentTimeMillis() - t0
            val rtf = if (dur > 0) ms / 1000.0 / dur else 0.0

            Log.i(tag, "==== ${lang.displayName} RESULT ====")
            Log.i(tag, "Text: '${result.text}'")
            Log.i(tag, "Len: ${result.text.length} chars")
            Log.i(tag, "Latency: ${ms}ms RTF: ${String.format("%.3f", rtf)}x")
            Log.i(tag, "========================")

            assertTrue(
                "${lang.displayName} ($engine) must produce non-empty text. Got: '${result.text}'",
                result.text.isNotBlank()
            )
        }
    }

    private fun readWavPcm16k(wavFile: File): ByteArray {
        val raf = RandomAccessFile(wavFile, "r")
        try {
            val riff = ByteArray(4); raf.readFully(riff)
            require(String(riff) == "RIFF") { "Not RIFF" }
            raf.skipBytes(4)
            val wave = ByteArray(4); raf.readFully(wave)
            require(String(wave) == "WAVE") { "Not WAVE" }

            var fmtTag: Short = 0; var nCh: Short = 0; var sr = 0; var bps: Short = 0

            while (raf.filePointer < raf.length()) {
                val id = ByteArray(4); raf.readFully(id)
                val idStr = String(id)
                val szB = ByteArray(4); raf.readFully(szB)
                val sz = ByteBuffer.wrap(szB).order(ByteOrder.LITTLE_ENDIAN).int

                if (idStr == "fmt ") {
                    val d = ByteArray(sz); raf.readFully(d)
                    val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
                    fmtTag = b.short; nCh = b.short; sr = b.int; b.int; b.short; bps = b.short
                    Log.i(tag, "WAV: fmt=$fmtTag ch=$nCh rate=$sr bits=$bps")
                } else if (idStr == "data") {
                    val data = ByteArray(sz); raf.readFully(data)
                    val mono: ShortArray
                    when (fmtTag.toInt()) {
                        1 -> {
                            val n = sz / (bps / 8) / nCh
                            mono = ShortArray(n)
                            val db = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until n) {
                                var s = 0L; for (c in 0 until nCh) s += db.short.toLong()
                                mono[i] = (s / nCh).toShort()
                            }
                        }
                        3 -> {
                            val n = sz / 4 / nCh
                            mono = ShortArray(n)
                            val db = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until n) {
                                var s = 0.0; for (c in 0 until nCh) s += db.float.toDouble()
                                mono[i] = ((s / nCh).coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported WAV fmt: $fmtTag")
                    }
                    val out = if (sr != 16000) {
                        val r = sr.toDouble() / 16000.0
                        val n = (mono.size / r).toInt()
                        ShortArray(n) { mono[((it * r).toInt()).coerceAtMost(mono.size - 1)] }
                    } else mono
                    val res = ByteArray(out.size * 2)
                    val rb = ByteBuffer.wrap(res).order(ByteOrder.LITTLE_ENDIAN)
                    for (s in out) rb.putShort(s)
                    return res
                } else {
                    raf.skipBytes(sz)
                }
            }
            throw IllegalArgumentException("No data chunk")
        } finally { raf.close() }
    }
}
