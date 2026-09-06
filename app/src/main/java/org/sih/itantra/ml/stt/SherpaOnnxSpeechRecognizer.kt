package org.sih.itantra.ml.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineDolphinModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.stt.SentenceFinalizer
import org.sih.itantra.core.stt.SpeechRecognizer
import org.sih.itantra.core.stt.SpeechResult
import org.sih.itantra.ml.model.ModelAssetManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real On-Device Neural Speech Recognizer powered by Sherpa-ONNX.
 * Uses Dolphin Small Multi-Lang CTC (INT8) for native Indic languages:
 * Hindi, Marathi, Gujarati, Bengali, Tamil, Telugu, Odia (direct native script decoding).
 * Uses Whisper-Tiny (INT8) for English.
 * Executes genuine offline inference without cloud APIs or network requests.
 */
class SherpaOnnxSpeechRecognizer(
    private val context: Context,
    val modelAssetManager: ModelAssetManager = ModelAssetManager(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : SpeechRecognizer {

    private val tag = "SherpaOnnxSTT"
    private val recognizers = java.util.concurrent.ConcurrentHashMap<IndicLanguage, OfflineRecognizer>()
    private val _results = MutableSharedFlow<SpeechResult>(replay = 1, extraBufferCapacity = 16)
    override val results: SharedFlow<SpeechResult> = _results.asSharedFlow()
    private val isListening = AtomicBoolean(false)
    private var isInitialized = false

    init {
        initEngine(IndicLanguage.HINDI)
    }

    @Synchronized
    fun getOrInitRecognizer(language: IndicLanguage): OfflineRecognizer? {
        recognizers[language]?.let { return it }

        // 1. IndicConformer NeMo CTC for supported Indic languages (SOTA accuracy)
        if (language != IndicLanguage.ODIA && language != IndicLanguage.ENGLISH && modelAssetManager.isIndicConformerSttReady(language)) {
            try {
                val nemoConfig = OfflineNemoEncDecCtcModelConfig(
                    model = modelAssetManager.getIndicConformerModelFile(language).absolutePath
                )
                val modelConfig = OfflineModelConfig().apply {
                    nemo = nemoConfig
                    tokens = modelAssetManager.indicConformerTokensFile.absolutePath
                    numThreads = 2
                    debug = false
                    provider = "cpu"
                }
                val featConfig = FeatureConfig().apply {
                    sampleRate = 16000
                    featureDim = 80
                }
                val config = OfflineRecognizerConfig().apply {
                    this.featConfig = featConfig
                    this.modelConfig = modelConfig
                    this.decodingMethod = "greedy_search"
                }
                val rec = OfflineRecognizer(assetManager = null, config = config)
                recognizers[language] = rec
                isInitialized = true
                Log.i(tag, "Sherpa-ONNX IndicConformer NeMo CTC STT initialized successfully for ${language.displayName} (${language.isoCode})!")
                return rec
            } catch (e: Throwable) {
                Log.e(tag, "Failed to initialize IndicConformer CTC for ${language.displayName}, attempting Dolphin fallback", e)
            }
        }

        // 2. Dolphin CTC for Odia or fallback
        val isIndic = language != IndicLanguage.ENGLISH
        if (isIndic && modelAssetManager.isDolphinSttReady()) {
            try {
                val dolphinConfig = OfflineDolphinModelConfig(
                    model = modelAssetManager.dolphinModelFile.absolutePath
                )
                val modelConfig = OfflineModelConfig().apply {
                    dolphin = dolphinConfig
                    tokens = modelAssetManager.dolphinTokensFile.absolutePath
                    numThreads = 2
                    debug = false
                    provider = "cpu"
                }
                val featConfig = FeatureConfig().apply {
                    sampleRate = 16000
                    featureDim = 80
                }
                val config = OfflineRecognizerConfig().apply {
                    this.featConfig = featConfig
                    this.modelConfig = modelConfig
                    this.decodingMethod = "greedy_search"
                }
                val rec = OfflineRecognizer(assetManager = null, config = config)
                recognizers[language] = rec
                isInitialized = true
                Log.i(tag, "Sherpa-ONNX Dolphin CTC STT initialized successfully for ${language.displayName} (${language.isoCode})!")
                return rec
            } catch (e: Throwable) {
                Log.e(tag, "Failed to initialize Dolphin CTC for ${language.displayName}, attempting Whisper fallback", e)
            }
        }

        // Whisper-Tiny for English or fallback
        if (modelAssetManager.isWhisperSttReady()) {
            try {
                val whisperConfig = OfflineWhisperModelConfig().apply {
                    encoder = modelAssetManager.whisperEncoderFile.absolutePath
                    decoder = modelAssetManager.whisperDecoderFile.absolutePath
                    this.language = language.isoCode
                    task = "transcribe"
                    tailPaddings = -1
                }

                val modelConfig = OfflineModelConfig().apply {
                    whisper = whisperConfig
                    tokens = modelAssetManager.whisperTokensFile.absolutePath
                    numThreads = 2
                    debug = false
                    provider = "cpu"
                }

                val featConfig = FeatureConfig().apply {
                    sampleRate = 16000
                    featureDim = 80
                }

                val config = OfflineRecognizerConfig().apply {
                    this.featConfig = featConfig
                    this.modelConfig = modelConfig
                    this.decodingMethod = "greedy_search"
                }

                val rec = OfflineRecognizer(assetManager = null, config = config)
                recognizers[language] = rec
                isInitialized = true
                Log.i(tag, "Sherpa-ONNX Whisper STT initialized successfully for ${language.displayName} (${language.isoCode})!")
                return rec
            } catch (e: Throwable) {
                Log.e(tag, "Failed to initialize Whisper STT for ${language.displayName}", e)
            }
        }

        Log.w(tag, "No STT model ready for ${language.displayName}")
        return null
    }

    @Synchronized
    fun initEngine(language: IndicLanguage = IndicLanguage.HINDI): Boolean {
        return getOrInitRecognizer(language) != null
    }

    override suspend fun processAudioSegment(pcmBytes: ByteArray, language: IndicLanguage): SpeechResult =
        withContext(dispatcher) {
            val currentRecognizer = getOrInitRecognizer(language) ?: getOrInitRecognizer(IndicLanguage.HINDI)
            if (currentRecognizer == null || pcmBytes.isEmpty()) {
                Log.w(tag, "Recognizer not initialized or audio empty")
                return@withContext SpeechResult(text = "", isFinal = true, language = language)
            }

            // MEASUREMENT INSTRUMENTATION (timing only — no behavior change):
            // This log fires on the inference dispatcher thread immediately before STT inference begins.
            // logcat -v epoch timestamp here = t_stt_input_ready = true speech-end anchor.
            Log.i(tag, "STT input ready: ${pcmBytes.size / 2} samples (${language.displayName})")

            try {
                // Convert 16-bit Mono PCM bytes (16kHz, little-endian) to FloatArray normalized [-1.0, 1.0]
                val numSamples = pcmBytes.size / 2
                val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val rawSamples = FloatArray(numSamples)
                var sumSq = 0.0
                for (i in 0 until numSamples) {
                    val s = buffer.get(i) / 32768.0f
                    rawSamples[i] = s
                    sumSq += s * s
                }

                // RMS Normalization (target: -20 dBFS = 0.10f) with gain clamped to avoid amplifying noise
                val rms = kotlin.math.sqrt(sumSq / numSamples.coerceAtLeast(1)).toFloat()
                val targetRms = 0.10f
                val gain = if (rms > 1e-4f) {
                    (targetRms / rms).coerceIn(0.2f, 4.0f)
                } else {
                    1.0f
                }

                // Silence padding (150ms = 2400 samples at 16kHz) to preserve boundary phonemes in CTC acoustic models
                val padSamples = (16000 * 0.15f).toInt()
                val totalSamples = rawSamples.size + 2 * padSamples
                val samples = FloatArray(totalSamples)
                for (i in 0 until numSamples) {
                    samples[padSamples + i] = (rawSamples[i] * gain).coerceIn(-1.0f, 1.0f)
                }

                val stream = currentRecognizer.createStream()
                stream.acceptWaveform(samples, 16000)
                currentRecognizer.decode(stream)
                val rawResult = currentRecognizer.getResult(stream)
                val rawText = rawResult.text.trim()
                stream.release()

                val finalText = SentenceFinalizer.finalizeSentence(rawText, language)
                val result = SpeechResult(
                    text = finalText,
                    isFinal = true,
                    language = language
                )
                _results.tryEmit(result)
                Log.i(tag, "Sherpa-ONNX ${language.displayName} STT Decoded: '$finalText' from ${samples.size} samples")
                result
            } catch (e: Throwable) {
                Log.e(tag, "Sherpa-ONNX ${language.displayName} STT decoding error", e)
                SpeechResult(text = "", isFinal = true, language = language)
            }
        }

    override fun startListening(language: IndicLanguage) {
        isListening.set(true)
    }

    override fun stopListening() {
        isListening.set(false)
    }

    override fun release() {
        for ((_, rec) in recognizers) {
            try {
                rec.release()
            } catch (e: Throwable) {
                Log.w(tag, "Error releasing recognizer", e)
            }
        }
        recognizers.clear()
        isInitialized = false
    }
}
