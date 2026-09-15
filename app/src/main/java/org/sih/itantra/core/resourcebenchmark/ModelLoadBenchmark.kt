package org.sih.itantra.core.resourcebenchmark

import android.content.Context
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.ml.model.ModelAssetManager
import org.sih.itantra.ml.stt.SherpaOnnxSpeechRecognizer
import org.sih.itantra.ml.tts.SherpaOnnxTtsEngine

/**
 * Benchmarks offline Sherpa-ONNX model lifecycle:
 * Load latency, first inference latency, steady-state latency, and memory footprint.
 */
class ModelLoadBenchmark(
    private val context: Context,
    private val memoryBenchmark: MemoryBenchmark = MemoryBenchmark(context)
) {
    private val modelAssetManager = ModelAssetManager(context)

    data class ModelBenchmarkMetric(
        val language: IndicLanguage,
        val modelType: String, // STT or TTS
        val modelName: String,
        val loadMs: Double,
        val firstInferenceMs: Double,
        val steadyInferenceMs: Double,
        val memoryBeforeLoadKb: Long,
        val memoryAfterLoadKb: Long,
        val memoryPeakKb: Long,
        val memoryAfterReleaseKb: Long
    )

    suspend fun benchmarkSttModel(
        language: IndicLanguage,
        pcmAudioBytes: ByteArray = ByteArray(32000)
    ): ModelBenchmarkMetric {
        System.gc()
        Thread.sleep(100)

        val memBefore = memoryBenchmark.sampleMemory().totalProcessMemoryKb
        val t0 = System.nanoTime()

        val recognizer = SherpaOnnxSpeechRecognizer(context, modelAssetManager)
        recognizer.initEngine(language)
        val t1 = System.nanoTime()
        val loadMs = (t1 - t0) / 1_000_000.0

        val memAfterLoad = memoryBenchmark.sampleMemory().totalProcessMemoryKb

        val t2 = System.nanoTime()
        recognizer.processAudioSegment(pcmAudioBytes, language)
        val t3 = System.nanoTime()
        val firstInferenceMs = (t3 - t2) / 1_000_000.0

        val memPeak = memoryBenchmark.sampleMemory().totalProcessMemoryKb

        var steadySumMs = 0.0
        val steadyRuns = 3
        for (i in 0 until steadyRuns) {
            val ts = System.nanoTime()
            recognizer.processAudioSegment(pcmAudioBytes, language)
            val te = System.nanoTime()
            steadySumMs += (te - ts) / 1_000_000.0
        }
        val steadyInferenceMs = steadySumMs / steadyRuns

        recognizer.release()
        System.gc()
        Thread.sleep(150)
        val memAfterRelease = memoryBenchmark.sampleMemory().totalProcessMemoryKb

        val modelName = when {
            language == IndicLanguage.ENGLISH -> "Whisper-Tiny INT8"
            language == IndicLanguage.ODIA -> "Dolphin Small CTC INT8"
            else -> "IndicConformer NeMo CTC INT8"
        }

        return ModelBenchmarkMetric(
            language = language,
            modelType = "STT",
            modelName = modelName,
            loadMs = loadMs,
            firstInferenceMs = firstInferenceMs,
            steadyInferenceMs = steadyInferenceMs,
            memoryBeforeLoadKb = memBefore,
            memoryAfterLoadKb = memAfterLoad,
            memoryPeakKb = memPeak,
            memoryAfterReleaseKb = memAfterRelease
        )
    }

    suspend fun benchmarkTtsModel(
        language: IndicLanguage,
        testPhrase: String = "iTantra tactical communication check."
    ): ModelBenchmarkMetric {
        System.gc()
        Thread.sleep(100)

        val memBefore = memoryBenchmark.sampleMemory().totalProcessMemoryKb
        val t0 = System.nanoTime()

        val ttsEngine = SherpaOnnxTtsEngine(context, modelAssetManager)
        ttsEngine.prepareLanguage(language)
        val t1 = System.nanoTime()
        val loadMs = (t1 - t0) / 1_000_000.0

        val memAfterLoad = memoryBenchmark.sampleMemory().totalProcessMemoryKb

        val t2 = System.nanoTime()
        ttsEngine.synthesize(testPhrase, language)
        val t3 = System.nanoTime()
        val firstInferenceMs = (t3 - t2) / 1_000_000.0

        val memPeak = memoryBenchmark.sampleMemory().totalProcessMemoryKb

        var steadySumMs = 0.0
        val steadyRuns = 3
        for (i in 0 until steadyRuns) {
            val ts = System.nanoTime()
            ttsEngine.synthesize(testPhrase, language)
            val te = System.nanoTime()
            steadySumMs += (te - ts) / 1_000_000.0
        }
        val steadyInferenceMs = steadySumMs / steadyRuns

        ttsEngine.release()
        System.gc()
        Thread.sleep(150)
        val memAfterRelease = memoryBenchmark.sampleMemory().totalProcessMemoryKb

        val voiceName = when (language) {
            IndicLanguage.HINDI -> "VITS Piper Rohan Medium (hi_IN)"
            IndicLanguage.ENGLISH -> "VITS Piper Lessac Medium (en_US)"
            IndicLanguage.TAMIL -> "VITS MMS Tamil (ta)"
            IndicLanguage.TELUGU -> "VITS Piper Maya Medium (te_IN)"
            IndicLanguage.MALAYALAM -> "VITS Piper Arjun Medium (ml_IN)"
            IndicLanguage.KANNADA -> "VITS MMS Kannada (kn)"
            IndicLanguage.MARATHI -> "VITS Piper Google Medium (mr_IN)"
            IndicLanguage.GUJARATI -> "VITS Mimic3 CMU Indic Low (gu_IN)"
            IndicLanguage.BENGALI -> "VITS Piper Bengali Medium (bn_IN)"
            IndicLanguage.ODIA -> "VITS MMS Odia (or)"
        }

        return ModelBenchmarkMetric(
            language = language,
            modelType = "TTS",
            modelName = voiceName,
            loadMs = loadMs,
            firstInferenceMs = firstInferenceMs,
            steadyInferenceMs = steadyInferenceMs,
            memoryBeforeLoadKb = memBefore,
            memoryAfterLoadKb = memAfterLoad,
            memoryPeakKb = memPeak,
            memoryAfterReleaseKb = memAfterRelease
        )
    }
}
